package com.retrogamestick.console.emulator

import android.content.Context
import android.util.Log
import com.retrogamestick.console.audio.AudioDriver
import com.retrogamestick.console.network.ConsoleNetworkManager
import com.retrogamestick.console.video.VideoDriver
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class EmulatorManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val networkManager: ConsoleNetworkManager,
    private val videoDriver: VideoDriver,
    private val audioDriver: AudioDriver,
    private val onGameLoaded: (SystemAVInfo) -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "EmulatorManager"
    private val bridge = LibretroBridge()
    private var isRunning = false
    private var currentRomPath = ""
    private var currentCorePath = ""
    private var avInfo: SystemAVInfo? = null
    
    // Input state for 2 players
    private val playerInputs = Array(2) { PlayerInput() }
    private var frameCount = 0L
    
    fun initialize(coreName: String = "fbalpha2012"): Boolean {
        return try {
            val coreFile = extractCore(coreName)
            currentCorePath = coreFile.absolutePath
            
            if (!bridge.loadCore(currentCorePath)) {
                onError("Failed to load core: $coreName")
                return false
            }
            
            // Set up callbacks
            setupCallbacks()
            
            // Extract bundled ROMs
            extractBundledRoms()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            onError("Initialization failed: ${e.message}")
            false
        }
    }
    
    private fun extractCore(coreName: String): File {
        val coreFile = File(context.filesDir, "cores/${coreName}_libretro_android.so")
        coreFile.parentFile?.mkdirs()
        
        if (!coreFile.exists()) {
            // Try to extract from assets
            context.assets.open("cores/${coreName}_libretro_android.so").use { input ->
                FileOutputStream(coreFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return coreFile
    }
    
    private fun extractBundledRoms() {
        val romsDir = File(context.filesDir, "roms/neogeo")
        romsDir.mkdirs()
        
        val roms = arrayOf("kof97.zip", "neogeo.zip", "pgm.zip")
        for (rom in roms) {
            val dest = File(romsDir, rom)
            if (!dest.exists()) {
                try {
                    context.assets.open("roms/neogeo/$rom").use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract $rom: ${e.message}")
                }
            }
        }
    }
    
    private fun setupCallbacks() {
        // Video callback - called from native thread
        bridge.setVideoRefreshCallback { data, width, height, pitch ->
            // Convert frame to ByteBuffer and send to video driver
            val buffer = (data as? java.nio.ByteBuffer) ?: java.nio.ByteBuffer.wrap(data as ByteArray)
            videoDriver.renderFrame(buffer, width, height, pitch.toInt())
        }
        
        // Audio callback - called from native thread
        bridge.setAudioSampleBatchCallback { data, frames ->
            // Write audio samples to AudioTrack
            audioDriver.writeSamples(data as ShortArray)
            frames
        }
        
        // Input poll callback
        bridge.setInputPollCallback {
            // Update input state from network
            updateInputFromNetwork()
        }
        
        // Input state callback
        bridge.setInputStateCallback { port, device, index, id ->
            getInputState(port, device, index, id)
        }
    }
    
    fun loadGame(romName: String): Boolean {
        val romsDir = File(context.filesDir, "roms/neogeo")
        val romFile = File(romsDir, romName)
        
        if (!romFile.exists()) {
            onError("ROM not found: $romName")
            return false
        }
        
        currentRomPath = romFile.absolutePath
        
        if (!bridge.loadGame(currentRomPath)) {
            onError("Failed to load game: $romName")
            return false
        }
        
        avInfo = bridge.getSystemAVInfo()
        onGameLoaded(avInfo!!)
        
        startEmulationLoop()
        return true
    }
    
    private fun startEmulationLoop() {
        isRunning = true
        scope.launch(Dispatchers.Default) {
            val frameTimeNs = (1_000_000_000L / avInfo!!.fps).toLong()
            var lastFrameTime = System.nanoTime()
            
            while (isRunning) {
                val startTime = System.nanoTime()
                
                bridge.runFrame()
                
                // Send input frames to network at 60fps
                sendInputFrames()
                
                frameCount++
                
                // Frame pacing
                val elapsed = System.nanoTime() - startTime
                val sleepTime = frameTimeNs - elapsed
                if (sleepTime > 0) {
                    delay(sleepTime / 1_000_000)
                }
                
                // Prevent drift
                val actualElapsed = System.nanoTime() - lastFrameTime
                if (actualElapsed > frameTimeNs * 2) {
                    lastFrameTime = System.nanoTime()
                } else {
                    lastFrameTime += frameTimeNs
                }
            }
        }
    }
    
    private fun sendInputFrames() {
        // Network sending happens in network manager
    }
    
    private fun updateInputFromNetwork() {
        // Called from emulation thread - network manager updates playerInputs
    }
    
    private fun getInputState(port: Int, device: Int, index: Int, id: Int): Short {
        if (port >= 2) return 0
        val input = playerInputs[port]
        
        return when (device) {
            LibretroConstants.RETRO_DEVICE_JOYPAD -> {
                val button = when (id) {
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_B -> input.buttons.getOrElse(0) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_Y -> input.buttons.getOrElse(1) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_SELECT -> input.buttons.getOrElse(2) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_START -> input.buttons.getOrElse(3) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_UP -> input.buttons.getOrElse(4) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_DOWN -> input.buttons.getOrElse(5) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_LEFT -> input.buttons.getOrElse(6) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_RIGHT -> input.buttons.getOrElse(7) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_A -> input.buttons.getOrElse(8) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_X -> input.buttons.getOrElse(9) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_L -> input.buttons.getOrElse(10) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_R -> input.buttons.getOrElse(11) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_L2 -> input.buttons.getOrElse(12) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_R2 -> input.buttons.getOrElse(13) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_L3 -> input.buttons.getOrElse(14) { false }
                    LibretroConstants.RETRO_DEVICE_ID_JOYPAD_R3 -> input.buttons.getOrElse(15) { false }
                    else -> false
                }
                if (button) 1 else 0
            }
            LibretroConstants.RETRO_DEVICE_ANALOG -> {
                when (index) {
                    LibretroConstants.RETRO_DEVICE_INDEX_ANALOG_LEFT -> {
                        when (id) {
                            LibretroConstants.RETRO_DEVICE_ID_ANALOG_X -> input.lx
                            LibretroConstants.RETRO_DEVICE_ID_ANALOG_Y -> input.ly
                            else -> 0
                        }
                    }
                    LibretroConstants.RETRO_DEVICE_INDEX_ANALOG_RIGHT -> {
                        when (id) {
                            LibretroConstants.RETRO_DEVICE_ID_ANALOG_X -> input.rx
                            LibretroConstants.RETRO_DEVICE_ID_ANALOG_Y -> input.ry
                            else -> 0
                        }
                    }
                    else -> 0
                }
            }
            else -> 0
        }
    }
    
    fun setPlayerInput(playerSlot: Int, input: PlayerInput) {
        if (playerSlot in 0..1) {
            playerInputs[playerSlot] = input
        }
    }
    
    fun stop() {
        isRunning = false
        bridge.unloadGame()
    }
    
    fun destroy() {
        stop()
        bridge.destroy()
        audioDriver.stop()
        videoDriver.destroy()
    }
    
    fun saveState(slot: Int): Boolean = bridge.saveState(slot)
    fun loadState(slot: Int): Boolean = bridge.loadState(slot)
}

data class PlayerInput(
    val buttons: Map<Int, Boolean> = emptyMap(),
    val lx: Short = 0,
    val ly: Short = 0,
    val rx: Short = 0,
    val ry: Short = 0
)

object LibretroConstants {
    const val RETRO_DEVICE_JOYPAD = 1
    const val RETRO_DEVICE_ANALOG = 2
    const val RETRO_DEVICE_INDEX_ANALOG_LEFT = 0
    const val RETRO_DEVICE_INDEX_ANALOG_RIGHT = 1
    
    const val RETRO_DEVICE_ID_JOYPAD_B = 0
    const val RETRO_DEVICE_ID_JOYPAD_Y = 1
    const val RETRO_DEVICE_ID_JOYPAD_SELECT = 2
    const val RETRO_DEVICE_ID_JOYPAD_START = 3
    const val RETRO_DEVICE_ID_JOYPAD_UP = 4
    const val RETRO_DEVICE_ID_JOYPAD_DOWN = 5
    const val RETRO_DEVICE_ID_JOYPAD_LEFT = 6
    const val RETRO_DEVICE_ID_JOYPAD_RIGHT = 7
    const val RETRO_DEVICE_ID_JOYPAD_A = 8
    const val RETRO_DEVICE_ID_JOYPAD_X = 9
    const val RETRO_DEVICE_ID_JOYPAD_L = 10
    const val RETRO_DEVICE_ID_JOYPAD_R = 11
    const val RETRO_DEVICE_ID_JOYPAD_L2 = 12
    const val RETRO_DEVICE_ID_JOYPAD_R2 = 13
    const val RETRO_DEVICE_ID_JOYPAD_L3 = 14
    const val RETRO_DEVICE_ID_JOYPAD_R3 = 15
    
    const val RETRO_DEVICE_ID_ANALOG_X = 0
    const val RETRO_DEVICE_ID_ANALOG_Y = 1
}