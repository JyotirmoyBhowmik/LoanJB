# LoanJB R8 / ProGuard rules (Room, Moshi, Retrofit, OkHttp, WorkManager, Coroutines, DataStore, Navigation)

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
-keep class com.jyotirmoyb.loan.domain.** { *; }
-keep class com.jyotirmoyb.loan.data.** { *; }

# Retrofit & OkHttp
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# WorkManager Workers
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin Metadata for Moshi KotlinJsonAdapterFactory fallback
-keep class kotlin.Metadata { *; }

# Room entities, DAOs, and generated database implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Preserve Application, BroadcastReceiver and AppWidgetProvider referenced in AndroidManifest.xml
-keep class com.jyotirmoyb.loan.LoanJBApplication { *; }
-keep class com.jyotirmoyb.loan.util.EmiReminderReceiver { *; }
-keep class com.jyotirmoyb.loan.widget.NextEmiWidgetProvider { *; }
-keep class com.jyotirmoyb.loan.util.EmiReminderWorker { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.debug.**

# Google Tink (androidx.security:security-crypto) compile-time ErrorProne annotations
-dontwarn com.google.errorprone.annotations.**
