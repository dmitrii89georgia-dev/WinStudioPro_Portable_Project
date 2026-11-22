package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.domain.calculator.OrderArchiveHelper
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.cad.WindowMiniThumbnail
import ru.winstudiopro.app.ui.components.IosSegmentedControl
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.i18n.normalizeCurrencyDisplay
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class OrdersListTab {
    ACTIVE,
    ARCHIVE
}

// =========================================================================================
// ЭКРАН СПИСКА ЗАКАЗОВ (НОВЫЕ СВЕРХУ, ФИЛЬТР ПО СТАТУСАМ, СМАЙЛИКИ СРОКОВ, МИНИАТЮРЫ)
// =========================================================================================
@Composable
internal fun OrdersListScreen(
    orders: List<CustomerOrder>,
    onBack: () -> Unit,
    onNewOrder: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onUpdateOrders: (List<CustomerOrder>) -> Unit,
    onExportOrder: (CustomerOrder) -> Unit = {},
    onImportOrder: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableStateOf(OrdersListTab.ACTIVE) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatuses by remember {
        mutableStateOf(AppStateStorage.loadOrdersFilterStatuses(context))
    }
    var showFilterDialog by remember { mutableStateOf(false) }
    var contractDialogTargetOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var deleteDraftTarget by remember { mutableStateOf<CustomerOrder?>(null) }
    var archiveOrderTarget by remember { mutableStateOf<CustomerOrder?>(null) }
    var deleteForeverTarget by remember { mutableStateOf<CustomerOrder?>(null) }

    val activeOrdersCount = remember(orders) { orders.count { !it.isArchived } }
    val archiveOrdersCount = remember(orders) { orders.count { it.isArchived } }

    // Заказы всегда отображаются в порядке от нового к старому (новый наверху)
    val filtered = remember(orders, selectedTab, searchQuery, selectedStatuses) {
        orders.filter { ord ->
            val tabMatches = when (selectedTab) {
                OrdersListTab.ACTIVE -> !ord.isArchived && ord.status in selectedStatuses
                OrdersListTab.ARCHIVE -> ord.isArchived
            }
            tabMatches && (
                searchQuery.isBlank() ||
                    ord.orderNumber.contains(searchQuery, ignoreCase = true) ||
                    ord.customerName.contains(searchQuery, ignoreCase = true) ||
                    ord.customerPhone.contains(searchQuery, ignoreCase = true) ||
                    ord.address.contains(searchQuery, ignoreCase = true)
                )
        }
    }

    val isPartialFilter = selectedTab == OrdersListTab.ACTIVE && selectedStatuses.size < OrderStatus.entries.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = "${strings.ordersTitle} (${orders.size})",
            onBack = onBack
        ) {
            Surface(
                color = palette.accentTeal.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onImportOrder() }
                    .testTag("orders_import_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = strings.importOrderHeaderBtn,
                        tint = palette.accentTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.importOrderHeaderBtn,
                        color = palette.accentTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            Surface(
                color = palette.accentBlue,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNewOrder() }
                    .testTag("orders_list_add_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = strings.newOrderTitle, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Переключатель «Активные» / «Архив»
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            IosSegmentedControl(
                items = listOf(
                    OrdersListTab.ACTIVE to "${strings.ordersTabActive} ($activeOrdersCount)",
                    OrdersListTab.ARCHIVE to "${strings.ordersTabArchive} ($archiveOrdersCount)"
                ),
                selectedItem = selectedTab,
                onSelect = { selectedTab = it },
                testTagPrefix = "orders_tab"
            )
        }

        // Строка поиска + кнопка «Фильтр» справа (для активных заказов)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(strings.searchOrdersHint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(14.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textSecondary)
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            if (selectedTab == OrdersListTab.ACTIVE) {
                Surface(
                    color = if (isPartialFilter) palette.accentBlue else palette.cardSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isPartialFilter) 0.dp else 1.dp,
                        color = if (isPartialFilter) Color.Transparent else palette.floatingBorder
                    ),
                    shadowElevation = if (palette.isDark) 0.dp else 1.5.dp,
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showFilterDialog = true }
                        .testTag("orders_filter_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = strings.filterBtn,
                            tint = if (isPartialFilter) Color.White else palette.accentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isPartialFilter) "${strings.filterBtn} (${selectedStatuses.size})" else strings.filterBtn,
                            color = if (isPartialFilter) Color.White else palette.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (selectedTab == OrdersListTab.ARCHIVE) Icons.Rounded.Inventory2 else Icons.Rounded.FilterAltOff,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (selectedTab == OrdersListTab.ARCHIVE) strings.emptyArchiveOrdersTitle else strings.emptyActiveOrdersTitle,
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isPartialFilter) {
                        Button(
                            onClick = {
                                val all = OrderStatus.entries.toSet()
                                selectedStatuses = all
                                AppStateStorage.saveOrdersFilterStatuses(context, all)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(strings.resetFilterBtn, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { ord ->
                    val curSym = normalizeCurrencyDisplay(ord.currencySymbol)
                    val statusColor = Color(ord.status.badgeColorHex)
                    val statusLabel = if (strings.language == AppLanguage.EN) ord.status.titleEn else ord.status.titleRu
                    val deadlineState = remember(ord.status, ord.contractDateMillis, ord.contractTermDays) {
                        ord.computeDeadlineState()
                    }
                    var statusDropdownExpanded by remember { mutableStateOf(false) }

                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.85f)),
                        shadowElevation = if (palette.isDark) 0.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenOrder(ord.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Строка 1: Название заказа слева + Раскрывающаяся цветная кнопка статуса справа
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ord.orderNumber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = palette.accentBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )

                                Box {
                                    Surface(
                                        color = statusColor,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .heightIn(min = 34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { statusDropdownExpanded = true }
                                            .testTag("order_status_dropdown_${ord.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDropDown,
                                                contentDescription = statusLabel,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = statusDropdownExpanded,
                                        onDismissRequest = { statusDropdownExpanded = false }
                                    ) {
                                        OrderStatus.entries.forEach { st ->
                                            val stCol = Color(st.badgeColorHex)
                                            val stTitle = if (strings.language == AppLanguage.EN) st.titleEn else st.titleRu
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(stCol, CircleShape)
                                                        )
                                                        Text(
                                                            text = stTitle,
                                                            fontWeight = if (ord.status == st) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 13.5.sp
                                                        )
                                                    }
                                                },
                                                trailingIcon = if (ord.status == st) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = stCol,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                } else null,
                                                onClick = {
                                                    statusDropdownExpanded = false
                                                    if (st == OrderStatus.CONTRACT) {
                                                        contractDialogTargetOrder = ord
                                                    } else {
                                                        onUpdateOrders(
                                                            orders.map {
                                                                if (it.id == ord.id) {
                                                                    OrderArchiveHelper.withStatusTransition(it, st)
                                                                } else it
                                                            }
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (ord.isLegacyImport || ord.isArchived) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val legacyInfo = listOfNotNull(
                                    ord.legacySystemName ?: ord.windows.firstOrNull()?.legacySystemName,
                                    ord.legacyColorName ?: ord.windows.firstOrNull()?.legacyColorName
                                ).distinct().joinToString(" • ")
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (ord.isLegacyImport) {
                                        Surface(
                                            color = palette.accentOrange.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = strings.legacyImportBadge,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = palette.accentOrange,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (legacyInfo.isNotBlank()) {
                                        Text(
                                            text = legacyInfo,
                                            fontSize = 11.sp,
                                            color = palette.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Строка 2: Клиент, телефон, адрес + безопасные кнопки действий
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${ord.customerName.ifBlank { "—" }} • ${ord.customerPhone.ifBlank { "—" }} • ${ord.address.ifBlank { "—" }}",
                                        fontSize = 12.sp,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "${ord.createdDate} • ${String.format(strings.windowsShortFormat, ord.windows.sumOf { it.quantity })} • $curSym",
                                        fontSize = 11.sp,
                                        color = palette.textSecondary
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { onExportOrder(ord) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.IosShare,
                                            contentDescription = strings.exportOrderItem,
                                            tint = palette.accentBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (!ord.isArchived) {
                                        if (ord.status == OrderStatus.DRAFT) {
                                            if (orders.size > 1) {
                                                IconButton(
                                                    onClick = { deleteDraftTarget = ord },
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .testTag("order_delete_draft_btn_${ord.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = strings.deleteWindowDesc,
                                                        tint = palette.accentRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                color = palette.secondaryBackground,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(0.8.dp, palette.floatingBorder),
                                                modifier = Modifier
                                                    .heightIn(min = 36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { archiveOrderTarget = ord }
                                                    .testTag("order_archive_btn_${ord.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Archive,
                                                        contentDescription = strings.moveToArchiveBtn,
                                                        tint = palette.textSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = strings.moveToArchiveBtn,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = palette.textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Смайлик контроля срока договора (на этапах Договор, Производство, Готово к монтажу, Монтаж)
                            if (deadlineState != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                ContractDeadlineSmileyPill(
                                    deadlineState = deadlineState,
                                    onClick = { contractDialogTargetOrder = ord }
                                )
                            }

                            // Миниатюры конструкций отображаются всегда
                            if (ord.windows.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ord.windows.forEachIndexed { idx, w ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(palette.secondaryBackground.copy(alpha = 0.65f))
                                                .border(0.7.dp, palette.floatingBorder, RoundedCornerShape(12.dp))
                                                .padding(5.dp)
                                        ) {
                                            WindowMiniThumbnail(
                                                topology = w.topology,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Text(
                                                text = "#${idx + 1} (${w.topology.totalWidthMm.roundToInt()}×${w.topology.totalHeightMm.roundToInt()})",
                                                fontSize = 9.sp,
                                                color = palette.textSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            if (ord.isArchived) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val restored = OrderArchiveHelper.restoreFromArchiveToDraft(ord)
                                            onUpdateOrders(orders.map { if (it.id == ord.id) restored else it })
                                            selectedTab = OrdersListTab.ACTIVE
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .testTag("archive_restore_btn_${ord.id}")
                                    ) {
                                        Text(
                                            text = "↩ ${strings.restoreToWorkBtn}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val cloned = OrderArchiveHelper.cloneArchiveOrderToNew(ord)
                                            onUpdateOrders(listOf(cloned) + orders)
                                            selectedTab = OrdersListTab.ACTIVE
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .testTag("archive_clone_btn_${ord.id}")
                                    ) {
                                        Text(
                                            text = "⧉ ${strings.cloneToNewOrderBtn}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { deleteForeverTarget = ord },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentRed.copy(alpha = 0.14f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("archive_delete_forever_btn_${ord.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteForever,
                                            contentDescription = strings.deleteForeverBtn,
                                            tint = palette.accentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        OrdersStatusFilterDialog(
            initialSelected = selectedStatuses,
            onDismiss = { showFilterDialog = false },
            onApply = { updatedSet ->
                selectedStatuses = updatedSet
                AppStateStorage.saveOrdersFilterStatuses(context, updatedSet)
                showFilterDialog = false
            }
        )
    }

    val targetForContract = contractDialogTargetOrder
    if (targetForContract != null) {
        ContractDeadlineDialog(
            initialDateMillis = targetForContract.contractDateMillis,
            initialTermDays = targetForContract.contractTermDays,
            onDismiss = { contractDialogTargetOrder = null },
            onConfirm = { pickedDateMs, termDays ->
                val nextStatus = if (targetForContract.status.showDeadlineSmiley) {
                    targetForContract.status
                } else {
                    OrderStatus.CONTRACT
                }
                onUpdateOrders(
                    orders.map {
                        if (it.id == targetForContract.id) {
                            OrderArchiveHelper.withStatusTransition(it, nextStatus).copy(
                                contractDateMillis = pickedDateMs,
                                contractTermDays = termDays
                            )
                        } else it
                    }
                )
                contractDialogTargetOrder = null
            }
        )
    }

    val draftToDelete = deleteDraftTarget
    if (draftToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteDraftTarget = null },
            title = { Text(strings.deleteDraftConfirmTitle, fontWeight = FontWeight.Bold) },
            text = { Text("${draftToDelete.orderNumber}\n\n${strings.deleteDraftConfirmBody}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateOrders(orders.filterNot { it.id == draftToDelete.id })
                        deleteDraftTarget = null
                    }
                ) {
                    Text(strings.deleteForeverBtn, color = palette.accentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDraftTarget = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }

    val orderToArchive = archiveOrderTarget
    if (orderToArchive != null) {
        AlertDialog(
            onDismissRequest = { archiveOrderTarget = null },
            title = { Text(strings.moveToArchiveConfirmTitle, fontWeight = FontWeight.Bold) },
            text = { Text("${orderToArchive.orderNumber}\n\n${strings.moveToArchiveConfirmBody}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val archived = OrderArchiveHelper.archiveOrder(orderToArchive)
                        onUpdateOrders(orders.map { if (it.id == orderToArchive.id) archived else it })
                        archiveOrderTarget = null
                    },
                    modifier = Modifier.testTag("archive_confirm_dialog_btn")
                ) {
                    Text(strings.moveToArchiveBtn, color = palette.accentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveOrderTarget = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }

    val targetDeleteForever = deleteForeverTarget
    if (targetDeleteForever != null) {
        AlertDialog(
            onDismissRequest = { deleteForeverTarget = null },
            title = { Text(strings.deleteForeverConfirmTitle, color = palette.accentRed, fontWeight = FontWeight.Bold) },
            text = { Text("${targetDeleteForever.orderNumber}\n\n${strings.deleteForeverConfirmBody}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateOrders(orders.filterNot { it.id == targetDeleteForever.id })
                        deleteForeverTarget = null
                    }
                ) {
                    Text(strings.deleteForeverBtn, color = palette.accentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteForeverTarget = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }
}

@Composable
private fun OrdersStatusFilterDialog(
    initialSelected: Set<OrderStatus>,
    onDismiss: () -> Unit,
    onApply: (Set<OrderStatus>) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var draftSelected by remember(initialSelected) { mutableStateOf(initialSelected) }
    val allSelected = draftSelected.size == OrderStatus.entries.size

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            color = palette.cardSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Длинная кнопка сверху — «Показать все» (включает все 8 или гасит все 8)
                Button(
                    onClick = {
                        draftSelected = if (allSelected) emptySet() else OrderStatus.entries.toSet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (allSelected) palette.accentBlue else palette.secondaryBackground
                    ),
                    border = if (allSelected) null else BorderStroke(1.dp, palette.floatingBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("orders_filter_show_all_btn")
                ) {
                    Text(
                        text = strings.showAllBtn,
                        color = if (allSelected) Color.White else palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Два столбика по 4 цветные кнопки статусов
                val rows = OrderStatus.entries.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { st ->
                                val isSel = st in draftSelected
                                val stColor = Color(st.badgeColorHex)
                                val title = if (strings.language == AppLanguage.EN) st.titleEn else st.titleRu
                                Surface(
                                    color = if (isSel) stColor else palette.secondaryBackground.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isSel) 0.dp else 1.dp,
                                        color = if (isSel) Color.Transparent else stColor.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            draftSelected = if (isSel) draftSelected - st else draftSelected + st
                                        }
                                        .testTag("orders_filter_status_${st.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(
                                                    color = if (isSel) Color.White else stColor,
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = title,
                                            color = if (isSel) Color.White else palette.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Кнопка «ОК» внизу
                Button(
                    onClick = { onApply(draftSelected) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("orders_filter_ok_btn")
                ) {
                    Text(
                        text = strings.filterOkBtn,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContractDeadlineDialog(
    initialDateMillis: Long?,
    initialTermDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (dateMillis: Long, termDays: Int) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )
    val initialWeeks = ((initialTermDays.coerceAtLeast(1) + 3) / 7).coerceAtLeast(1)
    var weeksInput by remember { mutableStateOf(initialWeeks.toString()) }
    val activeTermDays = ((weeksInput.toIntOrNull() ?: initialWeeks).coerceIn(1, 52)) * 7

    val pickedDateMs = datePickerState.selectedDateMillis ?: initialDateMillis ?: System.currentTimeMillis()
    val previewDeadlineText = remember(pickedDateMs, activeTermDays) {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = pickedDateMs
            add(java.util.Calendar.DAY_OF_YEAR, activeTermDays)
        }
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        String.format(strings.contractDeadlinePreviewFormat, fmt.format(cal.time), activeTermDays)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            color = palette.cardSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.contractDialogTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                DatePicker(
                    state = datePickerState,
                    title = null,
                    headline = null,
                    showModeToggle = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = strings.contractTermLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 3, 4, 6, 8).forEach { w ->
                        val isSel = weeksInput == w.toString()
                        FilterChip(
                            selected = isSel,
                            onClick = { weeksInput = w.toString() },
                            label = {
                                Text(
                                    text = "$w нед.",
                                    fontSize = 12.sp,
                                    color = if (isSel) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentTeal,
                                containerColor = palette.secondaryBackground
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = weeksInput,
                        onValueChange = { weeksInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Недель", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(105.dp),
                        singleLine = true
                    )
                    Surface(
                        color = palette.accentTeal.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = previewDeadlineText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentTeal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(strings.cancelBtn, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onConfirm(pickedDateMs, activeTermDays) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentTeal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("contract_dialog_save_btn")
                    ) {
                        Text(strings.saveBtn, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContractDeadlineSmileyPill(
    deadlineState: ContractDeadlineState,
    onClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val (bgColor, accentColor) = when (deadlineState.mood) {
        DeadlineMood.HAPPY_GREEN -> Color(0xFF249A41).copy(alpha = 0.13f) to Color(0xFF1E8738)
        DeadlineMood.CAUTIOUS_YELLOW -> Color(0xFFF59E0B).copy(alpha = 0.16f) to Color(0xFFB45309)
        DeadlineMood.SCARED_RED -> Color(0xFFE53935).copy(alpha = 0.14f) to Color(0xFFC62828)
    }
    val faceColor = when (deadlineState.mood) {
        DeadlineMood.HAPPY_GREEN -> Color(0xFF28A745)
        DeadlineMood.CAUTIOUS_YELLOW -> Color(0xFFF59E0B)
        DeadlineMood.SCARED_RED -> Color(0xFFE53935)
    }
    val textLabel = when {
        deadlineState.daysRemaining > 0 -> String.format(strings.deadlineLeftFormat, deadlineState.daysRemaining)
        deadlineState.daysRemaining == 0 -> strings.deadlineToday
        else -> String.format(strings.deadlineOverdueFormat, -deadlineState.daysRemaining)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.7.dp, faceColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val r = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Круг лица
                drawCircle(color = faceColor, radius = r)

                when (deadlineState.mood) {
                    DeadlineMood.HAPPY_GREEN -> {
                        // Довольный зелёный смайлик
                        drawCircle(color = Color.White, radius = r * 0.13f, center = Offset(cx - r * 0.34f, cy - r * 0.22f))
                        drawCircle(color = Color.White, radius = r * 0.13f, center = Offset(cx + r * 0.34f, cy - r * 0.22f))
                        val smile = Path().apply {
                            moveTo(cx - r * 0.42f, cy + r * 0.12f)
                            quadraticBezierTo(cx, cy + r * 0.56f, cx + r * 0.42f, cy + r * 0.12f)
                        }
                        drawPath(smile, color = Color.White, style = Stroke(width = r * 0.16f, cap = StrokeCap.Round))
                    }

                    DeadlineMood.CAUTIOUS_YELLOW -> {
                        // Настороженный жёлтый смайлик
                        drawLine(
                            color = Color(0xFF4A2B00),
                            start = Offset(cx - r * 0.50f, cy - r * 0.36f),
                            end = Offset(cx - r * 0.18f, cy - r * 0.46f),
                            strokeWidth = r * 0.12f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFF4A2B00),
                            start = Offset(cx + r * 0.18f, cy - r * 0.46f),
                            end = Offset(cx + r * 0.50f, cy - r * 0.36f),
                            strokeWidth = r * 0.12f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(color = Color(0xFF4A2B00), radius = r * 0.13f, center = Offset(cx - r * 0.34f, cy - r * 0.15f))
                        drawCircle(color = Color(0xFF4A2B00), radius = r * 0.13f, center = Offset(cx + r * 0.34f, cy - r * 0.15f))
                        val mouth = Path().apply {
                            moveTo(cx - r * 0.36f, cy + r * 0.28f)
                            quadraticBezierTo(cx, cy + r * 0.16f, cx + r * 0.36f, cy + r * 0.28f)
                        }
                        drawPath(mouth, color = Color(0xFF4A2B00), style = Stroke(width = r * 0.15f, cap = StrokeCap.Round))
                    }

                    DeadlineMood.SCARED_RED -> {
                        // Испуганный красный смайлик (широкие глаза + рот буквой О)
                        drawCircle(color = Color.White, radius = r * 0.22f, center = Offset(cx - r * 0.34f, cy - r * 0.20f))
                        drawCircle(color = Color.White, radius = r * 0.22f, center = Offset(cx + r * 0.34f, cy - r * 0.20f))
                        drawCircle(color = Color(0xFF3E0000), radius = r * 0.09f, center = Offset(cx - r * 0.34f, cy - r * 0.20f))
                        drawCircle(color = Color(0xFF3E0000), radius = r * 0.09f, center = Offset(cx + r * 0.34f, cy - r * 0.20f))
                        drawCircle(
                            color = Color.White,
                            radius = r * 0.22f,
                            center = Offset(cx, cy + r * 0.34f),
                            style = Stroke(width = r * 0.15f)
                        )
                    }
                }
            }
            Text(
                text = textLabel,
                color = accentColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
