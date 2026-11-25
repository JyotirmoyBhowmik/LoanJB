# LoanJB R8 / ProGuard rules (Room, Moshi, Coroutines, DataStore, Navigation)

-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations

# Moshi & generated JsonAdapters
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keep class com.jyotirmoyb.loan.engine.** { *; }
-keep class com.jyotirmoyb.loan.data.** { *; }

# Kotlin Metadata for Moshi KotlinJsonAdapterFactory fallback
-keep class kotlin.Metadata { *; }

# Room entities, DAOs, and generated database implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Preserve BroadcastReceiver referenced in AndroidManifest.xml
-keep class com.jyotirmoyb.loan.util.EmiReminderReceiver { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.debug.**
