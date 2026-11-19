plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.alejandro.milauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.alejandro.milauncher"
        // minSdk bajo a propósito: muchas radios chinas dicen "Android 11/12"
        // pero por dentro corren una versión más antigua.
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmado con la llave de debug para poder instalarlo directo en la radio.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sin dependencias externas: solo el SDK de Android, para que sea liviano (la radio tiene 2 GB de RAM).
dependencies {
}
