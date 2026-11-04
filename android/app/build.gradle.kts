import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing: keystore.properties (not in git) holds storeFile, storePassword, keyAlias, keyPassword.
// Without it, only the debug build is available.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.jumpypangolin.tidalremote"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jumpypangolin.tidalremote"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.media:media:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.4.0@aar")
}
