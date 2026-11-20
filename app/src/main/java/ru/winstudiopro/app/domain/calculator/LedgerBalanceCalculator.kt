package ru.winstudiopro.app.domain.calculator

import ru.winstudiopro.app.domain.model.CashAccount
import ru.winstudiopro.app.domain.model.CashCurrency
import ru.winstudiopro.app.domain.model.CashPayrollKind
import ru.winstudiopro.app.domain.model.CashTransaction
import ru.winstudiopro.app.domain.model.CashTransactionType
import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.OrderFinancialSummary

data class OrderPayrollStatus(
    val orderId: String,
    val orderNumber: String,
    val customerName: String,
    val assemblyAccruedUsd: Double,
    val assemblyPaidUsd: Double,
    val assemblyRemainingUsd: Double,
    val installAccruedUsd: Double,
    val installPaidUsd: Double,
    val installRemainingUsd: Double
)

/**
 * Единый детерминированный калькулятор балансов, долгов и зарплат из журнала транзакций (архитектура My Stash).
 */
object LedgerBalanceCalculator {

    fun calculateAccountBalance(
        accountId: String,
        transactions: List<CashTransaction>
    ): Double {
        var balance = 0.0
        transactions.forEach { tx ->
            when (tx.type) {
                CashTransactionType.INCOME -> {
                    if (tx.toAccountId == accountId) {
                        balance += tx.amountTo
                    }
                }
                CashTransactionType.EXPENSE -> {
                    if (tx.fromAccountId == accountId) {
                        balance -= tx.amountFrom
                    }
                }
                CashTransactionType.TRANSFER -> {
                    if (tx.fromAccountId == accountId) {
                        balance -= tx.amountFrom
                    }
                    if (tx.toAccountId == accountId) {
                        balance += tx.amountTo
                    }
                }
            }
        }
        return balance
    }

    fun calculateAllAccountBalances(
        accounts: List<CashAccount>,
        transactions: List<CashTransaction>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        accounts.forEach { acc ->
            result[acc.id] = 0.0
        }
        transactions.forEach { tx ->
            when (tx.type) {
                CashTransactionType.INCOME -> {
                    val toId = tx.toAccountId
                    if (toId != null && result.containsKey(toId)) {
                        result[toId] = (result[toId] ?: 0.0) + tx.amountTo
                    }
                }
                CashTransactionType.EXPENSE -> {
                    val fromId = tx.fromAccountId
                    if (fromId != null && result.containsKey(fromId)) {
                        result[fromId] = (result[fromId] ?: 0.0) - tx.amountFrom
                    }
                }
                CashTransactionType.TRANSFER -> {
                    val fromId = tx.fromAccountId
                    if (fromId != null && result.containsKey(fromId)) {
                        result[fromId] = (result[fromId] ?: 0.0) - tx.amountFrom
                    }
                    val toId = tx.toAccountId
                    if (toId != null && result.containsKey(toId)) {
                        result[toId] = (result[toId] ?: 0.0) + tx.amountTo
                    }
                }
            }
        }
        return result
    }

    fun toUsdEquivalent(
        amount: Double,
        currency: CashCurrency,
        gelPerUsdRate: Double
    ): Double {
        return when (currency) {
            CashCurrency.USD -> amount
            CashCurrency.GEL -> amount / gelPerUsdRate.coerceAtLeast(0.01)
        }
    }

    fun fromUsdEquivalent(
        amountUsd: Double,
        targetCurrency: CashCurrency,
        gelPerUsdRate: Double
    ): Double {
        return when (targetCurrency) {
            CashCurrency.USD -> amountUsd
            CashCurrency.GEL -> amountUsd * gelPerUsdRate.coerceAtLeast(0.01)
        }
    }

    fun convertAmount(
        amount: Double,
        fromCurrency: CashCurrency,
        toCurrency: CashCurrency,
        gelPerUsdRate: Double
    ): Double {
        if (fromCurrency == toCurrency) return amount
        val usd = toUsdEquivalent(amount, fromCurrency, gelPerUsdRate)
        return fromUsdEquivalent(usd, toCurrency, gelPerUsdRate)
    }

    fun calculateTotalBalanceInUsd(
        accounts: List<CashAccount>,
        transactions: List<CashTransaction>,
        gelPerUsdRate: Double
    ): Double {
        val balances = calculateAllAccountBalances(accounts, transactions)
        return accounts.sumOf { acc ->
            val rawBal = balances[acc.id] ?: 0.0
            toUsdEquivalent(rawBal, acc.currency, gelPerUsdRate)
        }
    }

    fun calculateOrderPaidUsd(
        orderId: String,
        accounts: List<CashAccount>,
        transactions: List<CashTransaction>,
        gelPerUsdRate: Double
    ): Double {
        val accById = accounts.associateBy { it.id }
        return transactions
            .filter { it.type == CashTransactionType.INCOME && it.orderId == orderId }
            .sumOf { tx ->
                val cur = accById[tx.toAccountId]?.currency ?: CashCurrency.USD
                toUsdEquivalent(tx.amountTo, cur, gelPerUsdRate)
            }
    }

    fun calculateOrderPayrollStatus(
        order: CustomerOrder,
        summary: OrderFinancialSummary,
        accounts: List<CashAccount>,
        transactions: List<CashTransaction>,
        gelPerUsdRate: Double
    ): OrderPayrollStatus {
        val accById = accounts.associateBy { it.id }
        val assemblyAccrued = summary.totalAssemblyWithFloorAndOps
        val installAccrued = summary.totalInstallationCost

        var assemblyPaid = 0.0
        var installPaid = 0.0

        transactions.forEach { tx ->
            if (tx.type == CashTransactionType.EXPENSE && tx.orderId == order.id) {
                val cur = accById[tx.fromAccountId]?.currency ?: CashCurrency.USD
                val amountUsd = toUsdEquivalent(tx.amountFrom, cur, gelPerUsdRate)
                val isAssembly = tx.payrollKind == CashPayrollKind.ASSEMBLY ||
                    tx.categoryId == "cat_payroll_assembly"
                val isInstall = tx.payrollKind == CashPayrollKind.INSTALLATION ||
                    tx.categoryId == "cat_payroll_install"
                if (isAssembly) {
                    assemblyPaid += amountUsd
                } else if (isInstall) {
                    installPaid += amountUsd
                }
            }
        }

        return OrderPayrollStatus(
            orderId = order.id,
            orderNumber = order.orderNumber,
            customerName = order.customerName,
            assemblyAccruedUsd = assemblyAccrued,
            assemblyPaidUsd = assemblyPaid,
            assemblyRemainingUsd = (assemblyAccrued - assemblyPaid).coerceAtLeast(0.0),
            installAccruedUsd = installAccrued,
            installPaidUsd = installPaid,
            installRemainingUsd = (installAccrued - installPaid).coerceAtLeast(0.0)
        )
    }
}
