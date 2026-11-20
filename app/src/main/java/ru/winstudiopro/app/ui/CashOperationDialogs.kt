package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.domain.calculator.LedgerBalanceCalculator
import ru.winstudiopro.app.domain.calculator.OrderPayrollStatus
import ru.winstudiopro.app.domain.calculator.ProductionAnalyticsCalculator
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.util.Locale
import java.util.UUID

enum class CashQuickOpMode {
    INCOME,
    EXPENSE,
    TRANSFER
}

internal data class OrderLedgerSnapshot(
    val order: CustomerOrder,
    val totalWithVatUsd: Double,
    val paidUsd: Double,
    val debtUsd: Double,
    val payroll: OrderPayrollStatus
)

@Composable
internal fun rememberOrderLedgerSnapshots(
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
    cashTransactions: List<CashTransaction>
): List<OrderLedgerSnapshot> {
    return remember(
        orders,
        profileSystems,
        glazingCatalog,
        colorCatalog,
        sillsCatalog,
        extraAccessoriesCatalog,
        workServicesCatalog,
        hardwareBrandsCatalog,
        assemblyPricingCatalog,
        orderExtraServicesCatalog,
        finSchemeSettings,
        cashAccounts,
        cashTransactions
    ) {
        orders.map { order ->
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
            val paidUsd = LedgerBalanceCalculator.calculateOrderPaidUsd(
                orderId = order.id,
                accounts = cashAccounts,
                transactions = cashTransactions,
                gelPerUsdRate = finSchemeSettings.gelPerUsdRate
            )
            val debtUsd = (summary.totalWithVat - paidUsd).coerceAtLeast(0.0)
            val payroll = LedgerBalanceCalculator.calculateOrderPayrollStatus(
                order = order,
                summary = summary,
                accounts = cashAccounts,
                transactions = cashTransactions,
                gelPerUsdRate = finSchemeSettings.gelPerUsdRate
            )
            OrderLedgerSnapshot(
                order = order,
                totalWithVatUsd = summary.totalWithVat,
                paidUsd = paidUsd,
                debtUsd = debtUsd,
                payroll = payroll
            )
        }
    }
}

private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

@Composable
internal fun QuickIncomeDialog(
    cashAccounts: List<CashAccount>,
    orderSnapshots: List<OrderLedgerSnapshot>,
    finSchemeSettings: FinSchemeSettings,
    onDismiss: () -> Unit,
    onConfirmTransaction: (CashTransaction) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    if (cashAccounts.isEmpty()) {
        onDismiss()
        return
    }
    var selectedAccountId by remember { mutableStateOf(cashAccounts.first().id) }
    val selectableOrders = remember(orderSnapshots) {
        orderSnapshots.filter {
            it.order.status in ProductionAnalyticsCalculator.REVENUE_STATUSES || it.debtUsd > 0.01
        }
    }
    var selectedOrderId by remember {
        mutableStateOf<String?>(selectableOrders.firstOrNull { it.debtUsd > 0.01 }?.order?.id)
    }
    var amountText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }

    val selectedAccount = cashAccounts.find { it.id == selectedAccountId } ?: cashAccounts.first()
    val selectedOrderSnap = selectableOrders.find { it.order.id == selectedOrderId }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            color = palette.cardSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "+ Приход (оплата)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentGreen
                )

                Text("Счёт зачисления:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cashAccounts.forEach { acc ->
                        val sel = acc.id == selectedAccount.id
                        FilterChip(
                            selected = sel,
                            onClick = { selectedAccountId = acc.id },
                            label = {
                                Text(
                                    "${acc.name} (${acc.currency.symbol})",
                                    fontSize = 11.5.sp,
                                    color = if (sel) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentGreen,
                                containerColor = palette.secondaryBackground
                            ),
                            modifier = Modifier.testTag("income_acc_${acc.id}")
                        )
                    }
                }

                Text("Заказ клиента (опционально):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedOrderId == null,
                        onClick = { selectedOrderId = null },
                        label = { Text("Прочий приход", fontSize = 11.5.sp) }
                    )
                    selectableOrders.forEach { snap ->
                        val sel = snap.order.id == selectedOrderId
                        FilterChip(
                            selected = sel,
                            onClick = { selectedOrderId = snap.order.id },
                            label = {
                                Text(
                                    "${snap.order.orderNumber} (долг ${fmt2(snap.debtUsd)}$)",
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("income_order_${snap.order.id}")
                        )
                    }
                }

                if (selectedOrderSnap != null) {
                    Surface(
                        color = palette.secondaryBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("income_order_debt_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${selectedOrderSnap.order.orderNumber} • ${selectedOrderSnap.order.customerName.ifBlank { "Клиент" }}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Итого: ${fmt2(selectedOrderSnap.totalWithVatUsd)} $ • Внесено: ${fmt2(selectedOrderSnap.paidUsd)} $ • Остаток долга: ${fmt2(selectedOrderSnap.debtUsd)} $",
                                fontSize = 11.5.sp,
                                color = if (selectedOrderSnap.debtUsd > 0.01) palette.accentOrange else palette.accentGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (selectedOrderSnap.debtUsd > 0.01) {
                                TextButton(
                                    onClick = {
                                        val converted = LedgerBalanceCalculator.fromUsdEquivalent(
                                            amountUsd = selectedOrderSnap.debtUsd,
                                            targetCurrency = selectedAccount.currency,
                                            gelPerUsdRate = finSchemeSettings.gelPerUsdRate
                                        )
                                        amountText = fmt2(converted)
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.testTag("income_fill_debt_btn")
                                ) {
                                    Text(
                                        text = "Подставить остаток долга в ${selectedAccount.currency.code}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentBlue
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма (${selectedAccount.currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("income_amount_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Комментарий (напр. Аванс по договору)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Отмена", color = palette.textPrimary)
                    }
                    Button(
                        onClick = {
                            val amount = amountText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            if (amount > 0.0) {
                                val defaultComment = if (selectedOrderSnap != null) {
                                    "Оплата по заказу ${selectedOrderSnap.order.orderNumber}"
                                } else {
                                    "Приход в кассу"
                                }
                                onConfirmTransaction(
                                    CashTransaction(
                                        id = "tx_${UUID.randomUUID().toString().take(8)}",
                                        dateMillis = System.currentTimeMillis(),
                                        type = CashTransactionType.INCOME,
                                        toAccountId = selectedAccount.id,
                                        amountTo = amount,
                                        orderId = selectedOrderId,
                                        comment = commentText.trim().ifBlank { defaultComment }
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("income_save_button")
                    ) {
                        Text("Провести", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickExpenseDialog(
    cashAccounts: List<CashAccount>,
    expenseCategories: List<CashExpenseCategory>,
    orders: List<CustomerOrder>,
    onDismiss: () -> Unit,
    onConfirmTransaction: (CashTransaction) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    if (cashAccounts.isEmpty()) {
        onDismiss()
        return
    }
    var selectedAccountId by remember { mutableStateOf(cashAccounts.first().id) }
    val initialCat = expenseCategories.firstOrNull { !it.isSystemPayroll } ?: expenseCategories.firstOrNull()
    var selectedCatId by remember { mutableStateOf(initialCat?.id) }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }

    val selectedAccount = cashAccounts.find { it.id == selectedAccountId } ?: cashAccounts.first()
    val selectedCategory = expenseCategories.find { it.id == selectedCatId }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            color = palette.cardSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "− Расход производства",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentRed
                )

                Text("Счёт списания:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cashAccounts.forEach { acc ->
                        val sel = acc.id == selectedAccount.id
                        FilterChip(
                            selected = sel,
                            onClick = { selectedAccountId = acc.id },
                            label = {
                                Text(
                                    "${acc.name} (${acc.currency.symbol})",
                                    fontSize = 11.5.sp,
                                    color = if (sel) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentRed,
                                containerColor = palette.secondaryBackground
                            ),
                            modifier = Modifier.testTag("expense_acc_${acc.id}")
                        )
                    }
                }

                Text("Категория расхода:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    expenseCategories.forEach { cat ->
                        val sel = cat.id == selectedCatId
                        FilterChip(
                            selected = sel,
                            onClick = { selectedCatId = cat.id },
                            label = { Text(cat.name, fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("expense_cat_${cat.id}")
                        )
                    }
                }

                if (orders.isNotEmpty()) {
                    Text("Привязать к заказу (опционально):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedOrderId == null,
                            onClick = { selectedOrderId = null },
                            label = { Text("Общие цеховые", fontSize = 11.5.sp) }
                        )
                        orders.take(12).forEach { ord ->
                            FilterChip(
                                selected = selectedOrderId == ord.id,
                                onClick = { selectedOrderId = ord.id },
                                label = { Text(ord.orderNumber, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма списания (${selectedAccount.currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Комментарий") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Отмена", color = palette.textPrimary)
                    }
                    Button(
                        onClick = {
                            val amount = amountText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            if (amount > 0.0) {
                                val kind = when (selectedCatId) {
                                    "cat_payroll_assembly" -> CashPayrollKind.ASSEMBLY
                                    "cat_payroll_install" -> CashPayrollKind.INSTALLATION
                                    "cat_dividends" -> CashPayrollKind.DIVIDENDS
                                    else -> CashPayrollKind.NONE
                                }
                                onConfirmTransaction(
                                    CashTransaction(
                                        id = "tx_${UUID.randomUUID().toString().take(8)}",
                                        dateMillis = System.currentTimeMillis(),
                                        type = CashTransactionType.EXPENSE,
                                        fromAccountId = selectedAccount.id,
                                        amountFrom = amount,
                                        categoryId = selectedCatId,
                                        orderId = selectedOrderId,
                                        payrollKind = kind,
                                        comment = commentText.trim().ifBlank { selectedCategory?.name ?: "Расход" }
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("expense_save_button")
                    ) {
                        Text("Списать", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickTransferDialog(
    cashAccounts: List<CashAccount>,
    finSchemeSettings: FinSchemeSettings,
    onDismiss: () -> Unit,
    onConfirmTransaction: (CashTransaction) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    if (cashAccounts.size < 2) {
        onDismiss()
        return
    }
    var fromId by remember { mutableStateOf(cashAccounts[0].id) }
    var toId by remember { mutableStateOf(cashAccounts[1].id) }
    var amountFromText by remember { mutableStateOf("") }
    var amountToText by remember { mutableStateOf("") }
    var manualTargetEdited by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    val fromAcc = cashAccounts.find { it.id == fromId } ?: cashAccounts[0]
    val toAcc = cashAccounts.find { it.id == toId && it.id != fromAcc.id }
        ?: cashAccounts.first { it.id != fromAcc.id }

    fun recalcTargetIfNeeded(rawFrom: String) {
        if (manualTargetEdited) return
        val v = rawFrom.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (v <= 0.0) {
            amountToText = ""
        } else {
            val converted = LedgerBalanceCalculator.convertAmount(
                amount = v,
                fromCurrency = fromAcc.currency,
                toCurrency = toAcc.currency,
                gelPerUsdRate = finSchemeSettings.gelPerUsdRate
            )
            amountToText = fmt2(converted)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            color = palette.cardSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = palette.accentBlue)
                    Text(
                        text = "Перевод / обмен валют",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentBlue
                    )
                }

                Text("Откуда (списать):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cashAccounts.forEach { acc ->
                        val sel = acc.id == fromAcc.id
                        FilterChip(
                            selected = sel,
                            onClick = {
                                fromId = acc.id
                                if (toId == acc.id) {
                                    toId = cashAccounts.first { it.id != acc.id }.id
                                }
                                manualTargetEdited = false
                                recalcTargetIfNeeded(amountFromText)
                            },
                            label = { Text("${acc.name} (${acc.currency.symbol})", fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("transfer_from_${acc.id}")
                        )
                    }
                }

                Text("Куда (зачислить):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cashAccounts.filter { it.id != fromAcc.id }.forEach { acc ->
                        val sel = acc.id == toAcc.id
                        FilterChip(
                            selected = sel,
                            onClick = {
                                toId = acc.id
                                manualTargetEdited = false
                                recalcTargetIfNeeded(amountFromText)
                            },
                            label = { Text("${acc.name} (${acc.currency.symbol})", fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("transfer_to_${acc.id}")
                        )
                    }
                }

                OutlinedTextField(
                    value = amountFromText,
                    onValueChange = { raw ->
                        amountFromText = raw
                        recalcTargetIfNeeded(raw)
                    },
                    label = { Text("Списать (${fromAcc.currency.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_amount_from_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amountToText,
                    onValueChange = { raw ->
                        manualTargetEdited = true
                        amountToText = raw
                    },
                    label = {
                        Text(
                            if (fromAcc.currency != toAcc.currency) {
                                "Зачислить (${toAcc.currency.symbol}, курс ${fmt2(finSchemeSettings.gelPerUsdRate)})"
                            } else {
                                "Зачислить (${toAcc.currency.symbol})"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_amount_to_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Комментарий (напр. Обмен валют / инкассация)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Отмена", color = palette.textPrimary)
                    }
                    Button(
                        onClick = {
                            val amtFrom = amountFromText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            val amtTo = amountToText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: amtFrom
                            if (amtFrom > 0.0 && amtTo > 0.0) {
                                onConfirmTransaction(
                                    CashTransaction(
                                        id = "tx_${UUID.randomUUID().toString().take(8)}",
                                        dateMillis = System.currentTimeMillis(),
                                        type = CashTransactionType.TRANSFER,
                                        fromAccountId = fromAcc.id,
                                        toAccountId = toAcc.id,
                                        amountFrom = amtFrom,
                                        amountTo = amtTo,
                                        comment = commentText.trim().ifBlank {
                                            "Перевод: ${fromAcc.name} → ${toAcc.name}"
                                        }
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("transfer_save_button")
                    ) {
                        Text("Перевести", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private enum class PayrollModalTab(val titleRu: String) {
    ASSEMBLY("Сборка цеха"),
    INSTALLATION("Монтаж"),
    DIVIDENDS("Дивиденды")
}

@Composable
internal fun PayrollModalDialog(
    cashAccounts: List<CashAccount>,
    orderSnapshots: List<OrderLedgerSnapshot>,
    finSchemeSettings: FinSchemeSettings,
    onDismiss: () -> Unit,
    onPayTransaction: (CashTransaction) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    if (cashAccounts.isEmpty()) {
        onDismiss()
        return
    }
    var activeTab by remember { mutableStateOf(PayrollModalTab.ASSEMBLY) }
    var selectedAccountId by remember { mutableStateOf(cashAccounts.first().id) }
    val selectedAccount = cashAccounts.find { it.id == selectedAccountId } ?: cashAccounts.first()

    var pendingPayoutOrder by remember { mutableStateOf<OrderPayrollStatus?>(null) }
    var pendingPayoutKind by remember { mutableStateOf(CashPayrollKind.ASSEMBLY) }
    var payoutAmountText by remember { mutableStateOf("") }
    var dividendAmountText by remember { mutableStateOf("") }
    var dividendComment by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            color = palette.cardSurface,
            shadowElevation = 18.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, tint = palette.accentOrange)
                        Text(
                            text = "Зарплаты и выплаты",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = palette.textSecondary)
                    }
                }
                HorizontalDivider(color = palette.divider)

                // Счёт списания
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cashAccounts.forEach { acc ->
                        val sel = acc.id == selectedAccount.id
                        FilterChip(
                            selected = sel,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text("${acc.name} (${acc.currency.symbol})", fontSize = 11.sp) }
                        )
                    }
                }

                // Вкладки: Сборка цеха | Монтаж | Дивиденды
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PayrollModalTab.entries.forEach { tab ->
                        val sel = activeTab == tab
                        Surface(
                            color = if (sel) palette.accentBlue else palette.secondaryBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeTab = tab }
                                .testTag("payroll_tab_${tab.name}")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(
                                    text = tab.titleRu,
                                    color = if (sel) Color.White else palette.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(top = 6.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (activeTab) {
                        PayrollModalTab.ASSEMBLY -> {
                            val list = orderSnapshots.filter {
                                it.order.status in setOf(
                                    OrderStatus.PRODUCTION,
                                    OrderStatus.READY_FOR_INSTALL,
                                    OrderStatus.INSTALLATION,
                                    OrderStatus.CLOSED
                                ) && it.payroll.assemblyAccruedUsd > 0.0
                            }
                            if (list.isEmpty()) {
                                Text(
                                    "Нет заказов в производстве с начисленной зарплатой сборки.",
                                    fontSize = 13.sp,
                                    color = palette.textSecondary
                                )
                            } else {
                                list.forEach { snap ->
                                    val p = snap.payroll
                                    PayrollOrderCard(
                                        orderNumber = snap.order.orderNumber,
                                        customerName = snap.order.customerName,
                                        accruedUsd = p.assemblyAccruedUsd,
                                        paidUsd = p.assemblyPaidUsd,
                                        remainingUsd = p.assemblyRemainingUsd,
                                        onPayClick = {
                                            pendingPayoutOrder = p
                                            pendingPayoutKind = CashPayrollKind.ASSEMBLY
                                            val defaultAmt = LedgerBalanceCalculator.fromUsdEquivalent(
                                                p.assemblyRemainingUsd,
                                                selectedAccount.currency,
                                                finSchemeSettings.gelPerUsdRate
                                            )
                                            payoutAmountText = fmt2(defaultAmt)
                                        }
                                    )
                                }
                            }
                        }

                        PayrollModalTab.INSTALLATION -> {
                            val list = orderSnapshots.filter {
                                it.order.status in setOf(
                                    OrderStatus.INSTALLATION,
                                    OrderStatus.CLOSED
                                ) && it.payroll.installAccruedUsd > 0.0
                            }
                            if (list.isEmpty()) {
                                Text(
                                    "Нет заказов на монтаже/закрытых с начисленной зарплатой монтажа.",
                                    fontSize = 13.sp,
                                    color = palette.textSecondary
                                )
                            } else {
                                list.forEach { snap ->
                                    val p = snap.payroll
                                    PayrollOrderCard(
                                        orderNumber = snap.order.orderNumber,
                                        customerName = snap.order.customerName,
                                        accruedUsd = p.installAccruedUsd,
                                        paidUsd = p.installPaidUsd,
                                        remainingUsd = p.installRemainingUsd,
                                        onPayClick = {
                                            pendingPayoutOrder = p
                                            pendingPayoutKind = CashPayrollKind.INSTALLATION
                                            val defaultAmt = LedgerBalanceCalculator.fromUsdEquivalent(
                                                p.installRemainingUsd,
                                                selectedAccount.currency,
                                                finSchemeSettings.gelPerUsdRate
                                            )
                                            payoutAmountText = fmt2(defaultAmt)
                                        }
                                    )
                                }
                            }
                        }

                        PayrollModalTab.DIVIDENDS -> {
                            Text(
                                "Выплата прибыли собственнику / дивиденды из счёта «${selectedAccount.name}»:",
                                fontSize = 13.sp,
                                color = palette.textSecondary
                            )
                            OutlinedTextField(
                                value = dividendAmountText,
                                onValueChange = { dividendAmountText = it },
                                label = { Text("Сумма выплаты (${selectedAccount.currency.symbol})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("dividend_amount_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = dividendComment,
                                onValueChange = { dividendComment = it },
                                label = { Text("Комментарий (напр. Дивиденды за ноябрь)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val amt = dividendAmountText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                    if (amt > 0.0) {
                                        onPayTransaction(
                                            CashTransaction(
                                                id = "tx_${UUID.randomUUID().toString().take(8)}",
                                                dateMillis = System.currentTimeMillis(),
                                                type = CashTransactionType.EXPENSE,
                                                fromAccountId = selectedAccount.id,
                                                amountFrom = amt,
                                                categoryId = "cat_dividends",
                                                payrollKind = CashPayrollKind.DIVIDENDS,
                                                comment = dividendComment.trim().ifBlank { "Выплата / Дивиденды" }
                                            )
                                        )
                                        dividendAmountText = ""
                                        dividendComment = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dividend_pay_button")
                            ) {
                                Text("Выплатить дивиденды", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    val targetPayroll = pendingPayoutOrder
    if (targetPayroll != null) {
        AlertDialog(
            onDismissRequest = { pendingPayoutOrder = null },
            title = {
                Text(
                    "${pendingPayoutKind.titleRu}: ${targetPayroll.orderNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Счёт: ${selectedAccount.name} (${selectedAccount.currency.symbol})",
                        fontSize = 12.5.sp,
                        color = palette.textSecondary
                    )
                    OutlinedTextField(
                        value = payoutAmountText,
                        onValueChange = { payoutAmountText = it },
                        label = { Text("Сумма выплаты (${selectedAccount.currency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payroll_confirm_amount_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = payoutAmountText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        if (amt > 0.0) {
                            val catId = if (pendingPayoutKind == CashPayrollKind.ASSEMBLY) {
                                "cat_payroll_assembly"
                            } else {
                                "cat_payroll_install"
                            }
                            onPayTransaction(
                                CashTransaction(
                                    id = "tx_${UUID.randomUUID().toString().take(8)}",
                                    dateMillis = System.currentTimeMillis(),
                                    type = CashTransactionType.EXPENSE,
                                    fromAccountId = selectedAccount.id,
                                    amountFrom = amt,
                                    categoryId = catId,
                                    orderId = targetPayroll.orderId,
                                    payrollKind = pendingPayoutKind,
                                    comment = "${pendingPayoutKind.titleRu} — ${targetPayroll.orderNumber}"
                                )
                            )
                            pendingPayoutOrder = null
                        }
                    },
                    modifier = Modifier.testTag("payroll_confirm_pay_btn")
                ) {
                    Text("Выплатить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPayoutOrder = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun PayrollOrderCard(
    orderNumber: String,
    customerName: String,
    accruedUsd: Double,
    paidUsd: Double,
    remainingUsd: Double,
    onPayClick: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.secondaryBackground.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.7.dp, palette.floatingBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(orderNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                    if (customerName.isNotBlank()) {
                        Text(customerName, fontSize = 11.5.sp, color = palette.textSecondary)
                    }
                }
                Button(
                    onClick = onPayClick,
                    enabled = remainingUsd > 0.01,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (remainingUsd > 0.01) palette.accentOrange else palette.accentGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 42.dp).testTag("payroll_pay_btn_$orderNumber")
                ) {
                    Text(
                        text = if (remainingUsd > 0.01) "Выплатить" else "Выплачено",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Начислено: ${fmt2(accruedUsd)} $", fontSize = 11.5.sp, color = palette.textPrimary)
                Text("Выплачено: ${fmt2(paidUsd)} $", fontSize = 11.5.sp, color = palette.accentGreen)
                Text(
                    "Остаток: ${fmt2(remainingUsd)} $",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingUsd > 0.01) palette.accentOrange else palette.accentGreen
                )
            }
        }
    }
}
