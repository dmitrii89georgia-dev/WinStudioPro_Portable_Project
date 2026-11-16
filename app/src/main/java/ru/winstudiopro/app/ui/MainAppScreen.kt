package ru.winstudiopro.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import ru.winstudiopro.app.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.data.PersistedAppState
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.ClientPdfReportMode
import ru.winstudiopro.app.pdf.PdfReportGenerator
import ru.winstudiopro.app.ui.cad.*
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
    PRICES_SCREEN,       // Прайсы на системы, сухари, стеклопакеты, фурнитуру, разное и работы
    WAREHOUSE_STOCK,     // Склад: папки по профильным системам (целые хлысты + деловые остатки)
    UI_SETTINGS,         // Настройки интерфейса и реквизитов компании для PDF
    WORKSHOP_CUTTING     // Модуль задания в цех и линейного 1D-раскроя профилей
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

private val MetroBlue = Color(0xFF007AFF)
private val MetroDarkBlue = Color(0xFF005BBF)
private val CanvasBgGray = Color(0xFFECEFF4)

/**
 * Единая верхняя панель навигации в стиле iOS для всех экранов приложения.
 */
@Composable
private fun UnifiedTopAppBar(
    title: String,
    onBack: () -> Unit,
    backgroundColor: Color = MetroBlue,
    actions: @Composable RowScope.() -> Unit = {}
) {
    IosTopAppBar(
        title = title,
        onBack = onBack,
        accentColor = LocalWinStudioPalette.current.accentBlue,
        actions = actions
    )
}

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
    var uiSettings by remember { mutableStateOf(initialPersisted.uiSettings) }

    var glazingCatalog by remember { mutableStateOf(initialPersisted.glazingCatalog) }
    var colorCatalog by remember { mutableStateOf(initialPersisted.colorCatalog) }
    var hardwareBrands by remember { mutableStateOf(initialPersisted.hardwareBrands) }
    var extraAccessories by remember { mutableStateOf(initialPersisted.extraAccessories) }
    var workServices by remember { mutableStateOf(initialPersisted.workServices) }
    val sillsCatalog = remember { DefaultCatalogs.defaultSills() }

    var screenMode by remember { mutableStateOf(AppScreenMode.METRO_HOME) }
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
                            uiSettings = uiSettings,
                            glazingCatalog = glazingCatalog,
                            colorCatalog = colorCatalog,
                            hardwareBrands = hardwareBrands,
                            extraAccessories = extraAccessories,
                            workServices = workServices
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
                    uiSettings = newState.uiSettings
                    glazingCatalog = newState.glazingCatalog
                    colorCatalog = newState.colorCatalog
                    hardwareBrands = newState.hardwareBrands
                    extraAccessories = newState.extraAccessories
                    workServices = newState.workServices
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
        newSettings: AppUiSettings = uiSettings,
        newGlazing: List<GlazingCatalogItem> = glazingCatalog,
        newColors: List<ColorCatalogItem> = colorCatalog,
        newHardwareBrands: List<HardwareBrandItem> = hardwareBrands,
        newExtras: List<ExtraAccessoryItem> = extraAccessories,
        newWorks: List<WorkServiceItem> = workServices
    ) {
        profileSystems = newProfiles
        orders = newOrders
        stockRemnants = newStock
        uiSettings = newSettings
        glazingCatalog = newGlazing
        colorCatalog = newColors
        hardwareBrands = newHardwareBrands
        extraAccessories = newExtras
        workServices = newWorks
        AppStateStorage.saveState(
            context = context,
            state = PersistedAppState(
                profileSystems = newProfiles,
                orders = newOrders,
                stockRemnants = newStock,
                uiSettings = newSettings,
                glazingCatalog = newGlazing,
                colorCatalog = newColors,
                hardwareBrands = newHardwareBrands,
                extraAccessories = newExtras,
                workServices = newWorks
            )
        )
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
            AppScreenMode.WORKSHOP_CUTTING -> AppScreenMode.CAD_WORKSPACE
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
                            onOpenSettings = { screenMode = AppScreenMode.UI_SETTINGS }
                        )
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
                                uiSettings = uiSettings,
                                onSelectWindowIndex = { idx -> activeWindowIndex = idx },
                                onUpdateOrder = { updatedOrder ->
                                    val newList = orders.map { if (it.id == updatedOrder.id) updatedOrder else it }
                                    persistAll(newOrders = newList)
                                },
                                onBackToHome = { screenMode = AppScreenMode.METRO_HOME },
                                onOpenWorkshopCutting = { screenMode = AppScreenMode.WORKSHOP_CUTTING },
                                onOpenPdfFile = onOpenPdfFile,
                                onSharePdfFile = onSharePdfFile,
                                onShareExportFile = onShareExportFile,
                                onImportOrder = { importOrderLauncher.launch("*/*") },
                                onImportBackup = { importBackupLauncher.launch("*/*") }
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
                            onUpdateOrders = { updated -> persistAll(newOrders = updated) },
                            onExportOrder = { ord ->
                                val file = AppStateStorage.createOrderJsonExportFile(context, ord)
                                onShareExportFile(file, "Заказ ${ord.orderNumber}")
                            },
                            onImportOrder = { importOrderLauncher.launch("*/*") },
                            onExportBackup = {
                                val file = AppStateStorage.createFullBackupFile(context)
                                onShareExportFile(file, "Полный бэкап WinStudio Pro")
                            }
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
                            colorCatalog = colorCatalog,
                            hardwareBrands = hardwareBrands,
                            extraAccessories = extraAccessories,
                            workServices = workServices,
                            currencySymbol = normalizeCurrencyDisplay(uiSettings.currencySymbol),
                            onBack = { screenMode = AppScreenMode.METRO_HOME },
                            onSaveProfiles = { updated -> persistAll(newProfiles = updated) },
                            onSaveGlazing = { updated -> persistAll(newGlazing = updated) },
                            onSaveColors = { updated -> persistAll(newColors = updated) },
                            onSaveHardware = { updated -> persistAll(newHardwareBrands = updated) },
                            onSaveExtras = { updated -> persistAll(newExtras = updated) },
                            onSaveWorks = { updated -> persistAll(newWorks = updated) }
                        )
                    }

                    AppScreenMode.WAREHOUSE_STOCK -> {
                        WarehouseBySystemScreen(
                            profileSystems = profileSystems,
                            stockBars = stockRemnants,
                            onBack = { screenMode = AppScreenMode.METRO_HOME },
                            onSaveStock = { updated -> persistAll(newStock = updated) }
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
                            onImportBackup = { importBackupLauncher.launch("*/*") }
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
                                onBackToCad = { screenMode = AppScreenMode.CAD_WORKSPACE },
                                onSaveStockRemnants = { updatedStock -> persistAll(newStock = updatedStock) },
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



// =========================================================================================
// 2. РАБОЧИЙ 2D-РЕДАКТОР КОНСТРУКЦИЙ (С ДИНАМИЧЕСКИМ СКРОЛЛОМ МЕТРОВ/СУХАРЕЙ/СУММ И ШТОРКОЙ НАСТРОЕК)
// =========================================================================================
@Composable
private fun CadEditorWorkspaceScreen(
    order: CustomerOrder,
    activeWindowIndex: Int,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    sillsCatalog: List<SillCatalogItem>,
    hardwareBrandsCatalog: List<HardwareBrandItem>,
    extraAccessoriesCatalog: List<ExtraAccessoryItem>,
    workServicesCatalog: List<WorkServiceItem>,
    uiSettings: AppUiSettings,
    onSelectWindowIndex: (Int) -> Unit,
    onUpdateOrder: (CustomerOrder) -> Unit,
    onBackToHome: () -> Unit,
    onOpenWorkshopCutting: () -> Unit,
    onOpenPdfFile: (File, String) -> Unit,
    onSharePdfFile: (File, String) -> Unit,
    onShareExportFile: (File, String) -> Unit = onSharePdfFile,
    onImportOrder: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val windows = order.windows
    val currentWindow = windows.getOrNull(activeWindowIndex) ?: return

    // Фактическая нижняя граница верхней плавающей панели для точного старта чертежа
    var topBarBottomPx by remember { mutableFloatStateOf(0f) }

    val activeProfile = profileSystems.find { it.id == currentWindow.profileSystemId }
        ?: profileSystems.firstOrNull()
        ?: DefaultCatalogs.defaultProfileSystems().first()

    val activeGlazing = glazingCatalog.find { it.id == currentWindow.glazingId }
        ?: glazingCatalog.first()

    val activeColor = WindowBomAndCostEngine.resolveWindowColor(
        window = currentWindow,
        profile = activeProfile,
        legacyColorCatalog = colorCatalog
    )

    // Расчёт всех конструкций текущего заказа в реальном времени (включая сухари, разное и работы из прайса)
    val allWindowResults = remember(
        order,
        profileSystems,
        glazingCatalog,
        colorCatalog,
        sillsCatalog,
        extraAccessoriesCatalog,
        workServicesCatalog,
        hardwareBrandsCatalog
    ) {
        order.windows.mapIndexed { idx, win ->
            WindowBomAndCostEngine.calculateWindow(
                window = win,
                profileSystems = profileSystems,
                glazingCatalog = glazingCatalog,
                colorCatalog = colorCatalog,
                sillsCatalog = sillsCatalog,
                extraAccessoriesCatalog = extraAccessoriesCatalog,
                workServicesCatalog = workServicesCatalog,
                hardwareBrandsCatalog = hardwareBrandsCatalog,
                windowIndexInOrder = idx + 1
            )
        }
    }
    val currentCalcResult = allWindowResults.getOrNull(activeWindowIndex)

    // Диалоги и панели
    var showLeftBlueMenu by remember { mutableStateOf(false) }
    var showRightSettingsDrawer by remember { mutableStateOf(false) }
    var showCustomerEditDialog by remember { mutableStateOf(false) }
    var showAlignmentDialog by remember { mutableStateOf(false) }
    var showManualPriceNumpad by remember { mutableStateOf(false) }
    var currentAlignmentMode by remember { mutableStateOf(ImpostAlignmentMode.BY_OPENING) }

    var activeCellForModal by remember { mutableStateOf<OpeningCellNode?>(null) }
    var numpadRequest by remember { mutableStateOf<NumpadRequestState?>(null) }
    var previewReportMode by remember { mutableStateOf<ClientPdfReportMode?>(null) }
    var snackMessage by remember { mutableStateOf<String?>(null) }

    // Стек истории изменений для кнопок «Отмена» (Undo) и «Повтор» (Redo) в 2D-редакторе
    var undoStack by remember(activeWindowIndex) { mutableStateOf(listOf<WindowItem>()) }
    var redoStack by remember(activeWindowIndex) { mutableStateOf(listOf<WindowItem>()) }

    // Перехват жеста «Назад» (свайп с края экрана), если открыта правая шторка настроек или левое меню
    BackHandler(enabled = showRightSettingsDrawer || showLeftBlueMenu) {
        if (showRightSettingsDrawer) {
            showRightSettingsDrawer = false
        } else if (showLeftBlueMenu) {
            showLeftBlueMenu = false
        }
    }

    fun updateCurrentWindow(transform: (WindowItem) -> WindowItem) {
        val modifiedCurrent = transform(currentWindow)
        if (modifiedCurrent != currentWindow) {
            undoStack = (undoStack + currentWindow).takeLast(25)
            redoStack = emptyList()
        }
        val switchedToCustomSystem = modifiedCurrent.profileSystemId != currentWindow.profileSystemId &&
            modifiedCurrent.profileSystemId != "al_test_system" &&
            modifiedCurrent.profileSystemId != "none"

        val updatedWindows = windows.mapIndexed { idx, item ->
            if (idx == activeWindowIndex) {
                modifiedCurrent
            } else if (switchedToCustomSystem && item.profileSystemId == "al_test_system") {
                // Автоматически заменяем тестовую систему по умолчанию на выбранную рабочую систему в остальных окнах заказа
                item.copy(
                    profileSystemId = modifiedCurrent.profileSystemId,
                    selectedShtapikId = modifiedCurrent.selectedShtapikId
                )
            } else {
                item
            }
        }
        onUpdateOrder(order.copy(windows = updatedWindows))
    }

    // Отмена последнего действия в 2D-редакторе (Undo)
    fun undoCurrentWindow() {
        if (undoStack.isEmpty()) return
        val previousState = undoStack.last()
        undoStack = undoStack.dropLast(1)
        redoStack = redoStack + currentWindow

        val updatedWindows = windows.mapIndexed { idx, item ->
            if (idx == activeWindowIndex) previousState else item
        }
        onUpdateOrder(order.copy(windows = updatedWindows))
    }

    // Повтор отменённого действия в 2D-редакторе (Redo)
    fun redoCurrentWindow() {
        if (redoStack.isEmpty()) return
        val nextState = redoStack.last()
        redoStack = redoStack.dropLast(1)
        undoStack = undoStack + currentWindow

        val updatedWindows = windows.mapIndexed { idx, item ->
            if (idx == activeWindowIndex) nextState else item
        }
        onUpdateOrder(order.copy(windows = updatedWindows))
    }

    // Применение настроек к ВСЕМ конструкциям заказа (с сохранением их индивидуальной геометрии)
    fun applySettingsToAllWindowsInOrder(
        applyProfile: Boolean = true,
        applyGlazing: Boolean = true,
        applyColors: Boolean = true,
        applyHardware: Boolean = true,
        applyFinancials: Boolean = true,
        applyExtrasAndWorks: Boolean = true
    ) {
        val updatedWindows = windows.map { win ->
            win.copy(
                profileSystemId = if (applyProfile) currentWindow.profileSystemId else win.profileSystemId,
                selectedShtapikId = if (applyProfile) currentWindow.selectedShtapikId else win.selectedShtapikId,
                glazingId = if (applyGlazing) currentWindow.glazingId else win.glazingId,
                roundGlassTo5Mm = if (applyGlazing) currentWindow.roundGlassTo5Mm else win.roundGlassTo5Mm,
                colorId = if (applyColors) currentWindow.colorId else win.colorId,
                customColorName = if (applyColors) currentWindow.customColorName else win.customColorName,
                customColorRal = if (applyColors) currentWindow.customColorRal else win.customColorRal,
                customColorHex = if (applyColors) currentWindow.customColorHex else win.customColorHex,
                customPaintingCost = if (applyColors) currentWindow.customPaintingCost else win.customPaintingCost,
                customColorUseRawForStock = if (applyColors) currentWindow.customColorUseRawForStock else win.customColorUseRawForStock,
                hardwareBrandId = if (applyHardware) currentWindow.hardwareBrandId else win.hardwareBrandId,
                mosquitoSystemId = if (applyHardware) currentWindow.mosquitoSystemId else win.mosquitoSystemId,
                financialSchemeId = if (applyFinancials) currentWindow.financialSchemeId else win.financialSchemeId,
                selectedExtraIds = if (applyExtrasAndWorks) currentWindow.selectedExtraIds else win.selectedExtraIds,
                selectedWorkIds = if (applyExtrasAndWorks) currentWindow.selectedWorkIds else win.selectedWorkIds
            )
        }
        onUpdateOrder(order.copy(windows = updatedWindows))
        snackMessage = "Настройки применены ко всем конструкциям заказа (${updatedWindows.size} шт.)!"
    }

    // Добавление новой конструкции 1500x1500 через "+" С НАСЛЕДОВАНИЕМ настроек текущего окна!
    fun addNewWindowInheritingSettings() {
        val nextNum = windows.size + 1
        val newWin = currentWindow.copy(
            id = UUID.randomUUID().toString(),
            name = "Окно $nextNum",
            quantity = 1,
            manualUnitPriceOverride = null,
            topology = WindowCadOperations.single1500Topology()
        )
        val updated = windows + newWin
        onUpdateOrder(order.copy(windows = updated))
        onSelectWindowIndex(updated.lastIndex)
    }

    // Полное копирование текущего окна (включая чертёж и все настройки)
    fun duplicateCurrentWindow() {
        val nextNum = windows.size + 1
        val copyWin = currentWindow.copy(
            id = UUID.randomUUID().toString(),
            name = "Окно $nextNum (копия)"
        )
        val updated = windows + copyWin
        onUpdateOrder(order.copy(windows = updated))
        onSelectWindowIndex(updated.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvasBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- ЦЕНТРАЛЬНАЯ ОБЛАСТЬ: ЧИСТЫЙ ЧЕРТЁЖ НА ВСЮ ШИРИНУ И ВЫСОТУ МЕЖДУ ПАНЕЛЯМИ ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(palette.canvasBg)
            ) {
                InteractiveWindowCadCanvas(
                    topology = currentWindow.topology,
                    profileSystem = activeProfile,
                    colorItem = activeColor,
                    showShadows = true,
                    showControls = true,
                    onOpenCellDialog = { cell -> activeCellForModal = cell },
                    onQuickSplitCell = { cellId, dir, parts ->
                        updateCurrentWindow { win ->
                            win.copy(topology = WindowCadOperations.splitOpening(win.topology, cellId, dir, parts))
                        }
                    },
                    onRemoveImpost = { impostId ->
                        updateCurrentWindow { win ->
                            win.copy(topology = WindowCadOperations.removeImpost(win.topology, impostId))
                        }
                    },
                    onEditTotalWidth = { curW ->
                        numpadRequest = NumpadRequestState.OverallWidth(curW.roundToInt())
                    },
                    onEditTotalHeight = { curH ->
                        numpadRequest = NumpadRequestState.OverallHeight(curH.roundToInt())
                    },
                    onEditChainSegment = { isHoriz, segIdx, curLen ->
                        numpadRequest = NumpadRequestState.ChainSegment(
                            isHorizontalAxis = isHoriz,
                            segmentIndex = segIdx,
                            currentMm = curLen.roundToInt()
                        )
                    },
                    onEqualizeChain = { _ ->
                        showAlignmentDialog = true
                    },
                    onEditDoorImpostHeight = { openingId, curHeightFromBottom ->
                        numpadRequest = NumpadRequestState.DoorSashImpostHeight(
                            openingId = openingId,
                            currentMm = curHeightFromBottom.roundToInt()
                        )
                    },
                    onToggleDoorBottomSandwich = { openingId ->
                        updateCurrentWindow { win ->
                            win.copy(topology = WindowCadOperations.toggleDoorBottomSandwich(win.topology, openingId))
                        }
                    },
                    topSafeInsetPx = topBarBottomPx,
                    rightSafeInsetPx = 0f,
                    bottomSafeInsetPx = 0f,
                    modifier = Modifier.fillMaxSize()
                )

                // ПЛАВАЮЩАЯ ВЕРХНЯЯ ПАНЕЛЬ ИНСТРУМЕНТОВ В СТИЛЕ iOS
                Surface(
                    color = palette.floatingGlass,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 4.dp else 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .onGloballyPositioned { coords ->
                            val bottom = coords.positionInParent().y + coords.size.height
                            if (kotlin.math.abs(topBarBottomPx - bottom) > 1f) {
                                topBarBottomPx = bottom
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = palette.accentBlue,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable { showLeftBlueMenu = !showLeftBlueMenu }
                                    .testTag("cad_menu_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Menu,
                                        contentDescription = strings.menuBtn,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = strings.menuBtn,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                color = palette.secondaryBackground.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showCustomerEditDialog = true }
                                    .testTag("cad_order_client_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = order.orderNumber,
                                            color = palette.textPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (order.customerName.isBlank()) strings.clientTapHint else "${order.customerName} ${order.customerPhone}",
                                            color = palette.textSecondary,
                                            fontSize = 9.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = strings.customerDetailsItem,
                                        tint = palette.accentBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Кнопка «Отмена» (Undo)
                            Surface(
                                color = if (undoStack.isNotEmpty()) palette.secondaryBackground else palette.secondaryBackground.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .size(36.dp, 38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable(enabled = undoStack.isNotEmpty()) { undoCurrentWindow() }
                                    .testTag("cad_undo_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Undo,
                                        contentDescription = strings.undoDesc,
                                        tint = if (undoStack.isNotEmpty()) palette.textPrimary else palette.textSecondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Кнопка «Повтор» (Redo)
                            Surface(
                                color = if (redoStack.isNotEmpty()) palette.secondaryBackground else palette.secondaryBackground.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .size(36.dp, 38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable(enabled = redoStack.isNotEmpty()) { redoCurrentWindow() }
                                    .testTag("cad_redo_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Redo,
                                        contentDescription = strings.redoDesc,
                                        tint = if (redoStack.isNotEmpty()) palette.textPrimary else palette.textSecondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Компактная кнопка удаления конструкции в верхнем баре (если конструкций > 1)
                            if (windows.size > 1) {
                                Surface(
                                    color = palette.accentRed.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(0.6.dp, palette.accentRed.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .size(36.dp, 38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .clickable {
                                            val updated = windows.filterIndexed { index, _ -> index != activeWindowIndex }
                                            onUpdateOrder(order.copy(windows = updated))
                                            onSelectWindowIndex((activeWindowIndex - 1).coerceAtLeast(0))
                                        }
                                        .testTag("cad_delete_window_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = strings.deleteWindowDesc,
                                            tint = palette.accentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Компактная кнопка настроек конструкции в верхнем баре
                            Surface(
                                color = palette.accentBlue,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .widthIn(min = 38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable { showRightSettingsDrawer = true }
                                    .testTag("cad_window_settings_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = if (currentWindow.quantity > 1) 8.dp else 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = strings.windowSettingsDesc,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (currentWindow.quantity > 1) {
                                        Text(
                                            text = "×${currentWindow.quantity}",
                                            color = Color(0xFFFFF59D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ПРАВЫЕ КОМПАКТНЫЕ ИНФОРМАЦИОННЫЕ ПЛАШКИ (СМЕЩЕНЫ НИЖЕ ПЛАВАЮЩЕЙ ВЕРХНЕЙ ПАНЕЛИ)
                val hwName = hardwareBrandsCatalog
                    .find { it.id == currentWindow.hardwareBrandId }?.name ?: strings.notAccounted
                val finName = DefaultCatalogs.defaultFinancialSchemes()
                    .find { it.id == currentWindow.financialSchemeId }?.name
                val badgesTopPadDp = with(density) { topBarBottomPx.toDp() }.let {
                    if (it > 0.dp) it + 6.dp else 64.dp
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = badgesTopPadDp, end = 8.dp)
                        .widthIn(max = 132.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (finName != null && currentWindow.financialSchemeId != "fin_none") {
                        CanvasReadOnlyInfoBadge(label = finName, bgColor = palette.accentRed)
                    }
                    if (currentWindow.profileSystemId != "none") {
                        CanvasReadOnlyInfoBadge(label = activeProfile.name, bgColor = palette.accentOrange)
                        val chosenSht = activeProfile.shtapikVariants.find { it.id == currentWindow.selectedShtapikId }
                            ?: activeProfile.shtapikVariants.minByOrNull { kotlin.math.abs(it.glazingThicknessSpMm - activeGlazing.thicknessMm) }
                            ?: activeProfile.shtapikVariants.firstOrNull()
                        if (chosenSht != null) {
                            CanvasReadOnlyInfoBadge(label = chosenSht.name, bgColor = Color(0xFFBF360C))
                        }
                    }
                    if (currentWindow.glazingId != "none") {
                        CanvasReadOnlyInfoBadge(label = activeGlazing.name, bgColor = palette.accentTeal)
                    }
                    if (currentWindow.colorId != "none") {
                        CanvasReadOnlyInfoBadge(label = activeColor.name, bgColor = Color(0xFF475569))
                    }
                    if (currentWindow.hardwareBrandId != "hw_none") {
                        CanvasReadOnlyInfoBadge(label = hwName, bgColor = palette.accentIndigo)
                    }
                    currentWindow.selectedExtraIds.forEach { eid ->
                        val extName = extraAccessoriesCatalog.find { it.id == eid }?.name
                        if (extName != null) {
                            CanvasReadOnlyInfoBadge(label = extName, bgColor = Color(0xFF00838F))
                        }
                    }
                    currentWindow.selectedWorkIds.forEach { wid ->
                        val wrkName = workServicesCatalog.find { it.id == wid }?.name
                        if (wrkName != null) {
                            CanvasReadOnlyInfoBadge(label = wrkName, bgColor = palette.accentGreen)
                        }
                    }
                }

                if (snackMessage != null) {
                    Surface(
                        color = palette.elevatedSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(0.8.dp, palette.floatingBorder),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { snackMessage = null }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(snackMessage!!, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.closeDesc,
                                tint = palette.accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // --- УЗКАЯ ПОЛОСКА КОММЕНТАРИЯ К ОКНУ НАД НИЖНИМ СКРОЛЛОМ (ТОЛЬКО ЕСЛИ ЕСТЬ ТЕКСТ) ---
            if (currentWindow.note.isNotBlank()) {
                Surface(
                    color = palette.secondaryBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRightSettingsDrawer = true }
                        .testTag("cad_window_note_strip")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = palette.divider, thickness = 0.6.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = strings.noteLabel,
                                tint = palette.accentBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = currentWindow.note,
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- ДИНАМИЧЕСКАЯ 2-СТРОЧНАЯ ПАНЕЛЬ РАСЧЁТА ("окно" / "все окна" + закреплённая колонка "всего") ---
            if (currentCalcResult != null) {
                DynamicBottomCostScrollBar(
                    currentResult = currentCalcResult,
                    allResults = allWindowResults,
                    order = order,
                    onClickTotal = { showRightSettingsDrawer = true }
                )
            }

            // --- НИЖНЯЯ ЛЕНТА КОНСТРУКЦИЙ ЗАКАЗА В СТИЛЕ iOS (КНОПКА "+" + КОПИЯ + МИНИАТЮРЫ ОКОН) ---
            Surface(
                color = palette.dockBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = palette.accentBlue,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { addNewWindowInheritingSettings() }
                                .testTag("cad_add_window_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "+ 1500×1500",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text("1500×1500", color = Color.White.copy(alpha = 0.92f), fontSize = 8.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Surface(
                            color = palette.accentTeal.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, palette.accentTeal.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .width(58.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { duplicateCurrentWindow() }
                                .testTag("cad_copy_window_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = strings.copyWindowBtn,
                                        tint = palette.accentTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = strings.copyWindowBtn,
                                        color = palette.accentTeal,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        val bottomScrollState = rememberScrollState()
                        LaunchedEffect(windows.size) {
                            bottomScrollState.scrollTo(0)
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(bottomScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            windows.mapIndexed { idx, winItem -> idx to winItem }.reversed().forEach { (idx, winItem) ->
                                val isSelected = idx == activeWindowIndex
                                val winProf = profileSystems.find { it.id == winItem.profileSystemId } ?: activeProfile
                                val winColorHex = WindowBomAndCostEngine.resolveWindowColor(
                                    window = winItem,
                                    profile = winProf,
                                    legacyColorCatalog = colorCatalog
                                ).hexColor
                                Box(
                                    modifier = Modifier
                                        .width(88.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) palette.cardSurface else palette.secondaryBackground.copy(alpha = 0.7f)
                                        )
                                        .border(
                                            width = if (isSelected) 2.2.dp else 0.8.dp,
                                            color = if (isSelected) palette.accentBlue else palette.floatingBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectWindowIndex(idx) }
                                        .padding(4.dp)
                                        .testTag("cad_window_thumb_$idx")
                                ) {
                                    WindowMiniThumbnail(
                                        topology = winItem.topology,
                                        frameColorHex = winColorHex,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 14.dp)
                                    )
                                    Text(
                                        text = "№${idx + 1} (${winItem.quantity}${strings.pcsShort})",
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) palette.accentBlue else palette.textSecondary,
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- ЛЕВОЕ БОКОВОЕ МЕНЮ ЗАКАЗА И ОТЧЁТОВ В СТИЛЕ iOS ---
        if (showLeftBlueMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable { showLeftBlueMenu = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                    color = palette.cardSurface,
                    shadowElevation = 18.dp,
                    modifier = Modifier
                        .width(292.dp)
                        .fillMaxHeight()
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.orderMenuHeader,
                                color = palette.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showLeftBlueMenu = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = strings.closeDesc,
                                    tint = palette.textSecondary
                                )
                            }
                        }
                        HorizontalDivider(color = palette.divider, thickness = 0.7.dp)

                        LeftMenuButton(
                            title = strings.backToHomeItem,
                            icon = Icons.Rounded.Home,
                            accentTint = palette.accentBlue
                        ) {
                            showLeftBlueMenu = false
                            onBackToHome()
                        }
                        LeftMenuButton(
                            title = strings.customerDetailsItem,
                            icon = Icons.Rounded.PersonOutline,
                            accentTint = palette.accentTeal
                        ) {
                            showLeftBlueMenu = false
                            showCustomerEditDialog = true
                        }
                        HorizontalDivider(
                            color = palette.divider,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LeftMenuButton(
                            title = strings.pdfDrawingsItem,
                            icon = Icons.Rounded.Description,
                            accentTint = palette.accentBlue
                        ) {
                            showLeftBlueMenu = false
                            previewReportMode = ClientPdfReportMode.DRAWINGS_ONLY
                        }
                        LeftMenuButton(
                            title = strings.pdfDrawingsDetailsItem,
                            icon = Icons.Rounded.FactCheck,
                            accentTint = palette.accentBlue
                        ) {
                            showLeftBlueMenu = false
                            previewReportMode = ClientPdfReportMode.DRAWINGS_WITH_DETAILS
                        }
                        LeftMenuButton(
                            title = strings.pdfCommercialItem,
                            icon = Icons.Rounded.ReceiptLong,
                            accentTint = palette.accentIndigo
                        ) {
                            showLeftBlueMenu = false
                            previewReportMode = ClientPdfReportMode.COMMERCIAL_PROPOSAL
                        }

                        HorizontalDivider(
                            color = palette.divider,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LeftMenuButton(
                            title = strings.pdfGlazingItem,
                            icon = Icons.Rounded.GridView,
                            accentTint = palette.accentTeal
                        ) {
                            showLeftBlueMenu = false
                            val file = PdfReportGenerator.generateGlazingSpecPdf(context, order, allWindowResults)
                            onOpenPdfFile(file, strings.pdfGlazingItem)
                        }
                        LeftMenuButton(
                            title = strings.pdfHardwareItem,
                            icon = Icons.Rounded.Build,
                            accentTint = palette.accentOrange
                        ) {
                            showLeftBlueMenu = false
                            val file = PdfReportGenerator.generateHardwareSpecPdf(context, order, allWindowResults)
                            onOpenPdfFile(file, strings.pdfHardwareItem)
                        }
                        LeftMenuButton(
                            title = strings.workshopCuttingItem,
                            icon = Icons.Rounded.ContentCut,
                            accentTint = palette.accentGreen
                        ) {
                            showLeftBlueMenu = false
                            onOpenWorkshopCutting()
                        }
                    }
                }
            }
        }

        // --- ПРАВАЯ БОКОВАЯ ШТОРКА "НАСТРОЙКИ ОКНА" В СТИЛЕ iOS ---
        if (showRightSettingsDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable { showRightSettingsDrawer = false }
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(344.dp)
                        .fillMaxHeight()
                        .clickable(enabled = false) {},
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    shadowElevation = 18.dp
                ) {
                    RightWindowSettingsDrawerContent(
                        order = order,
                        windowItem = currentWindow,
                        windowIndex = activeWindowIndex,
                        totalWindowsCount = windows.size,
                        profileSystems = profileSystems,
                        glazingCatalog = glazingCatalog,
                        colorCatalog = colorCatalog,
                        hardwareBrands = hardwareBrandsCatalog,
                        extraAccessories = extraAccessoriesCatalog,
                        workServices = workServicesCatalog,
                        onUpdateWindow = { updatedWin -> updateCurrentWindow { updatedWin } },
                        onOpenManualPriceNumpad = { showManualPriceNumpad = true },
                        onUpdateOrderDiscount = { newDiscount, newVat ->
                            onUpdateOrder(order.copy(discountPercent = newDiscount, taxVatPercent = newVat))
                        },
                        onApplyToAllWindows = { applyProf, applyGlz, applyClr, applyHw, applyFin, applyExtWrk ->
                            applySettingsToAllWindowsInOrder(
                                applyProfile = applyProf,
                                applyGlazing = applyGlz,
                                applyColors = applyClr,
                                applyHardware = applyHw,
                                applyFinancials = applyFin,
                                applyExtrasAndWorks = applyExtWrk
                            )
                        },
                        onClose = { showRightSettingsDrawer = false }
                    )
                }
            }
        }
    }

    // --- КАЛЬКУЛЯТОР "СВОЯ ЦЕНА" ---
    if (showManualPriceNumpad) {
        val displayCurrency = normalizeCurrencyDisplay(order.currencySymbol)
        OriginalDecimalNumpadModalDialog(
            title = "${strings.customPriceLabel.replaceFirstChar { it.uppercase() }} ($displayCurrency)",
            initialValue = currentWindow.manualUnitPriceOverride,
            onDismiss = { showManualPriceNumpad = false },
            onConfirm = { newCustomPrice ->
                updateCurrentWindow { it.copy(manualUnitPriceOverride = newCustomPrice) }
                showManualPriceNumpad = false
            }
        )
    }

    // --- МОДАЛЬНОЕ ОКНО: ИМПОСТЫ | ОТКРЫВАНИЕ (ПРИ КЛИКЕ НА ПРОЁМ ИЛИ СТВОРКУ) ---
    val targetCell = activeCellForModal
    if (targetCell != null) {
        ImpostsAndOpeningModalDialog(
            cell = targetCell,
            onDismiss = { activeCellForModal = null },
            onApply = { splitOp, newOpeningType, isSandwich, hasMosquito, isDoor, isReverseDoor, hasHorizSashImpost ->
                updateCurrentWindow { win ->
                    var topo = win.topology
                    val neighborHeightMm = WindowCadOperations.findNeighborHorizontalImpostHeightFromBottom(
                        topology = topo,
                        openingId = targetCell.id
                    )
                    val snapHeightMm = neighborHeightMm ?: targetCell.properties.sashImpostHeightFromBottomMm

                    val newProps = targetCell.properties.copy(
                        openingType = newOpeningType,
                        isDoorSash = isDoor,
                        isReverseDoorSash = isReverseDoor,
                        isSandwichFilling = isSandwich,
                        hasMosquitoNet = hasMosquito,
                        hasHorizontalSashImpost = hasHorizSashImpost,
                        sashImpostHeightFromBottomMm = snapHeightMm
                    )

                    topo = WindowCadOperations.updateOpeningProperties(topo, targetCell.id, newProps)

                    topo = when (splitOp) {
                        SplitOperation.NONE -> topo
                        SplitOperation.SPLIT_V2 -> WindowCadOperations.splitOpening(topo, targetCell.id, ImpostDirection.VERTICAL, 2)
                        SplitOperation.SPLIT_H2 -> WindowCadOperations.splitOpening(topo, targetCell.id, ImpostDirection.HORIZONTAL, 2)
                        SplitOperation.SPLIT_V3 -> WindowCadOperations.splitOpening(topo, targetCell.id, ImpostDirection.VERTICAL, 3)
                        SplitOperation.SPLIT_H3 -> WindowCadOperations.splitOpening(topo, targetCell.id, ImpostDirection.HORIZONTAL, 3)
                        SplitOperation.SPLIT_SHTULP_LEFT_MAIN, SplitOperation.SPLIT_SHTULP_RIGHT_MAIN -> topo
                    }
                    win.copy(topology = topo)
                }
                activeCellForModal = null
            }
        )
    }

    // --- МОДАЛЬНОЕ ОКНО: ЦИФРОВОЙ КАЛЬКУЛЯТОР РАЗМЕРОВ ---
    val activeNumpad = numpadRequest
    if (activeNumpad != null) {
        OriginalNumpadModalDialog(
            title = activeNumpad.title,
            initialValue = activeNumpad.initialValue,
            onDismiss = { numpadRequest = null },
            onConfirm = { newVal ->
                when (activeNumpad) {
                    is NumpadRequestState.OverallWidth -> {
                        val clamped = newVal.toFloat().coerceIn(300f, 8000f)
                        updateCurrentWindow { win ->
                            win.copy(
                                topology = WindowCadOperations.resizeTotalDimensions(
                                    win.topology,
                                    clamped,
                                    win.topology.totalHeightMm
                                )
                            )
                        }
                    }
                    is NumpadRequestState.OverallHeight -> {
                        val clamped = newVal.toFloat().coerceIn(300f, 6000f)
                        updateCurrentWindow { win ->
                            win.copy(
                                topology = WindowCadOperations.resizeTotalDimensions(
                                    win.topology,
                                    win.topology.totalWidthMm,
                                    clamped
                                )
                            )
                        }
                    }
                    is NumpadRequestState.ChainSegment -> {
                        updateCurrentWindow { win ->
                            win.copy(
                                topology = WindowCadOperations.updateChainSegmentLength(
                                    topology = win.topology,
                                    isHorizontalAxis = activeNumpad.isHorizontalAxis,
                                    segmentIndex = activeNumpad.segmentIndex,
                                    newSegmentLengthMm = newVal.toFloat()
                                )
                            )
                        }
                    }
                    is NumpadRequestState.DoorSashImpostHeight -> {
                        updateCurrentWindow { win ->
                            win.copy(
                                topology = WindowCadOperations.updateSashImpostHeightFromBottom(
                                    topology = win.topology,
                                    openingId = activeNumpad.openingId,
                                    newHeightFromBottomMm = newVal.toFloat()
                                )
                            )
                        }
                    }
                }
                numpadRequest = null
            }
        )
    }

    // --- МОДАЛЬНОЕ ОКНО: ВЫБОР СПОСОБА РАЗБИЕНИЯ ИМПОСТОВ (ПО ЧЕРТЕЖУ / ПО ПРОЁМАМ / ПО СВЕТУ) ---
    if (showAlignmentDialog) {
        ImpostAlignmentModeDialog(
            currentMode = currentAlignmentMode,
            onDismiss = { showAlignmentDialog = false },
            onSelectMode = { chosenMode, directionFilter ->
                currentAlignmentMode = chosenMode
                updateCurrentWindow { win ->
                    var topo = win.topology
                    if (directionFilter == null || directionFilter == ImpostDirection.VERTICAL) {
                        topo = WindowCadOperations.equalizeAxisSegments(
                            topology = topo,
                            isHorizontalAxis = true,
                            mode = chosenMode,
                            profileSystem = activeProfile
                        )
                    }
                    if (directionFilter == null || directionFilter == ImpostDirection.HORIZONTAL) {
                        topo = WindowCadOperations.equalizeAxisSegments(
                            topology = topo,
                            isHorizontalAxis = false,
                            mode = chosenMode,
                            profileSystem = activeProfile
                        )
                    }
                    win.copy(topology = topo)
                }
                showAlignmentDialog = false
                snackMessage = "${strings.impostsShortBtn}: ${chosenMode.titleRu}"
            }
        )
    }

    // --- МОДАЛЬНОЕ ОКНО: ДАННЫЕ КЛИЕНТА И НОМЕР ЗАКАЗА ---
    if (showCustomerEditDialog) {
        CustomerOrderDetailsDialog(
            order = order,
            onDismiss = { showCustomerEditDialog = false },
            onSave = { updatedOrder ->
                onUpdateOrder(updatedOrder)
                showCustomerEditDialog = false
            }
        )
    }

    // --- МОДАЛЬНОЕ ОКНО ПРЕДПРОСМОТРА РЕАЛЬНОГО PDF-ДОКУМЕНТА И СОХРАНЕНИЯ / ОТПРАВКИ PDF ---
    val activePreviewMode = previewReportMode
    if (activePreviewMode != null) {
        InAppReportPreviewDialog(
            order = order,
            windowResults = allWindowResults,
            mode = activePreviewMode,
            uiSettings = uiSettings,
            extraAccessoriesCatalog = extraAccessoriesCatalog,
            workServicesCatalog = workServicesCatalog,
            hardwareBrandsCatalog = hardwareBrandsCatalog,
            onSwitchMode = { previewReportMode = it },
            onDismiss = { previewReportMode = null },
            onOpenPdfFile = { file, title ->
                previewReportMode = null
                onOpenPdfFile(file, title)
            },
            onSharePdfFile = { file, title ->
                previewReportMode = null
                onSharePdfFile(file, title)
            }
        )
    }
}

/**
 * Компактная информационная плашка справа на холсте (только для чтения, не перекрывает клики по чертежу!).
 */
@Composable
private fun CanvasReadOnlyInfoBadge(
    label: String,
    bgColor: Color
) {
    Surface(
        color = bgColor.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.48f)),
        shadowElevation = 0.dp
    ) {
        Text(
            text = label,
            color = Color.White,
            style = TextStyle(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 11.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.55f),
                    offset = Offset(0f, 1f),
                    blurRadius = 3f
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * Нижняя динамическая 2-строчная панель с горизонтальным скроллом метров, штук и сумм,
 * и закреплённой колонкой «всего» справа в стиле iOS.
 */
@Composable
private fun DynamicBottomCostScrollBar(
    currentResult: WindowCalculationResult,
    allResults: List<WindowCalculationResult>,
    order: CustomerOrder,
    onClickTotal: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val curQty = currentResult.windowItem.quantity.coerceAtLeast(1)
    val cur = normalizeCurrencyDisplay(order.currencySymbol)

    fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

    data class CostColumnData(
        val header: String,
        val winTop: String,
        val winBottom: String,
        val allTop: String,
        val allBottom: String
    )

    val columns = remember(currentResult, allResults, cur, strings) {
        val winArea = currentResult.totalAreaM2 * curQty
        val allArea = allResults.sumOf { it.totalAreaM2 * it.windowItem.quantity }

        val winFrameM = currentResult.framePerimeterM * curQty
        val winFrameCost = currentResult.costFrame * curQty
        val allFrameM = allResults.sumOf { it.framePerimeterM * it.windowItem.quantity }
        val allFrameCost = allResults.sumOf { it.costFrame * it.windowItem.quantity }

        val winImpM = currentResult.impostsLengthM * curQty
        val winImpCost = currentResult.costImposts * curQty
        val allImpM = allResults.sumOf { it.impostsLengthM * it.windowItem.quantity }
        val allImpCost = allResults.sumOf { it.costImposts * it.windowItem.quantity }

        val winSashM = currentResult.sashPerimeterM * curQty
        val winSashCost = currentResult.costSashes * curQty
        val allSashM = allResults.sumOf { it.sashPerimeterM * it.windowItem.quantity }
        val allSashCost = allResults.sumOf { it.costSashes * it.windowItem.quantity }

        val winDoorM = currentResult.doorSashPerimeterM * curQty
        val winDoorCost = currentResult.costDoorSashes * curQty
        val allDoorM = allResults.sumOf { it.doorSashPerimeterM * it.windowItem.quantity }
        val allDoorCost = allResults.sumOf { it.costDoorSashes * it.windowItem.quantity }

        val winCornersCnt = currentResult.cornerConnectorsCount * curQty
        val winCornersCost = currentResult.costCornerConnectors * curQty
        val allCornersCnt = allResults.sumOf { it.cornerConnectorsCount * it.windowItem.quantity }
        val allCornersCost = allResults.sumOf { it.costCornerConnectors * it.windowItem.quantity }

        val winShtM = currentResult.shtapikPerimeterM * curQty
        val winShtCost = currentResult.costShtapik * curQty
        val allShtM = allResults.sumOf { it.shtapikPerimeterM * it.windowItem.quantity }
        val allShtCost = allResults.sumOf { it.costShtapik * it.windowItem.quantity }

        val winRubM = currentResult.rubberPerimeterM * curQty
        val winRubCost = currentResult.costRubber * curQty
        val allRubM = allResults.sumOf { it.rubberPerimeterM * it.windowItem.quantity }
        val allRubCost = allResults.sumOf { it.costRubber * it.windowItem.quantity }

        val winGlzM2 = currentResult.glazingAreaM2 * curQty
        val winGlzCost = currentResult.costGlazingOnly * curQty
        val allGlzM2 = allResults.sumOf { it.glazingAreaM2 * it.windowItem.quantity }
        val allGlzCost = allResults.sumOf { it.costGlazingOnly * it.windowItem.quantity }

        val winSandM2 = currentResult.sandwichAreaM2 * curQty
        val winSandCost = currentResult.costSandwichOnly * curQty
        val allSandM2 = allResults.sumOf { it.sandwichAreaM2 * it.windowItem.quantity }
        val allSandCost = allResults.sumOf { it.costSandwichOnly * it.windowItem.quantity }

        val winHwCost = currentResult.costHardware * curQty
        val allHwCost = allResults.sumOf { it.costHardware * it.windowItem.quantity }

        val winExtCost = currentResult.costExtrasOnly * curQty
        val allExtCost = allResults.sumOf { it.costExtrasOnly * it.windowItem.quantity }

        val winWrkCost = currentResult.costWorksOnly * curQty
        val allWrkCost = allResults.sumOf { it.costWorksOnly * it.windowItem.quantity }

        val winPaintCost = currentResult.costCustomPainting
        val allPaintCost = allResults.sumOf { it.costCustomPainting }

        buildList {
            add(CostColumnData(strings.colArea, "${fmt2(winArea)} m2", "", "${fmt2(allArea)} m2", ""))
            add(CostColumnData(strings.colFrames, "${fmt2(winFrameM)} m", "${fmt2(winFrameCost)} $cur", "${fmt2(allFrameM)} m", "${fmt2(allFrameCost)} $cur"))
            add(CostColumnData(strings.colImposts, "${fmt2(winImpM)} m", "${fmt2(winImpCost)} $cur", "${fmt2(allImpM)} m", "${fmt2(allImpCost)} $cur"))
            add(CostColumnData(strings.colSashes, "${fmt2(winSashM)} m", "${fmt2(winSashCost)} $cur", "${fmt2(allSashM)} m", "${fmt2(allSashCost)} $cur"))
            add(CostColumnData(strings.colDoorSashes, "${fmt2(winDoorM)} m", "${fmt2(winDoorCost)} $cur", "${fmt2(allDoorM)} m", "${fmt2(allDoorCost)} $cur"))
            add(CostColumnData(strings.colCleats, "$winCornersCnt ${strings.setsShort}", "${fmt2(winCornersCost)} $cur", "$allCornersCnt ${strings.setsShort}", "${fmt2(allCornersCost)} $cur"))
            add(CostColumnData(strings.colShtapik, "${fmt2(winShtM)} m", "${fmt2(winShtCost)} $cur", "${fmt2(allShtM)} m", "${fmt2(allShtCost)} $cur"))
            add(CostColumnData(strings.colRubber, "${fmt2(winRubM)} m", "${fmt2(winRubCost)} $cur", "${fmt2(allRubM)} m", "${fmt2(allRubCost)} $cur"))
            add(CostColumnData(strings.colGlass, "${fmt2(winGlzM2)} m2", "${fmt2(winGlzCost)} $cur", "${fmt2(allGlzM2)} m2", "${fmt2(allGlzCost)} $cur"))
            add(CostColumnData(strings.colSandwich, "${fmt2(winSandM2)} m2", "${fmt2(winSandCost)} $cur", "${fmt2(allSandM2)} m2", "${fmt2(allSandCost)} $cur"))
            add(CostColumnData(strings.colHardware, "${fmt2(winHwCost)} $cur", "", "${fmt2(allHwCost)} $cur", ""))
            add(CostColumnData(strings.colExtras, "${fmt2(winExtCost)} $cur", "", "${fmt2(allExtCost)} $cur", ""))
            add(CostColumnData(strings.colWorks, "${fmt2(winWrkCost)} $cur", "", "${fmt2(allWrkCost)} $cur", ""))
            if (allPaintCost > 0.0 || winPaintCost > 0.0) {
                add(CostColumnData(strings.colPainting, "${fmt2(winPaintCost)} $cur", "", "${fmt2(allPaintCost)} $cur", ""))
            }
        }
    }

    val isManualPrice = currentResult.windowItem.manualUnitPriceOverride != null
    val discPct = if (currentResult.discountPercentApplied > 0.0) currentResult.discountPercentApplied else order.discountPercent
    val winBaseTotal = (currentResult.windowItem.manualUnitPriceOverride ?: currentResult.rawUnitCostBeforeDiscount) * curQty + currentResult.costCustomPainting
    val winFinalTotal = (currentResult.windowItem.manualUnitPriceOverride ?: currentResult.rawUnitCostBeforeDiscount) * curQty * (1.0 - discPct / 100.0) + currentResult.costCustomPainting

    val orderBaseTotal = allResults.sumOf {
        val u = it.windowItem.manualUnitPriceOverride ?: it.rawUnitCostBeforeDiscount
        u * it.windowItem.quantity + it.costCustomPainting
    }
    val orderDiscountedTotal = allResults.sumOf {
        val d = if (it.discountPercentApplied > 0.0) it.discountPercentApplied else order.discountPercent
        val u = it.windowItem.manualUnitPriceOverride ?: it.rawUnitCostBeforeDiscount
        u * (1.0 - d / 100.0) * it.windowItem.quantity + it.costCustomPainting
    }

    Surface(
        color = palette.costBarBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Левая фиксированная колонка меток строк ("окно" / "все окна")
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight()
                        .background(palette.costBarSideBg)
                        .padding(horizontal = 5.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(strings.rowWindow, color = palette.textSecondary, fontSize = 9.5.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                    Text(strings.rowAllWindows, color = palette.textSecondary, fontSize = 9.5.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                }

                // Центральный горизонтальный скролл по всем статьям затрат
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columns.forEach { col ->
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = col.header,
                                color = palette.textSecondary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(col.winTop, color = palette.textPrimary, fontSize = 8.5.sp, fontWeight = FontWeight.Medium, lineHeight = 9.5.sp, maxLines = 1)
                                if (col.winBottom.isNotEmpty()) {
                                    Text(col.winBottom, color = palette.textSecondary, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(col.allTop, color = palette.textSecondary, fontSize = 8.5.sp, lineHeight = 9.5.sp, maxLines = 1)
                                if (col.allBottom.isNotEmpty()) {
                                    Text(col.allBottom, color = palette.textSecondary.copy(alpha = 0.8f), fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1)
                                }
                            }
                        }
                        VerticalDivider(color = palette.divider, modifier = Modifier.fillMaxHeight())
                    }
                }

                // Правая закреплённая колонка "всего"
                Column(
                    modifier = Modifier
                        .width(98.dp)
                        .fillMaxHeight()
                        .background(palette.costBarSideBg)
                        .clickable { onClickTotal() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("cad_cost_total_box"),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.colTotal, color = palette.accentBlue, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${fmt2(winBaseTotal)} $cur",
                            color = if (isManualPrice) palette.accentOrange else palette.textPrimary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp,
                            maxLines = 1
                        )
                        if (discPct > 0.0) {
                            Text(
                                text = "${fmt2(winFinalTotal)} $cur",
                                color = palette.accentGreen,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 9.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${fmt2(orderBaseTotal)} $cur",
                            color = palette.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 10.sp,
                            maxLines = 1
                        )
                        if (orderDiscountedTotal < orderBaseTotal - 0.01) {
                            Text(
                                text = "${fmt2(orderDiscountedTotal)} $cur",
                                color = palette.accentGreen,
                                fontSize = 8.5.sp,
                                lineHeight = 9.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftMenuButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    accentTint: Color = LocalWinStudioPalette.current.accentBlue,
    onClick: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentTint,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

// =========================================================================================
// ПРАВАЯ ШТОРКА "НАСТРОЙКИ ОКНА" (ТОЧНО КАК НА 00:31 - 01:12 В ВИДЕО + ПАКЕТНОЕ ПРИМЕНЕНИЕ)
// =========================================================================================
@Composable
private fun RightWindowSettingsDrawerContent(
    order: CustomerOrder,
    windowItem: WindowItem,
    windowIndex: Int,
    totalWindowsCount: Int,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    hardwareBrands: List<HardwareBrandItem>,
    extraAccessories: List<ExtraAccessoryItem>,
    workServices: List<WorkServiceItem>,
    onUpdateWindow: (WindowItem) -> Unit,
    onOpenManualPriceNumpad: () -> Unit,
    onUpdateOrderDiscount: (Double, Double) -> Unit,
    onApplyToAllWindows: (
        applyProfile: Boolean,
        applyGlazing: Boolean,
        applyColors: Boolean,
        applyHardware: Boolean,
        applyFinancials: Boolean,
        applyExtrasAndWorks: Boolean
    ) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val displayCurrency = normalizeCurrencyDisplay(order.currencySymbol)
    val scroll = rememberScrollState()
    val financialSchemes = remember { DefaultCatalogs.defaultFinancialSchemes() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.cardSurface)
    ) {
        // Верхний заголовок шторки в стиле iOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(strings.windowSettingsHeaderFormat, windowIndex + 1),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.segmentedBg)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = strings.closeDesc,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(color = palette.divider, thickness = 0.7.dp)

        // Прокручиваемое содержимое шторки
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. КОЛИЧЕСТВО (- | 1 | +)
            DrawerGrayHeader(strings.quantityLabel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = palette.accentBlue,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (windowItem.quantity > 1) {
                                onUpdateWindow(windowItem.copy(quantity = windowItem.quantity - 1))
                            }
                        }
                        .testTag("drawer_qty_minus")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("−", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    color = palette.secondaryBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${windowItem.quantity}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }
                }
                Surface(
                    color = palette.accentBlue,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onUpdateWindow(windowItem.copy(quantity = windowItem.quantity + 1))
                        }
                        .testTag("drawer_qty_plus")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. ЗАМЕТКА
            DrawerGrayHeader(strings.noteLabel)
            OutlinedTextField(
                value = windowItem.note,
                onValueChange = { onUpdateWindow(windowItem.copy(note = it)) },
                placeholder = { Text(strings.notePlaceholder, fontSize = 12.sp) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 3. СВОЯ ЦЕНА
            DrawerGrayHeader(strings.customPriceLabel)
            Surface(
                color = palette.accentOrange.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.accentOrange.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenManualPriceNumpad() }
                    .testTag("drawer_custom_price_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val manualVal = windowItem.manualUnitPriceOverride ?: 0.0
                    Text(
                        text = "${String.format(Locale.US, "%.2f", manualVal)} $displayCurrency",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = strings.customPriceHintSuffix,
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Кнопка пакетного применения настроек ко всем окнам заказа
            if (totalWindowsCount > 1) {
                Button(
                    onClick = {
                        onApplyToAllWindows(true, true, true, true, true, true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = String.format(strings.applyAllWindowsBtnFormat, totalWindowsCount),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 4. ФИНАНСОВАЯ СХЕМА
            DrawerGrayHeader(
                title = strings.finSchemeLabel,
                actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                onAction = { onApplyToAllWindows(false, false, false, false, true, false) }
            )
            financialSchemes.forEach { fin ->
                val selected = windowItem.financialSchemeId == fin.id
                DrawerRadioOptionRow(
                    label = fin.name,
                    selected = selected,
                    onClick = { onUpdateWindow(windowItem.copy(financialSchemeId = fin.id)) }
                )
            }

            // 5. ОКОННАЯ СИСТЕМА
            DrawerGrayHeader(
                title = strings.windowSystemLabel,
                actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                onAction = { onApplyToAllWindows(true, false, false, false, false, false) }
            )
            DrawerRadioOptionRow(
                label = strings.notAccounted,
                selected = windowItem.profileSystemId == "none",
                onClick = { onUpdateWindow(windowItem.copy(profileSystemId = "none")) }
            )
            profileSystems.forEach { sys ->
                val selected = sys.id == windowItem.profileSystemId
                DrawerRadioOptionRow(
                    label = sys.name,
                    subtitle = "${sys.priceFramePerM} / ${sys.priceSashPerM} / ${sys.priceDoorSashPerM} $displayCurrency/м",
                    selected = selected,
                    onClick = {
                        val defaultShtId = sys.shtapikVariants.firstOrNull()?.id ?: ""
                        val keptColorId = when {
                            windowItem.colorId == CUSTOM_CLIENT_COLOR_ID -> CUSTOM_CLIENT_COLOR_ID
                            sys.colors.any { it.id == windowItem.colorId } -> windowItem.colorId
                            else -> sys.colors.firstOrNull()?.id ?: "col_anthracite"
                        }
                        onUpdateWindow(
                            windowItem.copy(
                                profileSystemId = sys.id,
                                selectedShtapikId = defaultShtId,
                                colorId = keptColorId
                            )
                        )
                    }
                )
            }

            // 5.1. ВЫБОР ШТАПИКА ВЫБРАННОЙ ПРОФИЛЬНОЙ СИСТЕМЫ
            val currentSysForShtapik = profileSystems.find { it.id == windowItem.profileSystemId }
            if (currentSysForShtapik != null && currentSysForShtapik.shtapikVariants.isNotEmpty()) {
                DrawerGrayHeader(
                    title = String.format(strings.shtapikLabelFormat, currentSysForShtapik.name),
                    actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                    onAction = { onApplyToAllWindows(true, false, false, false, false, false) }
                )
                val activeGlzThickness = glazingCatalog.find { it.id == windowItem.glazingId }?.thicknessMm ?: 24
                val effectiveShtId = windowItem.selectedShtapikId.takeIf { id ->
                    currentSysForShtapik.shtapikVariants.any { it.id == id }
                } ?: (
                    currentSysForShtapik.shtapikVariants.minByOrNull {
                        kotlin.math.abs(it.glazingThicknessSpMm - activeGlzThickness)
                    } ?: currentSysForShtapik.shtapikVariants.first()
                ).id

                currentSysForShtapik.shtapikVariants.forEach { sht ->
                    val isShtSelected = sht.id == effectiveShtId
                    DrawerRadioOptionRow(
                        label = "${sht.name} (${sht.glazingThicknessSpMm} мм)",
                        subtitle = "H1=${fmtMm(sht.heightH1Mm)} мм • H2=${fmtMm(sht.widthH2Mm)} мм • ${sht.pricePerM} $displayCurrency/м",
                        selected = isShtSelected,
                        onClick = {
                            onUpdateWindow(windowItem.copy(selectedShtapikId = sht.id))
                        }
                    )
                }
            }

            // 6. СТЕКЛОПАКЕТЫ + ПЕРЕКЛЮЧАТЕЛЬ ОКРУГЛЕНИЯ ДО 0 / 5 ММ
            Surface(
                color = palette.secondaryBackground,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.glazingLabel,
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (totalWindowsCount > 1) {
                            Text(
                                text = strings.applyShort,
                                color = palette.accentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    onApplyToAllWindows(false, true, false, false, false, false)
                                }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = strings.round05MmLabel,
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = windowItem.roundGlassTo5Mm,
                            onCheckedChange = { round5 ->
                                onUpdateWindow(windowItem.copy(roundGlassTo5Mm = round5))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = palette.accentGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = palette.divider
                            )
                        )
                    }
                }
            }
            glazingCatalog.forEach { glz ->
                val selected = glz.id == windowItem.glazingId
                DrawerRadioOptionRow(
                    label = glz.name,
                    subtitle = if (glz.id == "none") null else "${glz.pricePerM2} $displayCurrency/m2",
                    selected = selected,
                    onClick = { onUpdateWindow(windowItem.copy(glazingId = glz.id)) }
                )
            }

            // 7. ЦВЕТ (строго цвета выбранной профильной системы + «Индивидуальный цвет клиента...»)
            var showCustomColorDialog by remember { mutableStateOf(false) }
            val activeSysForColors = profileSystems.find { it.id == windowItem.profileSystemId }
            DrawerGrayHeader(
                title = strings.colorLabel,
                actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                onAction = { onApplyToAllWindows(false, false, true, false, false, false) }
            )
            if (windowItem.profileSystemId == "none" || activeSysForColors == null) {
                Text(
                    text = "Выберите профильную систему выше для подбора цвета",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            } else {
                activeSysForColors.colors.forEach { clr ->
                    val selected = clr.id == windowItem.colorId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onUpdateWindow(windowItem.copy(colorId = clr.id)) }
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                            .testTag("drawer_color_${clr.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onUpdateWindow(windowItem.copy(colorId = clr.id)) },
                            colors = RadioButtonDefaults.colors(selectedColor = palette.accentBlue)
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(clr.hexColor), CircleShape)
                                .border(0.9.dp, palette.floatingBorder, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val titleWithRal = if (clr.ralCode.isNotBlank()) "${clr.name} (RAL ${clr.ralCode})" else clr.name
                            Text(
                                text = titleWithRal,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = palette.textPrimary
                            )
                            if (clr.priceMarkupPercent > 0.0) {
                                Text(
                                    text = "+${clr.priceMarkupPercent.roundToInt()}% к профилю",
                                    fontSize = 11.sp,
                                    color = palette.accentOrange
                                )
                            }
                        }
                    }
                }
            }

            // Последний пункт списка всегда: «🎨 Индивидуальный цвет клиента...»
            val isCustomSelected = windowItem.colorId == CUSTOM_CLIENT_COLOR_ID
            Surface(
                color = if (isCustomSelected) palette.accentBlue.copy(alpha = 0.11f) else palette.secondaryBackground.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = if (isCustomSelected) 1.2.dp else 0.7.dp,
                    color = if (isCustomSelected) palette.accentBlue else palette.floatingBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showCustomColorDialog = true }
                    .testTag("drawer_custom_client_color_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { showCustomColorDialog = true },
                        colors = RadioButtonDefaults.colors(selectedColor = palette.accentBlue)
                    )
                    if (isCustomSelected) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(windowItem.customColorHex), CircleShape)
                                .border(0.9.dp, palette.floatingBorder, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.customClientColorItem,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentBlue
                        )
                        if (isCustomSelected && (windowItem.customColorName.isNotBlank() || windowItem.customPaintingCost > 0.0)) {
                            val ralTxt = if (windowItem.customColorRal.isNotBlank()) " RAL ${windowItem.customColorRal}" else ""
                            val paintTxt = if (windowItem.customPaintingCost > 0.0) " • Покраска: ${windowItem.customPaintingCost.roundToInt()} $displayCurrency" else ""
                            Text(
                                text = "${windowItem.customColorName.ifBlank { "Цвет клиента" }}$ralTxt$paintTxt",
                                fontSize = 11.sp,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }

            if (showCustomColorDialog) {
                CustomClientColorDialog(
                    initialName = windowItem.customColorName,
                    initialRal = windowItem.customColorRal,
                    initialHex = windowItem.customColorHex,
                    initialPaintingCost = windowItem.customPaintingCost,
                    initialUseRawForStock = windowItem.customColorUseRawForStock,
                    currencySymbol = displayCurrency,
                    onDismiss = { showCustomColorDialog = false },
                    onSave = { cName, cRal, cHex, pCost, useRaw ->
                        onUpdateWindow(
                            windowItem.copy(
                                colorId = CUSTOM_CLIENT_COLOR_ID,
                                customColorName = cName,
                                customColorRal = cRal,
                                customColorHex = cHex,
                                customPaintingCost = pCost,
                                customColorUseRawForStock = useRaw
                            )
                        )
                        showCustomColorDialog = false
                    }
                )
            }

            // 8. ФУРНИТУРА
            DrawerGrayHeader(
                title = strings.hardwareLabel,
                actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                onAction = { onApplyToAllWindows(false, false, false, true, false, false) }
            )
            hardwareBrands.forEach { hw ->
                val selected = windowItem.hardwareBrandId == hw.id
                DrawerRadioOptionRow(
                    label = hw.name,
                    subtitle = if (hw.id == "hw_none") null else "+${hw.priceMultiplierOrAddon.roundToInt()} $displayCurrency",
                    selected = selected,
                    onClick = { onUpdateWindow(windowItem.copy(hardwareBrandId = hw.id)) }
                )
            }

            // 9. РАЗНОЕ
            DrawerGrayHeader(
                title = strings.extrasLabel,
                actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                onAction = { onApplyToAllWindows(false, false, false, false, false, true) }
            )
            extraAccessories.forEach { item ->
                val checked = windowItem.selectedExtraIds.contains(item.id)
                DrawerCheckboxOptionRow(
                    label = item.name,
                    subtitle = "${item.price} $displayCurrency/${item.rateType.labelRu}",
                    checked = checked,
                    onToggle = {
                        val next = if (checked) windowItem.selectedExtraIds - item.id else windowItem.selectedExtraIds + item.id
                        onUpdateWindow(windowItem.copy(selectedExtraIds = next))
                    }
                )
            }

            // 10. РАБОТЫ
            DrawerGrayHeader(
                title = strings.worksLabel,
                actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                onAction = { onApplyToAllWindows(false, false, false, false, false, true) }
            )
            workServices.forEach { item ->
                val checked = windowItem.selectedWorkIds.contains(item.id)
                DrawerCheckboxOptionRow(
                    label = item.name,
                    subtitle = "${item.price} $displayCurrency/${item.rateType.labelRu}",
                    checked = checked,
                    onToggle = {
                        val next = if (checked) windowItem.selectedWorkIds - item.id else windowItem.selectedWorkIds + item.id
                        onUpdateWindow(windowItem.copy(selectedWorkIds = next))
                    }
                )
            }

            // Общий НДС и скидка заказа
            DrawerGrayHeader(strings.vatAndDiscountLabel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = order.discountPercent.roundToInt().toString(),
                    onValueChange = { v ->
                        val d = (v.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 90.0)
                        onUpdateOrderDiscount(d, order.taxVatPercent)
                    },
                    label = { Text(strings.orderDiscountPct, fontSize = 10.sp) },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = order.taxVatPercent.roundToInt().toString(),
                    onValueChange = { v ->
                        val vat = (v.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 50.0)
                        onUpdateOrderDiscount(order.discountPercent, vat)
                    },
                    label = { Text(strings.vatPct, fontSize = 10.sp) },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        // Нижняя зелёная iOS-кнопка "Готово"
        Surface(
            color = palette.cardSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
                Box(modifier = Modifier.padding(12.dp)) {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drawer_done_button")
                    ) {
                        Text(
                            text = strings.okBtn,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerGrayHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.secondaryBackground,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                color = palette.textSecondary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
            if (actionText != null && onAction != null) {
                Text(
                    text = actionText,
                    fontSize = 11.5.sp,
                    color = palette.accentBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onAction() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerRadioOptionRow(
    label: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = palette.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = palette.textSecondary
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = palette.accentBlue)
        )
    }
}

@Composable
private fun DrawerCheckboxOptionRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                color = palette.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = palette.textSecondary
                )
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = palette.accentBlue)
        )
    }
}

// =========================================================================================
// ДИАЛОГ РЕДАКТИРОВАНИЯ ДАННЫХ КЛИЕНТА И НОМЕРА ЗАКАЗА
// =========================================================================================
@Composable
private fun CustomerOrderDetailsDialog(
    order: CustomerOrder,
    onDismiss: () -> Unit,
    onSave: (CustomerOrder) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var orderNumber by remember { mutableStateOf(order.orderNumber) }
    var customerName by remember { mutableStateOf(order.customerName) }
    var customerPhone by remember { mutableStateOf(order.customerPhone) }
    var address by remember { mutableStateOf(order.address) }
    var comment by remember { mutableStateOf(order.orderNote) }
    var status by remember { mutableStateOf(order.status) }
    var contractDateMs by remember { mutableStateOf(order.contractDateMillis) }
    var contractTermDays by remember { mutableIntStateOf(order.contractTermDays.coerceAtLeast(1)) }
    var showContractCalendar by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f),
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
                    text = strings.customerDialogTitle,
                    fontSize = 18.sp,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.customerDialogHint,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                OutlinedTextField(
                    value = orderNumber,
                    onValueChange = { orderNumber = it },
                    label = { Text(strings.orderNumberField) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text(strings.customerNameField) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text(strings.phoneField) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(strings.addressField) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(strings.orderNoteField) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = strings.orderStatusField,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrderStatus.entries.forEach { st ->
                        val stColor = Color(st.badgeColorHex)
                        val isSel = status == st
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                status = st
                                if (st == OrderStatus.CONTRACT) {
                                    showContractCalendar = true
                                }
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (isSel) Color.White else stColor, CircleShape)
                                )
                            },
                            label = {
                                Text(
                                    text = if (strings.language == AppLanguage.EN) st.titleEn else st.titleRu,
                                    fontSize = 11.5.sp,
                                    color = if (isSel) Color.White else palette.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = stColor,
                                containerColor = palette.secondaryBackground
                            )
                        )
                    }
                }

                if (status.showDeadlineSmiley) {
                    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
                    val summary = if (contractDateMs != null) {
                        "Договор от ${dateFormat.format(Date(contractDateMs!!))} • Срок: $contractTermDays дн."
                    } else {
                        "Дата договора не выбрана — нажмите для выбора"
                    }
                    OutlinedButton(
                        onClick = { showContractCalendar = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = strings.contractDialogTitle,
                            tint = palette.accentTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(summary, fontSize = 12.sp, color = palette.textPrimary)
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
                        onClick = {
                            onSave(
                                order.copy(
                                    orderNumber = orderNumber.ifBlank { order.orderNumber },
                                    customerName = customerName.trim(),
                                    customerPhone = customerPhone.trim(),
                                    address = address.trim(),
                                    orderNote = comment.trim(),
                                    status = status,
                                    contractDateMillis = contractDateMs,
                                    contractTermDays = contractTermDays
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(strings.saveBtn, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showContractCalendar) {
        ContractDeadlineDialog(
            initialDateMillis = contractDateMs,
            initialTermDays = contractTermDays,
            onDismiss = { showContractCalendar = false },
            onConfirm = { pickedDateMs, termDays ->
                contractDateMs = pickedDateMs
                contractTermDays = termDays
                showContractCalendar = false
            }
        )
    }
}

// =========================================================================================
// ДИАЛОГ НАСТРОЙКИ «ИНДИВИДУАЛЬНОГО ЦВЕТА КЛИЕНТА» + ФИКС. СТОИМОСТИ ПОКРАСКИ
// =========================================================================================
@Composable
private fun CustomClientColorDialog(
    initialName: String,
    initialRal: String,
    initialHex: Long,
    initialPaintingCost: Double,
    initialUseRawForStock: Boolean,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (name: String, ral: String, hex: Long, paintingCost: Double, useRawForStock: Boolean) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val swatchPalette = remember {
        listOf(
            0xFF383E42L to "Антрацит",
            0xFFFFFFFFL to "Белый",
            0xFFA8A8A8L to "Серебро",
            0xFF1F2124L to "Чёрный",
            0xFF474A51L to "Графит",
            0xFF442F29L to "Шоколад",
            0xFF8B5A2BL to "Золотой дуб",
            0xFFD32F2FL to "Красный",
            0xFF154889L to "Синий",
            0xFF1E5945L to "Зелёный",
            0xFFE6D2B5L to "Слоновая кость",
            0xFF9E5B40L to "Терракот"
        )
    }

    var colorName by remember { mutableStateOf(initialName) }
    var ralCode by remember { mutableStateOf(initialRal) }
    var selectedHex by remember { mutableLongStateOf(if (initialHex != 0L) initialHex else 0xFF383E42L) }
    var paintingCostStr by remember {
        mutableStateOf(if (initialPaintingCost > 0.0) initialPaintingCost.toString().removeSuffix(".0") else "")
    }
    var useRawForStock by remember { mutableStateOf(initialUseRawForStock) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f),
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
                    text = strings.customClientColorItem,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Text(
                    text = "Оттенок для 2D-чертежа и миниатюр:",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    swatchPalette.forEach { (hex, label) ->
                        val picked = selectedHex == hex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedHex = hex
                                    if (colorName.isBlank()) colorName = label
                                }
                                .padding(6.dp)
                                .background(Color(hex), CircleShape)
                                .border(
                                    width = if (picked) 3.dp else 1.dp,
                                    color = if (picked) palette.accentBlue else palette.floatingBorder,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = colorName,
                        onValueChange = { colorName = it },
                        label = { Text("Название цвета") },
                        placeholder = { Text("Напр. Графит клиента") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("custom_color_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ralCode,
                        onValueChange = { ralCode = it },
                        label = { Text("Код RAL") },
                        placeholder = { Text("7024") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("custom_color_ral_input"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = paintingCostStr,
                    onValueChange = { paintingCostStr = it },
                    label = { Text("Стоимость покраски (малярный цех, $currencySymbol)") },
                    placeholder = { Text("Напр. 150") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_color_cost_input"),
                    singleLine = true
                )
                Text(
                    text = "Фиксированная сумма за покраску конструкции — добавляется в смету отдельной строкой «${strings.customPaintingLineLabel}».",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )

                Text(
                    text = "Цвет профиля в раскрое и остатках склада:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                val resolvedLabel = buildString {
                    append(colorName.trim().ifBlank { "Цвет клиента" })
                    if (ralCode.isNotBlank()) append(" (${ralCode.trim()})")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !useRawForStock,
                        onClick = { useRawForStock = false },
                        label = { Text(resolvedLabel, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_color_stock_client")
                    )
                    FilterChip(
                        selected = useRawForStock,
                        onClick = { useRawForStock = true },
                        label = { Text(RAW_UNPAINTED_COLOR_NAME, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_color_stock_raw")
                    )
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
                            .testTag("custom_color_cancel_btn")
                    ) {
                        Text(strings.cancelBtn, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val parsedCost = paintingCostStr.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            onSave(
                                colorName.trim().ifBlank { "Цвет клиента" },
                                ralCode.trim(),
                                selectedHex,
                                parsedCost,
                                useRawForStock
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("custom_color_save_btn")
                    ) {
                        Text(strings.saveBtn, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================================
// ДИАЛОГ ДОБАВЛЕНИЯ ЦВЕТА В КАТАЛОГ ПРОФИЛЬНОЙ СИСТЕМЫ
// =========================================================================================
@Composable
private fun AddProfileColorDialog(
    onDismiss: () -> Unit,
    onAddColor: (ProfileColorSpec) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val quickPresets = remember {
        listOf(
            Triple("Антрацит", "7016", 0xFF383E42L),
            Triple("Белый", "9016", 0xFFFFFFFFL),
            Triple("Анодированный алюминий", "E6/EV1", 0xFFA8A8A8L),
            Triple("Чёрный", "9005", 0xFF1F2124L)
        )
    }
    val extraSwatches = remember {
        listOf(
            0xFF383E42L, 0xFFFFFFFFL, 0xFFA8A8A8L, 0xFF1F2124L,
            0xFF474A51L, 0xFF442F29L, 0xFF8B5A2BL, 0xFFD32F2FL,
            0xFF154889L, 0xFF1E5945L, 0xFFE6D2B5L, 0xFFC0C0C0L
        )
    }

    var name by remember { mutableStateOf("") }
    var ralCode by remember { mutableStateOf("") }
    var selectedHex by remember { mutableLongStateOf(0xFF383E42L) }
    var markupStr by remember { mutableStateOf("0") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f),
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
                    text = "Добавить цвет профильной системы",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Text(
                    text = "Быстрый выбор популярных цветов:",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPresets.forEach { (presetName, presetRal, presetHex) ->
                        FilterChip(
                            selected = name == presetName && selectedHex == presetHex,
                            onClick = {
                                name = presetName
                                ralCode = presetRal
                                selectedHex = presetHex
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(presetHex), CircleShape)
                                        .border(0.8.dp, palette.floatingBorder, CircleShape)
                                )
                            },
                            label = {
                                Text("$presetName ($presetRal)", fontSize = 11.5.sp)
                            }
                        )
                    }
                }

                Text(
                    text = "Оттенок на чертеже:",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    extraSwatches.forEach { hex ->
                        val picked = selectedHex == hex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedHex = hex }
                                .padding(6.dp)
                                .background(Color(hex), CircleShape)
                                .border(
                                    width = if (picked) 3.dp else 1.dp,
                                    color = if (picked) palette.accentBlue else palette.floatingBorder,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название цвета") },
                    placeholder = { Text("Напр. Серый") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_profile_color_name"),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ralCode,
                        onValueChange = { ralCode = it },
                        label = { Text("Код RAL / E6") },
                        placeholder = { Text("7016") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_profile_color_ral"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = markupStr,
                        onValueChange = { markupStr = it },
                        label = { Text("Наценка (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_profile_color_markup"),
                        singleLine = true
                    )
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
                        onClick = {
                            if (name.isNotBlank()) {
                                val markup = markupStr.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                onAddColor(
                                    ProfileColorSpec(
                                        id = "col_${UUID.randomUUID().toString().take(6)}",
                                        name = name.trim(),
                                        ralCode = ralCode.trim(),
                                        hexColor = selectedHex,
                                        priceMarkupPercent = markup
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("add_profile_color_confirm")
                    ) {
                        Text(strings.saveBtn, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================================
// ПРЕДПРОСМОТР РЕАЛЬНОГО PDF-ДОКУМЕНТА ЧЕРЕЗ PdfRenderer
// =========================================================================================
@Composable
private fun InAppReportPreviewDialog(
    order: CustomerOrder,
    windowResults: List<WindowCalculationResult>,
    mode: ClientPdfReportMode,
    uiSettings: AppUiSettings,
    extraAccessoriesCatalog: List<ExtraAccessoryItem>,
    workServicesCatalog: List<WorkServiceItem>,
    hardwareBrandsCatalog: List<HardwareBrandItem>,
    onSwitchMode: (ClientPdfReportMode) -> Unit,
    onDismiss: () -> Unit,
    onOpenPdfFile: (File, String) -> Unit,
    onSharePdfFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current

    // Генерируем реальный PDF-файл выбранного режима и рендерим его страницы через Android PdfRenderer
    val generatedPdfFile = remember(order, windowResults, mode, uiSettings, extraAccessoriesCatalog, workServicesCatalog, hardwareBrandsCatalog) {
        PdfReportGenerator.generateClientOrderPdf(
            context = context,
            order = order,
            windowResults = windowResults,
            mode = mode,
            companyHeader = uiSettings.companyName,
            companyContacts = uiSettings.companyPhone,
            extraAccessoriesCatalog = extraAccessoriesCatalog,
            workServicesCatalog = workServicesCatalog,
            hardwareBrandsCatalog = hardwareBrandsCatalog
        )
    }

    val renderedPages = remember(generatedPdfFile) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(generatedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bmp)
            }
            renderer.close()
            pfd.close()
        } catch (_: Exception) {
        }
        bitmaps
    }

    var showSavedPdfActionModal by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Верхний переключатель 3 режимов PDF-отчётов в стиле iOS
                Surface(
                    color = palette.cardSurface,
                    shadowElevation = 2.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClientPdfReportMode.entries.forEach { m ->
                                val selected = m == mode
                                Button(
                                    onClick = { onSwitchMode(m) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) palette.accentBlue else palette.secondaryBackground
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Text(
                                        text = m.titleRu,
                                        color = if (selected) Color.White else palette.textPrimary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
                    }
                }

                // Область предпросмотра реальных страниц сгенерированного PDF
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(renderedPages) { bmp ->
                        Surface(
                            color = Color.White,
                            shadowElevation = 6.dp,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Page",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                // Нижние кнопки ЗАКРЫТЬ | СОХРАНИТЬ PDF
                Surface(color = palette.cardSurface) {
                    Column {
                        HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                Text(strings.closeDesc, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { showSavedPdfActionModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("PDF", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Модальное окно после нажатия "PDF"
            if (showSavedPdfActionModal) {
                Dialog(onDismissRequest = { showSavedPdfActionModal = false }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.96f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(0.8.dp, palette.floatingBorder),
                        color = palette.cardSurface,
                        shadowElevation = 16.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = generatedPdfFile.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        showSavedPdfActionModal = false
                                        onSharePdfFile(generatedPdfFile, mode.titleRu)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentRed),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Text(
                                        "Share",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Button(
                                    onClick = {
                                        showSavedPdfActionModal = false
                                        onOpenPdfFile(generatedPdfFile, mode.titleRu)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Text(
                                        "Open",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Button(
                                    onClick = { showSavedPdfActionModal = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryBackground),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(46.dp)
                                ) {
                                    Text(
                                        strings.closeDesc,
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
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

// =========================================================================================
// 3. ЭКРАН СПИСКА ЗАКАЗОВ (НОВЫЕ СВЕРХУ, АККУРАТНАЯ КНОПКА "+" И ПЕРЕКЛЮЧАТЕЛЬ ICONS)
// =========================================================================================
@Composable
private fun OrdersListScreen(
    orders: List<CustomerOrder>,
    onBack: () -> Unit,
    onNewOrder: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onUpdateOrders: (List<CustomerOrder>) -> Unit,
    onExportOrder: (CustomerOrder) -> Unit = {},
    onImportOrder: () -> Unit = {},
    onExportBackup: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatuses by remember {
        mutableStateOf(AppStateStorage.loadOrdersFilterStatuses(context))
    }
    var showFilterDialog by remember { mutableStateOf(false) }
    var contractDialogTargetOrder by remember { mutableStateOf<CustomerOrder?>(null) }

    // Заказы всегда отображаются в порядке от нового к старому (новый наверху)
    val filtered = remember(orders, searchQuery, selectedStatuses) {
        orders.filter { ord ->
            ord.status in selectedStatuses && (
                searchQuery.isBlank() ||
                    ord.orderNumber.contains(searchQuery, ignoreCase = true) ||
                    ord.customerName.contains(searchQuery, ignoreCase = true) ||
                    ord.customerPhone.contains(searchQuery, ignoreCase = true) ||
                    ord.address.contains(searchQuery, ignoreCase = true)
                )
        }
    }

    val isPartialFilter = selectedStatuses.size < OrderStatus.entries.size

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

        // Строка поиска + кнопка «Фильтр» справа
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        imageVector = Icons.Rounded.FilterAltOff,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = strings.emptyFilterTitle,
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
                                                            orders.map { if (it.id == ord.id) it.copy(status = st) else it }
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Строка 2: Клиент, телефон, адрес + иконки Экспорт / Удалить
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    if (orders.size > 1) {
                                        IconButton(
                                            onClick = {
                                                onUpdateOrders(orders.filterNot { it.id == ord.id })
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = strings.deleteWindowDesc,
                                                tint = palette.accentRed,
                                                modifier = Modifier.size(20.dp)
                                            )
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
                            it.copy(
                                status = nextStatus,
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
private fun ContractDeadlineDialog(
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

private fun fmtMm(v: Float): String {
    val rounded10 = (v * 10f).roundToInt()
    return if (rounded10 % 10 == 0) {
        (rounded10 / 10).toString()
    } else {
        String.format(Locale.US, "%.1f", rounded10 / 10.0)
    }
}

private fun parseMmOrNull(s: String): Float? = s.trim().replace(',', '.').toFloatOrNull()

// =========================================================================================
// 4. КОНСТРУКТОР ПРОФИЛЬНЫХ СИСТЕМ (С ЧЕРТЕЖАМИ СЕЧЕНИЙ A..H, РАЗМЕРАМИ, НАХЛЁСТАМИ, ЦЕНАМИ И СУХАРЯМИ)
// =========================================================================================
private enum class ProfileAccordionSection(val letterBadge: String, val titleRu: String, val subtitleRu: String) {
    RAMA_A("A", "Рама", "Размеры A1–A4 (с точностью до 0.1 мм) и стоимость за 1 пог. метр"),
    IMPOST_B("B", "Импост", "Размеры B1–B4 (с точностью до 0.1 мм) и стоимость за 1 пог. метр"),
    STVORKA_C("C", "Створка", "Размеры C1–C3, нахлёст на раму и цена за 1 пог. м"),
    DVER_D("D", "Дверь", "Размеры D1–D3, нахлёст на раму и цена за 1 пог. м"),
    REV_DVER_E("E", "Обратная дверь", "Размеры E1–E3, нахлёст и цена за 1 пог. м"),
    UGLOVIK_F("F", "Угловик", "Эскиз углового профиля и цена за 1 пог. м для сметы"),
    SHTULP_G("G", "Штульп", "Размеры G1–G2 и стоимость за 1 пог. метр"),
    SHTAPIK_H("H", "Штапик", "Размеры H1, H2, правило раскроя 90°/45° и цена за метр"),
    CLEATS_SETS("∟", "Угловые сухари (комплекты)", "Цены комплектов на 4 угла (рама, створка, дверь) и на 1 импост")
}

@Composable
private fun ProfileConstructorScreen(
    profileSystems: List<ProfileSystemSpec>,
    currencySymbol: String,
    onBack: () -> Unit,
    onSaveProfiles: (List<ProfileSystemSpec>) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var selectedId by remember { mutableStateOf(profileSystems.firstOrNull()?.id ?: "") }
    val activeSys = profileSystems.find { it.id == selectedId } ?: profileSystems.firstOrNull()
    var expandedSection by remember { mutableStateOf<ProfileAccordionSection?>(ProfileAccordionSection.RAMA_A) }
    var savedBannerText by remember { mutableStateOf<String?>(null) }

    fun createNewProfileSystem() {
        val template = DefaultCatalogs.defaultProfileSystems().first()
        val newSys = template.copy(
            id = "sys_${UUID.randomUUID().toString().take(6)}",
            name = "Новая система ${profileSystems.size + 1}"
        )
        val updated = profileSystems + newSys
        onSaveProfiles(updated)
        selectedId = newSys.id
        expandedSection = ProfileAccordionSection.RAMA_A
        savedBannerText = "Создана профильная система «${newSys.name}»"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = strings.profilesScreenTitle,
            onBack = onBack
        ) {
            Surface(
                color = palette.accentBlue,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { createNewProfileSystem() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(strings.addSystemBtn, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (savedBannerText != null) {
            Surface(
                color = palette.accentGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { savedBannerText = null }
            ) {
                Text(
                    text = savedBannerText!!,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        if (activeSys != null) {
            // Состояния общих параметров системы (с поддержкой десятых долей миллиметра, например 39.2, 59.6, 92.4)
            var name by remember(activeSys.id) { mutableStateOf(activeSys.name) }
            var matType by remember(activeSys.id) { mutableStateOf(activeSys.materialType) }
            var stockBarLen by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.stockBarLengthMm)) }
            var weldAllow by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.weldAllowancePerCornerMm)) }
            var glzClearanceTotal by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.glazingClearanceTotalMm)) }

            // A. Рама (A1, A2, A3, A4 + цена за м)
            var a1InnerW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.frameWidthMm)) }
            var a2RebateH by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.frameRebateHeightA2Mm)) }
            var a3FullW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.frameFullWidthA3Mm)) }
            var a4SeatW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.frameGlazingSeatA4Mm)) }
            var priceFrameM by remember(activeSys.id) { mutableStateOf(activeSys.priceFramePerM.toString()) }

            // B. Импост (B1, B2, B3, B4 + цена за м)
            var b1InnerW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.impostWidthMm)) }
            var b2RebateH by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.impostRebateHeightB2Mm)) }
            var b3FullW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.impostFullWidthB3Mm)) }
            var b4SeatW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.impostGlazingSeatB4Mm)) }
            var priceImpostM by remember(activeSys.id) { mutableStateOf(activeSys.priceImpostPerM.toString()) }

            // C. Створка (C1, C2, C3 + нахлёст + цена за м)
            var c1InnerW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.sashWidthMm)) }
            var c2FullW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.sashFullWidthC2Mm)) }
            var c3SeatW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.sashGlazingSeatC3Mm)) }
            var cOverlap by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.sashOverlapMm)) }
            var priceSashM by remember(activeSys.id) { mutableStateOf(activeSys.priceSashPerM.toString()) }

            // D. Дверь (D1, D2, D3 + нахлёст + цена за м)
            var d1InnerW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.doorSashWidthMm)) }
            var d2FullW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.doorFullWidthD2Mm)) }
            var d3SeatW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.doorGlazingSeatD3Mm)) }
            var dOverlap by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.doorOverlapMm)) }
            var priceDoorM by remember(activeSys.id) { mutableStateOf(activeSys.priceDoorSashPerM.toString()) }

            // E. Обратная дверь (E1, E2, E3 + нахлёст + цена за м)
            var e1FullW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.revDoorFullWidthE1Mm)) }
            var e2InnerW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.revDoorInnerWidthE2Mm)) }
            var e3SeatW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.revDoorGlazingSeatE3Mm)) }
            var eOverlap by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.revDoorOverlapMm)) }
            var priceRevDoorM by remember(activeSys.id) { mutableStateOf(activeSys.priceRevDoorSashPerM.toString()) }

            // F. Угловик (только цена за м)
            var priceCornerProfileM by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerProfilePerM.toString()) }

            // G. Штульп (G1, G2 + цена за м)
            var g1RebateW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.shtulpRebateG1Mm)) }
            var g2FullW by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.shtulpWidthMm)) }
            var priceShtulpM by remember(activeSys.id) { mutableStateOf(activeSys.priceShtulpPerM.toString()) }

            // H. Штапики и резина
            var rubberAllowMm by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.rubberAllowanceTotalMm)) }
            var shtapikList by remember(activeSys.id) { mutableStateOf(activeSys.shtapikVariants) }

            // Цвета данной профильной системы
            var colorsList by remember(activeSys.id) { mutableStateOf(activeSys.colors) }
            var showAddColorDialog by remember { mutableStateOf(false) }

            // Угловые сухари (комплекты на 4 угла рамы, створки, двери и на 1 импост)
            var cleatFrameSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorFrame.toString()) }
            var cleatImpostSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceImpostConnector.toString()) }
            var cleatSashSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorSash.toString()) }
            var cleatDoorSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorDoorSash.toString()) }

            fun buildDraftSystem(
                overrideId: String = activeSys.id,
                overrideName: String = name.ifBlank { activeSys.name },
                overrideColors: List<ProfileColorSpec> = colorsList
            ): ProfileSystemSpec {
                val primaryShtapik = shtapikList.firstOrNull()
                return activeSys.copy(
                    id = overrideId,
                    name = overrideName,
                    materialType = matType,
                    stockBarLengthMm = parseMmOrNull(stockBarLen) ?: activeSys.stockBarLengthMm,
                    weldAllowancePerCornerMm = parseMmOrNull(weldAllow) ?: activeSys.weldAllowancePerCornerMm,
                    glazingClearanceTotalMm = parseMmOrNull(glzClearanceTotal) ?: activeSys.glazingClearanceTotalMm,
                    // A. Рама
                    frameWidthMm = parseMmOrNull(a1InnerW) ?: activeSys.frameWidthMm,
                    frameRebateHeightA2Mm = parseMmOrNull(a2RebateH) ?: activeSys.frameRebateHeightA2Mm,
                    frameFullWidthA3Mm = parseMmOrNull(a3FullW) ?: activeSys.frameFullWidthA3Mm,
                    frameGlazingSeatA4Mm = parseMmOrNull(a4SeatW) ?: activeSys.frameGlazingSeatA4Mm,
                    priceFramePerM = priceFrameM.replace(',', '.').toDoubleOrNull() ?: activeSys.priceFramePerM,
                    // B. Импост
                    impostWidthMm = parseMmOrNull(b1InnerW) ?: activeSys.impostWidthMm,
                    impostRebateHeightB2Mm = parseMmOrNull(b2RebateH) ?: activeSys.impostRebateHeightB2Mm,
                    impostFullWidthB3Mm = parseMmOrNull(b3FullW) ?: activeSys.impostFullWidthB3Mm,
                    impostGlazingSeatB4Mm = parseMmOrNull(b4SeatW) ?: activeSys.impostGlazingSeatB4Mm,
                    priceImpostPerM = priceImpostM.replace(',', '.').toDoubleOrNull() ?: activeSys.priceImpostPerM,
                    // C. Створка
                    sashWidthMm = parseMmOrNull(c1InnerW) ?: activeSys.sashWidthMm,
                    sashFullWidthC2Mm = parseMmOrNull(c2FullW) ?: activeSys.sashFullWidthC2Mm,
                    sashGlazingSeatC3Mm = parseMmOrNull(c3SeatW) ?: activeSys.sashGlazingSeatC3Mm,
                    sashOverlapMm = parseMmOrNull(cOverlap) ?: activeSys.sashOverlapMm,
                    priceSashPerM = priceSashM.replace(',', '.').toDoubleOrNull() ?: activeSys.priceSashPerM,
                    // D. Дверь
                    doorSashWidthMm = parseMmOrNull(d1InnerW) ?: activeSys.doorSashWidthMm,
                    doorFullWidthD2Mm = parseMmOrNull(d2FullW) ?: activeSys.doorFullWidthD2Mm,
                    doorGlazingSeatD3Mm = parseMmOrNull(d3SeatW) ?: activeSys.doorGlazingSeatD3Mm,
                    doorOverlapMm = parseMmOrNull(dOverlap) ?: activeSys.doorOverlapMm,
                    priceDoorSashPerM = priceDoorM.replace(',', '.').toDoubleOrNull() ?: activeSys.priceDoorSashPerM,
                    // E. Обратная дверь
                    revDoorFullWidthE1Mm = parseMmOrNull(e1FullW) ?: activeSys.revDoorFullWidthE1Mm,
                    revDoorInnerWidthE2Mm = parseMmOrNull(e2InnerW) ?: activeSys.revDoorInnerWidthE2Mm,
                    revDoorGlazingSeatE3Mm = parseMmOrNull(e3SeatW) ?: activeSys.revDoorGlazingSeatE3Mm,
                    revDoorOverlapMm = parseMmOrNull(eOverlap) ?: activeSys.revDoorOverlapMm,
                    priceRevDoorSashPerM = priceRevDoorM.replace(',', '.').toDoubleOrNull() ?: activeSys.priceRevDoorSashPerM,
                    // F. Угловик
                    priceCornerProfilePerM = priceCornerProfileM.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerProfilePerM,
                    // G. Штульп
                    shtulpRebateG1Mm = parseMmOrNull(g1RebateW) ?: activeSys.shtulpRebateG1Mm,
                    shtulpWidthMm = parseMmOrNull(g2FullW) ?: activeSys.shtulpWidthMm,
                    priceShtulpPerM = priceShtulpM.replace(',', '.').toDoubleOrNull() ?: activeSys.priceShtulpPerM,
                    // H. Штапики
                    rubberAllowanceTotalMm = parseMmOrNull(rubberAllowMm) ?: activeSys.rubberAllowanceTotalMm,
                    shtapikVariants = shtapikList.map { it.copy() },
                    shtapikWidthMm = primaryShtapik?.widthH2Mm ?: activeSys.shtapikWidthMm,
                    priceShtapikPerM = primaryShtapik?.pricePerM ?: activeSys.priceShtapikPerM,
                    // Цвета профильной системы
                    colors = overrideColors.map { it.copy() },
                    // Угловые сухари (комплекты)
                    priceCornerConnectorFrame = cleatFrameSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorFrame,
                    priceImpostConnector = cleatImpostSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceImpostConnector,
                    priceCornerConnectorSash = cleatSashSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorSash,
                    priceCornerConnectorDoorSash = cleatDoorSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorDoorSash
                )
            }

            fun saveCurrentSystem() {
                val updatedSys = buildDraftSystem()
                onSaveProfiles(profileSystems.map { if (it.id == updatedSys.id) updatedSys else it })
                savedBannerText = "Профильная система «${updatedSys.name}» сохранена!"
            }

            fun copyCurrentProfileSystem() {
                val baseName = name.trim().ifBlank { activeSys.name }
                val copied = buildDraftSystem(
                    overrideId = "sys_${UUID.randomUUID().toString().take(6)}",
                    overrideName = "$baseName (Копия)"
                )
                onSaveProfiles(profileSystems + copied)
                selectedId = copied.id
                expandedSection = ProfileAccordionSection.RAMA_A
                savedBannerText = "Создана и открыта копия системы «${copied.name}»"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // =========================================================================
                // БЛОК 1: КНОПКА "+" И СПИСОК СОЗДАННЫХ ПРОФИЛЬНЫХ СИСТЕМ
                // =========================================================================
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Список профильных систем (${profileSystems.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.textPrimary
                            )
                            Button(
                                onClick = { createNewProfileSystem() },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(strings.addSystemBtn, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        profileSystems.forEach { sys ->
                            val isSelected = sys.id == activeSys.id
                            Surface(
                                color = if (isSelected) palette.accentBlue.copy(alpha = 0.12f) else palette.secondaryBackground.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.8.dp,
                                        color = if (isSelected) palette.accentBlue else palette.floatingBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedId = sys.id }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                color = if (sys.materialType == ProfileMaterialType.ALUMINUM) Color(0xFF455A64) else palette.accentTeal,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = sys.materialType.shortBadge,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = sys.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = palette.textPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Рама A1=${fmtMm(sys.frameWidthMm)} мм (${sys.priceFramePerM} $currencySymbol/м) • Импост B1=${fmtMm(sys.impostWidthMm)} мм • Створка C1=${fmtMm(sys.sashWidthMm)} мм • Зазор СП=${fmtMm(sys.glazingClearanceTotalMm)} мм",
                                            fontSize = 11.sp,
                                            color = palette.textSecondary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = { selectedId = sys.id },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                if (isSelected) "✓ Выбрана" else "Редакт.",
                                                color = palette.accentBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if (profileSystems.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val rest = profileSystems.filterNot { it.id == sys.id }
                                                    onSaveProfiles(rest)
                                                    if (selectedId == sys.id) {
                                                        selectedId = rest.first().id
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Удалить систему",
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

                // =========================================================================
                // БЛОК 2: ОБЩИЕ ДАННЫЕ ВЫБРАННОЙ ПРОФИЛЬНОЙ СИСТЕМЫ + ЗАЗОР НА СТЕКЛОПАКЕТ
                // =========================================================================
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Редактирование системы: «$name»",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = palette.accentBlue,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { copyCurrentProfileSystem() },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_copy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = strings.copySystemBtn,
                                    tint = palette.accentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = strings.copySystemBtn,
                                    color = palette.accentBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название профильной системы") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileMaterialType.entries.forEach { mt ->
                                FilterChip(
                                    selected = matType == mt,
                                    onClick = {
                                        matType = mt
                                        weldAllow = if (mt == ProfileMaterialType.ALUMINUM) "0" else "3"
                                    },
                                    label = { Text(mt.titleRu, fontSize = 11.5.sp) }
                                )
                            }
                        }

                        // Блок «Каталог цветов системы»
                        Surface(
                            color = palette.secondaryBackground.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${strings.profileColorsTitle} (${colorsList.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = palette.textPrimary
                                    )
                                    Button(
                                        onClick = { showAddColorDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("profile_add_color_button")
                                    ) {
                                        Text(strings.addColorBtn, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (colorsList.isEmpty()) {
                                    Text(
                                        text = "Цвета не заданы — нажмите «${strings.addColorBtn}».",
                                        fontSize = 12.sp,
                                        color = palette.textSecondary
                                    )
                                } else {
                                    colorsList.forEach { clr ->
                                        Surface(
                                            color = palette.cardSurface,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(0.6.dp, palette.floatingBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .background(Color(clr.hexColor), CircleShape)
                                                        .border(1.dp, palette.floatingBorder, CircleShape)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val ralTxt = if (clr.ralCode.isNotBlank()) " • RAL ${clr.ralCode}" else ""
                                                    Text(
                                                        text = "${clr.name}$ralTxt",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = palette.textPrimary
                                                    )
                                                    Text(
                                                        text = if (clr.priceMarkupPercent > 0.0) {
                                                            "Наценка на профиль: +${clr.priceMarkupPercent.roundToInt()}%"
                                                        } else {
                                                            "Базовый цвет (наценка 0%)"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = if (clr.priceMarkupPercent > 0.0) palette.accentOrange else palette.textSecondary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val nextColors = colorsList.filterNot { it.id == clr.id }
                                                        colorsList = nextColors
                                                        val updatedSys = buildDraftSystem(overrideColors = nextColors)
                                                        onSaveProfiles(profileSystems.map { if (it.id == updatedSys.id) updatedSys else it })
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("profile_delete_color_${clr.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Удалить цвет ${clr.name}",
                                                        tint = palette.accentRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showAddColorDialog) {
                            AddProfileColorDialog(
                                onDismiss = { showAddColorDialog = false },
                                onAddColor = { newColor ->
                                    val nextColors = colorsList + newColor
                                    colorsList = nextColors
                                    val updatedSys = buildDraftSystem(overrideColors = nextColors)
                                    onSaveProfiles(profileSystems.map { if (it.id == updatedSys.id) updatedSys else it })
                                    showAddColorDialog = false
                                    savedBannerText = "Цвет «${newColor.name}» добавлен в систему"
                                }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = stockBarLen,
                                onValueChange = { stockBarLen = it },
                                label = { Text("Длина хлыста профиля (мм)") },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = weldAllow,
                                onValueChange = { weldAllow = it },
                                label = { Text("Припуск на сварку угла (мм)") },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        // Поле "Зазор на стеклопакет (мм)" + живой расчёт вычетов по проёмам
                        val clrMm = parseMmOrNull(glzClearanceTotal) ?: 10f
                        val c1Val = parseMmOrNull(c1InnerW) ?: 59.6f
                        val cOvVal = parseMmOrNull(cOverlap) ?: 6f
                        val d1Val = parseMmOrNull(d1InnerW) ?: 90f
                        val dOvVal = parseMmOrNull(dOverlap) ?: 6f
                        val e2Val = parseMmOrNull(e2InnerW) ?: 92.4f
                        val eOvVal = parseMmOrNull(eOverlap) ?: 6f

                        val sashDeduct = 2f * c1Val - 2f * cOvVal + clrMm
                        val doorDeduct = 2f * d1Val - 2f * dOvVal + clrMm
                        val revDoorDeduct = 2f * e2Val - 2f * eOvVal + clrMm

                        Surface(
                            color = palette.accentTeal.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, palette.accentTeal.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = glzClearanceTotal,
                                    onValueChange = { glzClearanceTotal = it },
                                    label = { Text("Зазор на стеклопакет (отнимается от проёма, напр. 10 мм или 14 мм)") },
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Text(
                                    text = "Итоговые формулы стеклопакета от чистого проёма рамы/импоста:\n" +
                                        "• В глухаре: Проём − ${fmtMm(clrMm)} мм\n" +
                                        "• В створке (C): Проём + 2×${fmtMm(cOvVal)} − 2×${fmtMm(c1Val)} − ${fmtMm(clrMm)} = Проём − ${fmtMm(sashDeduct)} мм\n" +
                                        "• В двери (D): Проём + 2×${fmtMm(dOvVal)} − 2×${fmtMm(d1Val)} − ${fmtMm(clrMm)} = Проём − ${fmtMm(doorDeduct)} мм\n" +
                                        "• В обратной двери (E): Проём + 2×${fmtMm(eOvVal)} − 2×${fmtMm(e2Val)} − ${fmtMm(clrMm)} = Проём − ${fmtMm(revDoorDeduct)} мм",
                                    fontSize = 11.sp,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // БЛОК 3: СПИСОК ПРОФИЛЕЙ (РАМА A .. ШТАПИК H) + УГЛОВЫЕ СУХАРИ В КОНЦЕ СПИСКА
                // =========================================================================
                Text(
                    "Нажмите на элемент профиля, чтобы раскрыть эскиз сечения, таблицу размеров и цену:",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                ProfileAccordionSection.entries.forEach { section ->
                    val isExp = expandedSection == section
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(0.8.dp, if (isExp) palette.accentBlue.copy(alpha = 0.5f) else palette.floatingBorder),
                        shadowElevation = if (palette.isDark) 0.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Заголовок элемента аккордеона
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isExp) palette.accentBlue.copy(alpha = 0.1f) else palette.cardSurface)
                                    .clickable {
                                        expandedSection = if (isExp) null else section
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = if (section == ProfileAccordionSection.CLEATS_SETS) palette.accentGreen else palette.accentBlue,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = section.letterBadge,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = section.titleRu,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = section.subtitleRu,
                                            fontSize = 11.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = if (isExp) "▲" else "▼",
                                    color = palette.accentBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isExp) {
                                HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    when (section) {
                                        ProfileAccordionSection.RAMA_A -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_rama_a,
                                                caption = "Эскиз сечения рамы (A1–A4)"
                                            )
                                            LetteredDimensionRow(
                                                badge = "A1",
                                                description = "Внутренняя ширина рамы до проёма (используется для расчёта проёмов, мм)",
                                                value = a1InnerW,
                                                onValueChange = {
                                                    a1InnerW = it
                                                    val v1 = parseMmOrNull(it)
                                                    val v2 = parseMmOrNull(a2RebateH)
                                                    if (v1 != null && v2 != null) {
                                                        a3FullW = fmtMm(v1 + v2)
                                                    }
                                                }
                                            )
                                            LetteredDimensionRow(
                                                badge = "A2",
                                                description = "Высота четверти / притвора рамы (мм)",
                                                value = a2RebateH,
                                                onValueChange = {
                                                    a2RebateH = it
                                                    val v1 = parseMmOrNull(a1InnerW)
                                                    val v2 = parseMmOrNull(it)
                                                    if (v1 != null && v2 != null) {
                                                        a3FullW = fmtMm(v1 + v2)
                                                    }
                                                }
                                            )
                                            LetteredDimensionRow(
                                                badge = "A3",
                                                description = "Полная (общая) ширина профиля рамы (для расчёта светового проёма, мм)",
                                                value = a3FullW,
                                                onValueChange = { a3FullW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "A4",
                                                description = "Посадочное место под стеклопакет + резину + штапик, до усика (мм)",
                                                value = a4SeatW,
                                                onValueChange = { a4SeatW = it }
                                            )
                                            ProfilePricePerMeterField(
                                                label = "Стоимость профиля Рамы за 1 пог. метр ($currencySymbol/м)",
                                                value = priceFrameM,
                                                onValueChange = { priceFrameM = it }
                                            )
                                        }

                                        ProfileAccordionSection.IMPOST_B -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_impost_b,
                                                caption = "Эскиз сечения импоста (B1–B4)"
                                            )
                                            LetteredDimensionRow(
                                                badge = "B1",
                                                description = "Внутренняя ширина коробки импоста (для расчёта проёмов и раскроя, мм)",
                                                value = b1InnerW,
                                                onValueChange = {
                                                    b1InnerW = it
                                                    val v1 = parseMmOrNull(it)
                                                    val v2 = parseMmOrNull(b2RebateH)
                                                    if (v1 != null && v2 != null) {
                                                        b3FullW = fmtMm(v1 + 2f * v2)
                                                    }
                                                }
                                            )
                                            LetteredDimensionRow(
                                                badge = "B2",
                                                description = "Высота четверти / притвора импоста с одной стороны (мм)",
                                                value = b2RebateH,
                                                onValueChange = {
                                                    b2RebateH = it
                                                    val v1 = parseMmOrNull(b1InnerW)
                                                    val v2 = parseMmOrNull(it)
                                                    if (v1 != null && v2 != null) {
                                                        b3FullW = fmtMm(v1 + 2f * v2)
                                                    }
                                                }
                                            )
                                            LetteredDimensionRow(
                                                badge = "B3",
                                                description = "Полная ширина профиля импоста (для расчёта светового проёма, мм)",
                                                value = b3FullW,
                                                onValueChange = { b3FullW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "B4",
                                                description = "Посадочное место под стеклопакет + резину + штапик (мм)",
                                                value = b4SeatW,
                                                onValueChange = { b4SeatW = it }
                                            )
                                            ProfilePricePerMeterField(
                                                label = "Стоимость профиля Импоста за 1 пог. метр ($currencySymbol/м)",
                                                value = priceImpostM,
                                                onValueChange = { priceImpostM = it }
                                            )
                                        }

                                        ProfileAccordionSection.STVORKA_C -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_stvorka_c,
                                                caption = "Эскиз сечения оконной створки (C1–C3)"
                                            )
                                            LetteredDimensionRow(
                                                badge = "C1",
                                                description = "Ширина от начала притвора до проёма под штапик (мм)",
                                                value = c1InnerW,
                                                onValueChange = { c1InnerW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "C2",
                                                description = "Общая ширина профиля створки (мм)",
                                                value = c2FullW,
                                                onValueChange = { c2FullW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "C3",
                                                description = "Посадочное место под стеклопакет + резину + штапик (мм)",
                                                value = c3SeatW,
                                                onValueChange = { c3SeatW = it }
                                            )
                                            OverlapInputCard(
                                                title = "Нахлёст створки на раму с одной стороны (мм)",
                                                value = cOverlap,
                                                onValueChange = { cOverlap = it }
                                            )
                                            ProfilePricePerMeterField(
                                                label = "Стоимость профиля Створки за 1 пог. метр ($currencySymbol/м)",
                                                value = priceSashM,
                                                onValueChange = { priceSashM = it }
                                            )
                                        }

                                        ProfileAccordionSection.DVER_D -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_dver_d,
                                                caption = "Эскиз сечения дверного профиля (D1–D3)"
                                            )
                                            LetteredDimensionRow(
                                                badge = "D1",
                                                description = "Ширина от начала притвора до проёма под штапик (мм)",
                                                value = d1InnerW,
                                                onValueChange = { d1InnerW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "D2",
                                                description = "Общая ширина профиля двери (мм)",
                                                value = d2FullW,
                                                onValueChange = { d2FullW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "D3",
                                                description = "Посадочное место под стеклопакет + резину + штапик (мм)",
                                                value = d3SeatW,
                                                onValueChange = { d3SeatW = it }
                                            )
                                            OverlapInputCard(
                                                title = "Нахлёст двери на раму с одной стороны (мм)",
                                                value = dOverlap,
                                                onValueChange = { dOverlap = it }
                                            )
                                            ProfilePricePerMeterField(
                                                label = "Стоимость профиля Двери за 1 пог. метр ($currencySymbol/м)",
                                                value = priceDoorM,
                                                onValueChange = { priceDoorM = it }
                                            )
                                        }

                                        ProfileAccordionSection.REV_DVER_E -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_rev_dver_e,
                                                caption = "Эскиз сечения обратной двери (E1–E3)"
                                            )
                                            LetteredDimensionRow(
                                                badge = "E1",
                                                description = "Общая ширина профиля обратной двери (мм)",
                                                value = e1FullW,
                                                onValueChange = { e1FullW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "E2",
                                                description = "Ширина от начала притвора до начала проёма двери под штапик (мм)",
                                                value = e2InnerW,
                                                onValueChange = { e2InnerW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "E3",
                                                description = "Посадочное место под стеклопакет + резину + штапик (мм)",
                                                value = e3SeatW,
                                                onValueChange = { e3SeatW = it }
                                            )
                                            OverlapInputCard(
                                                title = "Нахлёст обратной двери на раму с одной стороны (мм)",
                                                value = eOverlap,
                                                onValueChange = { eOverlap = it }
                                            )
                                            ProfilePricePerMeterField(
                                                label = "Стоимость профиля Обратной двери за 1 пог. метр ($currencySymbol/м)",
                                                value = priceRevDoorM,
                                                onValueChange = { priceRevDoorM = it }
                                            )
                                        }

                                        ProfileAccordionSection.UGLOVIK_F -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_uglovik_f,
                                                caption = "Эскиз углового соединителя (Угловик)"
                                            )
                                            Surface(
                                                color = palette.secondaryBackground,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Габариты двух конструкций при угловом соединении задаются по готовым размерам замерного листа. Для угловика в расчётах сметы используется стоимость за 1 погонный метр:",
                                                    fontSize = 11.5.sp,
                                                    color = palette.textSecondary,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                            ProfilePricePerMeterField(
                                                label = "Стоимость Угловика за 1 пог. метр ($currencySymbol/м)",
                                                value = priceCornerProfileM,
                                                onValueChange = { priceCornerProfileM = it }
                                            )
                                        }

                                        ProfileAccordionSection.SHTULP_G -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_shtulp_g,
                                                caption = "Эскиз сечения штульпового профиля (G1–G2)"
                                            )
                                            LetteredDimensionRow(
                                                badge = "G1",
                                                description = "Выступ притвора штульпа (мм)",
                                                value = g1RebateW,
                                                onValueChange = { g1RebateW = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "G2",
                                                description = "Общая ширина штульпового профиля (мм)",
                                                value = g2FullW,
                                                onValueChange = { g2FullW = it }
                                            )
                                            ProfilePricePerMeterField(
                                                label = "Стоимость профиля Штульпа за 1 пог. метр ($currencySymbol/м)",
                                                value = priceShtulpM,
                                                onValueChange = { priceShtulpM = it }
                                            )
                                        }

                                        ProfileAccordionSection.SHTAPIK_H -> {
                                            ProfileSchematicImageCard(
                                                drawableResId = R.drawable.profile_shtapik_h,
                                                caption = "Эскиз штапика (H1, H2) и схема посадки со стеклопакетом (СП)"
                                            )
                                            val seatMm = parseMmOrNull(a4SeatW) ?: 50f
                                            val rubMm = parseMmOrNull(rubberAllowMm) ?: 8f
                                            Surface(
                                                color = palette.accentBlue.copy(alpha = 0.09f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(0.8.dp, palette.accentBlue.copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = "Формула подбора штапика: СП = Посадочное место (A4=${fmtMm(seatMm)} мм) − Ширина штапика (H2) − Резина (${fmtMm(rubMm)} мм)",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = palette.accentBlue
                                                    )
                                                    Text(
                                                        text = if (matType == ProfileMaterialType.ALUMINUM) {
                                                            "Правило распила штапиков для АЛЮМИНИЯ (90°/90°):\n• Горизонтальные штапики = Ширина проёма\n• Вертикальные штапики = Высота проёма − 2 × H1 (две ширины горизонтального штапика)"
                                                        } else {
                                                            "Правило распила штапиков для ПВХ (45°/45°):\n• Горизонтальные = Ширина проёма | Вертикальные = Высота проёма"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = palette.textPrimary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    OutlinedTextField(
                                                        value = rubberAllowMm,
                                                        onValueChange = { rubberAllowMm = it },
                                                        label = { Text("Суммарная толщина резины (упорная + клиновая, обычно 4+4 = 8 мм)") },
                                                        shape = RoundedCornerShape(12.dp),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Варианты штапиков в этой системе (${shtapikList.size}):",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = palette.textPrimary
                                                )
                                                Button(
                                                    onClick = {
                                                        val newH2 = 18f
                                                        val calcSp = (seatMm - newH2 - rubMm).roundToInt().coerceAtLeast(4)
                                                        val newVar = ShtapikVariantSpec(
                                                            id = "sht_${UUID.randomUUID().toString().take(5)}",
                                                            name = "Штапик ${fmtMm(newH2)} мм",
                                                            heightH1Mm = 20f,
                                                            widthH2Mm = newH2,
                                                            glazingThicknessSpMm = calcSp,
                                                            pricePerM = 2.2
                                                        )
                                                        shtapikList = shtapikList + newVar
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text("+ Штапик", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }

                                            shtapikList.forEachIndexed { idx, item ->
                                                Surface(
                                                    color = palette.secondaryBackground.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Штапик #${idx + 1}: ${item.name} → под заполнение СП ${item.glazingThicknessSpMm} мм",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.5.sp,
                                                                color = palette.textPrimary
                                                            )
                                                            if (shtapikList.size > 1) {
                                                                IconButton(
                                                                    onClick = {
                                                                        shtapikList = shtapikList.filterIndexed { i, _ -> i != idx }
                                                                    },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                                        contentDescription = "Удалить штапик",
                                                                        tint = palette.accentRed,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            OutlinedTextField(
                                                                value = fmtMm(item.heightH1Mm),
                                                                onValueChange = { v ->
                                                                    val h1 = parseMmOrNull(v) ?: item.heightH1Mm
                                                                    shtapikList = shtapikList.toMutableList().also {
                                                                        it[idx] = item.copy(heightH1Mm = h1)
                                                                    }
                                                                },
                                                                label = { Text("H1 В проёме (мм)") },
                                                                shape = RoundedCornerShape(10.dp),
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                                modifier = Modifier.weight(1f),
                                                                singleLine = true
                                                            )
                                                            OutlinedTextField(
                                                                value = fmtMm(item.widthH2Mm),
                                                                onValueChange = { v ->
                                                                    val h2 = parseMmOrNull(v) ?: item.widthH2Mm
                                                                    val autoSp = (seatMm - h2 - rubMm).roundToInt().coerceAtLeast(4)
                                                                    shtapikList = shtapikList.toMutableList().also {
                                                                        it[idx] = item.copy(
                                                                            name = "Штапик ${fmtMm(h2)} мм",
                                                                            widthH2Mm = h2,
                                                                            glazingThicknessSpMm = autoSp
                                                                        )
                                                                    }
                                                                },
                                                                label = { Text("H2 Толщина (мм)") },
                                                                shape = RoundedCornerShape(10.dp),
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                                modifier = Modifier.weight(1f),
                                                                singleLine = true
                                                            )
                                                            OutlinedTextField(
                                                                value = item.glazingThicknessSpMm.toString(),
                                                                onValueChange = { v ->
                                                                    val sp = v.toIntOrNull() ?: item.glazingThicknessSpMm
                                                                    shtapikList = shtapikList.toMutableList().also {
                                                                        it[idx] = item.copy(glazingThicknessSpMm = sp)
                                                                    }
                                                                },
                                                                label = { Text("СП Пакет (мм)") },
                                                                shape = RoundedCornerShape(10.dp),
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                                modifier = Modifier.weight(1f),
                                                                singleLine = true
                                                            )
                                                        }

                                                        OutlinedTextField(
                                                            value = item.pricePerM.toString(),
                                                            onValueChange = { v ->
                                                                val pr = v.replace(',', '.').toDoubleOrNull() ?: item.pricePerM
                                                                shtapikList = shtapikList.toMutableList().also {
                                                                    it[idx] = item.copy(pricePerM = pr)
                                                                }
                                                            },
                                                            label = { Text("Цена этого штапика за 1 пог. метр ($currencySymbol/м)") },
                                                            shape = RoundedCornerShape(10.dp),
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                            modifier = Modifier.fillMaxWidth(),
                                                            singleLine = true
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        ProfileAccordionSection.CLEATS_SETS -> {
                                            Surface(
                                                color = palette.accentGreen.copy(alpha = 0.11f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(0.8.dp, palette.accentGreen.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "Автоматический расчёт комплектов сухарей в конструкции:",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp,
                                                        color = palette.accentGreen
                                                    )
                                                    Text(
                                                        text = "• 1 рама = 1 комплект сухарей рамы (на 4 угла)\n" +
                                                            "• 1 импост = 1 комплект сухарей импоста (на 2 торца)\n" +
                                                            "• 1 створка = 1 комплект створочных сухарей (на 4 угла)\n" +
                                                            "• 1 дверь = 1 комплект дверных сухарей (на 4 угла)",
                                                        fontSize = 11.5.sp,
                                                        color = palette.textPrimary
                                                    )
                                                }
                                            }

                                            LetteredDimensionRow(
                                                badge = "Рама",
                                                description = "Комплект угловых сухарей для 1 рамы (цена за 4 угла, $currencySymbol)",
                                                value = cleatFrameSetPrice,
                                                unitText = currencySymbol,
                                                onValueChange = { cleatFrameSetPrice = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "Имп.",
                                                description = "Комплект сухарей (соединителей) для 1 импоста ($currencySymbol)",
                                                value = cleatImpostSetPrice,
                                                unitText = currencySymbol,
                                                onValueChange = { cleatImpostSetPrice = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "Ств.",
                                                description = "Комплект угловых сухарей для 1 оконной створки (цена за 4 угла, $currencySymbol)",
                                                value = cleatSashSetPrice,
                                                unitText = currencySymbol,
                                                onValueChange = { cleatSashSetPrice = it }
                                            )
                                            LetteredDimensionRow(
                                                badge = "Дверь",
                                                description = "Комплект угловых сухарей для 1 двери (цена за 4 угла, $currencySymbol)",
                                                value = cleatDoorSetPrice,
                                                unitText = currencySymbol,
                                                onValueChange = { cleatDoorSetPrice = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Кнопка сохранения всей системы
                Button(
                    onClick = { saveCurrentSystem() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        "${strings.saveBtn}: «$name»",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSchematicImageCard(
    drawableResId: Int,
    caption: String
) {
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.secondaryBackground.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(6.dp)
            ) {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                )
            }
            Text(
                text = caption,
                fontSize = 11.5.sp,
                color = palette.textSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LetteredDimensionRow(
    badge: String,
    description: String,
    value: String,
    unitText: String = "мм",
    onValueChange: (String) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.secondaryBackground.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = palette.accentBlue,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.widthIn(min = 42.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Text(
                text = description,
                fontSize = 12.sp,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(unitText, fontSize = 10.5.sp) },
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.width(105.dp)
            )
        }
    }
}

@Composable
private fun OverlapInputCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val ov = parseMmOrNull(value) ?: 6f
    val addBothSides = ov * 2f
    val sampleW = 800f + addBothSides
    val sampleH = 1000f + addBothSides
    Surface(
        color = palette.accentOrange.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.accentOrange.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = palette.accentOrange
                    )
                    Text(
                        text = "При нахлёсте ${fmtMm(ov)} мм с каждой стороны (+${fmtMm(addBothSides)} мм к проёму): для проёма 800×1000 мм заготовки в раскрой будут ${fmtMm(sampleW)}×${fmtMm(sampleH)} мм",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("мм") },
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfilePricePerMeterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.accentGreen.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.accentGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = palette.accentGreen,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Цена") },
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

// =========================================================================================
// 5. ЭКРАН ПРАЙСОВ ($) — ПОЛНЫЙ РЕДАКТОР ПРАЙСА (ПРОФИЛИ + СУХАРИ, СТЕКЛОПАКЕТЫ, ФУРНИТУРА, ЦВЕТА, РАЗНОЕ, РАБОТЫ)
// Все добавленные и изменённые позиции сразу отображаются в правой шторке настроек окна ⚙!
// =========================================================================================
private enum class PriceCatalogTab(val titleRu: String) {
    PROFILES_AND_CLEATS("Профили и сухари"),
    GLAZING("Стеклопакеты"),
    HARDWARE("Фурнитура"),
    EXTRAS("Разное"),
    WORKS("Работы")
}

@Composable
private fun PricesEditorScreen(
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    hardwareBrands: List<HardwareBrandItem>,
    extraAccessories: List<ExtraAccessoryItem>,
    workServices: List<WorkServiceItem>,
    currencySymbol: String,
    onBack: () -> Unit,
    onSaveProfiles: (List<ProfileSystemSpec>) -> Unit,
    onSaveGlazing: (List<GlazingCatalogItem>) -> Unit,
    onSaveColors: (List<ColorCatalogItem>) -> Unit,
    onSaveHardware: (List<HardwareBrandItem>) -> Unit,
    onSaveExtras: (List<ExtraAccessoryItem>) -> Unit,
    onSaveWorks: (List<WorkServiceItem>) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var activeTab by remember { mutableStateOf(PriceCatalogTab.PROFILES_AND_CLEATS) }
    var selectedSysId by remember { mutableStateOf(profileSystems.firstOrNull()?.id ?: "") }
    val activeSys = profileSystems.find { it.id == selectedSysId } ?: profileSystems.firstOrNull()
    var savedToast by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = String.format(strings.pricesScreenTitleFormat, currencySymbol),
            onBack = onBack
        )

        // Вкладки разделов прайса в стиле iOS-пилюль
        Surface(
            color = palette.cardSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceCatalogTab.entries.forEach { tab ->
                        val sel = activeTab == tab
                        Surface(
                            color = if (sel) palette.accentBlue else palette.secondaryBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeTab = tab }
                                .testTag("price_tab_${tab.name}")
                        ) {
                            Text(
                                text = tab.titleRu,
                                color = if (sel) Color.White else palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.6.dp, color = palette.divider)
            }
        }

        if (savedToast != null) {
            Surface(
                color = palette.accentGreen,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { savedToast = null }
            ) {
                Text(
                    text = savedToast!!,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        when (activeTab) {
            PriceCatalogTab.PROFILES_AND_CLEATS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.secondaryBackground)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profileSystems.forEach { sys ->
                        val sel = sys.id == activeSys?.id
                        FilterChip(
                            selected = sel,
                            onClick = { selectedSysId = sys.id },
                            label = {
                                Text(
                                    text = sys.name,
                                    color = if (sel) Color.White else palette.textPrimary,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentBlue,
                                containerColor = palette.cardSurface
                            )
                        )
                    }
                }

                if (activeSys != null) {
                    var pFrame by remember(activeSys.id) { mutableStateOf(activeSys.priceFramePerM.toString()) }
                    var pImpost by remember(activeSys.id) { mutableStateOf(activeSys.priceImpostPerM.toString()) }
                    var pSash by remember(activeSys.id) { mutableStateOf(activeSys.priceSashPerM.toString()) }
                    var pDoorSash by remember(activeSys.id) { mutableStateOf(activeSys.priceDoorSashPerM.toString()) }
                    var pShtapik by remember(activeSys.id) { mutableStateOf(activeSys.priceShtapikPerM.toString()) }
                    var pRubber by remember(activeSys.id) { mutableStateOf(activeSys.priceRubberPerM.toString()) }

                    // Угловые сухари (закладные) и соединители импоста
                    var pCleatFrame by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorFrame.toString()) }
                    var pCleatSash by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorSash.toString()) }
                    var pCleatDoorSash by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorDoorSash.toString()) }
                    var pImpConn by remember(activeSys.id) { mutableStateOf(activeSys.priceImpostConnector.toString()) }

                    var pHwTurn by remember(activeSys.id) { mutableStateOf(activeSys.priceMechanicTurn.toString()) }
                    var pHwTiltTurn by remember(activeSys.id) { mutableStateOf(activeSys.priceMechanicTiltTurn.toString()) }
                    var pHwDoor by remember(activeSys.id) { mutableStateOf(activeSys.priceDoorHardware.toString()) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = palette.cardSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            shadowElevation = if (palette.isDark) 0.dp else 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "1. Погонные метры профилей и уплотнителя ($currencySymbol/м)",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pFrame,
                                        onValueChange = { pFrame = it },
                                        label = { Text("Рама ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pImpost,
                                        onValueChange = { pImpost = it },
                                        label = { Text("Импост ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pSash,
                                        onValueChange = { pSash = it },
                                        label = { Text("Створка окна ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pDoorSash,
                                        onValueChange = { pDoorSash = it },
                                        label = { Text("Створка двери ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pShtapik,
                                        onValueChange = { pShtapik = it },
                                        label = { Text("Штапик ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pRubber,
                                        onValueChange = { pRubber = it },
                                        label = { Text("Уплотнитель ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = palette.divider)
                                Text(
                                    "2. Угловые сухари (комплекты на 4 угла / на 1 импост, $currencySymbol/компл.)",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Text(
                                    "1 рама = 1 комплект сухарей рамы • 1 импост = 1 комплект сухарей импоста • 1 створка = 1 комплект створочных сухарей • 1 дверь = 1 комплект дверных сухарей:",
                                    fontSize = 11.5.sp,
                                    color = palette.textSecondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pCleatFrame,
                                        onValueChange = { pCleatFrame = it },
                                        label = { Text("Сухари рамы ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pCleatSash,
                                        onValueChange = { pCleatSash = it },
                                        label = { Text("Сухари створки ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pCleatDoorSash,
                                        onValueChange = { pCleatDoorSash = it },
                                        label = { Text("Сухари двери ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pImpConn,
                                        onValueChange = { pImpConn = it },
                                        label = { Text("Сухари импоста ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = palette.divider)
                                Text(
                                    "3. Базовые механизмы открывания ($currencySymbol/компл.)",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pHwTurn,
                                        onValueChange = { pHwTurn = it },
                                        label = { Text("Поворот. ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pHwTiltTurn,
                                        onValueChange = { pHwTiltTurn = it },
                                        label = { Text("Пов.-отк. ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pHwDoor,
                                        onValueChange = { pHwDoor = it },
                                        label = { Text("Дверной ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                Button(
                                    onClick = {
                                        val updated = activeSys.copy(
                                            priceFramePerM = pFrame.replace(',', '.').toDoubleOrNull() ?: activeSys.priceFramePerM,
                                            priceImpostPerM = pImpost.replace(',', '.').toDoubleOrNull() ?: activeSys.priceImpostPerM,
                                            priceSashPerM = pSash.replace(',', '.').toDoubleOrNull() ?: activeSys.priceSashPerM,
                                            priceDoorSashPerM = pDoorSash.replace(',', '.').toDoubleOrNull() ?: activeSys.priceDoorSashPerM,
                                            priceShtapikPerM = pShtapik.replace(',', '.').toDoubleOrNull() ?: activeSys.priceShtapikPerM,
                                            priceRubberPerM = pRubber.replace(',', '.').toDoubleOrNull() ?: activeSys.priceRubberPerM,
                                            priceCornerConnectorFrame = pCleatFrame.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorFrame,
                                            priceCornerConnectorSash = pCleatSash.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorSash,
                                            priceCornerConnectorDoorSash = pCleatDoorSash.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorDoorSash,
                                            priceImpostConnector = pImpConn.replace(',', '.').toDoubleOrNull() ?: activeSys.priceImpostConnector,
                                            priceMechanicTurn = pHwTurn.replace(',', '.').toDoubleOrNull() ?: activeSys.priceMechanicTurn,
                                            priceMechanicTiltTurn = pHwTiltTurn.replace(',', '.').toDoubleOrNull() ?: activeSys.priceMechanicTiltTurn,
                                            priceDoorHardware = pHwDoor.replace(',', '.').toDoubleOrNull() ?: activeSys.priceDoorHardware
                                        )
                                        onSaveProfiles(profileSystems.map { if (it.id == updated.id) updated else it })
                                        savedToast = "Прайс профильной системы «${updated.name}» сохранён!"
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("save_profile_prices_button")
                                ) {
                                    Text("Сохранить прайс системы и сухарей", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            PriceCatalogTab.GLAZING -> {
                var newGlzName by remember { mutableStateOf("") }
                var newGlzPrice by remember { mutableStateOf("25") }
                var newGlzSandwich by remember { mutableStateOf(false) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            color = palette.cardSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            shadowElevation = if (palette.isDark) 0.dp else 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Добавить стеклопакет / заполнение в прайс",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newGlzName,
                                        onValueChange = { newGlzName = it },
                                        label = { Text("Формула (напр. 4-16-4i)") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newGlzPrice,
                                        onValueChange = { newGlzPrice = it },
                                        label = { Text("Цена ($currencySymbol/m2)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = newGlzSandwich, onCheckedChange = { newGlzSandwich = it })
                                        Text("Непрозрачное (сэндвич)", fontSize = 12.5.sp, color = palette.textPrimary)
                                    }
                                    Button(
                                        onClick = {
                                            if (newGlzName.isNotBlank()) {
                                                val p = newGlzPrice.replace(',', '.').toDoubleOrNull() ?: 20.0
                                                val item = GlazingCatalogItem(
                                                    id = "glz_${UUID.randomUUID().toString().take(6)}",
                                                    name = newGlzName.trim(),
                                                    formula = newGlzName.trim(),
                                                    thicknessMm = 24,
                                                    isSandwich = newGlzSandwich,
                                                    pricePerM2 = p
                                                )
                                                onSaveGlazing(glazingCatalog + item)
                                                newGlzName = ""
                                                savedToast = "Добавлен стеклопакет «${item.name}»"
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue)
                                    ) {
                                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    items(glazingCatalog.filter { it.id != "none" }, key = { it.id }) { item ->
                        EditablePriceRowCard(
                            title = item.name,
                            subtitle = if (item.isSandwich) "Сэндвич-панель ($currencySymbol/m2)" else "Стеклопакет ($currencySymbol/m2)",
                            initialPrice = item.pricePerM2,
                            currencySymbol = currencySymbol,
                            onSavePrice = { newP ->
                                onSaveGlazing(glazingCatalog.map { if (it.id == item.id) it.copy(pricePerM2 = newP) else it })
                                savedToast = "Обновлена цена «${item.name}»: $newP $currencySymbol/m2"
                            },
                            onDelete = if (glazingCatalog.size > 2) {
                                { onSaveGlazing(glazingCatalog.filterNot { it.id == item.id }) }
                            } else null
                        )
                    }
                }
            }

            PriceCatalogTab.HARDWARE -> {
                var newHwName by remember { mutableStateOf("") }
                var newHwPrice by remember { mutableStateOf("25") }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            color = palette.cardSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            shadowElevation = if (palette.isDark) 0.dp else 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Добавить марку фурнитуры в прайс",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = newHwName,
                                        onValueChange = { newHwName = it },
                                        label = { Text("Название (напр. Siegenia Titan)") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newHwPrice,
                                        onValueChange = { newHwPrice = it },
                                        label = { Text("Надбавка ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(0.9f),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            if (newHwName.isNotBlank()) {
                                                val p = newHwPrice.replace(',', '.').toDoubleOrNull() ?: 0.0
                                                val item = HardwareBrandItem(
                                                    id = "hw_${UUID.randomUUID().toString().take(6)}",
                                                    name = newHwName.trim(),
                                                    priceMultiplierOrAddon = p
                                                )
                                                onSaveHardware(hardwareBrands + item)
                                                newHwName = ""
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = strings.saveBtn, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    items(hardwareBrands.filter { it.id != "hw_none" }, key = { it.id }) { item ->
                        EditablePriceRowCard(
                            title = item.name,
                            subtitle = "Надбавка за комплект створки ($currencySymbol)",
                            initialPrice = item.priceMultiplierOrAddon,
                            currencySymbol = currencySymbol,
                            onSavePrice = { newP ->
                                onSaveHardware(hardwareBrands.map { if (it.id == item.id) it.copy(priceMultiplierOrAddon = newP) else it })
                                savedToast = "Обновлена цена фурнитуры «${item.name}»"
                            },
                            onDelete = { onSaveHardware(hardwareBrands.filterNot { it.id == item.id }) }
                        )
                    }
                }
            }

            PriceCatalogTab.EXTRAS -> {
                var newExtName by remember { mutableStateOf("") }
                var newExtPrice by remember { mutableStateOf("15") }
                var newExtRate by remember { mutableStateOf(UnitRateType.PER_PIECE) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            color = palette.cardSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            shadowElevation = if (palette.isDark) 0.dp else 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Добавить комплектующее в раздел «Разное»",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newExtName,
                                        onValueChange = { newExtName = it },
                                        label = { Text("Название (напр. доводчик)") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newExtPrice,
                                        onValueChange = { newExtPrice = it },
                                        label = { Text("Цена ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(0.9f),
                                        singleLine = true
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        UnitRateType.entries.forEach { rt ->
                                            val sel = newExtRate == rt
                                            FilterChip(
                                                selected = sel,
                                                onClick = { newExtRate = rt },
                                                label = {
                                                    Text(
                                                        "за ${rt.labelRu}",
                                                        fontSize = 11.sp,
                                                        color = if (sel) Color.White else palette.textPrimary
                                                    )
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = palette.accentBlue,
                                                    containerColor = palette.secondaryBackground
                                                )
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (newExtName.isNotBlank()) {
                                                val p = newExtPrice.replace(',', '.').toDoubleOrNull() ?: 0.0
                                                val item = ExtraAccessoryItem(
                                                    id = "ext_${UUID.randomUUID().toString().take(6)}",
                                                    name = newExtName.trim(),
                                                    price = p,
                                                    rateType = newExtRate
                                                )
                                                onSaveExtras(extraAccessories + item)
                                                newExtName = ""
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue)
                                    ) {
                                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    items(extraAccessories, key = { it.id }) { item ->
                        EditablePriceRowCard(
                            title = item.name,
                            subtitle = "Расчёт: за 1 ${item.rateType.labelRu}",
                            initialPrice = item.price,
                            currencySymbol = "$currencySymbol/${item.rateType.labelRu}",
                            onSavePrice = { newP ->
                                onSaveExtras(extraAccessories.map { if (it.id == item.id) it.copy(price = newP) else it })
                                savedToast = "Обновлена цена «${item.name}»"
                            },
                            onDelete = { onSaveExtras(extraAccessories.filterNot { it.id == item.id }) }
                        )
                    }
                }
            }

            PriceCatalogTab.WORKS -> {
                var newWrkName by remember { mutableStateOf("") }
                var newWrkPrice by remember { mutableStateOf("20") }
                var newWrkRate by remember { mutableStateOf(UnitRateType.PER_M2) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            color = palette.cardSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            shadowElevation = if (palette.isDark) 0.dp else 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Добавить услугу в раздел «Работы»",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newWrkName,
                                        onValueChange = { newWrkName = it },
                                        label = { Text("Название работы") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newWrkPrice,
                                        onValueChange = { newWrkPrice = it },
                                        label = { Text("Цена ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(0.9f),
                                        singleLine = true
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        UnitRateType.entries.forEach { rt ->
                                            val sel = newWrkRate == rt
                                            FilterChip(
                                                selected = sel,
                                                onClick = { newWrkRate = rt },
                                                label = {
                                                    Text(
                                                        "за ${rt.labelRu}",
                                                        fontSize = 11.sp,
                                                        color = if (sel) Color.White else palette.textPrimary
                                                    )
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = palette.accentBlue,
                                                    containerColor = palette.secondaryBackground
                                                )
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (newWrkName.isNotBlank()) {
                                                val p = newWrkPrice.replace(',', '.').toDoubleOrNull() ?: 0.0
                                                val item = WorkServiceItem(
                                                    id = "wrk_${UUID.randomUUID().toString().take(6)}",
                                                    name = newWrkName.trim(),
                                                    price = p,
                                                    rateType = newWrkRate
                                                )
                                                onSaveWorks(workServices + item)
                                                newWrkName = ""
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue)
                                    ) {
                                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    items(workServices, key = { it.id }) { item ->
                        EditablePriceRowCard(
                            title = item.name,
                            subtitle = "Расчёт: за 1 ${item.rateType.labelRu}",
                            initialPrice = item.price,
                            currencySymbol = "$currencySymbol/${item.rateType.labelRu}",
                            onSavePrice = { newP ->
                                onSaveWorks(workServices.map { if (it.id == item.id) it.copy(price = newP) else it })
                                savedToast = "Обновлена расценка работы «${item.name}»"
                            },
                            onDelete = { onSaveWorks(workServices.filterNot { it.id == item.id }) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditablePriceRowCard(
    title: String,
    subtitle: String,
    initialPrice: Double,
    currencySymbol: String,
    onSavePrice: (Double) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var priceText by remember(initialPrice) { mutableStateOf(initialPrice.toString()) }
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        shadowElevation = if (palette.isDark) 0.dp else 1.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = palette.textPrimary)
                Text(subtitle, fontSize = 11.5.sp, color = palette.textSecondary)
            }
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(currencySymbol, fontSize = 10.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(0.85f),
                singleLine = true
            )
            Button(
                onClick = {
                    val p = priceText.replace(',', '.').toDoubleOrNull() ?: initialPrice
                    onSavePrice(p)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(strings.okBtn, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = strings.deleteWindowDesc, tint = palette.accentRed)
                }
            }
        }
    }
}

// =========================================================================================
// 6. СКЛАД МАТЕРИАЛА: ПАПКИ ПО ПРОФИЛЬНЫМ СИСТЕМАМ
// =========================================================================================
@Composable
private fun WarehouseBySystemScreen(
    profileSystems: List<ProfileSystemSpec>,
    stockBars: List<StockRemnantBar>,
    onBack: () -> Unit,
    onSaveStock: (List<StockRemnantBar>) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var selectedFolderSysId by remember { mutableStateOf(profileSystems.firstOrNull()?.id ?: "") }
    val activeSys = profileSystems.find { it.id == selectedFolderSysId } ?: profileSystems.firstOrNull()

    var selectedRole by remember { mutableStateOf(ProfileRole.FRAME) }
    var selectedColorName by remember(activeSys?.id) {
        mutableStateOf(activeSys?.colors?.firstOrNull()?.name ?: RAW_UNPAINTED_COLOR_NAME)
    }
    var selectedColorHex by remember(activeSys?.id) {
        mutableLongStateOf(activeSys?.colors?.firstOrNull()?.hexColor ?: 0xFFC0C0C0L)
    }
    var barLengthMm by remember(activeSys?.id) {
        mutableStateOf((activeSys?.stockBarLengthMm?.roundToInt() ?: 6000).toString())
    }
    var barQty by remember { mutableStateOf("20") }
    var isFullShipmentBar by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = strings.warehouseScreenTitle,
            onBack = onBack
        )

        Text(
            "Папки профильных систем:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = 14.dp, top = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profileSystems.forEach { sys ->
                val isSelected = sys.id == activeSys?.id
                val sysItems = stockBars.filter { it.profileSystemId == sys.id }
                val fullCnt = sysItems.filter { it.isFullStockBar }.sumOf { it.quantity }
                val remCnt = sysItems.filter { !it.isFullStockBar }.sumOf { it.quantity }

                Surface(
                    color = if (isSelected) palette.accentTeal else palette.cardSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = if (isSelected) 0.dp else 0.8.dp,
                        color = if (isSelected) Color.Transparent else palette.floatingBorder
                    ),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedFolderSysId = sys.id }
                        .testTag("warehouse_folder_${sys.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                            contentDescription = sys.name,
                            tint = if (isSelected) Color.White else palette.accentTeal,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = sys.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (isSelected) Color.White else palette.textPrimary
                            )
                            Text(
                                text = "Хлыстов: $fullCnt шт. | Остатков: $remCnt шт.",
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        if (activeSys != null) {
            val folderStock = stockBars.filter { it.profileSystemId == activeSys.id }
            val fullBarsList = folderStock.filter { it.isFullStockBar }
            val remnantsList = folderStock.filter { !it.isFullStockBar }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item {
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.8.dp, palette.floatingBorder),
                        shadowElevation = if (palette.isDark) 0.dp else 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Приход материала / внесение в папку «${activeSys.name}»",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = palette.accentTeal
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = isFullShipmentBar,
                                    onClick = {
                                        isFullShipmentBar = true
                                        barLengthMm = activeSys.stockBarLengthMm.roundToInt().toString()
                                    },
                                    label = {
                                        Text(
                                            "Целые хлысты",
                                            color = if (isFullShipmentBar) Color.White else palette.textPrimary
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.accentTeal,
                                        containerColor = palette.secondaryBackground
                                    )
                                )
                                FilterChip(
                                    selected = !isFullShipmentBar,
                                    onClick = {
                                        isFullShipmentBar = false
                                        barLengthMm = "1800"
                                    },
                                    label = {
                                        Text(
                                            "Деловой остаток",
                                            color = if (!isFullShipmentBar) Color.White else palette.textPrimary
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.accentTeal,
                                        containerColor = palette.secondaryBackground
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ProfileRole.entries.forEach { role ->
                                    val sel = selectedRole == role
                                    FilterChip(
                                        selected = sel,
                                        onClick = { selectedRole = role },
                                        label = {
                                            Text(
                                                role.titleRu,
                                                fontSize = 11.5.sp,
                                                color = if (sel) Color.White else palette.textPrimary
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.accentBlue,
                                            containerColor = palette.secondaryBackground
                                        )
                                    )
                                }
                            }

                            // Выбор цвета профиля для прихода на склад
                            val warehouseColorOptions = remember(activeSys.id, activeSys.colors) {
                                (activeSys.colors.map { it.name to it.hexColor } + (RAW_UNPAINTED_COLOR_NAME to 0xFFC0C0C0L))
                                    .distinctBy { it.first }
                            }
                            Text(
                                text = "Цвет профиля:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                warehouseColorOptions.forEach { (cName, cHex) ->
                                    val sel = selectedColorName == cName
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            selectedColorName = cName
                                            selectedColorHex = cHex
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(Color(cHex), CircleShape)
                                                    .border(0.8.dp, palette.floatingBorder, CircleShape)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = cName,
                                                fontSize = 11.5.sp,
                                                color = if (sel) Color.White else palette.textPrimary
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.accentTeal,
                                            containerColor = palette.secondaryBackground
                                        )
                                    )
                                }
                            }

                            if (isFullShipmentBar) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Быстрая длина:", fontSize = 11.sp, color = palette.textSecondary)
                                    listOf(5500, 6000, 6500, 7000, 8000).forEach { stdLen ->
                                        AssistChip(
                                            onClick = { barLengthMm = stdLen.toString() },
                                            label = { Text("${stdLen}мм", fontSize = 11.sp, color = palette.textPrimary) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = barLengthMm,
                                    onValueChange = { barLengthMm = it },
                                    label = { Text("Длина профиля (мм)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = barQty,
                                    onValueChange = { barQty = it },
                                    label = { Text("Количество (шт.)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Button(
                                onClick = {
                                    val len = (barLengthMm.toIntOrNull() ?: 6000).coerceIn(300, 12000)
                                    val qty = (barQty.toIntOrNull() ?: 1).coerceAtLeast(1)
                                    val newEntry = StockRemnantBar(
                                        id = UUID.randomUUID().toString(),
                                        profileSystemId = activeSys.id,
                                        role = selectedRole,
                                        lengthMm = len,
                                        quantity = qty,
                                        isFullStockBar = isFullShipmentBar,
                                        colorName = selectedColorName,
                                        colorHex = selectedColorHex
                                    )
                                    onSaveStock(listOf(newEntry) + stockBars)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accentTeal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("warehouse_add_stock_button")
                            ) {
                                Text(
                                    if (isFullShipmentBar) "+ Оприходовать целые профили"
                                    else "+ Добавить деловой остаток",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "1. Целые профили в наличии (${fullBarsList.sumOf { it.quantity }} шт.):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )
                }
                items(fullBarsList, key = { it.id }) { item ->
                    StockBarRowCard(
                        item = item,
                        onChangeQty = { delta ->
                            val newQ = item.quantity + delta
                            if (newQ <= 0) {
                                onSaveStock(stockBars.filterNot { it.id == item.id })
                            } else {
                                onSaveStock(stockBars.map { if (it.id == item.id) item.copy(quantity = newQ) else it })
                            }
                        },
                        onDelete = { onSaveStock(stockBars.filterNot { it.id == item.id }) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "2. Значимые деловые остатки после раскроя (${remnantsList.sumOf { it.quantity }} шт.):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )
                }
                items(remnantsList, key = { it.id }) { item ->
                    StockBarRowCard(
                        item = item,
                        onChangeQty = { delta ->
                            val newQ = item.quantity + delta
                            if (newQ <= 0) {
                                onSaveStock(stockBars.filterNot { it.id == item.id })
                            } else {
                                onSaveStock(stockBars.map { if (it.id == item.id) item.copy(quantity = newQ) else it })
                            }
                        },
                        onDelete = { onSaveStock(stockBars.filterNot { it.id == item.id }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StockBarRowCard(
    item: StockRemnantBar,
    onChangeQty: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        shadowElevation = if (palette.isDark) 0.dp else 1.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${item.role.titleRu} — длина ${item.lengthMm} мм (${String.format(Locale.US, "%.2f", item.lengthMm / 1000.0)} м)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = palette.textPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = palette.secondaryBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.6.dp, palette.floatingBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(item.colorHex), CircleShape)
                                    .border(0.6.dp, palette.floatingBorder, CircleShape)
                            )
                            Text(
                                text = item.colorName,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary
                            )
                        }
                    }
                    Text(
                        text = if (item.isFullStockBar) "Целый хлыст" else "Деловой остаток",
                        fontSize = 11.sp,
                        color = if (item.isFullStockBar) palette.accentTeal else palette.accentGreen
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onChangeQty(-1) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("−", color = palette.textPrimary)
                }
                Text("${item.quantity} шт.", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                OutlinedButton(
                    onClick = { onChangeQty(1) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("+", color = palette.textPrimary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = strings.deleteWindowDesc,
                        tint = palette.accentRed
                    )
                }
            }
        }
    }
}

// =========================================================================================
// 7. МОДУЛЬ "ЗАДАНИЕ В ЦЕХ И 1D РАСКРОЙ ПРОФИЛЯ (С УЧЁТОМ СКЛАДА И ДЕЛОВЫХ ОСТАТКОВ)"
// =========================================================================================
@Composable
private fun WorkshopAndCuttingScreen(
    order: CustomerOrder,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    sillsCatalog: List<SillCatalogItem>,
    extraAccessoriesCatalog: List<ExtraAccessoryItem>,
    workServicesCatalog: List<WorkServiceItem>,
    hardwareBrandsCatalog: List<HardwareBrandItem>,
    stockRemnants: List<StockRemnantBar>,
    onBackToCad: () -> Unit,
    onSaveStockRemnants: (List<StockRemnantBar>) -> Unit,
    onOpenPdfFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current

    val windowResults = remember(order, profileSystems, glazingCatalog, colorCatalog, sillsCatalog, extraAccessoriesCatalog, workServicesCatalog, hardwareBrandsCatalog) {
        order.windows.mapIndexed { idx, win ->
            WindowBomAndCostEngine.calculateWindow(
                window = win,
                profileSystems = profileSystems,
                glazingCatalog = glazingCatalog,
                colorCatalog = colorCatalog,
                sillsCatalog = sillsCatalog,
                extraAccessoriesCatalog = extraAccessoriesCatalog,
                workServicesCatalog = workServicesCatalog,
                hardwareBrandsCatalog = hardwareBrandsCatalog,
                windowIndexInOrder = idx + 1
            )
        }
    }

    val usableRemnants = remember(stockRemnants) { stockRemnants.filter { !it.isFullStockBar } }

    val (barAllocations, procurementSummaries) = remember(windowResults, usableRemnants, profileSystems) {
        WindowBomAndCostEngine.optimizeOrderProfileCutting(
            windowResults = windowResults,
            stockRemnants = usableRemnants,
            profileSystems = profileSystems
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = strings.workshopScreenTitle,
            onBack = onBackToCad
        ) {
            Surface(
                color = palette.accentTeal,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val newRemnantBars = procurementSummaries.flatMap { sum ->
                            sum.newBusinessRemnantsMm.map { remLen ->
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
                            }
                        }
                        onSaveStockRemnants(stockRemnants + newRemnantBars)
                    }
                    .testTag("workshop_save_remnants_button")
            ) {
                Text(
                    "Остатки → Склад",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
            Surface(
                color = palette.accentBlue,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val file = PdfReportGenerator.generateCuttingAndProcurementPdf(
                            context = context,
                            order = order,
                            allocations = barAllocations,
                            procurement = procurementSummaries,
                            windowResults = windowResults
                        )
                        onOpenPdfFile(file, "Задание в цех и раскрой PDF")
                    }
                    .testTag("workshop_pdf_button")
            ) {
                Text(
                    "PDF в цех",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "1. Потребность в профиле (Рама → Импост → Створка → Дверь → Штапик):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.textPrimary
                )
            }

            items(procurementSummaries) { sum ->
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color(sum.colorHex), CircleShape)
                                    .border(0.8.dp, palette.floatingBorder, CircleShape)
                            )
                            Text(
                                "${sum.role.titleRu.uppercase()} — ${sum.profileSystemName} [${sum.colorName}] (хлыст ${sum.stockBarLengthMm} мм)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.accentBlue
                            )
                        }
                        Text(
                            "Новых хлыстов: ${sum.newFullBarsToBuyCount} шт. • Из остатков склада: ${sum.usedRemnantBarsCount} шт. • Деталей: ${sum.totalCutPiecesCount} шт. (${String.format(Locale.US, "%.2f", sum.totalNetLengthM)} м) • Отход: ${String.format(Locale.US, "%.1f", sum.wastePercent)}%",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        if (sum.newBusinessRemnantsMm.isNotEmpty() && sum.role != ProfileRole.SHTAPIK) {
                            Text(
                                "Деловые остатки (≥400 мм): ${sum.newBusinessRemnantsMm.joinToString(", ") { "${it}мм" }}",
                                fontSize = 11.5.sp,
                                color = palette.accentGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "2. Визуальная карта раскроя хлыстов (без штапиков):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.textPrimary
                )
            }

            items(barAllocations.filter { it.role != ProfileRole.SHTAPIK }) { bar ->
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(bar.colorHex), CircleShape)
                                        .border(0.7.dp, palette.floatingBorder, CircleShape)
                                )
                                Text(
                                    "${bar.role.titleRu.uppercase()} (${bar.profileSystemName} • ${bar.colorName}) — Хлыст #${bar.barNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = palette.textPrimary
                                )
                            }
                            Text(
                                if (bar.isFromRemnantStock) "СО СКЛАДА ОСТАТКОВ (${bar.sourceLengthMm} мм)"
                                else "ЦЕЛЫЙ ХЛЫСТ (${bar.sourceLengthMm} мм)",
                                fontSize = 11.sp,
                                color = if (bar.isFromRemnantStock) palette.accentGreen else palette.accentBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, palette.floatingBorder, RoundedCornerShape(8.dp))
                        ) {
                            val srcLen = bar.sourceLengthMm.toFloat().coerceAtLeast(1f)
                            bar.cuts.forEach { piece ->
                                val wWeight = (piece.cutLengthMm / srcLen).coerceAtLeast(0.05f)
                                Box(
                                    modifier = Modifier
                                        .weight(wWeight)
                                        .fillMaxHeight()
                                        .background(Color(0xFFB3E5FC))
                                        .border(0.5.dp, Color(0xFF0288D1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${piece.cutLengthMm} (${piece.shortTag})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0D2538),
                                        maxLines = 1
                                    )
                                }
                            }
                            if (bar.remnantLengthMm > 0) {
                                val remWeight = (bar.remnantLengthMm / srcLen).coerceAtLeast(0.04f)
                                Box(
                                    modifier = Modifier
                                        .weight(remWeight)
                                        .fillMaxHeight()
                                        .background(
                                            if (bar.isUsableBusinessRemnant) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Ост:${bar.remnantLengthMm}",
                                        fontSize = 9.sp,
                                        color = Color(0xFF1F2937),
                                        maxLines = 1
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
