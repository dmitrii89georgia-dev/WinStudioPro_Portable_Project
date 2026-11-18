package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.calculator.CornerConnectorType
import ru.winstudiopro.app.domain.calculator.CuttingPlanManualHelper
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.RubberType
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.AllocatedCutPiece
import ru.winstudiopro.app.domain.model.CutBarAllocation
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.ImpostDirection
import ru.winstudiopro.app.domain.model.OpeningProperties
import ru.winstudiopro.app.domain.model.ProfileMaterialType
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.SashOpeningType
import ru.winstudiopro.app.domain.model.StockRemnantBar
import ru.winstudiopro.app.domain.model.WindowCadOperations
import ru.winstudiopro.app.domain.model.WindowItem

class OrderProcurementHelperTest {

    private val profiles = DefaultCatalogs.defaultProfileSystems()
    private val alProfile = profiles.first { it.materialType == ProfileMaterialType.ALUMINUM }
    private val glazing = DefaultCatalogs.defaultGlazingItems()
    private val colors = DefaultCatalogs.defaultColors()
    private val sills = DefaultCatalogs.defaultSills()
    private val hardwareBrands = DefaultCatalogs.defaultHardwareBrands()

    @Test
    fun `buildGlazingOrderItems and buildHardwareOrderItems produce accurate rows for mixed window`() {
        // Окно 1500x1500: левая створка — поворотно-откидная (левая), правая — глухая
        val topo = WindowCadOperations.splitOpening(
            WindowCadOperations.single1500Topology(),
            "op1",
            ImpostDirection.VERTICAL,
            2
        ).let {
            WindowCadOperations.updateOpeningProperties(
                it,
                "op1_v1",
                OpeningProperties(openingType = SashOpeningType.TILT_TURN_LEFT)
            )
        }
        val win = WindowItem(
            id = "w1",
            name = "Окно 1",
            quantity = 2,
            topology = topo,
            profileSystemId = alProfile.id,
            glazingId = "sp_24",
            colorId = alProfile.colors.first().id,
            hardwareBrandId = hardwareBrands.first().id
        )
        val res = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            hardwareBrandsCatalog = hardwareBrands
        )

        val glazingRows = OrderProcurementHelper.buildGlazingOrderItems(listOf(res))
        assertEquals(2, glazingRows.size)
        assertTrue(glazingRows.all { it.isSelected })
        assertTrue(glazingRows.all { it.quantity == 2 })
        assertTrue(glazingRows.all { it.singleAreaM2 > 0.0 && it.totalAreaM2 > 0.0 })

        val hwRows = OrderProcurementHelper.buildHardwareOrderItems(listOf(res), hardwareBrands)
        assertEquals(1, hwRows.size)
        assertEquals("Левая", hwRows[0].sideLabel)
        assertEquals(SashOpeningType.TILT_TURN_LEFT, hwRows[0].openingType)
        assertEquals(2, hwRows[0].quantity)
        assertTrue(hwRows[0].falzWidthMm > 400 && hwRows[0].falzHeightMm > 800)
        assertTrue(hwRows[0].isSelected)
    }

    @Test
    fun `calculateCornerConnectorsAndRubber computes all 4 connector types and 4 rubber types unchecked by default`() {
        // Три секции: глухарь + оконная створка + обратная дверь
        val topo = WindowCadOperations.splitOpening(
            WindowCadOperations.single1500Topology(),
            "op1",
            ImpostDirection.VERTICAL,
            3
        ).let { t1 ->
            WindowCadOperations.updateOpeningProperties(
                t1,
                "op1_v1",
                OpeningProperties(openingType = SashOpeningType.TURN_RIGHT, isDoorSash = true)
            )
        }.let { t2 ->
            WindowCadOperations.updateOpeningProperties(
                t2,
                "op1_v2",
                OpeningProperties(openingType = SashOpeningType.TURN_LEFT, isReverseDoorSash = true)
            )
        }
        // Отдельно добавим обычную оконную створку во втором окне
        val topo2 = WindowCadOperations.updateOpeningProperties(
            WindowCadOperations.single1500Topology(),
            "op1",
            OpeningProperties(openingType = SashOpeningType.TILT_TURN_RIGHT)
        )

        val res1 = WindowBomAndCostEngine.calculateWindow(
            window = WindowItem(id = "w1", name = "Дверной блок", quantity = 1, topology = topo, profileSystemId = alProfile.id),
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )
        val res2 = WindowBomAndCostEngine.calculateWindow(
            window = WindowItem(id = "w2", name = "Створка", quantity = 1, topology = topo2, profileSystemId = alProfile.id),
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        val (connectors, rubbers) = OrderProcurementHelper.calculateCornerConnectorsAndRubber(listOf(res1, res2))

        assertEquals(4, connectors.size)
        assertTrue(connectors.none { it.isSelected })
        assertEquals(2, connectors.first { it.type == CornerConnectorType.FRAME }.setsCount)
        assertEquals(1, connectors.first { it.type == CornerConnectorType.SASH }.setsCount)
        assertEquals(1, connectors.first { it.type == CornerConnectorType.DOOR }.setsCount)
        assertEquals(1, connectors.first { it.type == CornerConnectorType.REVERSE_DOOR }.setsCount)

        assertEquals(4, rubbers.size)
        assertTrue(rubbers.none { it.isSelected })
        assertTrue(rubbers.first { it.type == RubberType.FIXED_GLAZING }.lengthMeters > 0.0)
        assertTrue(rubbers.first { it.type == RubberType.FRAME_REBATE }.lengthMeters > 0.0)
        assertTrue(rubbers.first { it.type == RubberType.SASH_DOOR_REBATE }.lengthMeters > 0.0)
        assertTrue(rubbers.first { it.type == RubberType.WEDGE_SHTAPIK }.lengthMeters > 0.0)
    }

    @Test
    fun `findAvailableWarehouseRemnants filters business remnants by length, color trim, and already allocated count`() {
        val stock = listOf(
            StockRemnantBar(
                id = "full",
                profileSystemId = alProfile.id,
                role = ProfileRole.FRAME,
                lengthMm = 6000,
                quantity = 5,
                isFullStockBar = true,
                colorName = "Антрацит"
            ),
            StockRemnantBar(
                id = "rem_big",
                profileSystemId = alProfile.id,
                role = ProfileRole.FRAME,
                lengthMm = 2400,
                quantity = 2,
                isFullStockBar = false,
                colorName = " Антрацит "
            ),
            StockRemnantBar(
                id = "rem_small",
                profileSystemId = alProfile.id,
                role = ProfileRole.FRAME,
                lengthMm = 900,
                quantity = 1,
                isFullStockBar = false,
                colorName = "Антрацит"
            )
        )
        val allocatedBar = CutBarAllocation(
            barNumber = 1,
            profileSystemName = alProfile.name,
            role = ProfileRole.FRAME,
            isFromRemnantStock = true,
            sourceLengthMm = 2400,
            cuts = listOf(AllocatedCutPiece("Окно 1", "Рама", "1-1", 1200, 45, 45)),
            usedWithKerfMm = 1200,
            remnantLengthMm = 1200,
            isUsableBusinessRemnant = true,
            colorName = "Антрацит"
        )

        val available = CuttingPlanManualHelper.findAvailableWarehouseRemnants(
            stockRemnants = stock,
            allocations = listOf(allocatedBar),
            excludeBarIndex = -1,
            profileSystemName = alProfile.name,
            profileSystemId = alProfile.id,
            role = ProfileRole.FRAME,
            colorName = "антрацит",
            minLengthMm = 1500
        )

        assertEquals(1, available.size)
        assertEquals(2400, available[0].lengthMm)
        assertEquals(1, available[0].quantity)
        assertFalse(available.any { it.isFullStockBar })
    }
}
