package com.example.export

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

object PrintReportHelper {
    private const val TAG = "PrintReportHelper"

    /**
     * Renders the HTML report with clean print-only stylesheet and triggers the Android PrintManager.
     */
    fun printReport(
        context: Context,
        htmlContent: String,
        jobName: String = "Hisaab_Financial_Report"
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Printing is not supported on this device.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            // Create an in-memory WebView to render the HTML document with @media print CSS
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = false

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    try {
                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                        val printAttributes = PrintAttributes.Builder()
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()

                        printManager.print(jobName, printAdapter, printAttributes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initiating print job", e)
                        Toast.makeText(context, "Failed to start print job: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring webview for print", e)
            Toast.makeText(context, "Unable to prepare report for printing: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
