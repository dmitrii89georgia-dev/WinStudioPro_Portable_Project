package ru.winstudiopro.app.ui.cad

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class CircleHotspot(
    val center: Offset,
    val radius: Float,
    val action: () -> Unit
)

private data class RectHotspot(
    val rect: Rect,
    val action: () -> Unit
)

/**
 * Интерактивный 2D САПР-холст с правильным порядком слоёв (Z-Order),
 * честной шириной дверного профиля, внутренним импостом двери/створки и его внутренней размерной линией.
 */
@Composable
fun InteractiveWindowCadCanvas(
    topology: WindowBlockTopology,
    profileSystem: ProfileSystemSpec,
    colorItem: ColorCatalogItem,
    showShadows: Boolean,
    showControls: Boolean,
    onOpenCellDialog: (OpeningCellNode) -> Unit,
    onQuickSplitCell: (String, ImpostDirection, Int) -> Unit,
    onRemoveImpost: (String) -> Unit,
    onEditTotalWidth: (Float) -> Unit,
    onEditTotalHeight: (Float) -> Unit,
    onEditChainSegment: (Boolean, Int, Float) -> Unit,
    onEqualizeChain: (Boolean) -> Unit,
    onEditDoorImpostHeight: (String, Float) -> Unit = { _, _ -> },
    onToggleDoorBottomSandwich: (String) -> Unit = {},
    topSafeInsetPx: Float = 0f,
    rightSafeInsetPx: Float = 0f,
    bottomSafeInsetPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    var circleHotspots by remember { mutableStateOf<List<CircleHotspot>>(emptyList()) }
    var rectHotspots by remember { mutableStateOf<List<RectHotspot>>(emptyList()) }

    val latestTopology by rememberUpdatedState(topology)
    val latestOnOpenCellDialog by rememberUpdatedState(onOpenCellDialog)
    val latestOnQuickSplitCell by rememberUpdatedState(onQuickSplitCell)
    val latestOnRemoveImpost by rememberUpdatedState(onRemoveImpost)
    val latestOnEditTotalWidth by rememberUpdatedState(onEditTotalWidth)
    val latestOnEditTotalHeight by rememberUpdatedState(onEditTotalHeight)
    val latestOnEditChainSegment by rememberUpdatedState(onEditChainSegment)
    val latestOnEqualizeChain by rememberUpdatedState(onEqualizeChain)
    val latestOnEditDoorImpostHeight by rememberUpdatedState(onEditDoorImpostHeight)
    val latestOnToggleDoorBottomSandwich by rememberUpdatedState(onToggleDoorBottomSandwich)
    val palette = LocalWinStudioPalette.current

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvasBg)
            .pointerInput(topology, showControls) {
                detectTapGestures { tap ->
                    // 1. Сначала проверяем прямоугольные капсулы размеров (включая внутренний размер импоста двери),
                    //    приоритет у последних добавленных (верхних по Z-порядку)
                    val hitCircle = circleHotspots.lastOrNull { c ->
                        hypot(tap.x - c.center.x, tap.y - c.center.y) <= c.radius * 1.25f
                    }
                    if (hitCircle != null) {
                        hitCircle.action()
                        return@detectTapGestures
                    }
                    val hitRect = rectHotspots.lastOrNull { r -> r.rect.contains(tap) }
                    if (hitRect != null) {
                        hitRect.action()
                    }
                }
            }
    ) {
        // Лёгкая инженерная сетка на фоне холста в стиле iOS CAD
        val gridStep = 44f
        var gx = 0f
        while (gx < size.width) {
            drawLine(palette.canvasGridLine, Offset(gx, 0f), Offset(gx, size.height), 1f)
            gx += gridStep
        }
        var gy = 0f
        while (gy < size.height) {
            drawLine(palette.canvasGridLine, Offset(0f, gy), Offset(size.width, gy), 1f)
            gy += gridStep
        }

        val xCoords = latestTopology.xGridCoords
        val yCoords = latestTopology.yGridCoords
        val hasMultiX = xCoords.size > 2
        val hasMultiY = yCoords.size > 2

        // Адаптивные отступы: чертёж занимает всю область между панелями с комфортными полями ~12–14 dp
        val dimOverhangBottom = if (hasMultiX) 114f + 12.dp.toPx() else 70f + 12.dp.toPx()
        val padLeft = if (hasMultiY) 192f else 108f
        val padRight = max(14.dp.toPx(), rightSafeInsetPx)
        val rawPadTop = max(14.dp.toPx(), topSafeInsetPx + 12.dp.toPx())
        val rawPadBottom = dimOverhangBottom + bottomSafeInsetPx
        val maxVertPad = (size.height - 120f).coerceAtLeast(80f)
        val vertSum = (rawPadTop + rawPadBottom).coerceAtLeast(1f)
        val vertScale = if (vertSum > maxVertPad) maxVertPad / vertSum else 1f
        val padTop = rawPadTop * vertScale
        val padBottom = rawPadBottom * vertScale

        val availW = (size.width - padLeft - padRight).coerceAtLeast(100f)
        val availH = (size.height - padTop - padBottom).coerceAtLeast(100f)

        val totalW = latestTopology.totalWidthMm.coerceAtLeast(200f)
        val totalH = latestTopology.totalHeightMm.coerceAtLeast(200f)
        val scale = min(availW / totalW, availH / totalH)

        val drawW = totalW * scale
        val drawH = totalH * scale
        val originX = padLeft + (availW - drawW) / 2f
        val originY = padTop + (availH - drawH) / 2f

        val newCircles = mutableListOf<CircleHotspot>()
        val newRects = mutableListOf<RectHotspot>()

        val frameFillColor = Color(colorItem.hexColor)
        val borderDark = Color(0xFF55595E)
        val borderInner = Color(0xFF7B8087)
        val glassColor = Color(0xFFC5D6E8)
        val sandwichColor = Color(0xFFE4E8EE)

        // Честные пропорции профилей от миллиметров выбранной профильной системы:
        // дверная створка заметно шире оконной створки (как на 00:59 в видео!)
        val frameWidthPx = (profileSystem.frameWidthMm * scale).coerceIn(11f, 30f)
        val impostWidthPx = (profileSystem.impostWidthMm * scale).coerceIn(11f, 32f)
        val sashWidthPx = (profileSystem.sashWidthMm * scale).coerceIn(12f, 28f)
        val doorSashWidthPx = max(
            sashWidthPx * 1.58f,
            (profileSystem.doorSashWidthMm * scale).coerceIn(21f, 46f)
        )
        val shtapikPx = (profileSystem.shtapikWidthMm * scale * 0.3f).coerceIn(4f, 6.5f)
        val overlapPx = (profileSystem.sashOverlapMm * scale * 0.65f).coerceIn(4f, 8f)

        val dimTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.dimCapsuleText.toArgb()
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val openingSizeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(18, 42, 66)
            textSize = 21.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun drawClearOpeningSizeLabel(
            frame: FrameBlockNode,
            op: OpeningCellNode,
            paneLeft: Float,
            paneTop: Float,
            paneRight: Float,
            paneBottom: Float
        ) {
            if (paneRight - paneLeft < 54f || paneBottom - paneTop < 40f) return
            val leftDedMm = if (abs(op.leftMm - frame.leftMm) < 2f) profileSystem.frameWidthMm else profileSystem.impostWidthMm / 2f
            val rightDedMm = if (abs(op.rightMm - frame.rightMm) < 2f) profileSystem.frameWidthMm else profileSystem.impostWidthMm / 2f
            val topDedMm = if (abs(op.topMm - frame.topMm) < 2f) profileSystem.frameWidthMm else profileSystem.impostWidthMm / 2f
            val bottomDedMm = if (abs(op.bottomMm - frame.bottomMm) < 2f) profileSystem.frameWidthMm else profileSystem.impostWidthMm / 2f

            val clearWMm = max(40f, op.widthMm - leftDedMm - rightDedMm).roundToInt()
            val clearHMm = max(40f, op.heightMm - topDedMm - bottomDedMm).roundToInt()
            val txt = "$clearWMm×$clearHMm"

            val cx = (paneLeft + paneRight) / 2f
            val centerY = (paneTop + paneBottom) / 2f
            // Ставим под центральным голубым кружком, не перекрывая границы стекла
            val labelY = (centerY + 38f).coerceAtMost(paneBottom - 10f)
            val textW = openingSizeTextPaint.measureText(txt)
            val bgW = textW + 18f
            val bgH = 27f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.88f),
                topLeft = Offset(cx - bgW / 2f, labelY - bgH + 6f),
                size = Size(bgW, bgH),
                cornerRadius = CornerRadius(12f, 12f)
            )
            drawIntoCanvas { nc ->
                nc.nativeCanvas.drawText(txt, cx, labelY, openingSizeTextPaint)
            }
        }

        fun drawDimensionCapsule(cx: Float, cy: Float, label: String, onClick: () -> Unit) {
            val textW = dimTextPaint.measureText(label)
            val pillW = max(88f, textW + 24f)
            val pillH = 40f
            val topLeft = Offset(cx - pillW / 2f, cy - pillH / 2f)
            drawRoundRect(
                color = palette.dimCapsuleBg,
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(17f, 17f)
            )
            drawRoundRect(
                color = palette.accentBlue.copy(alpha = 0.34f),
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(17f, 17f),
                style = Stroke(width = 1.5f)
            )
            drawIntoCanvas { nc ->
                nc.nativeCanvas.drawText(label, cx, cy + 9.5f, dimTextPaint)
            }
            newRects.add(
                RectHotspot(Rect(topLeft.x, topLeft.y, topLeft.x + pillW, topLeft.y + pillH), onClick)
            )
        }

        fun drawGlassOrSandwichPane(
            gLeft: Float,
            gTop: Float,
            gRight: Float,
            gBottom: Float,
            isSandwich: Boolean,
            glazingBars: GlazingBarsPattern,
            hasMosquito: Boolean
        ) {
            if (gRight <= gLeft + 2f || gBottom <= gTop + 2f) return
            val fillCol = if (isSandwich) sandwichColor else glassColor
            drawRect(
                color = fillCol,
                topLeft = Offset(gLeft, gTop),
                size = Size(gRight - gLeft, gBottom - gTop)
            )
            // Внутренняя тень от профиля/штапика на стеклопакет (как в оригинальной программе!)
            if (showShadows) {
                val shBand = 5.5f
                // Тень сверху
                drawRect(
                    color = Color(0x28000000),
                    topLeft = Offset(gLeft, gTop),
                    size = Size(gRight - gLeft, min(shBand, gBottom - gTop))
                )
                // Тень слева
                drawRect(
                    color = Color(0x28000000),
                    topLeft = Offset(gLeft, gTop),
                    size = Size(min(shBand, gRight - gLeft), gBottom - gTop)
                )
            }
            drawRect(
                color = borderInner,
                topLeft = Offset(gLeft, gTop),
                size = Size(gRight - gLeft, gBottom - gTop),
                style = Stroke(width = 1.2f)
            )

            // Шпросы
            if (glazingBars != GlazingBarsPattern.NONE) {
                val rows = glazingBars.horizontalRows
                val cols = glazingBars.verticalCols
                for (r in 1..rows) {
                    val gy = gTop + (gBottom - gTop) * r / (rows + 1)
                    drawRect(
                        color = frameFillColor,
                        topLeft = Offset(gLeft, gy - 3f),
                        size = Size(gRight - gLeft, 6f)
                    )
                    drawLine(borderInner, Offset(gLeft, gy - 3f), Offset(gRight, gy - 3f), 1f)
                    drawLine(borderInner, Offset(gLeft, gy + 3f), Offset(gRight, gy + 3f), 1f)
                }
                for (c in 1..cols) {
                    val gx = gLeft + (gRight - gLeft) * c / (cols + 1)
                    drawRect(
                        color = frameFillColor,
                        topLeft = Offset(gx - 3f, gTop),
                        size = Size(6f, gBottom - gTop)
                    )
                    drawLine(borderInner, Offset(gx - 3f, gTop), Offset(gx - 3f, gBottom), 1f)
                    drawLine(borderInner, Offset(gx + 3f, gTop), Offset(gx + 3f, gBottom), 1f)
                }
            }

            // Москитная сетка (точки, как на 01:36 в видео)
            if (hasMosquito) {
                val step = 15f
                var my = gTop + 12f
                while (my < gBottom - 6f) {
                    var mx = gLeft + 12f
                    while (mx < gRight - 6f) {
                        drawCircle(Color(0x66222222), radius = 1.3f, center = Offset(mx, my))
                        mx += step
                    }
                    my += step
                }
            }
        }

        // =========================================================================
        // ОТРИСОВКА КАЖДОЙ РАМЫ СТРОГО ПО СЛОЯМ:
        // Слой 1: Коробка рамы
        // Слой 2: Импосты рамы (под створками!)
        // Слой 3: Глухие проемы (стекло, штапики, угловые кружки деления)
        // Слой 4: Створки и Двери ПОВЕРХ рамы и ПОВЕРХ импостов!
        // Слой 5: Красные кружки удаления импостов `×` поверх всего
        // =========================================================================
        latestTopology.frames.forEach { frame ->
            val fl = originX + frame.leftMm * scale
            val ft = originY + frame.topMm * scale
            val fr = originX + frame.rightMm * scale
            val fb = originY + frame.bottomMm * scale

            // --- СЛОЙ 1: КОРОБКА РАМЫ ---
            if (showShadows) {
                drawRect(
                    color = Color(0x38000000),
                    topLeft = Offset(fl + 5f, ft + 5f),
                    size = Size(fr - fl, fb - ft)
                )
            }

            drawRect(
                color = frameFillColor,
                topLeft = Offset(fl, ft),
                size = Size(fr - fl, fb - ft)
            )
            drawRect(
                color = borderDark,
                topLeft = Offset(fl, ft),
                size = Size(fr - fl, fb - ft),
                style = Stroke(width = 2f)
            )

            // Заусовка 45° по углам рамы
            drawLine(borderInner, Offset(fl, ft), Offset(fl + frameWidthPx, ft + frameWidthPx), 1.4f)
            drawLine(borderInner, Offset(fr, ft), Offset(fr - frameWidthPx, ft + frameWidthPx), 1.4f)
            drawLine(borderInner, Offset(fl, fb), Offset(fl + frameWidthPx, fb - frameWidthPx), 1.4f)
            drawLine(borderInner, Offset(fr, fb), Offset(fr - frameWidthPx, fb - frameWidthPx), 1.4f)

            // --- СЛОЙ 2: ИМПОСТЫ РАМЫ (рисуются ДО створок, чтобы створки лежали ПОВЕРХ импостов!) ---
            frame.imposts.forEach { imp ->
                if (imp.direction == ImpostDirection.VERTICAL) {
                    val x = originX + imp.startX * scale
                    val topInset = if (abs(imp.startY - frame.topMm) < 2f) frameWidthPx else impostWidthPx / 2f
                    val botInset = if (abs(imp.endY - frame.bottomMm) < 2f) frameWidthPx else impostWidthPx / 2f
                    val top = originY + imp.startY * scale + topInset
                    val bot = originY + imp.endY * scale - botInset
                    if (bot > top) {
                        drawRect(
                            color = frameFillColor,
                            topLeft = Offset(x - impostWidthPx / 2f, top),
                            size = Size(impostWidthPx, bot - top)
                        )
                        drawRect(
                            color = borderDark,
                            topLeft = Offset(x - impostWidthPx / 2f, top),
                            size = Size(impostWidthPx, bot - top),
                            style = Stroke(width = 1.6f)
                        )
                    }
                } else {
                    val y = originY + imp.startY * scale
                    val leftInset = if (abs(imp.startX - frame.leftMm) < 2f) frameWidthPx else impostWidthPx / 2f
                    val rightInset = if (abs(imp.endX - frame.rightMm) < 2f) frameWidthPx else impostWidthPx / 2f
                    val left = originX + imp.startX * scale + leftInset
                    val right = originX + imp.endX * scale - rightInset
                    if (right > left) {
                        drawRect(
                            color = frameFillColor,
                            topLeft = Offset(left, y - impostWidthPx / 2f),
                            size = Size(right - left, impostWidthPx)
                        )
                        drawRect(
                            color = borderDark,
                            topLeft = Offset(left, y - impostWidthPx / 2f),
                            size = Size(right - left, impostWidthPx),
                            style = Stroke(width = 1.6f)
                        )
                    }
                }
            }

            // --- СЛОЙ 3: ГЛУХИЕ ПРОЕМЫ (без створок) ---
            frame.openings.filter { !it.properties.openingType.hasSashProfile }.forEach { op ->
                val insetLeft = if (abs(op.leftMm - frame.leftMm) < 2f) frameWidthPx else impostWidthPx / 2f
                val insetRight = if (abs(op.rightMm - frame.rightMm) < 2f) frameWidthPx else impostWidthPx / 2f
                val insetTop = if (abs(op.topMm - frame.topMm) < 2f) frameWidthPx else impostWidthPx / 2f
                val insetBottom = if (abs(op.bottomMm - frame.bottomMm) < 2f) frameWidthPx else impostWidthPx / 2f

                val ol = originX + op.leftMm * scale + insetLeft
                val ot = originY + op.topMm * scale + insetTop
                val orx = originX + op.rightMm * scale - insetRight
                val ob = originY + op.bottomMm * scale - insetBottom

                if (orx <= ol + 8f || ob <= ot + 8f) return@forEach

                val props = op.properties
                // Контур проема и штапика
                drawRect(
                    color = borderDark,
                    topLeft = Offset(ol, ot),
                    size = Size(orx - ol, ob - ot),
                    style = Stroke(width = 1.6f)
                )
                val glassLeft = ol + shtapikPx
                val glassTop = ot + shtapikPx
                val glassRight = orx - shtapikPx
                val glassBottom = ob - shtapikPx

                drawGlassOrSandwichPane(
                    gLeft = glassLeft,
                    gTop = glassTop,
                    gRight = glassRight,
                    gBottom = glassBottom,
                    isSandwich = props.isSandwichFilling,
                    glazingBars = props.glazingBars,
                    hasMosquito = props.hasMosquitoNet
                )

                val cx = (glassLeft + glassRight) / 2f
                val cy = (glassTop + glassBottom) / 2f

                // Перекрестие `+` в центре глухого проема
                val crossArm = min(glassRight - glassLeft, glassBottom - glassTop) * 0.16f
                drawLine(Color(0xFF222222), Offset(cx - crossArm, cy), Offset(cx + crossArm, cy), 1.8f)
                drawLine(Color(0xFF222222), Offset(cx, cy - crossArm), Offset(cx, cy + crossArm), 1.8f)

                drawClearOpeningSizeLabel(frame, op, glassLeft, glassTop, glassRight, glassBottom)

                newRects.add(
                    RectHotspot(Rect(glassLeft, glassTop, glassRight, glassBottom)) {
                        latestOnOpenCellDialog(op)
                    }
                )

                if (showControls) {
                    val centerRadius = min(glassRight - glassLeft, glassBottom - glassTop).coerceIn(28f, 52f) * 0.45f
                    drawCircle(
                        color = Color(0x553B8EEA),
                        radius = centerRadius,
                        center = Offset(cx, cy)
                    )
                    newCircles.add(
                        CircleHotspot(
                            center = Offset(cx, cy),
                            radius = centerRadius,
                            action = { latestOnOpenCellDialog(op) }
                        )
                    )

                    if ((glassRight - glassLeft) > 112f && (glassBottom - glassTop) > 112f) {
                        val cornerR = 28f
                        val offsetFromEdge = 22f
                        val iconColor = Color(0xFF1565C0)

                        // Верхний левый: `||` (2 верт. импоста -> 3 части)
                        val tlCenter = Offset(glassLeft + offsetFromEdge, glassTop + offsetFromEdge)
                        drawCircle(Color(0x553B8EEA), cornerR, tlCenter)
                        drawLine(iconColor, Offset(tlCenter.x - 5.5f, tlCenter.y - 11f), Offset(tlCenter.x - 5.5f, tlCenter.y + 11f), 3.4f)
                        drawLine(iconColor, Offset(tlCenter.x + 5.5f, tlCenter.y - 11f), Offset(tlCenter.x + 5.5f, tlCenter.y + 11f), 3.4f)
                        newCircles.add(
                            CircleHotspot(tlCenter, cornerR) {
                                latestOnQuickSplitCell(op.id, ImpostDirection.VERTICAL, 3)
                            }
                        )

                        // Нижний левый: `|` (1 верт. импост -> 2 части)
                        val blCenter = Offset(glassLeft + offsetFromEdge, glassBottom - offsetFromEdge)
                        drawCircle(Color(0x553B8EEA), cornerR, blCenter)
                        drawLine(iconColor, Offset(blCenter.x, blCenter.y - 11f), Offset(blCenter.x, blCenter.y + 11f), 3.4f)
                        newCircles.add(
                            CircleHotspot(blCenter, cornerR) {
                                latestOnQuickSplitCell(op.id, ImpostDirection.VERTICAL, 2)
                            }
                        )

                        // Верхний правый: `=` (2 гориз. импоста -> 3 части)
                        val trCenter = Offset(glassRight - offsetFromEdge, glassTop + offsetFromEdge)
                        drawCircle(Color(0x553B8EEA), cornerR, trCenter)
                        drawLine(iconColor, Offset(trCenter.x - 11f, trCenter.y - 5.5f), Offset(trCenter.x + 11f, trCenter.y - 5.5f), 3.4f)
                        drawLine(iconColor, Offset(trCenter.x - 11f, trCenter.y + 5.5f), Offset(trCenter.x + 11f, trCenter.y + 5.5f), 3.4f)
                        newCircles.add(
                            CircleHotspot(trCenter, cornerR) {
                                latestOnQuickSplitCell(op.id, ImpostDirection.HORIZONTAL, 3)
                            }
                        )

                        // Нижний правый: `—` (1 гориз. импост -> 2 части)
                        val brCenter = Offset(glassRight - offsetFromEdge, glassBottom - offsetFromEdge)
                        drawCircle(Color(0x553B8EEA), cornerR, brCenter)
                        drawLine(iconColor, Offset(brCenter.x - 11f, brCenter.y), Offset(brCenter.x + 11f, brCenter.y), 3.4f)
                        newCircles.add(
                            CircleHotspot(brCenter, cornerR) {
                                latestOnQuickSplitCell(op.id, ImpostDirection.HORIZONTAL, 2)
                            }
                        )
                    }
                }
            }

            // --- СЛОЙ 4: СТВОРКИ И ДВЕРИ (рисуются ПОВЕРХ РАМЫ И ПОВЕРХ ИМПОСТОВ!) ---
            frame.openings.filter { it.properties.openingType.hasSashProfile }.forEach { op ->
                val insetLeft = if (abs(op.leftMm - frame.leftMm) < 2f) frameWidthPx else impostWidthPx / 2f
                val insetRight = if (abs(op.rightMm - frame.rightMm) < 2f) frameWidthPx else impostWidthPx / 2f
                val insetTop = if (abs(op.topMm - frame.topMm) < 2f) frameWidthPx else impostWidthPx / 2f
                val insetBottom = if (abs(op.bottomMm - frame.bottomMm) < 2f) frameWidthPx else impostWidthPx / 2f

                val ol = originX + op.leftMm * scale + insetLeft
                val ot = originY + op.topMm * scale + insetTop
                val orx = originX + op.rightMm * scale - insetRight
                val ob = originY + op.bottomMm * scale - insetBottom

                if (orx <= ol + 8f || ob <= ot + 8f) return@forEach

                val props = op.properties
                // Наплав створки/двери ложится ПОВЕРХ рамы и ПОВЕРХ импостов со всех 4 сторон!
                val sl = ol - overlapPx
                val st = ot - overlapPx
                val sr = orx + overlapPx
                val sb = ob + overlapPx
                val sw = if (props.isDoorSash) doorSashWidthPx else sashWidthPx

                // Внешняя тень створки/двери падает и на раму, и на импост!
                if (showShadows) {
                    drawRect(
                        color = Color(0x36000000),
                        topLeft = Offset(sl + 4.5f, st + 4.5f),
                        size = Size(sr - sl, sb - st)
                    )
                }

                // Полотно профиля створки или широкой двери
                drawRect(
                    color = frameFillColor,
                    topLeft = Offset(sl, st),
                    size = Size(sr - sl, sb - st)
                )
                drawRect(
                    color = borderDark,
                    topLeft = Offset(sl, st),
                    size = Size(sr - sl, sb - st),
                    style = Stroke(width = 2.1f)
                )

                // Углы заусовки 45° створки/двери
                drawLine(borderInner, Offset(sl, st), Offset(sl + sw, st + sw), 1.4f)
                drawLine(borderInner, Offset(sr, st), Offset(sr - sw, st + sw), 1.4f)
                drawLine(borderInner, Offset(sl, sb), Offset(sl + sw, sb - sw), 1.4f)
                drawLine(borderInner, Offset(sr, sb), Offset(sr - sw, sb - sw), 1.4f)

                val innerL = (sl + sw).coerceAtMost(sr - 6f)
                val innerT = (st + sw).coerceAtMost(sb - 6f)
                val innerR = (sr - sw).coerceAtLeast(innerL + 6f)
                val innerB = (sb - sw).coerceAtLeast(innerT + 6f)

                if (props.hasHorizontalSashImpost && (innerB - innerT) > 60f) {
                    // --- СТВОРКА / ДВЕРЬ С ВНУТРЕННИМ ГОРИЗОНТАЛЬНЫМ ИМПОСТОМ (как на 01:44 и 03:57 в видео!) ---
                    val impHeightMm = props.sashImpostHeightFromBottomMm.coerceIn(180f, (op.heightMm - 180f).coerceAtLeast(200f))
                    val impCenterY = (originY + op.bottomMm * scale - impHeightMm * scale)
                        .coerceIn(innerT + 28f, innerB - 28f)
                    val halfImpPx = (impostWidthPx * 0.48f).coerceAtLeast(5.5f)

                    // Верхний проем внутри двери/створки
                    val topSubBottom = impCenterY - halfImpPx
                    drawRect(
                        color = borderDark,
                        topLeft = Offset(innerL, innerT),
                        size = Size(innerR - innerL, topSubBottom - innerT),
                        style = Stroke(width = 1.5f)
                    )
                    val topGlassL = innerL + shtapikPx
                    val topGlassT = innerT + shtapikPx
                    val topGlassR = innerR - shtapikPx
                    val topGlassB = topSubBottom - shtapikPx

                    drawGlassOrSandwichPane(
                        gLeft = topGlassL,
                        gTop = topGlassT,
                        gRight = topGlassR,
                        gBottom = topGlassB,
                        isSandwich = props.isSandwichFilling,
                        glazingBars = props.glazingBars,
                        hasMosquito = props.hasMosquitoNet
                    )

                    // Нижний проем внутри двери/створки
                    val botSubTop = impCenterY + halfImpPx
                    drawRect(
                        color = borderDark,
                        topLeft = Offset(innerL, botSubTop),
                        size = Size(innerR - innerL, innerB - botSubTop),
                        style = Stroke(width = 1.5f)
                    )
                    val botGlassL = innerL + shtapikPx
                    val botGlassT = botSubTop + shtapikPx
                    val botGlassR = innerR - shtapikPx
                    val botGlassB = innerB - shtapikPx

                    drawGlassOrSandwichPane(
                        gLeft = botGlassL,
                        gTop = botGlassT,
                        gRight = botGlassR,
                        gBottom = botGlassB,
                        isSandwich = props.bottomPartIsSandwich || props.isSandwichFilling,
                        glazingBars = GlazingBarsPattern.NONE,
                        hasMosquito = props.hasMosquitoNet
                    )

                    // Сам горизонтальный импост внутри створки/двери
                    drawRect(
                        color = frameFillColor,
                        topLeft = Offset(innerL, topSubBottom),
                        size = Size(innerR - innerL, botSubTop - topSubBottom)
                    )
                    drawRect(
                        color = borderDark,
                        topLeft = Offset(innerL, topSubBottom),
                        size = Size(innerR - innerL, botSubTop - topSubBottom),
                        style = Stroke(width = 1.5f)
                    )

                    // Линии открывания рисуются по всему внутреннему контуру двери (как на 01:44 в видео!),
                    // а ручка ставится на уровне импоста/середины двери
                    drawOpeningLinesAndHandle(
                        openingType = props.openingType,
                        glassLeft = topGlassL,
                        glassTop = topGlassT,
                        glassRight = topGlassR,
                        glassBottom = botGlassB,
                        sashBandPx = sw,
                        handleOverrideY = impCenterY
                    )

                    val topCx = (topGlassL + topGlassR) / 2f
                    val topCy = (topGlassT + topGlassB) / 2f
                    val botCx = (botGlassL + botGlassR) / 2f
                    val botCy = (botGlassT + botGlassB) / 2f

                    drawClearOpeningSizeLabel(frame, op, topGlassL, topGlassT, topGlassR, topGlassB)

                    newRects.add(
                        RectHotspot(Rect(topGlassL, topGlassT, topGlassR, topGlassB)) {
                            latestOnOpenCellDialog(op)
                        }
                    )
                    newRects.add(
                        RectHotspot(Rect(botGlassL, botGlassT, botGlassR, botGlassB)) {
                            latestOnToggleDoorBottomSandwich(op.id)
                        }
                    )

                    // Внутренняя вертикальная размерная линия высоты импоста двери (как `1076` -> `800` на 01:44–01:50 в видео!)
                    val doorBottomAxisY = originY + op.bottomMm * scale
                    val dimX = (botGlassL + botGlassR) / 2f
                    drawLine(Color(0xFF222222), Offset(dimX, impCenterY), Offset(dimX, doorBottomAxisY), 1.4f)
                    drawCircle(Color.Black, 4f, Offset(dimX, impCenterY))
                    drawCircle(Color.Black, 4f, Offset(dimX, doorBottomAxisY))
                    drawDimensionCapsule(
                        cx = dimX,
                        cy = (impCenterY + doorBottomAxisY) / 2f,
                        label = "${impHeightMm.roundToInt()}"
                    ) {
                        latestOnEditDoorImpostHeight(op.id, impHeightMm)
                    }

                    if (showControls) {
                        // Голубой кружок в верхней части двери -> открывает диалог проема
                        val rTop = min(topGlassR - topGlassL, topGlassB - topGlassT).coerceIn(26f, 48f) * 0.44f
                        drawCircle(Color(0x553B8EEA), rTop, Offset(topCx, topCy))
                        newCircles.add(
                            CircleHotspot(Offset(topCx, topCy), rTop) {
                                latestOnOpenCellDialog(op)
                            }
                        )
                        // Голубой кружок в нижней части двери (справа от размерной линии, чтобы не перекрывать капсулу `800`)
                        val botCircleCenter = Offset(
                            (botGlassR - 26f).coerceAtLeast(botCx + 24f),
                            botCy
                        )
                        drawCircle(Color(0x553B8EEA), 18f, botCircleCenter)
                        newCircles.add(
                            CircleHotspot(botCircleCenter, 18f) {
                                latestOnToggleDoorBottomSandwich(op.id)
                            }
                        )
                    }
                } else {
                    // --- ОБЫЧНАЯ СТВОРКА ИЛИ ДВЕРЬ БЕЗ ВНУТРЕННЕГО ИМПОСТА ---
                    drawRect(
                        color = borderDark,
                        topLeft = Offset(innerL, innerT),
                        size = Size(innerR - innerL, innerB - innerT),
                        style = Stroke(width = 1.5f)
                    )
                    val glassLeft = innerL + shtapikPx
                    val glassTop = innerT + shtapikPx
                    val glassRight = innerR - shtapikPx
                    val glassBottom = innerB - shtapikPx

                    drawGlassOrSandwichPane(
                        gLeft = glassLeft,
                        gTop = glassTop,
                        gRight = glassRight,
                        gBottom = glassBottom,
                        isSandwich = props.isSandwichFilling,
                        glazingBars = props.glazingBars,
                        hasMosquito = props.hasMosquitoNet
                    )

                    drawOpeningLinesAndHandle(
                        openingType = props.openingType,
                        glassLeft = glassLeft,
                        glassTop = glassTop,
                        glassRight = glassRight,
                        glassBottom = glassBottom,
                        sashBandPx = sw
                    )

                    val cx = (glassLeft + glassRight) / 2f
                    val cy = (glassTop + glassBottom) / 2f

                    drawClearOpeningSizeLabel(frame, op, glassLeft, glassTop, glassRight, glassBottom)

                    newRects.add(
                        RectHotspot(Rect(glassLeft, glassTop, glassRight, glassBottom)) {
                            latestOnOpenCellDialog(op)
                        }
                    )

                    if (showControls) {
                        val centerRadius = min(glassRight - glassLeft, glassBottom - glassTop).coerceIn(28f, 52f) * 0.45f
                        drawCircle(
                            color = Color(0x553B8EEA),
                            radius = centerRadius,
                            center = Offset(cx, cy)
                        )
                        newCircles.add(
                            CircleHotspot(
                                center = Offset(cx, cy),
                                radius = centerRadius,
                                action = { latestOnOpenCellDialog(op) }
                            )
                        )
                    }
                }
            }

            // --- СЛОЙ 5: КРАСНЫЕ КРУЖКИ УДАЛЕНИЯ ИМПОСТОВ `×` ПОВЕРХ ВСЕХ СЛОЕВ ---
            if (showControls) {
                frame.imposts.forEach { imp ->
                    if (WindowCadOperations.canRemoveImpost(frame, imp)) {
                        val center = if (imp.direction == ImpostDirection.VERTICAL) {
                            val x = originX + imp.startX * scale
                            val top = originY + imp.startY * scale + frameWidthPx
                            val bot = originY + imp.endY * scale - frameWidthPx
                            Offset(x, (top + bot) / 2f)
                        } else {
                            val y = originY + imp.startY * scale
                            val left = originX + imp.startX * scale + frameWidthPx
                            val right = originX + imp.endX * scale - frameWidthPx
                            Offset((left + right) / 2f, y)
                        }
                        val r = 20f
                        drawCircle(Color(0x66E53935), r, center)
                        val d = 6.2f
                        drawLine(Color(0xFF8E0000), Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), 2.8f)
                        drawLine(Color(0xFF8E0000), Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), 2.8f)
                        newCircles.add(
                            CircleHotspot(center, r) {
                                latestOnRemoveImpost(imp.id)
                            }
                        )
                    }
                }
            }
        }

        // --- СЛОЙ 6: ВНЕШНИЕ РАЗМЕРНЫЕ ЦЕПОЧКИ СНИЗУ И СЛЕВА ---
        val dimLine = palette.dimLineColor
        if (hasMultiX) {
            val segLineY = originY + drawH + 44f
            for (i in 0 until (xCoords.size - 1)) {
                val x1 = originX + xCoords[i] * scale
                val x2 = originX + xCoords[i + 1] * scale
                val segLen = xCoords[i + 1] - xCoords[i]

                drawLine(dimLine, Offset(x1, segLineY), Offset(x2, segLineY), 1.5f)
                drawCircle(dimLine, 4.5f, Offset(x1, segLineY))
                drawCircle(dimLine, 4.5f, Offset(x2, segLineY))

                val midX = (x1 + x2) / 2f
                val segIdx = i
                drawDimensionCapsule(midX, segLineY, "${segLen.roundToInt()}") {
                    latestOnEditChainSegment(true, segIdx, segLen)
                }
            }
            if (showControls) {
                val eqCenter = Offset(originX + drawW, segLineY)
                drawCircle(palette.accentBlue.copy(alpha = 0.42f), 23f, eqCenter)
                newCircles.add(
                    CircleHotspot(eqCenter, 23f) {
                        latestOnEqualizeChain(true)
                    }
                )
            }

            val totalLineY = originY + drawH + 92f
            drawLine(dimLine, Offset(originX, totalLineY), Offset(originX + drawW, totalLineY), 1.5f)
            drawCircle(dimLine, 4.5f, Offset(originX, totalLineY))
            drawCircle(dimLine, 4.5f, Offset(originX + drawW, totalLineY))
            drawDimensionCapsule(originX + drawW / 2f, totalLineY, "${totalW.roundToInt()}") {
                latestOnEditTotalWidth(totalW)
            }
        } else {
            val totalLineY = originY + drawH + 48f
            drawLine(dimLine, Offset(originX, totalLineY), Offset(originX + drawW, totalLineY), 1.5f)
            drawCircle(dimLine, 4.5f, Offset(originX, totalLineY))
            drawCircle(dimLine, 4.5f, Offset(originX + drawW, totalLineY))
            drawDimensionCapsule(originX + drawW / 2f, totalLineY, "${totalW.roundToInt()}") {
                latestOnEditTotalWidth(totalW)
            }
        }

        if (hasMultiY) {
            val segLineX = originX - 50f
            for (j in 0 until (yCoords.size - 1)) {
                val y1 = originY + yCoords[j] * scale
                val y2 = originY + yCoords[j + 1] * scale
                val segLen = yCoords[j + 1] - yCoords[j]

                drawLine(dimLine, Offset(segLineX, y1), Offset(segLineX, y2), 1.5f)
                drawCircle(dimLine, 4.5f, Offset(segLineX, y1))
                drawCircle(dimLine, 4.5f, Offset(segLineX, y2))

                val midY = (y1 + y2) / 2f
                val segIdx = j
                drawDimensionCapsule(segLineX, midY, "${segLen.roundToInt()}") {
                    latestOnEditChainSegment(false, segIdx, segLen)
                }
            }
            if (showControls) {
                val eqCenter = Offset(segLineX, originY + drawH)
                drawCircle(palette.accentBlue.copy(alpha = 0.42f), 23f, eqCenter)
                newCircles.add(
                    CircleHotspot(eqCenter, 23f) {
                        latestOnEqualizeChain(false)
                    }
                )
            }

            val totalLineX = originX - 140f
            drawLine(dimLine, Offset(totalLineX, originY), Offset(totalLineX, originY + drawH), 1.5f)
            drawCircle(dimLine, 4.5f, Offset(totalLineX, originY))
            drawCircle(dimLine, 4.5f, Offset(totalLineX, originY + drawH))
            drawDimensionCapsule(totalLineX, originY + drawH / 2f, "${totalH.roundToInt()}") {
                latestOnEditTotalHeight(totalH)
            }
        } else {
            val totalLineX = originX - 54f
            drawLine(dimLine, Offset(totalLineX, originY), Offset(totalLineX, originY + drawH), 1.5f)
            drawCircle(dimLine, 4.5f, Offset(totalLineX, originY))
            drawCircle(dimLine, 4.5f, Offset(totalLineX, originY + drawH))
            drawDimensionCapsule(totalLineX, originY + drawH / 2f, "${totalH.roundToInt()}") {
                latestOnEditTotalHeight(totalH)
            }
        }

        circleHotspots = newCircles
        rectHotspots = newRects
    }
}

private fun DrawScope.drawOpeningLinesAndHandle(
    openingType: SashOpeningType,
    glassLeft: Float,
    glassTop: Float,
    glassRight: Float,
    glassBottom: Float,
    sashBandPx: Float,
    handleOverrideY: Float? = null
) {
    val lineCol = Color(0xFF1E2228)
    val cx = (glassLeft + glassRight) / 2f
    val cy = (glassTop + glassBottom) / 2f
    val handleY = handleOverrideY ?: cy

    fun drawHandleAt(hx: Float, hy: Float) {
        drawRect(
            color = Color.White,
            topLeft = Offset(hx - 3.5f, hy - 14f),
            size = Size(7f, 28f)
        )
        drawRect(
            color = Color(0xFF555555),
            topLeft = Offset(hx - 3.5f, hy - 14f),
            size = Size(7f, 28f),
            style = Stroke(1.3f)
        )
    }

    when (openingType) {
        SashOpeningType.TURN_LEFT -> {
            drawLine(lineCol, Offset(glassLeft, glassTop), Offset(glassRight, handleY), 1.5f)
            drawLine(lineCol, Offset(glassRight, handleY), Offset(glassLeft, glassBottom), 1.5f)
            drawHandleAt(glassRight + sashBandPx * 0.45f, handleY)
        }
        SashOpeningType.TURN_RIGHT -> {
            drawLine(lineCol, Offset(glassRight, glassTop), Offset(glassLeft, handleY), 1.5f)
            drawLine(lineCol, Offset(glassLeft, handleY), Offset(glassRight, glassBottom), 1.5f)
            drawHandleAt(glassLeft - sashBandPx * 0.45f, handleY)
        }
        SashOpeningType.TILT_TURN_LEFT -> {
            drawLine(lineCol, Offset(glassLeft, glassTop), Offset(glassRight, handleY), 1.5f)
            drawLine(lineCol, Offset(glassRight, handleY), Offset(glassLeft, glassBottom), 1.5f)
            drawLine(lineCol, Offset(glassLeft, glassBottom), Offset(cx, glassTop), 1.5f)
            drawLine(lineCol, Offset(cx, glassTop), Offset(glassRight, glassBottom), 1.5f)
            drawHandleAt(glassRight + sashBandPx * 0.45f, handleY)
        }
        SashOpeningType.TILT_TURN_RIGHT -> {
            drawLine(lineCol, Offset(glassRight, glassTop), Offset(glassLeft, handleY), 1.5f)
            drawLine(lineCol, Offset(glassLeft, handleY), Offset(glassRight, glassBottom), 1.5f)
            drawLine(lineCol, Offset(glassLeft, glassBottom), Offset(cx, glassTop), 1.5f)
            drawLine(lineCol, Offset(cx, glassTop), Offset(glassRight, glassBottom), 1.5f)
            drawHandleAt(glassLeft - sashBandPx * 0.45f, handleY)
        }
        SashOpeningType.TRANSOM_BOTTOM -> {
            drawLine(lineCol, Offset(glassLeft, glassBottom), Offset(cx, glassTop), 1.5f)
            drawLine(lineCol, Offset(cx, glassTop), Offset(glassRight, glassBottom), 1.5f)
        }
        SashOpeningType.TRANSOM_TOP -> {
            drawLine(lineCol, Offset(glassLeft, glassTop), Offset(cx, glassBottom), 1.5f)
            drawLine(lineCol, Offset(cx, glassBottom), Offset(glassRight, glassTop), 1.5f)
        }
        SashOpeningType.SLIDING_RIGHT -> {
            val w = (glassRight - glassLeft) * 0.28f
            drawLine(lineCol, Offset(cx - w, cy + 8f), Offset(cx - w, cy), 1.8f)
            drawLine(lineCol, Offset(cx - w, cy), Offset(cx + w, cy), 1.8f)
            drawLine(lineCol, Offset(cx + w - 10f, cy - 8f), Offset(cx + w, cy), 1.8f)
            drawLine(lineCol, Offset(cx + w - 10f, cy + 8f), Offset(cx + w, cy), 1.8f)
        }
        SashOpeningType.SLIDING_LEFT -> {
            val w = (glassRight - glassLeft) * 0.28f
            drawLine(lineCol, Offset(cx + w, cy + 8f), Offset(cx + w, cy), 1.8f)
            drawLine(lineCol, Offset(cx + w, cy), Offset(cx - w, cy), 1.8f)
            drawLine(lineCol, Offset(cx - w + 10f, cy - 8f), Offset(cx - w, cy), 1.8f)
            drawLine(lineCol, Offset(cx - w + 10f, cy + 8f), Offset(cx - w, cy), 1.8f)
        }
        SashOpeningType.SHTULP_TURN_TURN,
        SashOpeningType.SHTULP_TILTTURN_L_TURN_R,
        SashOpeningType.SHTULP_TURN_L_TILTTURN_R -> {
            drawLine(Color(0xFF5A5E64), Offset(cx, glassTop), Offset(cx, glassBottom), 4f)
            drawLine(lineCol, Offset(glassLeft, glassTop), Offset(cx - 2f, cy), 1.5f)
            drawLine(lineCol, Offset(cx - 2f, cy), Offset(glassLeft, glassBottom), 1.5f)
            if (openingType == SashOpeningType.SHTULP_TILTTURN_L_TURN_R) {
                val lcx = (glassLeft + cx) / 2f
                drawLine(lineCol, Offset(glassLeft, glassBottom), Offset(lcx, glassTop), 1.5f)
                drawLine(lineCol, Offset(lcx, glassTop), Offset(cx - 2f, glassBottom), 1.5f)
            }
            drawLine(lineCol, Offset(glassRight, glassTop), Offset(cx + 2f, cy), 1.5f)
            drawLine(lineCol, Offset(cx + 2f, cy), Offset(glassRight, glassBottom), 1.5f)
            if (openingType == SashOpeningType.SHTULP_TURN_L_TILTTURN_R) {
                val rcx = (cx + glassRight) / 2f
                drawLine(lineCol, Offset(cx + 2f, glassBottom), Offset(rcx, glassTop), 1.5f)
                drawLine(lineCol, Offset(rcx, glassTop), Offset(glassRight, glassBottom), 1.5f)
            }
        }
        SashOpeningType.VERT_SLIDE_UP, SashOpeningType.VERT_SLIDE_BOTH -> {
            drawLine(Color(0xFF5A5E64), Offset(glassLeft, cy), Offset(glassRight, cy), 4f)
            val botCy = (cy + glassBottom) / 2f
            drawLine(lineCol, Offset(cx, botCy + 16f), Offset(cx, botCy - 16f), 1.8f)
            drawLine(lineCol, Offset(cx - 8f, botCy - 6f), Offset(cx, botCy - 16f), 1.8f)
            drawLine(lineCol, Offset(cx + 8f, botCy - 6f), Offset(cx, botCy - 16f), 1.8f)
            if (openingType == SashOpeningType.VERT_SLIDE_BOTH) {
                val topCy = (glassTop + cy) / 2f
                drawLine(lineCol, Offset(cx, topCy - 16f), Offset(cx, topCy + 16f), 1.8f)
                drawLine(lineCol, Offset(cx - 8f, topCy + 6f), Offset(cx, topCy + 16f), 1.8f)
                drawLine(lineCol, Offset(cx + 8f, topCy + 6f), Offset(cx, topCy + 16f), 1.8f)
            }
        }
        else -> {}
    }
}
