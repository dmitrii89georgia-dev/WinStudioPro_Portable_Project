package ru.winstudiopro.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.domain.calculator.OrderArchiveHelper
import ru.winstudiopro.app.domain.calculator.ProductionAnalyticsCalculator
import ru.winstudiopro.app.domain.calculator.WindowBomAndCostEngine
import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.DefaultCatalogs
import ru.winstudiopro.app.domain.model.DefaultCashCatalogs
import ru.winstudiopro.app.domain.model.FinSchemeSettings
import ru.winstudiopro.app.domain.model.OrderStatus
import ru.winstudiopro.app.domain.model.WindowCadOperations
import ru.winstudiopro.app.domain.model.WindowItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArchiveLegacyFallbackAndBackupTest {

    @Before
    fun resetDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("winstudio_pro_v3.db")
        ctx.getSharedPreferences("winstudio_local_ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `legacy fallback engine uses 48-52-56-18 mm geometry and original names without crashing`() {
        val legacyWindow = WindowItem(
            id = "win_legacy_1",
            name = "Окно (старая база)",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            profileSystemId = "missing_old_pvc_70",
            glazingId = "missing_old_glass_32",
            colorId = "missing_old_brown",
            legacySystemName = "ПВХ Коричневый 70",
            legacyGlazingName = "32мм Энерго",
            legacyColorName = "Шоколад RAL 8017"
        )
        val profiles = DefaultCatalogs.defaultProfileSystems()
        val glazing = DefaultCatalogs.defaultGlazingItems()
        val colors = DefaultCatalogs.defaultColors()

        val resolvedProfile = WindowBomAndCostEngine.resolveProfileSystemWithFallback(legacyWindow, profiles)
        assertEquals(48f, resolvedProfile.frameWidthMm, 0.01f)
        assertEquals(52f, resolvedProfile.impostWidthMm, 0.01f)
        assertEquals(56f, resolvedProfile.sashWidthMm, 0.01f)
        assertEquals(18f, resolvedProfile.shtapikWidthMm, 0.01f)
        assertEquals("ПВХ Коричневый 70", resolvedProfile.name)

        val calc = WindowBomAndCostEngine.calculateWindow(
            window = legacyWindow,
            profileSystems = profiles,
            glazingCatalog = glazing,
            colorCatalog = colors,
            sillsCatalog = DefaultCatalogs.defaultSills()
        )
        assertEquals("ПВХ Коричневый 70", calc.profileSystem.name)
        assertEquals("32мм Энерго", calc.glazing.name)
        assertEquals("Шоколад RAL 8017", calc.color.name)
        assertTrue(calc.profileCuts.isNotEmpty())

        val (cuttingBars, _) = WindowBomAndCostEngine.optimizeOrderProfileCutting(
            windowResults = listOf(calc),
            stockRemnants = emptyList(),
            profileSystems = profiles
        )
        assertTrue(cuttingBars.isNotEmpty())
    }

    @Test
    fun `auto archive counts days from closedAtMillis and restore resets status to DRAFT`() {
        val dayMs = 86_400_000L
        val nowMs = 1_780_000_000_000L

        val baseDraft = CustomerOrder(
            id = "ord_1",
            orderNumber = "Заказ №1",
            customerName = "Иванов",
            customerPhone = "",
            address = "",
            createdDate = "01.05.2026",
            windows = listOf(WindowItem("w1", "Окно 1", 1, WindowCadOperations.single1500Topology()))
        )

        val closedOld = OrderArchiveHelper.withStatusTransition(baseDraft, OrderStatus.CLOSED, nowMs - 31L * dayMs)
        assertNotNull(closedOld.closedAtMillis)

        val closedRecent = OrderArchiveHelper.withStatusTransition(
            baseDraft.copy(id = "ord_2", orderNumber = "Заказ №2"),
            OrderStatus.CLOSED,
            nowMs - 10L * dayMs
        )
        val stillInProd = baseDraft.copy(
            id = "ord_3",
            orderNumber = "Заказ №3",
            status = OrderStatus.PRODUCTION,
            contractDateMillis = nowMs - 60L * dayMs
        )

        val result30 = OrderArchiveHelper.applyAutoArchivePolicy(
            orders = listOf(closedOld, closedRecent, stillInProd),
            autoArchiveAfterDays = 30,
            nowMillis = nowMs
        )
        assertEquals(1, result30.newlyArchivedCount)
        assertTrue(result30.orders[0].isArchived)
        assertFalse(result30.orders[1].isArchived)
        assertFalse(result30.orders[2].isArchived)

        val resultManual = OrderArchiveHelper.applyAutoArchivePolicy(
            orders = listOf(closedOld),
            autoArchiveAfterDays = 0,
            nowMillis = nowMs
        )
        assertEquals(0, resultManual.newlyArchivedCount)
        assertFalse(resultManual.orders[0].isArchived)

        val restored = OrderArchiveHelper.restoreFromArchiveToDraft(result30.orders[0])
        assertFalse(restored.isArchived)
        assertNull(restored.archivedAtMillis)
        assertNull(restored.closedAtMillis)
        assertEquals(OrderStatus.DRAFT, restored.status)

        val cloned = OrderArchiveHelper.cloneArchiveOrderToNew(result30.orders[0], nowMs)
        assertFalse(cloned.isArchived)
        assertFalse(cloned.isLegacyImport)
        assertEquals(OrderStatus.DRAFT, cloned.status)
        assertTrue(cloned.id != result30.orders[0].id)
    }

    @Test
    fun `analytics excludes legacy imports completely and separates archived from active obligations`() {
        val nowMs = System.currentTimeMillis()
        val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(nowMs))
        val win = WindowItem(
            id = "w_a",
            name = "Окно",
            quantity = 1,
            topology = WindowCadOperations.single1500Topology(),
            manualUnitPriceOverride = 500.0
        )

        val activeContract = CustomerOrder(
            id = "o_act",
            orderNumber = "Актив-1",
            customerName = "Клиент 1",
            customerPhone = "",
            address = "",
            createdDate = todayStr,
            windows = listOf(win),
            status = OrderStatus.CONTRACT,
            contractDateMillis = nowMs,
            manualOrderTotalOverride = 500.0
        )
        val archivedClosed = CustomerOrder(
            id = "o_arch",
            orderNumber = "Закрыт-2",
            customerName = "Клиент 2",
            customerPhone = "",
            address = "",
            createdDate = todayStr,
            windows = listOf(win),
            status = OrderStatus.CLOSED,
            closedAtMillis = nowMs,
            isArchived = true,
            isLegacyImport = false,
            manualOrderTotalOverride = 500.0
        )
        val legacyImport = CustomerOrder(
            id = "o_leg",
            orderNumber = "СтараяБД-999",
            customerName = "Старый клиент",
            customerPhone = "",
            address = "",
            createdDate = "10.01.2020",
            windows = listOf(win.copy(manualUnitPriceOverride = 99_999.0)),
            status = OrderStatus.CLOSED,
            isArchived = true,
            isLegacyImport = true,
            manualOrderTotalOverride = 99_999.0
        )

        val snapshot = ProductionAnalyticsCalculator.calculate(
            orders = listOf(activeContract, archivedClosed, legacyImport),
            profileSystems = DefaultCatalogs.defaultProfileSystems(),
            glazingCatalog = DefaultCatalogs.defaultGlazingItems(),
            colorCatalog = DefaultCatalogs.defaultColors(),
            sillsCatalog = DefaultCatalogs.defaultSills(),
            extraAccessoriesCatalog = DefaultCatalogs.defaultExtraAccessories(),
            workServicesCatalog = DefaultCatalogs.defaultWorkServices(),
            hardwareBrandsCatalog = DefaultCatalogs.defaultHardwareBrands(),
            assemblyPricingCatalog = DefaultCatalogs.defaultAssemblyPricingCatalog(),
            orderExtraServicesCatalog = DefaultCatalogs.defaultOrderExtraServices(),
            finSchemeSettings = FinSchemeSettings(),
            cashAccounts = DefaultCashCatalogs.defaultCashAccounts(),
            cashTransactions = emptyList(),
            nowMillis = nowMs
        )

        // В активных заказах только 1 (неархивный CONTRACT = 500$), а в общей выручке без legacy (500 + 500 = 1000$, без 99_999$)
        assertEquals(1, snapshot.activeOrdersCount)
        assertEquals(500.0, snapshot.activeContractsTotalUsd, 0.5)
        assertEquals(1000.0, snapshot.marginStructure.totalRevenueUsd, 0.5)
    }

    @Test
    fun `sqlite v8 persists legacy import archive flags and auto backup settings`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val backupJson = """
            {
              "app": "WinStudioPro",
              "backupVersion": 6,
              "orders": [
                {
                  "id": "legacy_ord_777",
                  "orderNumber": "БД-2022-777",
                  "customerName": "ООО Прошлый",
                  "customerPhone": "+995555000111",
                  "address": "Тбилиси",
                  "createdDate": "14.03.2022",
                  "status": "CLOSED",
                  "isArchived": true,
                  "isLegacyImport": true,
                  "archivedAtMillis": 1647216000000,
                  "closedAtMillis": 1647216000000,
                  "legacySystemName": "ПВХ Коричневый 70",
                  "legacyColorName": "Орех",
                  "windows": [
                    {
                      "id": "w_leg_777",
                      "name": "Окно 1",
                      "quantity": 1,
                      "profileSystemId": "missing_brown_pvc",
                      "glazingId": "missing_glz",
                      "colorId": "missing_col",
                      "legacySystemName": "ПВХ Коричневый 70",
                      "legacyGlazingName": "24мм Стекло",
                      "legacyColorName": "Орех"
                    }
                  ]
                }
              ],
              "appSettingsPayload": {
                "themeMode": "LIGHT",
                "language": "RU",
                "autoArchiveAfterDays": 60,
                "autoBackupEnabled": true,
                "autoBackupHour": 4,
                "googleDriveSyncEnabled": true,
                "googleDriveAccountEmail": "master@example.com"
              }
            }
        """.trimIndent()

        AppStateStorage.importFullBackupJsonString(ctx, backupJson)
        val reloaded = AppStateStorage.loadState(ctx)

        val imported = reloaded.orders.find { it.id == "legacy_ord_777" }
        assertNotNull(imported)
        assertTrue(imported!!.isArchived)
        assertTrue(imported.isLegacyImport)
        assertEquals("ПВХ Коричневый 70", imported.legacySystemName)
        assertEquals("ПВХ Коричневый 70", imported.windows.first().legacySystemName)

        assertEquals(60, reloaded.uiSettings.autoArchiveAfterDays)
        assertEquals(4, reloaded.uiSettings.autoBackupHour)
        assertTrue(reloaded.uiSettings.googleDriveSyncEnabled)
        assertEquals("master@example.com", reloaded.uiSettings.googleDriveAccountEmail)

        val hash1 = AppStateStorage.computeDatabaseContentHash(ctx)
        val hash2 = AppStateStorage.computeDatabaseContentHash(ctx)
        assertEquals(hash1, hash2)
    }
}
