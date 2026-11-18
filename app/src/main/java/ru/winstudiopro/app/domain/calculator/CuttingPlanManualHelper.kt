package ru.winstudiopro.app.domain.calculator

import ru.winstudiopro.app.domain.model.AllocatedCutPiece
import ru.winstudiopro.app.domain.model.CutBarAllocation
import ru.winstudiopro.app.domain.model.ProfileProcurementSummary
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.ProfileSystemSpec
import ru.winstudiopro.app.domain.model.StockRemnantBar
import java.util.UUID
import kotlin.math.roundToInt

sealed class MoveCutDestination {
    data class ExistingBar(val barIndex: Int) : MoveCutDestination()
    data class NewFullBar(val lengthMm: Int) : MoveCutDestination()
    data class WarehouseRemnant(val lengthMm: Int) : MoveCutDestination()
}

data class WarehouseStockApplyResult(
    val updatedStock: List<StockRemnantBar>,
    val consumedRemnantsCount: Int,
    val addedRemnantsCount: Int
)

/**
 * Помощник для ручного редактирования 1D-раскроя (при повреждении хлыста, замене длины,
 * переносе деталей между хлыстами и деловыми остатками) и пересчёта остатков на склад.
 */
object CuttingPlanManualHelper {

    private const val BUSINESS_REMNANT_MIN_MM = 400

    fun resolveProfileForBar(
        bar: CutBarAllocation,
        profileSystems: List<ProfileSystemSpec>
    ): ProfileSystemSpec? {
        return profileSystems.find { it.name == bar.profileSystemName }
            ?: profileSystems.firstOrNull()
    }

    fun calculateUsedWithKerfMm(cuts: List<AllocatedCutPiece>, kerfMm: Int): Int {
        if (cuts.isEmpty()) return 0
        val safeKerf = kerfMm.coerceAtLeast(0)
        val piecesSum = cuts.sumOf { it.cutLengthMm }
        val kerfsSum = (cuts.size - 1) * safeKerf
        return piecesSum + kerfsSum
    }

    fun recalculateBar(bar: CutBarAllocation, kerfMm: Int): CutBarAllocation {
        val usedMm = calculateUsedWithKerfMm(bar.cuts, kerfMm)
        val remMm = (bar.sourceLengthMm - usedMm).coerceAtLeast(0)
        return bar.copy(
            usedWithKerfMm = usedMm,
            remnantLengthMm = remMm,
            isUsableBusinessRemnant = remMm >= BUSINESS_REMNANT_MIN_MM
        )
    }

    fun barHasOverflow(bar: CutBarAllocation, kerfMm: Int): Boolean {
        return calculateUsedWithKerfMm(bar.cuts, kerfMm) > bar.sourceLengthMm
    }

    fun renumberAllocations(allocations: List<CutBarAllocation>): List<CutBarAllocation> {
        val nonEmpty = allocations.filter { it.cuts.isNotEmpty() }
        val counters = mutableMapOf<Triple<String, ProfileRole, String>, Int>()
        return nonEmpty
            .sortedWith(compareBy({ it.role.ordinal }, { it.profileSystemName }, { it.colorName }))
            .map { bar ->
                val key = Triple(bar.profileSystemName, bar.role, bar.colorName)
                val nextNum = (counters[key] ?: 0) + 1
                counters[key] = nextNum
                bar.copy(barNumber = nextNum)
            }
    }

    fun changeBarSource(
        allocations: List<CutBarAllocation>,
        barIndex: Int,
        newSourceLengthMm: Int,
        isFromRemnantStock: Boolean,
        profileSystems: List<ProfileSystemSpec>
    ): List<CutBarAllocation> {
        if (barIndex !in allocations.indices) return allocations
        val target = allocations[barIndex]
        val kerf = resolveProfileForBar(target, profileSystems)?.sawKerfMm?.roundToInt() ?: 4
        val updated = recalculateBar(
            target.copy(
                sourceLengthMm = newSourceLengthMm.coerceIn(300, 12000),
                isFromRemnantStock = isFromRemnantStock
            ),
            kerf
        )
        return allocations.mapIndexed { idx, b -> if (idx == barIndex) updated else b }
    }

    fun moveCutPiece(
        allocations: List<CutBarAllocation>,
        fromBarIndex: Int,
        cutIndex: Int,
        destination: MoveCutDestination,
        profileSystems: List<ProfileSystemSpec>
    ): List<CutBarAllocation> {
        if (fromBarIndex !in allocations.indices) return allocations
        val sourceBar = allocations[fromBarIndex]
        if (cutIndex !in sourceBar.cuts.indices) return allocations

        val piece = sourceBar.cuts[cutIndex]
        val kerf = resolveProfileForBar(sourceBar, profileSystems)?.sawKerfMm?.roundToInt() ?: 4

        val updatedSourceCuts = sourceBar.cuts.toMutableList().apply { removeAt(cutIndex) }
        val updatedSourceBar = recalculateBar(sourceBar.copy(cuts = updatedSourceCuts), kerf)

        val mutableBars = allocations.toMutableList()
        mutableBars[fromBarIndex] = updatedSourceBar

        when (destination) {
            is MoveCutDestination.ExistingBar -> {
                if (destination.barIndex !in mutableBars.indices || destination.barIndex == fromBarIndex) {
                    return allocations
                }
                val dstBar = mutableBars[destination.barIndex]
                val dstKerf = resolveProfileForBar(dstBar, profileSystems)?.sawKerfMm?.roundToInt() ?: kerf
                val newDstCuts = dstBar.cuts + piece
                mutableBars[destination.barIndex] = recalculateBar(dstBar.copy(cuts = newDstCuts), dstKerf)
            }

            is MoveCutDestination.NewFullBar -> {
                val created = recalculateBar(
                    CutBarAllocation(
                        barNumber = 1,
                        profileSystemName = sourceBar.profileSystemName,
                        role = sourceBar.role,
                        isFromRemnantStock = false,
                        sourceLengthMm = destination.lengthMm.coerceAtLeast(piece.cutLengthMm),
                        cuts = listOf(piece),
                        usedWithKerfMm = piece.cutLengthMm,
                        remnantLengthMm = 0,
                        isUsableBusinessRemnant = false,
                        colorName = sourceBar.colorName,
                        colorHex = sourceBar.colorHex
                    ),
                    kerf
                )
                mutableBars.add(created)
            }

            is MoveCutDestination.WarehouseRemnant -> {
                val created = recalculateBar(
                    CutBarAllocation(
                        barNumber = 1,
                        profileSystemName = sourceBar.profileSystemName,
                        role = sourceBar.role,
                        isFromRemnantStock = true,
                        sourceLengthMm = destination.lengthMm.coerceAtLeast(piece.cutLengthMm),
                        cuts = listOf(piece),
                        usedWithKerfMm = piece.cutLengthMm,
                        remnantLengthMm = 0,
                        isUsableBusinessRemnant = false,
                        colorName = sourceBar.colorName,
                        colorHex = sourceBar.colorHex
                    ),
                    kerf
                )
                mutableBars.add(created)
            }
        }

        return renumberAllocations(mutableBars)
    }

    /**
     * Пересчитывает сводку закупки по текущему (в т.ч. отредактированному вручную) раскрою.
     * Для штапика сохраняется исходная автоматическая сводка.
     */
    fun rebuildProcurementSummaries(
        allocations: List<CutBarAllocation>,
        baseAutoSummaries: List<ProfileProcurementSummary>,
        profileSystems: List<ProfileSystemSpec>
    ): List<ProfileProcurementSummary> {
        val result = mutableListOf<ProfileProcurementSummary>()
        val shtapikSummaries = baseAutoSummaries.filter { it.role == ProfileRole.SHTAPIK }

        val grouped = allocations
            .filter { it.role != ProfileRole.SHTAPIK && it.cuts.isNotEmpty() }
            .groupBy { Triple(it.profileSystemName, it.role, it.colorName) }
            .entries
            .sortedWith(compareBy({ it.key.second.ordinal }, { it.key.third }))

        for ((key, bars) in grouped) {
            val (sysName, role, colorName) = key
            val sys = profileSystems.find { it.name == sysName }
                ?: profileSystems.firstOrNull()
            val baseMatch = baseAutoSummaries.find {
                it.profileSystemName == sysName && it.role == role && it.colorName == colorName
            }
            val sysId = baseMatch?.profileSystemId ?: sys?.id ?: ""
            val stockLen = baseMatch?.stockBarLengthMm
                ?: sys?.stockBarLengthMm?.roundToInt()?.coerceIn(3000, 8500)
                ?: 6000
            val colorHex = bars.firstOrNull()?.colorHex ?: baseMatch?.colorHex ?: 0xFF383E42L

            val totalSourceMm = bars.sumOf { it.sourceLengthMm }.toDouble().coerceAtLeast(1.0)
            val totalNetMm = bars.sumOf { b -> b.cuts.sumOf { it.cutLengthMm } }.toDouble()
            val wastePct = ((totalSourceMm - totalNetMm) / totalSourceMm * 100.0).coerceAtLeast(0.0)
            val totalPieces = bars.sumOf { it.cuts.size }

            result.add(
                ProfileProcurementSummary(
                    profileSystemId = sysId,
                    profileSystemName = sysName,
                    role = role,
                    stockBarLengthMm = stockLen,
                    usedRemnantBarsCount = bars.count { it.isFromRemnantStock },
                    newFullBarsToBuyCount = bars.count { !it.isFromRemnantStock },
                    totalCutPiecesCount = totalPieces,
                    totalNetLengthM = totalNetMm / 1000.0,
                    wastePercent = wastePct,
                    newBusinessRemnantsMm = bars.filter { it.isUsableBusinessRemnant }.map { it.remnantLengthMm },
                    colorName = colorName,
                    colorHex = colorHex
                )
            )
        }

        result.addAll(shtapikSummaries)
        return result.sortedWith(compareBy({ it.role.ordinal }, { it.colorName }))
    }

    /**
     * Списывает использованные деловые остатки со склада и добавляет новые деловые остатки (≥ 400 мм),
     * полученные в результате актуального (включая ручное редактирование) раскроя.
     */
    fun applyWarehouseStockChanges(
        currentStock: List<StockRemnantBar>,
        allocations: List<CutBarAllocation>,
        procurementSummaries: List<ProfileProcurementSummary>,
        profileSystems: List<ProfileSystemSpec>
    ): WarehouseStockApplyResult {
        val mutableStock = currentStock.toMutableList()
        var consumed = 0

        val usedRemnantBars = allocations.filter { it.isFromRemnantStock && it.role != ProfileRole.SHTAPIK }
        for (usedBar in usedRemnantBars) {
            val sysId = profileSystems.find { it.name == usedBar.profileSystemName }?.id
            val matchIdx = mutableStock.indexOfFirst { stockItem ->
                !stockItem.isFullStockBar &&
                    stockItem.quantity > 0 &&
                    stockItem.role == usedBar.role &&
                    stockItem.lengthMm == usedBar.sourceLengthMm &&
                    stockItem.colorName.equals(usedBar.colorName, ignoreCase = true) &&
                    (sysId == null || stockItem.profileSystemId == sysId)
            }
            if (matchIdx >= 0) {
                val found = mutableStock[matchIdx]
                consumed += 1
                if (found.quantity <= 1) {
                    mutableStock.removeAt(matchIdx)
                } else {
                    mutableStock[matchIdx] = found.copy(quantity = found.quantity - 1)
                }
            }
        }

        var added = 0
        procurementSummaries
            .filter { it.role != ProfileRole.SHTAPIK }
            .forEach { sum ->
                sum.newBusinessRemnantsMm.forEach { remLen ->
                    if (remLen >= BUSINESS_REMNANT_MIN_MM) {
                        mutableStock.add(
                            StockRemnantBar(
                                id = UUID.randomUUID().toString(),
                                profileSystemId = sum.profileSystemId,
                                role = sum.role,
                                lengthMm = remLen,
                                quantity = 1,
                                isFullStockBar = false,
                                colorName = sum.colorName,
                                colorHex = sum.colorHex
                            )
                        )
                        added += 1
                    }
                }
            }

        return WarehouseStockApplyResult(
            updatedStock = mutableStock,
            consumedRemnantsCount = consumed,
            addedRemnantsCount = added
        )
    }

    /**
     * Возвращает доступные на складе деловые остатки (!isFullStockBar) для заданной системы, роли и цвета
     * с учётом уже использованных остатков в текущем раскрое.
     */
    fun findAvailableWarehouseRemnants(
        stockRemnants: List<StockRemnantBar>,
        allocations: List<CutBarAllocation>,
        excludeBarIndex: Int,
        profileSystemName: String,
        profileSystemId: String?,
        role: ProfileRole,
        colorName: String,
        minLengthMm: Int = 0
    ): List<StockRemnantBar> {
        val usedCountsByLength = mutableMapOf<Int, Int>()
        allocations.forEachIndexed { idx, b ->
            if (idx != excludeBarIndex &&
                b.isFromRemnantStock &&
                b.role == role &&
                b.profileSystemName == profileSystemName &&
                b.colorName.trim().equals(colorName.trim(), ignoreCase = true)
            ) {
                usedCountsByLength[b.sourceLengthMm] = (usedCountsByLength[b.sourceLengthMm] ?: 0) + 1
            }
        }

        val remainingUsed = usedCountsByLength.toMutableMap()
        val result = mutableListOf<StockRemnantBar>()
        stockRemnants.filter { rem ->
            !rem.isFullStockBar &&
                rem.quantity > 0 &&
                rem.role == role &&
                rem.lengthMm >= minLengthMm &&
                rem.colorName.trim().equals(colorName.trim(), ignoreCase = true) &&
                (profileSystemId == null || rem.profileSystemId == profileSystemId)
        }.sortedByDescending { it.lengthMm }.forEach { rem ->
            val alreadyUsed = remainingUsed[rem.lengthMm] ?: 0
            val availQty = (rem.quantity - alreadyUsed).coerceAtLeast(0)
            remainingUsed[rem.lengthMm] = (alreadyUsed - rem.quantity).coerceAtLeast(0)
            if (availQty > 0) {
                result.add(rem.copy(quantity = availQty))
            }
        }
        return result
    }
}
