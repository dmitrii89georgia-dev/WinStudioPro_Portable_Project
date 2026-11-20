package ru.winstudiopro.app.domain.model

import java.util.UUID

/**
 * Пресет клиентской скидки во вкладке «Фин схема».
 */
data class DiscountPresetItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val discountPercent: Double,
    val isDealer: Boolean = false
)

/**
 * Глобальные настройки финансовой схемы и ценообразования от себестоимости.
 */
data class FinSchemeSettings(
    val baseMarkupMultiplier: Double = 1.40,
    val markupPresets: List<Double> = listOf(1.25, 1.35, 1.40, 1.50, 1.65),
    val discountPresets: List<DiscountPresetItem> = DefaultCashCatalogs.defaultDiscountPresets(),
    val financialSchemes: List<FinancialSchemeItem> = DefaultCatalogs.defaultFinancialSchemes(),
    val defaultProfileSystemId: String = "",
    val defaultVatPercent: Double = 18.0,
    val warnBelowPrimeCost: Boolean = true,
    val gelPerUsdRate: Double = 2.70
)

/**
 * Валюты кассы и счетов (базовая валюта расчётов заказов — USD / $).
 */
enum class CashCurrency(
    val code: String,
    val symbol: String,
    val titleRu: String
) {
    USD("USD", "$", "Доллар США ($)"),
    GEL("GEL", "₾", "Грузинский лари (₾)");

    companion object {
        fun fromCode(raw: String?): CashCurrency {
            return entries.find { it.code.equals(raw?.trim(), ignoreCase = true) } ?: USD
        }
    }
}

enum class CashAccountKind(val titleRu: String) {
    CASH("Наличные"),
    BANK("Банк");

    companion object {
        fun fromStorage(raw: String?): CashAccountKind {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: CASH
        }
    }
}

/**
 * Счёт (конверт) модуля Кассы по архитектуре My Stash.
 */
data class CashAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val currency: CashCurrency,
    val kind: CashAccountKind = CashAccountKind.CASH,
    val sortOrder: Int = 0
)

/**
 * Тип кассовой транзакции по архитектуре My Stash.
 */
enum class CashTransactionType(val titleRu: String) {
    INCOME("Приход"),
    EXPENSE("Расход"),
    TRANSFER("Перевод");

    companion object {
        fun fromStorage(raw: String?): CashTransactionType {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EXPENSE
        }
    }
}

/**
 * Привязка расходной транзакции к выплате зарплаты / дивидендов.
 */
enum class CashPayrollKind(val titleRu: String) {
    NONE("Обычная операция"),
    ASSEMBLY("Зарплата сборки"),
    INSTALLATION("Зарплата монтажа"),
    DIVIDENDS("Выплата / Дивиденды");

    companion object {
        fun fromStorage(raw: String?): CashPayrollKind {
            return entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
        }
    }
}

/**
 * Настраиваемая категория расходов производства.
 */
data class CashExpenseCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isSystemPayroll: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * Транзакция кассы (приход, расход, перевод между счетами/валютами, выплата ЗП).
 */
data class CashTransaction(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long = System.currentTimeMillis(),
    val type: CashTransactionType,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val amountFrom: Double = 0.0,
    val amountTo: Double = 0.0,
    val categoryId: String? = null,
    val orderId: String? = null,
    val payrollKind: CashPayrollKind = CashPayrollKind.NONE,
    val comment: String = ""
)

object DefaultCashCatalogs {
    fun defaultDiscountPresets(): List<DiscountPresetItem> = listOf(
        DiscountPresetItem("disc_0", "Без скидки", 0.0, false),
        DiscountPresetItem("disc_5", "Скидка 5%", 5.0, false),
        DiscountPresetItem("disc_10", "Скидка 10%", 10.0, false),
        DiscountPresetItem("disc_15", "Скидка 15%", 15.0, false),
        DiscountPresetItem("disc_20_dealer", "Дилерская цена", 20.0, true)
    )

    fun defaultCashAccounts(): List<CashAccount> = listOf(
        CashAccount("acc_cash_usd", "Касса Наличные USD", CashCurrency.USD, CashAccountKind.CASH, 0),
        CashAccount("acc_cash_gel", "Касса Наличные GEL", CashCurrency.GEL, CashAccountKind.CASH, 1),
        CashAccount("acc_bank_usd", "Банковский счёт USD", CashCurrency.USD, CashAccountKind.BANK, 2),
        CashAccount("acc_bank_gel", "Банковский счёт GEL", CashCurrency.GEL, CashAccountKind.BANK, 3)
    )

    fun defaultExpenseCategories(): List<CashExpenseCategory> = listOf(
        CashExpenseCategory("cat_materials", "Закупка материалов", isSystemPayroll = false, sortOrder = 0),
        CashExpenseCategory("cat_rent_power", "Аренда и свет", isSystemPayroll = false, sortOrder = 1),
        CashExpenseCategory("cat_consumables", "Расходники цеха", isSystemPayroll = false, sortOrder = 2),
        CashExpenseCategory("cat_transport", "Транспорт", isSystemPayroll = false, sortOrder = 3),
        CashExpenseCategory("cat_payroll_assembly", "Зарплата сборки", isSystemPayroll = true, sortOrder = 4),
        CashExpenseCategory("cat_payroll_install", "Зарплата монтажа", isSystemPayroll = true, sortOrder = 5),
        CashExpenseCategory("cat_dividends", "Выплата / Дивиденды", isSystemPayroll = true, sortOrder = 6),
        CashExpenseCategory("cat_other", "Прочее", isSystemPayroll = false, sortOrder = 7)
    )
}
