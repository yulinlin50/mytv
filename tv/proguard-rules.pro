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

-keep class tv.danmaku.ijk.media.player.** { *; }
-keep class com.aliyun.rts.network.** { *; }
-keep class com.wangsu.httpclient.** { *; }
-keep class org.mozilla.javascript.** { *; }

-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**

-dontwarn dalvik.system.VMStack
-keep class com.tencent.smtt.** { *; }
-keep class com.tencent.tbs.** { *; }

# ========== 实时字幕：Vosk ==========
-keep class org.vosk.** { *; }
-keep class com.alphacephei.** { *; }

# ========== 实时字幕：JNA（Vosk 依赖） ==========
-keep class com.sun.jna.** { *; }
-keep class net.java.dev.jna.** { *; }

# ========== 实时字幕：ML Kit ==========
-keep class com.google.mlkit.** { *; }

# ========== 实时字幕：sherpa-onnx ==========
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ========== 实时字幕：Apache Commons Compress ==========
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }