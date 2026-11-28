# kotlinx.serialization já traz regras próprias no AAR/JAR.
# Mantém os serializers gerados dos modelos persistidos (backup JSON compatível entre versões).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.umbral.codex.model.**$$serializer { *; }
-keepclassmembers class com.umbral.codex.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.umbral.codex.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
