pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Opcional, só quando passado na linha de comando (-PcodexLocalRepo=/caminho): cópia local,
        // conferida por checksum, do motor gráfico do Robolectric para ambientes em que o Maven
        // Central limita downloads grandes. Restrito a um único artefato; o CI não usa.
        providers.gradleProperty("codexLocalRepo").orNull?.let { dir ->
            exclusiveContent {
                forRepository { maven { url = uri(dir); metadataSources { artifact() } } }
                filter { includeModule("org.robolectric", "nativeruntime-dist-compat") }
            }
        }
    }
}

rootProject.name = "GameBoosterX"
include(":app")
include(":grimorio")
