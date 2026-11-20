package ru.winstudiopro.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import ru.winstudiopro.app.domain.calculator.RubberType
import ru.winstudiopro.app.domain.model.*
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PersistedAppState(
    val profileSystems: List<ProfileSystemSpec>,
    val orders: List<CustomerOrder>,
    val stockRemnants: List<StockRemnantBar>,
    val stockRubber: List<StockRubberItem> = emptyList(),
    val uiSettings: AppUiSettings = AppUiSettings(),
    val glazingCatalog: List<GlazingCatalogItem> = DefaultCatalogs.defaultGlazingItems(),
    val colorCatalog: List<ColorCatalogItem> = DefaultCatalogs.defaultColors(),
    val hardwareBrands: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands(),
    val extraAccessories: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
    val workServices: List<WorkServiceItem> = DefaultCatalogs.defaultWorkServices(),
    val assemblyPricing: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
    val orderExtraServices: List<OrderExtraServiceItem> = DefaultCatalogs.defaultOrderExtraServices(),
    val finSchemeSettings: FinSchemeSettings = FinSchemeSettings(),
    val cashAccounts: List<CashAccount> = DefaultCashCatalogs.defaultCashAccounts(),
    val cashTransactions: List<CashTransaction> = emptyList(),
    val cashExpenseCategories: List<CashExpenseCategory> = DefaultCashCatalogs.defaultExpenseCategories()
)

private class WinStudioSqliteHelper(context: Context) :
    SQLiteOpenHelper(context, "winstudio_pro_v3.db", null, 7) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS profile_systems (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                json_payload TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS orders (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                order_number TEXT NOT NULL,
                customer_name TEXT NOT NULL,
                status TEXT NOT NULL,
                json_payload TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_inventory (
                id TEXT PRIMARY KEY,
                profile_system_id TEXT NOT NULL,
                role TEXT NOT NULL,
                length_mm INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                is_full_bar INTEGER NOT NULL DEFAULT 0,
                color_name TEXT NOT NULL DEFAULT 'Белый',
                color_hex INTEGER NOT NULL DEFAULT 4294967295
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_rubber (
                id TEXT PRIMARY KEY,
                profile_system_id TEXT NOT NULL,
                rubber_type TEXT NOT NULL,
                wedge_variant_id TEXT,
                wedge_thickness_mm REAL,
                label TEXT NOT NULL DEFAULT '',
                meters_in_stock REAL NOT NULL DEFAULT 0.0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                json_payload TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS assembly_presets (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                json_payload TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS order_extra_services (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                name TEXT NOT NULL,
                default_price REAL NOT NULL,
                apply_to_whole_order INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_accounts (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                name TEXT NOT NULL,
                currency_code TEXT NOT NULL,
                kind TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_expense_categories (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                name TEXT NOT NULL,
                is_system_payroll INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_transactions (
                id TEXT PRIMARY KEY,
                date_millis INTEGER NOT NULL,
                type TEXT NOT NULL,
                from_account_id TEXT,
                to_account_id TEXT,
                amount_from REAL NOT NULL,
                amount_to REAL NOT NULL,
                category_id TEXT,
                order_id TEXT,
                payroll_kind TEXT NOT NULL DEFAULT 'NONE',
                comment TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
        try {
            val cols = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(stock_inventory)", null).use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (nameIdx >= 0) cols.add(c.getString(nameIdx))
                }
            }
            if ("color_name" !in cols) {
                db.execSQL("ALTER TABLE stock_inventory ADD COLUMN color_name TEXT NOT NULL DEFAULT 'Белый'")
            }
            if ("color_hex" !in cols) {
                db.execSQL("ALTER TABLE stock_inventory ADD COLUMN color_hex INTEGER NOT NULL DEFAULT 4294967295")
            }
            if (oldVersion < 6) {
                AppStateStorage.migrateDatabaseV5ToV6(db)
            }
            if (oldVersion < 7) {
                AppStateStorage.migrateDatabaseV6ToV7(db)
            }
        } catch (e: Exception) {
            Log.e("WinStudioSqliteHelper", "Database upgrade error", e)
        }
    }
}

object AppStateStorage {

    private const val TAG = "AppStateStorage"
    private const val DEV_SEED_ASSET_NAME = "dev_seed.winbackup.json"
    private const val LOCAL_PREFS_NAME = "winstudio_local_ui_prefs"
    private const val KEY_FILTER_INITIALIZED = "orders_filter_initialized"
    private const val KEY_FILTER_STATUSES = "orders_filter_statuses"
    private const val KEY_DEV_SEED_APPLIED = "dev_seed_v1_applied"

    fun loadOrdersFilterStatuses(context: Context): Set<OrderStatus> {
        return try {
            val prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_FILTER_INITIALIZED, false)) {
                return OrderStatus.entries.toSet()
            }
            val rawSet = prefs.getStringSet(KEY_FILTER_STATUSES, null) ?: emptySet()
            rawSet.mapNotNull { name ->
                OrderStatus.entries.find { it.name == name }
            }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load orders filter statuses", e)
            OrderStatus.entries.toSet()
        }
    }

    fun saveOrdersFilterStatuses(context: Context, statuses: Set<OrderStatus>) {
        try {
            val prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_FILTER_INITIALIZED, true)
                .putStringSet(KEY_FILTER_STATUSES, statuses.map { it.name }.toSet())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save orders filter statuses", e)
        }
    }

    fun loadState(context: Context): PersistedAppState {
        return try {
            val dbHelper = WinStudioSqliteHelper(context)
            val db = dbHelper.readableDatabase

            val existingProfiles = loadProfilesFromDb(db)
            val existingOrders = loadOrdersFromDb(db)
            val existingStock = loadStockFromDb(db)
            val existingRubberStock = loadStockRubberFromDb(db)

            // Однократно подключаем тестовую базу dev_seed.winbackup.json (и при чистой установке,
            // и при обновлении тестового APK в эмуляторе, где уже лежала старая демо-база)
            val seededState = tryApplyDevSeedIfNeeded(
                context = context,
                db = dbHelper.writableDatabase,
                existingProfiles = existingProfiles,
                existingOrders = existingOrders,
                existingStock = existingStock,
                existingRubberStock = existingRubberStock
            )
            if (seededState != null) {
                return seededState
            }

            val profiles = existingProfiles.ifEmpty {
                val defaults = DefaultCatalogs.defaultProfileSystems()
                saveProfilesToDb(dbHelper.writableDatabase, defaults)
                defaults
            }

            val orders = existingOrders.ifEmpty {
                val defaults = defaultDemoOrders()
                saveOrdersToDb(dbHelper.writableDatabase, defaults)
                defaults
            }

            val settingsBundle = loadSettingsBundleFromDb(dbHelper.readableDatabase)
            if (!settingsBundle.v6CatalogsSeeded) {
                migrateDatabaseV5ToV6(dbHelper.writableDatabase)
            }
            val postV6Bundle = loadSettingsBundleFromDb(dbHelper.readableDatabase)
            if (!postV6Bundle.v7CashSeeded) {
                migrateDatabaseV6ToV7(dbHelper.writableDatabase)
            }
            val finalBundle = loadSettingsBundleFromDb(dbHelper.readableDatabase)
            val finalOrders = loadOrdersFromDb(dbHelper.readableDatabase).ifEmpty { orders }
            val assemblyPricing = loadAssemblyPricingFromDb(dbHelper.readableDatabase)
            val orderExtraServices = loadOrderExtraServicesFromDb(dbHelper.readableDatabase, fallbackToDefaults = !finalBundle.v6CatalogsSeeded)
            val cashAccounts = loadCashAccountsFromDb(dbHelper.readableDatabase)
            val cashTransactions = loadCashTransactionsFromDb(dbHelper.readableDatabase)
            val cashExpenseCategories = loadCashExpenseCategoriesFromDb(dbHelper.readableDatabase)

            PersistedAppState(
                profileSystems = profiles,
                orders = finalOrders,
                stockRemnants = existingStock,
                stockRubber = existingRubberStock,
                uiSettings = finalBundle.uiSettings,
                glazingCatalog = finalBundle.glazingCatalog,
                colorCatalog = finalBundle.colorCatalog,
                hardwareBrands = finalBundle.hardwareBrands,
                extraAccessories = finalBundle.extraAccessories,
                workServices = finalBundle.workServices,
                assemblyPricing = assemblyPricing,
                orderExtraServices = orderExtraServices,
                finSchemeSettings = finalBundle.finSchemeSettings,
                cashAccounts = cashAccounts,
                cashTransactions = cashTransactions,
                cashExpenseCategories = cashExpenseCategories
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted state, falling back to defaults", e)
            createDefaultInitialState()
        }
    }

    private fun tryApplyDevSeedIfNeeded(
        context: Context,
        db: SQLiteDatabase,
        existingProfiles: List<ProfileSystemSpec>,
        existingOrders: List<CustomerOrder>,
        existingStock: List<StockRemnantBar>,
        existingRubberStock: List<StockRubberItem>
    ): PersistedAppState? {
        val prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEV_SEED_APPLIED, false)) {
            return null
        }
        return try {
            val jsonText = context.assets.open(DEV_SEED_ASSET_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val root = JSONObject(jsonText)

            val ordArr = root.optJSONArray("orders") ?: JSONArray()
            val seededOrders = mutableListOf<CustomerOrder>()
            for (i in 0 until ordArr.length()) {
                seededOrders.add(parseOrder(ordArr.getJSONObject(i)))
            }

            val profArr = root.optJSONArray("profileSystems") ?: JSONArray()
            val seededProfiles = mutableListOf<ProfileSystemSpec>()
            for (i in 0 until profArr.length()) {
                seededProfiles.add(parseProfile(profArr.getJSONObject(i)))
            }

            val stockArr = root.optJSONArray("stockRemnants")
            val seededStock = mutableListOf<StockRemnantBar>()
            if (stockArr != null) {
                for (i in 0 until stockArr.length()) {
                    val so = stockArr.getJSONObject(i)
                    val role = try {
                        ProfileRole.valueOf(so.optString("role", "FRAME"))
                    } catch (_: Exception) {
                        ProfileRole.FRAME
                    }
                    seededStock.add(
                        StockRemnantBar(
                            id = so.optString("id", "stk_seed_$i"),
                            profileSystemId = so.optString("profileSystemId", "al_test_system"),
                            role = role,
                            lengthMm = so.optInt("lengthMm", 6000),
                            quantity = so.optInt("quantity", 1),
                            isFullStockBar = so.optBoolean("isFullStockBar", false),
                            colorName = so.optString("colorName", "Белый"),
                            colorHex = so.optLong("colorHex", 0xFFFFFFFFL)
                        )
                    )
                }
            }

            val onlyUntouchedDefaults =
                existingProfiles.all { it.id == "al_test_system" } &&
                    existingOrders.all { it.id == "order_demo_1" }

            val finalProfiles = if (onlyUntouchedDefaults) {
                seededProfiles.ifEmpty { DefaultCatalogs.defaultProfileSystems() }
            } else {
                (seededProfiles + existingProfiles).distinctBy { it.id }
                    .ifEmpty { DefaultCatalogs.defaultProfileSystems() }
            }

            val finalOrders = if (onlyUntouchedDefaults) {
                seededOrders.ifEmpty { defaultDemoOrders() }
            } else {
                (seededOrders + existingOrders.filter { it.id != "order_demo_1" })
                    .distinctBy { it.id }
                    .ifEmpty { defaultDemoOrders() }
            }

            val finalStock = if (onlyUntouchedDefaults) {
                seededStock
            } else {
                (seededStock + existingStock).distinctBy { it.id }
            }

            val rubberArr = root.optJSONArray("stockRubber")
            val seededRubber = parseStockRubberArray(rubberArr)

            val finalRubber = if (onlyUntouchedDefaults) {
                seededRubber
            } else {
                (seededRubber + existingRubberStock).distinctBy { it.id }
            }

            db.beginTransaction()
            try {
                saveProfilesToDb(db, finalProfiles)
                saveOrdersToDb(db, finalOrders)
                saveStockToDb(db, finalStock)
                saveStockRubberToDb(db, finalRubber)
                if (root.has("appSettingsPayload")) {
                    val cv = ContentValues().apply {
                        put("id", 1)
                        put("json_payload", root.getJSONObject("appSettingsPayload").toString())
                    }
                    db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                if (root.has("assemblyPricing")) {
                    saveAssemblyPricingToDb(db, parseAssemblyPricing(root.getJSONObject("assemblyPricing")))
                }
                if (root.has("orderExtraServices")) {
                    saveOrderExtraServicesToDb(db, parseOrderExtraServicesArray(root.getJSONArray("orderExtraServices")))
                }
                migrateDatabaseV5ToV6Internal(db)
                migrateDatabaseV6ToV7Internal(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            prefs.edit().putBoolean(KEY_DEV_SEED_APPLIED, true).commit()
            val settingsBundle = loadSettingsBundleFromDb(db)
            val migratedOrders = loadOrdersFromDb(db).ifEmpty { finalOrders }
            val assemblyPricing = loadAssemblyPricingFromDb(db)
            val orderExtraServices = loadOrderExtraServicesFromDb(db)
            val cashAccounts = loadCashAccountsFromDb(db)
            val cashTransactions = loadCashTransactionsFromDb(db)
            val cashExpenseCategories = loadCashExpenseCategoriesFromDb(db)
            Log.i(TAG, "Applied dev seed ($DEV_SEED_ASSET_NAME): ${migratedOrders.size} orders, ${finalProfiles.size} profiles")
            PersistedAppState(
                profileSystems = finalProfiles,
                orders = migratedOrders,
                stockRemnants = finalStock,
                stockRubber = finalRubber,
                uiSettings = settingsBundle.uiSettings,
                glazingCatalog = settingsBundle.glazingCatalog,
                colorCatalog = settingsBundle.colorCatalog,
                hardwareBrands = settingsBundle.hardwareBrands,
                extraAccessories = settingsBundle.extraAccessories,
                workServices = settingsBundle.workServices,
                assemblyPricing = assemblyPricing,
                orderExtraServices = orderExtraServices,
                finSchemeSettings = settingsBundle.finSchemeSettings,
                cashAccounts = cashAccounts,
                cashTransactions = cashTransactions,
                cashExpenseCategories = cashExpenseCategories
            )
        } catch (_: FileNotFoundException) {
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply dev seed asset $DEV_SEED_ASSET_NAME", e)
            null
        }
    }

    fun saveState(context: Context, state: PersistedAppState) {
        try {
            val dbHelper = WinStudioSqliteHelper(context)
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                saveProfilesToDb(db, state.profileSystems)
                saveOrdersToDb(db, state.orders)
                saveStockToDb(db, state.stockRemnants)
                saveStockRubberToDb(db, state.stockRubber)
                saveAssemblyPricingToDb(db, state.assemblyPricing)
                saveOrderExtraServicesToDb(db, state.orderExtraServices)
                saveCashAccountsToDb(db, state.cashAccounts)
                saveCashTransactionsToDb(db, state.cashTransactions)
                saveCashExpenseCategoriesToDb(db, state.cashExpenseCategories)
                saveSettingsBundleToDb(
                    db,
                    SettingsAndCatalogsBundle(
                        uiSettings = state.uiSettings,
                        glazingCatalog = state.glazingCatalog,
                        colorCatalog = state.colorCatalog,
                        hardwareBrands = state.hardwareBrands,
                        extraAccessories = state.extraAccessories,
                        workServices = state.workServices,
                        v6CatalogsSeeded = true,
                        finSchemeSettings = state.finSchemeSettings,
                        v7CashSeeded = true
                    )
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state to SQLite", e)
        }
    }

    // Экспорт одного заказа в форматированную строку JSON
    fun exportOrderToJsonString(order: CustomerOrder): String {
        return serializeOrder(order).toString(2)
    }

    // Импорт заказа из строки JSON
    fun importOrderFromJsonString(jsonString: String): CustomerOrder {
        val json = JSONObject(jsonString)
        val orderObj = if (json.has("orderNumber") && json.has("windows")) {
            json
        } else if (json.has("order")) {
            json.getJSONObject("order")
        } else {
            json
        }
        return parseOrder(orderObj)
    }

    // Создание временного файла .win.json для отправки через WhatsApp, Telegram, диск или почту
    fun createOrderJsonExportFile(context: Context, order: CustomerOrder): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeNum = order.orderNumber.replace(Regex("[^a-zA-Z0-9А-Яа-я_\\-]"), "_")
        val file = File(exportDir, "Заказ_${safeNum}.win.json")
        file.writeText(exportOrderToJsonString(order), Charsets.UTF_8)
        return file
    }

    // Полный экспорт всей базы (все системы, прайсы, склад, настройки и все заказы)
    fun exportFullBackupJsonString(context: Context): String {
        val dbHelper = WinStudioSqliteHelper(context)
        val db = dbHelper.readableDatabase
        val profiles = loadProfilesFromDb(db)
        val orders = loadOrdersFromDb(db)
        val stock = loadStockFromDb(db)
        val stockRubber = loadStockRubberFromDb(db)
        val assemblyPricing = loadAssemblyPricingFromDb(db)
        val orderExtraServices = loadOrderExtraServicesFromDb(db)
        val settingsBundle = loadSettingsBundleFromDb(db)
        val cashAccounts = loadCashAccountsFromDb(db)
        val cashTransactions = loadCashTransactionsFromDb(db)
        val cashExpenseCategories = loadCashExpenseCategoriesFromDb(db)

        val root = JSONObject().apply {
            put("app", "WinStudioPro")
            put("backupVersion", 5)
            put("timestamp", System.currentTimeMillis())
            put("exportDate", SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date()))

            val ordArr = JSONArray()
            orders.forEach { ordArr.put(serializeOrder(it)) }
            put("orders", ordArr)

            val profArr = JSONArray()
            profiles.forEach { profArr.put(serializeProfile(it)) }
            put("profileSystems", profArr)

            val stockArr = JSONArray()
            stock.forEach { r ->
                stockArr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("profileSystemId", r.profileSystemId)
                        put("role", r.role.name)
                        put("lengthMm", r.lengthMm)
                        put("quantity", r.quantity)
                        put("isFullStockBar", r.isFullStockBar)
                        put("colorName", r.colorName)
                        put("colorHex", r.colorHex)
                    }
                )
            }
            put("stockRemnants", stockArr)
            put("stockRubber", serializeStockRubberArray(stockRubber))
            put("assemblyPricing", serializeAssemblyPricing(assemblyPricing))
            put("orderExtraServices", serializeOrderExtraServicesArray(orderExtraServices))
            put("finSchemeSettings", serializeFinSchemeSettings(settingsBundle.finSchemeSettings))
            put("cashAccounts", serializeCashAccountsArray(cashAccounts))
            put("cashTransactions", serializeCashTransactionsArray(cashTransactions))
            put("cashExpenseCategories", serializeCashExpenseCategoriesArray(cashExpenseCategories))
        }

        db.rawQuery("SELECT json_payload FROM app_settings WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) {
                root.put("appSettingsPayload", JSONObject(c.getString(0)))
            }
        }
        return root.toString(2)
    }

    // Создание файла полного бэкапа базы данных
    fun createFullBackupFile(context: Context): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "WinStudio_Бэкап_${timeTag}.winbackup.json")
        file.writeText(exportFullBackupJsonString(context), Charsets.UTF_8)
        return file
    }

    // Восстановление всей базы данных из файла полного бэкапа
    fun importFullBackupJsonString(context: Context, jsonString: String): PersistedAppState {
        val root = JSONObject(jsonString)
        val current = loadState(context)

        val ordArr = root.optJSONArray("orders") ?: JSONArray()
        val importedOrders = mutableListOf<CustomerOrder>()
        for (i in 0 until ordArr.length()) {
            importedOrders.add(parseOrder(ordArr.getJSONObject(i)))
        }

        val profArr = root.optJSONArray("profileSystems") ?: JSONArray()
        val importedProfiles = mutableListOf<ProfileSystemSpec>()
        for (i in 0 until profArr.length()) {
            importedProfiles.add(parseProfile(profArr.getJSONObject(i)))
        }

        val stockArr = root.optJSONArray("stockRemnants")
        val importedStock = mutableListOf<StockRemnantBar>()
        if (stockArr != null) {
            for (i in 0 until stockArr.length()) {
                val so = stockArr.getJSONObject(i)
                val role = try {
                    ProfileRole.valueOf(so.optString("role", "FRAME"))
                } catch (_: Exception) {
                    ProfileRole.FRAME
                }
                importedStock.add(
                    StockRemnantBar(
                        id = so.optString("id", "stk_imp_$i"),
                        profileSystemId = so.optString("profileSystemId", "al_test_system"),
                        role = role,
                        lengthMm = so.optInt("lengthMm", 6000),
                        quantity = so.optInt("quantity", 1),
                        isFullStockBar = so.optBoolean("isFullStockBar", false),
                        colorName = so.optString("colorName", "Белый"),
                        colorHex = so.optLong("colorHex", 0xFFFFFFFFL)
                    )
                )
            }
        }

        val dbHelper = WinStudioSqliteHelper(context)
        val db = dbHelper.writableDatabase
        if (root.has("appSettingsPayload")) {
            val cv = ContentValues().apply {
                put("id", 1)
                put("json_payload", root.getJSONObject("appSettingsPayload").toString())
            }
            db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        val rubberArr = root.optJSONArray("stockRubber")
        val importedRubber = parseStockRubberArray(rubberArr)

        val importedAssembly = if (root.has("assemblyPricing") && !root.isNull("assemblyPricing")) {
            parseAssemblyPricing(root.getJSONObject("assemblyPricing"))
        } else {
            current.assemblyPricing
        }
        val importedOrderServices = if (root.has("orderExtraServices") && !root.isNull("orderExtraServices")) {
            parseOrderExtraServicesArray(root.getJSONArray("orderExtraServices"))
        } else {
            current.orderExtraServices
        }
        val importedFinScheme = if (root.has("finSchemeSettings") && !root.isNull("finSchemeSettings")) {
            parseFinSchemeSettings(root.getJSONObject("finSchemeSettings"))
        } else {
            current.finSchemeSettings
        }
        val importedCashAccounts = if (root.has("cashAccounts") && !root.isNull("cashAccounts")) {
            parseCashAccountsArray(root.getJSONArray("cashAccounts")).ifEmpty { current.cashAccounts }
        } else {
            current.cashAccounts
        }
        val importedCashTransactions = if (root.has("cashTransactions") && !root.isNull("cashTransactions")) {
            parseCashTransactionsArray(root.getJSONArray("cashTransactions"))
        } else {
            current.cashTransactions
        }
        val importedExpenseCategories = if (root.has("cashExpenseCategories") && !root.isNull("cashExpenseCategories")) {
            parseCashExpenseCategoriesArray(root.getJSONArray("cashExpenseCategories")).ifEmpty { current.cashExpenseCategories }
        } else {
            current.cashExpenseCategories
        }

        val normalizedImportedOrders = importedOrders.map { migrateLegacyDeliveryOnOrder(it) }
        val mergedOrders = (normalizedImportedOrders + current.orders).distinctBy { it.id }
        val mergedProfiles = (importedProfiles + current.profileSystems).distinctBy { it.id }
        val mergedStock = if (stockArr != null) {
            (importedStock + current.stockRemnants).distinctBy { it.id }
        } else {
            current.stockRemnants
        }
        val mergedRubber = if (rubberArr != null) {
            (importedRubber + current.stockRubber).distinctBy { it.id }
        } else {
            current.stockRubber
        }
        val mergedCashTransactions = (importedCashTransactions + current.cashTransactions).distinctBy { it.id }
            .sortedByDescending { it.dateMillis }

        val reloadedSettings = loadState(context)
        val cleanedWorkServices = reloadedSettings.workServices.filterNot {
            it.id == "wrk_delivery" || it.name.trim().equals("доставка", ignoreCase = true)
        }

        val newState = reloadedSettings.copy(
            orders = mergedOrders.ifEmpty { current.orders },
            profileSystems = mergedProfiles.ifEmpty { current.profileSystems },
            stockRemnants = mergedStock,
            stockRubber = mergedRubber,
            workServices = cleanedWorkServices,
            assemblyPricing = importedAssembly,
            orderExtraServices = importedOrderServices,
            finSchemeSettings = if (root.has("finSchemeSettings")) importedFinScheme else reloadedSettings.finSchemeSettings,
            cashAccounts = importedCashAccounts,
            cashTransactions = mergedCashTransactions,
            cashExpenseCategories = importedExpenseCategories
        )
        saveState(context, newState)
        return newState
    }

    fun createDefaultInitialState(): PersistedAppState {
        return PersistedAppState(
            profileSystems = DefaultCatalogs.defaultProfileSystems(),
            orders = defaultDemoOrders(),
            stockRemnants = listOf(
                StockRemnantBar(
                    id = "stk_full_1",
                    profileSystemId = "al_test_system",
                    role = ProfileRole.FRAME,
                    lengthMm = 6000,
                    quantity = 12,
                    isFullStockBar = true,
                    colorName = "Антрацит",
                    colorHex = 0xFF383E42
                ),
                StockRemnantBar(
                    id = "stk_full_2",
                    profileSystemId = "al_test_system",
                    role = ProfileRole.IMPOST,
                    lengthMm = 6000,
                    quantity = 8,
                    isFullStockBar = true,
                    colorName = "Антрацит",
                    colorHex = 0xFF383E42
                ),
                StockRemnantBar(
                    id = "stk_rem_1",
                    profileSystemId = "al_test_system",
                    role = ProfileRole.FRAME,
                    lengthMm = 2150,
                    quantity = 2,
                    isFullStockBar = false,
                    colorName = "Антрацит",
                    colorHex = 0xFF383E42
                )
            ),
            uiSettings = AppUiSettings()
        )
    }

    private fun defaultDemoOrders(): List<CustomerOrder> {
        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val w1 = WindowCadOperations.single1500Topology()
        return listOf(
            CustomerOrder(
                id = "order_demo_1",
                orderNumber = "Заказ №$nowStr",
                customerName = "",
                customerPhone = "",
                address = "",
                createdDate = nowStr,
                windows = listOf(
                    WindowItem(
                        id = "win_item_1",
                        name = "Окно 1",
                        quantity = 1,
                        topology = w1,
                        profileSystemId = "al_test_system",
                        glazingId = "sp_24",
                        colorId = "col_anthracite"
                    )
                ),
                status = OrderStatus.DRAFT,
                discountPercent = 0.0,
                taxVatPercent = 18.0,
                currencySymbol = "доллар"
            )
        )
    }

    private fun saveProfilesToDb(db: SQLiteDatabase, list: List<ProfileSystemSpec>) {
        db.delete("profile_systems", null, null)
        list.forEachIndexed { idx, p ->
            val cv = ContentValues().apply {
                put("id", p.id)
                put("sort_order", idx)
                put("json_payload", serializeProfile(p).toString())
            }
            db.insertWithOnConflict("profile_systems", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadProfilesFromDb(db: SQLiteDatabase): List<ProfileSystemSpec> {
        val res = mutableListOf<ProfileSystemSpec>()
        db.rawQuery("SELECT json_payload FROM profile_systems ORDER BY sort_order ASC", null).use { c ->
            while (c.moveToNext()) {
                res.add(parseProfile(JSONObject(c.getString(0))))
            }
        }
        return res
    }

    private fun saveOrdersToDb(db: SQLiteDatabase, orders: List<CustomerOrder>) {
        db.delete("orders", null, null)
        orders.forEachIndexed { idx, ord ->
            val cv = ContentValues().apply {
                put("id", ord.id)
                put("sort_order", idx)
                put("order_number", ord.orderNumber)
                put("customer_name", ord.customerName)
                put("status", ord.status.name)
                put("json_payload", serializeOrder(ord).toString())
            }
            db.insertWithOnConflict("orders", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadOrdersFromDb(db: SQLiteDatabase): List<CustomerOrder> {
        val res = mutableListOf<CustomerOrder>()
        db.rawQuery("SELECT json_payload FROM orders ORDER BY sort_order ASC", null).use { c ->
            while (c.moveToNext()) {
                res.add(parseOrder(JSONObject(c.getString(0))))
            }
        }
        return res
    }

    private fun saveStockToDb(db: SQLiteDatabase, list: List<StockRemnantBar>) {
        db.delete("stock_inventory", null, null)
        list.forEach { r ->
            val cv = ContentValues().apply {
                put("id", r.id)
                put("profile_system_id", r.profileSystemId)
                put("role", r.role.name)
                put("length_mm", r.lengthMm)
                put("quantity", r.quantity)
                put("is_full_bar", if (r.isFullStockBar) 1 else 0)
                put("color_name", r.colorName)
                put("color_hex", r.colorHex)
            }
            db.insertWithOnConflict("stock_inventory", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadStockFromDb(db: SQLiteDatabase): List<StockRemnantBar> {
        val res = mutableListOf<StockRemnantBar>()
        db.rawQuery(
            "SELECT id, profile_system_id, role, length_mm, quantity, is_full_bar, color_name, color_hex FROM stock_inventory",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val roleStr = c.getString(2)
                val role = try {
                    ProfileRole.valueOf(roleStr)
                } catch (_: Exception) {
                    ProfileRole.FRAME
                }
                val cName = if (c.isNull(6)) "Белый" else c.getString(6).ifBlank { "Белый" }
                val cHex = if (c.isNull(7)) 0xFFFFFFFFL else c.getLong(7)
                res.add(
                    StockRemnantBar(
                        id = c.getString(0),
                        profileSystemId = c.getString(1),
                        role = role,
                        lengthMm = c.getInt(3),
                        quantity = c.getInt(4),
                        isFullStockBar = c.getInt(5) == 1,
                        colorName = cName,
                        colorHex = cHex
                    )
                )
            }
        }
        return res
    }

    private fun saveStockRubberToDb(db: SQLiteDatabase, list: List<StockRubberItem>) {
        db.delete("stock_rubber", null, null)
        list.forEach { item ->
            val cv = ContentValues().apply {
                put("id", item.id)
                put("profile_system_id", item.profileSystemId)
                put("rubber_type", item.rubberType.name)
                if (item.wedgeVariantId != null) put("wedge_variant_id", item.wedgeVariantId) else putNull("wedge_variant_id")
                if (item.wedgeThicknessMm != null) put("wedge_thickness_mm", item.wedgeThicknessMm.toDouble()) else putNull("wedge_thickness_mm")
                put("label", item.label)
                put("meters_in_stock", item.metersInStock)
            }
            db.insertWithOnConflict("stock_rubber", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadStockRubberFromDb(db: SQLiteDatabase): List<StockRubberItem> {
        val res = mutableListOf<StockRubberItem>()
        db.rawQuery(
            "SELECT id, profile_system_id, rubber_type, wedge_variant_id, wedge_thickness_mm, label, meters_in_stock FROM stock_rubber",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val rType = try {
                    RubberType.valueOf(c.getString(2))
                } catch (_: Exception) {
                    RubberType.FIXED_GLAZING
                }
                res.add(
                    StockRubberItem(
                        id = c.getString(0),
                        profileSystemId = c.getString(1),
                        rubberType = rType,
                        wedgeVariantId = if (c.isNull(3)) null else c.getString(3),
                        wedgeThicknessMm = if (c.isNull(4)) null else c.getDouble(4).toFloat(),
                        label = if (c.isNull(5)) "" else c.getString(5),
                        metersInStock = c.getDouble(6).coerceAtLeast(0.0)
                    )
                )
            }
        }
        return res
    }

    private fun serializeStockRubberArray(list: List<StockRubberItem>): JSONArray {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("profileSystemId", r.profileSystemId)
                    put("rubberType", r.rubberType.name)
                    put("wedgeVariantId", r.wedgeVariantId ?: JSONObject.NULL)
                    put("wedgeThicknessMm", r.wedgeThicknessMm?.toDouble() ?: JSONObject.NULL)
                    put("label", r.label)
                    put("metersInStock", r.metersInStock)
                }
            )
        }
        return arr
    }

    private fun parseStockRubberArray(arr: JSONArray?): List<StockRubberItem> {
        if (arr == null) return emptyList()
        val list = mutableListOf<StockRubberItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val rType = try {
                RubberType.valueOf(o.optString("rubberType", "FIXED_GLAZING"))
            } catch (_: Exception) {
                RubberType.FIXED_GLAZING
            }
            list.add(
                StockRubberItem(
                    id = o.optString("id", "rub_imp_$i"),
                    profileSystemId = o.optString("profileSystemId", "al_test_system"),
                    rubberType = rType,
                    wedgeVariantId = if (o.isNull("wedgeVariantId")) null else o.optString("wedgeVariantId").takeIf { it.isNotBlank() },
                    wedgeThicknessMm = if (o.isNull("wedgeThicknessMm")) null else o.optDouble("wedgeThicknessMm").toFloat(),
                    label = o.optString("label", ""),
                    metersInStock = o.optDouble("metersInStock", 0.0).coerceAtLeast(0.0)
                )
            )
        }
        return list
    }

    internal fun migrateDatabaseV5ToV6(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            migrateDatabaseV5ToV6Internal(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    internal fun migrateDatabaseV6ToV7(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            migrateDatabaseV6ToV7Internal(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun migrateDatabaseV6ToV7Internal(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                json_payload TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_accounts (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                name TEXT NOT NULL,
                currency_code TEXT NOT NULL,
                kind TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_expense_categories (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                name TEXT NOT NULL,
                is_system_payroll INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_transactions (
                id TEXT PRIMARY KEY,
                date_millis INTEGER NOT NULL,
                type TEXT NOT NULL,
                from_account_id TEXT,
                to_account_id TEXT,
                amount_from REAL NOT NULL,
                amount_to REAL NOT NULL,
                category_id TEXT,
                order_id TEXT,
                payroll_kind TEXT NOT NULL DEFAULT 'NONE',
                comment TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        val hasCashAccounts = db.rawQuery("SELECT COUNT(*) FROM cash_accounts", null).use { c ->
            c.moveToFirst() && c.getInt(0) > 0
        }
        if (!hasCashAccounts) {
            saveCashAccountsToDb(db, DefaultCashCatalogs.defaultCashAccounts())
        }

        val hasExpenseCategories = db.rawQuery("SELECT COUNT(*) FROM cash_expense_categories", null).use { c ->
            c.moveToFirst() && c.getInt(0) > 0
        }
        if (!hasExpenseCategories) {
            saveCashExpenseCategoriesToDb(db, DefaultCashCatalogs.defaultExpenseCategories())
        }

        val currentBundle = loadSettingsBundleFromDb(db)
        if (!currentBundle.v7CashSeeded) {
            saveSettingsBundleToDb(
                db,
                currentBundle.copy(
                    finSchemeSettings = FinSchemeSettings(),
                    v7CashSeeded = true
                )
            )
        }
    }

    private fun migrateDatabaseV5ToV6Internal(db: SQLiteDatabase) {
        val currentBundle = loadSettingsBundleFromDb(db)
        var deliveryMigratedPrice: Double? = null
        val filteredWorks = currentBundle.workServices.filter { w ->
            val isDelivery = w.id == "wrk_delivery" || w.name.trim().equals("доставка", ignoreCase = true)
            if (isDelivery) {
                deliveryMigratedPrice = w.price
            }
            !isDelivery
        }

        val updatedBundle = currentBundle.copy(
            workServices = filteredWorks,
            v6CatalogsSeeded = true
        )
        saveSettingsBundleToDb(db, updatedBundle)

        // Засеиваем пресеты сборки Схема 1 по умолчанию, если таблица пуста
        val hasAssemblyPreset = db.rawQuery("SELECT COUNT(*) FROM assembly_presets WHERE id = 1", null).use { c ->
            c.moveToFirst() && c.getInt(0) > 0
        }
        if (!hasAssemblyPreset) {
            saveAssemblyPricingToDb(db, DefaultCatalogs.defaultAssemblyPricingCatalog())
        }

        // Засеиваем каталог доп. услуг заказа, если таблица пуста (с сохранением старой цены доставки)
        val hasOrderServices = db.rawQuery("SELECT COUNT(*) FROM order_extra_services", null).use { c ->
            c.moveToFirst() && c.getInt(0) > 0
        }
        if (!hasOrderServices) {
            val defaults = DefaultCatalogs.defaultOrderExtraServices().map { srv ->
                if (srv.id == "ord_srv_delivery" && deliveryMigratedPrice != null) {
                    srv.copy(defaultPrice = deliveryMigratedPrice!!)
                } else {
                    srv
                }
            }
            saveOrderExtraServicesToDb(db, defaults)
        }

        // Переносим отметку доставки (wrk_delivery) из окон в доп. услуги заказа без потери данных
        val existingOrders = loadOrdersFromDb(db)
        if (existingOrders.isNotEmpty()) {
            val migratedOrders = existingOrders.map { migrateLegacyDeliveryOnOrder(it) }
            if (migratedOrders != existingOrders) {
                saveOrdersToDb(db, migratedOrders)
            }
        }
    }

    private fun migrateLegacyDeliveryOnOrder(order: CustomerOrder): CustomerOrder {
        var hadDelivery = false
        val updatedWindows = order.windows.map { w ->
            if (w.selectedWorkIds.contains("wrk_delivery")) {
                hadDelivery = true
                w.copy(selectedWorkIds = w.selectedWorkIds.filterNot { it == "wrk_delivery" })
            } else {
                w
            }
        }
        if (!hadDelivery) return order
        val updatedServices = if (order.selectedOrderServices.any { it.serviceId == "ord_srv_delivery" }) {
            order.selectedOrderServices
        } else {
            order.selectedOrderServices + SelectedOrderService("ord_srv_delivery", null)
        }
        return order.copy(windows = updatedWindows, selectedOrderServices = updatedServices)
    }

    private fun saveAssemblyPricingToDb(db: SQLiteDatabase, pricing: AssemblyPricingCatalog) {
        val cv = ContentValues().apply {
            put("id", 1)
            put("json_payload", serializeAssemblyPricing(pricing).toString())
        }
        db.insertWithOnConflict("assembly_presets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun loadAssemblyPricingFromDb(db: SQLiteDatabase): AssemblyPricingCatalog {
        return try {
            db.rawQuery("SELECT json_payload FROM assembly_presets WHERE id = 1", null).use { c ->
                if (c.moveToFirst()) {
                    parseAssemblyPricing(JSONObject(c.getString(0)))
                } else {
                    DefaultCatalogs.defaultAssemblyPricingCatalog()
                }
            }
        } catch (_: Exception) {
            DefaultCatalogs.defaultAssemblyPricingCatalog()
        }
    }

    private fun saveOrderExtraServicesToDb(db: SQLiteDatabase, list: List<OrderExtraServiceItem>) {
        db.delete("order_extra_services", null, null)
        list.forEachIndexed { idx, srv ->
            val cv = ContentValues().apply {
                put("id", srv.id)
                put("sort_order", idx)
                put("name", srv.name)
                put("default_price", srv.defaultPrice)
                put("apply_to_whole_order", if (srv.applyToWholeOrder) 1 else 0)
            }
            db.insertWithOnConflict("order_extra_services", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadOrderExtraServicesFromDb(
        db: SQLiteDatabase,
        fallbackToDefaults: Boolean = true
    ): List<OrderExtraServiceItem> {
        return try {
            val list = mutableListOf<OrderExtraServiceItem>()
            db.rawQuery(
                "SELECT id, name, default_price, apply_to_whole_order FROM order_extra_services ORDER BY sort_order ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        OrderExtraServiceItem(
                            id = c.getString(0),
                            name = c.getString(1),
                            defaultPrice = c.getDouble(2),
                            applyToWholeOrder = c.getInt(3) == 1
                        )
                    )
                }
            }
            if (list.isEmpty() && fallbackToDefaults) {
                DefaultCatalogs.defaultOrderExtraServices()
            } else {
                list
            }
        } catch (_: Exception) {
            DefaultCatalogs.defaultOrderExtraServices()
        }
    }

    private fun serializeAssemblyPricing(a: AssemblyPricingCatalog): JSONObject = JSONObject().apply {
        put("defaultScheme", a.defaultScheme.name)
        put("frameCornerRate", a.frameCornerRate)
        put("sashCornerRate", a.sashCornerRate)
        put("doorCornerRate", a.doorCornerRate)
        put("impostRate", a.impostRate)
        put("hwTurnRate", a.hwTurnRate)
        put("hwTiltTurnRate", a.hwTiltTurnRate)
        put("hwShtulpRate", a.hwShtulpRate)
        put("hwSlidingRate", a.hwSlidingRate)
        put("hwDoorRate", a.hwDoorRate)
        put("beadRateMode", a.beadRateMode.name)
        put("beadRate", a.beadRate)
        put("rubberRateMode", a.rubberRateMode.name)
        put("rubberRate", a.rubberRate)
        put("floorAssemblyRate", a.floorAssemblyRate)
        put("ratePerM2", a.ratePerM2)
        put("ratePerMeter", a.ratePerMeter)
    }

    private fun parseAssemblyPricing(o: JSONObject): AssemblyPricingCatalog {
        val def = DefaultCatalogs.defaultAssemblyPricingCatalog()
        val scheme = try {
            AssemblySchemeType.valueOf(o.optString("defaultScheme", def.defaultScheme.name))
        } catch (_: Exception) {
            def.defaultScheme
        }
        val beadMode = try {
            BeadAssemblyRateMode.valueOf(o.optString("beadRateMode", def.beadRateMode.name))
        } catch (_: Exception) {
            def.beadRateMode
        }
        val rubberMode = try {
            RubberAssemblyRateMode.valueOf(o.optString("rubberRateMode", def.rubberRateMode.name))
        } catch (_: Exception) {
            def.rubberRateMode
        }
        return AssemblyPricingCatalog(
            defaultScheme = scheme,
            frameCornerRate = o.optDouble("frameCornerRate", def.frameCornerRate),
            sashCornerRate = o.optDouble("sashCornerRate", def.sashCornerRate),
            doorCornerRate = o.optDouble("doorCornerRate", def.doorCornerRate),
            impostRate = o.optDouble("impostRate", def.impostRate),
            hwTurnRate = o.optDouble("hwTurnRate", def.hwTurnRate),
            hwTiltTurnRate = o.optDouble("hwTiltTurnRate", def.hwTiltTurnRate),
            hwShtulpRate = o.optDouble("hwShtulpRate", def.hwShtulpRate),
            hwSlidingRate = o.optDouble("hwSlidingRate", def.hwSlidingRate),
            hwDoorRate = o.optDouble("hwDoorRate", def.hwDoorRate),
            beadRateMode = beadMode,
            beadRate = o.optDouble("beadRate", def.beadRate),
            rubberRateMode = rubberMode,
            rubberRate = o.optDouble("rubberRate", def.rubberRate),
            floorAssemblyRate = o.optDouble("floorAssemblyRate", def.floorAssemblyRate),
            ratePerM2 = o.optDouble("ratePerM2", def.ratePerM2),
            ratePerMeter = o.optDouble("ratePerMeter", def.ratePerMeter)
        )
    }

    private fun serializeOrderExtraServicesArray(list: List<OrderExtraServiceItem>): JSONArray {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("defaultPrice", s.defaultPrice)
                    put("applyToWholeOrder", s.applyToWholeOrder)
                }
            )
        }
        return arr
    }

    private fun parseOrderExtraServicesArray(arr: JSONArray?): List<OrderExtraServiceItem> {
        if (arr == null) return DefaultCatalogs.defaultOrderExtraServices()
        val list = mutableListOf<OrderExtraServiceItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                OrderExtraServiceItem(
                    id = o.optString("id", "ord_srv_$i"),
                    name = o.optString("name", "Услуга"),
                    defaultPrice = o.optDouble("defaultPrice", 0.0),
                    applyToWholeOrder = o.optBoolean("applyToWholeOrder", true)
                )
            )
        }
        return list
    }

    private fun saveCashAccountsToDb(db: SQLiteDatabase, list: List<CashAccount>) {
        db.delete("cash_accounts", null, null)
        list.forEachIndexed { idx, acc ->
            val cv = ContentValues().apply {
                put("id", acc.id)
                put("sort_order", idx)
                put("name", acc.name)
                put("currency_code", acc.currency.code)
                put("kind", acc.kind.name)
            }
            db.insertWithOnConflict("cash_accounts", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadCashAccountsFromDb(db: SQLiteDatabase): List<CashAccount> {
        return try {
            val list = mutableListOf<CashAccount>()
            db.rawQuery(
                "SELECT id, sort_order, name, currency_code, kind FROM cash_accounts ORDER BY sort_order ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        CashAccount(
                            id = c.getString(0),
                            sortOrder = c.getInt(1),
                            name = c.getString(2),
                            currency = CashCurrency.fromCode(c.getString(3)),
                            kind = CashAccountKind.fromStorage(c.getString(4))
                        )
                    )
                }
            }
            list.ifEmpty { DefaultCashCatalogs.defaultCashAccounts() }
        } catch (_: Exception) {
            DefaultCashCatalogs.defaultCashAccounts()
        }
    }

    private fun saveCashExpenseCategoriesToDb(db: SQLiteDatabase, list: List<CashExpenseCategory>) {
        db.delete("cash_expense_categories", null, null)
        list.forEachIndexed { idx, cat ->
            val cv = ContentValues().apply {
                put("id", cat.id)
                put("sort_order", idx)
                put("name", cat.name)
                put("is_system_payroll", if (cat.isSystemPayroll) 1 else 0)
            }
            db.insertWithOnConflict("cash_expense_categories", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadCashExpenseCategoriesFromDb(db: SQLiteDatabase): List<CashExpenseCategory> {
        return try {
            val list = mutableListOf<CashExpenseCategory>()
            db.rawQuery(
                "SELECT id, sort_order, name, is_system_payroll FROM cash_expense_categories ORDER BY sort_order ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        CashExpenseCategory(
                            id = c.getString(0),
                            sortOrder = c.getInt(1),
                            name = c.getString(2),
                            isSystemPayroll = c.getInt(3) == 1
                        )
                    )
                }
            }
            list.ifEmpty { DefaultCashCatalogs.defaultExpenseCategories() }
        } catch (_: Exception) {
            DefaultCashCatalogs.defaultExpenseCategories()
        }
    }

    private fun saveCashTransactionsToDb(db: SQLiteDatabase, list: List<CashTransaction>) {
        db.delete("cash_transactions", null, null)
        list.forEach { tx ->
            val cv = ContentValues().apply {
                put("id", tx.id)
                put("date_millis", tx.dateMillis)
                put("type", tx.type.name)
                if (tx.fromAccountId != null) put("from_account_id", tx.fromAccountId) else putNull("from_account_id")
                if (tx.toAccountId != null) put("to_account_id", tx.toAccountId) else putNull("to_account_id")
                put("amount_from", tx.amountFrom)
                put("amount_to", tx.amountTo)
                if (tx.categoryId != null) put("category_id", tx.categoryId) else putNull("category_id")
                if (tx.orderId != null) put("order_id", tx.orderId) else putNull("order_id")
                put("payroll_kind", tx.payrollKind.name)
                put("comment", tx.comment)
            }
            db.insertWithOnConflict("cash_transactions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadCashTransactionsFromDb(db: SQLiteDatabase): List<CashTransaction> {
        return try {
            val list = mutableListOf<CashTransaction>()
            db.rawQuery(
                "SELECT id, date_millis, type, from_account_id, to_account_id, amount_from, amount_to, category_id, order_id, payroll_kind, comment FROM cash_transactions ORDER BY date_millis DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        CashTransaction(
                            id = c.getString(0),
                            dateMillis = c.getLong(1),
                            type = CashTransactionType.fromStorage(c.getString(2)),
                            fromAccountId = if (c.isNull(3)) null else c.getString(3),
                            toAccountId = if (c.isNull(4)) null else c.getString(4),
                            amountFrom = c.getDouble(5),
                            amountTo = c.getDouble(6),
                            categoryId = if (c.isNull(7)) null else c.getString(7),
                            orderId = if (c.isNull(8)) null else c.getString(8),
                            payrollKind = CashPayrollKind.fromStorage(c.getString(9)),
                            comment = if (c.isNull(10)) "" else c.getString(10)
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeFinSchemeSettings(f: FinSchemeSettings): JSONObject = JSONObject().apply {
        put("baseMarkupMultiplier", f.baseMarkupMultiplier)
        val mArr = JSONArray()
        f.markupPresets.forEach { mArr.put(it) }
        put("markupPresets", mArr)
        val dArr = JSONArray()
        f.discountPresets.forEach { d ->
            dArr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("discountPercent", d.discountPercent)
                    put("isDealer", d.isDealer)
                }
            )
        }
        put("discountPresets", dArr)
        val fsArr = JSONArray()
        f.financialSchemes.forEach { s ->
            fsArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("discountPercent", s.discountPercent)
                    put("markupPercent", s.markupPercent)
                }
            )
        }
        put("financialSchemes", fsArr)
        put("defaultProfileSystemId", f.defaultProfileSystemId)
        put("defaultVatPercent", f.defaultVatPercent)
        put("warnBelowPrimeCost", f.warnBelowPrimeCost)
        put("gelPerUsdRate", f.gelPerUsdRate)
    }

    private fun parseFinSchemeSettings(o: JSONObject?): FinSchemeSettings {
        if (o == null) return FinSchemeSettings()
        val mArr = o.optJSONArray("markupPresets")
        val mList = if (mArr != null) {
            (0 until mArr.length()).map { mArr.optDouble(it, 1.40) }.filter { it > 0.0 }
        } else {
            emptyList()
        }.ifEmpty { listOf(1.25, 1.35, 1.40, 1.50, 1.65) }

        val dArr = o.optJSONArray("discountPresets")
        val dList = if (dArr != null) {
            val list = mutableListOf<DiscountPresetItem>()
            for (i in 0 until dArr.length()) {
                val item = dArr.getJSONObject(i)
                list.add(
                    DiscountPresetItem(
                        id = item.optString("id", "disc_$i"),
                        name = item.optString("name", "Скидка"),
                        discountPercent = item.optDouble("discountPercent", 0.0).coerceIn(0.0, 99.0),
                        isDealer = item.optBoolean("isDealer", false)
                    )
                )
            }
            list.ifEmpty { DefaultCashCatalogs.defaultDiscountPresets() }
        } else {
            DefaultCashCatalogs.defaultDiscountPresets()
        }

        val fsArr = o.optJSONArray("financialSchemes")
        val fsList = if (fsArr != null) {
            val list = mutableListOf<FinancialSchemeItem>()
            for (i in 0 until fsArr.length()) {
                val item = fsArr.getJSONObject(i)
                list.add(
                    FinancialSchemeItem(
                        id = item.optString("id", "fin_$i"),
                        name = item.optString("name", "Схема"),
                        discountPercent = item.optDouble("discountPercent", 0.0).coerceIn(0.0, 99.0),
                        markupPercent = item.optDouble("markupPercent", 0.0).coerceIn(0.0, 500.0)
                    )
                )
            }
            list.ifEmpty { DefaultCatalogs.defaultFinancialSchemes() }
        } else {
            DefaultCatalogs.defaultFinancialSchemes()
        }

        return FinSchemeSettings(
            baseMarkupMultiplier = o.optDouble("baseMarkupMultiplier", 1.40).coerceAtLeast(0.1),
            markupPresets = mList,
            discountPresets = dList,
            financialSchemes = fsList,
            defaultProfileSystemId = o.optString("defaultProfileSystemId", ""),
            defaultVatPercent = o.optDouble("defaultVatPercent", 18.0).coerceIn(0.0, 50.0),
            warnBelowPrimeCost = o.optBoolean("warnBelowPrimeCost", true),
            gelPerUsdRate = o.optDouble("gelPerUsdRate", 2.70).coerceAtLeast(0.01)
        )
    }

    private fun serializeCashAccountsArray(list: List<CashAccount>): JSONArray {
        val arr = JSONArray()
        list.forEach { acc ->
            arr.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("currency", acc.currency.code)
                    put("kind", acc.kind.name)
                    put("sortOrder", acc.sortOrder)
                }
            )
        }
        return arr
    }

    private fun parseCashAccountsArray(arr: JSONArray?): List<CashAccount> {
        if (arr == null) return emptyList()
        val list = mutableListOf<CashAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                CashAccount(
                    id = o.optString("id", "acc_$i"),
                    name = o.optString("name", "Счёт"),
                    currency = CashCurrency.fromCode(o.optString("currency", "USD")),
                    kind = CashAccountKind.fromStorage(o.optString("kind", "CASH")),
                    sortOrder = o.optInt("sortOrder", i)
                )
            )
        }
        return list
    }

    private fun serializeCashExpenseCategoriesArray(list: List<CashExpenseCategory>): JSONArray {
        val arr = JSONArray()
        list.forEach { cat ->
            arr.put(
                JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("isSystemPayroll", cat.isSystemPayroll)
                    put("sortOrder", cat.sortOrder)
                }
            )
        }
        return arr
    }

    private fun parseCashExpenseCategoriesArray(arr: JSONArray?): List<CashExpenseCategory> {
        if (arr == null) return emptyList()
        val list = mutableListOf<CashExpenseCategory>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                CashExpenseCategory(
                    id = o.optString("id", "cat_$i"),
                    name = o.optString("name", "Категория"),
                    isSystemPayroll = o.optBoolean("isSystemPayroll", false),
                    sortOrder = o.optInt("sortOrder", i)
                )
            )
        }
        return list
    }

    private fun serializeCashTransactionsArray(list: List<CashTransaction>): JSONArray {
        val arr = JSONArray()
        list.forEach { tx ->
            arr.put(
                JSONObject().apply {
                    put("id", tx.id)
                    put("dateMillis", tx.dateMillis)
                    put("type", tx.type.name)
                    put("fromAccountId", tx.fromAccountId ?: JSONObject.NULL)
                    put("toAccountId", tx.toAccountId ?: JSONObject.NULL)
                    put("amountFrom", tx.amountFrom)
                    put("amountTo", tx.amountTo)
                    put("categoryId", tx.categoryId ?: JSONObject.NULL)
                    put("orderId", tx.orderId ?: JSONObject.NULL)
                    put("payrollKind", tx.payrollKind.name)
                    put("comment", tx.comment)
                }
            )
        }
        return arr
    }

    private fun parseCashTransactionsArray(arr: JSONArray?): List<CashTransaction> {
        if (arr == null) return emptyList()
        val list = mutableListOf<CashTransaction>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                CashTransaction(
                    id = o.optString("id", "tx_$i"),
                    dateMillis = o.optLong("dateMillis", System.currentTimeMillis()),
                    type = CashTransactionType.fromStorage(o.optString("type", "EXPENSE")),
                    fromAccountId = if (o.isNull("fromAccountId")) null else o.optString("fromAccountId").takeIf { it.isNotBlank() },
                    toAccountId = if (o.isNull("toAccountId")) null else o.optString("toAccountId").takeIf { it.isNotBlank() },
                    amountFrom = o.optDouble("amountFrom", 0.0),
                    amountTo = o.optDouble("amountTo", 0.0),
                    categoryId = if (o.isNull("categoryId")) null else o.optString("categoryId").takeIf { it.isNotBlank() },
                    orderId = if (o.isNull("orderId")) null else o.optString("orderId").takeIf { it.isNotBlank() },
                    payrollKind = CashPayrollKind.fromStorage(o.optString("payrollKind", "NONE")),
                    comment = o.optString("comment", "")
                )
            )
        }
        return list
    }

    private data class SettingsAndCatalogsBundle(
        val uiSettings: AppUiSettings = AppUiSettings(),
        val glazingCatalog: List<GlazingCatalogItem> = DefaultCatalogs.defaultGlazingItems(),
        val colorCatalog: List<ColorCatalogItem> = DefaultCatalogs.defaultColors(),
        val hardwareBrands: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands(),
        val extraAccessories: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
        val workServices: List<WorkServiceItem> = DefaultCatalogs.defaultWorkServices(),
        val v6CatalogsSeeded: Boolean = false,
        val finSchemeSettings: FinSchemeSettings = FinSchemeSettings(),
        val v7CashSeeded: Boolean = false
    )

    private fun saveSettingsBundleToDb(db: SQLiteDatabase, bundle: SettingsAndCatalogsBundle) {
        val s = bundle.uiSettings
        val obj = JSONObject().apply {
            put("themeMode", s.themeMode.name)
            put("language", s.language.name)
            put("currencySymbol", s.currencySymbol)
            put("unitLabel", s.unitLabel)
            put("companyName", s.companyName)
            put("companyAddress", s.companyAddress)
            put("companyPhone", s.companyPhone)
            put("companyEmail", s.companyEmail)
            put("companyWeb", s.companyWeb)

            val glzArr = JSONArray()
            bundle.glazingCatalog.forEach { g ->
                glzArr.put(
                    JSONObject().apply {
                        put("id", g.id)
                        put("name", g.name)
                        put("formula", g.formula)
                        put("thicknessMm", g.thicknessMm)
                        put("isSandwich", g.isSandwich)
                        put("pricePerM2", g.pricePerM2)
                    }
                )
            }
            put("glazingCatalog", glzArr)

            val colArr = JSONArray()
            bundle.colorCatalog.forEach { c ->
                colArr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("hexColor", c.hexColor)
                        put("priceMarkupPercent", c.priceMarkupPercent)
                    }
                )
            }
            put("colorCatalog", colArr)

            val hwArr = JSONArray()
            bundle.hardwareBrands.forEach { h ->
                hwArr.put(
                    JSONObject().apply {
                        put("id", h.id)
                        put("name", h.name)
                        put("priceMultiplierOrAddon", h.priceMultiplierOrAddon)
                    }
                )
            }
            put("hardwareBrands", hwArr)

            val extArr = JSONArray()
            bundle.extraAccessories.forEach { e ->
                extArr.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("name", e.name)
                        put("price", e.price)
                        put("rateType", e.rateType.name)
                    }
                )
            }
            put("extraAccessories", extArr)

            val wrkArr = JSONArray()
            bundle.workServices.forEach { w ->
                wrkArr.put(
                    JSONObject().apply {
                        put("id", w.id)
                        put("name", w.name)
                        put("price", w.price)
                        put("rateType", w.rateType.name)
                    }
                )
            }
            put("workServices", wrkArr)
            put("v6CatalogsSeeded", bundle.v6CatalogsSeeded)
            put("finSchemeSettings", serializeFinSchemeSettings(bundle.finSchemeSettings))
            put("v7CashSeeded", bundle.v7CashSeeded)
        }
        val cv = ContentValues().apply {
            put("id", 1)
            put("json_payload", obj.toString())
        }
        db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun loadSettingsBundleFromDb(db: SQLiteDatabase): SettingsAndCatalogsBundle {
        db.rawQuery("SELECT json_payload FROM app_settings WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) {
                val o = JSONObject(c.getString(0))
                val theme = try {
                    AppThemeMode.valueOf(o.optString("themeMode", "LIGHT"))
                } catch (_: Exception) {
                    AppThemeMode.LIGHT
                }
                val lang = try {
                    AppLanguage.valueOf(o.optString("language", "RU"))
                } catch (_: Exception) {
                    AppLanguage.RU
                }
                val uiSettings = AppUiSettings(
                    themeMode = theme,
                    language = lang,
                    currencySymbol = o.optString("currencySymbol", "доллар"),
                    unitLabel = o.optString("unitLabel", "mm"),
                    companyName = o.optString("companyName", "WinStudio Pro"),
                    companyAddress = o.optString("companyAddress", ""),
                    companyPhone = o.optString("companyPhone", ""),
                    companyEmail = o.optString("companyEmail", ""),
                    companyWeb = o.optString("companyWeb", "")
                )

                val glazingList = o.optJSONArray("glazingCatalog")?.let { arr ->
                    val list = mutableListOf<GlazingCatalogItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            GlazingCatalogItem(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                formula = item.optString("formula", item.getString("name")),
                                thicknessMm = item.optInt("thicknessMm", 24),
                                isSandwich = item.optBoolean("isSandwich", false),
                                pricePerM2 = item.optDouble("pricePerM2", 20.0)
                            )
                        )
                    }
                    list.ifEmpty { null }
                } ?: DefaultCatalogs.defaultGlazingItems()

                val colorList = o.optJSONArray("colorCatalog")?.let { arr ->
                    val list = mutableListOf<ColorCatalogItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            ColorCatalogItem(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                hexColor = item.optLong("hexColor", 0xFFE6E9ED),
                                priceMarkupPercent = item.optDouble("priceMarkupPercent", 0.0)
                            )
                        )
                    }
                    list.ifEmpty { null }
                } ?: DefaultCatalogs.defaultColors()

                val hwList = o.optJSONArray("hardwareBrands")?.let { arr ->
                    val list = mutableListOf<HardwareBrandItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            HardwareBrandItem(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                priceMultiplierOrAddon = item.optDouble("priceMultiplierOrAddon", 0.0)
                            )
                        )
                    }
                    list.ifEmpty { null }
                } ?: DefaultCatalogs.defaultHardwareBrands()

                val extList = o.optJSONArray("extraAccessories")?.let { arr ->
                    val list = mutableListOf<ExtraAccessoryItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val rt = try {
                            UnitRateType.valueOf(item.optString("rateType", "PER_PIECE"))
                        } catch (_: Exception) {
                            UnitRateType.PER_PIECE
                        }
                        list.add(
                            ExtraAccessoryItem(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                price = item.optDouble("price", 0.0),
                                rateType = rt
                            )
                        )
                    }
                    list.ifEmpty { null }
                } ?: DefaultCatalogs.defaultExtraAccessories()

                val wrkList = o.optJSONArray("workServices")?.let { arr ->
                    val list = mutableListOf<WorkServiceItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val rt = try {
                            UnitRateType.valueOf(item.optString("rateType", "PER_M2"))
                        } catch (_: Exception) {
                            UnitRateType.PER_M2
                        }
                        list.add(
                            WorkServiceItem(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                price = item.optDouble("price", 0.0),
                                rateType = rt
                            )
                        )
                    }
                    list.ifEmpty { null }
                } ?: DefaultCatalogs.defaultWorkServices()

                return SettingsAndCatalogsBundle(
                    uiSettings = uiSettings,
                    glazingCatalog = glazingList,
                    colorCatalog = colorList,
                    hardwareBrands = hwList,
                    extraAccessories = extList,
                    workServices = wrkList,
                    v6CatalogsSeeded = o.optBoolean("v6CatalogsSeeded", false),
                    finSchemeSettings = parseFinSchemeSettings(o.optJSONObject("finSchemeSettings")),
                    v7CashSeeded = o.optBoolean("v7CashSeeded", false)
                )
            }
        }
        return SettingsAndCatalogsBundle()
    }

    private fun serializeProfile(p: ProfileSystemSpec): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("materialType", p.materialType.name)
        // A. Рама
        put("frameWidthMm", p.frameWidthMm.toDouble())
        put("frameRebateHeightA2Mm", p.frameRebateHeightA2Mm.toDouble())
        put("frameFullWidthA3Mm", p.frameFullWidthA3Mm.toDouble())
        put("frameGlazingSeatA4Mm", p.frameGlazingSeatA4Mm.toDouble())
        // B. Импост
        put("impostWidthMm", p.impostWidthMm.toDouble())
        put("impostRebateHeightB2Mm", p.impostRebateHeightB2Mm.toDouble())
        put("impostFullWidthB3Mm", p.impostFullWidthB3Mm.toDouble())
        put("impostGlazingSeatB4Mm", p.impostGlazingSeatB4Mm.toDouble())
        // C. Створка
        put("sashWidthMm", p.sashWidthMm.toDouble())
        put("sashFullWidthC2Mm", p.sashFullWidthC2Mm.toDouble())
        put("sashGlazingSeatC3Mm", p.sashGlazingSeatC3Mm.toDouble())
        put("sashOverlapMm", p.sashOverlapMm.toDouble())
        // D. Дверь
        put("doorSashWidthMm", p.doorSashWidthMm.toDouble())
        put("doorFullWidthD2Mm", p.doorFullWidthD2Mm.toDouble())
        put("doorGlazingSeatD3Mm", p.doorGlazingSeatD3Mm.toDouble())
        put("doorOverlapMm", p.doorOverlapMm.toDouble())
        // E. Обратная дверь
        put("revDoorFullWidthE1Mm", p.revDoorFullWidthE1Mm.toDouble())
        put("revDoorInnerWidthE2Mm", p.revDoorInnerWidthE2Mm.toDouble())
        put("revDoorGlazingSeatE3Mm", p.revDoorGlazingSeatE3Mm.toDouble())
        put("revDoorOverlapMm", p.revDoorOverlapMm.toDouble())
        // G. Штульп
        put("shtulpRebateG1Mm", p.shtulpRebateG1Mm.toDouble())
        put("shtulpWidthMm", p.shtulpWidthMm.toDouble())
        // H. Штапики
        put("shtapikWidthMm", p.shtapikWidthMm.toDouble())
        put("rubberAllowanceTotalMm", p.rubberAllowanceTotalMm.toDouble())
        val shtArr = JSONArray()
        p.shtapikVariants.forEach { s ->
            shtArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("heightH1Mm", s.heightH1Mm.toDouble())
                    put("widthH2Mm", s.widthH2Mm.toDouble())
                    put("glazingThicknessSpMm", s.glazingThicknessSpMm)
                    put("pricePerM", s.pricePerM)
                }
            )
        }
        put("shtapikVariants", shtArr)

        put("glazingClearanceTotalMm", p.glazingClearanceTotalMm.toDouble())
        put("glazingRebateDepthMm", p.glazingRebateDepthMm.toDouble())
        put("glazingClearanceMm", p.glazingClearanceMm.toDouble())
        put("weldAllowancePerCornerMm", p.weldAllowancePerCornerMm.toDouble())
        put("stockBarLengthMm", p.stockBarLengthMm.toDouble())
        put("sawKerfMm", p.sawKerfMm.toDouble())
        put("priceFramePerM", p.priceFramePerM)
        put("priceImpostPerM", p.priceImpostPerM)
        put("priceSashPerM", p.priceSashPerM)
        put("priceDoorSashPerM", p.priceDoorSashPerM)
        put("priceRevDoorSashPerM", p.priceRevDoorSashPerM)
        put("priceCornerProfilePerM", p.priceCornerProfilePerM)
        put("priceShtulpPerM", p.priceShtulpPerM)
        put("priceShtapikPerM", p.priceShtapikPerM)
        put("priceRubberPerM", p.priceRubberPerM)
        put("priceReinforcementFramePerM", p.priceReinforcementFramePerM)
        put("priceReinforcementImpostPerM", p.priceReinforcementImpostPerM)
        put("priceReinforcementSashPerM", p.priceReinforcementSashPerM)
        put("priceCornerConnectorFrame", p.priceCornerConnectorFrame)
        put("priceCornerConnectorSash", p.priceCornerConnectorSash)
        put("priceCornerConnectorDoorSash", p.priceCornerConnectorDoorSash)
        put("priceImpostConnector", p.priceImpostConnector)
        put("priceMechanicTurn", p.priceMechanicTurn)
        put("priceMechanicTiltTurn", p.priceMechanicTiltTurn)
        put("priceMechanicTransom", p.priceMechanicTransom)
        put("priceMechanicSliding", p.priceMechanicSliding)
        put("priceMechanicShtulp", p.priceMechanicShtulp)
        put("priceDoorHardware", p.priceDoorHardware)
        put("priceSeatProfilePerM", p.priceSeatProfilePerM)
        put("priceDoorsillPerM", p.priceDoorsillPerM)
        val colorsArr = JSONArray()
        p.colors.forEach { c ->
            colorsArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("ralCode", c.ralCode)
                    put("hexColor", c.hexColor)
                    put("priceMarkupPercent", c.priceMarkupPercent)
                }
            )
        }
        put("colors", colorsArr)
        put("rubberCatalog", JSONObject().apply {
            fun putBoxSpec(spec: RubberBoxSpec) = JSONObject().apply {
                put("code", spec.code)
                put("name", spec.name)
                put("thicknessMm", spec.thicknessMm.toDouble())
                put("metersPerBox", spec.metersPerBox)
                put("pricePerBox", spec.pricePerBox)
            }
            put("fixedGlazing", putBoxSpec(p.rubberCatalog.fixedGlazing))
            put("frameRebate", putBoxSpec(p.rubberCatalog.frameRebate))
            put("sashDoorRebate", putBoxSpec(p.rubberCatalog.sashDoorRebate))
            val wArr = JSONArray()
            p.rubberCatalog.wedgeVariants.forEach { w ->
                wArr.put(
                    JSONObject().apply {
                        put("id", w.id)
                        put("code", w.code)
                        put("name", w.name)
                        put("thicknessMm", w.thicknessMm.toDouble())
                        put("metersPerBox", w.metersPerBox)
                        put("pricePerBox", w.pricePerBox)
                    }
                )
            }
            put("wedgeVariants", wArr)
        })
    }

    private fun parseProfile(o: JSONObject): ProfileSystemSpec {
        val shtVariants = o.optJSONArray("shtapikVariants")?.let { arr ->
            val list = mutableListOf<ShtapikVariantSpec>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    ShtapikVariantSpec(
                        id = item.optString("id", "sht_$i"),
                        name = item.optString("name", "Штапик"),
                        heightH1Mm = item.optDouble("heightH1Mm", 20.0).toFloat(),
                        widthH2Mm = item.optDouble("widthH2Mm", 18.0).toFloat(),
                        glazingThicknessSpMm = item.optInt("glazingThicknessSpMm", 24),
                        pricePerM = item.optDouble("pricePerM", 2.2)
                    )
                )
            }
            list.ifEmpty { null }
        } ?: listOf(
            ShtapikVariantSpec("sht_18", "Штапик 18 мм", 20f, 18f, 24, o.optDouble("priceShtapikPerM", 2.2)),
            ShtapikVariantSpec("sht_22", "Штапик 22 мм", 20f, 22f, 20, o.optDouble("priceShtapikPerM", 2.3))
        )

        val a1 = o.optDouble("frameWidthMm", 48.0).toFloat()
        val a2 = o.optDouble("frameRebateHeightA2Mm", 20.0).toFloat()
        val a3 = o.optDouble("frameFullWidthA3Mm", (a1 + a2).toDouble()).toFloat()

        val b1 = o.optDouble("impostWidthMm", 48.0).toFloat()
        val b2 = o.optDouble("impostRebateHeightB2Mm", 20.0).toFloat()
        val b3 = o.optDouble("impostFullWidthB3Mm", (b1 + 2f * b2).toDouble()).toFloat()

        return ProfileSystemSpec(
            id = o.getString("id"),
            name = o.getString("name"),
            materialType = ProfileMaterialType.valueOf(o.optString("materialType", "ALUMINUM")),
            frameWidthMm = a1,
            frameRebateHeightA2Mm = a2,
            frameFullWidthA3Mm = a3,
            frameGlazingSeatA4Mm = o.optDouble("frameGlazingSeatA4Mm", 50.0).toFloat(),
            impostWidthMm = b1,
            impostRebateHeightB2Mm = b2,
            impostFullWidthB3Mm = b3,
            impostGlazingSeatB4Mm = o.optDouble("impostGlazingSeatB4Mm", 50.0).toFloat(),
            sashWidthMm = o.optDouble("sashWidthMm", 56.0).toFloat(),
            sashFullWidthC2Mm = o.optDouble("sashFullWidthC2Mm", 76.0).toFloat(),
            sashGlazingSeatC3Mm = o.optDouble("sashGlazingSeatC3Mm", 50.0).toFloat(),
            sashOverlapMm = o.optDouble("sashOverlapMm", 6.0).toFloat(),
            doorSashWidthMm = o.optDouble("doorSashWidthMm", 85.0).toFloat(),
            doorFullWidthD2Mm = o.optDouble("doorFullWidthD2Mm", 105.0).toFloat(),
            doorGlazingSeatD3Mm = o.optDouble("doorGlazingSeatD3Mm", 50.0).toFloat(),
            doorOverlapMm = o.optDouble("doorOverlapMm", o.optDouble("sashOverlapMm", 6.0)).toFloat(),
            revDoorFullWidthE1Mm = o.optDouble("revDoorFullWidthE1Mm", 105.0).toFloat(),
            revDoorInnerWidthE2Mm = o.optDouble("revDoorInnerWidthE2Mm", 85.0).toFloat(),
            revDoorGlazingSeatE3Mm = o.optDouble("revDoorGlazingSeatE3Mm", 50.0).toFloat(),
            revDoorOverlapMm = o.optDouble("revDoorOverlapMm", 6.0).toFloat(),
            shtulpRebateG1Mm = o.optDouble("shtulpRebateG1Mm", 12.0).toFloat(),
            shtulpWidthMm = o.optDouble("shtulpWidthMm", 64.0).toFloat(),
            shtapikWidthMm = o.optDouble("shtapikWidthMm", 18.0).toFloat(),
            rubberAllowanceTotalMm = o.optDouble("rubberAllowanceTotalMm", 8.0).toFloat(),
            shtapikVariants = shtVariants,
            glazingClearanceTotalMm = o.optDouble("glazingClearanceTotalMm", 10.0).toFloat(),
            glazingRebateDepthMm = o.optDouble("glazingRebateDepthMm", 15.0).toFloat(),
            glazingClearanceMm = o.optDouble("glazingClearanceMm", 5.0).toFloat(),
            weldAllowancePerCornerMm = o.optDouble("weldAllowancePerCornerMm", 0.0).toFloat(),
            stockBarLengthMm = o.optDouble("stockBarLengthMm", 6000.0).toFloat(),
            sawKerfMm = o.optDouble("sawKerfMm", 4.0).toFloat(),
            priceFramePerM = o.optDouble("priceFramePerM", 8.5),
            priceImpostPerM = o.optDouble("priceImpostPerM", 9.5),
            priceSashPerM = o.optDouble("priceSashPerM", 9.2),
            priceDoorSashPerM = o.optDouble("priceDoorSashPerM", 14.0),
            priceRevDoorSashPerM = o.optDouble("priceRevDoorSashPerM", 14.5),
            priceCornerProfilePerM = o.optDouble("priceCornerProfilePerM", 11.0),
            priceShtulpPerM = o.optDouble("priceShtulpPerM", 8.0),
            priceShtapikPerM = o.optDouble("priceShtapikPerM", 2.2),
            priceRubberPerM = o.optDouble("priceRubberPerM", 0.6),
            priceReinforcementFramePerM = o.optDouble("priceReinforcementFramePerM", 0.0),
            priceReinforcementImpostPerM = o.optDouble("priceReinforcementImpostPerM", 0.0),
            priceReinforcementSashPerM = o.optDouble("priceReinforcementSashPerM", 0.0),
            priceCornerConnectorFrame = o.optDouble("priceCornerConnectorFrame", 6.0),
            priceCornerConnectorSash = o.optDouble("priceCornerConnectorSash", 6.0),
            priceCornerConnectorDoorSash = o.optDouble("priceCornerConnectorDoorSash", 8.0),
            priceImpostConnector = o.optDouble("priceImpostConnector", 3.6),
            priceMechanicTurn = o.optDouble("priceMechanicTurn", 22.0),
            priceMechanicTiltTurn = o.optDouble("priceMechanicTiltTurn", 32.0),
            priceMechanicTransom = o.optDouble("priceMechanicTransom", 20.0),
            priceMechanicSliding = o.optDouble("priceMechanicSliding", 25.0),
            priceMechanicShtulp = o.optDouble("priceMechanicShtulp", 38.0),
            priceDoorHardware = o.optDouble("priceDoorHardware", 55.0),
            priceSeatProfilePerM = o.optDouble("priceSeatProfilePerM", 2.5),
            priceDoorsillPerM = o.optDouble("priceDoorsillPerM", 7.5),
            colors = o.optJSONArray("colors")?.let { arr ->
                val list = mutableListOf<ProfileColorSpec>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(
                        ProfileColorSpec(
                            id = item.optString("id", "col_$i"),
                            name = item.optString("name", "Цвет"),
                            ralCode = item.optString("ralCode", ""),
                            hexColor = item.optLong("hexColor", 0xFFFFFFFFL),
                            priceMarkupPercent = item.optDouble("priceMarkupPercent", 0.0)
                        )
                    )
                }
                list.ifEmpty { null }
            } ?: DefaultCatalogs.defaultProfileColors(),
            rubberCatalog = o.optJSONObject("rubberCatalog")?.let { rc ->
                val defCat = ProfileRubberCatalog()
                fun parseBoxSpec(obj: JSONObject?, fallback: RubberBoxSpec): RubberBoxSpec {
                    if (obj == null) return fallback
                    return RubberBoxSpec(
                        code = obj.optString("code", fallback.code),
                        name = obj.optString("name", fallback.name),
                        thicknessMm = obj.optDouble("thicknessMm", fallback.thicknessMm.toDouble()).toFloat(),
                        metersPerBox = obj.optDouble("metersPerBox", fallback.metersPerBox).coerceAtLeast(1.0),
                        pricePerBox = obj.optDouble("pricePerBox", fallback.pricePerBox).coerceAtLeast(0.0)
                    )
                }
                val wedgeArr = rc.optJSONArray("wedgeVariants")
                val parsedWedges = if (wedgeArr != null) {
                    val wList = mutableListOf<WedgeRubberVariantSpec>()
                    for (i in 0 until wedgeArr.length()) {
                        val wo = wedgeArr.getJSONObject(i)
                        wList.add(
                            WedgeRubberVariantSpec(
                                id = wo.optString("id", "wedge_$i"),
                                code = wo.optString("code", ""),
                                name = wo.optString("name", "Клин"),
                                thicknessMm = wo.optDouble("thicknessMm", 3.6).toFloat(),
                                metersPerBox = wo.optDouble("metersPerBox", 210.0).coerceAtLeast(1.0),
                                pricePerBox = wo.optDouble("pricePerBox", 126.0).coerceAtLeast(0.0)
                            )
                        )
                    }
                    wList.ifEmpty { defCat.wedgeVariants }
                } else {
                    defCat.wedgeVariants
                }
                ProfileRubberCatalog(
                    fixedGlazing = parseBoxSpec(rc.optJSONObject("fixedGlazing"), defCat.fixedGlazing),
                    wedgeVariants = parsedWedges,
                    frameRebate = parseBoxSpec(rc.optJSONObject("frameRebate"), defCat.frameRebate),
                    sashDoorRebate = parseBoxSpec(rc.optJSONObject("sashDoorRebate"), defCat.sashDoorRebate)
                )
            } ?: ProfileRubberCatalog()
        )
    }

    private fun serializeOrder(ord: CustomerOrder): JSONObject = JSONObject().apply {
        put("id", ord.id)
        put("orderNumber", ord.orderNumber)
        put("customerName", ord.customerName)
        put("customerPhone", ord.customerPhone)
        put("customerEmail", ord.customerEmail)
        put("address", ord.address)
        put("orderNote", ord.orderNote)
        put("createdDate", ord.createdDate)
        put("status", ord.status.name)
        put("contractDateMillis", ord.contractDateMillis ?: JSONObject.NULL)
        put("contractTermDays", ord.contractTermDays)
        put("discountPercent", ord.discountPercent)
        put("markupPercent", ord.markupPercent)
        put("taxVatPercent", ord.taxVatPercent)
        put("manualOrderTotalOverride", ord.manualOrderTotalOverride ?: JSONObject.NULL)
        put("currencySymbol", ord.currencySymbol)
        put("stockDeductedForProduction", ord.stockDeductedForProduction)
        put("floorAssemblyCount", ord.floorAssemblyCount)
        put("extraAssemblyOpsCost", ord.extraAssemblyOpsCost)
        put("extraAssemblyOpsNote", ord.extraAssemblyOpsNote)
        val selSrvArr = JSONArray()
        ord.selectedOrderServices.forEach { sel ->
            selSrvArr.put(
                JSONObject().apply {
                    put("serviceId", sel.serviceId)
                    put("customPriceOverride", sel.customPriceOverride ?: JSONObject.NULL)
                }
            )
        }
        put("selectedOrderServices", selSrvArr)
        if (ord.customCuttingAllocations != null) {
            val cutArr = JSONArray()
            ord.customCuttingAllocations.forEach { alloc ->
                cutArr.put(
                    JSONObject().apply {
                        put("barNumber", alloc.barNumber)
                        put("profileSystemName", alloc.profileSystemName)
                        put("role", alloc.role.name)
                        put("isFromRemnantStock", alloc.isFromRemnantStock)
                        put("sourceLengthMm", alloc.sourceLengthMm)
                        put("usedWithKerfMm", alloc.usedWithKerfMm)
                        put("remnantLengthMm", alloc.remnantLengthMm)
                        put("isUsableBusinessRemnant", alloc.isUsableBusinessRemnant)
                        put("colorName", alloc.colorName)
                        put("colorHex", alloc.colorHex)
                        val piecesArr = JSONArray()
                        alloc.cuts.forEach { piece ->
                            piecesArr.put(
                                JSONObject().apply {
                                    put("windowName", piece.windowName)
                                    put("positionLabel", piece.positionLabel)
                                    put("shortTag", piece.shortTag)
                                    put("cutLengthMm", piece.cutLengthMm)
                                    put("leftAngleDeg", piece.leftAngleDeg)
                                    put("rightAngleDeg", piece.rightAngleDeg)
                                }
                            )
                        }
                        put("cuts", piecesArr)
                    }
                )
            }
            put("customCuttingAllocations", cutArr)
        }
        val wArr = JSONArray()
        ord.windows.forEach { w ->
            wArr.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("name", w.name)
                    put("quantity", w.quantity)
                    put("profileSystemId", w.profileSystemId)
                    put("selectedShtapikId", w.selectedShtapikId)
                    put("glazingId", w.glazingId)
                    put("roundGlassTo5Mm", w.roundGlassTo5Mm)
                    put("colorId", w.colorId)
                    put("customColorName", w.customColorName)
                    put("customColorRal", w.customColorRal)
                    put("customColorHex", w.customColorHex)
                    put("customPaintingCost", w.customPaintingCost)
                    put("customColorUseRawForStock", w.customColorUseRawForStock)
                    put("innerSillId", w.innerSillId)
                    put("outerSillId", w.outerSillId)
                    put("financialSchemeId", w.financialSchemeId)
                    put("hardwareBrandId", w.hardwareBrandId)
                    put("mosquitoSystemId", w.mosquitoSystemId)
                    put("selectedExtraIds", JSONArray(w.selectedExtraIds))
                    put("selectedWorkIds", JSONArray(w.selectedWorkIds))
                    put("note", w.note)
                    put("manualUnitPriceOverride", w.manualUnitPriceOverride ?: JSONObject.NULL)
                    put("assemblySchemeOverride", w.assemblySchemeOverride?.name ?: JSONObject.NULL)
                    put("includeInstallationWork", w.includeInstallationWork)
                    put("installationPricePerM2", w.installationPricePerM2)
                    put("hasSeatProfile", w.addons.hasSeatProfile)
                    put("hasDoorsill", w.addons.hasDoorsill)
                    put("hasCornerJamb", w.addons.hasCornerJamb)
                    put("hasCornerProfile", w.addons.hasCornerProfile)
                    put("hasExtenderProfile90", w.addons.hasExtenderProfile90)
                    put("extTop", w.addons.extenderTopMm)
                    put("extBot", w.addons.extenderBottomMm)
                    put("extLeft", w.addons.extenderLeftMm)
                    put("extRight", w.addons.extenderRightMm)
                    put("topology", serializeTopology(w.topology))
                }
            )
        }
        put("windows", wArr)
    }

    private fun parseOrder(o: JSONObject): CustomerOrder {
        val wArr = o.optJSONArray("windows") ?: JSONArray()
        val windows = mutableListOf<WindowItem>()
        for (j in 0 until wArr.length()) {
            val wo = wArr.getJSONObject(j)
            val manualOverride = if (wo.isNull("manualUnitPriceOverride")) null else wo.optDouble("manualUnitPriceOverride")
            val extrasArr = wo.optJSONArray("selectedExtraIds")
            val extraList = mutableListOf<String>()
            if (extrasArr != null) {
                for (k in 0 until extrasArr.length()) extraList.add(extrasArr.getString(k))
            }
            val worksArr = wo.optJSONArray("selectedWorkIds")
            val workList = mutableListOf<String>()
            if (worksArr != null) {
                for (k in 0 until worksArr.length()) workList.add(worksArr.getString(k))
            }
            windows.add(
                WindowItem(
                    id = wo.getString("id"),
                    name = wo.getString("name"),
                    quantity = wo.optInt("quantity", 1),
                    topology = wo.optJSONObject("topology")?.let { parseTopology(it) }
                        ?: WindowCadOperations.single1500Topology(),
                    profileSystemId = wo.optString("profileSystemId", "al_test_system"),
                    selectedShtapikId = wo.optString("selectedShtapikId", ""),
                    glazingId = wo.optString("glazingId", "sp_24"),
                    roundGlassTo5Mm = wo.optBoolean("roundGlassTo5Mm", false),
                    colorId = wo.optString("colorId", "none"),
                    customColorName = wo.optString("customColorName", ""),
                    customColorRal = wo.optString("customColorRal", ""),
                    customColorHex = wo.optLong("customColorHex", 0xFF607D8BL),
                    customPaintingCost = wo.optDouble("customPaintingCost", 0.0),
                    customColorUseRawForStock = wo.optBoolean("customColorUseRawForStock", false),
                    innerSillId = wo.optString("innerSillId", "sill_in_none"),
                    outerSillId = wo.optString("outerSillId", "sill_out_none"),
                    financialSchemeId = wo.optString("financialSchemeId", "fin_none"),
                    hardwareBrandId = wo.optString("hardwareBrandId", "hw_none"),
                    mosquitoSystemId = wo.optString("mosquitoSystemId", "msq_none"),
                    selectedExtraIds = extraList,
                    selectedWorkIds = workList,
                    note = wo.optString("note", ""),
                    manualUnitPriceOverride = manualOverride,
                    assemblySchemeOverride = if (wo.isNull("assemblySchemeOverride") || !wo.has("assemblySchemeOverride")) {
                        null
                    } else {
                        try {
                            AssemblySchemeType.valueOf(wo.optString("assemblySchemeOverride"))
                        } catch (_: Exception) {
                            null
                        }
                    },
                    includeInstallationWork = wo.optBoolean("includeInstallationWork", false),
                    installationPricePerM2 = wo.optDouble("installationPricePerM2", 15.0),
                    addons = WindowPerimeterAddons(
                        hasSeatProfile = wo.optBoolean("hasSeatProfile", false),
                        hasDoorsill = wo.optBoolean("hasDoorsill", false),
                        hasCornerJamb = wo.optBoolean("hasCornerJamb", false),
                        hasCornerProfile = wo.optBoolean("hasCornerProfile", false),
                        hasExtenderProfile90 = wo.optBoolean("hasExtenderProfile90", false),
                        extenderTopMm = wo.optInt("extTop", 0),
                        extenderBottomMm = wo.optInt("extBot", 0),
                        extenderLeftMm = wo.optInt("extLeft", 0),
                        extenderRightMm = wo.optInt("extRight", 0)
                    )
                )
            )
        }
        val statusStr = o.optString("status", "DRAFT")
        val status = OrderStatus.fromStorageName(statusStr)
        val contractDateMs = if (o.isNull("contractDateMillis") || !o.has("contractDateMillis")) {
            null
        } else {
            o.optLong("contractDateMillis").takeIf { it > 0L }
        }
        val contractTermDays = o.optInt("contractTermDays", 28).coerceAtLeast(1)
        val manualOrdTotal = if (o.isNull("manualOrderTotalOverride")) null else o.optDouble("manualOrderTotalOverride")
        val customCutting = if (o.has("customCuttingAllocations") && !o.isNull("customCuttingAllocations")) {
            val cutArr = o.optJSONArray("customCuttingAllocations")
            if (cutArr != null) {
                val parsedBars = mutableListOf<CutBarAllocation>()
                for (i in 0 until cutArr.length()) {
                    val bo = cutArr.getJSONObject(i)
                    val role = try {
                        ProfileRole.valueOf(bo.optString("role", "FRAME"))
                    } catch (_: Exception) {
                        ProfileRole.FRAME
                    }
                    val piecesArr = bo.optJSONArray("cuts") ?: JSONArray()
                    val cuts = mutableListOf<AllocatedCutPiece>()
                    for (k in 0 until piecesArr.length()) {
                        val po = piecesArr.getJSONObject(k)
                        cuts.add(
                            AllocatedCutPiece(
                                windowName = po.optString("windowName", ""),
                                positionLabel = po.optString("positionLabel", ""),
                                shortTag = po.optString("shortTag", "1"),
                                cutLengthMm = po.optInt("cutLengthMm", 0),
                                leftAngleDeg = po.optInt("leftAngleDeg", 45),
                                rightAngleDeg = po.optInt("rightAngleDeg", 45)
                            )
                        )
                    }
                    parsedBars.add(
                        CutBarAllocation(
                            barNumber = bo.optInt("barNumber", 1),
                            profileSystemName = bo.optString("profileSystemName", ""),
                            role = role,
                            isFromRemnantStock = bo.optBoolean("isFromRemnantStock", false),
                            sourceLengthMm = bo.optInt("sourceLengthMm", 6000),
                            cuts = cuts,
                            usedWithKerfMm = bo.optInt("usedWithKerfMm", 0),
                            remnantLengthMm = bo.optInt("remnantLengthMm", 0),
                            isUsableBusinessRemnant = bo.optBoolean("isUsableBusinessRemnant", false),
                            colorName = bo.optString("colorName", "Белый"),
                            colorHex = bo.optLong("colorHex", 0xFFFFFFFFL)
                        )
                    )
                }
                parsedBars
            } else null
        } else null
            val selSrvArr = o.optJSONArray("selectedOrderServices")
            val parsedSelectedServices = mutableListOf<SelectedOrderService>()
            if (selSrvArr != null) {
                for (i in 0 until selSrvArr.length()) {
                    val so = selSrvArr.getJSONObject(i)
                    val sId = so.optString("serviceId", "")
                    if (sId.isNotBlank()) {
                        val ovr = if (so.isNull("customPriceOverride") || !so.has("customPriceOverride")) {
                            null
                        } else {
                            so.optDouble("customPriceOverride")
                        }
                        parsedSelectedServices.add(SelectedOrderService(sId, ovr))
                    }
                }
            }
            return CustomerOrder(
            id = o.getString("id"),
            orderNumber = o.optString("orderNumber", "Заказ №1"),
            customerName = o.optString("customerName", ""),
            customerPhone = o.optString("customerPhone", ""),
            customerEmail = o.optString("customerEmail", ""),
            address = o.optString("address", ""),
            orderNote = o.optString("orderNote", ""),
            createdDate = o.optString("createdDate", "17.10.2026"),
            windows = windows,
            status = status,
            contractDateMillis = contractDateMs,
            contractTermDays = contractTermDays,
            discountPercent = o.optDouble("discountPercent", 0.0),
            markupPercent = o.optDouble("markupPercent", 0.0),
            taxVatPercent = o.optDouble("taxVatPercent", 18.0),
            manualOrderTotalOverride = manualOrdTotal,
            currencySymbol = o.optString("currencySymbol", "доллар"),
            customCuttingAllocations = customCutting,
            stockDeductedForProduction = o.optBoolean("stockDeductedForProduction", false),
            floorAssemblyCount = o.optInt("floorAssemblyCount", 0),
            extraAssemblyOpsCost = o.optDouble("extraAssemblyOpsCost", 0.0),
            extraAssemblyOpsNote = o.optString("extraAssemblyOpsNote", ""),
            selectedOrderServices = parsedSelectedServices
        )
    }

    private fun serializeTopology(t: WindowBlockTopology): JSONObject {
        val fArr = JSONArray()
        t.frames.forEach { f ->
            val opArr = JSONArray()
            f.openings.forEach { op ->
                opArr.put(
                    JSONObject().apply {
                        put("id", op.id)
                        put("frameId", op.frameId)
                        put("leftMm", op.leftMm.toDouble())
                        put("topMm", op.topMm.toDouble())
                        put("rightMm", op.rightMm.toDouble())
                        put("bottomMm", op.bottomMm.toDouble())
                        put("openingType", op.properties.openingType.name)
                        put("isDoorSash", op.properties.isDoorSash)
                        put("isReverseDoorSash", op.properties.isReverseDoorSash)
                        put("isSandwichFilling", op.properties.isSandwichFilling)
                        put("hasMosquitoNet", op.properties.hasMosquitoNet)
                        put("hasHorizontalSashImpost", op.properties.hasHorizontalSashImpost)
                        put("sashImpostHeightFromBottomMm", op.properties.sashImpostHeightFromBottomMm.toDouble())
                        put("bottomPartIsSandwich", op.properties.bottomPartIsSandwich)
                        put("glazingBars", op.properties.glazingBars.name)
                        put("sashImpostMode", op.properties.sashImpostMode.name)
                        put("paneSandwichFlags", JSONArray(op.properties.paneSandwichFlags))
                        put("shtulpSplitRatio", op.properties.shtulpSplitRatio.toDouble())
                    }
                )
            }
            val impArr = JSONArray()
            f.imposts.forEach { imp ->
                impArr.put(
                    JSONObject().apply {
                        put("id", imp.id)
                        put("frameId", imp.frameId)
                        put("direction", imp.direction.name)
                        put("startX", imp.startX.toDouble())
                        put("startY", imp.startY.toDouble())
                        put("endX", imp.endX.toDouble())
                        put("endY", imp.endY.toDouble())
                    }
                )
            }
            fArr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("leftMm", f.leftMm.toDouble())
                    put("topMm", f.topMm.toDouble())
                    put("rightMm", f.rightMm.toDouble())
                    put("bottomMm", f.bottomMm.toDouble())
                    put("openings", opArr)
                    put("imposts", impArr)
                }
            )
        }
        return JSONObject().apply { put("frames", fArr) }
    }

    private fun parseTopology(obj: JSONObject): WindowBlockTopology {
        val fArr = obj.optJSONArray("frames") ?: return WindowCadOperations.single1500Topology()
        val frames = mutableListOf<FrameBlockNode>()
        for (i in 0 until fArr.length()) {
            val fo = fArr.getJSONObject(i)
            val opArr = fo.optJSONArray("openings") ?: JSONArray()
            val openings = mutableListOf<OpeningCellNode>()
            for (j in 0 until opArr.length()) {
                val oo = opArr.getJSONObject(j)
                val opTypeStr = oo.optString("openingType", "FIXED_GLASS")
                val safeOpType = try {
                    SashOpeningType.valueOf(opTypeStr)
                } catch (_: Exception) {
                    SashOpeningType.FIXED_GLASS
                }
                val barsStr = oo.optString("glazingBars", "NONE")
                val safeBars = try {
                    GlazingBarsPattern.valueOf(barsStr)
                } catch (_: Exception) {
                    GlazingBarsPattern.NONE
                }
                val impostModeStr = oo.optString("sashImpostMode", "NONE")
                val safeImpostMode = try {
                    SashImpostMode.valueOf(impostModeStr)
                } catch (_: Exception) {
                    SashImpostMode.NONE
                }
                val paneFlagsArr = oo.optJSONArray("paneSandwichFlags")
                val paneFlags = if (paneFlagsArr != null) {
                    (0 until paneFlagsArr.length()).map { idx -> paneFlagsArr.optBoolean(idx, false) }
                } else {
                    emptyList()
                }
                val splitRatio = oo.optDouble("shtulpSplitRatio", 0.5).toFloat().coerceIn(0.15f, 0.85f)
                openings.add(
                    OpeningCellNode(
                        id = oo.getString("id"),
                        frameId = oo.getString("frameId"),
                        leftMm = oo.getDouble("leftMm").toFloat(),
                        topMm = oo.getDouble("topMm").toFloat(),
                        rightMm = oo.getDouble("rightMm").toFloat(),
                        bottomMm = oo.getDouble("bottomMm").toFloat(),
                        properties = OpeningProperties(
                            openingType = safeOpType,
                            isDoorSash = oo.optBoolean("isDoorSash", false),
                            isReverseDoorSash = oo.optBoolean("isReverseDoorSash", false),
                            isSandwichFilling = oo.optBoolean("isSandwichFilling", false),
                            hasMosquitoNet = oo.optBoolean("hasMosquitoNet", false),
                            hasHorizontalSashImpost = oo.optBoolean("hasHorizontalSashImpost", false),
                            sashImpostHeightFromBottomMm = oo.optDouble("sashImpostHeightFromBottomMm", 800.0).toFloat(),
                            bottomPartIsSandwich = oo.optBoolean("bottomPartIsSandwich", false),
                            glazingBars = safeBars,
                            sashImpostMode = safeImpostMode,
                            paneSandwichFlags = paneFlags,
                            shtulpSplitRatio = splitRatio
                        )
                    )
                )
            }
            val impArr = fo.optJSONArray("imposts") ?: JSONArray()
            val imposts = mutableListOf<ImpostSegmentNode>()
            for (k in 0 until impArr.length()) {
                val io = impArr.getJSONObject(k)
                imposts.add(
                    ImpostSegmentNode(
                        id = io.getString("id"),
                        frameId = io.getString("frameId"),
                        direction = ImpostDirection.valueOf(io.optString("direction", "VERTICAL")),
                        startX = io.getDouble("startX").toFloat(),
                        startY = io.getDouble("startY").toFloat(),
                        endX = io.getDouble("endX").toFloat(),
                        endY = io.getDouble("endY").toFloat()
                    )
                )
            }
            frames.add(
                FrameBlockNode(
                    id = fo.getString("id"),
                    leftMm = fo.getDouble("leftMm").toFloat(),
                    topMm = fo.getDouble("topMm").toFloat(),
                    rightMm = fo.getDouble("rightMm").toFloat(),
                    bottomMm = fo.getDouble("bottomMm").toFloat(),
                    openings = openings,
                    imposts = imposts
                )
            )
        }
        return WindowBlockTopology(frames)
    }
}
