package ru.winstudiopro.app.domain.calculator

import ru.winstudiopro.app.domain.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object WindowBomAndCostEngine {

    private fun finalizeGlassDim(rawMm: Float, roundTo5Mm: Boolean): Int {
        val exactInt = max(80f, rawMm).roundToInt()
        return if (roundTo5Mm) {
            ((exactInt + 2) / 5) * 5
        } else {
            exactInt
        }
    }

    /**
     * Разрешает цвет конструкции с приоритетом:
     * 1) Индивидуальный цвет клиента (CUSTOM_CLIENT_COLOR_ID)
     * 2) Каталог цветов выбранной профильной системы
     * 3) Прежний общий каталог цветов (для обратной совместимости старых заказов)
     */
    fun resolveWindowColor(
        window: WindowItem,
        profile: ProfileSystemSpec,
        legacyColorCatalog: List<ColorCatalogItem> = DefaultCatalogs.defaultColors()
    ): ColorCatalogItem {
        if (window.colorId == CUSTOM_CLIENT_COLOR_ID) {
            val baseName = window.customColorName.trim().ifBlank { "Цвет клиента" }
            val label = if (window.customColorRal.isNotBlank()) {
                "$baseName (RAL ${window.customColorRal.trim()})"
            } else {
                baseName
            }
            return ColorCatalogItem(
                id = CUSTOM_CLIENT_COLOR_ID,
                name = label,
                hexColor = window.customColorHex,
                priceMarkupPercent = 0.0
            )
        }
        profile.colors.find { it.id == window.colorId }?.let { c ->
            val label = if (c.ralCode.isNotBlank()) "${c.name} (${c.ralCode})" else c.name
            return ColorCatalogItem(
                id = c.id,
                name = label,
                hexColor = c.hexColor,
                priceMarkupPercent = c.priceMarkupPercent
            )
        }
        legacyColorCatalog.find { it.id == window.colorId }?.let { legacy ->
            return legacy
        }
        profile.colors.firstOrNull()?.let { firstC ->
            val label = if (firstC.ralCode.isNotBlank()) "${firstC.name} (${firstC.ralCode})" else firstC.name
            return ColorCatalogItem(
                id = firstC.id,
                name = label,
                hexColor = firstC.hexColor,
                priceMarkupPercent = firstC.priceMarkupPercent
            )
        }
        return legacyColorCatalog.firstOrNull()
            ?: ColorCatalogItem("col_anthracite", "Антрацит (7016)", 0xFF383E42, 0.0)
    }

    /**
     * Возвращает имя и HEX цвета профиля для карт 1D-раскроя и учёта на складе.
     */
    fun resolveWindowCutColor(
        window: WindowItem,
        profile: ProfileSystemSpec,
        legacyColorCatalog: List<ColorCatalogItem> = DefaultCatalogs.defaultColors()
    ): Pair<String, Long> {
        if (window.colorId == CUSTOM_CLIENT_COLOR_ID) {
            return if (window.customColorUseRawForStock) {
                RAW_UNPAINTED_COLOR_NAME to 0xFFC0C0C0L
            } else {
                val baseName = window.customColorName.trim().ifBlank { "Цвет клиента" }
                val label = if (window.customColorRal.isNotBlank()) {
                    "$baseName (${window.customColorRal.trim()})"
                } else {
                    baseName
                }
                label to window.customColorHex
            }
        }
        profile.colors.find { it.id == window.colorId }?.let { c ->
            return c.name to c.hexColor
        }
        if (window.colorId == "none") {
            return "Белый" to 0xFFFFFFFFL
        }
        legacyColorCatalog.find { it.id == window.colorId }?.let { legacy ->
            return legacy.name.replaceFirstChar { it.uppercase() } to legacy.hexColor
        }
        profile.colors.firstOrNull()?.let { firstC ->
            return firstC.name to firstC.hexColor
        }
        return "Антрацит" to 0xFF383E42L
    }

    fun calculateWindow(
        window: WindowItem,
        profileSystems: List<ProfileSystemSpec>,
        glazingCatalog: List<GlazingCatalogItem>,
        colorCatalog: List<ColorCatalogItem>,
        sillsCatalog: List<SillCatalogItem>,
        extraAccessoriesCatalog: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
        workServicesCatalog: List<WorkServiceItem> = DefaultCatalogs.defaultWorkServices(),
        hardwareBrandsCatalog: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands(),
        financialSchemesCatalog: List<FinancialSchemeItem> = DefaultCatalogs.defaultFinancialSchemes(),
        windowIndexInOrder: Int = 0,
        assemblyPricingCatalog: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog()
    ): WindowCalculationResult {
        val winNum = if (windowIndexInOrder > 0) {
            windowIndexInOrder
        } else {
            Regex("\\d+").find(window.name)?.value?.toIntOrNull() ?: 1
        }
        val isNoSystemSelected = window.profileSystemId == "none"
        val profile = if (isNoSystemSelected) {
            ProfileSystemSpec(
                id = "none",
                name = "не учитывать",
                materialType = ProfileMaterialType.ALUMINUM,
                priceFramePerM = 0.0,
                priceImpostPerM = 0.0,
                priceSashPerM = 0.0,
                priceDoorSashPerM = 0.0,
                priceShtapikPerM = 0.0,
                priceRubberPerM = 0.0,
                priceCornerConnectorFrame = 0.0,
                priceCornerConnectorSash = 0.0,
                priceCornerConnectorDoorSash = 0.0,
                priceImpostConnector = 0.0,
                priceMechanicTurn = 0.0,
                priceMechanicTiltTurn = 0.0,
                priceMechanicTransom = 0.0,
                priceMechanicSliding = 0.0,
                priceMechanicShtulp = 0.0,
                priceDoorHardware = 0.0,
                priceCornerProfilePerM = 0.0,
                priceShtulpPerM = 0.0,
                priceSeatProfilePerM = 0.0,
                priceDoorsillPerM = 0.0,
                rubberCatalog = ProfileRubberCatalog(
                    fixedGlazing = RubberBoxSpec(name = "", metersPerBox = 208.0, pricePerBox = 0.0),
                    wedgeVariants = listOf(WedgeRubberVariantSpec("w_none", "", "", 3.6f, 210.0, 0.0)),
                    frameRebate = RubberBoxSpec(name = "", metersPerBox = 365.0, pricePerBox = 0.0),
                    sashDoorRebate = RubberBoxSpec(name = "", metersPerBox = 357.0, pricePerBox = 0.0)
                )
            )
        } else {
            profileSystems.find { it.id == window.profileSystemId }
                ?: profileSystems.firstOrNull { it.id != "al_test_system" }
                ?: profileSystems.first()
        }

        val glazing = glazingCatalog.find { it.id == window.glazingId }
            ?: glazingCatalog.first()
        val sandwichItem = glazingCatalog.find { it.isSandwich }
            ?: GlazingCatalogItem("sandwich_24", "сендвич", "Сэндвич 24", 24, true, 18.0)
        val matchedShtapik = profile.shtapikVariants.find { it.id == window.selectedShtapikId }
            ?: profile.shtapikVariants.minByOrNull { abs(it.glazingThicknessSpMm - glazing.thicknessMm) }
            ?: profile.shtapikVariants.firstOrNull()
        val activeShtapikPricePerM = matchedShtapik?.pricePerM ?: profile.priceShtapikPerM
        val shtapikNameSuffix = if (matchedShtapik != null) " [${matchedShtapik.name}]" else ""
        val isAluminumSystem = profile.materialType == ProfileMaterialType.ALUMINUM
        val shtapikCutAngle = if (isAluminumSystem) 90 else 45
        val shtapikVertDeductBothSidesMm = if (isAluminumSystem) {
            2f * (matchedShtapik?.heightH1Mm ?: profile.shtapikWidthMm)
        } else {
            0f
        }
        val color = resolveWindowColor(window, profile, colorCatalog)
        val (cutColorName, cutColorHex) = resolveWindowCutColor(window, profile, colorCatalog)
        val innerSill = sillsCatalog.find { it.id == window.innerSillId }
        val outerSill = sillsCatalog.find { it.id == window.outerSillId }
        val finScheme = financialSchemesCatalog.find { it.id == window.financialSchemeId }
        val hwBrand = hardwareBrandsCatalog.find { it.id == window.hardwareBrandId }
        val mosqSys = DefaultCatalogs.defaultMosquitoSystems().find { it.id == window.mosquitoSystemId }

        val qty = window.quantity.coerceAtLeast(1)
        val weld2Sides = (2f * profile.effectiveWeldAllowancePerCornerMm).roundToInt()

        val profileCuts = mutableListOf<ProfileCutPiece>()
        val glazingSpecs = mutableListOf<GlazingSpecItem>()
        val hardwareSpecs = mutableListOf<SashHardwareSpecItem>()

        var framePerimeterM = 0.0
        var impostsLengthM = 0.0
        var impostPiecesCount = 0
        var sashPerimeterM = 0.0
        var doorSashPerimeterM = 0.0
        var shtulpLengthM = 0.0
        var windowSashLeavesTotalCount = 0
        var doorSashLeavesTotalCount = 0
        val activeShtapikH2 = matchedShtapik?.widthH2Mm ?: profile.shtapikWidthMm
        var shtapikPerimeterM = 0.0
        var rubberPerimeterM = 0.0
        var fixedRubberPerUnitM = 0.0
        var rebateRubberPerUnitM = 0.0
        var wedgeCostPerUnit = 0.0
        val usedWedgeLabels = mutableListOf<String>()
        var glazingAreaM2 = 0.0
        var sandwichAreaM2 = 0.0
        var hardwareCostTotal = 0.0
        var mosquitoCostTotal = 0.0
        var glazingBarsLengthM = 0.0

        val frames = window.topology.frames
        val totalWindowSashesInWin = frames.sumOf { f ->
            f.openings.count { it.properties.openingType.hasSashProfile && !it.properties.isDoorSash && !it.properties.isReverseDoorSash }
        }
        val totalDoorSashesInWin = frames.sumOf { f ->
            f.openings.count { it.properties.openingType.hasSashProfile && (it.properties.isDoorSash || it.properties.isReverseDoorSash) }
        }
        var impCounter = 0
        var sashCounter = 0
        var doorCounter = 0

        frames.forEachIndexed { fIdx, frame ->
            val fLabel = if (frames.size > 1) "Рама ${fIdx + 1}" else "Рама"
            val frameShortTag = if (frames.size > 1) "$winNum,${fIdx + 1}" else "$winNum"
            val fw = frame.widthMm.roundToInt()
            val fh = frame.heightMm.roundToInt()

            profileCuts.add(
                ProfileCutPiece(
                    windowName = window.name,
                    profileSystemId = profile.id,
                    profileSystemName = profile.name,
                    materialType = profile.materialType,
                    role = ProfileRole.FRAME,
                    positionLabel = "$fLabel (Гориз. верх/низ)",
                    shortTag = frameShortTag,
                    nominalLengthMm = fw,
                    cutLengthMm = fw + weld2Sides,
                    leftAngleDeg = 45,
                    rightAngleDeg = 45,
                    quantity = 2 * qty
                )
            )
            profileCuts.add(
                ProfileCutPiece(
                    windowName = window.name,
                    profileSystemId = profile.id,
                    profileSystemName = profile.name,
                    materialType = profile.materialType,
                    role = ProfileRole.FRAME,
                    positionLabel = "$fLabel (Верт. лев/прав)",
                    shortTag = frameShortTag,
                    nominalLengthMm = fh,
                    cutLengthMm = fh + weld2Sides,
                    leftAngleDeg = 45,
                    rightAngleDeg = 45,
                    quantity = 2 * qty
                )
            )
            framePerimeterM += 2.0 * (frame.widthMm + frame.heightMm) / 1000.0

            // Импосты рамы: сначала по порядку вертикальные (слева направо), потом горизонтальные (сверху вниз)
            val sortedImposts = frame.imposts.sortedWith(
                compareBy<ImpostSegmentNode>(
                    { if (it.direction == ImpostDirection.VERTICAL) 0 else 1 },
                    { if (it.direction == ImpostDirection.VERTICAL) (it.startX / 5f).roundToInt() else (it.startY / 5f).roundToInt() },
                    { if (it.direction == ImpostDirection.VERTICAL) (it.startY / 5f).roundToInt() else (it.startX / 5f).roundToInt() }
                )
            )

            sortedImposts.forEach { imp ->
                impCounter += 1
                val impShortTag = "$winNum,$impCounter"
                val deductStart = if (imp.direction == ImpostDirection.VERTICAL) {
                    if (abs(imp.startY - frame.topMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                } else {
                    if (abs(imp.startX - frame.leftMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                }
                val deductEnd = if (imp.direction == ImpostDirection.VERTICAL) {
                    if (abs(imp.endY - frame.bottomMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                } else {
                    if (abs(imp.endX - frame.rightMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                }
                val clearImpLen = max(100f, imp.lengthMm - deductStart - deductEnd).roundToInt()
                profileCuts.add(
                    ProfileCutPiece(
                        windowName = window.name,
                        profileSystemId = profile.id,
                        profileSystemName = profile.name,
                        materialType = profile.materialType,
                        role = ProfileRole.IMPOST,
                        positionLabel = "$fLabel Импост $impShortTag",
                        shortTag = impShortTag,
                        nominalLengthMm = imp.lengthMm.roundToInt(),
                        cutLengthMm = clearImpLen,
                        leftAngleDeg = 90,
                        rightAngleDeg = 90,
                        quantity = 1 * qty
                    )
                )
                impostsLengthM += clearImpLen / 1000.0
                impostPiecesCount += 1
            }

            // Проемы и створки внутри рамы (упорядочены слева направо, снизу/сверху по колонкам)
            val sortedOpenings = frame.openings.sortedWith(
                compareBy<OpeningCellNode>(
                    { (it.leftMm / 5f).roundToInt() },
                    { (it.topMm / 5f).roundToInt() }
                )
            )
            sortedOpenings.forEachIndexed { opIdx, op ->
                val cellCode = "Пр.${opIdx + 1}"
                val leftDeduction = if (abs(op.leftMm - frame.leftMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                val rightDeduction = if (abs(op.rightMm - frame.rightMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                val topDeduction = if (abs(op.topMm - frame.topMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
                val bottomDeduction = if (abs(op.bottomMm - frame.bottomMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f

                val lightW = max(100f, op.widthMm - leftDeduction - rightDeduction)
                val lightH = max(100f, op.heightMm - topDeduction - bottomDeduction)
                val props = op.properties

                if (!props.openingType.hasSashProfile) {
                    val glassW = finalizeGlassDim(lightW - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                    val glassH = finalizeGlassDim(lightH - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                    val singleArea = (glassW.toDouble() * glassH.toDouble()) / 1_000_000.0
                    val isSand = props.isSandwichFilling
                    glazingSpecs.add(
                        GlazingSpecItem(
                            windowName = window.name,
                            cellLabel = "$cellCode (Глух. проём ${lightW.roundToInt()}×${lightH.roundToInt()})",
                            formula = if (isSand) sandwichItem.formula else glazing.formula,
                            isSandwich = isSand,
                            widthMm = glassW,
                            heightMm = glassH,
                            quantity = qty,
                            singleAreaM2 = singleArea,
                            totalAreaM2 = singleArea * qty
                        )
                    )
                    if (isSand) sandwichAreaM2 += singleArea else glazingAreaM2 += singleArea

                    val horizShtLen = lightW.roundToInt()
                    val vertShtLen = max(40f, lightH - shtapikVertDeductBothSidesMm).roundToInt()
                    val shtapikPerimM = 2.0 * (horizShtLen + vertShtLen) / 1000.0
                    shtapikPerimeterM += shtapikPerimM
                    val fixedPerimM = 2.0 * (lightW + lightH) / 1000.0
                    rubberPerimeterM += fixedPerimM
                    fixedRubberPerUnitM += fixedPerimM
                    val glassThickMm = if (isSand) sandwichItem.thicknessMm else glazing.thicknessMm.coerceAtLeast(4)
                    val matchedWedge = OrderProcurementHelper.selectOptimalWedgeVariant(
                        profile = profile,
                        shtapikH2Mm = activeShtapikH2,
                        glazingThicknessMm = glassThickMm,
                        seatMm = profile.frameGlazingSeatA4Mm
                    )
                    wedgeCostPerUnit += fixedPerimM * (matchedWedge?.pricePerMeter ?: profile.priceRubberPerM)
                    if (matchedWedge != null) usedWedgeLabels.add(matchedWedge.name)

                    profileCuts.add(
                        ProfileCutPiece(
                            windowName = window.name,
                            profileSystemId = profile.id,
                            profileSystemName = profile.name,
                            materialType = profile.materialType,
                            role = ProfileRole.SHTAPIK,
                            positionLabel = "$cellCode Штапик гориз.$shtapikNameSuffix",
                            shortTag = "$winNum",
                            nominalLengthMm = horizShtLen,
                            cutLengthMm = horizShtLen,
                            leftAngleDeg = shtapikCutAngle,
                            rightAngleDeg = shtapikCutAngle,
                            quantity = 2 * qty
                        )
                    )
                    profileCuts.add(
                        ProfileCutPiece(
                            windowName = window.name,
                            profileSystemId = profile.id,
                            profileSystemName = profile.name,
                            materialType = profile.materialType,
                            role = ProfileRole.SHTAPIK,
                            positionLabel = "$cellCode Штапик верт.$shtapikNameSuffix",
                            shortTag = "$winNum",
                            nominalLengthMm = vertShtLen,
                            cutLengthMm = vertShtLen,
                            leftAngleDeg = shtapikCutAngle,
                            rightAngleDeg = shtapikCutAngle,
                            quantity = 2 * qty
                        )
                    )
                } else {
                    val isDoubleShtulp = props.openingType.isShtulp
                    val isVertDouble = props.openingType == SashOpeningType.VERT_SLIDE_UP ||
                        props.openingType == SashOpeningType.VERT_SLIDE_BOTH
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
                    val isAnyDoor = props.isDoorSash || props.isReverseDoorSash
                    val sashShortTag = if (isAnyDoor) {
                        doorCounter += 1
                        if (totalDoorSashesInWin > 1) "$winNum,$doorCounter" else "$winNum"
                    } else {
                        sashCounter += 1
                        if (totalWindowSashesInWin > 1) "$winNum,$sashCounter" else "$winNum"
                    }
                    val sashRole = if (isAnyDoor) ProfileRole.DOOR_SASH else ProfileRole.SASH
                    val roleTitleLabel = when {
                        props.isReverseDoorSash -> "Обр.дверь"
                        props.isDoorSash -> "Дверь"
                        else -> "Створка"
                    }

                    data class LeafCutSpec(
                        val outerWMm: Float,
                        val outerHMm: Float,
                        val leafMultiplier: Int,
                        val sideSuffix: String
                    )

                    val leafSpecs: List<LeafCutSpec> = if (isDoubleShtulp) {
                        val shtulpGeom = WindowCadOperations.computeShtulpGeometry(frame, op, profile)
                        val gapMm = (profile.shtulpRebateG1Mm - overlapMm).coerceAtLeast(0f)
                        val leafWL = (shtulpGeom.lightWLMm + overlapMm - gapMm / 2f).coerceAtLeast(100f)
                        val leafWR = (shtulpGeom.lightWRMm + overlapMm - gapMm / 2f).coerceAtLeast(100f)
                        val leafH = (lightH + 2f * overlapMm).coerceAtLeast(100f)
                        val shtulpLenMm = leafH.roundToInt()
                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = ProfileRole.SHTULP,
                                positionLabel = "$cellCode Штульп $roleTitleLabel (${props.openingType.shortCode})",
                                shortTag = sashShortTag,
                                nominalLengthMm = shtulpLenMm,
                                cutLengthMm = shtulpLenMm,
                                leftAngleDeg = 90,
                                rightAngleDeg = 90,
                                quantity = 1 * qty
                            )
                        )
                        shtulpLengthM += shtulpLenMm / 1000.0
                        listOf(
                            LeafCutSpec(leafWL, leafH, 1, " Лев."),
                            LeafCutSpec(leafWR, leafH, 1, " Прав.")
                        )
                    } else if (isVertDouble) {
                        listOf(
                            LeafCutSpec(
                                outerWMm = lightW + 2f * overlapMm,
                                outerHMm = (lightH / 2f) + 2f * overlapMm,
                                leafMultiplier = 2,
                                sideSuffix = ""
                            )
                        )
                    } else {
                        listOf(
                            LeafCutSpec(
                                outerWMm = lightW + 2f * overlapMm,
                                outerHMm = lightH + 2f * overlapMm,
                                leafMultiplier = 1,
                                sideSuffix = ""
                            )
                        )
                    }

                    val seatMm = when {
                        props.isReverseDoorSash -> profile.revDoorGlazingSeatE3Mm
                        props.isDoorSash -> profile.doorGlazingSeatD3Mm
                        else -> profile.sashGlazingSeatC3Mm
                    }
                    val effectiveImpostMode = props.effectiveSashImpostMode()

                    leafSpecs.forEach { leaf ->
                        val sashOuterW = leaf.outerWMm.roundToInt()
                        val sashOuterH = leaf.outerHMm.roundToInt()
                        val mult = leaf.leafMultiplier

                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = sashRole,
                                positionLabel = "$cellCode $roleTitleLabel${leaf.sideSuffix} гориз. (${props.openingType.shortCode})",
                                shortTag = sashShortTag,
                                nominalLengthMm = sashOuterW,
                                cutLengthMm = sashOuterW + weld2Sides,
                                leftAngleDeg = 45,
                                rightAngleDeg = 45,
                                quantity = 2 * mult * qty
                            )
                        )
                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = sashRole,
                                positionLabel = "$cellCode $roleTitleLabel${leaf.sideSuffix} верт. (${props.openingType.shortCode})",
                                shortTag = sashShortTag,
                                nominalLengthMm = sashOuterH,
                                cutLengthMm = sashOuterH + weld2Sides,
                                leftAngleDeg = 45,
                                rightAngleDeg = 45,
                                quantity = 2 * mult * qty
                            )
                        )

                        val leafPerimM = mult * 2.0 * (sashOuterW + sashOuterH) / 1000.0
                        if (isAnyDoor) {
                            doorSashPerimeterM += leafPerimM
                            doorSashLeavesTotalCount += mult
                        } else {
                            sashPerimeterM += leafPerimM
                            windowSashLeavesTotalCount += mult
                        }
                        rubberPerimeterM += leafPerimM * 2.0
                        if (props.openingType != SashOpeningType.BLIND_SASH) {
                            rebateRubberPerUnitM += leafPerimM
                        }

                        val sashInnerLightW = max(80f, leaf.outerWMm - 2f * sashProfileW)
                        val sashInnerLightH = max(80f, leaf.outerHMm - 2f * sashProfileW)

                        data class InnerPaneBox(val index: Int, val nameSuffix: String, val wMm: Float, val hMm: Float)
                        val innerPanes: List<InnerPaneBox> = when (effectiveImpostMode) {
                            SashImpostMode.NONE -> listOf(
                                InnerPaneBox(0, "", sashInnerLightW, sashInnerLightH)
                            )
                            SashImpostMode.V1 -> {
                                impCounter += 1
                                val innerImpTag = "$winNum,$impCounter"
                                val impLen = sashInnerLightH.roundToInt()
                                profileCuts.add(
                                    ProfileCutPiece(
                                        windowName = window.name,
                                        profileSystemId = profile.id,
                                        profileSystemName = profile.name,
                                        materialType = profile.materialType,
                                        role = ProfileRole.IMPOST,
                                        positionLabel = "$cellCode Импост $innerImpTag верт. внутри створки",
                                        shortTag = innerImpTag,
                                        nominalLengthMm = impLen,
                                        cutLengthMm = impLen,
                                        leftAngleDeg = 90,
                                        rightAngleDeg = 90,
                                        quantity = mult * qty
                                    )
                                )
                                impostsLengthM += (impLen * mult) / 1000.0
                                impostPiecesCount += mult
                                val partW = max(60f, (sashInnerLightW - profile.impostWidthMm) / 2f)
                                listOf(
                                    InnerPaneBox(0, " Лев.", partW, sashInnerLightH),
                                    InnerPaneBox(1, " Прав.", partW, sashInnerLightH)
                                )
                            }
                            SashImpostMode.H1 -> {
                                impCounter += 1
                                val innerImpTag = "$winNum,$impCounter"
                                val impLen = sashInnerLightW.roundToInt()
                                profileCuts.add(
                                    ProfileCutPiece(
                                        windowName = window.name,
                                        profileSystemId = profile.id,
                                        profileSystemName = profile.name,
                                        materialType = profile.materialType,
                                        role = ProfileRole.IMPOST,
                                        positionLabel = "$cellCode Импост $innerImpTag гориз. внутри створки/двери",
                                        shortTag = innerImpTag,
                                        nominalLengthMm = impLen,
                                        cutLengthMm = impLen,
                                        leftAngleDeg = 90,
                                        rightAngleDeg = 90,
                                        quantity = mult * qty
                                    )
                                )
                                impostsLengthM += (impLen * mult) / 1000.0
                                impostPiecesCount += mult
                                val botH = (props.sashImpostHeightFromBottomMm - sashProfileW - profile.impostWidthMm / 2f)
                                    .coerceIn(80f, (sashInnerLightH - 120f).coerceAtLeast(80f))
                                val topH = (sashInnerLightH - botH - profile.impostWidthMm).coerceAtLeast(80f)
                                listOf(
                                    InnerPaneBox(0, " Верх", sashInnerLightW, topH),
                                    InnerPaneBox(1, " Низ", sashInnerLightW, botH)
                                )
                            }
                            SashImpostMode.V2 -> {
                                repeat(2) {
                                    impCounter += 1
                                }
                                val innerImpTag = "$winNum,$impCounter"
                                val impLen = sashInnerLightH.roundToInt()
                                profileCuts.add(
                                    ProfileCutPiece(
                                        windowName = window.name,
                                        profileSystemId = profile.id,
                                        profileSystemName = profile.name,
                                        materialType = profile.materialType,
                                        role = ProfileRole.IMPOST,
                                        positionLabel = "$cellCode Импосты $innerImpTag (2 верт.) внутри створки",
                                        shortTag = innerImpTag,
                                        nominalLengthMm = impLen,
                                        cutLengthMm = impLen,
                                        leftAngleDeg = 90,
                                        rightAngleDeg = 90,
                                        quantity = 2 * mult * qty
                                    )
                                )
                                impostsLengthM += (impLen * 2 * mult) / 1000.0
                                impostPiecesCount += 2 * mult
                                val partW = max(60f, (sashInnerLightW - 2f * profile.impostWidthMm) / 3f)
                                listOf(
                                    InnerPaneBox(0, " Сек.1", partW, sashInnerLightH),
                                    InnerPaneBox(1, " Сек.2", partW, sashInnerLightH),
                                    InnerPaneBox(2, " Сек.3", partW, sashInnerLightH)
                                )
                            }
                            SashImpostMode.H2 -> {
                                repeat(2) {
                                    impCounter += 1
                                }
                                val innerImpTag = "$winNum,$impCounter"
                                val impLen = sashInnerLightW.roundToInt()
                                profileCuts.add(
                                    ProfileCutPiece(
                                        windowName = window.name,
                                        profileSystemId = profile.id,
                                        profileSystemName = profile.name,
                                        materialType = profile.materialType,
                                        role = ProfileRole.IMPOST,
                                        positionLabel = "$cellCode Импосты $innerImpTag (2 гориз.) внутри створки",
                                        shortTag = innerImpTag,
                                        nominalLengthMm = impLen,
                                        cutLengthMm = impLen,
                                        leftAngleDeg = 90,
                                        rightAngleDeg = 90,
                                        quantity = 2 * mult * qty
                                    )
                                )
                                impostsLengthM += (impLen * 2 * mult) / 1000.0
                                impostPiecesCount += 2 * mult
                                val partH = max(60f, (sashInnerLightH - 2f * profile.impostWidthMm) / 3f)
                                listOf(
                                    InnerPaneBox(0, " Верх", sashInnerLightW, partH),
                                    InnerPaneBox(1, " Серед.", sashInnerLightW, partH),
                                    InnerPaneBox(2, " Низ", sashInnerLightW, partH)
                                )
                            }
                        }

                        innerPanes.forEach { pane ->
                            val glassW = finalizeGlassDim(pane.wMm - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                            val glassH = finalizeGlassDim(pane.hMm - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                            val singleArea = (glassW.toDouble() * glassH.toDouble()) / 1_000_000.0
                            val isSand = props.isPaneSandwich(pane.index)

                            glazingSpecs.add(
                                GlazingSpecItem(
                                    windowName = window.name,
                                    cellLabel = "$cellCode ($roleTitleLabel${leaf.sideSuffix}${pane.nameSuffix}, проём ${lightW.roundToInt()}×${lightH.roundToInt()})",
                                    formula = if (isSand) sandwichItem.formula else glazing.formula,
                                    isSandwich = isSand,
                                    widthMm = glassW,
                                    heightMm = glassH,
                                    quantity = mult * qty,
                                    singleAreaM2 = singleArea,
                                    totalAreaM2 = singleArea * mult * qty
                                )
                            )
                            if (isSand) sandwichAreaM2 += singleArea * mult else glazingAreaM2 += singleArea * mult

                            val horizShtLen = pane.wMm.roundToInt()
                            val vertShtLen = max(40f, pane.hMm - shtapikVertDeductBothSidesMm).roundToInt()
                            val shtPerim = mult * 2.0 * (horizShtLen + vertShtLen) / 1000.0
                            shtapikPerimeterM += shtPerim
                            val paneGlassPerimM = mult * 2.0 * (pane.wMm + pane.hMm) / 1000.0
                            rubberPerimeterM += paneGlassPerimM

                            val glassThickMm = if (isSand) sandwichItem.thicknessMm else glazing.thicknessMm.coerceAtLeast(4)
                            val matchedWedge = OrderProcurementHelper.selectOptimalWedgeVariant(profile, activeShtapikH2, glassThickMm, seatMm)
                            wedgeCostPerUnit += paneGlassPerimM * (matchedWedge?.pricePerMeter ?: profile.priceRubberPerM)
                            if (matchedWedge != null) usedWedgeLabels.add(matchedWedge.name)

                            profileCuts.add(
                                ProfileCutPiece(
                                    windowName = window.name,
                                    profileSystemId = profile.id,
                                    profileSystemName = profile.name,
                                    materialType = profile.materialType,
                                    role = ProfileRole.SHTAPIK,
                                    positionLabel = "$cellCode Штапик $roleTitleLabel${leaf.sideSuffix}${pane.nameSuffix} гориз.$shtapikNameSuffix",
                                    shortTag = sashShortTag,
                                    nominalLengthMm = horizShtLen,
                                    cutLengthMm = horizShtLen,
                                    leftAngleDeg = shtapikCutAngle,
                                    rightAngleDeg = shtapikCutAngle,
                                    quantity = 2 * mult * qty
                                )
                            )
                            profileCuts.add(
                                ProfileCutPiece(
                                    windowName = window.name,
                                    profileSystemId = profile.id,
                                    profileSystemName = profile.name,
                                    materialType = profile.materialType,
                                    role = ProfileRole.SHTAPIK,
                                    positionLabel = "$cellCode Штапик $roleTitleLabel${leaf.sideSuffix}${pane.nameSuffix} верт.$shtapikNameSuffix",
                                    shortTag = sashShortTag,
                                    nominalLengthMm = vertShtLen,
                                    cutLengthMm = vertShtLen,
                                    leftAngleDeg = shtapikCutAngle,
                                    rightAngleDeg = shtapikCutAngle,
                                    quantity = 2 * mult * qty
                                )
                            )
                        }
                    }

                    val repLeafW = leafSpecs.first().outerWMm.roundToInt()
                    val repLeafH = leafSpecs.first().outerHMm.roundToInt()
                    val falzW = (repLeafW - 16).coerceAtLeast(250)
                    val falzH = (repLeafH - 16).coerceAtLeast(250)
                    val baseSetPrice = when {
                        props.isDoorSash -> profile.priceDoorHardware
                        props.openingType == SashOpeningType.BLIND_SASH -> 0.0
                        props.openingType == SashOpeningType.TURN_LEFT || props.openingType == SashOpeningType.TURN_RIGHT -> profile.priceMechanicTurn
                        props.openingType == SashOpeningType.TILT_TURN_LEFT || props.openingType == SashOpeningType.TILT_TURN_RIGHT -> profile.priceMechanicTiltTurn
                        props.openingType == SashOpeningType.TRANSOM_BOTTOM || props.openingType == SashOpeningType.TRANSOM_TOP -> profile.priceMechanicTransom
                        isDoubleShtulp -> profile.priceMechanicShtulp
                        props.openingType == SashOpeningType.SLIDING_LEFT || props.openingType == SashOpeningType.SLIDING_RIGHT || isVertDouble -> profile.priceMechanicSliding
                        else -> 0.0
                    }
                    val brandAddon = if (hwBrand != null && hwBrand.id != "hw_none") hwBrand.priceMultiplierOrAddon else 0.0
                    val setPrice = baseSetPrice + brandAddon
                    val rangeGroup = "FFB ${((falzW / 200) * 200)}..${((falzW / 200) + 1) * 200} / FFH ${((falzH / 200) * 200)}..${((falzH / 200) + 1) * 200}"
                    hardwareSpecs.add(
                        SashHardwareSpecItem(
                            windowName = window.name,
                            cellLabel = cellCode,
                            openingType = props.openingType,
                            isDoor = props.isDoorSash,
                            falzWidthMm = falzW,
                            falzHeightMm = falzH,
                            quantity = qty,
                            hasMosquitoNet = props.hasMosquitoNet,
                            sizeRangeGroup = rangeGroup,
                            costPerSet = setPrice
                        )
                    )
                    hardwareCostTotal += setPrice

                    if (props.hasMosquitoNet) {
                        val repW = leafSpecs.first().outerWMm.roundToInt()
                        val repH = leafSpecs.first().outerHMm.roundToInt()
                        val mosqArea = (repW.toDouble() * repH.toDouble()) / 1_000_000.0
                        val rate = if (mosqSys != null && mosqSys.id != "msq_none") mosqSys.pricePerM2 else 15.0
                        mosquitoCostTotal += max(10.0, mosqArea * rate)
                    }
                }

                if (props.glazingBars != GlazingBarsPattern.NONE) {
                    val barsLenM = (props.glazingBars.horizontalRows * lightW + props.glazingBars.verticalCols * lightH) / 1000.0
                    glazingBarsLengthM += barsLenM
                }
            }
        }

        val totalW = window.topology.totalWidthMm.roundToInt()
        val totalH = window.topology.totalHeightMm.roundToInt()
        val totalAreaM2 = (totalW.toDouble() * totalH.toDouble()) / 1_000_000.0
        val widthM = totalW / 1000.0
        val heightM = totalH / 1000.0

        var addonsCost = 0.0
        if (innerSill != null && innerSill.widthMm > 0) {
            addonsCost += widthM * innerSill.pricePerM + innerSill.capPricePerPair
        }
        if (outerSill != null && outerSill.widthMm > 0) {
            addonsCost += widthM * outerSill.pricePerM + outerSill.capPricePerPair
        }
        if (window.addons.hasSeatProfile) {
            addonsCost += widthM * profile.priceSeatProfilePerM
        }
        if (window.addons.hasDoorsill) {
            addonsCost += widthM * profile.priceDoorsillPerM
        }
        if (window.addons.hasCornerJamb) {
            addonsCost += (widthM + 2.0 * heightM) * 4.0
        }

        var costExtrasOnly = 0.0
        window.selectedExtraIds.forEach { eid ->
            if (eid == "ext_corner") return@forEach
            val item = extraAccessoriesCatalog.find { it.id == eid } ?: return@forEach
            costExtrasOnly += when (item.rateType) {
                UnitRateType.PER_PIECE -> item.price
                UnitRateType.PER_M2 -> item.price * totalAreaM2
                UnitRateType.PER_M -> item.price * framePerimeterM
            }
        }
        var costWorksOnly = 0.0
        window.selectedWorkIds.forEach { wid ->
            val item = workServicesCatalog.find { it.id == wid } ?: return@forEach
            costWorksOnly += when (item.rateType) {
                UnitRateType.PER_PIECE -> item.price
                UnitRateType.PER_M2 -> item.price * totalAreaM2
                UnitRateType.PER_M -> item.price * framePerimeterM
            }
        }
        val costInstallation = if (window.includeInstallationWork) totalAreaM2 * window.installationPricePerM2 else 0.0
        costWorksOnly += costInstallation
        val extrasAndWorksCost = costExtrasOnly + costWorksOnly

        // Изолированный угловик (по высоте конструкции, 90°/90°, 1 шт) и доборный профиль 90° (по ширине конструкции, по цене рамы, 1 шт)
        val hasCornerAddon = window.addons.hasCornerProfile || window.selectedExtraIds.contains("ext_corner")
        val costCornerProfile = if (hasCornerAddon && !isNoSystemSelected) {
            heightM * profile.priceCornerProfilePerM
        } else {
            0.0
        }
        if (hasCornerAddon && !isNoSystemSelected) {
            profileCuts.add(
                ProfileCutPiece(
                    windowName = window.name,
                    profileSystemId = profile.id,
                    profileSystemName = profile.name,
                    materialType = profile.materialType,
                    role = ProfileRole.CORNER_PROFILE,
                    positionLabel = "Угловик (90°)",
                    shortTag = "$winNum",
                    nominalLengthMm = totalH,
                    cutLengthMm = totalH,
                    leftAngleDeg = 90,
                    rightAngleDeg = 90,
                    quantity = 1 * qty
                )
            )
        }

        val costExtenderProfile = if (window.addons.hasExtenderProfile90 && !isNoSystemSelected) {
            widthM * profile.priceFramePerM
        } else {
            0.0
        }
        if (window.addons.hasExtenderProfile90 && !isNoSystemSelected) {
            profileCuts.add(
                ProfileCutPiece(
                    windowName = window.name,
                    profileSystemId = profile.id,
                    profileSystemName = profile.name,
                    materialType = profile.materialType,
                    role = ProfileRole.EXTENDER,
                    positionLabel = "Доборный профиль (90°)",
                    shortTag = "$winNum",
                    nominalLengthMm = totalW,
                    cutLengthMm = totalW,
                    leftAngleDeg = 90,
                    rightAngleDeg = 90,
                    quantity = 1 * qty
                )
            )
        }

        // Чистая стоимость профилей (рама, импост, створка, дверная створка, штульп, угловик, добор)
        val costFrame = framePerimeterM * (profile.priceFramePerM + profile.priceReinforcementFramePerM)
        val costImposts = impostsLengthM * (profile.priceImpostPerM + profile.priceReinforcementImpostPerM)
        val costSashesOnly = sashPerimeterM * (profile.priceSashPerM + profile.priceReinforcementSashPerM)
        val costDoorSashesOnly = doorSashPerimeterM * (profile.priceDoorSashPerM + profile.priceReinforcementSashPerM)
        val costShtulp = shtulpLengthM * profile.priceShtulpPerM

        // Угловые сухари (комплекты на 4 угла рамы, створки, двери + комплекты на 1 импост)
        val frameCornerSetsCount = frames.size
        val sashCornerSetsCount = windowSashLeavesTotalCount
        val doorCornerSetsCount = doorSashLeavesTotalCount
        val impostConnectorSetsCount = impostPiecesCount
        val cornerConnectorsCount = if (isNoSystemSelected) {
            0
        } else {
            frameCornerSetsCount + sashCornerSetsCount + doorCornerSetsCount + impostConnectorSetsCount
        }
        val costCornerConnectors = if (isNoSystemSelected) {
            0.0
        } else {
            frameCornerSetsCount * profile.priceCornerConnectorFrame +
                sashCornerSetsCount * profile.priceCornerConnectorSash +
                doorCornerSetsCount * profile.priceCornerConnectorDoorSash +
                impostConnectorSetsCount * profile.priceImpostConnector
        }

        val costShtapikOnly = shtapikPerimeterM * activeShtapikPricePerM +
            glazingBarsLengthM * profile.priceGlazingBarPerM
        val costFixedRubber = fixedRubberPerUnitM * profile.rubberCatalog.fixedGlazing.pricePerMeter
        val costFrameRebateRubber = rebateRubberPerUnitM * profile.rubberCatalog.frameRebate.pricePerMeter
        val costSashRebateRubber = rebateRubberPerUnitM * profile.rubberCatalog.sashDoorRebate.pricePerMeter
        val costRubberOnly = costFixedRubber + costFrameRebateRubber + costSashRebateRubber + wedgeCostPerUnit
        val costShtapikAndRubber = costShtapikOnly + costRubberOnly

        val costGlazingOnly = glazingAreaM2 * glazing.pricePerM2
        val costSandwichOnly = sandwichAreaM2 * (if (glazing.id == "none") 0.0 else sandwichItem.pricePerM2)
        val costGlazingAndSandwich = costGlazingOnly + costSandwichOnly

        val profilesBaseCost = costFrame + costImposts + costSashesOnly + costDoorSashesOnly +
            costShtulp + costCornerProfile + costExtenderProfile + costCornerConnectors + costShtapikAndRubber
        val costColorMarkup = profilesBaseCost * (color.priceMarkupPercent / 100.0)

        val materialsAndWorksCost = costFrame + costImposts + costSashesOnly + costDoorSashesOnly +
            costShtulp + costCornerProfile + costExtenderProfile +
            costCornerConnectors + costShtapikAndRubber + costGlazingAndSandwich + hardwareCostTotal +
            mosquitoCostTotal + addonsCost + extrasAndWorksCost + costColorMarkup

        val costAssembly = if (isNoSystemSelected) {
            0.0
        } else {
            calculateWindowAssemblyCost(
                window = window,
                assemblyPricing = assemblyPricingCatalog,
                framesCount = frames.size,
                windowSashLeavesCount = windowSashLeavesTotalCount,
                doorSashLeavesCount = doorSashLeavesTotalCount,
                impostPiecesCount = impostPiecesCount,
                hardwareSpecs = hardwareSpecs,
                glazingSpecs = glazingSpecs,
                profileCuts = profileCuts,
                qty = qty,
                totalAreaM2 = totalAreaM2,
                totalProfilePerimeterM = framePerimeterM + impostsLengthM + sashPerimeterM + doorSashPerimeterM + shtulpLengthM,
                glazingPlusSandwichAreaM2 = glazingAreaM2 + sandwichAreaM2,
                rubberPerimeterM = rubberPerimeterM
            )
        }

        val unitPrimeCost = materialsAndWorksCost + costAssembly
        val rawUnitCost = unitPrimeCost

        val discPct = finScheme?.discountPercent ?: 0.0
        val markupPct = finScheme?.markupPercent ?: 0.0
        val afterMarkupCost = if (markupPct > 0.0) rawUnitCost * (1.0 + markupPct / 100.0) else rawUnitCost
        val discountedUnitCost = if (discPct > 0.0) {
            afterMarkupCost * (1.0 - discPct / 100.0)
        } else {
            afterMarkupCost
        }

        val costCustomPainting = if (window.colorId == CUSTOM_CLIENT_COLOR_ID && window.customPaintingCost > 0.0) {
            window.customPaintingCost
        } else {
            0.0
        }

        val finalUnitCost = window.manualUnitPriceOverride ?: discountedUnitCost
        val taggedCuts = profileCuts.map { it.copy(colorName = cutColorName, colorHex = cutColorHex) }

        return WindowCalculationResult(
            windowItem = window,
            profileSystem = profile,
            glazing = glazing,
            color = color,
            totalAreaM2 = totalAreaM2,
            framePerimeterM = framePerimeterM,
            impostsLengthM = impostsLengthM,
            sashPerimeterM = sashPerimeterM,
            doorSashPerimeterM = doorSashPerimeterM,
            shtulpLengthM = shtulpLengthM,
            shtapikPerimeterM = shtapikPerimeterM,
            rubberPerimeterM = rubberPerimeterM,
            glazingAreaM2 = glazingAreaM2,
            sandwichAreaM2 = sandwichAreaM2,
            cornerConnectorsCount = cornerConnectorsCount,
            costFrame = costFrame,
            costImposts = costImposts,
            costSashes = costSashesOnly,
            costDoorSashes = costDoorSashesOnly,
            costShtulp = costShtulp,
            costCornerProfile = costCornerProfile,
            costExtenderProfile = costExtenderProfile,
            costCornerConnectors = costCornerConnectors,
            costShtapik = costShtapikOnly,
            costRubber = costRubberOnly,
            costShtapikAndRubber = costShtapikAndRubber,
            costGlazingOnly = costGlazingOnly,
            costSandwichOnly = costSandwichOnly,
            costGlazingAndSandwich = costGlazingAndSandwich,
            costHardware = hardwareCostTotal,
            costMosquitoNets = mosquitoCostTotal,
            costSillsAndAddons = addonsCost,
            costExtrasOnly = costExtrasOnly + addonsCost + mosquitoCostTotal,
            costWorksOnly = costWorksOnly,
            costExtrasAndWorks = extrasAndWorksCost,
            costColorMarkup = costColorMarkup,
            costCustomPainting = costCustomPainting,
            cutColorName = cutColorName,
            cutColorHex = cutColorHex,
            costInstallationWork = costInstallation,
            rawUnitCostBeforeDiscount = afterMarkupCost,
            discountPercentApplied = discPct,
            unitTotalCost = finalUnitCost,
            lineTotalCost = finalUnitCost * qty + costCustomPainting,
            profileCuts = taggedCuts,
            glazingSpecs = glazingSpecs,
            hardwareSpecs = hardwareSpecs,
            matchedWedgeSummary = usedWedgeLabels.distinct().joinToString(", "),
            costAssembly = costAssembly,
            costInstallation = costWorksOnly,
            costOrderServices = 0.0,
            unitPrimeCost = unitPrimeCost
        )
    }

    private fun calculateWindowAssemblyCost(
        window: WindowItem,
        assemblyPricing: AssemblyPricingCatalog,
        framesCount: Int,
        windowSashLeavesCount: Int,
        doorSashLeavesCount: Int,
        impostPiecesCount: Int,
        hardwareSpecs: List<SashHardwareSpecItem>,
        glazingSpecs: List<GlazingSpecItem>,
        profileCuts: List<ProfileCutPiece>,
        qty: Int,
        totalAreaM2: Double,
        totalProfilePerimeterM: Double,
        glazingPlusSandwichAreaM2: Double,
        rubberPerimeterM: Double
    ): Double {
        val scheme = window.assemblySchemeOverride ?: assemblyPricing.defaultScheme
        return when (scheme) {
            AssemblySchemeType.OPERATIONS -> {
                val cornersCost =
                    (framesCount * 4) * assemblyPricing.frameCornerRate +
                        (windowSashLeavesCount * 4) * assemblyPricing.sashCornerRate +
                        (doorSashLeavesCount * 4) * assemblyPricing.doorCornerRate
                val impostsCost = impostPiecesCount * assemblyPricing.impostRate
                val hardwareOpsCost = hardwareSpecs.sumOf { spec ->
                    when {
                        spec.openingType == SashOpeningType.BLIND_SASH -> 0.0
                        spec.isDoor -> assemblyPricing.hwDoorRate
                        spec.openingType == SashOpeningType.TURN_LEFT ||
                            spec.openingType == SashOpeningType.TURN_RIGHT ||
                            spec.openingType == SashOpeningType.TRANSOM_BOTTOM ||
                            spec.openingType == SashOpeningType.TRANSOM_TOP -> assemblyPricing.hwTurnRate
                        spec.openingType == SashOpeningType.TILT_TURN_LEFT ||
                            spec.openingType == SashOpeningType.TILT_TURN_RIGHT -> assemblyPricing.hwTiltTurnRate
                        spec.openingType.isShtulp -> assemblyPricing.hwShtulpRate
                        spec.openingType == SashOpeningType.SLIDING_LEFT ||
                            spec.openingType == SashOpeningType.SLIDING_RIGHT ||
                            spec.openingType == SashOpeningType.VERT_SLIDE_UP ||
                            spec.openingType == SashOpeningType.VERT_SLIDE_BOTH -> assemblyPricing.hwSlidingRate
                        else -> 0.0
                    }
                }
                val beadsCost = when (assemblyPricing.beadRateMode) {
                    BeadAssemblyRateMode.PER_OPENING -> {
                        val openingsPerUnit = glazingSpecs.sumOf { (it.quantity / qty.coerceAtLeast(1)).coerceAtLeast(1) }
                        openingsPerUnit * assemblyPricing.beadRate
                    }
                    BeadAssemblyRateMode.PER_PIECE -> {
                        val beadPiecesPerUnit = profileCuts
                            .filter { it.role == ProfileRole.SHTAPIK }
                            .sumOf { (it.quantity / qty.coerceAtLeast(1)).coerceAtLeast(1) }
                        beadPiecesPerUnit * assemblyPricing.beadRate
                    }
                }
                val rubberCost = when (assemblyPricing.rubberRateMode) {
                    RubberAssemblyRateMode.PER_M2_GLAZING -> glazingPlusSandwichAreaM2 * assemblyPricing.rubberRate
                    RubberAssemblyRateMode.PER_METER_CONTOUR -> rubberPerimeterM * assemblyPricing.rubberRate
                }
                cornersCost + impostsCost + hardwareOpsCost + beadsCost + rubberCost
            }
            AssemblySchemeType.PER_M2 -> totalAreaM2 * assemblyPricing.ratePerM2
            AssemblySchemeType.PER_METER -> totalProfilePerimeterM * assemblyPricing.ratePerMeter
        }
    }

    fun resolveOrderServicePrice(
        selected: SelectedOrderService,
        catalog: List<OrderExtraServiceItem>
    ): Double {
        return selected.customPriceOverride
            ?: catalog.find { it.id == selected.serviceId }?.defaultPrice
            ?: 0.0
    }

    fun calculateOrderFinancialSummary(
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>,
        assemblyPricingCatalog: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
        orderExtraServicesCatalog: List<OrderExtraServiceItem> = DefaultCatalogs.defaultOrderExtraServices(),
        finSchemeSettings: FinSchemeSettings = FinSchemeSettings()
    ): OrderFinancialSummary {
        var totalMaterialsCost = 0.0
        var totalWindowsAssemblyCost = 0.0
        var totalInstallationCost = 0.0
        var totalWindowsPrimeCost = 0.0

        windowResults.forEach { res ->
            val w = res.windowItem
            val qty = w.quantity.coerceAtLeast(1)
            val matPerUnit = (res.unitPrimeCost - res.costAssembly - res.costInstallation).coerceAtLeast(0.0)
            totalMaterialsCost += matPerUnit * qty + res.costCustomPainting
            totalWindowsAssemblyCost += res.costAssembly * qty
            totalInstallationCost += res.costInstallation * qty
            totalWindowsPrimeCost += res.unitPrimeCost * qty + res.costCustomPainting
        }

        val floorAssemblyCost = order.floorAssemblyCount.coerceAtLeast(0) * assemblyPricingCatalog.floorAssemblyRate
        val extraOpsCost = order.extraAssemblyOpsCost.coerceAtLeast(0.0)
        val totalAssemblyWithFloorAndOps = totalWindowsAssemblyCost + floorAssemblyCost + extraOpsCost
        val totalOrderServicesCost = order.selectedOrderServices.sumOf {
            resolveOrderServicePrice(it, orderExtraServicesCatalog).coerceAtLeast(0.0)
        }
        val orderExtrasLevelCost = floorAssemblyCost + extraOpsCost + totalOrderServicesCost

        val orderPrimeCost = totalWindowsPrimeCost + orderExtrasLevelCost
        val effectiveMarkupMultiplier =
            (finSchemeSettings.baseMarkupMultiplier * (1.0 + order.markupPercent / 100.0)).coerceAtLeast(0.0)

        var totalWindowsBaseCost = 0.0
        var totalWindowsDiscountedCost = 0.0
        val breakdowns = mutableListOf<WindowTurnkeyPriceBreakdown>()
        val winCountSafe = windowResults.size.coerceAtLeast(1)

        windowResults.forEach { res ->
            val w = res.windowItem
            val qty = w.quantity.coerceAtLeast(1)
            val winDirectPrimeLine = res.unitPrimeCost * qty + res.costCustomPainting
            val share = if (totalWindowsPrimeCost > 0.0) {
                winDirectPrimeLine / totalWindowsPrimeCost
            } else {
                1.0 / winCountSafe
            }
            val winFullPrimeLine = winDirectPrimeLine + orderExtrasLevelCost * share
            val itemDiscPct = if (res.discountPercentApplied > 0.0) {
                res.discountPercentApplied
            } else {
                order.discountPercent
            }.coerceIn(0.0, 99.0)

            val lineBaseSale = if (w.manualUnitPriceOverride != null) {
                w.manualUnitPriceOverride * qty + res.costCustomPainting
            } else {
                winFullPrimeLine * effectiveMarkupMultiplier
            }
            val lineDiscountedSale = lineBaseSale * (1.0 - itemDiscPct / 100.0)

            totalWindowsBaseCost += lineBaseSale
            totalWindowsDiscountedCost += lineDiscountedSale
            breakdowns.add(
                WindowTurnkeyPriceBreakdown(
                    windowId = w.id,
                    quantity = qty,
                    primeCostLine = winFullPrimeLine,
                    unitBaseSale = lineBaseSale / qty,
                    lineBaseSale = lineBaseSale,
                    discountPercent = itemDiscPct,
                    unitDiscountedSale = lineDiscountedSale / qty,
                    lineDiscountedSale = lineDiscountedSale
                )
            )
        }

        val orderSaleBeforeDiscountAndVat = if (windowResults.isEmpty()) {
            orderPrimeCost * effectiveMarkupMultiplier
        } else {
            totalWindowsBaseCost
        }
        val orderSaleAfterDiscountBeforeVat = if (windowResults.isEmpty()) {
            orderSaleBeforeDiscountAndVat * (1.0 - order.discountPercent.coerceIn(0.0, 99.0) / 100.0)
        } else {
            totalWindowsDiscountedCost
        }
        val discountAmount = (orderSaleBeforeDiscountAndVat - orderSaleAfterDiscountBeforeVat).coerceAtLeast(0.0)
        val vatAmount = orderSaleAfterDiscountBeforeVat * (order.taxVatPercent / 100.0)
        val totalWithVat = order.manualOrderTotalOverride ?: (orderSaleAfterDiscountBeforeVat + vatAmount)

        val effectiveNetAfterDiscount = if (order.manualOrderTotalOverride != null) {
            val vatDivisor = 1.0 + order.taxVatPercent / 100.0
            if (vatDivisor > 0.0) order.manualOrderTotalOverride / vatDivisor else order.manualOrderTotalOverride
        } else {
            orderSaleAfterDiscountBeforeVat
        }
        val isBelowPrimeCost = finSchemeSettings.warnBelowPrimeCost &&
            orderPrimeCost > 0.0 &&
            effectiveNetAfterDiscount < (orderPrimeCost - 0.005)

        return OrderFinancialSummary(
            totalWindowsBaseCost = totalWindowsBaseCost,
            totalWindowsDiscountedCost = totalWindowsDiscountedCost,
            totalWindowsAssemblyCost = totalWindowsAssemblyCost,
            floorAssemblyCost = floorAssemblyCost,
            extraAssemblyOpsCost = extraOpsCost,
            totalAssemblyWithFloorAndOps = totalAssemblyWithFloorAndOps,
            totalInstallationCost = totalInstallationCost,
            totalOrderServicesCost = totalOrderServicesCost,
            orderPrimeCost = orderPrimeCost,
            orderSaleBeforeDiscountAndVat = orderSaleBeforeDiscountAndVat,
            orderSaleAfterDiscountBeforeVat = orderSaleAfterDiscountBeforeVat,
            discountAmount = discountAmount,
            vatAmount = vatAmount,
            totalWithVat = totalWithVat,
            totalMaterialsCost = totalMaterialsCost,
            markupMultiplierApplied = effectiveMarkupMultiplier,
            isBelowPrimeCost = isBelowPrimeCost,
            windowBreakdowns = breakdowns
        )
    }

    fun optimizeOrderProfileCutting(
        windowResults: List<WindowCalculationResult>,
        stockRemnants: List<StockRemnantBar>,
        profileSystems: List<ProfileSystemSpec>
    ): Pair<List<CutBarAllocation>, List<ProfileProcurementSummary>> {
        val allPieces = windowResults.flatMap { it.profileCuts }
        val grouped = allPieces
            .groupBy { Triple(it.profileSystemId, it.role, it.colorName) }
            .entries
            .sortedWith(compareBy({ it.key.second.ordinal }, { it.key.third }))

        val allAllocations = mutableListOf<CutBarAllocation>()
        val summaries = mutableListOf<ProfileProcurementSummary>()

        for ((key, pieces) in grouped) {
            val (sysId, role, colorName) = key
            val groupColorHex = pieces.firstOrNull()?.colorHex ?: 0xFF383E42L
            val sys = profileSystems.find { it.id == sysId } ?: profileSystems.first()
            val fullBarLen = sys.stockBarLengthMm.roundToInt().coerceIn(3000, 8500)
            val kerf = sys.sawKerfMm.roundToInt().coerceAtLeast(0)

            val expandedPieces = mutableListOf<ProfileCutPiece>()
            for (p in pieces) {
                repeat(p.quantity) {
                    expandedPieces.add(p.copy(quantity = 1))
                }
            }
            expandedPieces.sortByDescending { it.cutLengthMm }

            val availableRemnants = mutableListOf<Int>()
            stockRemnants.filter {
                it.profileSystemId == sysId &&
                    it.role == role &&
                    it.colorName.equals(colorName, ignoreCase = true)
            }.forEach { rem ->
                repeat(rem.quantity) { availableRemnants.add(rem.lengthMm) }
            }
            availableRemnants.sort()

            data class OpenBar(
                val isFromRemnant: Boolean,
                val sourceLenMm: Int,
                val cuts: MutableList<AllocatedCutPiece> = mutableListOf(),
                var usedMm: Int = 0
            ) {
                val remainingMm: Int get() = (sourceLenMm - usedMm).coerceAtLeast(0)
            }

            val activeBars = mutableListOf<OpenBar>()

            for (piece in expandedPieces) {
                val neededLen = piece.cutLengthMm.coerceAtMost(fullBarLen)
                var targetBar = activeBars.firstOrNull { bar ->
                    val extraKerf = if (bar.cuts.isEmpty()) 0 else kerf
                    bar.remainingMm >= neededLen + extraKerf
                }

                if (targetBar == null) {
                    val remIdx = availableRemnants.indexOfFirst { it >= neededLen }
                    if (remIdx >= 0) {
                        val remLen = availableRemnants.removeAt(remIdx)
                        targetBar = OpenBar(isFromRemnant = true, sourceLenMm = remLen)
                        activeBars.add(targetBar)
                    }
                }

                if (targetBar == null) {
                    targetBar = OpenBar(isFromRemnant = false, sourceLenMm = fullBarLen)
                    activeBars.add(targetBar)
                }

                val extraKerf = if (targetBar.cuts.isEmpty()) 0 else kerf
                targetBar.cuts.add(
                    AllocatedCutPiece(
                        windowName = piece.windowName,
                        positionLabel = piece.positionLabel,
                        shortTag = piece.shortTag,
                        cutLengthMm = piece.cutLengthMm,
                        leftAngleDeg = piece.leftAngleDeg,
                        rightAngleDeg = piece.rightAngleDeg
                    )
                )
                targetBar.usedMm += neededLen + extraKerf
            }

            val roleAllocations = activeBars.mapIndexed { idx, bar ->
                val rem = bar.remainingMm
                CutBarAllocation(
                    barNumber = idx + 1,
                    profileSystemName = sys.name,
                    role = role,
                    isFromRemnantStock = bar.isFromRemnant,
                    sourceLengthMm = bar.sourceLenMm,
                    cuts = bar.cuts,
                    usedWithKerfMm = bar.usedMm,
                    remnantLengthMm = rem,
                    isUsableBusinessRemnant = rem >= 400,
                    colorName = colorName,
                    colorHex = groupColorHex
                )
            }
            allAllocations.addAll(roleAllocations)

            val totalSourceMm = roleAllocations.sumOf { it.sourceLengthMm }.toDouble().coerceAtLeast(1.0)
            val totalNetMm = expandedPieces.sumOf { it.cutLengthMm }.toDouble()
            val wastePct = ((totalSourceMm - totalNetMm) / totalSourceMm * 100.0).coerceAtLeast(0.0)

            summaries.add(
                ProfileProcurementSummary(
                    profileSystemId = sysId,
                    profileSystemName = sys.name,
                    role = role,
                    stockBarLengthMm = fullBarLen,
                    usedRemnantBarsCount = roleAllocations.count { it.isFromRemnantStock },
                    newFullBarsToBuyCount = roleAllocations.count { !it.isFromRemnantStock },
                    totalCutPiecesCount = expandedPieces.size,
                    totalNetLengthM = totalNetMm / 1000.0,
                    wastePercent = wastePct,
                    newBusinessRemnantsMm = roleAllocations.filter { it.isUsableBusinessRemnant }.map { it.remnantLengthMm },
                    colorName = colorName,
                    colorHex = groupColorHex
                )
            )
        }

        return allAllocations to summaries
    }
}
