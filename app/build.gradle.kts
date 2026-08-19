plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cyank.xenondroidcc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cyank.xenondroidcc"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // The toolchain binaries land here during the GitHub Actions build
    // (see .github/workflows/build-toolchain-and-apk.yml). They are
    // NOT executable as regular assets on Android — installExecutables()
    // in MainActivity copies + chmods them into app-private storage
    // on first run, which is required for Android to allow execution.
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
