// scanLib — cloned from github.com/entropyconquers/android-document-scanner-library
// (Canny edge detection + contour finding + perspective correction, real OpenCV algorithm)
//
// Changes made from the original repo:
// - OpenCV: was runtime-loaded via the old, deprecated "OpenCV Manager" APK
//   pattern (org.opencv.android.OpenCVLoader.initAsync + BaseLoaderCallback).
//   Since OpenCV 4.9.0 (Dec 2023), OpenCV publishes a real AAR on Maven
//   Central (org.opencv:opencv, Apache-2.0) that statically links the native
//   library into THIS app — no separate Manager app needed on the device.
//   model/OpenCVLoader.kt was rewritten to use OpenCVLoader.initLocal().
// - CameraX bumped to 1.3.3 to match the rest of the project (was 1.1.0-alpha04
//   / 1.0.0-alpha24 — too old to coexist safely with our app module).
// - Dropped com.github.garg-lucifer:AndroidDocumentFilter (JitPack, unverified/
//   unmaintained) — RasFocus's own ScanToPdfScreen.kt already implements
//   Magic Color / Grayscale / B&W filters natively with ColorMatrix, so this
//   extra third-party dependency isn't needed.
// - Dropped the maven-publish block — we consume this as a local project
//   module, not as a published artifact.
// - namespace moved here from AndroidManifest.xml's `package` attribute
//   (required by AGP 8+).

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.vishesh.scanner"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ── Real OpenCV — Maven Central AAR, no Manager app needed ──────────────
    implementation("org.opencv:opencv:4.9.0")

    api("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    api("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    api("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // CameraX — matched to the app module's version so both resolve to one
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")

    // Zoomable ImageView used by CropperActivity for corner-drag adjustment
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("com.airbnb.android:lottie:6.4.0")
}
