package com.retrogamestick.console.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class AudioDriver(
    private val context: Context,
    private val sampleRate: Int = 48000,
    private val channelCount: Int = 2
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val bufferSize: Int
    
    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        bufferSize = max(minBufferSize * 4, 8192)
    }
    
    fun start() {
        if (isPlaying) return
        
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        isPlaying = true
    }
    
    fun writeSamples(samples: ShortArray, offset: Int, count: Int): Int {
        return audioTrack?.write(samples, offset, count, AudioTrack.WRITE_BLOCKING) ?: 0
    }
    
    fun writeSamples(samples: ShortArray): Int {
        return writeSamples(samples, 0, samples.size)
    }
    
    fun stop() {
        if (!isPlaying) return
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        isPlaying = false
    }
    
    fun getBufferSize(): Int = bufferSize
    fun isInitialized(): Boolean = audioTrack != null
}