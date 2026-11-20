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
    WORKS("Монтаж"),
    ASSEMBLY("Сборка"),
    ORDER_SERVICES("Доп. услуги"),
    FIN_SCHEME("Фин схема")
}

@Composable
internal fun PricesEditorScreen(
    profileSystems: List<ProfileSystemSpec>,
    glazingCatalog: List<GlazingCatalogItem>,
    hardwareBrands: List<HardwareBrandItem>,
    extraAccessories: List<ExtraAccessoryItem>,
    workServices: List<WorkServiceItem>,
    assemblyPricing: AssemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
    orderExtraServices: List<OrderExtraServiceItem> = DefaultCatalogs.defaultOrderExtraServices(),
    finSchemeSettings: FinSchemeSettings = FinSchemeSettings(),
    currencySymbol: String,
    onBack: () -> Unit,
    onSaveProfiles: (List<ProfileSystemSpec>) -> Unit,
    onSaveGlazing: (List<GlazingCatalogItem>) -> Unit,
    onSaveHardware: (List<HardwareBrandItem>) -> Unit,
    onSaveExtras: (List<ExtraAccessoryItem>) -> Unit,
    onSaveWorks: (List<WorkServiceItem>) -> Unit,
    onSaveAssemblyPricing: (AssemblyPricingCatalog) -> Unit = {},
    onSaveOrderExtraServices: (List<OrderExtraServiceItem>) -> Unit = {},
    onSaveFinSchemeSettings: (FinSchemeSettings) -> Unit = {}
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
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeTab = tab }
                                .testTag("price_tab_${tab.name}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tab.titleRu,
                                    color = if (sel) Color.White else palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
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
                                    "Добавить услугу в раздел «Монтаж»",
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentBlue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newWrkName,
                                        onValueChange = { newWrkName = it },
                                        label = { Text("Название монтажной работы") },
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
                                savedToast = "Обновлена расценка монтажа «${item.name}»"
                            },
                            onDelete = { onSaveWorks(workServices.filterNot { it.id == item.id }) }
                        )
                    }
                }
            }

            PriceCatalogTab.ASSEMBLY -> {
                AssemblyPricingTabContent(
                    assemblyPricing = assemblyPricing,
                    currencySymbol = currencySymbol,
                    onSave = { updated ->
                        onSaveAssemblyPricing(updated)
                        savedToast = "Настройки цеховой сборки сохранены (${updated.defaultScheme.shortTitleRu})"
                    }
                )
            }

            PriceCatalogTab.ORDER_SERVICES -> {
                OrderExtraServicesTabContent(
                    services = orderExtraServices,
                    currencySymbol = currencySymbol,
                    onSaveServices = { updated, toastMsg ->
                        onSaveOrderExtraServices(updated)
                        savedToast = toastMsg
                    }
                )
            }

            PriceCatalogTab.FIN_SCHEME -> {
                FinSchemeTabContent(
                    finSchemeSettings = finSchemeSettings,
                    currencySymbol = currencySymbol,
                    onSave = { updated ->
                        onSaveFinSchemeSettings(updated)
                        savedToast = "Настройки «Фин схема» сохранены (коэфф. ×${String.format(java.util.Locale.US, "%.2f", updated.baseMarkupMultiplier)})"
                    }
                )
            }
        }
    }
}

@Composable
private fun AssemblyPricingTabContent(
    assemblyPricing: AssemblyPricingCatalog,
    currencySymbol: String,
    onSave: (AssemblyPricingCatalog) -> Unit
) {
    val palette = LocalWinStudioPalette.current

    var defaultScheme by remember(assemblyPricing) { mutableStateOf(assemblyPricing.defaultScheme) }
    var frameCornerRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.frameCornerRate.toString()) }
    var sashCornerRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.sashCornerRate.toString()) }
    var doorCornerRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.doorCornerRate.toString()) }
    var impostRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.impostRate.toString()) }
    var hwTurnRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.hwTurnRate.toString()) }
    var hwTiltTurnRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.hwTiltTurnRate.toString()) }
    var hwShtulpRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.hwShtulpRate.toString()) }
    var hwSlidingRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.hwSlidingRate.toString()) }
    var hwDoorRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.hwDoorRate.toString()) }
    var beadRateMode by remember(assemblyPricing) { mutableStateOf(assemblyPricing.beadRateMode) }
    var beadRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.beadRate.toString()) }
    var rubberRateMode by remember(assemblyPricing) { mutableStateOf(assemblyPricing.rubberRateMode) }
    var rubberRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.rubberRate.toString()) }
    var floorAssemblyRate by remember(assemblyPricing) { mutableStateOf(assemblyPricing.floorAssemblyRate.toString()) }
    var ratePerM2 by remember(assemblyPricing) { mutableStateOf(assemblyPricing.ratePerM2.toString()) }
    var ratePerMeter by remember(assemblyPricing) { mutableStateOf(assemblyPricing.ratePerMeter.toString()) }

    fun parseD(raw: String, fallback: Double): Double =
        raw.trim().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: fallback

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("assembly_pricing_scroll"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Выбор схемы по умолчанию
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = if (palette.isDark) 0.dp else 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Схема расчёта сборки по умолчанию",
                    fontWeight = FontWeight.Bold,
                    color = palette.accentBlue
                )
                Text(
                    "Используется для всех новых и существующих заказов, если в окне не выбрана индивидуальная схема:",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )
                AssemblySchemeType.entries.forEach { scheme ->
                    val selected = defaultScheme == scheme
                    Surface(
                        color = if (selected) palette.accentBlue.copy(alpha = 0.12f) else palette.secondaryBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (selected) 1.2.dp else 0.7.dp,
                            color = if (selected) palette.accentBlue else palette.floatingBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { defaultScheme = scheme }
                            .testTag("assembly_default_scheme_${scheme.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { defaultScheme = scheme },
                                colors = RadioButtonDefaults.colors(selectedColor = palette.accentBlue)
                            )
                            Text(
                                text = scheme.titleRu,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Схема 1: Калькулятор по операциям
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = if (palette.isDark) 0.dp else 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Схема 1: Калькулятор по операциям ($currencySymbol)",
                    fontWeight = FontWeight.Bold,
                    color = palette.accentBlue
                )
                Text(
                    "Распил и обжим углов (ставка за 1 узел / угол):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = frameCornerRate,
                        onValueChange = { frameCornerRate = it },
                        label = { Text("Рама ($currencySymbol/узел)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("asm_frame_corner_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sashCornerRate,
                        onValueChange = { sashCornerRate = it },
                        label = { Text("Створка ($currencySymbol/узел)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("asm_sash_corner_input"),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = doorCornerRate,
                        onValueChange = { doorCornerRate = it },
                        label = { Text("Дверь ($currencySymbol/узел)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("asm_door_corner_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = impostRate,
                        onValueChange = { impostRate = it },
                        label = { Text("Импост ($currencySymbol/шт)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("asm_impost_input"),
                        singleLine = true
                    )
                }

                HorizontalDivider(color = palette.divider)
                Text(
                    "Установка фурнитуры по типам ($currencySymbol/створка):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hwTurnRate,
                        onValueChange = { hwTurnRate = it },
                        label = { Text("Поворотная ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hwTiltTurnRate,
                        onValueChange = { hwTiltTurnRate = it },
                        label = { Text("Повор.-отк. ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hwShtulpRate,
                        onValueChange = { hwShtulpRate = it },
                        label = { Text("Штульп ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hwSlidingRate,
                        onValueChange = { hwSlidingRate = it },
                        label = { Text("Сдвижная ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = hwDoorRate,
                    onValueChange = { hwDoorRate = it },
                    label = { Text("Дверная фурнитура ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider(color = palette.divider)
                Text(
                    "Штапики / проёмы:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeadAssemblyRateMode.entries.forEach { mode ->
                        val sel = beadRateMode == mode
                        FilterChip(
                            selected = sel,
                            onClick = { beadRateMode = mode },
                            label = {
                                Text(
                                    text = mode.titleRu,
                                    fontSize = 11.5.sp,
                                    color = if (sel) Color.White else palette.textPrimary
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentBlue,
                                containerColor = palette.secondaryBackground
                            ),
                            modifier = Modifier.testTag("asm_bead_mode_${mode.name}")
                        )
                    }
                }
                OutlinedTextField(
                    value = beadRate,
                    onValueChange = { beadRate = it },
                    label = { Text("Ставка за штапики (${beadRateMode.titleRu}, $currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider(color = palette.divider)
                Text(
                    "Резина (уплотнитель):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RubberAssemblyRateMode.entries.forEach { mode ->
                        val sel = rubberRateMode == mode
                        FilterChip(
                            selected = sel,
                            onClick = { rubberRateMode = mode },
                            label = {
                                Text(
                                    text = mode.titleRu,
                                    fontSize = 11.sp,
                                    color = if (sel) Color.White else palette.textPrimary
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentBlue,
                                containerColor = palette.secondaryBackground
                            ),
                            modifier = Modifier.testTag("asm_rubber_mode_${mode.name}")
                        )
                    }
                }
                OutlinedTextField(
                    value = rubberRate,
                    onValueChange = { rubberRate = it },
                    label = { Text("Ставка за резину (${rubberRateMode.titleRu}, $currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider(color = palette.divider)
                OutlinedTextField(
                    value = floorAssemblyRate,
                    onValueChange = { floorAssemblyRate = it },
                    label = { Text("Сборка на этаже (за 1 конструкцию, $currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("asm_floor_rate_input"),
                    singleLine = true
                )
            }
        }

        // Схема 2 и Схема 3
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = if (palette.isDark) 0.dp else 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Схема 2 и Схема 3: Фиксированные ставки ($currencySymbol)",
                    fontWeight = FontWeight.Bold,
                    color = palette.accentBlue
                )
                OutlinedTextField(
                    value = ratePerM2,
                    onValueChange = { ratePerM2 = it },
                    label = { Text("Схема 2: Ставка за 1 м² конструкции ($currencySymbol/м²)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("asm_rate_m2_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ratePerMeter,
                    onValueChange = { ratePerMeter = it },
                    label = { Text("Схема 3: Ставка за 1 м.п. периметра профиля ($currencySymbol/м.п.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("asm_rate_meter_input"),
                    singleLine = true
                )
            }
        }

        Button(
            onClick = {
                onSave(
                    AssemblyPricingCatalog(
                        defaultScheme = defaultScheme,
                        frameCornerRate = parseD(frameCornerRate, assemblyPricing.frameCornerRate),
                        sashCornerRate = parseD(sashCornerRate, assemblyPricing.sashCornerRate),
                        doorCornerRate = parseD(doorCornerRate, assemblyPricing.doorCornerRate),
                        impostRate = parseD(impostRate, assemblyPricing.impostRate),
                        hwTurnRate = parseD(hwTurnRate, assemblyPricing.hwTurnRate),
                        hwTiltTurnRate = parseD(hwTiltTurnRate, assemblyPricing.hwTiltTurnRate),
                        hwShtulpRate = parseD(hwShtulpRate, assemblyPricing.hwShtulpRate),
                        hwSlidingRate = parseD(hwSlidingRate, assemblyPricing.hwSlidingRate),
                        hwDoorRate = parseD(hwDoorRate, assemblyPricing.hwDoorRate),
                        beadRateMode = beadRateMode,
                        beadRate = parseD(beadRate, assemblyPricing.beadRate),
                        rubberRateMode = rubberRateMode,
                        rubberRate = parseD(rubberRate, assemblyPricing.rubberRate),
                        floorAssemblyRate = parseD(floorAssemblyRate, assemblyPricing.floorAssemblyRate),
                        ratePerM2 = parseD(ratePerM2, assemblyPricing.ratePerM2),
                        ratePerMeter = parseD(ratePerMeter, assemblyPricing.ratePerMeter)
                    )
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_assembly_pricing_button")
        ) {
            Text("Сохранить настройки сборки", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OrderExtraServicesTabContent(
    services: List<OrderExtraServiceItem>,
    currencySymbol: String,
    onSaveServices: (List<OrderExtraServiceItem>, String) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var newSrvName by remember { mutableStateOf("") }
    var newSrvPrice by remember { mutableStateOf("25") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("order_services_lazy_column"),
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
                        "Добавить в «Дополнительные услуги заказа»",
                        fontWeight = FontWeight.Bold,
                        color = palette.accentBlue
                    )
                    Text(
                        "Применяется целиком ко всему заказу (доставка, подъём на этаж, автокран и др.). В конкретном заказе цену можно переопределить.",
                        fontSize = 11.5.sp,
                        color = palette.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newSrvName,
                            onValueChange = { newSrvName = it },
                            label = { Text("Название услуги") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.4f).testTag("order_service_name_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newSrvPrice,
                            onValueChange = { newSrvPrice = it },
                            label = { Text("Цена ($currencySymbol)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.9f).testTag("order_service_price_input"),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (newSrvName.isNotBlank()) {
                                    val p = newSrvPrice.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                    val item = OrderExtraServiceItem(
                                        id = "ord_srv_${UUID.randomUUID().toString().take(6)}",
                                        name = newSrvName.trim(),
                                        defaultPrice = p,
                                        applyToWholeOrder = true
                                    )
                                    onSaveServices(services + item, "Добавлена услуга заказа «${item.name}»")
                                    newSrvName = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("order_service_add_button")
                        ) {
                            Text("+ Добавить услугу", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        items(services, key = { it.id }) { srv ->
            EditablePriceRowCard(
                title = srv.name,
                subtitle = if (srv.applyToWholeOrder) "На весь заказ • по умолчанию" else "На заказ",
                initialPrice = srv.defaultPrice,
                currencySymbol = currencySymbol,
                onSavePrice = { newP ->
                    val updated = services.map { if (it.id == srv.id) it.copy(defaultPrice = newP) else it }
                    onSaveServices(updated, "Обновлена базовая цена услуги «${srv.name}»")
                },
                onDelete = {
                    onSaveServices(services.filterNot { it.id == srv.id }, "Услуга «${srv.name}» удалена")
                }
            )
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

@Composable
private fun FinSchemeTabContent(
    finSchemeSettings: FinSchemeSettings,
    currencySymbol: String,
    onSave: (FinSchemeSettings) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var markupText by remember(finSchemeSettings) {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", finSchemeSettings.baseMarkupMultiplier))
    }
    var markupPresets by remember(finSchemeSettings) {
        mutableStateOf(finSchemeSettings.markupPresets)
    }
    var discountPresets by remember(finSchemeSettings) {
        mutableStateOf(finSchemeSettings.discountPresets)
    }
    var defaultVatText by remember(finSchemeSettings) {
        mutableStateOf(
            if (finSchemeSettings.defaultVatPercent % 1.0 == 0.0) {
                finSchemeSettings.defaultVatPercent.toInt().toString()
            } else {
                finSchemeSettings.defaultVatPercent.toString()
            }
        )
    }
    var warnBelowPrimeCost by remember(finSchemeSettings) {
        mutableStateOf(finSchemeSettings.warnBelowPrimeCost)
    }

    var newDiscName by remember { mutableStateOf("") }
    var newDiscPct by remember { mutableStateOf("") }
    var newDiscDealer by remember { mutableStateOf(false) }

    val parsedMarkup = markupText.replace(',', '.').toDoubleOrNull()?.coerceIn(1.0, 10.0)
        ?: finSchemeSettings.baseMarkupMultiplier
    val markupPct = ((parsedMarkup - 1.0) * 100.0).coerceAtLeast(0.0)
    val parsedVat = defaultVatText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 50.0)
        ?: finSchemeSettings.defaultVatPercent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("fin_scheme_scroll"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Коэффициент наценки базовой цены
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = if (palette.isDark) 0.dp else 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Коэффициент наценки базовой цены",
                    fontWeight = FontWeight.Bold,
                    color = palette.accentBlue
                )
                Text(
                    text = "Применяется ко всей себестоимости заказа (материалы + сборка + монтаж + услуги заказа). При 1.40 базовая цена продажи = Себестоимость × 1.40 (+40%).",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = markupText,
                        onValueChange = { markupText = it },
                        label = { Text("Коэффициент (напр. 1.40)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fin_markup_multiplier_input"),
                        singleLine = true
                    )
                    Surface(
                        color = palette.accentBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, palette.accentBlue.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Наценка", fontSize = 10.sp, color = palette.textSecondary)
                            Text(
                                text = "+${String.format(java.util.Locale.US, "%.0f", markupPct)}%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentBlue
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    markupPresets.forEach { preset ->
                        val isSel = kotlin.math.abs(parsedMarkup - preset) < 0.005
                        FilterChip(
                            selected = isSel,
                            onClick = { markupText = String.format(java.util.Locale.US, "%.2f", preset) },
                            label = {
                                Text(
                                    text = "×${String.format(java.util.Locale.US, "%.2f", preset)}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSel) Color.White else palette.textPrimary
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentBlue,
                                containerColor = palette.secondaryBackground
                            ),
                            modifier = Modifier.testTag("fin_markup_chip_${String.format(java.util.Locale.US, "%.2f", preset)}")
                        )
                    }
                }
                Surface(
                    color = palette.secondaryBackground.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val samplePrime = 100.0
                    val sampleBase = samplePrime * parsedMarkup
                    Text(
                        text = "Пример: себестоимость 100.00 $currencySymbol → базовая цена для клиента ${String.format(java.util.Locale.US, "%.2f", sampleBase)} $currencySymbol",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // 2. Пресеты скидок для быстрого выбора в заказе
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = if (palette.isDark) 0.dp else 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Пресеты скидок клиентам (быстрый выбор в шторке заказа)",
                    fontWeight = FontWeight.Bold,
                    color = palette.accentBlue
                )
                discountPresets.forEach { preset ->
                    Surface(
                        color = palette.secondaryBackground.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                                val tag = if (preset.isDealer) " • Дилер" else ""
                                Text(
                                    text = "Скидка ${preset.discountPercent.toInt()}%$tag",
                                    fontSize = 11.sp,
                                    color = if (preset.isDealer) palette.accentOrange else palette.textSecondary
                                )
                            }
                            if (discountPresets.size > 1) {
                                IconButton(
                                    onClick = {
                                        discountPresets = discountPresets.filterNot { it.id == preset.id }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Удалить пресет",
                                        tint = palette.accentRed
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.divider)
                Text("Добавить пресет скидки", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newDiscName,
                        onValueChange = { newDiscName = it },
                        label = { Text("Название (напр. Постоянный)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f).testTag("fin_new_discount_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newDiscPct,
                        onValueChange = { newDiscPct = it },
                        label = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f).testTag("fin_new_discount_pct_input"),
                        singleLine = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newDiscDealer, onCheckedChange = { newDiscDealer = it })
                        Text("Дилерская цена", fontSize = 12.sp, color = palette.textPrimary)
                    }
                    Button(
                        onClick = {
                            val pct = newDiscPct.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 90.0) ?: return@Button
                            val title = newDiscName.trim().ifBlank { "Скидка ${pct.toInt()}%" }
                            discountPresets = discountPresets + DiscountPresetItem(
                                id = "disc_${UUID.randomUUID().toString().take(6)}",
                                name = title,
                                discountPercent = pct,
                                isDealer = newDiscDealer
                            )
                            newDiscName = ""
                            newDiscPct = ""
                            newDiscDealer = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("fin_add_discount_button")
                    ) {
                        Text("+ Добавить", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 3. НДС по умолчанию и контроль минимальной цены
        Surface(
            color = palette.cardSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.8.dp, palette.floatingBorder),
            shadowElevation = if (palette.isDark) 0.dp else 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "НДС по умолчанию и контроль маржинальности",
                    fontWeight = FontWeight.Bold,
                    color = palette.accentBlue
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = kotlin.math.abs(parsedVat - 0.0) < 0.01,
                        onClick = { defaultVatText = "0" },
                        label = { Text("Без НДС (0%)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("fin_vat_0_chip")
                    )
                    FilterChip(
                        selected = kotlin.math.abs(parsedVat - 18.0) < 0.01,
                        onClick = { defaultVatText = "18" },
                        label = { Text("НДС 18%") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("fin_vat_18_chip")
                    )
                    OutlinedTextField(
                        value = defaultVatText,
                        onValueChange = { defaultVatText = it },
                        label = { Text("НДС %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                HorizontalDivider(color = palette.divider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Контроль минимальной цены",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Подсвечивать предупреждение в заказе, если цена после скидки опустилась ниже себестоимости",
                            fontSize = 11.5.sp,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = warnBelowPrimeCost,
                        onCheckedChange = { warnBelowPrimeCost = it },
                        modifier = Modifier.testTag("fin_warn_below_prime_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.accentGreen
                        )
                    )
                }
            }
        }

        Button(
            onClick = {
                val updatedPresets = (markupPresets + parsedMarkup).distinct().sorted()
                onSave(
                    finSchemeSettings.copy(
                        baseMarkupMultiplier = parsedMarkup,
                        markupPresets = updatedPresets,
                        discountPresets = discountPresets,
                        defaultVatPercent = parsedVat,
                        warnBelowPrimeCost = warnBelowPrimeCost
                    )
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_fin_scheme_button")
        ) {
            Text("Сохранить финансовую схему", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

