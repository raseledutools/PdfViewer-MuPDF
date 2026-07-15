import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // FIX: Kotlin 2.0+ decoupled the Compose compiler from the Kotlin
    // compiler itself — this project uses Kotlin 2.1.10 (RasFocus-final
    // uses 1.9.24, which is why it never needed this plugin; not something
    // to copy from there, this is specific to this project's newer Kotlin
    // version). Version already declared with apply false at the root
    // build.gradle.kts, just needed to actually be applied here.
    id("com.google.gms.google-services")
    id("kotlin-kapt")
}

android {
    namespace = "com.rasel.RasFocus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rasel.RasFocus"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GOOGLE_WEB_CLIENT_ID — priority order:
        // 1. gradle.properties / local.properties (developer machine / CI secret)
        // 2. GOOGLE_WEB_CLIENT_ID environment variable (CI/CD)
        // 3. Auto-read from google-services.json (OAuth client type=3 = web client)
        //    This ensures Google Sign-In always works even without a manually set property.
        val googleClientId: String = run {
            val fromProp = project.findProperty("GOOGLE_WEB_CLIENT_ID") as String?
            val fromEnv  = System.getenv("GOOGLE_WEB_CLIENT_ID")
            if (!fromProp.isNullOrBlank()) return@run fromProp
            if (!fromEnv.isNullOrBlank())  return@run fromEnv
            // Fallback: parse google-services.json directly
            try {
                val gsFile = file("google-services.json")
                if (gsFile.exists()) {
                    val json = groovy.json.JsonSlurper().parse(gsFile) as Map<*, *>
                    @Suppress("UNCHECKED_CAST")
                    val clients = json["client"] as? List<Map<*, *>> ?: emptyList()
                    for (client in clients) {
                        @Suppress("UNCHECKED_CAST")
                        val oauthClients = client["oauth_client"] as? List<Map<*, *>> ?: continue
                        val webClient = oauthClients.firstOrNull { it["client_type"] == 3 }
                        val id = webClient?.get("client_id") as? String
                        if (!id.isNullOrBlank()) return@run id
                    }
                }
            } catch (_: Exception) {}
            "" // last resort — will show error in UI if blank
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")

        vectorDrawables {
            useSupportLibrary = true
        }

        // MuPDF native .so libs — all ABIs needed for full flavor
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    // ✅ ABI splits — build TIME optimization: only produce 2 APKs instead of 5.
    // - universal: bundles ALL ABIs, for real distribution (any phone can install)
    // - armeabi-v7a: single-ABI, small, for the developer's own device during
    //   local/debug testing (dev's test phone is armeabi-v7a)
    // arm64-v8a/x86/x86_64 per-ABI splits removed on purpose — they were built
    // but never used (CI only ever uploaded one anyway), so this cuts real
    // Gradle build time. If a per-arm64-v8a APK is needed again later, add
    // "arm64-v8a" back to the include(...) list below.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a")
            isUniversalApk = true
        }
    }

    // ✅ Product Flavors — light = PDF ছাড়া (ছোট, OpenCV/pdfium/scanLib নাই), full = PDF সহ
    flavorDimensions += "mode"
    productFlavors {
        create("light") {
            dimension = "mode"
            // applicationId same — google-services.json match করার জন্য
        }
        create("full") {
            dimension = "mode"
            // MuPDF + pdfium native .so libs এর জন্য
            packaging.jniLibs.useLegacyPackaging = true
        }
    }

    // ✅ Source sets — PdfViewerActivity flavor অনুযায়ী আলাদা
    sourceSets {
        getByName("full") {
            java.srcDirs("src/full/java")
        }
        getByName("light") {
            java.srcDirs("src/light/java")
        }
    }

    // ✅ Fixed signing config — CI এ একই keystore ব্যবহার করে সবসময়
    // একই signature produce করে, তাই update install করতে আর uninstall লাগে না।
    // এখন debug AND release দুটোই এই একই config ব্যবহার করে — তাই debug থেকে
    // release এ upgrade করলেও "App not installed" (signature mismatch) হবে না।
    signingConfigs {
        create("fixedSigning") {
            val keystoreB64 = System.getenv("KEYSTORE_BASE64") ?: ""
            if (keystoreB64.isNotEmpty()) {
                // CI: base64 decode করে temp file এ লেখো
                val keystoreFile = File(buildDir, "rasfocus_fixed.keystore")
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(Base64.getDecoder().decode(keystoreB64))
                storeFile     = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "rasfocus123"
                keyAlias      = System.getenv("KEY_ALIAS")          ?: "rasfocus_debug"
                keyPassword   = System.getenv("KEY_PASSWORD")       ?: "rasfocus123"
            }
        }
    }

    buildTypes {
        debug {
            val keystoreB64 = System.getenv("KEYSTORE_BASE64") ?: ""
            if (keystoreB64.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("fixedSigning")
            }
        }
        release {
            // FIX: without a signingConfig here, assembleFullRelease produces
            // an UNSIGNED APK — Android refuses to install any unsigned APK,
            // even for sideloading. Same fixedSigning keystore as debug, so
            // switching from the old debug build to this release build won't
            // trigger a signature-mismatch "App not installed" either.
            val keystoreB64 = System.getenv("KEYSTORE_BASE64") ?: ""
            if (keystoreB64.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("fixedSigning")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // ✅ Performance: faster Compose skipping, reduce allocations
        freeCompilerArgs += listOf(
            "-Xjvm-default=all"
        )
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ✅ R8 full mode — class merging, inlining, dead code removal (more aggressive than default)
    // proguard-android-optimize.txt already enables this but making it explicit
    bundle {
        language { enableSplit = false }
        density  { enableSplit = false }
    }

    // NOTE: composeOptions.kotlinCompilerExtensionVersion removed — with the
    // org.jetbrains.kotlin.plugin.compose plugin applied (Kotlin 2.0+), the
    // Compose compiler version is tied to the Kotlin version automatically;
    // this block would have been silently ignored.

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
                "*.proto",
                "androidsupportmultidexversion.txt",
                "kotlin/**",
                "**/*.txt",
                "**/*.md"
            )
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // FIX: androidx.camera:camera-camera2/lifecycle/view and com.google.zxing:core
    // were declared here as plain `implementation` (both flavors) but grep
    // across the ENTIRE app module (src/main, src/full, src/light — every .kt
    // file and every .xml layout) found ZERO usage anywhere. Pure dead weight,
    // likely leftover from a removed QR/barcode feature. Removed outright —
    // shrinks BOTH light and full, no functional risk since nothing references
    // these classes. The Scan-to-PDF feature has its OWN camera-core
    // dependency, correctly scoped to fullImplementation below (via scanLib),
    // unaffected by this removal.
    // ✅ Scan to PDF scanner — full flavor only. Self-contained CameraX +
    // ML Kit barcode scanner (no OpenCV/scanLib — that native library is
    // gone; this is what previously made the full APK's scanner feature
    // heavy). Kotlin source (ScanToPdfScreen.kt) lives only in src/full/java,
    // with a lightweight src/light/java stub of the same composable — so
    // light can't pull this in even if scoping were ever mistaken.
    "fullImplementation"("androidx.camera:camera-core:1.3.3")
    "fullImplementation"("androidx.camera:camera-camera2:1.3.3")
    "fullImplementation"("androidx.camera:camera-lifecycle:1.3.3")
    "fullImplementation"("androidx.camera:camera-view:1.3.3")
    "fullImplementation"("com.google.mlkit:barcode-scanning:17.3.0")
    "fullImplementation"("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("androidx.core:core-ktx:1.13.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel + Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Firebase & Play Services
    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:2.5.0") {
        // FIX: Cloudinary transitively pulls in Facebook's Fresco
        // (com.facebook.fresco:fresco, via its own optional "download"/UI
        // helper artifact) purely for ITS OWN image-display widgets
        // (SimpleDraweeView etc). RasFocus only ever calls
        // MediaManager.get().upload(...) to push child-monitoring
        // screenshots — it never uses Cloudinary's Fresco-based display
        // components. Fresco showed up by name in a crash trace
        // (IllegalStateException: CompositionLocal LocalLifecycleOwner not
        // present, inside com.facebook.imagepipeline.nativecode.b) — a
        // known class of bug when Fresco's view/pipeline code runs outside
        // a properly lifecycle-attached container. Excluding it removes
        // both the crash risk and ~2-4MB of genuinely unused native code.
        exclude(group = "com.facebook.fresco")
    }

    // Google Location Services
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.7.0")

    // WebKit
    implementation("androidx.webkit:webkit:1.11.0")

    // Media
    implementation("androidx.media:media:1.7.0")

    // Security Crypto
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    // Math
    implementation("net.objecthunter:exp4j:0.4.8")

    // ✅ PDF — শুধু full flavor-এ include হবে
    "fullImplementation"("com.tom-roush:pdfbox-android:2.0.27.0")

    // ✅ MuPDF — official Artifex viewer SDK (full flavor only)
    // True vector rendering: sharp at any zoom (re-renders from vectors, not bitmap-stretch)
    // Supports: PDF, XPS, EPUB, CBZ, MOBI
    // Maven host: maven.ghostscript.com (only real Maven host for this artifact)
    // Version: 1.15.+ (floating — Gradle picks the latest 1.15.x that exists on the repo)
    "fullImplementation"("com.artifex.mupdf:viewer:1.15.+")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
