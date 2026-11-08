package ru.winstudiopro.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    ORDER_STATUS_BOARD,  // Доска статусов заказов (менеджер активных заказов)
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

private val MetroBlue = Color(0xFF0099CC)
private val MetroDarkBlue = Color(0xFF007799)
private val CanvasBgGray = Color(0xFFCCCCCC)

/**
 * Единая аккуратная верхняя синяя панель (52.dp) для всех экранов приложения.
 */
@Composable
private fun UnifiedTopAppBar(
    title: String,
    onBack: () -> Unit,
    backgroundColor: Color = MetroBlue,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        color = backgroundColor,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = actions
            )
        }
    }
}

@Composable
fun WinStudioProMainApp(
    onOpenPdfFile: (File, String) -> Unit,
    onSharePdfFile: (File, String) -> Unit = onOpenPdfFile
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
            colorId = "none"
        )

        val newOrder = CustomerOrder(
            id = UUID.randomUUID().toString(),
            orderNumber = "Заказ №($nowStamp)",
            customerName = "",
            customerPhone = "",
            address = "",
            createdDate = todayStr,
            currencySymbol = uiSettings.currencySymbol,
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
    val isDark = uiSettings.themeMode == AppThemeMode.DARK

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = if (isDark) Color(0xFF1E2228) else Color(0xFFF2F5F8)
    ) {
        when (screenMode) {
            AppScreenMode.METRO_HOME -> {
                MetroStartDashboard(
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
                    onNavigate = { target -> screenMode = target }
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
                    onUpdateOrders = { updated -> persistAll(newOrders = updated) }
                )
            }

            AppScreenMode.PROFILE_CONSTRUCTOR -> {
                ProfileConstructorScreen(
                    profileSystems = profileSystems,
                    currencySymbol = uiSettings.currencySymbol,
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
                    currencySymbol = uiSettings.currencySymbol,
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

            AppScreenMode.ORDER_STATUS_BOARD -> {
                OrderStatusManagerScreen(
                    orders = orders,
                    onBack = { screenMode = AppScreenMode.METRO_HOME },
                    onOpenOrder = { orderId ->
                        activeOrderId = orderId
                        activeWindowIndex = 0
                        screenMode = AppScreenMode.CAD_WORKSPACE
                    },
                    onUpdateOrders = { updated -> persistAll(newOrders = updated) }
                )
            }

            AppScreenMode.UI_SETTINGS -> {
                UiSettingsScreen(
                    settings = uiSettings,
                    onBack = { screenMode = AppScreenMode.METRO_HOME },
                    onSaveSettings = { newSettings ->
                        persistAll(newSettings = newSettings)
                    }
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

// =========================================================================================
// 1. СТАРТОВЫЙ ПЛИТОЧНЫЙ ЭКРАН (В СТИЛЕ ОРИГИНАЛА + РАСШИРЕННЫЕ ПЛИТКИ)
// =========================================================================================
@Composable
private fun MetroStartDashboard(
    orders: List<CustomerOrder>,
    profileSystems: List<ProfileSystemSpec>,
    stockRemnants: List<StockRemnantBar>,
    uiSettings: AppUiSettings,
    onNewOrderClick: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onNavigate: (AppScreenMode) -> Unit
) {
    val lastOrder = orders.getOrNull(0)
    val prevOrder = orders.getOrNull(1)
    val scrollState = rememberScrollState()
    val isDark = uiSettings.themeMode == AppThemeMode.DARK

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF1A1D21) else Color(0xFFE6EAEE))
            .verticalScroll(scrollState)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WinStudio Pro",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF263238)
            )
            Text(
                text = "SQLite • ${profileSystems.size} пром. систем • ${orders.size} заказов",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        // РЯД 1: "новый заказ (+)" и "заказы"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = MetroBlue,
                title = "новый заказ",
                subtitle = "1500×1500 (сразу в чертёж)",
                onClick = onNewOrderClick
            ) {
                Text(
                    text = "+",
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
            }

            MetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = MetroBlue,
                title = "заказы",
                subtitle = "всего: ${orders.size}",
                onClick = { onNavigate(AppScreenMode.ORDERS_LIST) }
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // РЯД 2: "последний заказ" и "предпоследний заказ" с живыми миниатюрами
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrderPreviewMetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                headerLabel = "последний заказ",
                order = lastOrder,
                onClick = {
                    if (lastOrder != null) onOpenOrder(lastOrder.id) else onNewOrderClick()
                }
            )
            OrderPreviewMetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                headerLabel = "предпоследний заказ",
                order = prevOrder,
                onClick = {
                    if (prevOrder != null) onOpenOrder(prevOrder.id)
                }
            )
        }

        // РЯД 3: "профильные системы (конструктор)" и "прайсы ($)"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = MetroBlue,
                title = "профильные системы",
                subtitle = "конструктор геометрии (${profileSystems.size})",
                onClick = { onNavigate(AppScreenMode.PROFILE_CONSTRUCTOR) }
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            MetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = MetroBlue,
                title = "прайсы",
                subtitle = "профили, сухари, стёкла, работы",
                onClick = { onNavigate(AppScreenMode.PRICES_SCREEN) }
            ) {
                Text(
                    text = "$",
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
            }
        }

        // РЯД 4: "склад (материал и остатки по папкам систем)" и "статусы заказов"
        val fullBarsCount = stockRemnants.filter { it.isFullStockBar }.sumOf { it.quantity }
        val remnantsCount = stockRemnants.filter { !it.isFullStockBar }.sumOf { it.quantity }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = Color(0xFF00838F),
                title = "склад материала",
                subtitle = "хлыстов: $fullBarsCount | остатков: $remnantsCount",
                onClick = { onNavigate(AppScreenMode.WAREHOUSE_STOCK) }
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            val inWorkCount = orders.count { it.status == OrderStatus.IN_WORKSHOP || it.status == OrderStatus.CONFIRMED }
            MetroTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = Color(0xFF00838F),
                title = "статусы заказов",
                subtitle = "в работе / в цеху: $inWorkCount",
                onClick = { onNavigate(AppScreenMode.ORDER_STATUS_BOARD) }
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // РЯД 5: "настройки интерфейса"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetroTile(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                backgroundColor = MetroDarkBlue,
                title = "настройки интерфейса и реквизитов PDF",
                subtitle = "валюта: ${uiSettings.currencySymbol} • тема: ${uiSettings.themeMode.titleRu} • компания: ${uiSettings.companyName}",
                onClick = { onNavigate(AppScreenMode.UI_SETTINGS) }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

@Composable
private fun MetroTile(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    centerContent: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 26.dp),
            contentAlignment = Alignment.Center,
            content = centerContent
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OrderPreviewMetroTile(
    modifier: Modifier = Modifier,
    headerLabel: String,
    order: CustomerOrder?,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MetroBlue)
            .clickable(enabled = order != null) { onClick() }
            .padding(10.dp)
    ) {
        if (order == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("нет заказа", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstWin = order.windows.firstOrNull()
                    if (firstWin != null) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(4.dp)
                        ) {
                            WindowMiniThumbnail(
                                topology = firstWin.topology,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.orderNumber,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (order.customerName.isNotBlank()) {
                            Text(
                                text = order.customerName,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Окон: ${order.windows.sumOf { it.quantity }} шт.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = order.status.titleRu,
                            color = Color(0xFFFFF59D),
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text = headerLabel,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
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
    onSharePdfFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val windows = order.windows
    val currentWindow = windows.getOrNull(activeWindowIndex) ?: return

    val activeProfile = profileSystems.find { it.id == currentWindow.profileSystemId }
        ?: profileSystems.firstOrNull()
        ?: DefaultCatalogs.defaultProfileSystems().first()

    val activeGlazing = glazingCatalog.find { it.id == currentWindow.glazingId }
        ?: glazingCatalog.first()

    val activeColor = colorCatalog.find { it.id == currentWindow.colorId }
        ?: colorCatalog.first()

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- ВЕРХНЯЯ ПАНЕЛЬ ИНСТРУМЕНТОВ РЕДАКТОРА (50.dp, аккуратная, без наползания кнопок) ---
            Surface(
                color = Color(0xFF263238),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Button(
                            onClick = { showLeftBlueMenu = !showLeftBlueMenu },
                            colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("≡ МЕНЮ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Surface(
                            color = Color(0xFF37474F),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { showCustomerEditDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = order.orderNumber,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (order.customerName.isBlank()) "Клиент (нажмите для ввода)" else "${order.customerName} ${order.customerPhone}",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Данные клиента",
                                    tint = Color(0xFF80DEEA),
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
                        IconButton(
                            onClick = { undoCurrentWindow() },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (undoStack.isNotEmpty()) Color(0xFF37474F) else Color(0xFF263238),
                                    RoundedCornerShape(3.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Отмена (Undo)",
                                tint = if (undoStack.isNotEmpty()) Color.White else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Кнопка «Повтор» (Redo)
                        IconButton(
                            onClick = { redoCurrentWindow() },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (redoStack.isNotEmpty()) Color(0xFF37474F) else Color(0xFF263238),
                                    RoundedCornerShape(3.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Redo,
                                contentDescription = "Повтор (Redo)",
                                tint = if (redoStack.isNotEmpty()) Color.White else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Кнопка выравнивания импостов (справа вверху)
                        Button(
                            onClick = { showAlignmentDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "|↔| Импосты (${currentAlignmentMode.titleRu.substringBefore(" ")})",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- ЦЕНТРАЛЬНАЯ ОБЛАСТЬ: СЕРЫЙ ХОЛСТ ЧЕРТЕЖА + СПРАВА ИНФОРМАЦИОННЫЕ ПЛАШКИ + ВНИЗУ СПРАВА КНОПКА НАСТРОЕК ⚙ ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(CanvasBgGray)
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
                    modifier = Modifier.fillMaxSize()
                )

                // ПРАВЫЕ КОМПАКТНЫЕ ИНФОРМАЦИОННЫЕ ПЛАШКИ (НЕ ПЕРЕХВАТЫВАЮТ КЛИКИ ЧЕРТЕЖА, КАК В ОРИГИНАЛЕ НА 00:54 ВИДЕО!)
                val hwName = hardwareBrandsCatalog
                    .find { it.id == currentWindow.hardwareBrandId }?.name ?: "не учитывать"
                val finName = DefaultCatalogs.defaultFinancialSchemes()
                    .find { it.id == currentWindow.financialSchemeId }?.name

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .widthIn(max = 136.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (finName != null && currentWindow.financialSchemeId != "fin_none") {
                        CanvasReadOnlyInfoBadge(label = finName, bgColor = Color(0xFFD32F2F))
                    }
                    if (currentWindow.profileSystemId != "none") {
                        CanvasReadOnlyInfoBadge(label = activeProfile.name, bgColor = Color(0xFFE65100))
                        val chosenSht = activeProfile.shtapikVariants.find { it.id == currentWindow.selectedShtapikId }
                            ?: activeProfile.shtapikVariants.minByOrNull { kotlin.math.abs(it.glazingThicknessSpMm - activeGlazing.thicknessMm) }
                            ?: activeProfile.shtapikVariants.firstOrNull()
                        if (chosenSht != null) {
                            CanvasReadOnlyInfoBadge(label = chosenSht.name, bgColor = Color(0xFFBF360C))
                        }
                    }
                    if (currentWindow.glazingId != "none") {
                        CanvasReadOnlyInfoBadge(label = activeGlazing.name, bgColor = Color(0xFF0099CC))
                    }
                    if (currentWindow.colorId != "none") {
                        CanvasReadOnlyInfoBadge(label = activeColor.name, bgColor = Color(0xFF455A64))
                    }
                    if (currentWindow.hardwareBrandId != "hw_none") {
                        CanvasReadOnlyInfoBadge(label = hwName, bgColor = Color(0xFF6A1B9A))
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
                            CanvasReadOnlyInfoBadge(label = wrkName, bgColor = Color(0xFF558B2F))
                        }
                    }
                }

                // ЗАМЕТКА К ОКНУ ВНИЗУ СЛЕВА НАД ДИНАМИЧЕСКИМ СКРОЛЛОМ (КАК НА 00:54 В ВИДЕО!)
                if (currentWindow.note.isNotBlank()) {
                    Text(
                        text = currentWindow.note,
                        color = Color(0xFF333333),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 6.dp)
                    )
                }

                // КНОПКИ В ПРАВОМ НИЖНЕМ УГЛУ ХОЛСТА (КОРЗИНА 🗑 + КНОПКА НАСТРОЕК ⚙ ВМЕСТО ЦИРКУЛЯ ОРИГИНАЛА!)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (windows.size > 1) {
                        Surface(
                            color = Color(0xFF455A64).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(42.dp)
                                .clickable {
                                    val updated = windows.filterIndexed { index, _ -> index != activeWindowIndex }
                                    onUpdateOrder(order.copy(windows = updated))
                                    onSelectWindowIndex((activeWindowIndex - 1).coerceAtLeast(0))
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить окно",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = MetroBlue,
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .height(42.dp)
                            .clickable { showRightSettingsDrawer = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Настройки окна",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            if (currentWindow.quantity > 1) {
                                Text(
                                    text = "${currentWindow.quantity} шт",
                                    color = Color(0xFFFFF59D),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (snackMessage != null) {
                    Surface(
                        color = Color(0xFF263238),
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clickable { snackMessage = null }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(snackMessage!!, color = Color.White, fontSize = 12.sp)
                            Text("✕", color = Color(0xFF80DEEA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

            // --- НИЖНЯЯ ЛЕНТА КОНСТРУКЦИЙ ЗАКАЗА (КНОПКА "+" СЛЕВА СРАЗУ ДОБАВЛЯЕТ 1500х1500 С ТЕКУЩИМИ НАСТРОЙКАМИ!) ---
            Surface(
                color = Color(0xFF37474F),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MetroBlue)
                            .clickable { addNewWindowInheritingSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("1500×1500", color = Color.White, fontSize = 8.5.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00838F))
                            .clickable { duplicateCurrentWindow() }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                "Копия\nокна",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.5.sp
                            )
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
                            val winColorHex = colorCatalog.find { it.id == winItem.colorId }?.hexColor ?: 0xFFE6E9ED
                            Box(
                                modifier = Modifier
                                    .width(88.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color.White else Color(0xFFECEFF1))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MetroBlue else Color.Gray,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSelectWindowIndex(idx) }
                                    .padding(3.dp)
                            ) {
                                WindowMiniThumbnail(
                                    topology = winItem.topology,
                                    frameColorHex = winColorHex,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 13.dp)
                                )
                                Text(
                                    text = "№${idx + 1} (${winItem.quantity}шт)",
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MetroDarkBlue else Color.DarkGray,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- ЛЕВОЕ ВЫПАДАЮЩЕЕ СИНЕЕ МЕНЮ "≡" (КАК В ОРИГИНАЛЕ НА 01:14 ВИДЕО) ---
        if (showLeftBlueMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { showLeftBlueMenu = false }
            ) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(MetroBlue)
                        .clickable(enabled = false) {}
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "меню заказа и отчёты",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.4f))

                    LeftMenuButton("На главный экран (Плитки)") {
                        showLeftBlueMenu = false
                        onBackToHome()
                    }
                    LeftMenuButton("Данные клиента и № заказа") {
                        showLeftBlueMenu = false
                        showCustomerEditDialog = true
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

                    LeftMenuButton("📄 Чертежи (предпросмотр и PDF)") {
                        showLeftBlueMenu = false
                        previewReportMode = ClientPdfReportMode.DRAWINGS_ONLY
                    }
                    LeftMenuButton("📋 Чертежи с подробностями (PDF)") {
                        showLeftBlueMenu = false
                        previewReportMode = ClientPdfReportMode.DRAWINGS_WITH_DETAILS
                    }
                    LeftMenuButton("💼 Коммерческое предложение (PDF)") {
                        showLeftBlueMenu = false
                        previewReportMode = ClientPdfReportMode.COMMERCIAL_PROPOSAL
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

                    LeftMenuButton("🪟 Заказ стеклопакетов (PDF)") {
                        showLeftBlueMenu = false
                        val file = PdfReportGenerator.generateGlazingSpecPdf(context, order, allWindowResults)
                        onOpenPdfFile(file, "Заказ стеклопакетов")
                    }
                    LeftMenuButton("🔩 Заказ фурнитуры (PDF)") {
                        showLeftBlueMenu = false
                        val file = PdfReportGenerator.generateHardwareSpecPdf(context, order, allWindowResults)
                        onOpenPdfFile(file, "Заказ фурнитуры")
                    }
                    LeftMenuButton("🪚 Задание в цех + 1D Раскрой профиля") {
                        showLeftBlueMenu = false
                        onOpenWorkshopCutting()
                    }
                }
            }
        }

        // --- ПРАВАЯ БОКОВАЯ ШТОРКА "НАСТРОЙКИ ОКНА" (В СТИЛЕ ОРИГИНАЛА 00:31 - 01:12 + ПАКЕТНОЕ ПРИМЕНЕНИЕ) ---
        if (showRightSettingsDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { showRightSettingsDrawer = false }
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(340.dp)
                        .fillMaxHeight()
                        .clickable(enabled = false) {},
                    color = Color.White,
                    shadowElevation = 16.dp
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

    // --- КАЛЬКУЛЯТОР "СВОЯ ЦЕНА" (ПРИ КЛИКЕ НА ЖЁЛТУЮ ПЛАШКУ "СВОЯ ЦЕНА" В ШТОРКЕ НАСТРОЕК, КАК НА 00:50 В ВИДЕО!) ---
    if (showManualPriceNumpad) {
        OriginalDecimalNumpadModalDialog(
            title = "Своя цена за 1 окно (${order.currencySymbol})",
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

    // --- МОДАЛЬНОЕ ОКНО: ФИРМЕННЫЙ СИНЕ-БЕЛЫЙ ЦИФРОВОЙ КАЛЬКУЛЯТОР РАЗМЕРОВ ---
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
                snackMessage = "Импосты выровнены: ${chosenMode.titleRu}"
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
        color = bgColor.copy(alpha = 0.86f),
        shape = RoundedCornerShape(2.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
        )
    }
}

/**
 * Нижняя динамическая 2-строчная панель с горизонтальным скроллом метров, штук (включая угловые сухари) и сумм,
 * и закреплённой колонкой «всего» справа (как на 00:00–00:22 эталонного видео!).
 */
@Composable
private fun DynamicBottomCostScrollBar(
    currentResult: WindowCalculationResult,
    allResults: List<WindowCalculationResult>,
    order: CustomerOrder,
    onClickTotal: () -> Unit
) {
    val curQty = currentResult.windowItem.quantity.coerceAtLeast(1)
    val cur = order.currencySymbol

    fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

    data class CostColumnData(
        val header: String,
        val winTop: String,
        val winBottom: String,
        val allTop: String,
        val allBottom: String
    )

    val columns = remember(currentResult, allResults, cur) {
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

        listOf(
            CostColumnData("площадь", "${fmt2(winArea)} m2", "", "${fmt2(allArea)} m2", ""),
            CostColumnData("рамы", "${fmt2(winFrameM)} m", "${fmt2(winFrameCost)} $cur", "${fmt2(allFrameM)} m", "${fmt2(allFrameCost)} $cur"),
            CostColumnData("импосты", "${fmt2(winImpM)} m", "${fmt2(winImpCost)} $cur", "${fmt2(allImpM)} m", "${fmt2(allImpCost)} $cur"),
            CostColumnData("створки", "${fmt2(winSashM)} m", "${fmt2(winSashCost)} $cur", "${fmt2(allSashM)} m", "${fmt2(allSashCost)} $cur"),
            CostColumnData("двер.створки", "${fmt2(winDoorM)} m", "${fmt2(winDoorCost)} $cur", "${fmt2(allDoorM)} m", "${fmt2(allDoorCost)} $cur"),
            CostColumnData("сухари/соед.", "$winCornersCnt компл.", "${fmt2(winCornersCost)} $cur", "$allCornersCnt компл.", "${fmt2(allCornersCost)} $cur"),
            CostColumnData("штапики", "${fmt2(winShtM)} m", "${fmt2(winShtCost)} $cur", "${fmt2(allShtM)} m", "${fmt2(allShtCost)} $cur"),
            CostColumnData("уплотнит.", "${fmt2(winRubM)} m", "${fmt2(winRubCost)} $cur", "${fmt2(allRubM)} m", "${fmt2(allRubCost)} $cur"),
            CostColumnData("стекла", "${fmt2(winGlzM2)} m2", "${fmt2(winGlzCost)} $cur", "${fmt2(allGlzM2)} m2", "${fmt2(allGlzCost)} $cur"),
            CostColumnData("сэндв.п.", "${fmt2(winSandM2)} m2", "${fmt2(winSandCost)} $cur", "${fmt2(allSandM2)} m2", "${fmt2(allSandCost)} $cur"),
            CostColumnData("фурнитура", "${fmt2(winHwCost)} $cur", "", "${fmt2(allHwCost)} $cur", ""),
            CostColumnData("разное", "${fmt2(winExtCost)} $cur", "", "${fmt2(allExtCost)} $cur", ""),
            CostColumnData("работы", "${fmt2(winWrkCost)} $cur", "", "${fmt2(allWrkCost)} $cur", "")
        )
    }

    val isManualPrice = currentResult.windowItem.manualUnitPriceOverride != null
    val discPct = if (currentResult.discountPercentApplied > 0.0) currentResult.discountPercentApplied else order.discountPercent
    val winBaseTotal = (currentResult.windowItem.manualUnitPriceOverride ?: currentResult.rawUnitCostBeforeDiscount) * curQty
    val winFinalTotal = winBaseTotal * (1.0 - discPct / 100.0)

    val orderBaseTotal = allResults.sumOf {
        val u = it.windowItem.manualUnitPriceOverride ?: it.rawUnitCostBeforeDiscount
        u * it.windowItem.quantity
    }
    val orderDiscountedTotal = allResults.sumOf {
        val d = if (it.discountPercentApplied > 0.0) it.discountPercentApplied else order.discountPercent
        val u = it.windowItem.manualUnitPriceOverride ?: it.rawUnitCostBeforeDiscount
        u * (1.0 - d / 100.0) * it.windowItem.quantity
    }

    Surface(
        color = Color(0xFF4A4A4A),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая фиксированная колонка меток строк ("окно" / "все окна")
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF3E3E3E))
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Spacer(modifier = Modifier.height(11.dp))
                Text("окно", color = Color(0xFFCCCCCC), fontSize = 9.5.sp, maxLines = 1)
                Text("все окна", color = Color(0xFFCCCCCC), fontSize = 9.5.sp, maxLines = 1)
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
                            .width(78.dp)
                            .fillMaxHeight()
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = col.header,
                            color = Color(0xFFBDBDBD),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(col.winTop, color = Color.White, fontSize = 8.5.sp, lineHeight = 9.5.sp, maxLines = 1)
                            if (col.winBottom.isNotEmpty()) {
                                Text(col.winBottom, color = Color(0xFFE0E0E0), fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(col.allTop, color = Color(0xFFB0BEC5), fontSize = 8.5.sp, lineHeight = 9.5.sp, maxLines = 1)
                            if (col.allBottom.isNotEmpty()) {
                                Text(col.allBottom, color = Color(0xFF90A4AE), fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1)
                            }
                        }
                    }
                    VerticalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxHeight())
                }
            }

            // Правая закреплённая колонка "всего" (как на 00:00–00:54 в видео!)
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF333333))
                    .clickable { onClickTotal() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("всего", color = Color(0xFFE0E0E0), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${fmt2(winBaseTotal)} $cur",
                        color = if (isManualPrice) Color(0xFFFFEB3B) else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                        maxLines = 1
                    )
                    if (discPct > 0.0) {
                        Text(
                            text = "${fmt2(winFinalTotal)} $cur",
                            color = Color(0xFF80CBC4),
                            fontSize = 8.5.sp,
                            lineHeight = 9.5.sp,
                            maxLines = 1
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${fmt2(orderBaseTotal)} $cur",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 10.sp,
                        maxLines = 1
                    )
                    if (orderDiscountedTotal < orderBaseTotal - 0.01) {
                        Text(
                            text = "${fmt2(orderDiscountedTotal)} $cur",
                            color = Color(0xFF80CBC4),
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

@Composable
private fun LeftMenuButton(
    title: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(text = title, color = Color.White, fontSize = 14.sp)
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
    val scroll = rememberScrollState()
    val financialSchemes = remember { DefaultCatalogs.defaultFinancialSchemes() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Верхний заголовок шторки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MetroBlue)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "настройки окна №${windowIndex + 1}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
            }
        }

        // Прокручиваемое содержимое шторки
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. КОЛИЧЕСТВО (- | 1 | +) как на 00:31 видео
            DrawerGrayHeader("количество")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MetroBlue, RoundedCornerShape(2.dp))
                        .clickable {
                            if (windowItem.quantity > 1) {
                                onUpdateWindow(windowItem.copy(quantity = windowItem.quantity - 1))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .border(1.dp, Color.LightGray, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${windowItem.quantity}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MetroBlue, RoundedCornerShape(2.dp))
                        .clickable {
                            onUpdateWindow(windowItem.copy(quantity = windowItem.quantity + 1))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 2. ЗАМЕТКА (например "белое. открывание наружу") как на 00:35 видео
            DrawerGrayHeader("заметка")
            OutlinedTextField(
                value = windowItem.note,
                onValueChange = { onUpdateWindow(windowItem.copy(note = it)) },
                placeholder = { Text("Например: белое. открывание наружу", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 3. СВОЯ ЦЕНА (жёлтая полоса, открывающая калькулятор — как на 00:50 видео!)
            DrawerGrayHeader("своя цена")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFFFFEB3B), RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0xFFFBC02D), RoundedCornerShape(2.dp))
                    .clickable { onOpenManualPriceNumpad() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val manualVal = windowItem.manualUnitPriceOverride ?: 0.0
                Text(
                    text = "${String.format(Locale.US, "%.2f", manualVal)} ${order.currencySymbol}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF222222)
                )
            }

            // Кнопка пакетного применения настроек ко всем окнам заказа
            if (totalWindowsCount > 1) {
                Button(
                    onClick = {
                        onApplyToAllWindows(true, true, true, true, true, true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "ПРИМЕНИТЬ НАСТРОЙКИ КО ВСЕМ ОКНАМ ($totalWindowsCount ШТ.)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. ФИНАНСОВАЯ СХЕМА (скидки / дилерская цена)
            DrawerGrayHeader(
                title = "финансовая схема",
                actionText = if (totalWindowsCount > 1) "Ко всем" else null,
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
                title = "оконная система",
                actionText = if (totalWindowsCount > 1) "Ко всем" else null,
                onAction = { onApplyToAllWindows(true, false, false, false, false, false) }
            )
            DrawerRadioOptionRow(
                label = "не учитывать",
                selected = windowItem.profileSystemId == "none",
                onClick = { onUpdateWindow(windowItem.copy(profileSystemId = "none")) }
            )
            profileSystems.forEach { sys ->
                val selected = sys.id == windowItem.profileSystemId
                DrawerRadioOptionRow(
                    label = sys.name,
                    subtitle = "рама ${sys.priceFramePerM} • створка ${sys.priceSashPerM} • дверь ${sys.priceDoorSashPerM} ${order.currencySymbol}/м",
                    selected = selected,
                    onClick = {
                        val defaultShtId = sys.shtapikVariants.firstOrNull()?.id ?: ""
                        onUpdateWindow(windowItem.copy(profileSystemId = sys.id, selectedShtapikId = defaultShtId))
                    }
                )
            }

            // 5.1. ВЫБОР ШТАПИКА ВЫБРАННОЙ ПРОФИЛЬНОЙ СИСТЕМЫ (например, 18 мм или 22 мм)
            val currentSysForShtapik = profileSystems.find { it.id == windowItem.profileSystemId }
            if (currentSysForShtapik != null && currentSysForShtapik.shtapikVariants.isNotEmpty()) {
                DrawerGrayHeader(
                    title = "штапик системы (${currentSysForShtapik.name})",
                    actionText = if (totalWindowsCount > 1) "Ко всем" else null,
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
                        label = "${sht.name} (под пакет ${sht.glazingThicknessSpMm} мм)",
                        subtitle = "ширина H1=${fmtMm(sht.heightH1Mm)} мм • толщина H2=${fmtMm(sht.widthH2Mm)} мм • ${sht.pricePerM} ${order.currencySymbol}/м",
                        selected = isShtSelected,
                        onClick = {
                            onUpdateWindow(windowItem.copy(selectedShtapikId = sht.id))
                        }
                    )
                }
            }

            // 6. СТЕКЛОПАКЕТЫ + ПЕРЕКЛЮЧАТЕЛЬ ОКРУГЛЕНИЯ ДО 0 / 5 ММ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF8C8C8C))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "стеклопакеты",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (totalWindowsCount > 1) {
                        Text(
                            text = "[Ко всем]",
                            color = Color(0xFFFFEB3B),
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
                        text = "Округл. 0/5 мм",
                        color = Color.White,
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
                            checkedTrackColor = MetroBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF555555)
                        )
                    )
                }
            }
            glazingCatalog.forEach { glz ->
                val selected = glz.id == windowItem.glazingId
                DrawerRadioOptionRow(
                    label = glz.name,
                    subtitle = if (glz.id == "none") null else "${glz.pricePerM2} ${order.currencySymbol}/m2",
                    selected = selected,
                    onClick = { onUpdateWindow(windowItem.copy(glazingId = glz.id)) }
                )
            }

            // 7. ЦВЕТ
            DrawerGrayHeader(
                title = "цвет",
                actionText = if (totalWindowsCount > 1) "Ко всем" else null,
                onAction = { onApplyToAllWindows(false, false, true, false, false, false) }
            )
            colorCatalog.forEach { clr ->
                val selected = clr.id == windowItem.colorId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateWindow(windowItem.copy(colorId = clr.id)) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onUpdateWindow(windowItem.copy(colorId = clr.id)) },
                        colors = RadioButtonDefaults.colors(selectedColor = MetroBlue)
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(clr.hexColor), RoundedCornerShape(3.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = clr.name, fontSize = 13.5.sp, color = Color(0xFF212121))
                        if (clr.priceMarkupPercent > 0.0) {
                            Text(
                                text = "+${clr.priceMarkupPercent.roundToInt()}%",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 8. ФУРНИТУРА
            DrawerGrayHeader(
                title = "фурнитура",
                actionText = if (totalWindowsCount > 1) "Ко всем" else null,
                onAction = { onApplyToAllWindows(false, false, false, true, false, false) }
            )
            hardwareBrands.forEach { hw ->
                val selected = windowItem.hardwareBrandId == hw.id
                DrawerRadioOptionRow(
                    label = hw.name,
                    subtitle = if (hw.id == "hw_none") null else "+${hw.priceMultiplierOrAddon.roundToInt()} ${order.currencySymbol}",
                    selected = selected,
                    onClick = { onUpdateWindow(windowItem.copy(hardwareBrandId = hw.id)) }
                )
            }

            // 9. РАЗНОЕ (чекбоксы из Прайса)
            DrawerGrayHeader(
                title = "разное",
                actionText = if (totalWindowsCount > 1) "Ко всем" else null,
                onAction = { onApplyToAllWindows(false, false, false, false, false, true) }
            )
            extraAccessories.forEach { item ->
                val checked = windowItem.selectedExtraIds.contains(item.id)
                DrawerCheckboxOptionRow(
                    label = item.name,
                    subtitle = "${item.price} ${order.currencySymbol}/${item.rateType.labelRu}",
                    checked = checked,
                    onToggle = {
                        val next = if (checked) windowItem.selectedExtraIds - item.id else windowItem.selectedExtraIds + item.id
                        onUpdateWindow(windowItem.copy(selectedExtraIds = next))
                    }
                )
            }

            // 10. РАБОТЫ (чекбоксы из Прайса)
            DrawerGrayHeader(
                title = "работы",
                actionText = if (totalWindowsCount > 1) "Ко всем" else null,
                onAction = { onApplyToAllWindows(false, false, false, false, false, true) }
            )
            workServices.forEach { item ->
                val checked = windowItem.selectedWorkIds.contains(item.id)
                DrawerCheckboxOptionRow(
                    label = item.name,
                    subtitle = "${item.price} ${order.currencySymbol}/${item.rateType.labelRu}",
                    checked = checked,
                    onToggle = {
                        val next = if (checked) windowItem.selectedWorkIds - item.id else windowItem.selectedWorkIds + item.id
                        onUpdateWindow(windowItem.copy(selectedWorkIds = next))
                    }
                )
            }

            // Общий НДС заказа
            DrawerGrayHeader("налог НДС и скидка заказа")
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
                    label = { Text("Общая скидка (%)", fontSize = 10.sp) },
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
                    label = { Text("НДС (%)", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        // Нижняя зелёная кнопка "ОК" во всю ширину шторки (как на 00:31 - 01:12 в видео!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF8BC34A))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ОК",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DrawerGrayHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE0E0E0))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = Color(0xFF424242),
            fontWeight = FontWeight.Medium
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                fontSize = 11.sp,
                color = MetroDarkBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAction() }
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = Color(0xFF212121)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = Color.Gray
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MetroBlue)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                color = Color(0xFF212121)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = Color.Gray
                )
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = MetroBlue)
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
    var orderNumber by remember { mutableStateOf(order.orderNumber) }
    var customerName by remember { mutableStateOf(order.customerName) }
    var customerPhone by remember { mutableStateOf(order.customerPhone) }
    var address by remember { mutableStateOf(order.address) }
    var comment by remember { mutableStateOf(order.orderNote) }
    var status by remember { mutableStateOf(order.status) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, MetroBlue, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Реквизиты заказа и клиента", fontSize = 18.sp, color = MetroBlue, fontWeight = FontWeight.Bold)
                Text(
                    "Шаблонный номер назначается автоматически. Вы можете заполнить данные клиента в любой момент:",
                    fontSize = 11.5.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = orderNumber,
                    onValueChange = { orderNumber = it },
                    label = { Text("Номер / название заказа") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("ФИО клиента / Организация") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("Телефон") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес доставки / монтажа") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Примечание к заказу") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Статус заказа:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrderStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.titleRu, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ОТМЕНА", color = Color.White)
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
                                    status = status
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("СОХРАНИТЬ", color = Color.White)
                    }
                }
            }
        }
    }
}

// =========================================================================================
// ПРЕДПРОСМОТР РЕАЛЬНОГО PDF-ДОКУМЕНТА ЧЕРЕЗ PdfRenderer + МОДАЛЬНОЕ ОКНО "ОТПРАВИТЬ / ОТКРЫТЬ / ЗАКРЫТЬ" (01:19 В ВИДЕО!)
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
            color = Color(0xFFCCCCCC)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Верхний переключатель 3 режимов PDF-отчётов
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF263238))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClientPdfReportMode.entries.forEach { m ->
                        val selected = m == mode
                        Button(
                            onClick = { onSwitchMode(m) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MetroBlue else Color(0xFF455A64)
                            ),
                            shape = RoundedCornerShape(3.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = m.titleRu,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Область предпросмотра реальных страниц сгенерированного PDF
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(renderedPages) { bmp ->
                        Surface(
                            color = Color.White,
                            shadowElevation = 6.dp,
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Страница PDF",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Нижние кнопки ЗАКРЫТЬ | СОХРАНИТЬ PDF (как на 01:18 в видео!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFECEFF1))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("ЗАКРЫТЬ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showSavedPdfActionModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("СОХРАНИТЬ PDF", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Модальное окно после нажатия "СОХРАНИТЬ PDF": ОТПРАВИТЬ PDF | ОТКРЫТЬ PDF | ЗАКРЫТЬ (как на 01:19 в видео!)
            if (showSavedPdfActionModal) {
                Dialog(onDismissRequest = { showSavedPdfActionModal = false }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .border(2.dp, MetroBlue, RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Документ сохранён в файл:",
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = generatedPdfFile.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        showSavedPdfActionModal = false
                                        onSharePdfFile(generatedPdfFile, mode.titleRu)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                    shape = RoundedCornerShape(2.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        "ОТПРАВИТЬ PDF",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Button(
                                    onClick = {
                                        showSavedPdfActionModal = false
                                        onOpenPdfFile(generatedPdfFile, mode.titleRu)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7CB342)),
                                    shape = RoundedCornerShape(2.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        "ОТКРЫТЬ PDF",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Button(
                                    onClick = { showSavedPdfActionModal = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                                    shape = RoundedCornerShape(2.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        "ЗАКРЫТЬ",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
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
    onUpdateOrders: (List<CustomerOrder>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showIcons by remember { mutableStateOf(true) }

    // Заказы всегда отображаются в порядке от нового к старому (новый наверху), без ненужной сортировки A-Z
    val filtered = remember(orders, searchQuery) {
        orders.filter {
            searchQuery.isBlank() ||
                it.orderNumber.contains(searchQuery, ignoreCase = true) ||
                it.customerName.contains(searchQuery, ignoreCase = true) ||
                it.customerPhone.contains(searchQuery, ignoreCase = true) ||
                it.address.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        UnifiedTopAppBar(
            title = "заказы (${orders.size})",
            onBack = onBack
        ) {
            Surface(
                color = if (showIcons) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clickable { showIcons = !showIcons }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("ICONS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Surface(
                color = MetroDarkBlue,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .clickable { onNewOrder() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Поиск по № заказа, клиенту, телефону или адресу...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { ord ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(4.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOrder(ord.id) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ord.orderNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MetroDarkBlue)
                                Text(
                                    text = "Клиент: ${ord.customerName.ifBlank { "не указан" }} • Тел.: ${ord.customerPhone.ifBlank { "—" }} • Адрес: ${ord.address.ifBlank { "—" }}",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = "Дата: ${ord.createdDate} • Конструкций: ${ord.windows.sumOf { it.quantity }} шт. • Статус: ${ord.status.titleRu}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            if (orders.size > 1) {
                                IconButton(
                                    onClick = {
                                        onUpdateOrders(orders.filterNot { it.id == ord.id })
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFD32F2F))
                                }
                            }
                        }

                        if (showIcons && ord.windows.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
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
                                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                                            .padding(4.dp)
                                    ) {
                                        WindowMiniThumbnail(
                                            topology = w.topology,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Text(
                                            "#${idx + 1} (${w.topology.totalWidthMm.roundToInt()}×${w.topology.totalHeightMm.roundToInt()})",
                                            fontSize = 9.sp,
                                            color = Color.DarkGray
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

    Column(modifier = Modifier.fillMaxSize()) {
        UnifiedTopAppBar(
            title = "профильные системы",
            onBack = onBack
        ) {
            Surface(
                color = MetroDarkBlue,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .clickable { createNewProfileSystem() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("+ Добавить систему", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (savedBannerText != null) {
            Surface(
                color = Color(0xFF2E7D32),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { savedBannerText = null }
            ) {
                Text(
                    text = savedBannerText!!,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

            // Угловые сухари (комплекты на 4 угла рамы, створки, двери и на 1 импост)
            var cleatFrameSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorFrame.toString()) }
            var cleatImpostSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceImpostConnector.toString()) }
            var cleatSashSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorSash.toString()) }
            var cleatDoorSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorDoorSash.toString()) }

            fun saveCurrentSystem() {
                val primaryShtapik = shtapikList.firstOrNull()
                val updatedSys = activeSys.copy(
                    name = name.ifBlank { activeSys.name },
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
                    shtapikVariants = shtapikList,
                    shtapikWidthMm = primaryShtapik?.widthH2Mm ?: activeSys.shtapikWidthMm,
                    priceShtapikPerM = primaryShtapik?.pricePerM ?: activeSys.priceShtapikPerM,
                    // Угловые сухари (комплекты)
                    priceCornerConnectorFrame = cleatFrameSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorFrame,
                    priceImpostConnector = cleatImpostSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceImpostConnector,
                    priceCornerConnectorSash = cleatSashSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorSash,
                    priceCornerConnectorDoorSash = cleatDoorSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorDoorSash
                )
                onSaveProfiles(profileSystems.map { if (it.id == updatedSys.id) updatedSys else it })
                savedBannerText = "Профильная система «${updatedSys.name}» сохранена!"
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
                Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Список профильных систем (${profileSystems.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MetroDarkBlue
                            )
                            Button(
                                onClick = { createNewProfileSystem() },
                                colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("+ Добавить профильную систему", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        profileSystems.forEach { sys ->
                            val isSelected = sys.id == activeSys.id
                            Surface(
                                color = if (isSelected) Color(0xFFE1F5FE) else Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MetroBlue else Color(0xFFCFD8DC),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedId = sys.id }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                color = if (sys.materialType == ProfileMaterialType.ALUMINUM) Color(0xFF455A64) else Color(0xFF00838F),
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = sys.materialType.shortBadge,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = sys.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                        }
                                        Text(
                                            text = "Рама A1=${fmtMm(sys.frameWidthMm)} мм (${sys.priceFramePerM} $currencySymbol/м) • Импост B1=${fmtMm(sys.impostWidthMm)} мм • Створка C1=${fmtMm(sys.sashWidthMm)} мм • Зазор СП=${fmtMm(sys.glazingClearanceTotalMm)} мм",
                                            fontSize = 11.sp,
                                            color = Color(0xFF546E7A)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = { selectedId = sys.id },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                if (isSelected) "✎ Выбрана" else "✎ Редакт.",
                                                color = MetroDarkBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
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
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Удалить систему",
                                                    tint = Color(0xFFD32F2F),
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
                Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Редактирование системы: «$name»",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MetroDarkBlue
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название профильной системы") },
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = stockBarLen,
                                onValueChange = { stockBarLen = it },
                                label = { Text("Длина хлыста профиля (мм)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = weldAllow,
                                onValueChange = { weldAllow = it },
                                label = { Text("Припуск на сварку угла (мм)") },
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
                            color = Color(0xFFE0F7FA),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF80DEEA), RoundedCornerShape(6.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = glzClearanceTotal,
                                    onValueChange = { glzClearanceTotal = it },
                                    label = { Text("Зазор на стеклопакет (отнимается от проёма, напр. 10 мм или 14 мм)") },
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
                                    color = Color(0xFF006064),
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
                    color = Color(0xFF455A64),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                ProfileAccordionSection.entries.forEach { section ->
                    val isExp = expandedSection == section
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Заголовок элемента аккордеона
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isExp) Color(0xFFE1F5FE) else Color.White)
                                    .clickable {
                                        expandedSection = if (isExp) null else section
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = if (section == ProfileAccordionSection.CLEATS_SETS) Color(0xFF00695C) else MetroBlue,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(32.dp)
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
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = section.subtitleRu,
                                            fontSize = 11.sp,
                                            color = Color(0xFF607D8B)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isExp) "▲ Свернуть" else "▼ Раскрыть",
                                    color = MetroDarkBlue,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isExp) {
                                HorizontalDivider(color = Color(0xFFCFD8DC))
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
                                                color = Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Габариты двух конструкций при угловом соединении задаются по готовым размерам замерного листа. Для угловика в расчётах сметы используется стоимость за 1 погонный метр:",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF475569),
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
                                                color = Color(0xFFF0F9FF),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(6.dp))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = "Формула подбора штапика: СП = Посадочное место (A4=${fmtMm(seatMm)} мм) − Ширина штапика (H2) − Резина (${fmtMm(rubMm)} мм)",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0369A1)
                                                    )
                                                    Text(
                                                        text = if (matType == ProfileMaterialType.ALUMINUM) {
                                                            "Правило распила штапиков для АЛЮМИНИЯ (90°/90°):\n• Горизонтальные штапики = Ширина проёма\n• Вертикальные штапики = Высота проёма − 2 × H1 (две ширины горизонтального штапика)"
                                                        } else {
                                                            "Правило распила штапиков для ПВХ (45°/45°):\n• Горизонтальные = Ширина проёма | Вертикальные = Высота проёма"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF0284C7),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    OutlinedTextField(
                                                        value = rubberAllowMm,
                                                        onValueChange = { rubberAllowMm = it },
                                                        label = { Text("Суммарная толщина резины (упорная + клиновая, обычно 4+4 = 8 мм)") },
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
                                                    color = MetroDarkBlue
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("+ Добавить штапик", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            shtapikList.forEachIndexed { idx, item ->
                                                Surface(
                                                    color = Color(0xFFF8FAFC),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(6.dp))
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
                                                                color = Color(0xFF1E293B)
                                                            )
                                                            if (shtapikList.size > 1) {
                                                                IconButton(
                                                                    onClick = {
                                                                        shtapikList = shtapikList.filterIndexed { i, _ -> i != idx }
                                                                    },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Delete,
                                                                        contentDescription = "Удалить штапик",
                                                                        tint = Color(0xFFD32F2F),
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
                                                color = Color(0xFFE0F2F1),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color(0xFF80CBC4), RoundedCornerShape(6.dp))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "Автоматический расчёт комплектов сухарей в конструкции:",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp,
                                                        color = Color(0xFF004D40)
                                                    )
                                                    Text(
                                                        text = "• 1 рама = 1 комплект сухарей рамы (на 4 угла)\n" +
                                                            "• 1 импост = 1 комплект сухарей импоста (на 2 торца)\n" +
                                                            "• 1 створка = 1 комплект створочных сухарей (на 4 угла)\n" +
                                                            "• 1 дверь = 1 комплект дверных сухарей (на 4 угла)",
                                                        fontSize = 11.5.sp,
                                                        color = Color(0xFF00695C)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        "СОХРАНИТЬ ПРОФИЛЬНУЮ СИСТЕМУ «${name.uppercase()}»",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
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
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(id = drawableResId),
                contentDescription = caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Text(
                text = caption,
                fontSize = 11.5.sp,
                color = Color(0xFF475569),
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
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Color(0xFF0277BD),
                shape = RoundedCornerShape(5.dp),
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
                color = Color(0xFF1E293B),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(unitText, fontSize = 10.5.sp) },
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
    val ov = parseMmOrNull(value) ?: 6f
    val addBothSides = ov * 2f
    val sampleW = 800f + addBothSides
    val sampleH = 1000f + addBothSides
    Surface(
        color = Color(0xFFFFF8E1),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFFFCC80), RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
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
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "При нахлёсте ${fmtMm(ov)} мм с каждой стороны (+${fmtMm(addBothSides)} мм к проёму): для проёма 800×1000 мм заготовки в раскрой будут ${fmtMm(sampleW)}×${fmtMm(sampleH)} мм",
                        fontSize = 11.sp,
                        color = Color(0xFF6D4C41)
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("мм") },
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
    Surface(
        color = Color(0xFFE8F5E9),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = Color(0xFF1B5E20),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Цена") },
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
    COLORS("Цвета"),
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
    var activeTab by remember { mutableStateOf(PriceCatalogTab.PROFILES_AND_CLEATS) }
    var selectedSysId by remember { mutableStateOf(profileSystems.firstOrNull()?.id ?: "") }
    val activeSys = profileSystems.find { it.id == selectedSysId } ?: profileSystems.firstOrNull()
    var savedToast by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        UnifiedTopAppBar(
            title = "прайсы ($currencySymbol)",
            onBack = onBack
        )

        // Вкладки разделов прайса
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF263238))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PriceCatalogTab.entries.forEach { tab ->
                val sel = activeTab == tab
                Button(
                    onClick = { activeTab = tab },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sel) MetroBlue else Color(0xFF455A64)
                    ),
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(tab.titleRu, color = Color.White, fontSize = 11.5.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (savedToast != null) {
            Surface(
                color = Color(0xFF2E7D32),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { savedToast = null }
            ) {
                Text(
                    text = savedToast!!,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        when (activeTab) {
            PriceCatalogTab.PROFILES_AND_CLEATS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profileSystems.forEach { sys ->
                        FilterChip(
                            selected = sys.id == activeSys?.id,
                            onClick = { selectedSysId = sys.id },
                            label = { Text(sys.name) }
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
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("1. Погонные метры профилей и уплотнителя ($currencySymbol/м)", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pFrame,
                                        onValueChange = { pFrame = it },
                                        label = { Text("Рама ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pImpost,
                                        onValueChange = { pImpost = it },
                                        label = { Text("Импост ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pDoorSash,
                                        onValueChange = { pDoorSash = it },
                                        label = { Text("Створка двери ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pRubber,
                                        onValueChange = { pRubber = it },
                                        label = { Text("Уплотнитель ($currencySymbol/м)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("2. Угловые сухари (комплекты на 4 угла / на 1 импост, $currencySymbol/компл.)", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Text(
                                    "1 рама = 1 комплект сухарей рамы • 1 импост = 1 комплект сухарей импоста • 1 створка = 1 комплект створочных сухарей • 1 дверь = 1 комплект дверных сухарей:",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pCleatFrame,
                                        onValueChange = { pCleatFrame = it },
                                        label = { Text("Комплект сухарей рамы ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pCleatSash,
                                        onValueChange = { pCleatSash = it },
                                        label = { Text("Комплект сухарей створки ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pCleatDoorSash,
                                        onValueChange = { pCleatDoorSash = it },
                                        label = { Text("Комплект сухарей двери ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pImpConn,
                                        onValueChange = { pImpConn = it },
                                        label = { Text("Комплект сухарей импоста ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("3. Базовые механизмы открывания ($currencySymbol/компл.)", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pHwTurn,
                                        onValueChange = { pHwTurn = it },
                                        label = { Text("Поворотный ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pHwTiltTurn,
                                        onValueChange = { pHwTiltTurn = it },
                                        label = { Text("Пов.-откидной ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pHwDoor,
                                        onValueChange = { pHwDoor = it },
                                        label = { Text("Дверной ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                    colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("СОХРАНИТЬ ПРАЙС СИСТЕМЫ И СУХАРЕЙ", color = Color.White, fontWeight = FontWeight.Bold)
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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Добавить стеклопакет / заполнение в прайс", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newGlzName,
                                        onValueChange = { newGlzName = it },
                                        label = { Text("Формула / название (напр. 4-16-4i)") },
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newGlzPrice,
                                        onValueChange = { newGlzPrice = it },
                                        label = { Text("Цена ($currencySymbol/m2)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        Text("Непрозрачное (сэндвич)", fontSize = 12.sp)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue)
                                    ) {
                                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.Bold)
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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Добавить марку фурнитуры в прайс", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = newHwName,
                                        onValueChange = { newHwName = it },
                                        label = { Text("Название (напр. Siegenia Titan)") },
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newHwPrice,
                                        onValueChange = { newHwPrice = it },
                                        label = { Text("Надбавка ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue)
                                    ) {
                                        Text("+", color = Color.White, fontWeight = FontWeight.Bold)
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

            PriceCatalogTab.COLORS -> {
                val colorPalette = remember {
                    listOf(
                        0xFFD32F2FL to "Красный (RAL 3020)",
                        0xFF9B1B30L to "Рубиновый (RAL 3003)",
                        0xFF383E42L to "Антрацит (RAL 7016)",
                        0xFF474A51L to "Графит (RAL 7024)",
                        0xFF442F29L to "Шоколад (RAL 8017)",
                        0xFF3D3635L to "Тёмно-коричневый (RAL 8019)",
                        0xFF8B5A2BL to "Золотой дуб",
                        0xFF4A2E1BL to "Тёмный / Морёный дуб",
                        0xFF6B2D26L to "Махагон",
                        0xFF6E472BL to "Орех",
                        0xFF1F2124L to "Чёрный муар (RAL 9005)",
                        0xFFA5A9B0L to "Серебристый (RAL 9006)",
                        0xFF1E5945L to "Зелёный мох (RAL 6005)",
                        0xFF154889L to "Синий (RAL 5005)",
                        0xFFE6D2B5L to "Слоновая кость (RAL 1015)",
                        0xFFF5F7FAL to "Белый (RAL 9016)"
                    )
                }
                var newClrName by remember { mutableStateOf("") }
                var newClrMarkup by remember { mutableStateOf("15") }
                var selectedNewHex by remember { mutableLongStateOf(colorPalette.first().first) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Добавить цвет / ламинацию в прайс", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Text(
                                    "Выберите оттенок для отображения на 2D-чертеже и в PDF:",
                                    fontSize = 11.5.sp,
                                    color = Color.DarkGray
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    colorPalette.forEach { (hex, presetLabel) ->
                                        val isPicked = selectedNewHex == hex
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clickable {
                                                    selectedNewHex = hex
                                                    if (newClrName.isBlank()) {
                                                        newClrName = presetLabel
                                                    }
                                                }
                                                .padding(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isPicked) 34.dp else 28.dp)
                                                    .background(Color(hex), RoundedCornerShape(6.dp))
                                                    .border(
                                                        width = if (isPicked) 3.dp else 1.dp,
                                                        color = if (isPicked) MetroBlue else Color.Gray,
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(selectedNewHex), RoundedCornerShape(6.dp))
                                            .border(1.5.dp, Color.DarkGray, RoundedCornerShape(6.dp))
                                    )
                                    OutlinedTextField(
                                        value = newClrName,
                                        onValueChange = { newClrName = it },
                                        label = { Text("Цвет (напр. Красный / Антрацит)") },
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newClrMarkup,
                                        onValueChange = { newClrMarkup = it },
                                        label = { Text("Наценка (%)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(0.8f),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            if (newClrName.isNotBlank()) {
                                                val p = newClrMarkup.replace(',', '.').toDoubleOrNull() ?: 10.0
                                                val item = ColorCatalogItem(
                                                    id = "clr_${UUID.randomUUID().toString().take(6)}",
                                                    name = newClrName.trim(),
                                                    hexColor = selectedNewHex,
                                                    priceMarkupPercent = p
                                                )
                                                onSaveColors(colorCatalog + item)
                                                newClrName = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue)
                                    ) {
                                        Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    items(colorCatalog.filter { it.id != "none" }, key = { it.id }) { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            EditablePriceRowCard(
                                title = item.name,
                                subtitle = "Наценка к профилю (%) • Нажмите на квадрат ниже для смены оттенка",
                                initialPrice = item.priceMarkupPercent,
                                currencySymbol = "%",
                                onSavePrice = { newP ->
                                    onSaveColors(colorCatalog.map { if (it.id == item.id) it.copy(priceMarkupPercent = newP) else it })
                                    savedToast = "Обновлена наценка цвета «${item.name}»"
                                },
                                onDelete = { onSaveColors(colorCatalog.filterNot { it.id == item.id }) }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Оттенок:", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                colorPalette.forEach { (hex, _) ->
                                    val isCurrent = item.hexColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(if (isCurrent) 24.dp else 20.dp)
                                            .background(Color(hex), RoundedCornerShape(4.dp))
                                            .border(
                                                width = if (isCurrent) 2.5.dp else 1.dp,
                                                color = if (isCurrent) MetroBlue else Color.Gray,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                onSaveColors(
                                                    colorCatalog.map {
                                                        if (it.id == item.id) it.copy(hexColor = hex) else it
                                                    }
                                                )
                                                savedToast = "Обновлён оттенок для «${item.name}»"
                                            }
                                    )
                                }
                            }
                        }
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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Добавить комплектующее в раздел «Разное»", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newExtName,
                                        onValueChange = { newExtName = it },
                                        label = { Text("Название (напр. доводчик дверной)") },
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newExtPrice,
                                        onValueChange = { newExtPrice = it },
                                        label = { Text("Цена ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                            FilterChip(
                                                selected = newExtRate == rt,
                                                onClick = { newExtRate = rt },
                                                label = { Text("за ${rt.labelRu}", fontSize = 11.sp) }
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
                                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue)
                                    ) {
                                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.Bold)
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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Добавить услугу в раздел «Работы»", fontWeight = FontWeight.Bold, color = MetroDarkBlue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newWrkName,
                                        onValueChange = { newWrkName = it },
                                        label = { Text("Название работы") },
                                        modifier = Modifier.weight(1.4f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newWrkPrice,
                                        onValueChange = { newWrkPrice = it },
                                        label = { Text("Цена ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                            FilterChip(
                                                selected = newWrkRate == rt,
                                                onClick = { newWrkRate = rt },
                                                label = { Text("за ${rt.labelRu}", fontSize = 11.sp) }
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
                                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue)
                                    ) {
                                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.Bold)
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
    var priceText by remember(initialPrice) { mutableStateOf(initialPrice.toString()) }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF212121))
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(currencySymbol, fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.85f),
                singleLine = true
            )
            Button(
                onClick = {
                    val p = priceText.replace(',', '.').toDoubleOrNull() ?: initialPrice
                    onSavePrice(p)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text("ОК", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFD32F2F))
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
    var selectedFolderSysId by remember { mutableStateOf(profileSystems.firstOrNull()?.id ?: "") }
    val activeSys = profileSystems.find { it.id == selectedFolderSysId } ?: profileSystems.firstOrNull()

    var selectedRole by remember { mutableStateOf(ProfileRole.FRAME) }
    var barLengthMm by remember(activeSys?.id) {
        mutableStateOf((activeSys?.stockBarLengthMm?.roundToInt() ?: 6000).toString())
    }
    var barQty by remember { mutableStateOf("20") }
    var isFullShipmentBar by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        UnifiedTopAppBar(
            title = "склад материала и остатков",
            onBack = onBack,
            backgroundColor = Color(0xFF00838F)
        )

        Text(
            "Папки профильных систем:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profileSystems.forEach { sys ->
                val isSelected = sys.id == activeSys?.id
                val sysItems = stockBars.filter { it.profileSystemId == sys.id }
                val fullCnt = sysItems.filter { it.isFullStockBar }.sumOf { it.quantity }
                val remCnt = sysItems.filter { !it.isFullStockBar }.sumOf { it.quantity }

                Surface(
                    color = if (isSelected) Color(0xFF00838F) else Color.White,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .clickable { selectedFolderSysId = sys.id }
                        .border(1.dp, Color(0xFF00838F), RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📁", fontSize = 20.sp)
                        Column {
                            Text(
                                text = sys.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isSelected) Color.White else Color.Black
                            )
                            Text(
                                text = "Хлыстов: $fullCnt шт. | Остатков: $remCnt шт.",
                                fontSize = 10.5.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.Gray
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
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Приход материала / внесение в папку «${activeSys.name}»",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF006064)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = isFullShipmentBar,
                                    onClick = {
                                        isFullShipmentBar = true
                                        barLengthMm = activeSys.stockBarLengthMm.roundToInt().toString()
                                    },
                                    label = { Text("Целые хлысты (5500..8000 мм)") }
                                )
                                FilterChip(
                                    selected = !isFullShipmentBar,
                                    onClick = {
                                        isFullShipmentBar = false
                                        barLengthMm = "1800"
                                    },
                                    label = { Text("Деловой остаток после раскроя") }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ProfileRole.entries.forEach { role ->
                                    FilterChip(
                                        selected = selectedRole == role,
                                        onClick = { selectedRole = role },
                                        label = { Text(role.titleRu, fontSize = 11.5.sp) }
                                    )
                                }
                            }

                            if (isFullShipmentBar) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Быстрая длина:", fontSize = 11.sp, color = Color.Gray)
                                    listOf(5500, 6000, 6500, 7000, 8000).forEach { stdLen ->
                                        AssistChip(
                                            onClick = { barLengthMm = stdLen.toString() },
                                            label = { Text("${stdLen}мм", fontSize = 10.5.sp) }
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
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = barQty,
                                    onValueChange = { barQty = it },
                                    label = { Text("Количество (шт.)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        isFullStockBar = isFullShipmentBar
                                    )
                                    onSaveStock(listOf(newEntry) + stockBars)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isFullShipmentBar) "+ ОПРИХОДОВАТЬ ЦЕЛЫЕ ПРОФИЛИ НА СКЛАД"
                                    else "+ ДОБАВИТЬ ДЕЛОВОЙ ОСТАТОК В ПАПКУ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
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
                        color = Color(0xFF263238)
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
                        color = Color(0xFF263238)
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
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.role.titleRu} — длина ${item.lengthMm} мм (${String.format(Locale.US, "%.2f", item.lengthMm / 1000.0)} м)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Text(
                    text = if (item.isFullStockBar) "Целый складской хлыст" else "Деловой остаток после раскроя",
                    fontSize = 11.sp,
                    color = if (item.isFullStockBar) Color(0xFF00838F) else Color(0xFF2E7D32)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onChangeQty(-1) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(30.dp)
                ) {
                    Text("−")
                }
                Text("${item.quantity} шт.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedButton(
                    onClick = { onChangeQty(1) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(30.dp)
                ) {
                    Text("+")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F))
                }
            }
        }
    }
}

// =========================================================================================
// 7. ДОСКА СТАТУСОВ ЗАКАЗОВ (МЕНЕДЖЕР АКТИВНЫХ ЗАКАЗОВ)
// =========================================================================================
@Composable
private fun OrderStatusManagerScreen(
    orders: List<CustomerOrder>,
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onUpdateOrders: (List<CustomerOrder>) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        UnifiedTopAppBar(
            title = "статусы заказов",
            onBack = onBack,
            backgroundColor = Color(0xFF00838F)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orders, key = { it.id }) { ord ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).clickable { onOpenOrder(ord.id) }) {
                                Text(ord.orderNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MetroDarkBlue)
                                Text(
                                    "Клиент: ${ord.customerName.ifBlank { "не указан" }} • Конструкций: ${ord.windows.sumOf { it.quantity }} шт.",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }
                            TextButton(onClick = { onOpenOrder(ord.id) }) {
                                Text("Открыть чертёж →", color = MetroBlue)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OrderStatus.entries.forEach { st ->
                                FilterChip(
                                    selected = ord.status == st,
                                    onClick = {
                                        onUpdateOrders(
                                            orders.map { if (it.id == ord.id) ord.copy(status = st) else it }
                                        )
                                    },
                                    label = { Text(st.titleRu, fontSize = 11.5.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================================
// 8. НАСТРОЙКИ ИНТЕРФЕЙСА И РЕКВИЗИТОВ КОМПАНИИ ДЛЯ PDF
// =========================================================================================
@Composable
private fun UiSettingsScreen(
    settings: AppUiSettings,
    onBack: () -> Unit,
    onSaveSettings: (AppUiSettings) -> Unit
) {
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var currencySymbol by remember { mutableStateOf(settings.currencySymbol) }
    var companyName by remember { mutableStateOf(settings.companyName) }
    var companyPhone by remember { mutableStateOf(settings.companyPhone) }

    Column(modifier = Modifier.fillMaxSize()) {
        UnifiedTopAppBar(
            title = "настройки и реквизиты PDF",
            onBack = onBack,
            backgroundColor = MetroDarkBlue
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Тема оформления:", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { tm ->
                                FilterChip(
                                    selected = themeMode == tm,
                                    onClick = { themeMode = tm },
                                    label = { Text(tm.titleRu) }
                                )
                            }
                        }
                    }

                    Text("Валюта расчётов:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("доллар", "₾", "₽", "€").forEach { sym ->
                            FilterChip(
                                selected = currencySymbol == sym,
                                onClick = { currencySymbol = sym },
                                label = { Text(sym) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Название компании в шапке PDF-отчётов") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = companyPhone,
                        onValueChange = { companyPhone = it },
                        label = { Text("Телефон / Контакты в шапке PDF") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            onSaveSettings(
                                settings.copy(
                                    themeMode = themeMode,
                                    currencySymbol = currencySymbol,
                                    companyName = companyName,
                                    companyPhone = companyPhone
                                )
                            )
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetroBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("СОХРАНИТЬ НАСТРОЙКИ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================================
// 9. МОДУЛЬ "ЗАДАНИЕ В ЦЕХ И 1D РАСКРОЙ ПРОФИЛЯ (С УЧЁТОМ СКЛАДА И ДЕЛОВЫХ ОСТАТКОВ)"
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

    Column(modifier = Modifier.fillMaxSize()) {
        UnifiedTopAppBar(
            title = "Задание в цех и 1D-раскрой",
            onBack = onBackToCad
        ) {
            Surface(
                color = Color(0xFF00838F),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .clickable {
                        val newRemnantBars = procurementSummaries.flatMap { sum ->
                            sum.newBusinessRemnantsMm.map { remLen ->
                                StockRemnantBar(
                                    id = UUID.randomUUID().toString(),
                                    profileSystemId = sum.profileSystemId,
                                    role = sum.role,
                                    lengthMm = remLen,
                                    quantity = 1,
                                    isFullStockBar = false
                                )
                            }
                        }
                        onSaveStockRemnants(stockRemnants + newRemnantBars)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("Остатки → Склад", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Surface(
                color = MetroDarkBlue,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
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
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("PDF В ЦЕХ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "1. Потребность в профиле (Рама → Импост → Створка → Дверь → Штапик):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF263238)
                )
            }

            items(procurementSummaries) { sum ->
                Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${sum.role.titleRu.uppercase()} — ${sum.profileSystemName} (хлыст ${sum.stockBarLengthMm} мм)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MetroDarkBlue
                        )
                        Text(
                            "Новых хлыстов: ${sum.newFullBarsToBuyCount} шт. • Из остатков склада: ${sum.usedRemnantBarsCount} шт. • Деталей: ${sum.totalCutPiecesCount} шт. (${String.format(Locale.US, "%.2f", sum.totalNetLengthM)} м) • Отход: ${String.format(Locale.US, "%.1f", sum.wastePercent)}%",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                        if (sum.newBusinessRemnantsMm.isNotEmpty() && sum.role != ProfileRole.SHTAPIK) {
                            Text(
                                "Деловые остатки (≥400 мм): ${sum.newBusinessRemnantsMm.joinToString(", ") { "${it}мм" }}",
                                fontSize = 11.5.sp,
                                color = Color(0xFF2E7D32),
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
                    color = Color(0xFF263238)
                )
            }

            items(barAllocations.filter { it.role != ProfileRole.SHTAPIK }) { bar ->
                Surface(color = Color.White, shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${bar.role.titleRu.uppercase()} (${bar.profileSystemName}) — Хлыст #${bar.barNumber}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MetroDarkBlue
                            )
                            Text(
                                if (bar.isFromRemnantStock) "СО СКЛАДА ОСТАТКОВ (${bar.sourceLengthMm} мм)"
                                else "ЦЕЛЫЙ ХЛЫСТ (${bar.sourceLengthMm} мм)",
                                fontSize = 11.sp,
                                color = if (bar.isFromRemnantStock) Color(0xFF2E7D32) else MetroDarkBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
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
                                        color = Color.DarkGray,
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
