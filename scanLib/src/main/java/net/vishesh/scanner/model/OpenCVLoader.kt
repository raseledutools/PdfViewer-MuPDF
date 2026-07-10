package net.vishesh.scanner.model

import android.content.Context
import android.util.Log
import java.lang.ref.WeakReference

enum class OpenCvStatus {
    LOADED, ERROR
}

// FIX: original implementation used the deprecated "OpenCV Manager" runtime
// pattern (org.opencv.android.OpenCVLoader.initAsync + BaseLoaderCallback),
// which required a separate "OpenCV Manager" APK to be installed on the
// device — that app is no longer reliably distributed on the Play Store and
// this pattern is unsupported on modern Android versions.
//
// Since OpenCV 4.9.0, the org.opencv:opencv Maven Central AAR statically
// bundles the native library INSIDE this app — no external Manager app,
// no async callback needed. OpenCVLoader.initLocal() loads it synchronously
// in one call.
class OpenCVLoader(context: Context) {
    private val reference = WeakReference(context)

    fun load(callback: (OpenCvStatus) -> Unit) {
        val loaded = try {
            org.opencv.android.OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            Log.e("RasFocusScanLib", "OpenCV native load failed: ${e.message}")
            false
        }
        callback(if (loaded) OpenCvStatus.LOADED else OpenCvStatus.ERROR)
    }
}
