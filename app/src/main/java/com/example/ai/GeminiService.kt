package com.example.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExtractedTransaction(
    val type: String, // "EXPENSE" or "INCOME"
    val amount: Double,
    val category: String,
    val date: String,
    val title: String,
    val note: String,
    val rawText: String
)

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Extracts structured transaction details from spoken or typed Urdu/English text using Gemini 3.5 Flash.
     * Falls back to a resilient local parser if API key is not yet set or network fails.
     */
    suspend fun extractTransactionFromText(
        inputText: String,
        availableCategories: List<String>
    ): ExtractedTransaction = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        // If no API key configured or placeholder key, use resilient local Urdu/English parser
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is placeholder or empty. Using smart local parser fallback.")
            return@withContext fallbackLocalParser(inputText, availableCategories, todayStr)
        }

        try {
            val categoriesPrompt = availableCategories.joinToString(", ")
            val systemPrompt = """
                You are an expert personal finance AI assistant for the app 'Hisaab'.
                Extract financial transaction details from the user's spoken or written Urdu, Roman Urdu, or English text.
                Today's date is: $todayStr.
                
                Standard categories available: [$categoriesPrompt].
                
                Rules:
                1. Detect whether this is an 'EXPENSE' or 'INCOME'.
                   - Examples for INCOME: "salary mili", "munafa aya", "pese aye", "got paid", "received 5000", "pocket money mili".
                   - Examples for EXPENSE: "kharch kiye", "khareeda", "bill bhara", "chai pi", "paid 200", "spent 500 on groceries".
                2. Extract the numeric amount (convert words like 'hazaar' to 1000, 'lakh' to 100000 if needed).
                3. Map the expense/income to the most relevant category from the available list: [$categoriesPrompt].
                   - chai, khana, biryani, roti -> Food
                   - petrol, uber, rickshaw, careem, bus -> Transport
                   - bijli, gas, internet, bills -> Bills
                   - kapray, shopping -> Shopping
                   - dawa, doctor, hospital -> Health
                   - fee, books, school -> Education
                   - rent, kiraya -> Rent
                   - tankhwa, salary -> Salary
                   - dukan, business -> Business
                   - If uncertain, use 'Other'.
                4. Extract or infer the date in 'YYYY-MM-DD' format. If "aaj" (today), use '$todayStr'. If "kal" (yesterday), compute yesterday's date.
                5. Create a concise descriptive title (e.g. 'Chai and Food', 'Monthly Salary').
                6. Keep the user's exact utterance as note.
                
                Respond ONLY with a valid JSON object matching this schema:
                {
                  "type": "EXPENSE" or "INCOME",
                  "amount": 0.0,
                  "category": "Food",
                  "date": "YYYY-MM-DD",
                  "title": "Short title",
                  "note": "Full context or note"
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Input: \"$inputText\"")
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                val systemInstructionObj = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                }
                put("systemInstruction", systemInstructionObj)

                val generationConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                Log.e(TAG, "Gemini API error: $errBody")
                return@withContext fallbackLocalParser(inputText, availableCategories, todayStr)
            }

            val responseBodyString = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBodyString)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val textContent = parts?.optJSONObject(0)?.optString("text") ?: ""

            if (textContent.isBlank()) {
                return@withContext fallbackLocalParser(inputText, availableCategories, todayStr)
            }

            // Parse returned JSON from Gemini
            val parsedResult = JSONObject(textContent)
            val extractedType = parsedResult.optString("type", "EXPENSE").uppercase()
            val validExtractedType = if (extractedType == "INCOME") "INCOME" else "EXPENSE"
            val amount = parsedResult.optDouble("amount", 0.0)
            val category = parsedResult.optString("category", "Other")
            val date = parsedResult.optString("date", todayStr)
            val title = parsedResult.optString("title", inputText.take(30))
            val note = parsedResult.optString("note", inputText)

            ExtractedTransaction(
                type = validExtractedType,
                amount = if (amount > 0) amount else 0.0,
                category = if (availableCategories.contains(category)) category else "Other",
                date = if (date.isNotBlank()) date else todayStr,
                title = title.ifBlank { inputText.take(30) },
                note = note.ifBlank { inputText },
                rawText = inputText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Gemini API, falling back to local extractor", e)
            fallbackLocalParser(inputText, availableCategories, todayStr)
        }
    }

    /**
     * Resilient rule-based local parser for Roman Urdu, Urdu and English.
     * Guaranteed to work even when offline or if Gemini API is unreachable.
     */
    fun fallbackLocalParser(
        inputText: String,
        availableCategories: List<String>,
        todayStr: String
    ): ExtractedTransaction {
        val lower = inputText.lowercase(Locale.getDefault())

        // 1. Detect Type
        val incomeKeywords = listOf(
            "salary", "tankhwa", "mili", "received", "income", "munafa",
            "pese aye", "kamai", "profit", "earned", "credit", "wasool"
        )
        val isIncome = incomeKeywords.any { lower.contains(it) }
        val type = if (isIncome) "INCOME" else "EXPENSE"

        // 2. Extract numeric amount
        // Look for numbers like 500, 80000, 50,000, 1.5k, etc.
        val numberRegex = Regex("""(\d+[\d,]*\.?\d*)""")
        val match = numberRegex.find(lower.replace(",", ""))
        var amount = match?.value?.toDoubleOrNull() ?: 0.0

        // Check for "k" or "hazaar" (e.g. "80 hazar" or "80k")
        if (lower.contains("hazar") || lower.contains("hazaar") || lower.contains("k")) {
            val kMatch = Regex("""(\d+)\s*(hazar|hazaar|k)""").find(lower)
            if (kMatch != null) {
                val num = kMatch.groupValues[1].toDoubleOrNull()
                if (num != null) {
                    amount = num * 1000.0
                }
            }
        }
        if (lower.contains("lakh") || lower.contains("lac")) {
            val lakhMatch = Regex("""(\d+)\s*(lakh|lac)""").find(lower)
            if (lakhMatch != null) {
                val num = lakhMatch.groupValues[1].toDoubleOrNull()
                if (num != null) {
                    amount = num * 100000.0
                }
            }
        }

        // 3. Detect Category
        val category = when {
            listOf("chai", "khana", "khaney", "lunch", "dinner", "breakfast", "roti", "food", "tea", "coffee", "restaurant", "burger", "pizza", "biryani", "fruits", "subzi").any { lower.contains(it) } -> "Food"
            listOf("petrol", "diesel", "transport", "uber", "careem", "rickshaw", "taxi", "bus", "fuel", "bike", "car").any { lower.contains(it) } -> "Transport"
            listOf("bijli", "gas", "electricity", "bill", "bills", "internet", "wifi", "water", "pani").any { lower.contains(it) } -> "Bills"
            listOf("shopping", "kapray", "shoes", "clothes", "mall", "daraz").any { lower.contains(it) } -> "Shopping"
            listOf("dawa", "medicine", "doctor", "hospital", "clinic", "health", "sehat").any { lower.contains(it) } -> "Health"
            listOf("fee", "school", "college", "uni", "university", "books", "education", "course").any { lower.contains(it) } -> "Education"
            listOf("kiraya", "rent", "makaan").any { lower.contains(it) } -> "Rent"
            listOf("salary", "tankhwa", "wages").any { lower.contains(it) } -> "Salary"
            listOf("business", "dukan", "karobar", "shop", "investment").any { lower.contains(it) } -> "Business"
            else -> if (isIncome) "Salary" else "Other"
        }

        val title = when {
            category == "Food" -> "Food & Drinks"
            category == "Transport" -> "Travel / Transport"
            category == "Bills" -> "Utility Bills"
            category == "Salary" -> "Salary / Earnings"
            else -> inputText.take(30).replaceFirstChar { it.uppercase() }
        }

        return ExtractedTransaction(
            type = type,
            amount = amount,
            category = if (availableCategories.contains(category)) category else "Other",
            date = todayStr,
            title = title,
            note = inputText,
            rawText = inputText
        )
    }

    /**
     * Generates concise, actionable financial advice and budgeting insights.
     */
    suspend fun generateFinancialAdvice(
        totalIncome: Double,
        totalExpense: Double,
        monthlyBudget: Double,
        currency: String,
        topCategories: List<Pair<String, Double>>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        val savings = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) (savings / totalIncome * 100).toInt() else 0
        val topCategoryStr = topCategories.take(3).joinToString(", ") { "${it.first}: $currency ${it.second.toInt()}" }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = """
                    You are a friendly personal financial advisor for Hisaab app.
                    Financial Snapshot:
                    - Total Income: $currency $totalIncome
                    - Total Expense: $currency $totalExpense
                    - Net Savings: $currency $savings ($savingsRate% savings rate)
                    - Monthly Budget Limit: $currency $monthlyBudget
                    - Top Expense Categories: $topCategoryStr

                    Provide 3 brief, encouraging, bullet-point financial recommendations in plain English with optional Urdu terminology (like Bachat, Karobar, Kharch). Keep under 120 words total.
                """.trimIndent()

                val requestBodyJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                val partObj = JSONObject().apply {
                                    put("text", prompt)
                                }
                                put(partObj)
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val request = Request.Builder()
                    .url("$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey")
                    .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val root = JSONObject(responseBody)
                    val text = root.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    if (text.isNotBlank()) return@withContext text.trim()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini advisor API call failed, using smart local rules: ${e.message}")
            }
        }

        // Resilient fallback rule-based advisor
        val adviceBuilder = StringBuilder()
        if (monthlyBudget > 0) {
            val budgetPercent = ((totalExpense / monthlyBudget) * 100).toInt()
            if (budgetPercent > 100) {
                adviceBuilder.append("⚠️ Budget Alert: You have utilized $budgetPercent% of your monthly budget ($currency ${totalExpense.toInt()} of $currency ${monthlyBudget.toInt()}). Consider curtailing non-essential discretionary spends for the rest of the month.\n\n")
            } else {
                adviceBuilder.append("✅ Budget On Track: You have used $budgetPercent% of your $currency ${monthlyBudget.toInt()} budget. You still have $currency ${(monthlyBudget - totalExpense).toInt()} available.\n\n")
            }
        }

        if (savingsRate >= 20) {
            adviceBuilder.append("🌟 Strong Bachat (Savings): Your current savings rate is $savingsRate%, well above healthy financial benchmarks! Consider putting aside these reserves into a dedicated emergency fund or halal yield investment.")
        } else if (savingsRate > 0) {
            adviceBuilder.append("💡 Steady Progress: You are saving $savingsRate% of your earnings. Aiming for the 50/30/20 rule (50% needs, 30% wants, 20% savings) can help build a 3-month safety buffer.")
        } else {
            adviceBuilder.append("⚠️ Deficit Warning: Current expenses exceed income. Review your highest spending categories ($topCategoryStr) to eliminate impulsive outflows and balance your cash flow.")
        }

        adviceBuilder.toString()
    }
}
