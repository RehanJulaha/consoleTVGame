cmake_minimum_required(VERSION 3.18.1)
project("retro_frontend" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Android NDK paths
if(ANDROID)
    set(CMAKE_ANDROID_NDK ${CMAKE_ANDROID_NDK} CACHE PATH "Android NDK path")
endif()

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Find log library
find_library(log_lib log)
find_library(android_lib android)
find_library(EGL_lib EGL)
find_library(GLESv2_lib GLESv2)
find_library(OpenSLES_lib OpenSLES)

# RetroArch libretro frontend
add_library(retro_frontend SHARED
    retro_frontend.cpp
)

target_include_directories(retro_frontend PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(retro_frontend
    ${log_lib}
    ${android_lib}
    ${EGL_lib}
    ${GLESv2_lib}
    ${OpenSLES_lib}
    dl
    m
)

# Compiler flags
target_compile_options(retro_frontend PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fPIC
    -DANDROID
    -D__ANDROID__
    -DRETRO_ENVIRONMENT_GET_LOG_INTERFACE=27
)

# Set output directory for JNI libs
set_target_properties(retro_frontend PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/jniLibs/${ANDROID_ABI}
)