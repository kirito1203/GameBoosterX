plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Códice Umbral — grimório digital de fichas de RPG.
// Módulo independente do Game Booster X: APK, ícone, nome e pacote próprios.
android {
    namespace = "com.umbral.codex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.umbral.codex"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Mesma estratégia de assinatura do app principal: chave própria via Secrets, senão debug.
    val keystorePath = System.getenv("GBX_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("gbx") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("GBX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GBX_KEY_ALIAS")
                keyPassword = System.getenv("GBX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("gbx")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("gbx") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric: renderiza as telas reais na JVM (testes de interface e capturas de tela).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.systemProperty("codex.screenshots", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
            it.maxHeapSize = "3g"
            // Opcional: imagens do Android já baixadas (modo offline do Robolectric).
            providers.gradleProperty("robolectricJars").orNull?.let { dir ->
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", dir)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
