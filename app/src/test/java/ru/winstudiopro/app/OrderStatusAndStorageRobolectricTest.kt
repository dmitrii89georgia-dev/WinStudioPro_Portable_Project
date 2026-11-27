package ru.winstudiopro.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `sqlite v5 database upgrades non-destructively to v6 and migrates wrk_delivery to order extra services`() {
        // Имитируем существующую пользовательскую БД версии 5 до обновления
        context.getSharedPreferences("winstudio_local_ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dev_seed_v1_applied", true)
            .commit()

        val dbFile = context.getDatabasePath("winstudio_pro_v3.db")
        dbFile.parentFile?.mkdirs()
        val legacyDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS profile_systems (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, json_payload TEXT NOT NULL)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS orders (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, order_number TEXT NOT NULL, customer_name TEXT NOT NULL, status TEXT NOT NULL, json_payload TEXT NOT NULL)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS stock_inventory (id TEXT PRIMARY KEY, profile_system_id TEXT NOT NULL, role TEXT NOT NULL, length_mm INTEGER NOT NULL, quantity INTEGER NOT NULL, is_full_bar INTEGER NOT NULL DEFAULT 0, color_name TEXT NOT NULL DEFAULT 'Белый', color_hex INTEGER NOT NULL DEFAULT 4294967295)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS stock_rubber (id TEXT PRIMARY KEY, profile_system_id TEXT NOT NULL, rubber_type TEXT NOT NULL, wedge_variant_id TEXT, wedge_thickness_mm REAL, label TEXT NOT NULL DEFAULT '', meters_in_stock REAL NOT NULL DEFAULT 0.0)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS app_settings (id INTEGER PRIMARY KEY CHECK (id = 1), json_payload TEXT NOT NULL)")

        val legacyWorkServices = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("id", "wrk_install")
                put("name", "Монтаж конструкции")
                put("rateType", "PER_M2")
                put("price", 14.0)
            })
            put(org.json.JSONObject().apply {
                put("id", "wrk_delivery")
                put("name", "Доставка")
                put("rateType", "PER_PIECE")
                put("price", 85.0)
            })
        }
        val legacySettingsJson = org.json.JSONObject().apply {
            put("currencySymbol", "$")
            put("workServices", legacyWorkServices)
        }
        legacyDb.execSQL(
            "INSERT INTO app_settings(id, json_payload) VALUES (1, ?)",
            arrayOf(legacySettingsJson.toString())
        )

        val legacyTopologyJson = org.json.JSONObject().apply {
            put("frames", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("id", "f1")
                    put("leftMm", 0.0)
                    put("topMm", 0.0)
                    put("rightMm", 1500.0)
                    put("bottomMm", 1500.0)
                    put("openings", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("id", "op1")
                            put("frameId", "f1")
                            put("leftMm", 0.0)
                            put("topMm", 0.0)
                            put("rightMm", 1500.0)
                            put("bottomMm", 1500.0)
                            put("openingType", "FIXED")
                        })
                    })
                    put("imposts", org.json.JSONArray())
                })
            })
        }

        val legacyOrderJson = org.json.JSONObject().apply {
            put("id", "legacy_ord_v5")
            put("orderNumber", "V5-100")
            put("customerName", "Клиент V5")
            put("customerPhone", "+7999")
            put("address", "ул. Базовая")
            put("createdDate", "10.11.2026")
            put("windows", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("id", "win_v5")
                    put("name", "Окно V5")
                    put("quantity", 1)
                    put("topology", legacyTopologyJson)
                    put("selectedWorkIds", org.json.JSONArray().apply {
                        put("wrk_install")
                        put("wrk_delivery")
                    })
                })
            })
        }
        legacyDb.execSQL(
            "INSERT INTO orders(id, sort_order, order_number, customer_name, status, json_payload) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("legacy_ord_v5", 0, "V5-100", "Клиент V5", "DRAFT", legacyOrderJson.toString())
        )
        legacyDb.version = 5
        legacyDb.close()

        // Загрузка состояния запускает миграцию 5 -> 6
        val migratedState = AppStateStorage.loadState(context)

        // 1. Старые данные сохранились без потерь
        val migratedOrder = migratedState.orders.firstOrNull { it.id == "legacy_ord_v5" }
        assertNotNull(migratedOrder)
        assertEquals("V5-100", migratedOrder!!.orderNumber)

        // 2. Доставка удалена из Монтажа ("Работы") и перенесена в Доп. услуги заказа с сохранением цены 85.0
        assertTrue(migratedState.workServices.none { it.id == "wrk_delivery" })
        assertTrue(migratedState.workServices.any { it.id == "wrk_install" })
        val deliveryService = migratedState.orderExtraServices.find { it.id == "ord_srv_delivery" }
        assertNotNull(deliveryService)
        assertEquals(85.0, deliveryService!!.defaultPrice, 0.001)

        // 3. В заказе wrk_delivery перенесён из окна в selectedOrderServices
        assertFalse(migratedOrder.windows.first().selectedWorkIds.contains("wrk_delivery"))
        assertTrue(migratedOrder.windows.first().selectedWorkIds.contains("wrk_install"))
        assertTrue(migratedOrder.selectedOrderServices.any { it.serviceId == "ord_srv_delivery" })
        assertEquals(
            ru.winstudiopro.app.domain.model.AssemblySchemeType.OPERATIONS,
            migratedState.assemblyPricing.defaultScheme
        )

        // 4. Проверяем round-trip новых финансовых полей через полный бэкап v4
        val enrichedState = migratedState.copy(
            assemblyPricing = migratedState.assemblyPricing.copy(
                defaultScheme = ru.winstudiopro.app.domain.model.AssemblySchemeType.PER_M2,
                ratePerM2 = 22.5
            ),
            orders = migratedState.orders.map {
                it.copy(
                    floorAssemblyCount = 3,
                    extraAssemblyOpsCost = 45.0,
                    extraAssemblyOpsNote = "Нестандартные углы"
                )
            }
        )
        AppStateStorage.saveState(context, enrichedState)
        val backupJson = AppStateStorage.exportFullBackupJsonString(context)
        val restored = AppStateStorage.importFullBackupJsonString(context, backupJson)
        assertEquals(ru.winstudiopro.app.domain.model.AssemblySchemeType.PER_M2, restored.assemblyPricing.defaultScheme)
        assertEquals(22.5, restored.assemblyPricing.ratePerM2, 0.001)
        assertEquals(3, restored.orders.first().floorAssemblyCount)
        assertEquals(45.0, restored.orders.first().extraAssemblyOpsCost, 0.001)
        assertEquals("Нестандартные углы", restored.orders.first().extraAssemblyOpsNote)
    }

    @Test
    fun `incremental upsertOrderToDb deleteOrderFromDb and emptied seeded catalogs persist reliably`() {
        val state = AppStateStorage.loadState(context)
        val initialCount = state.orders.size

        val newOrder = CustomerOrder(
            id = "inc_order_1",
            orderNumber = "INC-001",
            customerName = "Иван",
            customerPhone = "+7900",
            address = "Москва",
            createdDate = "27.11.2026",
            windows = listOf(
                state.orders.first().windows.first().copy(
                    id = "inc_win_1",
                    doorHardwareId = "hw_door_base",
                    sashHardwareId = "hw_maco",
                    transomHardwareId = "hw_transom_base"
                )
            )
        )
        AppStateStorage.upsertOrderToDb(context, newOrder, insertAtTop = true)

        var reloaded = AppStateStorage.loadState(context)
        assertEquals(initialCount + 1, reloaded.orders.size)
        assertEquals("inc_order_1", reloaded.orders.first().id)
        assertEquals("hw_door_base", reloaded.orders.first().windows.first().doorHardwareId)
        assertEquals("hw_maco", reloaded.orders.first().windows.first().sashHardwareId)
        assertEquals("hw_transom_base", reloaded.orders.first().windows.first().transomHardwareId)

        // Обновление существующего заказа сохраняет его позицию и не дублирует запись
        AppStateStorage.upsertOrderToDb(context, newOrder.copy(customerName = "Пётр"), insertAtTop = false)
        reloaded = AppStateStorage.loadState(context)
        assertEquals(initialCount + 1, reloaded.orders.size)
        assertEquals("inc_order_1", reloaded.orders.first().id)
        assertEquals("Пётр", reloaded.orders.first().customerName)

        // Удаление одного заказа оставляет остальные на месте
        AppStateStorage.deleteOrderFromDb(context, "inc_order_1")
        reloaded = AppStateStorage.loadState(context)
        assertEquals(initialCount, reloaded.orders.size)
        assertFalse(reloaded.orders.any { it.id == "inc_order_1" })

        // Полная очистка каталогов extraAccessories / workServices / orderExtraServices не должна возвращать дефолты
        AppStateStorage.saveNonOrderState(
            context,
            reloaded.copy(
                extraAccessories = emptyList(),
                workServices = emptyList(),
                orderExtraServices = emptyList()
            )
        )
        val afterClear = AppStateStorage.loadState(context)
        assertTrue(afterClear.extraAccessories.isEmpty())
        assertTrue(afterClear.workServices.isEmpty())
        assertTrue(afterClear.orderExtraServices.isEmpty())
    }
}
