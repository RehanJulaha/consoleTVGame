package com.retrogamestick.console.video

import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VideoDriver(private val onFrameReady: (SurfaceTexture) -> Unit) : GLSurfaceView.Renderer {
    private var surfaceTexture: SurfaceTexture? = null
    private var textureId = 0
    private var initialized = false
    
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        // Create texture for libretro frame
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]
        
        GLES30.glBindTexture(GL11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(GL11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR)
        GLES30.glTexParameteri(GL11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR)
        GLES30.glTexParameteri(GL11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GL11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE)
        
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture?.setOnFrameAvailableListener { onFrameReady(it) }
        initialized = true
    }
    
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }
    
    override fun onDrawFrame(gl: GL10) {
        surfaceTexture?.updateTexImage()
        // Render texture to screen
        // This is handled by native code
    }
    
    fun getSurface(): Surface? = surfaceTexture?.let { Surface(it) }
    
    fun getTextureId(): Int = textureId
    
    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES30.glDeleteTextures(1, textures, 0)
            textureId = 0
        }
        initialized = false
    }
    
    companion object {
        external fun nativeInit(surface: Surface)
        external fun nativeSetSurfaceSize(width: Int, height: Int)
        external fun nativeRenderFrame(buffer: java.nio.ByteBuffer, width: Int, height: Int, pitch: Int)
        external fun nativeRelease()
    }
}