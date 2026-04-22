# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Hilt
-keepclasseswithmembernames class * { @dagger.hilt.* <methods>; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes used with JSON parsing
-keep class com.ringhud.hud.ArObject { *; }
-keep class com.ringhud.ble.ImuFrame { *; }
-keep class com.ringhud.gesture.NormFrame { *; }
