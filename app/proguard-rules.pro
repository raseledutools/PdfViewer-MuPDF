# ==============================================================================
# RasFocus ProGuard Rules — Crash-safe + Optimized
# ==============================================================================

# -- Readable stack traces -----------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,*Annotation*
-renamesourcefileattribute SourceFile

# -- R8 optimization -----------------------------------------------------------
-optimizationpasses 3
-allowaccessmodification

# -- RasFocus: Keep all class/method (crash-safe) ------------------------------
-keep class com.rasel.RasFocus.** { *; }

# -- Kotlin --------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# -- Coroutines ----------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# -- Firebase ------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# -- Credential Manager / Google Sign-In ---------------------------------------
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# -- ML Kit Document Scanner ---------------------------------------------------
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# -- Cloudinary ----------------------------------------------------------------
-keep class com.cloudinary.** { *; }
-dontwarn com.cloudinary.**

# -- PDFBox --------------------------------------------------------------------
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.**

# -- Coil ----------------------------------------------------------------------
-dontwarn coil.**

# -- OkHttp --------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**

# -- Gson ----------------------------------------------------------------------
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.examples.android.model.** { *; }
-keep class com.google.gson.** { *; }

# -- Room ----------------------------------------------------------------------
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }

# -- Navigation Component ------------------------------------------------------
-keep class androidx.navigation.** { *; }
-keepnames class androidx.navigation.compose.** { *; }
-keepnames class androidx.navigation.NavBackStackEntry { *; }

# -- Jetpack Compose -----------------------------------------------------------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# -- AndroidX Lifecycle & ViewModels -------------------------------------------
-keep class androidx.lifecycle.** { *; }
-keep class androidx.lifecycle.ViewModel { *; }

# -- Android essentials --------------------------------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
