package com.example.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ReportExportHelper {

    fun sharePdfReport(
        context: Context,
        summary: ReportSummary,
        currency: String,
        userName: String = ""
    ) {
        try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val sanitizedLabel = summary.rangeLabel.replace(" ", "_").replace("/", "-")
            val pdfFile = File(reportsDir, "Hisaab_Report_${sanitizedLabel}.pdf")

            FileOutputStream(pdfFile).use { fos ->
                PdfReportGenerator.generatePdf(summary, currency, fos, userName)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Hisaab Financial Report - ${summary.rangeLabel}")
                putExtra(Intent.EXTRA_TEXT, "Here is the financial summary for ${summary.rangeLabel} from Hisaab.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Error sharing PDF: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun shareCsvReport(
        context: Context,
        summary: ReportSummary,
        currency: String
    ) {
        try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val sanitizedLabel = summary.rangeLabel.replace(" ", "_").replace("/", "-")
            val csvFile = File(reportsDir, "Hisaab_Transactions_${sanitizedLabel}.csv")

            FileOutputStream(csvFile).use { fos ->
                CsvReportGenerator.writeToStream(summary, currency, fos)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                csvFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Hisaab Transactions - ${summary.rangeLabel}")
                putExtra(Intent.EXTRA_TEXT, "CSV export of transactions for ${summary.rangeLabel} from Hisaab.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share CSV Export"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Error sharing CSV: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
