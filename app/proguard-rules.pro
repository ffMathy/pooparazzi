# R8 rules of the phone app (release builds). Libraries bring their own rules (kotlinx.serialization, Room, Glance,
# WorkManager, Navigation); these cover what the app itself reaches by name.

# Navigation routes, the widget memo and the edit-sheet draft are kotlinx.serialization classes: keep their generated
# serializers reachable (route objects are looked up through their serializer).
-keepclassmembers @kotlinx.serialization.Serializable class com.ffmathy.pooparazzi.phone.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Glance instantiates widget action callbacks by class name when a button is tapped.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager instantiates the widget refresh worker by class name.
-keep class com.ffmathy.pooparazzi.phone.widget.WidgetRefreshWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Readable crash traces without shipping the mapping inside the APK.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
