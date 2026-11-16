package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
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
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.util.UUID

// =========================================================================================
// ЭКРАН ПРАЙСОВ ($) — РЕДАКТОР ЦЕН ПРОФИЛЕЙ, СУХАРЕЙ, СТЕКЛОПАКЕТОВ, ФУРНИТУРЫ, РАЗНОГО И РАБОТ
// =========================================================================================
private enum class PriceCatalogTab(val titleRu: String) {
    PROFILES_AND_CLEATS("Профили и сухари"),
    GLAZING("Стеклопакеты"),
    HARDWARE("Фурнитура"),
    EXTRAS("Разное"),
    WORKS("Работы")
}

@Composable
internal fun PricesEditorScreen(
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    hardwareBrands: List<HardwareBrandItem>,
    extraAccessories: List<ExtraAccessoryItem>,
    workServices: List<WorkServiceItem>,
    currencySymbol: String,
    onBack: () -> Unit,
    onSaveProfiles: (List<ProfileSystemSpec>) -> Unit,
    onSaveGlazing: (List<GlazingCatalogItem>) -> Unit,
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
