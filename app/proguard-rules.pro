# kotlinx.serialization keeps generated serializers via @Serializable; keep DTOs.
-keep,includedescriptorclasses class com.wordly.app.**$$serializer { *; }
-keepclassmembers class com.wordly.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.wordly.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
