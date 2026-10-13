package com.example.ui.analytics

import com.example.data.model.Transaction
import com.example.ui.components.CategorySlice
import com.example.ui.components.ChartCategoryColors
import com.example.ui.components.DailyTrendPoint
import com.example.ui.components.MonthlyBarData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AnalyticsPeriod {
    MONTHLY,
    QUARTERLY,
    YEARLY
}

data class PeriodMetrics(
    val period: AnalyticsPeriod,
    val currentPeriodLabel: String,
    val previousPeriodLabel: String,
    val currentIncome: Double,
    val previousIncome: Double,
    val incomeChangePct: Double,
    val currentExpense: Double,
    val previousExpense: Double,
    val expenseChangePct: Double,
    val currentBalance: Double,
    val previousBalance: Double,
    val balanceChangePct: Double,
    val savingsRate: Double,
    val topExpenseCategory: String,
    val averageDailySpend: Double,
    val categorySlices: List<CategorySlice>,
    val monthlyBars: List<MonthlyBarData>,
    val dailyTrend: List<DailyTrendPoint>
)

object AnalyticsCalculator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthYearFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val shortMonthFormat = SimpleDateFormat("MMM", Locale.US)

    fun calculate(
        transactions: List<Transaction>,
        period: AnalyticsPeriod,
        referenceDate: Date = Date()
    ): PeriodMetrics {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) // 0-indexed

        val (currentTxs, previousTxs, currentLabel, prevLabel) = when (period) {
            AnalyticsPeriod.MONTHLY -> {
                val currentMonthStr = String.format(Locale.US, "%04d-%02d", currentYear, currentMonth + 1)
                val prevCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                val prevMonthStr = String.format(Locale.US, "%04d-%02d", prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH) + 1)

                val cList = transactions.filter { it.date.startsWith(currentMonthStr) }
                val pList = transactions.filter { it.date.startsWith(prevMonthStr) }

                val cLabel = SimpleDateFormat("MMM yyyy", Locale.US).format(cal.time)
                val pLabel = SimpleDateFormat("MMM yyyy", Locale.US).format(prevCal.time)
                Quad(cList, pList, cLabel, pLabel)
            }
            AnalyticsPeriod.QUARTERLY -> {
                val currentQ = (currentMonth / 3) + 1
                val prevQ = if (currentQ == 1) 4 else currentQ - 1
                val prevQYear = if (currentQ == 1) currentYear - 1 else currentYear

                fun inQuarter(dateStr: String, q: Int, y: Int): Boolean {
                    if (!dateStr.startsWith(y.toString())) return false
                    val month = dateStr.substringAfter("-").substringBefore("-").toIntOrNull() ?: return false
                    return (month - 1) / 3 + 1 == q
                }

                val cList = transactions.filter { inQuarter(it.date, currentQ, currentYear) }
                val pList = transactions.filter { inQuarter(it.date, prevQ, prevQYear) }
                Quad(cList, pList, "Q$currentQ $currentYear", "Q$prevQ $prevQYear")
            }
            AnalyticsPeriod.YEARLY -> {
                val currentYearStr = currentYear.toString()
                val prevYearStr = (currentYear - 1).toString()

                val cList = transactions.filter { it.date.startsWith(currentYearStr) }
                val pList = transactions.filter { it.date.startsWith(prevYearStr) }
                Quad(cList, pList, currentYearStr, prevYearStr)
            }
        }

        val curIncome = currentTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
        val prevIncome = previousTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
        val incomeChange = calcChangePct(curIncome, prevIncome)

        val curExpense = currentTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val prevExpense = previousTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val expenseChange = calcChangePct(curExpense, prevExpense)

        val curBalance = curIncome - curExpense
        val prevBalance = prevIncome - prevExpense
        val balanceChange = calcChangePct(curBalance, prevBalance)

        val savingsRate = if (curIncome > 0) ((curBalance / curIncome) * 100.0).coerceIn(-100.0, 100.0) else 0.0

        // Categories breakdown for current period expenses (or all if no expenses)
        val expenseTxs = currentTxs.filter { it.type == "EXPENSE" }
        val totalCatAmount = expenseTxs.sumOf { it.amount }
        val groupedCat = expenseTxs.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val topCategory = groupedCat.firstOrNull()?.first ?: "None"

        val slices = groupedCat.mapIndexed { index, (cat, amount) ->
            val pct = if (totalCatAmount > 0) (amount / totalCatAmount).toFloat() else 0f
            CategorySlice(
                category = cat,
                amount = amount,
                percentage = pct,
                color = ChartCategoryColors[index % ChartCategoryColors.size]
            )
        }

        // Monthly bars: Last 6 months history
        val monthlyBars = mutableListOf<MonthlyBarData>()
        val loopCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -5) }
        for (i in 0..5) {
            val y = loopCal.get(Calendar.YEAR)
            val m = loopCal.get(Calendar.MONTH) + 1
            val prefix = String.format(Locale.US, "%04d-%02d", y, m)
            val monthLabel = shortMonthFormat.format(loopCal.time)
            val monthTxs = transactions.filter { it.date.startsWith(prefix) }
            val inc = monthTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
            val exp = monthTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            monthlyBars.add(MonthlyBarData(monthLabel, inc, exp))
            loopCal.add(Calendar.MONTH, 1)
        }

        // Daily trend for current period (last 14 days or grouped by date)
        val dailyGroups = currentTxs.filter { it.type == "EXPENSE" }
            .groupBy { it.date }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toSortedMap()

        val dailyPoints = if (dailyGroups.isEmpty()) {
            emptyList<DailyTrendPoint>()
        } else {
            dailyGroups.entries.toList().takeLast(14).map { entry ->
                val dateStr = entry.key
                val sum = entry.value
                val shortLabel = try {
                    val parsed = dateFormat.parse(dateStr)
                    SimpleDateFormat("dd MMM", Locale.US).format(parsed ?: Date())
                } catch (e: Exception) {
                    dateStr.takeLast(5)
                }
                DailyTrendPoint(shortLabel, sum)
            }
        }

        val daysCount = (if (dailyGroups.isNotEmpty()) dailyGroups.size else 1).coerceAtLeast(1)
        val avgDaily = curExpense / daysCount

        return PeriodMetrics(
            period = period,
            currentPeriodLabel = currentLabel,
            previousPeriodLabel = prevLabel,
            currentIncome = curIncome,
            previousIncome = prevIncome,
            incomeChangePct = incomeChange,
            currentExpense = curExpense,
            previousExpense = prevExpense,
            expenseChangePct = expenseChange,
            currentBalance = curBalance,
            previousBalance = prevBalance,
            balanceChangePct = balanceChange,
            savingsRate = savingsRate,
            topExpenseCategory = topCategory,
            averageDailySpend = avgDaily,
            categorySlices = slices,
            monthlyBars = monthlyBars,
            dailyTrend = dailyPoints
        )
    }

    private fun calcChangePct(current: Double, previous: Double): Double {
        return if (previous == 0.0) {
            if (current > 0.0) 100.0 else 0.0
        } else {
            ((current - previous) / kotlin.math.abs(previous)) * 100.0
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
