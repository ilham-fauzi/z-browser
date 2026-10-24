plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.browserz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.browserz.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // local test builds only; real signing comes with release setup
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

// Profiles are generated on demand (./gradlew :app:generateBaselineProfile) and committed; builds must not need a device.
baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:webview"))
    implementation(project(":core:ui"))
    implementation(project(":engine:adblock"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:search"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:filters"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}
