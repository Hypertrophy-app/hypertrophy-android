# Hilt generated entry points.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Clerk SDK models and reflective auth entry points.
-keep class com.clerk.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Room entities and DAOs must survive shrinking so the schema stays intact.
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

-dontwarn com.google.mediapipe.proto.**
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.protobuf.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.google.mediapipe.framework.** { *; }

# SceneView / Filament native methods and rendering classes (catalog-pinned).
-keep class io.github.sceneview.** { *; }
-keep class com.google.android.filament.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Kotlin coroutines and serialization used by Ktor and Room.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Compose
-keep class androidx.compose.** { *; }

# Ktor client
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# CameraX
-keep class androidx.camera.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# AutoValue shaded javapoet references JDK-internal type visitors.
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile
