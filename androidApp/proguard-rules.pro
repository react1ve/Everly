# kotlinx.serialization ships its own consumer rules (covers @Serializable routes + backup DTOs).
# Koin resolves by Kotlin types, no reflection on our classes.
# Glance: ActionCallback classes are instantiated by name from the widget PendingIntent.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# Receivers/Activity/Application are kept by the merged manifest rules.
