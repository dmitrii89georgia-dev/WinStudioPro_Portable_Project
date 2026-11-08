package ru.winstudiopro.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import ru.winstudiopro.app.domain.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PersistedAppState(
    val profileSystems: List<ProfileSystemSpec>,
    val orders: List<CustomerOrder>,
    val stockRemnants: List<StockRemnantBar>,
    val uiSettings: AppUiSettings = AppUiSettings(),
    val glazingCatalog: List<GlazingCatalogItem> = DefaultCatalogs.defaultGlazingItems(),
    val colorCatalog: List<ColorCatalogItem> = DefaultCatalogs.defaultColors(),
    val hardwareBrands: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands(),
    val extraAccessories: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
    val workServices: List<WorkServiceItem> = DefaultCatalogs.defaultWorkServices()
)

private class WinStudioSqliteHelper(context: Context) :
    SQLiteOpenHelper(context, "winstudio_pro_v3.db", null, 3) {

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
                is_full_bar INTEGER NOT NULL DEFAULT 0
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }
}

object AppStateStorage {

    fun loadState(context: Context): PersistedAppState {
        return try {
            val dbHelper = WinStudioSqliteHelper(context)
            val db = dbHelper.readableDatabase

            val profiles = loadProfilesFromDb(db).ifEmpty {
                val defaults = DefaultCatalogs.defaultProfileSystems()
                saveProfilesToDb(dbHelper.writableDatabase, defaults)
                defaults
            }

            val orders = loadOrdersFromDb(db).ifEmpty {
                val defaults = defaultDemoOrders()
                saveOrdersToDb(dbHelper.writableDatabase, defaults)
                defaults
            }

            val stock = loadStockFromDb(db)
            val settingsBundle = loadSettingsBundleFromDb(db)

            PersistedAppState(
                profileSystems = profiles,
                orders = orders,
                stockRemnants = stock,
                uiSettings = settingsBundle.uiSettings,
                glazingCatalog = settingsBundle.glazingCatalog,
                colorCatalog = settingsBundle.colorCatalog,
                hardwareBrands = settingsBundle.hardwareBrands,
                extraAccessories = settingsBundle.extraAccessories,
                workServices = settingsBundle.workServices
            )
        } catch (_: Exception) {
            createDefaultInitialState()
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
                saveSettingsBundleToDb(
                    db,
                    SettingsAndCatalogsBundle(
                        uiSettings = state.uiSettings,
                        glazingCatalog = state.glazingCatalog,
                        colorCatalog = state.colorCatalog,
                        hardwareBrands = state.hardwareBrands,
                        extraAccessories = state.extraAccessories,
                        workServices = state.workServices
                    )
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
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

    // Полный экспорт всей базы (все системы Asas, прайсы, склад, настройки и все заказы)
    fun exportFullBackupJsonString(context: Context): String {
        val dbHelper = WinStudioSqliteHelper(context)
        val db = dbHelper.readableDatabase
        val profiles = loadProfilesFromDb(db)
        val orders = loadOrdersFromDb(db)

        val root = JSONObject().apply {
            put("app", "WinStudioPro")
            put("backupVersion", 1)
            put("timestamp", System.currentTimeMillis())
            put("exportDate", SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date()))

            val ordArr = JSONArray()
            orders.forEach { ordArr.put(serializeOrder(it)) }
            put("orders", ordArr)

            val profArr = JSONArray()
            profiles.forEach { profArr.put(serializeProfile(it)) }
            put("profileSystems", profArr)
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

        val dbHelper = WinStudioSqliteHelper(context)
        val db = dbHelper.writableDatabase
        if (root.has("appSettingsPayload")) {
            val cv = ContentValues().apply {
                put("id", 1)
                put("json_payload", root.getJSONObject("appSettingsPayload").toString())
            }
            db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        val mergedOrders = (importedOrders + current.orders).distinctBy { it.id }
        val mergedProfiles = (importedProfiles + current.profileSystems).distinctBy { it.id }

        val newState = loadState(context).copy(
            orders = mergedOrders.ifEmpty { current.orders },
            profileSystems = mergedProfiles.ifEmpty { current.profileSystems }
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
                    isFullStockBar = true
                ),
                StockRemnantBar(
                    id = "stk_full_2",
                    profileSystemId = "al_test_system",
                    role = ProfileRole.IMPOST,
                    lengthMm = 6000,
                    quantity = 8,
                    isFullStockBar = true
                ),
                StockRemnantBar(
                    id = "stk_rem_1",
                    profileSystemId = "al_test_system",
                    role = ProfileRole.FRAME,
                    lengthMm = 2150,
                    quantity = 2,
                    isFullStockBar = false
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
                        colorId = "none"
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
            }
            db.insertWithOnConflict("stock_inventory", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun loadStockFromDb(db: SQLiteDatabase): List<StockRemnantBar> {
        val res = mutableListOf<StockRemnantBar>()
        db.rawQuery(
            "SELECT id, profile_system_id, role, length_mm, quantity, is_full_bar FROM stock_inventory",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val roleStr = c.getString(2)
                val role = try {
                    ProfileRole.valueOf(roleStr)
                } catch (_: Exception) {
                    ProfileRole.FRAME
                }
                res.add(
                    StockRemnantBar(
                        id = c.getString(0),
                        profileSystemId = c.getString(1),
                        role = role,
                        lengthMm = c.getInt(3),
                        quantity = c.getInt(4),
                        isFullStockBar = c.getInt(5) == 1
                    )
                )
            }
        }
        return res
    }

    private data class SettingsAndCatalogsBundle(
        val uiSettings: AppUiSettings = AppUiSettings(),
        val glazingCatalog: List<GlazingCatalogItem> = DefaultCatalogs.defaultGlazingItems(),
        val colorCatalog: List<ColorCatalogItem> = DefaultCatalogs.defaultColors(),
        val hardwareBrands: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands(),
        val extraAccessories: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
        val workServices: List<WorkServiceItem> = DefaultCatalogs.defaultWorkServices()
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
                    workServices = wrkList
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
            priceDoorsillPerM = o.optDouble("priceDoorsillPerM", 7.5)
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
        put("discountPercent", ord.discountPercent)
        put("markupPercent", ord.markupPercent)
        put("taxVatPercent", ord.taxVatPercent)
        put("manualOrderTotalOverride", ord.manualOrderTotalOverride ?: JSONObject.NULL)
        put("currencySymbol", ord.currencySymbol)
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
                    put("innerSillId", w.innerSillId)
                    put("outerSillId", w.outerSillId)
                    put("financialSchemeId", w.financialSchemeId)
                    put("hardwareBrandId", w.hardwareBrandId)
                    put("mosquitoSystemId", w.mosquitoSystemId)
                    put("selectedExtraIds", JSONArray(w.selectedExtraIds))
                    put("selectedWorkIds", JSONArray(w.selectedWorkIds))
                    put("note", w.note)
                    put("manualUnitPriceOverride", w.manualUnitPriceOverride ?: JSONObject.NULL)
                    put("includeInstallationWork", w.includeInstallationWork)
                    put("installationPricePerM2", w.installationPricePerM2)
                    put("hasSeatProfile", w.addons.hasSeatProfile)
                    put("hasDoorsill", w.addons.hasDoorsill)
                    put("hasCornerJamb", w.addons.hasCornerJamb)
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
                    topology = parseTopology(wo.getJSONObject("topology")),
                    profileSystemId = wo.optString("profileSystemId", "al_test_system"),
                    selectedShtapikId = wo.optString("selectedShtapikId", ""),
                    glazingId = wo.optString("glazingId", "sp_24"),
                    roundGlassTo5Mm = wo.optBoolean("roundGlassTo5Mm", false),
                    colorId = wo.optString("colorId", "none"),
                    innerSillId = wo.optString("innerSillId", "sill_in_none"),
                    outerSillId = wo.optString("outerSillId", "sill_out_none"),
                    financialSchemeId = wo.optString("financialSchemeId", "fin_none"),
                    hardwareBrandId = wo.optString("hardwareBrandId", "hw_none"),
                    mosquitoSystemId = wo.optString("mosquitoSystemId", "msq_none"),
                    selectedExtraIds = extraList,
                    selectedWorkIds = workList,
                    note = wo.optString("note", ""),
                    manualUnitPriceOverride = manualOverride,
                    includeInstallationWork = wo.optBoolean("includeInstallationWork", false),
                    installationPricePerM2 = wo.optDouble("installationPricePerM2", 15.0),
                    addons = WindowPerimeterAddons(
                        hasSeatProfile = wo.optBoolean("hasSeatProfile", false),
                        hasDoorsill = wo.optBoolean("hasDoorsill", false),
                        hasCornerJamb = wo.optBoolean("hasCornerJamb", false),
                        extenderTopMm = wo.optInt("extTop", 0),
                        extenderBottomMm = wo.optInt("extBot", 0),
                        extenderLeftMm = wo.optInt("extLeft", 0),
                        extenderRightMm = wo.optInt("extRight", 0)
                    )
                )
            )
        }
        val statusStr = o.optString("status", "DRAFT")
        val status = try {
            OrderStatus.valueOf(statusStr)
        } catch (_: Exception) {
            OrderStatus.DRAFT
        }
        val manualOrdTotal = if (o.isNull("manualOrderTotalOverride")) null else o.optDouble("manualOrderTotalOverride")
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
            discountPercent = o.optDouble("discountPercent", 0.0),
            markupPercent = o.optDouble("markupPercent", 0.0),
            taxVatPercent = o.optDouble("taxVatPercent", 18.0),
            manualOrderTotalOverride = manualOrdTotal,
            currencySymbol = o.optString("currencySymbol", "доллар")
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
                            glazingBars = safeBars
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
