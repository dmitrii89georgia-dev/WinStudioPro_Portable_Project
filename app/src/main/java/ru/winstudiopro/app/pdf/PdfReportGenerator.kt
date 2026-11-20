package ru.winstudiopro.app.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import ru.winstudiopro.app.domain.calculator.CornerConnectorOrderItem
import ru.winstudiopro.app.domain.calculator.GlazingOrderItem
import ru.winstudiopro.app.domain.calculator.HardwareOrderItem
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.ProfileSupplierOrderLine
import ru.winstudiopro.app.domain.calculator.RubberOrderItem
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
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
        companyAddress: String = "",
        extraAccessoriesCatalog: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
        workServicesCatalog: List<WorkServiceItem> = DefaultCatalogs.defaultWorkServices(),
        hardwareBrandsCatalog: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands(),
        assemblyPricingCatalog: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
        orderExtraServicesCatalog: List<OrderExtraServiceItem> = DefaultCatalogs.defaultOrderExtraServices(),
        finSchemeSettings: FinSchemeSettings = FinSchemeSettings()
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
        val topBoxH = if (companyAddress.isNotBlank() && companyContacts.isNotBlank()) 48f else 38f
        canvas.drawRect(RectF(MARGIN, y, MARGIN + halfW, y + topBoxH), thinLinePaint)
        canvas.drawRect(RectF(MARGIN + halfW, y, PAGE_W - MARGIN, y + topBoxH), thinLinePaint)
        canvas.drawText(companyHeader.ifBlank { "WinStudio Pro" }, MARGIN + 6f, y + 13f, boldPaint)
        var compY = y + 25f
        if (companyContacts.isNotBlank()) {
            canvas.drawText("Тел: $companyContacts", MARGIN + 6f, compY, smallPaint)
            compY += 11f
        }
        if (companyAddress.isNotBlank()) {
            canvas.drawText("Адрес: $companyAddress".take(42), MARGIN + 6f, compY, smallPaint)
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
                drawCategoryAndItems("монтаж", selectedWorks)
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
                // Лаконичное Коммерческое предложение: сетка 2 конструкции в ряд (без себестоимости, раскроя и лишних заголовков)
                val finSummary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
                    order = order,
                    windowResults = windowResults,
                    assemblyPricingCatalog = assemblyPricingCatalog,
                    orderExtraServicesCatalog = orderExtraServicesCatalog,
                    finSchemeSettings = finSchemeSettings
                )
                val cur = normalizeCurrencyDisplay(order.currencySymbol)
                val colGap = 8f
                val colW = (contentW - colGap) / 2f
                val cellH = 294f
                val rowGap = 8f
                val pairs = windowResults.chunked(2)

                pairs.forEachIndexed { pairIdx, pair ->
                    if (y + cellH > PAGE_H - 36f) {
                        startNewPage()
                    }

                    pair.forEachIndexed { colIdx, res ->
                        val globalIdx = pairIdx * 2 + colIdx + 1
                        val wItem = res.windowItem
                        val cellLeft = MARGIN + colIdx * (colW + colGap)
                        val cellRight = cellLeft + colW
                        val cellTop = y
                        val cellBot = y + cellH

                        canvas.drawRect(RectF(cellLeft, cellTop, cellRight, cellBot), borderPaint)

                        val cellModel = buildCompactProposalCellModel(
                            indexOneBased = globalIdx,
                            order = order,
                            res = res,
                            finSummary = finSummary,
                            extraAccessoriesCatalog = extraAccessoriesCatalog,
                            hardwareBrandsCatalog = hardwareBrandsCatalog
                        )
                        // Шапка ячейки: только порядковый номер (№1, №2...) слева, габариты и кол-во справа
                        val hdrH = 15f
                        canvas.drawRect(
                            RectF(cellLeft + 0.5f, cellTop + 0.5f, cellRight - 0.5f, cellTop + hdrH),
                            cellHeaderBgPaint
                        )
                        canvas.drawLine(cellLeft, cellTop + hdrH, cellRight, cellTop + hdrH, thinLinePaint)
                        canvas.drawText(cellModel.headerNumber, cellLeft + 5f, cellTop + 10.5f, boldPaint)

                        canvas.drawText(
                            cellModel.headerDimsQty,
                            cellRight - 5f - smallPaint.measureText(cellModel.headerDimsQty),
                            cellTop + 10.5f,
                            smallPaint
                        )

                        // Миниатюра с общими и промежуточными размерами секций
                        val sketchTop = cellTop + hdrH + 4f
                        val sketchBot = sketchTop + 140f
                        val sketchBox = RectF(
                            cellLeft + 6f,
                            sketchTop,
                            cellRight - 6f,
                            sketchBot
                        )
                        val profColorHex = if (wItem.colorId == "none") 0xFFFFFFFFL else res.color.hexColor
                        drawWindowTopologyVector(
                            canvas = canvas,
                            box = sketchBox,
                            topology = wItem.topology,
                            profile = res.profileSystem,
                            profileColorHex = profColorHex,
                            showClearOpeningSizes = true
                        )

                        var infoY = sketchBot + 4f
                        canvas.drawLine(cellLeft, infoY, cellRight, infoY, thinLinePaint)
                        infoY += 10f

                        // Информация о применённых материалах (сразу после миниатюры)
                        cellModel.materialLines.take(3).forEach { matLine ->
                            canvas.drawText(matLine.take(40), cellLeft + 6f, infoY, bodyPaint)
                            infoY += 11f
                        }
                        cellModel.extraLines.forEach { exLine ->
                            canvas.drawText(exLine.take(40), cellLeft + 6f, infoY, smallPaint)
                            infoY += 10.5f
                        }

                        // Блок итоговых цифр «под ключ» в нижней части ячейки
                        val pRowH = 14.5f
                        val pTableTop = cellBot - cellModel.priceRows.size * pRowH
                        val rowsList = cellModel.priceRows

                        rowsList.forEachIndexed { rIdx, (lbl, vStr) ->
                            val rTop = pTableTop + rIdx * pRowH
                            val isLast = rIdx == rowsList.lastIndex
                            if (isLast) {
                                canvas.drawRect(RectF(cellLeft + 0.5f, rTop, cellRight - 0.5f, cellBot - 0.5f), grayBarPaint)
                            }
                            canvas.drawLine(cellLeft, rTop, cellRight, rTop, thinLinePaint)
                            val lblP = if (isLast) boldPaint else smallPaint
                            val valP = if (isLast) boldPaint else bodyPaint
                            canvas.drawText(lbl, cellLeft + 6f, rTop + 10.5f, lblP)
                            canvas.drawText(vStr, cellRight - 6f - valP.measureText(vStr), rTop + 10.5f, valP)
                        }
                    }
                    y += cellH + rowGap
                }
            }
        }

        // Итоговый блок «Весь заказ» и «Подписи»
        val neededFooterH = if (mode == ClientPdfReportMode.COMMERCIAL_PROPOSAL) 185f else 85f
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
        if (mode == ClientPdfReportMode.COMMERCIAL_PROPOSAL) {
            summaryRows.addAll(
                buildCompactProposalSummaryRows(
                    order = order,
                    windowResults = windowResults,
                    assemblyPricingCatalog = assemblyPricingCatalog,
                    orderExtraServicesCatalog = orderExtraServicesCatalog,
                    finSchemeSettings = finSchemeSettings
                )
            )
        } else {
            summaryRows.add("Всего изделий (шт)" to "$totalConstructions")
            summaryRows.add("Общая площадь (м²)" to "${fmt(totalArea)} м²")
        }

        val sumRowH = 15f
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
            canvas.drawText(label, midX - 6f - p.measureText(label), rTop + 10.5f, p)
            canvas.drawText(valStr, PAGE_W - MARGIN - 6f - p.measureText(valStr), rTop + 10.5f, p)
        }
        y += summaryRows.size * sumRowH + 10f

        if (mode == ClientPdfReportMode.COMMERCIAL_PROPOSAL) {
            val payNote = "Ориентировочные условия оплаты: аванс 70%, окончательный расчёт после монтажа (30%)"
            canvas.drawText(payNote, MARGIN, y + 8f, smallPaint)
            y += 18f
        } else {
            y += 4f
        }

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
     * 2. Заказ стеклопакетов и сэндвич-панелей (таблица с датой генерации и выбранными/отредактированными позициями).
     */
    fun generateGlazingSpecPdf(
        context: Context,
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>,
        selectedItems: List<GlazingOrderItem>? = null
    ): File {
        val doc = PdfDocument()
        val generatedDateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 12.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 37, 41)
            textSize = 8.4f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 72, 118)
            style = Paint.Style.FILL
        }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 247, 251)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(155, 165, 178)
            style = Paint.Style.STROKE
            strokeWidth = 0.65f
        }

        val items = (selectedItems ?: OrderProcurementHelper.buildGlazingOrderItems(windowResults))
            .filter { it.isSelected }

        val tableRight = PAGE_W - MARGIN
        val colX = floatArrayOf(
            MARGIN,          // 0: №
            MARGIN + 26f,    // 1: № окна / изделие
            MARGIN + 132f,   // 2: № проёма
            MARGIN + 186f,   // 3: Формула
            MARGIN + 340f,   // 4: Ширина × Высота
            MARGIN + 444f,   // 5: Кол-во
            MARGIN + 486f,   // 6: Площадь
            tableRight       // 7: правая граница
        )

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawTableHeader() {
            val hdrH = 22f
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, headerBgPaint)
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, borderPaint)
            for (i in 1 until colX.lastIndex) {
                canvas.drawLine(colX[i], y, colX[i], y + hdrH, borderPaint)
            }
            val headers = listOf("№", "Окно", "Проём", "Формула", "Ширина × Высота", "Кол.", "S, м²")
            headers.forEachIndexed { idx, h ->
                canvas.drawText(h, colX[idx] + 4f, y + 14.5f, headerPaint)
            }
            y += hdrH
        }

        canvas.drawText(
            "Заказ стеклопакетов для заказа № ${order.orderNumber} от $generatedDateStr",
            MARGIN,
            y + 13f,
            titlePaint
        )
        y += 21f
        val subInfo = listOfNotNull(
            order.customerName.takeIf { it.isNotBlank() }?.let { "Клиент: $it" },
            order.address.takeIf { it.isNotBlank() }?.let { "Адрес: $it" }
        ).joinToString("   |   ")
        if (subInfo.isNotBlank()) {
            canvas.drawText(subInfo.take(96), MARGIN, y, rowPaint)
            y += 14f
        } else {
            y += 4f
        }

        drawTableHeader()

        items.forEachIndexed { idx, item ->
            val rowH = 20f
            if (y + rowH > PAGE_H - 52f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
                drawTableHeader()
            }
            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, y, tableRight, y + rowH, zebraPaint)
            }
            canvas.drawRect(MARGIN, y, tableRight, y + rowH, borderPaint)
            for (i in 1 until colX.lastIndex) {
                canvas.drawLine(colX[i], y, colX[i], y + rowH, borderPaint)
            }
            canvas.drawText("${idx + 1}", colX[0] + 4f, y + 13.5f, rowPaint)
            canvas.drawText("№${item.windowIndex} ${item.windowName}".take(18), colX[1] + 4f, y + 13.5f, rowPaint)
            canvas.drawText(item.openingNumber.take(8), colX[2] + 4f, y + 13.5f, rowPaint)
            canvas.drawText(item.formula.take(26), colX[3] + 4f, y + 13.5f, rowPaint)
            canvas.drawText("${item.widthMm} × ${item.heightMm} мм", colX[4] + 4f, y + 13.5f, boldPaint)
            canvas.drawText("${item.quantity} шт", colX[5] + 4f, y + 13.5f, rowPaint)
            canvas.drawText(fmt(item.totalAreaM2), colX[6] + 4f, y + 13.5f, rowPaint)
            y += rowH
        }

        y += 14f
        val totalPacks = items.sumOf { it.quantity }
        val totalArea = items.sumOf { it.totalAreaM2 }
        canvas.drawText(
            "ИТОГО СТЕКЛОПАКЕТОВ: $totalPacks шт.   |   ОБЩАЯ ПЛОЩАДЬ: ${fmt(totalArea)} м²",
            MARGIN,
            y + 10f,
            boldPaint
        )

        doc.finishPage(page)
        val outFile = createFreshReportFile(context, "Glazing", order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * 3. Заказ фурнитуры и комплектующих (PDF-таблица выбранных позиций с датой генерации).
     */
    fun generateHardwareSpecPdf(
        context: Context,
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>,
        selectedItems: List<HardwareOrderItem>? = null,
        hardwareBrandsCatalog: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands()
    ): File {
        val doc = PdfDocument()
        val generatedDateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 12.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 37, 41)
            textSize = 8.2f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 8.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 95, 18)
            style = Paint.Style.FILL
        }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(252, 247, 241)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(165, 165, 165)
            style = Paint.Style.STROKE
            strokeWidth = 0.65f
        }

        val items = (selectedItems ?: OrderProcurementHelper.buildHardwareOrderItems(windowResults, hardwareBrandsCatalog))
            .filter { it.isSelected }

        val tableRight = PAGE_W - MARGIN
        val colX = floatArrayOf(
            MARGIN,          // 0: №
            MARGIN + 24f,    // 1: № констр.
            MARGIN + 114f,   // 2: Тип открывания
            MARGIN + 270f,   // 3: Размер (мм)
            MARGIN + 362f,   // 4: Сторона (лев/прав)
            MARGIN + 428f,   // 5: Кол-во
            MARGIN + 468f,   // 6: Марка / Прим.
            tableRight       // 7
        )

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawTableHeader() {
            val hdrH = 22f
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, headerBgPaint)
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, borderPaint)
            for (i in 1 until colX.lastIndex) {
                canvas.drawLine(colX[i], y, colX[i], y + hdrH, borderPaint)
            }
            val headers = listOf("№", "Констр.", "Тип открывания", "Размер (мм)", "Сторона", "Кол.", "Марка / Прим.")
            headers.forEachIndexed { idx, h ->
                canvas.drawText(h, colX[idx] + 4f, y + 14.5f, headerPaint)
            }
            y += hdrH
        }

        canvas.drawText(
            "Заказ фурнитуры для заказа № ${order.orderNumber} от $generatedDateStr",
            MARGIN,
            y + 13f,
            titlePaint
        )
        y += 21f
        val subInfo = listOfNotNull(
            order.customerName.takeIf { it.isNotBlank() }?.let { "Клиент: $it" },
            order.address.takeIf { it.isNotBlank() }?.let { "Адрес: $it" }
        ).joinToString("   |   ")
        if (subInfo.isNotBlank()) {
            canvas.drawText(subInfo.take(96), MARGIN, y, rowPaint)
            y += 14f
        } else {
            y += 4f
        }

        drawTableHeader()

        items.forEachIndexed { idx, item ->
            val rowH = 20f
            if (y + rowH > PAGE_H - 52f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
                drawTableHeader()
            }
            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, y, tableRight, y + rowH, zebraPaint)
            }
            canvas.drawRect(MARGIN, y, tableRight, y + rowH, borderPaint)
            for (i in 1 until colX.lastIndex) {
                canvas.drawLine(colX[i], y, colX[i], y + rowH, borderPaint)
            }
            val kindPrefix = if (item.isDoor) "[Дверь] " else ""
            val noteText = listOfNotNull(
                item.brandName.takeIf { it.isNotBlank() },
                item.note.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            canvas.drawText("${idx + 1}", colX[0] + 4f, y + 13.5f, rowPaint)
            canvas.drawText("№${item.windowIndex} (${item.cellLabel})".take(15), colX[1] + 4f, y + 13.5f, rowPaint)
            canvas.drawText("$kindPrefix${item.openingType.titleRu}".take(26), colX[2] + 4f, y + 13.5f, rowPaint)
            canvas.drawText(item.sizeText, colX[3] + 4f, y + 13.5f, boldPaint)
            canvas.drawText(item.sideLabel.take(12), colX[4] + 4f, y + 13.5f, rowPaint)
            canvas.drawText("${item.quantity} к.", colX[5] + 4f, y + 13.5f, rowPaint)
            canvas.drawText(noteText.take(14), colX[6] + 4f, y + 13.5f, rowPaint)
            y += rowH
        }

        y += 14f
        val totalSets = items.sumOf { it.quantity }
        canvas.drawText(
            "ИТОГО КОМПЛЕКТОВ ФУРНИТУРЫ: $totalSets компл. (${items.size} поз.)",
            MARGIN,
            y + 10f,
            boldPaint
        )

        doc.finishPage(page)
        val outFile = createFreshReportFile(context, "Hardware", order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * 3б. Заказ профиля поставщику (выбранные профили + опциональные закладные/сухари и 4 вида резины).
     */
    fun generateProfileOrderPdf(
        context: Context,
        order: CustomerOrder,
        profileLines: List<ProfileSupplierOrderLine>,
        connectorLines: List<CornerConnectorOrderItem>,
        rubberLines: List<RubberOrderItem>
    ): File {
        val doc = PdfDocument()
        val generatedDateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 20)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 37, 41)
            textSize = 8.6f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 76)
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(44, 62, 80)
            style = Paint.Style.FILL
        }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 247, 250)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(155, 165, 178)
            style = Paint.Style.STROKE
            strokeWidth = 0.65f
        }

        val activeProfiles = profileLines.filter { it.isSelected && it.barsCount > 0 }
        val activeConnectors = connectorLines.filter { it.isSelected && it.setsCount > 0 }
        val activeRubbers = rubberLines.filter { it.isSelected && (it.boxesCount > 0 || it.lengthMeters > 0.0) }

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
            "Заказ профиля — Заказ № ${order.orderNumber} от $generatedDateStr",
            MARGIN,
            y + 13f,
            titlePaint
        )
        y += 22f

        val systemsSummary = activeProfiles.map { it.profileSystemName }.distinct().joinToString(", ").ifBlank { "—" }
        val colorsSummary = activeProfiles.map { it.colorName }.distinct().joinToString(", ").ifBlank { "—" }
        canvas.drawText("Профильная система: $systemsSummary", MARGIN, y, boldPaint)
        y += 13f
        canvas.drawText("Цвет профиля: $colorsSummary", MARGIN, y, boldPaint)
        y += 16f

        val tableRight = PAGE_W - MARGIN

        if (activeProfiles.isNotEmpty()) {
            val colX = floatArrayOf(
                MARGIN,
                MARGIN + 26f,
                MARGIN + 150f,
                MARGIN + 318f,
                MARGIN + 392f,
                MARGIN + 462f,
                tableRight
            )

            fun drawProfileHeader() {
                val hdrH = 21f
                canvas.drawRect(MARGIN, y, tableRight, y + hdrH, headerBgPaint)
                canvas.drawRect(MARGIN, y, tableRight, y + hdrH, borderPaint)
                for (i in 1 until colX.lastIndex) canvas.drawLine(colX[i], y, colX[i], y + hdrH, borderPaint)
                listOf("№", "Профиль", "Система • Цвет", "Хлыст, мм", "Кол-во", "Длина, м").forEachIndexed { idx, h ->
                    canvas.drawText(h, colX[idx] + 4f, y + 14f, headerPaint)
                }
                y += hdrH
            }

            drawProfileHeader()
            activeProfiles.forEachIndexed { idx, line ->
                val rowH = 20f
                if (y + rowH > PAGE_H - MARGIN) {
                    ensureSpace(rowH + 25f)
                    drawProfileHeader()
                }
                if (idx % 2 == 1) canvas.drawRect(MARGIN, y, tableRight, y + rowH, zebraPaint)
                canvas.drawRect(MARGIN, y, tableRight, y + rowH, borderPaint)
                for (i in 1 until colX.lastIndex) canvas.drawLine(colX[i], y, colX[i], y + rowH, borderPaint)
                canvas.drawText("${idx + 1}", colX[0] + 4f, y + 13.5f, rowPaint)
                canvas.drawText(line.role.titleRu.replaceFirstChar { it.uppercase() }, colX[1] + 4f, y + 13.5f, boldPaint)
                canvas.drawText("${line.profileSystemName} • ${line.colorName}".take(30), colX[2] + 4f, y + 13.5f, rowPaint)
                canvas.drawText("${line.stockBarLengthMm} мм", colX[3] + 4f, y + 13.5f, rowPaint)
                canvas.drawText("${line.barsCount} шт.", colX[4] + 4f, y + 13.5f, boldPaint)
                canvas.drawText("${fmt(line.totalLengthMeters)} м", colX[5] + 4f, y + 13.5f, boldPaint)
                y += rowH
            }
            y += 10f
            val totalBars = activeProfiles.sumOf { it.barsCount }
            val totalProfileM = activeProfiles.sumOf { it.totalLengthMeters }
            canvas.drawText(
                "ИТОГО ПРОФИЛЯ: $totalBars хлыст. (${fmt(totalProfileM)} п.м.)",
                MARGIN,
                y + 8f,
                boldPaint
            )
            y += 22f
        }

        if (activeConnectors.isNotEmpty()) {
            ensureSpace(70f)
            canvas.drawText("Закладные (угловые сухари):", MARGIN, y + 10f, sectionPaint)
            y += 16f

            val colX = floatArrayOf(MARGIN, MARGIN + 26f, MARGIN + 310f, MARGIN + 426f, tableRight)
            val hdrH = 20f
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, headerBgPaint)
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, borderPaint)
            for (i in 1 until colX.lastIndex) canvas.drawLine(colX[i], y, colX[i], y + hdrH, borderPaint)
            listOf("№", "Наименование", "Комплектов", "Всего углов").forEachIndexed { idx, h ->
                canvas.drawText(h, colX[idx] + 4f, y + 13.5f, headerPaint)
            }
            y += hdrH

            activeConnectors.forEachIndexed { idx, c ->
                val rowH = 19f
                ensureSpace(rowH)
                if (idx % 2 == 1) canvas.drawRect(MARGIN, y, tableRight, y + rowH, zebraPaint)
                canvas.drawRect(MARGIN, y, tableRight, y + rowH, borderPaint)
                for (i in 1 until colX.lastIndex) canvas.drawLine(colX[i], y, colX[i], y + rowH, borderPaint)
                canvas.drawText("${idx + 1}", colX[0] + 4f, y + 13f, rowPaint)
                canvas.drawText(c.type.titleRu, colX[1] + 4f, y + 13f, rowPaint)
                canvas.drawText("${c.setsCount} компл.", colX[2] + 4f, y + 13f, boldPaint)
                canvas.drawText("${c.totalCornersCount} шт.", colX[3] + 4f, y + 13f, rowPaint)
                y += rowH
            }
            y += 16f
        }

        if (activeRubbers.isNotEmpty()) {
            ensureSpace(85f)
            canvas.drawText("Резина (уплотнитель):", MARGIN, y + 10f, sectionPaint)
            y += 16f

            val colX = floatArrayOf(
                MARGIN,
                MARGIN + 26f,
                MARGIN + 290f,
                MARGIN + 365f,
                MARGIN + 445f,
                tableRight
            )
            val hdrH = 20f
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, headerBgPaint)
            canvas.drawRect(MARGIN, y, tableRight, y + hdrH, borderPaint)
            for (i in 1 until colX.lastIndex) canvas.drawLine(colX[i], y, colX[i], y + hdrH, borderPaint)
            listOf("№", "Вид уплотнителя", "Коробок", "В коробке", "Итого (п.м.)").forEachIndexed { idx, h ->
                canvas.drawText(h, colX[idx] + 4f, y + 13.5f, headerPaint)
            }
            y += hdrH

            activeRubbers.forEachIndexed { idx, r ->
                val rowH = 19f
                ensureSpace(rowH)
                if (idx % 2 == 1) canvas.drawRect(MARGIN, y, tableRight, y + rowH, zebraPaint)
                canvas.drawRect(MARGIN, y, tableRight, y + rowH, borderPaint)
                for (i in 1 until colX.lastIndex) canvas.drawLine(colX[i], y, colX[i], y + rowH, borderPaint)
                canvas.drawText("${idx + 1}", colX[0] + 4f, y + 13f, rowPaint)
                canvas.drawText(r.displayTitle.take(42), colX[1] + 4f, y + 13f, rowPaint)
                canvas.drawText(if (r.boxesCount > 0) "${r.boxesCount} кор." else "—", colX[2] + 4f, y + 13f, boldPaint)
                canvas.drawText(
                    if (r.boxesCount > 0 && r.metersPerBox > 0.0) "${fmt(r.metersPerBox)} м" else "—",
                    colX[3] + 4f,
                    y + 13f,
                    rowPaint
                )
                canvas.drawText("${fmt(r.orderedMeters)} п.м.", colX[4] + 4f, y + 13f, boldPaint)
                y += rowH
            }
            y += 10f
            val totalBoxes = activeRubbers.sumOf { it.boxesCount }
            val totalRubberM = activeRubbers.sumOf { it.orderedMeters }
            val boxesLabel = if (totalBoxes > 0) "$totalBoxes кор. • " else ""
            canvas.drawText(
                "ИТОГО РЕЗИНЫ: $boxesLabel${fmt(totalRubberM)} п.м.",
                MARGIN,
                y + 8f,
                boldPaint
            )
            y += 18f
        }

        doc.finishPage(page)
        val outFile = createFreshReportFile(context, "Profile_Order", order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * Формирует сгруппированные по ролям (Р, И, С, Д, Д.обр.) размеры заготовок для блока «Размеры» в «Задание в цех».
     */
    internal fun buildWorkshopCutSizesRows(res: WindowCalculationResult): List<Pair<String, List<String>>> {
        val cuts = res.profileCuts
        if (cuts.isEmpty()) return emptyList()

        fun formatDimensionGroup(items: List<ProfileCutPiece>, verticalFirst: Boolean): List<String> {
            if (items.isEmpty()) return emptyList()
            val ordered = if (verticalFirst) {
                items.sortedWith(
                    compareBy<ProfileCutPiece>(
                        { if (it.positionLabel.contains("верт", ignoreCase = true)) 0 else 1 },
                        { -it.cutLengthMm }
                    )
                )
            } else {
                items
            }
            val byLen = linkedMapOf<Int, Int>()
            ordered.forEach { piece ->
                byLen[piece.cutLengthMm] = (byLen[piece.cutLengthMm] ?: 0) + piece.quantity
            }
            return byLen.entries.map { (len, qty) -> "$len - ${qty}шт" }
        }

        val frameLines = formatDimensionGroup(cuts.filter { it.role == ProfileRole.FRAME }, verticalFirst = true)
        val impostLines = formatDimensionGroup(cuts.filter { it.role == ProfileRole.IMPOST }, verticalFirst = false)
        val sashLines = formatDimensionGroup(cuts.filter { it.role == ProfileRole.SASH }, verticalFirst = true)
        val doorLines = formatDimensionGroup(
            cuts.filter { it.role == ProfileRole.DOOR_SASH && !it.positionLabel.contains("Обр.дверь") },
            verticalFirst = true
        )
        val revDoorLines = formatDimensionGroup(
            cuts.filter { it.role == ProfileRole.DOOR_SASH && it.positionLabel.contains("Обр.дверь") },
            verticalFirst = true
        )

        return buildList {
            if (frameLines.isNotEmpty()) add("Р:" to frameLines)
            if (impostLines.isNotEmpty()) add("И:" to impostLines)
            if (sashLines.isNotEmpty()) add("С:" to sashLines)
            if (doorLines.isNotEmpty()) add("Д:" to doorLines)
            if (revDoorLines.isNotEmpty()) add("Д.обр.:" to revDoorLines)
        }
    }

    /**
     * Формирует нумерованный слева направо список размеров фурнитуры для «Задание в цех»:
     * - Двери (обычные и обратные): только высота + сторона (напр. 2020 правая)
     * - Поворотно-откидные створки: ширина/высота + сторона (напр. 500/1856 левая)
     * - Поворотные / раздвижные створки: высота + сторона
     * - Откидные (фрамуги): ширина
     */
    internal fun buildWorkshopHardwareLines(res: WindowCalculationResult): List<String> {
        val profile = res.profileSystem
        val frames = res.windowItem.topology.frames.sortedBy { it.leftMm }
        val activeOpenings = frames.flatMap { frame ->
            frame.openings
                .sortedWith(
                    compareBy<OpeningCellNode>(
                        { (it.leftMm / 5f).roundToInt() },
                        { (it.topMm / 5f).roundToInt() }
                    )
                )
                .map { frame to it }
        }.filter { (_, op) ->
            op.properties.openingType.hasSashProfile && op.properties.openingType != SashOpeningType.BLIND_SASH
        }

        return activeOpenings.mapIndexed { idx, (frame, op) ->
            val props = op.properties
            val leftDed = if (abs(op.leftMm - frame.leftMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
            val rightDed = if (abs(op.rightMm - frame.rightMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
            val topDed = if (abs(op.topMm - frame.topMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f
            val botDed = if (abs(op.bottomMm - frame.bottomMm) < 2f) profile.frameWidthMm else profile.impostWidthMm / 2f

            val lightW = max(100f, op.widthMm - leftDed - rightDed)
            val lightH = max(100f, op.heightMm - topDed - botDed)

            val isDoubleShtulp = props.openingType == SashOpeningType.SHTULP_TURN_TURN ||
                props.openingType == SashOpeningType.SHTULP_TILTTURN_L_TURN_R ||
                props.openingType == SashOpeningType.SHTULP_TURN_L_TILTTURN_R
            val isVertDouble = props.openingType == SashOpeningType.VERT_SLIDE_UP ||
                props.openingType == SashOpeningType.VERT_SLIDE_BOTH

            val singleLightW = if (isDoubleShtulp) lightW / 2f else lightW
            val singleLightH = if (isVertDouble) lightH / 2f else lightH
            val overlapMm = when {
                props.isReverseDoorSash -> profile.revDoorOverlapMm
                props.isDoorSash -> profile.doorOverlapMm
                else -> profile.sashOverlapMm
            }
            val sashOuterW = (singleLightW + 2f * overlapMm).roundToInt()
            val sashOuterH = (singleLightH + 2f * overlapMm).roundToInt()
            val falzW = (sashOuterW - 16).coerceAtLeast(250)
            val falzH = (sashOuterH - 16).coerceAtLeast(250)

            val isAnyDoor = props.isDoorSash || props.isReverseDoorSash
            val dimStr = when {
                isAnyDoor -> "$falzH"
                props.openingType == SashOpeningType.TILT_TURN_LEFT ||
                    props.openingType == SashOpeningType.TILT_TURN_RIGHT ||
                    props.openingType == SashOpeningType.SHTULP_TILTTURN_L_TURN_R ||
                    props.openingType == SashOpeningType.SHTULP_TURN_L_TILTTURN_R -> "$falzW/$falzH"
                props.openingType == SashOpeningType.TRANSOM_BOTTOM ||
                    props.openingType == SashOpeningType.TRANSOM_TOP -> "$falzW"
                else -> "$falzH"
            }

            val sideStr = when (props.openingType) {
                SashOpeningType.TURN_LEFT,
                SashOpeningType.TILT_TURN_LEFT,
                SashOpeningType.SLIDING_LEFT -> "левая"
                SashOpeningType.TURN_RIGHT,
                SashOpeningType.TILT_TURN_RIGHT,
                SashOpeningType.SLIDING_RIGHT -> "правая"
                SashOpeningType.SHTULP_TURN_TURN,
                SashOpeningType.SHTULP_TILTTURN_L_TURN_R,
                SashOpeningType.SHTULP_TURN_L_TILTTURN_R -> "лев.+прав."
                else -> ""
            }

            if (sideStr.isNotEmpty()) "${idx + 1}) $dimStr $sideStr" else "${idx + 1}) $dimStr"
        }
    }

    /**
     * 4а. Чертежи для монтажа (PDF, сетка 2×4 = до 8 конструкций на лист):
     * - Чертежи с размерами и чистыми проёмами
     * - В правом блоке: Профиль, Штапик, Цвет, Заполнение (без Габарита и без Фурнитуры)
     * - Внизу карточки: блок комментария, если он заполнен у конструкции
     */
    fun generateInstallationDrawingsPdf(
        context: Context,
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>
    ): File {
        val doc = PdfDocument()
        val pageMargin = 22f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 20)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boldSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 15, 15)
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 7.1f
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(240, 240, 240)
            style = Paint.Style.FILL
        }
        val noteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(246, 246, 246)
            style = Paint.Style.FILL
        }
        val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = pageMargin

        fun startNewPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = pageMargin
        }

        canvas.drawText(
            "ЧЕРТЕЖИ ДЛЯ МОНТАЖА — ${order.orderNumber} (${order.createdDate})",
            pageMargin,
            y + 11f,
            titlePaint
        )
        y += 16f

        val clientSummary = listOfNotNull(
            order.customerName.takeIf { it.isNotBlank() }?.let { "Клиент: $it" },
            order.address.takeIf { it.isNotBlank() }?.let { "Адрес: $it" },
            order.customerPhone.takeIf { it.isNotBlank() }?.let { "Тел.: $it" }
        ).joinToString("  •  ")
        if (clientSummary.isNotBlank()) {
            canvas.drawText(clientSummary.take(105), pageMargin, y + 7f, smallPaint)
            y += 11f
        } else {
            y += 3f
        }

        val contentW = PAGE_W - 2 * pageMargin
        val colGap = 8f
        val colW = (contentW - colGap) / 2f
        val rowGap = 5f
        val rowCardH = 186f

        windowResults.chunked(2).forEachIndexed { rowIdx, pair ->
            if (y + rowCardH > PAGE_H - pageMargin) {
                startNewPage()
            }
            val rowTop = y
            pair.forEachIndexed { colIdx, res ->
                val winNum = rowIdx * 2 + colIdx + 1
                val cellLeft = pageMargin + colIdx * (colW + colGap)
                val cellRight = cellLeft + colW
                val cellBot = rowTop + rowCardH
                canvas.drawRect(RectF(cellLeft, rowTop, cellRight, cellBot), gridBorderPaint)

                // Шапка ячейки
                val hdrH = 13f
                canvas.drawRect(RectF(cellLeft, rowTop, cellRight, rowTop + hdrH), headerBgPaint)
                canvas.drawLine(cellLeft, rowTop + hdrH, cellRight, rowTop + hdrH, gridBorderPaint)
                canvas.drawText(
                    "Конструкция №$winNum: ${res.windowItem.name} (${res.windowItem.quantity} шт.)",
                    cellLeft + 5f,
                    rowTop + 9.5f,
                    boldSmallPaint
                )

                val noteText = res.windowItem.note.trim()
                val noteBoxH = if (noteText.isNotEmpty()) 20f else 0f
                val bodyBottom = cellBot - noteBoxH

                if (noteBoxH > 0f) {
                    val noteRect = RectF(cellLeft, bodyBottom, cellRight, cellBot)
                    canvas.drawRect(noteRect, noteBgPaint)
                    canvas.drawLine(cellLeft, bodyBottom, cellRight, bodyBottom, gridBorderPaint)
                    canvas.drawText("Комментарий:", cellLeft + 4f, bodyBottom + 8.5f, boldSmallPaint)
                    val prefixW = boldSmallPaint.measureText("Комментарий: ")
                    canvas.drawText(
                        noteText.take(54),
                        cellLeft + 4f + prefixW,
                        bodyBottom + 8.5f,
                        smallPaint
                    )
                    if (noteText.length > 54) {
                        canvas.drawText(
                            noteText.drop(54).take(66),
                            cellLeft + 4f,
                            bodyBottom + 17f,
                            smallPaint
                        )
                    }
                }

                val drawW = colW * 0.54f
                val drawRect = RectF(
                    cellLeft + 3f,
                    rowTop + hdrH + 2f,
                    cellLeft + 3f + drawW,
                    bodyBottom - 3f
                )
                drawWindowTopologyVector(
                    canvas = canvas,
                    box = drawRect,
                    topology = res.windowItem.topology,
                    profile = res.profileSystem,
                    profileColorHex = res.color.hexColor,
                    showClearOpeningSizes = true
                )

                // Правая колонка: Профиль, Штапик, Цвет, Заполнение (без Габарита и Фурнитуры)
                val specX = cellLeft + drawW + 6f
                val specMaxW = cellRight - specX - 4f
                var sy = rowTop + hdrH + 10f

                fun drawSpecLine(txt: String, isBold: Boolean = false) {
                    val p = if (isBold) boldSmallPaint else smallPaint
                    val words = txt.split(" ")
                    var curLine = ""
                    for (w in words) {
                        val candidate = if (curLine.isEmpty()) w else "$curLine $w"
                        if (p.measureText(candidate) <= specMaxW || curLine.isEmpty()) {
                            curLine = candidate
                        } else {
                            if (sy < bodyBottom - 3f) {
                                canvas.drawText(curLine, specX, sy, p)
                                sy += 8.8f
                            }
                            curLine = w
                        }
                    }
                    if (curLine.isNotEmpty() && sy < bodyBottom - 3f) {
                        canvas.drawText(curLine, specX, sy, p)
                        sy += 8.8f
                    }
                }

                drawSpecLine("Профиль: ${res.profileSystem.name}", isBold = true)
                val activeSht = res.profileSystem.shtapikVariants.find { it.id == res.windowItem.selectedShtapikId }
                    ?: res.profileSystem.shtapikVariants.firstOrNull()
                if (activeSht != null) {
                    drawSpecLine("Штапик: ${activeSht.name}")
                }
                drawSpecLine("Цвет: ${res.color.name}")
                sy += 3f
                if (res.glazingSpecs.isNotEmpty()) {
                    drawSpecLine("Заполнение:", isBold = true)
                    res.glazingSpecs.forEachIndexed { gIdx, gl ->
                        drawSpecLine("${gIdx + 1}) ${gl.widthMm}×${gl.heightMm} мм (${gl.quantity} шт.) — ${gl.formula}")
                    }
                }
            }
            y += rowCardH + rowGap
        }

        doc.finishPage(page)
        val outFile = createFreshReportFile(context, "Installation_Drawings", order.orderNumber)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /**
     * 4. Задание в цех + карта линейного 1D-раскроя профилей (оптимизировано для монохромного лазерного принтера):
     * 1) Сетка конструкций 2×4 (8 конструкций на лист) с чертежом, блоком «Размеры» (Р, И, С, Д, Д.обр.)
     *    и блоком «Фурнитура» с нумерацией слева направо;
     * 2) Монохромные блоки раскроя по типам профиля (РАМА -> ИМПОСТ -> СТВОРКА -> ДВЕРЬ -> ШТАПИК),
     *    с компактной меткой слева `1)6000-327`, без цветной заливки отрезков и со светло-серым остатком.
     */
    fun generateCuttingAndProcurementPdf(
        context: Context,
        order: CustomerOrder,
        allocations: List<CutBarAllocation>,
        procurement: List<ProfileProcurementSummary>,
        windowResults: List<WindowCalculationResult> = emptyList()
    ): File {
        val doc = PdfDocument()
        val pageMargin = 22f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 15, 15)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val blockHeaderFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(235, 235, 235)
            style = Paint.Style.FILL
        }
        val blockHeaderStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 45, 45)
            style = Paint.Style.STROKE
            strokeWidth = 0.85f
        }
        val blockTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 15, 15)
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boldSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 15, 15)
            textSize = 7.0f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = 6.8f
        }
        val barLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 15, 15)
            textSize = 7.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cutTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 10, 10)
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val barWhiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val remnantGrayFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(212, 212, 212)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 70, 70)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        val cardHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(240, 240, 240)
            style = Paint.Style.FILL
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = pageMargin

        fun ensureSpace(neededH: Float) {
            if (y + neededH > PAGE_H - pageMargin) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = pageMargin
            }
        }

        canvas.drawText(
            "ЗАДАНИЕ В ЦЕХ — Заказ № ${order.orderNumber}",
            pageMargin,
            y + 11f,
            titlePaint
        )
        y += 18f

        val contentW = PAGE_W - 2 * pageMargin

        // 1. ЧЕРТЕЖИ В СЕТКЕ 2×4 (8 конструкций на лист)
        if (windowResults.isNotEmpty()) {
            val colGap = 8f
            val colW = (contentW - colGap) / 2f
            val rowGap = 5f
            val rowCardH = 186f

            windowResults.chunked(2).forEachIndexed { rowIdx, pair ->
                ensureSpace(rowCardH + rowGap)
                val rowTop = y
                pair.forEachIndexed { colIdx, res ->
                    val winNum = rowIdx * 2 + colIdx + 1
                    val cellLeft = pageMargin + colIdx * (colW + colGap)
                    val cellRight = cellLeft + colW
                    val cellBot = rowTop + rowCardH
                    val cellRect = RectF(cellLeft, rowTop, cellRight, cellBot)
                    canvas.drawRect(cellRect, gridBorderPaint)

                    val hdrH = 13f
                    canvas.drawRect(RectF(cellLeft, rowTop, cellRight, rowTop + hdrH), cardHeaderBgPaint)
                    canvas.drawLine(cellLeft, rowTop + hdrH, cellRight, rowTop + hdrH, gridBorderPaint)
                    canvas.drawText(
                        "Конструкция №$winNum: ${res.windowItem.name} (${res.windowItem.quantity} шт.)",
                        cellLeft + 5f,
                        rowTop + 9.5f,
                        boldSmallPaint
                    )

                    val drawW = colW * 0.51f
                    val drawRect = RectF(
                        cellLeft + 2f,
                        rowTop + hdrH + 2f,
                        cellLeft + 2f + drawW,
                        cellBot - 3f
                    )
                    drawWindowTopologyVector(
                        canvas = canvas,
                        box = drawRect,
                        topology = res.windowItem.topology,
                        profile = res.profileSystem,
                        profileColorHex = res.color.hexColor,
                        showClearOpeningSizes = true
                    )

                    // Правая колонка: Профиль, Штапик, Цвет, Размеры (Р, И, С, Д, Д.обр.), Фурнитура
                    val specX = cellLeft + drawW + 5f
                    val specMaxW = cellRight - specX - 4f
                    var sy = rowTop + hdrH + 9.5f

                    val cutRows = buildWorkshopCutSizesRows(res)
                    val hwLines = buildWorkshopHardwareLines(res)
                    val approxTotalLines = 3 + 1 + cutRows.sumOf { it.second.size } +
                        if (hwLines.isNotEmpty()) (1 + hwLines.size) else 0
                    val lineStep = if (approxTotalLines > 17) 7.6f else 8.4f

                    fun drawSpecText(txt: String, isBold: Boolean = false, indentX: Float = 0f) {
                        val p = if (isBold) boldSmallPaint else smallPaint
                        val maxW = (specMaxW - indentX).coerceAtLeast(30f)
                        val words = txt.split(" ")
                        var curLine = ""
                        for (w in words) {
                            val candidate = if (curLine.isEmpty()) w else "$curLine $w"
                            if (p.measureText(candidate) <= maxW || curLine.isEmpty()) {
                                curLine = candidate
                            } else {
                                if (sy < cellBot - 3f) {
                                    canvas.drawText(curLine, specX + indentX, sy, p)
                                    sy += lineStep
                                }
                                curLine = w
                            }
                        }
                        if (curLine.isNotEmpty() && sy < cellBot - 3f) {
                            canvas.drawText(curLine, specX + indentX, sy, p)
                            sy += lineStep
                        }
                    }

                    drawSpecText("Профиль: ${res.profileSystem.name}", isBold = true)
                    val activeSht = res.profileSystem.shtapikVariants.find { it.id == res.windowItem.selectedShtapikId }
                        ?: res.profileSystem.shtapikVariants.firstOrNull()
                    if (activeSht != null) {
                        drawSpecText("Штапик: ${activeSht.name}")
                    }
                    drawSpecText("Цвет: ${res.color.name}")

                    // Блок «Размеры» (вместо стеклопакетов)
                    if (cutRows.isNotEmpty() && sy < cellBot - 4f) {
                        sy += 2f
                        drawSpecText("Размеры:", isBold = true)
                        val valueOffsetX = 27f
                        cutRows.forEach { (prefix, dimLines) ->
                            dimLines.forEachIndexed { dIdx, dimTxt ->
                                if (sy < cellBot - 3f) {
                                    if (dIdx == 0) {
                                        canvas.drawText(prefix, specX, sy, boldSmallPaint)
                                    }
                                    canvas.drawText(dimTxt, specX + valueOffsetX, sy, smallPaint)
                                    sy += lineStep
                                }
                            }
                        }
                    }

                    // Блок «Фурнитура» (только размеры и сторона, нумерация слева направо)
                    if (hwLines.isNotEmpty() && sy < cellBot - 4f) {
                        sy += 2f
                        drawSpecText("Фурнитура:", isBold = true)
                        hwLines.forEach { hwLine ->
                            drawSpecText(hwLine)
                        }
                    }
                }
                y += rowCardH + rowGap
            }
            y += 6f
        }

        // 2. БЛОКИ РАСКРОЯ ДЛЯ МОНОХРОМНОГО ЛАЗЕРНОГО ПРИНТЕРА
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
                val minBlockH = if (isShtapik) 24f else 42f
                ensureSpace(minBlockH)

                // Шапка блока профиля (нейтральная для монохромной печати)
                val headerRect = RectF(pageMargin, y, PAGE_W - pageMargin, y + 18f)
                canvas.drawRect(headerRect, blockHeaderFillPaint)
                canvas.drawRect(headerRect, blockHeaderStrokePaint)

                val totalBarsUsed = sum.newFullBarsToBuyCount + sum.usedRemnantBarsCount
                val headerText = if (isShtapik) {
                    "${role.titleRu.uppercase()} (${sum.profileSystemName} • ${sum.colorName}) — Хлыстов ${sum.stockBarLengthMm} мм: $totalBarsUsed шт. (Новых: ${sum.newFullBarsToBuyCount}, склад: ${sum.usedRemnantBarsCount}) | Всего: ${fmt(sum.totalNetLengthM)} п.м."
                } else {
                    "${role.titleRu.uppercase()} (${sum.profileSystemName} • ${sum.colorName}) — Хлыстов ${sum.stockBarLengthMm} мм: $totalBarsUsed шт. (Новых: ${sum.newFullBarsToBuyCount}, склад: ${sum.usedRemnantBarsCount}) | Дет.: ${sum.totalCutPiecesCount} (${fmt(sum.totalNetLengthM)} п.м.)"
                }
                canvas.drawText(headerText, pageMargin + 6f, y + 12f, blockTitlePaint)
                y += 22f

                if (!isShtapik) {
                    val sysRoleAllocs = roleAllocations.filter {
                        it.profileSystemName == sum.profileSystemName && it.colorName == sum.colorName
                    }
                    val labelW = 66f
                    val barHeight = 14f
                    val barLeft = pageMargin + labelW
                    val barWidth = (contentW - labelW).coerceAtLeast(120f)

                    sysRoleAllocs.forEach { alloc ->
                        ensureSpace(barHeight + 5f)
                        val barTop = y

                        // Компактная метка слева вместо заголовка над лентой: например 1)6000-327
                        val compactLabel = "${alloc.barNumber})${alloc.sourceLengthMm}-${alloc.remnantLengthMm}"
                        canvas.drawText(compactLabel, pageMargin + 1f, barTop + 10.2f, barLabelPaint)

                        val totalLen = max(
                            alloc.sourceLengthMm.toFloat(),
                            alloc.cuts.sumOf { it.cutLengthMm }.toFloat()
                        ).coerceAtLeast(1f)
                        var curX = barLeft
                        alloc.cuts.forEach { piece ->
                            val wPx = (piece.cutLengthMm / totalLen) * barWidth
                            val rect = RectF(curX, barTop, curX + wPx, barTop + barHeight)
                            canvas.drawRect(rect, barWhiteFillPaint)
                            canvas.drawRect(rect, strokePaint)

                            val labelWithTag = "${piece.cutLengthMm} (${piece.shortTag})"
                            val shortLabel = "${piece.cutLengthMm}"
                            if (wPx >= cutTextPaint.measureText(labelWithTag) + 4f) {
                                canvas.drawText(labelWithTag, curX + 2.5f, barTop + 9.8f, cutTextPaint)
                            } else if (wPx >= smallPaint.measureText(shortLabel) + 2.5f) {
                                canvas.drawText(shortLabel, curX + 1.5f, barTop + 9.8f, smallPaint)
                            }
                            curX += wPx
                        }

                        if (curX < barLeft + barWidth) {
                            val remRect = RectF(curX, barTop, barLeft + barWidth, barTop + barHeight)
                            canvas.drawRect(remRect, remnantGrayFillPaint)
                            canvas.drawRect(remRect, strokePaint)
                            val remTxt = "${alloc.remnantLengthMm}"
                            if (remRect.width() >= smallPaint.measureText(remTxt) + 4f) {
                                canvas.drawText(remTxt, curX + 2.5f, barTop + 9.8f, smallPaint)
                            }
                        }

                        y += barHeight + 4f
                    }
                    y += 4f
                } else {
                    y += 3f
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

    data class CompactProposalCellModel(
        val headerNumber: String,
        val headerDimsQty: String,
        val materialLines: List<String>,
        val extraLines: List<String>,
        val priceRows: List<Pair<String, String>>
    )

    fun buildCompactProposalCellModel(
        indexOneBased: Int,
        order: CustomerOrder,
        res: WindowCalculationResult,
        finSummary: OrderFinancialSummary,
        extraAccessoriesCatalog: List<ExtraAccessoryItem> = DefaultCatalogs.defaultExtraAccessories(),
        hardwareBrandsCatalog: List<HardwareBrandItem> = DefaultCatalogs.defaultHardwareBrands()
    ): CompactProposalCellModel {
        val cur = normalizeCurrencyDisplay(order.currencySymbol)
        val wItem = res.windowItem
        val headerNumber = "№$indexOneBased"
        val headerDimsQty = "${wItem.topology.totalWidthMm.roundToInt()}×${wItem.topology.totalHeightMm.roundToInt()} мм • ${wItem.quantity} шт"

        val profColor = if (wItem.colorId == "none") "" else " • ${res.color.name}"
        val profLine = if (wItem.profileSystemId != "none") {
            "Профиль: ${res.profileSystem.name}$profColor"
        } else {
            "Профиль: —"
        }
        val glzLine = "Заполнение: ${res.glazing.name}"
        val hwBrand = hardwareBrandsCatalog.find { it.id == wItem.hardwareBrandId }
        val hwLine = if (hwBrand != null && hwBrand.id != "hw_none") {
            "Фурнитура: ${hwBrand.name}"
        } else {
            "Фурнитура: стандарт"
        }

        val extras = mutableListOf<String>()
        val selectedExtrasNames = wItem.selectedExtraIds.mapNotNull { id ->
            extraAccessoriesCatalog.find { it.id == id }?.name
        }
        if (selectedExtrasNames.isNotEmpty()) {
            extras.add("Доп.: ${selectedExtrasNames.joinToString(", ")}")
        }
        if (wItem.note.isNotBlank()) {
            extras.add("Прим.: ${wItem.note}")
        }

        val breakdown = finSummary.windowBreakdownFor(wItem.id)
        val lineBase = breakdown?.lineBaseSale ?: (res.unitTotalCost * wItem.quantity + res.costCustomPainting)
        val lineDisc = breakdown?.lineDiscountedSale ?: lineBase
        val discPct = breakdown?.discountPercent ?: order.discountPercent
        val hasDiscount = discPct > 0.0 && (lineBase - lineDisc) > 0.005

        val areaText = "${fmt(res.totalAreaM2 * wItem.quantity)} м²"
        val priceRows = buildList {
            add("Площадь" to areaText)
            add("Базовая цена" to "${fmtMoney2(lineBase)} $cur")
            if (hasDiscount) {
                add("Скидка (-${fmtPct(discPct)}%)" to "-${fmtMoney2(lineBase - lineDisc)} $cur")
            }
            add("Итого" to "${fmtMoney2(lineDisc)} $cur")
        }

        return CompactProposalCellModel(
            headerNumber = headerNumber,
            headerDimsQty = headerDimsQty,
            materialLines = listOf(profLine, glzLine, hwLine),
            extraLines = extras,
            priceRows = priceRows
        )
    }

    fun buildCompactProposalSummaryRows(
        order: CustomerOrder,
        windowResults: List<WindowCalculationResult>,
        assemblyPricingCatalog: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
        orderExtraServicesCatalog: List<OrderExtraServiceItem> = DefaultCatalogs.defaultOrderExtraServices(),
        finSchemeSettings: FinSchemeSettings = FinSchemeSettings()
    ): List<Pair<String, String>> {
        val totalConstructions = windowResults.sumOf { it.windowItem.quantity }
        val totalArea = windowResults.sumOf { it.totalAreaM2 * it.windowItem.quantity }
        val cur = normalizeCurrencyDisplay(order.currencySymbol)
        val finSummary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = order,
            windowResults = windowResults,
            assemblyPricingCatalog = assemblyPricingCatalog,
            orderExtraServicesCatalog = orderExtraServicesCatalog,
            finSchemeSettings = finSchemeSettings
        )
        return buildList {
            add("Всего изделий (шт)" to "$totalConstructions")
            add("Общая площадь (м²)" to "${fmt(totalArea)} м²")
            if (finSummary.discountAmount > 0.005) {
                add("Базовая стоимость" to "${fmtMoney2(finSummary.orderSaleBeforeDiscountAndVat)} $cur")
                add("Скидка" to "-${fmtMoney2(finSummary.discountAmount)} $cur")
            }
            add("Итоговая сумма без НДС" to "${fmtMoney2(finSummary.orderSaleAfterDiscountBeforeVat)} $cur")
            if (order.taxVatPercent > 0.0) {
                add("НДС (${fmtPct(order.taxVatPercent)}%)" to "${fmtMoney2(finSummary.vatAmount)} $cur")
            }
            add("ИТОГО К ОПЛАТЕ С НДС" to "${fmtMoney2(finSummary.totalWithVat)} $cur")
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    private fun fmtPct(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
    private fun fmtMoney2(v: Double): String = String.format(Locale.US, "%.2f", v)
}
