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
        // pdfium-android (barteksc) is hosted on jitpack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PdfViewerMuPDF"
include(":app")
