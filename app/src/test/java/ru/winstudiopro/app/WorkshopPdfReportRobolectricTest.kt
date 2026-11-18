package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator

class WorkshopPdfReportRobolectricTest {

    private fun buildComplexMultiSashWindow(index: Int): WindowItem {
        var topology = WindowCadOperations.resizeTotalDimensions(WindowCadOperations.single1500Topology(), 2400f, 2100f)
        val firstOpId = topology.allOpenings.first().id
        topology = WindowCadOperations.splitOpening(topology, firstOpId, ImpostDirection.VERTICAL, 3)

        val opsSorted = topology.allOpenings.sortedBy { it.leftMm }
        topology = WindowCadOperations.updateOpeningProperties(
            topology,
            opsSorted[0].id,
            OpeningProperties(openingType = SashOpeningType.TILT_TURN_LEFT)
        )
        topology = WindowCadOperations.updateOpeningProperties(
            topology,
            opsSorted[1].id,
            OpeningProperties(openingType = SashOpeningType.TRANSOM_BOTTOM)
        )
        topology = WindowCadOperations.updateOpeningProperties(
            topology,
            opsSorted[2].id,
            OpeningProperties(
                openingType = SashOpeningType.TURN_RIGHT,
                isDoorSash = true,
                isReverseDoorSash = true
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
}
