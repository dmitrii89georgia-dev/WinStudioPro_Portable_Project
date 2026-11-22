package ru.winstudiopro.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.winstudiopro.app.data.backup.BackupManager
import ru.winstudiopro.app.data.backup.GoogleDriveBackupClient
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.cad.WindowMiniThumbnail
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.i18n.normalizeCurrencyDisplay
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * Главный экран в стиле iPhone: фиксированная статичная компоновка без прокрутки
 * на портретных телефонах — нижние кнопки всегда видны на экране.
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
    onOpenSettings: () -> Unit,
    onOpenCash: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onQuickIncome: () -> Unit = {},
    onQuickExpense: () -> Unit = {},
    onQuickTransfer: () -> Unit = {}
) {
    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current

    val activeOrders = remember(orders) { orders.filterNot { it.isArchived } }
    val lastOrder = activeOrders.getOrNull(0)
    val prevOrder = activeOrders.getOrNull(1)
    val fullBarsCount = stockRemnants.filter { it.isFullStockBar }.sumOf { it.quantity }
    val remnantsCount = stockRemnants.filter { !it.isFullStockBar }.sumOf { it.quantity }
    val themeChip = if (uiSettings.themeMode == AppThemeMode.DARK) s.themeDark else s.themeLight
    val curDisp = normalizeCurrencyDisplay(uiSettings.currencySymbol)

    @Composable
    fun HeaderBar(compact: Boolean) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WinStudio Pro",
                fontSize = if (compact) 23.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = p.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

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
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun HeroNewOrderCard(modifier: Modifier, compact: Boolean) {
        val heroShape = RoundedCornerShape(20.dp)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .shadow(if (p.isDark) 0.dp else 5.dp, heroShape)
                .clip(heroShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(p.accentBlue, p.accentTeal)
                    )
                )
                .clickable { onNewOrderClick() }
                .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 12.dp)
                .testTag("dashboard_new_order_card")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = s.newOrderTitle,
                        color = Color.White,
                        fontSize = if (compact) 18.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = s.newOrderSubtitle,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = if (compact) 11.5.sp else 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(if (compact) 42.dp else 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = s.newOrderTitle,
                        tint = Color.White,
                        modifier = Modifier.size(if (compact) 26.dp else 30.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun SettingsCompactCard(compact: Boolean) {
        IosGroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 50.dp else 56.dp)
                .testTag("dashboard_settings_card"),
            onClick = onOpenSettings,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 32.dp else 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF636366)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = s.settingsTitle,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = s.settingsTitle,
                        color = p.textPrimary,
                        fontSize = if (compact) 14.sp else 15.sp,
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
                        fontSize = 11.sp,
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

    @Composable
    fun QuickActionsBottomBar() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onQuickIncome,
                colors = ButtonDefaults.buttonColors(containerColor = p.accentGreen),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("dashboard_quick_income_btn")
            ) {
                Text(
                    text = "+ Приход",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onQuickExpense,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("dashboard_quick_expense_btn")
            ) {
                Text(
                    text = "− Расход",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onQuickTransfer,
                colors = ButtonDefaults.buttonColors(containerColor = p.accentBlue),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("dashboard_quick_transfer_btn")
            ) {
                Text(
                    text = "⇄ Перевод",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val useStaticLayout = maxHeight >= 540.dp
        val isCompactHeight = maxHeight < 680.dp
        val gap = if (isCompactHeight) 6.dp else 8.dp
        val outerVertPad = if (isCompactHeight) 8.dp else 10.dp

        if (useStaticLayout) {
            // Полностью статичный, не прокручиваемый главный экран: все плитки и нижние кнопки помещаются целиком
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = outerVertPad),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                HeaderBar(compact = isCompactHeight)

                HeroNewOrderCard(
                    modifier = Modifier.weight(0.84f),
                    compact = isCompactHeight
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.22f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IosDashboardTileCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.AutoMirrored.Rounded.ListAlt,
                        iconBg = p.accentBlue,
                        title = s.ordersTitle,
                        subtitle = String.format(s.ordersCountFormat, orders.size),
                        testTag = "dashboard_orders_card",
                        onClick = onOpenOrdersList
                    )
                    IosDashboardTileCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.Rounded.Architecture,
                        iconBg = p.accentIndigo,
                        title = s.profileSystemsTitle,
                        subtitle = String.format(s.profileSystemsSubFormat, profileSystems.size),
                        testTag = "dashboard_profiles_card",
                        onClick = onOpenProfileConstructor
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IosDashboardTileCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.Rounded.Payments,
                        iconBg = p.accentGreen,
                        title = s.pricesTitle,
                        subtitle = s.pricesSubtitle,
                        testTag = "dashboard_prices_card",
                        onClick = onOpenPrices
                    )
                    IosDashboardTileCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.Rounded.Inventory2,
                        iconBg = p.accentTeal,
                        title = s.warehouseTitle,
                        subtitle = String.format(s.warehouseSubFormat, fullBarsCount, remnantsCount),
                        testTag = "dashboard_warehouse_card",
                        onClick = onOpenWarehouse
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IosDashboardTileCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.Rounded.AccountBalanceWallet,
                        iconBg = Color(0xFF1E88E5),
                        title = s.cashTitle,
                        subtitle = s.cashSubtitle,
                        testTag = "dashboard_cash_card",
                        onClick = onOpenCash
                    )
                    IosDashboardTileCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.Rounded.BarChart,
                        iconBg = Color(0xFF8E24AA),
                        title = s.analyticsTitle,
                        subtitle = s.analyticsSubtitle,
                        testTag = "dashboard_analytics_card",
                        onClick = onOpenAnalytics
                    )
                }

                SettingsCompactCard(compact = isCompactHeight)

                QuickActionsBottomBar()
            }
        } else {
            // Запасной режим для очень низких окон (альбомный режим / split-screen): прокрутка середины при закреплённых нижних кнопках
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeaderBar(compact = true)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroNewOrderCard(
                        modifier = Modifier.height(78.dp),
                        compact = true
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IosDashboardTileCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.AutoMirrored.Rounded.ListAlt,
                            iconBg = p.accentBlue,
                            title = s.ordersTitle,
                            subtitle = String.format(s.ordersCountFormat, orders.size),
                            testTag = "dashboard_orders_card",
                            onClick = onOpenOrdersList
                        )
                        IosDashboardTileCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Rounded.Architecture,
                            iconBg = p.accentIndigo,
                            title = s.profileSystemsTitle,
                            subtitle = String.format(s.profileSystemsSubFormat, profileSystems.size),
                            testTag = "dashboard_profiles_card",
                            onClick = onOpenProfileConstructor
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IosDashboardTileCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Rounded.Payments,
                            iconBg = p.accentGreen,
                            title = s.pricesTitle,
                            subtitle = s.pricesSubtitle,
                            testTag = "dashboard_prices_card",
                            onClick = onOpenPrices
                        )
                        IosDashboardTileCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Rounded.Inventory2,
                            iconBg = p.accentTeal,
                            title = s.warehouseTitle,
                            subtitle = String.format(s.warehouseSubFormat, fullBarsCount, remnantsCount),
                            testTag = "dashboard_warehouse_card",
                            onClick = onOpenWarehouse
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IosDashboardTileCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Rounded.AccountBalanceWallet,
                            iconBg = Color(0xFF1E88E5),
                            title = s.cashTitle,
                            subtitle = s.cashSubtitle,
                            testTag = "dashboard_cash_card",
                            onClick = onOpenCash
                        )
                        IosDashboardTileCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Rounded.BarChart,
                            iconBg = Color(0xFF8E24AA),
                            title = s.analyticsTitle,
                            subtitle = s.analyticsSubtitle,
                            testTag = "dashboard_analytics_card",
                            onClick = onOpenAnalytics
                        )
                    }
                    SettingsCompactCard(compact = true)
                }
                QuickActionsBottomBar()
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
        modifier = modifier.testTag(testTag),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useHorizontal = maxHeight < 76.dp
            if (useHorizontal) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            color = p.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            color = p.textSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            color = p.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            color = p.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
        contentPadding = PaddingValues(10.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 96.dp
            if (order == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = headerLabel,
                        color = p.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                            fontSize = 12.5.sp
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = p.accentTeal.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(7.dp)
                        ) {
                            Text(
                                text = "${order.windows.sumOf { it.quantity }}",
                                color = p.accentTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = if (compact) 4.dp else 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val firstWin = order.windows.firstOrNull()
                        if (firstWin != null) {
                            Box(
                                modifier = Modifier
                                    .size(if (compact) 48.dp else 58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.canvasBg)
                                    .border(0.6.dp, p.floatingBorder, RoundedCornerShape(10.dp))
                                    .padding(3.dp)
                            ) {
                                WindowMiniThumbnail(
                                    topology = firstWin.topology,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = order.orderNumber,
                                color = p.textPrimary,
                                fontSize = if (compact) 11.5.sp else 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (compact) 1 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (order.customerName.isNotBlank()) {
                                Text(
                                    text = order.customerName,
                                    color = p.textSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (s.language == AppLanguage.EN) order.status.name.lowercase().replace('_', ' ') else order.status.titleRu,
                                color = p.accentOrange,
                                fontSize = 10.sp,
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
    onImportBackup: () -> Unit = {},
    onOpenGuide: () -> Unit = {}
) {
    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var companyName by remember(settings.companyName) { mutableStateOf(settings.companyName) }
    var companyPhone by remember(settings.companyPhone) { mutableStateOf(settings.companyPhone) }
    var companyAddress by remember(settings.companyAddress) { mutableStateOf(settings.companyAddress) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var isSyncingBackup by remember { mutableStateOf(false) }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val authResult = try {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(activityResult.data)
        } catch (_: Exception) {
            null
        }
        val token = authResult?.accessToken
        if (!token.isNullOrBlank()) {
            isSyncingBackup = true
            scope.launch {
                val email = withContext(Dispatchers.IO) {
                    GoogleDriveBackupClient.fetchDriveUserEmail(token) ?: "Google аккаунт"
                }
                onUpdateSettingsLive(
                    settings.copy(
                        googleDriveSyncEnabled = true,
                        googleDriveAccountEmail = email
                    )
                )
                val outcome = BackupManager.runBackupNow(
                    context = context,
                    force = true,
                    explicitAccessToken = token
                )
                onUpdateSettingsLive(outcome.updatedUiSettings.copy(googleDriveAccountEmail = email, googleDriveSyncEnabled = true))
                statusBanner = outcome.summaryMessage
                isSyncingBackup = false
            }
        } else {
            statusBanner = "Подключение Google Диска отменено"
        }
    }

    fun requestDriveAuthorization(syncImmediately: Boolean) {
        val activity = context.findHostActivity()
        val authClient = if (activity != null) {
            Identity.getAuthorizationClient(activity)
        } else {
            Identity.getAuthorizationClient(context)
        }
        authClient.authorize(BackupManager.buildDriveAuthorizationRequest())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    driveAuthLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        isSyncingBackup = true
                        scope.launch {
                            val email = withContext(Dispatchers.IO) {
                                GoogleDriveBackupClient.fetchDriveUserEmail(token)
                                    ?: settings.googleDriveAccountEmail.ifBlank { "Google аккаунт" }
                            }
                            onUpdateSettingsLive(
                                settings.copy(
                                    googleDriveSyncEnabled = true,
                                    googleDriveAccountEmail = email
                                )
                            )
                            if (syncImmediately) {
                                val outcome = BackupManager.runBackupNow(
                                    context = context,
                                    force = true,
                                    explicitAccessToken = token
                                )
                                onUpdateSettingsLive(outcome.updatedUiSettings.copy(googleDriveAccountEmail = email, googleDriveSyncEnabled = true))
                                statusBanner = outcome.summaryMessage
                            } else {
                                statusBanner = "Google Диск подключён ($email)"
                            }
                            isSyncingBackup = false
                        }
                    }
                }
            }
            .addOnFailureListener { err ->
                statusBanner = "Ошибка входа Google: ${err.localizedMessage ?: "проверьте Google Play Services"}"
            }
    }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_company_phone_field"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = companyAddress,
                    onValueChange = { companyAddress = it },
                    label = { Text(s.companyAddressLabel) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_company_address_field"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        onUpdateSettingsLive(
                            settings.copy(
                                companyName = companyName.trim().ifBlank { "WinStudio Pro" },
                                companyPhone = companyPhone.trim(),
                                companyAddress = companyAddress.trim()
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

            // 5. АВТОАРХИВАЦИЯ ЗАКРЫТЫХ ЗАКАЗОВ
            SettingsSectionHeader(s.sectionAutoArchive)
            IosGroupedCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.testTag("settings_auto_archive_header"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Inventory2, p.accentTeal)
                    Text(
                        text = s.autoArchiveAfterLabel,
                        color = p.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(14, 30, 60, 90, 0).forEach { days ->
                        val isSel = settings.autoArchiveAfterDays == days
                        val label = if (days == 0) s.autoArchiveManualLabel else "$days дн."
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                onUpdateSettingsLive(settings.copy(autoArchiveAfterDays = days))
                                statusBanner = "${s.autoArchiveAfterLabel}: $label"
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else p.textPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = p.accentTeal,
                                containerColor = p.secondaryBackground
                            ),
                            modifier = Modifier.testTag("settings_auto_archive_$days")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = s.autoArchiveHint,
                    color = p.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }

            // 6. РЕЗЕРВНОЕ КОПИРОВАНИЕ И GOOGLE ДИСК
            SettingsSectionHeader(s.sectionBackupData)
            IosGroupedCard(contentPadding = PaddingValues(14.dp)) {
                // Ежедневный локальный автобэкап
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Schedule, p.accentGreen)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.autoBackupTitle,
                            color = p.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = s.autoBackupHint,
                            color = p.textSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = settings.autoBackupEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettingsLive(settings.copy(autoBackupEnabled = enabled))
                        },
                        modifier = Modifier.testTag("settings_auto_backup_switch")
                    )
                }

                if (settings.autoBackupEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = s.autoBackupTimeLabel,
                        color = p.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0, 2, 4, 22).forEach { hr ->
                            val isSel = settings.autoBackupHour == hr
                            val label = String.format(Locale.US, "%02d:00", hr)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    onUpdateSettingsLive(settings.copy(autoBackupHour = hr))
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (isSel) Color.White else p.textPrimary
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = p.accentGreen,
                                    containerColor = p.secondaryBackground
                                ),
                                modifier = Modifier.testTag("settings_auto_backup_hour_$hr")
                            )
                        }
                    }
                }

                if (settings.autoBackupLastRunMillis != null || settings.autoBackupLastStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val dtStr = settings.autoBackupLastRunMillis?.let { ms ->
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))
                    } ?: ""
                    Text(
                        text = listOfNotNull(
                            dtStr.takeIf { it.isNotBlank() },
                            settings.autoBackupLastStatus.takeIf { it.isNotBlank() }
                        ).joinToString(" — "),
                        fontSize = 11.5.sp,
                        color = p.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (!isSyncingBackup) {
                            isSyncingBackup = true
                            scope.launch {
                                val outcome = BackupManager.runBackupNow(context, force = true)
                                onUpdateSettingsLive(outcome.updatedUiSettings)
                                statusBanner = outcome.summaryMessage
                                isSyncingBackup = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .testTag("settings_backup_now_button")
                ) {
                    if (isSyncingBackup) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(s.backupNowBtn, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = p.divider, thickness = 0.7.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Облачный бэкап на Google Диск
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.CloudUpload, p.accentBlue)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.googleDriveTitle,
                            color = p.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = s.googleDriveHint,
                            color = p.textSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (settings.googleDriveAccountEmail.isBlank() && !settings.googleDriveSyncEnabled) {
                    Button(
                        onClick = { requestDriveAuthorization(syncImmediately = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = p.accentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("settings_gdrive_connect_button")
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s.googleDriveConnectBtn, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                } else {
                    Surface(
                        color = p.secondaryBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${s.googleDriveConnectedPrefix} ${settings.googleDriveAccountEmail.ifBlank { "Google аккаунт" }}",
                                    color = p.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        onUpdateSettingsLive(
                                            settings.copy(
                                                googleDriveSyncEnabled = false,
                                                googleDriveAccountEmail = "",
                                                googleDriveLastStatus = "Отключено"
                                            )
                                        )
                                        statusBanner = "Google Диск отключён"
                                    },
                                    modifier = Modifier.testTag("settings_gdrive_disconnect_button")
                                ) {
                                    Text(s.googleDriveDisconnectBtn, color = p.accentRed, fontSize = 12.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Автовыгрузка на Google Диск",
                                    color = p.textSecondary,
                                    fontSize = 12.sp
                                )
                                Switch(
                                    checked = settings.googleDriveSyncEnabled,
                                    onCheckedChange = { enabled ->
                                        onUpdateSettingsLive(settings.copy(googleDriveSyncEnabled = enabled))
                                    },
                                    modifier = Modifier.testTag("settings_gdrive_sync_switch")
                                )
                            }
                            if (settings.googleDriveLastStatus.isNotBlank()) {
                                Text(
                                    text = settings.googleDriveLastStatus,
                                    color = p.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Button(
                                onClick = { requestDriveAuthorization(syncImmediately = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = p.accentBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("settings_gdrive_sync_now_button")
                            ) {
                                Text(s.googleDriveSyncNowBtn, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = p.divider, thickness = 0.7.dp)
                Spacer(modifier = Modifier.height(8.dp))

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

            // 6. СПРАВКА И ОБУЧЕНИЕ
            SettingsSectionHeader(s.sectionHelpGuide)
            IosGroupedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_guide_button"),
                onClick = onOpenGuide,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.MenuBook, p.accentBlue)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.guideButtonTitle,
                            color = p.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = s.guideButtonSubtitle,
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

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
