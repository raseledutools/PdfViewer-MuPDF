plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rasel.pdfviewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rasel.pdfviewer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val b64 = System.getenv("KEYSTORE_BASE64")
            if (b64 != null) {
                val ksFile = File(buildDir, "release.keystore")
                ksFile.writeBytes(java.util.Base64.getDecoder().decode(b64))
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias     = System.getenv("KEY_ALIAS")
                keyPassword  = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // MuPDF native .so libs
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Accompanist — system ui controller
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ─────────────────────────────────────────────────────────────────────────
    // MuPDF Android SDK — official Artifex viewer-core (Maven Central)
    // True vector rendering: same engine as Adobe / MuPDF desktop
    // Sharp at any zoom — re-renders from vectors, NOT bitmap-stretch
    // Supports: PDF, XPS, EPUB, CBZ, MOBI
    // FIX: 1.24.10 doesn't actually exist on maven.ghostscript.com (confirmed
    // by the build failure itself — "Could not find ...viewer/1.24.10/...").
    // mvnrepository.com's listing was apparently wrong/stale for this
    // artifact. Switched to a floating version, matching MuPDF's own current
    // official docs exactly (mupdf.readthedocs.io/en/latest/guide/
    // using-with-android.html) — Gradle resolves this against the repo's
    // real maven-metadata.xml itself, so it always gets a version that
    // genuinely exists there instead of a hand-picked number that might not.
    // ─────────────────────────────────────────────────────────────────────────
    implementation("com.artifex.mupdf:viewer:1.15.+")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
