package com.github.reygnn.lobber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ui.InstallViewModel
import com.github.reygnn.lobber.ui.InstallerScreen
import com.github.reygnn.lobber.ui.OnboardingScreen
import com.github.reygnn.lobber.ui.OnboardingViewModel
import com.github.reygnn.lobber.ui.SettingsScreen
import com.github.reygnn.lobber.ui.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as LobberApplication).settingsStore
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LobberApp(store)
                }
            }
        }
    }
}

@Composable
private fun LobberApp(store: SettingsStore) {
    val factory = remember(store) { LobberViewModelFactory(store) }
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val installVm: InstallViewModel = viewModel(factory = factory)
    val onboardingVm: OnboardingViewModel = viewModel(factory = factory)

    val configured by settingsVm.isConfigured.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "loading") {
        composable("loading") {
            LaunchedEffect(configured) {
                when (configured) {
                    true  -> nav.navigate("installer")  { popUpTo("loading") { inclusive = true } }
                    false -> nav.navigate("onboarding") { popUpTo("loading") { inclusive = true } }
                    null  -> Unit // still resolving
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable("onboarding") {
            OnboardingScreen(
                viewModel = onboardingVm,
                onDone = {
                    nav.navigate("installer") { popUpTo("onboarding") { inclusive = true } }
                },
                onManual = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsVm,
                onSaved = {
                    nav.navigate("installer") { popUpTo("settings") { inclusive = true } }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("installer") {
            InstallerScreen(
                viewModel = installVm,
                onOpenSettings = { nav.navigate("settings") },
            )
        }
    }
}

private class LobberViewModelFactory(
    private val store: SettingsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        SettingsViewModel::class.java   -> SettingsViewModel(store) as T
        InstallViewModel::class.java    -> InstallViewModel(store) as T
        OnboardingViewModel::class.java -> OnboardingViewModel(store) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}
