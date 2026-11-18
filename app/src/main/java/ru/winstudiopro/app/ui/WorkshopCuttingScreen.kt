package ru.winstudiopro.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.domain.calculator.CornerConnectorOrderItem
import ru.winstudiopro.app.domain.calculator.CuttingPlanManualHelper
import ru.winstudiopro.app.domain.calculator.MoveCutDestination
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.ProfileSupplierOrderLine
import ru.winstudiopro.app.domain.calculator.RubberOrderItem
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private data class SelectedCutForMove(
    val barIndex: Int,
    val cutIndex: Int
)

// =========================================================================================
// МОДУЛЬ «1D РАСКРОЙ ПРОФИЛЯ» (АВТО-ОПТИМИЗАЦИЯ + РУЧНОЕ РЕДАКТИРОВАНИЕ ХЛЫСТОВ И ОТРЕЗКОВ)
// =========================================================================================
@Composable
internal fun WorkshopAndCuttingScreen(
    order: CustomerOrder,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    sillsCatalog: List<SillCatalogItem>,
    extraAccessoriesCatalog: List<ExtraAccessoryItem>,
    workServicesCatalog: List<WorkServiceItem>,
    hardwareBrandsCatalog: List<HardwareBrandItem>,
    stockRemnants: List<StockRemnantBar>,
    stockRubber: List<StockRubberItem> = emptyList(),
    onUpdateOrder: (CustomerOrder) -> Unit,
    onBackToCad: () -> Unit,
    onSaveStockRemnants: (List<StockRemnantBar>) -> Unit,
    onOpenPdfFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current

    val windowResults = remember(
        order.windows,
        profileSystems,
        glazingCatalog,
        colorCatalog,
        sillsCatalog,
        extraAccessoriesCatalog,
        workServicesCatalog,
        hardwareBrandsCatalog
    ) {
        order.windows.mapIndexed { idx, win ->
            WindowBomAndCostEngine.calculateWindow(
                window = win,
                profileSystems = profileSystems,
                glazingCatalog = glazingCatalog,
                colorCatalog = colorCatalog,
                sillsCatalog = sillsCatalog,
                extraAccessoriesCatalog = extraAccessoriesCatalog,
                workServicesCatalog = workServicesCatalog,
                hardwareBrandsCatalog = hardwareBrandsCatalog,
                windowIndexInOrder = idx + 1
            )
        }
    }

    val usableRemnants = remember(stockRemnants) {
        stockRemnants.filter { !it.isFullStockBar && it.quantity > 0 }
    }

    val autoResult = remember(windowResults, usableRemnants, profileSystems) {
        WindowBomAndCostEngine.optimizeOrderProfileCutting(
            windowResults = windowResults,
            stockRemnants = usableRemnants,
            profileSystems = profileSystems
        )
    }

    val isManualMode = order.customCuttingAllocations != null
    val barAllocations = order.customCuttingAllocations ?: autoResult.first
    val procurementSummaries = remember(isManualMode, barAllocations, autoResult, profileSystems) {
        if (isManualMode) {
            CuttingPlanManualHelper.rebuildProcurementSummaries(
                allocations = barAllocations,
                baseAutoSummaries = autoResult.second,
                profileSystems = profileSystems
            )
        } else {
            autoResult.second
        }
    }

    var editingBarIndex by remember { mutableStateOf<Int?>(null) }
    var movingPieceRef by remember { mutableStateOf<SelectedCutForMove?>(null) }

    // Позиции заказа профиля поставщику (профили — выбраны по умолчанию, закладные и резина — сняты по умолчанию)
    var profileOrderLines by remember(order.id) {
        mutableStateOf(
            procurementSummaries.map { sum ->
                ProfileSupplierOrderLine(
                    profileSystemName = sum.profileSystemName,
                    role = sum.role,
                    colorName = sum.colorName,
                    stockBarLengthMm = sum.stockBarLengthMm,
                    barsCount = sum.newFullBarsToBuyCount,
                    isSelected = true
                )
            }
        )
    }
    LaunchedEffect(procurementSummaries) {
        val prevByKey = profileOrderLines.associateBy { Triple(it.profileSystemName, it.role, it.colorName) }
        profileOrderLines = procurementSummaries.map { sum ->
            val prev = prevByKey[Triple(sum.profileSystemName, sum.role, sum.colorName)]
            ProfileSupplierOrderLine(
                profileSystemName = sum.profileSystemName,
                role = sum.role,
                colorName = sum.colorName,
                stockBarLengthMm = sum.stockBarLengthMm,
                barsCount = sum.newFullBarsToBuyCount,
                isSelected = prev?.isSelected ?: true
            )
        }
    }

    val calculatedHardwarePair = remember(windowResults, stockRubber) {
        OrderProcurementHelper.calculateCornerConnectorsAndRubber(windowResults, stockRubber)
    }
    var connectorOrderLines by remember(order.id, calculatedHardwarePair) {
        mutableStateOf(calculatedHardwarePair.first)
    }
    var rubberOrderLines by remember(order.id, calculatedHardwarePair) {
        mutableStateOf(calculatedHardwarePair.second)
    }

    var editingProfileLineIndex by remember { mutableStateOf<Int?>(null) }
    var editingConnectorIndex by remember { mutableStateOf<Int?>(null) }
    var editingRubberIndex by remember { mutableStateOf<Int?>(null) }

    val indexedNonShtapikBars = remember(barAllocations) {
        barAllocations.mapIndexedNotNull { idx, bar ->
            if (bar.role == ProfileRole.SHTAPIK) null else idx to bar
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = strings.cuttingItem,
            onBack = onBackToCad
        ) {
            Surface(
                color = palette.accentTeal,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val applyResult = CuttingPlanManualHelper.applyWarehouseStockChanges(
                            currentStock = stockRemnants,
                            allocations = barAllocations,
                            procurementSummaries = procurementSummaries,
                            profileSystems = profileSystems
                        )
                        onSaveStockRemnants(applyResult.updatedStock)
                        Toast.makeText(
                            context,
                            "Списано остатков: ${applyResult.consumedRemnantsCount} • Добавлено на склад: ${applyResult.addedRemnantsCount}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .testTag("workshop_save_remnants_button")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(
                        "Остатки → Склад",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Surface(
                color = palette.accentBlue,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val file = PdfReportGenerator.generateCuttingAndProcurementPdf(
                            context = context,
                            order = order,
                            allocations = barAllocations,
                            procurement = procurementSummaries,
                            windowResults = windowResults
                        )
                        onOpenPdfFile(file, strings.workshopTaskItem)
                    }
                    .testTag("workshop_pdf_button")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(
                        "PDF в цех",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .testTag("workshop_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    color = if (isManualMode) palette.accentOrange.copy(alpha = 0.14f) else palette.accentBlue.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        0.8.dp,
                        if (isManualMode) palette.accentOrange.copy(alpha = 0.45f) else palette.floatingBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isManualMode) {
                                    "Ручной раскрой (изменения сохранены в заказе)"
                                } else {
                                    "Автоматический раскрой (можно редактировать вручную)"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Нажмите на отрезок для переноса или ✎ у хлыста, чтобы изменить длину",
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                        if (isManualMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = palette.accentOrange,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onUpdateOrder(order.copy(customCuttingAllocations = null))
                                    }
                                    .testTag("workshop_reset_auto_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestartAlt,
                                        contentDescription = "Сбросить в автоматический раскрой",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Сброс",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "1. Потребность в профиле (Рама → Импост → Створка → Дверь → Штапик):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.textPrimary
                )
            }

            itemsIndexed(procurementSummaries) { idx, sum ->
                val orderLine = profileOrderLines.getOrNull(idx) ?: ProfileSupplierOrderLine(
                    profileSystemName = sum.profileSystemName,
                    role = sum.role,
                    colorName = sum.colorName,
                    stockBarLengthMm = sum.stockBarLengthMm,
                    barsCount = sum.newFullBarsToBuyCount,
                    isSelected = true
                )
                Surface(
                    color = if (orderLine.isSelected) palette.cardSurface else palette.cardSurface.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        width = if (orderLine.isSelected) 1.dp else 0.7.dp,
                        color = if (orderLine.isSelected) palette.accentBlue.copy(alpha = 0.45f) else palette.floatingBorder
                    ),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color(sum.colorHex), CircleShape)
                                    .border(0.8.dp, palette.floatingBorder, CircleShape)
                            )
                            Text(
                                text = "${sum.role.titleRu.uppercase()} — ${sum.profileSystemName} [${sum.colorName}] (${sum.stockBarLengthMm} мм)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = palette.accentBlue,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { editingProfileLineIndex = idx },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("workshop_profile_edit_${idx}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Изменить количество хлыстов профиля",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    profileOrderLines = profileOrderLines.mapIndexed { i, item ->
                                        if (i == idx) item.copy(isSelected = !item.isSelected) else item
                                    }
                                },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("workshop_profile_check_${idx}")
                            ) {
                                Icon(
                                    imageVector = if (orderLine.isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    contentDescription = if (orderLine.isSelected) "Исключить профиль из заказа" else "Включить профиль в заказ",
                                    tint = if (orderLine.isSelected) palette.accentBlue else palette.textSecondary
                                )
                            }
                        }
                        Text(
                            "Новых хлыстов: ${sum.newFullBarsToBuyCount} шт. • Из остатков склада: ${sum.usedRemnantBarsCount} шт. • Деталей: ${sum.totalCutPiecesCount} шт. (${String.format(Locale.US, "%.2f", sum.totalNetLengthM)} м) • Отход: ${String.format(Locale.US, "%.1f", sum.wastePercent)}%",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        Text(
                            "В заказ профиля: ${orderLine.barsCount} шт. (${String.format(Locale.US, "%.2f", orderLine.totalLengthMeters)} м)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (orderLine.isSelected) palette.textPrimary else palette.textSecondary
                        )
                        if (sum.newBusinessRemnantsMm.isNotEmpty() && sum.role != ProfileRole.SHTAPIK) {
                            Text(
                                "Деловые остатки (≥400 мм): ${sum.newBusinessRemnantsMm.joinToString(", ") { "${it}мм" }}",
                                fontSize = 11.5.sp,
                                color = palette.accentGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // --- ЗАКЛАДНЫЕ (УГЛОВЫЕ СУХАРИ) ДЛЯ ЗАКАЗА ПРОФИЛЯ (ПО УМОЛЧАНИЮ СНЯТЫ) ---
            item {
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Закладные (угловые сухари) — по желанию в заказ:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = palette.textPrimary
                        )
                        connectorOrderLines.forEachIndexed { idx, conn ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        connectorOrderLines = connectorOrderLines.mapIndexed { i, item ->
                                            if (i == idx) item.copy(isSelected = !item.isSelected) else item
                                        }
                                    },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("workshop_connector_check_${conn.type.name}")
                                ) {
                                    Icon(
                                        imageVector = if (conn.isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                        contentDescription = conn.type.titleRu,
                                        tint = if (conn.isSelected) palette.accentBlue else palette.textSecondary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conn.type.titleRu,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "${conn.setsCount} компл. (${conn.totalCornersCount} шт.)",
                                        fontSize = 11.5.sp,
                                        color = palette.textSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { editingConnectorIndex = idx },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("workshop_connector_edit_${conn.type.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Редактировать количество ${conn.type.titleRu}",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- УПЛОТНИТЕЛЬНАЯ РЕЗИНА ДЛЯ ЗАКАЗА ПРОФИЛЯ (ПОТРЕБНОСТЬ / СКЛАД / КОРОБКИ) ---
            item {
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Резина (уплотнитель) — потребность, склад и заказ коробками:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = palette.textPrimary
                        )
                        rubberOrderLines.forEachIndexed { idx, rub ->
                            val isFirstOfType = idx == rubberOrderLines.indexOfFirst { it.type == rub.type }
                            val checkTag = if (isFirstOfType) {
                                "workshop_rubber_check_${rub.type.name}"
                            } else {
                                "workshop_rubber_check_${rub.type.name}_${idx}"
                            }
                            val editTag = if (isFirstOfType) {
                                "workshop_rubber_edit_${rub.type.name}"
                            } else {
                                "workshop_rubber_edit_${rub.type.name}_${idx}"
                            }
                            val rowBg = when {
                                rub.hasShortage -> palette.accentRed.copy(alpha = 0.08f)
                                rub.lengthMeters > 0.0 -> palette.accentGreen.copy(alpha = 0.06f)
                                else -> Color.Transparent
                            }
                            val rowBorder = when {
                                rub.hasShortage -> BorderStroke(0.9.dp, palette.accentRed.copy(alpha = 0.45f))
                                rub.isSelected -> BorderStroke(0.9.dp, palette.accentBlue.copy(alpha = 0.45f))
                                else -> BorderStroke(0.6.dp, palette.floatingBorder.copy(alpha = 0.6f))
                            }
                            Surface(
                                color = rowBg,
                                shape = RoundedCornerShape(12.dp),
                                border = rowBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("workshop_rubber_row_${idx}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                rubberOrderLines = rubberOrderLines.mapIndexed { i, item ->
                                                    if (i == idx) {
                                                        val nextSel = !item.isSelected
                                                        val nextBoxes = if (nextSel && item.boxesCount <= 0 && item.lengthMeters > 0.0) {
                                                            1
                                                        } else {
                                                            item.boxesCount
                                                        }
                                                        item.copy(isSelected = nextSel, boxesCount = nextBoxes)
                                                    } else item
                                                }
                                            },
                                            modifier = Modifier
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .testTag(checkTag)
                                        ) {
                                            Icon(
                                                imageVector = if (rub.isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                                contentDescription = rub.displayTitle,
                                                tint = if (rub.isSelected) palette.accentBlue else palette.textSecondary
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = rub.displayTitle,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = palette.textPrimary
                                            )
                                            Text(
                                                text = "Потребность: ${String.format(Locale.US, "%.2f", rub.lengthMeters)} м • Склад: ${String.format(Locale.US, "%.2f", rub.warehouseMeters)} м",
                                                fontSize = 11.5.sp,
                                                color = palette.textSecondary
                                            )
                                            if (rub.hasShortage) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.WarningAmber,
                                                        contentDescription = "Нехватка резины на складе",
                                                        tint = palette.accentRed,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Не хватает ${String.format(Locale.US, "%.2f", rub.shortageMeters)} м!",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = palette.accentRed
                                                    )
                                                }
                                            } else if (rub.lengthMeters > 0.0) {
                                                Text(
                                                    text = "На складе достаточно",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = palette.accentGreen
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { editingRubberIndex = idx },
                                            modifier = Modifier
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .testTag(editTag)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Редактировать заказ ${rub.displayTitle}",
                                                tint = palette.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (rub.isSelected) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 10.dp, end = 6.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "В заказ: ${rub.boxesCount} кор. (${String.format(Locale.US, "%.0f", rub.orderedMeters)} м, по ${rub.metersPerBox.roundToInt()} м/кор.)",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = palette.accentBlue,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        rubberOrderLines = rubberOrderLines.mapIndexed { i, item ->
                                                            if (i == idx) item.copy(boxesCount = (item.boxesCount - 1).coerceAtLeast(1)) else item
                                                        }
                                                    },
                                                    enabled = rub.boxesCount > 1,
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier
                                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                        .testTag("workshop_rubber_box_minus_${idx}")
                                                ) {
                                                    Text("−", fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        rubberOrderLines = rubberOrderLines.mapIndexed { i, item ->
                                                            if (i == idx) item.copy(boxesCount = (item.boxesCount + 1).coerceAtMost(999)) else item
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier
                                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                        .testTag("workshop_rubber_box_plus_${idx}")
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- КНОПКА «СФОРМИРОВАТЬ ЗАКАЗ» (PDF «ЗАКАЗ ПРОФИЛЯ») ---
            item {
                val anyOrderSelected = profileOrderLines.any { it.isSelected && it.barsCount > 0 } ||
                    connectorOrderLines.any { it.isSelected && it.setsCount > 0 } ||
                    rubberOrderLines.any { it.isSelected && (it.boxesCount > 0 || it.lengthMeters > 0.0) }
                Button(
                    onClick = {
                        if (!anyOrderSelected) {
                            Toast.makeText(context, "Выберите хотя бы одну позицию с ненулевым количеством", Toast.LENGTH_SHORT).show()
                        } else {
                            val file = PdfReportGenerator.generateProfileOrderPdf(
                                context = context,
                                order = order,
                                profileLines = profileOrderLines,
                                connectorLines = connectorOrderLines,
                                rubberLines = rubberOrderLines
                            )
                            onOpenPdfFile(file, "Заказ профиля")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("workshop_profile_order_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Сформировать заказ профиля (PDF)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "2. Визуальная карта раскроя хлыстов (без штапиков):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.textPrimary
                )
            }

            items(indexedNonShtapikBars, key = { (idx, bar) -> "${idx}_${bar.profileSystemName}_${bar.role.name}_${bar.barNumber}" }) { (barIdx, bar) ->
                val kerfMm = CuttingPlanManualHelper.resolveProfileForBar(bar, profileSystems)
                    ?.sawKerfMm?.roundToInt() ?: 4
                val hasOverflow = CuttingPlanManualHelper.barHasOverflow(bar, kerfMm)

                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        width = if (hasOverflow) 1.5.dp else 0.8.dp,
                        color = if (hasOverflow) palette.accentRed else palette.floatingBorder
                    ),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(bar.colorHex), CircleShape)
                                        .border(0.7.dp, palette.floatingBorder, CircleShape)
                                )
                                Text(
                                    "${bar.role.titleRu.uppercase()} (${bar.profileSystemName} • ${bar.colorName}) — Хлыст #${bar.barNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = palette.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (bar.isFromRemnantStock) palette.accentGreen.copy(alpha = 0.14f) else palette.accentBlue.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    0.7.dp,
                                    if (bar.isFromRemnantStock) palette.accentGreen.copy(alpha = 0.5f) else palette.accentBlue.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { editingBarIndex = barIdx }
                                    .testTag("workshop_edit_bar_${barIdx}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (bar.isFromRemnantStock) {
                                            "Склад: ${bar.sourceLengthMm} мм"
                                        } else {
                                            "Хлыст: ${bar.sourceLengthMm} мм"
                                        },
                                        fontSize = 11.sp,
                                        color = if (bar.isFromRemnantStock) palette.accentGreen else palette.accentBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Изменить длину хлыста",
                                        tint = if (bar.isFromRemnantStock) palette.accentGreen else palette.accentBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (hasOverflow) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = "Превышение длины хлыста",
                                    tint = palette.accentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Переполнение на ${bar.usedWithKerfMm - bar.sourceLengthMm} мм! Перенесите отрезок на другой хлыст.",
                                    color = palette.accentRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, palette.floatingBorder, RoundedCornerShape(8.dp))
                        ) {
                            val srcLen = bar.sourceLengthMm.toFloat().coerceAtLeast(1f)
                            bar.cuts.forEachIndexed { cutIdx, piece ->
                                val wWeight = (piece.cutLengthMm / srcLen).coerceAtLeast(0.06f)
                                Box(
                                    modifier = Modifier
                                        .weight(wWeight)
                                        .fillMaxHeight()
                                        .background(if (hasOverflow) Color(0xFFFFE0B2) else Color(0xFFB3E5FC))
                                        .border(0.6.dp, if (hasOverflow) Color(0xFFE65100) else Color(0xFF0288D1))
                                        .clickable {
                                            movingPieceRef = SelectedCutForMove(barIndex = barIdx, cutIndex = cutIdx)
                                        }
                                        .testTag("workshop_cut_${barIdx}_${cutIdx}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "${piece.cutLengthMm}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0D2538),
                                            maxLines = 1
                                        )
                                        Text(
                                            piece.shortTag,
                                            fontSize = 8.5.sp,
                                            color = Color(0xFF1E3A5F),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (bar.remnantLengthMm > 0) {
                                val remWeight = (bar.remnantLengthMm / srcLen).coerceAtLeast(0.05f)
                                Box(
                                    modifier = Modifier
                                        .weight(remWeight)
                                        .fillMaxHeight()
                                        .background(
                                            if (bar.isUsableBusinessRemnant) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Ост:${bar.remnantLengthMm}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1F2937),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- ДИАЛОГ ИЗМЕНЕНИЯ ИСХОДНОЙ ДЛИНЫ ХЛЫСТА (НАПР. ПОВРЕЖДЕННЫЙ КРАЙ ИЛИ ОСТАТОК СО СКЛАДА) ---
    val activeEditBarIdx = editingBarIndex
    if (activeEditBarIdx != null && activeEditBarIdx in barAllocations.indices) {
        val bar = barAllocations[activeEditBarIdx]
        val matchedSys = CuttingPlanManualHelper.resolveProfileForBar(bar, profileSystems)
        val defaultStockLen = matchedSys?.stockBarLengthMm?.roundToInt()?.coerceIn(3000, 8500) ?: 6000
        val kerfMm = matchedSys?.sawKerfMm?.roundToInt() ?: 4

        val matchingStockRemnants = remember(stockRemnants, barAllocations, activeEditBarIdx, matchedSys) {
            CuttingPlanManualHelper.findAvailableWarehouseRemnants(
                stockRemnants = stockRemnants,
                allocations = barAllocations,
                excludeBarIndex = activeEditBarIdx,
                profileSystemName = bar.profileSystemName,
                profileSystemId = matchedSys?.id,
                role = bar.role,
                colorName = bar.colorName
            )
        }

        EditBarSourceDialog(
            bar = bar,
            defaultStockLen = defaultStockLen,
            kerfMm = kerfMm,
            matchingRemnants = matchingStockRemnants,
            onDismiss = { editingBarIndex = null },
            onConfirm = { newLengthMm, isRemnant ->
                val updated = CuttingPlanManualHelper.changeBarSource(
                    allocations = barAllocations,
                    barIndex = activeEditBarIdx,
                    newSourceLengthMm = newLengthMm,
                    isFromRemnantStock = isRemnant,
                    profileSystems = profileSystems
                )
                onUpdateOrder(order.copy(customCuttingAllocations = updated))
                editingBarIndex = null
            }
        )
    }

    // --- ДИАЛОГ ПЕРЕНОСА ОТРЕЗКА НА ДРУГОЙ ХЛЫСТ ИЛИ ОСТАТОК ---
    val activeMoveRef = movingPieceRef
    if (activeMoveRef != null && activeMoveRef.barIndex in barAllocations.indices) {
        val srcBar = barAllocations[activeMoveRef.barIndex]
        if (activeMoveRef.cutIndex in srcBar.cuts.indices) {
            val piece = srcBar.cuts[activeMoveRef.cutIndex]
            val matchedSys = CuttingPlanManualHelper.resolveProfileForBar(srcBar, profileSystems)
            val defaultStockLen = matchedSys?.stockBarLengthMm?.roundToInt()?.coerceIn(3000, 8500) ?: 6000
            val kerfMm = matchedSys?.sawKerfMm?.roundToInt() ?: 4

            val compatibleExistingBars = barAllocations.mapIndexedNotNull { idx, other ->
                if (idx != activeMoveRef.barIndex &&
                    other.role == srcBar.role &&
                    other.profileSystemName == srcBar.profileSystemName &&
                    other.colorName.equals(srcBar.colorName, ignoreCase = true)
                ) {
                    idx to other
                } else null
            }

            val matchingStockRemnants = CuttingPlanManualHelper.findAvailableWarehouseRemnants(
                stockRemnants = stockRemnants,
                allocations = barAllocations,
                excludeBarIndex = -1,
                profileSystemName = srcBar.profileSystemName,
                profileSystemId = matchedSys?.id,
                role = srcBar.role,
                colorName = srcBar.colorName,
                minLengthMm = piece.cutLengthMm
            )

            MoveCutPieceDialog(
                sourceBar = srcBar,
                piece = piece,
                kerfMm = kerfMm,
                defaultStockLen = defaultStockLen,
                compatibleBars = compatibleExistingBars,
                matchingRemnants = matchingStockRemnants,
                onDismiss = { movingPieceRef = null },
                onSelectDestination = { dest ->
                    val updated = CuttingPlanManualHelper.moveCutPiece(
                        allocations = barAllocations,
                        fromBarIndex = activeMoveRef.barIndex,
                        cutIndex = activeMoveRef.cutIndex,
                        destination = dest,
                        profileSystems = profileSystems
                    )
                    onUpdateOrder(order.copy(customCuttingAllocations = updated))
                    movingPieceRef = null
                }
            )
        }
    }

    // --- ДИАЛОГИ РЕДАКТИРОВАНИЯ ПОЗИЦИЙ В ЗАКАЗЕ ПРОФИЛЯ ---
    val editProfileIdx = editingProfileLineIndex
    if (editProfileIdx != null && editProfileIdx in profileOrderLines.indices) {
        val line = profileOrderLines[editProfileIdx]
        NumericOrderQuantityDialog(
            title = "Количество хлыстов: ${line.role.titleRu}",
            subtitle = "${line.profileSystemName} • ${line.colorName} (хлыст ${line.stockBarLengthMm} мм)",
            label = "Количество хлыстов (шт.)",
            initialText = line.barsCount.toString(),
            allowDecimal = false,
            inputTestTag = "workshop_profile_qty_input",
            confirmTestTag = "workshop_profile_qty_confirm",
            onDismiss = { editingProfileLineIndex = null },
            onConfirm = { raw ->
                val newBars = raw.toIntOrNull()?.coerceIn(0, 9999) ?: line.barsCount
                profileOrderLines = profileOrderLines.mapIndexed { i, item ->
                    if (i == editProfileIdx) item.copy(barsCount = newBars, isSelected = if (newBars > 0) true else item.isSelected) else item
                }
                editingProfileLineIndex = null
            }
        )
    }

    val editConnIdx = editingConnectorIndex
    if (editConnIdx != null && editConnIdx in connectorOrderLines.indices) {
        val conn = connectorOrderLines[editConnIdx]
        NumericOrderQuantityDialog(
            title = conn.type.titleRu,
            subtitle = "Укажите количество комплектов (1 компл. = 4 угла)",
            label = "Комплектов (компл.)",
            initialText = conn.setsCount.toString(),
            allowDecimal = false,
            inputTestTag = "workshop_connector_qty_input",
            confirmTestTag = "workshop_connector_qty_confirm",
            onDismiss = { editingConnectorIndex = null },
            onConfirm = { raw ->
                val newSets = raw.toIntOrNull()?.coerceIn(0, 99999) ?: conn.setsCount
                connectorOrderLines = connectorOrderLines.mapIndexed { i, item ->
                    if (i == editConnIdx) item.copy(setsCount = newSets, isSelected = if (newSets > 0) true else item.isSelected) else item
                }
                editingConnectorIndex = null
            }
        )
    }

    val editRubIdx = editingRubberIndex
    if (editRubIdx != null && editRubIdx in rubberOrderLines.indices) {
        val rub = rubberOrderLines[editRubIdx]
        NumericOrderQuantityDialog(
            title = rub.displayTitle,
            subtitle = "Укажите количество коробок к заказу (1 коробка = ${rub.metersPerBox.roundToInt()} пог. м)",
            label = "Количество коробок (кор.)",
            initialText = rub.boxesCount.coerceAtLeast(0).toString(),
            allowDecimal = false,
            inputTestTag = "workshop_rubber_qty_input",
            confirmTestTag = "workshop_rubber_qty_confirm",
            onDismiss = { editingRubberIndex = null },
            onConfirm = { raw ->
                val newBoxes = raw.toIntOrNull()?.coerceIn(0, 9999) ?: rub.boxesCount
                rubberOrderLines = rubberOrderLines.mapIndexed { i, item ->
                    if (i == editRubIdx) {
                        item.copy(
                            boxesCount = newBoxes,
                            isSelected = if (newBoxes > 0) true else item.isSelected
                        )
                    } else item
                }
                editingRubberIndex = null
            }
        )
    }
}

@Composable
private fun NumericOrderQuantityDialog(
    title: String,
    subtitle: String,
    label: String,
    initialText: String,
    allowDecimal: Boolean,
    inputTestTag: String,
    confirmTestTag: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var textValue by remember(initialText) { mutableStateOf(initialText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = palette.textPrimary)
                Text(subtitle, fontSize = 12.sp, color = palette.textSecondary)
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { raw ->
                        textValue = if (allowDecimal) {
                            raw.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                        } else {
                            raw.filter { it.isDigit() }.take(5)
                        }
                    },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(inputTestTag)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Отмена", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onConfirm(textValue) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(confirmTestTag)
                    ) {
                        Text("Применить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBarSourceDialog(
    bar: CutBarAllocation,
    defaultStockLen: Int,
    kerfMm: Int,
    matchingRemnants: List<StockRemnantBar>,
    onDismiss: () -> Unit,
    onConfirm: (newLengthMm: Int, isRemnant: Boolean) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var lengthInput by remember(bar) { mutableStateOf(bar.sourceLengthMm.toString()) }
    var isFromRemnant by remember(bar) { mutableStateOf(bar.isFromRemnantStock) }

    val parsedLen = lengthInput.toIntOrNull()
    val requiredMm = CuttingPlanManualHelper.calculateUsedWithKerfMm(bar.cuts, kerfMm)
    val previewRemnant = parsedLen?.minus(requiredMm)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Длина хлыста #${bar.barNumber} (${bar.role.titleRu})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "На хлысте ${bar.cuts.size} дет. (${requiredMm} мм с учётом реза ${kerfMm} мм).",
                    fontSize = 12.5.sp,
                    color = palette.textSecondary
                )

                OutlinedTextField(
                    value = lengthInput,
                    onValueChange = { raw ->
                        lengthInput = raw.filter { it.isDigit() }.take(5)
                    },
                    label = { Text("Фактическая длина заготовки (мм)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workshop_edit_bar_length_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            lengthInput = defaultStockLen.toString()
                            isFromRemnant = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Целый $defaultStockLen", fontSize = 11.5.sp)
                    }
                    val damagedLen = (defaultStockLen - 1000).coerceAtLeast(1000)
                    OutlinedButton(
                        onClick = {
                            lengthInput = damagedLen.toString()
                            isFromRemnant = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Брак края $damagedLen", fontSize = 11.5.sp)
                    }
                }

                if (previewRemnant != null) {
                    Text(
                        text = if (previewRemnant >= 0) {
                            "Остаток после резки: $previewRemnant мм"
                        } else {
                            "Внимание! Не хватает ${-previewRemnant} мм для текущих отрезков."
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (previewRemnant >= 0) palette.accentGreen else palette.accentRed
                    )
                }

                if (matchingRemnants.isNotEmpty()) {
                    Text(
                        text = "Или взять деловой остаток со склада:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = palette.textPrimary
                    )
                    matchingRemnants.forEach { rem ->
                        Surface(
                            color = palette.background,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    lengthInput = rem.lengthMm.toString()
                                    isFromRemnant = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Остаток ${rem.lengthMm} мм",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = palette.accentGreen
                                )
                                Text(
                                    text = "В наличии: ${rem.quantity} шт.",
                                    fontSize = 11.5.sp,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Отмена", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        enabled = parsedLen != null && parsedLen in 300..12000,
                        onClick = {
                            val safeLen = parsedLen ?: bar.sourceLengthMm
                            onConfirm(safeLen, isFromRemnant)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("workshop_edit_bar_confirm")
                    ) {
                        Text("Применить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveCutPieceDialog(
    sourceBar: CutBarAllocation,
    piece: AllocatedCutPiece,
    kerfMm: Int,
    defaultStockLen: Int,
    compatibleBars: List<Pair<Int, CutBarAllocation>>,
    matchingRemnants: List<StockRemnantBar>,
    onDismiss: () -> Unit,
    onSelectDestination: (MoveCutDestination) -> Unit
) {
    val palette = LocalWinStudioPalette.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Перенести отрезок ${piece.cutLengthMm} мм (${piece.shortTag})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "${piece.positionLabel} • Сейчас на хлысте #${sourceBar.barNumber} (${sourceBar.sourceLengthMm} мм)",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )

                if (compatibleBars.isNotEmpty()) {
                    Text(
                        text = "Другие хлысты этого профиля и цвета:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = palette.textPrimary
                    )
                    compatibleBars.forEach { (idx, targetBar) ->
                        val neededWithKerf = piece.cutLengthMm + if (targetBar.cuts.isNotEmpty()) kerfMm else 0
                        val freeAfter = targetBar.remnantLengthMm - neededWithKerf
                        val fits = freeAfter >= 0
                        Surface(
                            color = palette.background,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                0.8.dp,
                                if (fits) palette.accentGreen.copy(alpha = 0.55f) else palette.accentRed.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectDestination(MoveCutDestination.ExistingBar(idx)) }
                                .testTag("workshop_move_to_bar_${idx}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Хлыст #${targetBar.barNumber} (${if (targetBar.isFromRemnantStock) "склад" else "целый"} ${targetBar.sourceLengthMm} мм)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = if (fits) {
                                            "Поместится • Остаток станет: $freeAfter мм"
                                        } else {
                                            "Превышение на ${-freeAfter} мм"
                                        },
                                        fontSize = 11.sp,
                                        color = if (fits) palette.accentGreen else palette.accentRed
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = "Перенести сюда",
                                    tint = if (fits) palette.accentGreen else palette.accentRed
                                )
                            }
                        }
                    }
                }

                // Всегда отображаем раздел переноса на деловой остаток со склада
                var showManualRemnantInput by remember { mutableStateOf(false) }
                var manualRemnantLenInput by remember {
                    mutableStateOf((piece.cutLengthMm + 300).coerceAtLeast(800).toString())
                }

                Text(
                    text = "Деловые остатки со склада:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = palette.textPrimary
                )
                if (matchingRemnants.isNotEmpty()) {
                    matchingRemnants.forEachIndexed { remIdx, rem ->
                        val remAfter = rem.lengthMm - piece.cutLengthMm
                        Surface(
                            color = palette.background,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.8.dp, palette.accentTeal.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectDestination(MoveCutDestination.WarehouseRemnant(rem.lengthMm)) }
                                .testTag("workshop_move_to_remnant_${remIdx}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Остаток склада ${rem.lengthMm} мм (${rem.quantity} шт.)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = palette.accentTeal
                                    )
                                    Text(
                                        text = "Останется $remAfter мм",
                                        fontSize = 11.sp,
                                        color = palette.textSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = "Взять остаток со склада",
                                    tint = palette.accentTeal
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = palette.background,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.6.dp, palette.floatingBorder)
                    ) {
                        Text(
                            text = "На складе нет подходящих деловых остатков (≥ ${piece.cutLengthMm} мм) для «${sourceBar.role.titleRu} • ${sourceBar.colorName}».",
                            fontSize = 11.5.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }

                if (!showManualRemnantInput) {
                    OutlinedButton(
                        onClick = { showManualRemnantInput = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("workshop_open_manual_remnant_btn")
                    ) {
                        Text(
                            text = "+ Перенести на остаток со склада (указать мм)",
                            color = palette.accentTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    val manualLen = manualRemnantLenInput.toIntOrNull()
                    val isValidManual = manualLen != null && manualLen >= piece.cutLengthMm && manualLen <= 12000
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualRemnantLenInput,
                            onValueChange = { raw ->
                                manualRemnantLenInput = raw.filter { it.isDigit() }.take(5)
                            },
                            label = { Text("Длина остатка (мм)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("workshop_manual_remnant_input")
                        )
                        Button(
                            enabled = isValidManual,
                            onClick = {
                                val len = manualLen ?: return@Button
                                onSelectDestination(MoveCutDestination.WarehouseRemnant(len))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("workshop_manual_remnant_confirm")
                        ) {
                            Text("Взять", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    color = palette.accentBlue.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, palette.accentBlue.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectDestination(MoveCutDestination.NewFullBar(defaultStockLen)) }
                        .testTag("workshop_move_to_new_bar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "+ Вынести на новый целый хлыст ($defaultStockLen мм)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = palette.accentBlue
                            )
                            Text(
                                text = "Остаток: ${(defaultStockLen - piece.cutLengthMm).coerceAtLeast(0)} мм",
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Закрыть", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
