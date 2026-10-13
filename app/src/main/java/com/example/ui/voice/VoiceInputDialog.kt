package com.example.ui.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ai.ExtractedTransaction
import com.example.ai.GeminiService
import com.example.analytics.AnalyticsManager
import com.example.speech.SpeechLanguage
import com.example.speech.SpeechManager
import com.example.ui.home.DefaultCategories
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceInputDialog(
    customCategories: List<String>,
    currency: String,
    onDismiss: () -> Unit,
    onSaveTransaction: (title: String, amount: Double, type: String, category: String, date: String, note: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val speechManager = remember { SpeechManager(context) }
    val isMicAvailable = remember { speechManager.isSpeechAvailable() }

    var selectedLanguage by remember { mutableStateOf(SpeechLanguage.URDU) }
    var isListening by remember { mutableStateOf(false) }
    var isProcessingAI by remember { mutableStateOf(false) }
    var capturedText by remember { mutableStateOf("") }
    var manualInputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var extractedResult by remember { mutableStateOf<ExtractedTransaction?>(null) }

    // Confirmation Card editable fields
    var editType by remember { mutableStateOf("EXPENSE") }
    var editAmountText by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("Food") }
    var editDateText by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }

    val allCategories = remember(customCategories) {
        (DefaultCategories + customCategories).distinct()
    }

    fun processWithGemini(text: String) {
        if (text.isBlank()) return
        capturedText = text
        isListening = false
        isProcessingAI = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val extracted = GeminiService.extractTransactionFromText(
                    inputText = text,
                    availableCategories = allCategories
                )
                extractedResult = extracted
                editType = extracted.type
                editAmountText = if (extracted.amount > 0) {
                    if (extracted.amount % 1.0 == 0.0) extracted.amount.toLong().toString() else extracted.amount.toString()
                } else ""
                editCategory = extracted.category
                editDateText = extracted.date
                editTitle = extracted.title
                editNote = extracted.note
                AnalyticsManager.logVoiceSuccess()
            } catch (e: Exception) {
                errorMessage = "AI extraction error: ${e.message}"
                AnalyticsManager.logVoiceError(e.message ?: "extraction_failed")
            } finally {
                isProcessingAI = false
            }
        }
    }

    // Android Speech Recognition System Intent fallback
    val speechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                processWithGemini(spoken)
            } else {
                errorMessage = "No speech detected. Please speak clearly or type below."
                AnalyticsManager.logVoiceError("no_speech_detected")
            }
        } else {
            AnalyticsManager.logVoiceError("speech_intent_cancelled")
        }
    }

    fun startListeningWithManager() {
        errorMessage = null
        if (!isMicAvailable) {
            errorMessage = "Microphone speech service is not available on this device. You can type Roman Urdu or English below!"
            AnalyticsManager.logVoiceError("speech_unavailable")
            return
        }

        AnalyticsManager.logVoiceStart()
        isListening = true
        speechManager.startListening(
            language = selectedLanguage,
            onPartialResults = { partial ->
                capturedText = partial
            },
            onResults = { fullText ->
                processWithGemini(fullText)
            },
            onError = { err ->
                isListening = false
                AnalyticsManager.logVoiceError(err)
                // If direct SpeechRecognizer fails, try launch system speech intent
                try {
                    val intent = speechManager.createSpeechIntent(selectedLanguage)
                    speechIntentLauncher.launch(intent)
                } catch (e: Exception) {
                    errorMessage = err
                }
            }
        )
    }

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListeningWithManager()
        } else {
            errorMessage = "Microphone permission is required to listen. You can type below instead."
            AnalyticsManager.logVoiceError("permission_denied")
        }
    }

    fun checkAndStartListening() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startListeningWithManager()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
        }
    }

    Dialog(
        onDismissRequest = {
            speechManager.stopListening()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with AI Voice Badge and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Voice Entry (Gemini AI)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Speak in Urdu or English",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            speechManager.stopListening()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("voice_dialog_close")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Selector Pills (Urdu ur-PK vs English en-US)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SpeechLanguage.entries.forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        Surface(
                            onClick = {
                                if (selectedLanguage != lang) {
                                    selectedLanguage = lang
                                    if (isListening) {
                                        speechManager.stopListening()
                                        startListeningWithManager()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.testTag("lang_toggle_${lang.name.lowercase()}")
                        ) {
                            Text(
                                text = "${lang.flag} ${lang.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // State 1: Extracted Confirmation Card
                if (extractedResult != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI Extracted Details",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Edit before saving",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quoted Voice Text
                            if (capturedText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "\"$capturedText\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Type Toggle (Expense vs Income)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterChip(
                                    selected = editType == "EXPENSE",
                                    onClick = { editType = "EXPENSE" },
                                    label = { Text("Expense", fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = ExpenseRed)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ExpenseRed.copy(alpha = 0.18f),
                                        selectedLabelColor = ExpenseRed
                                    ),
                                    modifier = Modifier.weight(1f).testTag("confirm_type_expense")
                                )
                                FilterChip(
                                    selected = editType == "INCOME",
                                    onClick = { editType = "INCOME" },
                                    label = { Text("Income", fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = IncomeGreen)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IncomeGreen.copy(alpha = 0.18f),
                                        selectedLabelColor = IncomeGreen
                                    ),
                                    modifier = Modifier.weight(1f).testTag("confirm_type_income")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Amount Input
                            OutlinedTextField(
                                value = editAmountText,
                                onValueChange = {
                                    editAmountText = it
                                    amountError = false
                                },
                                label = { Text("Amount ($currency)") },
                                isError = amountError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_amount_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            if (amountError) {
                                Text(
                                    text = "Please enter a valid amount greater than 0",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Category Chips
                            Text(
                                text = "Category",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                allCategories.take(8).forEach { cat ->
                                    val isSelected = editCategory == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { editCategory = cat },
                                        label = { Text(cat, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("confirm_cat_$cat")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Date Field
                            OutlinedTextField(
                                value = editDateText,
                                onValueChange = { editDateText = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Note Field
                            OutlinedTextField(
                                value = editNote,
                                onValueChange = { editNote = it },
                                label = { Text("Note / Description") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons: Save & Re-record
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        extractedResult = null
                                        capturedText = ""
                                        checkAndStartListening()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Speak Again")
                                }

                                Button(
                                    onClick = {
                                        val amt = editAmountText.toDoubleOrNull()
                                        if (amt == null || amt <= 0.0) {
                                            amountError = true
                                            return@Button
                                        }
                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        val validDate = editDateText.ifBlank { today }
                                        val validTitle = editTitle.ifBlank { "$editCategory $editType" }
                                        onSaveTransaction(
                                            validTitle,
                                            amt,
                                            editType,
                                            editCategory,
                                            validDate,
                                            editNote
                                        )
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (editType == "INCOME") IncomeGreen else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("confirm_save_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Entry", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (isProcessingAI) {
                    // Processing with Gemini AI State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting with Gemini AI...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"$capturedText\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    // Large Animated Microphone Button Area
                    val infiniteTransition = rememberInfiniteTransition(label = "MicPulseTransition")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isListening) 1.25f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseScale"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(150.dp)
                            .padding(12.dp)
                    ) {
                        // Pulsing outer ripple when listening
                        if (isListening) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            )
                        }

                        // Large Mic Button (84dp)
                        Surface(
                            onClick = {
                                if (isListening) {
                                    speechManager.stopListening()
                                    isListening = false
                                    if (capturedText.isNotBlank()) {
                                        processWithGemini(capturedText)
                                    }
                                } else {
                                    checkAndStartListening()
                                }
                            },
                            shape = CircleShape,
                            color = if (isListening) ExpenseRed else MaterialTheme.colorScheme.primary,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(88.dp)
                                .testTag("large_microphone_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                    contentDescription = "Microphone",
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isListening) "Sun raha hoon... (Listening... Tap to stop)" else "Tap to Speak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isListening) ExpenseRed else MaterialTheme.colorScheme.onSurface
                    )

                    if (capturedText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = capturedText,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Error Message Banner (Graceful feedback if mic is unsupported or fails)
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Text Input Fallback Area with Example Chips
                    Text(
                        text = "Or type Roman Urdu / English text:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualInputText,
                            onValueChange = { manualInputText = it },
                            placeholder = { Text("e.g. aaj 500 rupay chai pe kharch kiye", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_manual_input_field"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (manualInputText.isNotBlank()) {
                                    processWithGemini(manualInputText)
                                }
                            },
                            enabled = manualInputText.isNotBlank(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (manualInputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("voice_manual_submit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send to AI",
                                tint = if (manualInputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Examples chips from user prompt
                    Text(
                        text = "Example inputs (tap to test):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sampleInputs = listOf(
                            "aaj 500 rupay chai aur khaney pe kharch kiye",
                            "salary 80000 mili",
                            "petrol 2500 ka dalwaya",
                            "bijli ka bill 7500 ada kiya"
                        )
                        sampleInputs.forEach { sample ->
                            Surface(
                                onClick = {
                                    manualInputText = sample
                                    processWithGemini(sample)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.testTag("sample_chip_${sample.take(10).replace(" ", "_")}")
                            ) {
                                Text(
                                    text = sample,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
