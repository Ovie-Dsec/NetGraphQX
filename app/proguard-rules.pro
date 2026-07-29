# Proguard rules for NetGraph QX

# Keep exp4j expression parser
-keep class net.objecthunter.exp4j.** { *; }

# Keep Compose runtime
-dontwarn androidx.compose.**
