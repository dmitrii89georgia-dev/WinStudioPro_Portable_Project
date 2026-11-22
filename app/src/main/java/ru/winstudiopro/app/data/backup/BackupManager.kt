package ru.winstudiopro.app.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.winstudiopro.app.data.AppStateStorage
import ru.winstudiopro.app.domain.model.AppUiSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class BackupRunOutcome(
    val updatedUiSettings: AppUiSettings,
    val localSaved: Boolean,
    val driveUploaded: Boolean,
    val skippedUnchanged: Boolean = false,
    val shouldRetry: Boolean = false,
    val summaryMessage: String
)

/**
 * Координатор ежедневного автобэкапа (локально в Downloads/WinStudio_Backups + Google Диск)
 * с контролем изменения SHA-256 хеша базы данных и ротацией до 7 последних файлов.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val DAILY_BACKUP_WORK_NAME = "winstudio_daily_auto_backup"
    private const val LOCAL_FOLDER_NAME = "WinStudio_Backups"
    private const val KEEP_FILES_COUNT = 7
    private const val FILE_PREFIX = "WinStudio_Backup_"
    private const val FILE_SUFFIX = ".winbackup.json"

    fun buildDriveAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleDriveBackupClient.DRIVE_FILE_SCOPE)))
            .build()
    }

    fun scheduleDailyAutoBackup(
        context: Context,
        enabled: Boolean,
        targetHour: Int
    ) {
        try {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                wm.cancelUniqueWork(DAILY_BACKUP_WORK_NAME)
                return
            }
            val delayMs = computeInitialDelayToHourMillis(targetHour.coerceIn(0, 23))
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(
                DAILY_BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager scheduling skipped/failed: ${e.message}")
        }
    }

    internal fun computeInitialDelayToHourMillis(
        targetHour: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val next = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, targetHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= cal.timeInMillis + 60_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (next.timeInMillis - nowMillis).coerceAtLeast(60_000L)
    }

    suspend fun runBackupNow(
        context: Context,
        force: Boolean,
        explicitAccessToken: String? = null
    ): BackupRunOutcome = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val nowMs = System.currentTimeMillis()
        val state = AppStateStorage.loadState(appContext)
        val currentHash = AppStateStorage.computeDatabaseContentHash(appContext)
        val lastHash = AppStateStorage.getLastBackupStateHash(appContext)
        val driveEnabled = state.uiSettings.googleDriveSyncEnabled

        if (!force && lastHash == currentHash && (!driveEnabled || state.uiSettings.googleDriveLastSyncMillis != null)) {
            val unchangedUi = AppStateStorage.updateBackupSyncStatusInDb(appContext) { cur ->
                cur.copy(
                    autoBackupLastRunMillis = nowMs,
                    autoBackupLastStatus = "Без изменений (копия не требовалась)"
                )
            }
            return@withContext BackupRunOutcome(
                updatedUiSettings = unchangedUi,
                localSaved = false,
                driveUploaded = false,
                skippedUnchanged = true,
                summaryMessage = "Изменений за день нет — бэкап пропущен"
            )
        }

        val jsonText = AppStateStorage.exportFullBackupJsonString(appContext)
        val jsonBytes = jsonText.toByteArray(Charsets.UTF_8)
        val dateTag = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        val fileName = "$FILE_PREFIX$dateTag$FILE_SUFFIX"

        var localSaved = false
        var localStatus = ""
        try {
            saveLocalBackupAndPrune(appContext, fileName, jsonBytes)
            localSaved = true
            localStatus = "Локально: $fileName"
            AppStateStorage.saveLastBackupStateHash(appContext, currentHash)
        } catch (e: Exception) {
            Log.e(TAG, "Local backup save failed", e)
            localStatus = "Ошибка локального сохранения: ${e.message ?: "IO"}"
        }

        var driveUploaded = false
        var shouldRetry = false
        var driveStatus = state.uiSettings.googleDriveLastStatus
        var driveSyncTime = state.uiSettings.googleDriveLastSyncMillis

        if (driveEnabled || !explicitAccessToken.isNullOrBlank()) {
            try {
                val token = explicitAccessToken?.takeIf { it.isNotBlank() } ?: run {
                    val authResult = Tasks.await(
                        Identity.getAuthorizationClient(appContext).authorize(buildDriveAuthorizationRequest())
                    )
                    if (authResult.hasResolution()) {
                        null
                    } else {
                        authResult.accessToken
                    }
                }
                if (token.isNullOrBlank()) {
                    driveStatus = "Требуется повторная авторизация Google в Настройках"
                } else {
                    GoogleDriveBackupClient.uploadBackupAndPrune(
                        accessToken = token,
                        fileName = fileName,
                        jsonBytes = jsonBytes,
                        keepCount = KEEP_FILES_COUNT
                    )
                    driveUploaded = true
                    driveSyncTime = nowMs
                    driveStatus = "Google Диск: загружено ($fileName)"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google Drive upload failed", e)
                shouldRetry = true
                driveStatus = "Ошибка Google Диска: ${e.message?.take(80) ?: "нет сети"}"
            }
        }

        val updatedUi = AppStateStorage.updateBackupSyncStatusInDb(appContext) { cur ->
            cur.copy(
                autoBackupLastRunMillis = nowMs,
                autoBackupLastStatus = localStatus,
                googleDriveLastSyncMillis = driveSyncTime ?: cur.googleDriveLastSyncMillis,
                googleDriveLastStatus = if (driveEnabled || !explicitAccessToken.isNullOrBlank()) driveStatus else cur.googleDriveLastStatus
            )
        }

        val msg = buildString {
            append(localStatus)
            if (driveEnabled || !explicitAccessToken.isNullOrBlank()) {
                append(" • ")
                append(driveStatus)
            }
        }
        BackupRunOutcome(
            updatedUiSettings = updatedUi,
            localSaved = localSaved,
            driveUploaded = driveUploaded,
            shouldRetry = shouldRetry,
            summaryMessage = msg
        )
    }

    private fun saveLocalBackupAndPrune(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$LOCAL_FOLDER_NAME"

            // Удаляем одноимённый бэкап за сегодняшний день, если он уже создавался
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Не удалось создать файл в Downloads/$LOCAL_FOLDER_NAME")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("Поток записи MediaStore недоступен")

            val publish = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, publish, null, null)

            // Ротация: оставляем 7 последних файлов приложения в Downloads/WinStudio_Backups
            val ownedIds = mutableListOf<Long>()
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$FILE_PREFIX%$FILE_SUFFIX"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    if (idIdx >= 0) ownedIds.add(cursor.getLong(idIdx))
                }
            }
            if (ownedIds.size > KEEP_FILES_COUNT) {
                for (i in KEEP_FILES_COUNT until ownedIds.size) {
                    val delUri = ContentUris.withAppendedId(collection, ownedIds[i])
                    try {
                        resolver.delete(delUri, null, null)
                    } catch (_: Exception) {
                    }
                }
            }
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val backupDir = File(baseDir, LOCAL_FOLDER_NAME).apply { mkdirs() }
            val target = File(backupDir, fileName)
            target.writeBytes(bytes)
            val allFiles = backupDir.listFiles { f ->
                f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            if (allFiles.size > KEEP_FILES_COUNT) {
                allFiles.drop(KEEP_FILES_COUNT).forEach { old ->
                    try {
                        old.delete()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
