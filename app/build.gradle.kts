import java.util.Properties

plugins {
    id("com.android.application")
}

// Official releases are signed with a private key kept outside this repo. Without it,
// release builds fall back to the local debug key so anyone can build and sideload.
val signingFile = file(
    System.getenv("WYELINK_SIGNING_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.android/wyelink-release/keystore.properties"
)
val signing = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use { load(it) } }

android {
    namespace = "app.wyelink"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.wyelink"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"
    }

    signingConfigs {
        if (signingFile.isFile) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
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
        aidl = true // Custom Tabs service contract, see src/main/aidl
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Zero runtime dependencies on purpose. Tests only:
    testImplementation("junit:junit:4.13.2")
}
