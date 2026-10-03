plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kellian.wifilunettes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kellian.wifilunettes"
        minSdk = 26
        // VOLONTAIREMENT 28 : c'est ce qui autorise l'app à se connecter
        // elle-même à un Wi-Fi (addNetwork / enableNetwork) sur Android 10+.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
