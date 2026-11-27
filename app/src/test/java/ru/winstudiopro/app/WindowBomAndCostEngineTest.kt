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

    @Test
    fun `hardwareBrandId hw_none zeroes out costHardware while preserving hardwareSpecs`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val profile = profiles.first()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()
        val hwBrands = DefaultCatalogs.defaultHardwareBrands()

        val topo = WindowCadOperations.updateOpeningProperties(
            WindowCadOperations.single1500Topology(),
            "op1",
            OpeningProperties(openingType = SashOpeningType.TILT_TURN_RIGHT)
        )
        val baseWin = WindowItem(
            id = "w_hw_test",
            name = "Окно фурнитура",
            quantity = 1,
            topology = topo,
            profileSystemId = profile.id,
            glazingId = "sp_24",
            colorId = profile.colors.first().id,
            hardwareBrandId = "hw_none"
        )

        val resExcluded = WindowBomAndCostEngine.calculateWindow(
            window = baseWin,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            hardwareBrandsCatalog = hwBrands
        )
        assertEquals(0.0, resExcluded.costHardware, 0.001)
        assertEquals(1, resExcluded.hardwareSpecs.size)
        assertEquals(0.0, resExcluded.hardwareSpecs.first().costPerSet, 0.001)

        val brand = hwBrands.first { it.id != "hw_none" }
        val resIncluded = WindowBomAndCostEngine.calculateWindow(
            window = baseWin.copy(hardwareBrandId = brand.id, sashHardwareId = brand.id),
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            hardwareBrandsCatalog = hwBrands
        )
        assertEquals(brand.priceMultiplierOrAddon, resIncluded.costHardware, 0.001)
    }

    @Test
    fun `3-group hardware resolves DOOR SASH TRANSOM separately keeps assembly labor on hw_none and routes tilt-turn to SASH`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val profile = profiles.first()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()
        val hwBrands = DefaultCatalogs.defaultHardwareBrands()
        val asmCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog()

        // 3 проёма: 1) Поворотная с маркером двери (DOOR), 2) Поворотно-откидная с маркером двери (SASH!), 3) Фрамуга (TRANSOM)
        var topo = WindowCadOperations.splitOpening(
            WindowCadOperations.single1500Topology(),
            "op1",
            ImpostDirection.VERTICAL,
            3
        )
        val ops = topo.allOpenings.sortedBy { it.leftMm }
        topo = WindowCadOperations.updateOpeningProperties(
            topo,
            ops[0].id,
            OpeningProperties(openingType = SashOpeningType.TURN_RIGHT, isDoorSash = true)
        )
        topo = WindowCadOperations.updateOpeningProperties(
            topo,
            ops[1].id,
            OpeningProperties(openingType = SashOpeningType.TILT_TURN_LEFT, isDoorSash = true)
        )
        topo = WindowCadOperations.updateOpeningProperties(
            topo,
            ops[2].id,
            OpeningProperties(openingType = SashOpeningType.TRANSOM_BOTTOM)
        )

        val doorBrand = hwBrands.first { it.group == ru.winstudiopro.app.domain.model.HardwareGroup.DOOR && it.id != "hw_none" }
        val sashBrand = hwBrands.first { it.group == ru.winstudiopro.app.domain.model.HardwareGroup.SASH && it.id != "hw_none" }
        val transomBrand = hwBrands.first { it.group == ru.winstudiopro.app.domain.model.HardwareGroup.TRANSOM && it.id != "hw_none" }

        val winAllSelected = WindowItem(
            id = "w_3grp",
            name = "Смешанная конструкция",
            quantity = 1,
            topology = topo,
            profileSystemId = profile.id,
            glazingId = "sp_24",
            colorId = profile.colors.first().id,
            doorHardwareId = doorBrand.id,
            sashHardwareId = sashBrand.id,
            transomHardwareId = transomBrand.id
        )

        val resAll = WindowBomAndCostEngine.calculateWindow(
            window = winAllSelected,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            hardwareBrandsCatalog = hwBrands,
            assemblyPricingCatalog = asmCatalog
        )
        val expectedSum = doorBrand.priceMultiplierOrAddon + sashBrand.priceMultiplierOrAddon + transomBrand.priceMultiplierOrAddon
        assertEquals(expectedSum, resAll.costHardware, 0.001)
        assertEquals(3, resAll.hardwareSpecs.size)

        // Убираем только дверную фурнитуру (hw_none): стоимость материала двери = 0, но ЗП сборщика остаётся прежней!
        val resDoorClient = WindowBomAndCostEngine.calculateWindow(
            window = winAllSelected.copy(doorHardwareId = "hw_none"),
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            hardwareBrandsCatalog = hwBrands,
            assemblyPricingCatalog = asmCatalog
        )
        assertEquals(
            sashBrand.priceMultiplierOrAddon + transomBrand.priceMultiplierOrAddon,
            resDoorClient.costHardware,
            0.001
        )
        assertEquals(resAll.costAssembly, resDoorClient.costAssembly, 0.001)
        assertEquals(3, resDoorClient.hardwareSpecs.size)
    }

    @Test
    fun `BLIND_SASH does not add rebate rubber perimeter`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val profile = profiles.first()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()

        val fixedTopo = WindowCadOperations.single1500Topology()
        val blindSashTopo = WindowCadOperations.updateOpeningProperties(
            fixedTopo,
            "op1",
            OpeningProperties(openingType = SashOpeningType.BLIND_SASH)
        )
        val turnSashTopo = WindowCadOperations.updateOpeningProperties(
            fixedTopo,
            "op1",
            OpeningProperties(openingType = SashOpeningType.TURN_RIGHT)
        )

        val resFixed = WindowBomAndCostEngine.calculateWindow(
            window = WindowItem("wf", "Глухарь", 1, fixedTopo, profile.id, "sp_24", profile.colors.first().id),
            profileSystems = profiles, glazingCatalog = glazing, colorCatalog = colors, sillsCatalog = sills
        )
        val resBlind = WindowBomAndCostEngine.calculateWindow(
            window = WindowItem("wb", "Глух.ств", 1, blindSashTopo, profile.id, "sp_24", profile.colors.first().id),
            profileSystems = profiles, glazingCatalog = glazing, colorCatalog = colors, sillsCatalog = sills
        )
        val resTurn = WindowBomAndCostEngine.calculateWindow(
            window = WindowItem("wt", "Повор", 1, turnSashTopo, profile.id, "sp_24", profile.colors.first().id),
            profileSystems = profiles, glazingCatalog = glazing, colorCatalog = colors, sillsCatalog = sills
        )

        assertTrue(resBlind.rubberPerimeterM < resTurn.rubberPerimeterM)
        assertTrue(resTurn.rubberPerimeterM > resFixed.rubberPerimeterM + 4.0)
    }

    @Test
    fun `resolveOrderServicePrice returns 0 for deleted service even with customPriceOverride and reconciles manual overrides`() {
        val catalog = listOf(
            ru.winstudiopro.app.domain.model.OrderExtraServiceItem("srv_1", "Доставка", 50.0)
        )
        val deletedSelection = ru.winstudiopro.app.domain.model.SelectedOrderService(
            serviceId = "srv_deleted",
            customPriceOverride = 120.0
        )
        assertEquals(0.0, WindowBomAndCostEngine.resolveOrderServicePrice(deletedSelection, catalog), 0.001)

        val activeSelection = ru.winstudiopro.app.domain.model.SelectedOrderService(
            serviceId = "srv_1",
            customPriceOverride = 80.0
        )
        assertEquals(80.0, WindowBomAndCostEngine.resolveOrderServicePrice(activeSelection, catalog), 0.001)
    }
}
