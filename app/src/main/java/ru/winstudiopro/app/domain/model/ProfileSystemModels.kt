package ru.winstudiopro.app.domain.model

enum class ProfileMaterialType(val titleRu: String, val shortBadge: String) {
    ALUMINUM("Алюминий (сборка на закладных)", "AL"),
    PVC("ПВХ (сварка с припуском на плавку)", "PVC")
}

/**
 * Вариант штапика внутри профильной системы (раздел H).
 * Позволяет задать несколько видов штапика (например 18 мм -> пакет 24 мм, 22 мм -> пакет 20 мм).
 */
data class ShtapikVariantSpec(
    val id: String,
    val name: String,
    val heightH1Mm: Float = 20f,            // H1 — высота штапика (мм)
    val widthH2Mm: Float = 18f,             // H2 — ширина штапика (мм)
    val glazingThicknessSpMm: Int = 24,     // СП — толщина стеклопакета/заполнения (мм)
    val pricePerM: Double = 2.2             // Стоимость за 1 пог. метр штапика
)

const val CUSTOM_CLIENT_COLOR_ID = "custom_client"
const val RAW_UNPAINTED_COLOR_NAME = "Неокрашенный (сырой)"

/**
 * Спецификация цвета / покрытия профильной системы.
 */
data class ProfileColorSpec(
    val id: String,
    val name: String,
    val ralCode: String = "",
    val hexColor: Long = 0xFFFFFFFF,
    val priceMarkupPercent: Double = 0.0
)

/**
 * Полная параметрическая модель профильной системы (конструктор профильных систем).
 * Все размеры задаются в миллиметрах по буквенным схемам (A, B, C, D, E, F, G, H)
 * и напрямую влияют на 2D-чертеж, выравнивание по проёмам и по свету, размеры стеклопакетов и раскрой профилей.
 */
data class ProfileSystemSpec(
    val id: String,
    val name: String,
    val materialType: ProfileMaterialType = ProfileMaterialType.ALUMINUM,

    // --- A. РАМА ---
    val frameWidthMm: Float = 48f,              // A1 — Внутренняя ширина рамы (мм) — для расчёта проёмов в чистоте
    val frameRebateHeightA2Mm: Float = 20f,     // A2 — Высота четверти / притвора рамы (мм)
    val frameFullWidthA3Mm: Float = 68f,        // A3 — Полная ширина профиля рамы (мм) — для светового проёма
    val frameGlazingSeatA4Mm: Float = 50f,      // A4 — Посадочное место под стеклопакет + резину + штапик (мм)

    // --- B. ИМПОСТ ---
    val impostWidthMm: Float = 48f,             // B1 — Внутренняя ширина коробки импоста (мм) — для расчёта проёмов
    val impostRebateHeightB2Mm: Float = 20f,    // B2 — Высота четверти / притвора импоста (мм)
    val impostFullWidthB3Mm: Float = 88f,       // B3 — Полная ширина профиля импоста (мм) — для светового проёма
    val impostGlazingSeatB4Mm: Float = 50f,     // B4 — Посадочное место под стеклопакет + резину + штапик (мм)

    // --- C. СТВОРКА ОКОННАЯ ---
    val sashWidthMm: Float = 56f,               // C1 — Ширина от начала притвора до проёма под штапик (мм)
    val sashFullWidthC2Mm: Float = 76f,         // C2 — Общая ширина профиля створки (мм)
    val sashGlazingSeatC3Mm: Float = 50f,       // C3 — Посадочное место под стеклопакет + резину + штапик (мм)
    val sashOverlapMm: Float = 6f,              // Нахлёст створки на раму с одной стороны (мм), например 6 мм -> +12 мм к проёму

    // --- D. ДВЕРЬ ---
    val doorSashWidthMm: Float = 85f,           // D1 — Ширина от начала притвора до проёма под штапик (мм)
    val doorFullWidthD2Mm: Float = 105f,        // D2 — Общая ширина профиля двери (мм)
    val doorGlazingSeatD3Mm: Float = 50f,       // D3 — Посадочное место под стеклопакет + резину + штапик (мм)
    val doorOverlapMm: Float = 6f,              // Нахлёст двери на раму с одной стороны (мм), например 6 мм -> +12 мм к проёму

    // --- E. ОБРАТНАЯ ДВЕРЬ ---
    val revDoorFullWidthE1Mm: Float = 105f,     // E1 — Общая ширина профиля обратной двери (мм)
    val revDoorInnerWidthE2Mm: Float = 85f,     // E2 — Ширина от начала притвора до начала проёма двери под штапик (мм)
    val revDoorGlazingSeatE3Mm: Float = 50f,    // E3 — Посадочное место под стеклопакет + резину + штапик (мм)
    val revDoorOverlapMm: Float = 6f,           // Нахлёст обратной двери на раму с одной стороны (мм)

    // --- G. ШТУЛЬП ---
    val shtulpRebateG1Mm: Float = 12f,          // G1 — Выступ притвора штульпа (мм)
    val shtulpWidthMm: Float = 64f,             // G2 — Общая ширина штульпового профиля (мм)

    // --- H. ШТАПИКИ И РЕЗИНА ---
    val shtapikWidthMm: Float = 18f,            // H2 — Базовая ширина штапика по умолчанию (мм)
    val rubberAllowanceTotalMm: Float = 8f,     // Суммарный зазор на обе резины (упорная + клиновая, стандартно 4+4 = 8 мм)
    val shtapikVariants: List<ShtapikVariantSpec> = listOf(
        ShtapikVariantSpec(
            id = "sht_18",
            name = "Штапик 18 мм",
            heightH1Mm = 20f,
            widthH2Mm = 18f,
            glazingThicknessSpMm = 24,
            pricePerM = 2.2
        ),
        ShtapikVariantSpec(
            id = "sht_22",
            name = "Штапик 22 мм",
            heightH1Mm = 20f,
            widthH2Mm = 22f,
            glazingThicknessSpMm = 20,
            pricePerM = 2.3
        )
    ),

    // --- Допуски фальца и раскроя ---
    val glazingClearanceTotalMm: Float = 10f,   // Общий зазор на стеклопакет (отнимается от ширины и высоты проёма, например 10 мм или 14 мм)
    val glazingRebateDepthMm: Float = 15f,      // Глубина фальца под стеклопакет с одной стороны (мм)
    val glazingClearanceMm: Float = 5f,         // Монтажный зазор под подкладки с одной стороны (мм)
    val weldAllowancePerCornerMm: Float = 0f,   // Припуск на плавку на 1 угол (0 мм для AL, ~3 мм для PVC)
    val stockBarLengthMm: Float = 6000f,        // Стандартная длина хлыста профиля (обычно 6000 мм, от 5500 до 8000 мм)
    val sawKerfMm: Float = 4f,                  // Ширина реза пилы (мм)

    // --- Ценовые параметры за 1 пог. метр профиля ---
    val priceFramePerM: Double = 8.5,
    val priceImpostPerM: Double = 9.5,
    val priceSashPerM: Double = 9.2,
    val priceDoorSashPerM: Double = 14.0,
    val priceRevDoorSashPerM: Double = 14.5,
    val priceCornerProfilePerM: Double = 11.0,  // F — Угловик (цена за 1 пог. метр)
    val priceShtulpPerM: Double = 8.0,          // G — Штульп (цена за 1 пог. метр)
    val priceShtapikPerM: Double = 2.2,
    val priceRubberPerM: Double = 0.6,
    val priceReinforcementFramePerM: Double = 0.0,
    val priceReinforcementImpostPerM: Double = 0.0,
    val priceReinforcementSashPerM: Double = 0.0,

    // --- Цены комплектов угловых сухарей (за комплект на 4 угла / на 1 импост) ---
    val priceCornerConnectorFrame: Double = 6.0,     // Комплект сухарей на 1 раму (4 угла)
    val priceImpostConnector: Double = 3.6,          // Комплект сухарей на 1 импост (2 торца)
    val priceCornerConnectorSash: Double = 6.0,      // Комплект сухарей на 1 створку (4 угла)
    val priceCornerConnectorDoorSash: Double = 8.0,  // Комплект сухарей на 1 дверь (4 угла)

    val priceMechanicTurn: Double = 22.0,
    val priceMechanicTiltTurn: Double = 32.0,
    val priceMechanicTransom: Double = 20.0,
    val priceMechanicSliding: Double = 25.0,
    val priceMechanicShtulp: Double = 38.0,
    val priceDoorHardware: Double = 55.0,
    val priceSeatProfilePerM: Double = 2.5,
    val priceDoorsillPerM: Double = 7.5,
    val priceExtender20PerM: Double = 4.0,
    val priceExtender40PerM: Double = 5.5,
    val priceExtender60PerM: Double = 7.5,
    val priceGlazingBarPerM: Double = 3.0,

    // --- Каталог доступных цветов профильной системы (Антрацит — базовый по умолчанию) ---
    val colors: List<ProfileColorSpec> = DefaultCatalogs.defaultProfileColors()
) {
    /**
     * Заход стеклопакета внутрь профиля от линии светового проема с одной стороны (мм).
     */
    val netGlazingBitePerSideMm: Float
        get() = (glazingRebateDepthMm - glazingClearanceMm).coerceAtLeast(0f)

    /**
     * Эффективный припуск на плавку на один угол (для алюминия 0, для ПВХ — weldAllowancePerCornerMm).
     */
    val effectiveWeldAllowancePerCornerMm: Float
        get() = if (materialType == ProfileMaterialType.PVC) weldAllowancePerCornerMm else 0f
}

data class GlazingCatalogItem(
    val id: String,
    val name: String,
    val formula: String,
    val thicknessMm: Int,
    val isSandwich: Boolean = false,
    val pricePerM2: Double
)

data class ColorCatalogItem(
    val id: String,
    val name: String,
    val hexColor: Long,
    val priceMarkupPercent: Double = 0.0
)

data class SillCatalogItem(
    val id: String,
    val name: String,
    val isOuterSill: Boolean,
    val widthMm: Int,
    val pricePerM: Double,
    val capPricePerPair: Double = 1.2
)

data class FinancialSchemeItem(
    val id: String,
    val name: String,
    val discountPercent: Double = 0.0,
    val markupPercent: Double = 0.0
)

data class HardwareBrandItem(
    val id: String,
    val name: String,
    val priceMultiplierOrAddon: Double = 0.0
)

enum class UnitRateType(val labelRu: String) {
    PER_PIECE("шт"),
    PER_M2("m2"),
    PER_M("m")
}

data class ExtraAccessoryItem(
    val id: String,
    val name: String,
    val price: Double,
    val rateType: UnitRateType = UnitRateType.PER_PIECE
)

data class WorkServiceItem(
    val id: String,
    val name: String,
    val price: Double,
    val rateType: UnitRateType = UnitRateType.PER_M2
)

data class MosquitoSystemItem(
    val id: String,
    val name: String,
    val pricePerM2: Double
)

enum class AppThemeMode(val titleRu: String) {
    LIGHT("Светлая (Классическая)"),
    DARK("Тёмная (Цеховая)")
}

enum class AppLanguage(val code: String, val title: String) {
    RU("RU", "Русский"),
    EN("EN", "English")
}

data class AppUiSettings(
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val language: AppLanguage = AppLanguage.RU,
    val currencySymbol: String = "доллар",
    val unitLabel: String = "mm",
    val companyName: String = "WinStudio Pro",
    val companyAddress: String = "",
    val companyPhone: String = "",
    val companyEmail: String = "",
    val companyWeb: String = ""
)

object DefaultCatalogs {
    /**
     * По запросу пользователя: 1 тестовая алюминиевая профильная система по умолчанию.
     * Свои системы добавляются и настраиваются в разделе «профильные системы».
     */
    fun defaultProfileSystems(): List<ProfileSystemSpec> = listOf(
        ProfileSystemSpec(
            id = "al_test_system",
            name = "Алюминиевая система 1 (Тест)",
            materialType = ProfileMaterialType.ALUMINUM,
            frameWidthMm = 50f,
            impostWidthMm = 60f,
            sashWidthMm = 56f,
            doorSashWidthMm = 94f,
            shtapikWidthMm = 18f,
            shtulpWidthMm = 64f,
            sashOverlapMm = 8f,
            glazingRebateDepthMm = 15f,
            glazingClearanceMm = 5f,
            weldAllowancePerCornerMm = 0f,
            stockBarLengthMm = 6000f,
            sawKerfMm = 4f,
            priceFramePerM = 8.5,
            priceImpostPerM = 9.5,
            priceSashPerM = 9.2,
            priceDoorSashPerM = 14.0,
            priceShtapikPerM = 2.2,
            priceRubberPerM = 0.6,
            priceCornerConnectorFrame = 1.5,
            priceCornerConnectorSash = 1.5,
            priceImpostConnector = 1.8,
            priceMechanicTurn = 22.0,
            priceMechanicTiltTurn = 32.0
        )
    )

    fun defaultGlazingItems(): List<GlazingCatalogItem> = listOf(
        GlazingCatalogItem("none", "не учитывать", "без пакета", 0, false, 0.0),
        GlazingCatalogItem("sp_24", "24мм", "4-16-4", 24, false, 20.0),
        GlazingCatalogItem("sp_32", "4-10-4-10-4", "4-10-4-10-4", 32, false, 25.0),
        GlazingCatalogItem("sp_32_g", "4-10-4-10-4g", "4-10-4-10-4g", 32, false, 30.0),
        GlazingCatalogItem("sp_24_i", "4g-16-4", "4g-16-4", 24, false, 23.0),
        GlazingCatalogItem("glass_6_temp", "стекло 6мм зак", "6 мм закал.", 6, false, 20.0),
        GlazingCatalogItem("glass_6_12_6_temp", "6g-12-6z закаленные", "6g-12-6z", 24, false, 45.0),
        GlazingCatalogItem("triplex", "триплекс", "Триплекс", 8, false, 38.0),
        GlazingCatalogItem("sandwich_24", "сендвич", "Сэндвич 24", 24, true, 18.0)
    )

    fun defaultProfileColors(): List<ProfileColorSpec> = listOf(
        ProfileColorSpec("col_anthracite", "Антрацит", "7016", 0xFF383E42, 0.0),
        ProfileColorSpec("col_white", "Белый", "9016", 0xFFFFFFFF, 0.0),
        ProfileColorSpec("col_silver", "Серебро (анод.)", "E6/EV1", 0xFFA8A8A8, 0.0),
        ProfileColorSpec("col_black", "Чёрный", "9005", 0xFF1F2124, 0.0)
    )

    fun defaultColors(): List<ColorCatalogItem> = listOf(
        ColorCatalogItem("none", "не учитывать", 0xFFE6E9ED, 0.0),
        ColorCatalogItem("antracit", "антрацит", 0xFF62666A, 12.0),
        ColorCatalogItem("bereza", "береза", 0xFFE6CEA8, 10.0),
        ColorCatalogItem("buk", "бук", 0xFFD89A6C, 10.0),
        ColorCatalogItem("vishnya", "вишня", 0xFF8B3E2F, 12.0),
        ColorCatalogItem("dub", "дуб", 0xFFA67B5B, 10.0),
        ColorCatalogItem("korich", "коричневый", 0xFF5A403A, 10.0),
        ColorCatalogItem("cherniy", "чёрный", 0xFF2B2D2F, 15.0)
    )

    fun defaultSills(): List<SillCatalogItem> = listOf(
        SillCatalogItem("sill_out_none", "не учитывать", true, 0, 0.0, 0.0),
        SillCatalogItem("sill_out_200", "отлив белый 200", true, 200, 3.8, 1.0),
        SillCatalogItem("sill_out_300", "отлив белый 300", true, 300, 5.2, 1.0),
        SillCatalogItem("sill_in_none", "не учитывать", false, 0, 0.0, 0.0),
        SillCatalogItem("sill_in_300", "белый 300", false, 300, 6.0, 1.2),
        SillCatalogItem("sill_in_400", "белый 400", false, 400, 7.8, 1.2),
        SillCatalogItem("sill_in_mram300", "под мрамор 300", false, 300, 8.5, 1.5),
        SillCatalogItem("sill_in_mram400", "под мрамор 400", false, 400, 10.2, 1.5)
    )

    fun defaultFinancialSchemes(): List<FinancialSchemeItem> = listOf(
        FinancialSchemeItem("fin_none", "не учитывать", 0.0, 0.0),
        FinancialSchemeItem("fin_dealer", "дилерская цена", 1.0, 60.0),
        FinancialSchemeItem("fin_disc_1", "скидка 1%", 1.0, 0.0),
        FinancialSchemeItem("fin_disc_3", "скидка 3%", 3.0, 0.0),
        FinancialSchemeItem("fin_disc_5", "скидка 5%", 5.0, 0.0),
        FinancialSchemeItem("fin_disc_10", "скидка 10%", 10.0, 0.0),
        FinancialSchemeItem("fin_disc_20", "скидка 20%", 20.0, 0.0),
        FinancialSchemeItem("fin_disc_30", "скидка 30%", 30.0, 0.0),
        FinancialSchemeItem("fin_disc_40", "скидка 40%", 40.0, 0.0),
        FinancialSchemeItem("fin_disc_50", "скидка 50%", 50.0, 0.0)
    )

    fun defaultHardwareBrands(): List<HardwareBrandItem> = listOf(
        HardwareBrandItem("hw_none", "не учитывать", 0.0),
        HardwareBrandItem("hw_acado", "acado", 12.0),
        HardwareBrandItem("hw_maco", "maco", 25.0),
        HardwareBrandItem("hw_roto", "roto", 28.0),
        HardwareBrandItem("hw_roto_patio", "roto patio", 85.0),
        HardwareBrandItem("hw_siegenia", "siegenia", 24.0),
        HardwareBrandItem("hw_white_1", "белая тип 1", 18.0),
        HardwareBrandItem("hw_white_2", "белая тип 2", 22.0)
    )

    fun defaultExtraAccessories(): List<ExtraAccessoryItem> = listOf(
        ExtraAccessoryItem("ext_latch", "защелка+ручка", 20.0, UnitRateType.PER_PIECE),
        ExtraAccessoryItem("ext_screws", "комплект шурупов", 1.5, UnitRateType.PER_M),
        ExtraAccessoryItem("ext_foam", "пена монтажная", 3.0, UnitRateType.PER_M),
        ExtraAccessoryItem("ext_conn", "соединитель", 20.0, UnitRateType.PER_PIECE),
        ExtraAccessoryItem("ext_corner", "угловик", 50.0, UnitRateType.PER_PIECE)
    )

    fun defaultWorkServices(): List<WorkServiceItem> = listOf(
        WorkServiceItem("wrk_trash", "вынос мусора", 15.0, UnitRateType.PER_PIECE),
        WorkServiceItem("wrk_demount", "демонтаж", 10.0, UnitRateType.PER_PIECE),
        WorkServiceItem("wrk_delivery", "доставка", 15.0, UnitRateType.PER_PIECE),
        WorkServiceItem("wrk_mount", "монтаж", 18.0, UnitRateType.PER_M2),
        WorkServiceItem("wrk_mount_al", "монтаж алюминия", 20.0, UnitRateType.PER_PIECE),
        WorkServiceItem("wrk_mount_concrete", "монтаж в бетон", 22.0, UnitRateType.PER_M2),
        WorkServiceItem("wrk_mount_sill_out", "монтаж отлива", 5.0, UnitRateType.PER_M),
        WorkServiceItem("wrk_mount_sill_in", "монтаж подоконника", 7.0, UnitRateType.PER_M)
    )

    fun defaultMosquitoSystems(): List<MosquitoSystemItem> = listOf(
        MosquitoSystemItem("msq_none", "не учитывать", 0.0),
        MosquitoSystemItem("msq_anticat", "сетка антикошка", 25.0),
        MosquitoSystemItem("msq_white", "сетка белая", 15.0),
        MosquitoSystemItem("msq_brown", "сетка коричневая", 17.0),
        MosquitoSystemItem("msq_window", "сетка окно", 16.0),
        MosquitoSystemItem("msq_plisse", "сетка плиссе", 30.0),
        MosquitoSystemItem("msq_gray", "сетка серая", 16.0)
    )
}
