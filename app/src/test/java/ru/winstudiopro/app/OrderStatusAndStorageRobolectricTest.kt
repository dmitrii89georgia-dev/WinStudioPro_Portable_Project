package ru.winstudiopro.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.DeadlineMood
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.OrderStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderStatusAndStorageRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("winstudio_pro_v3.db")
        context.getSharedPreferences("winstudio_local_ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `OrderStatus migrates legacy names and filters deadline smiley visibility`() {
        assertEquals(OrderStatus.APPROVAL, OrderStatus.fromStorageName("CONFIRMED"))
        assertEquals(OrderStatus.PRODUCTION, OrderStatus.fromStorageName("IN_WORKSHOP"))
        assertEquals(OrderStatus.READY_FOR_INSTALL, OrderStatus.fromStorageName("READY"))
        assertEquals(OrderStatus.CLOSED, OrderStatus.fromStorageName("COMPLETED"))
        assertEquals(OrderStatus.DRAFT, OrderStatus.fromStorageName("UNKNOWN_LEGACY"))

        val now = 1_794_855_326_963L
        val oneDayMs = 86_400_000L

        val greenOrder = CustomerOrder(
            id = "o1",
            orderNumber = "1",
            customerName = "",
            customerPhone = "",
            address = "",
            createdDate = "16.11.2026",
            windows = emptyList(),
            status = OrderStatus.PRODUCTION,
            contractDateMillis = now,
            contractTermDays = 20
        )
        assertEquals(DeadlineMood.HAPPY_GREEN, greenOrder.computeDeadlineState(now)?.mood)

        val yellowOrder = greenOrder.copy(contractTermDays = 5)
        assertEquals(DeadlineMood.CAUTIOUS_YELLOW, yellowOrder.computeDeadlineState(now)?.mood)

        val redOrder = greenOrder.copy(contractTermDays = 2)
        assertEquals(DeadlineMood.SCARED_RED, redOrder.computeDeadlineState(now)?.mood)

        val overdueOrder = greenOrder.copy(contractDateMillis = now - 30 * oneDayMs, contractTermDays = 28)
        assertEquals(DeadlineMood.SCARED_RED, overdueOrder.computeDeadlineState(now)?.mood)

        // На этапах Черновик, Замер, Согласование и Закрыт смайлик не отображается
        assertNull(greenOrder.copy(status = OrderStatus.DRAFT).computeDeadlineState(now))
        assertNull(greenOrder.copy(status = OrderStatus.MEASUREMENT).computeDeadlineState(now))
        assertNull(greenOrder.copy(status = OrderStatus.APPROVAL).computeDeadlineState(now))
        assertNull(greenOrder.copy(status = OrderStatus.CLOSED).computeDeadlineState(now))
    }

    @Test
    fun `AppStateStorage loads bundled dev seed backup cleanly and preserves user edits afterward`() {
        val initialState = AppStateStorage.loadState(context)

        // В dev_seed.winbackup.json ровно 16 заказов и 2 профильные системы Asas
        assertEquals(16, initialState.orders.size)
        assertTrue(initialState.profileSystems.any { it.name.contains("Asas rwt 55", ignoreCase = true) })
        assertTrue(initialState.profileSystems.any { it.name.contains("Asas rwt 64", ignoreCase = true) })

        // Проверяем, что после удаления 1 заказа и повторного loadState база НЕ сбрасывается обратно к 16
        val trimmed = initialState.copy(orders = initialState.orders.drop(1))
        AppStateStorage.saveState(context, trimmed)
        val reloaded = AppStateStorage.loadState(context)
        assertEquals(15, reloaded.orders.size)
    }

    @Test
    fun `all 16 real orders from dev seed calculate BOM and 1D cutting without errors`() {
        val state = AppStateStorage.loadState(context)
        val sills = DefaultCatalogs.defaultSills()

        state.orders.forEach { order ->
            assertTrue("Order ${order.orderNumber} must contain windows", order.windows.isNotEmpty())
            val results = order.windows.mapIndexed { idx, win ->
                val calc = WindowBomAndCostEngine.calculateWindow(
                    window = win,
                    profileSystems = state.profileSystems,
                    glazingCatalog = state.glazingCatalog,
                    colorCatalog = state.colorCatalog,
                    sillsCatalog = sills,
                    extraAccessoriesCatalog = state.extraAccessories,
                    workServicesCatalog = state.workServices,
                    hardwareBrandsCatalog = state.hardwareBrands,
                    windowIndexInOrder = idx + 1
                )
                assertTrue("Window ${win.name} in ${order.orderNumber} must have positive area", calc.totalAreaM2 > 0.0)
                assertTrue("Window ${win.name} in ${order.orderNumber} must have profile cuts", calc.profileCuts.isNotEmpty())
                calc
            }

            val (allocations, summaries) = WindowBomAndCostEngine.optimizeOrderProfileCutting(
                windowResults = results,
                stockRemnants = state.stockRemnants,
                profileSystems = state.profileSystems
            )
            assertTrue("Cutting allocations should not be empty for ${order.orderNumber}", allocations.isNotEmpty())
            assertTrue("Procurement summaries should not be empty for ${order.orderNumber}", summaries.isNotEmpty())
        }
    }

    @Test
    fun `full backup export and re-import roundtrip preserves all orders and profiles`() {
        val state = AppStateStorage.loadState(context)
        val backupJson = AppStateStorage.exportFullBackupJsonString(context)
        assertTrue(backupJson.contains("WinStudioPro"))

        val singleOrderJson = AppStateStorage.exportOrderToJsonString(state.orders.first())
        val parsedOrder = AppStateStorage.importOrderFromJsonString(singleOrderJson)
        assertEquals(state.orders.first().id, parsedOrder.id)
        assertEquals(state.orders.first().windows.size, parsedOrder.windows.size)

        val reimported = AppStateStorage.importFullBackupJsonString(context, backupJson)
        assertEquals(state.orders.size, reimported.orders.size)
        assertEquals(state.profileSystems.size, reimported.profileSystems.size)
    }

    @Test
    fun `orders status filter preferences persist independently from backup`() {
        // По умолчанию включены все 8 статусов
        assertEquals(OrderStatus.entries.toSet(), AppStateStorage.loadOrdersFilterStatuses(context))

        val subset = setOf(OrderStatus.CONTRACT, OrderStatus.PRODUCTION)
        AppStateStorage.saveOrdersFilterStatuses(context, subset)
        assertEquals(subset, AppStateStorage.loadOrdersFilterStatuses(context))

        // Пустой фильтр ("скрыть все") тоже корректно сохраняется
        AppStateStorage.saveOrdersFilterStatuses(context, emptySet())
        assertEquals(emptySet<OrderStatus>(), AppStateStorage.loadOrdersFilterStatuses(context))
        assertNotNull(AppStateStorage.loadState(context))
    }

    @Test
    fun `rubber catalog stock rubber and production deduction flag persist in sqlite and backup`() {
        val state = AppStateStorage.loadState(context)
        val firstSys = state.profileSystems.first()
        val updatedRubberCat = firstSys.rubberCatalog.copy(
            fixedGlazing = firstSys.rubberCatalog.fixedGlazing.copy(metersPerBox = 250.0, pricePerBox = 375.0),
            wedgeVariants = firstSys.rubberCatalog.wedgeVariants + ru.winstudiopro.app.domain.model.WedgeRubberVariantSpec(
                id = "w_custom_55",
                code = "01.17.99",
                name = "Клин 5.5 мм",
                thicknessMm = 5.5f,
                metersPerBox = 150.0,
                pricePerBox = 180.0
            )
        )
        val updatedProfiles = state.profileSystems.mapIndexed { idx, p ->
            if (idx == 0) p.copy(rubberCatalog = updatedRubberCat) else p
        }
        val updatedOrders = state.orders.mapIndexed { idx, o ->
            if (idx == 0) o.copy(status = OrderStatus.PRODUCTION, stockDeductedForProduction = true) else o
        }
        val customStockRubber = listOf(
            ru.winstudiopro.app.domain.model.StockRubberItem(
                id = "rub_test_1",
                profileSystemId = firstSys.id,
                rubberType = ru.winstudiopro.app.domain.calculator.RubberType.WEDGE_SHTAPIK,
                wedgeVariantId = "w_custom_55",
                wedgeThicknessMm = 5.5f,
                label = "Клин 5.5 мм",
                metersInStock = 142.75
            )
        )

        AppStateStorage.saveState(
            context,
            state.copy(
                profileSystems = updatedProfiles,
                orders = updatedOrders,
                stockRubber = customStockRubber
            )
        )

        val reloaded = AppStateStorage.loadState(context)
        assertEquals(250.0, reloaded.profileSystems.first().rubberCatalog.fixedGlazing.metersPerBox, 0.01)
        assertEquals(375.0, reloaded.profileSystems.first().rubberCatalog.fixedGlazing.pricePerBox, 0.01)
        assertTrue(reloaded.profileSystems.first().rubberCatalog.wedgeVariants.any { it.id == "w_custom_55" })
        assertTrue(reloaded.orders.first().stockDeductedForProduction)
        assertEquals(1, reloaded.stockRubber.size)
        assertEquals(142.75, reloaded.stockRubber.first().metersInStock, 0.01)

        val backupJson = AppStateStorage.exportFullBackupJsonString(context)
        val restored = AppStateStorage.importFullBackupJsonString(context, backupJson)
        assertEquals(1, restored.stockRubber.size)
        assertEquals(142.75, restored.stockRubber.first().metersInStock, 0.01)
        assertTrue(restored.orders.first().stockDeductedForProduction)
    }
}
