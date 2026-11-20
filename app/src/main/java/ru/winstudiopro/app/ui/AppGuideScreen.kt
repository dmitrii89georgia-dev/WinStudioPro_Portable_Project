package ru.winstudiopro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.winstudiopro.app.domain.model.AppLanguage
import ru.winstudiopro.app.ui.components.IosGroupedCard
import ru.winstudiopro.app.ui.components.IosTopAppBar
import ru.winstudiopro.app.ui.i18n.LocalAppStrings
import ru.winstudiopro.app.ui.theme.LocalWinStudioPalette

private data class GuideStep(
    val title: String,
    val body: String
)

private data class GuideSection(
    val id: String,
    val number: Int,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val accent: Color,
    val steps: List<GuideStep>
)

@Composable
fun AppGuideScreen(
    onBack: () -> Unit
) {
    val p = LocalWinStudioPalette.current
    val s = LocalAppStrings.current
    val isEn = s.language == AppLanguage.EN

    val sections = remember(isEn) { buildGuideSections(isEn) }
    var expandedIds by remember(sections) { mutableStateOf(setOf(sections.first().id)) }
    val allExpanded = expandedIds.size == sections.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IosTopAppBar(
            title = s.guideScreenTitle,
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = {
                        expandedIds = if (allExpanded) emptySet() else sections.map { it.id }.toSet()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("guide_toggle_all_btn")
                ) {
                    Text(
                        text = if (allExpanded) s.guideCollapseAll else s.guideExpandAll,
                        color = p.accentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("guide_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                IosGroupedCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(p.accentBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = s.guideScreenTitle,
                                tint = p.accentBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.guideIntroTitle,
                                color = p.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = s.guideIntroBody,
                                color = p.textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            itemsIndexed(sections, key = { _, item -> item.id }) { _, section ->
                val isExpanded = expandedIds.contains(section.id)
                val arrowRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "guideChevron_${section.id}"
                )

                IosGroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guide_section_${section.id}"),
                    onClick = {
                        expandedIds = if (isExpanded) {
                            expandedIds - section.id
                        } else {
                            expandedIds + section.id
                        }
                    },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(section.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.title,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = section.accent.copy(alpha = 0.14f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${section.number}",
                                        color = section.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = section.title,
                                    color = p.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = section.summary,
                                color = p.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = p.textSecondary,
                            modifier = Modifier.rotate(arrowRotation)
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = p.divider, thickness = 0.7.dp)
                            section.steps.forEach { step ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(p.background.copy(alpha = 0.65f))
                                        .border(0.6.dp, p.floatingBorder, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = step.title,
                                        color = section.accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = step.body,
                                        color = p.textPrimary,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private fun buildGuideSections(isEn: Boolean): List<GuideSection> {
    if (isEn) {
        return listOf(
            GuideSection(
                id = "home",
                number = 1,
                title = "Home Screen & Quick Start",
                summary = "Instant new order, recent projects, and private quick cash actions",
                icon = Icons.Rounded.Home,
                accent = Color(0xFF0A84FF),
                steps = listOf(
                    GuideStep(
                        title = "New Order in 1 tap",
                        body = "Tap «New Order» to immediately open a 1500×1500 mm frame in the 2D CAD editor without filling out forms first. Client details can be entered anytime."
                    ),
                    GuideStep(
                        title = "Recent Orders & Catalogs",
                        body = "The top row shows your latest and previous orders with live thumbnails. Below are quick tiles for Orders, Profiles, Prices, Warehouse, Cashbox, Analytics, and Settings."
                    ),
                    GuideStep(
                        title = "Private Quick Actions",
                        body = "The 3 pinned buttons at the bottom (+ Income, − Expense, ⇄ Transfer) record cash operations in seconds without revealing balances on the Home Screen."
                    )
                )
            ),
            GuideSection(
                id = "cad",
                number = 2,
                title = "2D Window & Door CAD",
                summary = "Dimensions, mullions, opening types, sandwich panels, and copies",
                icon = Icons.Rounded.GridView,
                accent = Color(0xFF30B0C7),
                steps = listOf(
                    GuideStep(
                        title = "Resizing & Mullions",
                        body = "Tap any overall or section dimension line on the canvas to enter exact millimeters on the numpad. Use «Mullions» to add/remove splits or equalize bays by axis or clear opening."
                    ),
                    GuideStep(
                        title = "Openings & Doors",
                        body = "Tap inside any glazed cell to pick Fixed, Turn, Tilt-Turn, Transom, Door profile (including outward swing), Sandwich panel, Mosquito net, or a horizontal sash transom."
                    ),
                    GuideStep(
                        title = "Multiple Windows & Undo",
                        body = "Add new constructions or duplicate existing ones with «Copy». Undo/Redo arrows let you safely step through edits."
                    )
                )
            ),
            GuideSection(
                id = "pricing",
                number = 3,
                title = "Window Settings & Turnkey Pricing",
                summary = "Profile systems, glazing, assembly schemes, markup, and discounts",
                icon = Icons.Rounded.Payments,
                accent = Color(0xFF34C759),
                steps = listOf(
                    GuideStep(
                        title = "Right Settings Drawer",
                        body = "Tap the gear icon inside CAD to select profile system, glazing bead, glass unit (with 0/5 mm rounding), color or custom painting, hardware brand, accessories, installation, workshop/floor assembly, and order-level extra services."
                    ),
                    GuideStep(
                        title = "Fin Scheme & Prime Cost Control",
                        body = "Base sale prices use the multiplier from Prices → Fin Scheme (1.40 by default) over total prime cost. Applying a custom price or high discount triggers a «below prime cost!» alert if profit turns negative."
                    )
                )
            ),
            GuideSection(
                id = "profiles",
                number = 4,
                title = "Profiles, Gaskets & Price Lists",
                summary = "Custom profile geometry, 4 gasket types, and 3 assembly formulas",
                icon = Icons.Rounded.Architecture,
                accent = Color(0xFF5856D6),
                steps = listOf(
                    GuideStep(
                        title = "Profile Constructor",
                        body = "Configure visible widths (frame, mullion, sash, bead), welding allowances, stock bar length, colors, cleats, and the 4 rubber gasket rows (enter box meters and price per box to auto-compute $/m)."
                    ),
                    GuideStep(
                        title = "Prices Catalogs",
                        body = "Maintain rates for Profiles, Glazing, Hardware, Accessories, Installation, Assembly (per operation / per m² / per sash), Order Services, and Financial Scheme."
                    )
                )
            ),
            GuideSection(
                id = "warehouse",
                number = 5,
                title = "Orders, Statuses & Warehouse",
                summary = "Deadline tracking and automatic stock deduction on Production",
                icon = Icons.Rounded.Inventory2,
                accent = Color(0xFFFF9500),
                steps = listOf(
                    GuideStep(
                        title = "Statuses & Deadlines",
                        body = "Track every order from Draft through Measurement, Approval, Contract, Production, Ready, Installation, and Closed. Contract dates show remaining or overdue days."
                    ),
                    GuideStep(
                        title = "Auto Stock Deduction",
                        body = "Moving an order to «Production» automatically deducts required profiles (reusing matching remnants first, then full bars) and gasket meters, placing leftover cutoffs back into usable remnants."
                    )
                )
            ),
            GuideSection(
                id = "pdf",
                number = 6,
                title = "PDF Reports, Cutting & Procurement",
                summary = "Compact 2-per-row proposals, 1D cutting optimization, glass & hardware orders",
                icon = Icons.Rounded.PictureAsPdf,
                accent = Color(0xFF1E88E5),
                steps = listOf(
                    GuideStep(
                        title = "Client PDFs",
                        body = "From the left CAD menu generate Drawings, Detailed Drawings, Commercial Proposal (2 constructions per row with clear opening dimensions, materials, and 70/30 payment terms), or Installation Drawings."
                    ),
                    GuideStep(
                        title = "Workshop & Supplier Orders",
                        body = "Open «Cutting Plan» for 1D bar optimization, manual bar length overrides, and supplier PDF (profiles, cleats, gaskets). Use «Glazing Order» and «Hardware Order» editors to review/check items before PDF export."
                    )
                )
            ),
            GuideSection(
                id = "cash_analytics",
                number = 7,
                title = "Cashbox, Payroll & Analytics",
                summary = "Multicurrency accounts (USD/GEL), piecework pay, and production KPIs",
                icon = Icons.Rounded.AccountBalanceWallet,
                accent = Color(0xFF8E24AA),
                steps = listOf(
                    GuideStep(
                        title = "Cashbox & Payroll",
                        body = "Manage USD/GEL accounts, filter ledger history, customize production expense categories, and track piecework Accrued / Paid / Remaining amounts for workshop assembly, installation, and dividends."
                    ),
                    GuideStep(
                        title = "Production Pulse Analytics",
                        body = "Switch between 6-month Revenue Bar Chart and Margin Structure Donut, and monitor active orders, contract receivables, obligations, and Free Money."
                    )
                )
            ),
            GuideSection(
                id = "backup",
                number = 8,
                title = "Settings & Full Backup",
                summary = "Theme, language, company PDF header, and JSON export/import",
                icon = Icons.Rounded.Backup,
                accent = Color(0xFF636366),
                steps = listOf(
                    GuideStep(
                        title = "Export & Restore",
                        body = "Save your entire database (.winbackup.json) or share single orders (.json) before switching devices or reinstalling."
                    )
                )
            )
        )
    }

    return listOf(
        GuideSection(
            id = "home",
            number = 1,
            title = "Главный экран и быстрый старт",
            summary = "Создание заказа в 1 нажатие, последние проекты и быстрые кнопки кассы",
            icon = Icons.Rounded.Home,
            accent = Color(0xFF0A84FF),
            steps = listOf(
                GuideStep(
                    title = "1. Мгновенный запуск нового заказа",
                    body = "Нажмите большую карточку «Новый заказ» — программа сразу откроет 2D-чертёжник с готовой рамой 1500×1500 мм. Данные клиента, адрес и телефон можно заполнить позже через кнопку «Клиент» внутри чертежа."
                ),
                GuideStep(
                    title = "2. Последние заказы и разделы",
                    body = "Под кнопкой «Новый заказ» отображаются «Последний заказ» и «Предпоследний» с миниатюрами. Ниже расположены плитки разделов: Заказы, Профили, Прайсы, Склад, Касса, Аналитика и Настройки."
                ),
                GuideStep(
                    title = "3. Приватные быстрые операции внизу",
                    body = "В нижней части главного экрана закреплены 3 кнопки: «+ Приход», «− Расход» и «⇄ Перевод». Суммы на счетах скрыты на главном экране — увидеть балансы можно только войдя в раздел «Касса»."
                )
            )
        ),
        GuideSection(
            id = "cad",
            number = 2,
            title = "2D-конструктор окон и дверей",
            summary = "Размеры в мм, импосты, типы открывания, двери, сэндвич и копирование",
            icon = Icons.Rounded.GridView,
            accent = Color(0xFF30B0C7),
            steps = listOf(
                GuideStep(
                    title = "1. Изменение габаритов и размеров секций",
                    body = "Нажмите прямо на любую размерную линию (общую ширину/высоту или размер секции) — откроется цифровая клавиатура для точного ввода в миллиметрах."
                ),
                GuideStep(
                    title = "2. Добавление и выравнивание импостов",
                    body = "Кнопка «Импосты» позволяет разделить конструкцию по вертикали или горизонтали, а также выровнять секции по осям или по световым проёмам («Способ разбиения импостов»)."
                ),
                GuideStep(
                    title = "3. Настройка створок, дверей и заполнения",
                    body = "Нажмите внутри нужной ячейки чертежа: выберите глухое окно, поворотное, поворотно-откидное, фрамугу, дверной профиль (в том числе с обратной створкой наружу), сэндвич-панель или москитную сетку. Для створок и дверей можно задать горизонтальный импост на нужной высоте от низа."
                ),
                GuideStep(
                    title = "4. Несколько окон в заказе и отмена действий",
                    body = "В верхней полосе заказа можно добавлять новые конструкции, переключаться между ними и дублировать готовые кнопкой «Копия». Стрелки отмены и повтора позволяют безопасно откатывать правки."
                )
            )
        ),
        GuideSection(
            id = "pricing",
            number = 3,
            title = "Шторка окна и расчёт цены «под ключ»",
            summary = "Выбор системы, пакета, цвета, сборки, скидки и контроль себестоимости",
            icon = Icons.Rounded.Payments,
            accent = Color(0xFF34C759),
            steps = listOf(
                GuideStep(
                    title = "1. Настройки конструкции (правая шторка ⚙)",
                    body = "Нажмите на значок шестерёнки на верхней панели редактора: выберите оконную систему, штапик, стеклопакет (при необходимости включите «Округл. 0/5 мм»), цвет или индивидуальную покраску, марку фурнитуры, доп. комплектующие («Разное»), монтаж, схему сборки в цеху и количество конструкций для сборки на этаже."
                ),
                GuideStep(
                    title = "2. Применение ко всем окнам и услуги заказа",
                    body = "Кнопка «Применить ко всем окнам» переносит выбранные материалы сразу на все изделия в заказе. Блок «Дополнительные услуги заказа» (например, доставка) учитывается на весь заказ целиком."
                ),
                GuideStep(
                    title = "3. Наценка «Фин схемы», скидка, НДС и своя цена",
                    body = "Продажная цена рассчитывается как общая себестоимость заказа × коэффициент наценки (по умолчанию 1.40 из Прайсов → «Фин схема»). В шторке можно быстро выбрать скидку чипами, задать НДС или ввести «Свою цену» вручную — если цена уйдёт в минус от себестоимости, появится предупреждение «ниже себест.!»."
                ),
                GuideStep(
                    title = "4. Нижняя расчётная лента",
                    body = "Внизу экрана отображается разбивка: верхняя строка — по текущему окну, нижняя — по всему заказу (всего продажа, площадь, профили, резина, стёкла/сэндвич, фурнитура, сборка, монтаж, доп. услуги и себестоимость)."
                )
            )
        ),
        GuideSection(
            id = "profiles",
            number = 4,
            title = "Профили, резина и каталоги «Прайсы»",
            summary = "Геометрия систем, расчёт резины по коробкам, монтаж, сборка и фин. схема",
            icon = Icons.Rounded.Architecture,
            accent = Color(0xFF5856D6),
            steps = listOf(
                GuideStep(
                    title = "1. Конструктор профилей и уплотнительной резины",
                    body = "В разделе «Профили» задаются видимые высоты (рамы, импоста, створки, штапика), припуски на сварку/запил, длина хлыста, цены, цвета и сухари. В самом низу списка находится блок «Резина (уплотнители)»: 4 типа (для глухаря, клин разной толщины, притвор рамы, притвор створки/двери). Укажите метров в коробке и цену за коробку — цена за 1 метр рассчитается автоматически."
                ),
                GuideStep(
                    title = "2. Прайсы: Сборка, Монтаж, Услуги и Фин схема",
                    body = "В разделе «Прайсы» доступны вкладки: Профили, Стеклопакеты, Фурнитура, Разное, Монтаж, Сборка (3 схемы расчёта: 1 — по операциям/узлам, 2 — за м², 3 — за створку), Доп. услуги заказа и «Фин схема» (коэффициент наценки, курс USD/GEL и пресеты скидок)."
                )
            )
        ),
        GuideSection(
            id = "warehouse",
            number = 5,
            title = "Заказы, статусы и Склад",
            summary = "Фильтрация, сроки договоров и автосписание материала в производство",
            icon = Icons.Rounded.Inventory2,
            accent = Color(0xFFFF9500),
            steps = listOf(
                GuideStep(
                    title = "1. Список заказов, поиск и статусы",
                    body = "В разделе «Заказы» ищите заказы по номеру, ФИО клиента, телефону или адресу, фильтруйте по статусам (Черновик, Замер, Согласование, Договор, Производство, Готов, Монтаж, Закрыт, Отменён) и следите за оставшимися днями по дате договора."
                ),
                GuideStep(
                    title = "2. Склад и автоматическое списание",
                    body = "На «Складе» хранятся целые хлысты, деловые остатки профилей (по системам и цветам) и метры уплотнительной резины. При переводе заказа в статус «Производство» программа автоматически (однократно) списывает нужные отрезки (сначала деловые остатки, затем целые хлысты), списывает резину и возвращает новые обрезки на склад."
                )
            )
        ),
        GuideSection(
            id = "pdf",
            number = 6,
            title = "PDF-отчёты, раскрой и заказы поставщикам",
            summary = "Компактное КП (2 окна в ряд), цех, раскрой 1D, стеклопакеты и фурнитура",
            icon = Icons.Rounded.PictureAsPdf,
            accent = Color(0xFF1E88E5),
            steps = listOf(
                GuideStep(
                    title = "1. Отчёты для клиента (левое «Меню» в чертеже)",
                    body = "Генерируйте в PDF: «Чертежи», «Чертежи с подробностями», «Коммерческое предложение» (компактная сетка 2 конструкции в ряд с номерами №1, №2…, миниатюрами с просветами, материалами и ценами «под ключ» + условия оплаты 70/30) и «Чертежи для монтажа»."
                ),
                GuideStep(
                    title = "2. Задание в цех и 1D-раскрой профиля",
                    body = "В «Раскрое» программа оптимально раскладывает заготовки по хлыстам и деловым остаткам. Длину любого хлыста можно изменить вручную. Внизу выберите нужные профили, закладные/сухари и резину и нажмите «Сформировать заказ профиля (PDF)»."
                ),
                GuideStep(
                    title = "3. Заказ стеклопакетов и фурнитуры",
                    body = "Пункты «Заказ стеклопакетов» и «Заказ фурнитуры» открывают редакторы позиций перед выгрузкой PDF: можно изменить формулу пакета, отметить нужные позиции галочками или нажать «Выбрать все», после чего сформировать готовый PDF с датой."
                )
            )
        ),
        GuideSection(
            id = "cash_analytics",
            number = 7,
            title = "Касса (Мани-менеджер) и Аналитика",
            summary = "Мультивалютные счета, сдельная зарплата и графики «Пульс производства»",
            icon = Icons.Rounded.AccountBalanceWallet,
            accent = Color(0xFF8E24AA),
            steps = listOf(
                GuideStep(
                    title = "1. Счета, переводы и категории расходов",
                    body = "Внутри «Кассы» 3 вкладки: «Счета» (балансы в USD и GEL, общий эквивалент по курсу), «История» (фильтрация по типу и счёту, редактирование и удаление операций) и «Настройки» (управление счетами и списком производственных статей расхода)."
                ),
                GuideStep(
                    title = "2. Сдельная зарплата и учёт по заказам",
                    body = "Кнопка «Зарплаты» в шапке Кассы показывает по каждому заказу в статусах «Договор / Производство / Готов / Монтаж»: Начислено, Выплачено и Остаток по цеховой сборке, монтажу и дивидендам. Кнопки «+ Приход» позволяют привязать оплату к заказу с показом остатка долга клиента."
                ),
                GuideStep(
                    title = "3. Экран «Аналитика» («Пульс производства»)",
                    body = "Переключайте графики «Продажи (6 мес.)» и «Структура и маржа», а также контролируйте 4 метрики: заказы в работе (без черновиков), сумму договоров и дебиторку, обязательства по заказам и «Свободные деньги» (остаток в кассе минус обязательства)."
                )
            )
        ),
        GuideSection(
            id = "backup",
            number = 8,
            title = "Настройки, валюта и резервные копии",
            summary = "Тема, язык, реквизиты компании и перенос всей базы на другой телефон",
            icon = Icons.Rounded.Backup,
            accent = Color(0xFF636366),
            steps = listOf(
                GuideStep(
                    title = "1. Внешний вид, валюта и шапка PDF",
                    body = "В «Настройках» переключаются светлая/тёмная тема, язык (RU / EN), базовая валюта (с возможностью применить её ко всем существующим заказам) и реквизиты компании (название, телефон и адрес для PDF)."
                ),
                GuideStep(
                    title = "2. Резервное копирование (JSON)",
                    body = "Всегда можно сохранить полную копию базы (.winbackup.json) — заказы, профили, прайсы, склад, счета и транзакции кассы — и восстановить её на новом устройстве."
                )
            )
        )
    )
}
