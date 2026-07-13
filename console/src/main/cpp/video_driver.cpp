#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <mutex>
#include <cstring>

#define LOG_TAG "VideoDriver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct VideoDriver {
    ANativeWindow* window = nullptr;
    GLuint textureId = 0;
    GLuint programId = 0;
    GLuint vertexShader = 0;
    GLuint fragmentShader = 0;
    GLint positionLoc = -1;
    GLint texCoordLoc = -1;
    GLint textureLoc = -1;
    GLint mvpLoc = -1;
    
    int frameWidth = 0;
    int frameHeight = 0;
    int surfaceWidth = 0;
    int surfaceHeight = 0;
    
    float vertices[20] = {
        -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
         1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
         1.0f,  1.0f, 0.0f, 1.0f, 1.0f,
    };
    
    GLuint vbo = 0;
    GLuint vao = 0;
    
    std::mutex mutex;
    bool initialized = false;
};

static VideoDriver g_videoDriver;

static const char* vertexShaderSource = R"(
#version 300 es
precision mediump float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform mat4 uMVP;
out vec2 vTexCoord;
void main() {
    gl_Position = uMVP * vec4(aPosition, 1.0);
    vTexCoord = aTexCoord;
}
)";

static const char* fragmentShaderSource = R"(
#version 300 es
precision mediump float;
in vec2 vTexCoord;
uniform sampler2D uTexture;
out vec4 fragColor;
void main() {
    fragColor = texture(uTexture, vTexCoord);
}
)";

static GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    
    GLint compiled;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint len;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &len);
        char* log = new char[len];
        glGetShaderInfoLog(shader, len, nullptr, log);
        LOGE("Shader compile error: %s", log);
        delete[] log;
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static GLuint createProgram(const char* vs, const char* fs) {
    GLuint vsId = compileShader(GL_VERTEX_SHADER, vs);
    GLuint fsId = compileShader(GL_FRAGMENT_SHADER, fs);
    if (!vsId || !fsId) return 0;
    
    GLuint program = glCreateProgram();
    glAttachShader(program, vsId);
    glAttachShader(program, fsId);
    glLinkProgram(program);
    
    GLint linked;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint len;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &len);
        char* log = new char[len];
        glGetProgramInfoLog(program, len, nullptr, log);
        LOGE("Program link error: %s", log);
        delete[] log;
        glDeleteProgram(program);
        return 0;
    }
    
    glDeleteShader(vsId);
    glDeleteShader(fsId);
    return program;
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_video_VideoDriver_nativeInit(JNIEnv* env, jobject thiz, jobject surface) {
    std::lock_guard<std::mutex> lock(g_videoDriver.mutex);
    
    if (g_videoDriver.initialized) {
        LOGI("Video driver already initialized");
        return;
    }
    
    g_videoDriver.window = ANativeWindow_fromSurface(env, surface);
    if (!g_videoDriver.window) {
        LOGE("Failed to get native window from surface");
        return;
    }
    
    // Initialize EGL
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor;
    if (!eglInitialize(display, &major, &minor)) {
        LOGE("EGL initialization failed");
        return;
    }
    
    EGLint configAttrs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_NONE
    };
    
    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(display, configAttrs, &config, 1, &numConfigs) || numConfigs == 0) {
        LOGE("Failed to choose EGL config");
        return;
    }
    
    EGLint contextAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttrs);
    if (context == EGL_NO_CONTEXT) {
        LOGE("Failed to create EGL context");
        return;
    }
    
    EGLSurface surface_egl = eglCreateWindowSurface(display, config, g_videoDriver.window, nullptr);
    if (surface_egl == EGL_NO_SURFACE) {
        LOGE("Failed to create EGL surface");
        return;
    }
    
    if (!eglMakeCurrent(display, surface_egl, surface_egl, context)) {
        LOGE("Failed to make EGL context current");
        return;
    }
    
    // Create shaders and program
    g_videoDriver.programId = createProgram(vertexShaderSource, fragmentShaderSource);
    if (!g_videoDriver.programId) {
        LOGE("Failed to create shader program");
        return;
    }
    
    g_videoDriver.positionLoc = glGetAttribLocation(g_videoDriver.programId, "aPosition");
    g_videoDriver.texCoordLoc = glGetAttribLocation(g_videoDriver.programId, "aTexCoord");
    g_videoDriver.textureLoc = glGetUniformLocation(g_videoDriver.programId, "uTexture");
    g_videoDriver.mvpLoc = glGetUniformLocation(g_videoDriver.programId, "uMVP");
    
    // Create texture
    glGenTextures(1, &g_videoDriver.textureId);
    glBindTexture(GL_TEXTURE_2D, g_videoDriver.textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    
    // Create VBO and VAO
    glGenBuffers(1, &g_videoDriver.vbo);
    glBindBuffer(GL_ARRAY_BUFFER, g_videoDriver.vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(g_videoDriver.vertices), g_videoDriver.vertices, GL_STATIC_DRAW);
    
    glGenVertexArrays(1, &g_videoDriver.vao);
    glBindVertexArray(g_videoDriver.vao);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)(3 * sizeof(float)));
    
    g_videoDriver.initialized = true;
    LOGI("Video driver initialized");
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_video_VideoDriver_nativeSetSurfaceSize(JNIEnv* env, jobject thiz, jint width, jint height) {
    std::lock_guard<std::mutex> lock(g_videoDriver.mutex);
    g_videoDriver.surfaceWidth = width;
    g_videoDriver.surfaceHeight = height;
    glViewport(0, 0, width, height);
    LOGI("Surface size set: %dx%d", width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_video_VideoDriver_nativeRenderFrame(JNIEnv* env, jobject thiz, jobject buffer, jint width, jint height, jint pitch) {
    std::lock_guard<std::mutex> lock(g_videoDriver.mutex);
    
    if (!g_videoDriver.initialized || !buffer) return;
    
    g_videoDriver.frameWidth = width;
    g_videoDriver.frameHeight = height;
    
    void* pixels = env->GetDirectBufferAddress(buffer);
    if (!pixels) return;
    
    // Update texture with new frame data
    glBindTexture(GL_TEXTURE_2D, g_videoDriver.textureId);
    
    // Assuming RGB565 or XRGB8888 format from libretro
    // pitch is in bytes, width is in pixels
    int bpp = pitch / width;
    if (bpp == 2) {
        // RGB565
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, pixels);
    } else if (bpp == 4) {
        // XRGB8888 or ARGB8888
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    } else {
        // Fallback - copy row by row
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        uint8_t* dst = new uint8_t[width * height * 4];
        uint8_t* src = static_cast<uint8_t*>(pixels);
        for (int y = 0; y < height; y++) {
            memcpy(dst + y * width * 4, src + y * pitch, width * 4);
        }
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, dst);
        delete[] dst;
    }
    
    // Render
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(g_videoDriver.programId);
    
    // Simple orthographic projection
    float aspect = (float)g_videoDriver.surfaceWidth / g_videoDriver.surfaceHeight;
    float frameAspect = (float)width / height;
    float scaleX = 1.0f, scaleY = 1.0f;
    
    if (frameAspect > aspect) {
        scaleY = aspect / frameAspect;
    } else {
        scaleX = frameAspect / aspect;
    }
    
    float mvp[16] = {
        scaleX, 0, 0, 0,
        0, scaleY, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1
    };
    
    glUniformMatrix4fv(g_videoDriver.mvpLoc, 1, GL_FALSE, mvp);
    glUniform1i(g_videoDriver.textureLoc, 0);
    
    glBindVertexArray(g_videoDriver.vao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    
    glBindVertexArray(0);
    glUseProgram(0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_video_VideoDriver_nativeRelease(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_videoDriver.mutex);
    
    if (g_videoDriver.vbo) {
        glDeleteBuffers(1, &g_videoDriver.vbo);
        g_videoDriver.vbo = 0;
    }
    if (g_videoDriver.vao) {
        glDeleteVertexArrays(1, &g_videoDriver.vao);
        g_videoDriver.vao = 0;
    }
    if (g_videoDriver.textureId) {
        glDeleteTextures(1, &g_videoDriver.textureId);
        g_videoDriver.textureId = 0;
    }
    if (g_videoDriver.programId) {
        glDeleteProgram(g_videoDriver.programId);
        g_videoDriver.programId = 0;
    }
    
    if (g_videoDriver.window) {
        ANativeWindow_release(g_videoDriver.window);
        g_videoDriver.window = nullptr;
    }
    
    g_videoDriver.initialized = false;
    LOGI("Video driver released");
}