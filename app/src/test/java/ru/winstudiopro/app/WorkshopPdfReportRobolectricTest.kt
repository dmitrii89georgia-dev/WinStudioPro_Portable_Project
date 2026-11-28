package ru.winstudiopro.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowAndroidPdfDocument {
    private var pagesCount = 0

    @Implementation
    fun __constructor__() {
        // Нет нативного mNativeDocument в JVM
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        pagesCount++
        val bmp = Bitmap.createBitmap(
            pageInfo.pageWidth.coerceAtLeast(1),
            pageInfo.pageHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        return ReflectionHelpers.callConstructor(
            PdfDocument.Page::class.java,
            ReflectionHelpers.ClassParameter.from(Canvas::class.java, canvas),
            ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo::class.java, pageInfo)
        )
    }

    @Implementation
    fun finishPage(@Suppress("UNUSED_PARAMETER") page: PdfDocument.Page?) {
        // no-op
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        val header = "%PDF-1.4\n% Robolectric WinStudioPro Test PDF (pages=$pagesCount)\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    @Implementation
    fun close() {
        // no-op
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowAndroidPdfDocument::class])
class WorkshopPdfReportRobolectricTest {

    private fun buildComplexMultiSashWindow(index: Int): WindowItem {
        var topology = WindowCadOperations.resizeTotalDimensions(WindowCadOperations.single1500Topology(), 2400f, 2100f)
        val firstOpId = topology.allOpenings.first().id
        topology = WindowCadOperations.splitOpening(topology, firstOpId, ImpostDirection.VERTICAL, 3)

        val opsSorted = topology.allOpenings.sortedBy { it.leftMm }
        topology = WindowCadOperations.updateOpeningProperties(
            topology,
            opsSorted[0].id,
            OpeningProperties(
                openingType = SashOpeningType.TILT_TURN_LEFT,
                mosquitoNetType = MosquitoNetType.WINDOW
            )
        )
        topology = WindowCadOperations.updateOpeningProperties(
            topology,
            opsSorted[1].id,
            OpeningProperties(
                openingType = SashOpeningType.TRANSOM_BOTTOM,
                mosquitoNetType = MosquitoNetType.PLISSE
            )
        )
        topology = WindowCadOperations.updateOpeningProperties(
            topology,
            opsSorted[2].id,
            OpeningProperties(
                openingType = SashOpeningType.TURN_RIGHT,
                isDoorSash = true,
                isReverseDoorSash = true,
                mosquitoNetType = MosquitoNetType.DOOR
            )
        )

        return WindowItem(
            id = "w_$index",
            name = "Конструкция №$index",
            quantity = 1,
            topology = topology,
            profileSystemId = DefaultCatalogs.defaultProfileSystems().first().id,
            glazingId = DefaultCatalogs.defaultGlazingItems().first().id,
            colorId = DefaultCatalogs.defaultColors().first().id,
            note = if (index == 1) "Монтаж с ПСУЛ и утеплением откоса" else ""
        )
    }

    @Test
    fun `buildWorkshopCutSizesRows groups frame impost sash door and reverse door sizes`() {
        val win = buildComplexMultiSashWindow(1)
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val calc = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = profiles,
            glazingCatalog = DefaultCatalogs.defaultGlazingItems(),
            colorCatalog = DefaultCatalogs.defaultColors(),
            sillsCatalog = DefaultCatalogs.defaultSills(),
            extraAccessoriesCatalog = DefaultCatalogs.defaultExtraAccessories(),
            workServicesCatalog = DefaultCatalogs.defaultWorkServices(),
            hardwareBrandsCatalog = DefaultCatalogs.defaultHardwareBrands()
        )

        val rows = PdfReportGenerator.buildWorkshopCutSizesRows(calc)
        val keys = rows.map { it.first }
        assertTrue("Expected frame row Р:", keys.contains("Р:"))
        assertTrue("Expected impost row И:", keys.contains("И:"))
        assertTrue("Expected sash row С:", keys.contains("С:"))
        assertTrue("Expected reverse door row Д.обр.:", keys.contains("Д.обр.:"))

        rows.forEach { (_, dims) ->
            assertTrue(dims.isNotEmpty())
            dims.forEach { d -> assertTrue("Expected '- Nшт' format, got: $d", d.contains(" - ") && d.endsWith("шт")) }
        }
    }

    @Test
    fun `buildWorkshopHardwareLines formats numbered left-to-right sizes cleanly without brand text`() {
        val win = buildComplexMultiSashWindow(1)
        val calc = WindowBomAndCostEngine.calculateWindow(
            window = win,
            profileSystems = DefaultCatalogs.defaultProfileSystems(),
            glazingCatalog = DefaultCatalogs.defaultGlazingItems(),
            colorCatalog = DefaultCatalogs.defaultColors(),
            sillsCatalog = DefaultCatalogs.defaultSills(),
            extraAccessoriesCatalog = DefaultCatalogs.defaultExtraAccessories(),
            workServicesCatalog = DefaultCatalogs.defaultWorkServices(),
            hardwareBrandsCatalog = DefaultCatalogs.defaultHardwareBrands()
        )

        val hwLines = PdfReportGenerator.buildWorkshopHardwareLines(calc)
        assertEquals(3, hwLines.size)
        assertTrue("Line 1 should be tilt-turn W/H левая: ${hwLines[0]}", hwLines[0].startsWith("1) ") && hwLines[0].contains("/") && hwLines[0].endsWith("левая"))
        assertTrue("Line 2 should be transom width only: ${hwLines[1]}", hwLines[1].startsWith("2) ") && !hwLines[1].contains("/") && !hwLines[1].contains("левая"))
        assertTrue("Line 3 should be door height-only правая: ${hwLines[2]}", hwLines[2].startsWith("3) ") && !hwLines[2].contains("/") && hwLines[2].endsWith("правая"))
        hwLines.forEach { line ->
            assertFalse(line.contains("Фурнитура", ignoreCase = true))
            assertFalse(line.contains("Поворотно", ignoreCase = true))
        }
    }

    @Test
    fun `generateMosquitoOrderPdf generateInstallationDrawingsPdf and generateCuttingAndProcurementPdf produce valid non-empty PDFs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val msqCatalog = DefaultCatalogs.defaultMosquitoSystems()
        val win1 = buildComplexMultiSashWindow(1)

        // Узкие проёмы для проверки адаптивного шрифта размеров светлого проёма в PDF
        var narrowTopo = WindowCadOperations.resizeTotalDimensions(WindowCadOperations.single1500Topology(), 2800f, 1200f)
        narrowTopo = WindowCadOperations.splitOpening(narrowTopo, narrowTopo.allOpenings.first().id, ImpostDirection.VERTICAL, 5)
        val winNarrow = win1.copy(id = "w_narrow", name = "Конструкция 2", topology = narrowTopo)

        val order = CustomerOrder(
            id = "ord_pdf_test",
            orderNumber = "PDF-2026-1",
            customerName = "Тест Клиент",
            customerPhone = "+79990000000",
            address = "ул. Тестовая, 1",
            createdDate = "28.11.2026",
            windows = listOf(win1, winNarrow)
        )
        val calcs = order.windows.mapIndexed { idx, w ->
            WindowBomAndCostEngine.calculateWindow(
                window = w,
                profileSystems = profiles,
                glazingCatalog = DefaultCatalogs.defaultGlazingItems(),
                colorCatalog = DefaultCatalogs.defaultColors(),
                sillsCatalog = DefaultCatalogs.defaultSills(),
                hardwareBrandsCatalog = DefaultCatalogs.defaultHardwareBrands(),
                windowIndexInOrder = idx + 1,
                mosquitoSystemsCatalog = msqCatalog
            )
        }

        val msqPdf = PdfReportGenerator.generateMosquitoOrderPdf(context, order, calcs, mosquitoSystemsCatalog = msqCatalog)
        assertTrue("Mosquito PDF should exist", msqPdf.exists() && msqPdf.length() > 30)
        assertEquals("%PDF", msqPdf.inputStream().use { it.readNBytes(4).toString(Charsets.US_ASCII) })

        val installPdf = PdfReportGenerator.generateInstallationDrawingsPdf(context, order, calcs)
        assertTrue("Installation PDF should exist", installPdf.exists() && installPdf.length() > 30)
        assertEquals("%PDF", installPdf.inputStream().use { it.readNBytes(4).toString(Charsets.US_ASCII) })

        val (allocs, proc) = WindowBomAndCostEngine.optimizeOrderProfileCutting(calcs, emptyList(), profiles)
        val cuttingPdf = PdfReportGenerator.generateCuttingAndProcurementPdf(context, order, allocs, proc, calcs)
        assertTrue("Cutting PDF should exist", cuttingPdf.exists() && cuttingPdf.length() > 30)
        assertEquals("%PDF", cuttingPdf.inputStream().use { it.readNBytes(4).toString(Charsets.US_ASCII) })
    }
}
