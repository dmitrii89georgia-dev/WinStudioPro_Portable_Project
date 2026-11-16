package ru.winstudiopro.app.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.i18n.normalizeCurrencyDisplay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ClientPdfReportMode(val titleRu: String) {
    DRAWINGS_ONLY("Чертежи"),
    DRAWINGS_WITH_DETAILS("Чертежи с подробностями"),
    COMMERCIAL_PROPOSAL("Коммерческое предложение")
}

object PdfReportGenerator {

    private const val PAGE_W = 595 // A4 72 dpi
    private const val PAGE_H = 842
    private const val MARGIN = 24f

    /**
     * Генератор клиентских PDF-отчётов в точности по эталонным PDF оригинала:
     * 1) Чертежи (сетка 2×3 = 6 ячеек на страницу, одинаковый авто-масштаб для маленьких и больших конструкций)
     * 2) Чертежи с подробностями (сетка 2×3 = 6 ячеек на страницу + колонка спецификаций справа с маркерами ●)
     * 3) Коммерческое предложение (крупные блоки с чертежом, спецификацией ●, 6-строчной таблицей расчёта и итоговой таблицей «Весь заказ»)
     */
    fun generateClientOrderPdf(
        context: Context,
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>,
        mode: ClientPdfReportMode = ClientPdfReportMode.COMMERCIAL_PROPOSAL,
        companyHeader: String = "WinStudio Pro",
        companyContacts: String = "",
        extraAccessoriesCatalog: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
        workServicesCatalog: List<WorkServiceItem> = DefaultCatalogs.defaultWorkServices(),
        hardwareBrandsCatalog: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands()
    ): File {
        val doc = PdfDocument()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 25, 25)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 8.0f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 7.2f
        }
        val specCategoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 70, 70)
            textSize = 7.0f
            textAlign = Paint.Align.RIGHT
        }
        val specValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 20)
            textSize = 7.4f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val grayBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 232, 232)
            style = Paint.Style.FILL
        }
        val cellHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 245, 245)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 110, 110)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val thinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(165, 165, 165)
            style = Paint.Style.STROKE
            strokeWidth = 0.55f
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawPageFooter(pNum: Int) {
            val fText = "Стр. $pNum"
            canvas.drawText(
                fText,
                PAGE_W - MARGIN - smallPaint.measureText(fText),
                PAGE_H - 10f,
                smallPaint
            )
        }

        fun startNewPage() {
            drawPageFooter(pageNum)
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
        }

        // Верхняя шапка документа (как в оригинальных PDF)
        val contentW = PAGE_W - MARGIN * 2
        val halfW = contentW / 2f

        // Блок компании и номера заказа
        val topBoxH = 38f
        canvas.drawRect(RectF(MARGIN, y, MARGIN + halfW, y + topBoxH), thinLinePaint)
        canvas.drawRect(RectF(MARGIN + halfW, y, PAGE_W - MARGIN, y + topBoxH), thinLinePaint)
        canvas.drawText(companyHeader.ifBlank { "WinStudio Pro" }, MARGIN + 6f, y + 13f, boldPaint)
        if (companyContacts.isNotBlank()) {
            canvas.drawText(companyContacts, MARGIN + 6f, y + 26f, smallPaint)
        }
        val ordTitle = "${order.orderNumber} (от ${order.createdDate})"
        canvas.drawText(
            ordTitle,
            PAGE_W - MARGIN - 6f - boldPaint.measureText(ordTitle),
            y + 22f,
            boldPaint
        )
        y += topBoxH

        // Таблица данных клиента
        val clientBoxH = 42f
        canvas.drawRect(RectF(MARGIN, y, MARGIN + halfW, y + clientBoxH), thinLinePaint)
        canvas.drawRect(RectF(MARGIN + halfW, y, PAGE_W - MARGIN, y + clientBoxH), thinLinePaint)

        canvas.drawText("Клиент: ${order.customerName.ifBlank { "—" }}", MARGIN + 6f, y + 12f, smallPaint)
        canvas.drawText("Адрес: ${order.address.ifBlank { "—" }}", MARGIN + 6f, y + 24f, smallPaint)
        canvas.drawText("Телефон: ${order.customerPhone.ifBlank { "—" }}", MARGIN + 6f, y + 36f, smallPaint)

        canvas.drawText("Эл. почта: ${order.customerEmail.ifBlank { "—" }}", MARGIN + halfW + 6f, y + 12f, smallPaint)
        canvas.drawText("Заметка: ${order.orderNote.ifBlank { "—" }}", MARGIN + halfW + 6f, y + 24f, smallPaint)
        y += clientBoxH + 8f

        // Заголовок отчёта по центру
        val modeTitleW = titlePaint.measureText(mode.titleRu)
        canvas.drawText(mode.titleRu, (PAGE_W - modeTitleW) / 2f, y + 10f, titlePaint)
        y += 16f

        // Вспомогательная функция отрисовки колонки спецификаций справа внутри ячейки (с маркерами ●)
        fun drawRightSpecsColumn(
            res: WindowCalculationResult,
            colLeft: Float,
            colRight: Float,
            startY: Float,
            maxBottomY: Float,
            drawNoteInside: Boolean
        ) {
            var curY = startY
            val wItem = res.windowItem
            if (drawNoteInside && wItem.note.isNotBlank()) {
                val noteLines = wItem.note.chunked(24)
                noteLines.take(2).forEach { line ->
                    if (curY + 9f < maxBottomY) {
                        canvas.drawText(line, colRight - 3f, curY + 7f, specCategoryPaint)
                        curY += 9.5f
                    }
                }
                curY += 2f
            }

            fun drawCategoryAndItems(categoryTitle: String, items: List<String>) {
                if (items.isEmpty() || curY + 18f > maxBottomY) return
                canvas.drawRect(RectF(colLeft, curY, colRight, curY + 10f), grayBarPaint)
                canvas.drawText(categoryTitle, colRight - 3f, curY + 7.5f, specCategoryPaint)
                curY += 11f
                items.forEach { itemText ->
                    if (curY + 9f <= maxBottomY) {
                        canvas.drawText("${itemText.take(22)} ●", colRight - 3f, curY + 7f, specValuePaint)
                        curY += 9.5f
                    }
                }
                curY += 1.5f
            }

            if (wItem.profileSystemId != "none") {
                drawCategoryAndItems("оконная система", listOf(res.profileSystem.name))
            }
            if (wItem.glazingId != "none") {
                drawCategoryAndItems("тип стеклопакетов", listOf(res.glazing.name))
            }
            if (wItem.colorId != "none") {
                drawCategoryAndItems("цвет", listOf(res.color.name))
            }
            if (res.costCustomPainting > 0.0) {
                drawCategoryAndItems(
                    "индивидуальная покраска",
                    listOf("${fmtMoney2(res.costCustomPainting)} ${normalizeCurrencyDisplay(order.currencySymbol)}")
                )
            }
            val hwBrand = hardwareBrandsCatalog.find { it.id == wItem.hardwareBrandId }
            if (hwBrand != null && hwBrand.id != "hw_none") {
                drawCategoryAndItems("фурнитура", listOf(hwBrand.name))
            }
            val selectedExtras = wItem.selectedExtraIds.mapNotNull { id ->
                extraAccessoriesCatalog.find { it.id == id }?.name
            }
            if (selectedExtras.isNotEmpty()) {
                drawCategoryAndItems("разное", selectedExtras)
            }
            val selectedWorks = wItem.selectedWorkIds.mapNotNull { id ->
                workServicesCatalog.find { it.id == id }?.name
            }
            if (selectedWorks.isNotEmpty()) {
                drawCategoryAndItems("работы", selectedWorks)
            }
        }

        when (mode) {
            ClientPdfReportMode.DRAWINGS_ONLY, ClientPdfReportMode.DRAWINGS_WITH_DETAILS -> {
                // Сетка 2 колонки × 3 ряда (6 конструкций на страницу, как в оригинальном PDF!)
                val colW = contentW / 2f
                val cellH = 212f
                val pairs = windowResults.chunked(2)

                pairs.forEachIndexed { pairIdx, pair ->
                    if (y + cellH > PAGE_H - 42f) {
                        startNewPage()
                    }

                    pair.forEachIndexed { colIdx, res ->
                        val globalIdx = pairIdx * 2 + colIdx + 1
                        val cellLeft = MARGIN + colIdx * colW
                        val cellRight = cellLeft + colW
                        val cellTop = y
                        val cellBot = y + cellH

                        canvas.drawRect(RectF(cellLeft, cellTop, cellRight, cellBot), borderPaint)

                        // Шапка ячейки: №1 слева | Коли-во: 1 справа
                        val hdrH = 14f
                        canvas.drawRect(RectF(cellLeft + 0.5f, cellTop + 0.5f, cellRight - 0.5f, cellTop + hdrH), cellHeaderBgPaint)
                        canvas.drawLine(cellLeft, cellTop + hdrH, cellRight, cellTop + hdrH, thinLinePaint)
                        canvas.drawText("№$globalIdx", cellLeft + 5f, cellTop + 10f, boldPaint)
                        val qtyStr = "Коли-во: ${res.windowItem.quantity}"
                        canvas.drawText(
                            qtyStr,
                            cellRight - 5f - boldPaint.measureText(qtyStr),
                            cellTop + 10f,
                            boldPaint
                        )

                        if (mode == ClientPdfReportMode.DRAWINGS_ONLY) {
                            if (res.windowItem.note.isNotBlank()) {
                                canvas.drawText(
                                    res.windowItem.note.take(36),
                                    cellLeft + 5f,
                                    cellTop + hdrH + 10f,
                                    smallPaint
                                )
                            }
                            val sketchBox = RectF(
                                cellLeft + 6f,
                                cellTop + hdrH + (if (res.windowItem.note.isNotBlank()) 13f else 4f),
                                cellRight - 6f,
                                cellBot - 4f
                            )
                            val profColorHex = if (res.windowItem.colorId == "none") 0xFFFFFFFFL else res.color.hexColor
                            drawWindowTopologyVector(
                                canvas = canvas,
                                box = sketchBox,
                                topology = res.windowItem.topology,
                                profile = res.profileSystem,
                                profileColorHex = profColorHex,
                                showClearOpeningSizes = false
                            )
                        } else {
                            // Чертежи с подробностями: слева чертёж (62% ширины ячейки), справа спецификации с ● (38%)
                            val sketchRight = cellLeft + colW * 0.62f
                            val sketchBox = RectF(
                                cellLeft + 4f,
                                cellTop + hdrH + 4f,
                                sketchRight - 2f,
                                cellBot - 4f
                            )
                            val profColorHex = if (res.windowItem.colorId == "none") 0xFFFFFFFFL else res.color.hexColor
                            drawWindowTopologyVector(
                                canvas = canvas,
                                box = sketchBox,
                                topology = res.windowItem.topology,
                                profile = res.profileSystem,
                                profileColorHex = profColorHex,
                                showClearOpeningSizes = true
                            )
                            drawRightSpecsColumn(
                                res = res,
                                colLeft = sketchRight + 2f,
                                colRight = cellRight - 3f,
                                startY = cellTop + hdrH + 3f,
                                maxBottomY = cellBot - 3f,
                                drawNoteInside = true
                            )
                        }
                    }
                    y += cellH
                }
            }

            ClientPdfReportMode.COMMERCIAL_PROPOSAL -> {
                // Коммерческое предложение: крупные блоки (по 2 на страницу) с чертежом, спецификацией и таблицей цен
                windowResults.forEachIndexed { idx, res ->
                    val wItem = res.windowItem
                    val hasCustomPaint = res.costCustomPainting > 0.0
                    val blockH = if (hasCustomPaint) 305.5f else 292f
                    if (y + blockH > PAGE_H - 42f) {
                        startNewPage()
                    }

                    val blockLeft = MARGIN
                    val blockRight = PAGE_W - MARGIN
                    val blockTop = y
                    val blockBot = y + blockH

                    canvas.drawRect(RectF(blockLeft, blockTop, blockRight, blockBot), borderPaint)

                    // Верхняя полоса изделия: №1 слева | заметка справа
                    val hdrH = 15f
                    canvas.drawRect(RectF(blockLeft + 0.5f, blockTop + 0.5f, blockRight - 0.5f, blockTop + hdrH), cellHeaderBgPaint)
                    canvas.drawLine(blockLeft, blockTop + hdrH, blockRight, blockTop + hdrH, thinLinePaint)
                    canvas.drawText("№${idx + 1}", blockLeft + 6f, blockTop + 11f, boldPaint)
                    if (wItem.note.isNotBlank()) {
                        val noteStr = wItem.note.take(55)
                        canvas.drawText(
                            noteStr,
                            blockRight - 6f - bodyPaint.measureText(noteStr),
                            blockTop + 11f,
                            bodyPaint
                        )
                    }

                    val tableRowsCount = if (hasCustomPaint) 7 else 6
                    val rowH = 13.5f
                    val tableH = tableRowsCount * rowH
                    val tableTop = blockBot - tableH
                    val upperAreaBot = tableTop - 3f

                    // Левая часть верхнего блока: векторный чертёж с цепочками размеров
                    val sketchRight = blockLeft + contentW * 0.58f
                    val sketchBox = RectF(
                        blockLeft + 8f,
                        blockTop + hdrH + 4f,
                        sketchRight - 6f,
                        upperAreaBot
                    )
                    val profColorHex = if (wItem.colorId == "none") 0xFFFFFFFFL else res.color.hexColor
                    drawWindowTopologyVector(
                        canvas = canvas,
                        box = sketchBox,
                        topology = wItem.topology,
                        profile = res.profileSystem,
                        profileColorHex = profColorHex,
                        showClearOpeningSizes = false
                    )

                    // Правая часть верхнего блока: спецификация с ●
                    drawRightSpecsColumn(
                        res = res,
                        colLeft = sketchRight + 10f,
                        colRight = blockRight - 6f,
                        startY = blockTop + hdrH + 4f,
                        maxBottomY = upperAreaBot,
                        drawNoteInside = false
                    )

                    // Таблица расчёта изделия
                    val itemDiscPct = if (res.discountPercentApplied > 0.0) res.discountPercentApplied else order.discountPercent
                    val unitBase = if (wItem.manualUnitPriceOverride != null) {
                        wItem.manualUnitPriceOverride
                    } else {
                        (if (res.rawUnitCostBeforeDiscount > 0.0) res.rawUnitCostBeforeDiscount else res.unitTotalCost) *
                            (1.0 + order.markupPercent / 100.0)
                    }
                    val unitDiscounted = unitBase * (1.0 - itemDiscPct / 100.0)
                    val totalBase = unitBase * wItem.quantity + res.costCustomPainting
                    val totalDiscounted = unitDiscounted * wItem.quantity + res.costCustomPainting
                    val cur = order.currencySymbol

                    val kpRows = buildList {
                        add("площадь изделия" to "${fmt(res.totalAreaM2)} m2")
                        add("цена" to "${fmtMoney2(unitBase)} $cur")
                        add("цена со скидкой (-${fmtPct(itemDiscPct)}%)" to "${fmtMoney2(unitDiscounted)} $cur")
                        add("количество конструкций" to "${wItem.quantity}")
                        if (hasCustomPaint) {
                            add("индивидуальная покраска профиля" to "${fmtMoney2(res.costCustomPainting)} $cur")
                        }
                        add("стоимость" to "${fmtMoney2(totalBase)} $cur")
                        add("стоимость со скидкой" to "${fmtMoney2(totalDiscounted)} $cur")
                    }

                    val midColX = blockLeft + contentW * 0.65f
                    kpRows.forEachIndexed { rIdx, (label, valStr) ->
                        val rTop = tableTop + rIdx * rowH
                        val rBot = rTop + rowH
                        if (rIdx % 2 == 0) {
                            canvas.drawRect(RectF(blockLeft + 0.5f, rTop, blockRight - 0.5f, rBot), cellHeaderBgPaint)
                        }
                        canvas.drawLine(blockLeft, rTop, blockRight, rTop, thinLinePaint)
                        canvas.drawLine(midColX, rTop, midColX, rBot, thinLinePaint)

                        val isBoldRow = rIdx == kpRows.lastIndex
                        val p = if (isBoldRow) boldPaint else bodyPaint
                        canvas.drawText(label, midColX - 6f - bodyPaint.measureText(label), rTop + 10f, bodyPaint)
                        canvas.drawText(valStr, blockRight - 6f - p.measureText(valStr), rTop + 10f, p)
                    }

                    y += blockH + 8f
                }
            }
        }

        // Итоговый блок «Весь заказ» и «Подписи» (как в эталонных PDF)
        val neededFooterH = if (mode == ClientPdfReportMode.COMMERCIAL_PROPOSAL) 150f else 85f
        if (y + neededFooterH > PAGE_H - 30f) {
            startNewPage()
        }
        y += 8f

        val totalConstructions = windowResults.sumOf { it.windowItem.quantity }
        val totalArea = windowResults.sumOf { it.totalAreaM2 * it.windowItem.quantity }

        // Заголовок «Весь заказ»
        val secTitle = "Весь заказ"
        canvas.drawText(secTitle, (PAGE_W - boldPaint.measureText(secTitle)) / 2f, y + 10f, boldPaint)
        y += 14f

        val summaryRows = mutableListOf<Pair<String, String>>()
        summaryRows.add("количество конструкций" to "$totalConstructions")
        summaryRows.add("общая квадратура конструкций" to "${fmt(totalArea)} m2")

        if (mode == ClientPdfReportMode.COMMERCIAL_PROPOSAL) {
            val cur = order.currencySymbol
            var sumRaw = 0.0
            var sumDiscounted = 0.0
            windowResults.forEach { res ->
                val wItem = res.windowItem
                val itemDiscPct = if (res.discountPercentApplied > 0.0) res.discountPercentApplied else order.discountPercent
                val unitBase = wItem.manualUnitPriceOverride
                    ?: ((if (res.rawUnitCostBeforeDiscount > 0.0) res.rawUnitCostBeforeDiscount else res.unitTotalCost) *
                        (1.0 + order.markupPercent / 100.0))
                val unitDisc = unitBase * (1.0 - itemDiscPct / 100.0)
                sumRaw += unitBase * wItem.quantity + res.costCustomPainting
                sumDiscounted += unitDisc * wItem.quantity + res.costCustomPainting
            }
            if (order.discountPercent > 0.0 && windowResults.all { it.discountPercentApplied == 0.0 }) {
                sumDiscounted = sumRaw * (1.0 - order.discountPercent / 100.0)
            }
            val discountAmount = (sumRaw - sumDiscounted).coerceAtLeast(0.0)
            val effDiscPct = if (sumRaw > 0.0) (discountAmount / sumRaw) * 100.0 else 0.0
            val vatAmount = sumDiscounted * (order.taxVatPercent / 100.0)
            val finalTotal = order.manualOrderTotalOverride ?: (sumDiscounted + vatAmount)

            summaryRows.add("общая цена" to "${fmtMoney2(sumRaw)} $cur")
            summaryRows.add("общая скидка (-${fmtPct(effDiscPct)}%)" to "-${fmtMoney2(discountAmount)} $cur")
            summaryRows.add("общая цена со скидкой" to "${fmtMoney2(sumDiscounted)} $cur")
            summaryRows.add("НДС (${fmtPct(order.taxVatPercent)}%)" to "${fmtMoney2(vatAmount)} $cur")
            summaryRows.add("ИТОГО (С НДС)" to "${fmtMoney2(finalTotal)} $cur")
        }

        val sumRowH = 14f
        val midX = MARGIN + contentW * 0.65f
        summaryRows.forEachIndexed { idx, (label, valStr) ->
            val rTop = y + idx * sumRowH
            val rBot = rTop + sumRowH
            if (idx % 2 == 0) {
                canvas.drawRect(RectF(MARGIN, rTop, PAGE_W - MARGIN, rBot), cellHeaderBgPaint)
            }
            canvas.drawRect(RectF(MARGIN, rTop, PAGE_W - MARGIN, rBot), thinLinePaint)
            canvas.drawLine(midX, rTop, midX, rBot, thinLinePaint)
            val isLast = idx == summaryRows.lastIndex && mode == ClientPdfReportMode.COMMERCIAL_PROPOSAL
            val p = if (isLast) boldPaint else bodyPaint
            canvas.drawText(label, midX - 6f - p.measureText(label), rTop + 10f, p)
            canvas.drawText(valStr, PAGE_W - MARGIN - 6f - p.measureText(valStr), rTop + 10f, p)
        }
        y += summaryRows.size * sumRowH + 14f

        // Блок «Подписи»
        val sigTitle = "Подписи"
        canvas.drawText(sigTitle, (PAGE_W - boldPaint.measureText(sigTitle)) / 2f, y + 8f, boldPaint)
        y += 12f
        val sigBoxH = 26f
        canvas.drawRect(RectF(MARGIN, y, MARGIN + halfW, y + sigBoxH), thinLinePaint)
        canvas.drawRect(RectF(MARGIN + halfW, y, PAGE_W - MARGIN, y + sigBoxH), thinLinePaint)
        canvas.drawText("Исполнитель:", MARGIN + 6f, y + 16f, bodyPaint)
        canvas.drawText("Заказчик:", MARGIN + halfW + 6f, y + 16f, bodyPaint)

        drawPageFooter(pageNum)
        doc.finishPage(page)

        val prefix = when (mode) {
            ClientPdfReportMode.DRAWINGS_ONLY -> "Drawings"
            ClientPdfReportMode.DRAWINGS_WITH_DETAILS -> "Drawings_Details"
            ClientPdfReportMode.COMMERCIAL_PROPOSAL -> "KP"
        }
        val outFile = createFreshReportFile(context, prefix, order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * 2. Производственная ведомость стеклопакетов и сэндвич-панелей для всего заказа.
     */
    fun generateGlazingSpecPdf(
        context: Context,
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>
    ): File {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 37, 41)
            textSize = 9f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 153, 204)
            style = Paint.Style.FILL
        }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(242, 248, 252)
            style = Paint.Style.FILL
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawTableHeader() {
            canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 22f, headerBgPaint)
            canvas.drawText("№", MARGIN + 6f, y + 15f, headerPaint)
            canvas.drawText("Изделие / Проём", MARGIN + 28f, y + 15f, headerPaint)
            canvas.drawText("Формула / Тип заполнения", MARGIN + 185f, y + 15f, headerPaint)
            canvas.drawText("Ширина × Высота (мм)", MARGIN + 335f, y + 15f, headerPaint)
            canvas.drawText("Кол-во", MARGIN + 455f, y + 15f, headerPaint)
            canvas.drawText("S общ., м²", MARGIN + 498f, y + 15f, headerPaint)
            y += 22f
        }

        canvas.drawText(
            "ЗАКАЗ СТЕКЛОПАКЕТОВ И СЭНДВИЧ-ПАНЕЛЕЙ — ${order.orderNumber}",
            MARGIN,
            y + 14f,
            titlePaint
        )
        y += 22f
        canvas.drawText(
            "Клиент: ${order.customerName.ifBlank { "—" }}   |   Дата: ${order.createdDate}",
            MARGIN,
            y,
            rowPaint
        )
        y += 14f

        drawTableHeader()

        val allItems = windowResults.flatMap { it.glazingSpecs }
        allItems.forEachIndexed { idx, item ->
            if (y + 20f > PAGE_H - 70f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
                drawTableHeader()
            }
            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 20f, zebraPaint)
            }
            canvas.drawText("${idx + 1}", MARGIN + 6f, y + 14f, rowPaint)
            canvas.drawText("${item.windowName} (${item.cellLabel})".take(28), MARGIN + 28f, y + 14f, rowPaint)
            canvas.drawText(item.formula.take(26), MARGIN + 185f, y + 14f, rowPaint)
            canvas.drawText("${item.widthMm} × ${item.heightMm} мм", MARGIN + 335f, y + 14f, boldPaint)
            canvas.drawText("${item.quantity} шт.", MARGIN + 455f, y + 14f, rowPaint)
            canvas.drawText(fmt(item.totalAreaM2), MARGIN + 498f, y + 14f, rowPaint)
            y += 20f
        }

        y += 16f
        val totalPacks = allItems.sumOf { it.quantity }
        val totalArea = allItems.sumOf { it.totalAreaM2 }
        canvas.drawText(
            "ИТОГО ЗАПОЛНЕНИЙ: $totalPacks шт.   |   ОБЩАЯ ПЛОЩАДЬ: ${fmt(totalArea)} м²",
            MARGIN,
            y + 12f,
            boldPaint
        )

        doc.finishPage(page)
        val outFile = createFreshReportFile(context, "Glazing", order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * 3. Заказ фурнитуры и комплектующих (PDF).
     */
    fun generateHardwareSpecPdf(
        context: Context,
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>
    ): File {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 37, 41)
            textSize = 9.5f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 119, 153)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        canvas.drawText(
            "ВЕДОМОСТЬ ФУРНИТУРЫ И КОМПЛЕКТУЮЩИХ — ${order.orderNumber}",
            MARGIN,
            y + 14f,
            titlePaint
        )
        y += 26f

        windowResults.forEachIndexed { idx, res ->
            if (y + 90f > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }
            val w = res.windowItem
            val hwBrandName = DefaultCatalogs.defaultHardwareBrands()
                .find { it.id == w.hardwareBrandId }?.name ?: "Базовая"
            val mosqName = DefaultCatalogs.defaultMosquitoSystems()
                .find { it.id == w.mosquitoSystemId }?.name ?: "Стандарт"

            canvas.drawText(
                "Изделие #${idx + 1}: ${w.name} (${w.topology.totalWidthMm.roundToInt()}×${w.topology.totalHeightMm.roundToInt()} мм) × ${w.quantity} шт. | Марка: $hwBrandName",
                MARGIN,
                y,
                boldPaint
            )
            y += 14f

            if (res.hardwareSpecs.isEmpty()) {
                canvas.drawText("   Глухое изделие (без фурнитурных комплектов створок)", MARGIN, y, rowPaint)
                y += 13f
            } else {
                res.hardwareSpecs.forEach { hw ->
                    val kind = if (hw.isDoor) "ДВЕРЬ" else "ОКНО"
                    val msq = if (hw.hasMosquitoNet) " + Москитная сетка ($mosqName)" else ""
                    canvas.drawText(
                        "   • [$kind] ${hw.cellLabel}: ${hw.openingType.titleRu} (фальц ${hw.falzWidthMm}×${hw.falzHeightMm} мм) — ${hw.quantity} компл.$msq",
                        MARGIN,
                        y,
                        rowPaint
                    )
                    y += 13f
                }
            }
            canvas.drawText(
                "   Угловые сухари и соединители импоста: ${res.cornerConnectorsCount * w.quantity} шт.   |   Уплотнитель EPDM: ${fmt(res.rubberPerimeterM * w.quantity)} п.м.",
                MARGIN,
                y,
                rowPaint
            )
            y += 18f
        }

        doc.finishPage(page)
        val outFile = createFreshReportFile(context, "Hardware", order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * 4. Карта линейного 1D-раскроя профилей + Чертежи с подробностями для цеха.
     * Скомпонована компактно и визуально разделена по блокам:
     * 1) Чертежи с подробностями (вверху отчёта);
     * 2) Блоки раскроя по типам профиля в строгом порядке:
     *    РАМА -> ИМПОСТ -> СТВОРКА -> ДВЕРЬ -> ШТАПИК (для штапика выводится только количество хлыстов без полос раскроя).
     */
    fun generateCuttingAndProcurementPdf(
        context: Context,
        order: CustomerOrder,
        allocations: List<CutBarAllocation>,
        procurement: List<ProfileProcurementSummary>,
        windowResults: List<WindowCalculationResult> = emptyList()
    ): File {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 102, 140)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val blockHeaderFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 238, 248)
            style = Paint.Style.FILL
        }
        val blockHeaderStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 160, 195)
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }
        val blockTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 45, 80)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 37, 41)
            textSize = 8.6f
        }
        val boldSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 25, 30)
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 55, 65)
            textSize = 8.0f
        }
        val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(195, 228, 248)
            style = Paint.Style.FILL
        }
        val remnantFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(215, 245, 222)
            style = Paint.Style.FILL
        }
        val wasteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 222, 222)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 80, 105)
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }
        val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 165, 180)
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun ensureSpace(neededH: Float) {
            if (y + neededH > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        canvas.drawText(
            "ЗАДАНИЕ В ЦЕХ: ЧЕРТЕЖИ И РАСКРОЙ ПРОФИЛЯ — Заказ № ${order.orderNumber}",
            MARGIN,
            y + 12f,
            titlePaint
        )
        y += 20f

        // 1. ЧЕРТЕЖИ С ПОДРОБНОСТЯМИ (вверху отчёта, компактно по 2 в ряд)
        if (windowResults.isNotEmpty()) {
            val contentW = PAGE_W - 2 * MARGIN
            val colGap = 10f
            val colW = (contentW - colGap) / 2f
            val rowCardH = 195f

            windowResults.chunked(2).forEachIndexed { rowIdx, pair ->
                ensureSpace(rowCardH + 8f)
                val rowTop = y
                pair.forEachIndexed { colIdx, res ->
                    val winNum = rowIdx * 2 + colIdx + 1
                    val cellLeft = MARGIN + colIdx * (colW + colGap)
                    val cellRect = RectF(cellLeft, rowTop, cellLeft + colW, rowTop + rowCardH)
                    canvas.drawRect(cellRect, gridBorderPaint)

                    canvas.drawText(
                        "Конструкция №$winNum: ${res.windowItem.name} (${res.windowItem.quantity} шт.)",
                        cellLeft + 5f,
                        rowTop + 11f,
                        boldSmallPaint
                    )

                    val drawW = colW * 0.54f
                    val drawRect = RectF(
                        cellLeft + 3f,
                        rowTop + 15f,
                        cellLeft + 3f + drawW,
                        rowTop + rowCardH - 4f
                    )
                    drawWindowTopologyVector(
                        canvas = canvas,
                        box = drawRect,
                        topology = res.windowItem.topology,
                        profile = res.profileSystem,
                        profileColorHex = res.color.hexColor,
                        showClearOpeningSizes = true
                    )

                    // Правая колонка с характеристиками, стеклопакетами и фурнитурой
                    val specX = cellLeft + drawW + 6f
                    val specMaxW = cellLeft + colW - specX - 4f
                    var sy = rowTop + 23f

                    fun drawSpecLine(txt: String, isBold: Boolean = false) {
                        val p = if (isBold) boldSmallPaint else smallPaint
                        val words = txt.split(" ")
                        var curLine = ""
                        for (w in words) {
                            val candidate = if (curLine.isEmpty()) w else "$curLine $w"
                            if (p.measureText(candidate) <= specMaxW || curLine.isEmpty()) {
                                curLine = candidate
                            } else {
                                if (sy < rowTop + rowCardH - 4f) {
                                    canvas.drawText(curLine, specX, sy, p)
                                    sy += 9.5f
                                }
                                curLine = w
                            }
                        }
                        if (curLine.isNotEmpty() && sy < rowTop + rowCardH - 4f) {
                            canvas.drawText(curLine, specX, sy, p)
                            sy += 9.5f
                        }
                    }

                    drawSpecLine("Профиль: ${res.profileSystem.name}", isBold = true)
                    val activeSht = res.profileSystem.shtapikVariants.find { it.id == res.windowItem.selectedShtapikId }
                        ?: res.profileSystem.shtapikVariants.firstOrNull()
                    if (activeSht != null) {
                        drawSpecLine("Штапик: ${activeSht.name}")
                    }
                    drawSpecLine("Цвет: ${res.color.name}")
                    drawSpecLine("Габарит: ${res.windowItem.topology.totalWidthMm.roundToInt()}×${res.windowItem.topology.totalHeightMm.roundToInt()} мм")
                    sy += 2f
                    drawSpecLine("Заполнение:", isBold = true)
                    res.glazingSpecs.forEachIndexed { gIdx, gl ->
                        drawSpecLine("${gIdx + 1}) ${gl.widthMm}×${gl.heightMm} мм (${gl.quantity} шт.) — ${gl.formula}")
                    }
                    if (res.hardwareSpecs.isNotEmpty()) {
                        sy += 2f
                        drawSpecLine("Фурнитура:", isBold = true)
                        res.hardwareSpecs.take(4).forEach { hw ->
                            drawSpecLine("• ${hw.cellLabel}: ${hw.openingType.titleRu} (${hw.quantity} компл.)")
                        }
                    }
                }
                y += rowCardH + 8f
            }
        }

        // 2. ВИЗУАЛЬНО РАЗДЕЛЁННЫЕ БЛОКИ РАСКРОЯ ПО РОЛЯМ:
        // РАМА -> ИМПОСТ -> СТВОРКА -> ДВЕРЬ -> ШТАПИК
        val orderedRoles = listOf(
            ProfileRole.FRAME,
            ProfileRole.IMPOST,
            ProfileRole.SASH,
            ProfileRole.DOOR_SASH,
            ProfileRole.SHTAPIK
        )

        orderedRoles.forEach { role ->
            val roleProcurements = procurement.filter { it.role == role }
            val roleAllocations = allocations.filter { it.role == role }
            if (roleProcurements.isEmpty() && roleAllocations.isEmpty()) return@forEach

            roleProcurements.forEach { sum ->
                val isShtapik = role == ProfileRole.SHTAPIK
                val minBlockH = if (isShtapik) 26f else 54f
                ensureSpace(minBlockH)

                // Шапка блока профиля
                val headerRect = RectF(MARGIN, y, PAGE_W - MARGIN, y + 20f)
                canvas.drawRect(headerRect, blockHeaderFillPaint)
                canvas.drawRect(headerRect, blockHeaderStrokePaint)

                val totalBarsUsed = sum.newFullBarsToBuyCount + sum.usedRemnantBarsCount
                val headerText = if (isShtapik) {
                    "${role.titleRu.uppercase()} (${sum.profileSystemName} • ${sum.colorName}) — Хлыстов ${sum.stockBarLengthMm} мм: $totalBarsUsed шт. (Новых: ${sum.newFullBarsToBuyCount}, со склада: ${sum.usedRemnantBarsCount}) | Всего: ${fmt(sum.totalNetLengthM)} п.м."
                } else {
                    "${role.titleRu.uppercase()} (${sum.profileSystemName} • ${sum.colorName}) — Хлыстов ${sum.stockBarLengthMm} мм: $totalBarsUsed шт. (Новых: ${sum.newFullBarsToBuyCount}, со склада: ${sum.usedRemnantBarsCount}) | Дет.: ${sum.totalCutPiecesCount} (${fmt(sum.totalNetLengthM)} п.м.)"
                }
                canvas.drawText(headerText, MARGIN + 6f, y + 13.5f, blockTitlePaint)
                y += 24f

                // Для ШТАПИКА полосы раскроя не рисуем (только количество хлыстов в шапке)
                if (!isShtapik) {
                    val sysRoleAllocs = roleAllocations.filter {
                        it.profileSystemName == sum.profileSystemName && it.colorName == sum.colorName
                    }
                    sysRoleAllocs.forEach { alloc ->
                        ensureSpace(34f)
                        val sourceTag = if (alloc.isFromRemnantStock) "Остаток ${alloc.sourceLengthMm} мм" else "Хлыст ${alloc.sourceLengthMm} мм"
                        val remTag = if (alloc.isUsableBusinessRemnant) "На склад: ${alloc.remnantLengthMm} мм" else "Обрезь: ${alloc.remnantLengthMm} мм"
                        canvas.drawText(
                            "Хлыст #${alloc.barNumber} ($sourceTag) -> $remTag",
                            MARGIN + 2f,
                            y + 8.5f,
                            smallPaint
                        )
                        y += 11f

                        val barLeft = MARGIN
                        val barWidth = PAGE_W - 2 * MARGIN
                        val barTop = y
                        val barHeight = 18f
                        val totalLen = alloc.sourceLengthMm.toFloat().coerceAtLeast(1f)

                        var curX = barLeft
                        alloc.cuts.forEach { piece ->
                            val wPx = (piece.cutLengthMm / totalLen) * barWidth
                            val rect = RectF(curX, barTop, curX + wPx, barTop + barHeight)
                            canvas.drawRect(rect, barFillPaint)
                            canvas.drawRect(rect, strokePaint)

                            // Только размер и номер в скобках без градусов: напр. 1500 (1) или 1422 (1,1)
                            val labelWithTag = "${piece.cutLengthMm} (${piece.shortTag})"
                            val shortLabel = "${piece.cutLengthMm}"
                            if (wPx >= boldSmallPaint.measureText(labelWithTag) + 4f) {
                                canvas.drawText(labelWithTag, curX + 2.5f, barTop + 12.2f, boldSmallPaint)
                            } else if (wPx >= smallPaint.measureText(shortLabel) + 3f) {
                                canvas.drawText(shortLabel, curX + 1.5f, barTop + 12.2f, smallPaint)
                            }
                            curX += wPx
                        }

                        if (curX < barLeft + barWidth) {
                            val remRect = RectF(curX, barTop, barLeft + barWidth, barTop + barHeight)
                            canvas.drawRect(remRect, if (alloc.isUsableBusinessRemnant) remnantFillPaint else wasteFillPaint)
                            canvas.drawRect(remRect, strokePaint)
                            if (remRect.width() > 34f) {
                                canvas.drawText("Ост: ${alloc.remnantLengthMm}", curX + 3f, barTop + 12.2f, smallPaint)
                            }
                        }

                        y += barHeight + 5f
                    }
                    y += 4f
                } else {
                    y += 4f
                }
            }
        }

        doc.finishPage(page)
        val outFile = createFreshReportFile(context, "Workshop_Cutting", order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * Точная векторная отрисовка 2D-чертежа конструкции для PDF:
     * - Любая конструкция (и форточка 500×500, и витраж 2000×2700) автоматически масштабируется под размер ячейки;
     * - Толщины профилей ограничены в экранных/печатных пунктах (pt), чтобы не превращаться ни в волоски, ни в толстые бруски;
     * - Поддерживает заливку профиля выбранным цветом (profileColorHex) и вывод чистовых размеров проёма (Ш×В) внутри проёмов;
     * - Слева рисуются цепочки вертикальных размеров (общая высота + разбивка по импостам с чёрными точками •);
     * - Снизу рисуются цепочки горизонтальных размеров (разбивка по импостам + общая ширина с чёрными точками •);
     * - В глухих стеклопакетах рисуется аккуратный крестик «+» по центру (как в эталонных PDF).
     */
    private fun drawWindowTopologyVector(
        canvas: Canvas,
        box: RectF,
        topology: WindowBlockTopology,
        profile: ProfileSystemSpec,
        profileColorHex: Long = 0xFFFFFFFFL,
        showClearOpeningSizes: Boolean = false
    ) {
        val totalW = topology.totalWidthMm.coerceAtLeast(100f)
        val totalH = topology.totalHeightMm.coerceAtLeast(100f)

        val xCoords = topology.xGridCoords
        val yCoords = topology.yGridCoords
        val hasMultiX = xCoords.size > 2
        val hasMultiY = yCoords.size > 2

        val padLeft = if (hasMultiY) 32f else 20f
        val padBottom = if (hasMultiX) 30f else 18f
        val padTop = 6f
        val padRight = 8f

        val availW = (box.width() - padLeft - padRight).coerceAtLeast(36f)
        val availH = (box.height() - padTop - padBottom).coerceAtLeast(36f)
        val scale = min(availW / totalW, availH / totalH)

        val drawW = totalW * scale
        val drawH = totalH * scale
        val originX = box.left + padLeft + (availW - drawW) / 2f
        val originY = box.top + padTop + (availH - drawH) / 2f

        val rawColorInt = profileColorHex.toInt()
        val isDarkProfile = (Color.red(rawColorInt) * 0.299 + Color.green(rawColorInt) * 0.587 + Color.blue(rawColorInt) * 0.114) < 120.0

        val frameFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rawColorInt
            style = Paint.Style.FILL
        }
        val strokeDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDarkProfile) Color.rgb(15, 18, 22) else Color.rgb(45, 50, 55)
            style = Paint.Style.STROKE
            strokeWidth = 0.85f
        }
        val strokeInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDarkProfile) Color.rgb(210, 215, 222) else Color.rgb(95, 100, 108)
            style = Paint.Style.STROKE
            strokeWidth = 0.55f
        }
        val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(212, 224, 238)
            style = Paint.Style.FILL
        }
        val sandwichFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(236, 239, 243)
            style = Paint.Style.FILL
        }
        val openLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 60, 65)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        val dimLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 40, 40)
            style = Paint.Style.STROKE
            strokeWidth = 0.65f
        }
        val dimDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 25, 25)
            style = Paint.Style.FILL
        }
        val dimTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 15, 15)
            textSize = 7.2f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val openingSizeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 45, 70)
            textSize = 6.2f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val openingSizeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 255, 255, 255)
            style = Paint.Style.FILL
        }

        // Толщины профилей в пунктах PDF (одинаково аккуратные и на форточке 500×500, и на витраже 2000×2700!)
        val framePts = (profile.frameWidthMm * scale).coerceIn(3.8f, 7.2f)
        val impostPts = (profile.impostWidthMm * scale).coerceIn(3.6f, 7.0f)
        val sashPts = (profile.sashWidthMm * scale).coerceIn(3.8f, 7.0f)
        val doorSashPts = max(sashPts * 1.45f, (profile.doorSashWidthMm * scale).coerceIn(5.8f, 10.5f))
        val overlapPts = (profile.sashOverlapMm * scale * 0.6f).coerceIn(1.2f, 2.5f)

        fun drawFixedCrosshair(rect: RectF) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val arm = min(rect.width(), rect.height()) * 0.12f
            if (arm >= 2.5f) {
                canvas.drawLine(cx - arm, cy, cx + arm, cy, openLinePaint)
                canvas.drawLine(cx, cy - arm, cx, cy + arm, openLinePaint)
            }
        }

        fun drawClearOpeningTextPdf(rect: RectF, frame: FrameBlockNode, op: OpeningCellNode) {
            if (!showClearOpeningSizes) return
            val leftDed = if (abs(op.leftMm - frame.leftMm) <= 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
            val rightDed = if (abs(op.rightMm - frame.rightMm) <= 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
            val topDed = if (abs(op.topMm - frame.topMm) <= 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
            val botDed = if (abs(op.bottomMm - frame.bottomMm) <= 2f) profile.frameWidthMm else profile.impostWidthMm / 2f

            val clearW = (op.widthMm - leftDed - rightDed).coerceAtLeast(10f).roundToInt()
            val clearH = (op.heightMm - topDed - botDed).coerceAtLeast(10f).roundToInt()
            val label = "${clearW}×${clearH}"
            val tw = openingSizeTextPaint.measureText(label)
            if (rect.width() >= tw + 4f && rect.height() >= 14f) {
                val tx = rect.centerX()
                val ty = (rect.bottom - 3.5f).coerceAtLeast(rect.centerY() + 2f)
                val bgRect = RectF(tx - tw / 2f - 1.5f, ty - 6f, tx + tw / 2f + 1.5f, ty + 1.5f)
                canvas.drawRoundRect(bgRect, 1.5f, 1.5f, openingSizeBgPaint)
                canvas.drawText(label, tx, ty, openingSizeTextPaint)
            }
        }

        fun drawHandleVector(x: Float, y: Float, isDoor: Boolean, isLeftHanded: Boolean) {
            val dir = if (isLeftHanded) -1f else 1f
            canvas.drawRect(RectF(x - 1.2f, y - 3.2f, x + 1.2f, y + 3.2f), frameFill)
            canvas.drawRect(RectF(x - 1.2f, y - 3.2f, x + 1.2f, y + 3.2f), strokeDark)
            if (isDoor) {
                canvas.drawLine(x, y - 1f, x + dir * 5.5f, y - 1f, strokeDark)
            } else {
                canvas.drawLine(x, y, x, y + 5.5f, strokeDark)
            }
        }

        topology.frames.forEach { frame ->
            val fl = originX + frame.leftMm * scale
            val ft = originY + frame.topMm * scale
            val fr = originX + frame.rightMm * scale
            val fb = originY + frame.bottomMm * scale
            val outerRect = RectF(fl, ft, fr, fb)

            // 1. Внешняя рама + угловые запилы 45°
            canvas.drawRect(outerRect, frameFill)
            canvas.drawRect(outerRect, strokeDark)
            val innerFrameRect = RectF(fl + framePts, ft + framePts, fr - framePts, fb - framePts)
            if (innerFrameRect.width() > 4f && innerFrameRect.height() > 4f) {
                canvas.drawRect(innerFrameRect, strokeInner)
                canvas.drawLine(fl, ft, innerFrameRect.left, innerFrameRect.top, strokeInner)
                canvas.drawLine(fr, ft, innerFrameRect.right, innerFrameRect.top, strokeInner)
                canvas.drawLine(fl, fb, innerFrameRect.left, innerFrameRect.bottom, strokeInner)
                canvas.drawLine(fr, fb, innerFrameRect.right, innerFrameRect.bottom, strokeInner)
            }

            fun calcClearRectPts(op: OpeningCellNode): RectF {
                val l = if (abs(op.leftMm - frame.leftMm) <= 2f) fl + framePts else originX + op.leftMm * scale + impostPts / 2f
                val r = if (abs(op.rightMm - frame.rightMm) <= 2f) fr - framePts else originX + op.rightMm * scale - impostPts / 2f
                val t = if (abs(op.topMm - frame.topMm) <= 2f) ft + framePts else originY + op.topMm * scale + impostPts / 2f
                val b = if (abs(op.bottomMm - frame.bottomMm) <= 2f) fb - framePts else originY + op.bottomMm * scale - impostPts / 2f
                return RectF(l, t, r.coerceAtLeast(l + 2f), b.coerceAtLeast(t + 2f))
            }

            // 2. Импосты рамы
            frame.imposts.forEach { imp ->
                if (imp.direction == ImpostDirection.VERTICAL) {
                    val cx = originX + imp.startX * scale
                    val topY = if (abs(imp.startY - frame.topMm) <= 2f) ft + framePts else originY + imp.startY * scale + impostPts / 2f
                    val botY = if (abs(imp.endY - frame.bottomMm) <= 2f) fb - framePts else originY + imp.endY * scale - impostPts / 2f
                    val r = RectF(cx - impostPts / 2f, topY, cx + impostPts / 2f, botY)
                    canvas.drawRect(r, frameFill)
                    canvas.drawRect(r, strokeDark)
                } else {
                    val cy = originY + imp.startY * scale
                    val leftX = if (abs(imp.startX - frame.leftMm) <= 2f) fl + framePts else originX + imp.startX * scale + impostPts / 2f
                    val rightX = if (abs(imp.endX - frame.rightMm) <= 2f) fr - framePts else originX + imp.endX * scale - impostPts / 2f
                    val r = RectF(leftX, cy - impostPts / 2f, rightX, cy + impostPts / 2f)
                    canvas.drawRect(r, frameFill)
                    canvas.drawRect(r, strokeDark)
                }
            }

            // 3. Глухие проёмы (со знаком «+» в центре стекла)
            frame.openings.filter { !it.properties.openingType.hasSashProfile }.forEach { op ->
                val gRect = calcClearRectPts(op)
                canvas.drawRect(gRect, if (op.properties.isSandwichFilling) sandwichFill else glassFill)
                canvas.drawRect(gRect, strokeDark)
                if (!op.properties.isSandwichFilling) {
                    drawFixedCrosshair(gRect)
                }
                drawClearOpeningTextPdf(gRect, frame, op)
            }

            // 4. Створки и широкие ДВЕРИ поверх рамы и импостов
            frame.openings.filter { it.properties.openingType.hasSashProfile }.forEach { op ->
                val p = op.properties
                val clearRect = calcClearRectPts(op)
                val sashW = if (p.isDoorSash) doorSashPts else sashPts

                val sLeft = clearRect.left - overlapPts
                val sTop = clearRect.top - overlapPts
                val sRight = clearRect.right + overlapPts
                val sBottom = clearRect.bottom + overlapPts
                val sashRect = RectF(sLeft, sTop, sRight, sBottom)

                canvas.drawRect(sashRect, frameFill)
                canvas.drawRect(sashRect, strokeDark)

                val gLeft = sLeft + sashW
                val gTop = sTop + sashW
                val gRight = sRight - sashW
                val gBottom = sBottom - sashW

                // Угловые запилы 45° створки/двери
                if (gRight > gLeft + 2f && gBottom > gTop + 2f) {
                    canvas.drawLine(sLeft, sTop, gLeft, gTop, strokeInner)
                    canvas.drawLine(sRight, sTop, gRight, gTop, strokeInner)
                    canvas.drawLine(sLeft, sBottom, gLeft, gBottom, strokeInner)
                    canvas.drawLine(sRight, sBottom, gRight, gBottom, strokeInner)

                    val glassRect = RectF(gLeft, gTop, gRight, gBottom)
                    if (p.hasHorizontalSashImpost) {
                        val impostYFromTopMm = (op.bottomMm - p.sashImpostHeightFromBottomMm)
                            .coerceIn(op.topMm + 120f, op.bottomMm - 120f)
                        val impostCenterY = originY + impostYFromTopMm * scale
                        val halfImp = impostPts * 0.48f

                        val topGlass = RectF(gLeft, gTop, gRight, (impostCenterY - halfImp).coerceAtLeast(gTop + 1f))
                        val botGlass = RectF(gLeft, (impostCenterY + halfImp).coerceAtMost(gBottom - 1f), gRight, gBottom)

                        canvas.drawRect(topGlass, if (p.isSandwichFilling) sandwichFill else glassFill)
                        canvas.drawRect(topGlass, strokeDark)

                        val botIsSandwich = p.bottomPartIsSandwich || p.isSandwichFilling
                        canvas.drawRect(botGlass, if (botIsSandwich) sandwichFill else glassFill)
                        canvas.drawRect(botGlass, strokeDark)

                        val impRect = RectF(gLeft, impostCenterY - halfImp, gRight, impostCenterY + halfImp)
                        canvas.drawRect(impRect, frameFill)
                        canvas.drawRect(impRect, strokeDark)
                    } else {
                        canvas.drawRect(glassRect, if (p.isSandwichFilling) sandwichFill else glassFill)
                        canvas.drawRect(glassRect, strokeDark)
                    }

                    // Линии открывания и ручка
                    val cx = glassRect.centerX()
                    val cy = glassRect.centerY()
                    when (p.openingType) {
                        SashOpeningType.TURN_LEFT, SashOpeningType.TILT_TURN_LEFT -> {
                            canvas.drawLine(glassRect.left, glassRect.top, glassRect.right, cy, openLinePaint)
                            canvas.drawLine(glassRect.right, cy, glassRect.left, glassRect.bottom, openLinePaint)
                            drawHandleVector(sRight - sashW / 2f, cy, p.isDoorSash, isLeftHanded = true)
                        }
                        SashOpeningType.TURN_RIGHT, SashOpeningType.TILT_TURN_RIGHT -> {
                            canvas.drawLine(glassRect.right, glassRect.top, glassRect.left, cy, openLinePaint)
                            canvas.drawLine(glassRect.left, cy, glassRect.right, glassRect.bottom, openLinePaint)
                            drawHandleVector(sLeft + sashW / 2f, cy, p.isDoorSash, isLeftHanded = false)
                        }
                        SashOpeningType.TRANSOM_BOTTOM -> {
                            canvas.drawLine(glassRect.left, glassRect.bottom, cx, glassRect.top, openLinePaint)
                            canvas.drawLine(cx, glassRect.top, glassRect.right, glassRect.bottom, openLinePaint)
                        }
                        SashOpeningType.TRANSOM_TOP -> {
                            canvas.drawLine(glassRect.left, glassRect.top, cx, glassRect.bottom, openLinePaint)
                            canvas.drawLine(cx, glassRect.bottom, glassRect.right, glassRect.top, openLinePaint)
                        }
                        else -> {}
                    }
                    if (p.openingType == SashOpeningType.TILT_TURN_LEFT || p.openingType == SashOpeningType.TILT_TURN_RIGHT) {
                        canvas.drawLine(glassRect.left, glassRect.bottom, cx, glassRect.top, openLinePaint)
                        canvas.drawLine(cx, glassRect.top, glassRect.right, glassRect.bottom, openLinePaint)
                    }
                    drawClearOpeningTextPdf(glassRect, frame, op)
                }
            }
        }

        // 5. Цепочки размерных линий СЛЕВА (вертикальные) и СНИЗУ (горизонтальные) с точками • как в оригинальных PDF
        val dotR = 1.4f

        // Вертикальные размеры слева
        if (hasMultiY) {
            val innerChainX = originX - 8f
            val outerChainX = originX - 20f
            for (i in 0 until yCoords.size - 1) {
                val y1 = originY + yCoords[i] * scale
                val y2 = originY + yCoords[i + 1] * scale
                val segMm = (yCoords[i + 1] - yCoords[i]).roundToInt()
                canvas.drawLine(innerChainX, y1, innerChainX, y2, dimLinePaint)
                canvas.drawLine(innerChainX - 2f, y1, originX - 1f, y1, dimLinePaint)
                canvas.drawLine(innerChainX - 2f, y2, originX - 1f, y2, dimLinePaint)
                canvas.drawCircle(innerChainX, y1, dotR, dimDotPaint)
                canvas.drawCircle(innerChainX, y2, dotR, dimDotPaint)
                canvas.save()
                val midY = (y1 + y2) / 2f
                canvas.rotate(-90f, innerChainX - 2.5f, midY)
                canvas.drawText("$segMm", innerChainX - 2.5f, midY, dimTextPaint)
                canvas.restore()
            }
            canvas.drawLine(outerChainX, originY, outerChainX, originY + drawH, dimLinePaint)
            canvas.drawLine(outerChainX - 2f, originY, originX - 1f, originY, dimLinePaint)
            canvas.drawLine(outerChainX - 2f, originY + drawH, originX - 1f, originY + drawH, dimLinePaint)
            canvas.drawCircle(outerChainX, originY, dotR, dimDotPaint)
            canvas.drawCircle(outerChainX, originY + drawH, dotR, dimDotPaint)
            canvas.save()
            val midTotalY = originY + drawH / 2f
            canvas.rotate(-90f, outerChainX - 2.5f, midTotalY)
            canvas.drawText("${totalH.roundToInt()}", outerChainX - 2.5f, midTotalY, dimTextPaint)
            canvas.restore()
        } else {
            val outerChainX = originX - 9f
            canvas.drawLine(outerChainX, originY, outerChainX, originY + drawH, dimLinePaint)
            canvas.drawLine(outerChainX - 2f, originY, originX - 1f, originY, dimLinePaint)
            canvas.drawLine(outerChainX - 2f, originY + drawH, originX - 1f, originY + drawH, dimLinePaint)
            canvas.drawCircle(outerChainX, originY, dotR, dimDotPaint)
            canvas.drawCircle(outerChainX, originY + drawH, dotR, dimDotPaint)
            canvas.save()
            val midTotalY = originY + drawH / 2f
            canvas.rotate(-90f, outerChainX - 2.5f, midTotalY)
            canvas.drawText("${totalH.roundToInt()}", outerChainX - 2.5f, midTotalY, dimTextPaint)
            canvas.restore()
        }

        // Горизонтальные размеры снизу
        if (hasMultiX) {
            val innerChainY = originY + drawH + 9f
            val outerChainY = originY + drawH + 20f
            for (i in 0 until xCoords.size - 1) {
                val x1 = originX + xCoords[i] * scale
                val x2 = originX + xCoords[i + 1] * scale
                val segMm = (xCoords[i + 1] - xCoords[i]).roundToInt()
                canvas.drawLine(x1, innerChainY, x2, innerChainY, dimLinePaint)
                canvas.drawLine(x1, originY + drawH + 1f, x1, innerChainY + 2f, dimLinePaint)
                canvas.drawLine(x2, originY + drawH + 1f, x2, innerChainY + 2f, dimLinePaint)
                canvas.drawCircle(x1, innerChainY, dotR, dimDotPaint)
                canvas.drawCircle(x2, innerChainY, dotR, dimDotPaint)
                canvas.drawText("$segMm", (x1 + x2) / 2f, innerChainY - 1.8f, dimTextPaint)
            }
            canvas.drawLine(originX, outerChainY, originX + drawW, outerChainY, dimLinePaint)
            canvas.drawLine(originX, originY + drawH + 1f, originX, outerChainY + 2f, dimLinePaint)
            canvas.drawLine(originX + drawW, originY + drawH + 1f, originX + drawW, outerChainY + 2f, dimLinePaint)
            canvas.drawCircle(originX, outerChainY, dotR, dimDotPaint)
            canvas.drawCircle(originX + drawW, outerChainY, dotR, dimDotPaint)
            canvas.drawText("${totalW.roundToInt()}", originX + drawW / 2f, outerChainY - 1.8f, dimTextPaint)
        } else {
            val outerChainY = originY + drawH + 9f
            canvas.drawLine(originX, outerChainY, originX + drawW, outerChainY, dimLinePaint)
            canvas.drawLine(originX, originY + drawH + 1f, originX, outerChainY + 2f, dimLinePaint)
            canvas.drawLine(originX + drawW, originY + drawH + 1f, originX + drawW, outerChainY + 2f, dimLinePaint)
            canvas.drawCircle(originX, outerChainY, dotR, dimDotPaint)
            canvas.drawCircle(originX + drawW, outerChainY, dotR, dimDotPaint)
            canvas.drawText("${totalW.roundToInt()}", originX + drawW / 2f, outerChainY - 1.8f, dimTextPaint)
        }
    }

    private fun getOutputDirectory(context: Context): File {
        val dir = File(context.filesDir, "pdf_reports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Создает свежий файл отчета с уникальным таймстемпом и очищает старые версии отчетов,
     * чтобы внешние PDF-ридеры Android никогда не открывали устаревший кэш из памяти.
     */
    private fun createFreshReportFile(context: Context, prefix: String, orderNumber: String): File {
        val dir = getOutputDirectory(context)
        val safeNum = orderNumber.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_").ifBlank { "order" }
        try {
            dir.listFiles()?.filter {
                it.name.startsWith("${prefix}_${safeNum}") || (System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000)
            }?.forEach { oldFile ->
                oldFile.delete()
            }
        } catch (_: Exception) {}

        val timeTag = SimpleDateFormat("HHmmss", Locale.US).format(Date()) + "_" + (System.currentTimeMillis() % 1000)
        return File(dir, "${prefix}_${safeNum}_${timeTag}.pdf")
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    private fun fmtPct(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
    private fun fmtMoney2(v: Double): String = String.format(Locale.US, "%.2f", v)
}
