package com.rasel.RasFocus

import android.app.Application
import android.content.ComponentCallbacks2

/**
 * FIX: Custom Application class ensures DataManager.init() is called
 * before any Service (AccessibilityService, BootReceiver, etc.) runs,
 * preventing UninitializedPropertyAccessException crashes.
 */
class RasFocusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DataManager.init(this)
        // FIX: FirebaseKeywordSync আগে শুধু extrem_block accessibility service চালু
        // হলে init হতো — কিন্তু RasBrowser/YouTube-in-app/Facebook-in-app এর adult
        // keyword scanner এখন সরাসরি এই cache থেকে পড়ে, তাই accessibility service
        // চালু আছে কিনা তার উপর নির্ভর না করে app process শুরু হওয়ার সাথে সাথেই
        // (internet থাকলে) Firebase থেকে keyword/domain list load করে ফেলি।
        com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.init(this)
        
        // Setup background auto-updater
        com.rasel.RasFocus.AutoUpdater.setupBackgroundAutoUpdate(this)
    }

    // ══════════════════════════════════════════════════════════════════
    // ★ Fix: Itel/low-RAM device (Android 10, ~2-3GB) এ multiple floating
    // WebView (YouTube + Facebook + RasBrowser) একসাথে চললে RAM চাপে
    // Android পুরো process kill করে দেয় — recent apps থেকে ফিরলে তাই
    // notun cold-start হয়, আগের state/screen থাকে না।
    //
    // onTrimMemory system memory-pressure warning দেয় process পুরো kill
    // হওয়ার আগে। সেই warning এ background floating WebView গুলোর memory
    // cache হালকা করে দেই (destroy না করে) — যাতে overall RAM চাপ কমে
    // এবং পুরো process বাঁচার সুযোগ বাড়ে।
    // ══════════════════════════════════════════════════════════════════
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            try {
                com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                    .trimMemoryIfBackground()
            } catch (_: Exception) { }
            try {
                com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService
                    .trimMemoryIfBackground()
            } catch (_: Exception) { }
        }
    }
}
