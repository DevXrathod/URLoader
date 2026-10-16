# URLoader ProGuard / R8 Rules

# Preserve youtubedl-android and ffmpeg native bindings & JNI members
-keep class com.yausername.youtubedl_android.** { *; }
-keepclassmembers class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keepclassmembers class com.yausername.ffmpeg.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX Core & Lifecycle
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# Preserve Data Models used in serialization
-keepclassmembers class com.dev.urloader.core.common.DownloadRecord {
    <fields>;
    <methods>;
}