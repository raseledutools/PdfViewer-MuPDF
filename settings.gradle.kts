pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // FIX: this is the ONLY real Maven host for com.artifex.mupdf:viewer —
        // verified against Artifex's own official build.gradle
        // (github.com/ArtifexSoftware/mupdf-android-viewer/blob/master/build.gradle).
        // jitpack.io never hosted this artifact (401 = repo exists but doesn't
        // have it, not a permissions issue you can fix) and
        // artifacts.mupdf.com doesn't even resolve via DNS — it isn't a real
        // domain. Both removed.
        maven { url = uri("https://maven.ghostscript.com") }
    }
}

rootProject.name = "PdfViewerMuPDF"
include(":app")
