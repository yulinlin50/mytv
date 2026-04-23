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

# ============ Kotlinx Serialization ============
# Preserve EpgList and Epg serialization
# EpgList uses delegation by 'value' (List<Epg>), need to preserve both class and delegate

-keep class top.yogiczy.mytv.core.data.entities.epg.** { *; }
-keepclassmembers class top.yogiczy.mytv.core.data.entities.epg.EpgList {
    <init>();
    <fields>;
}

# Preserve data classes used in serialization
-keepclassmembers class top.yogiczy.mytv.core.data.entities.epg.Epg {
    <fields>;
}

-keepclassmembers class top.yogiczy.mytv.core.data.entities.epg.EpgProgramme {
    <fields>;
}

-keepclassmembers class top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeRecent {
    <fields>;
}
