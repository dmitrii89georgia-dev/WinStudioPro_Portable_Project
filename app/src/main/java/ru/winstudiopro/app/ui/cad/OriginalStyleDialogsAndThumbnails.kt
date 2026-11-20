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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import kotlin.math.abs
import kotlin.math.max
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
    SPLIT_H3
}

private fun SplitOperation.toSashImpostMode(): SashImpostMode = when (this) {
    SplitOperation.NONE -> SashImpostMode.NONE
    SplitOperation.SPLIT_V2 -> SashImpostMode.V1
    SplitOperation.SPLIT_H2 -> SashImpostMode.H1
    SplitOperation.SPLIT_V3 -> SashImpostMode.V2
    SplitOperation.SPLIT_H3 -> SashImpostMode.H2
}

private fun SashImpostMode.toSplitOperation(): SplitOperation = when (this) {
    SashImpostMode.NONE -> SplitOperation.NONE
    SashImpostMode.V1 -> SplitOperation.SPLIT_V2
    SashImpostMode.H1 -> SplitOperation.SPLIT_H2
    SashImpostMode.V2 -> SplitOperation.SPLIT_V3
    SashImpostMode.H2 -> SplitOperation.SPLIT_H3
}

@Composable
fun ImpostsAndOpeningModalDialog(
    cell: OpeningCellNode,
    onDismiss: () -> Unit,
    onApply: (
        splitOp: SplitOperation,
        newOpeningType: SashOpeningType,
        sashImpostMode: SashImpostMode,
        paneSandwichFlags: List<Boolean>,
        hasMosquito: Boolean,
        isDoor: Boolean,
        isReverseDoor: Boolean
    ) -> Unit
) {
    val props = cell.properties
    var selectedSplit by remember { mutableStateOf(SplitOperation.NONE) }
    var selectedOpening by remember { mutableStateOf(props.openingType) }
    var selectedSashImpostMode by remember { mutableStateOf(props.effectiveSashImpostMode()) }
    var paneFlags by remember {
        mutableStateOf(List(3) { idx -> props.isPaneSandwich(idx) })
    }
    var hasMosquito by remember { mutableStateOf(props.hasMosquitoNet) }
    var isDoor by remember { mutableStateOf(props.isDoorSash) }
    var isReverseDoor by remember { mutableStateOf(props.isReverseDoorSash) }

    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
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
                        modifier = Modifier.weight(0.38f)
                    )
                    Text(
                        text = s.openingHeader,
                        color = p.accentBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.62f)
                    )
                }
                HorizontalDivider(color = p.divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ЛЕВАЯ КОЛОНКА: 5 вариантов импостов + сэндвич панели (1..3 кнопки)
                        Column(
                            modifier = Modifier.weight(0.38f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val activeImpostMode = if (selectedOpening.hasSashProfile) {
                                selectedSashImpostMode
                            } else {
                                selectedSplit.toSashImpostMode()
                            }
                            val impostPairs = listOf(
                                SashImpostMode.NONE to SashImpostMode.V1,
                                SashImpostMode.H1 to SashImpostMode.V2,
                                SashImpostMode.H2 to null
                            )
                            impostPairs.forEach { (leftMode, rightMode) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(leftMode, rightMode).forEach { mode ->
                                        if (mode == null) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        } else {
                                            IconCellButton(
                                                selected = activeImpostMode == mode,
                                                onClick = {
                                                    if (selectedOpening.hasSashProfile) {
                                                        selectedSashImpostMode = mode
                                                        selectedSplit = SplitOperation.NONE
                                                    } else {
                                                        val mapped = mode.toSplitOperation()
                                                        selectedSplit = if (selectedSplit == mapped) {
                                                            SplitOperation.NONE
                                                        } else {
                                                            mapped
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("impost_mode_${mode.name.lowercase()}")
                                            ) {
                                                DrawImpostIcon(mode = mode)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "сэндвич панели",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = p.textPrimary
                            )
                            val paneCount = if (selectedOpening.hasSashProfile) {
                                selectedSashImpostMode.paneCount
                            } else {
                                1
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (idx in 0 until paneCount) {
                                    val isPaneSw = paneFlags.getOrElse(idx) { false }
                                    val paneHint = when {
                                        paneCount == 1 -> "1"
                                        activeImpostMode == SashImpostMode.H1 -> if (idx == 0) "В" else "Н"
                                        activeImpostMode == SashImpostMode.V1 -> if (idx == 0) "Л" else "П"
                                        else -> "${idx + 1}"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isPaneSw) p.accentBlue.copy(alpha = 0.2f) else p.background)
                                            .border(
                                                width = if (isPaneSw) 2.dp else 1.2.dp,
                                                color = if (isPaneSw) p.accentBlue else p.accentBlue.copy(alpha = 0.55f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                paneFlags = paneFlags.mapIndexed { i, v ->
                                                    if (i == idx) !v else v
                                                }
                                            }
                                            .testTag("sandwich_pane_$idx"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (isPaneSw) Color(0xFF37474F) else Color(0xFFBBDEFB)
                                                    )
                                                    .border(1.dp, p.accentBlue, RoundedCornerShape(4.dp))
                                            )
                                            Text(
                                                text = paneHint,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPaneSw) p.accentBlue else p.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ПРАВАЯ КОЛОНКА: ОТКРЫВАНИЕ (3x3 стандарт + Глухая створка + 4 варианта штульпа)
                        val sashGrid = listOf(
                            listOf(SashOpeningType.TURN_LEFT, SashOpeningType.TURN_RIGHT, SashOpeningType.TRANSOM_BOTTOM),
                            listOf(SashOpeningType.TILT_TURN_LEFT, SashOpeningType.TILT_TURN_RIGHT, SashOpeningType.TRANSOM_TOP),
                            listOf(SashOpeningType.SLIDING_LEFT, SashOpeningType.SLIDING_RIGHT, SashOpeningType.FIXED_GLASS)
                        )
                        val shtulpGrid = listOf(
                            listOf(SashOpeningType.SHTULP_TURN_L_PASSIVE_R, SashOpeningType.SHTULP_TILTTURN_L_PASSIVE_R),
                            listOf(SashOpeningType.SHTULP_PASSIVE_L_TURN_R, SashOpeningType.SHTULP_PASSIVE_L_TILTTURN_R)
                        )
                        Column(
                            modifier = Modifier.weight(0.62f),
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
                                                    selectedSashImpostMode = SashImpostMode.NONE
                                                    isDoor = false
                                                    isReverseDoor = false
                                                    hasMosquito = false
                                                } else {
                                                    selectedSplit = SplitOperation.NONE
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("opening_${sType.name.lowercase()}")
                                        ) {
                                            DrawOpeningIcon(sType)
                                        }
                                    }
                                }
                            }

                            // Кнопка «Глухая створка»
                            val isBlindSelected = selectedOpening == SashOpeningType.BLIND_SASH
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isBlindSelected) p.accentBlue.copy(alpha = 0.2f) else p.background)
                                    .border(
                                        width = if (isBlindSelected) 2.dp else 0.8.dp,
                                        color = if (isBlindSelected) p.accentBlue else p.divider,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedOpening = SashOpeningType.BLIND_SASH
                                        selectedSplit = SplitOperation.NONE
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("opening_blind_sash"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.size(28.dp)) {
                                    DrawOpeningIcon(SashOpeningType.BLIND_SASH)
                                }
                                Text(
                                    text = "Глухая створка",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = p.textPrimary
                                )
                            }

                            // 4 варианта штульпового открывания со шпингалетами на пассивной створке
                            shtulpGrid.forEach { rowTypes ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowTypes.forEach { sType ->
                                        val isSelected = selectedOpening == sType
                                        IconCellButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedOpening = sType
                                                selectedSplit = SplitOperation.NONE
                                            },
                                            aspectRatio = 1.48f,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("opening_${sType.name.lowercase()}")
                                        ) {
                                            DrawOpeningIcon(sType)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = p.divider, thickness = 0.7.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Чекбоксы: "двери", "обратная дверь", "москитная сетка"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clickable(enabled = selectedOpening.hasSashProfile) {
                                    isDoor = !isDoor
                                    if (isDoor) isReverseDoor = false
                                }
                                .testTag("checkbox_door"),
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
                                .heightIn(min = 44.dp)
                                .clickable(enabled = selectedOpening.hasSashProfile) {
                                    isReverseDoor = !isReverseDoor
                                    if (isReverseDoor) isDoor = false
                                }
                                .testTag("checkbox_reverse_door"),
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(enabled = selectedOpening.hasSashProfile) {
                                hasMosquito = !hasMosquito
                            }
                            .testTag("checkbox_mosquito"),
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
                            .height(48.dp)
                            .testTag("cancel_opening_dialog_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.segmentedBg)
                    ) {
                        Text(s.cancelBtn, color = p.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val effectiveMode = if (selectedOpening.hasSashProfile) {
                                selectedSashImpostMode
                            } else {
                                SashImpostMode.NONE
                            }
                            val pCount = effectiveMode.paneCount
                            val finalFlags = List(pCount) { idx -> paneFlags.getOrElse(idx) { false } }
                            onApply(
                                if (selectedOpening.hasSashProfile) SplitOperation.NONE else selectedSplit,
                                selectedOpening,
                                effectiveMode,
                                finalFlags,
                                hasMosquito && selectedOpening.hasSashProfile,
                                isDoor && selectedOpening.hasSashProfile,
                                isReverseDoor && selectedOpening.hasSashProfile
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("apply_opening_dialog_btn"),
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
    aspectRatio: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalWinStudioPalette.current
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) p.accentBlue.copy(alpha = 0.2f) else p.background)
            .border(
                width = if (selected) 2.dp else 0.8.dp,
                color = if (selected) p.accentBlue else p.divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(5.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun DrawImpostIcon(mode: SashImpostMode) {
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

        when (mode) {
            SashImpostMode.NONE -> {}
            SashImpostMode.V1 -> {
                drawLine(Color(0xFF424242), Offset(w / 2f, pad), Offset(w / 2f, h - pad), 3f)
            }
            SashImpostMode.H1 -> {
                drawLine(Color(0xFF424242), Offset(pad, h / 2f), Offset(w - pad, h / 2f), 3f)
            }
            SashImpostMode.V2 -> {
                val x1 = pad + rectW / 3f
                val x2 = pad + rectW * 2f / 3f
                drawLine(Color(0xFF424242), Offset(x1, pad), Offset(x1, h - pad), 3f)
                drawLine(Color(0xFF424242), Offset(x2, pad), Offset(x2, h - pad), 3f)
            }
            SashImpostMode.H2 -> {
                val y1 = pad + rectH / 3f
                val y2 = pad + rectH * 2f / 3f
                drawLine(Color(0xFF424242), Offset(pad, y1), Offset(w - pad, y1), 3f)
                drawLine(Color(0xFF424242), Offset(pad, y2), Offset(w - pad, y2), 3f)
            }
        }
    }
}

@Composable
private fun DrawOpeningIcon(sashType: SashOpeningType) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val padX = w * 0.08f
        val padY = h * 0.1f

        if (sashType.isShtulp) {
            val l = padX
            val r = w - padX
            val t = padY
            val b = h - padY
            val midX = (l + r) / 2f
            val gapHalf = max(2f, (r - l) * 0.025f)
            val lOuterR = midX - gapHalf
            val rOuterL = midX + gapHalf
            val sashThick = min(w, h) * 0.10f

            // Левая и правая створка (рамка + голубое стекло)
            listOf(l to lOuterR, rOuterL to r).forEachIndexed { idx, (sl, sr) ->
                drawRect(Color.White, Offset(sl, t), Size(sr - sl, b - t))
                drawRect(Color(0xFF424242), Offset(sl, t), Size(sr - sl, b - t), style = Stroke(width = 2f))
                val gl = sl + sashThick
                val gr = sr - sashThick
                val gt = t + sashThick
                val gb = b - sashThick
                drawRect(Color(0xFFBBDEFB), Offset(gl, gt), Size(gr - gl, gb - gt))
                drawRect(Color(0xFF424242), Offset(gl, gt), Size(gr - gl, gb - gt), style = Stroke(width = 1.4f))

                val cx = (gl + gr) / 2f
                val cy = (gt + gb) / 2f
                val isLeft = idx == 0
                if (isLeft) {
                    drawLine(Color(0xFF212121), Offset(gl, gt), Offset(gr, cy), 1.8f)
                    drawLine(Color(0xFF212121), Offset(gr, cy), Offset(gl, gb), 1.8f)
                    if (sashType.isShtulpLeftTiltTurn) {
                        drawLine(Color(0xFF212121), Offset(gl, gb), Offset(cx, gt), 1.8f)
                        drawLine(Color(0xFF212121), Offset(cx, gt), Offset(gr, gb), 1.8f)
                    }
                    if (sashType.isShtulpLeftPassive) {
                        val sx = gr - 3f
                        drawLine(Color(0xFF111111), Offset(sx, t - 2f), Offset(sx, gt + 7f), 2.4f)
                        drawLine(Color(0xFF111111), Offset(sx, gt + 7f), Offset(sx - 4f, gt + 7f), 2.4f)
                        drawLine(Color(0xFF111111), Offset(sx, b + 2f), Offset(sx, gb - 7f), 2.4f)
                        drawLine(Color(0xFF111111), Offset(sx, gb - 7f), Offset(sx - 4f, gb - 7f), 2.4f)
                    }
                } else {
                    drawLine(Color(0xFF212121), Offset(gr, gt), Offset(gl, cy), 1.8f)
                    drawLine(Color(0xFF212121), Offset(gl, cy), Offset(gr, gb), 1.8f)
                    if (sashType.isShtulpRightTiltTurn) {
                        drawLine(Color(0xFF212121), Offset(gl, gb), Offset(cx, gt), 1.8f)
                        drawLine(Color(0xFF212121), Offset(cx, gt), Offset(gr, gb), 1.8f)
                    }
                    if (sashType.isShtulpRightPassive) {
                        val sx = gl + 3f
                        drawLine(Color(0xFF111111), Offset(sx, t - 2f), Offset(sx, gt + 7f), 2.4f)
                        drawLine(Color(0xFF111111), Offset(sx, gt + 7f), Offset(sx + 4f, gt + 7f), 2.4f)
                        drawLine(Color(0xFF111111), Offset(sx, b + 2f), Offset(sx, gb - 7f), 2.4f)
                        drawLine(Color(0xFF111111), Offset(sx, gb - 7f), Offset(sx + 4f, gb - 7f), 2.4f)
                    }
                }
            }
            return@Canvas
        }

        val pad = min(w, h) * 0.12f
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
            style = Stroke(width = 3.2f)
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
            SashOpeningType.FIXED_GLASS -> {
                val arm = min(rectW, rectH) * 0.22f
                drawLine(Color(0xFF212121), Offset(cx - arm, cy), Offset(cx + arm, cy), stroke)
                drawLine(Color(0xFF212121), Offset(cx, cy - arm), Offset(cx, cy + arm), stroke)
            }
            SashOpeningType.BLIND_SASH -> {
                val innerInset = min(rectW, rectH) * 0.16f
                drawRect(
                    color = Color(0xFF424242),
                    topLeft = Offset(pad + innerInset, pad + innerInset),
                    size = Size(rectW - innerInset * 2f, rectH - innerInset * 2f),
                    style = Stroke(width = 1.6f)
                )
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
// Отрисовывает широкий профиль дверей, 5 вариантов импостов и асимметричный штульп!
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

        fun drawMiniPanes(gx: Float, gy: Float, gw: Float, gh: Float, p: OpeningProperties, opHeightMm: Float) {
            val mode = p.effectiveSashImpostMode()
            val glassCol = Color(0xFF90CAF9)
            val swCol = Color(0xFFECEFF1)
            when (mode) {
                SashImpostMode.NONE -> {
                    drawRect(if (p.isPaneSandwich(0)) swCol else glassCol, Offset(gx, gy), Size(gw, gh))
                }
                SashImpostMode.V1 -> {
                    drawRect(if (p.isPaneSandwich(0)) swCol else glassCol, Offset(gx, gy), Size(gw / 2f, gh))
                    drawRect(if (p.isPaneSandwich(1)) swCol else glassCol, Offset(gx + gw / 2f, gy), Size(gw / 2f, gh))
                    drawLine(Color(0xFF37474F), Offset(gx + gw / 2f, gy), Offset(gx + gw / 2f, gy + gh), strokeWidth = 1.6f)
                }
                SashImpostMode.H1 -> {
                    val ratioFromBot = (p.sashImpostHeightFromBottomMm / opHeightMm.coerceAtLeast(1f)).coerceIn(0.18f, 0.82f)
                    val splitY = (gy + gh * (1f - ratioFromBot)).coerceIn(gy + 1f, gy + gh - 1f)
                    drawRect(if (p.isPaneSandwich(0)) swCol else glassCol, Offset(gx, gy), Size(gw, splitY - gy))
                    drawRect(if (p.isPaneSandwich(1)) swCol else glassCol, Offset(gx, splitY), Size(gw, gy + gh - splitY))
                    drawLine(Color(0xFF37474F), Offset(gx, splitY), Offset(gx + gw, splitY), strokeWidth = 1.6f)
                }
                SashImpostMode.V2 -> {
                    val step = gw / 3f
                    for (i in 0 until 3) {
                        drawRect(if (p.isPaneSandwich(i)) swCol else glassCol, Offset(gx + i * step, gy), Size(step, gh))
                    }
                    drawLine(Color(0xFF37474F), Offset(gx + step, gy), Offset(gx + step, gy + gh), strokeWidth = 1.4f)
                    drawLine(Color(0xFF37474F), Offset(gx + step * 2f, gy), Offset(gx + step * 2f, gy + gh), strokeWidth = 1.4f)
                }
                SashImpostMode.H2 -> {
                    val step = gh / 3f
                    for (i in 0 until 3) {
                        drawRect(if (p.isPaneSandwich(i)) swCol else glassCol, Offset(gx, gy + i * step), Size(gw, step))
                    }
                    drawLine(Color(0xFF37474F), Offset(gx, gy + step), Offset(gx + gw, gy + step), strokeWidth = 1.4f)
                    drawLine(Color(0xFF37474F), Offset(gx, gy + step * 2f), Offset(gx + gw, gy + step * 2f), strokeWidth = 1.4f)
                }
            }
        }

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
                val isDoorLeaf = (p.isDoorSash || p.isReverseDoorSash) && p.openingType.hasSashProfile
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
                    if (p.openingType.isShtulp) {
                        val splitX = gx + gw * p.shtulpSplitRatio.coerceIn(0.15f, 0.85f)
                        val gapHalf = max(1f, min(2f, gw * 0.02f))
                        val lW = (splitX - gapHalf - gx).coerceAtLeast(1f)
                        val rX = splitX + gapHalf
                        val rW = (gx + gw - rX).coerceAtLeast(1f)

                        drawMiniPanes(gx, gy, lW, gh, p, op.heightMm)
                        drawMiniPanes(rX, gy, rW, gh, p, op.heightMm)
                        drawRect(Color(0xFF424242), Offset(gx, gy), Size(lW, gh), style = Stroke(width = 1.2f))
                        drawRect(Color(0xFF424242), Offset(rX, gy), Size(rW, gh), style = Stroke(width = 1.2f))
                        drawLine(Color(0xFF263238), Offset(splitX, gy), Offset(splitX, gy + gh), strokeWidth = 1.8f)

                        val lCx = gx + lW / 2f
                        val rCx = rX + rW / 2f
                        val cy = gy + gh / 2f
                        drawLine(Color(0xFF333333), Offset(gx, gy), Offset(gx + lW, cy), 0.9f)
                        drawLine(Color(0xFF333333), Offset(gx + lW, cy), Offset(gx, gy + gh), 0.9f)
                        if (p.openingType.isShtulpLeftTiltTurn) {
                            drawLine(Color(0xFF333333), Offset(gx, gy + gh), Offset(lCx, gy), 0.9f)
                            drawLine(Color(0xFF333333), Offset(lCx, gy), Offset(gx + lW, gy + gh), 0.9f)
                        }
                        drawLine(Color(0xFF333333), Offset(rX + rW, gy), Offset(rX, cy), 0.9f)
                        drawLine(Color(0xFF333333), Offset(rX, cy), Offset(rX + rW, gy + gh), 0.9f)
                        if (p.openingType.isShtulpRightTiltTurn) {
                            drawLine(Color(0xFF333333), Offset(rX, gy + gh), Offset(rCx, gy), 0.9f)
                            drawLine(Color(0xFF333333), Offset(rCx, gy), Offset(rX + rW, gy + gh), 0.9f)
                        }
                    } else {
                        drawMiniPanes(gx, gy, gw, gh, p, op.heightMm)
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
                            SashOpeningType.TRANSOM_TOP -> {
                                drawLine(Color(0xFF333333), Offset(l, t), Offset(cx, b), 1f)
                                drawLine(Color(0xFF333333), Offset(cx, b), Offset(r, t), 1f)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
