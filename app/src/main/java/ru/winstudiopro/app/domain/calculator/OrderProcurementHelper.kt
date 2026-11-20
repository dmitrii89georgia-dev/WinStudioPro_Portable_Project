package ru.winstudiopro.app.domain.calculator

import ru.winstudiopro.app.domain.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Позиция в редакторе «Заказ стеклопакетов».
 */
data class GlazingOrderItem(
    val id: String,
    val rowIndex: Int,
    val windowIndex: Int,
    val windowName: String,
    val openingNumber: String,
    val formula: String,
    val isSandwich: Boolean,
    val widthMm: Int,
    val heightMm: Int,
    val quantity: Int,
    val singleAreaM2: Double,
    val totalAreaM2: Double,
    val isSelected: Boolean = true
)

/**
 * Позиция в редакторе «Заказ фурнитуры».
 */
data class HardwareOrderItem(
    val id: String,
    val rowIndex: Int,
    val windowIndex: Int,
    val windowName: String,
    val cellLabel: String,
    val openingType: SashOpeningType,
    val isDoor: Boolean,
    val isReverseDoor: Boolean,
    val falzWidthMm: Int,
    val falzHeightMm: Int,
    val sideLabel: String,
    val brandName: String,
    val quantity: Int,
    val note: String = "",
    val isSelected: Boolean = true
) {
    val sizeText: String
        get() = "$falzWidthMm × $falzHeightMm мм"
}

enum class CornerConnectorType(val titleRu: String) {
    FRAME("Сухари рамы"),
    SASH("Сухари створки"),
    DOOR("Сухари двери"),
    REVERSE_DOOR("Сухари обратной двери")
}

data class CornerConnectorOrderItem(
    val type: CornerConnectorType,
    val setsCount: Int,
    val cornersPerSet: Int = 4,
    val isSelected: Boolean = false
) {
    val totalCornersCount: Int get() = setsCount * cornersPerSet
}

enum class RubberType(val titleRu: String) {
    FIXED_GLAZING("Резина для глухаря"),
    FRAME_REBATE("Резина рамы на притвор"),
    SASH_DOOR_REBATE("Резина створки/двери на притвор"),
    WEDGE_SHTAPIK("Клиновая резина")
}

data class RubberOrderItem(
    val type: RubberType,
    val lengthMeters: Double,
    val isSelected: Boolean = false,
    val profileSystemId: String = "",
    val profileSystemName: String = "",
    val wedgeVariantId: String? = null,
    val wedgeThicknessMm: Float? = null,
    val variantLabel: String = "",
    val metersPerBox: Double = 200.0,
    val warehouseMeters: Double = 0.0,
    val boxesCount: Int = 0
) {
    val displayTitle: String
        get() = if (variantLabel.isNotBlank()) "${type.titleRu} ($variantLabel)" else type.titleRu

    val shortageMeters: Double
        get() = (lengthMeters - warehouseMeters).coerceAtLeast(0.0)

    val hasShortage: Boolean
        get() = lengthMeters > warehouseMeters + 0.001

    val orderedMeters: Double
        get() = if (boxesCount > 0 && metersPerBox > 0.0) boxesCount * metersPerBox else lengthMeters
}

data class ProfileSupplierOrderLine(
    val profileSystemName: String,
    val role: ProfileRole,
    val colorName: String,
    val stockBarLengthMm: Int,
    val barsCount: Int,
    val isSelected: Boolean = true
) {
    val totalLengthMeters: Double
        get() = (barsCount.coerceAtLeast(0) * stockBarLengthMm.toDouble()) / 1000.0
}

/**
 * Помощник формирования редактируемых заказов:
 * 1) Заказ стеклопакетов и сэндвич-панелей;
 * 2) Заказ фурнитуры;
 * 3) Подбор оптимальной клиновой резины, расчёт сухарей и 4 видов уплотнителя с остатками склада.
 */
object OrderProcurementHelper {

    /**
     * Автоматический подбор оптимальной клиновой резины:
     * Свободный зазор под клин = Посадочное место (A4/C3/D3/E3) − Штапик (H2) − Наружная резина глухаря − Толщина СП.
     */
    fun selectOptimalWedgeVariant(
        profile: ProfileSystemSpec,
        shtapikH2Mm: Float,
        glazingThicknessMm: Int,
        seatMm: Float
    ): WedgeRubberVariantSpec? {
        val variants = profile.rubberCatalog.wedgeVariants
        if (variants.isEmpty()) return null
        val outerFixedRubberMm = profile.rubberCatalog.fixedGlazing.thicknessMm.coerceAtLeast(0f)
        val neededWedgeMm = seatMm - shtapikH2Mm - outerFixedRubberMm - glazingThicknessMm.toFloat()
        return variants.minByOrNull { abs(it.thicknessMm - neededWedgeMm) }
    }

    fun resolveMatchedShtapik(
        window: WindowItem,
        profile: ProfileSystemSpec,
        glazingThicknessMm: Int
    ): ShtapikVariantSpec? {
        return profile.shtapikVariants.find { it.id == window.selectedShtapikId }
            ?: profile.shtapikVariants.minByOrNull { abs(it.glazingThicknessSpMm - glazingThicknessMm) }
            ?: profile.shtapikVariants.firstOrNull()
    }

    fun buildGlazingOrderItems(windowResults: List<WindowCalculationResult>): List<GlazingOrderItem> {
        val result = mutableListOf<GlazingOrderItem>()
        var rowNum = 1
        windowResults.forEachIndexed { winIdx, res ->
            res.glazingSpecs.forEachIndexed { specIdx, spec ->
                val openingCode = Regex("Пр\\.\\d+").find(spec.cellLabel)?.value
                    ?: "Пр.${specIdx + 1}"
                result.add(
                    GlazingOrderItem(
                        id = "glz_${winIdx}_${specIdx}",
                        rowIndex = rowNum++,
                        windowIndex = winIdx + 1,
                        windowName = res.windowItem.name,
                        openingNumber = openingCode,
                        formula = spec.formula,
                        isSandwich = spec.isSandwich,
                        widthMm = spec.widthMm,
                        heightMm = spec.heightMm,
                        quantity = spec.quantity,
                        singleAreaM2 = spec.singleAreaM2,
                        totalAreaM2 = spec.totalAreaM2,
                        isSelected = true
                    )
                )
            }
        }
        return result
    }

    fun buildHardwareOrderItems(
        windowResults: List<WindowCalculationResult>,
        hardwareBrandsCatalog: List<HardwareBrandItem>
    ): List<HardwareOrderItem> {
        val result = mutableListOf<HardwareOrderItem>()
        var rowNum = 1

        windowResults.forEachIndexed { winIdx, res ->
            val win = res.windowItem
            val profile = res.profileSystem
            val qty = win.quantity.coerceAtLeast(1)
            val brandName = hardwareBrandsCatalog.find { it.id == win.hardwareBrandId }?.name
                ?.takeIf { win.hardwareBrandId != "hw_none" }
                ?: "Стандарт"

            val frames = win.topology.frames.sortedBy { it.leftMm }
            var cellOrderInWin = 0
            frames.forEach { frame ->
                val sortedOpenings = frame.openings.sortedWith(
                    compareBy<OpeningCellNode>(
                        { (it.leftMm / 5f).roundToInt() },
                        { (it.topMm / 5f).roundToInt() }
                    )
                )
                sortedOpenings.forEachIndexed { opIdx, op ->
                    val props = op.properties
                    if (!props.openingType.hasSashProfile || props.openingType == SashOpeningType.BLIND_SASH) {
                        return@forEachIndexed
                    }
                    cellOrderInWin += 1
                    val leftDed = if (abs(op.leftMm - frame.leftMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                    val rightDed = if (abs(op.rightMm - frame.rightMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                    val topDed = if (abs(op.topMm - frame.topMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                    val botDed = if (abs(op.bottomMm - frame.bottomMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f

                    val lightW = max(100f, op.widthMm - leftDed - rightDed)
                    val lightH = max(100f, op.heightMm - topDed - botDed)

                    val isDoubleShtulp = props.openingType.isShtulp
                    val isVertDouble = props.openingType == SashOpeningType.VERT_SLIDE_UP ||
                        props.openingType == SashOpeningType.VERT_SLIDE_BOTH

                    val overlapMm = when {
                        props.isReverseDoorSash -> profile.revDoorOverlapMm
                        props.isDoorSash -> profile.doorOverlapMm
                        else -> profile.sashOverlapMm
                    }
                    val sashOuterW: Int
                    val sashOuterH: Int
                    if (isDoubleShtulp) {
                        val geom = WindowCadOperations.computeShtulpGeometry(frame, op, profile)
                        val gapMm = (profile.shtulpRebateG1Mm - overlapMm).coerceAtLeast(0f)
                        val leafWL = (geom.lightWLMm + overlapMm - gapMm / 2f).coerceAtLeast(100f)
                        val leafWR = (geom.lightWRMm + overlapMm - gapMm / 2f).coerceAtLeast(100f)
                        sashOuterW = (if (props.openingType.isShtulpLeftPassive) leafWR else leafWL).roundToInt()
                        sashOuterH = (lightH + 2f * overlapMm).roundToInt()
                    } else {
                        val singleLightH = if (isVertDouble) lightH / 2f else lightH
                        sashOuterW = (lightW + 2f * overlapMm).roundToInt()
                        sashOuterH = (singleLightH + 2f * overlapMm).roundToInt()
                    }
                    val falzW = (sashOuterW - 16).coerceAtLeast(250)
                    val falzH = (sashOuterH - 16).coerceAtLeast(250)

                    result.add(
                        HardwareOrderItem(
                            id = "hw_${winIdx}_${cellOrderInWin}_${op.id}",
                            rowIndex = rowNum++,
                            windowIndex = winIdx + 1,
                            windowName = win.name,
                            cellLabel = "Пр.${opIdx + 1}",
                            openingType = props.openingType,
                            isDoor = props.isDoorSash || props.isReverseDoorSash,
                            isReverseDoor = props.isReverseDoorSash,
                            falzWidthMm = falzW,
                            falzHeightMm = falzH,
                            sideLabel = resolveSideLabel(props.openingType),
                            brandName = brandName,
                            quantity = qty,
                            isSelected = true
                        )
                    )
                }
            }
        }
        return result
    }

    fun resolveSideLabel(openingType: SashOpeningType): String = when (openingType) {
        SashOpeningType.TURN_LEFT,
        SashOpeningType.TILT_TURN_LEFT,
        SashOpeningType.SLIDING_LEFT -> "Левая"
        SashOpeningType.TURN_RIGHT,
        SashOpeningType.TILT_TURN_RIGHT,
        SashOpeningType.SLIDING_RIGHT -> "Правая"
        SashOpeningType.SHTULP_TURN_L_PASSIVE_R,
        SashOpeningType.SHTULP_TILTTURN_L_PASSIVE_R -> "Акт.Л + Пас.П"
        SashOpeningType.SHTULP_PASSIVE_L_TURN_R,
        SashOpeningType.SHTULP_PASSIVE_L_TILTTURN_R -> "Пас.Л + Акт.П"
        SashOpeningType.SHTULP_TURN_TURN,
        SashOpeningType.SHTULP_TILTTURN_L_TURN_R,
        SashOpeningType.SHTULP_TURN_L_TILTTURN_R -> "Лев.+прав."
        SashOpeningType.TRANSOM_BOTTOM,
        SashOpeningType.TRANSOM_TOP -> "Откидная"
        SashOpeningType.VERT_SLIDE_UP,
        SashOpeningType.VERT_SLIDE_BOTH -> "Верт."
        SashOpeningType.FIXED_GLASS,
        SashOpeningType.BLIND_SASH -> "—"
    }

    /**
     * Подсчёт комплектов сухарей и 4 видов уплотнительной резины (без учёта остатков склада).
     */
    fun calculateCornerConnectorsAndRubber(
        windowResults: List<WindowCalculationResult>
    ): Pair<List<CornerConnectorOrderItem>, List<RubberOrderItem>> =
        calculateCornerConnectorsAndRubber(windowResults, emptyList())

    /**
     * Точный подсчёт комплектов угловых сухарей (закладных) и 4 типов уплотнительной резины
     * с автоподбором толщины клина, остатками на складе и расчётом рекомендуемых коробок.
     * По умолчанию все галочки сняты (isSelected = false).
     */
    fun calculateCornerConnectorsAndRubber(
        windowResults: List<WindowCalculationResult>,
        stockRubber: List<StockRubberItem>
    ): Pair<List<CornerConnectorOrderItem>, List<RubberOrderItem>> {
        var frameSets = 0
        var sashSets = 0
        var doorSets = 0
        var reverseDoorSets = 0

        data class RubberBucketKey(
            val profileSystemId: String,
            val type: RubberType,
            val wedgeVariantId: String?
        )

        data class RubberBucketAcc(
            val profile: ProfileSystemSpec,
            val type: RubberType,
            val wedge: WedgeRubberVariantSpec?,
            var meters: Double = 0.0
        )

        val buckets = linkedMapOf<RubberBucketKey, RubberBucketAcc>()
        var primaryProfile: ProfileSystemSpec? = null

        fun addRubberMeters(
            profile: ProfileSystemSpec,
            type: RubberType,
            wedge: WedgeRubberVariantSpec?,
            meters: Double
        ) {
            if (meters <= 0.0) return
            val key = RubberBucketKey(profile.id, type, wedge?.id)
            val acc = buckets.getOrPut(key) { RubberBucketAcc(profile, type, wedge, 0.0) }
            acc.meters += meters
        }

        windowResults.forEach { res ->
            val win = res.windowItem
            if (win.profileSystemId == "none") return@forEach
            val profile = res.profileSystem
            if (primaryProfile == null) primaryProfile = profile
            val qty = win.quantity.coerceAtLeast(1)

            val matchedShtapik = resolveMatchedShtapik(win, profile, res.glazing.thicknessMm)
            val shtapikH2 = matchedShtapik?.widthH2Mm ?: profile.shtapikWidthMm
            val baseGlassThickMm = res.glazing.thicknessMm.coerceAtLeast(4)
            val sandwichThickMm = 24

            val frames = win.topology.frames
            frameSets += frames.size * qty

            frames.forEach { frame ->
                frame.openings.forEach { op ->
                    val leftDed = if (abs(op.leftMm - frame.leftMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                    val rightDed = if (abs(op.rightMm - frame.rightMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                    val topDed = if (abs(op.topMm - frame.topMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                    val botDed = if (abs(op.bottomMm - frame.bottomMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f

                    val lightW = max(100f, op.widthMm - leftDed - rightDed)
                    val lightH = max(100f, op.heightMm - topDed - botDed)
                    val props = op.properties

                    if (!props.openingType.hasSashProfile) {
                        val fixedPerimM = 2.0 * (lightW + lightH) / 1000.0 * qty
                        val glassThick = if (props.isSandwichFilling) sandwichThickMm else baseGlassThickMm
                        val wedge = selectOptimalWedgeVariant(profile, shtapikH2, glassThick, profile.frameGlazingSeatA4Mm)
                        addRubberMeters(profile, RubberType.FIXED_GLAZING, null, fixedPerimM)
                        addRubberMeters(profile, RubberType.WEDGE_SHTAPIK, wedge, fixedPerimM)
                    } else {
                        val isDoubleShtulp = props.openingType.isShtulp
                        val isVertDouble = props.openingType == SashOpeningType.VERT_SLIDE_UP ||
                            props.openingType == SashOpeningType.VERT_SLIDE_BOTH
                        val leavesCount = if (isDoubleShtulp || isVertDouble) 2 else 1

                        when {
                            props.isReverseDoorSash -> reverseDoorSets += leavesCount * qty
                            props.isDoorSash -> doorSets += leavesCount * qty
                            else -> sashSets += leavesCount * qty
                        }

                        val overlapMm = when {
                            props.isReverseDoorSash -> profile.revDoorOverlapMm
                            props.isDoorSash -> profile.doorOverlapMm
                            else -> profile.sashOverlapMm
                        }
                        val sashProfileW = when {
                            props.isReverseDoorSash -> profile.revDoorInnerWidthE2Mm
                            props.isDoorSash -> profile.doorSashWidthMm
                            else -> profile.sashWidthMm
                        }
                        val seatMm = when {
                            props.isReverseDoorSash -> profile.revDoorGlazingSeatE3Mm
                            props.isDoorSash -> profile.doorGlazingSeatD3Mm
                            else -> profile.sashGlazingSeatC3Mm
                        }

                        val leafDims: List<Pair<Float, Float>> = if (isDoubleShtulp) {
                            val geom = WindowCadOperations.computeShtulpGeometry(frame, op, profile)
                            val gapMm = (profile.shtulpRebateG1Mm - overlapMm).coerceAtLeast(0f)
                            val leafWL = (geom.lightWLMm + overlapMm - gapMm / 2f).coerceAtLeast(100f)
                            val leafWR = (geom.lightWRMm + overlapMm - gapMm / 2f).coerceAtLeast(100f)
                            val leafH = (lightH + 2f * overlapMm).coerceAtLeast(100f)
                            listOf(leafWL to leafH, leafWR to leafH)
                        } else if (isVertDouble) {
                            val leafW = lightW + 2f * overlapMm
                            val leafH = (lightH / 2f) + 2f * overlapMm
                            listOf(leafW to leafH, leafW to leafH)
                        } else {
                            listOf((lightW + 2f * overlapMm) to (lightH + 2f * overlapMm))
                        }

                        val effMode = props.effectiveSashImpostMode()
                        leafDims.forEach { (leafOuterW, leafOuterH) ->
                            val leafPerimM = 2.0 * (leafOuterW.roundToInt() + leafOuterH.roundToInt()) / 1000.0 * qty
                            if (props.openingType != SashOpeningType.BLIND_SASH) {
                                addRubberMeters(profile, RubberType.FRAME_REBATE, null, leafPerimM)
                                addRubberMeters(profile, RubberType.SASH_DOOR_REBATE, null, leafPerimM)
                            }
                            val sashInnerW = max(80f, leafOuterW - 2f * sashProfileW)
                            val sashInnerH = max(80f, leafOuterH - 2f * sashProfileW)

                            val paneBoxes: List<Triple<Int, Float, Float>> = when (effMode) {
                                SashImpostMode.NONE -> listOf(Triple(0, sashInnerW, sashInnerH))
                                SashImpostMode.V1 -> {
                                    val pw = max(60f, (sashInnerW - profile.impostWidthMm) / 2f)
                                    listOf(Triple(0, pw, sashInnerH), Triple(1, pw, sashInnerH))
                                }
                                SashImpostMode.H1 -> {
                                    val botH = (props.sashImpostHeightFromBottomMm - sashProfileW - profile.impostWidthMm / 2f)
                                        .coerceIn(80f, (sashInnerH - 120f).coerceAtLeast(80f))
                                    val topH = (sashInnerH - botH - profile.impostWidthMm).coerceAtLeast(80f)
                                    listOf(Triple(0, sashInnerW, topH), Triple(1, sashInnerW, botH))
                                }
                                SashImpostMode.V2 -> {
                                    val pw = max(60f, (sashInnerW - 2f * profile.impostWidthMm) / 3f)
                                    listOf(Triple(0, pw, sashInnerH), Triple(1, pw, sashInnerH), Triple(2, pw, sashInnerH))
                                }
                                SashImpostMode.H2 -> {
                                    val ph = max(60f, (sashInnerH - 2f * profile.impostWidthMm) / 3f)
                                    listOf(Triple(0, sashInnerW, ph), Triple(1, sashInnerW, ph), Triple(2, sashInnerW, ph))
                                }
                            }
                            paneBoxes.forEach { (paneIdx, pw, ph) ->
                                val glassThick = if (props.isPaneSandwich(paneIdx)) sandwichThickMm else baseGlassThickMm
                                val wedge = selectOptimalWedgeVariant(profile, shtapikH2, glassThick, seatMm)
                                addRubberMeters(
                                    profile,
                                    RubberType.WEDGE_SHTAPIK,
                                    wedge,
                                    2.0 * (pw + ph) / 1000.0 * qty
                                )
                            }
                        }
                    }
                }
            }
        }

        val connectors = listOf(
            CornerConnectorOrderItem(CornerConnectorType.FRAME, frameSets, 4, false),
            CornerConnectorOrderItem(CornerConnectorType.SASH, sashSets, 4, false),
            CornerConnectorOrderItem(CornerConnectorType.DOOR, doorSets, 4, false),
            CornerConnectorOrderItem(CornerConnectorType.REVERSE_DOOR, reverseDoorSets, 4, false)
        )

        val fallbackProfile = primaryProfile ?: DefaultCatalogs.defaultProfileSystems().first()

        fun buildRubberItem(
            profile: ProfileSystemSpec,
            type: RubberType,
            wedge: WedgeRubberVariantSpec?,
            requiredM: Double
        ): RubberOrderItem {
            val metersPerBox = when (type) {
                RubberType.FIXED_GLAZING -> profile.rubberCatalog.fixedGlazing.metersPerBox
                RubberType.FRAME_REBATE -> profile.rubberCatalog.frameRebate.metersPerBox
                RubberType.SASH_DOOR_REBATE -> profile.rubberCatalog.sashDoorRebate.metersPerBox
                RubberType.WEDGE_SHTAPIK -> wedge?.metersPerBox
                    ?: profile.rubberCatalog.wedgeVariants.firstOrNull()?.metersPerBox
                    ?: 210.0
            }.coerceAtLeast(1.0)

            val stockMeters = findRubberWarehouseMeters(
                stockRubber = stockRubber,
                profileSystemId = profile.id,
                rubberType = type,
                wedgeVariantId = wedge?.id,
                wedgeThicknessMm = wedge?.thicknessMm
            )
            val deficitM = (requiredM - stockMeters).coerceAtLeast(0.0)
            val suggestedBoxes = when {
                deficitM > 0.001 -> kotlin.math.ceil(deficitM / metersPerBox).toInt().coerceAtLeast(1)
                requiredM > 0.001 -> kotlin.math.ceil(requiredM / metersPerBox).toInt().coerceAtLeast(1)
                else -> 0
            }
            val label = wedge?.name ?: ""
            return RubberOrderItem(
                type = type,
                lengthMeters = requiredM,
                isSelected = false,
                profileSystemId = profile.id,
                profileSystemName = profile.name,
                wedgeVariantId = wedge?.id,
                wedgeThicknessMm = wedge?.thicknessMm,
                variantLabel = label,
                metersPerBox = metersPerBox,
                warehouseMeters = stockMeters,
                boxesCount = suggestedBoxes
            )
        }

        val rubbers = mutableListOf<RubberOrderItem>()
        listOf(RubberType.FIXED_GLAZING, RubberType.FRAME_REBATE, RubberType.SASH_DOOR_REBATE).forEach { rType ->
            val matched = buckets.values.filter { it.type == rType }
            if (matched.isEmpty()) {
                rubbers.add(buildRubberItem(fallbackProfile, rType, null, 0.0))
            } else {
                matched.forEach { acc ->
                    rubbers.add(buildRubberItem(acc.profile, rType, null, acc.meters))
                }
            }
        }

        val wedgeBuckets = buckets.values.filter { it.type == RubberType.WEDGE_SHTAPIK }
        if (wedgeBuckets.isEmpty()) {
            val defaultWedge = fallbackProfile.rubberCatalog.wedgeVariants.firstOrNull()
            rubbers.add(buildRubberItem(fallbackProfile, RubberType.WEDGE_SHTAPIK, defaultWedge, 0.0))
        } else {
            wedgeBuckets.forEach { acc ->
                rubbers.add(buildRubberItem(acc.profile, RubberType.WEDGE_SHTAPIK, acc.wedge, acc.meters))
            }
        }

        return connectors to rubbers
    }

    fun findRubberWarehouseMeters(
        stockRubber: List<StockRubberItem>,
        profileSystemId: String,
        rubberType: RubberType,
        wedgeVariantId: String? = null,
        wedgeThicknessMm: Float? = null
    ): Double {
        return stockRubber.filter { item ->
            item.profileSystemId == profileSystemId &&
                item.rubberType == rubberType &&
                (rubberType != RubberType.WEDGE_SHTAPIK ||
                    (wedgeVariantId != null && item.wedgeVariantId == wedgeVariantId) ||
                    (wedgeThicknessMm != null && item.wedgeThicknessMm != null && abs(item.wedgeThicknessMm - wedgeThicknessMm) < 0.05f))
        }.sumOf { it.metersInStock.coerceAtLeast(0.0) }
    }
}
