package com.carlos.miflujo.ui.report.export

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.carlos.miflujo.ui.report.ReportUiState
import java.io.File
import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReportPdfExporter {
    suspend fun shareMonthlyReport(
        context: Context,
        uiState: ReportUiState,
    ) {
        val pdfFile = withContext(Dispatchers.IO) {
            val outputFile = context.monthlyReportFile(uiState.selectedMonth.pdfFileName())
            ReportPdfGenerator().generate(
                uiState = uiState,
                outputFile = outputFile,
            )
            outputFile
        }

        withContext(Dispatchers.Main.immediate) {
            sharePdf(
                context = context,
                pdfFile = pdfFile,
            )
        }
    }

    private fun Context.monthlyReportFile(fileName: String): File {
        val outputDirectory = File(applicationContext.cacheDir, SharedReportsDirectory)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("No se pudo crear el directorio de reportes.")
        }
        return File(outputDirectory, fileName)
    }

    private fun sharePdf(
        context: Context,
        pdfFile: File,
    ) {
        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, "MiFlujo - Reporte mensual")
            clipData = ClipData.newUri(
                context.contentResolver,
                pdfFile.name,
                pdfUri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir reporte").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        context.startActivity(chooser)
    }

    private fun YearMonth.pdfFileName(): String {
        return "MiFlujo-reporte-$year-${monthValue.toString().padStart(2, '0')}.pdf"
    }
}

private const val SharedReportsDirectory = "shared_reports"
