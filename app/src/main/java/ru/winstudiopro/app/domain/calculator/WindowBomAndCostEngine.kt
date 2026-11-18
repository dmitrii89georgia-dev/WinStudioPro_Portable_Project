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
        windowIndexInOrder: Int = 0
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
                    val isDoubleShtulp = props.openingType == SashOpeningType.SHTULP_TURN_TURN ||
                        props.openingType == SashOpeningType.SHTULP_TILTTURN_L_TURN_R ||
                        props.openingType == SashOpeningType.SHTULP_TURN_L_TILTTURN_R
                    val isVertDouble = props.openingType == SashOpeningType.VERT_SLIDE_UP ||
                        props.openingType == SashOpeningType.VERT_SLIDE_BOTH

                    val sashLeavesCount = if (isDoubleShtulp || isVertDouble) 2 else 1
                    val singleLightW = if (isDoubleShtulp) lightW / 2f else lightW
                    val singleLightH = if (isVertDouble) lightH / 2f else lightH

                    val isAnyDoor = props.isDoorSash || props.isReverseDoorSash
                    val sashShortTag = if (isAnyDoor) {
                        doorCounter += 1
                        if (totalDoorSashesInWin > 1) "$winNum,$doorCounter" else "$winNum"
                    } else {
                        sashCounter += 1
                        if (totalWindowSashesInWin > 1) "$winNum,$sashCounter" else "$winNum"
                    }

                    val overlapMm = when {
                        props.isReverseDoorSash -> profile.revDoorOverlapMm
                        props.isDoorSash -> profile.doorOverlapMm
                        else -> profile.sashOverlapMm
                    }
                    val sashOuterWFloat = singleLightW + 2f * overlapMm
                    val sashOuterHFloat = singleLightH + 2f * overlapMm
                    val sashOuterW = sashOuterWFloat.roundToInt()
                    val sashOuterH = sashOuterHFloat.roundToInt()
                    val sashProfileW = when {
                        props.isReverseDoorSash -> profile.revDoorInnerWidthE2Mm
                        props.isDoorSash -> profile.doorSashWidthMm
                        else -> profile.sashWidthMm
                    }
                    val sashRole = if (isAnyDoor) ProfileRole.DOOR_SASH else ProfileRole.SASH
                    val roleTitleLabel = when {
                        props.isReverseDoorSash -> "Обр.дверь"
                        props.isDoorSash -> "Дверь"
                        else -> "Створка"
                    }

                    profileCuts.add(
                        ProfileCutPiece(
                            windowName = window.name,
                            profileSystemId = profile.id,
                            profileSystemName = profile.name,
                            materialType = profile.materialType,
                            role = sashRole,
                            positionLabel = "$cellCode $roleTitleLabel гориз. (${props.openingType.shortCode})",
                            shortTag = sashShortTag,
                            nominalLengthMm = sashOuterW,
                            cutLengthMm = sashOuterW + weld2Sides,
                            leftAngleDeg = 45,
                            rightAngleDeg = 45,
                            quantity = 2 * sashLeavesCount * qty
                        )
                    )
                    profileCuts.add(
                        ProfileCutPiece(
                            windowName = window.name,
                            profileSystemId = profile.id,
                            profileSystemName = profile.name,
                            materialType = profile.materialType,
                            role = sashRole,
                            positionLabel = "$cellCode $roleTitleLabel верт. (${props.openingType.shortCode})",
                            shortTag = sashShortTag,
                            nominalLengthMm = sashOuterH,
                            cutLengthMm = sashOuterH + weld2Sides,
                            leftAngleDeg = 45,
                            rightAngleDeg = 45,
                            quantity = 2 * sashLeavesCount * qty
                        )
                    )

                    val leafPerimM = sashLeavesCount * 2.0 * (sashOuterW + sashOuterH) / 1000.0
                    if (isAnyDoor) {
                        doorSashPerimeterM += leafPerimM
                        doorSashLeavesTotalCount += sashLeavesCount
                    } else {
                        sashPerimeterM += leafPerimM
                        windowSashLeavesTotalCount += sashLeavesCount
                    }
                    rubberPerimeterM += leafPerimM * 2.0
                    if (props.openingType != SashOpeningType.BLIND_SASH) {
                        rebateRubberPerUnitM += leafPerimM
                    }

                    val sashInnerLightW = max(80f, sashOuterWFloat - 2f * sashProfileW)
                    val sashInnerLightH = max(80f, sashOuterHFloat - 2f * sashProfileW)

                    if (props.hasHorizontalSashImpost) {
                        // Внутренний горизонтальный импост внутри двери/створки (как на 01:44 в видео!)
                        impCounter += 1
                        val innerImpShortTag = "$winNum,$impCounter"
                        val sashImpLen = sashInnerLightW.roundToInt()
                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = ProfileRole.IMPOST,
                                positionLabel = "$cellCode Импост $innerImpShortTag внутри створки/двери",
                                shortTag = innerImpShortTag,
                                nominalLengthMm = sashImpLen,
                                cutLengthMm = sashImpLen,
                                leftAngleDeg = 90,
                                rightAngleDeg = 90,
                                quantity = sashLeavesCount * qty
                            )
                        )
                        impostsLengthM += (sashImpLen * sashLeavesCount) / 1000.0
                        impostPiecesCount += sashLeavesCount

                        val botH = (props.sashImpostHeightFromBottomMm - sashProfileW - profile.impostWidthMm / 2f)
                            .coerceIn(80f, (sashInnerLightH - 120f).coerceAtLeast(80f))
                        val topH = (sashInnerLightH - botH - profile.impostWidthMm).coerceAtLeast(80f)

                        val glassW = finalizeGlassDim(sashInnerLightW - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                        val topGlassH = finalizeGlassDim(topH - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                        val botGlassH = finalizeGlassDim(botH - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)

                        val topArea = (glassW.toDouble() * topGlassH.toDouble()) / 1_000_000.0
                        val botArea = (glassW.toDouble() * botGlassH.toDouble()) / 1_000_000.0

                        val topIsSand = props.isSandwichFilling
                        val botIsSand = props.bottomPartIsSandwich || props.isSandwichFilling

                        glazingSpecs.add(
                            GlazingSpecItem(
                                windowName = window.name,
                                cellLabel = "$cellCode ($roleTitleLabel Верх, проём ${lightW.roundToInt()}×${lightH.roundToInt()})",
                                formula = if (topIsSand) sandwichItem.formula else glazing.formula,
                                isSandwich = topIsSand,
                                widthMm = glassW,
                                heightMm = topGlassH,
                                quantity = sashLeavesCount * qty,
                                singleAreaM2 = topArea,
                                totalAreaM2 = topArea * sashLeavesCount * qty
                            )
                        )
                        glazingSpecs.add(
                            GlazingSpecItem(
                                windowName = window.name,
                                cellLabel = "$cellCode ($roleTitleLabel Низ)",
                                formula = if (botIsSand) sandwichItem.formula else glazing.formula,
                                isSandwich = botIsSand,
                                widthMm = glassW,
                                heightMm = botGlassH,
                                quantity = sashLeavesCount * qty,
                                singleAreaM2 = botArea,
                                totalAreaM2 = botArea * sashLeavesCount * qty
                            )
                        )
                        if (topIsSand) sandwichAreaM2 += topArea * sashLeavesCount else glazingAreaM2 += topArea * sashLeavesCount
                        if (botIsSand) sandwichAreaM2 += botArea * sashLeavesCount else glazingAreaM2 += botArea * sashLeavesCount

                        val horizShtLen = sashInnerLightW.roundToInt()
                        val topVertShtLen = max(40f, topH - shtapikVertDeductBothSidesMm).roundToInt()
                        val botVertShtLen = max(40f, botH - shtapikVertDeductBothSidesMm).roundToInt()
                        val shtPerim = sashLeavesCount * (2.0 * (horizShtLen + topVertShtLen) + 2.0 * (horizShtLen + botVertShtLen)) / 1000.0
                        shtapikPerimeterM += shtPerim
                        val topPerimM = sashLeavesCount * 2.0 * (sashInnerLightW + topH) / 1000.0
                        val botPerimM = sashLeavesCount * 2.0 * (sashInnerLightW + botH) / 1000.0
                        rubberPerimeterM += topPerimM + botPerimM
                        val seatMm = when {
                            props.isReverseDoorSash -> profile.revDoorGlazingSeatE3Mm
                            props.isDoorSash -> profile.doorGlazingSeatD3Mm
                            else -> profile.sashGlazingSeatC3Mm
                        }
                        val topThick = if (topIsSand) sandwichItem.thicknessMm else glazing.thicknessMm.coerceAtLeast(4)
                        val botThick = if (botIsSand) sandwichItem.thicknessMm else glazing.thicknessMm.coerceAtLeast(4)
                        val wedgeTop = OrderProcurementHelper.selectOptimalWedgeVariant(profile, activeShtapikH2, topThick, seatMm)
                        val wedgeBot = OrderProcurementHelper.selectOptimalWedgeVariant(profile, activeShtapikH2, botThick, seatMm)
                        wedgeCostPerUnit += topPerimM * (wedgeTop?.pricePerMeter ?: profile.priceRubberPerM)
                        wedgeCostPerUnit += botPerimM * (wedgeBot?.pricePerMeter ?: profile.priceRubberPerM)
                        if (wedgeTop != null) usedWedgeLabels.add(wedgeTop.name)
                        if (wedgeBot != null) usedWedgeLabels.add(wedgeBot.name)

                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = ProfileRole.SHTAPIK,
                                positionLabel = "$cellCode Штапик $roleTitleLabel гориз.$shtapikNameSuffix",
                                shortTag = sashShortTag,
                                nominalLengthMm = horizShtLen,
                                cutLengthMm = horizShtLen,
                                leftAngleDeg = shtapikCutAngle,
                                rightAngleDeg = shtapikCutAngle,
                                quantity = 4 * sashLeavesCount * qty
                            )
                        )
                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = ProfileRole.SHTAPIK,
                                positionLabel = "$cellCode Штапик $roleTitleLabel верт. (верх)$shtapikNameSuffix",
                                shortTag = sashShortTag,
                                nominalLengthMm = topVertShtLen,
                                cutLengthMm = topVertShtLen,
                                leftAngleDeg = shtapikCutAngle,
                                rightAngleDeg = shtapikCutAngle,
                                quantity = 2 * sashLeavesCount * qty
                            )
                        )
                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = ProfileRole.SHTAPIK,
                                positionLabel = "$cellCode Штапик $roleTitleLabel верт. (низ)$shtapikNameSuffix",
                                shortTag = sashShortTag,
                                nominalLengthMm = botVertShtLen,
                                cutLengthMm = botVertShtLen,
                                leftAngleDeg = shtapikCutAngle,
                                rightAngleDeg = shtapikCutAngle,
                                quantity = 2 * sashLeavesCount * qty
                            )
                        )
                    } else {
                        val glassW = finalizeGlassDim(sashInnerLightW - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                        val glassH = finalizeGlassDim(sashInnerLightH - profile.glazingClearanceTotalMm, window.roundGlassTo5Mm)
                        val singleArea = (glassW.toDouble() * glassH.toDouble()) / 1_000_000.0
                        val isSand = props.isSandwichFilling

                        glazingSpecs.add(
                            GlazingSpecItem(
                                windowName = window.name,
                                cellLabel = "$cellCode ($roleTitleLabel, проём ${lightW.roundToInt()}×${lightH.roundToInt()})",
                                formula = if (isSand) sandwichItem.formula else glazing.formula,
                                isSandwich = isSand,
                                widthMm = glassW,
                                heightMm = glassH,
                                quantity = sashLeavesCount * qty,
                                singleAreaM2 = singleArea,
                                totalAreaM2 = singleArea * sashLeavesCount * qty
                            )
                        )
                        if (isSand) sandwichAreaM2 += singleArea * sashLeavesCount else glazingAreaM2 += singleArea * sashLeavesCount

                        val horizShtLen = sashInnerLightW.roundToInt()
                        val vertShtLen = max(40f, sashInnerLightH - shtapikVertDeductBothSidesMm).roundToInt()
                        val shtPerim = sashLeavesCount * 2.0 * (horizShtLen + vertShtLen) / 1000.0
                        shtapikPerimeterM += shtPerim
                        val sashGlassPerimM = sashLeavesCount * 2.0 * (sashInnerLightW + sashInnerLightH) / 1000.0
                        rubberPerimeterM += sashGlassPerimM
                        val seatMm = when {
                            props.isReverseDoorSash -> profile.revDoorGlazingSeatE3Mm
                            props.isDoorSash -> profile.doorGlazingSeatD3Mm
                            else -> profile.sashGlazingSeatC3Mm
                        }
                        val glassThickMm = if (isSand) sandwichItem.thicknessMm else glazing.thicknessMm.coerceAtLeast(4)
                        val matchedWedge = OrderProcurementHelper.selectOptimalWedgeVariant(profile, activeShtapikH2, glassThickMm, seatMm)
                        wedgeCostPerUnit += sashGlassPerimM * (matchedWedge?.pricePerMeter ?: profile.priceRubberPerM)
                        if (matchedWedge != null) usedWedgeLabels.add(matchedWedge.name)

                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = ProfileRole.SHTAPIK,
                                positionLabel = "$cellCode Штапик $roleTitleLabel гориз.$shtapikNameSuffix",
                                shortTag = sashShortTag,
                                nominalLengthMm = horizShtLen,
                                cutLengthMm = horizShtLen,
                                leftAngleDeg = shtapikCutAngle,
                                rightAngleDeg = shtapikCutAngle,
                                quantity = 2 * sashLeavesCount * qty
                            )
                        )
                        profileCuts.add(
                            ProfileCutPiece(
                                windowName = window.name,
                                profileSystemId = profile.id,
                                profileSystemName = profile.name,
                                materialType = profile.materialType,
                                role = ProfileRole.SHTAPIK,
                                positionLabel = "$cellCode Штапик $roleTitleLabel верт.$shtapikNameSuffix",
                                shortTag = sashShortTag,
                                nominalLengthMm = vertShtLen,
                                cutLengthMm = vertShtLen,
                                leftAngleDeg = shtapikCutAngle,
                                rightAngleDeg = shtapikCutAngle,
                                quantity = 2 * sashLeavesCount * qty
                            )
                        )
                    }

                    val falzW = (sashOuterW - 16).coerceAtLeast(250)
                    val falzH = (sashOuterH - 16).coerceAtLeast(250)
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
                        val mosqArea = (sashOuterW.toDouble() * sashOuterH.toDouble()) / 1_000_000.0
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

        // Чистая стоимость профилей (рама, импост, створка, дверная створка)
        val costFrame = framePerimeterM * (profile.priceFramePerM + profile.priceReinforcementFramePerM)
        val costImposts = impostsLengthM * (profile.priceImpostPerM + profile.priceReinforcementImpostPerM)
        val costSashesOnly = sashPerimeterM * (profile.priceSashPerM + profile.priceReinforcementSashPerM)
        val costDoorSashesOnly = doorSashPerimeterM * (profile.priceDoorSashPerM + profile.priceReinforcementSashPerM)

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

        val profilesBaseCost = costFrame + costImposts + costSashesOnly + costDoorSashesOnly + costCornerConnectors + costShtapikAndRubber
        val costColorMarkup = profilesBaseCost * (color.priceMarkupPercent / 100.0)

        val rawUnitCost = costFrame + costImposts + costSashesOnly + costDoorSashesOnly +
            costCornerConnectors + costShtapikAndRubber + costGlazingAndSandwich + hardwareCostTotal +
            mosquitoCostTotal + addonsCost + extrasAndWorksCost + costColorMarkup

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
            shtapikPerimeterM = shtapikPerimeterM,
            rubberPerimeterM = rubberPerimeterM,
            glazingAreaM2 = glazingAreaM2,
            sandwichAreaM2 = sandwichAreaM2,
            cornerConnectorsCount = cornerConnectorsCount,
            costFrame = costFrame,
            costImposts = costImposts,
            costSashes = costSashesOnly,
            costDoorSashes = costDoorSashesOnly,
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
            matchedWedgeSummary = usedWedgeLabels.distinct().joinToString(", ")
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
