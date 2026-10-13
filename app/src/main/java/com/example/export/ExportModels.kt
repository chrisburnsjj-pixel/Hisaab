package com.example.export

import com.example.data.model.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExportRangeType(val displayName: String) {
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    CUSTOM("Custom")
}

data class CategoryReportItem(
    val category: String,
    val amount: Double,
    val count: Int,
    val percentageOfExpense: Double
)

data class ReportSummary(
    val rangeLabel: String,
    val startDate: String,
    val endDate: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val netBalance: Double,
    val savingsRate: Double,
    val transactionCount: Int,
    val categoryBreakdown: List<CategoryReportItem>,
    val transactions: List<Transaction>
)

object ExportRangeCalculator {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun computeDateBounds(
        type: ExportRangeType,
        year: Int,
        month: Int, // 1-12
        quarter: Int, // 1-4
        customStart: String,
        customEnd: String
    ): Triple<String, String, String> { // Pair of (startDate, endDate, label)
        val cal = Calendar.getInstance()

        return when (type) {
            ExportRangeType.MONTHLY -> {
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month - 1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = dateFormat.format(cal.time)

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                val end = dateFormat.format(cal.time)

                val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
                val label = monthFormat.format(cal.time)
                Triple(start, end, label)
            }
            ExportRangeType.QUARTERLY -> {
                val startMonth = (quarter - 1) * 3
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, startMonth)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = dateFormat.format(cal.time)

                val endMonth = startMonth + 2
                cal.set(Calendar.MONTH, endMonth)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                val end = dateFormat.format(cal.time)

                val label = "Q$quarter $year"
                Triple(start, end, label)
            }
            ExportRangeType.YEARLY -> {
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = dateFormat.format(cal.time)

                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                val end = dateFormat.format(cal.time)

                val label = "Year $year"
                Triple(start, end, label)
            }
            ExportRangeType.CUSTOM -> {
                val start = if (customStart.isNotBlank()) customStart else dateFormat.format(Date())
                val end = if (customEnd.isNotBlank()) customEnd else dateFormat.format(Date())
                val label = "$start to $end"
                Triple(start, end, label)
            }
        }
    }

    fun buildSummary(
        allTransactions: List<Transaction>,
        startDate: String,
        endDate: String,
        rangeLabel: String
    ): ReportSummary {
        val filtered = allTransactions.filter { tx ->
            val d = tx.date
            d >= startDate && d <= endDate
        }.sortedByDescending { it.date }

        val totalIncome = filtered.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = filtered.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) {
            ((totalIncome - totalExpense) / totalIncome * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        val expenseTxList = filtered.filter { it.type == "EXPENSE" }
        val categoryBreakdown = expenseTxList
            .groupBy { it.category }
            .map { (cat, list) ->
                val amt = list.sumOf { it.amount }
                val pct = if (totalExpense > 0) (amt / totalExpense * 100.0) else 0.0
                CategoryReportItem(
                    category = cat,
                    amount = amt,
                    count = list.size,
                    percentageOfExpense = pct
                )
            }
            .sortedByDescending { it.amount }

        return ReportSummary(
            rangeLabel = rangeLabel,
            startDate = startDate,
            endDate = endDate,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netBalance = netBalance,
            savingsRate = savingsRate,
            transactionCount = filtered.size,
            categoryBreakdown = categoryBreakdown,
            transactions = filtered
        )
    }
}
