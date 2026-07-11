// Top-level build file — PdfViewer MuPDF
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
    // FIX: app/build.gradle.kts applies this plugin but its version was never
    // declared anywhere — "Plugin ... was not found" is Gradle's plugins-DSL
    // way of saying "I don't know what version to use". Version matched to
    // what the sibling RasFocus-final project already uses.
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.devtools.ksp") version "2.1.10-1.0.31" apply false
}
