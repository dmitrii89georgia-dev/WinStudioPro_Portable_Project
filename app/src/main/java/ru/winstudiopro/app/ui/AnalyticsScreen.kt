package ru.winstudiopro.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.winstudiopro.app.domain.calculator.MarginStructureStats
import ru.winstudiopro.app.domain.calculator.MonthlyRevenueBar
import ru.winstudiopro.app.domain.calculator.ProductionAnalyticsCalculator
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.components.IosGroupedCard
import ru.winstudiopro.app.ui.components.IosSegmentedControl
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.util.Locale
import kotlin.math.roundToInt

private enum class AnalyticsChartMode {
    REVENUE_DYNAMICS,
    MARGIN_STRUCTURE
}

private fun fmtUsd2(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun fmtUsd0(v: Double): String = String.format(Locale.US, "%.0f", v)
private fun fmtPct1(v: Double): String = String.format(Locale.US, "%.1f", v)

@Composable
internal fun AnalyticsScreen(
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
    onOpenOrdersList: () -> Unit,
    onOpenCash: () -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var chartMode by remember { mutableStateOf(AnalyticsChartMode.REVENUE_DYNAMICS) }

    val snapshot = remember(
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
        ProductionAnalyticsCalculator.calculate(
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = "Пульс производства",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Переключатель графиков в стиле iOS
            IosSegmentedControl(
                items = listOf(
                    AnalyticsChartMode.REVENUE_DYNAMICS to "Динамика выручки",
                    AnalyticsChartMode.MARGIN_STRUCTURE to "Структура маржи"
                ),
                selectedItem = chartMode,
                onSelect = { chartMode = it },
                testTagPrefix = "analytics_chart_tab"
            )

            // Карточка графика (анимация внутри каждого графика)
            when (chartMode) {
                AnalyticsChartMode.REVENUE_DYNAMICS -> {
                    SalesBarChartCard(
                        bars = snapshot.monthlyRevenueBars,
                        totalRevenueUsd = snapshot.monthlyRevenueBars.sumOf { it.revenueUsd }
                    )
                }

                AnalyticsChartMode.MARGIN_STRUCTURE -> {
                    MarginDonutChartCard(
                        margin = snapshot.marginStructure
                    )
                }
            }

            // Заголовок блока KPI
            Text(
                text = "Финансовые и производственные KPI",
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Сетка KPI 2x2 — Ряд 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnalyticsKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("analytics_kpi_in_work"),
                    icon = Icons.Rounded.Assignment,
                    iconBg = palette.accentIndigo,
                    title = "В работе",
                    mainValue = "${snapshot.activeOrdersCount} зак.",
                    mainValueColor = palette.textPrimary,
                    subtitle = "Черновики/замеры: ${snapshot.draftOrdersCount}",
                    onClick = onOpenOrdersList
                )

                AnalyticsKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("analytics_kpi_contracts"),
                    icon = Icons.Rounded.Payments,
                    iconBg = palette.accentBlue,
                    title = "Деньги по договорам",
                    mainValue = "${fmtUsd2(snapshot.activeContractsTotalUsd)} $",
                    mainValueColor = palette.accentBlue,
                    subtitle = "Внесено: ${fmtUsd2(snapshot.activePaidAdvancesUsd)} $\nДолг: ${fmtUsd2(snapshot.activeRemainingDebtUsd)} $"
                )
            }

            // Сетка KPI 2x2 — Ряд 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnalyticsKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("analytics_kpi_obligations"),
                    icon = Icons.Rounded.Engineering,
                    iconBg = palette.accentOrange,
                    title = "Обязательства (себест.)",
                    mainValue = "${fmtUsd2(snapshot.obligationsTotalUsd)} $",
                    mainValueColor = palette.accentOrange,
                    subtitle = "Мат./услуги: ${fmtUsd2(snapshot.obligationsMaterialsAndServicesUsd)} $\nОстаток ЗП: ${fmtUsd2(snapshot.obligationsPayrollRemainingUsd)} $"
                )

                val isFreePositive = snapshot.freeMoneyUsd >= 0.0
                AnalyticsKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("analytics_kpi_free_money"),
                    icon = Icons.Rounded.AccountBalanceWallet,
                    iconBg = if (isFreePositive) palette.accentGreen else palette.accentRed,
                    title = "Свободные деньги",
                    mainValue = "${fmtUsd2(snapshot.freeMoneyUsd)} $",
                    mainValueColor = if (isFreePositive) palette.accentGreen else palette.accentRed,
                    subtitle = "Касса (${fmtUsd2(snapshot.totalCashBalanceUsd)}$) − Обяз.",
                    onClick = onOpenCash
                )
            }
        }
    }
}

@Composable
private fun SalesBarChartCard(
    bars: List<MonthlyRevenueBar>,
    totalRevenueUsd: Double
) {
    val palette = LocalWinStudioPalette.current
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(bars) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
        )
    }

    IosGroupedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_bar_chart_card"),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Выручка за 6 месяцев",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Договоры, в работе и закрытые заказы",
                    color = palette.textSecondary,
                    fontSize = 11.5.sp
                )
            }
            Surface(
                color = palette.accentBlue.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "${fmtUsd2(totalRevenueUsd)} $",
                    color = palette.accentBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val maxBarVal = (bars.maxOfOrNull { it.revenueUsd } ?: 0.0).coerceAtLeast(1.0)
        val hasAnyRevenue = bars.any { it.revenueUsd > 0.01 }
        val textPrimaryArgb = palette.textPrimary.toArgb()
        val textSecondaryArgb = palette.textSecondary.toArgb()
        val gridColor = palette.divider
        val barTopColor = palette.accentBlue
        val barBottomColor = palette.accentTeal

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("analytics_bar_canvas")
            ) {
                val topPad = 24.dp.toPx()
                val bottomPad = 26.dp.toPx()
                val sidePad = 8.dp.toPx()
                val plotW = (size.width - sidePad * 2).coerceAtLeast(1f)
                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                val baseLineY = topPad + plotH

                // Горизонтальные направляющие
                for (step in 0..3) {
                    val y = topPad + (plotH * step / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(sidePad, y),
                        end = Offset(sidePad + plotW, y),
                        strokeWidth = if (step == 3) 1.5f else 1f
                    )
                }

                if (bars.isNotEmpty()) {
                    val slotW = plotW / bars.size.toFloat()
                    val barW = (slotW * 0.48f).coerceIn(18.dp.toPx(), 42.dp.toPx())
                    val radiusPx = 8.dp.toPx()

                    val labelPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = textSecondaryArgb
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 11.sp.toPx()
                    }
                    val valuePaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = textPrimaryArgb
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 10.sp.toPx()
                        isFakeBoldText = true
                    }

                    bars.forEachIndexed { idx, bar ->
                        val centerX = sidePad + slotW * (idx + 0.5f)
                        val fraction = (bar.revenueUsd / maxBarVal).toFloat().coerceIn(0f, 1f)
                        val animatedH = if (bar.revenueUsd > 0.0) {
                            (plotH * fraction * animProgress.value).coerceAtLeast(6.dp.toPx())
                        } else {
                            3.dp.toPx() * animProgress.value
                        }
                        val left = centerX - barW / 2f
                        val top = baseLineY - animatedH

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = if (bar.revenueUsd > 0.0) {
                                    listOf(barTopColor, barBottomColor)
                                } else {
                                    listOf(gridColor, gridColor)
                                },
                                startY = top,
                                endY = baseLineY
                            ),
                            topLeft = Offset(left, top),
                            size = Size(barW, animatedH),
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )

                        // Подпись суммы над столбцом
                        val valLabel = if (bar.revenueUsd >= 1000.0) {
                            "${fmtUsd0(bar.revenueUsd)}$"
                        } else if (bar.revenueUsd > 0.0) {
                            "${bar.revenueUsd.roundToInt()}$"
                        } else {
                            "0$"
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            valLabel,
                            centerX,
                            (top - 6.dp.toPx()).coerceAtLeast(12.dp.toPx()),
                            valuePaint
                        )

                        // Подпись месяца
                        drawContext.canvas.nativeCanvas.drawText(
                            bar.shortMonthLabel,
                            centerX,
                            baseLineY + 18.dp.toPx(),
                            labelPaint
                        )
                    }
                }
            }

            if (!hasAnyRevenue) {
                Surface(
                    color = palette.secondaryBackground.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "Нет подтверждённых договоров за период",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarginDonutChartCard(
    margin: MarginStructureStats
) {
    val palette = LocalWinStudioPalette.current
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(margin) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    IosGroupedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_donut_chart_card"),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Структура маржинальности",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Распределение выручки: материалы, ФОТ и прибыль",
                    color = palette.textSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val profitColor = palette.accentGreen
    val materialsColor = palette.accentBlue
        val payrollColor = palette.accentOrange
        val emptyTrackColor = palette.divider

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(144.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("analytics_donut_canvas")
                ) {
                    val strokePx = 22.dp.toPx()
                    val diameter = size.minDimension - strokePx
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Фоновое кольцо
                    drawArc(
                        color = emptyTrackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )

                    if (margin.totalRevenueUsd > 0.0) {
                        val totalPct = (margin.netProfitPercent + margin.materialsPercent + margin.payrollPercent)
                            .coerceAtLeast(1.0)
                        val profitSweep = ((margin.netProfitPercent / totalPct) * 360.0).toFloat() * animProgress.value
                        val matSweep = ((margin.materialsPercent / totalPct) * 360.0).toFloat() * animProgress.value
                        val paySweep = ((margin.payrollPercent / totalPct) * 360.0).toFloat() * animProgress.value

                        var start = -90f
                        if (matSweep > 0.5f) {
                            drawArc(
                                color = materialsColor,
                                startAngle = start,
                                sweepAngle = matSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                            )
                            start += matSweep
                        }
                        if (paySweep > 0.5f) {
                            drawArc(
                                color = payrollColor,
                                startAngle = start,
                                sweepAngle = paySweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                            )
                            start += paySweep
                        }
                        if (profitSweep > 0.5f) {
                            drawArc(
                                color = profitColor,
                                startAngle = start,
                                sweepAngle = profitSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${fmtPct1(margin.netProfitPercent)}%",
                        color = profitColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Маржа",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DonutLegendRow(
                    dotColor = profitColor,
                    label = "Чистая прибыль",
                    amountUsd = margin.netProfitUsd,
                    percent = margin.netProfitPercent
                )
                DonutLegendRow(
                    dotColor = materialsColor,
                    label = "Материалы и услуги",
                    amountUsd = margin.materialsAndServicesUsd,
                    percent = margin.materialsPercent
                )
                DonutLegendRow(
                    dotColor = payrollColor,
                    label = "Зарплата (цех+монт.)",
                    amountUsd = margin.payrollTotalUsd,
                    percent = margin.payrollPercent
                )
            }
        }
    }
}

@Composable
private fun DonutLegendRow(
    dotColor: Color,
    label: String,
    amountUsd: Double,
    percent: Double
) {
    val palette = LocalWinStudioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${fmtUsd2(amountUsd)} $ (${fmtPct1(percent)}%)",
                color = palette.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AnalyticsKpiCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconBg: Color,
    title: String,
    mainValue: String,
    mainValueColor: Color,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalWinStudioPalette.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        color = palette.cardSurface,
        shape = shape,
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        shadowElevation = if (palette.isDark) 0.dp else 2.dp,
        modifier = modifier
            .clip(shape)
            .let { mod -> if (onClick != null) mod.clickable(onClick = onClick) else mod }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mainValue,
                    color = mainValueColor,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
