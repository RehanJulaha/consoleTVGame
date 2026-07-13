#include <aaudio/AAudio.h>
#include <android/log.h>
#include <mutex>
#include <vector>
#include <cstring>

#define LOG_TAG "AudioDriver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct AudioDriver {
    AAudioStream* stream = nullptr;
    std::mutex mutex;
    std::vector<int16_t> buffer;
    size_t writeIndex = 0;
    size_t readIndex = 0;
    bool initialized = false;
    int32_t sampleRate = 48000;
    int32_t framesPerBurst = 0;
};

static AudioDriver g_audioDriver;

static aaudio_data_callback_result_t audioCallback(
    AAudioStream* stream,
    void* userData,
    void* audioData,
    int32_t numFrames
) {
    int16_t* output = static_cast<int16_t*>(audioData);
    AudioDriver* driver = static_cast<AudioDriver*>(userData);
    
    std::lock_guard<std::mutex> lock(driver->mutex);
    
    int32_t channels = AAudioStream_getChannelCount(stream);
    int32_t available = (driver->writeIndex >= driver->readIndex) 
        ? driver->writeIndex - driver->readIndex 
        : driver->buffer.size() - (driver->readIndex - driver->writeIndex);
    
    int32_t framesAvailable = available / channels;
    int32_t framesToWrite = std::min(numFrames, framesAvailable);
    
    if (framesToWrite > 0) {
        int32_t firstChunk = std::min(framesToWrite, 
            static_cast<int32_t>((driver->buffer.size() - driver->readIndex) / channels));
        
        if (firstChunk > 0) {
            memcpy(output, &driver->buffer[driver->readIndex], firstChunk * channels * sizeof(int16_t));
            driver->readIndex = (driver->readIndex + firstChunk * channels) % driver->buffer.size();
            framesToWrite -= firstChunk;
            output += firstChunk * channels;
        }
        
        if (framesToWrite > 0) {
            memcpy(output, &driver->buffer[driver->readIndex], framesToWrite * channels * sizeof(int16_t));
            driver->readIndex = (driver->readIndex + framesToWrite * channels) % driver->buffer.size();
        }
    }
    
    // Fill remaining with silence
    int32_t remainingFrames = numFrames - framesToWrite;
    if (remainingFrames > 0) {
        memset(output, 0, remainingFrames * channels * sizeof(int16_t));
    }
    
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_retrogamestick_console_audio_AudioDriver_nativeInit(JNIEnv* env, jobject thiz, jint sampleRate, jint bufferSizeMs) {
    std::lock_guard<std::mutex> lock(g_audioDriver.mutex);
    
    if (g_audioDriver.initialized) {
        LOGI("Audio driver already initialized");
        return JNI_TRUE;
    }
    
    g_audioDriver.sampleRate = sampleRate;
    g_audioDriver.buffer.resize(sampleRate * 2 * 2 * bufferSizeMs / 1000); // stereo, 16-bit
    g_audioDriver.writeIndex = 0;
    g_audioDriver.readIndex = 0;
    
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create stream builder: %d", result);
        return JNI_FALSE;
    }
    
    AAudioStreamBuilder_setDeviceId(builder, AAUDIO_UNSPECIFIED);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, 2); // Stereo
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, sampleRate * bufferSizeMs / 1000);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setDataCallback(builder, audioCallback, &g_audioDriver);
    
    result = AAudioStreamBuilder_openStream(builder, &g_audioDriver.stream);
    AAudioStreamBuilder_delete(builder);
    
    if (result != AAUDIO_OK) {
        LOGE("Failed to open audio stream: %d", result);
        return JNI_FALSE;
    }
    
    g_audioDriver.framesPerBurst = AAudioStream_getFramesPerBurst(g_audioDriver.stream);
    g_audioDriver.sampleRate = AAudioStream_getSampleRate(g_audioDriver.stream);
    
    result = AAudioStream_requestStart(g_audioDriver.stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start audio stream: %d", result);
        AAudioStream_close(g_audioDriver.stream);
        g_audioDriver.stream = nullptr;
        return JNI_FALSE;
    }
    
    g_audioDriver.initialized = true;
    LOGI("Audio driver initialized: sampleRate=%d, framesPerBurst=%d", g_audioDriver.sampleRate, g_audioDriver.framesPerBurst);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_audio_AudioDriver_nativeWriteAudio(JNIEnv* env, jobject thiz, jshortArray audioData, jint numFrames) {
    std::lock_guard<std::mutex> lock(g_audioDriver.mutex);
    
    if (!g_audioDriver.initialized || !audioData) return;
    
    jshort* data = env->GetShortArrayElements(audioData, nullptr);
    int32_t channels = 2; // Stereo
    
    int32_t framesToWrite = numFrames;
    int32_t samplesToWrite = framesToWrite * channels;
    
    int32_t availableSpace = (g_audioDriver.readIndex > g_audioDriver.writeIndex)
        ? g_audioDriver.readIndex - g_audioDriver.writeIndex
        : g_audioDriver.buffer.size() - (g_audioDriver.writeIndex - g_audioDriver.readIndex);
    
    if (samplesToWrite > availableSpace) {
        // Buffer full, drop oldest frames
        samplesToWrite = availableSpace;
        framesToWrite = samplesToWrite / channels;
    }
    
    if (framesToWrite <= 0) {
        env->ReleaseShortArrayElements(audioData, data, JNI_ABORT);
        return;
    }
    
    int32_t firstChunk = std::min(samplesToWrite, 
        static_cast<int32_t>(g_audioDriver.buffer.size() - g_audioDriver.writeIndex));
    
    if (firstChunk > 0) {
        memcpy(&g_audioDriver.buffer[g_audioDriver.writeIndex], data, firstChunk * sizeof(int16_t));
        g_audioDriver.writeIndex = (g_audioDriver.writeIndex + firstChunk) % g_audioDriver.buffer.size();
        samplesToWrite -= firstChunk;
        data += firstChunk;
    }
    
    if (samplesToWrite > 0) {
        memcpy(&g_audioDriver.buffer[g_audioDriver.writeIndex], data, samplesToWrite * sizeof(int16_t));
        g_audioDriver.writeIndex = (g_audioDriver.writeIndex + samplesToWrite) % g_audioDriver.buffer.size();
    }
    
    env->ReleaseShortArrayElements(audioData, data, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_audio_AudioDriver_nativeRelease(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_audioDriver.mutex);
    
    if (g_audioDriver.stream) {
        AAudioStream_requestStop(g_audioDriver.stream);
        AAudioStream_close(g_audioDriver.stream);
        g_audioDriver.stream = nullptr;
    }
    
    g_audioDriver.buffer.clear();
    g_audioDriver.initialized = false;
    LOGI("Audio driver released");
}