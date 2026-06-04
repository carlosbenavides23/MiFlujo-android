package com.carlos.miflujo.ui.backup

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.carlos.miflujo.domain.model.Movement
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupDocument(
    val fileName: String,
    val json: String,
)

object BackupExporter {
    suspend fun createBackupDocument(
        movements: List<Movement>,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): BackupDocument = withContext(Dispatchers.Default) {
        BackupDocument(
            fileName = createdAt.backupFileName(),
            json = BackupJsonSerializer.serialize(
                createdAt = createdAt,
                movements = movements,
            ),
        )
    }

    suspend fun saveBackup(
        context: Context,
        destinationUri: Uri,
        backupDocument: BackupDocument,
    ) {
        withContext(Dispatchers.IO) {
            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("No se pudo abrir el archivo de destino.")

            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(backupDocument.json)
            }
        }
    }

    suspend fun shareBackup(
        context: Context,
        backupDocument: BackupDocument,
    ) {
        val backupFile = withContext(Dispatchers.IO) {
            val outputFile = context.backupFile(backupDocument.fileName)
            outputFile.writeText(
                text = backupDocument.json,
                charset = Charsets.UTF_8,
            )
            outputFile
        }

        withContext(Dispatchers.Main.immediate) {
            shareJson(
                context = context,
                backupFile = backupFile,
            )
        }
    }

    private fun Context.backupFile(fileName: String): File {
        val outputDirectory = File(applicationContext.cacheDir, SharedBackupsDirectory)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("No se pudo crear el directorio de respaldos.")
        }
        return File(outputDirectory, fileName)
    }

    private fun shareJson(
        context: Context,
        backupFile: File,
    ) {
        val backupUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = BackupJsonMimeType
            putExtra(Intent.EXTRA_STREAM, backupUri)
            putExtra(Intent.EXTRA_SUBJECT, "MiFlujo - Respaldo local")
            clipData = ClipData.newUri(
                context.contentResolver,
                backupFile.name,
                backupUri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir respaldo").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        context.startActivity(chooser)
    }

    private fun LocalDateTime.backupFileName(): String =
        "MiFlujo-backup-${format(BackupFileNameFormatter)}.json"
}

private const val SharedBackupsDirectory = "shared_backups"
const val BackupJsonMimeType = "application/json"
private val BackupFileNameFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmm", Locale.ROOT)
