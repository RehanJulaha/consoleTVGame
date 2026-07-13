# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# WebRTC
-dontwarn org.webrtc.**
-keep class org.webrtc.** { *; }

# JmDNS
-dontwarn javax.jmdns.**
-keep class javax.jmdns.** { *; }

# Protobuf
-dontwarn com.google.protobuf.**
-keep class com.google.protobuf.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Keep JNI classes
-keep class com.retrogamestick.console.emulator.** { *; }
-keep class com.retrogamestick.console.video.** { *; }
-keep class com.retrogamestick.console.audio.** { *; }
-keep class com.retrogamestick.controller.network.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }