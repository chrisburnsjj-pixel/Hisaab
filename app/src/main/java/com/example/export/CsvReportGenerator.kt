package com.example.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object CsvReportGenerator {

    fun generateCsv(summary: ReportSummary, currency: String): String {
        val sb = StringBuilder()

        // Metadata Header
        sb.append("# Hisaab Financial Report\n")
        sb.append("# Period: ").append(escapeCsv(summary.rangeLabel)).append(" (").append(summary.startDate).append(" to ").append(summary.endDate).append(")\n")
        sb.append("# Currency: ").append(currency).append("\n")
        sb.append("# Total Income: ").append(String.format(java.util.Locale.US, "%.2f", summary.totalIncome)).append("\n")
        sb.append("# Total Expense: ").append(String.format(java.util.Locale.US, "%.2f", summary.totalExpense)).append("\n")
        sb.append("# Net Balance: ").append(String.format(java.util.Locale.US, "%.2f", summary.netBalance)).append("\n")
        sb.append("# Total Transactions: ").append(summary.transactionCount).append("\n")
        sb.append("\n")

        // CSV Column Header
        sb.append("Date,Type,Category,Title,Amount,Currency,Note\n")

        // Data Rows
        for (tx in summary.transactions) {
            sb.append(escapeCsv(tx.date)).append(",")
            sb.append(escapeCsv(tx.type)).append(",")
            sb.append(escapeCsv(tx.category)).append(",")
            sb.append(escapeCsv(tx.title)).append(",")
            sb.append(String.format(java.util.Locale.US, "%.2f", tx.amount)).append(",")
            sb.append(escapeCsv(currency)).append(",")
            sb.append(escapeCsv(tx.note))
            sb.append("\n")
        }

        return sb.toString()
    }

    fun writeToStream(summary: ReportSummary, currency: String, outputStream: OutputStream) {
        val csv = generateCsv(summary, currency)
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(csv)
            writer.flush()
        }
    }

    private fun escapeCsv(value: String): String {
        var str = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        if (str.contains(",") || str.contains("\"") || str.contains(";")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }
}
