package com.carlos.miflujo.ui.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupImporter {
    suspend fun readBackup(
        contentResolver: ContentResolver,
        sourceUri: Uri,
    ): ParsedBackup = withContext(Dispatchers.IO) {
        val inputStream = contentResolver.openInputStream(sourceUri)
            ?: throw IOException("No se pudo abrir el respaldo seleccionado.")
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
        BackupJsonParser.parse(json)
    }
}
