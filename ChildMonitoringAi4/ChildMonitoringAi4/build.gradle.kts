// Project-level build.gradle.kts
plugins {
    kotlin("android") version "1.8.20" apply false  // Add the Kotlin version
    kotlin("kapt") version "1.8.20" apply false    // Add kapt for annotation processing
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.gms.google.services) apply false
}

