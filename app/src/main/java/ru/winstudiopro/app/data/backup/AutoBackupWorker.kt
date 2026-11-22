package ru.winstudiopro.app.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Фоновый планировщик ежедневного автобэкапа WinStudio Pro.
 * Выполняет сохранение локальной копии и при включённой интеграции — загрузку на Google Диск.
 */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val outcome = BackupManager.runBackupNow(
            context = applicationContext,
            force = false
        )
        return if (outcome.shouldRetry && runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
