package com.jyotirmoyb.loan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jyotirmoyb.loan.features.onboarding.OnboardingScreen
import com.jyotirmoyb.loan.features.profile.ProfileViewModel
import com.jyotirmoyb.loan.navigation.LoanJBMainShell
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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

    LoanJBTheme(themeMode = prefs.themeMode) {
        when {
            !profileState.isPreferencesLoaded -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
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
