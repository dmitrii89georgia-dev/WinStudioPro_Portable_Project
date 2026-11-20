package ru.winstudiopro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.winstudiopro.app.R
import ru.winstudiopro.app.domain.model.*
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

// =========================================================================================
// КОНСТРУКТОР ПРОФИЛЬНЫХ СИСТЕМ (ЧЕРТЕЖИ СЕЧЕНИЙ A..H, РАЗМЕРЫ, НАХЛЁСТЫ, ЦЕНЫ И СУХАРИ)
// =========================================================================================
private enum class ProfileAccordionSection(val letterBadge: String, val titleRu: String, val subtitleRu: String) {
    RAMA_A("A", "Рама", "Размеры A1–A4 (с точностью до 0.1 мм) и стоимость за 1 пог. метр"),
    IMPOST_B("B", "Импост", "Размеры B1–B4 (с точностью до 0.1 мм) и стоимость за 1 пог. метр"),
    STVORKA_C("C", "Створка", "Размеры C1–C3, нахлёст на раму и цена за 1 пог. м"),
    DVER_D("D", "Дверь", "Размеры D1–D3, нахлёст на раму и цена за 1 пог. м"),
    REV_DVER_E("E", "Обратная дверь", "Размеры E1–E3, нахлёст на раму и цена за 1 пог. м"),
    UGLOVIK_F("F", "Угловик", "Эскиз углового профиля и цена за 1 пог. м для сметы"),
    SHTULP_G("G", "Штульп", "Размеры G1–G2 и стоимость за 1 пог. метр"),
    SHTAPIK_H("H", "Штапик", "Размеры H1, H2, правило раскроя 90°/45° и цена за метр"),
    CLEATS_SETS("∟", "Угловые сухари (комплекты)", "Цены комплектов на 4 угла (рама, створка, дверь) и на 1 импост"),
    RUBBER_GASKETS("◐", "Резина (уплотнители)", "4 вида резины: глухарь, клин (по толщинам), притвор рамы и створки • расчёт цены за 1 м из коробки")
}

@Composable
internal fun ProfileConstructorScreen(
    profileSystems: List<ProfileSystemSpec>,
    currencySymbol: String,
    defaultProfileSystemId: String = "",
    onSetDefaultProfileSystem: (String) -> Unit = {},
    onBack: () -> Unit,
    onSaveProfiles: (List<ProfileSystemSpec>) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        UnifiedTopAppBar(
            title = strings.profilesScreenTitle,
            onBack = onBack
        ) {
            Surface(
                color = palette.accentBlue,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { createNewProfileSystem() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(strings.addSystemBtn, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (savedBannerText != null) {
            Surface(
                color = palette.accentGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { savedBannerText = null }
            ) {
                Text(
                    text = savedBannerText!!,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        if (activeSys != null) {
            // Состояния общих параметров системы (с поддержкой десятых долей миллиметра, например 39.2, 59.6, 92.4)
            var name by remember(activeSys.id) { mutableStateOf(activeSys.name) }
            var matType by remember(activeSys.id) { mutableStateOf(activeSys.materialType) }
            var stockBarLen by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.stockBarLengthMm)) }
            var weldAllow by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.weldAllowancePerCornerMm)) }
            var sawKerfMmText by remember(activeSys.id) { mutableStateOf(fmtMm(activeSys.sawKerfMm)) }
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

            // Цвета данной профильной системы
            var colorsList by remember(activeSys.id) { mutableStateOf(activeSys.colors) }
            var showAddColorDialog by remember { mutableStateOf(false) }

            // Угловые сухари (комплекты на 4 угла рамы, створки, двери и на 1 импост)
            var cleatFrameSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorFrame.toString()) }
            var cleatImpostSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceImpostConnector.toString()) }
            var cleatSashSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorSash.toString()) }
            var cleatDoorSetPrice by remember(activeSys.id) { mutableStateOf(activeSys.priceCornerConnectorDoorSash.toString()) }

            // Уплотнительная резина (4 типа: глухарь, клинья разной толщины, притвор рамы, притвор створки/двери)
            var rubberCatalogState by remember(activeSys.id) { mutableStateOf(activeSys.rubberCatalog) }
            var expandedRubberRow by remember(activeSys.id) { mutableStateOf<Int?>(null) }

            fun buildDraftSystem(
                overrideId: String = activeSys.id,
                overrideName: String = name.ifBlank { activeSys.name },
                overrideColors: List<ProfileColorSpec> = colorsList
            ): ProfileSystemSpec {
                val primaryShtapik = shtapikList.firstOrNull()
                return activeSys.copy(
                    id = overrideId,
                    name = overrideName,
                    materialType = matType,
                    stockBarLengthMm = parseMmOrNull(stockBarLen) ?: activeSys.stockBarLengthMm,
                    weldAllowancePerCornerMm = parseMmOrNull(weldAllow) ?: activeSys.weldAllowancePerCornerMm,
                    sawKerfMm = (parseMmOrNull(sawKerfMmText) ?: activeSys.sawKerfMm).coerceIn(0f, 15f),
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
                    shtapikVariants = shtapikList.map { it.copy() },
                    shtapikWidthMm = primaryShtapik?.widthH2Mm ?: activeSys.shtapikWidthMm,
                    priceShtapikPerM = primaryShtapik?.pricePerM ?: activeSys.priceShtapikPerM,
                    // Цвета профильной системы
                    colors = overrideColors.map { it.copy() },
                    // Угловые сухари (комплекты)
                    priceCornerConnectorFrame = cleatFrameSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorFrame,
                    priceImpostConnector = cleatImpostSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceImpostConnector,
                    priceCornerConnectorSash = cleatSashSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorSash,
                    priceCornerConnectorDoorSash = cleatDoorSetPrice.replace(',', '.').toDoubleOrNull() ?: activeSys.priceCornerConnectorDoorSash,
                    // Каталог уплотнительной резины (4 вида) и синхронизация базовой цены за 1 м
                    rubberCatalog = rubberCatalogState.copy(
                        wedgeVariants = rubberCatalogState.wedgeVariants.map { it.copy() }
                    ),
                    priceRubberPerM = rubberCatalogState.fixedGlazing.pricePerMeter
                )
            }

            fun saveCurrentSystem() {
                val updatedSys = buildDraftSystem()
                onSaveProfiles(profileSystems.map { if (it.id == updatedSys.id) updatedSys else it })
                savedBannerText = "Профильная система «${updatedSys.name}» сохранена!"
            }

            fun copyCurrentProfileSystem() {
                val baseName = name.trim().ifBlank { activeSys.name }
                val copied = buildDraftSystem(
                    overrideId = "sys_${UUID.randomUUID().toString().take(6)}",
                    overrideName = "$baseName (Копия)"
                )
                onSaveProfiles(profileSystems + copied)
                selectedId = copied.id
                expandedSection = ProfileAccordionSection.RAMA_A
                savedBannerText = "Создана и открыта копия системы «${copied.name}»"
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
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Список профильных систем (${profileSystems.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.textPrimary
                            )
                            Button(
                                onClick = { createNewProfileSystem() },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(strings.addSystemBtn, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        profileSystems.forEach { sys ->
                            val isSelected = sys.id == activeSys.id
                            Surface(
                                color = if (isSelected) palette.accentBlue.copy(alpha = 0.12f) else palette.secondaryBackground.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.8.dp,
                                        color = if (isSelected) palette.accentBlue else palette.floatingBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedId = sys.id }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                color = if (sys.materialType == ProfileMaterialType.ALUMINUM) Color(0xFF455A64) else palette.accentTeal,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = sys.materialType.shortBadge,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = sys.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = palette.textPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Рама A1=${fmtMm(sys.frameWidthMm)} мм (${sys.priceFramePerM} $currencySymbol/м) • Импост B1=${fmtMm(sys.impostWidthMm)} мм • Створка C1=${fmtMm(sys.sashWidthMm)} мм • Рез пилы=${fmtMm(sys.sawKerfMm)} мм • Зазор СП=${fmtMm(sys.glazingClearanceTotalMm)} мм",
                                            fontSize = 11.sp,
                                            color = palette.textSecondary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val isDefault = sys.id == defaultProfileSystemId
                                        Surface(
                                            color = if (isDefault) palette.accentGreen.copy(alpha = 0.16f) else palette.secondaryBackground,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                0.8.dp,
                                                if (isDefault) palette.accentGreen else palette.floatingBorder
                                            ),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    onSetDefaultProfileSystem(sys.id)
                                                    savedBannerText = "Система «${sys.name}» назначена по умолчанию для новых заказов"
                                                }
                                                .testTag("profile_set_default_${sys.id}")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (isDefault) "★ По умолч." else "По умолч.",
                                                    color = if (isDefault) palette.accentGreen else palette.textSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = { selectedId = sys.id },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text(
                                                if (isSelected) "✓ Выбрана" else "Редакт.",
                                                color = palette.accentBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
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
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Удалить систему",
                                                    tint = palette.accentRed,
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
                Surface(
                    color = palette.cardSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                    shadowElevation = if (palette.isDark) 0.dp else 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Редактирование системы: «$name»",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = palette.accentBlue,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { copyCurrentProfileSystem() },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_copy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = strings.copySystemBtn,
                                    tint = palette.accentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = strings.copySystemBtn,
                                    color = palette.accentBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название профильной системы") },
                            shape = RoundedCornerShape(12.dp),
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

                        // Блок «Каталог цветов системы»
                        Surface(
                            color = palette.secondaryBackground.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, palette.floatingBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${strings.profileColorsTitle} (${colorsList.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = palette.textPrimary
                                    )
                                    Button(
                                        onClick = { showAddColorDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("profile_add_color_button")
                                    ) {
                                        Text(strings.addColorBtn, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (colorsList.isEmpty()) {
                                    Text(
                                        text = "Цвета не заданы — нажмите «${strings.addColorBtn}».",
                                        fontSize = 12.sp,
                                        color = palette.textSecondary
                                    )
                                } else {
                                    colorsList.forEach { clr ->
                                        Surface(
                                            color = palette.cardSurface,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(0.6.dp, palette.floatingBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .background(Color(clr.hexColor), CircleShape)
                                                        .border(1.dp, palette.floatingBorder, CircleShape)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val ralTxt = if (clr.ralCode.isNotBlank()) " • RAL ${clr.ralCode}" else ""
                                                    Text(
                                                        text = "${clr.name}$ralTxt",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = palette.textPrimary
                                                    )
                                                    Text(
                                                        text = if (clr.priceMarkupPercent > 0.0) {
                                                            "Наценка на профиль: +${clr.priceMarkupPercent.roundToInt()}%"
                                                        } else {
                                                            "Базовый цвет (наценка 0%)"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = if (clr.priceMarkupPercent > 0.0) palette.accentOrange else palette.textSecondary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val nextColors = colorsList.filterNot { it.id == clr.id }
                                                        colorsList = nextColors
                                                        val updatedSys = buildDraftSystem(overrideColors = nextColors)
                                                        onSaveProfiles(profileSystems.map { if (it.id == updatedSys.id) updatedSys else it })
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("profile_delete_color_${clr.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Удалить цвет ${clr.name}",
                                                        tint = palette.accentRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showAddColorDialog) {
                            AddProfileColorDialog(
                                onDismiss = { showAddColorDialog = false },
                                onAddColor = { newColor ->
                                    val nextColors = colorsList + newColor
                                    colorsList = nextColors
                                    val updatedSys = buildDraftSystem(overrideColors = nextColors)
                                    onSaveProfiles(profileSystems.map { if (it.id == updatedSys.id) updatedSys else it })
                                    showAddColorDialog = false
                                    savedBannerText = "Цвет «${newColor.name}» добавлен в систему"
                                }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = stockBarLen,
                                onValueChange = { stockBarLen = it },
                                label = { Text("Длина хлыста профиля (мм)") },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = weldAllow,
                                onValueChange = { weldAllow = it },
                                label = { Text("Припуск на сварку угла (мм)") },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = sawKerfMmText,
                            onValueChange = { sawKerfMmText = it },
                            label = { Text("Толщина диска пилы / рез (мм)") },
                            supportingText = {
                                Text(
                                    "Учитывается при 1D-раскрое профилей между соседними заготовками (напр. 4 или 5 мм)",
                                    fontSize = 11.sp
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_saw_kerf_input"),
                            singleLine = true
                        )

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
                            color = palette.accentTeal.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, palette.accentTeal.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = glzClearanceTotal,
                                    onValueChange = { glzClearanceTotal = it },
                                    label = { Text("Зазор на стеклопакет (отнимается от проёма, напр. 10 мм или 14 мм)") },
                                    shape = RoundedCornerShape(12.dp),
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
                                    color = palette.textPrimary,
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
                    color = palette.textSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                ProfileAccordionSection.entries.forEach { section ->
                    val isExp = expandedSection == section
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(0.8.dp, if (isExp) palette.accentBlue.copy(alpha = 0.5f) else palette.floatingBorder),
                        shadowElevation = if (palette.isDark) 0.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Заголовок элемента аккордеона
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isExp) palette.accentBlue.copy(alpha = 0.1f) else palette.cardSurface)
                                    .clickable {
                                        expandedSection = if (isExp) null else section
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = when (section) {
                                            ProfileAccordionSection.CLEATS_SETS -> palette.accentGreen
                                            ProfileAccordionSection.RUBBER_GASKETS -> palette.accentTeal
                                            else -> palette.accentBlue
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(34.dp)
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
                                            fontSize = 14.5.sp,
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = section.subtitleRu,
                                            fontSize = 11.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = if (isExp) "▲" else "▼",
                                    color = palette.accentBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isExp) {
                                HorizontalDivider(color = palette.divider, thickness = 0.7.dp)
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
                                                color = palette.secondaryBackground,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Габариты двух конструкций при угловом соединении задаются по готовым размерам замерного листа. Для угловика в расчётах сметы используется стоимость за 1 погонный метр:",
                                                    fontSize = 11.5.sp,
                                                    color = palette.textSecondary,
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
                                                color = palette.accentBlue.copy(alpha = 0.09f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(0.8.dp, palette.accentBlue.copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = "Формула подбора штапика: СП = Посадочное место (A4=${fmtMm(seatMm)} мм) − Ширина штапика (H2) − Резина (${fmtMm(rubMm)} мм)",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = palette.accentBlue
                                                    )
                                                    Text(
                                                        text = if (matType == ProfileMaterialType.ALUMINUM) {
                                                            "Правило распила штапиков для АЛЮМИНИЯ (90°/90°):\n• Горизонтальные штапики = Ширина проёма\n• Вертикальные штапики = Высота проёма − 2 × H1 (две ширины горизонтального штапика)"
                                                        } else {
                                                            "Правило распила штапиков для ПВХ (45°/45°):\n• Горизонтальные = Ширина проёма | Вертикальные = Высота проёма"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = palette.textPrimary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    OutlinedTextField(
                                                        value = rubberAllowMm,
                                                        onValueChange = { rubberAllowMm = it },
                                                        label = { Text("Суммарная толщина резины (упорная + клиновая, обычно 4+4 = 8 мм)") },
                                                        shape = RoundedCornerShape(12.dp),
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
                                                    color = palette.textPrimary
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text("+ Штапик", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }

                                            shtapikList.forEachIndexed { idx, item ->
                                                Surface(
                                                    color = palette.secondaryBackground.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = BorderStroke(0.8.dp, palette.floatingBorder),
                                                    modifier = Modifier.fillMaxWidth()
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
                                                                color = palette.textPrimary
                                                            )
                                                            if (shtapikList.size > 1) {
                                                                IconButton(
                                                                    onClick = {
                                                                        shtapikList = shtapikList.filterIndexed { i, _ -> i != idx }
                                                                    },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                                        contentDescription = "Удалить штапик",
                                                                        tint = palette.accentRed,
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
                                                                shape = RoundedCornerShape(10.dp),
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
                                                                shape = RoundedCornerShape(10.dp),
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
                                                                shape = RoundedCornerShape(10.dp),
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
                                                            shape = RoundedCornerShape(10.dp),
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
                                                color = palette.accentGreen.copy(alpha = 0.11f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(0.8.dp, palette.accentGreen.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "Автоматический расчёт комплектов сухарей в конструкции:",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp,
                                                        color = palette.accentGreen
                                                    )
                                                    Text(
                                                        text = "• 1 рама = 1 комплект сухарей рамы (на 4 угла)\n" +
                                                            "• 1 импост = 1 комплект сухарей импоста (на 2 торца)\n" +
                                                            "• 1 створка = 1 комплект створочных сухарей (на 4 угла)\n" +
                                                            "• 1 дверь = 1 комплект дверных сухарей (на 4 угла)",
                                                        fontSize = 11.5.sp,
                                                        color = palette.textPrimary
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

                                        ProfileAccordionSection.RUBBER_GASKETS -> {
                                            ProfileRubberSectionContent(
                                                catalog = rubberCatalogState,
                                                expandedIndex = expandedRubberRow,
                                                currencySymbol = currencySymbol,
                                                onToggleExpand = { idx ->
                                                    expandedRubberRow = if (expandedRubberRow == idx) null else idx
                                                },
                                                onUpdateCatalog = { updated ->
                                                    rubberCatalogState = updated
                                                }
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
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        "${strings.saveBtn}: «$name»",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// =========================================================================================
// ДИАЛОГ ДОБАВЛЕНИЯ ЦВЕТА В КАТАЛОГ ПРОФИЛЬНОЙ СИСТЕМЫ
// =========================================================================================
@Composable
private fun AddProfileColorDialog(
    onDismiss: () -> Unit,
    onAddColor: (ProfileColorSpec) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    val strings = LocalAppStrings.current
    val quickPresets = remember {
        listOf(
            Triple("Антрацит", "7016", 0xFF383E42L),
            Triple("Белый", "9016", 0xFFFFFFFFL),
            Triple("Анодированный алюминий", "E6/EV1", 0xFFA8A8A8L),
            Triple("Чёрный", "9005", 0xFF1F2124L)
        )
    }
    val extraSwatches = remember {
        listOf(
            0xFF383E42L, 0xFFFFFFFFL, 0xFFA8A8A8L, 0xFF1F2124L,
            0xFF474A51L, 0xFF442F29L, 0xFF8B5A2BL, 0xFFD32F2FL,
            0xFF154889L, 0xFF1E5945L, 0xFFE6D2B5L, 0xFFC0C0C0L
        )
    }

    var name by remember { mutableStateOf("") }
    var ralCode by remember { mutableStateOf("") }
    var selectedHex by remember { mutableLongStateOf(0xFF383E42L) }
    var markupStr by remember { mutableStateOf("0") }

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
                    text = "Добавить цвет профильной системы",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Text(
                    text = "Быстрый выбор популярных цветов:",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPresets.forEach { (presetName, presetRal, presetHex) ->
                        FilterChip(
                            selected = name == presetName && selectedHex == presetHex,
                            onClick = {
                                name = presetName
                                ralCode = presetRal
                                selectedHex = presetHex
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(presetHex), CircleShape)
                                        .border(0.8.dp, palette.floatingBorder, CircleShape)
                                )
                            },
                            label = {
                                Text("$presetName ($presetRal)", fontSize = 11.5.sp)
                            }
                        )
                    }
                }

                Text(
                    text = "Оттенок на чертеже:",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    extraSwatches.forEach { hex ->
                        val picked = selectedHex == hex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedHex = hex }
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

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название цвета") },
                    placeholder = { Text("Напр. Серый") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_profile_color_name"),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ralCode,
                        onValueChange = { ralCode = it },
                        label = { Text("Код RAL / E6") },
                        placeholder = { Text("7016") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_profile_color_ral"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = markupStr,
                        onValueChange = { markupStr = it },
                        label = { Text("Наценка (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_profile_color_markup"),
                        singleLine = true
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
                            if (name.isNotBlank()) {
                                val markup = markupStr.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                onAddColor(
                                    ProfileColorSpec(
                                        id = "col_${UUID.randomUUID().toString().take(6)}",
                                        name = name.trim(),
                                        ralCode = ralCode.trim(),
                                        hexColor = selectedHex,
                                        priceMarkupPercent = markup
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("add_profile_color_confirm")
                    ) {
                        Text(strings.saveBtn, color = Color.White, fontWeight = FontWeight.Bold)
                    }
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
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.secondaryBackground.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(6.dp)
            ) {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                )
            }
            Text(
                text = caption,
                fontSize = 11.5.sp,
                color = palette.textSecondary,
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
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.secondaryBackground.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.floatingBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = palette.accentBlue,
                shape = RoundedCornerShape(10.dp),
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
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(unitText, fontSize = 10.5.sp) },
                shape = RoundedCornerShape(10.dp),
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
    val palette = LocalWinStudioPalette.current
    val ov = parseMmOrNull(value) ?: 6f
    val addBothSides = ov * 2f
    val sampleW = 800f + addBothSides
    val sampleH = 1000f + addBothSides
    Surface(
        color = palette.accentOrange.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.accentOrange.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        color = palette.accentOrange
                    )
                    Text(
                        text = "При нахлёсте ${fmtMm(ov)} мм с каждой стороны (+${fmtMm(addBothSides)} мм к проёму): для проёма 800×1000 мм заготовки в раскрой будут ${fmtMm(sampleW)}×${fmtMm(sampleH)} мм",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("мм") },
                    shape = RoundedCornerShape(10.dp),
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
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.accentGreen.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, palette.accentGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = palette.accentGreen,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Цена") },
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

@Composable
private fun ProfileRubberSectionContent(
    catalog: ProfileRubberCatalog,
    expandedIndex: Int?,
    currencySymbol: String,
    onToggleExpand: (Int) -> Unit,
    onUpdateCatalog: (ProfileRubberCatalog) -> Unit
) {
    val palette = LocalWinStudioPalette.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1. Резина для глухаря
        RubberAccordionRowCard(
            rowIndex = 0,
            isExpanded = expandedIndex == 0,
            title = "1. Резина для глухаря",
            subtitle = "${String.format(Locale.US, "%.0f", catalog.fixedGlazing.metersPerBox)} м в кор. • ${String.format(Locale.US, "%.2f", catalog.fixedGlazing.pricePerMeter)} $currencySymbol/м",
            crossSectionResId = R.drawable.gasket_fixed,
            schemeResId = R.drawable.gasket_scheme_fixed,
            schemeCaption = "Место установки: наружная упорная резина стеклопакета",
            testTag = "rubber_row_fixed",
            onToggle = { onToggleExpand(0) }
        ) {
            RubberSingleBoxEditor(
                spec = catalog.fixedGlazing,
                currencySymbol = currencySymbol,
                showThicknessField = true,
                testTagPrefix = "rubber_fixed",
                onUpdate = { updated ->
                    onUpdateCatalog(catalog.copy(fixedGlazing = updated))
                }
            )
        }

        // 2. Клиновая резина (поддерживает несколько толщин клина)
        val wedgeSummaryText = catalog.wedgeVariants.joinToString(" / ") { "${fmtMm(it.thicknessMm)}мм" }
        RubberAccordionRowCard(
            rowIndex = 1,
            isExpanded = expandedIndex == 1,
            title = "2. Клиновая резина (${catalog.wedgeVariants.size} вида)",
            subtitle = "Толщины: $wedgeSummaryText • автоподбор под штапик и СП",
            crossSectionResId = R.drawable.gasket_wedge,
            schemeResId = R.drawable.gasket_scheme_wedge,
            schemeCaption = "Место установки: внутренний клин со стороны штапика",
            testTag = "rubber_row_wedge",
            onToggle = { onToggleExpand(1) }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = palette.accentBlue.copy(alpha = 0.09f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.7.dp, palette.accentBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Программа автоматически выбирает нужный клин по формуле:\nЗазор = Посадочное место (A4/C3) − Штапик (H2) − Наружная резина − Толщина СП",
                        fontSize = 11.sp,
                        color = palette.accentBlue,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Виды клиновой резины (${catalog.wedgeVariants.size}):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = palette.textPrimary
                    )
                    Button(
                        onClick = {
                            val nextThick = ((catalog.wedgeVariants.maxOfOrNull { it.thicknessMm } ?: 3.6f) + 0.5f)
                            val newWedge = WedgeRubberVariantSpec(
                                id = "wedge_${UUID.randomUUID().toString().take(5)}",
                                code = "",
                                name = "Клин ${fmtMm(nextThick)} мм",
                                thicknessMm = nextThick,
                                metersPerBox = 200.0,
                                pricePerBox = 120.0
                            )
                            onUpdateCatalog(catalog.copy(wedgeVariants = catalog.wedgeVariants + newWedge))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accentBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_wedge_variant_button")
                    ) {
                        Text("+ Клин", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                catalog.wedgeVariants.forEachIndexed { idx, wItem ->
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, palette.floatingBorder),
                        modifier = Modifier.fillMaxWidth()
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
                                    text = "Клин #${idx + 1}: ${wItem.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = palette.textPrimary
                                )
                                if (catalog.wedgeVariants.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val updated = catalog.wedgeVariants.filterIndexed { i, _ -> i != idx }
                                            onUpdateCatalog(catalog.copy(wedgeVariants = updated))
                                        },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Удалить клин",
                                            tint = palette.accentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            var thickInput by remember(wItem.id, wItem.thicknessMm) { mutableStateOf(fmtMm(wItem.thicknessMm)) }
                            var metersInput by remember(wItem.id, wItem.metersPerBox) {
                                mutableStateOf(fmtBoxNum(wItem.metersPerBox))
                            }
                            var boxPriceInput by remember(wItem.id, wItem.pricePerBox) {
                                mutableStateOf(fmtBoxNum(wItem.pricePerBox))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = thickInput,
                                    onValueChange = { raw ->
                                        thickInput = raw
                                        val parsed = parseMmOrNull(raw)?.coerceIn(0.5f, 15f)
                                        if (parsed != null) {
                                            val next = catalog.wedgeVariants.toMutableList().also {
                                                it[idx] = wItem.copy(
                                                    thicknessMm = parsed,
                                                    name = "Клин ${fmtMm(parsed)} мм"
                                                )
                                            }
                                            onUpdateCatalog(catalog.copy(wedgeVariants = next))
                                        }
                                    },
                                    label = { Text("Толщина (мм)", fontSize = 10.5.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wedge_thick_input_$idx")
                                )
                                OutlinedTextField(
                                    value = metersInput,
                                    onValueChange = { raw ->
                                        metersInput = raw
                                        val parsed = raw.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0)
                                        if (parsed != null) {
                                            val next = catalog.wedgeVariants.toMutableList().also {
                                                it[idx] = wItem.copy(metersPerBox = parsed)
                                            }
                                            onUpdateCatalog(catalog.copy(wedgeVariants = next))
                                        }
                                    },
                                    label = { Text("М в кор.", fontSize = 10.5.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wedge_meters_input_$idx")
                                )
                                OutlinedTextField(
                                    value = boxPriceInput,
                                    onValueChange = { raw ->
                                        boxPriceInput = raw
                                        val parsed = raw.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0)
                                        if (parsed != null) {
                                            val next = catalog.wedgeVariants.toMutableList().also {
                                                it[idx] = wItem.copy(pricePerBox = parsed)
                                            }
                                            onUpdateCatalog(catalog.copy(wedgeVariants = next))
                                        }
                                    },
                                    label = { Text("Цена кор.", fontSize = 10.5.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wedge_price_input_$idx")
                                )
                            }

                            Surface(
                                color = palette.accentGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Цена за 1 м (${wItem.name}): ${String.format(Locale.US, "%.2f", wItem.pricePerMeter)} $currencySymbol/м",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = palette.accentGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Резина рамы на притвор
        RubberAccordionRowCard(
            rowIndex = 2,
            isExpanded = expandedIndex == 2,
            title = "3. Резина рамы на притвор",
            subtitle = "${String.format(Locale.US, "%.0f", catalog.frameRebate.metersPerBox)} м в кор. • ${String.format(Locale.US, "%.2f", catalog.frameRebate.pricePerMeter)} $currencySymbol/м",
            crossSectionResId = R.drawable.gasket_frame_rebate,
            schemeResId = R.drawable.gasket_scheme_frame_rebate,
            schemeCaption = "Место установки: наружный притвор рамы (акустический контур)",
            testTag = "rubber_row_frame_rebate",
            onToggle = { onToggleExpand(2) }
        ) {
            RubberSingleBoxEditor(
                spec = catalog.frameRebate,
                currencySymbol = currencySymbol,
                showThicknessField = false,
                testTagPrefix = "rubber_frame",
                onUpdate = { updated ->
                    onUpdateCatalog(catalog.copy(frameRebate = updated))
                }
            )
        }

        // 4. Резина створки / двери на притвор
        RubberAccordionRowCard(
            rowIndex = 3,
            isExpanded = expandedIndex == 3,
            title = "4. Резина створки/двери на притвор",
            subtitle = "${String.format(Locale.US, "%.0f", catalog.sashDoorRebate.metersPerBox)} м в кор. • ${String.format(Locale.US, "%.2f", catalog.sashDoorRebate.pricePerMeter)} $currencySymbol/м",
            crossSectionResId = R.drawable.gasket_sash_rebate,
            schemeResId = R.drawable.gasket_scheme_sash_rebate,
            schemeCaption = "Место установки: внутренний притвор створки / двери",
            testTag = "rubber_row_sash_rebate",
            onToggle = { onToggleExpand(3) }
        ) {
            RubberSingleBoxEditor(
                spec = catalog.sashDoorRebate,
                currencySymbol = currencySymbol,
                showThicknessField = false,
                testTagPrefix = "rubber_sash",
                onUpdate = { updated ->
                    onUpdateCatalog(catalog.copy(sashDoorRebate = updated))
                }
            )
        }
    }
}

private fun fmtBoxNum(v: Double): String {
    return if (v % 1.0 == 0.0) {
        v.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.2f", v)
    }
}

@Composable
private fun RubberAccordionRowCard(
    rowIndex: Int,
    isExpanded: Boolean,
    title: String,
    subtitle: String,
    crossSectionResId: Int,
    schemeResId: Int,
    schemeCaption: String,
    testTag: String,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val palette = LocalWinStudioPalette.current
    Surface(
        color = palette.secondaryBackground.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isExpanded) 1.dp else 0.7.dp,
            color = if (isExpanded) palette.accentTeal.copy(alpha = 0.55f) else palette.floatingBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag(testTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(0.7.dp, palette.floatingBorder, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = crossSectionResId),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = palette.accentTeal,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    color = palette.accentTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = palette.divider, thickness = 0.6.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.7.dp, palette.floatingBorder),
                            modifier = Modifier.weight(0.42f)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = crossSectionResId),
                                    contentDescription = "Сечение: $title",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                )
                                Text(
                                    text = "Сечение",
                                    fontSize = 10.sp,
                                    color = Color(0xFF5A6577)
                                )
                            }
                        }
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.7.dp, palette.floatingBorder),
                            modifier = Modifier.weight(0.58f)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = schemeResId),
                                    contentDescription = schemeCaption,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                )
                                Text(
                                    text = "Узел установки",
                                    fontSize = 10.sp,
                                    color = Color(0xFF5A6577)
                                )
                            }
                        }
                    }
                    Text(
                        text = schemeCaption,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun RubberSingleBoxEditor(
    spec: RubberBoxSpec,
    currencySymbol: String,
    showThicknessField: Boolean,
    testTagPrefix: String,
    onUpdate: (RubberBoxSpec) -> Unit
) {
    val palette = LocalWinStudioPalette.current
    var metersInput by remember(spec.name, spec.metersPerBox) { mutableStateOf(fmtBoxNum(spec.metersPerBox)) }
    var boxPriceInput by remember(spec.name, spec.pricePerBox) { mutableStateOf(fmtBoxNum(spec.pricePerBox)) }
    var thickInput by remember(spec.name, spec.thicknessMm) { mutableStateOf(fmtMm(spec.thicknessMm)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showThicknessField) {
                OutlinedTextField(
                    value = thickInput,
                    onValueChange = { raw ->
                        thickInput = raw
                        val parsed = parseMmOrNull(raw)?.coerceIn(1f, 12f)
                        if (parsed != null) onUpdate(spec.copy(thicknessMm = parsed))
                    },
                    label = { Text("Толщина (мм)", fontSize = 10.5.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("${testTagPrefix}_thick_input")
                )
            }
            OutlinedTextField(
                value = metersInput,
                onValueChange = { raw ->
                    metersInput = raw
                    val parsed = raw.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0)
                    if (parsed != null) onUpdate(spec.copy(metersPerBox = parsed))
                },
                label = { Text("Метров в коробке", fontSize = 10.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_meters_input")
            )
            OutlinedTextField(
                value = boxPriceInput,
                onValueChange = { raw ->
                    boxPriceInput = raw
                    val parsed = raw.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0)
                    if (parsed != null) onUpdate(spec.copy(pricePerBox = parsed))
                },
                label = { Text("Цена за коробку ($currencySymbol)", fontSize = 10.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_price_input")
            )
        }

        Surface(
            color = palette.accentGreen.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.7.dp, palette.accentGreen.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Расчётная цена за 1 пог. метр: ${String.format(Locale.US, "%.2f", spec.pricePerMeter)} $currencySymbol/м",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = palette.accentGreen,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("${testTagPrefix}_price_per_m")
            )
        }
    }
}
