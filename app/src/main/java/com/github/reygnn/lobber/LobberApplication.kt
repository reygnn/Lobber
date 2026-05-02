package com.github.reygnn.lobber

import android.app.Application
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ssh.SshSecurity

class LobberApplication : Application() {
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        SshSecurity.installBouncyCastle()
        settingsStore = SettingsStore(this)
    }
}
