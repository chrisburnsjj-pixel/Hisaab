package com.example

import com.example.data.model.Transaction
import com.example.ui.analytics.AnalyticsCalculator
import com.example.ui.analytics.AnalyticsPeriod
import com.example.ui.home.DefaultCategories
import com.example.ui.home.SupportedCurrencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExampleUnitTest {

    @Test
    fun testDefaultCategoriesSpecification() {
        val expected = listOf(
            "Food", "Transport", "Bills", "Shopping", "Health",
            "Education", "Rent", "Salary", "Business", "Other"
        )
        assertEquals(expected, DefaultCategories)
    }

    @Test
    fun testDefaultCurrencyIsPKR() {
        val pkr = SupportedCurrencies.find { it.code == "PKR" }
        assertTrue("PKR must be present in supported currencies", pkr != null)
        assertEquals("PKR", SupportedCurrencies.first().code)
    }

    @Test
    fun testAnalyticsCalculatorWithTransactions() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dateFormat.format(Date())

        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        val prevMonthStr = dateFormat.format(cal.time)

        val txs = listOf(
            Transaction(
                id = "1",
                userId = "user1",
                title = "Salary",
                amount = 100000.0,
                type = "INCOME",
                category = "Salary",
                date = todayStr
            ),
            Transaction(
                id = "2",
                userId = "user1",
                title = "Groceries",
                amount = 25000.0,
                type = "EXPENSE",
                category = "Food",
                date = todayStr
            ),
            Transaction(
                id = "3",
                userId = "user1",
                title = "Electricity",
                amount = 15000.0,
                type = "EXPENSE",
                category = "Bills",
                date = todayStr
            ),
            Transaction(
                id = "4",
                userId = "user1",
                title = "Last Month Salary",
                amount = 90000.0,
                type = "INCOME",
                category = "Salary",
                date = prevMonthStr
            ),
            Transaction(
                id = "5",
                userId = "user1",
                title = "Last Month Rent",
                amount = 35000.0,
                type = "EXPENSE",
                category = "Rent",
                date = prevMonthStr
            )
        )

        val metrics = AnalyticsCalculator.calculate(txs, AnalyticsPeriod.MONTHLY)

        assertEquals(100000.0, metrics.currentIncome, 0.01)
        assertEquals(40000.0, metrics.currentExpense, 0.01)
        assertEquals(60000.0, metrics.currentBalance, 0.01)

        assertEquals(90000.0, metrics.previousIncome, 0.01)
        assertEquals(35000.0, metrics.previousExpense, 0.01)
        assertEquals(55000.0, metrics.previousBalance, 0.01)

        // Savings rate = (100000 - 40000) / 100000 * 100 = 60.0%
        assertEquals(60.0, metrics.savingsRate, 0.01)

        // Category breakdown
        assertEquals(2, metrics.categorySlices.size)
        assertTrue(metrics.categorySlices.any { it.category == "Food" && it.amount == 25000.0 })
        assertTrue(metrics.categorySlices.any { it.category == "Bills" && it.amount == 15000.0 })
    }

    @Test
    fun testVoiceSpeechLanguages() {
        assertEquals("ur-PK", com.example.speech.SpeechLanguage.URDU.code)
        assertEquals("en-US", com.example.speech.SpeechLanguage.ENGLISH.code)
    }

    @Test
    fun testGeminiVoiceParsingSampleInputs() {
        val todayStr = "2026-10-13"

        // Example 1 from user prompt: "aaj 500 rupay chai aur khaney pe kharch kiye"
        val input1 = "aaj 500 rupay chai aur khaney pe kharch kiye"
        val parsed1 = com.example.ai.GeminiService.fallbackLocalParser(
            input1,
            DefaultCategories,
            todayStr
        )
        assertEquals("EXPENSE", parsed1.type)
        assertEquals(500.0, parsed1.amount, 0.01)
        assertEquals("Food", parsed1.category)
        assertEquals(todayStr, parsed1.date)

        // Example 2 from user prompt: "salary 80000 mili"
        val input2 = "salary 80000 mili"
        val parsed2 = com.example.ai.GeminiService.fallbackLocalParser(
            input2,
            DefaultCategories,
            todayStr
        )
        assertEquals("INCOME", parsed2.type)
        assertEquals(80000.0, parsed2.amount, 0.01)
        assertEquals("Salary", parsed2.category)
        assertEquals(todayStr, parsed2.date)
    }

    @Test
    fun testExportRangeBoundsCalculation() {
        // Monthly
        val (mStart, mEnd, mLabel) = com.example.export.ExportRangeCalculator.computeDateBounds(
            type = com.example.export.ExportRangeType.MONTHLY,
            year = 2026,
            month = 10,
            quarter = 4,
            customStart = "",
            customEnd = ""
        )
        assertEquals("2026-10-01", mStart)
        assertEquals("2026-10-31", mEnd)
        assertEquals("October 2026", mLabel)

        // Quarterly
        val (qStart, qEnd, qLabel) = com.example.export.ExportRangeCalculator.computeDateBounds(
            type = com.example.export.ExportRangeType.QUARTERLY,
            year = 2026,
            month = 10,
            quarter = 3,
            customStart = "",
            customEnd = ""
        )
        assertEquals("2026-07-01", qStart)
        assertEquals("2026-09-30", qEnd)
        assertEquals("Q3 2026", qLabel)

        // Yearly
        val (yStart, yEnd, yLabel) = com.example.export.ExportRangeCalculator.computeDateBounds(
            type = com.example.export.ExportRangeType.YEARLY,
            year = 2026,
            month = 1,
            quarter = 1,
            customStart = "",
            customEnd = ""
        )
        assertEquals("2026-01-01", yStart)
        assertEquals("2026-12-31", yEnd)
        assertEquals("Year 2026", yLabel)

        // Custom
        val (cStart, cEnd, cLabel) = com.example.export.ExportRangeCalculator.computeDateBounds(
            type = com.example.export.ExportRangeType.CUSTOM,
            year = 2026,
            month = 1,
            quarter = 1,
            customStart = "2026-05-10",
            customEnd = "2026-06-20"
        )
        assertEquals("2026-05-10", cStart)
        assertEquals("2026-06-20", cEnd)
        assertEquals("2026-05-10 to 2026-06-20", cLabel)
    }

    @Test
    fun testCsvReportGeneration() {
        val txs = listOf(
            Transaction(
                id = "tx1",
                userId = "u1",
                title = "Dinner with friends",
                amount = 2500.0,
                type = "EXPENSE",
                category = "Food",
                date = "2026-10-10",
                note = "biryani & drinks"
            ),
            Transaction(
                id = "tx2",
                userId = "u1",
                title = "Monthly Salary",
                amount = 80000.0,
                type = "INCOME",
                category = "Salary",
                date = "2026-10-01",
                note = "received via bank"
            )
        )

        val summary = com.example.export.ExportRangeCalculator.buildSummary(
            allTransactions = txs,
            startDate = "2026-10-01",
            endDate = "2026-10-31",
            rangeLabel = "October 2026"
        )

        assertEquals(80000.0, summary.totalIncome, 0.01)
        assertEquals(2500.0, summary.totalExpense, 0.01)
        assertEquals(77500.0, summary.netBalance, 0.01)
        assertEquals(2, summary.transactionCount)
        assertEquals(1, summary.categoryBreakdown.size)

        val csv = com.example.export.CsvReportGenerator.generateCsv(summary, "PKR")
        assertTrue("CSV should contain header line", csv.contains("Date,Type,Category,Title,Amount,Currency,Note"))
        assertTrue("CSV should contain expense row", csv.contains("2026-10-10,EXPENSE,Food"))
        assertTrue("CSV should contain income row", csv.contains("2026-10-01,INCOME,Salary"))
        assertTrue("CSV should contain formatted amounts", csv.contains("2500.00,PKR"))
    }

    @Test
    fun testHtmlReportAndPrintStylesheet() {
        val txs = listOf(
            Transaction(
                id = "tx1",
                userId = "u1",
                title = "Grocery",
                amount = 4500.0,
                type = "EXPENSE",
                category = "Food",
                date = "2026-10-12"
            )
        )

        val summary = com.example.export.ExportRangeCalculator.buildSummary(
            allTransactions = txs,
            startDate = "2026-10-01",
            endDate = "2026-10-31",
            rangeLabel = "October 2026"
        )

        val html = com.example.export.HtmlReportGenerator.generateHtmlReport(summary, "PKR", "Junaid")
        assertTrue("HTML should have @media print stylesheet", html.contains("@media print"))
        assertTrue("HTML should specify print page margin", html.contains("@page"))
        assertTrue("HTML should contain Hisaab header", html.contains("Hisaab"))
        assertTrue("HTML should contain category breakdown", html.contains("Expense Category Breakdown"))
        assertTrue("HTML should contain transaction details", html.contains("Transaction Details"))
        assertTrue("HTML should contain currency", html.contains("PKR"))
    }

    @Test
    fun testFirebaseAnalyticsEventsAndPrivacy() {
        com.example.analytics.AnalyticsManager.clearEvents()

        // 1. Log sign_up & login
        com.example.analytics.AnalyticsManager.logSignUp()
        com.example.analytics.AnalyticsManager.logLogin()

        // 2. Log add_transaction (manual & voice)
        com.example.analytics.AnalyticsManager.logAddTransaction("manual")
        com.example.analytics.AnalyticsManager.logAddTransaction("voice")

        // 3. Log voice events
        com.example.analytics.AnalyticsManager.logVoiceStart()
        com.example.analytics.AnalyticsManager.logVoiceSuccess()
        com.example.analytics.AnalyticsManager.logVoiceError("test_error")

        // 4. Log page open events
        com.example.analytics.AnalyticsManager.logOpenDashboard()
        com.example.analytics.AnalyticsManager.logOpenAnalytics()

        // 5. Log export events
        com.example.analytics.AnalyticsManager.logExportPdf()
        com.example.analytics.AnalyticsManager.logExportCsv()

        // 6. Log advisor and budget
        com.example.analytics.AnalyticsManager.logUseAiAdvisor()
        com.example.analytics.AnalyticsManager.logSetBudget()

        val events = com.example.analytics.AnalyticsManager.events.value
        assertEquals("Should have logged 13 event entries", 13, events.size)

        val eventNames = events.map { it.name }.toSet()
        val requiredEvents = setOf(
            "sign_up", "login", "add_transaction", "voice_start",
            "voice_success", "voice_error", "open_dashboard", "open_analytics",
            "export_pdf", "export_csv", "use_ai_advisor", "set_budget"
        )
        assertTrue("All 12 required event names must be logged", eventNames.containsAll(requiredEvents))

        // Privacy verification: check add_transaction events
        val addEvents = events.filter { it.name == "add_transaction" }
        assertEquals(2, addEvents.size)
        for (evt in addEvents) {
            assertTrue("Must contain 'method' parameter", evt.params.containsKey("method"))
            assertFalse("Must NOT contain 'amount' parameter", evt.params.containsKey("amount"))
            assertFalse("Must NOT contain 'note' parameter", evt.params.containsKey("note"))
            assertFalse("Must NOT contain 'title' parameter", evt.params.containsKey("title"))
        }

        // Privacy verification: check set_budget event
        val budgetEvents = events.filter { it.name == "set_budget" }
        assertEquals(1, budgetEvents.size)
        assertFalse("Budget event must NOT contain monetary amount", budgetEvents.first().params.containsKey("amount"))
    }

    @Test
    fun testRemoteConfigDefaultsAndOverrides() {
        // Test defaults
        assertEquals("voice_first", com.example.config.RemoteConfigManager.DEFAULT_HOME_CTA_VARIANT)
        assertTrue(com.example.config.RemoteConfigManager.DEFAULT_ONBOARDING_ENABLED)
        assertEquals("dashboard", com.example.config.RemoteConfigManager.DEFAULT_ADVISOR_PROMPT_POSITION)

        // Test overrides
        com.example.config.RemoteConfigManager.setOverrideHomeCtaVariant("manual_first")
        assertEquals("manual_first", com.example.config.RemoteConfigManager.homeCtaVariant.value)

        com.example.config.RemoteConfigManager.setOverrideAdvisorPromptPosition("budget_page")
        assertEquals("budget_page", com.example.config.RemoteConfigManager.advisorPromptPosition.value)

        com.example.config.RemoteConfigManager.setOverrideOnboardingEnabled(false)
        assertFalse(com.example.config.RemoteConfigManager.onboardingEnabled.value)

        // Test reset overrides
        com.example.config.RemoteConfigManager.resetAllOverrides()
        assertEquals("voice_first", com.example.config.RemoteConfigManager.homeCtaVariant.value)
        assertTrue(com.example.config.RemoteConfigManager.onboardingEnabled.value)
        assertEquals("dashboard", com.example.config.RemoteConfigManager.advisorPromptPosition.value)
    }
}

