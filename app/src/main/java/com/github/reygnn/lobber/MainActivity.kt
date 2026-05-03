package com.github.reygnn.lobber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.reygnn.lobber.data.ConfigState
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ui.InstallViewModel
import com.github.reygnn.lobber.ui.InstallerScreen
import com.github.reygnn.lobber.ui.LobberTheme
import com.github.reygnn.lobber.ui.OnboardingScreen
import com.github.reygnn.lobber.ui.OnboardingViewModel
import com.github.reygnn.lobber.ui.SettingsScreen
import com.github.reygnn.lobber.ui.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as LobberApplication).settingsStore
        setContent {
            LobberTheme {
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

    val configState by settingsVm.configState.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    // AAB-Liste leeren, sobald die App in den Hintergrund geht. Hier ist
    // LocalLifecycleOwner noch die Activity (NavHost erst weiter unten), also
    // feuert ON_STOP nur bei echtem Backgrounding und nicht beim In-App-
    // Wechsel zwischen Installer- und Settings-Screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, installVm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) installVm.clearAabs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(navController = nav, startDestination = "loading") {
        composable("loading") {
            LaunchedEffect(configState) {
                when (configState) {
                    ConfigState.Configured   -> nav.navigate("installer")  { popUpTo("loading") { inclusive = true } }
                    ConfigState.Unconfigured -> nav.navigate("onboarding") { popUpTo("loading") { inclusive = true } }
                    ConfigState.Loading      -> Unit // still resolving
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

