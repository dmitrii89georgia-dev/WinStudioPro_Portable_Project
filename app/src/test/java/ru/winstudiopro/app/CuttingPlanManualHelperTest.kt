package ru.winstudiopro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.winstudiopro.app.domain.calculator.CuttingPlanManualHelper
import ru.winstudiopro.app.domain.calculator.MoveCutDestination
import ru.winstudiopro.app.domain.model.AllocatedCutPiece
import ru.winstudiopro.app.domain.model.CutBarAllocation
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.StockRemnantBar

class CuttingPlanManualHelperTest {

    private val profiles = DefaultCatalogs.defaultProfileSystems()
    private val testSysName = profiles.first().name
    private val testSysId = profiles.first().id

    private fun makeBar(
        barNumber: Int,
        sourceMm: Int = 6000,
        isRemnant: Boolean = false,
        lengths: List<Int>
    ): CutBarAllocation {
        val kerf = 4
        val cuts = lengths.mapIndexed { idx, len ->
            AllocatedCutPiece(
                windowName = "Окно 1",
                positionLabel = "Рама",
                shortTag = "1-${idx + 1}",
                cutLengthMm = len,
                leftAngleDeg = 45,
                rightAngleDeg = 45
            )
        }
        val used = CuttingPlanManualHelper.calculateUsedWithKerfMm(cuts, kerf)
        val rem = (sourceMm - used).coerceAtLeast(0)
        return CutBarAllocation(
            barNumber = barNumber,
            profileSystemName = testSysName,
            role = ProfileRole.FRAME,
            isFromRemnantStock = isRemnant,
            sourceLengthMm = sourceMm,
            cuts = cuts,
            usedWithKerfMm = used,
            remnantLengthMm = rem,
            isUsableBusinessRemnant = rem >= 400,
            colorName = "Белый",
            colorHex = 0xFFFFFFFFL
        )
    }

    @Test
    fun `changeBarSource updates remnant and detects overflow on damaged bar`() {
        val bar = makeBar(barNumber = 1, sourceMm = 6000, lengths = listOf(2200, 2200, 1200))
        // 2200 + 2200 + 1200 + 2*4 = 5608 мм
        assertEquals(5608, bar.usedWithKerfMm)
        assertEquals(392, bar.remnantLengthMm)

        // У хлыста повреждён 1 метр -> фактически 5000 мм (переполнение!)
        val shortened = CuttingPlanManualHelper.changeBarSource(
            allocations = listOf(bar),
            barIndex = 0,
            newSourceLengthMm = 5000,
            isFromRemnantStock = false,
            profileSystems = profiles
        )
        assertEquals(5000, shortened[0].sourceLengthMm)
        assertEquals(0, shortened[0].remnantLengthMm)
        assertTrue(CuttingPlanManualHelper.barHasOverflow(shortened[0], 4))

        // Переносим деталь 1200 на новый хлыст -> исходный хлыст больше не переполнен и даёт деловой остаток 596 мм
        val moved = CuttingPlanManualHelper.moveCutPiece(
            allocations = shortened,
            fromBarIndex = 0,
            cutIndex = 2,
            destination = MoveCutDestination.NewFullBar(6000),
            profileSystems = profiles
        )
        assertEquals(2, moved.size)
        assertFalse(CuttingPlanManualHelper.barHasOverflow(moved[0], 4))
        // 2200 + 2200 + 4 = 4404 -> остаток 596 мм
        assertEquals(596, moved[0].remnantLengthMm)
        assertTrue(moved[0].isUsableBusinessRemnant)
        // Новый хлыст: 6000 - 1200 = 4800 мм
        assertEquals(4800, moved[1].remnantLengthMm)
        assertTrue(moved[1].isUsableBusinessRemnant)
    }

    @Test
    fun `moveCutPiece removes empty bar and renumbers remaining bars`() {
        val b1 = makeBar(barNumber = 1, sourceMm = 6000, lengths = listOf(1500))
        val b2 = makeBar(barNumber = 2, sourceMm = 6000, lengths = listOf(1400))

        val moved = CuttingPlanManualHelper.moveCutPiece(
            allocations = listOf(b1, b2),
            fromBarIndex = 0,
            cutIndex = 0,
            destination = MoveCutDestination.ExistingBar(1),
            profileSystems = profiles
        )

        assertEquals(1, moved.size)
        assertEquals(1, moved[0].barNumber)
        assertEquals(listOf(1400, 1500), moved[0].cuts.map { it.cutLengthMm })
        // 1400 + 1500 + 4 = 2904 -> остаток 3096 мм
        assertEquals(3096, moved[0].remnantLengthMm)
    }

    @Test
    fun `applyWarehouseStockChanges consumes used remnants and adds business remnants over 400mm`() {
        val remBar = makeBar(barNumber = 1, sourceMm = 2000, isRemnant = true, lengths = listOf(1500)) // ост 500мм
        val fullBar = makeBar(barNumber = 2, sourceMm = 6000, isRemnant = false, lengths = listOf(2800, 2900)) // ост 296мм (< 400)
        val allocs = listOf(remBar, fullBar)
        val summaries = CuttingPlanManualHelper.rebuildProcurementSummaries(
            allocations = allocs,
            baseAutoSummaries = emptyList(),
            profileSystems = profiles
        )

        val currentStock = listOf(
            StockRemnantBar(
                id = "s1",
                profileSystemId = testSysId,
                role = ProfileRole.FRAME,
                lengthMm = 2000,
                quantity = 2,
                isFullStockBar = false,
                colorName = "Белый"
            )
        )

        val res = CuttingPlanManualHelper.applyWarehouseStockChanges(
            currentStock = currentStock,
            allocations = allocs,
            procurementSummaries = summaries,
            profileSystems = profiles
        )

        assertEquals(1, res.consumedRemnantsCount)
        assertEquals(1, res.addedRemnantsCount)
        // Было 2 шт по 2000мм -> списался 1 (остался 1 по 2000мм), добавился 1 деловой остаток 500мм (остаток 296мм отброшен)
        assertEquals(2, res.updatedStock.size)
        assertEquals(1, res.updatedStock.find { it.lengthMm == 2000 }?.quantity)
        assertEquals(1, res.updatedStock.find { it.lengthMm == 500 }?.quantity)
    }
}
