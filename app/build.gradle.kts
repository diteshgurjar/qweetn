plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Requires app/google-services.json (downloaded from your Firebase project) — see
    // config/fcm.php in the backend for the full setup steps.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.qweet.rider"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qweet.rider"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Change this to your live domain. Must include the trailing /rider/ path.
        buildConfigField("String", "API_BASE_URL", "\"https://kanu.rf.gd/api/v1/rider/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Secure token storage (Android Keystore backed, per the API's recommendation)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Best-available fused location provider (GPS + network + sensors fusion)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Push notifications (FCM) — delivery pipe only; your own backend (includes/fcm.php)
    // decides what gets sent and when. No third-party push relay is used.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Lets us `.await()` the Firebase Task<String> (getInstanceId token) from a coroutine.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
