package com.retrogamestick.console.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import retrofit.gamestick.network.InputFrame

class ConsoleNetworkManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onInputFrame: (InputFrame) -> Unit
) {
    private val TAG = "ConsoleNetworkManager"
    
    fun startDiscovery() {
        Log.d(TAG, "Starting network discovery (stub)")
    }
    
    fun disconnectAll() {
        Log.d(TAG, "Disconnecting all (stub)")
    }
}