package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.AssemblyPricingCatalog
import ru.winstudiopro.app.domain.model.AssemblySchemeType
import ru.winstudiopro.app.domain.model.BeadAssemblyRateMode
import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.FinSchemeSettings
import ru.winstudiopro.app.domain.model.ImpostDirection
import ru.winstudiopro.app.domain.model.OpeningProperties
import ru.winstudiopro.app.domain.model.OrderExtraServiceItem
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.RubberAssemblyRateMode
import ru.winstudiopro.app.domain.model.SashOpeningType
import ru.winstudiopro.app.domain.model.SelectedOrderService
import ru.winstudiopro.app.domain.model.WindowCadOperations
import ru.winstudiopro.app.domain.model.WindowItem

class AssemblyAndFinancialUnitTest {

    private fun buildSampleTwoSashWindow(schemeOverride: AssemblySchemeType? = null): WindowItem {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val alProfile = profiles.first()
        var topo = WindowCadOperations.splitOpening(
            WindowCadOperations.single1500Topology(),
            "op1",
            ImpostDirection.VERTICAL,
            2
        )
        topo = WindowCadOperations.updateOpeningProperties(
            topo,
            "op1_v0",
            OpeningProperties(openingType = SashOpeningType.TURN_LEFT)
        )
        topo = WindowCadOperations.updateOpeningProperties(
            topo,
            "op1_v1",
            OpeningProperties(openingType = SashOpeningType.TILT_TURN_RIGHT)
        )
        return WindowItem(
            id = "w_asm_test",
            name = "Окно 1",
            quantity = 1,
            topology = topo,
            profileSystemId = alProfile.id,
            glazingId = "sp_24",
            colorId = alProfile.colors.first().id,
            financialSchemeId = "fin_none",
            assemblySchemeOverride = schemeOverride
        )
    }

    @Test
    fun `default catalogs remove wrk_delivery from installation works and expose it in order extra services`() {
        val works = DefaultCatalogs.defaultWorkServices()
        assertFalse(works.any { it.id == "wrk_delivery" })

        val orderServices = DefaultCatalogs.defaultOrderExtraServices()
        assertTrue(orderServices.any { it.id == "ord_srv_delivery" })
        assertTrue(orderServices.all { it.applyToWholeOrder })

        val assemblyDefaults = DefaultCatalogs.defaultAssemblyPricingCatalog()
        assertEquals(AssemblySchemeType.OPERATIONS, assemblyDefaults.defaultScheme)
    }

    @Test
    fun `scheme 1 operations calculates corners impost hardware bead modes and rubber modes accurately`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()
        val win = buildSampleTwoSashWindow()

        val baseAssembly = AssemblyPricingCatalog(
            defaultScheme = AssemblySchemeType.OPERATIONS,
            frameCornerRate = 1.0,      // 4 угла рамы = 4.0
            sashCornerRate = 1.5,       // 2 створки * 4 = 8 углов = 12.0
            doorCornerRate = 2.0,       // 0 дверей
            impostRate = 3.0,           // 1 импост = 3.0
            hwTurnRate = 4.0,           // 1 поворотная = 4.0
            hwTiltTurnRate = 5.0,       // 1 поворотно-откидная = 5.0
            hwShtulpRate = 6.0,
            hwSlidingRate = 7.0,
            hwDoorRate = 8.0,
            beadRateMode = BeadAssemblyRateMode.PER_OPENING,
            beadRate = 2.0,             // 2 проёма * 2.0 = 4.0
            rubberRateMode = RubberAssemblyRateMode.PER_M2_GLAZING,
            rubberRate = 10.0,
            floorAssemblyRate = 12.0,
            ratePerM2 = 15.0,
            ratePerMeter = 2.5
        )

        val resOpening = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            assemblyPricingCatalog = baseAssembly
        )

        val glazingArea = resOpening.glazingAreaM2 + resOpening.sandwichAreaM2
        val expectedOpsCost = 4.0 + 12.0 + 3.0 + 4.0 + 5.0 + 4.0 + glazingArea * 10.0
        assertEquals(expectedOpsCost, resOpening.costAssembly, 0.001)
        assertTrue(resOpening.unitPrimeCost > resOpening.costAssembly)

        // Переключаем штапики на ПО ШТУКАМ (PER_PIECE) и резину на ЗА М.П. (PER_METER_CONTOUR)
        val beadPieceCount = resOpening.profileCuts
            .filter { it.role == ProfileRole.SHTAPIK }
            .sumOf { it.quantity }
        val altAssembly = baseAssembly.copy(
            beadRateMode = BeadAssemblyRateMode.PER_PIECE,
            beadRate = 0.5,
            rubberRateMode = RubberAssemblyRateMode.PER_METER_CONTOUR,
            rubberRate = 1.2
        )
        val resPieceAndMeter = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            assemblyPricingCatalog = altAssembly
        )
        val expectedAltCost = 4.0 + 12.0 + 3.0 + 4.0 + 5.0 +
            beadPieceCount * 0.5 +
            resPieceAndMeter.rubberPerimeterM * 1.2
        assertEquals(expectedAltCost, resPieceAndMeter.costAssembly, 0.001)
    }

    @Test
    fun `schemes 2 and 3 compute assembly by m2 and profile perimeter meters`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()

        val asmCatalog = AssemblyPricingCatalog(
            defaultScheme = AssemblySchemeType.OPERATIONS,
            ratePerM2 = 20.0,
            ratePerMeter = 3.0
        )

        val winM2 = buildSampleTwoSashWindow(schemeOverride = AssemblySchemeType.PER_M2)
        val resM2 = WindowBomAndCostEngine.calculateWindow(
            window = winM2,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            assemblyPricingCatalog = asmCatalog
        )
        assertEquals(resM2.totalAreaM2 * 20.0, resM2.costAssembly, 0.001)

        val winMeter = buildSampleTwoSashWindow(schemeOverride = AssemblySchemeType.PER_METER)
        val resMeter = WindowBomAndCostEngine.calculateWindow(
            window = winMeter,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            assemblyPricingCatalog = asmCatalog
        )
        val totalProfilePerim = resMeter.framePerimeterM + resMeter.impostsLengthM +
            resMeter.sashPerimeterM + resMeter.doorSashPerimeterM
        assertEquals(totalProfilePerim * 3.0, resMeter.costAssembly, 0.001)
    }

    @Test
    fun `order financial summary dynamically uses catalog service price unless overridden and includes all components in prime and VAT totals`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()
        val asmCatalog = AssemblyPricingCatalog(
            defaultScheme = AssemblySchemeType.PER_M2,
            ratePerM2 = 10.0,
            floorAssemblyRate = 15.0
        )
        val serviceCatalog = listOf(
            OrderExtraServiceItem(id = "ord_srv_delivery", name = "Доставка", defaultPrice = 40.0),
            OrderExtraServiceItem(id = "ord_srv_crane", name = "Кран", defaultPrice = 120.0)
        )

        val win = buildSampleTwoSashWindow(schemeOverride = AssemblySchemeType.PER_M2).copy(quantity = 2)
        val res = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            assemblyPricingCatalog = asmCatalog
        )

        val order = CustomerOrder(
            id = "ord_fin_1",
            orderNumber = "FIN-1",
            customerName = "Тест",
            customerPhone = "",
            address = "",
            createdDate = "19.11.2026",
            windows = listOf(win),
            discountPercent = 10.0,
            taxVatPercent = 20.0,
            floorAssemblyCount = 2,
            extraAssemblyOpsCost = 25.0,
            extraAssemblyOpsNote = "Срочный крепёж",
            selectedOrderServices = listOf(
                SelectedOrderService(serviceId = "ord_srv_delivery", customPriceOverride = null),
                SelectedOrderService(serviceId = "ord_srv_crane", customPriceOverride = 150.0)
            )
        )

        val summary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = order,
            windowResults = listOf(res),
            assemblyPricingCatalog = asmCatalog,
            orderExtraServicesCatalog = serviceCatalog,
            finSchemeSettings = FinSchemeSettings(baseMarkupMultiplier = 1.0)
        )

        // Сборка на окна (2 * 22.5) + сборка на этаже (2 * 15) + доп. операции (25)
        assertEquals(res.costAssembly * 2, summary.totalWindowsAssemblyCost, 0.001)
        assertEquals(30.0, summary.floorAssemblyCost, 0.001)
        assertEquals(25.0, summary.extraAssemblyOpsCost, 0.001)
        assertEquals(res.costAssembly * 2 + 55.0, summary.totalAssemblyWithFloorAndOps, 0.001)

        // Услуги заказа: 40 (динамически из каталога) + 150 (переопределено вручную) = 190
        assertEquals(190.0, summary.totalOrderServicesCost, 0.001)

        // Себестоимость и итоги с НДС включают и сборку, и услуги заказа
        val expectedOrderExtras = 30.0 + 25.0 + 190.0
        assertEquals(res.unitPrimeCost * 2 + expectedOrderExtras, summary.orderPrimeCost, 0.001)
        val expectedSaleAfterDiscount = (res.rawUnitCostBeforeDiscount * 2 + expectedOrderExtras) * 0.90
        assertEquals(expectedSaleAfterDiscount, summary.orderSaleAfterDiscountBeforeVat, 0.001)
        assertEquals(expectedSaleAfterDiscount * 1.20, summary.totalWithVat, 0.001)

        // Если в прайсе изменилась базовая цена доставки (40 -> 60), сумма в заказе без custom override пересчитывается!
        val updatedCatalog = listOf(
            OrderExtraServiceItem(id = "ord_srv_delivery", name = "Доставка", defaultPrice = 60.0),
            OrderExtraServiceItem(id = "ord_srv_crane", name = "Кран", defaultPrice = 120.0)
        )
        val updatedSummary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = order,
            windowResults = listOf(res),
            assemblyPricingCatalog = asmCatalog,
            orderExtraServicesCatalog = updatedCatalog,
            finSchemeSettings = FinSchemeSettings(baseMarkupMultiplier = 1.0)
        )
        assertEquals(210.0, updatedSummary.totalOrderServicesCost, 0.001)
    }

    @Test
    fun `fin scheme applies 1_40 multiplier proportionally across windows and warns when discounted below prime cost`() {
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()
        val asmCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog()
        val serviceCatalog = listOf(
            OrderExtraServiceItem(id = "ord_srv_delivery", name = "Доставка", defaultPrice = 100.0)
        )

        val win = buildSampleTwoSashWindow().copy(quantity = 1)
        val res = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = sills,
            assemblyPricingCatalog = asmCatalog
        )

        val normalOrder = CustomerOrder(
            id = "ord_fin_markup",
            orderNumber = "FIN-140",
            customerName = "Тест",
            customerPhone = "",
            address = "",
            createdDate = "19.11.2026",
            windows = listOf(win),
            discountPercent = 10.0,
            taxVatPercent = 18.0,
            selectedOrderServices = listOf(SelectedOrderService("ord_srv_delivery"))
        )

        val finDefault = FinSchemeSettings() // 1.40 по умолчанию
        val summary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = normalOrder,
            windowResults = listOf(res),
            assemblyPricingCatalog = asmCatalog,
            orderExtraServicesCatalog = serviceCatalog,
            finSchemeSettings = finDefault
        )

        val expectedPrime = res.unitPrimeCost + 100.0
        val expectedBaseSale = expectedPrime * 1.40
        val expectedAfterDisc = expectedBaseSale * 0.90
        assertEquals(expectedPrime, summary.orderPrimeCost, 0.01)
        assertEquals(expectedBaseSale, summary.orderSaleBeforeDiscountAndVat, 0.01)
        assertEquals(expectedAfterDisc, summary.orderSaleAfterDiscountBeforeVat, 0.01)
        assertEquals(expectedAfterDisc * 1.18, summary.totalWithVat, 0.01)
        assertFalse(summary.isBelowPrimeCost)

        val breakdown = summary.windowBreakdownFor(win.id)!!
        assertEquals(expectedBaseSale, breakdown.unitBaseSale, 0.01)
        assertEquals(expectedAfterDisc, breakdown.unitDiscountedSale, 0.01)

        // Скидка 35% при наценке 1.40 даёт 1.40 * 0.65 = 0.91 < 1.0 -> предупреждение ниже себестоимости!
        val dumpingOrder = normalOrder.copy(discountPercent = 35.0)
        val dumpingSummary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = dumpingOrder,
            windowResults = listOf(res),
            assemblyPricingCatalog = asmCatalog,
            orderExtraServicesCatalog = serviceCatalog,
            finSchemeSettings = finDefault
        )
        assertTrue(dumpingSummary.isBelowPrimeCost)
    }
}
