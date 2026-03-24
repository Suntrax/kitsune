# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
# http://developer.android.com/guide/developing/tools/proguard.html

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Jsoup
-keeppackagenames org.jsoup.nodes

# Coil
-dontwarn coil.**
