plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val nokkel = System.getenv("NOKKEL_FIL")

android {
    namespace = "me.arnee.verktoy"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.arnee.verktoy"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("VERSJON") ?: "0.1.0"
    }

    signingConfigs {
        if (nokkel != null) {
            create("release") {
                storeFile = file(nokkel)
                storePassword = System.getenv("NOKKEL_PASSORD")
                keyAlias = System.getenv("NOKKEL_ALIAS")
                keyPassword = System.getenv("NOKKEL_PASSORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (nokkel != null) signingConfig = signingConfigs.getByName("release")
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
