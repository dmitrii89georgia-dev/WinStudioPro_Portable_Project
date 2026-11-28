package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import ru.winstudiopro.app.domain.calculator.MosquitoOrderItem
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.io.File
import java.util.Locale
import kotlin.math.max

// =========================================================================================
// РЕДАКТОР «ЗАКАЗ МОСКИТНЫХ СЕТОК» С БЫСТРЫМ ВЫБОРОМ (О / Д / П) И РЕДАКТИРОВАНИЕМ ПЕРЕД PDF
// =========================================================================================
@Composable
internal fun MosquitoOrderScreen(
    order: CustomerOrder,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    sillsCatalog: List<SillCatalogItem>,
    extraAccessoriesCatalog: List<ExtraAccessoryItem>,
    workServicesCatalog: List<WorkServiceItem>,
    hardwareBrandsCatalog: List<HardwareBrandItem>,
    mosquitoSystemsCatalog: List<MosquitoSystemItem>,
    onBackToCad: () -> Unit,
    onOpenPdfFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current

    val windowResults = remember(
        order.windows,
        order.mosquitoPricingMode,
        profileSystems,
        glazingCatalog,
        colorCatalog,
        sillsCatalog,
        extraAccessoriesCatalog,
        workServicesCatalog,
        hardwareBrandsCatalog,
        mosquitoSystemsCatalog
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
                windowIndexInOrder = idx + 1,
                mosquitoSystemsCatalog = mosquitoSystemsCatalog,
                mosquitoPricingMode = order.mosquitoPricingMode
            )
        }
    }

    var items by remember(windowResults, mosquitoSystemsCatalog) {
        mutableStateOf(OrderProcurementHelper.buildMosquitoOrderItems(windowResults, mosquitoSystemsCatalog))
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val allSelected = items.isNotEmpty() && items.all { it.isSelected }
    val selectedItems = items.filter { it.isSelected }
    val selectedCount = selectedItems.sumOf { it.quantity }
    val selectedBillableArea = selectedItems.sumOf { it.billableAreaM2 * it.quantity }

    fun selectOnlyByPredicate(predicate: (MosquitoOrderItem) -> Boolean) {
        items = items.map { it.copy(isSelected = predicate(it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = "Заказ москитных сеток",
            onBack = onBackToCad
        ) {
            Surface(
                color = if (allSelected) palette.accentTeal else palette.secondaryBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.7.dp, palette.floatingBorder),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = items.isNotEmpty()) {
                        val nextState = !allSelected
                        items = items.map { it.copy(isSelected = nextState) }
                    }
                    .testTag("mosquito_select_all_btn")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (allSelected) "Снять все" else "Выбрать все",
                        color = if (allSelected) Color.White else palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "В заказе нет отмеченных москитных сеток (О / Д / П)",
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            // Быстрый фильтр выбора по типу сетки (О / Д / П)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MosquitoNetType.entries.forEach { nt ->
                    val matchingCount = items.count { it.netType == nt }
                    if (matchingCount > 0) {
                        val isExactlyThisSelected = selectedItems.isNotEmpty() &&
                            selectedItems.size == matchingCount &&
                            selectedItems.all { it.netType == nt }
                        FilterChip(
                            selected = isExactlyThisSelected,
                            onClick = { selectOnlyByPredicate { it.netType == nt } },
                            label = {
                                Text(
                                    text = "${nt.shortBadge} — ${nt.titleRu} ($matchingCount)",
                                    fontSize = 12.sp,
                                    color = if (isExactlyThisSelected) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentTeal,
                                containerColor = palette.cardSurface
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("mosquito_quick_${nt.name}")
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        color = palette.accentTeal.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.7.dp, palette.floatingBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Позиций: ${selectedItems.size} из ${items.size} ($selectedCount шт.)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Расч. S = ${String.format(Locale.US, "%.2f", selectedBillableArea)} м²",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentTeal
                            )
                        }
                    }
                }

                itemsIndexed(items, key = { _, it -> it.id }) { idx, item ->
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (item.isSelected) 1.dp else 0.7.dp,
                            color = if (item.isSelected) palette.accentTeal.copy(alpha = 0.55f) else palette.floatingBorder
                        ),
                        shadowElevation = if (palette.isDark) 0.dp else 1.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = palette.accentTeal.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "№${item.rowIndex} • ${item.netType.shortBadge}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.accentTeal,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "Констр. №${item.windowIndex} (${item.cellLabel})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${item.netType.titleRu}: ${item.systemName}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accentBlue
                                )
                                Text(
                                    text = "Размер: ${item.sizeText} • Цвет: ${item.colorName} • ${item.quantity} шт. • расч. ${String.format(Locale.US, "%.2f", item.billableAreaM2 * item.quantity)} м²",
                                    fontSize = 11.5.sp,
                                    color = palette.textSecondary
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        text = "Прим.: ${item.note}",
                                        fontSize = 11.sp,
                                        color = palette.accentTeal
                                    )
                                }
                            }

                            IconButton(
                                onClick = { editingIndex = idx },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mosquito_edit_${idx}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Редактировать москитную сетку",
                                    tint = palette.accentBlue
                                )
                            }

                            IconButton(
                                onClick = {
                                    items = items.mapIndexed { i, cur ->
                                        if (i == idx) cur.copy(isSelected = !cur.isSelected) else cur
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mosquito_check_${idx}")
                            ) {
                                Icon(
                                    imageVector = if (item.isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    contentDescription = if (item.isSelected) "Исключить из заказа" else "Включить в заказ",
                                    tint = if (item.isSelected) palette.accentGreen else palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = palette.cardSurface,
            shadowElevation = 8.dp,
            border = BorderStroke(0.6.dp, palette.floatingBorder)
        ) {
            Button(
                enabled = selectedItems.isNotEmpty(),
                onClick = {
                    val file = PdfReportGenerator.generateMosquitoOrderPdf(
                        context = context,
                        order = order,
                        windowResults = windowResults,
                        selectedItems = selectedItems,
                        mosquitoSystemsCatalog = mosquitoSystemsCatalog
                    )
                    onOpenPdfFile(file, "Заказ москитных сеток")
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .heightIn(min = 50.dp)
                    .testTag("mosquito_generate_pdf_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Сформировать заказ ($selectedCount шт. • ${String.format(Locale.US, "%.2f", selectedBillableArea)} м²)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    val activeEditIdx = editingIndex
    if (activeEditIdx != null && activeEditIdx in items.indices) {
        EditMosquitoItemDialog(
            item = items[activeEditIdx],
            onDismiss = { editingIndex = null },
            onConfirm = { updated ->
                items = items.mapIndexed { i, cur -> if (i == activeEditIdx) updated else cur }
                editingIndex = null
            }
        )
    }
}

@Composable
private fun EditMosquitoItemDialog(
    item: MosquitoOrderItem,
    onDismiss: () -> Unit,
    onConfirm: (MosquitoOrderItem) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var selectedType by remember(item) { mutableStateOf(item.netType) }
    var modelInput by remember(item) { mutableStateOf(item.systemName) }
    var colorInput by remember(item) { mutableStateOf(item.colorName) }
    var widthInput by remember(item) { mutableStateOf(item.widthMm.toString()) }
    var heightInput by remember(item) { mutableStateOf(item.heightMm.toString()) }
    var qtyInput by remember(item) { mutableStateOf(item.quantity.toString()) }
    var noteInput by remember(item) { mutableStateOf(item.note) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = 14.dp,
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
                    text = "Позиция №${item.rowIndex} (Констр. №${item.windowIndex} ${item.cellLabel})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary
                )

                Text(
                    text = "Вид москитной сетки:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MosquitoNetType.entries.forEach { nt ->
                        val sel = selectedType == nt
                        FilterChip(
                            selected = sel,
                            onClick = { selectedType = nt },
                            label = {
                                Text(
                                    text = "${nt.shortBadge} — ${nt.titleRu}",
                                    fontSize = 11.5.sp,
                                    color = if (sel) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentTeal,
                                containerColor = palette.background
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Модель сетки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthInput,
                        onValueChange = { widthInput = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Ширина (мм)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Высота (мм)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Кол-во") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(86.dp)
                    )
                }

                OutlinedTextField(
                    value = colorInput,
                    onValueChange = { colorInput = it },
                    label = { Text("Цвет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Примечание") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mosquito_edit_note_input")
                )

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
                        onClick = {
                            val newW = (widthInput.toIntOrNull() ?: item.widthMm).coerceIn(100, 5000)
                            val newH = (heightInput.toIntOrNull() ?: item.heightMm).coerceIn(100, 5000)
                            val rawArea = (newW.toDouble() * newH.toDouble()) / 1_000_000.0
                            onConfirm(
                                item.copy(
                                    netType = selectedType,
                                    systemName = modelInput.trim().ifBlank { item.systemName },
                                    widthMm = newW,
                                    heightMm = newH,
                                    colorName = colorInput.trim().ifBlank { item.colorName },
                                    quantity = (qtyInput.toIntOrNull() ?: item.quantity).coerceIn(1, 500),
                                    singleAreaM2 = rawArea,
                                    billableAreaM2 = max(1.0, rawArea),
                                    note = noteInput.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("mosquito_edit_confirm_btn")
                    ) {
                        Text("Применить", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
