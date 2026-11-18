package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.winstudiopro.app.R
import ru.winstudiopro.app.domain.calculator.OrderProcurementHelper
import ru.winstudiopro.app.domain.calculator.RubberType
import ru.winstudiopro.app.domain.calculator.WarehouseInventoryHelper
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.ProfileSystemSpec
import ru.winstudiopro.app.domain.model.RAW_UNPAINTED_COLOR_NAME
import ru.winstudiopro.app.domain.model.StockRemnantBar
import ru.winstudiopro.app.domain.model.StockRubberItem
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private data class WarehouseRubberEntry(
    val key: String,
    val rubberType: RubberType,
    val wedgeVariantId: String?,
    val wedgeThicknessMm: Float?,
    val title: String,
    val code: String,
    val metersPerBox: Double,
    val metersInStock: Double,
    val drawableResId: Int
)

// =========================================================================================
// СКЛАД МАТЕРИАЛА: ПАПКИ ПО ПРОФИЛЬНЫМ СИСТЕМАМ
// =========================================================================================
@Composable
internal fun WarehouseBySystemScreen(
    profileSystems: List<ProfileSystemSpec>,
    stockBars: List<StockRemnantBar>,
    stockRubber: List<StockRubberItem> = emptyList(),
    onBack: () -> Unit,
    onSaveStock: (List<StockRemnantBar>) -> Unit,
    onSaveRubberStock: (List<StockRubberItem>) -> Unit = {}
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    var selectedFolderSysId by remember { mutableStateOf(profileSystems.firstOrNull()?.id ?: "") }
    val activeSys = profileSystems.find { it.id == selectedFolderSysId } ?: profileSystems.firstOrNull()
    var editingRubberEntry by remember { mutableStateOf<WarehouseRubberEntry?>(null) }

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
                val sysRubberM = stockRubber.filter { it.profileSystemId == sys.id }
                    .sumOf { it.metersInStock.coerceAtLeast(0.0) }

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
                                text = "Хлыстов: $fullCnt | Ост: $remCnt | Рез: ${String.format(Locale.US, "%.0f", sysRubberM)}м",
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
                    .padding(horizontal = 14.dp)
                    .testTag("warehouse_lazy_column"),
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

                // --- 3. УПЛОТНИТЕЛЬНАЯ РЕЗИНА НА СКЛАДЕ (В МЕТРАХ) ---
                val rubberCatalog = activeSys.rubberCatalog
                val rubberEntries = buildList {
                    add(
                        WarehouseRubberEntry(
                            key = "rub_fixed_${activeSys.id}",
                            rubberType = RubberType.FIXED_GLAZING,
                            wedgeVariantId = null,
                            wedgeThicknessMm = rubberCatalog.fixedGlazing.thicknessMm,
                            title = rubberCatalog.fixedGlazing.name.ifBlank { RubberType.FIXED_GLAZING.titleRu },
                            code = rubberCatalog.fixedGlazing.code,
                            metersPerBox = rubberCatalog.fixedGlazing.metersPerBox,
                            metersInStock = OrderProcurementHelper.findRubberWarehouseMeters(
                                stockRubber = stockRubber,
                                profileSystemId = activeSys.id,
                                rubberType = RubberType.FIXED_GLAZING
                            ),
                            drawableResId = R.drawable.gasket_fixed
                        )
                    )
                    rubberCatalog.wedgeVariants.forEach { wedge ->
                        add(
                            WarehouseRubberEntry(
                                key = "rub_wedge_${activeSys.id}_${wedge.id}",
                                rubberType = RubberType.WEDGE_SHTAPIK,
                                wedgeVariantId = wedge.id,
                                wedgeThicknessMm = wedge.thicknessMm,
                                title = "Клин: ${wedge.name} (${String.format(Locale.US, "%.1f", wedge.thicknessMm)} мм)",
                                code = wedge.code,
                                metersPerBox = wedge.metersPerBox,
                                metersInStock = OrderProcurementHelper.findRubberWarehouseMeters(
                                    stockRubber = stockRubber,
                                    profileSystemId = activeSys.id,
                                    rubberType = RubberType.WEDGE_SHTAPIK,
                                    wedgeVariantId = wedge.id,
                                    wedgeThicknessMm = wedge.thicknessMm
                                ),
                                drawableResId = R.drawable.gasket_wedge
                            )
                        )
                    }
                    add(
                        WarehouseRubberEntry(
                            key = "rub_frame_${activeSys.id}",
                            rubberType = RubberType.FRAME_REBATE,
                            wedgeVariantId = null,
                            wedgeThicknessMm = null,
                            title = rubberCatalog.frameRebate.name.ifBlank { RubberType.FRAME_REBATE.titleRu },
                            code = rubberCatalog.frameRebate.code,
                            metersPerBox = rubberCatalog.frameRebate.metersPerBox,
                            metersInStock = OrderProcurementHelper.findRubberWarehouseMeters(
                                stockRubber = stockRubber,
                                profileSystemId = activeSys.id,
                                rubberType = RubberType.FRAME_REBATE
                            ),
                            drawableResId = R.drawable.gasket_frame_rebate
                        )
                    )
                    add(
                        WarehouseRubberEntry(
                            key = "rub_sash_${activeSys.id}",
                            rubberType = RubberType.SASH_DOOR_REBATE,
                            wedgeVariantId = null,
                            wedgeThicknessMm = null,
                            title = rubberCatalog.sashDoorRebate.name.ifBlank { RubberType.SASH_DOOR_REBATE.titleRu },
                            code = rubberCatalog.sashDoorRebate.code,
                            metersPerBox = rubberCatalog.sashDoorRebate.metersPerBox,
                            metersInStock = OrderProcurementHelper.findRubberWarehouseMeters(
                                stockRubber = stockRubber,
                                profileSystemId = activeSys.id,
                                rubberType = RubberType.SASH_DOOR_REBATE
                            ),
                            drawableResId = R.drawable.gasket_sash_rebate
                        )
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    val totalSysRubberM = rubberEntries.sumOf { it.metersInStock }
                    Text(
                        "3. Уплотнительная резина на складе (${String.format(Locale.US, "%.1f", totalSysRubberM)} м):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )
                }

                items(rubberEntries, key = { it.key }) { entry ->
                    WarehouseRubberRowCard(
                        entry = entry,
                        onAddBox = {
                            onSaveRubberStock(
                                WarehouseInventoryHelper.upsertRubberStock(
                                    currentStockRubber = stockRubber,
                                    profileSystemId = activeSys.id,
                                    rubberType = entry.rubberType,
                                    wedgeVariantId = entry.wedgeVariantId,
                                    wedgeThicknessMm = entry.wedgeThicknessMm,
                                    label = entry.title,
                                    deltaMeters = entry.metersPerBox
                                )
                            )
                        },
                        onDeltaMeters = { delta ->
                            onSaveRubberStock(
                                WarehouseInventoryHelper.upsertRubberStock(
                                    currentStockRubber = stockRubber,
                                    profileSystemId = activeSys.id,
                                    rubberType = entry.rubberType,
                                    wedgeVariantId = entry.wedgeVariantId,
                                    wedgeThicknessMm = entry.wedgeThicknessMm,
                                    label = entry.title,
                                    deltaMeters = delta
                                )
                            )
                        },
                        onOpenEdit = { editingRubberEntry = entry }
                    )
                }
            }
        }
    }

    val activeEditRubber = editingRubberEntry
    if (activeEditRubber != null && activeSys != null) {
        EditRubberStockDialog(
            entry = activeEditRubber,
            onDismiss = { editingRubberEntry = null },
            onConfirmAbsoluteMeters = { newMeters ->
                onSaveRubberStock(
                    WarehouseInventoryHelper.upsertRubberStock(
                        currentStockRubber = stockRubber,
                        profileSystemId = activeSys.id,
                        rubberType = activeEditRubber.rubberType,
                        wedgeVariantId = activeEditRubber.wedgeVariantId,
                        wedgeThicknessMm = activeEditRubber.wedgeThicknessMm,
                        label = activeEditRubber.title,
                        absoluteMeters = newMeters
                    )
                )
                editingRubberEntry = null
            }
        )
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

@Composable
private fun WarehouseRubberRowCard(
    entry: WarehouseRubberEntry,
    onAddBox: () -> Unit,
    onDeltaMeters: (Double) -> Unit,
    onOpenEdit: () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        shadowElevation = if (palette.isDark) 0.dp else 1.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("warehouse_rubber_card_${entry.key}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(id = entry.drawableResId),
                        contentDescription = entry.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = buildString {
                            if (entry.code.isNotBlank()) append("Арт. ${entry.code} • ")
                            append("В коробке: ${entry.metersPerBox.roundToInt()} м")
                        },
                        fontSize = 11.5.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = "Остаток: ${String.format(Locale.US, "%.2f", entry.metersInStock)} м",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (entry.metersInStock > 0.0) palette.accentGreen else palette.textSecondary
                    )
                }
                IconButton(
                    onClick = onOpenEdit,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("warehouse_rubber_edit_${entry.key}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Изменить остаток ${entry.title}",
                        tint = palette.accentBlue
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onDeltaMeters(-10.0) },
                    enabled = entry.metersInStock > 0.0,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("−10 м", fontSize = 12.sp, color = palette.textPrimary)
                }
                OutlinedButton(
                    onClick = { onDeltaMeters(10.0) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("+10 м", fontSize = 12.sp, color = palette.textPrimary)
                }
                Button(
                    onClick = onAddBox,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentTeal),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("warehouse_rubber_add_box_${entry.key}")
                ) {
                    Text(
                        text = "+1 кор. (+${entry.metersPerBox.roundToInt()} м)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EditRubberStockDialog(
    entry: WarehouseRubberEntry,
    onDismiss: () -> Unit,
    onConfirmAbsoluteMeters: (Double) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var metersInput by remember(entry.key) {
        mutableStateOf(String.format(Locale.US, "%.2f", entry.metersInStock))
    }
    var addBoxesInput by remember(entry.key) { mutableStateOf("1") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "В 1 коробке ${entry.metersPerBox.roundToInt()} пог. м. Укажите точный остаток на складе или добавьте приход коробками.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                OutlinedTextField(
                    value = metersInput,
                    onValueChange = { raw ->
                        metersInput = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(10)
                    },
                    label = { Text("Остаток на складе (п.м.)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warehouse_rubber_meters_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = addBoxesInput,
                        onValueChange = { raw ->
                            addBoxesInput = raw.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("Приход (коробок)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val current = metersInput.replace(',', '.').toDoubleOrNull() ?: entry.metersInStock
                            val boxes = (addBoxesInput.toIntOrNull() ?: 1).coerceAtLeast(1)
                            val updated = (current + boxes * entry.metersPerBox).coerceAtLeast(0.0)
                            metersInput = String.format(Locale.US, "%.2f", updated)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentTeal),
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.SemiBold)
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Отмена", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val parsed = metersInput.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0)
                                ?: entry.metersInStock
                            onConfirmAbsoluteMeters(parsed)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("warehouse_rubber_meters_confirm")
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
