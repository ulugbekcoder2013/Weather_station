# ProGuard & R8 Optimization Rules for Smart Home Weather Station

# Keep Room database schemas & entities
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# Keep DTO data models for Gson serialization
-keepclassmembers class com.weatherstation.app.data.remote.dto.** { *; }
-keep class com.weatherstation.app.data.remote.dto.** { *; }

# Keep domain models
-keep class com.weatherstation.app.domain.model.** { *; }

# Keep Retrofit annotations and interfaces
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Keep WorkManager worker classes
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
