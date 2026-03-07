# Keep all classes in the com.ayn.magni package
-keep class com.ayn.magni.** { *; }
-keep class com.ayn.magni.**$* { *; }

# Android framework and AndroidX classes (always keep)
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class android.** { *; }
-keep interface android.** { *; }

# Keep WebView client and interface implementations
-keep class * implements android.webkit.WebViewClient
-keep class * extends android.webkit.WebViewClient { *; }

# Keep callback interfaces for WebView
-keep class * extends android.webkit.WebChromeClient { *; }

# Kotlin metadata
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep View constructors for inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Preserve line numbers for crash logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
