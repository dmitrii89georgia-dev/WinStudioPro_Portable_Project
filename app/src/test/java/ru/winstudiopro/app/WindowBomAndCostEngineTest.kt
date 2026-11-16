package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.CUSTOM_CLIENT_COLOR_ID
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.ImpostDirection
import ru.winstudiopro.app.domain.model.OpeningProperties
import ru.winstudiopro.app.domain.model.ProfileMaterialType
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.RAW_UNPAINTED_COLOR_NAME
import ru.winstudiopro.app.domain.model.SashOpeningType
import ru.winstudiopro.app.domain.model.StockRemnantBar
import ru.winstudiopro.app.domain.model.WindowCadOperations
import ru.winstudiopro.app.domain.model.WindowItem

class WindowBomAndCostEngineTest {

    @Test
    fun `calculateWindow computes aluminum cuts and glazing correctly`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val alProfile = profiles.first { it.materialType == ProfileMaterialType.ALUMINUM }
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()

        val topo = WindowCadOperations.splitOpening(
            WindowCadOperations.single1500Topology(),
            "op1",
            ImpostDirection.VERTICAL,
            2
        ).let {
            WindowCadOperations.updateOpeningProperties(
                it,
                "op1_v1",
                OpeningProperties(openingType = SashOpeningType.TILT_TURN_RIGHT)
            )
        }

        val win = WindowItem(
            id = "w1",
            name = "Окно 1",
            quantity = 1,
            topology = topo,
            profileSystemId = alProfile.id,
            glazingId = "sp_24",
            colorId = alProfile.colors.first().id
        )

        val result = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        assertEquals(2.25, result.totalAreaM2, 0.001)
        assertEquals(6.0, result.framePerimeterM, 0.001)
        assertTrue(result.impostsLengthM > 1.0)
        assertTrue(result.sashPerimeterM > 2.0)
        assertTrue(result.unitTotalCost > 0.0)
        assertTrue(result.profileCuts.any { it.role == ProfileRole.FRAME })
        assertTrue(result.profileCuts.any { it.role == ProfileRole.IMPOST })
        assertTrue(result.profileCuts.any { it.role == ProfileRole.SASH })
        assertEquals(2, result.glazingSpecs.size)
    }

    @Test
    fun `custom client color adds fixed painting cost once per project line and respects stock color option`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val profile = profiles.first()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()

        val baseWin = WindowItem(
            id = "w_custom",
            name = "Окно 1",
            quantity = 3,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = profile.id,
            glazingId = "sp_24",
            colorId = CUSTOM_CLIENT_COLOR_ID,
            customColorName = "Бордовый",
            customColorRal = "3005",
            customColorHex = 0xFF5E2129,
            customPaintingCost = 500.0,
            customColorUseRawForStock = false
        )

        val resClientStock = WindowBomAndCostEngine.calculateWindow(
            window = baseWin,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        // Покраска фиксированная (500), НЕ умножается на quantity=3
        assertEquals(500.0, resClientStock.costCustomPainting, 0.001)
        assertEquals(resClientStock.unitTotalCost * 3 + 500.0, resClientStock.lineTotalCost, 0.001)
        assertTrue(resClientStock.cutColorName.contains("Бордовый"))

        val rawWin = baseWin.copy(customColorUseRawForStock = true)
        val resRawStock = WindowBomAndCostEngine.calculateWindow(
            window = rawWin,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )
        assertEquals(RAW_UNPAINTED_COLOR_NAME, resRawStock.cutColorName)
    }

    @Test
    fun `optimizeOrderProfileCutting matches stock remnants strictly by color`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val profile = profiles.first()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()

        val anthraciteColor = profile.colors.first { it.name.contains("Антрацит", ignoreCase = true) }
        val whiteColor = profile.colors.first { it.name.contains("Белый", ignoreCase = true) }

        val winAnthracite = WindowItem(
            id = "w_ant",
            name = "Окно Антрацит",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = profile.id,
            glazingId = "sp_24",
            colorId = anthraciteColor.id
        )

        val calc = WindowBomAndCostEngine.calculateWindow(
            window = winAnthracite,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        // На складе есть остаток Белый 3200 мм (не должен подойти к Антрациту!)
        // и один остаток Антрацит 3200 мм (должен подойти для 2 резов по 1500 мм!)
        val stock = listOf(
            StockRemnantBar(
                id = "rem_white",
                profileSystemId = profile.id,
                role = ProfileRole.FRAME,
                lengthMm = 3200,
                quantity = 2,
                isFullStockBar = false,
                colorName = whiteColor.name,
                colorHex = whiteColor.hexColor
            ),
            StockRemnantBar(
                id = "rem_ant",
                profileSystemId = profile.id,
                role = ProfileRole.FRAME,
                lengthMm = 3200,
                quantity = 1,
                isFullStockBar = false,
                colorName = anthraciteColor.name,
                colorHex = anthraciteColor.hexColor
            )
        )

        val (allocations, summaries) = WindowBomAndCostEngine.optimizeOrderProfileCutting(
            windowResults = listOf(calc),
            stockRemnants = stock,
            profileSystems = profiles
        )

        val frameSummary = summaries.first { it.role == ProfileRole.FRAME }
        // Использован ровно 1 подходящий остаток Антрацита + 1 новый хлыст для оставшихся 2 отрезков
        assertEquals(1, frameSummary.usedRemnantBarsCount)
        assertEquals(1, frameSummary.newFullBarsToBuyCount)
        assertFalse(allocations.any { it.colorName.equals(whiteColor.name, ignoreCase = true) })
        assertTrue(allocations.any { it.isFromRemnantStock && it.colorName.equals(anthraciteColor.name, ignoreCase = true) })
    }
}
