#include <mutex>
#include <array>

struct InputDriver {
    std::mutex mutex;
    
    // Player input state (2 players)
    struct PlayerState {
        uint32_t buttons = 0;
        int16_t lx = 0, ly = 0;
        int16_t rx = 0, ry = 0;
    } players[2];
    
    // Libretro input callback
    retro_input_state_t inputStateCb = nullptr;
};

static InputDriver g_inputDriver;

// Button mapping from our input frame to libretro IDs
static const int BUTTON_MAP[16] = {
    RETRO_DEVICE_ID_JOYPAD_B,      // 0: B
    RETRO_DEVICE_ID_JOYPAD_Y,      // 1: Y
    RETRO_DEVICE_ID_JOYPAD_SELECT, // 2: Select
    RETRO_DEVICE_ID_JOYPAD_START,  // 3: Start
    RETRO_DEVICE_ID_JOYPAD_UP,     // 4: Up
    RETRO_DEVICE_ID_JOYPAD_DOWN,   // 5: Down
    RETRO_DEVICE_ID_JOYPAD_LEFT,   // 6: Left
    RETRO_DEVICE_ID_JOYPAD_RIGHT,  // 7: Right
    RETRO_DEVICE_ID_JOYPAD_A,      // 8: A
    RETRO_DEVICE_ID_JOYPAD_X,      // 9: X
    RETRO_DEVICE_ID_JOYPAD_L,      // 10: L
    RETRO_DEVICE_ID_JOYPAD_R,      // 11: R
    RETRO_DEVICE_ID_JOYPAD_L2,     // 12: L2
    RETRO_DEVICE_ID_JOYPAD_R2,     // 13: R2
    RETRO_DEVICE_ID_JOYPAD_L3,     // 14: L3
    RETRO_DEVICE_ID_JOYPAD_R3,     // 15: R3
};

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_input_InputDriver_nativeSetInputStateCallback(JNIEnv* env, jobject thiz, jlong callbackPtr) {
    // Store the callback pointer for JNI calls
    // This is called from Kotlin to set the libretro input_state_cb
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_input_InputDriver_nativeSetInputFrame(JNIEnv* env, jobject thiz, jint playerSlot, jint buttons, jint lx, jint ly, jint rx, jint ry) {
    if (playerSlot < 0 || playerSlot > 1) return;
    
    std::lock_guard<std::mutex> lock(g_inputDriver.mutex);
    
    g_inputDriver.players[playerSlot].buttons = buttons;
    g_inputDriver.players[playerSlot].lx = static_cast<int16_t>(lx);
    g_inputDriver.players[playerSlot].ly = static_cast<int16_t>(ly);
    g_inputDriver.players[playerSlot].rx = static_cast<int16_t>(rx);
    g_inputDriver.players[playerSlot].ry = static_cast<int16_t>(ry);
}

extern "C" JNIEXPORT void JNICALL
Java_com_retrogamestick_console_input_InputDriver_nativePollInput(JNIEnv* env, jobject thiz) {
    // Called by libretro's input_poll_cb
    // Update any time-sensitive input state here
    // Currently no-op since we use direct state setting
}

// Called by libretro's input_state_cb
int16_t retro_input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port > 1) return 0;
    
    std::lock_guard<std::mutex> lock(g_inputDriver.mutex);
    const auto& player = g_inputDriver.players[port];
    
    if (device == RETRO_DEVICE_JOYPAD) {
        if (id < 16) {
            return (player.buttons & (1 << id)) ? 1 : 0;
        }
    } else if (device == RETRO_DEVICE_ANALOG) {
        if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) return player.lx;
            if (id == RETRO_DEVICE_ID_ANALOG_Y) return player.ly;
        } else if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) return player.rx;
            if (id == RETRO_DEVICE_ID_ANALOG_Y) return player.ry;
        }
    }
    
    return 0;
}