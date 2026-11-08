plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val nokkel = System.getenv("NOKKEL_FIL")
val versjon = 100 + ((findProperty("versjon.sms") as String?)?.toInt() ?: 0)

android {
    namespace = "me.arnee.sms"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.arnee.sms"
        minSdk = 26
        targetSdk = 35
        versionCode = versjon
        versionName = versjon.toString()
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

dependencies {
    implementation(project(":felles"))
    testImplementation("junit:junit:4.13.2")
}
