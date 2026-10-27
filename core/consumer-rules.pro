# Entry and Settings travel as JSON between phone and watch (kotlinx.serialization).
-keepclassmembers @kotlinx.serialization.Serializable class com.ffmathy.pooparazzi.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.ffmathy.pooparazzi.core.sync.EntrySyncListenerService
