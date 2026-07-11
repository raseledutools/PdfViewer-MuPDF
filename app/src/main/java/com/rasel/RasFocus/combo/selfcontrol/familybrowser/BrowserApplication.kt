package com.rasel.pdfviewer.combo.selfcontrol.familybrowser

import android.app.Application
import android.webkit.WebView
import com.rasel.pdfviewer.BuildConfig

/**
 * BrowserApplication.kt
 * Application class — initializes global state once at startup.
 */
class BrowserApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable WebView debugging in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
