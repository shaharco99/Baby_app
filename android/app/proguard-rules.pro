# Bouncy Castle ships JCE provider classes that reference desktop-JDK APIs absent on Android.
# We only use the lightweight org.bouncycastle.crypto.* API, so the rest can be stripped.
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.jce.provider.**

# Garmin Connect IQ companion SDK talks to Garmin Connect over AIDL binding and its own
# serializer, which reach classes reflectively; keep it whole (WatchTimerPublisher's Garmin sink).
-keep class com.garmin.** { *; }
-dontwarn com.garmin.**
