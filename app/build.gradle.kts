plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.odettegd.nexus3d"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.odettegd.nexus3d"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.android.filament:filament-android:1.77.2")
    implementation("com.google.android.filament:gltfio-android:1.77.2")
    implementation("com.google.android.filament:filament-utils-android:1.77.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
