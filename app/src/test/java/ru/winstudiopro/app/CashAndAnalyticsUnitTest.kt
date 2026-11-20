package ru.winstudiopro.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.data.PersistedAppState
import ru.winstudiopro.app.domain.calculator.LedgerBalanceCalculator
import ru.winstudiopro.app.domain.calculator.ProductionAnalyticsCalculator
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CashAndAnalyticsUnitTest {

    private fun sampleOrder(
        id: String,
        number: String,
        status: OrderStatus,
        createdDate: String = "15.11.2026",
        contractDateMillis: Long? = null
    ): CustomerOrder {
        val sys = DefaultCatalogs.defaultProfileSystems().first()
        val w1 = WindowItem(
            id = "${id}_w1",
            name = "Конструкция 1",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = sys.id,
            glazingId = DefaultCatalogs.defaultGlazingItems().first().id,
            colorId = sys.colors.first().id
        )
        val w2 = WindowItem(
            id = "${id}_w2",
            name = "Конструкция 2",
            quantity = 2,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = sys.id,
            glazingId = DefaultCatalogs.defaultGlazingItems().first().id,
            colorId = sys.colors.first().id
        )
        return CustomerOrder(
            id = id,
            orderNumber = number,
            customerName = "Иван Петров",
            customerPhone = "+995 555 11 22 33",
            address = "г. Батуми, ул. Руставели 10",
            createdDate = createdDate,
            contractDateMillis = contractDateMillis,
            status = status,
            windows = listOf(w1, w2),
            taxVatPercent = 18.0
        )
    }

    @Test
    fun `ledger calculator handles multicurrency balances order payments and payroll accruals`() {
        val accounts = DefaultCashCatalogs.defaultCashAccounts()
        val order = sampleOrder("ord_1", "Д-100", OrderStatus.PRODUCTION)
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()
        val asmCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog()
        val fin = FinSchemeSettings(gelPerUsdRate = 2.50)

        val winResults = order.windows.mapIndexed { idx, w ->
            WindowBomAndCostEngine.calculateWindow(
                window = w,
                profileSystems = profiles,
                glazingCatalog = glazing,
                colorCatalog = colors,
                sillsCatalog = sills,
                assemblyPricingCatalog = asmCatalog,
                windowIndexInOrder = idx + 1
            )
        }
        val summary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = order,
            windowResults = winResults,
            assemblyPricingCatalog = asmCatalog,
            finSchemeSettings = fin
        )

        val txs = listOf(
            CashTransaction(
                id = "t1",
                type = CashTransactionType.INCOME,
                toAccountId = "acc_cash_usd",
                amountTo = 1000.0,
                orderId = order.id
            ),
            CashTransaction(
                id = "t2",
                type = CashTransactionType.INCOME,
                toAccountId = "acc_cash_gel",
                amountTo = 500.0, // 500 / 2.5 = 200 USD
                orderId = order.id
            ),
            CashTransaction(
                id = "t3",
                type = CashTransactionType.TRANSFER,
                fromAccountId = "acc_cash_usd",
                toAccountId = "acc_bank_gel",
                amountFrom = 100.0,
                amountTo = 250.0 // 100 USD
            ),
            CashTransaction(
                id = "t4",
                type = CashTransactionType.EXPENSE,
                fromAccountId = "acc_cash_usd",
                amountFrom = 40.0,
                categoryId = "cat_payroll_assembly",
                orderId = order.id,
                payrollKind = CashPayrollKind.ASSEMBLY
            )
        )

        val balances = LedgerBalanceCalculator.calculateAllAccountBalances(accounts, txs)
        assertEquals(860.0, balances["acc_cash_usd"] ?: 0.0, 0.01)
        assertEquals(500.0, balances["acc_cash_gel"] ?: 0.0, 0.01)
        assertEquals(250.0, balances["acc_bank_gel"] ?: 0.0, 0.01)

        // Общий баланс USD = 860 + (500 + 250)/2.5 = 860 + 300 = 1160
        val totalUsd = LedgerBalanceCalculator.calculateTotalBalanceInUsd(accounts, txs, 2.50)
        assertEquals(1160.0, totalUsd, 0.01)

        // Оплачено по заказу = 1000 + 500/2.5 = 1200 USD
        val orderPaid = LedgerBalanceCalculator.calculateOrderPaidUsd(order.id, accounts, txs, 2.50)
        assertEquals(1200.0, orderPaid, 0.01)

        val payroll = LedgerBalanceCalculator.calculateOrderPayrollStatus(
            order = order,
            summary = summary,
            accounts = accounts,
            transactions = txs,
            gelPerUsdRate = 2.50
        )
        assertEquals(summary.totalAssemblyWithFloorAndOps, payroll.assemblyAccruedUsd, 0.01)
        assertEquals(40.0, payroll.assemblyPaidUsd, 0.01)
        assertEquals((summary.totalAssemblyWithFloorAndOps - 40.0).coerceAtLeast(0.0), payroll.assemblyRemainingUsd, 0.01)
    }

    @Test
    fun `production analytics filters active vs draft orders and calculates free money and margin`() {
        val nowCal = Calendar.getInstance()
        val activeOrder = sampleOrder(
            id = "ord_active",
            number = "АКТ-1",
            status = OrderStatus.CONTRACT,
            contractDateMillis = nowCal.timeInMillis
        )
        val draftOrder = sampleOrder(
            id = "ord_draft",
            number = "ЧЕРН-1",
            status = OrderStatus.DRAFT,
            contractDateMillis = nowCal.timeInMillis
        )

        val accounts = DefaultCashCatalogs.defaultCashAccounts()
        val txs = listOf(
            CashTransaction(
                id = "tx_in",
                type = CashTransactionType.INCOME,
                toAccountId = "acc_cash_usd",
                amountTo = 5000.0,
                orderId = activeOrder.id
            )
        )

        val snapshot = ProductionAnalyticsCalculator.calculate(
            orders = listOf(activeOrder, draftOrder),
            profileSystems = DefaultCatalogs.defaultProfileSystems(),
            glazingCatalog = DefaultCatalogs.defaultGlazingItems(),
            colorCatalog = DefaultCatalogs.defaultColors(),
            sillsCatalog = DefaultCatalogs.defaultSills(),
            extraAccessoriesCatalog = DefaultCatalogs.defaultExtraAccessories(),
            workServicesCatalog = DefaultCatalogs.defaultWorkServices(),
            hardwareBrandsCatalog = DefaultCatalogs.defaultHardwareBrands(),
            assemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
            orderExtraServicesCatalog = DefaultCatalogs.defaultOrderExtraServices(),
            finSchemeSettings = FinSchemeSettings(),
            cashAccounts = accounts,
            cashTransactions = txs,
            nowMillis = nowCal.timeInMillis
        )

        assertEquals(1, snapshot.activeOrdersCount)
        assertEquals(1, snapshot.draftOrdersCount)
        assertEquals(5000.0, snapshot.activePaidAdvancesUsd, 0.01)
        assertEquals(5000.0, snapshot.totalCashBalanceUsd, 0.01)
        assertEquals(snapshot.totalCashBalanceUsd - snapshot.obligationsTotalUsd, snapshot.freeMoneyUsd, 0.01)
        assertEquals(6, snapshot.monthlyRevenueBars.size)
        assertTrue(snapshot.monthlyRevenueBars.last().revenueUsd > 0.0)
        val pctSum = snapshot.marginStructure.netProfitPercent +
            snapshot.marginStructure.materialsPercent +
            snapshot.marginStructure.payrollPercent
        assertEquals(100.0, pctSum, 0.2)
    }

    @Test
    fun `sqlite migrates v6 to v7 with default cash accounts and backup json v5 roundtrips`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_v6_migration.db")
        dbFile.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (id INTEGER PRIMARY KEY CHECK (id = 1), json_payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS assembly_presets (id INTEGER PRIMARY KEY CHECK (id = 1), json_payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS order_extra_services (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, name TEXT NOT NULL, default_price REAL NOT NULL, apply_to_whole_order INTEGER NOT NULL DEFAULT 1)")
        db.version = 6

        AppStateStorage.migrateDatabaseV6ToV7(db)

        val curAcc = db.rawQuery("SELECT COUNT(*) FROM cash_accounts", null)
        curAcc.use {
            assertTrue(it.moveToFirst())
            assertEquals(4, it.getInt(0))
        }
        val curCat = db.rawQuery("SELECT COUNT(*) FROM cash_expense_categories", null)
        curCat.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getInt(0) >= 8)
        }
        db.close()

        // Проверка полного JSON-бэкапа (roundtrip)
        val initial = AppStateStorage.createDefaultInitialState()
        val customFin = initial.finSchemeSettings.copy(baseMarkupMultiplier = 1.55, gelPerUsdRate = 2.85)
        val customTx = CashTransaction(
            id = "tx_rt_1",
            type = CashTransactionType.INCOME,
            toAccountId = "acc_cash_usd",
            amountTo = 777.0,
            comment = "Тест бэкапа"
        )
        AppStateStorage.saveState(
            context,
            PersistedAppState(
                profileSystems = initial.profileSystems,
                orders = initial.orders,
                stockRemnants = initial.stockRemnants,
                finSchemeSettings = customFin,
                cashAccounts = initial.cashAccounts,
                cashTransactions = listOf(customTx),
                cashExpenseCategories = initial.cashExpenseCategories
            )
        )
        val json = AppStateStorage.exportFullBackupJsonString(context)
        val restored = AppStateStorage.importFullBackupJsonString(context, json)
        assertEquals(1.55, restored.finSchemeSettings.baseMarkupMultiplier, 0.001)
        assertEquals(2.85, restored.finSchemeSettings.gelPerUsdRate, 0.001)
        assertEquals(1, restored.cashTransactions.size)
        assertEquals(777.0, restored.cashTransactions.first().amountTo, 0.01)
    }

    @Test
    fun `compact commercial proposal builds 2-per-row cells without door-window labels and conditional VAT`() {
        val order = sampleOrder("ord_pdf", "КП-2026", OrderStatus.CONTRACT)
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()
        val sills = DefaultCatalogs.defaultSills()
        val fin = FinSchemeSettings(baseMarkupMultiplier = 1.40)

        val winResults = order.windows.mapIndexed { idx, w ->
            WindowBomAndCostEngine.calculateWindow(
                window = w,
                profileSystems = profiles,
                glazingCatalog = glazing,
                colorCatalog = colors,
                sillsCatalog = sills,
                windowIndexInOrder = idx + 1
            )
        }
        val finSummary = WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = order,
            windowResults = winResults,
            finSchemeSettings = fin
        )

        // Сетка 2 конструкции в ряд
        val rows = winResults.chunked(2)
        assertEquals(1, rows.size)
        assertEquals(2, rows.first().size)

        val cell1 = PdfReportGenerator.buildCompactProposalCellModel(
            indexOneBased = 1,
            order = order,
            res = winResults[0],
            finSummary = finSummary
        )
        val cell2 = PdfReportGenerator.buildCompactProposalCellModel(
            indexOneBased = 2,
            order = order,
            res = winResults[1],
            finSummary = finSummary
        )

        assertEquals("№1", cell1.headerNumber)
        assertEquals("№2", cell2.headerNumber)
        assertFalse(cell1.headerNumber.contains("Окно", ignoreCase = true))
        assertFalse(cell1.headerNumber.contains("Дверь", ignoreCase = true))
        assertTrue(cell1.headerDimsQty.contains("1 шт"))
        assertTrue(cell2.headerDimsQty.contains("2 шт"))
        assertTrue(cell1.materialLines.any { it.startsWith("Профиль:") })
        assertTrue(cell1.materialLines.any { it.startsWith("Заполнение:") })
        assertTrue(cell1.materialLines.any { it.startsWith("Фурнитура:") })

        val priceLabels = cell1.priceRows.map { it.first }
        assertTrue(priceLabels.contains("Площадь"))
        assertTrue(priceLabels.contains("Базовая цена"))
        assertTrue(priceLabels.contains("Итого"))
        assertFalse(priceLabels.any { it.contains("Себестоимость", ignoreCase = true) })

        // Проверка сводной таблицы с НДС 18%
        val summaryWithVat = PdfReportGenerator.buildCompactProposalSummaryRows(
            order = order,
            windowResults = winResults,
            finSchemeSettings = fin
        )
        val labelsWithVat = summaryWithVat.map { it.first }
        assertTrue(labelsWithVat.contains("Всего изделий (шт)"))
        assertTrue(labelsWithVat.contains("Общая площадь (м²)"))
        assertTrue(labelsWithVat.contains("Итоговая сумма без НДС"))
        assertTrue(labelsWithVat.any { it.startsWith("НДС (18%)") })
        assertTrue(labelsWithVat.contains("ИТОГО К ОПЛАТЕ С НДС"))

        // Проверка без НДС (0%) — строка НДС не выводится
        val orderNoVat = order.copy(taxVatPercent = 0.0)
        val summaryNoVat = PdfReportGenerator.buildCompactProposalSummaryRows(
            order = orderNoVat,
            windowResults = winResults,
            finSchemeSettings = fin
        )
        assertFalse(summaryNoVat.any { it.first.startsWith("НДС") })
    }
}
