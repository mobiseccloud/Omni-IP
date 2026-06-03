# Keep JNI entry points intact
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.mobisec.omniip.core.NativeEngine { *; }

# Keep dnsjava packet parsing records intact
-keep class org.xbill.DNS.** { *; }
