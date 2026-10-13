package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

enum class SpeechLanguage(val code: String, val displayName: String, val flag: String) {
    URDU("ur-PK", "Urdu (اردو)", "🇵🇰"),
    ENGLISH("en-US", "English", "🇬🇧")
}

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    data class Processing(val text: String) : SpeechState()
    data class Error(val message: String, val canTypeInstead: Boolean = true) : SpeechState()
}

class SpeechManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun isSpeechAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Creates standard speech recognition Intent that can be used with ActivityResultContracts
     */
    fun createSpeechIntent(language: SpeechLanguage = SpeechLanguage.URDU): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.code)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(SpeechLanguage.URDU.code, SpeechLanguage.ENGLISH.code))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (language == SpeechLanguage.URDU) "Hisaab bolain: 'aaj 500 rupay chai pe kharch kiye'..." else "Speak transaction: 'spent 500 on groceries'..."
            )
        }
    }

    /**
     * Start speech recognition using direct SpeechRecognizer listener
     */
    fun startListening(
        language: SpeechLanguage = SpeechLanguage.URDU,
        onRmsChanged: (Float) -> Unit = {},
        onPartialResults: (String) -> Unit = {},
        onResults: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSpeechAvailable()) {
            onError("Speech recognition is not supported on this device. You can type transaction details instead.")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechManager", "Ready for speech in ${language.code}")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechManager", "Speech started")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d("SpeechManager", "End of speech")
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone."
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error. Please retry."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection required for speech recognition."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly or type below."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Retrying..."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected in time."
                            else -> "Speech recognition error ($error)."
                        }
                        Log.e("SpeechManager", "Speech error code $error: $message")
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            onResults(text)
                        } else {
                            onError("Could not understand voice. Please try again or type.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { onPartialResults(it) }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = createSpeechIntent(language)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("SpeechManager", "Failed to start listening", e)
            onError(e.message ?: "Could not start speech recognition.")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechManager", "Error stopping speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }
}
