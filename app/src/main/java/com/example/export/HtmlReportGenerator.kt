package com.example.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HtmlReportGenerator {

    fun generateHtmlReport(summary: ReportSummary, currency: String, userName: String = ""): String {
        val generatedAt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())

        val balanceColor = if (summary.netBalance >= 0) "#10b981" else "#ef4444"
        val balancePrefix = if (summary.netBalance >= 0) "+" else ""

        val categoryRows = StringBuilder()
        for (item in summary.categoryBreakdown) {
            val pctFormatted = String.format(Locale.US, "%.1f", item.percentageOfExpense)
            val amtFormatted = String.format(Locale.US, "%,.2f", item.amount)
            categoryRows.append("""
                <tr>
                    <td style="font-weight: 600;">${escapeHtml(item.category)}</td>
                    <td style="text-align: center;">${item.count}</td>
                    <td style="text-align: right; font-weight: 600;">$currency $amtFormatted</td>
                    <td style="text-align: right;">
                        <div style="display: flex; align-items: center; justify-content: flex-end; gap: 8px;">
                            <span style="font-size: 12px; color: #6b7280;">$pctFormatted%</span>
                            <div style="width: 60px; height: 6px; background-color: #f3f4f6; border-radius: 999px; overflow: hidden;">
                                <div style="width: ${pctFormatted}%; height: 100%; background-color: #ef4444;"></div>
                            </div>
                        </div>
                    </td>
                </tr>
            """.trimIndent())
        }

        val transactionRows = StringBuilder()
        for (tx in summary.transactions) {
            val isIncome = tx.type == "INCOME"
            val typeColor = if (isIncome) "#10b981" else "#ef4444"
            val typeBg = if (isIncome) "#ecfdf5" else "#fef2f2"
            val typeLabel = if (isIncome) "Income" else "Expense"
            val prefix = if (isIncome) "+" else "-"
            val amtFormatted = String.format(Locale.US, "%,.2f", tx.amount)

            transactionRows.append("""
                <tr>
                    <td style="white-space: nowrap; color: #6b7280; font-size: 13px;">${escapeHtml(tx.date)}</td>
                    <td>
                        <span class="badge" style="background-color: $typeBg; color: $typeColor; border: 1px solid ${typeColor}33;">
                            $typeLabel
                        </span>
                    </td>
                    <td>
                        <span style="font-weight: 500; color: #374151;">${escapeHtml(tx.category)}</span>
                    </td>
                    <td>
                        <div style="font-weight: 600; color: #111827;">${escapeHtml(tx.title)}</div>
                        ${if (tx.note.isNotBlank()) "<div style=\"font-size: 12px; color: #6b7280; margin-top: 2px;\">${escapeHtml(tx.note)}</div>" else ""}
                    </td>
                    <td style="text-align: right; font-weight: 700; color: $typeColor; white-space: nowrap;">
                        $prefix$currency $amtFormatted
                    </td>
                </tr>
            """.trimIndent())
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Hisaab Financial Report - ${escapeHtml(summary.rangeLabel)}</title>
    <style>
        /* Base Screen Styles */
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            color: #111827;
            background-color: #f9fafb;
            padding: 24px;
            line-height: 1.5;
            -webkit-print-color-adjust: exact;
            print-color-adjust: exact;
        }
        .container {
            max-width: 900px;
            margin: 0 auto;
            background: #ffffff;
            border-radius: 16px;
            padding: 32px;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
            border: 1px solid #e5e7eb;
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            border-bottom: 2px solid #f3f4f6;
            padding-bottom: 24px;
            margin-bottom: 24px;
        }
        .brand-title {
            font-size: 26px;
            font-weight: 800;
            color: #0f172a;
            letter-spacing: -0.5px;
        }
        .brand-subtitle {
            font-size: 13px;
            color: #64748b;
            margin-top: 4px;
        }
        .meta-box {
            text-align: right;
            font-size: 13px;
            color: #475569;
        }
        .meta-box strong {
            color: #0f172a;
        }
        .period-pill {
            display: inline-block;
            background: #e0f2fe;
            color: #0369a1;
            font-size: 12px;
            font-weight: 700;
            padding: 4px 10px;
            border-radius: 999px;
            margin-bottom: 6px;
        }
        .kpi-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 16px;
            margin-bottom: 32px;
        }
        .kpi-card {
            background: #f8fafc;
            border: 1px solid #e2e8f0;
            border-radius: 12px;
            padding: 16px;
        }
        .kpi-label {
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: #64748b;
            margin-bottom: 6px;
        }
        .kpi-value {
            font-size: 20px;
            font-weight: 800;
            color: #0f172a;
        }
        .kpi-income .kpi-value { color: #10b981; }
        .kpi-expense .kpi-value { color: #ef4444; }
        .kpi-balance .kpi-value { color: $balanceColor; }

        .section {
            margin-bottom: 32px;
        }
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: baseline;
            margin-bottom: 12px;
            padding-bottom: 6px;
            border-bottom: 1px solid #e2e8f0;
        }
        .section-title {
            font-size: 16px;
            font-weight: 700;
            color: #1e293b;
        }
        .section-sub {
            font-size: 12px;
            color: #64748b;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
        }
        th {
            background-color: #f1f5f9;
            color: #475569;
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            padding: 10px 14px;
            text-align: left;
            border-bottom: 1px solid #cbd5e1;
        }
        td {
            padding: 12px 14px;
            border-bottom: 1px solid #f1f5f9;
            color: #334155;
            vertical-align: middle;
        }
        tr:last-child td {
            border-bottom: none;
        }
        tbody tr:hover {
            background-color: #f8fafc;
        }
        .badge {
            display: inline-block;
            font-size: 11px;
            font-weight: 700;
            padding: 3px 8px;
            border-radius: 6px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .footer {
            margin-top: 32px;
            padding-top: 16px;
            border-top: 1px solid #e2e8f0;
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 12px;
            color: #94a3b8;
        }

        /* Clean Print-Only Stylesheet */
        @media print {
            @page {
                size: A4 portrait;
                margin: 12mm 14mm 14mm 14mm;
            }
            body {
                background: #ffffff !important;
                padding: 0 !important;
                color: #000000 !important;
                font-size: 12pt !important;
            }
            .container {
                max-width: 100% !important;
                margin: 0 !important;
                padding: 0 !important;
                border: none !important;
                box-shadow: none !important;
                border-radius: 0 !important;
            }
            .no-print {
                display: none !important;
            }
            .kpi-grid {
                gap: 10px !important;
                margin-bottom: 20px !important;
            }
            .kpi-card {
                padding: 10px !important;
                background: #fdfdfd !important;
                border: 1px solid #cccccc !important;
                page-break-inside: avoid;
            }
            .kpi-value {
                font-size: 15pt !important;
            }
            .section {
                page-break-inside: auto;
                margin-bottom: 20px !important;
            }
            .section-title {
                font-size: 13pt !important;
            }
            table {
                font-size: 10pt !important;
                width: 100% !important;
            }
            th {
                background-color: #eaeaea !important;
                color: #000000 !important;
                padding: 6px 8px !important;
                border-bottom: 1.5pt solid #888888 !important;
            }
            td {
                padding: 6px 8px !important;
                border-bottom: 0.5pt solid #dddddd !important;
            }
            tr {
                page-break-inside: avoid !important;
                page-break-after: auto !important;
            }
            thead {
                display: table-header-group !important;
            }
            tfoot {
                display: table-footer-group !important;
            }
            .header {
                border-bottom: 1.5pt solid #444444 !important;
                padding-bottom: 12px !important;
                margin-bottom: 16px !important;
            }
            .brand-title {
                font-size: 20pt !important;
                color: #000000 !important;
            }
            .footer {
                border-top: 1pt solid #aaaaaa !important;
                font-size: 9pt !important;
                color: #555555 !important;
                page-break-inside: avoid;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header">
            <div>
                <div class="brand-title">Hisaab</div>
                <div class="brand-subtitle">Financial Summary & Transaction Report</div>
                ${if (userName.isNotBlank()) "<div style=\"font-size: 12px; color: #475569; margin-top: 2px;\">Account: <strong>" + escapeHtml(userName) + "</strong></div>" else ""}
            </div>
            <div class="meta-box">
                <div class="period-pill">${escapeHtml(summary.rangeLabel)}</div>
                <div>Range: <strong>${escapeHtml(summary.startDate)}</strong> to <strong>${escapeHtml(summary.endDate)}</strong></div>
                <div>Generated: ${escapeHtml(generatedAt)}</div>
                <div>Base Currency: <strong>$currency</strong></div>
            </div>
        </div>

        <!-- KPI Metrics Grid -->
        <div class="kpi-grid">
            <div class="kpi-card kpi-income">
                <div class="kpi-label">Total Income</div>
                <div class="kpi-value">$currency ${String.format(Locale.US, "%,.2f", summary.totalIncome)}</div>
            </div>
            <div class="kpi-card kpi-expense">
                <div class="kpi-label">Total Expense</div>
                <div class="kpi-value">$currency ${String.format(Locale.US, "%,.2f", summary.totalExpense)}</div>
            </div>
            <div class="kpi-card kpi-balance">
                <div class="kpi-label">Net Balance</div>
                <div class="kpi-value">$balancePrefix$currency ${String.format(Locale.US, "%,.2f", summary.netBalance)}</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Transactions</div>
                <div class="kpi-value">${summary.transactionCount} <span style="font-size: 12px; font-weight: 500; color: #64748b;">(${String.format(Locale.US, "%.0f", summary.savingsRate)}% saved)</span></div>
            </div>
        </div>

        <!-- Category Breakdown Section -->
        ${if (summary.categoryBreakdown.isNotEmpty()) """
        <div class="section">
            <div class="section-header">
                <div class="section-title">Expense Category Breakdown</div>
                <div class="section-sub">${summary.categoryBreakdown.size} active categories</div>
            </div>
            <table>
                <thead>
                    <tr>
                        <th>Category</th>
                        <th style="text-align: center;">Count</th>
                        <th style="text-align: right;">Total Amount</th>
                        <th style="text-align: right;">Share of Expenses</th>
                    </tr>
                </thead>
                <tbody>
                    $categoryRows
                </tbody>
            </table>
        </div>
        """ else """
        <div class="section" style="padding: 12px; background: #f8fafc; border-radius: 8px; text-align: center; color: #64748b; font-size: 13px;">
            No expenses recorded for this period.
        </div>
        """}

        <!-- Transactions List Section -->
        <div class="section">
            <div class="section-header">
                <div class="section-title">Transaction Details</div>
                <div class="section-sub">${summary.transactions.size} entries recorded</div>
            </div>
            ${if (summary.transactions.isNotEmpty()) """
            <table>
                <thead>
                    <tr>
                        <th>Date</th>
                        <th>Type</th>
                        <th>Category</th>
                        <th>Title & Note</th>
                        <th style="text-align: right;">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $transactionRows
                </tbody>
            </table>
            """ else """
            <div style="padding: 24px; text-align: center; color: #94a3b8; font-size: 14px;">
                No transactions found for the selected range.
            </div>
            """}
        </div>

        <!-- Footer -->
        <div class="footer">
            <div>Generated by Hisaab Personal Finance</div>
            <div>Confidential &bull; Page 1 of 1</div>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
