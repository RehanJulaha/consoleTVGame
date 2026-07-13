package com.retrogamestick.controller

import android.app.Application
import android.util.Log

class RetroGameStickControllerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("RetroGameStickController", "Application created")
    }
}