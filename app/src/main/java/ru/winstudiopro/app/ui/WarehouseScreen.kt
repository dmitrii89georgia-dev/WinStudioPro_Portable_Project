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
import ru.winstudiopro.app.domain.model.ProfileRole
import ru.winstudiopro.app.domain.model.ProfileSystemSpec
import ru.winstudiopro.app.domain.model.RAW_UNPAINTED_COLOR_NAME
import ru.winstudiopro.app.domain.model.StockRemnantBar
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

// =========================================================================================
// СКЛАД МАТЕРИАЛА: ПАПКИ ПО ПРОФИЛЬНЫМ СИСТЕМАМ
// =========================================================================================
@Composable
internal fun WarehouseBySystemScreen(
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
