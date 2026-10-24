plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds are signed when these are set (CI), else left unsigned.
val keystore: String? = System.getenv("FERROCINE_KEYSTORE")

android {
    namespace = "io.github.sebastka.ferrocine"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.sebastka.ferrocine"
        minSdk = 26
        targetSdk = 36
        // CI passes the release tag's date as the version (2026-10-24.1 →
        // 2026102401); local builds are 1.
        versionCode = (System.getenv("FERROCINE_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("FERROCINE_VERSION_NAME") ?: "dev"
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("FERROCINE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FERROCINE_KEY_ALIAS")
                keyPassword = System.getenv("FERROCINE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.tv.material3.ExperimentalTvMaterial3Api")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
}
