package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.example.analytics.AnalyticsManager
import com.example.auth.AuthManager
import com.example.config.RemoteConfigManager
import com.example.data.repository.TransactionRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.onboarding.OnboardingDialog
import com.example.ui.onboarding.OnboardingPreferences
import com.example.ui.theme.HisaabTheme
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }

            HisaabTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HisaabApp(
                        themeMode = themeMode,
                        onToggleTheme = {
                            themeMode = if (themeMode == ThemeMode.DARK) {
                                ThemeMode.LIGHT
                            } else {
                                ThemeMode.DARK
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HisaabApp(
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isCheckingAuth by remember { mutableStateOf(currentUser == null) }

    // Initialize Analytics and Remote Config once on app start
    LaunchedEffect(Unit) {
        AnalyticsManager.init(context)
        RemoteConfigManager.init(context, scope)
    }

    // Synchronize authentication state
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Attempt silent auto-sign in on startup if not already authenticated
    LaunchedEffect(Unit) {
        if (currentUser == null) {
            AuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    isCheckingAuth = false
                },
                onUnauthenticated = {
                    isCheckingAuth = false
                },
                scope = scope
            )
        } else {
            isCheckingAuth = false
        }
    }

    Crossfade(
        targetState = currentUser,
        label = "AuthGateTransition"
    ) { user ->
        if (isCheckingAuth && user == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (user == null) {
            AuthScreen(
                currentThemeMode = themeMode,
                onToggleTheme = onToggleTheme,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        } else {
            val repository = remember(user.uid) {
                TransactionRepository(context)
            }
            val homeViewModel: HomeViewModel = viewModel(
                key = "HomeViewModel_${user.uid}",
                factory = HomeViewModel.Factory(repository)
            )

            val onboardingEnabled by RemoteConfigManager.onboardingEnabled.collectAsState()
            var showOnboardingDialog by remember(user.uid) {
                mutableStateOf(!OnboardingPreferences.hasSeenOnboarding(context))
            }

            HomeScreen(
                viewModel = homeViewModel,
                currentThemeMode = themeMode,
                onToggleTheme = onToggleTheme,
                onSignOutComplete = {
                    currentUser = null
                }
            )

            // Show 3-step onboarding introduction if enabled by Remote Config
            if (onboardingEnabled && showOnboardingDialog) {
                OnboardingDialog(
                    onDismiss = {
                        showOnboardingDialog = false
                    }
                )
            }
        }
    }
}
