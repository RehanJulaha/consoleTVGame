package com.retrogamestick.controller

import android.app.Application
import timber.log.Timber

class RetroGameStickControllerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}