package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.AllocatedCutPiece
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.FinSchemeSettings
import ru.winstudiopro.app.domain.model.FinancialSchemeItem
import ru.winstudiopro.app.domain.model.OpeningProperties
import ru.winstudiopro.app.domain.model.ProfileCutPiece
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.SashImpostMode
import ru.winstudiopro.app.domain.model.SashOpeningType
import ru.winstudiopro.app.domain.model.WindowCadOperations
import ru.winstudiopro.app.domain.model.WindowCalculationResult
import ru.winstudiopro.app.domain.model.WindowItem
import ru.winstudiopro.app.domain.model.WindowPerimeterAddons
import kotlin.math.abs

class CadGeometryAndPricingV6Test {

    private val profiles = DefaultCatalogs.defaultProfileSystems()
    private val baseProfile = profiles.first().copy(
        priceShtulpPerM = 12.0,
        priceCornerProfilePerM = 9.0,
        sawKerfMm = 4f
    )
    private val glazing = DefaultCatalogs.defaultGlazingItems()
    private val colors = DefaultCatalogs.defaultColors()
    private val sills = DefaultCatalogs.defaultSills()

    @Test
    fun `shtulp geometry supports asymmetric leaf split shtulp cut and procurement labels`() {
        val baseTopo = WindowCadOperations.updateOpeningProperties(
            WindowCadOperations.single1500Topology(),
            "op1",
            OpeningProperties(openingType = SashOpeningType.SHTULP_TILTTURN_L_PASSIVE_R)
        )
        val asymTopo = WindowCadOperations.updateShtulpSplitBySegment(
            topology = baseTopo,
            frameId = "f1",
            openingId = "op1",
            isLeftLeaf = true,
            enteredWidthMm = 650f,
            profileSystem = baseProfile
        )
        val asymOp = asymTopo.allOpenings.first()
        val geom = WindowCadOperations.computeShtulpGeometry(asymTopo.frames.first(), asymOp, baseProfile)
        assertEquals(650f, geom.leftDedMm + geom.lightWLMm, 1.5f)
        assertTrue(geom.lightWLMm < geom.lightWRMm)

        val win = WindowItem(
            id = "w_shtulp",
            name = "Штульповое окно",
            quantity = 1,
            topology = asymTopo,
            profileSystemId = baseProfile.id,
            glazingId = "sp_24",
            colorId = baseProfile.colors.first().id
        )
        val res = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = listOf(baseProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        val shtulpCuts = res.profileCuts.filter { it.role == ProfileRole.SHTULP }
        assertEquals(1, shtulpCuts.size)
        assertEquals(90, shtulpCuts[0].leftAngleDeg)
        assertEquals(90, shtulpCuts[0].rightAngleDeg)
        assertTrue(res.shtulpLengthM > 1.0)
        assertTrue(res.costShtulp > 0.0)

        val leftHorizSash = res.profileCuts.first {
            it.role == ProfileRole.SASH && it.positionLabel.contains("Лев.") && it.positionLabel.contains("гориз.")
        }
        val rightHorizSash = res.profileCuts.first {
            it.role == ProfileRole.SASH && it.positionLabel.contains("Прав.") && it.positionLabel.contains("гориз.")
        }
        assertTrue(leftHorizSash.nominalLengthMm < rightHorizSash.nominalLengthMm)

        val hwRows = OrderProcurementHelper.buildHardwareOrderItems(
            listOf(res),
            DefaultCatalogs.defaultHardwareBrands()
        )
        assertEquals(1, hwRows.size)
        assertEquals("Акт.Л + Пас.П", hwRows[0].sideLabel)
        assertTrue(hwRows[0].falzWidthMm < asymOp.widthMm.toInt() / 2 + 100)
    }

    @Test
    fun `sash impost modes V1 H1 V2 H2 and per-pane sandwich flags calculate imposts glazing and shtapiks`() {
        // Режим H1 с сэндвичем только в верхнем проёме двери (индекс 0 = true, индекс 1 = false)
        val topoH1 = WindowCadOperations.updateOpeningProperties(
            WindowCadOperations.single1500Topology(),
            "op1",
            OpeningProperties(
                openingType = SashOpeningType.TURN_RIGHT,
                isDoorSash = true,
                sashImpostMode = SashImpostMode.H1,
                hasHorizontalSashImpost = true,
                paneSandwichFlags = listOf(true, false)
            )
        )
        val resH1 = WindowBomAndCostEngine.calculateWindow(
            window = WindowItem(
                id = "w_door_h1",
                name = "Дверь H1",
                quantity = 1,
                topology = topoH1,
                profileSystemId = baseProfile.id,
                glazingId = "sp_24",
                colorId = baseProfile.colors.first().id
            ),
            profileSystems = listOf(baseProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )
        assertEquals(2, resH1.glazingSpecs.size)
        assertTrue(resH1.glazingSpecs[0].isSandwich)
        assertFalse(resH1.glazingSpecs[1].isSandwich)
        assertTrue(resH1.sandwichAreaM2 > 0.0)
        assertTrue(resH1.glazingAreaM2 > 0.0)
        assertTrue(resH1.profileCuts.any { it.role == ProfileRole.IMPOST && it.positionLabel.contains("внутри створки/двери") })

        // Режим V2 (2 вертикальных импоста внутри створки -> 3 проёма, сэндвич в среднем)
        val topoV2 = WindowCadOperations.updateOpeningProperties(
            WindowCadOperations.single1500Topology(),
            "op1",
            OpeningProperties(
                openingType = SashOpeningType.TURN_LEFT,
                sashImpostMode = SashImpostMode.V2,
                paneSandwichFlags = listOf(false, true, false)
            )
        )
        val resV2 = WindowBomAndCostEngine.calculateWindow(
            window = WindowItem(
                id = "w_sash_v2",
                name = "Створка V2",
                quantity = 1,
                topology = topoV2,
                profileSystemId = baseProfile.id,
                glazingId = "sp_24",
                colorId = baseProfile.colors.first().id
            ),
            profileSystems = listOf(baseProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )
        assertEquals(3, resV2.glazingSpecs.size)
        assertFalse(resV2.glazingSpecs[0].isSandwich)
        assertTrue(resV2.glazingSpecs[1].isSandwich)
        assertFalse(resV2.glazingSpecs[2].isSandwich)
        val impostCutV2 = resV2.profileCuts.first { it.role == ProfileRole.IMPOST }
        assertEquals(2, impostCutV2.quantity)
        // 3 проёма * 4 штапика = 12 штук штапика
        assertEquals(12, resV2.profileCuts.filter { it.role == ProfileRole.SHTAPIK }.sumOf { it.quantity })
    }

    @Test
    fun `independent corner and extender 90 addons add 90-deg cuts and price without altering window geometry`() {
        val baseWin = WindowItem(
            id = "w_plain",
            name = "Окно 1",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = baseProfile.id,
            glazingId = "sp_24",
            colorId = baseProfile.colors.first().id,
            addons = WindowPerimeterAddons()
        )
        val withAddons = baseWin.copy(
            id = "w_with_addons",
            addons = WindowPerimeterAddons(hasCornerProfile = true, hasExtenderProfile90 = true)
        )

        val resPlain = WindowBomAndCostEngine.calculateWindow(
            window = baseWin,
            profileSystems = listOf(baseProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )
        val resAddons = WindowBomAndCostEngine.calculateWindow(
            window = withAddons,
            profileSystems = listOf(baseProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )

        // Размеры рамы и стеклопакета не меняются
        assertEquals(resPlain.framePerimeterM, resAddons.framePerimeterM, 0.001)
        assertEquals(resPlain.glazingAreaM2, resAddons.glazingAreaM2, 0.001)

        // Добавляются угловик (1500 мм по высоте, 90°) и доборный профиль 90° (1500 мм по ширине, 90°)
        assertEquals(0.0, resPlain.costCornerProfile, 0.001)
        assertEquals(0.0, resPlain.costExtenderProfile, 0.001)
        assertEquals(1.5 * baseProfile.priceCornerProfilePerM, resAddons.costCornerProfile, 0.001)
        assertEquals(1.5 * baseProfile.priceFramePerM, resAddons.costExtenderProfile, 0.001)

        val cornerCut = resAddons.profileCuts.first { it.role == ProfileRole.CORNER_PROFILE }
        assertEquals(1500, cornerCut.cutLengthMm)
        assertEquals(90, cornerCut.leftAngleDeg)
        assertEquals(90, cornerCut.rightAngleDeg)

        val extCut = resAddons.profileCuts.first { it.role == ProfileRole.EXTENDER }
        assertEquals(1500, extCut.cutLengthMm)
        assertEquals(90, extCut.leftAngleDeg)
        assertEquals(90, extCut.rightAngleDeg)
    }

    @Test
    fun `sawKerfMm directly changes 1D cutting bar count when pieces sit on boundary`() {
        val sysThinKerf = baseProfile.copy(id = "sys_kerf", stockBarLengthMm = 6000f, sawKerfMm = 4f)
        val sysThickKerf = baseProfile.copy(id = "sys_kerf", stockBarLengthMm = 6000f, sawKerfMm = 6f)

        val win = WindowItem(
            id = "w_kerf",
            name = "Окно рез",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = "sys_kerf",
            glazingId = "sp_24",
            colorId = baseProfile.colors.first().id
        )
        val baseRes = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = listOf(sysThinKerf),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills
        )
        // Подменяем отрезки на граничный кейс: 3000 мм + 2995 мм = 5995 мм
        // При пиле 4 мм -> 5999 <= 6000 (1 хлыст). При пиле 6 мм -> 6001 > 6000 (2 хлыста).
        val boundaryCuts = listOf(
            ProfileCutPiece("Окно", "sys_kerf", sysThinKerf.name, sysThinKerf.materialType, ProfileRole.FRAME, "Рама 1", "1-1", 3000, 3000, 45, 45, 1, "Белый", 0xFFFFFFFFL),
            ProfileCutPiece("Окно", "sys_kerf", sysThinKerf.name, sysThinKerf.materialType, ProfileRole.FRAME, "Рама 2", "1-2", 2995, 2995, 45, 45, 1, "Белый", 0xFFFFFFFFL)
        )
        val testRes = baseRes.copy(profileCuts = boundaryCuts)

        val (_, summary4mm) = WindowBomAndCostEngine.optimizeOrderProfileCutting(
            windowResults = listOf(testRes),
            stockRemnants = emptyList(),
            profileSystems = listOf(sysThinKerf)
        )
        val (_, summary6mm) = WindowBomAndCostEngine.optimizeOrderProfileCutting(
            windowResults = listOf(testRes),
            stockRemnants = emptyList(),
            profileSystems = listOf(sysThickKerf)
        )

        assertEquals(1, summary4mm.first().newFullBarsToBuyCount)
        assertEquals(2, summary6mm.first().newFullBarsToBuyCount)
    }

    @Test
    fun `editable FinancialSchemeItem applies custom markup and discount to window pricing`() {
        val customSchemes = DefaultCatalogs.defaultFinancialSchemes() + FinancialSchemeItem(
            id = "fin_vip",
            name = "VIP Дилер",
            discountPercent = 10.0,
            markupPercent = 20.0
        )
        val baseWin = WindowItem(
            id = "w_fin",
            name = "Окно фин",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = baseProfile.id,
            glazingId = "sp_24",
            colorId = baseProfile.colors.first().id,
            financialSchemeId = "fin_none"
        )
        val vipWin = baseWin.copy(financialSchemeId = "fin_vip")

        val resNone = WindowBomAndCostEngine.calculateWindow(
            window = baseWin,
            profileSystems = listOf(baseProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            financialSchemesCatalog = customSchemes
        )
        val resVip = WindowBomAndCostEngine.calculateWindow(
            window = vipWin,
            profileSystems = listOf(baseProfile),
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            financialSchemesCatalog = customSchemes
        )

        // +20% наценка и -10% скидка -> 1.20 * 0.90 = 1.08 от базовой цены
        assertEquals(10.0, resVip.discountPercentApplied, 0.001)
        assertEquals(resNone.unitPrimeCost * 1.20, resVip.rawUnitCostBeforeDiscount, 0.01)
        assertEquals(resNone.unitPrimeCost * 1.08, resVip.unitTotalCost, 0.01)

        // Проверяем настройки FinSchemeSettings с defaultProfileSystemId
        val settings = FinSchemeSettings(
            financialSchemes = customSchemes,
            defaultProfileSystemId = baseProfile.id
        )
        assertEquals(baseProfile.id, settings.defaultProfileSystemId)
        assertTrue(settings.financialSchemes.any { it.id == "fin_vip" && abs(it.discountPercent - 10.0) < 0.01 })
    }
}
