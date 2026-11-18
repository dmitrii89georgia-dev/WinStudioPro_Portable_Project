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
import ru.winstudiopro.app.domain.calculator.HardwareOrderItem
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.io.File

// =========================================================================================
// РЕДАКТОР «ЗАКАЗ ФУРНИТУРЫ» С БЫСТРЫМ ВЫБОРОМ ПО ТИПУ ОТКРЫВАНИЯ И РЕДАКТИРОВАНИЕМ
// =========================================================================================
@Composable
internal fun HardwareOrderScreen(
    order: CustomerOrder,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    sillsCatalog: List<SillCatalogItem>,
    extraAccessoriesCatalog: List<ExtraAccessoryItem>,
    workServicesCatalog: List<WorkServiceItem>,
    hardwareBrandsCatalog: List<HardwareBrandItem>,
    onBackToCad: () -> Unit,
    onOpenPdfFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current

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

    var items by remember(windowResults, hardwareBrandsCatalog) {
        mutableStateOf(OrderProcurementHelper.buildHardwareOrderItems(windowResults, hardwareBrandsCatalog))
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val allSelected = items.isNotEmpty() && items.all { it.isSelected }
    val selectedItems = items.filter { it.isSelected }
    val selectedSetsCount = selectedItems.sumOf { it.quantity }

    fun selectOnlyByPredicate(predicate: (HardwareOrderItem) -> Boolean) {
        items = items.map { it.copy(isSelected = predicate(it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = "Заказ фурнитуры",
            onBack = onBackToCad
        ) {
            Surface(
                color = if (allSelected) palette.accentOrange else palette.secondaryBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.7.dp, palette.floatingBorder),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = items.isNotEmpty()) {
                        val nextState = !allSelected
                        items = items.map { it.copy(isSelected = nextState) }
                    }
                    .testTag("hardware_select_all_btn")
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
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "В заказе нет открывающихся створок/дверей",
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            // Быстрый фильтр выбора по группе фурнитуры (для раздельных поставщиков)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickGroups: List<Triple<String, String, (HardwareOrderItem) -> Boolean>> = listOf(
                    Triple("hardware_quick_tilt_turn", "Пов.-отк.") {
                        !it.isDoor && (
                            it.openingType == SashOpeningType.TILT_TURN_LEFT ||
                                it.openingType == SashOpeningType.TILT_TURN_RIGHT ||
                                it.openingType == SashOpeningType.SHTULP_TILTTURN_L_TURN_R ||
                                it.openingType == SashOpeningType.SHTULP_TURN_L_TILTTURN_R
                            )
                    },
                    Triple("hardware_quick_turn", "Поворотные") {
                        !it.isDoor && (
                            it.openingType == SashOpeningType.TURN_LEFT ||
                                it.openingType == SashOpeningType.TURN_RIGHT ||
                                it.openingType == SashOpeningType.SHTULP_TURN_TURN
                            )
                    },
                    Triple("hardware_quick_transom", "Откидные") {
                        !it.isDoor && (
                            it.openingType == SashOpeningType.TRANSOM_BOTTOM ||
                                it.openingType == SashOpeningType.TRANSOM_TOP
                            )
                    },
                    Triple("hardware_quick_doors", "Двери") { it.isDoor }
                )

                quickGroups.forEach { (tag, label, predicate) ->
                    val matchingCount = items.count(predicate)
                    if (matchingCount > 0) {
                        val isExactlyThisGroupSelected = selectedItems.isNotEmpty() &&
                            selectedItems.size == matchingCount &&
                            selectedItems.all(predicate)
                        FilterChip(
                            selected = isExactlyThisGroupSelected,
                            onClick = { selectOnlyByPredicate(predicate) },
                            label = {
                                Text(
                                    text = "$label ($matchingCount)",
                                    fontSize = 12.sp,
                                    color = if (isExactlyThisGroupSelected) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentOrange,
                                containerColor = palette.cardSurface
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(tag)
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
                itemsIndexed(items, key = { _, it -> it.id }) { idx, item ->
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (item.isSelected) 1.dp else 0.7.dp,
                            color = if (item.isSelected) palette.accentOrange.copy(alpha = 0.5f) else palette.floatingBorder
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
                                        color = palette.accentOrange.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "№${item.rowIndex}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.accentOrange,
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
                                    text = buildString {
                                        if (item.isDoor) append("[Дверь] ")
                                        append(item.openingType.titleRu)
                                    },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accentBlue
                                )
                                Text(
                                    text = "Размер: ${item.sizeText} • Сторона: ${item.sideLabel} • ${item.quantity} компл.",
                                    fontSize = 12.sp,
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
                                    .testTag("hardware_edit_${idx}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Редактировать позицию фурнитуры",
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
                                    .testTag("hardware_check_${idx}")
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
                    val file = PdfReportGenerator.generateHardwareSpecPdf(
                        context = context,
                        order = order,
                        windowResults = windowResults,
                        selectedItems = selectedItems,
                        hardwareBrandsCatalog = hardwareBrandsCatalog
                    )
                    onOpenPdfFile(file, "Заказ фурнитуры")
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .heightIn(min = 50.dp)
                    .testTag("hardware_generate_pdf_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Сформировать заказ ($selectedSetsCount компл.)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    val activeEditIdx = editingIndex
    if (activeEditIdx != null && activeEditIdx in items.indices) {
        EditHardwareItemDialog(
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
private fun EditHardwareItemDialog(
    item: HardwareOrderItem,
    onDismiss: () -> Unit,
    onConfirm: (HardwareOrderItem) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var selectedType by remember(item) { mutableStateOf(item.openingType) }
    var isDoor by remember(item) { mutableStateOf(item.isDoor) }
    var sideInput by remember(item) { mutableStateOf(item.sideLabel) }
    var widthInput by remember(item) { mutableStateOf(item.falzWidthMm.toString()) }
    var heightInput by remember(item) { mutableStateOf(item.falzHeightMm.toString()) }
    var qtyInput by remember(item) { mutableStateOf(item.quantity.toString()) }
    var brandInput by remember(item) { mutableStateOf(item.brandName) }
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
                    text = "Тип открывания:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val editableTypes = listOf(
                        SashOpeningType.TILT_TURN_LEFT,
                        SashOpeningType.TILT_TURN_RIGHT,
                        SashOpeningType.TURN_LEFT,
                        SashOpeningType.TURN_RIGHT,
                        SashOpeningType.TRANSOM_BOTTOM,
                        SashOpeningType.SLIDING_LEFT,
                        SashOpeningType.SLIDING_RIGHT
                    )
                    editableTypes.forEach { t ->
                        val sel = selectedType == t
                        FilterChip(
                            selected = sel,
                            onClick = {
                                selectedType = t
                                sideInput = OrderProcurementHelper.resolveSideLabel(t)
                            },
                            label = {
                                Text(
                                    t.titleRu,
                                    fontSize = 11.5.sp,
                                    color = if (sel) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentOrange,
                                containerColor = palette.background
                            )
                        )
                    }
                }

                Text(
                    text = "Сторона:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Левая", "Правая", "Откидная").forEach { side ->
                        val sel = sideInput == side
                        FilterChip(
                            selected = sel,
                            onClick = { sideInput = side },
                            label = {
                                Text(
                                    side,
                                    fontSize = 12.sp,
                                    color = if (sel) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentBlue,
                                containerColor = palette.background
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { isDoor = !isDoor }
                ) {
                    Checkbox(checked = isDoor, onCheckedChange = { isDoor = it })
                    Text("Дверная фурнитура", fontSize = 13.sp, color = palette.textPrimary)
                }

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
                    value = brandInput,
                    onValueChange = { brandInput = it },
                    label = { Text("Марка фурнитуры") },
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
                        .testTag("hardware_edit_note_input")
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
                            onConfirm(
                                item.copy(
                                    openingType = selectedType,
                                    isDoor = isDoor,
                                    sideLabel = sideInput,
                                    falzWidthMm = (widthInput.toIntOrNull() ?: item.falzWidthMm).coerceIn(200, 4000),
                                    falzHeightMm = (heightInput.toIntOrNull() ?: item.falzHeightMm).coerceIn(200, 4000),
                                    quantity = (qtyInput.toIntOrNull() ?: item.quantity).coerceIn(1, 500),
                                    brandName = brandInput.trim(),
                                    note = noteInput.trim()
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("hardware_edit_confirm_btn")
                    ) {
                        Text("Применить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
