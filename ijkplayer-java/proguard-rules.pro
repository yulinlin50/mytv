# JNI 调用的类和方法需要保留，否则混淆后会导致运行时崩溃
-keep class tv.danmaku.ijk.media.player.** { *; }
-keep class com.aliyun.rts.** { *; }
-keep class com.wangsu.httpclient.** { *; }

# 保留 @CalledByNative 和 @AccessedByNative 注解的方法和字段
-keepclassmembers class * {
    @tv.danmaku.ijk.media.player.annotations.CalledByNative <methods>;
    @tv.danmaku.ijk.media.player.annotations.AccessedByNative <fields>;
}