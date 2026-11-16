package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.pdf.PdfReportGenerator
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.io.File
import java.util.Locale
import java.util.UUID

// =========================================================================================
// МОДУЛЬ «ЗАДАНИЕ В ЦЕХ И 1D РАСКРОЙ ПРОФИЛЯ (С УЧЁТОМ СКЛАДА И ДЕЛОВЫХ ОСТАТКОВ)»
// =========================================================================================
@Composable
internal fun WorkshopAndCuttingScreen(
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
