# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keepattributes EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.digitalsignage.tv.core.network.model.** { *; }

# Socket.IO Client
-keep class io.socket.** { *; }
-keep class io.socket.client.** { *; }
-keep class io.socket.engineio.client.** { *; }

# Room Database
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**
-keep class com.digitalsignage.tv.core.database.entity.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# ZXing Core
-keep class com.google.zxing.** { *; }

# Compose for TV
-keep class androidx.tv.material3.** { *; }
-keep class androidx.compose.ui.** { *; }

# Domain Models
-keep class com.digitalsignage.tv.domain.model.** { *; }
-keep class com.digitalsignage.tv.core.storage.** { *; }
