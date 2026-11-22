package ru.winstudiopro.app.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriveUploadResult(
    val fileId: String,
    val fileName: String,
    val deletedOldCount: Int
)

/**
 * Клиент REST API Google Drive v3 на базе стандартного HttpURLConnection.
 * Работает только в рамках ограниченного скоупа `drive.file` (папка и файлы,
 * созданные самим приложением WinStudio Pro). Токены доступа не сохраняются в БД.
 */
object GoogleDriveBackupClient {

    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val BACKUP_FOLDER_NAME = "WinStudio_Backups"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    fun fetchDriveUserEmail(accessToken: String): String? {
        return try {
            val json = JSONObject(
                executeRequest(
                    urlStr = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)",
                    method = "GET",
                    accessToken = accessToken
                )
            )
            json.optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun uploadBackupAndPrune(
        accessToken: String,
        fileName: String,
        jsonBytes: ByteArray,
        keepCount: Int = 7
    ): DriveUploadResult {
        require(accessToken.isNotBlank()) { "Пустой токен доступа Google Drive" }
        val folderId = findOrCreateBackupFolder(accessToken)
        val existingId = findFileInFolderByName(accessToken, folderId, fileName)
        val uploadedId = if (existingId != null) {
            updateExistingFile(accessToken, existingId, jsonBytes)
            existingId
        } else {
            createMultipartFile(accessToken, folderId, fileName, jsonBytes)
        }
        val deletedCount = pruneOldBackupsInFolder(accessToken, folderId, keepCount)
        return DriveUploadResult(
            fileId = uploadedId,
            fileName = fileName,
            deletedOldCount = deletedCount
        )
    }

    private fun findOrCreateBackupFolder(accessToken: String): String {
        val query = URLEncoder.encode(
            "name = '$BACKUP_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
            "UTF-8"
        )
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id,name)&pageSize=5"
        val searchJson = JSONObject(executeRequest(searchUrl, "GET", accessToken))
        val files = searchJson.optJSONArray("files")
        if (files != null && files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }

        val payload = JSONObject().apply {
            put("name", BACKUP_FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString().toByteArray(Charsets.UTF_8)

        val createdJson = JSONObject(
            executeRequest(
                urlStr = "https://www.googleapis.com/drive/v3/files?fields=id",
                method = "POST",
                accessToken = accessToken,
                contentType = "application/json; charset=UTF-8",
                body = payload
            )
        )
        return createdJson.getString("id")
    }

    private fun findFileInFolderByName(
        accessToken: String,
        folderId: String,
        fileName: String
    ): String? {
        val escapedName = fileName.replace("'", "\\'")
        val query = URLEncoder.encode(
            "name = '$escapedName' and '$folderId' in parents and trashed = false",
            "UTF-8"
        )
        val url = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id)&pageSize=2"
        val res = JSONObject(executeRequest(url, "GET", accessToken))
        val files = res.optJSONArray("files")
        return if (files != null && files.length() > 0) {
            files.getJSONObject(0).getString("id")
        } else {
            null
        }
    }

    private fun createMultipartFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        jsonBytes: ByteArray
    ): String {
        val boundary = "winstudio_boundary_${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray(listOf(folderId)))
            put("mimeType", "application/json")
        }.toString()

        val out = ByteArrayOutputStream()
        out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
        out.write(metadata.toByteArray(Charsets.UTF_8))
        out.write("\r\n--$boundary\r\n".toByteArray(Charsets.UTF_8))
        out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
        out.write(jsonBytes)
        out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        val resp = JSONObject(
            executeRequest(
                urlStr = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                method = "POST",
                accessToken = accessToken,
                contentType = "multipart/related; boundary=$boundary",
                body = out.toByteArray()
            )
        )
        return resp.getString("id")
    }

    private fun updateExistingFile(
        accessToken: String,
        fileId: String,
        jsonBytes: ByteArray
    ) {
        executeRequest(
            urlStr = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
            method = "PATCH",
            accessToken = accessToken,
            contentType = "application/json; charset=UTF-8",
            body = jsonBytes
        )
    }

    private fun pruneOldBackupsInFolder(
        accessToken: String,
        folderId: String,
        keepCount: Int
    ): Int {
        if (keepCount <= 0) return 0
        val query = URLEncoder.encode(
            "'$folderId' in parents and trashed = false",
            "UTF-8"
        )
        val url = "https://www.googleapis.com/drive/v3/files?q=$query&orderBy=createdTime%20desc&fields=files(id,name)&pageSize=50"
        val res = JSONObject(executeRequest(url, "GET", accessToken))
        val files = res.optJSONArray("files") ?: return 0
        var deleted = 0
        for (i in keepCount until files.length()) {
            val id = files.getJSONObject(i).optString("id", "")
            if (id.isNotBlank()) {
                try {
                    executeRequest(
                        urlStr = "https://www.googleapis.com/drive/v3/files/$id",
                        method = "DELETE",
                        accessToken = accessToken
                    )
                    deleted += 1
                } catch (_: Exception) {
                }
            }
        }
        return deleted
    }

    private fun executeRequest(
        urlStr: String,
        method: String,
        accessToken: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (contentType != null) {
                setRequestProperty("Content-Type", contentType)
            }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        return try {
            if (body != null) {
                conn.outputStream.use { os ->
                    os.write(body)
                    os.flush()
                }
            }
            val code = conn.responseCode
            val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("Google Drive HTTP $code: ${text.take(300)}")
            }
            text
        } finally {
            conn.disconnect()
        }
    }
}
