package ru.winstudiopro.app.domain.calculator

import ru.winstudiopro.app.domain.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MonthlyRevenueBar(
    val yearMonthKey: String,
    val shortMonthLabel: String,
    val revenueUsd: Double
)

data class MarginStructureStats(
    val totalRevenueUsd: Double,
    val netProfitUsd: Double,
    val materialsAndServicesUsd: Double,
    val payrollTotalUsd: Double,
    val netProfitPercent: Double,
    val materialsPercent: Double,
    val payrollPercent: Double
)

data class ProductionAnalyticsSnapshot(
    val activeOrdersCount: Int,
    val draftOrdersCount: Int,
    val activeContractsTotalUsd: Double,
    val activePaidAdvancesUsd: Double,
    val activeRemainingDebtUsd: Double,
    val obligationsMaterialsAndServicesUsd: Double,
    val obligationsPayrollRemainingUsd: Double,
    val obligationsTotalUsd: Double,
    val totalCashBalanceUsd: Double,
    val freeMoneyUsd: Double,
    val monthlyRevenueBars: List<MonthlyRevenueBar>,
    val marginStructure: MarginStructureStats
)

/**
 * Анализатор производственных и финансовых показателей для экрана «Аналитика».
 */
object ProductionAnalyticsCalculator {

    val ACTIVE_PRODUCTION_STATUSES: Set<OrderStatus> = setOf(
        OrderStatus.CONTRACT,
        OrderStatus.PRODUCTION,
        OrderStatus.READY_FOR_INSTALL,
        OrderStatus.INSTALLATION
    )

    val DRAFT_PREP_STATUSES: Set<OrderStatus> = setOf(
        OrderStatus.DRAFT,
        OrderStatus.MEASUREMENT,
        OrderStatus.APPROVAL
    )

    val REVENUE_STATUSES: Set<OrderStatus> = ACTIVE_PRODUCTION_STATUSES + OrderStatus.CLOSED

    fun calculate(
        orders: List<CustomerOrder>,
        profileSystems: List<ProfileSystemSpec>,
        glazingCatalog: List<GlazingCatalogItem>,
        colorCatalog: List<ColorCatalogItem>,
        sillsCatalog: List<SillCatalogItem>,
        extraAccessoriesCatalog: List<ExtraAccessoryItem>,
        workServicesCatalog: List<WorkServiceItem>,
        hardwareBrandsCatalog: List<HardwareBrandItem>,
        assemblyPricingCatalog: AssemblyPricingCatalog,
        orderExtraServicesCatalog: List<OrderExtraServiceItem>,
        finSchemeSettings: FinSchemeSettings,
        cashAccounts: List<CashAccount>,
        cashTransactions: List<CashTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): ProductionAnalyticsSnapshot {
        val summariesByOrderId = orders.associate { order ->
            val winResults = order.windows.mapIndexed { idx, win ->
                WindowBomAndCostEngine.calculateWindow(
                    window = win,
                    profileSystems = profileSystems,
                    glazingCatalog = glazingCatalog,
                    colorCatalog = colorCatalog,
                    sillsCatalog = sillsCatalog,
                    extraAccessoriesCatalog = extraAccessoriesCatalog,
                    workServicesCatalog = workServicesCatalog,
                    hardwareBrandsCatalog = hardwareBrandsCatalog,
                    financialSchemesCatalog = finSchemeSettings.financialSchemes,
                    assemblyPricingCatalog = assemblyPricingCatalog,
                    windowIndexInOrder = idx + 1
                )
            }
            val summary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
                order = order,
                windowResults = winResults,
                assemblyPricingCatalog = assemblyPricingCatalog,
                orderExtraServicesCatalog = orderExtraServicesCatalog,
                finSchemeSettings = finSchemeSettings
            )
            order.id to summary
        }

        val activeOrders = orders.filter { it.status in ACTIVE_PRODUCTION_STATUSES }
        val draftOrders = orders.filter { it.status in DRAFT_PREP_STATUSES }

        var activeContractsTotal = 0.0
        var activePaidAdvances = 0.0
        var obligationsMaterials = 0.0
        var obligationsPayrollRemaining = 0.0

        activeOrders.forEach { order ->
            val sum = summariesByOrderId[order.id] ?: return@forEach
            activeContractsTotal += sum.totalWithVat
            activePaidAdvances += LedgerBalanceCalculator.calculateOrderPaidUsd(
                orderId = order.id,
                accounts = cashAccounts,
                transactions = cashTransactions,
                gelPerUsdRate = finSchemeSettings.gelPerUsdRate
            )
            obligationsMaterials += sum.totalMaterialsCost + sum.totalOrderServicesCost
            val payrollStatus = LedgerBalanceCalculator.calculateOrderPayrollStatus(
                order = order,
                summary = sum,
                accounts = cashAccounts,
                transactions = cashTransactions,
                gelPerUsdRate = finSchemeSettings.gelPerUsdRate
            )
            obligationsPayrollRemaining += payrollStatus.assemblyRemainingUsd + payrollStatus.installRemainingUsd
        }

        val activeRemainingDebt = (activeContractsTotal - activePaidAdvances).coerceAtLeast(0.0)
        val obligationsTotal = obligationsMaterials + obligationsPayrollRemaining
        val totalCashUsd = LedgerBalanceCalculator.calculateTotalBalanceInUsd(
            accounts = cashAccounts,
            transactions = cashTransactions,
            gelPerUsdRate = finSchemeSettings.gelPerUsdRate
        )
        val freeMoney = totalCashUsd - obligationsTotal

        // Динамика выручки за последние 6 месяцев
        val revenueOrders = orders.filter { it.status in REVENUE_STATUSES }
        val monthlyBars = buildMonthlyRevenueBars(
            orders = revenueOrders,
            summariesByOrderId = summariesByOrderId,
            nowMillis = nowMillis
        )

        // Структура маржинальности по вырученным/активным заказам (fallback на все при отсутствии активных)
        val marginPool = revenueOrders.ifEmpty { activeOrders }
        var totalRev = 0.0
        var totalMatAndSrv = 0.0
        var totalPayroll = 0.0
        marginPool.forEach { order ->
            val sum = summariesByOrderId[order.id] ?: return@forEach
            totalRev += sum.totalWithVat
            totalMatAndSrv += sum.totalMaterialsCost + sum.totalOrderServicesCost
            totalPayroll += sum.totalAssemblyWithFloorAndOps + sum.totalInstallationCost
        }
        val netProfit = (totalRev - totalMatAndSrv - totalPayroll).coerceAtLeast(0.0)
        val denom = (netProfit + totalMatAndSrv + totalPayroll).takeIf { it > 0.0 } ?: 1.0
        val matPct = (totalMatAndSrv / denom) * 100.0
        val payPct = (totalPayroll / denom) * 100.0
        val profitPct = (netProfit / denom) * 100.0

        return ProductionAnalyticsSnapshot(
            activeOrdersCount = activeOrders.size,
            draftOrdersCount = draftOrders.size,
            activeContractsTotalUsd = activeContractsTotal,
            activePaidAdvancesUsd = activePaidAdvances,
            activeRemainingDebtUsd = activeRemainingDebt,
            obligationsMaterialsAndServicesUsd = obligationsMaterials,
            obligationsPayrollRemainingUsd = obligationsPayrollRemaining,
            obligationsTotalUsd = obligationsTotal,
            totalCashBalanceUsd = totalCashUsd,
            freeMoneyUsd = freeMoney,
            monthlyRevenueBars = monthlyBars,
            marginStructure = MarginStructureStats(
                totalRevenueUsd = totalRev,
                netProfitUsd = netProfit,
                materialsAndServicesUsd = totalMatAndSrv,
                payrollTotalUsd = totalPayroll,
                netProfitPercent = if (totalRev > 0.0) profitPct else 0.0,
                materialsPercent = if (totalRev > 0.0) matPct else 0.0,
                payrollPercent = if (totalRev > 0.0) payPct else 0.0
            )
        )
    }

    private fun buildMonthlyRevenueBars(
        orders: List<CustomerOrder>,
        summariesByOrderId: Map<String, OrderFinancialSummary>,
        nowMillis: Long
    ): List<MonthlyRevenueBar> {
        val monthNamesRu = listOf(
            "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
        )
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -5)
        }
        val buckets = LinkedHashMap<String, Pair<String, Double>>()
        repeat(6) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val key = String.format(Locale.US, "%04d-%02d", y, m + 1)
            buckets[key] = monthNamesRu[m] to 0.0
            cal.add(Calendar.MONTH, 1)
        }

        orders.forEach { order ->
            val sum = summariesByOrderId[order.id] ?: return@forEach
            val ts = resolveOrderTimestamp(order)
            val oCal = Calendar.getInstance().apply { timeInMillis = ts }
            val key = String.format(
                Locale.US,
                "%04d-%02d",
                oCal.get(Calendar.YEAR),
                oCal.get(Calendar.MONTH) + 1
            )
            val existing = buckets[key]
            if (existing != null) {
                buckets[key] = existing.first to (existing.second + sum.totalWithVat)
            }
        }

        return buckets.entries.map { (k, pair) ->
            MonthlyRevenueBar(
                yearMonthKey = k,
                shortMonthLabel = pair.first,
                revenueUsd = pair.second
            )
        }
    }

    private fun resolveOrderTimestamp(order: CustomerOrder): Long {
        if (order.contractDateMillis != null && order.contractDateMillis > 0L) {
            return order.contractDateMillis
        }
        val formats = listOf("dd.MM.yyyy HH:mm", "dd.MM.yyyy", "yyyy-MM-dd")
        for (fmt in formats) {
            try {
                val parsed = SimpleDateFormat(fmt, Locale.getDefault()).parse(order.createdDate.trim())
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
