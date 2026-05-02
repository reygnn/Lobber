package com.github.reygnn.lobber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ui.InstallViewModel
import com.github.reygnn.lobber.ui.OnboardingViewModel
import com.github.reygnn.lobber.ui.SettingsViewModel

class LobberViewModelFactory(
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
