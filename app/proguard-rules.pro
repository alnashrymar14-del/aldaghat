# Media3 relies on reflection for some codec/effect lookups.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlin coroutines service loader entries.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
