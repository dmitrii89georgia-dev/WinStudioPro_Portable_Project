package ru.winstudiopro.app.domain.calculator

import ru.winstudiopro.app.domain.model.*
import java.util.UUID
import kotlin.math.abs

data class ProductionDeductionResult(
    val updatedOrder: CustomerOrder,
    val updatedStockBars: List<StockRemnantBar>,
    val updatedStockRubber: List<StockRubberItem>,
    val consumedFullBarsCount: Int,
    val consumedRemnantsCount: Int,
    val addedRemnantsCount: Int,
    val deductedRubberMeters: Double
)

/**
 * Помощник автоматического списания профилей (целых хлыстов и остатков), добавления деловых остатков
 * и списания уплотнительной резины в метрах со склада при переводе заказа в статус «Производство».
 */
object WarehouseInventoryHelper {

    fun upsertRubberStock(
        currentStockRubber: List<StockRubberItem>,
        profileSystemId: String,
        rubberType: RubberType,
        wedgeVariantId: String? = null,
        wedgeThicknessMm: Float? = null,
        label: String,
        deltaMeters: Double? = null,
        absoluteMeters: Double? = null
    ): List<StockRubberItem> {
        val list = currentStockRubber.toMutableList()
        val matchIdx = findMatchingRubberIndex(list, profileSystemId, rubberType, wedgeVariantId, wedgeThicknessMm)
        if (matchIdx >= 0) {
            val existing = list[matchIdx]
            val nextMeters = when {
                absoluteMeters != null -> absoluteMeters.coerceAtLeast(0.0)
                deltaMeters != null -> (existing.metersInStock + deltaMeters).coerceAtLeast(0.0)
                else -> existing.metersInStock
            }
            list[matchIdx] = existing.copy(
                label = label.ifBlank { existing.label },
                wedgeVariantId = wedgeVariantId ?: existing.wedgeVariantId,
                wedgeThicknessMm = wedgeThicknessMm ?: existing.wedgeThicknessMm,
                metersInStock = nextMeters
            )
        } else {
            val initialMeters = (absoluteMeters ?: deltaMeters ?: 0.0).coerceAtLeast(0.0)
            list.add(
                StockRubberItem(
                    id = "rub_${UUID.randomUUID().toString().take(8)}",
                    profileSystemId = profileSystemId,
                    rubberType = rubberType,
                    wedgeVariantId = wedgeVariantId,
                    wedgeThicknessMm = wedgeThicknessMm,
                    label = label,
                    metersInStock = initialMeters
                )
            )
        }
        return list
    }

    fun applyProductionStockDeduction(
        order: CustomerOrder,
        profileSystems: List<ProfileSystemSpec>,
        glazingCatalog: List<GlazingCatalogItem>,
        colorCatalog: List<ColorCatalogItem>,
        sillsCatalog: List<SillCatalogItem>,
        extraAccessoriesCatalog: List<ExtraAccessoryItem>,
        workServicesCatalog: List<WorkServiceItem>,
        hardwareBrandsCatalog: List<HardwareBrandItem>,
        currentStockBars: List<StockRemnantBar>,
        currentStockRubber: List<StockRubberItem>,
        financialSchemesCatalog: List<FinancialSchemeItem> = DefaultCatalogs.defaultFinancialSchemes(),
        assemblyPricingCatalog: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog()
    ): ProductionDeductionResult {
        if (order.stockDeductedForProduction) {
            return ProductionDeductionResult(
                updatedOrder = order,
                updatedStockBars = currentStockBars,
                updatedStockRubber = currentStockRubber,
                consumedFullBarsCount = 0,
                consumedRemnantsCount = 0,
                addedRemnantsCount = 0,
                deductedRubberMeters = 0.0
            )
        }

        val windowResults = order.windows.mapIndexed { idx, win ->
            WindowBomAndCostEngine.calculateWindow(
                window = win,
                profileSystems = profileSystems,
                glazingCatalog = glazingCatalog,
                colorCatalog = colorCatalog,
                sillsCatalog = sillsCatalog,
                extraAccessoriesCatalog = extraAccessoriesCatalog,
                workServicesCatalog = workServicesCatalog,
                hardwareBrandsCatalog = hardwareBrandsCatalog,
                financialSchemesCatalog = financialSchemesCatalog,
                windowIndexInOrder = idx + 1,
                assemblyPricingCatalog = assemblyPricingCatalog,
                orderLegacySystemName = order.legacySystemName,
                orderLegacyGlazingName = order.legacyGlazingName,
                orderLegacyColorName = order.legacyColorName
            )
        }

        val usableRemnants = currentStockBars.filter { !it.isFullStockBar && it.quantity > 0 }
        val autoResult = WindowBomAndCostEngine.optimizeOrderProfileCutting(
            windowResults = windowResults,
            stockRemnants = usableRemnants,
            profileSystems = profileSystems
        )
        val allocations = order.customCuttingAllocations ?: autoResult.first
        val summaries = if (order.customCuttingAllocations != null) {
            CuttingPlanManualHelper.rebuildProcurementSummaries(
                allocations = allocations,
                baseAutoSummaries = autoResult.second,
                profileSystems = profileSystems
            )
        } else {
            autoResult.second
        }

        // 1. Списание использованных остатков и возврат новых деловых остатков (>= 400 мм)
        val remnantApply = CuttingPlanManualHelper.applyWarehouseStockChanges(
            currentStock = currentStockBars,
            allocations = allocations,
            procurementSummaries = summaries,
            profileSystems = profileSystems
        )

        // 2. Списание использованных целых хлыстов со склада (включая штапики, если они ведутся на складе)
        val mutableBars = remnantApply.updatedStock.toMutableList()
        var consumedFull = 0
        allocations.filter { !it.isFromRemnantStock }.forEach { usedFullBar ->
            val sysId = profileSystems.find { it.name == usedFullBar.profileSystemName }?.id
            val fullIdx = mutableBars.indexOfFirst { stockItem ->
                stockItem.isFullStockBar &&
                    stockItem.quantity > 0 &&
                    stockItem.role == usedFullBar.role &&
                    stockItem.colorName.equals(usedFullBar.colorName, ignoreCase = true) &&
                    stockItem.lengthMm >= usedFullBar.sourceLengthMm &&
                    (sysId == null || stockItem.profileSystemId == sysId)
            }
            if (fullIdx >= 0) {
                val found = mutableBars[fullIdx]
                consumedFull += 1
                if (found.quantity <= 1) {
                    mutableBars.removeAt(fullIdx)
                } else {
                    mutableBars[fullIdx] = found.copy(quantity = found.quantity - 1)
                }
            }
        }

        // 3. Списание уплотнительной резины в метрах по всем 4 типам и подобранным клиньям
        val (_, rubberItems) = OrderProcurementHelper.calculateCornerConnectorsAndRubber(
            windowResults = windowResults,
            stockRubber = currentStockRubber
        )
        val mutableRubber = currentStockRubber.toMutableList()
        var deductedRubberM = 0.0
        rubberItems.filter { it.lengthMeters > 0.0 && it.profileSystemId.isNotBlank() && it.profileSystemId != "none" }
            .forEach { demand ->
                val idx = findMatchingRubberIndex(
                    list = mutableRubber,
                    profileSystemId = demand.profileSystemId,
                    rubberType = demand.type,
                    wedgeVariantId = demand.wedgeVariantId,
                    wedgeThicknessMm = demand.wedgeThicknessMm
                )
                if (idx >= 0) {
                    val existing = mutableRubber[idx]
                    val actuallyDeducted = minOf(existing.metersInStock.coerceAtLeast(0.0), demand.lengthMeters)
                    deductedRubberM += actuallyDeducted
                    mutableRubber[idx] = existing.copy(
                        metersInStock = (existing.metersInStock - demand.lengthMeters).coerceAtLeast(0.0)
                    )
                }
            }

        return ProductionDeductionResult(
            updatedOrder = order.copy(stockDeductedForProduction = true),
            updatedStockBars = mutableBars,
            updatedStockRubber = mutableRubber,
            consumedFullBarsCount = consumedFull,
            consumedRemnantsCount = remnantApply.consumedRemnantsCount,
            addedRemnantsCount = remnantApply.addedRemnantsCount,
            deductedRubberMeters = deductedRubberM
        )
    }

    private fun findMatchingRubberIndex(
        list: List<StockRubberItem>,
        profileSystemId: String,
        rubberType: RubberType,
        wedgeVariantId: String?,
        wedgeThicknessMm: Float?
    ): Int {
        return list.indexOfFirst { item ->
            item.profileSystemId == profileSystemId &&
                item.rubberType == rubberType &&
                (rubberType != RubberType.WEDGE_SHTAPIK ||
                    (wedgeVariantId != null && item.wedgeVariantId == wedgeVariantId) ||
                    (wedgeThicknessMm != null && item.wedgeThicknessMm != null && abs(item.wedgeThicknessMm - wedgeThicknessMm) < 0.05f))
        }
    }
}
