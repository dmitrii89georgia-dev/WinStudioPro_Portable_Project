package ru.winstudiopro.app.ui.cad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import kotlin.math.min

private val OriginalBlue = Color(0xFF007AFF)
private val OriginalDarkBlue = Color(0xFF0062CC)

// =========================================================================
// 1. ФИРМЕННЫЙ СИНЕ-БЕЛЫЙ ЦИФРОВОЙ КАЛЬКУЛЯТОР РАЗМЕРОВ (как на 00:17 видео)
// =========================================================================
@Composable
fun OriginalNumpadModalDialog(
    title: String,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var displayStr by remember(initialValue) { mutableStateOf(initialValue.toString()) }
    var isFirstInput by remember(initialValue) { mutableStateOf(true) }

    fun appendDigit(digit: String) {
        if (isFirstInput) {
            displayStr = digit
            isFirstInput = false
        } else if (displayStr.length < 5) {
            displayStr = if (displayStr == "0") digit else displayStr + digit
        }
    }

    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(310.dp)
                .border(1.dp, p.floatingBorder, RoundedCornerShape(24.dp)),
            color = p.cardSurface,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.background)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        color = p.textSecondary
                    )
                    Text(
                        text = displayStr.ifEmpty { "0" },
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val rows = listOf(
                    listOf("7", "8", "9"),
                    listOf("4", "5", "6"),
                    listOf("1", "2", "3"),
                    listOf("0", ".", "C")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            val isClear = key == "C" || key == "."
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isClear) p.segmentedBg else p.elevatedSurface)
                                    .border(0.7.dp, p.floatingBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        when (key) {
                                            "C", "." -> {
                                                displayStr = "0"
                                                isFirstInput = true
                                            }
                                            else -> appendDigit(key)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    color = if (isClear) p.accentRed else p.textPrimary,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Нижний ряд: Отмена | Стереть | Подтвердить
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.segmentedBg)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = s.cancelBtn,
                            tint = p.textSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.segmentedBg)
                            .clickable {
                                if (displayStr.length > 1) {
                                    displayStr = displayStr.dropLast(1)
                                    isFirstInput = false
                                } else {
                                    displayStr = "0"
                                    isFirstInput = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⌫",
                            color = p.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.accentBlue)
                            .clickable {
                                val parsed = displayStr.toIntOrNull() ?: initialValue
                                if (parsed > 50) {
                                    onConfirm(parsed)
                                } else {
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = s.okBtn,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. МОДАЛЬНОЕ ОКНО "ИМПОСТЫ | ОТКРЫВАНИЕ"
// Поддерживает:
// - разбивку проёма (верт/гор/2 верт/2 гор/штульп)
// - установку ВНУТРЕННЕГО горизонтального импоста в створку/дверь (когда выбрана створка + горизонтальное деление — как на 01:44 видео!)
// - флаг "двери" (широкий дверной профиль + сэндвич внизу)
// =========================================================================
enum class SplitOperation {
    NONE,
    SPLIT_V2,
    SPLIT_H2,
    SPLIT_V3,
    SPLIT_H3,
    SPLIT_SHTULP_LEFT_MAIN,
    SPLIT_SHTULP_RIGHT_MAIN
}

@Composable
fun ImpostsAndOpeningModalDialog(
    cell: OpeningCellNode,
    onDismiss: () -> Unit,
    onApply: (
        splitOp: SplitOperation,
        newOpeningType: SashOpeningType,
        isSandwich: Boolean,
        hasMosquito: Boolean,
        isDoor: Boolean,
        isReverseDoor: Boolean,
        hasHorizontalSashImpost: Boolean
    ) -> Unit
) {
    val props = cell.properties
    var selectedSplit by remember { mutableStateOf(SplitOperation.NONE) }
    var selectedOpening by remember { mutableStateOf(props.openingType) }
    var isSandwich by remember { mutableStateOf(props.isSandwichFilling) }
    var hasMosquito by remember { mutableStateOf(props.hasMosquitoNet) }
    var isDoor by remember { mutableStateOf(props.isDoorSash) }
    var isReverseDoor by remember { mutableStateOf(props.isReverseDoorSash) }
    var hasSashImpost by remember { mutableStateOf(props.hasHorizontalSashImpost) }

    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .border(1.dp, p.floatingBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = p.cardSurface,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.impostsHeader,
                        color = p.accentBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.36f)
                    )
                    Text(
                        text = s.openingHeader,
                        color = p.accentBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.64f)
                    )
                }
                HorizontalDivider(color = p.divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ЛЕВАЯ КОЛОНКА: ИМПОСТЫ (2x3)
                    Column(
                        modifier = Modifier.weight(0.36f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconCellButton(
                                selected = selectedSplit == SplitOperation.SPLIT_V2,
                                onClick = {
                                    selectedSplit = if (selectedSplit == SplitOperation.SPLIT_V2) SplitOperation.NONE else SplitOperation.SPLIT_V2
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                DrawImpostIcon(type = SplitOperation.SPLIT_V2)
                            }
                            IconCellButton(
                                selected = selectedSplit == SplitOperation.SPLIT_H2 || (selectedOpening.hasSashProfile && hasSashImpost),
                                onClick = {
                                    if (selectedOpening.hasSashProfile) {
                                        // Как в видео 01:44: если выбрана створка или дверь, горизонтальный импост ставится ВНУТРЬ створки/двери!
                                        hasSashImpost = !hasSashImpost
                                        selectedSplit = SplitOperation.NONE
                                    } else {
                                        selectedSplit = if (selectedSplit == SplitOperation.SPLIT_H2) SplitOperation.NONE else SplitOperation.SPLIT_H2
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                DrawImpostIcon(type = SplitOperation.SPLIT_H2)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconCellButton(
                                selected = selectedSplit == SplitOperation.SPLIT_V3,
                                onClick = {
                                    selectedSplit = if (selectedSplit == SplitOperation.SPLIT_V3) SplitOperation.NONE else SplitOperation.SPLIT_V3
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                DrawImpostIcon(type = SplitOperation.SPLIT_V3)
                            }
                            IconCellButton(
                                selected = selectedSplit == SplitOperation.SPLIT_H3,
                                onClick = {
                                    selectedSplit = if (selectedSplit == SplitOperation.SPLIT_H3) SplitOperation.NONE else SplitOperation.SPLIT_H3
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                DrawImpostIcon(type = SplitOperation.SPLIT_H3)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconCellButton(
                                selected = selectedOpening == SashOpeningType.SHTULP_TILTTURN_L_TURN_R,
                                onClick = {
                                    selectedOpening = SashOpeningType.SHTULP_TILTTURN_L_TURN_R
                                    selectedSplit = SplitOperation.NONE
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                DrawImpostIcon(type = SplitOperation.SPLIT_SHTULP_LEFT_MAIN)
                            }
                            IconCellButton(
                                selected = selectedOpening == SashOpeningType.SHTULP_TURN_L_TILTTURN_R,
                                onClick = {
                                    selectedOpening = SashOpeningType.SHTULP_TURN_L_TILTTURN_R
                                    selectedSplit = SplitOperation.NONE
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                DrawImpostIcon(type = SplitOperation.SPLIT_SHTULP_RIGHT_MAIN)
                            }
                        }
                    }

                    // ПРАВАЯ КОЛОНКА: ОТКРЫВАНИЕ (3x3 как в оригинале)
                    val sashGrid = listOf(
                        listOf(SashOpeningType.TURN_LEFT, SashOpeningType.TURN_RIGHT, SashOpeningType.TRANSOM_BOTTOM),
                        listOf(SashOpeningType.TILT_TURN_LEFT, SashOpeningType.TILT_TURN_RIGHT, SashOpeningType.TRANSOM_TOP),
                        listOf(SashOpeningType.SLIDING_LEFT, SashOpeningType.SLIDING_RIGHT, SashOpeningType.FIXED_GLASS)
                    )
                    Column(
                        modifier = Modifier.weight(0.64f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sashGrid.forEach { rowTypes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowTypes.forEach { sType ->
                                    val isSelected = selectedOpening == sType &&
                                        (selectedSplit == SplitOperation.NONE || sType.hasSashProfile)
                                    IconCellButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedOpening = sType
                                            if (!sType.hasSashProfile) {
                                                hasSashImpost = false
                                                isDoor = false
                                                isReverseDoor = false
                                                hasMosquito = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        DrawOpeningIcon(sType)
                                    }
                                }
                            }
                        }
                    }
                }

                // Подсказка, если включён внутренний импост створки/двери
                if (selectedOpening.hasSashProfile && hasSashImpost) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = OriginalBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Горизонтальный импост внутри створки/двери (авто-привязка к соседнему импосту)",
                                fontSize = 11.sp,
                                color = OriginalDarkBlue,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { hasSashImpost = false },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Убрать", fontSize = 11.sp, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Чекбоксы: "непрозрачное заполнение", "москитная сетка", "двери", "обратная дверь"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isSandwich = !isSandwich },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSandwich,
                            onCheckedChange = { isSandwich = it },
                            colors = CheckboxDefaults.colors(checkedColor = p.accentBlue)
                        )
                        Text(s.sandwichCheckbox, fontSize = 12.5.sp, color = p.textPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = selectedOpening.hasSashProfile) {
                                hasMosquito = !hasMosquito
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasMosquito && selectedOpening.hasSashProfile,
                            onCheckedChange = { hasMosquito = it },
                            enabled = selectedOpening.hasSashProfile,
                            colors = CheckboxDefaults.colors(checkedColor = p.accentBlue)
                        )
                        Text(
                            s.mosquitoCheckbox,
                            fontSize = 12.5.sp,
                            color = if (selectedOpening.hasSashProfile) p.textPrimary else p.textSecondary.copy(alpha = 0.45f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = selectedOpening.hasSashProfile) {
                                isDoor = !isDoor
                                if (isDoor) isReverseDoor = false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isDoor && selectedOpening.hasSashProfile,
                            onCheckedChange = {
                                isDoor = it
                                if (it) isReverseDoor = false
                            },
                            enabled = selectedOpening.hasSashProfile,
                            colors = CheckboxDefaults.colors(checkedColor = p.accentBlue)
                        )
                        Text(
                            s.isDoorCheckbox,
                            fontSize = 12.5.sp,
                            color = if (selectedOpening.hasSashProfile) p.textPrimary else p.textSecondary.copy(alpha = 0.45f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = selectedOpening.hasSashProfile) {
                                isReverseDoor = !isReverseDoor
                                if (isReverseDoor) isDoor = false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isReverseDoor && selectedOpening.hasSashProfile,
                            onCheckedChange = {
                                isReverseDoor = it
                                if (it) isDoor = false
                            },
                            enabled = selectedOpening.hasSashProfile,
                            colors = CheckboxDefaults.colors(checkedColor = p.accentBlue)
                        )
                        Text(
                            s.reverseDoorCheckbox,
                            fontSize = 12.5.sp,
                            color = if (selectedOpening.hasSashProfile) p.textPrimary else p.textSecondary.copy(alpha = 0.45f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Кнопки Отмена | Применить в стиле iOS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.segmentedBg)
                    ) {
                        Text(s.cancelBtn, color = p.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            onApply(
                                selectedSplit,
                                selectedOpening,
                                isSandwich,
                                hasMosquito && selectedOpening.hasSashProfile,
                                isDoor && selectedOpening.hasSashProfile,
                                isReverseDoor && selectedOpening.hasSashProfile,
                                hasSashImpost && selectedOpening.hasSashProfile
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accentBlue)
                    ) {
                        Text(s.applyBtn, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. МОДАЛЬНОЕ ОКНО ВЫРАВНИВАНИЯ ИМПОСТОВ ("ПО ЧЕРТЕЖУ", "ПО ПРОЁМАМ", "ПО СВЕТУ")
// =========================================================================
@Composable
fun ImpostAlignmentModeDialog(
    currentMode: ImpostAlignmentMode,
    onDismiss: () -> Unit,
    onSelectMode: (ImpostAlignmentMode, ImpostDirection?) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    var targetFilter by remember { mutableStateOf<ImpostDirection?>(null) }

    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .border(1.dp, p.floatingBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = p.cardSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = s.alignmentHeader,
                    color = p.accentBlue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider(
                    color = p.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = s.applyToLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = targetFilter == ImpostDirection.VERTICAL,
                        onClick = { targetFilter = ImpostDirection.VERTICAL },
                        label = { Text(s.verticalBtn, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = targetFilter == null,
                        onClick = { targetFilter = null },
                        label = { Text(s.allBtn, fontSize = 11.sp) },
                        modifier = Modifier.weight(0.75f)
                    )
                    FilterChip(
                        selected = targetFilter == ImpostDirection.HORIZONTAL,
                        onClick = { targetFilter = ImpostDirection.HORIZONTAL },
                        label = { Text(s.horizontalBtn, fontSize = 11.sp) },
                        modifier = Modifier.weight(1.1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = s.alignHint,
                    fontSize = 11.5.sp,
                    color = p.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                ImpostAlignmentMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                color = if (isSelected) p.accentBlue else p.divider,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedMode = mode
                                onSelectMode(mode, targetFilter)
                            },
                        color = if (isSelected) p.accentBlue.copy(alpha = 0.12f) else p.background
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedMode = mode
                                    onSelectMode(mode, targetFilter)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = p.accentBlue)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.titleRu,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = p.textPrimary
                                )
                                Text(
                                    text = mode.descriptionRu,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = p.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.segmentedBg)
                    ) {
                        Text(s.cancelBtn, color = p.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onSelectMode(selectedMode, targetFilter) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accentBlue)
                    ) {
                        Text(s.alignBtn, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun OriginalDecimalNumpadModalDialog(
    title: String,
    initialValue: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit
) {
    val initFormatted = if (initialValue == null || initialValue <= 0.0) {
        "0"
    } else if (initialValue % 1.0 == 0.0) {
        initialValue.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", initialValue)
    }
    var displayStr by remember(initialValue) { mutableStateOf(initFormatted) }
    var isFirstInput by remember(initialValue) { mutableStateOf(true) }

    fun appendChar(ch: String) {
        if (ch == ".") {
            if (isFirstInput) {
                displayStr = "0."
                isFirstInput = false
            } else if (!displayStr.contains(".")) {
                displayStr += "."
            }
            return
        }
        if (isFirstInput) {
            displayStr = ch
            isFirstInput = false
        } else if (displayStr.length < 9) {
            displayStr = if (displayStr == "0") ch else displayStr + ch
        }
    }

    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(310.dp)
                .border(1.dp, p.floatingBorder, RoundedCornerShape(24.dp)),
            color = p.cardSurface,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.background)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "$title ${s.customPriceHintSuffix}",
                        fontSize = 11.sp,
                        color = p.textSecondary
                    )
                    Text(
                        text = displayStr.ifEmpty { "0" },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val rows = listOf(
                    listOf("7", "8", "9"),
                    listOf("4", "5", "6"),
                    listOf("1", "2", "3"),
                    listOf("0", ".", "C")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            val isClear = key == "C"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isClear) p.segmentedBg else p.elevatedSurface)
                                    .border(0.7.dp, p.floatingBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        when (key) {
                                            "C" -> {
                                                displayStr = "0"
                                                isFirstInput = true
                                            }
                                            else -> appendChar(key)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    color = if (isClear) p.accentRed else p.textPrimary,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.segmentedBg)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = s.cancelBtn,
                            tint = p.textSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.segmentedBg)
                            .clickable {
                                if (displayStr.length > 1) {
                                    displayStr = displayStr.dropLast(1)
                                    isFirstInput = false
                                } else {
                                    displayStr = "0"
                                    isFirstInput = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⌫",
                            color = p.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.accentBlue)
                            .clickable {
                                val parsed = displayStr.replace(',', '.').toDoubleOrNull() ?: 0.0
                                onConfirm(if (parsed <= 0.0) null else parsed)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = s.okBtn,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCellButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalWinStudioPalette.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) p.accentBlue.copy(alpha = 0.2f) else p.background)
            .border(
                width = if (selected) 2.dp else 0.8.dp,
                color = if (selected) p.accentBlue else p.divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun DrawImpostIcon(type: SplitOperation) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.12f
        val rectW = w - pad * 2f
        val rectH = h - pad * 2f

        drawRect(
            color = Color(0xFFBBDEFB),
            topLeft = Offset(pad, pad),
            size = Size(rectW, rectH)
        )
        drawRect(
            color = Color(0xFF424242),
            topLeft = Offset(pad, pad),
            size = Size(rectW, rectH),
            style = Stroke(width = 3f)
        )

        when (type) {
            SplitOperation.SPLIT_V2 -> {
                drawLine(Color(0xFF424242), Offset(w / 2f, pad), Offset(w / 2f, h - pad), 3f)
            }
            SplitOperation.SPLIT_H2 -> {
                drawLine(Color(0xFF424242), Offset(pad, h / 2f), Offset(w - pad, h / 2f), 3f)
            }
            SplitOperation.SPLIT_V3 -> {
                val x1 = pad + rectW / 3f
                val x2 = pad + rectW * 2f / 3f
                drawLine(Color(0xFF424242), Offset(x1, pad), Offset(x1, h - pad), 3f)
                drawLine(Color(0xFF424242), Offset(x2, pad), Offset(x2, h - pad), 3f)
            }
            SplitOperation.SPLIT_H3 -> {
                val y1 = pad + rectH / 3f
                val y2 = pad + rectH * 2f / 3f
                drawLine(Color(0xFF424242), Offset(pad, y1), Offset(w - pad, y1), 3f)
                drawLine(Color(0xFF424242), Offset(pad, y2), Offset(w - pad, y2), 3f)
            }
            SplitOperation.SPLIT_SHTULP_LEFT_MAIN -> {
                drawLine(
                    color = Color(0xFF424242),
                    start = Offset(w / 2f, pad),
                    end = Offset(w / 2f, h - pad),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
                )
                drawLine(Color(0xFF424242), Offset(w / 2f, pad), Offset(pad, h / 2f), 1.8f)
                drawLine(Color(0xFF424242), Offset(pad, h / 2f), Offset(w / 2f, h - pad), 1.8f)
                drawLine(Color(0xFF424242), Offset(w / 2f, pad), Offset(w - pad, h / 2f), 1.8f)
                drawLine(Color(0xFF424242), Offset(w - pad, h / 2f), Offset(w / 2f, h - pad), 1.8f)
            }
            SplitOperation.SPLIT_SHTULP_RIGHT_MAIN -> {
                drawLine(
                    color = Color(0xFF424242),
                    start = Offset(w / 2f, pad),
                    end = Offset(w / 2f, h - pad),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
                )
                drawLine(Color(0xFF424242), Offset(w / 2f, pad), Offset(pad, h / 2f), 1.8f)
                drawLine(Color(0xFF424242), Offset(pad, h / 2f), Offset(w / 2f, h - pad), 1.8f)
                drawLine(Color(0xFF424242), Offset(pad, h - pad), Offset(w / 4f, pad), 1.8f)
                drawLine(Color(0xFF424242), Offset(w / 4f, pad), Offset(w / 2f, h - pad), 1.8f)
                drawLine(Color(0xFF424242), Offset(w / 2f, pad), Offset(w - pad, h / 2f), 1.8f)
                drawLine(Color(0xFF424242), Offset(w - pad, h / 2f), Offset(w / 2f, h - pad), 1.8f)
            }
            SplitOperation.NONE -> {}
        }
    }
}

@Composable
private fun DrawOpeningIcon(sashType: SashOpeningType) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.12f
        val rectW = w - pad * 2f
        val rectH = h - pad * 2f

        drawRect(
            color = Color(0xFFBBDEFB),
            topLeft = Offset(pad, pad),
            size = Size(rectW, rectH)
        )
        drawRect(
            color = Color(0xFF424242),
            topLeft = Offset(pad, pad),
            size = Size(rectW, rectH),
            style = Stroke(width = 3.5f)
        )

        val l = pad
        val r = w - pad
        val t = pad
        val b = h - pad
        val cx = w / 2f
        val cy = h / 2f
        val stroke = 2f

        when (sashType) {
            SashOpeningType.TURN_LEFT -> {
                drawLine(Color(0xFF212121), Offset(l, t), Offset(r, cy), stroke)
                drawLine(Color(0xFF212121), Offset(r, cy), Offset(l, b), stroke)
                drawHandleSmall(Offset(r - 3f, cy))
            }
            SashOpeningType.TURN_RIGHT -> {
                drawLine(Color(0xFF212121), Offset(r, t), Offset(l, cy), stroke)
                drawLine(Color(0xFF212121), Offset(l, cy), Offset(r, b), stroke)
                drawHandleSmall(Offset(l + 3f, cy))
            }
            SashOpeningType.TRANSOM_BOTTOM -> {
                drawLine(Color(0xFF212121), Offset(l, b), Offset(cx, t), stroke)
                drawLine(Color(0xFF212121), Offset(cx, t), Offset(r, b), stroke)
                drawHandleSmall(Offset(cx, t + 3f), horizontal = true)
            }
            SashOpeningType.TILT_TURN_LEFT -> {
                drawLine(Color(0xFF212121), Offset(l, t), Offset(r, cy), stroke)
                drawLine(Color(0xFF212121), Offset(r, cy), Offset(l, b), stroke)
                drawLine(Color(0xFF212121), Offset(l, b), Offset(cx, t), stroke)
                drawLine(Color(0xFF212121), Offset(cx, t), Offset(r, b), stroke)
                drawHandleSmall(Offset(r - 3f, cy))
            }
            SashOpeningType.TILT_TURN_RIGHT -> {
                drawLine(Color(0xFF212121), Offset(r, t), Offset(l, cy), stroke)
                drawLine(Color(0xFF212121), Offset(l, cy), Offset(r, b), stroke)
                drawLine(Color(0xFF212121), Offset(l, b), Offset(cx, t), stroke)
                drawLine(Color(0xFF212121), Offset(cx, t), Offset(r, b), stroke)
                drawHandleSmall(Offset(l + 3f, cy))
            }
            SashOpeningType.TRANSOM_TOP -> {
                drawLine(Color(0xFF212121), Offset(l, t), Offset(cx, b), stroke)
                drawLine(Color(0xFF212121), Offset(cx, b), Offset(r, t), stroke)
                drawHandleSmall(Offset(cx, b - 3f), horizontal = true)
            }
            SashOpeningType.SLIDING_LEFT -> {
                drawLine(Color(0xFF212121), Offset(r - 8f, cy), Offset(l + 8f, cy), stroke * 1.3f)
                drawLine(Color(0xFF212121), Offset(l + 8f, cy), Offset(l + 15f, cy - 5f), stroke * 1.3f)
                drawLine(Color(0xFF212121), Offset(l + 8f, cy), Offset(l + 15f, cy + 5f), stroke * 1.3f)
            }
            SashOpeningType.SLIDING_RIGHT -> {
                drawLine(Color(0xFF212121), Offset(l + 8f, cy), Offset(r - 8f, cy), stroke * 1.3f)
                drawLine(Color(0xFF212121), Offset(r - 8f, cy), Offset(r - 15f, cy - 5f), stroke * 1.3f)
                drawLine(Color(0xFF212121), Offset(r - 8f, cy), Offset(r - 15f, cy + 5f), stroke * 1.3f)
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawHandleSmall(center: Offset, horizontal: Boolean = false) {
    if (horizontal) {
        drawLine(Color.Black, Offset(center.x - 5f, center.y), Offset(center.x + 5f, center.y), 2.5f)
    } else {
        drawLine(Color.Black, Offset(center.x, center.y - 5f), Offset(center.x, center.y + 5f), 2.5f)
    }
}

// =========================================================================
// 4. МИНИАТЮРА ОКНА (ДЛЯ ЛЕНТЫ КОНСТРУКЦИЙ И СПИСКА ЗАКАЗОВ)
// Отрисовывает также широкий профиль дверей и внутренний импост двери!
// =========================================================================
@Composable
fun WindowMiniThumbnail(
    topology: WindowBlockTopology,
    modifier: Modifier = Modifier,
    frameColorHex: Long = 0xFFE6E9ED
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas

        val pad = 4f
        val totalW = topology.totalWidthMm.coerceAtLeast(100f)
        val totalH = topology.totalHeightMm.coerceAtLeast(100f)
        val scale = min((w - pad * 2f) / totalW, (h - pad * 2f) / totalH)
        val drawW = totalW * scale
        val drawH = totalH * scale
        val ox = (w - drawW) / 2f
        val oy = (h - drawH) / 2f

        val baseColor = Color(frameColorHex)

        topology.frames.forEach { frame ->
            val fx = ox + frame.leftMm * scale
            val fy = oy + frame.topMm * scale
            val fw = frame.widthMm * scale
            val fh = frame.heightMm * scale

            drawRect(
                color = baseColor,
                topLeft = Offset(fx, fy),
                size = Size(fw, fh)
            )
            drawRect(
                color = Color(0xFF424242),
                topLeft = Offset(fx, fy),
                size = Size(fw, fh),
                style = Stroke(width = 2f)
            )

            frame.openings.forEach { op ->
                val p = op.properties
                val isDoorLeaf = p.isDoorSash && p.openingType.hasSashProfile
                val inset = if (isDoorLeaf) {
                    (min(op.widthMm, op.heightMm) * 0.13f).coerceIn(38f, 88f)
                } else if (p.openingType.hasSashProfile) {
                    (min(op.widthMm, op.heightMm) * 0.08f).coerceIn(24f, 55f)
                } else {
                    (min(op.widthMm, op.heightMm) * 0.05f).coerceIn(14f, 34f)
                }

                val gx = ox + (op.leftMm + inset) * scale
                val gy = oy + (op.topMm + inset) * scale
                val gw = (op.widthMm - inset * 2f) * scale
                val gh = (op.heightMm - inset * 2f) * scale

                if (gw > 1f && gh > 1f) {
                    if (p.hasHorizontalSashImpost && p.openingType.hasSashProfile) {
                        val ratioFromBot = (p.sashImpostHeightFromBottomMm / op.heightMm.coerceAtLeast(1f)).coerceIn(0.18f, 0.82f)
                        val splitY = (gy + gh * (1f - ratioFromBot)).coerceIn(gy + 1f, gy + gh - 1f)
                        val topFill = if (p.isSandwichFilling) Color(0xFFECEFF1) else Color(0xFF90CAF9)
                        val botFill = if (p.bottomPartIsSandwich || p.isSandwichFilling) Color(0xFFECEFF1) else Color(0xFF90CAF9)

                        drawRect(color = topFill, topLeft = Offset(gx, gy), size = Size(gw, splitY - gy))
                        drawRect(color = botFill, topLeft = Offset(gx, splitY), size = Size(gw, gy + gh - splitY))
                        drawLine(Color(0xFF37474F), Offset(gx, splitY), Offset(gx + gw, splitY), strokeWidth = 2f)
                    } else {
                        val fillColor = if (p.isSandwichFilling) Color(0xFFECEFF1) else Color(0xFF90CAF9)
                        drawRect(
                            color = fillColor,
                            topLeft = Offset(gx, gy),
                            size = Size(gw, gh)
                        )
                    }

                    drawRect(
                        color = Color(0xFF424242),
                        topLeft = Offset(gx, gy),
                        size = Size(gw, gh),
                        style = Stroke(width = if (isDoorLeaf) 2.2f else 1.2f)
                    )

                    val l = gx
                    val r = gx + gw
                    val t = gy
                    val b = gy + gh
                    val cx = (l + r) / 2f
                    val cy = (t + b) / 2f
                    when (p.openingType) {
                        SashOpeningType.TURN_LEFT -> {
                            drawLine(Color(0xFF333333), Offset(l, t), Offset(r, cy), 1f)
                            drawLine(Color(0xFF333333), Offset(r, cy), Offset(l, b), 1f)
                        }
                        SashOpeningType.TURN_RIGHT -> {
                            drawLine(Color(0xFF333333), Offset(r, t), Offset(l, cy), 1f)
                            drawLine(Color(0xFF333333), Offset(l, cy), Offset(r, b), 1f)
                        }
                        SashOpeningType.TILT_TURN_LEFT -> {
                            drawLine(Color(0xFF333333), Offset(l, t), Offset(r, cy), 1f)
                            drawLine(Color(0xFF333333), Offset(r, cy), Offset(l, b), 1f)
                            drawLine(Color(0xFF333333), Offset(l, b), Offset(cx, t), 1f)
                            drawLine(Color(0xFF333333), Offset(cx, t), Offset(r, b), 1f)
                        }
                        SashOpeningType.TILT_TURN_RIGHT -> {
                            drawLine(Color(0xFF333333), Offset(r, t), Offset(l, cy), 1f)
                            drawLine(Color(0xFF333333), Offset(l, cy), Offset(r, b), 1f)
                            drawLine(Color(0xFF333333), Offset(l, b), Offset(cx, t), 1f)
                            drawLine(Color(0xFF333333), Offset(cx, t), Offset(r, b), 1f)
                        }
                        SashOpeningType.TRANSOM_BOTTOM -> {
                            drawLine(Color(0xFF333333), Offset(l, b), Offset(cx, t), 1f)
                            drawLine(Color(0xFF333333), Offset(cx, t), Offset(r, b), 1f)
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
