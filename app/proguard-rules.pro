# LoanJB R8 / ProGuard rules (Room, Moshi, Retrofit, OkHttp, WorkManager, Coroutines, DataStore, Navigation)

-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, Exceptions

# Moshi @JsonClass models, generated JsonAdapters, and custom JsonAdapters
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keep class * extends com.squareup.moshi.JsonAdapter { *; }

# Keep sealed scalar model and enums serialized by Moshi by name
-keep class com.jyotirmoyb.loan.data.remote.ConfigScalar { *; }
-keep class com.jyotirmoyb.loan.data.remote.ConfigScalar$* { *; }
-keep enum com.jyotirmoyb.loan.domain.RegionCode { *; }
-keep enum com.jyotirmoyb.loan.domain.CurrencyCode { *; }
-keep enum com.jyotirmoyb.loan.domain.PrepaymentMode { *; }
-keep enum com.jyotirmoyb.loan.domain.InterestRateType { *; }
-keep enum com.jyotirmoyb.loan.data.remote.ConfigValueSource { *; }
-keep enum com.jyotirmoyb.loan.data.remote.ConfigCategory { *; }

# Retrofit & OkHttp
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# WorkManager Workers (RemoteConfigSyncWorker, AmfiNavSyncWorker, EmiReminderWorker, etc.)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin Metadata for Moshi KotlinJsonAdapterFactory fallback
-keep class kotlin.Metadata { *; }

# Room entities, DAOs, and generated database implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.jyotirmoyb.loan.data.*_Impl { *; }
-dontwarn androidx.room.paging.**

# Application, BroadcastReceivers, and AppWidgetProviders referenced in AndroidManifest.xml
-keep class com.jyotirmoyb.loan.LoanJBApplication { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.debug.**

# Google Tink (legacy one-time migration only) compile-time ErrorProne annotations
-dontwarn com.google.errorprone.annotations.**
