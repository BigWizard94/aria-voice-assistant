# Aria Voice Assistant - R8 / ProGuard rules

# ── Vosk + JNA ────────────────────────────────────────────────────────────────
# Vosk's Java API calls into libvosk.so through JNA. JNA finds Java classes,
# fields and callbacks by name via reflection/JNI, so nothing in either package
# may be renamed or removed.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
# JNA references desktop-only AWT classes that don't exist on Android.
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# ── OkHttp / Okio (ship their own consumer rules; silence optional deps) ─────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Gson ──────────────────────────────────────────────────────────────────────
# Gson reflects over field names and generic signatures.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses
-dontwarn sun.misc.**
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ── App models serialised with Gson (field names = JSON keys) ────────────────
-keep class com.bigwizard.aria.data.model.** { *; }

# ── Room ─────────────────────────────────────────────────────────────────────
# (Room ships consumer rules; these are belt-and-braces.)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Crash reports: keep file/line numbers ────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Note: Compose, AndroidX and Kotlin coroutines ship their own consumer rules.
# The previous blanket "-keep class androidx.compose.** { *; }" disabled
# shrinking for all of Compose and bloated the release APK — removed.
