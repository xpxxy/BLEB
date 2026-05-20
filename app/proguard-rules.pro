# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# BLE callback classes used via reflection
-keep class com.bleb.bridge.ble.** { *; }
-keep class com.bleb.bridge.service.** { *; }

# Data classes
-keep class com.bleb.bridge.ble.model.** { *; }
-keep class com.bleb.bridge.bridge.BridgeState** { *; }
