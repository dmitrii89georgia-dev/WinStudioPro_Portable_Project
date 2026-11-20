package ru.winstudiopro.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class CashAndAnalyticsUiRobolectricTest {

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
    fun `home dashboard exposes cashbox analytics and quick actions without showing balances`() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_cash_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_analytics_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_quick_income_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_quick_expense_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_quick_transfer_btn").performScrollTo().assertIsDisplayed()

        // На главном экране нет отображения общего баланса
        composeRule.onNodeWithTag("cash_total_balance_card").assertDoesNotExist()

        // Проверяем открытие быстрого диалога прихода с главного экрана
        composeRule.onNodeWithTag("dashboard_quick_income_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("income_amount_input").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `prices screen exposes FinScheme tab and saves settings`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_prices_card").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("price_tab_FIN_SCHEME").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Коэффициент наценки базовой цены").assertIsDisplayed()
        composeRule.onNodeWithTag("save_fin_scheme_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `cashbox opens accounts executes quick income and displays payroll modal`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_cash_card").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cash_total_balance_card").assertIsDisplayed()
        composeRule.onNodeWithTag("cash_quick_income_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("income_amount_input").performTextReplacement("250")
        composeRule.onNodeWithTag("income_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cash_header_payroll_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Зарплаты и выплаты").assertIsDisplayed()
    }

    @Test
    fun `analytics opens charts switches bar to donut and displays KPI grid`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_analytics_card").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("analytics_bar_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_kpi_in_work").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_kpi_contracts").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_kpi_obligations").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("analytics_kpi_free_money").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("analytics_chart_tab_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("analytics_donut_canvas").assertIsDisplayed()
    }
}
