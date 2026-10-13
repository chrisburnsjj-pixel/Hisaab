package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width in points
    private const val PAGE_HEIGHT = 842 // A4 standard height in points
    private const val MARGIN = 36f

    fun generatePdf(
        summary: ReportSummary,
        currency: String,
        outputStream: OutputStream,
        userName: String = ""
    ) {
        val document = PdfDocument()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
        }

        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            isFakeBoldText = true
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(220, 224, 230)
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        var y = MARGIN + 20f

        fun drawHeader() {
            // Header
            boldPaint.textSize = 20f
            boldPaint.color = Color.rgb(15, 23, 42)
            canvas.drawText("Hisaab", MARGIN, y, boldPaint)

            boldPaint.textSize = 10f
            boldPaint.color = Color.rgb(100, 116, 139)
            canvas.drawText("Financial Summary & Transaction Report", MARGIN, y + 14f, boldPaint)

            if (userName.isNotBlank()) {
                textPaint.textSize = 9f
                textPaint.color = Color.rgb(71, 85, 105)
                canvas.drawText("Account: $userName", MARGIN, y + 26f, textPaint)
            }

            // Right-aligned metadata
            textPaint.textSize = 9f
            textPaint.color = Color.rgb(71, 85, 105)
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
            canvas.drawText("Period: ${summary.rangeLabel}", PAGE_WIDTH - MARGIN - 180f, y, boldPaint)
            canvas.drawText("Range: ${summary.startDate} to ${summary.endDate}", PAGE_WIDTH - MARGIN - 180f, y + 12f, textPaint)
            canvas.drawText("Generated: $dateStr", PAGE_WIDTH - MARGIN - 180f, y + 24f, textPaint)
            canvas.drawText("Base Currency: $currency", PAGE_WIDTH - MARGIN - 180f, y + 36f, textPaint)

            y += 48f

            // Dividing line
            strokePaint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, strokePaint)
            y += 16f
        }

        drawHeader()

        // 1. Summary Cards Row
        val cardWidth = (PAGE_WIDTH - (MARGIN * 2) - 30f) / 4f
        val cardHeight = 52f

        val kpis = listOf(
            Triple("TOTAL INCOME", String.format(Locale.US, "$currency %,.2f", summary.totalIncome), Color.rgb(16, 185, 129)),
            Triple("TOTAL EXPENSE", String.format(Locale.US, "$currency %,.2f", summary.totalExpense), Color.rgb(239, 68, 68)),
            Triple("NET BALANCE", String.format(Locale.US, "${if (summary.netBalance >= 0) "+" else ""}$currency %,.2f", summary.netBalance), if (summary.netBalance >= 0) Color.rgb(16, 185, 129) else Color.rgb(239, 68, 68)),
            Triple("TRANSACTIONS", "${summary.transactionCount} (${String.format(Locale.US, "%.0f", summary.savingsRate)}% saved)", Color.rgb(15, 23, 42))
        )

        for (i in kpis.indices) {
            val left = MARGIN + i * (cardWidth + 10f)
            val rect = RectF(left, y, left + cardWidth, y + cardHeight)

            fillPaint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(rect, 6f, 6f, fillPaint)

            strokePaint.color = Color.rgb(226, 232, 240)
            canvas.drawRoundRect(rect, 6f, 6f, strokePaint)

            boldPaint.textSize = 7f
            boldPaint.color = Color.rgb(100, 116, 139)
            canvas.drawText(kpis[i].first, left + 8f, y + 16f, boldPaint)

            boldPaint.textSize = 11f
            boldPaint.color = kpis[i].third
            canvas.drawText(kpis[i].second, left + 8f, y + 36f, boldPaint)
        }

        y += cardHeight + 24f

        // 2. Category Breakdown Section
        if (summary.categoryBreakdown.isNotEmpty()) {
            boldPaint.textSize = 12f
            boldPaint.color = Color.rgb(30, 41, 59)
            canvas.drawText("Expense Category Breakdown", MARGIN, y, boldPaint)
            y += 14f

            // Table Header
            fillPaint.color = Color.rgb(241, 245, 249)
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 18f, fillPaint)

            boldPaint.textSize = 8.5f
            boldPaint.color = Color.rgb(71, 85, 105)
            canvas.drawText("CATEGORY", MARGIN + 8f, y + 12f, boldPaint)
            canvas.drawText("COUNT", MARGIN + 220f, y + 12f, boldPaint)
            canvas.drawText("TOTAL AMOUNT", MARGIN + 320f, y + 12f, boldPaint)
            canvas.drawText("SHARE (%)", MARGIN + 440f, y + 12f, boldPaint)
            y += 20f

            textPaint.textSize = 9f
            for (cat in summary.categoryBreakdown) {
                if (y > PAGE_HEIGHT - 60f) {
                    // New page
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN + 20f
                    drawHeader()
                }

                strokePaint.color = Color.rgb(241, 245, 249)
                canvas.drawLine(MARGIN, y + 16f, PAGE_WIDTH - MARGIN, y + 16f, strokePaint)

                textPaint.color = Color.rgb(15, 23, 42)
                canvas.drawText(cat.category, MARGIN + 8f, y + 12f, textPaint)
                canvas.drawText(cat.count.toString(), MARGIN + 220f, y + 12f, textPaint)
                boldPaint.textSize = 9f
                boldPaint.color = Color.rgb(15, 23, 42)
                canvas.drawText(String.format(Locale.US, "$currency %,.2f", cat.amount), MARGIN + 320f, y + 12f, boldPaint)
                textPaint.color = Color.rgb(100, 116, 139)
                canvas.drawText(String.format(Locale.US, "%.1f%%", cat.percentageOfExpense), MARGIN + 440f, y + 12f, textPaint)

                y += 20f
            }
            y += 16f
        }

        // 3. Transactions List Section
        boldPaint.textSize = 12f
        boldPaint.color = Color.rgb(30, 41, 59)
        canvas.drawText("Transaction Details (${summary.transactions.size} records)", MARGIN, y, boldPaint)
        y += 14f

        // Table Header
        fillPaint.color = Color.rgb(241, 245, 249)
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 18f, fillPaint)

        boldPaint.textSize = 8.5f
        boldPaint.color = Color.rgb(71, 85, 105)
        canvas.drawText("DATE", MARGIN + 6f, y + 12f, boldPaint)
        canvas.drawText("TYPE", MARGIN + 75f, y + 12f, boldPaint)
        canvas.drawText("CATEGORY", MARGIN + 130f, y + 12f, boldPaint)
        canvas.drawText("TITLE & NOTE", MARGIN + 230f, y + 12f, boldPaint)
        canvas.drawText("AMOUNT", MARGIN + 440f, y + 12f, boldPaint)
        y += 20f

        for (tx in summary.transactions) {
            if (y > PAGE_HEIGHT - 60f) {
                // New page
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN + 20f
                drawHeader()

                // Re-draw table header on new page
                fillPaint.color = Color.rgb(241, 245, 249)
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 18f, fillPaint)
                boldPaint.textSize = 8.5f
                boldPaint.color = Color.rgb(71, 85, 105)
                canvas.drawText("DATE", MARGIN + 6f, y + 12f, boldPaint)
                canvas.drawText("TYPE", MARGIN + 75f, y + 12f, boldPaint)
                canvas.drawText("CATEGORY", MARGIN + 130f, y + 12f, boldPaint)
                canvas.drawText("TITLE & NOTE", MARGIN + 230f, y + 12f, boldPaint)
                canvas.drawText("AMOUNT", MARGIN + 440f, y + 12f, boldPaint)
                y += 20f
            }

            strokePaint.color = Color.rgb(241, 245, 249)
            canvas.drawLine(MARGIN, y + 18f, PAGE_WIDTH - MARGIN, y + 18f, strokePaint)

            textPaint.textSize = 8.5f
            textPaint.color = Color.rgb(100, 116, 139)
            canvas.drawText(tx.date, MARGIN + 6f, y + 12f, textPaint)

            // Type badge
            val isIncome = tx.type == "INCOME"
            boldPaint.textSize = 8f
            boldPaint.color = if (isIncome) Color.rgb(16, 185, 129) else Color.rgb(239, 68, 68)
            canvas.drawText(if (isIncome) "INCOME" else "EXPENSE", MARGIN + 75f, y + 12f, boldPaint)

            textPaint.color = Color.rgb(51, 65, 85)
            canvas.drawText(tx.category.take(15), MARGIN + 130f, y + 12f, textPaint)

            val displayTitle = if (tx.note.isNotBlank()) "${tx.title} (${tx.note})" else tx.title
            canvas.drawText(displayTitle.take(32), MARGIN + 230f, y + 12f, textPaint)

            boldPaint.textSize = 8.5f
            boldPaint.color = if (isIncome) Color.rgb(16, 185, 129) else Color.rgb(239, 68, 68)
            val amtStr = "${if (isIncome) "+" else "-"}$currency ${String.format(Locale.US, "%,.2f", tx.amount)}"
            canvas.drawText(amtStr, MARGIN + 440f, y + 12f, boldPaint)

            y += 22f
        }

        // Draw Footer
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(148, 163, 184)
        canvas.drawText("Generated by Hisaab &bull; Page $pageNumber", MARGIN, PAGE_HEIGHT - 20f, textPaint)

        document.finishPage(page)

        try {
            document.writeTo(outputStream)
            outputStream.flush()
        } finally {
            document.close()
        }
    }
}
