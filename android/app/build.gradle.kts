plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Geheimnis für die Freischalt-Codes (siehe ../../freigabe/)
val freigabeSecret = rootProject.file("../freigabe/geheimnis.txt").readText().trim()

android {
    namespace = "de.sportgermany.handybremse"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.sportgermany.handybremse"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        buildConfigField("String", "FREIGABE_SECRET", "\"$freigabeSecret\"")
        // Notfall-Code: hier liegt nur ein Abdruck (SHA-256 mit Salz), nie der Code selbst
        buildConfigField("String", "NOTFALL_SALT", "\"c1a2c92d1891bb4f8b242533a4215ed7\"")
        buildConfigField("String", "NOTFALL_HASH", "\"e06277a713048e009781b3c8bc8e38e612f3f1027371bbb9deeb4c4a87f1bddb\"")
    }

    signingConfigs {
        create("release") {
            // Fester Schlüssel, damit Updates sich ohne Deinstallation installieren lassen
            storeFile = rootProject.file("keystore/handybremse.jks")
            storePassword = "handybremse"
            keyAlias = "handybremse"
            keyPassword = "handybremse"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
