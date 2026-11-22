package ru.winstudiopro.app.domain.calculator

import ru.winstudiopro.app.domain.model.CustomerOrder
import ru.winstudiopro.app.domain.model.OrderStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AutoArchiveResult(
    val orders: List<CustomerOrder>,
    val newlyArchivedCount: Int
)

/**
 * Управляет жизненным циклом архива заказов:
 * - автоматическое проставление даты закрытия (`closedAtMillis`) при смене статуса;
 * - фоновая/загрузочная автоархивация заказов в статусе CLOSED по истечении заданного числа дней;
 * - ручной перенос в архив, возврат в «Черновик» и клонирование архивного заказа в новый.
 */
object OrderArchiveHelper {

    private const val DAY_MILLIS = 86_400_000L

    fun withStatusTransition(
        order: CustomerOrder,
        newStatus: OrderStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): CustomerOrder {
        val nextClosedAt = when {
            newStatus == OrderStatus.CLOSED && order.status != OrderStatus.CLOSED -> nowMillis
            newStatus == OrderStatus.CLOSED -> order.closedAtMillis ?: nowMillis
            else -> null
        }
        return order.copy(
            status = newStatus,
            closedAtMillis = nextClosedAt
        )
    }

    fun applyStatusTransitions(
        previousOrders: List<CustomerOrder>,
        updatedOrders: List<CustomerOrder>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<CustomerOrder> {
        val prevById = previousOrders.associateBy { it.id }
        return updatedOrders.map { next ->
            val prev = prevById[next.id]
            when {
                next.status == OrderStatus.CLOSED && prev?.status != OrderStatus.CLOSED -> {
                    next.copy(closedAtMillis = next.closedAtMillis ?: nowMillis)
                }
                next.status != OrderStatus.CLOSED && prev?.status == OrderStatus.CLOSED && next.closedAtMillis != null -> {
                    next.copy(closedAtMillis = null)
                }
                else -> next
            }
        }
    }

    fun archiveOrder(
        order: CustomerOrder,
        nowMillis: Long = System.currentTimeMillis()
    ): CustomerOrder {
        return order.copy(
            isArchived = true,
            archivedAtMillis = order.archivedAtMillis ?: nowMillis
        )
    }

    fun restoreFromArchiveToDraft(order: CustomerOrder): CustomerOrder {
        return order.copy(
            isArchived = false,
            archivedAtMillis = null,
            closedAtMillis = null,
            status = OrderStatus.DRAFT
        )
    }

    fun cloneArchiveOrderToNew(
        source: CustomerOrder,
        nowMillis: Long = System.currentTimeMillis()
    ): CustomerOrder {
        val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(nowMillis))
        val stampStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(nowMillis))
        val clonedWindows = source.windows.map { w ->
            w.copy(id = UUID.randomUUID().toString())
        }
        val cleanBaseName = source.orderNumber.trim().ifBlank { "Заказ" }
        return source.copy(
            id = UUID.randomUUID().toString(),
            orderNumber = "$cleanBaseName (копия $stampStr)",
            createdDate = todayStr,
            windows = clonedWindows,
            status = OrderStatus.DRAFT,
            contractDateMillis = null,
            customCuttingAllocations = null,
            stockDeductedForProduction = false,
            isArchived = false,
            archivedAtMillis = null,
            closedAtMillis = null,
            isLegacyImport = false
        )
    }

    fun applyAutoArchivePolicy(
        orders: List<CustomerOrder>,
        autoArchiveAfterDays: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): AutoArchiveResult {
        if (autoArchiveAfterDays <= 0) {
            return AutoArchiveResult(orders = orders, newlyArchivedCount = 0)
        }
        val thresholdMs = autoArchiveAfterDays.toLong() * DAY_MILLIS
        var archivedCount = 0
        val updated = orders.map { ord ->
            if (!ord.isArchived && ord.status == OrderStatus.CLOSED) {
                val refMillis = ord.closedAtMillis
                    ?: ord.contractDateMillis?.takeIf { it > 0L }
                    ?: parseOrderDateMillis(ord.createdDate)
                if (refMillis != null && (nowMillis - refMillis) >= thresholdMs) {
                    archivedCount += 1
                    ord.copy(
                        isArchived = true,
                        archivedAtMillis = ord.archivedAtMillis ?: nowMillis,
                        closedAtMillis = ord.closedAtMillis ?: refMillis
                    )
                } else {
                    ord
                }
            } else {
                ord
            }
        }
        return AutoArchiveResult(orders = updated, newlyArchivedCount = archivedCount)
    }

    fun parseOrderDateMillis(rawDate: String): Long? {
        val trimmed = rawDate.trim()
        if (trimmed.isBlank()) return null
        val formats = listOf("dd.MM.yyyy HH:mm", "dd.MM.yyyy", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        for (pattern in formats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }
                val parsed = fmt.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
