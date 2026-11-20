package ru.winstudiopro.app.domain.model

import ru.winstudiopro.app.domain.calculator.RubberType

data class WindowPresetTemplate(
    val id: String,
    val titleRu: String,
    val subtitleRu: String,
    val buildTopology: () -> WindowBlockTopology
)

object WindowPresetTemplates {
    private fun single1500(): WindowBlockTopology = WindowCadOperations.single1500Topology()

    val all: List<WindowPresetTemplate> = listOf(
        WindowPresetTemplate("cfg_1", "1 секция", "1500 × 1500") { single1500() },
        WindowPresetTemplate("cfg_2v", "2 секции (верт.)", "1500 × 1500") {
            WindowCadOperations.splitOpening(single1500(), "op1", ImpostDirection.VERTICAL, 2)
        },
        WindowPresetTemplate("cfg_3v", "3 секции (верт.)", "1500 × 1500") {
            WindowCadOperations.splitOpening(single1500(), "op1", ImpostDirection.VERTICAL, 3)
        },
        WindowPresetTemplate("cfg_4v", "4 секции (верт.)", "2000 × 1500") {
            val base = WindowCadOperations.resizeTotalDimensions(single1500(), 2000f, 1500f)
            val s1 = WindowCadOperations.splitOpening(base, "op1", ImpostDirection.VERTICAL, 2)
            val s2 = WindowCadOperations.splitOpening(s1, "op1_v0", ImpostDirection.VERTICAL, 2)
            WindowCadOperations.splitOpening(s2, "op1_v1", ImpostDirection.VERTICAL, 2)
        },
        WindowPresetTemplate("cfg_2h", "2 секции (гориз.)", "1500 × 1500") {
            WindowCadOperations.splitOpening(single1500(), "op1", ImpostDirection.HORIZONTAL, 2)
        },
        WindowPresetTemplate("cfg_3h", "3 секции (гориз.)", "1500 × 1500") {
            WindowCadOperations.splitOpening(single1500(), "op1", ImpostDirection.HORIZONTAL, 3)
        },
        WindowPresetTemplate("cfg_balc_left_win", "Балкон (Окно + Дверь)", "2100 × 2150") {
            WindowBlockTopology(
                frames = listOf(
                    FrameBlockNode(
                        id = "f_win",
                        leftMm = 0f,
                        topMm = 0f,
                        rightMm = 1300f,
                        bottomMm = 1400f,
                        openings = listOf(OpeningCellNode("op_w", "f_win", 0f, 0f, 1300f, 1400f))
                    ),
                    FrameBlockNode(
                        id = "f_door",
                        leftMm = 1300f,
                        topMm = 0f,
                        rightMm = 2100f,
                        bottomMm = 2150f,
                        openings = listOf(OpeningCellNode("op_d", "f_door", 1300f, 0f, 2100f, 2150f))
                    )
                )
            )
        },
        WindowPresetTemplate("cfg_balc_center_door", "Балкон (Окно + Дверь + Окно)", "2700 × 2150") {
            WindowBlockTopology(
                frames = listOf(
                    FrameBlockNode(
                        id = "f_w1",
                        leftMm = 0f,
                        topMm = 0f,
                        rightMm = 950f,
                        bottomMm = 1400f,
                        openings = listOf(OpeningCellNode("op_w1", "f_w1", 0f, 0f, 950f, 1400f))
                    ),
                    FrameBlockNode(
                        id = "f_door",
                        leftMm = 950f,
                        topMm = 0f,
                        rightMm = 1750f,
                        bottomMm = 2150f,
                        openings = listOf(OpeningCellNode("op_d", "f_door", 950f, 0f, 1750f, 2150f))
                    ),
                    FrameBlockNode(
                        id = "f_w2",
                        leftMm = 1750f,
                        topMm = 0f,
                        rightMm = 2700f,
                        bottomMm = 1400f,
                        openings = listOf(OpeningCellNode("op_w2", "f_w2", 1750f, 0f, 2700f, 1400f))
                    )
                )
            )
        },
        WindowPresetTemplate("cfg_balc_right_win", "Балкон (Дверь + Окно)", "2100 × 2150") {
            WindowBlockTopology(
                frames = listOf(
                    FrameBlockNode(
                        id = "f_door",
                        leftMm = 0f,
                        topMm = 0f,
                        rightMm = 800f,
                        bottomMm = 2150f,
                        openings = listOf(OpeningCellNode("op_d", "f_door", 0f, 0f, 800f, 2150f))
                    ),
                    FrameBlockNode(
                        id = "f_win",
                        leftMm = 800f,
                        topMm = 0f,
                        rightMm = 2100f,
                        bottomMm = 1400f,
                        openings = listOf(OpeningCellNode("op_w", "f_win", 800f, 0f, 2100f, 1400f))
                    )
                )
            )
        },
        WindowPresetTemplate("cfg_transom_2", "Фрамуга + 2 секции", "1500 × 1800") {
            val base = WindowCadOperations.resizeTotalDimensions(single1500(), 1500f, 1800f)
            val sh = WindowCadOperations.splitOpening(base, "op1", ImpostDirection.HORIZONTAL, 2)
            val topFixed = WindowCadOperations.updateChainSegmentLength(sh, false, 0, 450f)
            WindowCadOperations.splitOpening(topFixed, "op1_h1", ImpostDirection.VERTICAL, 2)
        },
        WindowPresetTemplate("cfg_transom_3", "Фрамуга + 3 секции", "2100 × 1800") {
            val base = WindowCadOperations.resizeTotalDimensions(single1500(), 2100f, 1800f)
            val sh = WindowCadOperations.splitOpening(base, "op1", ImpostDirection.HORIZONTAL, 2)
            val topFixed = WindowCadOperations.updateChainSegmentLength(sh, false, 0, 450f)
            WindowCadOperations.splitOpening(topFixed, "op1_h1", ImpostDirection.VERTICAL, 3)
        },
        WindowPresetTemplate("cfg_2v_top_split", "2 секции + фрамуга", "1500 × 1800") {
            val base = WindowCadOperations.resizeTotalDimensions(single1500(), 1500f, 1800f)
            val sv = WindowCadOperations.splitOpening(base, "op1", ImpostDirection.VERTICAL, 2)
            val sh = WindowCadOperations.splitOpening(sv, "op1_v1", ImpostDirection.HORIZONTAL, 2)
            WindowCadOperations.updateChainSegmentLength(sh, false, 0, 450f)
        }
    )
}

/**
 * Конструкция (изделие) внутри заказа.
 */
data class WindowItem(
    val id: String,
    val name: String,
    val quantity: Int = 1,
    val topology: WindowBlockTopology,
    val profileSystemId: String = "al_test_system",
    val selectedShtapikId: String = "",
    val glazingId: String = "sp_24",
    val roundGlassTo5Mm: Boolean = false,
    val colorId: String = "col_anthracite",
    val customColorName: String = "",
    val customColorRal: String = "",
    val customColorHex: Long = 0xFF607D8B,
    val customPaintingCost: Double = 0.0, // Фиксированная сумма за покраску в малярке за весь проект/позицию
    val customColorUseRawForStock: Boolean = false,
    val innerSillId: String = "sill_in_none",
    val outerSillId: String = "sill_out_none",
    val financialSchemeId: String = "fin_none",
    val hardwareBrandId: String = "hw_none",
    val mosquitoSystemId: String = "msq_none",
    val selectedExtraIds: List<String> = emptyList(),
    val selectedWorkIds: List<String> = emptyList(),
    val addons: WindowPerimeterAddons = WindowPerimeterAddons(hasSeatProfile = false),
    val includeInstallationWork: Boolean = false,
    val installationPricePerM2: Double = 15.0,
    val manualUnitPriceOverride: Double? = null,
    val note: String = "",
    val assemblySchemeOverride: AssemblySchemeType? = null
)

/**
 * Хлыст или деловой остаток профиля на складе (с группировкой по папкам профильных систем и цвету).
 * Поддерживает как целые хлысты в наличии (приход материала 5500..8000 мм), так и деловые остатки после раскроя.
 */
data class StockRemnantBar(
    val id: String,
    val profileSystemId: String,
    val role: ProfileRole,
    val lengthMm: Int,
    val quantity: Int = 1,
    val isFullStockBar: Boolean = false,
    val colorName: String = "Белый",
    val colorHex: Long = 0xFFFFFFFF
)

/**
 * Складской остаток уплотнительной резины (учёт и списание в метрах п.м. по профильной системе).
 */
data class StockRubberItem(
    val id: String,
    val profileSystemId: String,
    val rubberType: RubberType,
    val wedgeVariantId: String? = null,
    val wedgeThicknessMm: Float? = null,
    val label: String = "",
    val metersInStock: Double = 0.0
)

enum class OrderStatus(
    val titleRu: String,
    val titleEn: String,
    val badgeColorHex: Long,
    val showDeadlineSmiley: Boolean = false
) {
    DRAFT("Черновик", "Draft", 0xFF787880, false),
    MEASUREMENT("Замер", "Measure", 0xFF0A84FF, false),
    APPROVAL("Согласование", "Approval", 0xFF9C42CC, false),
    CONTRACT("Договор", "Contract", 0xFF0097A7, true),
    PRODUCTION("Производство", "Production", 0xFFEF7D00, true),
    READY_FOR_INSTALL("Готово к монтажу", "Ready", 0xFF249A41, true),
    INSTALLATION("Монтаж", "Install", 0xFF524FD1, true),
    CLOSED("Заказ закрыт", "Closed", 0xFF4B606B, false);

    companion object {
        fun fromStorageName(raw: String?): OrderStatus {
            return when (raw?.trim()) {
                "CONFIRMED" -> APPROVAL
                "IN_WORKSHOP" -> PRODUCTION
                "READY" -> READY_FOR_INSTALL
                "COMPLETED" -> CLOSED
                else -> entries.find { it.name == raw?.trim() } ?: DRAFT
            }
        }
    }
}

enum class DeadlineMood {
    HAPPY_GREEN,
    CAUTIOUS_YELLOW,
    SCARED_RED
}

data class ContractDeadlineState(
    val daysRemaining: Int,
    val deadlineMillis: Long,
    val mood: DeadlineMood
)

/**
 * Заказ клиента (содержит произвольное количество окон/конструкций).
 */
data class CustomerOrder(
    val id: String,
    val orderNumber: String,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String = "",
    val address: String,
    val orderNote: String = "",
    val createdDate: String,
    val windows: List<WindowItem>,
    val status: OrderStatus = OrderStatus.DRAFT,
    val contractDateMillis: Long? = null,
    val contractTermDays: Int = 28,
    val discountPercent: Double = 0.0,
    val markupPercent: Double = 0.0,
    val taxVatPercent: Double = 18.0,
    val manualOrderTotalOverride: Double? = null,
    val currencySymbol: String = "доллар",
    val customCuttingAllocations: List<CutBarAllocation>? = null,
    val stockDeductedForProduction: Boolean = false,
    val floorAssemblyCount: Int = 0,
    val extraAssemblyOpsCost: Double = 0.0,
    val extraAssemblyOpsNote: String = "",
    val selectedOrderServices: List<SelectedOrderService> = emptyList()
) {
    fun computeDeadlineState(nowMillis: Long = System.currentTimeMillis()): ContractDeadlineState? {
        if (!status.showDeadlineSmiley) return null
        val startMs = contractDateMillis ?: return null
        val termDays = contractTermDays.coerceAtLeast(1)

        fun toLocalEpochDay(millis: Long): Long {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
            val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
            }
            return utc.timeInMillis / 86_400_000L
        }

        val startDay = toLocalEpochDay(startMs)
        val todayDay = toLocalEpochDay(nowMillis)
        val deadlineDay = startDay + termDays
        val daysLeft = (deadlineDay - todayDay).toInt()

        val endCal = java.util.Calendar.getInstance().apply {
            timeInMillis = startMs
            add(java.util.Calendar.DAY_OF_YEAR, termDays)
        }
        val mood = when {
            daysLeft > 7 -> DeadlineMood.HAPPY_GREEN
            daysLeft >= 4 -> DeadlineMood.CAUTIOUS_YELLOW
            else -> DeadlineMood.SCARED_RED
        }
        return ContractDeadlineState(
            daysRemaining = daysLeft,
            deadlineMillis = endCal.timeInMillis,
            mood = mood
        )
    }
}

enum class ProfileRole(val titleRu: String) {
    FRAME("Профиль рамы"),
    IMPOST("Профиль импоста"),
    SASH("Профиль створки (оконной)"),
    DOOR_SASH("Профиль створки (дверной)"),
    SHTAPIK("Штапик"),
    SEAT_PROFILE("Подставочный профиль"),
    DOORSILL("Порог"),
    EXTENDER("Доборный профиль")
}

data class ProfileCutPiece(
    val windowName: String,
    val profileSystemId: String,
    val profileSystemName: String,
    val materialType: ProfileMaterialType,
    val role: ProfileRole,
    val positionLabel: String,
    val shortTag: String = "1",
    val nominalLengthMm: Int,
    val cutLengthMm: Int,
    val leftAngleDeg: Int,
    val rightAngleDeg: Int,
    val quantity: Int,
    val colorName: String = "Антрацит",
    val colorHex: Long = 0xFF383E42
)

data class GlazingSpecItem(
    val windowName: String,
    val cellLabel: String,
    val formula: String,
    val isSandwich: Boolean,
    val widthMm: Int,
    val heightMm: Int,
    val quantity: Int,
    val singleAreaM2: Double,
    val totalAreaM2: Double
)

data class SashHardwareSpecItem(
    val windowName: String,
    val cellLabel: String,
    val openingType: SashOpeningType,
    val isDoor: Boolean,
    val falzWidthMm: Int,
    val falzHeightMm: Int,
    val quantity: Int,
    val hasMosquitoNet: Boolean,
    val sizeRangeGroup: String,
    val costPerSet: Double
)

data class WindowCalculationResult(
    val windowItem: WindowItem,
    val profileSystem: ProfileSystemSpec,
    val glazing: GlazingCatalogItem,
    val color: ColorCatalogItem,
    val totalAreaM2: Double,
    val framePerimeterM: Double,
    val impostsLengthM: Double,
    val sashPerimeterM: Double,
    val doorSashPerimeterM: Double,
    val shtapikPerimeterM: Double,
    val rubberPerimeterM: Double,
    val glazingAreaM2: Double,
    val sandwichAreaM2: Double,
    val cornerConnectorsCount: Int = 0,
    val costFrame: Double,
    val costImposts: Double,
    val costSashes: Double,
    val costDoorSashes: Double,
    val costCornerConnectors: Double = 0.0,
    val costShtapik: Double,
    val costRubber: Double,
    val costShtapikAndRubber: Double,
    val costGlazingOnly: Double = 0.0,
    val costSandwichOnly: Double = 0.0,
    val costGlazingAndSandwich: Double,
    val costHardware: Double,
    val costMosquitoNets: Double,
    val costSillsAndAddons: Double,
    val costExtrasOnly: Double = 0.0,
    val costWorksOnly: Double = 0.0,
    val costExtrasAndWorks: Double = 0.0,
    val costColorMarkup: Double,
    val costCustomPainting: Double = 0.0,
    val cutColorName: String = "Антрацит",
    val cutColorHex: Long = 0xFF383E42,
    val costInstallationWork: Double,
    val rawUnitCostBeforeDiscount: Double = 0.0,
    val discountPercentApplied: Double = 0.0,
    val unitTotalCost: Double,
    val lineTotalCost: Double,
    val profileCuts: List<ProfileCutPiece>,
    val glazingSpecs: List<GlazingSpecItem>,
    val hardwareSpecs: List<SashHardwareSpecItem>,
    val matchedWedgeSummary: String = "",
    val costAssembly: Double = 0.0,
    val costInstallation: Double = 0.0,
    val costOrderServices: Double = 0.0,
    val unitPrimeCost: Double = 0.0
)

data class WindowTurnkeyPriceBreakdown(
    val windowId: String,
    val quantity: Int,
    val primeCostLine: Double,
    val unitBaseSale: Double,
    val lineBaseSale: Double,
    val discountPercent: Double,
    val unitDiscountedSale: Double,
    val lineDiscountedSale: Double
)

data class OrderFinancialSummary(
    val totalWindowsBaseCost: Double,
    val totalWindowsDiscountedCost: Double,
    val totalWindowsAssemblyCost: Double,
    val floorAssemblyCost: Double,
    val extraAssemblyOpsCost: Double,
    val totalAssemblyWithFloorAndOps: Double,
    val totalInstallationCost: Double,
    val totalOrderServicesCost: Double,
    val orderPrimeCost: Double,
    val orderSaleBeforeDiscountAndVat: Double,
    val orderSaleAfterDiscountBeforeVat: Double,
    val discountAmount: Double,
    val vatAmount: Double,
    val totalWithVat: Double,
    val totalMaterialsCost: Double = 0.0,
    val markupMultiplierApplied: Double = 1.40,
    val isBelowPrimeCost: Boolean = false,
    val windowBreakdowns: List<WindowTurnkeyPriceBreakdown> = emptyList()
) {
    fun windowBreakdownFor(windowId: String): WindowTurnkeyPriceBreakdown? {
        return windowBreakdowns.find { it.windowId == windowId }
    }
}

data class CutBarAllocation(
    val barNumber: Int,
    val profileSystemName: String,
    val role: ProfileRole,
    val isFromRemnantStock: Boolean,
    val sourceLengthMm: Int,
    val cuts: List<AllocatedCutPiece>,
    val usedWithKerfMm: Int,
    val remnantLengthMm: Int,
    val isUsableBusinessRemnant: Boolean,
    val colorName: String = "Антрацит",
    val colorHex: Long = 0xFF383E42
)

data class AllocatedCutPiece(
    val windowName: String,
    val positionLabel: String,
    val shortTag: String = "1",
    val cutLengthMm: Int,
    val leftAngleDeg: Int,
    val rightAngleDeg: Int
)

data class ProfileProcurementSummary(
    val profileSystemId: String,
    val profileSystemName: String,
    val role: ProfileRole,
    val stockBarLengthMm: Int,
    val usedRemnantBarsCount: Int,
    val newFullBarsToBuyCount: Int,
    val totalCutPiecesCount: Int,
    val totalNetLengthM: Double,
    val wastePercent: Double,
    val newBusinessRemnantsMm: List<Int>,
    val colorName: String = "Антрацит",
    val colorHex: Long = 0xFF383E42
)
