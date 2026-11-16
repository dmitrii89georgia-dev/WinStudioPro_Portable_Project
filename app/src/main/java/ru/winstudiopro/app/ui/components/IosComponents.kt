package ru.winstudiopro.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.cad.WindowMiniThumbnail
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.i18n.normalizeCurrencyDisplay
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette

/**
 * Единая верхняя панель навигации в стиле iOS для второстепенных экранов.
 */
@Composable
fun IosTopAppBar(
    title: String,
    onBack: () -> Unit,
    accentColor: Color = LocalWinStudioPalette.current.accentBlue,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.cardSurface.copy(alpha = 0.96f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
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
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("top_bar_back_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(p.segmentedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = s.backDesc,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    color = p.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
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
        HorizontalDivider(color = p.divider, thickness = 0.7.dp)
    }
}

/**
 * Скруглённая карточка в стиле iOS (мягкая тень и тонкая контурная обводка).
 */
@Composable
fun IosGroupedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalWinStudioPalette.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = p.cardSurface,
        shape = shape,
        shadowElevation = if (p.isDark) 0.dp else 2.dp,
        modifier = modifier
            .border(0.8.dp, p.floatingBorder, shape)
            .clip(shape)
            .let { mod -> if (onClick != null) mod.clickable(onClick = onClick) else mod }
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Сегментированный переключатель в стиле iPhone (UISegmentedControl).
 */
@Composable
fun <T> IosSegmentedControl(
    items: List<Pair<T, String>>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segment"
) {
    val p = LocalWinStudioPalette.current
    val selectedIndex = items.indexOfFirst { it.first == selectedItem }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.segmentedBg)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / items.size.coerceAtLeast(1)
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "segmentThumb"
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(if (p.isDark) 0.dp else 2.dp, RoundedCornerShape(9.dp))
                .clip(RoundedCornerShape(9.dp))
                .background(p.segmentedThumb)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { idx, (value, label) ->
                val isSelected = idx == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) p.textPrimary else p.textSecondary,
                    label = "segText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(value) }
                        .testTag("${testTagPrefix}_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Главный экран в стиле iPhone: мягкие карточки, скругления, быстрый переход в Настройки.
 */
@Composable
fun IosHomeDashboard(
    orders: List<CustomerOrder>,
    profileSystems: List<ProfileSystemSpec>,
    stockRemnants: List<StockRemnantBar>,
    uiSettings: AppUiSettings,
    onNewOrderClick: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenOrdersList: () -> Unit,
    onOpenProfileConstructor: () -> Unit,
    onOpenPrices: () -> Unit,
    onOpenWarehouse: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current
    val scrollState = rememberScrollState()

    val lastOrder = orders.getOrNull(0)
    val prevOrder = orders.getOrNull(1)
    val fullBarsCount = stockRemnants.filter { it.isFullStockBar }.sumOf { it.quantity }
    val remnantsCount = stockRemnants.filter { !it.isFullStockBar }.sumOf { it.quantity }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Верхняя шапка в стиле iOS Large Title + кнопка Настроек ⚙
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WinStudio Pro",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = p.textPrimary,
                modifier = Modifier.weight(1f)
            )

            // Кнопка быстрого перехода в Настройки
            Surface(
                color = p.cardSurface,
                shape = CircleShape,
                shadowElevation = if (p.isDark) 0.dp else 3.dp,
                modifier = Modifier
                    .size(48.dp)
                    .border(0.8.dp, p.floatingBorder, CircleShape)
                    .clip(CircleShape)
                    .clickable { onOpenSettings() }
                    .testTag("dashboard_settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = s.settingsTitle,
                        tint = p.accentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Крупная акцентная карточка «Новый заказ» в стиле iPhone
        val heroShape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
                .shadow(if (p.isDark) 0.dp else 6.dp, heroShape)
                .clip(heroShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(p.accentBlue, p.accentTeal)
                    )
                )
                .clickable { onNewOrderClick() }
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("dashboard_new_order_card")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.newOrderTitle,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = s.newOrderSubtitle,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = s.newOrderTitle,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Последние два заказа с живыми миниатюрами
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IosRecentOrderCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                headerLabel = s.lastOrderLabel,
                order = lastOrder,
                onClick = {
                    if (lastOrder != null) onOpenOrder(lastOrder.id) else onNewOrderClick()
                }
            )
            IosRecentOrderCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                headerLabel = s.prevOrderLabel,
                order = prevOrder,
                onClick = {
                    if (prevOrder != null) onOpenOrder(prevOrder.id)
                }
            )
        }

        // Сетка разделов (2 колонки, мягкие карточки с iOS squircle-иконками)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IosDashboardTileCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Rounded.ListAlt,
                iconBg = p.accentBlue,
                title = s.ordersTitle,
                subtitle = String.format(s.ordersCountFormat, orders.size),
                testTag = "dashboard_orders_card",
                onClick = onOpenOrdersList
            )
            IosDashboardTileCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Architecture,
                iconBg = p.accentIndigo,
                title = s.profileSystemsTitle,
                subtitle = String.format(s.profileSystemsSubFormat, profileSystems.size),
                testTag = "dashboard_profiles_card",
                onClick = onOpenProfileConstructor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IosDashboardTileCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Payments,
                iconBg = p.accentGreen,
                title = s.pricesTitle,
                subtitle = s.pricesSubtitle,
                testTag = "dashboard_prices_card",
                onClick = onOpenPrices
            )
            IosDashboardTileCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Inventory2,
                iconBg = p.accentTeal,
                title = s.warehouseTitle,
                subtitle = String.format(s.warehouseSubFormat, fullBarsCount, remnantsCount),
                testTag = "dashboard_warehouse_card",
                onClick = onOpenWarehouse
            )
        }

        val themeChip = if (uiSettings.themeMode == AppThemeMode.DARK) s.themeDark else s.themeLight
        val curDisp = normalizeCurrencyDisplay(uiSettings.currencySymbol)
        IosGroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_settings_card"),
            onClick = onOpenSettings,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF636366)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = s.settingsTitle,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.settingsTitle,
                        color = p.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(
                            s.settingsSubtitleFormat,
                            themeChip,
                            uiSettings.language.code,
                            curDisp
                        ),
                        color = p.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = p.textSecondary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun IosDashboardTileCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val p = LocalWinStudioPalette.current
    IosGroupedCard(
        modifier = modifier
            .height(114.dp)
            .testTag(testTag),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = p.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = p.textSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun IosRecentOrderCard(
    modifier: Modifier = Modifier,
    headerLabel: String,
    order: CustomerOrder?,
    onClick: () -> Unit
) {
    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    IosGroupedCard(
        modifier = modifier,
        onClick = if (order != null) onClick else null,
        contentPadding = PaddingValues(12.dp)
    ) {
        if (order == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = headerLabel,
                    color = p.textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.noOrderLabel,
                        color = p.textSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerLabel,
                        color = p.accentBlue,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = p.accentTeal.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${order.windows.sumOf { it.quantity }}",
                            color = p.accentTeal,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstWin = order.windows.firstOrNull()
                    if (firstWin != null) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.canvasBg)
                                .border(0.6.dp, p.floatingBorder, RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            WindowMiniThumbnail(
                                topology = firstWin.topology,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = order.orderNumber,
                            color = p.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (order.customerName.isNotBlank()) {
                            Text(
                                text = order.customerName,
                                color = p.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (s.language == AppLanguage.EN) order.status.name.lowercase().replace('_', ' ') else order.status.titleRu,
                            color = p.accentOrange,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Экран настроек в стиле iPhone (iOS Inset Grouped Settings):
 * - Светлая / Тёмная тема (мгновенное переключение)
 * - Язык интерфейса RU / EN (мгновенное переключение)
 * - Валюта по умолчанию ($, €, ₾, ₽)
 * - Реквизиты компании для PDF
 */
@Composable
fun IosSettingsScreen(
    settings: AppUiSettings,
    onBack: () -> Unit,
    onUpdateSettingsLive: (AppUiSettings) -> Unit,
    onApplyCurrencyToAllOrders: (String) -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    var companyName by remember(settings.companyName) { mutableStateOf(settings.companyName) }
    var companyPhone by remember(settings.companyPhone) { mutableStateOf(settings.companyPhone) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val activeCurrency = normalizeCurrencyDisplay(settings.currencySymbol)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
    ) {
        IosTopAppBar(
            title = s.settingsScreenTitle,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(visible = statusBanner != null) {
                Surface(
                    color = p.accentGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.8.dp, p.accentGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { statusBanner = null }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = p.accentGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusBanner ?: "",
                            color = p.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 1. ОФОРМЛЕНИЕ (ТЕМА)
            SettingsSectionHeader(s.sectionAppearance)
            IosGroupedCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.DarkMode, p.accentIndigo)
                    Text(
                        text = s.themeLabel,
                        color = p.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                IosSegmentedControl(
                    items = listOf(
                        AppThemeMode.LIGHT to s.themeLight,
                        AppThemeMode.DARK to s.themeDark
                    ),
                    selectedItem = settings.themeMode,
                    onSelect = { newTheme ->
                        onUpdateSettingsLive(settings.copy(themeMode = newTheme))
                    },
                    testTagPrefix = "settings_theme"
                )
            }

            // 2. ЯЗЫК ПРИЛОЖЕНИЯ
            SettingsSectionHeader(s.sectionLanguage)
            IosGroupedCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Language, p.accentBlue)
                    Text(
                        text = s.languageLabel,
                        color = p.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                IosSegmentedControl(
                    items = listOf(
                        AppLanguage.RU to "Русский",
                        AppLanguage.EN to "English"
                    ),
                    selectedItem = settings.language,
                    onSelect = { newLang ->
                        onUpdateSettingsLive(settings.copy(language = newLang))
                    },
                    testTagPrefix = "settings_language"
                )
            }

            // 3. ВАЛЮТА ПО УМОЛЧАНИЮ
            SettingsSectionHeader(s.sectionCurrency)
            IosGroupedCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.CurrencyExchange, p.accentGreen)
                    Text(
                        text = s.currencyLabel,
                        color = p.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                IosSegmentedControl(
                    items = listOf(
                        "$" to "$ (USD)",
                        "€" to "€ (EUR)",
                        "₾" to "₾ (GEL)",
                        "₽" to "₽ (RUB)"
                    ),
                    selectedItem = activeCurrency,
                    onSelect = { newCur ->
                        onUpdateSettingsLive(settings.copy(currencySymbol = newCur))
                    },
                    testTagPrefix = "settings_currency"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = s.currencyHint,
                    color = p.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = p.divider, thickness = 0.7.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onApplyCurrencyToAllOrders(activeCurrency)
                            statusBanner = "${s.currencyAppliedToast} ($activeCurrency)"
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = s.applyCurrencyToExistingOrders,
                        color = p.accentBlue,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = p.accentBlue
                    )
                }
            }

            // 4. РЕКВИЗИТЫ КОМПАНИИ ДЛЯ PDF
            SettingsSectionHeader(s.sectionCompanyPdf)
            IosGroupedCard(contentPadding = PaddingValues(14.dp)) {
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text(s.companyNameLabel) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = companyPhone,
                    onValueChange = { companyPhone = it },
                    label = { Text(s.companyPhoneLabel) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        onUpdateSettingsLive(
                            settings.copy(
                                companyName = companyName.trim().ifBlank { "WinStudio Pro" },
                                companyPhone = companyPhone.trim()
                            )
                        )
                        statusBanner = s.settingsSavedToast
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = p.accentBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_save_button")
                ) {
                    Text(
                        text = s.saveSettingsBtn,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 5. РЕЗЕРВНОЕ КОПИРОВАНИЕ И ДАННЫЕ (iOS Grouped List)
            SettingsSectionHeader(s.sectionBackupData)
            IosGroupedCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onExportBackup() }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("settings_export_backup_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Backup, p.accentIndigo)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.backupExportTitle,
                            color = p.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = s.backupExportHint,
                            color = p.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = p.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = p.divider, thickness = 0.7.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImportBackup() }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("settings_import_backup_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Restore, p.accentOrange)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.backupImportTitle,
                            color = p.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = s.backupImportHint,
                            color = p.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = p.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val p = LocalWinStudioPalette.current
    Text(
        text = title,
        color = p.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}

@Composable
private fun SettingsIconBadge(icon: ImageVector, bgColor: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}
