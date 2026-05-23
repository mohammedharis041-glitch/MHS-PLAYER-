# MHS Player ProGuard Rules

# Keep model classes
-keep class com.mhs.player.media.model.** { *; }
-keep class com.mhs.player.database.** { *; }
-keep class com.mhs.player.settings.** { *; }

# Keep all project classes to avoid issues with reflection/dependency injection
-keep class com.mhs.player.** { *; }
-keep interface com.mhs.player.** { *; }
-keep enum com.mhs.player.** { *; }

# Also keep the specific Application and Activity classes
-keep class com.mhs.player.MhsApplication { *; }
-keep class com.mhs.player.MainActivity { *; }
-keep class com.mhs.player.player.service.MhsPlaybackService { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Hilt
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**
-keep class javax.inject.** { *; }
-keep class com.mhs.player.MhsApplication { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Kotlin Coroutines — keep ALL internal implementation classes
# Prevents: NoClassDefFoundError: kotlinx.coroutines.internal.CoroutineExceptionHandlerImplKt
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.internal.** { *; }
-keepclassmembernames class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembernames class kotlinx.coroutines.internal.** { volatile <fields>; }

# Required for coroutine debug infrastructure
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# AndroidX Navigation
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

