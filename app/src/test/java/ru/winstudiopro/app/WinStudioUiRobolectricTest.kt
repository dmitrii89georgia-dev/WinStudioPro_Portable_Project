package ru.winstudiopro.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
}
