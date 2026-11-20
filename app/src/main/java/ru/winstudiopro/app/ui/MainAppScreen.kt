package ru.winstudiopro.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.data.PersistedAppState
import ru.winstudiopro.app.domain.calculator.WarehouseInventoryHelper
import ru.winstudiopro.app.domain.model.AppUiSettings
import ru.winstudiopro.app.domain.model.AssemblyPricingCatalog
import ru.winstudiopro.app.domain.model.CashAccount
import ru.winstudiopro.app.domain.model.CashExpenseCategory
import ru.winstudiopro.app.domain.model.CashTransaction
import ru.winstudiopro.app.domain.model.ColorCatalogItem
import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.ExtraAccessoryItem
import ru.winstudiopro.app.domain.model.FinSchemeSettings
import ru.winstudiopro.app.domain.model.GlazingCatalogItem
import ru.winstudiopro.app.domain.model.HardwareBrandItem
import ru.winstudiopro.app.domain.model.OrderExtraServiceItem
import ru.winstudiopro.app.domain.model.OrderStatus
import ru.winstudiopro.app.domain.model.ProfileSystemSpec
import ru.winstudiopro.app.domain.model.StockRemnantBar
import ru.winstudiopro.app.domain.model.StockRubberItem
import ru.winstudiopro.app.domain.model.WindowCadOperations
import ru.winstudiopro.app.domain.model.WindowItem
import ru.winstudiopro.app.domain.model.WorkServiceItem
import ru.winstudiopro.app.ui.components.IosHomeDashboard
import ru.winstudiopro.app.ui.components.IosSettingsScreen
import ru.winstudiopro.app.ui.components.IosTopAppBar
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.i18n.normalizeCurrencyDisplay
import ru.winstudiopro.app.ui.i18n.stringsFor
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import ru.winstudiopro.app.ui.theme.WinStudioTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

enum class AppScreenMode {
    METRO_HOME,          // Плиточный стартовый экран
    CAD_WORKSPACE,       // 2D-редактор конструкций заказа
    ORDERS_LIST,         // Список всех заказов (от нового к старому, с поиском и миниатюрами)
    PROFILE_CONSTRUCTOR, // Конструктор профильных систем (геометрия в мм, допуски, формулы)
    PRICES_SCREEN,       // Прайсы на системы, сухари, стеклопакеты, фурнитуру, разное, работы и фин. схему
    WAREHOUSE_STOCK,     // Склад: папки по профильным системам (целые хлысты + деловые остатки)
    CASH_MANAGER,        // Касса и счета (мультивалюта, история, категории расходов, зарплаты)
    ANALYTICS_PULSE,     // Аналитика: пульс производства, графики выручки и маржи, KPI
    UI_SETTINGS,         // Настройки интерфейса и реквизитов компании для PDF
    USER_GUIDE,          // Обучение и краткая пошаговая инструкция по программе
    WORKSHOP_CUTTING,    // Модуль задания в цех и линейного 1D-раскроя профилей
    GLAZING_ORDER_EDITOR,// Редактор «Заказ стеклопакетов» перед генерацией PDF
    HARDWARE_ORDER_EDITOR// Редактор «Заказ фурнитуры» перед генерацией PDF
}

// Состояние вызова цифрового калькулятора размеров
sealed class NumpadRequestState(val title: String, val initialValue: Int) {
    class OverallWidth(currentMm: Int) : NumpadRequestState("Ширина конструкции (мм)", currentMm)
    class OverallHeight(currentMm: Int) : NumpadRequestState("Высота конструкции (мм)", currentMm)
    class ChainSegment(
        val isHorizontalAxis: Boolean,
        val segmentIndex: Int,
        currentMm: Int
    ) : NumpadRequestState(
        if (isHorizontalAxis) "Ширина секции (мм)" else "Высота секции (мм)",
        currentMm
    )
    class DoorSashImpostHeight(
        val openingId: String,
        currentMm: Int
    ) : NumpadRequestState("Высота импоста створки/двери от низа (мм)", currentMm)
}

/**
 * Единая верхняя панель навигации в стиле iOS для всех экранов приложения.
 */
@Composable
internal fun UnifiedTopAppBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    IosTopAppBar(
        title = title,
        onBack = onBack,
        accentColor = LocalWinStudioPalette.current.accentBlue,
        actions = actions
    )
}

internal fun fmtMm(v: Float): String {
    val rounded10 = (v * 10f).roundToInt()
    return if (rounded10 % 10 == 0) {
        (rounded10 / 10).toString()
    } else {
        String.format(Locale.US, "%.1f", rounded10 / 10.0)
    }
}

internal fun parseMmOrNull(s: String): Float? = s.trim().replace(',', '.').toFloatOrNull()

@Composable
fun WinStudioProMainApp(
    onOpenPdfFile: (File, String) -> Unit,
    onSharePdfFile: (File, String) -> Unit = onOpenPdfFile,
    onShareExportFile: (File, String) -> Unit = onSharePdfFile
) {
    val context = LocalContext.current
    val initialPersisted = remember { AppStateStorage.loadState(context) }

    var profileSystems by remember { mutableStateOf(initialPersisted.profileSystems) }
    var orders by remember { mutableStateOf(initialPersisted.orders) }
    var stockRemnants by remember { mutableStateOf(initialPersisted.stockRemnants) }
    var stockRubber by remember { mutableStateOf(initialPersisted.stockRubber) }
    var uiSettings by remember { mutableStateOf(initialPersisted.uiSettings) }

    var glazingCatalog by remember { mutableStateOf(initialPersisted.glazingCatalog) }
    var colorCatalog by remember { mutableStateOf(initialPersisted.colorCatalog) }
    var hardwareBrands by remember { mutableStateOf(initialPersisted.hardwareBrands) }
    var extraAccessories by remember { mutableStateOf(initialPersisted.extraAccessories) }
    var workServices by remember { mutableStateOf(initialPersisted.workServices) }
    var assemblyPricing by remember { mutableStateOf(initialPersisted.assemblyPricing) }
    var orderExtraServices by remember { mutableStateOf(initialPersisted.orderExtraServices) }
    var finSchemeSettings by remember { mutableStateOf(initialPersisted.finSchemeSettings) }
    var cashAccounts by remember { mutableStateOf(initialPersisted.cashAccounts) }
    var cashTransactions by remember { mutableStateOf(initialPersisted.cashTransactions) }
    var cashExpenseCategories by remember { mutableStateOf(initialPersisted.cashExpenseCategories) }
    val sillsCatalog = remember { DefaultCatalogs.defaultSills() }

    var screenMode by remember { mutableStateOf(AppScreenMode.METRO_HOME) }
    var homeQuickOpMode by remember { mutableStateOf<CashQuickOpMode?>(null) }
    var activeOrderId by remember {
        mutableStateOf(orders.firstOrNull()?.id ?: "")
    }
    var activeWindowIndex by remember { mutableIntStateOf(0) }

    // Лаунчер для импорта одного заказа из файла .json / .win
    val importOrderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!jsonText.isNullOrBlank()) {
                    val importedOrder = AppStateStorage.importOrderFromJsonString(jsonText)
                    val updatedOrders = listOf(importedOrder) + orders.filter { it.id != importedOrder.id }
                    orders = updatedOrders
                    AppStateStorage.saveState(
                        context = context,
                        state = PersistedAppState(
                            profileSystems = profileSystems,
                            orders = updatedOrders,
                            stockRemnants = stockRemnants,
                            stockRubber = stockRubber,
                            uiSettings = uiSettings,
                            glazingCatalog = glazingCatalog,
                            colorCatalog = colorCatalog,
                            hardwareBrands = hardwareBrands,
                            extraAccessories = extraAccessories,
                            workServices = workServices,
                            assemblyPricing = assemblyPricing,
                            orderExtraServices = orderExtraServices,
                            finSchemeSettings = finSchemeSettings,
                            cashAccounts = cashAccounts,
                            cashTransactions = cashTransactions,
                            cashExpenseCategories = cashExpenseCategories
                        )
                    )
                    activeOrderId = importedOrder.id
                    activeWindowIndex = 0
                    screenMode = AppScreenMode.CAD_WORKSPACE
                    Toast.makeText(context, "Заказ «${importedOrder.orderNumber}» успешно импортирован!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка импорта заказа: ${e.localizedMessage ?: "неверный формат"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Лаунчер для восстановления всей базы данных из файла полного бэкапа
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!jsonText.isNullOrBlank()) {
                    val newState = AppStateStorage.importFullBackupJsonString(context, jsonText)
                    profileSystems = newState.profileSystems
                    orders = newState.orders
                    stockRemnants = newState.stockRemnants
                    stockRubber = newState.stockRubber
                    uiSettings = newState.uiSettings
                    glazingCatalog = newState.glazingCatalog
                    colorCatalog = newState.colorCatalog
                    hardwareBrands = newState.hardwareBrands
                    extraAccessories = newState.extraAccessories
                    workServices = newState.workServices
                    assemblyPricing = newState.assemblyPricing
                    orderExtraServices = newState.orderExtraServices
                    finSchemeSettings = newState.finSchemeSettings
                    cashAccounts = newState.cashAccounts
                    cashTransactions = newState.cashTransactions
                    cashExpenseCategories = newState.cashExpenseCategories
                    if (orders.isNotEmpty()) {
                        activeOrderId = orders.first().id
                        activeWindowIndex = 0
                    }
                    Toast.makeText(context, "База данных успешно восстановлена из бэкапа!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка восстановления бэкапа: ${e.localizedMessage ?: "неверный файл"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Автосохранение в SQLite при любых изменениях состояния и прайсов
    fun persistAll(
        newProfiles: List<ProfileSystemSpec> = profileSystems,
        newOrders: List<CustomerOrder> = orders,
        newStock: List<StockRemnantBar> = stockRemnants,
        newStockRubber: List<StockRubberItem> = stockRubber,
        newSettings: AppUiSettings = uiSettings,
        newGlazing: List<GlazingCatalogItem> = glazingCatalog,
        newColors: List<ColorCatalogItem> = colorCatalog,
        newHardwareBrands: List<HardwareBrandItem> = hardwareBrands,
        newExtras: List<ExtraAccessoryItem> = extraAccessories,
        newWorks: List<WorkServiceItem> = workServices,
        newAssemblyPricing: AssemblyPricingCatalog = assemblyPricing,
        newOrderExtraServices: List<OrderExtraServiceItem> = orderExtraServices,
        newFinSchemeSettings: FinSchemeSettings = finSchemeSettings,
        newCashAccounts: List<CashAccount> = cashAccounts,
        newCashTransactions: List<CashTransaction> = cashTransactions,
        newCashExpenseCategories: List<CashExpenseCategory> = cashExpenseCategories
    ) {
        profileSystems = newProfiles
        orders = newOrders
        stockRemnants = newStock
        stockRubber = newStockRubber
        uiSettings = newSettings
        glazingCatalog = newGlazing
        colorCatalog = newColors
        hardwareBrands = newHardwareBrands
        extraAccessories = newExtras
        workServices = newWorks
        assemblyPricing = newAssemblyPricing
        orderExtraServices = newOrderExtraServices
        finSchemeSettings = newFinSchemeSettings
        cashAccounts = newCashAccounts
        cashTransactions = newCashTransactions
        cashExpenseCategories = newCashExpenseCategories
        AppStateStorage.saveState(
            context = context,
            state = PersistedAppState(
                profileSystems = newProfiles,
                orders = newOrders,
                stockRemnants = newStock,
                stockRubber = newStockRubber,
                uiSettings = newSettings,
                glazingCatalog = newGlazing,
                colorCatalog = newColors,
                hardwareBrands = newHardwareBrands,
                extraAccessories = newExtras,
                workServices = newWorks,
                assemblyPricing = newAssemblyPricing,
                orderExtraServices = newOrderExtraServices,
                finSchemeSettings = newFinSchemeSettings,
                cashAccounts = newCashAccounts,
                cashTransactions = newCashTransactions,
                cashExpenseCategories = newCashExpenseCategories
            )
        )
    }

    // Автоматическое списание профилей, деловых остатков и резины при переводе заказа в «Производство»
    fun handleOrdersUpdateWithStockDeduction(updatedOrders: List<CustomerOrder>) {
        val oldById = orders.associateBy { it.id }
        var curBars = stockRemnants
        var curRubber = stockRubber
        var totalDeductedOrders = 0
        var totalConsumedFull = 0
        var totalConsumedRem = 0
        var totalAddedRem = 0
        var totalRubberM = 0.0

        val finalOrders = updatedOrders.map { newOrder ->
            val oldOrder = oldById[newOrder.id]
            val enteredProduction = newOrder.status == OrderStatus.PRODUCTION &&
                oldOrder?.status != OrderStatus.PRODUCTION &&
                !newOrder.stockDeductedForProduction
            if (enteredProduction) {
                val deduction = WarehouseInventoryHelper.applyProductionStockDeduction(
                    order = newOrder,
                    profileSystems = profileSystems,
                    glazingCatalog = glazingCatalog,
                    colorCatalog = colorCatalog,
                    sillsCatalog = sillsCatalog,
                    extraAccessoriesCatalog = extraAccessories,
                    workServicesCatalog = workServices,
                    hardwareBrandsCatalog = hardwareBrands,
                    currentStockBars = curBars,
                    currentStockRubber = curRubber
                )
                curBars = deduction.updatedStockBars
                curRubber = deduction.updatedStockRubber
                totalDeductedOrders += 1
                totalConsumedFull += deduction.consumedFullBarsCount
                totalConsumedRem += deduction.consumedRemnantsCount
                totalAddedRem += deduction.addedRemnantsCount
                totalRubberM += deduction.deductedRubberMeters
                deduction.updatedOrder
            } else {
                newOrder
            }
        }

        persistAll(
            newOrders = finalOrders,
            newStock = curBars,
            newStockRubber = curRubber
        )

        if (totalDeductedOrders > 0) {
            val rubberMsg = String.format(Locale.US, "%.1f", totalRubberM)
            Toast.makeText(
                context,
                "В производство! Списано: хлыстов $totalConsumedFull, ост. $totalConsumedRem, резины $rubberMsg м • На склад ост.: $totalAddedRem",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Мгновенное создание нового заказа без предварительной анкеты клиента!
    // Сразу открывается 2D-редактор с рамой 1500x1500 и номером "Заказ №(дата и время)".
    // По умолчанию выбирается созданная пользователем профильная система (если есть), а не тестовая.
    fun createNewOrderAndOpenEditor() {
        val defaultSys = profileSystems.lastOrNull { it.id != "al_test_system" }
            ?: profileSystems.firstOrNull()
            ?: DefaultCatalogs.defaultProfileSystems().first()
        val nowStamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

        val firstWindow = WindowItem(
            id = UUID.randomUUID().toString(),
            name = "Окно 1",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = defaultSys.id,
            selectedShtapikId = defaultSys.shtapikVariants.firstOrNull()?.id ?: "",
            glazingId = glazingCatalog.firstOrNull { it.id != "none" }?.id ?: "sp_24",
            colorId = defaultSys.colors.firstOrNull()?.id ?: "col_anthracite"
        )

        val newOrder = CustomerOrder(
            id = UUID.randomUUID().toString(),
            orderNumber = "Заказ №($nowStamp)",
            customerName = "",
            customerPhone = "",
            address = "",
            createdDate = todayStr,
            currencySymbol = normalizeCurrencyDisplay(uiSettings.currencySymbol),
            status = OrderStatus.DRAFT,
            taxVatPercent = finSchemeSettings.defaultVatPercent,
            windows = listOf(firstWindow)
        )

        // Новые заказы всегда добавляются НАВЕРХ списка (от нового к старому)
        val updatedOrders = listOf(newOrder) + orders
        persistAll(newOrders = updatedOrders)
        activeOrderId = newOrder.id
        activeWindowIndex = 0
        screenMode = AppScreenMode.CAD_WORKSPACE
    }

    // Системный жест «Назад» (свайп с края экрана на телефоне):
    // возвращает на предыдущий экран вместо сворачивания приложения!
    BackHandler(enabled = screenMode != AppScreenMode.METRO_HOME) {
        screenMode = when (screenMode) {
            AppScreenMode.WORKSHOP_CUTTING,
            AppScreenMode.GLAZING_ORDER_EDITOR,
            AppScreenMode.HARDWARE_ORDER_EDITOR -> AppScreenMode.CAD_WORKSPACE
            AppScreenMode.USER_GUIDE -> AppScreenMode.UI_SETTINGS
            else -> AppScreenMode.METRO_HOME
        }
    }

    val currentOrder = orders.find { it.id == activeOrderId } ?: orders.firstOrNull()
    val appStrings = remember(uiSettings.language) { stringsFor(uiSettings.language) }

    WinStudioTheme(themeMode = uiSettings.themeMode) {
        CompositionLocalProvider(LocalAppStrings provides appStrings) {
            val palette = LocalWinStudioPalette.current
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = palette.background
            ) {
                when (screenMode) {
                    AppScreenMode.METRO_HOME -> {
                        IosHomeDashboard(
                            orders = orders,
                            profileSystems = profileSystems,
                            stockRemnants = stockRemnants,
                            uiSettings = uiSettings,
                            onNewOrderClick = { createNewOrderAndOpenEditor() },
                            onOpenOrder = { orderId ->
                                activeOrderId = orderId
                                activeWindowIndex = 0
                                screenMode = AppScreenMode.CAD_WORKSPACE
                            },
                            onOpenOrdersList = { screenMode = AppScreenMode.ORDERS_LIST },
                            onOpenProfileConstructor = { screenMode = AppScreenMode.PROFILE_CONSTRUCTOR },
                            onOpenPrices = { screenMode = AppScreenMode.PRICES_SCREEN },
                            onOpenWarehouse = { screenMode = AppScreenMode.WAREHOUSE_STOCK },
                            onOpenSettings = { screenMode = AppScreenMode.UI_SETTINGS },
                            onOpenCash = { screenMode = AppScreenMode.CASH_MANAGER },
                            onOpenAnalytics = { screenMode = AppScreenMode.ANALYTICS_PULSE },
                            onQuickIncome = { homeQuickOpMode = CashQuickOpMode.INCOME },
                            onQuickExpense = { homeQuickOpMode = CashQuickOpMode.EXPENSE },
                            onQuickTransfer = { homeQuickOpMode = CashQuickOpMode.TRANSFER }
                        )

                        when (homeQuickOpMode) {
                            CashQuickOpMode.INCOME -> {
                                val snapshots = rememberOrderLedgerSnapshots(
                                    orders = orders,
                                    profileSystems = profileSystems,
                                    glazingCatalog = glazingCatalog,
                                    colorCatalog = colorCatalog,
                                    sillsCatalog = sillsCatalog,
                                    extraAccessoriesCatalog = extraAccessories,
                                    workServicesCatalog = workServices,
                                    hardwareBrandsCatalog = hardwareBrands,
                                    assemblyPricingCatalog = assemblyPricing,
                                    orderExtraServicesCatalog = orderExtraServices,
                                    finSchemeSettings = finSchemeSettings,
                                    cashAccounts = cashAccounts,
                                    cashTransactions = cashTransactions
                                )
                                QuickIncomeDialog(
                                    cashAccounts = cashAccounts,
                                    orderSnapshots = snapshots,
                                    finSchemeSettings = finSchemeSettings,
                                    onDismiss = { homeQuickOpMode = null },
                                    onConfirmTransaction = { tx ->
                                        persistAll(newCashTransactions = listOf(tx) + cashTransactions)
                                        homeQuickOpMode = null
                                        Toast.makeText(context, "Приход проведён", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            CashQuickOpMode.EXPENSE -> {
                                QuickExpenseDialog(
                                    cashAccounts = cashAccounts,
                                    expenseCategories = cashExpenseCategories,
                                    orders = orders,
                                    onDismiss = { homeQuickOpMode = null },
                                    onConfirmTransaction = { tx ->
                                        persistAll(newCashTransactions = listOf(tx) + cashTransactions)
                                        homeQuickOpMode = null
                                        Toast.makeText(context, "Расход списан", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            CashQuickOpMode.TRANSFER -> {
                                QuickTransferDialog(
                                    cashAccounts = cashAccounts,
                                    finSchemeSettings = finSchemeSettings,
                                    onDismiss = { homeQuickOpMode = null },
                                    onConfirmTransaction = { tx ->
                                        persistAll(newCashTransactions = listOf(tx) + cashTransactions)
                                        homeQuickOpMode = null
                                        Toast.makeText(context, "Перевод выполнен", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            null -> Unit
                        }
                    }

                    AppScreenMode.CAD_WORKSPACE -> {
                        if (currentOrder == null) {
                            LaunchedEffect(Unit) { createNewOrderAndOpenEditor() }
                        } else {
                            CadEditorWorkspaceScreen(
                                order = currentOrder,
                                activeWindowIndex = activeWindowIndex.coerceIn(0, (currentOrder.windows.size - 1).coerceAtLeast(0)),
                                profileSystems = profileSystems,
                                glazingCatalog = glazingCatalog,
                                colorCatalog = colorCatalog,
                                sillsCatalog = sillsCatalog,
                                hardwareBrandsCatalog = hardwareBrands,
                                extraAccessoriesCatalog = extraAccessories,
                                workServicesCatalog = workServices,
                                assemblyPricingCatalog = assemblyPricing,
                                orderExtraServicesCatalog = orderExtraServices,
                                finSchemeSettings = finSchemeSettings,
                                stockRemnants = stockRemnants,
                                uiSettings = uiSettings,
                                onSelectWindowIndex = { idx -> activeWindowIndex = idx },
                                onUpdateOrder = { updatedOrder ->
                                    val newList = orders.map { if (it.id == updatedOrder.id) updatedOrder else it }
                                    handleOrdersUpdateWithStockDeduction(newList)
                                },
                                onBackToHome = { screenMode = AppScreenMode.METRO_HOME },
                                onOpenWorkshopCutting = { screenMode = AppScreenMode.WORKSHOP_CUTTING },
                                onOpenGlazingOrder = { screenMode = AppScreenMode.GLAZING_ORDER_EDITOR },
                                onOpenHardwareOrder = { screenMode = AppScreenMode.HARDWARE_ORDER_EDITOR },
                                onOpenPdfFile = onOpenPdfFile,
                                onSharePdfFile = onSharePdfFile
                            )
                        }
                    }

                    AppScreenMode.ORDERS_LIST -> {
                        OrdersListScreen(
                            orders = orders,
                            onBack = { screenMode = AppScreenMode.METRO_HOME },
                            onNewOrder = { createNewOrderAndOpenEditor() },
                            onOpenOrder = { orderId ->
                                activeOrderId = orderId
                                activeWindowIndex = 0
                                screenMode = AppScreenMode.CAD_WORKSPACE
                            },
                            onUpdateOrders = { updated -> handleOrdersUpdateWithStockDeduction(updated) },
                            onExportOrder = { ord ->
                                val file = AppStateStorage.createOrderJsonExportFile(context, ord)
                                onShareExportFile(file, "Заказ ${ord.orderNumber}")
                            },
                            onImportOrder = { importOrderLauncher.launch("*/*") }
                        )
                    }

                    AppScreenMode.PROFILE_CONSTRUCTOR -> {
                        ProfileConstructorScreen(
                            profileSystems = profileSystems,
                            currencySymbol = normalizeCurrencyDisplay(uiSettings.currencySymbol),
                            onBack = { screenMode = AppScreenMode.METRO_HOME },
                            onSaveProfiles = { updated -> persistAll(newProfiles = updated) }
                        )
                    }

                    AppScreenMode.PRICES_SCREEN -> {
                        PricesEditorScreen(
                            profileSystems = profileSystems,
                            glazingCatalog = glazingCatalog,
                            hardwareBrands = hardwareBrands,
                            extraAccessories = extraAccessories,
                            workServices = workServices,
                            assemblyPricing = assemblyPricing,
                            orderExtraServices = orderExtraServices,
                            finSchemeSettings = finSchemeSettings,
                            currencySymbol = normalizeCurrencyDisplay(uiSettings.currencySymbol),
                            onBack = { screenMode = AppScreenMode.METRO_HOME },
                            onSaveProfiles = { updated -> persistAll(newProfiles = updated) },
                            onSaveGlazing = { updated -> persistAll(newGlazing = updated) },
                            onSaveHardware = { updated -> persistAll(newHardwareBrands = updated) },
                            onSaveExtras = { updated -> persistAll(newExtras = updated) },
                            onSaveWorks = { updated -> persistAll(newWorks = updated) },
                            onSaveAssemblyPricing = { updated -> persistAll(newAssemblyPricing = updated) },
                            onSaveOrderExtraServices = { updated -> persistAll(newOrderExtraServices = updated) },
                            onSaveFinSchemeSettings = { updated -> persistAll(newFinSchemeSettings = updated) }
                        )
                    }

                    AppScreenMode.WAREHOUSE_STOCK -> {
                        WarehouseBySystemScreen(
                            profileSystems = profileSystems,
                            stockBars = stockRemnants,
                            stockRubber = stockRubber,
                            onBack = { screenMode = AppScreenMode.METRO_HOME },
                            onSaveStock = { updated -> persistAll(newStock = updated) },
                            onSaveRubberStock = { updatedRubber -> persistAll(newStockRubber = updatedRubber) }
                        )
                    }

                    AppScreenMode.CASH_MANAGER -> {
                        CashManagerScreen(
                            orders = orders,
                            profileSystems = profileSystems,
                            glazingCatalog = glazingCatalog,
                            colorCatalog = colorCatalog,
                            sillsCatalog = sillsCatalog,
                            extraAccessoriesCatalog = extraAccessories,
                            workServicesCatalog = workServices,
                            hardwareBrandsCatalog = hardwareBrands,
                            assemblyPricingCatalog = assemblyPricing,
                            orderExtraServicesCatalog = orderExtraServices,
                            finSchemeSettings = finSchemeSettings,
                            cashAccounts = cashAccounts,
                            cashTransactions = cashTransactions,
                            cashExpenseCategories = cashExpenseCategories,
                            onSaveFinSchemeSettings = { updated -> persistAll(newFinSchemeSettings = updated) },
                            onSaveCashAccounts = { updated -> persistAll(newCashAccounts = updated) },
                            onSaveCashTransactions = { updated -> persistAll(newCashTransactions = updated) },
                            onSaveCashExpenseCategories = { updated -> persistAll(newCashExpenseCategories = updated) },
                            onBack = { screenMode = AppScreenMode.METRO_HOME }
                        )
                    }

                    AppScreenMode.ANALYTICS_PULSE -> {
                        AnalyticsScreen(
                            orders = orders,
                            profileSystems = profileSystems,
                            glazingCatalog = glazingCatalog,
                            colorCatalog = colorCatalog,
                            sillsCatalog = sillsCatalog,
                            extraAccessoriesCatalog = extraAccessories,
                            workServicesCatalog = workServices,
                            hardwareBrandsCatalog = hardwareBrands,
                            assemblyPricingCatalog = assemblyPricing,
                            orderExtraServicesCatalog = orderExtraServices,
                            finSchemeSettings = finSchemeSettings,
                            cashAccounts = cashAccounts,
                            cashTransactions = cashTransactions,
                            onOpenOrdersList = { screenMode = AppScreenMode.ORDERS_LIST },
                            onOpenCash = { screenMode = AppScreenMode.CASH_MANAGER },
                            onBack = { screenMode = AppScreenMode.METRO_HOME }
                        )
                    }

                    AppScreenMode.UI_SETTINGS -> {
                        IosSettingsScreen(
                            settings = uiSettings,
                            onBack = { screenMode = AppScreenMode.METRO_HOME },
                            onUpdateSettingsLive = { newSettings ->
                                persistAll(newSettings = newSettings)
                            },
                            onApplyCurrencyToAllOrders = { cur ->
                                val updatedOrders = orders.map { it.copy(currencySymbol = cur) }
                                persistAll(newOrders = updatedOrders)
                            },
                            onExportBackup = {
                                val file = AppStateStorage.createFullBackupFile(context)
                                onShareExportFile(file, "Полный бэкап WinStudio Pro")
                            },
                            onImportBackup = { importBackupLauncher.launch("*/*") },
                            onOpenGuide = { screenMode = AppScreenMode.USER_GUIDE }
                        )
                    }

                    AppScreenMode.USER_GUIDE -> {
                        AppGuideScreen(
                            onBack = { screenMode = AppScreenMode.UI_SETTINGS }
                        )
                    }

                    AppScreenMode.WORKSHOP_CUTTING -> {
                        if (currentOrder != null) {
                            WorkshopAndCuttingScreen(
                                order = currentOrder,
                                profileSystems = profileSystems,
                                glazingCatalog = glazingCatalog,
                                colorCatalog = colorCatalog,
                                sillsCatalog = sillsCatalog,
                                extraAccessoriesCatalog = extraAccessories,
                                workServicesCatalog = workServices,
                                hardwareBrandsCatalog = hardwareBrands,
                                stockRemnants = stockRemnants,
                                stockRubber = stockRubber,
                                onUpdateOrder = { updatedOrder ->
                                    val newList = orders.map { if (it.id == updatedOrder.id) updatedOrder else it }
                                    handleOrdersUpdateWithStockDeduction(newList)
                                },
                                onBackToCad = { screenMode = AppScreenMode.CAD_WORKSPACE },
                                onSaveStockRemnants = { updatedStock -> persistAll(newStock = updatedStock) },
                                onOpenPdfFile = onOpenPdfFile
                            )
                        } else {
                            screenMode = AppScreenMode.METRO_HOME
                        }
                    }

                    AppScreenMode.GLAZING_ORDER_EDITOR -> {
                        if (currentOrder != null) {
                            GlazingOrderScreen(
                                order = currentOrder,
                                profileSystems = profileSystems,
                                glazingCatalog = glazingCatalog,
                                colorCatalog = colorCatalog,
                                sillsCatalog = sillsCatalog,
                                extraAccessoriesCatalog = extraAccessories,
                                workServicesCatalog = workServices,
                                hardwareBrandsCatalog = hardwareBrands,
                                onBackToCad = { screenMode = AppScreenMode.CAD_WORKSPACE },
                                onOpenPdfFile = onOpenPdfFile
                            )
                        } else {
                            screenMode = AppScreenMode.METRO_HOME
                        }
                    }

                    AppScreenMode.HARDWARE_ORDER_EDITOR -> {
                        if (currentOrder != null) {
                            HardwareOrderScreen(
                                order = currentOrder,
                                profileSystems = profileSystems,
                                glazingCatalog = glazingCatalog,
                                colorCatalog = colorCatalog,
                                sillsCatalog = sillsCatalog,
                                extraAccessoriesCatalog = extraAccessories,
                                workServicesCatalog = workServices,
                                hardwareBrandsCatalog = hardwareBrands,
                                onBackToCad = { screenMode = AppScreenMode.CAD_WORKSPACE },
                                onOpenPdfFile = onOpenPdfFile
                            )
                        } else {
                            screenMode = AppScreenMode.METRO_HOME
                        }
                    }
                }
            }
        }
    }
}
