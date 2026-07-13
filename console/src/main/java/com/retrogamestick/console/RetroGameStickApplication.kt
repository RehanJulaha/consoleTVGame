package com.retrogamestick.console

import android.app.Application
import timber.log.Timber

class RetroGameStickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}