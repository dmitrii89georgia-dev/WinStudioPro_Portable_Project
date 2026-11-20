package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.winstudiopro.app.domain.calculator.LedgerBalanceCalculator
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.components.IosSegmentedControl
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class CashManagerTab(val titleRu: String) {
    ACCOUNTS("Счета"),
    HISTORY("История"),
    SETTINGS("Настройки")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CashManagerScreen(
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
    cashExpenseCategories: List<CashExpenseCategory>,
    onSaveFinSchemeSettings: (FinSchemeSettings) -> Unit,
    onSaveCashAccounts: (List<CashAccount>) -> Unit,
    onSaveCashTransactions: (List<CashTransaction>) -> Unit,
    onSaveCashExpenseCategories: (List<CashExpenseCategory>) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val coroutineScope = rememberCoroutineScope()
    val tabs = CashManagerTab.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    var activeQuickDialog by remember { mutableStateOf<CashQuickOpMode?>(null) }
    var showPayrollModal by remember { mutableStateOf(false) }

    val orderSnapshots = rememberOrderLedgerSnapshots(
        orders = orders,
        profileSystems = profileSystems,
        glazingCatalog = glazingCatalog,
        colorCatalog = colorCatalog,
        sillsCatalog = sillsCatalog,
        extraAccessoriesCatalog = extraAccessoriesCatalog,
        workServicesCatalog = workServicesCatalog,
        hardwareBrandsCatalog = hardwareBrandsCatalog,
        assemblyPricingCatalog = assemblyPricingCatalog,
        orderExtraServicesCatalog = orderExtraServicesCatalog,
        finSchemeSettings = finSchemeSettings,
        cashAccounts = cashAccounts,
        cashTransactions = cashTransactions
    )

    val accountBalances = remember(cashAccounts, cashTransactions) {
        LedgerBalanceCalculator.calculateAllAccountBalances(cashAccounts, cashTransactions)
    }
    val totalUsdBalance = remember(cashAccounts, cashTransactions, finSchemeSettings.gelPerUsdRate) {
        LedgerBalanceCalculator.calculateTotalBalanceInUsd(
            accounts = cashAccounts,
            transactions = cashTransactions,
            gelPerUsdRate = finSchemeSettings.gelPerUsdRate
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = "Касса и счета",
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = { showPayrollModal = true },
                    modifier = Modifier.testTag("cash_header_payroll_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "Зарплаты",
                        tint = palette.accentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Зарплаты", color = palette.accentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.cardSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            IosSegmentedControl(
                items = tabs.map { it to it.titleRu },
                selectedItem = tabs[pagerState.currentPage],
                onSelect = { tab ->
                    coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                },
                testTagPrefix = "cash_tab"
            )
        }
        HorizontalDivider(color = palette.divider, thickness = 0.7.dp)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIdx ->
            when (tabs[pageIdx]) {
                CashManagerTab.ACCOUNTS -> {
                    CashAccountsTabContent(
                        totalUsdBalance = totalUsdBalance,
                        gelPerUsdRate = finSchemeSettings.gelPerUsdRate,
                        cashAccounts = cashAccounts,
                        accountBalances = accountBalances,
                        onOpenQuickOp = { activeQuickDialog = it },
                        onOpenPayroll = { showPayrollModal = true }
                    )
                }

                CashManagerTab.HISTORY -> {
                    CashHistoryTabContent(
                        transactions = cashTransactions,
                        cashAccounts = cashAccounts,
                        expenseCategories = cashExpenseCategories,
                        orders = orders,
                        onDeleteTransaction = { txId ->
                            onSaveCashTransactions(cashTransactions.filterNot { it.id == txId })
                        }
                    )
                }

                CashManagerTab.SETTINGS -> {
                    CashSettingsTabContent(
                        finSchemeSettings = finSchemeSettings,
                        cashAccounts = cashAccounts,
                        expenseCategories = cashExpenseCategories,
                        cashTransactions = cashTransactions,
                        onSaveFinSchemeSettings = onSaveFinSchemeSettings,
                        onSaveCashAccounts = onSaveCashAccounts,
                        onSaveCashExpenseCategories = onSaveCashExpenseCategories
                    )
                }
            }
        }
    }

    when (activeQuickDialog) {
        CashQuickOpMode.INCOME -> QuickIncomeDialog(
            cashAccounts = cashAccounts,
            orderSnapshots = orderSnapshots,
            finSchemeSettings = finSchemeSettings,
            onDismiss = { activeQuickDialog = null },
            onConfirmTransaction = { tx ->
                onSaveCashTransactions(listOf(tx) + cashTransactions)
                activeQuickDialog = null
            }
        )

        CashQuickOpMode.EXPENSE -> QuickExpenseDialog(
            cashAccounts = cashAccounts,
            expenseCategories = cashExpenseCategories,
            orders = orders,
            onDismiss = { activeQuickDialog = null },
            onConfirmTransaction = { tx ->
                onSaveCashTransactions(listOf(tx) + cashTransactions)
                activeQuickDialog = null
            }
        )

        CashQuickOpMode.TRANSFER -> QuickTransferDialog(
            cashAccounts = cashAccounts,
            finSchemeSettings = finSchemeSettings,
            onDismiss = { activeQuickDialog = null },
            onConfirmTransaction = { tx ->
                onSaveCashTransactions(listOf(tx) + cashTransactions)
                activeQuickDialog = null
            }
        )

        null -> {}
    }

    if (showPayrollModal) {
        PayrollModalDialog(
            cashAccounts = cashAccounts,
            orderSnapshots = orderSnapshots,
            finSchemeSettings = finSchemeSettings,
            onDismiss = { showPayrollModal = false },
            onPayTransaction = { tx ->
                onSaveCashTransactions(listOf(tx) + cashTransactions)
            }
        )
    }
}

@Composable
private fun CashAccountsTabContent(
    totalUsdBalance: Double,
    gelPerUsdRate: Double,
    cashAccounts: List<CashAccount>,
    accountBalances: Map<String, Double>,
    onOpenQuickOp: (CashQuickOpMode) -> Unit,
    onOpenPayroll: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Итоговый баланс в эквиваленте USD
        val heroShape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(palette.accentBlue, Color(0xFF00838F))
                    )
                )
                .padding(18.dp)
                .testTag("cash_total_balance_card")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ОБЩИЙ БАЛАНС КАССЫ И СЧЕТОВ",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${fmt2(totalUsdBalance)} $",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Курс пересчёта: 1 USD = ${fmt2(gelPerUsdRate)} GEL",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.5.sp
                )
            }
        }

        // Быстрые кнопки операций + Зарплаты
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onOpenQuickOp(CashQuickOpMode.INCOME) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentGreen),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("cash_quick_income_btn")
            ) {
                Text("+ Приход", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
            Button(
                onClick = { onOpenQuickOp(CashQuickOpMode.EXPENSE) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentRed),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("cash_quick_expense_btn")
            ) {
                Text("− Расход", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
            Button(
                onClick = { onOpenQuickOp(CashQuickOpMode.TRANSFER) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("cash_quick_transfer_btn")
            ) {
                Text("⇄ Перевод", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }

        OutlinedButton(
            onClick = onOpenPayroll,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.accentOrange.copy(alpha = 0.65f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("cash_payroll_button")
        ) {
            Icon(Icons.Rounded.Groups, contentDescription = null, tint = palette.accentOrange)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Зарплаты цеха, монтажа и дивиденды",
                color = palette.accentOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
        }

        // Карточки счетов / конвертов
        cashAccounts.forEach { acc ->
            val rawBalance = accountBalances[acc.id] ?: 0.0
            val usdEquiv = LedgerBalanceCalculator.toUsdEquivalent(rawBalance, acc.currency, gelPerUsdRate)
            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.8.dp, palette.floatingBorder),
                shadowElevation = if (palette.isDark) 0.dp else 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cash_account_card_${acc.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                (if (acc.kind == CashAccountKind.BANK) palette.accentIndigo else palette.accentTeal)
                                    .copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (acc.kind == CashAccountKind.BANK) Icons.Rounded.AccountBalance else Icons.Rounded.AccountBalanceWallet,
                            contentDescription = acc.name,
                            tint = if (acc.kind == CashAccountKind.BANK) palette.accentIndigo else palette.accentTeal
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text("${acc.kind.titleRu} • ${acc.currency.code}", fontSize = 11.5.sp, color = palette.textSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${fmt2(rawBalance)} ${acc.currency.symbol}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rawBalance < 0) palette.accentRed else palette.textPrimary
                        )
                        if (acc.currency != CashCurrency.USD) {
                            Text(
                                text = "≈ ${fmt2(usdEquiv)} $",
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashHistoryTabContent(
    transactions: List<CashTransaction>,
    cashAccounts: List<CashAccount>,
    expenseCategories: List<CashExpenseCategory>,
    orders: List<CustomerOrder>,
    onDeleteTransaction: (String) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var typeFilter by remember { mutableStateOf<CashTransactionType?>(null) }
    val accById = remember(cashAccounts) { cashAccounts.associateBy { it.id } }
    val catById = remember(expenseCategories) { expenseCategories.associateBy { it.id } }
    val ordById = remember(orders) { orders.associateBy { it.id } }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    val filtered = remember(transactions, typeFilter) {
        transactions
            .filter { typeFilter == null || it.type == typeFilter }
            .sortedByDescending { it.dateMillis }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null },
                label = { Text("Все (${transactions.size})") }
            )
            CashTransactionType.entries.forEach { t ->
                FilterChip(
                    selected = typeFilter == t,
                    onClick = { typeFilter = t },
                    label = { Text(t.titleRu) },
                    modifier = Modifier.testTag("cash_history_filter_${t.name}")
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Транзакций пока нет. Нажмите «+ Приход», «− Расход» или «⇄ Перевод».",
                    color = palette.textSecondary,
                    fontSize = 13.5.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(filtered, key = { it.id }) { tx ->
                    val fromAcc = accById[tx.fromAccountId]
                    val toAcc = accById[tx.toAccountId]
                    val cat = catById[tx.categoryId]
                    val ord = ordById[tx.orderId]
                    val (badgeColor, signStr, amountStr, accountTitle) = when (tx.type) {
                        CashTransactionType.INCOME -> listOf(
                            palette.accentGreen,
                            "+",
                            "${String.format(Locale.US, "%.2f", tx.amountTo)} ${toAcc?.currency?.symbol ?: "$"}",
                            toAcc?.name ?: "Счёт"
                        )
                        CashTransactionType.EXPENSE -> listOf(
                            palette.accentRed,
                            "−",
                            "${String.format(Locale.US, "%.2f", tx.amountFrom)} ${fromAcc?.currency?.symbol ?: "$"}",
                            fromAcc?.name ?: "Счёт"
                        )
                        CashTransactionType.TRANSFER -> listOf(
                            palette.accentBlue,
                            "⇄",
                            "${String.format(Locale.US, "%.2f", tx.amountFrom)}${fromAcc?.currency?.symbol ?: ""} → ${String.format(Locale.US, "%.2f", tx.amountTo)}${toAcc?.currency?.symbol ?: ""}",
                            "${fromAcc?.name ?: "?"} → ${toAcc?.name ?: "?"}"
                        )
                    }
                    val colorTint = badgeColor as Color
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.7.dp, palette.floatingBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_tx_row_${tx.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colorTint.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(signStr as String, color = colorTint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.comment.ifBlank { tx.type.titleRu },
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = buildList {
                                    add(accountTitle as String)
                                    if (cat != null) add(cat.name)
                                    if (ord != null) add(ord.orderNumber)
                                    add(dateFmt.format(Date(tx.dateMillis)))
                                }.joinToString(" • ")
                                Text(sub, fontSize = 11.sp, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                text = if (tx.type == CashTransactionType.TRANSFER) amountStr as String else "${signStr}${amountStr}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorTint
                            )
                            IconButton(
                                onClick = { onDeleteTransaction(tx.id) },
                                modifier = Modifier.size(38.dp).testTag("cash_tx_delete_${tx.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Удалить операцию",
                                    tint = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashSettingsTabContent(
    finSchemeSettings: FinSchemeSettings,
    cashAccounts: List<CashAccount>,
    expenseCategories: List<CashExpenseCategory>,
    cashTransactions: List<CashTransaction>,
    onSaveFinSchemeSettings: (FinSchemeSettings) -> Unit,
    onSaveCashAccounts: (List<CashAccount>) -> Unit,
    onSaveCashExpenseCategories: (List<CashExpenseCategory>) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var rateText by remember(finSchemeSettings.gelPerUsdRate) {
        mutableStateOf(String.format(Locale.US, "%.2f", finSchemeSettings.gelPerUsdRate))
    }
    var newCategoryName by remember { mutableStateOf("") }
    var newAccountName by remember { mutableStateOf("") }
    var newAccountCurrency by remember { mutableStateOf(CashCurrency.USD) }
    var newAccountKind by remember { mutableStateOf(CashAccountKind.CASH) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Курс обмена валют GEL/USD
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Курс валют (для кассы и отчётов)", fontWeight = FontWeight.Bold, color = palette.accentBlue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("GEL за 1 USD") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("cash_gel_rate_input"),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val r = rateText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.1, 100.0)
                                ?: finSchemeSettings.gelPerUsdRate
                            onSaveFinSchemeSettings(finSchemeSettings.copy(gelPerUsdRate = r))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp).testTag("cash_save_rate_btn")
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }

        // 2. Настраиваемые категории расходов производства
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Категории расходов производства", fontWeight = FontWeight.Bold, color = palette.accentBlue)
                Text(
                    text = "Настраиваемый список статей для учёта цеховых расходов. Системные категории зарплат защищены от удаления.",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )
                expenseCategories.forEach { cat ->
                    Surface(
                        color = palette.secondaryBackground.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                                if (cat.isSystemPayroll) {
                                    Text("системная (для блока зарплат)", fontSize = 10.5.sp, color = palette.accentOrange)
                                }
                            }
                            if (!cat.isSystemPayroll) {
                                IconButton(
                                    onClick = {
                                        onSaveCashExpenseCategories(expenseCategories.filterNot { it.id == cat.id })
                                    },
                                    modifier = Modifier.size(40.dp).testTag("cash_delete_cat_${cat.id}")
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить категорию", tint = palette.accentRed)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Новая категория (напр. Реклама)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("cash_new_category_input"),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                val next = CashExpenseCategory(
                                    id = "cat_${UUID.randomUUID().toString().take(6)}",
                                    name = newCategoryName.trim(),
                                    isSystemPayroll = false,
                                    sortOrder = expenseCategories.size
                                )
                                onSaveCashExpenseCategories(expenseCategories + next)
                                newCategoryName = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp).testTag("cash_add_category_btn")
                    ) {
                        Text("+")
                    }
                }
            }
        }

        // 3. Счета (конверты)
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Управление счетами кассы", fontWeight = FontWeight.Bold, color = palette.accentBlue)
                cashAccounts.forEach { acc ->
                    val hasTx = cashTransactions.any { it.fromAccountId == acc.id || it.toAccountId == acc.id }
                    Surface(
                        color = palette.secondaryBackground.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                                Text("${acc.kind.titleRu} • ${acc.currency.code}", fontSize = 11.sp, color = palette.textSecondary)
                            }
                            if (cashAccounts.size > 1 && !hasTx) {
                                IconButton(
                                    onClick = { onSaveCashAccounts(cashAccounts.filterNot { it.id == acc.id }) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить счёт", tint = palette.accentRed)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newAccountName,
                    onValueChange = { newAccountName = it },
                    label = { Text("Название нового счёта") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("cash_new_account_input"),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CashCurrency.entries.forEach { cur ->
                        FilterChip(
                            selected = newAccountCurrency == cur,
                            onClick = { newAccountCurrency = cur },
                            label = { Text(cur.code) }
                        )
                    }
                    CashAccountKind.entries.forEach { k ->
                        FilterChip(
                            selected = newAccountKind == k,
                            onClick = { newAccountKind = k },
                            label = { Text(k.titleRu) }
                        )
                    }
                }
                Button(
                    onClick = {
                        if (newAccountName.isNotBlank()) {
                            val acc = CashAccount(
                                id = "acc_${UUID.randomUUID().toString().take(6)}",
                                name = newAccountName.trim(),
                                currency = newAccountCurrency,
                                kind = newAccountKind,
                                sortOrder = cashAccounts.size
                            )
                            onSaveCashAccounts(cashAccounts + acc)
                            newAccountName = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cash_add_account_btn")
                ) {
                    Text("+ Добавить счёт", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
