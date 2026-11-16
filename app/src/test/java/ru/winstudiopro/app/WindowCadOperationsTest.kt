package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.ImpostAlignmentMode
import ru.winstudiopro.app.domain.model.ImpostDirection
import ru.winstudiopro.app.domain.model.OpeningProperties
import ru.winstudiopro.app.domain.model.SashOpeningType
import ru.winstudiopro.app.domain.model.WindowCadOperations

class WindowCadOperationsTest {

    @Test
    fun `single1500Topology creates valid 1500x1500 single opening frame`() {
        val topo = WindowCadOperations.single1500Topology()
        assertEquals(1500f, topo.totalWidthMm, 0.01f)
        assertEquals(1500f, topo.totalHeightMm, 0.01f)
        assertEquals(1, topo.frames.size)
        assertEquals(1, topo.allOpenings.size)
        assertEquals(0, topo.allImposts.size)
    }

    @Test
    fun `splitOpening and removeImpost update openings and imposts consistently`() {
        val base = WindowCadOperations.single1500Topology()
        val split = WindowCadOperations.splitOpening(base, "op1", ImpostDirection.VERTICAL, 3)

        assertEquals(3, split.allOpenings.size)
        assertEquals(2, split.allImposts.size)
        assertEquals(listOf(0f, 500f, 1000f, 1500f), split.xGridCoords)

        val firstImpostId = split.allImposts.first().id
        val merged = WindowCadOperations.removeImpost(split, firstImpostId)
        assertEquals(2, merged.allOpenings.size)
        assertEquals(1, merged.allImposts.size)
    }

    @Test
    fun `resizeTotalDimensions scales frame and maintains min segment integrity`() {
        val split = WindowCadOperations.splitOpening(
            WindowCadOperations.single1500Topology(),
            "op1",
            ImpostDirection.VERTICAL,
            2
        )
        val resized = WindowCadOperations.resizeTotalDimensions(split, 2400f, 1800f)

        assertEquals(2400f, resized.totalWidthMm, 0.01f)
        assertEquals(1800f, resized.totalHeightMm, 0.01f)
        assertEquals(1200f, resized.allOpenings[0].widthMm, 1f)
        assertEquals(1200f, resized.allOpenings[1].widthMm, 1f)
    }

    @Test
    fun `equalizeAxisSegments supports BY_AXIS, BY_OPENING and BY_DAYLIGHT modes`() {
        val profile = DefaultCatalogs.defaultProfileSystems().first()
        var topo = WindowCadOperations.splitOpening(
            WindowCadOperations.single1500Topology(),
            "op1",
            ImpostDirection.VERTICAL,
            2
        )
        // Делаем правую секцию поворотно-откидной створкой, а левую оставляем глухой
        topo = WindowCadOperations.updateOpeningProperties(
            topo,
            "op1_v1",
            OpeningProperties(openingType = SashOpeningType.TILT_TURN_RIGHT)
        )

        val byAxis = WindowCadOperations.equalizeAxisSegments(topo, isHorizontalAxis = true, mode = ImpostAlignmentMode.BY_AXIS, profileSystem = profile)
        assertEquals(750f, byAxis.allOpenings[0].widthMm, 1f)
        assertEquals(750f, byAxis.allOpenings[1].widthMm, 1f)

        val byOpening = WindowCadOperations.equalizeAxisSegments(topo, isHorizontalAxis = true, mode = ImpostAlignmentMode.BY_OPENING, profileSystem = profile)
        assertEquals(750f, byOpening.allOpenings[0].widthMm, 1f)

        val byDaylight = WindowCadOperations.equalizeAxisSegments(topo, isHorizontalAxis = true, mode = ImpostAlignmentMode.BY_DAYLIGHT, profileSystem = profile)
        // Чтобы световые проёмы были равны, створка (с шириной профиля створки) получает большую осевую ширину, чем глухарь
        assertTrue(byDaylight.allOpenings[1].widthMm > byDaylight.allOpenings[0].widthMm)
    }
}
