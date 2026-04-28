package com.github.reygnn.lobber

import android.app.Application
import com.github.reygnn.lobber.data.SettingsStore

class LobberApplication : Application() {
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
    }
}
