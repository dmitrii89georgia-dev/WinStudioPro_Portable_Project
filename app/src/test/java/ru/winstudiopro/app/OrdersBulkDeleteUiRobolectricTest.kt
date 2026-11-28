package ru.winstudiopro.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.data.PersistedAppState
import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.OrderStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class OrdersBulkDeleteUiRobolectricTest {

    private fun testOrder(
        id: String,
        number: String,
        status: OrderStatus,
        archived: Boolean
    ) = CustomerOrder(
        id = id,
        orderNumber = number,
        customerName = "Клиент $number",
        customerPhone = "",
        address = "",
        createdDate = "28.11.2026",
        windows = emptyList(),
        status = status,
        isArchived = archived
    )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val seedRule = object : TestWatcher() {
        override fun starting(description: Description?) {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            ctx.deleteDatabase("winstudio_pro_v3.db")
            ctx.getSharedPreferences("winstudio_local_ui_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            AppStateStorage.loadState(ctx)
            AppStateStorage.saveState(
                ctx,
                PersistedAppState(
                    profileSystems = DefaultCatalogs.defaultProfileSystems(),
                    orders = listOf(
                        testOrder("act_draft", "A-DRAFT", OrderStatus.DRAFT, false),
                        testOrder("act_measure", "A-MEASURE", OrderStatus.MEASUREMENT, false),
                        testOrder("act_contract", "A-CONTRACT", OrderStatus.CONTRACT, false),
                        testOrder("act_prod", "A-PROD", OrderStatus.PRODUCTION, false),
                        testOrder("arc_draft", "ARC-DRAFT", OrderStatus.DRAFT, true),
                        testOrder("arc_closed", "ARC-CLOSED", OrderStatus.CLOSED, true)
                    ),
                    stockRemnants = emptyList()
                )
            )
        }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(seedRule).around(composeRule)

    @Test
    fun `multi-select deletes only orders below CONTRACT on active and archive tabs and protects CONTRACT and above`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_orders_card").performClick()
        composeRule.waitForIdle()

        // 1. Вкладка «Активные»: долгое нажатие на черновик запускает режим множественного выбора
        composeRule.onNodeWithTag("order_card_act_draft").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("orders_bulk_action_bar").assertIsDisplayed()

        // Попытка выбрать защищённые заказы (CONTRACT и PRODUCTION) не должна добавлять их в выделение
        composeRule.onNodeWithTag("order_card_act_contract").performClick()
        composeRule.onNodeWithTag("order_card_act_prod").performTouchInput { longClick() }
        composeRule.waitForIdle()
        assert(composeRule.onAllNodesWithText("Выбрано: 1", substring = true).fetchSemanticsNodes().isNotEmpty())

        // Нажимаем «Выделить все» — должен добавиться только A-MEASURE (итого 2, без CONTRACT/PRODUCTION)
        composeRule.onNodeWithTag("orders_bulk_select_all_btn").performClick()
        composeRule.waitForIdle()
        assert(composeRule.onAllNodesWithText("Выбрано: 2", substring = true).fetchSemanticsNodes().isNotEmpty())

        // Удаляем выбранные через диалог подтверждения
        composeRule.onNodeWithTag("orders_bulk_delete_btn").performClick()
        composeRule.waitForIdle()
        assert(composeRule.onAllNodesWithText("Действительно удалить 2 проект(ов)?", substring = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("orders_bulk_confirm_yes_btn").performClick()
        composeRule.waitForIdle()

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        composeRule.waitUntil(timeoutMillis = 3000) {
            AppStateStorage.loadState(ctx).orders.none { it.id == "act_draft" || it.id == "act_measure" }
        }
        val afterActiveDelete = AppStateStorage.loadState(ctx).orders
        assertFalse(afterActiveDelete.any { it.id == "act_draft" || it.id == "act_measure" })
        assertTrue(afterActiveDelete.any { it.id == "act_contract" })
        assertTrue(afterActiveDelete.any { it.id == "act_prod" })

        // 2. Переходим на вкладку «Архив»: проверяем массовый выбор и защиту CLOSED заказа
        composeRule.onNodeWithTag("orders_tab_1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("order_card_arc_draft").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("orders_bulk_action_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("order_card_arc_closed").performClick()
        composeRule.waitForIdle()
        assert(composeRule.onAllNodesWithText("Выбрано: 1", substring = true).fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag("orders_bulk_delete_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("orders_bulk_confirm_yes_btn").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 3000) {
            AppStateStorage.loadState(ctx).orders.none { it.id == "arc_draft" }
        }
        val afterArchiveDelete = AppStateStorage.loadState(ctx).orders.map { it.id }
        assertEquals(listOf("act_contract", "act_prod", "arc_closed"), afterArchiveDelete)
    }
}
