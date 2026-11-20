package ru.winstudiopro.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.domain.calculator.CuttingPlanManualHelper
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.ClientPdfReportMode
import ru.winstudiopro.app.pdf.PdfReportGenerator
import ru.winstudiopro.app.ui.cad.*
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.i18n.normalizeCurrencyDisplay
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

// =========================================================================================
// РАБОЧИЙ 2D-РЕДАКТОР КОНСТРУКЦИЙ (С ДИНАМИЧЕСКИМ СКРОЛЛОМ МЕТРОВ/СУХАРЕЙ/СУММ И ШТОРКОЙ НАСТРОЕК)
// =========================================================================================
@Composable
internal fun CadEditorWorkspaceScreen(
    order: CustomerOrder,
    activeWindowIndex: Int,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    colorCatalog: List<ColorCatalogItem>,
    sillsCatalog: List<SillCatalogItem>,
    hardwareBrandsCatalog: List<HardwareBrandItem>,
    extraAccessoriesCatalog: List<ExtraAccessoryItem>,
    workServicesCatalog: List<WorkServiceItem>,
    assemblyPricingCatalog: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
    orderExtraServicesCatalog: List<OrderExtraServiceItem> = DefaultCatalogs.defaultOrderExtraServices(),
    finSchemeSettings: FinSchemeSettings = FinSchemeSettings(),
    stockRemnants: List<StockRemnantBar> = emptyList(),
    uiSettings: AppUiSettings,
    onSelectWindowIndex: (Int) -> Unit,
    onUpdateOrder: (CustomerOrder) -> Unit,
    onBackToHome: () -> Unit,
    onOpenWorkshopCutting: () -> Unit,
    onOpenGlazingOrder: () -> Unit = {},
    onOpenHardwareOrder: () -> Unit = {},
    onOpenPdfFile: (File, String) -> Unit,
    onSharePdfFile: (File, String) -> Unit
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

    // Расчёт всех конструкций текущего заказа в реальном времени (включая сборку, сухари, разное и монтаж из прайса)
    val allWindowResults = remember(
        order,
        profileSystems,
        glazingCatalog,
        colorCatalog,
        sillsCatalog,
        extraAccessoriesCatalog,
        workServicesCatalog,
        hardwareBrandsCatalog,
        assemblyPricingCatalog
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
                assemblyPricingCatalog = assemblyPricingCatalog,
                windowIndexInOrder = idx + 1
            )
        }
    }
    val currentCalcResult = allWindowResults.getOrNull(activeWindowIndex)
    val orderFinancialSummary = remember(
        order,
        allWindowResults,
        assemblyPricingCatalog,
        orderExtraServicesCatalog,
        finSchemeSettings
    ) {
        WindowBomAndCostEngine.calculateOrderFinancialSummary(
            order = order,
            windowResults = allWindowResults,
            assemblyPricingCatalog = assemblyPricingCatalog,
            orderExtraServicesCatalog = orderExtraServicesCatalog,
            finSchemeSettings = finSchemeSettings
        )
    }

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
        onUpdateOrder(order.copy(windows = updatedWindows, customCuttingAllocations = null))
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
        onUpdateOrder(order.copy(windows = updatedWindows, customCuttingAllocations = null))
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
        onUpdateOrder(order.copy(windows = updatedWindows, customCuttingAllocations = null))
    }

    // Применение настроек к ВСЕМ конструкциям заказа (с сохранением их индивидуальной геометрии)
    fun applySettingsToAllWindowsInOrder(
        applyProfile: Boolean = true,
        applyGlazing: Boolean = true,
        applyColors: Boolean = true,
        applyHardware: Boolean = true,
        applyFinancials: Boolean = true,
        applyExtrasAndWorks: Boolean = true,
        applyAssemblyScheme: Boolean = false
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
                selectedWorkIds = if (applyExtrasAndWorks) currentWindow.selectedWorkIds else win.selectedWorkIds,
                assemblySchemeOverride = if (applyAssemblyScheme) currentWindow.assemblySchemeOverride else win.assemblySchemeOverride
            )
        }
        onUpdateOrder(order.copy(windows = updatedWindows, customCuttingAllocations = null))
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
        onUpdateOrder(order.copy(windows = updated, customCuttingAllocations = null))
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
        onUpdateOrder(order.copy(windows = updated, customCuttingAllocations = null))
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
                                            onUpdateOrder(order.copy(windows = updated, customCuttingAllocations = null))
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
                    orderFinancialSummary = orderFinancialSummary,
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

                        LeftMenuButton(
                            title = strings.pdfInstallationDrawingsItem,
                            icon = Icons.Rounded.Architecture,
                            accentTint = palette.accentBlue,
                            modifier = Modifier.testTag("cad_menu_installation_drawings")
                        ) {
                            showLeftBlueMenu = false
                            val file = PdfReportGenerator.generateInstallationDrawingsPdf(
                                context = context,
                                order = order,
                                windowResults = allWindowResults
                            )
                            onOpenPdfFile(file, strings.pdfInstallationDrawingsItem)
                        }

                        HorizontalDivider(
                            color = palette.divider,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LeftMenuButton(
                            title = strings.pdfGlazingItem,
                            icon = Icons.Rounded.GridView,
                            accentTint = palette.accentTeal,
                            modifier = Modifier.testTag("cad_menu_glazing_order")
                        ) {
                            showLeftBlueMenu = false
                            onOpenGlazingOrder()
                        }
                        LeftMenuButton(
                            title = strings.pdfHardwareItem,
                            icon = Icons.Rounded.Build,
                            accentTint = palette.accentOrange,
                            modifier = Modifier.testTag("cad_menu_hardware_order")
                        ) {
                            showLeftBlueMenu = false
                            onOpenHardwareOrder()
                        }
                        LeftMenuButton(
                            title = strings.workshopTaskItem,
                            icon = Icons.Rounded.Assignment,
                            accentTint = palette.accentGreen,
                            modifier = Modifier.testTag("cad_menu_workshop_task")
                        ) {
                            showLeftBlueMenu = false
                            val usableRemnants = stockRemnants.filter { !it.isFullStockBar }
                            val autoResult = WindowBomAndCostEngine.optimizeOrderProfileCutting(
                                windowResults = allWindowResults,
                                stockRemnants = usableRemnants,
                                profileSystems = profileSystems
                            )
                            val activeAllocations = order.customCuttingAllocations ?: autoResult.first
                            val activeSummaries = if (order.customCuttingAllocations != null) {
                                CuttingPlanManualHelper.rebuildProcurementSummaries(
                                    allocations = activeAllocations,
                                    baseAutoSummaries = autoResult.second,
                                    profileSystems = profileSystems
                                )
                            } else {
                                autoResult.second
                            }
                            val file = PdfReportGenerator.generateCuttingAndProcurementPdf(
                                context = context,
                                order = order,
                                allocations = activeAllocations,
                                procurement = activeSummaries,
                                windowResults = allWindowResults
                            )
                            onOpenPdfFile(file, strings.workshopTaskItem)
                        }
                        LeftMenuButton(
                            title = strings.cuttingItem,
                            icon = Icons.Rounded.ContentCut,
                            accentTint = palette.accentGreen,
                            modifier = Modifier.testTag("cad_menu_cutting")
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
                        hardwareBrands = hardwareBrandsCatalog,
                        extraAccessories = extraAccessoriesCatalog,
                        workServices = workServicesCatalog,
                        assemblyPricingCatalog = assemblyPricingCatalog,
                        orderExtraServicesCatalog = orderExtraServicesCatalog,
                        finSchemeSettings = finSchemeSettings,
                        orderFinancialSummary = orderFinancialSummary,
                        onUpdateWindow = { updatedWin -> updateCurrentWindow { updatedWin } },
                        onUpdateOrder = { updatedOrder -> onUpdateOrder(updatedOrder) },
                        onOpenManualPriceNumpad = { showManualPriceNumpad = true },
                        onUpdateOrderDiscount = { newDiscount, newVat ->
                            onUpdateOrder(order.copy(discountPercent = newDiscount, taxVatPercent = newVat))
                        },
                        onApplyToAllWindows = { applyProf, applyGlz, applyClr, applyHw, applyFin, applyExtWrk, applyAsm ->
                            applySettingsToAllWindowsInOrder(
                                applyProfile = applyProf,
                                applyGlazing = applyGlz,
                                applyColors = applyClr,
                                applyHardware = applyHw,
                                applyFinancials = applyFin,
                                applyExtrasAndWorks = applyExtWrk,
                                applyAssemblyScheme = applyAsm
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
            assemblyPricingCatalog = assemblyPricingCatalog,
            orderExtraServicesCatalog = orderExtraServicesCatalog,
            finSchemeSettings = finSchemeSettings,
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
    orderFinancialSummary: OrderFinancialSummary,
    onClickTotal: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val curQty = currentResult.windowItem.quantity.coerceAtLeast(1)
    val cur = normalizeCurrencyDisplay(order.currencySymbol)

    fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

    data class CostColumnData(
        val tagKey: String,
        val header: String,
        val winTop: String,
        val winBottom: String,
        val allTop: String,
        val allBottom: String,
        val emphasizeValue: Boolean = false
    )

    val columns = remember(currentResult, allResults, orderFinancialSummary, cur, strings) {
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

        val winInstallCost = currentResult.costInstallation * curQty
        val allInstallCost = orderFinancialSummary.totalInstallationCost

        val winAssemblyCost = currentResult.costAssembly * curQty
        val allAssemblyCost = orderFinancialSummary.totalAssemblyWithFloorAndOps

        val allOrderServicesCost = orderFinancialSummary.totalOrderServicesCost

        val winPaintCost = currentResult.costCustomPainting
        val allPaintCost = allResults.sumOf { it.costCustomPainting }

        val winPrimeCost = currentResult.unitPrimeCost * curQty + winPaintCost
        val allPrimeCost = orderFinancialSummary.orderPrimeCost

        buildList {
            add(CostColumnData("area", strings.colArea, "${fmt2(winArea)} m2", "", "${fmt2(allArea)} m2", ""))
            add(CostColumnData("frames", strings.colFrames, "${fmt2(winFrameM)} m", "${fmt2(winFrameCost)} $cur", "${fmt2(allFrameM)} m", "${fmt2(allFrameCost)} $cur"))
            add(CostColumnData("imposts", strings.colImposts, "${fmt2(winImpM)} m", "${fmt2(winImpCost)} $cur", "${fmt2(allImpM)} m", "${fmt2(allImpCost)} $cur"))
            add(CostColumnData("sashes", strings.colSashes, "${fmt2(winSashM)} m", "${fmt2(winSashCost)} $cur", "${fmt2(allSashM)} m", "${fmt2(allSashCost)} $cur"))
            add(CostColumnData("doors", strings.colDoorSashes, "${fmt2(winDoorM)} m", "${fmt2(winDoorCost)} $cur", "${fmt2(allDoorM)} m", "${fmt2(allDoorCost)} $cur"))
            add(CostColumnData("cleats", strings.colCleats, "$winCornersCnt ${strings.setsShort}", "${fmt2(winCornersCost)} $cur", "$allCornersCnt ${strings.setsShort}", "${fmt2(allCornersCost)} $cur"))
            add(CostColumnData("shtapik", strings.colShtapik, "${fmt2(winShtM)} m", "${fmt2(winShtCost)} $cur", "${fmt2(allShtM)} m", "${fmt2(allShtCost)} $cur"))
            add(CostColumnData("rubber", strings.colRubber, "${fmt2(winRubM)} m", "${fmt2(winRubCost)} $cur", "${fmt2(allRubM)} m", "${fmt2(allRubCost)} $cur"))
            add(CostColumnData("glass", strings.colGlass, "${fmt2(winGlzM2)} m2", "${fmt2(winGlzCost)} $cur", "${fmt2(allGlzM2)} m2", "${fmt2(allGlzCost)} $cur"))
            add(CostColumnData("sandwich", strings.colSandwich, "${fmt2(winSandM2)} m2", "${fmt2(winSandCost)} $cur", "${fmt2(allSandM2)} m2", "${fmt2(allSandCost)} $cur"))
            add(CostColumnData("hardware", strings.colHardware, "${fmt2(winHwCost)} $cur", "", "${fmt2(allHwCost)} $cur", ""))
            add(CostColumnData("extras", strings.colExtras, "${fmt2(winExtCost)} $cur", "", "${fmt2(allExtCost)} $cur", ""))
            add(CostColumnData("works", strings.colWorks, "${fmt2(winInstallCost)} $cur", "", "${fmt2(allInstallCost)} $cur", ""))
            add(CostColumnData("assembly", strings.colAssembly, "${fmt2(winAssemblyCost)} $cur", "", "${fmt2(allAssemblyCost)} $cur", ""))
            add(CostColumnData("order_services", strings.colOrderServices, "—", "", "${fmt2(allOrderServicesCost)} $cur", ""))
            if (allPaintCost > 0.0 || winPaintCost > 0.0) {
                add(CostColumnData("painting", strings.colPainting, "${fmt2(winPaintCost)} $cur", "", "${fmt2(allPaintCost)} $cur", ""))
            }
            add(CostColumnData("prime_cost", strings.colPrimeCost, "${fmt2(winPrimeCost)} $cur", "", "${fmt2(allPrimeCost)} $cur", "", emphasizeValue = true))
        }
    }

    val isManualPrice = currentResult.windowItem.manualUnitPriceOverride != null
    val winBreakdown = orderFinancialSummary.windowBreakdownFor(currentResult.windowItem.id)
    val winSaleAfterDiscount = winBreakdown?.lineDiscountedSale
        ?: ((currentResult.windowItem.manualUnitPriceOverride ?: currentResult.rawUnitCostBeforeDiscount) *
            curQty * (1.0 - (if (currentResult.discountPercentApplied > 0.0) currentResult.discountPercentApplied else order.discountPercent) / 100.0) +
            currentResult.costCustomPainting)
    val winSaleWithVat = winSaleAfterDiscount * (1.0 + order.taxVatPercent / 100.0)

    val orderSaleAfterDiscount = orderFinancialSummary.orderSaleAfterDiscountBeforeVat
    val orderTotalWithVat = orderFinancialSummary.totalWithVat
    val isBelowPrime = orderFinancialSummary.isBelowPrimeCost

    Surface(
        color = palette.costBarBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag("cad_cost_bar")
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
                        .width(64.dp)
                        .fillMaxHeight()
                        .background(palette.costBarSideBg)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(11.dp))
                    Text(strings.rowWindow, color = palette.textSecondary, fontSize = 9.5.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                    Text(strings.rowAllWindows, color = palette.textSecondary, fontSize = 9.5.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                }

                // Центральный горизонтальный скролл по всем статьям затрат
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                        .testTag("cad_cost_scroll_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columns.forEach { col ->
                        Column(
                            modifier = Modifier
                                .width(84.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                .testTag("cad_cost_col_${col.tagKey}"),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = col.header,
                                color = if (col.emphasizeValue) palette.accentOrange else palette.textSecondary,
                                fontSize = 9.sp,
                                fontWeight = if (col.emphasizeValue) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = col.winTop,
                                    color = if (col.emphasizeValue) palette.accentOrange else palette.textPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = if (col.emphasizeValue) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = 10.sp,
                                    maxLines = 1
                                )
                                if (col.winBottom.isNotEmpty()) {
                                    Text(col.winBottom, color = palette.textSecondary, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = col.allTop,
                                    color = if (col.emphasizeValue) palette.accentOrange.copy(alpha = 0.9f) else palette.textSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = if (col.emphasizeValue) FontWeight.SemiBold else FontWeight.Normal,
                                    lineHeight = 10.sp,
                                    maxLines = 1
                                )
                                if (col.allBottom.isNotEmpty()) {
                                    Text(col.allBottom, color = palette.textSecondary.copy(alpha = 0.8f), fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1)
                                }
                            }
                        }
                        VerticalDivider(color = palette.divider, modifier = Modifier.fillMaxHeight())
                    }
                }

                // Правая закреплённая колонка "всего" (Продажа / С НДС для окна и всего заказа)
                Column(
                    modifier = Modifier
                        .width(114.dp)
                        .fillMaxHeight()
                        .background(
                            if (isBelowPrime) palette.accentRed.copy(alpha = 0.14f) else palette.costBarSideBg
                        )
                        .clickable { onClickTotal() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("cad_cost_total_box"),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isBelowPrime) {
                            Text(
                                text = "ниже себест.!",
                                color = palette.accentRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("cad_below_prime_badge")
                            )
                        }
                        Text(strings.colTotal, color = palette.accentBlue, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${fmt2(winSaleAfterDiscount)} $cur",
                            color = if (isManualPrice) palette.accentOrange else palette.textPrimary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.5.sp,
                            maxLines = 1
                        )
                        if (order.taxVatPercent > 0.0) {
                            Text(
                                text = "с НДС ${fmt2(winSaleWithVat)} $cur",
                                color = palette.accentGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${fmt2(orderSaleAfterDiscount)} $cur",
                            color = if (isBelowPrime) palette.accentRed else palette.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 10.sp,
                            maxLines = 1
                        )
                        if (order.taxVatPercent > 0.0 || order.manualOrderTotalOverride != null) {
                            Text(
                                text = "с НДС ${fmt2(orderTotalWithVat)} $cur",
                                color = when {
                                    isBelowPrime -> palette.accentRed
                                    order.manualOrderTotalOverride != null -> palette.accentOrange
                                    else -> palette.accentGreen
                                },
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 9.sp,
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
    icon: ImageVector? = null,
    accentTint: Color = LocalWinStudioPalette.current.accentBlue,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Row(
        modifier = modifier
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
// ПРАВАЯ ШТОРКА «НАСТРОЙКИ ОКНА» + ПАКЕТНОЕ ПРИМЕНЕНИЕ
// =========================================================================================
@Composable
private fun RightWindowSettingsDrawerContent(
    order: CustomerOrder,
    windowItem: WindowItem,
    windowIndex: Int,
    totalWindowsCount: Int,
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    hardwareBrands: List<HardwareBrandItem>,
    extraAccessories: List<ExtraAccessoryItem>,
    workServices: List<WorkServiceItem>,
    assemblyPricingCatalog: AssemblyPricingCatalog,
    orderExtraServicesCatalog: List<OrderExtraServiceItem>,
    finSchemeSettings: FinSchemeSettings = FinSchemeSettings(),
    orderFinancialSummary: OrderFinancialSummary,
    onUpdateWindow: (WindowItem) -> Unit,
    onUpdateOrder: (CustomerOrder) -> Unit,
    onOpenManualPriceNumpad: () -> Unit,
    onUpdateOrderDiscount: (Double, Double) -> Unit,
    onApplyToAllWindows: (
        applyProfile: Boolean,
        applyGlazing: Boolean,
        applyColors: Boolean,
        applyHardware: Boolean,
        applyFinancials: Boolean,
        applyExtrasAndWorks: Boolean,
        applyAssemblyScheme: Boolean
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
                        onApplyToAllWindows(true, true, true, true, true, true, true)
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
                onAction = { onApplyToAllWindows(false, false, false, false, true, false, false) }
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
                onAction = { onApplyToAllWindows(true, false, false, false, false, false, false) }
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
                    onAction = { onApplyToAllWindows(true, false, false, false, false, false, false) }
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
                                    onApplyToAllWindows(false, true, false, false, false, false, false)
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
                onAction = { onApplyToAllWindows(false, false, true, false, false, false, false) }
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
                onAction = { onApplyToAllWindows(false, false, false, true, false, false, false) }
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
                onAction = { onApplyToAllWindows(false, false, false, false, false, true, false) }
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

            // 10. МОНТАЖ
            DrawerGrayHeader(
                title = strings.worksLabel,
                actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                onAction = { onApplyToAllWindows(false, false, false, false, false, true, false) }
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

            // 11. СБОРКА В ЦЕХУ (схема на окно + сборка на этаже и доп. операции на весь заказ)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_assembly_section"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerGrayHeader(
                    title = strings.assemblyLabel,
                    actionText = if (totalWindowsCount > 1) strings.applyShort else null,
                    onAction = { onApplyToAllWindows(false, false, false, false, false, false, true) }
                )
                DrawerRadioOptionRow(
                    label = "По умолчанию (${assemblyPricingCatalog.defaultScheme.titleRu})",
                    subtitle = "Активная схема каталога «Прайсы»",
                    selected = windowItem.assemblySchemeOverride == null,
                    modifier = Modifier.testTag("drawer_assembly_scheme_DEFAULT"),
                    onClick = { onUpdateWindow(windowItem.copy(assemblySchemeOverride = null)) }
                )
                DrawerRadioOptionRow(
                    label = AssemblySchemeType.OPERATIONS.titleRu,
                    subtitle = "По операциям в $displayCurrency",
                    selected = windowItem.assemblySchemeOverride == AssemblySchemeType.OPERATIONS,
                    modifier = Modifier.testTag("drawer_assembly_scheme_OPERATIONS"),
                    onClick = { onUpdateWindow(windowItem.copy(assemblySchemeOverride = AssemblySchemeType.OPERATIONS)) }
                )
                DrawerRadioOptionRow(
                    label = AssemblySchemeType.PER_M2.titleRu,
                    subtitle = "${String.format(Locale.US, "%.2f", assemblyPricingCatalog.ratePerM2)} $displayCurrency/м²",
                    selected = windowItem.assemblySchemeOverride == AssemblySchemeType.PER_M2,
                    modifier = Modifier.testTag("drawer_assembly_scheme_PER_M2"),
                    onClick = { onUpdateWindow(windowItem.copy(assemblySchemeOverride = AssemblySchemeType.PER_M2)) }
                )
                DrawerRadioOptionRow(
                    label = AssemblySchemeType.PER_METER.titleRu,
                    subtitle = "${String.format(Locale.US, "%.2f", assemblyPricingCatalog.ratePerMeter)} $displayCurrency/м.п.",
                    selected = windowItem.assemblySchemeOverride == AssemblySchemeType.PER_METER,
                    modifier = Modifier.testTag("drawer_assembly_scheme_PER_METER"),
                    onClick = { onUpdateWindow(windowItem.copy(assemblySchemeOverride = AssemblySchemeType.PER_METER)) }
                )

                Surface(
                    color = palette.secondaryBackground.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${strings.floorAssemblyLabel} (на весь заказ)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        var floorCountText by remember(order.id, order.floorAssemblyCount) {
                            mutableStateOf(if (order.floorAssemblyCount == 0) "" else order.floorAssemblyCount.toString())
                        }
                        OutlinedTextField(
                            value = floorCountText,
                            onValueChange = { raw ->
                                floorCountText = raw.filter { it.isDigit() }
                                val cnt = floorCountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                                onUpdateOrder(order.copy(floorAssemblyCount = cnt))
                            },
                            label = { Text("Кол-во конструкций на этаже", fontSize = 11.sp) },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_floor_assembly_input")
                        )
                        val floorTotal = order.floorAssemblyCount.coerceAtLeast(0) * assemblyPricingCatalog.floorAssemblyRate
                        Text(
                            text = "Ставка: ${String.format(Locale.US, "%.2f", assemblyPricingCatalog.floorAssemblyRate)} $displayCurrency/шт • Итого: ${String.format(Locale.US, "%.2f", floorTotal)} $displayCurrency",
                            fontSize = 10.5.sp,
                            color = palette.textSecondary
                        )

                        var extraOpsText by remember(order.id) {
                            mutableStateOf(if (order.extraAssemblyOpsCost == 0.0) "" else order.extraAssemblyOpsCost.toString().removeSuffix(".0"))
                        }
                        LaunchedEffect(order.extraAssemblyOpsCost) {
                            val parsed = extraOpsText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (kotlin.math.abs(parsed - order.extraAssemblyOpsCost) > 0.001) {
                                extraOpsText = if (order.extraAssemblyOpsCost == 0.0) "" else order.extraAssemblyOpsCost.toString().removeSuffix(".0")
                            }
                        }
                        OutlinedTextField(
                            value = extraOpsText,
                            onValueChange = { raw ->
                                extraOpsText = raw
                                val parsed = raw.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                onUpdateOrder(order.copy(extraAssemblyOpsCost = parsed))
                            },
                            label = { Text("${strings.extraOpsCostLabel} ($displayCurrency)", fontSize = 11.sp) },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_extra_ops_cost_input")
                        )
                        OutlinedTextField(
                            value = order.extraAssemblyOpsNote,
                            onValueChange = { note ->
                                onUpdateOrder(order.copy(extraAssemblyOpsNote = note))
                            },
                            label = { Text(strings.extraOpsNoteLabel, fontSize = 11.sp) },
                            placeholder = { Text("Напр. нестандартный узел / усиление") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_extra_ops_note_input")
                        )
                    }
                }
            }

            // 12. ДОПОЛНИТЕЛЬНЫЕ УСЛУГИ ЗАКАЗА (применяются ко всему заказу)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_order_services_section"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerGrayHeader(title = strings.orderServicesLabel)
                Text(
                    text = "Выбранные услуги прибавляются ко всему заказу. Для любой отмеченной услуги можно задать сумму вручную.",
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                orderExtraServicesCatalog.forEach { service ->
                    val existing = order.selectedOrderServices.find { it.serviceId == service.id }
                    val isChecked = existing != null
                    val effectivePrice = existing?.customPriceOverride ?: service.defaultPrice
                    DrawerCheckboxOptionRow(
                        label = service.name,
                        subtitle = if (existing?.customPriceOverride != null) {
                            "Сумма в заказе: ${String.format(Locale.US, "%.2f", effectivePrice)} $displayCurrency (в прайсе ${String.format(Locale.US, "%.2f", service.defaultPrice)})"
                        } else {
                            "${String.format(Locale.US, "%.2f", service.defaultPrice)} $displayCurrency (на весь заказ)"
                        },
                        checked = isChecked,
                        modifier = Modifier.testTag("drawer_order_service_check_${service.id}"),
                        onToggle = {
                            val nextList = if (isChecked) {
                                order.selectedOrderServices.filterNot { it.serviceId == service.id }
                            } else {
                                order.selectedOrderServices + SelectedOrderService(
                                    serviceId = service.id,
                                    customPriceOverride = null
                                )
                            }
                            onUpdateOrder(order.copy(selectedOrderServices = nextList))
                        }
                    )
                    if (isChecked) {
                        var overrideText by remember(order.id, service.id, isChecked) {
                            mutableStateOf(
                                existing?.customPriceOverride?.toString()?.removeSuffix(".0") ?: ""
                            )
                        }
                        OutlinedTextField(
                            value = overrideText,
                            onValueChange = { raw ->
                                overrideText = raw
                                val parsed = if (raw.isBlank()) {
                                    null
                                } else {
                                    raw.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0)
                                }
                                val nextList = order.selectedOrderServices.map { sel ->
                                    if (sel.serviceId == service.id) {
                                        sel.copy(customPriceOverride = parsed)
                                    } else {
                                        sel
                                    }
                                }
                                onUpdateOrder(order.copy(selectedOrderServices = nextList))
                            },
                            label = { Text("Инд. цена: ${service.name} ($displayCurrency)", fontSize = 10.5.sp) },
                            placeholder = { Text("По прайсу: ${String.format(Locale.US, "%.2f", service.defaultPrice)}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, bottom = 4.dp)
                                .testTag("drawer_order_service_price_${service.id}")
                        )
                    }
                }
            }

            // Общий НДС и скидка заказа
            DrawerGrayHeader(strings.vatAndDiscountLabel)
            if (finSchemeSettings.discountPresets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    finSchemeSettings.discountPresets.forEach { preset ->
                        val isSel = kotlin.math.abs(order.discountPercent - preset.discountPercent) < 0.01
                        FilterChip(
                            selected = isSel,
                            onClick = { onUpdateOrderDiscount(preset.discountPercent, order.taxVatPercent) },
                            label = {
                                Text(
                                    text = if (preset.isDealer) "${preset.name} (${preset.discountPercent.toInt()}%)" else preset.name,
                                    fontSize = 11.sp,
                                    color = if (isSel) Color.White else palette.textPrimary
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (preset.isDealer) palette.accentOrange else palette.accentBlue,
                                containerColor = palette.secondaryBackground
                            ),
                            modifier = Modifier.testTag("drawer_discount_preset_${preset.discountPercent.toInt()}")
                        )
                    }
                }
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = kotlin.math.abs(order.taxVatPercent - 0.0) < 0.01,
                    onClick = { onUpdateOrderDiscount(order.discountPercent, 0.0) },
                    label = { Text("Без НДС 0%", fontSize = 11.sp) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("drawer_vat_0_chip")
                )
                FilterChip(
                    selected = kotlin.math.abs(order.taxVatPercent - 18.0) < 0.01,
                    onClick = { onUpdateOrderDiscount(order.discountPercent, 18.0) },
                    label = { Text("НДС 18%", fontSize = 11.sp) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("drawer_vat_18_chip")
                )
            }
            if (orderFinancialSummary.isBelowPrimeCost) {
                Surface(
                    color = palette.accentRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.accentRed.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drawer_below_prime_warning")
                ) {
                    Text(
                        text = "⚠ Внимание: цена со скидкой (${String.format(Locale.US, "%.2f", orderFinancialSummary.orderSaleAfterDiscountBeforeVat)} $displayCurrency) ниже себестоимости заказа (${String.format(Locale.US, "%.2f", orderFinancialSummary.orderPrimeCost)} $displayCurrency)!",
                        color = palette.accentRed,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Row(
        modifier = modifier
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
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Row(
        modifier = modifier
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

                if (order.stockDeductedForProduction) {
                    Text(
                        text = "✓ Профили, остатки и резина уже списаны со склада в производство",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accentGreen
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
    assemblyPricingCatalog: AssemblyPricingCatalog,
    orderExtraServicesCatalog: List<OrderExtraServiceItem>,
    finSchemeSettings: FinSchemeSettings = FinSchemeSettings(),
    onSwitchMode: (ClientPdfReportMode) -> Unit,
    onDismiss: () -> Unit,
    onOpenPdfFile: (File, String) -> Unit,
    onSharePdfFile: (File, String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current

    // Генерируем реальный PDF-файл выбранного режима и рендерим его страницы через Android PdfRenderer
    val generatedPdfFile = remember(
        order,
        windowResults,
        mode,
        uiSettings,
        extraAccessoriesCatalog,
        workServicesCatalog,
        hardwareBrandsCatalog,
        assemblyPricingCatalog,
        orderExtraServicesCatalog,
        finSchemeSettings
    ) {
        PdfReportGenerator.generateClientOrderPdf(
            context = context,
            order = order,
            windowResults = windowResults,
            mode = mode,
            companyHeader = uiSettings.companyName,
            companyContacts = uiSettings.companyPhone,
            companyAddress = uiSettings.companyAddress,
            extraAccessoriesCatalog = extraAccessoriesCatalog,
            workServicesCatalog = workServicesCatalog,
            hardwareBrandsCatalog = hardwareBrandsCatalog,
            assemblyPricingCatalog = assemblyPricingCatalog,
            orderExtraServicesCatalog = orderExtraServicesCatalog,
            finSchemeSettings = finSchemeSettings
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
