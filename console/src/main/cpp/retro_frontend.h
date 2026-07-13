#ifndef RETRO_FRONTEND_H
#define RETRO_FRONTEND_H

#include <jni.h>
#include <libretro.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>

#ifdef __cplusplus
extern "C" {
#endif

// Core handles
struct RetroCore {
    void* handle = nullptr;
    retro_init_t init = nullptr;
    retro_deinit_t deinit = nullptr;
    retro_api_version_t api_version = nullptr;
    retro_get_system_info_t get_system_info = nullptr;
    retro_get_system_av_info_t get_system_av_info = nullptr;
    retro_set_controller_port_device_t set_controller_port_device = nullptr;
    retro_reset_t reset = nullptr;
    retro_run_t run = nullptr;
    retro_load_game_t load_game = nullptr;
    retro_unload_game_t unload_game = nullptr;
    retro_serialize_t serialize = nullptr;
    retro_unserialize_t unserialize = nullptr;
    retro_cheat_reset_t cheat_reset = nullptr;
    retro_cheat_set_t cheat_set = nullptr;
    retro_get_memory_data_t get_memory_data = nullptr;
    retro_get_memory_size_t get_memory_size = nullptr;
    retro_set_environment_t set_environment = nullptr;
    retro_set_video_refresh_t set_video_refresh = nullptr;
    retro_set_audio_sample_t set_audio_sample = nullptr;
    retro_set_audio_sample_batch_t set_audio_sample_batch = nullptr;
    retro_set_input_poll_t set_input_poll = nullptr;
    retro_set_input_state_t set_input_state = nullptr;
};

class LibretroFrontend {
public:
    LibretroFrontend();
    ~LibretroFrontend();
    
    bool loadCore(const char* corePath);
    void unloadCore();
    
    bool loadGame(const char* romPath);
    void unloadGame();
    
    void runFrame();
    
    void setVideoRefreshCallback(retro_video_refresh_t cb);
    void setAudioSampleBatchCallback(retro_audio_sample_batch_t cb);
    void setInputPollCallback(retro_input_poll_t cb);
    void setInputStateCallback(retro_input_state_t cb);
    
    void setInputState(int port, int device, int index, int id, int16_t value);
    
    bool isCoreLoaded() const { return coreLoaded; }
    bool isGameLoaded() const { return gameLoaded; }
    
    struct SystemAVInfo {
        int width = 0;
        int height = 0;
        int max_width = 0;
        int max_height = 0;
        float aspect_ratio = 1.0f;
        float sample_rate = 48000.0f;
        float fps = 60.0f;
    };
    
    SystemAVInfo getSystemAVInfo() const;
    
    // Save/Load state
    bool saveState(int slot);
    bool loadState(int slot);
    
    // Core options
    void setCoreOption(const char* key, const char* value);
    
private:
    RetroCore core;
    bool coreLoaded = false;
    bool gameLoaded = false;
    
    // Callbacks
    retro_video_refresh_t videoRefreshCb = nullptr;
    retro_audio_sample_batch_t audioSampleBatchCb = nullptr;
    retro_input_poll_t inputPollCb = nullptr;
    retro_input_state_t inputStateCb = nullptr;
    
    // Environment callbacks
    static bool environmentCb(unsigned cmd, void* data);
    static void logCb(enum retro_log_level level, const char* fmt, ...);
    
    // Framebuffer for video
    mutable std::mutex frameMutex;
    struct FrameData {
        uint32_t* pixels = nullptr;
        int width = 0;
        int height = 0;
        int pitch = 0;
        bool hasFrame = false;
    } currentFrame;
    
    // Audio buffer
    std::vector<int16_t> audioBuffer;
    std::mutex audioMutex;
    
    // Input state for 2 players
    struct InputState {
        uint32_t buttons = 0;
        int16_t lx = 0, ly = 0;
        int16_t rx = 0, ry = 0;
    } playerInput[2];
    
    std::mutex inputMutex;
};

// JNI bridges
extern "C" JNIEXPORT jlong JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeInit(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeDestroy(JNIEnv* env, jobject thiz, jlong ptr);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeLoadCore(JNIEnv* env, jobject thiz, jlong ptr, jstring corePath);

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeUnloadCore(JNIEnv* env, jobject thiz, jlong ptr);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeLoadGame(JNIEnv* env, jobject thiz, jlong ptr, jstring romPath);

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeUnloadGame(JNIEnv* env, jobject thiz, jlong ptr);

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeRunFrame(JNIEnv* env, jobject thiz, jlong ptr);

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeSetInputState(JNIEnv* env, jobject thiz, jlong ptr, jint port, jint device, jint index, jint id, jint value);

extern "C" JNIEXPORT jintArray JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeGetSystemAVInfo(JNIEnv* env, jobject thiz, jlong ptr);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeSaveState(JNIEnv* env, jobject thiz, jlong ptr, jint slot);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeLoadState(JNIEnv* env, jobject thiz, jlong ptr, jint slot);

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_emulator_LibretroBridge_nativeSetCoreOption(JNIEnv* env, jobject thiz, jlong ptr, jstring key, jstring value);

#ifdef __cplusplus
}
#endif

#endif // RETRO_FRONTEND_H