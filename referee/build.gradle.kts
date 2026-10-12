// Relationship Referee — APP module build file (this replaces app/build.gradle.kts,
// NOT the project-level build.gradle.kts in the root folder).
//
// If Android Studio asks to sync or upgrade versions while you use this file, ACCEPT.
// Plugin/Compose versions live in the project's version catalog
// (gradle/libs.versions.toml) exactly as the template created them — the spots
// below marked "VERSION:" are where numbers matter.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "www.daqtpi.com"
    compileSdk = 36 // VERSION: template default — if Studio suggests newer, accept

    defaultConfig {
        applicationId = "www.daqtpi.com"
        minSdk = 24
        targetSdk = 36 // VERSION: keep the same as compileSdk

        // Must be HIGHER than the version code of the current AppCreator24
        // build, or Google Play will reject the upload as "not an update".
        // If an upload ever complains about versionCode, just raise this number.
        versionCode = 100
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Google Play Billing — powers the $1 "verdict" purchase.
    // VERSION: 8.0.0 — if Android Studio offers an update here, accept it.
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.android.billingclient:billing-ktx:8.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
