package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.domain.calculator.GlazingOrderItem
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.io.File
import java.util.Locale

// =========================================================================================
// РЕДАКТОР «ЗАКАЗ СТЕКЛОПАКЕТОВ» С ВЫБОРОМ И РЕДАКТИРОВАНИЕМ ТИПА ПЕРЕД ГЕНЕРАЦИЕЙ PDF
// =========================================================================================
@Composable
internal fun GlazingOrderScreen(
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

    var items by remember(windowResults) {
        mutableStateOf(OrderProcurementHelper.buildGlazingOrderItems(windowResults))
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val allSelected = items.isNotEmpty() && items.all { it.isSelected }
    val selectedItems = items.filter { it.isSelected }
    val selectedQty = selectedItems.sumOf { it.quantity }
    val selectedArea = selectedItems.sumOf { it.totalAreaM2 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = "Заказ стеклопакетов",
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
                    .testTag("glazing_select_all_btn")
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
                    text = "В заказе нет позиций заполнения",
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        color = palette.accentBlue.copy(alpha = 0.09f),
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
                                text = "Позиций: ${selectedItems.size} из ${items.size} (${selectedQty} шт.)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Площадь: ${String.format(Locale.US, "%.2f", selectedArea)} м²",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentBlue
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
                            color = if (item.isSelected) palette.accentBlue.copy(alpha = 0.45f) else palette.floatingBorder
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
                                        color = palette.accentBlue.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "№${item.rowIndex}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.accentBlue,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "Окно №${item.windowIndex} • ${item.openingNumber}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = item.formula,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.isSandwich) palette.accentOrange else palette.accentTeal
                                )
                                Text(
                                    text = "${item.widthMm} × ${item.heightMm} мм (${item.quantity} шт.) • S = ${String.format(Locale.US, "%.2f", item.totalAreaM2)} м²",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary
                                )
                            }

                            IconButton(
                                onClick = { editingIndex = idx },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("glazing_edit_${idx}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Изменить тип стеклопакета",
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
                                    .testTag("glazing_check_${idx}")
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
                    val file = PdfReportGenerator.generateGlazingSpecPdf(
                        context = context,
                        order = order,
                        windowResults = windowResults,
                        selectedItems = selectedItems
                    )
                    onOpenPdfFile(file, "Заказ стеклопакетов")
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .heightIn(min = 50.dp)
                    .testTag("glazing_generate_pdf_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Сформировать заказ (${selectedItems.size} поз.)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    val activeEditIdx = editingIndex
    if (activeEditIdx != null && activeEditIdx in items.indices) {
        val current = items[activeEditIdx]
        EditGlazingItemDialog(
            item = current,
            glazingCatalog = glazingCatalog,
            onDismiss = { editingIndex = null },
            onConfirm = { newFormula, isSandwich ->
                items = items.mapIndexed { i, cur ->
                    if (i == activeEditIdx) {
                        cur.copy(formula = newFormula, isSandwich = isSandwich)
                    } else cur
                }
                editingIndex = null
            }
        )
    }
}

@Composable
private fun EditGlazingItemDialog(
    item: GlazingOrderItem,
    glazingCatalog: List<GlazingCatalogItem>,
    onDismiss: () -> Unit,
    onConfirm: (formula: String, isSandwich: Boolean) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var formulaInput by remember(item) { mutableStateOf(item.formula) }
    var isSandwich by remember(item) { mutableStateOf(item.isSandwich) }

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
                    text = "Тип заполнения (Окно №${item.windowIndex} • ${item.openingNumber})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "Размер пакета: ${item.widthMm} × ${item.heightMm} мм",
                    fontSize = 12.5.sp,
                    color = palette.textSecondary
                )

                OutlinedTextField(
                    value = formulaInput,
                    onValueChange = { formulaInput = it },
                    label = { Text("Формула / тип стеклопакета") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("glazing_edit_formula_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { isSandwich = !isSandwich }
                        .padding(horizontal = 4.dp)
                ) {
                    Checkbox(
                        checked = isSandwich,
                        onCheckedChange = { isSandwich = it }
                    )
                    Text(
                        text = "Сэндвич-панель",
                        fontSize = 13.sp,
                        color = palette.textPrimary
                    )
                }

                val catalogChoices = glazingCatalog.filter { it.id != "none" }
                if (catalogChoices.isNotEmpty()) {
                    Text(
                        text = "Выбрать из справочника:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = palette.textSecondary
                    )
                    catalogChoices.forEach { cat ->
                        val selected = formulaInput.trim() == cat.formula
                        Surface(
                            color = if (selected) palette.accentBlue.copy(alpha = 0.14f) else palette.background,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                0.8.dp,
                                if (selected) palette.accentBlue else palette.floatingBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    formulaInput = cat.formula
                                    isSandwich = cat.isSandwich
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = cat.formula,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
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
                        enabled = formulaInput.isNotBlank(),
                        onClick = { onConfirm(formulaInput.trim(), isSandwich) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("glazing_edit_confirm_btn")
                    ) {
                        Text("Применить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
