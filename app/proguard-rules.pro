# Keep reflection and data model classes safe during R8 minification
-keep class com.example.data.** { *; }
-keep class android.app.ActivityOptions { *; }
-keep class android.util.MiuiMultiWindowUtils { *; }
-dontwarn android.util.MiuiMultiWindowUtils
