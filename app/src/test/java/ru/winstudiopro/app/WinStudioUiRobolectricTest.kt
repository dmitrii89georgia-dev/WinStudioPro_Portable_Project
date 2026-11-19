package ru.winstudiopro.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WinStudioUiRobolectricTest {

    @Before
    fun resetCleanStorage() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("winstudio_pro_v3.db")
        ctx.getSharedPreferences("winstudio_local_ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `home dashboard opens orders list with seeded orders and applies status filter`() {
        composeRule.waitForIdle()

        // Главный экран: видны основные плитки разделов (нижние прокручиваем в область видимости)
        composeRule.onNodeWithTag("dashboard_orders_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profiles_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_prices_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_warehouse_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_settings_card").performScrollTo().assertIsDisplayed()

        // Переходим в список заказов
        composeRule.onNodeWithTag("dashboard_orders_card").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Должен отображаться заголовок с количеством 16 заказов из dev_seed.winbackup.json
        assert(composeRule.onAllNodesWithText("Заказы (16)", substring = true).fetchSemanticsNodes().isNotEmpty())

        // Открываем диалог фильтра статусов
        composeRule.onNodeWithTag("orders_filter_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Проверяем кнопки внутри диалога фильтра: "Показать все" выключает все, затем включаем "Черновик" и жмём ОК
        composeRule.onNodeWithTag("orders_filter_show_all_btn").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("orders_filter_status_DRAFT").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("orders_filter_ok_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Возвращаемся на главный экран по кнопке "Назад"
        composeRule.onNodeWithTag("top_bar_back_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_orders_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `cad drawer separates installation drawings, workshop task, and cutting manual editor`() {
        composeRule.waitForIdle()

        // Создаём новый заказ из главного экрана и переходим в 2D-редактор
        composeRule.onNodeWithTag("dashboard_orders_card").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("orders_list_add_button").performClick()
        composeRule.waitForIdle()

        // Открываем левую шторку 2D-редактора
        composeRule.onNodeWithTag("cad_menu_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cad_menu_installation_drawings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cad_menu_workshop_task").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cad_menu_cutting").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Меняем длину 1-го хлыста вручную с 6000 на 5000 мм (прокручиваем LazyColumn до хлыста)
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_edit_bar_0"))
        composeRule.onNodeWithTag("workshop_edit_bar_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workshop_edit_bar_length_input").performTextReplacement("5000")
        composeRule.onNodeWithTag("workshop_edit_bar_confirm").performClick()
        composeRule.waitForIdle()

        // Появилась кнопка сброса и статус ручного раскроя
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_reset_auto_button"))
        composeRule.onNodeWithTag("workshop_reset_auto_button").assertIsDisplayed()
        assert(composeRule.onAllNodesWithText("Ручной раскрой", substring = true).fetchSemanticsNodes().isNotEmpty())

        // Нажимаем на 1-й отрезок и проверяем раздел "Деловые остатки со склада"
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_cut_0_0"))
        composeRule.onNodeWithTag("workshop_cut_0_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workshop_open_manual_remnant_btn").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("workshop_manual_remnant_input").assertIsDisplayed()
        composeRule.onAllNodesWithText("Закрыть")[0].performClick()
        composeRule.waitForIdle()

        // Сбрасываем обратно на авто-раскрой
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_reset_auto_button"))
        composeRule.onNodeWithTag("workshop_reset_auto_button").performClick()
        composeRule.waitForIdle()
        assert(composeRule.onAllNodesWithText("Автоматический раскрой", substring = true).fetchSemanticsNodes().isNotEmpty())

        // Проверяем наличие чекбоксов профиля, закладных, 4 видов резины и кнопки "Сформировать заказ профиля (PDF)"
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_profile_check_0"))
        composeRule.onNodeWithTag("workshop_profile_check_0").assertIsDisplayed()
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_connector_check_FRAME"))
        composeRule.onNodeWithTag("workshop_connector_check_FRAME").assertIsDisplayed()
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_rubber_check_FIXED_GLAZING"))
        composeRule.onNodeWithTag("workshop_rubber_check_FIXED_GLAZING").assertIsDisplayed()
        composeRule.onNodeWithTag("workshop_lazy_column")
            .performScrollToNode(hasTestTag("workshop_profile_order_pdf_button"))
        composeRule.onNodeWithTag("workshop_profile_order_pdf_button").assertIsDisplayed()
    }

    @Test
    fun `glazing and hardware order editors open from cad menu and allow selection and editing`() {
        composeRule.waitForIdle()

        // Открываем первый существующий заказ из базы и заходим в 2D-конструктор
        composeRule.onNodeWithTag("dashboard_orders_card").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Окон:", substring = true)[0].performClick()
        composeRule.waitForIdle()

        // Открываем "Заказ стеклопакетов"
        composeRule.onNodeWithTag("cad_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cad_menu_glazing_order").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("glazing_select_all_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("glazing_select_all_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("glazing_generate_pdf_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()

        // Открываем "Заказ фурнитуры"
        composeRule.onNodeWithTag("cad_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cad_menu_hardware_order").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hardware_select_all_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hardware_select_all_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hardware_generate_pdf_btn").assertIsDisplayed()
    }

    @Test
    fun `profiles screen displays rubber section with 4 rows and warehouse manages rubber meters`() {
        composeRule.waitForIdle()

        // 1. Открываем "Профили", раскрываем секцию "Резина (уплотнители)" внизу списка
        composeRule.onNodeWithTag("dashboard_profiles_card").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Резина (уплотнители)", substring = true)[0]
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rubber_row_fixed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rubber_row_wedge").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rubber_row_frame_rebate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rubber_row_sash_rebate").performScrollTo().assertIsDisplayed()

        // Возвращаемся на главный экран
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()

        // 2. Открываем "Склад", прокручиваем LazyColumn до раздела уплотнительной резины
        composeRule.onNodeWithTag("dashboard_warehouse_card").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("warehouse_lazy_column")
            .performScrollToNode(hasText("Уплотнительная резина", substring = true))
        composeRule.waitForIdle()
        assert(composeRule.onAllNodesWithText("Уплотнительная резина", substring = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `prices tabs and cad drawer display assembly schemes order services and prime cost bar`() {
        composeRule.waitForIdle()

        // 1. Открываем "Прайсы" и проверяем переименованную вкладку "Монтаж", вкладку "Сборка" и вкладку "Доп. услуги"
        composeRule.onNodeWithTag("dashboard_prices_card").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("price_tab_WORKS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("price_tab_ASSEMBLY").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("price_tab_ORDER_SERVICES").performScrollTo().assertIsDisplayed()

        // Проверяем вкладку "Сборка" и сохранение ставок
        composeRule.onNodeWithTag("price_tab_ASSEMBLY").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_assembly_pricing_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Проверяем вкладку "Доп. услуги" и добавление услуги заказа
        composeRule.onNodeWithTag("price_tab_ORDER_SERVICES").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("order_service_name_input").performScrollTo().performTextReplacement("Упаковка в плёнку")
        composeRule.onNodeWithTag("order_service_price_input").performScrollTo().performTextReplacement("35")
        composeRule.onNodeWithTag("order_service_add_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()

        // 2. Открываем заказ в 2D-конструкторе и проверяем нижнюю расчётную панель и правую шторку
        composeRule.onNodeWithTag("dashboard_orders_card").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Окон:", substring = true)[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cad_cost_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("cad_cost_total_box").assertIsDisplayed()

        // Открываем правую шторку настроек окна
        composeRule.onNodeWithTag("cad_window_settings_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drawer_assembly_section", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_assembly_scheme_PER_M2").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drawer_floor_assembly_input").performScrollTo().performTextReplacement("2")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drawer_order_services_section", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_order_service_check_ord_srv_delivery").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drawer_order_service_price_ord_srv_delivery").performScrollTo().performTextReplacement("75")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drawer_done_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cad_cost_bar").assertIsDisplayed()
    }
}
