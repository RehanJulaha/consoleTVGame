package com.retrogamestick.console

import android.app.Application
import android.util.Log

class RetroGameStickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("RetroGameStick", "Application created")
    }
}