// Module-level build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    kotlin("android") // Apply Kotlin Android plugin
    kotlin("kapt")    // Apply Kapt plugin for annotation processing
}

android {
    namespace = "com.childmonitorai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.childmonitorai"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.1.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")

    // Room dependencies
    val room_version = "2.5.2"
    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version") // Use kapt for Room
    implementation("androidx.room:room-ktx:$room_version")

    // Coroutine Support (Optional)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
