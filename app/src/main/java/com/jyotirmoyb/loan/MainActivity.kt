package com.jyotirmoyb.loan

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jyotirmoyb.loan.features.onboarding.OnboardingScreen
import com.jyotirmoyb.loan.features.profile.ProfileViewModel
import com.jyotirmoyb.loan.features.security.AppLockScreen
import com.jyotirmoyb.loan.navigation.LoanJBMainShell
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoanJBApp()
        }
    }
}

@Composable
fun LoanJBApp(
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val prefs = profileState.preferences
    val activity = LocalContext.current as? AppCompatActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var sessionUnlocked by remember { mutableStateOf(false) }

    // Apply FLAG_SECURE reactively when screenshot protection is toggled
    LaunchedEffect(prefs.flagSecureEnabled, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (prefs.flagSecureEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Re-lock session when app goes into the background and App Lock is enabled
    DisposableEffect(lifecycleOwner, prefs.appLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && prefs.appLockEnabled) {
                sessionUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // If user just enabled App Lock from inside the app, keep current session unlocked until next backgrounding
    var prevAppLockEnabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(profileState.isPreferencesLoaded, prefs.appLockEnabled) {
        if (!profileState.isPreferencesLoaded) return@LaunchedEffect
        val previous = prevAppLockEnabled
        if (previous == null) {
            // Initial app launch: unlock automatically only when App Lock is off
            sessionUnlocked = !prefs.appLockEnabled
        } else if (!previous && prefs.appLockEnabled) {
            // User just toggled App Lock ON inside Privacy screen; don't abruptly kick them out mid-session
            sessionUnlocked = true
        } else if (!prefs.appLockEnabled) {
            sessionUnlocked = true
        }
        prevAppLockEnabled = prefs.appLockEnabled
    }

    LoanJBTheme(themeMode = prefs.themeMode) {
        when {
            !profileState.isPreferencesLoaded -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
            prefs.appLockEnabled && !sessionUnlocked -> {
                AppLockScreen(
                    hasBiometricAvailable = profileState.hasBiometricAvailable,
                    hasPinConfigured = profileState.hasPinConfigured,
                    onVerifyPin = { profileViewModel.verifyAppPin(it) },
                    onUnlocked = { sessionUnlocked = true }
                )
            }
            !prefs.onboardingCompleted -> {
                OnboardingScreen(
                    onFinished = { profileViewModel.setOnboardingCompleted(true) }
                )
            }
            else -> {
                LoanJBMainShell(profileViewModel = profileViewModel)
            }
        }
    }
}
