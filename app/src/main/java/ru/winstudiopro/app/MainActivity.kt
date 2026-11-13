package ru.winstudiopro.app

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import ru.winstudiopro.app.ui.WinStudioProMainApp
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WinStudioProMainApp(
                onOpenPdfFile = { file, title ->
                    openPdfInViewer(file, title)
                },
                onSharePdfFile = { file, title ->
                    sharePdfViaApps(file, title)
                },
                onShareExportFile = { file, title ->
                    shareExportFile(file, title)
                }
            )
        }
    }

    private fun openPdfInViewer(file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (e: Exception) {
            // Если на устройстве нет приложения для просмотра PDF, предлагаем отправить его
            sharePdfViaApps(file, title)
        }
    }

    private fun sharePdfViaApps(file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Отправить PDF: $title")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "PDF сохранен: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareExportFile(file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Экспорт: $title")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Файл сохранен: ${file.name}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
