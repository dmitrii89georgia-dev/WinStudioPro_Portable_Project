package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.RubberType
import ru.winstudiopro.app.domain.calculator.WarehouseInventoryHelper
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.ImpostDirection
import ru.winstudiopro.app.domain.model.OpeningProperties
import ru.winstudiopro.app.domain.model.OrderStatus
import ru.winstudiopro.app.domain.model.ProfileMaterialType
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.ProfileRubberCatalog
import ru.winstudiopro.app.domain.model.RubberBoxSpec
import ru.winstudiopro.app.domain.model.SashOpeningType
import ru.winstudiopro.app.domain.model.StockRemnantBar
import ru.winstudiopro.app.domain.model.StockRubberItem
import ru.winstudiopro.app.domain.model.WedgeRubberVariantSpec
import ru.winstudiopro.app.domain.model.WindowCadOperations
import ru.winstudiopro.app.domain.model.WindowItem

class RubberAndWarehouseUnitTest {

    private val profiles = DefaultCatalogs.defaultProfileSystems()
    private val baseProfile = profiles.first { it.materialType == ProfileMaterialType.ALUMINUM }
    private val glazing = DefaultCatalogs.defaultGlazingItems()
    private val colors = DefaultCatalogs.defaultColors()
    private val sills = DefaultCatalogs.defaultSills()

    @Test
    fun `rubber box price computes pricePerMeter accurately and updates window rubber cost`() {
        val customCatalog = ProfileRubberCatalog(
            fixedGlazing = RubberBoxSpec(
                code = "01.11.15",
                name = "Резина для глухаря",
                thicknessMm = 4.0f,
                metersPerBox = 200.0,
                pricePerBox = 300.0 // 1.50 за метр
            ),
            wedgeVariants = listOf(
                WedgeRubberVariantSpec("w_36", "01.17.04", "Клин 3.6 мм", 3.6f, 200.0, 500.0) // 2.50 за метр
            ),
            frameRebate = RubberBoxSpec("01.18.03", "Резина рамы", 0f, 300.0, 300.0),
            sashDoorRebate = RubberBoxSpec("01.18.04", "Резина створки", 0f, 300.0, 300.0)
        )
        assertEquals(1.5, customCatalog.fixedGlazing.pricePerMeter, 0.0001)
        assertEquals(2.5, customCatalog.wedgeVariants.first().pricePerMeter, 0.0001)

        val customProfile = baseProfile.copy(id = "prof_custom_rub", rubberCatalog = customCatalog)
        val win = WindowItem(
            id = "w_rub",
            name = "Глухарь",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = customProfile.id,
            glazingId = "sp_24",
            colorId = customProfile.colors.first().id
        )

        val res = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = listOf(customProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        // Для глухаря используется 1 полный периметр наружной резины (1.5/м) + 1 полный периметр клина (2.5/м)
        val expectedCost = res.rubberPerimeterM * (1.5 + 2.5)
        assertEquals(expectedCost, res.costRubber, 0.01)
        assertTrue(res.matchedWedgeSummary.contains("Клин 3.6"))
    }

    @Test
    fun `selectOptimalWedgeVariant chooses closest wedge thickness for glazing and shtapik clearance`() {
        // Посадочное место 50 мм, штапик 18 мм, глухарь-резина 4 мм
        val wedgeFor25 = OrderProcurementHelper.selectOptimalWedgeVariant(baseProfile, 18f, 25, 50f)
        assertNotNull(wedgeFor25)
        assertEquals(2.8f, wedgeFor25!!.thicknessMm, 0.05f) // свободно 3.0 мм -> 2.8 мм

        val wedgeFor24 = OrderProcurementHelper.selectOptimalWedgeVariant(baseProfile, 18f, 24, 50f)
        assertNotNull(wedgeFor24)
        assertEquals(3.6f, wedgeFor24!!.thicknessMm, 0.05f) // свободно 4.0 мм -> 3.6 мм

        val wedgeFor23 = OrderProcurementHelper.selectOptimalWedgeVariant(baseProfile, 18f, 23, 50f)
        assertNotNull(wedgeFor23)
        assertEquals(4.6f, wedgeFor23!!.thicknessMm, 0.05f) // свободно 5.0 мм -> 4.6 мм
    }

    @Test
    fun `procurement helper computes rubber shortage and sufficient warehouse coverage`() {
        val win = WindowItem(
            id = "w1",
            name = "Окно 1",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = baseProfile.id,
            glazingId = "sp_24",
            colorId = baseProfile.colors.first().id
        )
        val res = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        val partialStock = listOf(
            StockRubberItem(
                id = "sr_fixed",
                profileSystemId = baseProfile.id,
                rubberType = RubberType.FIXED_GLAZING,
                metersInStock = 2.0
            ),
            StockRubberItem(
                id = "sr_wedge",
                profileSystemId = baseProfile.id,
                rubberType = RubberType.WEDGE_SHTAPIK,
                wedgeVariantId = "wedge_3_6",
                wedgeThicknessMm = 3.6f,
                metersInStock = 50.0
            )
        )

        val (_, rubbers) = OrderProcurementHelper.calculateCornerConnectorsAndRubber(listOf(res), partialStock)
        val fixedLine = rubbers.first { it.type == RubberType.FIXED_GLAZING }
        val wedgeLine = rubbers.first { it.type == RubberType.WEDGE_SHTAPIK }

        assertTrue(fixedLine.hasShortage)
        assertEquals((fixedLine.lengthMeters - 2.0).coerceAtLeast(0.0), fixedLine.shortageMeters, 0.01)
        assertFalse(wedgeLine.hasShortage)
        assertEquals(0.0, wedgeLine.shortageMeters, 0.001)
        assertEquals(1, fixedLine.boxesCount)
    }

    @Test
    fun `applyProductionStockDeduction consumes bars remnants and rubber once and is idempotent`() {
        val anthracite = baseProfile.colors.first { it.name.contains("Антрацит", ignoreCase = true) }
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
            id = "w_prod",
            name = "Окно 1",
            quantity = 1,
            topology = topo,
            profileSystemId = baseProfile.id,
            glazingId = "sp_24",
            colorId = anthracite.id
        )
        val order = CustomerOrder(
            id = "ord_prod",
            orderNumber = "П-100",
            customerName = "Тест",
            customerPhone = "",
            address = "",
            createdDate = "18.11.2026",
            windows = listOf(win),
            status = OrderStatus.PRODUCTION,
            stockDeductedForProduction = false
        )

        val initialBars = listOf(
            StockRemnantBar("b_frame", baseProfile.id, ProfileRole.FRAME, 6000, quantity = 5, isFullStockBar = true, colorName = anthracite.name, colorHex = anthracite.hexColor),
            StockRemnantBar("b_impost", baseProfile.id, ProfileRole.IMPOST, 6000, quantity = 2, isFullStockBar = true, colorName = anthracite.name, colorHex = anthracite.hexColor),
            StockRemnantBar("b_sash", baseProfile.id, ProfileRole.SASH, 6000, quantity = 2, isFullStockBar = true, colorName = anthracite.name, colorHex = anthracite.hexColor),
            StockRemnantBar("b_sht", baseProfile.id, ProfileRole.SHTAPIK, 6000, quantity = 3, isFullStockBar = true, colorName = anthracite.name, colorHex = anthracite.hexColor)
        )
        val initialRubber = listOf(
            StockRubberItem("r_fix", baseProfile.id, RubberType.FIXED_GLAZING, label = "Глухарь", metersInStock = 100.0),
            StockRubberItem("r_wdg", baseProfile.id, RubberType.WEDGE_SHTAPIK, wedgeVariantId = "wedge_3_6", wedgeThicknessMm = 3.6f, label = "Клин 3.6", metersInStock = 100.0),
            StockRubberItem("r_frm", baseProfile.id, RubberType.FRAME_REBATE, label = "Притвор рамы", metersInStock = 100.0),
            StockRubberItem("r_ssh", baseProfile.id, RubberType.SASH_DOOR_REBATE, label = "Притвор створки", metersInStock = 100.0)
        )

        val firstPass = WarehouseInventoryHelper.applyProductionStockDeduction(
            order = order,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            extraAccessoriesCatalog = DefaultCatalogs.defaultExtraAccessories(),
            workServicesCatalog = DefaultCatalogs.defaultWorkServices(),
            hardwareBrandsCatalog = DefaultCatalogs.defaultHardwareBrands(),
            currentStockBars = initialBars,
            currentStockRubber = initialRubber
        )

        assertTrue(firstPass.updatedOrder.stockDeductedForProduction)
        assertTrue(firstPass.consumedFullBarsCount > 0)
        assertTrue(firstPass.deductedRubberMeters > 5.0)
        assertTrue(firstPass.updatedStockRubber.all { it.metersInStock < 100.0 })

        // Повторный вызов не должен списывать материалы повторно
        val secondPass = WarehouseInventoryHelper.applyProductionStockDeduction(
            order = firstPass.updatedOrder,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            extraAccessoriesCatalog = DefaultCatalogs.defaultExtraAccessories(),
            workServicesCatalog = DefaultCatalogs.defaultWorkServices(),
            hardwareBrandsCatalog = DefaultCatalogs.defaultHardwareBrands(),
            currentStockBars = firstPass.updatedStockBars,
            currentStockRubber = firstPass.updatedStockRubber
        )
        assertEquals(0, secondPass.consumedFullBarsCount)
        assertEquals(0.0, secondPass.deductedRubberMeters, 0.0001)
        assertEquals(firstPass.updatedStockBars, secondPass.updatedStockBars)
        assertEquals(firstPass.updatedStockRubber, secondPass.updatedStockRubber)
    }
}
