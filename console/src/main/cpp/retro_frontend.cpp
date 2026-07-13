#include "retro_frontend.h"
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>
#include <algorithm>

static LibretroFrontend* g_frontend = nullptr;

extern "C" JNIEXPORT jlong JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeInit(JNIEnv* env, jobject thiz) {
    g_frontend = new LibretroFrontend();
    return reinterpret_cast<jlong>(g_frontend);
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeDestroy(JNIEnv* env, jobject thiz, jlong ptr) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (frontend) {
        delete frontend;
    }
    if (g_frontend == frontend) {
        g_frontend = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeLoadCore(JNIEnv* env, jobject thiz, jlong ptr, jstring corePath) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (!frontend) return JNI_FALSE;
    
    const char* path = env->GetStringUTFChars(corePath, nullptr);
    bool result = frontend->loadCore(path);
    env->ReleaseStringUTFChars(corePath, path);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeUnloadCore(JNIEnv* env, jobject thiz, jlong ptr) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (frontend) {
        frontend->unloadCore();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeLoadGame(JNIEnv* env, jobject thiz, jlong ptr, jstring romPath) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (!frontend) return JNI_FALSE;
    
    const char* path = env->GetStringUTFChars(romPath, nullptr);
    bool result = frontend->loadGame(path);
    env->ReleaseStringUTFChars(romPath, path);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeUnloadGame(JNIEnv* env, jobject thiz, jlong ptr) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (frontend) {
        frontend->unloadGame();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeRunFrame(JNIEnv* env, jobject thiz, jlong ptr) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (frontend) {
        frontend->runFrame();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeSetInputState(JNIEnv* env, jobject thiz, jlong ptr, jint port, jint device, jint index, jint id, jint value) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (frontend) {
        frontend->setInputState(port, device, index, id, static_cast<int16_t>(value));
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeGetSystemAVInfo(JNIEnv* env, jobject thiz, jlong ptr) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (!frontend) return nullptr;
    
    auto info = frontend->getSystemAVInfo();
    jintArray result = env->NewIntArray(6);
    jint data[6] = {
        info.width, info.height, info.max_width, info.max_height,
        static_cast<int>(info.fps * 1000), static_cast<int>(info.sample_rate)
    };
    env->SetIntArrayRegion(result, 0, 6, data);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeSaveState(JNIEnv* env, jobject thiz, jlong ptr, jint slot) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (!frontend) return JNI_FALSE;
    return frontend->saveState(slot) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeLoadState(JNIEnv* env, jobject thiz, jlong ptr, jint slot) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (!frontend) return JNI_FALSE;
    return frontend->loadState(slot) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeSetCoreOption(JNIEnv* env, jobject thiz, jlong ptr, jstring key, jstring value) {
    LibretroFrontend* frontend = reinterpret_cast<LibretroFrontend*>(ptr);
    if (!frontend) return;
    
    const char* k = env->GetStringUTFChars(key, nullptr);
    const char* v = env->GetStringUTFChars(value, nullptr);
    frontend->setCoreOption(k, v);
    env->ReleaseStringUTFChars(key, k);
    env->ReleaseStringUTFChars(value, v);
}