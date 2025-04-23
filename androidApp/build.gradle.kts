plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10" // si usás kotlinx.serialization
}

android {
    namespace = "com.cedo.botn"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cedo.botn"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "license"
    productFlavors {
        create("licenciaA") {
            dimension = "license"
            buildConfigField("String", "EXTENSION_ID", "\"7b22d3cc-a51f-473e-b8c3-066b0a68412c\"")
        }
        create("licenciaB") {
            dimension = "license"
            buildConfigField("String", "EXTENSION_ID", "\"f9bef76e-8def-11e9-bc42-526af7764f64\"")
        }
        create("licenciaC") {
            dimension = "license"
            buildConfigField("String", "EXTENSION_ID", "\"57d64f8b-2448-45f5-8365-29a8e6820235\"")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4" // ✔️ Versión estable de Compose Compiler
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val compose_version = "1.5.4"

    implementation("androidx.compose.ui:ui:$compose_version")
    implementation("androidx.compose.material:material:$compose_version")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose_version")
    implementation("androidx.compose.material3:material3:1.2.0")

    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.google.android.material:material:1.11.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
}
