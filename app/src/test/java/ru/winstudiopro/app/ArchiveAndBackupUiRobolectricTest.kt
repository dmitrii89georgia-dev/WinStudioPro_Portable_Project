package ru.winstudiopro.app

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
class ArchiveAndBackupUiRobolectricTest {

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
    fun `orders list protects non-draft orders with archive and restores archived order to draft`() {
        composeRule.waitForIdle()

        // Переходим в список заказов
        composeRule.onNodeWithTag("dashboard_orders_card").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Вкладки «Активные» и «Архив» отображаются
        composeRule.onNodeWithTag("orders_tab_0").assertIsDisplayed()
        composeRule.onNodeWithTag("orders_tab_1").assertIsDisplayed()

        // Первый заказ из dev_seed имеет статус INSTALLATION (не черновик)
        val seededNonDraftId = "8ad463d2-8de7-4fd1-b08c-bbe56ce30e8d"
        composeRule.onAllNodesWithTag("order_delete_draft_btn_$seededNonDraftId").assertCountEquals(0)
        composeRule.onNodeWithTag("order_archive_btn_$seededNonDraftId").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Подтверждаем перенос в архив
        composeRule.onNodeWithTag("archive_confirm_dialog_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // В «Активных» его больше нет
        composeRule.onAllNodesWithTag("order_archive_btn_$seededNonDraftId").assertCountEquals(0)

        // Переключаемся на вкладку «Архив»
        composeRule.onNodeWithTag("orders_tab_1").performClick()
        composeRule.waitForIdle()

        // Видны кнопки «В работу», «Копия в новый» и «Удалить навсегда»
        composeRule.onNodeWithTag("archive_restore_btn_$seededNonDraftId").assertIsDisplayed()
        composeRule.onNodeWithTag("archive_clone_btn_$seededNonDraftId").assertIsDisplayed()
        composeRule.onNodeWithTag("archive_delete_forever_btn_$seededNonDraftId").assertIsDisplayed()

        // На архивном заказе смена статуса отключена
        composeRule.onNodeWithTag("order_status_dropdown_$seededNonDraftId").assertIsNotEnabled()

        // Возвращаем в работу -> статус становится DRAFT и появляется кнопка удаления черновика
        composeRule.onNodeWithTag("archive_restore_btn_$seededNonDraftId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("order_delete_draft_btn_$seededNonDraftId").assertIsDisplayed()
    }

    @Test
    fun `settings screen exposes auto archive, local auto backup, and google drive controls`() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_settings_card").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_auto_backup_switch").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_auto_archive_30").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_backup_now_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_gdrive_connect_button").performScrollTo().assertIsDisplayed()
    }
}
