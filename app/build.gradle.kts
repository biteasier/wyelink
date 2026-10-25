plugins {
    id("com.android.application")
}

android {
    namespace = "app.wyelink"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.wyelink"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the local debug key so the APK can be sideloaded right away.
            // Swap in your own signingConfig before publishing to a store.
            signingConfig = signingConfigs.getByName("debug")
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
