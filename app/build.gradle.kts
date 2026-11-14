plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gamebooster.x"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gamebooster.x"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.0-barra-lateral"
    }

    // Assinatura opcional por variáveis de ambiente (GitHub Secrets). Nenhuma chave ou senha
    // fica no repositório. Sem elas, os APKs usam a chave de debug padrão do Android Gradle Plugin.
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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
