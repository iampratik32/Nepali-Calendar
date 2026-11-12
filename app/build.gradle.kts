import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: read from env (CI secrets) or keystore.properties (local). Falls back to the
// debug key so a release APK can always be produced and sideloaded.
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signing.getProperty(key)

android {
    namespace = "app.patro"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.patro"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        val dataUrl = (project.findProperty("patro.dataUrl") as String?)
            ?: "https://raw.githubusercontent.com/iampratik32/Nepali-Calendar/main/data/"
        buildConfigField("String", "DATA_URL", "\"$dataUrl\"")
    }

    signingConfigs {
        val storeFilePath = signingValue("storeFile", "PATRO_KEYSTORE")
        if (storeFilePath != null && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "PATRO_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "PATRO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "PATRO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "/META-INF/**/*.kotlin_module",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is stubbed in JVM unit tests; use the real one there.
    testImplementation("org.json:json:20250517")
}
