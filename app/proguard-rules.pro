# Regras padrão do Android Gradle Plugin já cobrem Compose, DataStore e Lifecycle.
# Mantém nomes de enums usados para persistência no DataStore.
# Shizuku: newProcess é acessado por reflexão.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

-keepclassmembers enum com.gamebooster.x.core.** { *; }
