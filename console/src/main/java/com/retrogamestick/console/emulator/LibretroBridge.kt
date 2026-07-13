package com.retrogamestick.console.emulator

import android.content.Context
import android.util.Log

class LibretroBridge {
    companion object {
        private const val TAG = "LibretroBridge"
        private var isLoaded = false
        
        @JvmStatic
        fun loadLibrary() {
            if (!isLoaded) {
                try {
                    System.loadLibrary("retro_frontend")
                    isLoaded = true
                    Log.i(TAG, "libretro_frontend loaded successfully")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load libretro_frontend", e)
                    throw e
                }
            }
        }
    }
    
    private var nativePtr: Long = 0
    
    init {
        LibretroBridge.loadLibrary()
        nativePtr = nativeInit()
        if (nativePtr == 0L) {
            throw RuntimeException("Failed to initialize libretro frontend")
        }
    }
    
    fun loadCore(corePath: String): Boolean {
        return nativeLoadCore(nativePtr, corePath)
    }
    
    fun unloadCore() {
        nativeUnloadCore(nativePtr)
    }
    
    fun loadGame(romPath: String): Boolean {
        return nativeLoadGame(nativePtr, romPath)
    }
    
    fun unloadGame() {
        nativeUnloadGame(nativePtr)
    }
    
    fun runFrame() {
        nativeRunFrame(nativePtr)
    }
    
    fun setInputState(port: Int, device: Int, index: Int, id: Int, value: Int) {
        nativeSetInputState(nativePtr, port, device, index, id, value)
    }
    
    fun getSystemAVInfo(): SystemAVInfo {
        val arr = nativeGetSystemAVInfo(nativePtr)
        return SystemAVInfo(
            width = arr[0],
            height = arr[1],
            maxWidth = arr[2],
            maxHeight = arr[3],
            fps = arr[4] / 1000.0f,
            sampleRate = arr[5]
        )
    }
    
    fun saveState(slot: Int): Boolean {
        return nativeSaveState(nativePtr, slot)
    }
    
    fun loadState(slot: Int): Boolean {
        return nativeLoadState(nativePtr, slot)
    }
    
    fun setCoreOption(key: String, value: String) {
        nativeSetCoreOption(nativePtr, key, value)
    }
    
    fun destroy() {
        if (nativePtr != 0L) {
            nativeDestroy(nativePtr)
            nativePtr = 0
        }
    }
    
    data class SystemAVInfo(
        val width: Int,
        val height: Int,
        val maxWidth: Int,
        val maxHeight: Int,
        val fps: Float,
        val sampleRate: Int
    )
    
    external fun nativeInit(): Long
    external fun nativeDestroy(ptr: Long)
    external fun nativeLoadCore(ptr: Long, corePath: String): Boolean
    external fun nativeUnloadCore(ptr: Long)
    external fun nativeLoadGame(ptr: Long, romPath: String): Boolean
    external fun nativeUnloadGame(ptr: Long)
    external fun nativeRunFrame(ptr: Long)
    external fun nativeSetInputState(ptr: Long, port: Int, device: Int, index: Int, id: Int, value: Int)
    external fun nativeGetSystemAVInfo(ptr: Long): IntArray
    external fun nativeSaveState(ptr: Long, slot: Int): Boolean
    external fun nativeLoadState(ptr: Long, slot: Int): Boolean
    external fun nativeSetCoreOption(ptr: Long, key: String, value: String)
}