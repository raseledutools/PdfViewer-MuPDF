// ============================================================
// RasFocusSettingsComplete.kt
// ONE FILE — Full Settings UI (Jetpack Compose) + All Blocking Logic
//
// SECTIONS:
//  A. Data / Prefs
//  B. Blocking Service (Accessibility)
//  C. All Blocking Handlers (inline)
//  D. Settings UI (Compose)
// ============================================================

package com.rasel.RasFocus.selfcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.concurrent.TimeUnit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.provider.Settings
import android.app.Activity
import android.content.Intent
import com.rasel.RasFocus.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning


// ════════════════════════════════════════════════════════════
// A. PREFS — সব toggle এর state এখানে
// ════════════════════════════════════════════════════════════

class BlockerPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)

    // ── Content Blocking ──
    var blockAdult: Boolean
        get() = prefs.getBoolean("adult", true)
        set(v) = prefs.edit().putBoolean("adult", v).apply()

    var blockAdultSiteList: Boolean
        get() = prefs.getBoolean("adult_site_list", false)
        set(v) = prefs.edit().putBoolean("adult_site_list", v).apply()

    // ── Adult Block Sub-Toggles (৫টি মেইন টগল) ──
    var blockInstantKeyboard: Boolean
        get() = prefs.getBoolean("instant_keyboard", true)
        set(v) = prefs.edit().putBoolean("instant_keyboard", v).apply()

    var blockNormalLoading: Boolean
        get() = prefs.getBoolean("normal_loading", true)
        set(v) = prefs.edit().putBoolean("normal_loading", v).apply()

    var blockAdultImageWeb: Boolean
        get() = prefs.getBoolean("adult_image_web", true)
        set(v) = prefs.edit().putBoolean("adult_image_web", v).apply()

    var blockRomanticKeyword: Boolean
        get() = prefs.getBoolean("romantic_keyword", false)
        set(v) = prefs.edit().putBoolean("romantic_keyword", v).apply()

    var blockAnyAppTyping: Boolean
        get() = prefs.getBoolean("any_app_typing", true)
        set(v) = prefs.edit().putBoolean("any_app_typing", v).apply()

    // Legacy aliases (backward compatibility)
    val blockRomanticKeywords: Boolean get() = blockRomanticKeyword
    val blockAdultKeywordAnyApp: Boolean get() = blockAnyAppTyping

    var blockSearch: Boolean
        get() = prefs.getBoolean("search", false)
        set(v) = prefs.edit().putBoolean("search", v).apply()

    var blockReels: Boolean
        get() = prefs.getBoolean("reels", false)
        set(v) = prefs.edit().putBoolean("reels", v).apply()

    var blockInstaSearch: Boolean
        get() = prefs.getBoolean("insta_search", false)
        set(v) = prefs.edit().putBoolean("insta_search", v).apply()

    var blockYtShorts: Boolean
        get() = prefs.getBoolean("yt_shorts", true)
        set(v) = prefs.edit().putBoolean("yt_shorts", v).apply()

    var blockWaChannels: Boolean
        get() = prefs.getBoolean("wa_channels", false)
        set(v) = prefs.edit().putBoolean("wa_channels", v).apply()

    var blockInstaStories: Boolean
        get() = prefs.getBoolean("insta_stories", false)
        set(v) = prefs.edit().putBoolean("insta_stories", v).apply()

    var blockWaStatus: Boolean
        get() = prefs.getBoolean("wa_status", false)
        set(v) = prefs.edit().putBoolean("wa_status", v).apply()

    var blockWaBusinessStatus: Boolean
        get() = prefs.getBoolean("wa_biz_status", false)
        set(v) = prefs.edit().putBoolean("wa_biz_status", v).apply()

    var blockWaBusinessChannels: Boolean
        get() = prefs.getBoolean("wa_biz_channels", false)
        set(v) = prefs.edit().putBoolean("wa_biz_channels", v).apply()

    var blockSnapSpotlight: Boolean
        get() = prefs.getBoolean("snap_spotlight", false)
        set(v) = prefs.edit().putBoolean("snap_spotlight", v).apply()

    var blockSnapStories: Boolean
        get() = prefs.getBoolean("snap_stories", false)
        set(v) = prefs.edit().putBoolean("snap_stories", v).apply()

    var blockTikTok: Boolean
        get() = prefs.getBoolean("tiktok", false)
        set(v) = prefs.edit().putBoolean("tiktok", v).apply()

    var blockTikTokLive: Boolean
        get() = prefs.getBoolean("tiktok_live", false)
        set(v) = prefs.edit().putBoolean("tiktok_live", v).apply()

    var blockUnsupported: Boolean
        get() = prefs.getBoolean("unsupported_browser", false)
        set(v) = prefs.edit().putBoolean("unsupported_browser", v).apply()

    var blockNewApps: Boolean
        get() = prefs.getBoolean("new_apps", false)
        set(v) = prefs.edit().putBoolean("new_apps", v).apply()

    var blockFbVideo: Boolean
        get() = prefs.getBoolean("fb_video", false)
        set(v) = prefs.edit().putBoolean("fb_video", v).apply()

    // পুরো Facebook app block — video/reels না, পুরো app-ই। Messenger এর
    // Facebook icon সহ যেকোনো entry point দিয়ে খুললেও package foreground
    // এ আসা মাত্রই kill হবে (locale/UI-text এর উপর নির্ভর করে না)।
    var blockFacebookApp: Boolean
        get() = prefs.getBoolean("fb_app_full", false)
        set(v) = prefs.edit().putBoolean("fb_app_full", v).apply()

    var uninstallProtection: Boolean
        get() = prefs.getBoolean("uninstall_prot", false)
        set(v) = prefs.edit().putBoolean("uninstall_prot", v).apply()

    var blockAdb: Boolean
        get() = prefs.getBoolean("block_adb", false)
        set(v) = prefs.edit().putBoolean("block_adb", v).apply()

    var blockPowerOff: Boolean
        get() = prefs.getBoolean("block_poweroff", false)
        set(v) = prefs.edit().putBoolean("block_poweroff", v).apply()

    var blockSafeMode: Boolean
        get() = prefs.getBoolean("block_safemode", false)
        set(v) = prefs.edit().putBoolean("block_safemode", v).apply()

    var blockReboot: Boolean
        get() = prefs.getBoolean("block_reboot", false)
        set(v) = prefs.edit().putBoolean("block_reboot", v).apply()

    var blockRecovery: Boolean
        get() = prefs.getBoolean("block_recovery", false)
        set(v) = prefs.edit().putBoolean("block_recovery", v).apply()

    var blockedMessage: String
        get() = prefs.getString("blocked_msg", "This page is blocked.") ?: "This page is blocked."
        set(v) = prefs.edit().putString("blocked_msg", v).apply()

    var blockedCountdown: Int
        get() = prefs.getInt("blocked_countdown", 3)
        set(v) = prefs.edit().putInt("blocked_countdown", v).apply()

    var redirectUrl: String
        get() = prefs.getString("redirect_url", "https://www.google.com") ?: "https://www.google.com"
        set(v) = prefs.edit().putString("redirect_url", v).apply()

    var focusLockMode: String
        get() = prefs.getString("focus_lock_mode", "none") ?: "none"
        set(v) = prefs.edit().putString("focus_lock_mode", v).apply()

    var focusLockActive: Boolean
        get() = prefs.getBoolean("focus_lock_active", false)
        set(v) = prefs.edit().putBoolean("focus_lock_active", v).apply()

    var focusLockEndTime: Long
        get() = prefs.getLong("focus_lock_end_time", 0L)
        set(v) = prefs.edit().putLong("focus_lock_end_time", v).apply()

    var focusLockPassword: String
        get() = prefs.getString("focus_lock_password", "") ?: ""
        set(v) = prefs.edit().putString("focus_lock_password", v).apply()

    var focusLockLongText: String
        get() = prefs.getString("focus_lock_long_text", "") ?: ""
        set(v) = prefs.edit().putString("focus_lock_long_text", v).apply()

    var focusLockLang: String
        get() = prefs.getString("focus_lock_lang", "bn") ?: "bn"
        set(v) = prefs.edit().putString("focus_lock_lang", v).apply()
}


// ════════════════════════════════════════════════════════════
// B. ACCESSIBILITY SERVICE — Main Entry Point
// ════════════════════════════════════════════════════════════

class RasFocusBlockingService : AccessibilityService() {

    companion object {
        var instance: RasFocusBlockingService? = null
    }

    private lateinit var prefs: BlockerPrefs
    private val mainHandler = Handler(Looper.getMainLooper())
    // overlayView & windowManager moved to BlockPage.kt

    // ── Typing-aware gating for "Normal Loading Block" ──
    // টাইপ করার সময় (এবং টাইপ থামার পর ছোট্ট একটা debounce window পর্যন্ত) কোনো
    // loading/URL/keyword scan চলবে না — সার্চ সাবমিট হয়ে loading শুরু হলে তবেই scan হবে।
    private var lastTypingEventTime = 0L
    private val TYPING_DEBOUNCE_MS = 600L

    // ════════════════════════════════════════════════════════
    // OVERLAY — Block কারণ সহ popup দেখায়, তারপর HOME
    // ════════════════════════════════════════════════════════

    private var lastPopupTime = 0L

    private fun blockWithMessage(featureTitle: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastPopupTime > 1500L) {
            lastPopupTime = now
            mainHandler.post { showBlockOverlay(featureTitle, reason) }
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 80L)
        }
    }

    /**
     * EXTREME KILL LOGIC (Deep Think):
     * ১. সবার আগে blocking overlay দেখাও — স্ক্রিন সাথে সাথে ঢেকে যায়।
     * ২. GLOBAL_ACTION_BACK ফায়ার করে current tab/screen destroy করো।
     * ৩. FLAG_ACTIVITY_CLEAR_TASK সহ HOME Intent দিয়ে অ্যাপটি পুরোপুরি kill করো।
     */
    private fun forceKillAppAndGoHome(pkg: String, featureTitle: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastPopupTime > 1000L) {
            lastPopupTime = now

            // ১. সবার আগে blocking overlay দেখাও (Instant Cover)
            mainHandler.post { showBlockOverlay(featureTitle, reason) }

            // ২. Current screen / tab destroy করতে BACK press
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 50L)

            // ৩. Task stack clear করে HOME-এ পাঠাও
            mainHandler.postDelayed({
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                        addCategory(android.content.Intent.CATEGORY_HOME)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, 150L)
        }
    }


    // ── Block UI delegated to BlockPage.kt ──────────────────────────
    private fun showBlockOverlay(featureTitle: String, reason: String) {
        val type = when {
            featureTitle.contains("Short", true)  -> BlockPage.Type.SHORTS
            featureTitle.contains("Reel", true)   -> BlockPage.Type.REELS
            featureTitle.contains("TikTok", true) -> BlockPage.Type.REELS
            featureTitle.contains("Adult", true)  -> BlockPage.Type.ADULT
            featureTitle.contains("Website", true) || featureTitle.contains("Site", true) -> BlockPage.Type.WEBSITE
            featureTitle.contains("PANIC", true)  -> BlockPage.Type.PANIC
            featureTitle.contains("DENIED", true) || featureTitle.contains("LOCK", true) -> BlockPage.Type.SYSTEM
            else -> BlockPage.Type.FOCUS
        }
        BlockPage.show(this, type, featureTitle, reason)
    }

    private fun removeOverlay() { BlockPage.dismiss(this) }


    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = BlockerPrefs(this)

        // ── Firebase remote keyword/domain sync শুরু করো ──
        FirebaseKeywordSync.init(this)

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
        showRunningNotification()
    }

    private fun showRunningNotification() {
        try {
            val channelId = "rasfocus_service_running"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "RasFocus Active",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Extreme Block running"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
            val settingsIntent = android.content.Intent(this, RasFocusSettingsActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = android.app.PendingIntent.getActivity(
                this, 3001, settingsIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, channelId)
                    .setContentTitle("RasFocus Extreme Block চলছে")
                    .setContentText("Blocking active — ট্যাপ করে settings খুলুন")
                    .setSmallIcon(R.drawable.ic_notification_shield)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("RasFocus Extreme Block চলছে")
                    .setContentText("Blocking active")
                    .setSmallIcon(R.drawable.ic_notification_shield)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build()
            }
            nm.notify(3001, notification)
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg0 = event.packageName?.toString() ?: return

        if (pkg0 == "com.rasel.RasFocus" ||
            pkg0 == "com.rasfocus" ||
            pkg0 == "android" ||
            pkg0 == "com.android.systemui" ||
            pkg0 == "com.samsung.android.systemui" ||
            pkg0 == "com.miui.systemui") return

        val isTypingEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        val isPageLoadEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val isContentChangedEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        // ── Typing time track: শুধু actual typing event এ update করব ──
        // PAGE LOAD event (WINDOW_STATE_CHANGED) কে কখনো "typing" হিসেবে count করব না।
        if (isTypingEvent) lastTypingEventTime = System.currentTimeMillis()

        // ── 1. Instant Typing Block — extrem_block এ handle করা হয় না, UnifiedBlockerService এ হয় ──

        // ── Browser এ Page Load / Navigation হলে সাথে সাথে adult check চালাও ──
        // WINDOW_STATE_CHANGED = নতুন page load বা tab navigation — এটা typing নয়।
        // Typing debounce window এ থাকলেও browser এর URL-based block bypass করে চলবে।
        val isBrowserPkg = pkg0 in ALL_BROWSER_PKGS
        if (isBrowserPkg && (isPageLoadEvent || isContentChangedEvent)) {
            if (fastAdultCheck(event, pkg0)) return
        }

        // ── Content Blocking gate ──
        // শুধু actual typing এর সময় loading scan skip হবে।
        // Page load event (WINDOW_STATE_CHANGED / WINDOW_CONTENT_CHANGED) এ skip হবে না।
        val stillTyping = isTypingEvent || (System.currentTimeMillis() - lastTypingEventTime < TYPING_DEBOUNCE_MS)
        val skipLoadingChecks = stillTyping && !isPageLoadEvent  // page load হলে typing debounce ভাঙবে

        // fastAdultCheck: browser-এ page load event এ উপরে চালানো হয়েছে,
        // non-browser বা typing-শেষে অন্য event এর জন্য এখানে চালাও।
        if (!isBrowserPkg && !stillTyping && fastAdultCheck(event, pkg0)) return
        if (isBrowserPkg && !stillTyping && !isPageLoadEvent && !isContentChangedEvent && fastAdultCheck(event, pkg0)) return

        val root = rootInActiveWindow ?: return
        val pkg  = event.packageName?.toString() ?: run { root.recycle(); return }

        try {
            var handled = false

            // ── Content Blocking (only once typing has stopped & page/app starts loading) ──
            if (!skipLoadingChecks) {
                if (!handled) handled = handleAdultContent(root, pkg)
                if (!handled) handleSafeSearch(root, pkg)
                if (!handled) handled = handleWebViewAdultBlock(root, pkg)
            }

            // ── Universal Search Keyword Block ──
            // নিজেই typing/search result detect করে — skipLoadingChecks gate এর বাইরে।
            // Facebook (katana + lite), YouTube, Instagram, Telegram, Reddit ইত্যাদিতে কাজ করবে।
            if (!handled) handled = handleUniversalScreenKeywordBlock(root, pkg)

            if (!handled) handled = handleImageVideoSearch(root, pkg)
            if (!handled) handled = handleYouTubeShorts(root, pkg)
            if (!handled) handled = handleReels(root, pkg)
            if (!handled) handled = handleInstagramSearch(root, pkg)
            if (!handled) handled = handleWhatsAppChannels(root, pkg)
            if (!handled) handled = handleInstagramStories(root, pkg)
            if (!handled) handled = handleSnapchat(root, pkg)
            if (!handled) handled = handleWhatsAppStatus(root, pkg)
            if (!handled) handled = handleWaBusinessBlocking(root, pkg)
            if (!handled) handled = handleTikTok(root, pkg)
            if (!handled && (pkg == "com.facebook.katana" || pkg == "com.facebook.lite")) {
                handled = handleFacebookAppBlock(pkg)
                if (!handled) handled = handleFacebookVideo(root, pkg)
                if (!handled) handled = handleFacebookFeedShortVideo(root, pkg)
            }

            // ── Advanced ──
            if (!handled) handled = handleUnsupportedBrowsers(root, pkg)
            if (!handled) handled = handleUnsupportedWebView(root, pkg)
            if (!handled) handled = handleNewlyInstalledApps(root, pkg)

            // ── Protection ──
            if (!handled) handled = handleUninstallProtection(root, pkg)
            if (!handled) handleRebootProtection(root, pkg)

        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(3001)
        } catch (_: Exception) {}
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val restartIntent = android.content.Intent(this, ServiceRestartReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this, 1001, restartIntent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 3000L,
                pendingIntent
            )
        } catch (_: Exception) {}
        super.onDestroy()
    }

    fun checkCurrentWindow() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: run { root.recycle(); return }
        if (pkg == "com.rasel.RasFocus" || pkg == "android" ||
            pkg == "com.android.systemui" || pkg == "com.samsung.android.systemui") {
            root.recycle(); return
        }
        try {
            var handled = false
            if (!handled) handled = handleAdultContent(root, pkg)
            if (!handled) handled = handleWebViewAdultBlock(root, pkg)
            if (!handled) handled = handleUniversalScreenKeywordBlock(root, pkg)
            if (!handled) handled = handleImageVideoSearch(root, pkg)
            if (!handled) handled = handleYouTubeShorts(root, pkg)
            if (!handled) handled = handleReels(root, pkg)
            if (!handled) handled = handleInstagramSearch(root, pkg)
            if (!handled) handled = handleWhatsAppChannels(root, pkg)
            if (!handled) handled = handleInstagramStories(root, pkg)
            if (!handled) handled = handleSnapchat(root, pkg)
            if (!handled) handled = handleWhatsAppStatus(root, pkg)
            if (!handled) handled = handleWaBusinessBlocking(root, pkg)
            if (!handled) handled = handleTikTok(root, pkg)
            if (!handled) handled = handleUnsupportedBrowsers(root, pkg)
            if (!handled) handled = handleUnsupportedWebView(root, pkg)
            if (!handled) handled = handleNewlyInstalledApps(root, pkg)
            if (!handled) handled = handleUninstallProtection(root, pkg)
            if (!handled) handleRebootProtection(root, pkg)
            if (!handled && (pkg == "com.facebook.katana" || pkg == "com.facebook.lite")) {
                if (!handled) handled = handleFacebookAppBlock(pkg)
                if (!handled) handled = handleFacebookVideo(root, pkg)
                if (!handled) handleFacebookFeedShortVideo(root, pkg)
            }
        } catch (_: Exception) {
        } finally {
            root.recycle()
        }
    }


    // ════════════════════════════════════════════════════════
    // C. ALL BLOCKING HANDLERS
    // ════════════════════════════════════════════════════════

    private val ALL_BROWSER_PKGS = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",          // Samsung Internet
        "org.mozilla.firefox",
        "com.microsoft.emmx",                    // Edge
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.UCMobile.intl",
        "com.yandex.browser",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "mark.via.gp",                           // Via Browser
        "com.duckduckgo.mobile.android",
        "com.mi.globalbrowser",                  // Mi Browser
        "com.hihonor.browser",
        "com.huawei.browser",
        "com.puffin.client.android"
    )

    private val BROWSER_URL_BAR_IDS = mapOf(
        "com.android.chrome"               to listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/omnibox_text", "com.android.chrome:id/location_bar_edit_text"),
        "com.sec.android.app.sbrowser"     to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text", "com.sec.android.app.sbrowser:id/sb_urlbar_input"),
        "org.mozilla.firefox"              to listOf("org.mozilla.firefox:id/url_bar_title", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/url_edit_text"),
        "com.microsoft.emmx"               to listOf("com.microsoft.emmx:id/address_bar_edit_text", "com.microsoft.emmx:id/url_bar"),
        "com.brave.browser"                to listOf("com.brave.browser:id/url_bar", "com.brave.browser:id/omnibox_text"),
        "com.opera.browser"                to listOf("com.opera.browser:id/url_field"),
        "com.opera.mini.native"            to listOf("com.opera.mini.native:id/url_field"),
        "com.UCMobile.intl"                to listOf("com.UCMobile.intl:id/webview_tab_editurl"),
        "com.yandex.browser"               to listOf("com.yandex.browser:id/bro_urlbar_url"),
        "com.kiwibrowser.browser"          to listOf("com.kiwibrowser.browser:id/url_bar", "com.kiwibrowser.browser:id/omnibox_text"),
        "com.vivaldi.browser"              to listOf("com.vivaldi.browser:id/url_bar", "com.vivaldi.browser:id/omnibox_text"),
        "mark.via.gp"                      to listOf("mark.via.gp:id/cv"),
        "com.duckduckgo.mobile.android"    to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
        "com.mi.globalbrowser"             to listOf("com.mi.globalbrowser:id/url_address"),
        "com.hihonor.browser"              to listOf("com.hihonor.browser:id/url_edit_text"),
        "com.huawei.browser"               to listOf("com.huawei.browser:id/url_edit_text"),
        "com.puffin.client.android"        to listOf("com.puffin.client.android:id/url_edit_text")
    )

    private fun extractBrowserUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val ids = BROWSER_URL_BAR_IDS[pkg]
        if (ids != null) {
            for (id in ids) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val t = nodes[0].text?.toString()?.lowercase()
                        ?: nodes[0].contentDescription?.toString()?.lowercase()
                    if (!t.isNullOrBlank()) return t
                }
            }
        }
        return findNodeWithUrl(root)
    }

    private fun extractChromeUrl(root: AccessibilityNodeInfo): String? =
        extractBrowserUrl(root, "com.android.chrome")

    private fun findNodeWithUrl(node: AccessibilityNodeInfo): String? {
        val t = node.text?.toString()?.lowercase()?.trim() ?: ""
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.")) return t
        if (t.contains(".com/") || t.contains(".org/") || t.contains(".net/") ||
            t.contains(".xxx") || t.contains(".sex") || t.contains(".adult")) return t
        if (t.length >= 8 && !t.contains(" ") &&
            (t.endsWith(".com") || t.endsWith(".org") || t.endsWith(".net") ||
             t.endsWith(".xxx") || t.endsWith(".sex"))) return t
        for (i in 0 until node.childCount) {
            val r = findNodeWithUrl(node.getChild(i) ?: continue)
            if (r != null) return r
        }
        return null
    }

    private fun isTabActive(root: AccessibilityNodeInfo, name: String) =
        root.findAccessibilityNodeInfosByText(name)
            .any { it.isSelected || it.isChecked || it.isFocused }

    // ── Allowed browsers (always pass-through): Chrome, Samsung Internet, Edge ──
    // বাকি সব unsupported toggle ON থাকলে block হবে
    private val ALLOWED_BROWSERS = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",   // Samsung Internet
        "com.microsoft.emmx"              // Edge
    )

    private val BLOCKED_BROWSERS = setOf(
        "com.opera.browser", "com.opera.mini.native", "com.UCMobile.intl",
        "org.mozilla.firefox", "com.brave.browser",
        "com.duckduckgo.mobile.android", "com.yandex.browser",
        "com.kiwibrowser.browser", "com.vivaldi.browser", "mark.via.gp",
        "com.puffin.client.android", "com.jawal.browser",
        "com.mi.globalbrowser", "com.hihonor.browser", "com.huawei.browser"
    )
    private val BLOCKED_BROWSER_NAMES = listOf(
        "Opera", "Opera Mini", "UC Browser", "Firefox", "Brave",
        "DuckDuckGo", "Yandex Browser", "Kiwi", "Vivaldi",
        "Via Browser", "Puffin", "Mi Browser"
        // "Edge" removed — Edge is allowed
        // "Samsung Internet" removed — Samsung browser is allowed
    )

    private val rasFocusPackage = "com.rasfocus"
    private val rasFocusName = "RasFocus"

    private var _adultDomainList: List<String>? = null
    private fun getAdultDomainList(): List<String> {
        if (_adultDomainList == null) {
            _adultDomainList = try {
                assets.open("adultsite.txt")
                    .bufferedReader()
                    .readLines()
                    .map { it.trim().trimStart('*', '.').lowercase() }
                    .filter { it.isNotEmpty() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        return _adultDomainList!!
    }

    // ── HARDCODED KEYWORDS REMOVED — সব কিছু Firebase Realtime DB থেকে আসে ──
    // FirebaseKeywordSync.getAdultKeywords() → adult_keywords
    // FirebaseKeywordSync.getAdultDomains()  → adult_domains
    // romantic keywords ও Firebase-এ adult_keywords এর অংশ হিসেবে রাখো

    // ── ROMANTIC KEYWORDS REMOVED — Firebase-এ adult_keywords node-এ রাখো ──

    private fun activeExtremeKeywords(): List<String> {
        // সম্পূর্ণ Firebase-only — কোনো hardcoded keyword নেই
        // FirebaseKeywordSync.init() service start-এ call হয়ে যায়,
        // তাই এখানে শুধু in-memory set পড়লেই হয়
        if (!prefs.blockNormalLoading) return emptyList()
        return FirebaseKeywordSync.getAdultKeywords().toList()
    }

    // ── HARDCODED ADULT DOMAINS REMOVED — Firebase-এ adult_domains node-এ রাখো ──
    // রানটাইমে FirebaseKeywordSync.getAdultDomains() থেকে আসবে

    private fun fastAdultCheck(event: AccessibilityEvent, pkg: String): Boolean {
        if (pkg !in ALL_BROWSER_PKGS) return false
        if (!prefs.blockNormalLoading && !prefs.blockAdultSiteList) return false
        // typing event এ block করব না — শুধু page load/navigation এ URL check
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return false

        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        if (root != null) {
            val urlFromRoot = extractBrowserUrl(root, pkg)?.lowercase()?.trim() ?: ""
            root.recycle()
            if (urlFromRoot.isNotBlank()) {
                if (prefs.blockNormalLoading && (urlFromRoot.contains("safe=off") || urlFromRoot.contains("safe=images"))) {
                    blockAdultInBrowser(pkg)
                    return true
                }
                if (isAdultUrl(urlFromRoot)) {
                    blockAdultInBrowser(pkg)
                    return true
                }
            }
        }
        return false
    }

    private fun blockAdultInBrowser(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastPopupTime > 1200L) {
            lastPopupTime = now
            mainHandler.post { showBlockOverlay("Adult Content", "This page contains adult content and has been blocked.") }
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 120)
            mainHandler.postDelayed({ closeBrowserTab(pkg) }, 600)
            mainHandler.postDelayed({
                try {
                    val currentPkg = rootInActiveWindow?.packageName?.toString() ?: ""
                    if (currentPkg in ALL_BROWSER_PKGS) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                } catch (_: Exception) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, 2500)
        }
    }

    private fun closeBrowserTab(pkg: String) {
        try {
            val root2 = rootInActiveWindow ?: return
            val currentPkg = root2.packageName?.toString() ?: run { root2.recycle(); return }

            val closeTabIds = when (pkg) {
                "com.android.chrome" -> listOf(
                    "com.android.chrome:id/tab_close_button", "com.android.chrome:id/close_button", "com.android.chrome:id/action_close_tab"
                )
                "com.sec.android.app.sbrowser" -> listOf("com.sec.android.app.sbrowser:id/close_button", "com.sec.android.app.sbrowser:id/tab_close_btn")
                "com.brave.browser" -> listOf("com.brave.browser:id/tab_close_button", "com.brave.browser:id/close_button")
                "com.microsoft.emmx" -> listOf("com.microsoft.emmx:id/close_tab_button", "com.microsoft.emmx:id/tab_close_button")
                "com.opera.browser" -> listOf("com.opera.browser:id/close_tab_button")
                "com.kiwibrowser.browser" -> listOf("com.kiwibrowser.browser:id/tab_close_button", "com.kiwibrowser.browser:id/close_button")
                "com.vivaldi.browser" -> listOf("com.vivaldi.browser:id/tab_close_button", "com.vivaldi.browser:id/close_button")
                else -> emptyList()
            }

            if (currentPkg == pkg) {
                for (id in closeTabIds) {
                    val nodes = root2.findAccessibilityNodeInfosByViewId(id)
                    val closeBtn = nodes.firstOrNull { it.isVisibleToUser && it.isClickable }
                    if (closeBtn != null) {
                        closeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        root2.recycle()
                        return
                    }
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                } catch (_: Exception) {}
            }
            root2.recycle()
        } catch (_: Exception) {}
    }

    private fun isAdultUrl(text: String): Boolean {
        // ── Keyword check — সব Firebase থেকে আসে ──
        if (prefs.blockNormalLoading || prefs.blockRomanticKeywords || prefs.blockAdultKeywordAnyApp) {
            if (activeExtremeKeywords().any { text.contains(it) }) return true
        }
        val host = try {
            val raw = if (text.startsWith("http")) text.trim() else "https://${text.trim()}"
            android.net.Uri.parse(raw).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (e: Exception) { "" }
        if (host.isNotEmpty()) {
            // ── Firebase remote domains check ──
            if (prefs.blockNormalLoading) {
                val remoteDomains = FirebaseKeywordSync.getAdultDomains()
                if (remoteDomains.contains(host)) return true
                if (remoteDomains.any { host.endsWith(".$it") }) return true
            }
            // ── Assets adultsite.txt — website list (এটা থাকবে) ──
            if (prefs.blockNormalLoading || prefs.blockAdultSiteList) {
                val domainList = getAdultDomainList()
                if (domainList.any { host == it || host.endsWith(".$it") }) return true
            }
        }

        // ── Firebase full remote check (keywords + domains + TLDs) ──
        if (prefs.blockAdultImageWeb && FirebaseKeywordSync.isBlockedByRemote(text, host)) return true

        return false
    }

    private fun collectAllText(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            n ?: return
            n.text?.let { sb.append(it).append(" ") }
            n.contentDescription?.let { sb.append(it).append(" ") }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(node)
        return sb.toString()
    }

    private fun handleAdultContent(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg !in ALL_BROWSER_PKGS) return false
        if (!prefs.blockNormalLoading && !prefs.blockAdultSiteList) return false
        // শুধু search/go button press করার পর navigation event-এ URL চেক হবে।
        val urlBarFocused = (BROWSER_URL_BAR_IDS[pkg] ?: emptyList()).any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isFocused || it.isAccessibilityFocused }
        }
        if (urlBarFocused) return false

        val url = extractBrowserUrl(root, pkg)?.lowercase()?.trim() ?: ""
        val titleText = extractBrowserPageTitle(root, pkg)?.lowercase()?.trim() ?: ""

        if (url.isNotBlank() && isAdultUrl(url)) {
            blockAdultInBrowser(pkg)
            return true
        }
        if (titleText.isNotBlank() && isAdultUrl(titleText)) {
            blockAdultInBrowser(pkg)
            return true
        }

        // Deep text scan: শুধু Google/Bing/DuckDuckGo/Yandex search result page-এ করব।
        // Browser home page, news site, social media redirect ইত্যাদিতে false positive এড়াতে।
        // CRITICAL: Google homepage (google.com/) এবং google.com/webhp ইত্যাদি exclude করতে
        // হবে — এগুলো search result page নয়। শুধু ?q= বা &q= থাকলে search result।
        if (prefs.blockNormalLoading) {
            val isSearchResultPage = (url.contains("google.") && url.contains("/search") && (url.contains("?q=") || url.contains("&q="))) ||
                (url.contains("bing.com/search") && (url.contains("?q=") || url.contains("&q="))) ||
                (url.contains("search.yahoo.com") && url.contains("?p=")) ||
                url.contains("duckduckgo.com/?q=") ||
                (url.contains("yandex.com/search") && url.contains("text=")) ||
                (url.contains("yandex.ru/search") && url.contains("text="))
            if (isSearchResultPage) {
                // শুধু URL এবং title scan করব, পুরো page DOM নয় — false positive কমাতে
                val urlAndTitle = "$url $titleText".trim()
                if (urlAndTitle.isNotBlank() && isAdultUrl(urlAndTitle)) {
                    blockAdultInBrowser(pkg)
                    return true
                }
            }
        }

        return false
    }

    private fun extractBrowserPageTitle(root: AccessibilityNodeInfo, pkg: String): String? {
        val titleIds = mapOf(
            "com.android.chrome"            to listOf("com.android.chrome:id/title_bar", "com.android.chrome:id/tab_title", "com.android.chrome:id/url_bar"),
            "com.sec.android.app.sbrowser"  to listOf("com.sec.android.app.sbrowser:id/title", "com.sec.android.app.sbrowser:id/url_text"),
            "org.mozilla.firefox"           to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_title_view"),
            "com.microsoft.emmx"            to listOf("com.microsoft.emmx:id/title"),
            "com.brave.browser"             to listOf("com.brave.browser:id/title_bar", "com.brave.browser:id/tab_title"),
            "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
            "com.opera.browser"             to listOf("com.opera.browser:id/title_bar"),
            "com.yandex.browser"            to listOf("com.yandex.browser:id/bro_urlbar_title")
        )
        val ids = titleIds[pkg] ?: return null
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val t = nodes[0].text?.toString() ?: nodes[0].contentDescription?.toString()
                if (!t.isNullOrBlank()) return t
            }
        }
        return null
    }

    // ── 1b-EXTRA. Universal Screen Adult Keyword Block ─────────
    private var lastScreenScanBlockTime = 0L
    private val ADULT_SCAN_TARGET_PKGS = setOf(
        "org.telegram.messenger", "org.telegram.messenger.web",
        "com.facebook.katana", "com.facebook.lite",
        "com.google.android.youtube", "com.instagram.android",
        "com.twitter.android", "com.x.android",
        "com.reddit.frontpage", "com.reddit.redditisfun",
        "com.snapchat.android", "com.pinterest", "com.linkedin.android"
    )
    private val ANY_APP_SCAN_TARGET_PKGS = ADULT_SCAN_TARGET_PKGS + setOf("com.whatsapp", "com.whatsapp.w4b")

    private fun handleUniversalScreenKeywordBlock(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockNormalLoading) return false
        if (pkg in ALL_BROWSER_PKGS) return false  // browser-এ handleAdultContent দেখে

        val scanSet = ADULT_SCAN_TARGET_PKGS
        if (pkg !in scanSet) return false

        val now = System.currentTimeMillis()
        if (now - lastScreenScanBlockTime < 2000L) return false

        val activeKeywords = activeExtremeKeywords()

        // ══════════════════════════════════════════════════════════
        // ── YouTube: শুধু search submit হওয়ার পর block ──
        // home/feed page বা typing-এর সময় কোনো block হবে না।
        // শুধু search query (URL বা result page title) check হবে।
        // ══════════════════════════════════════════════════════════
        if (pkg == "com.google.android.youtube") {
            val ytSearchBarIds = listOf(
                "com.google.android.youtube:id/search_edit_text",
                "com.google.android.youtube:id/search_bar_text",
                "com.google.android.youtube:id/search_bar_input"
            )
            // Search bar focused = user এখনও টাইপ করছে → scan করব না
            val searchBarFocused = ytSearchBarIds.any { id ->
                root.findAccessibilityNodeInfosByViewId(id).any { it.isFocused || it.isAccessibilityFocused }
            }
            if (searchBarFocused) return false

            // ── Search result page কিনা check ──
            // YouTube search result URL: vnd.youtube://results?search_query=...
            // Accessibility tree-তে search_results_container বা চলমান title দেখে বুঝব
            val isSearchResultPage =
                root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/results").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_results_container").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/channel_list_item").isNotEmpty()

            if (!isSearchResultPage) return false  // search result page নয় → home/feed → block করব না

            // ── Search query text নিয়ে keyword check ──
            val searchQueryText = ytSearchBarIds.mapNotNull { id ->
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.text?.toString()
            }.firstOrNull()?.lowercase()?.trim() ?: ""

            // Result page title থেকেও নিই (e.g. "porn - YouTube")
            val resultTitleText = (
                root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title").orEmpty() +
                root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/results_title").orEmpty()
            ).mapNotNull { it.text?.toString() }.joinToString(" ").lowercase()

            val combined = "$searchQueryText $resultTitleText".trim()
            if (combined.isBlank()) return false

            if (activeKeywords.any { combined.contains(it) }) {
                lastScreenScanBlockTime = now
                forceKillAppAndGoHome(pkg, "Content Blocked", "Blocked keyword detected in YouTube search.")
                return true
            }
            return false
        }

        // ══════════════════════════════════════════════════════════
        // ── Facebook / Facebook Lite: search submit-এর পর block ──
        // Search bar focused বা home feed থাকলে block হবে না।
        // ══════════════════════════════════════════════════════════
        if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite") {
            val fbSearchBarIds = listOf(
                "com.facebook.katana:id/search_box_input",
                "com.facebook.katana:id/global_search_edittext",
                "com.facebook.lite:id/search_box_input"
            )
            val fbSearchFocused = fbSearchBarIds.any { id ->
                root.findAccessibilityNodeInfosByViewId(id).any { it.isFocused || it.isAccessibilityFocused }
            }
            if (fbSearchFocused) return false  // typing চলছে → block না

            // ── Facebook search result page কিনা ──
            val isFbSearchPage =
                root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/search_results_list").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/search_results_recyclerview").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/unified_search_results").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.facebook.lite:id/search_results_list").isNotEmpty()

            if (!isFbSearchPage) return false  // home page বা অন্য কোথাও → block না

            // ── Search bar-এর current text (submitted query) ──
            val fbQueryText = fbSearchBarIds.mapNotNull { id ->
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.text?.toString()
            }.firstOrNull()?.lowercase()?.trim() ?: ""

            if (fbQueryText.isBlank()) return false  // কোনো query নেই → skip

            if (activeKeywords.any { fbQueryText.contains(it) }) {
                lastScreenScanBlockTime = now
                forceKillAppAndGoHome(pkg, "Content Blocked", "Blocked keyword detected in Facebook search.")
                return true
            }
            return false
        }

        // ══════════════════════════════════════════════════════════
        // ── অন্য apps (Telegram, Instagram, Reddit, Twitter, etc.) ──
        // Input field focused থাকলে scan করব না।
        // শুধু navigated/loaded screen-এ keyword দেখব।
        // CRITICAL: App-এর home/feed screen-এ block হবে না।
        // শুধু search result বা explicit content page-এ block হবে।
        // ══════════════════════════════════════════════════════════

        // ── Instagram: শুধু Search/Explore result page-এ block ──
        if (pkg == "com.instagram.android") {
            val isSearchResultPage =
                root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/search_results_list").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tag_result_list").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/row_hashtag_container").isNotEmpty()
            if (!isSearchResultPage) return false  // home feed / profile → block না

            val searchText = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/action_bar_search_edit_text")
                .firstOrNull()?.text?.toString()?.lowercase()?.trim() ?: ""
            if (searchText.isBlank()) return false

            if (activeKeywords.any { searchText.contains(it) }) {
                lastScreenScanBlockTime = now
                forceKillAppAndGoHome(pkg, "Content Blocked", "Blocked keyword detected in Instagram search.")
                return true
            }
            return false
        }
        val anyInputFocused = run {
            fun hasFocusedInput(node: AccessibilityNodeInfo?): Boolean {
                node ?: return false
                val cn = node.className?.toString() ?: ""
                if ((cn.contains("EditText") || cn.contains("SearchView")) &&
                    (node.isFocused || node.isAccessibilityFocused)) return true
                for (i in 0 until node.childCount) {
                    if (hasFocusedInput(node.getChild(i))) return true
                }
                return false
            }
            hasFocusedInput(root)
        }
        if (anyInputFocused) return false

        // ── CRITICAL: General apps (Telegram, Reddit, Twitter) ──
        // Home/feed screen-এ title scan করব না — false positive হয়।
        // শুধু search result বা channel/subreddit page-এ scan করব।
        // Search result page detect: কোনো search result container visible?
        val isSearchOrContentPage = run {
            val ids = listOf(
                "org.telegram.messenger:id/search_field",
                "com.reddit.frontpage:id/search_results",
                "com.twitter.android:id/search_result_list",
                "com.x.android:id/search_result_list",
                "com.pinterest:id/search_results_recycler",
                "com.linkedin.android:id/search_results_list"
            )
            // যদি কোনো search result container থাকে তাহলেই scan করব
            ids.any { id -> root.findAccessibilityNodeInfosByViewId(id).isNotEmpty() }
        }
        if (!isSearchOrContentPage) return false  // home feed → block না

        // ── শুধু URL/title/page heading দেখব, পুরো screen dump না ──
        // collectAllText দিয়ে home feed scan করলে false positive হয়।
        // তাই শুধু visible title/heading nodes চেক করব।
        val titleText = run {
            val titleNodes = mutableListOf<AccessibilityNodeInfo>()
            fun collectTitles(node: AccessibilityNodeInfo?) {
                node ?: return
                val cn = node.className?.toString() ?: ""
                val rid = node.viewIdResourceName ?: ""
                if (rid.contains("title") || rid.contains("heading") ||
                    rid.contains("toolbar") || rid.contains("action_bar") ||
                    cn.contains("Toolbar") || cn.contains("ActionBar")) {
                    titleNodes.add(node)
                }
                for (i in 0 until node.childCount) collectTitles(node.getChild(i))
            }
            collectTitles(root)
            titleNodes.mapNotNull { it.text?.toString() }.joinToString(" ").lowercase()
        }

        if (titleText.isBlank()) return false

        val match = activeKeywords.firstOrNull { titleText.contains(it) } ?: return false

        lastScreenScanBlockTime = now
        forceKillAppAndGoHome(pkg, "Content Blocked", "Blocked keyword detected on screen.")
        return true
    }

    private val safeSearchLastRedirect = HashMap<String, Long>()
    private fun handleSafeSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockNormalLoading) return false
        if (pkg !in ALL_BROWSER_PKGS) return false

        val url = extractBrowserUrl(root, pkg)?.lowercase() ?: return false
        if (!url.contains("google.") || !url.contains("/search")) return false

        if (url.contains("safe=off") || url.contains("safe=images")) {
            blockAdultInBrowser(pkg)
            return true
        }
        if (url.contains("safe=active") || url.contains("safe=strict")) return false

        val now = System.currentTimeMillis()
        if ((now - (safeSearchLastRedirect[pkg] ?: 0L)) < 1500L) return false
        safeSearchLastRedirect[pkg] = now

        val safeUrl = url + (if (url.contains("?")) "&" else "?") + "safe=active"
        mainHandler.postDelayed({
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(safeUrl)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    setPackage(pkg)
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }, 300)
        return false
    }

    private fun handleWebViewAdultBlock(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg.startsWith("com.android.") || pkg == "android") return false

        // Allowed browsers (Chrome, Samsung, Edge) — handleAdultContent দেখে, এখানে skip
        if (pkg in ALLOWED_BROWSERS) return false

        // Blocked browsers: unsupported toggle ON থাকলে WebView-ও block
        val isBlockedBrowser = pkg in BLOCKED_BROWSERS
        if (isBlockedBrowser) {
            if (prefs.blockUnsupported) {
                forceKillAppAndGoHome(pkg, "Unsupported Browser", "এই browser ব্লক করা আছে।")
                return true
            }
            // Screen Scan OFF থাকলে WebView adult check skip
            if (!prefs.blockAdultImageWeb) return false
        } else {
            // Normal app WebView — Screen Scan Block (বাটন ২) দিয়ে guard
            if (!prefs.blockAdultImageWeb) return false
        }

        if (!containsWebViewNode(root)) return false

        val urlText = findWebViewUrl(root)?.lowercase()?.trim() ?: findNodeWithUrl(root)?.lowercase()?.trim() ?: ""
        if (urlText.isNotBlank() && isAdultUrl(urlText)) {
            forceKillAppAndGoHome(pkg, "Adult Content", "This page contains adult content and has been blocked.")
            return true
        }
        return false
    }

    private fun findWebViewUrl(node: AccessibilityNodeInfo?): String? {
        node ?: return null
        val cn = node.className?.toString() ?: ""
        if (cn.contains("WebView") || cn.contains("XWalkView")) {
            val cd = node.contentDescription?.toString() ?: ""
            if (cd.startsWith("http") || cd.startsWith("www.")) return cd
            val t = node.text?.toString() ?: ""
            if (t.startsWith("http") || t.startsWith("www.")) return t
        }
        for (i in 0 until node.childCount) {
            val r = findWebViewUrl(node.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun containsWebViewNode(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        val cn = node.className?.toString() ?: ""
        if (cn.contains("WebView") || cn.contains("XWalkView")) return true
        for (i in 0 until node.childCount) {
            if (containsWebViewNode(node.getChild(i))) return true
        }
        return false
    }

    private fun handleImageVideoSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockAdultImageWeb) return false
        if (pkg != "com.android.chrome") return false
        val url = extractChromeUrl(root) ?: ""
        val urlBlocked = url.contains("tbm=isch") || url.contains("tbm=vid") || url.contains("google.com/images") || url.contains("google.com/videohp")
        val tabBlocked = url.isEmpty() && (isTabActive(root, "Images") || isTabActive(root, "Videos"))
        if (urlBlocked || tabBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }
        return false
    }

    private fun handleYouTubeShorts(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockYtShorts) return false
        if (pkg != "com.google.android.youtube") return false
        val tab = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar_item_label")
            .any { it.text?.toString()?.equals("Shorts", true) == true && (it.isSelected || it.parent?.isSelected == true) }
        if (tab) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        val player = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/shorts_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page").isNotEmpty()
        if (player) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        val via = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/watch_while_layout").isNotEmpty()
            && root.findAccessibilityNodeInfosByText("Shorts").any { it.isSelected }
        if (via) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        val fb = root.findAccessibilityNodeInfosByText("Shorts").any { it.isSelected || it.isChecked || it.parent?.isSelected == true }
        if (fb) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        return false
    }

    private fun handleReels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockReels) return false
        when (pkg) {
            "com.instagram.android" -> {
                if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab").isNotEmpty() ||
                    root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container").isNotEmpty() ||
                    root.findAccessibilityNodeInfosByText("Reels").any { it.contentDescription?.contains("Reels") == true && it.isSelected } ||
                    isTabActive(root, "Reels")) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            "com.facebook.katana" -> {
                if (isFbReelsViewerOpen(root)) {
                    blockFacebookContent("Facebook Reels", "Facebook Reels is blocked.")
                    return true
                }
                if (root.findAccessibilityNodeInfosByText("Reels").any { it.isVisibleToUser && (it.isSelected || it.isFocused || it.parent?.isSelected == true) }) {
                    blockFacebookContent("Facebook Reels", "Facebook Reels is blocked.")
                    return true
                }
                if (root.findAccessibilityNodeInfosByText("Short videos").any { it.isVisibleToUser && (it.isSelected || it.parent?.isSelected == true) } ||
                    root.findAccessibilityNodeInfosByText("Short Videos").any { it.isVisibleToUser && (it.isSelected || it.parent?.isSelected == true) }) {
                    blockFacebookContent("Facebook Reels", "Facebook Short Videos is blocked.")
                    return true
                }
            }
        }
        return false
    }

    private fun handleInstagramSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockInstaSearch) return false
        if (pkg != "com.instagram.android") return false
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/search_tab").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/action_bar_search_edit_text").any { it.isFocused || it.isAccessibilityFocused } ||
            root.findAccessibilityNodeInfosByText("Search and explore").isNotEmpty() ||
            root.findAccessibilityNodeInfosByText("Search").any { it.isSelected }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    private fun handleWhatsAppChannels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockWaChannels) return false
        if (pkg != "com.whatsapp") return false
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/updates_tab").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/channels_home_content_layout").isNotEmpty() ||
            root.findAccessibilityNodeInfosByText("Channels").any { it.isSelected || it.isChecked } ||
            root.findAccessibilityNodeInfosByText("Updates").any { it.isSelected || it.isChecked }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    private var fbBlockLastTime = 0L
    private fun blockFacebookContent(featureTitle: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - fbBlockLastTime < 1200L) return
        fbBlockLastTime = now
        mainHandler.post { showBlockOverlay(featureTitle, reason) }
        mainHandler.postDelayed({
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    setPackage("com.facebook.katana")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            } catch (_: Exception) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }, 400)
    }

    private fun isFbVideoPlayerActuallyOpen(root: AccessibilityNodeInfo): Boolean {
        val fullscreen = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/fullscreen_video_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/fb_video_player").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_player_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_scrubber").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_control_overlay").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_controls_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/inline_video_controller").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_player_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_fullscreen_button").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/pip_button").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_player_progress_bar").any { it.isVisibleToUser }
        if (fullscreen) return true

        val story = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/story_viewer_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_story_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/story_video_player").isNotEmpty()
        if (story) return true

        val hasScrubberOrControls = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_unmute_button").any { it.isVisibleToUser }
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_pause_button").any { it.isVisibleToUser }
        if (hasScrubberOrControls) return true
        return false
    }

    private fun isFbReelsViewerOpen(root: AccessibilityNodeInfo): Boolean {
        val byId = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_fragment").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/short_video_player").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/clip_player_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/fb_shorts_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reel_player_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reel_viewer_content").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_player_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/vertical_video_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_channel_reels_container").isNotEmpty()
        if (byId) return true

        val tabSelected = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_tab_button").any { it.isSelected || it.isChecked || it.parent?.isSelected == true }
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/tab_reels").any { it.isSelected || it.isChecked }
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_nav_icon").any { it.isSelected || it.parent?.isSelected == true }
        if (tabSelected) return true

        val trayAndViewer = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_tray_container").isNotEmpty()
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_root").isNotEmpty()
        if (trayAndViewer) return true

        val reelsTextSelected = root.findAccessibilityNodeInfosByText("Reels").any { node -> (node.isSelected || node.isChecked || node.parent?.isSelected == true) && node.isVisibleToUser }
        if (reelsTextSelected) return true

        val hasReelsUi = (root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_like_button").any { it.isVisibleToUser }
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_comment_button").any { it.isVisibleToUser }
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_share_button").any { it.isVisibleToUser })
        if (hasReelsUi) return true
        return false
    }

    // ── পুরো Facebook app block ──
    // সরাসরি package-name match করে instant kill করে — কোনো view-id বা
    // on-screen text পড়ার দরকার নেই, তাই Facebook app update বা device
    // language যাই হোক না কেন কাজ করবে। Messenger এর Facebook shortcut
    // দিয়ে খুললেও com.facebook.katana foreground এ আসা মাত্রই ধরা পড়বে।
    private fun handleFacebookAppBlock(pkg: String): Boolean {
        if (!prefs.blockFacebookApp) return false
        if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite") {
            forceKillAppAndGoHome(pkg, "Facebook Blocked", "Facebook is blocked on this device.")
            return true
        }
        return false
    }

    private fun handleFacebookVideo(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockFbVideo) return false
        if (root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/watch_tab").isNotEmpty()
            || root.findAccessibilityNodeInfosByText("Watch").any { it.isSelected || it.parent?.isSelected == true }) {
            blockFacebookContent("Facebook Video", "Facebook Watch tab is blocked.")
            return true
        }
        if (isFbVideoPlayerActuallyOpen(root)) {
            blockFacebookContent("Facebook Video", "Facebook video player is blocked.")
            return true
        }
        return false
    }

    private fun handleFacebookFeedShortVideo(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockFbVideo && !prefs.blockReels) return false
        if (isFbReelsViewerOpen(root)) {
            blockFacebookContent("Facebook Reels", "Facebook Reels is blocked.")
            return true
        }
        if (prefs.blockFbVideo) {
            val autoplay = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/auto_play_video").any { it.isVisibleToUser }
            if (autoplay) {
                blockFacebookContent("Facebook Video", "Facebook auto-play video is blocked.")
                return true
            }
        }
        return false
    }

    private fun handleInstagramStories(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockInstaStories) return false
        if (pkg != "com.instagram.android") return false
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/story_container_layout").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/stories_viewer_fragment").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_header_overlay_fragment").isNotEmpty()
            || root.findAccessibilityNodeInfosByText("Story").any { it.isVisibleToUser && it.isClickable }
            || root.findAccessibilityNodeInfosByText("Your story").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_video_see_more_layout").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/story_viewer_container").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }

    private fun handleSnapchat(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg != "com.snapchat.android") return false
        if (prefs.blockSnapSpotlight) {
            if (root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/spotlight_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/discover_feed_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Spotlight").any { it.isSelected || it.parent?.isSelected == true }
                || root.findAccessibilityNodeInfosByText("Spotlight").any { it.isVisibleToUser && it.isClickable }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        if (prefs.blockSnapStories) {
            if (root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/story_viewer_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/stories_feed_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/top_snap_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Stories").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    private fun handleWhatsAppStatus(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockWaStatus) return false
        if (pkg != "com.whatsapp") return false
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_tab").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_list").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_view_page_indicator").isNotEmpty()
            || root.findAccessibilityNodeInfosByText("Status").any { it.isSelected || it.isChecked }
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_viewer_fragment_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_header_text_view").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    private fun handleWaBusinessBlocking(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg != "com.whatsapp.w4b") return false
        if (prefs.blockWaBusinessStatus) {
            if (root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/status_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/status_list").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Status").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_BACK); return true
            }
        }
        if (prefs.blockWaBusinessChannels) {
            if (root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/updates_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/channels_home_content_layout").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Channels").any { it.isSelected || it.isChecked }
                || root.findAccessibilityNodeInfosByText("Updates").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_BACK); return true
            }
        }
        return false
    }

    private fun handleTikTok(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val tiktokPkgs = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.tiktok.android")
        if (pkg !in tiktokPkgs) return false
        if (prefs.blockTikTok) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        if (prefs.blockTikTokLive) {
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/live_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("$pkg:id/live_room_fragment").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("LIVE").any { it.isVisibleToUser }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    private fun handleUnsupportedBrowsers(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockUnsupported) return false
        // Chrome, Samsung Internet, Edge — সবসময় allow
        if (ALLOWED_BROWSERS.contains(pkg)) return false
        if (BLOCKED_BROWSERS.contains(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        val recentPkgs = setOf("com.android.systemui", "com.samsung.android.systemui", "com.miui.systemui")
        if (recentPkgs.contains(pkg)) {
            val inRecents = BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isVisibleToUser } }
            if (inRecents) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        if (pkg == "com.android.settings") {
            val header = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/entity_header_title")
                .firstOrNull { it.isVisibleToUser }?.text?.toString() ?: ""
            if (BLOCKED_BROWSER_NAMES.any { header.contains(it, true) }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
            val defaultBrowserSec = root.findAccessibilityNodeInfosByText("Browser app").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Default browser").any { it.isVisibleToUser }
            if (defaultBrowserSec && BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isClickable || it.isSelected } }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        val chooserVisible = root.findAccessibilityNodeInfosByViewId("com.android.intentresolver:id/chooser_list").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByViewId("android:id/resolver_list").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByText("Open with").any { it.isVisibleToUser }
        if (chooserVisible && BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isVisibleToUser } }) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        val notif = root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/notification_panel").any { it.isVisibleToUser }
        if (notif && BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isVisibleToUser } }) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }

    // ── App-এর ভেতরের embedded WebView block (browser app না হয়েও web browsing) ──
    // Messenger এর "Facebook" shortcut এরকম case ধরার জন্য — এখানে foreground
    // package কখনো Facebook হয় না (Messenger-ই থাকে), তাই package-match দিয়ে
    // ধরা যায় না। এর বদলে screen-এ বড়সড় android.webkit.WebView node আছে কিনা
    // সেটা দেখে ধরা হচ্ছে — content কী সেটা matter করে না, কোনো webview মানেই block।
    private fun findWebViewNode(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (node.className?.toString() == "android.webkit.WebView") return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findWebViewNode(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun handleUnsupportedWebView(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockUnsupported) return false
        if (pkg.startsWith("com.android.") || pkg == "android") return false
        if (pkg in ALL_BROWSER_PKGS) return false // এগুলো handleUnsupportedBrowsers/handleAdultContent দিয়ে আলাদাভাবে হ্যান্ডল হয়

        val webView = findWebViewNode(root) ?: return false
        if (!webView.isVisibleToUser) return false

        // ছোট/hidden webview (Google/Facebook login popup, payment checkout ইত্যাদি)
        // বাদ দিতে — screen area এর কমপক্ষে ৪৫% দখল করলে তবেই "browsing করছে" ধরা হবে।
        val screenBounds = android.graphics.Rect()
        root.getBoundsInScreen(screenBounds)
        val wvBounds = android.graphics.Rect()
        webView.getBoundsInScreen(wvBounds)
        val screenArea = (screenBounds.width().toLong() * screenBounds.height().toLong()).coerceAtLeast(1)
        val wvArea = wvBounds.width().toLong() * wvBounds.height().toLong()
        if (wvArea * 100 / screenArea < 45) return false

        forceKillAppAndGoHome(pkg, "Unsupported Browser", "এই app এর ভেতরের browser (WebView) ব্লক করা আছে।")
        return true
    }

    private fun handleNewlyInstalledApps(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockNewApps) return false
        val installerPkgs = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
        if (installerPkgs.contains(pkg)) {
            val confirm = root.findAccessibilityNodeInfosByText("Do you want to install this app?").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/install_confirm_panel").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Install anyway").any { it.isClickable }
                || root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/install_button").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("App installed").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/install_success_text").any { it.isVisibleToUser }
            if (confirm) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        if (pkg == "com.android.vending") {
            val install = root.findAccessibilityNodeInfosByViewId("com.android.vending:id/buy_button").any { it.isVisibleToUser && it.isClickable }
                || root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Update").any { it.isClickable && it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByViewId("com.android.vending:id/update_button").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Update all").any { it.isClickable }
                || (root.findAccessibilityNodeInfosByText("Open").any { it.isClickable && it.isVisibleToUser } && root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser })
            if (install) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        if (pkg == "com.android.settings") {
            val unkScreen = root.findAccessibilityNodeInfosByText("Allow from this source").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Install unknown apps").any { it.isVisibleToUser }
            if (unkScreen) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        val fileMgrs = setOf(
            "com.estrongs.android.pop", "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer",
            "com.asus.filemanager", "com.alphainventor.filemanager", "com.android.documentsui"
        )
        if (fileMgrs.contains(pkg)) {
            val apk = root.findAccessibilityNodeInfosByText(".apk").any { it.isVisibleToUser && it.isClickable }
                || root.findAccessibilityNodeInfosByText("Install").any { it.isClickable }
            if (apk) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        val oemMap = mapOf(
            "com.sec.android.app.samsungapps" to "com.sec.android.app.samsungapps:id/btn_install",
            "com.xiaomi.mipicks"               to "com.xiaomi.mipicks:id/install_button",
            "com.huawei.appmarket"             to "com.huawei.appmarket:id/button_install"
        )
        for ((oemPkg, btnId) in oemMap) {
            if (pkg == oemPkg) {
                val btn = root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId(btnId).any { it.isVisibleToUser }
                if (btn) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
        }
        val oemStores = setOf("com.oppo.market", "com.heytap.market", "com.vivo.appstore", "com.oneplus.store")
        if (oemStores.contains(pkg)) {
            if (root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Get").any { it.isClickable && it.isVisibleToUser }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    private fun handleUninstallProtection(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.uninstallProtection) return false
        try {
            if (pkg == "com.android.settings") {
                if (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/uninstall_button").any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                val header = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/entity_header_title")
                    .any { it.text?.toString()?.contains(rasFocusName, true) == true }
                val unBtn = root.findAccessibilityNodeInfosByText("Uninstall").any { it.isClickable && it.isVisibleToUser }
                if (header && unBtn) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                if (root.findAccessibilityNodeInfosByText("Do you want to uninstall this app?").isNotEmpty()
                    || root.findAccessibilityNodeInfosByText("Uninstall $rasFocusName?").isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            if (pkg == "com.android.vending") {
                if (root.findAccessibilityNodeInfosByViewId("com.android.vending:id/uninstall_button").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser && it.isClickable }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            val instPkgs = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
            if (instPkgs.contains(pkg)) {
                if (root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Do you want to uninstall this app?").isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            if (pkg == "com.android.settings") {
                if (root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    if (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button").any { it.isVisibleToUser && it.isEnabled }
                        || root.findAccessibilityNodeInfosByText("Force stop").any { it.isClickable }
                        || root.findAccessibilityNodeInfosByViewId("com.android.settings:id/clear_user_data_button").any { it.isVisibleToUser }) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            }
            val fileMgrs5 = setOf(
                "com.estrongs.android.pop", "com.google.android.apps.nbu.files",
                "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer",
                "com.asus.filemanager", "com.alphainventor.filemanager"
            )
            if (fileMgrs5.contains(pkg)) {
                if (root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }
                    && root.findAccessibilityNodeInfosByText("Uninstall").any { it.isClickable }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            val launchers = setOf(
                "com.google.android.apps.nexuslauncher", "com.samsung.android.launcher",
                "com.miui.home", "com.huawei.android.launcher", "com.oppo.launcher",
                "com.vivo.launcher", "com.android.launcher", "com.android.launcher3",
                "com.teslacoilsw.launcher", "org.zimmob.zimlx"
            )
            if (launchers.contains(pkg)) {
                if (root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                val hasAppName = root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }
                val hasRemove  = root.findAccessibilityNodeInfosByText("Remove").any { it.isVisibleToUser && it.isClickable }
                if (hasAppName && hasRemove) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            val gameLaunchers = setOf(
                "com.samsung.android.game.gamehome", "com.garena.game.freefire",
                "com.mobile.legends", "com.tencent.ig", "com.dts.freefireth"
            )
            if (gameLaunchers.contains(pkg)) {
                val hasApp = root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isChecked || it.isSelected || it.isVisibleToUser }
                val hasDel = root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Delete").any { it.isVisibleToUser }
                if (hasApp && hasDel) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            if (pkg == "com.android.settings") {
                val adminScreen = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/device_admin_settings").isNotEmpty()
                    || root.findAccessibilityNodeInfosByText("Device admin apps").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Device Administrator").any { it.isVisibleToUser }
                if (adminScreen && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isClickable || it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                if (root.findAccessibilityNodeInfosByText("Deactivate").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Deactivate this device admin app").any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            if (pkg == "com.android.settings") {
                val accScreen = root.findAccessibilityNodeInfosByText("Accessibility").any { it.isVisibleToUser }
                if (accScreen && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    val tryingToTurnOff = root.findAccessibilityNodeInfosByText("Turn off").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Stop").any { it.isClickable }
                        || (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget").any { it.isVisibleToUser && !it.isChecked }
                            && root.findAccessibilityNodeInfosByText("OK").any { it.isVisibleToUser && it.isClickable })
                    if (tryingToTurnOff) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                }
            }
            if (pkg == "com.android.settings") {
                val running = root.findAccessibilityNodeInfosByText("Running services").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Running apps").any { it.isVisibleToUser }
                if (running && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            val uninstallTexts = listOf("Uninstall $rasFocusName?", "Remove $rasFocusName?", "Delete $rasFocusName?", "Do you want to uninstall $rasFocusName?")
            for (txt in uninstallTexts) {
                if (root.findAccessibilityNodeInfosByText(txt).isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            if (pkg == "com.itel") {
                val appNodes = root.findAccessibilityNodeInfosByText(rasFocusName)
                for (node in appNodes) {
                    if (node.isChecked || node.isSelected || node.parent?.isChecked == true || node.parent?.isSelected == true) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            }
            try {
                val freezeBadgeNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/checkBox_show_freeze_badge")
                if (freezeBadgeNodes.any { it.isVisibleToUser }) {
                    if (root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser } || freezeBadgeNodes.any { it.isChecked }) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}
            try {
                val deleteContainerNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/delete_icon_container")
                if (deleteContainerNodes.any { it.isVisibleToUser } && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                val allNodes = root.findAccessibilityNodeInfosByText(rasFocusName)
                for (node in allNodes) {
                    if (node.parent?.viewIdResourceName?.endsWith(":id/delete_icon_container") == true) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}
            try {
                val uninstallTitleNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/txt_uninstall_main_title")
                for (titleNode in uninstallTitleNodes) {
                    val txt = titleNode.text?.toString() ?: continue
                    if (txt.contains("Uninstall", true) || txt.contains(rasFocusName, true)) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}
            try {
                val manufacturer = android.os.Build.MANUFACTURER.lowercase()
                if (listOf("tecno", "infinix", "itel", "transsion").any { manufacturer.contains(it) }) {
                    val allText = collectAllText(root)
                    if ((allText.contains("freezer", true) || allText.contains("/freeze", true)) && allText.contains(rasFocusName, true)) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }

    private fun handleRebootProtection(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockReboot && !prefs.blockPowerOff && !prefs.blockSafeMode
            && !prefs.blockRecovery && !prefs.blockAdb) return false
        try {
            val powerMenuPkgs = setOf("com.android.systemui", "com.samsung.android.systemui", "com.miui.systemui", "com.huawei.systemmanager", "com.oppo.systemui", "com.vivo.systemui", "com.oneplus.systemui", "com.coloros.systemui")

            if (prefs.blockReboot && powerMenuPkgs.contains(pkg)) {
                val reboot = root.findAccessibilityNodeInfosByText("Restart").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Reboot").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/restart_button").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId("com.samsung.android.systemui:id/restart_button").any { it.isVisibleToUser }
                if (reboot) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

                val pmVisible = root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/global_actions_grid_item").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/power_menu").any { it.isVisibleToUser }
                if (pmVisible && (root.findAccessibilityNodeInfosByText("Restart").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Reboot").any { it.isVisibleToUser })) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }

            if (prefs.blockPowerOff && powerMenuPkgs.contains(pkg)) {
                val powerOff = root.findAccessibilityNodeInfosByText("Power off").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Shut down").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/power_off_button").any { it.isVisibleToUser }
                if (powerOff) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                val confirm = root.findAccessibilityNodeInfosByText("Power off?").isNotEmpty() || root.findAccessibilityNodeInfosByText("Shut down?").isNotEmpty()
                if (confirm) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }

            if (prefs.blockSafeMode) {
                val safeMode = root.findAccessibilityNodeInfosByText("Safe mode").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Running in safe mode").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Restart to safe mode?").isNotEmpty()
                    || root.findAccessibilityNodeInfosByText("Reboot into safe mode?").isNotEmpty()
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/safe_mode_text").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId("com.samsung.android.systemui:id/safe_mode_button").any { it.isVisibleToUser }
                if (safeMode) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }

            if (prefs.blockRecovery) {
                val recovery = root.findAccessibilityNodeInfosByText("Recovery mode").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Reboot to recovery").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Unlock bootloader").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Fastboot mode").any { it.isVisibleToUser }
                if (recovery) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

                if (pkg == "com.android.settings") {
                    val factory = root.findAccessibilityNodeInfosByText("Factory data reset").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Erase all data").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Reset phone").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByViewId("com.android.settings:id/eraseButton").any { it.isVisibleToUser }
                    if (factory) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                }
            }

            if (prefs.blockAdb && pkg == "com.android.settings") {
                val adbDialog = root.findAccessibilityNodeInfosByText("Allow USB debugging?").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Allow wireless debugging?").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("RSA key fingerprint").any { it.isVisibleToUser }
                if (adbDialog) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                val adbScreen = root.findAccessibilityNodeInfosByText("USB debugging").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Wireless debugging").any { it.isVisibleToUser }
                if (adbScreen) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }
}

// ════════════════════════════════════════════════════════════
// D. SETTINGS UI — Jetpack Compose
// ════════════════════════════════════════════════════════════

class RasFocusSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RasFocusSettingsTheme {
                ExtremeBlockScreen()
            }
        }
    }
}

private val BG_DEEP       = Color(0xFF0A0C10)
private val BG_CARD = Color(0xFF1E293B)
private val BG_CARD2 = Color(0xFF334155)
private val ACCENT = Color(0xFF0096B4)
private val ACCENT2       = Color(0xFF7B5CFA)
private val ACCENT_RED    = Color(0xFFFF3B5C)
private val ACCENT_AMBER  = Color(0xFFFFB800)
private val TEXT_PRIMARY  = Color(0xFFEAEDF3)
private val TEXT_SEC      = Color(0xFF6B7280)
private val DIVIDER       = Color(0xFF1E222C)
private val SWITCH_OFF    = Color(0xFF2A2F3D)

@Composable
fun RasFocusSettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BG_DEEP, surface = BG_CARD, primary = ACCENT, onBackground = TEXT_PRIMARY, onSurface = TEXT_PRIMARY
        ), content = content
    )
}

@Composable
fun ExtremeBlockScreen() {
    val ctx = LocalContext.current
    val prefs = remember { BlockerPrefs(ctx) }

    // ── Adult Block — ৩টা toggle ──
    var blockNormalLoading   by remember { mutableStateOf(prefs.blockNormalLoading) }   // বাটন ১: URL load এ block
    var blockAdultImageWeb   by remember { mutableStateOf(prefs.blockAdultImageWeb) }   // বাটন ২: Screen scan block
    var blockInstantKeyboard by remember { mutableStateOf(prefs.blockInstantKeyboard) } // বাটন ৩: Typing block

    var blockSearch      by remember { mutableStateOf(prefs.blockSearch) }
    var blockReels       by remember { mutableStateOf(prefs.blockReels) }
    var blockInstaSearch by remember { mutableStateOf(prefs.blockInstaSearch) }
    var blockYtShorts    by remember { mutableStateOf(prefs.blockYtShorts) }
    var blockWaChannels  by remember { mutableStateOf(prefs.blockWaChannels) }

    var blockInstaStories      by remember { mutableStateOf(prefs.blockInstaStories) }
    var blockWaStatus          by remember { mutableStateOf(prefs.blockWaStatus) }
    var blockWaBusinessStatus  by remember { mutableStateOf(prefs.blockWaBusinessStatus) }
    var blockWaBusinessChannels by remember { mutableStateOf(prefs.blockWaBusinessChannels) }
    var blockSnapSpotlight     by remember { mutableStateOf(prefs.blockSnapSpotlight) }
    var blockSnapStories       by remember { mutableStateOf(prefs.blockSnapStories) }
    var blockTikTok            by remember { mutableStateOf(prefs.blockTikTok) }
    var blockTikTokLive        by remember { mutableStateOf(prefs.blockTikTokLive) }

    var blockUnsupported by remember { mutableStateOf(prefs.blockUnsupported) }
    var blockNewApps     by remember { mutableStateOf(prefs.blockNewApps) }
    var blockFbVideo     by remember { mutableStateOf(prefs.blockFbVideo) }
    var blockFacebookApp by remember { mutableStateOf(prefs.blockFacebookApp) }

    val dpm = remember { ctx.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager }
    val adminComponent = remember { android.content.ComponentName(ctx, com.rasel.RasFocus.features.MyDeviceAdminReceiver::class.java) }
    fun isAdminActive() = dpm.isAdminActive(adminComponent)
    fun isAccessibilityActive(): Boolean {
        val fullName = "${ctx.packageName}/.selfcontrol.RasFocusBlockingService"
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return list.contains(fullName, ignoreCase = true) || list.contains(ctx.packageName, ignoreCase = true)
    }

    var uninstallProt by remember { mutableStateOf(isAdminActive()) }
    var isServiceActive by remember { mutableStateOf(isAccessibilityActive()) }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val adminNowActive = isAdminActive()
        uninstallProt = adminNowActive; prefs.uninstallProtection = adminNowActive
        if (adminNowActive && !isAccessibilityActive()) {
            android.widget.Toast.makeText(ctx, "✅ Device Admin দেওয়া হয়েছে! এখন Accessibility permission দিন।", android.widget.Toast.LENGTH_LONG).show()
            try { ctx.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val adminActive = isAdminActive(); uninstallProt = adminActive; prefs.uninstallProtection = adminActive; isServiceActive = isAccessibilityActive()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var blockAdb         by remember { mutableStateOf(prefs.blockAdb) }
    var blockPowerOff    by remember { mutableStateOf(prefs.blockPowerOff) }
    var blockSafeMode    by remember { mutableStateOf(prefs.blockSafeMode) }
    var blockReboot      by remember { mutableStateOf(prefs.blockReboot) }
    var blockRecovery    by remember { mutableStateOf(prefs.blockRecovery) }
    var blockedMsg       by remember { mutableStateOf(prefs.blockedMessage) }
    var redirectUrl      by remember { mutableStateOf(prefs.redirectUrl) }

    var focusLockActive  by remember { mutableStateOf(prefs.focusLockActive) }
    var focusLockMode    by remember { mutableStateOf(prefs.focusLockMode) }
    var focusLockEndTime by remember { mutableStateOf(prefs.focusLockEndTime) }
    var showFocusSetupDialog by remember { mutableStateOf(false) }
    var showFocusUnlockDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(BG_DEEP)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = 40.dp.toPx()
            for (col in 0..(size.width / spacing).toInt() + 1) drawLine(Color(0xFF131720), Offset(col * spacing, 0f), Offset(col * spacing, size.height), strokeWidth = 0.5f)
            for (row in 0..(size.height / spacing).toInt() + 1) drawLine(Color(0xFF131720), Offset(0f, row * spacing), Offset(size.width, row * spacing), strokeWidth = 0.5f)
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            HeaderBar(isActive = isServiceActive)
            Spacer(Modifier.height(12.dp))

            FocusLockTopBar(isActive = focusLockActive, endTime = focusLockEndTime, onStartClick = { showFocusSetupDialog = true }, onUnlockClick = { showFocusUnlockDialog = true })
            Spacer(Modifier.height(20.dp))

            SectionHeader(icon = "⊡", title = "Content Blocking", accentColor = ACCENT)
            Spacer(Modifier.height(8.dp))

            // ── Adult Block — ৩টা সরাসরি toggle ──
            BlockingCard {
                SubSwitchRow(
                    icon = "🔗", label = "Normal Adult Block",
                    sublabel = "URL load হওয়ার পরে adult site block করবে",
                    checked = blockNormalLoading, accentColor = ACCENT_RED,
                    onCheckedChange = {
                        if (it || !focusLockActive) {
                            blockNormalLoading = it
                            prefs.blockNormalLoading = it
                            prefs.blockAdult = it
                            prefs.blockAdultSiteList = it
                            RasFocusBlockingService.instance?.checkCurrentWindow()
                        }
                    }
                )
                RasDivider()
                SubSwitchRow(
                    icon = "🔍", label = "Screen Scan Block",
                    sublabel = "যেকোনো app এ adult content screen এ এলেই block করবে",
                    checked = blockAdultImageWeb, accentColor = ACCENT_RED,
                    onCheckedChange = {
                        if (it || !focusLockActive) {
                            blockAdultImageWeb = it
                            prefs.blockAdultImageWeb = it
                            RasFocusBlockingService.instance?.checkCurrentWindow()
                        }
                    }
                )
                RasDivider()
                SubSwitchRow(
                    icon = "⌨", label = "Instant Typing Block",
                    sublabel = "টাইপ করার সাথে সাথে যেকোনো app এ block করবে",
                    checked = blockInstantKeyboard, accentColor = ACCENT_RED,
                    onCheckedChange = {
                        if (it || !focusLockActive) {
                            blockInstantKeyboard = it
                            prefs.blockInstantKeyboard = it
                            prefs.blockAnyAppTyping = it
                            RasFocusBlockingService.instance?.checkCurrentWindow()
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            BlockingCard {
                RasSwitch(label = "Block Instagram search", sublabel = "Blocks Explore & search tab", checked = blockInstaSearch, accentColor = ACCENT, onCheckedChange = { if (it || !focusLockActive) { blockInstaSearch = it; prefs.blockInstaSearch = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                RasSwitch(label = "Block WhatsApp channels", sublabel = "Blocks Updates/Channels tab", checked = blockWaChannels, accentColor = ACCENT, onCheckedChange = { if (it || !focusLockActive) { blockWaChannels = it; prefs.blockWaChannels = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } })
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(icon = "▶", title = "Block Reels / Shorts", accentColor = ACCENT)
            Spacer(Modifier.height(6.dp))
            Text("Block distracting content like Shorts, Reels, and other in-app feeds to stay focused.", color = TEXT_SEC, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().background(BG_CARD, RoundedCornerShape(10.dp)).border(1.dp, DIVIDER, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
            Spacer(Modifier.height(8.dp))

            AppReelsCard("▶", "YouTube", Color(0xFFFF0000), listOf(AppReelsRow.Toggle("Shorts", blockYtShorts) { if (it || !focusLockActive) { blockYtShorts = it; prefs.blockYtShorts = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }))
            Spacer(Modifier.height(8.dp))
            AppReelsCard("📷", "Instagram", Color(0xFFE1306C), listOf(AppReelsRow.Toggle("Reels", blockReels) { if (it || !focusLockActive) { blockReels = it; prefs.blockReels = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }, AppReelsRow.Toggle("Stories", blockInstaStories) { if (it || !focusLockActive) { blockInstaStories = it; prefs.blockInstaStories = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }))
            Spacer(Modifier.height(8.dp))
            AppReelsCard("💬", "WhatsApp", Color(0xFF25D366), listOf(AppReelsRow.Toggle("Status", blockWaStatus) { if (it || !focusLockActive) { blockWaStatus = it; prefs.blockWaStatus = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }, AppReelsRow.Toggle("Channels", blockWaChannels) { if (it || !focusLockActive) { blockWaChannels = it; prefs.blockWaChannels = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }))
            Spacer(Modifier.height(8.dp))
            AppReelsCard("👻", "Snapchat", Color(0xFFFFFC00), listOf(AppReelsRow.Toggle("Spotlight", blockSnapSpotlight) { if (it || !focusLockActive) { blockSnapSpotlight = it; prefs.blockSnapSpotlight = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }, AppReelsRow.Toggle("Stories", blockSnapStories) { if (it || !focusLockActive) { blockSnapStories = it; prefs.blockSnapStories = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }))
            Spacer(Modifier.height(8.dp))
            AppReelsCard("💼", "WA Business", Color(0xFF128C7E), listOf(AppReelsRow.Toggle("Status", blockWaBusinessStatus) { if (it || !focusLockActive) { blockWaBusinessStatus = it; prefs.blockWaBusinessStatus = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }, AppReelsRow.Toggle("Channels", blockWaBusinessChannels) { if (it || !focusLockActive) { blockWaBusinessChannels = it; prefs.blockWaBusinessChannels = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }))
            Spacer(Modifier.height(8.dp))
            AppReelsCard("🎵", "TikTok", Color(0xFF010101), listOf(AppReelsRow.Toggle("Feed / For You", blockTikTok) { if (it || !focusLockActive) { blockTikTok = it; prefs.blockTikTok = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }, AppReelsRow.Toggle("LIVE", blockTikTokLive) { if (it || !focusLockActive) { blockTikTokLive = it; prefs.blockTikTokLive = it; RasFocusBlockingService.instance?.checkCurrentWindow() } }))

            SectionHeader(icon = "◈", title = "Advanced Blocking", accentColor = ACCENT2)
            Spacer(Modifier.height(8.dp))
            BlockingCard {
                RasSwitch(label = "Block unsupported browsers", sublabel = "Opera, Firefox, Brave, UC & more", checked = blockUnsupported, accentColor = ACCENT2, onCheckedChange = { if (it || !focusLockActive) { blockUnsupported = it; prefs.blockUnsupported = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                RasSwitch(label = "Block newly installed apps", sublabel = "Prevents all new installs & APK sideloads", checked = blockNewApps, accentColor = ACCENT2, onCheckedChange = { if (it || !focusLockActive) { blockNewApps = it; prefs.blockNewApps = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                RasSwitch(label = "Block Facebook video", sublabel = "Blocks Watch, Reels & inline videos", checked = blockFbVideo, accentColor = ACCENT2, onCheckedChange = { if (it || !focusLockActive) { blockFbVideo = it; prefs.blockFbVideo = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                RasSwitch(label = "Block Facebook completely", sublabel = "Kills Facebook instantly — even opened via Messenger's Facebook icon", checked = blockFacebookApp, accentColor = ACCENT2, onCheckedChange = { if (it || !focusLockActive) { blockFacebookApp = it; prefs.blockFacebookApp = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(icon = "⬡", title = "App Protection", accentColor = ACCENT_RED)
            Spacer(Modifier.height(8.dp))
            MasterToggleCard(label = "Uninstall protection", sublabel = "10-layer block — Settings, Play Store, ADB, launchers & more", checked = uninstallProt, onCheckedChange = { newValue ->
                if (newValue || !focusLockActive) {
                    if (newValue) {
                        if (isAdminActive()) {
                            uninstallProt = true; prefs.uninstallProtection = true
                            if (!isAccessibilityActive()) {
                                android.widget.Toast.makeText(ctx, "⚠️ Uninstall protection-এর জন্য Accessibility permission দিন!", android.widget.Toast.LENGTH_LONG).show()
                                try { ctx.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
                            }
                        } else {
                            val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "RasFocus-কে uninstall থেকে রক্ষা করতে Device Admin permission দরকার। এটা চালু থাকলে কেউ app টা delete করতে পারবে না।")
                            }
                            try { adminLauncher.launch(intent) } catch (e: Exception) { android.widget.Toast.makeText(ctx, "Device Admin permission দিতে ব্যর্থ হয়েছে", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                    } else {
                        try {
                            if (dpm.isAdminActive(adminComponent)) dpm.removeActiveAdmin(adminComponent)
                            else { uninstallProt = false; prefs.uninstallProtection = false }
                        } catch (e: Exception) { uninstallProt = false; prefs.uninstallProtection = false }
                    }
                }
            })

            Spacer(Modifier.height(8.dp))
            BlockingCard {
                SubSwitchRow(icon = "⏻", label = "Block power off", sublabel = "Prevents device shutdown", checked = blockPowerOff, accentColor = ACCENT_AMBER, onCheckedChange = { if (it || !focusLockActive) { blockPowerOff = it; prefs.blockPowerOff = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                SubSwitchRow(icon = "↺", label = "Block reboot", sublabel = "Blocks restart from power menu & Settings", checked = blockReboot, accentColor = ACCENT_AMBER, onCheckedChange = { if (it || !focusLockActive) { blockReboot = it; prefs.blockReboot = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                SubSwitchRow(icon = "⚠", label = "Block safe mode", sublabel = "Detects & exits safe mode boot", checked = blockSafeMode, accentColor = ACCENT_AMBER, onCheckedChange = { if (it || !focusLockActive) { blockSafeMode = it; prefs.blockSafeMode = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                SubSwitchRow(icon = "⟳", label = "Block recovery / factory reset", sublabel = "Blocks recovery mode, bootloader, factory reset", checked = blockRecovery, accentColor = ACCENT_RED, onCheckedChange = { if (it || !focusLockActive) { blockRecovery = it; prefs.blockRecovery = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
                RasDivider()
                SubSwitchRow(icon = "⌁", label = "Block ADB / USB debugging", sublabel = "Blocks ADB authorization dialogs", checked = blockAdb, accentColor = ACCENT_RED, onCheckedChange = { if (it || !focusLockActive) { blockAdb = it; prefs.blockAdb = it; RasFocusBlockingService.instance?.checkCurrentWindow() } })
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(icon = "✦", title = "Customize Blocked Screen", accentColor = TEXT_SEC)
            Spacer(Modifier.height(8.dp))
            BlockingCard {
                CustomizeRow(label = "Blocked screen message", value = blockedMsg, placeholder = "This page is blocked.", onValueChange = { blockedMsg = it; prefs.blockedMessage = it })
                RasDivider()
                CustomizeRow(label = "Redirect after closing (any URL)", value = redirectUrl, placeholder = "https://www.google.com", onValueChange = { redirectUrl = it; prefs.redirectUrl = it })
            }

            Spacer(Modifier.height(56.dp))
        }

        if (showFocusSetupDialog) {
            FocusLockSetupDialog(prefs = prefs, onDismiss = { showFocusSetupDialog = false }, onActivated = { mode, endMs ->
                focusLockActive = true; focusLockMode = mode; focusLockEndTime = endMs
                prefs.focusLockActive = true; prefs.focusLockMode = mode; prefs.focusLockEndTime = endMs
                showFocusSetupDialog = false
            })
        }
        if (showFocusUnlockDialog) {
            FocusLockUnlockDialog(prefs = prefs, onDismiss = { showFocusUnlockDialog = false }, onUnlocked = {
                focusLockActive = false; focusLockMode = "none"; focusLockEndTime = 0L
                prefs.focusLockActive = false; prefs.focusLockMode = "none"; prefs.focusLockEndTime = 0L
                showFocusUnlockDialog = false
            })
        }
    }
}

// ── UI Components ─────────────────────────────────────────

@Composable
fun HeaderBar(isActive: Boolean) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(40.dp).background(Brush.linearGradient(listOf(ACCENT, ACCENT2), start = Offset(0f, 0f), end = Offset(40f, 40f)), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("RF", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("RasFocus", color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text("Content Blocker", color = TEXT_SEC, fontSize = 12.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.background(if (isActive) Color(0xFF1A2332) else Color(0xFF2A1515), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(if (isActive) "● ACTIVE" else "○ INACTIVE", color = if (isActive) ACCENT else ACCENT_RED, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        if (!isActive) {
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF2A1515), RoundedCornerShape(12.dp)).border(1.dp, ACCENT_RED.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ACCENT_RED, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Accessibility Service চালু নেই", color = ACCENT_RED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Blocking কাজ করছে না — তাপ করে চালু করুন", color = TEXT_SEC, fontSize = 11.sp)
                }
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.background(ACCENT_RED, RoundedCornerShape(8.dp)).clickable { try { ctx.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("চালু করুন", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(icon: String, title: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = accentColor, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.3f), Color.Transparent))))
    }
}

@Composable
fun BlockingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(BG_CARD, RoundedCornerShape(14.dp)).border(1.dp, DIVIDER, RoundedCornerShape(14.dp)), content = content)
}

@Composable
fun MasterToggleCard(label: String, sublabel: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(if (checked) Color(0xFF1A0A0E) else BG_CARD, RoundedCornerShape(14.dp)).border(1.dp, if (checked) ACCENT_RED.copy(alpha = 0.5f) else DIVIDER, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(sublabel, color = TEXT_SEC, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            RasToggle(checked = checked, accentColor = ACCENT_RED, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun RasSwitch(label: String, sublabel: String, checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(sublabel, color = TEXT_SEC, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        RasToggle(checked = checked, accentColor = accentColor, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SubSwitchRow(icon: String, label: String, sublabel: String, checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(30.dp).background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 13.sp, color = accentColor) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(sublabel, color = TEXT_SEC, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        RasToggle(checked = checked, accentColor = accentColor, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CustomizeRow(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = TEXT_SEC, fontSize = 11.sp, letterSpacing = 0.3.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(value = value, onValueChange = onValueChange, textStyle = TextStyle(color = TEXT_PRIMARY, fontSize = 13.sp), decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth().background(BG_DEEP, RoundedCornerShape(8.dp)).border(1.dp, DIVIDER, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (value.isEmpty()) Text(placeholder, color = TEXT_SEC, fontSize = 13.sp)
                inner()
            }
        })
    }
}

@Composable
fun RasToggle(checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    val trackColor by animateColorAsState(if (checked) accentColor.copy(alpha = 0.25f) else SWITCH_OFF, tween(250))
    val thumbColor by animateColorAsState(if (checked) accentColor else Color(0xFF4B5563), tween(250))
    val offset by animateDpAsState(if (checked) 20.dp else 2.dp, tween(250))

    Box(modifier = Modifier.width(44.dp).height(26.dp).background(trackColor, RoundedCornerShape(13.dp)).border(1.dp, if (checked) accentColor.copy(alpha = 0.5f) else Color(0xFF374151), RoundedCornerShape(13.dp)).clickable { onCheckedChange(!checked) }) {
        Box(modifier = Modifier.padding(start = offset).align(Alignment.CenterStart).size(22.dp).background(thumbColor, CircleShape).then(if (checked) Modifier.shadow(4.dp, CircleShape, spotColor = accentColor) else Modifier))
    }
}

sealed class AppReelsRow {
    data class Toggle(val label: String, val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : AppReelsRow()
    data class Upgrade(val label: String) : AppReelsRow()
}

@Composable
fun AppReelsCard(appIcon: String, appName: String, appIconColor: Color, rows: List<AppReelsRow>) {
    Column(modifier = Modifier.fillMaxWidth().background(BG_CARD, RoundedCornerShape(14.dp)).border(1.dp, DIVIDER, RoundedCornerShape(14.dp))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).background(appIconColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).border(1.dp, appIconColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(appIcon, fontSize = 16.sp) }
            Spacer(Modifier.width(12.dp))
            Text(appName, color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        rows.forEachIndexed { index, row ->
            Box(modifier = Modifier.fillMaxWidth().padding(start = 28.dp)) {
                Canvas(modifier = Modifier.width(2.dp).height(if (index == rows.lastIndex) 24.dp else 48.dp).align(Alignment.TopStart)) {
                    val dashLen = 6.dp.toPx()
                    val gap = 4.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color(0xFF2A3040), Offset(size.width / 2, y), Offset(size.width / 2, minOf(y + dashLen, size.height)), 1.5f)
                        y += dashLen + gap
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (row) { is AppReelsRow.Toggle -> row.label; is AppReelsRow.Upgrade -> row.label }, color = TEXT_PRIMARY, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    when (row) { is AppReelsRow.Toggle -> RasToggle(row.checked, ACCENT, row.onCheckedChange); is AppReelsRow.Upgrade -> UpgradeButton() }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun UpgradeButton() {
    val pulse by rememberInfiniteTransition().animateFloat(0.92f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    Box(modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }.background(Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFFB800))), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👑", fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            Text("Upgrade", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        }
    }
}

@Composable
fun RasDivider() { Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(DIVIDER)) }

@Composable
fun FocusLockTopBar(isActive: Boolean, endTime: Long, onStartClick: () -> Unit, onUnlockClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.96f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    var remainingMs by remember { mutableStateOf(0L) }
    LaunchedEffect(isActive, endTime) {
        while (isActive && endTime > 0) {
            remainingMs = maxOf(0L, endTime - System.currentTimeMillis())
            if (remainingMs == 0L) break
            kotlinx.coroutines.delay(1000L)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).zIndex(10f)) {
        Box(modifier = Modifier.fillMaxWidth().background(if (isActive) Brush.horizontalGradient(listOf(Color(0xFF0D2B1F), Color(0xFF0D1A2B))) else Brush.horizontalGradient(listOf(Color(0xFF111827), Color(0xFF1A1F2E))), RoundedCornerShape(16.dp)).border(1.5.dp, if (isActive) ACCENT.copy(0.6f) else Color(0xFF2A3040), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(36.dp).background(if (isActive) ACCENT.copy(0.15f) else Color(0xFF1E2330), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(if (isActive) "🔒" else "🎯", fontSize = 16.sp) }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isActive) "Focus Lock Active" else "Start Focus", color = if (isActive) ACCENT else TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (isActive && endTime > 0 && remainingMs > 0) {
                        val h = TimeUnit.MILLISECONDS.toHours(remainingMs); val m = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60; val s = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
                        Text("%02d:%02d:%02d".format(h, m, s), color = ACCENT.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    } else if (isActive) {
                        Text("Active", color = ACCENT.copy(0.7f), fontSize = 11.sp)
                    }
                }
                Box(modifier = Modifier.graphicsLayer { if (isActive) { scaleX = pulse; scaleY = pulse } }.background(if (isActive) Brush.horizontalGradient(listOf(Color(0xFFFF3B5C), Color(0xFFFF6B35))) else Brush.horizontalGradient(listOf(ACCENT, ACCENT2)), RoundedCornerShape(50.dp)).clickable { if (isActive) onUnlockClick() else onStartClick() }.padding(horizontal = 18.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(if (isActive) "Unlock" else "Start", color = if (isActive) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class ModeItem(val code: String, val icon: String, val title: String, val sub: String)

@Composable
fun FocusLockSetupDialog(prefs: BlockerPrefs, onDismiss: () -> Unit, onActivated: (mode: String, endMs: Long) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var selectedMode by remember { mutableStateOf("") }
    var selfDays by remember { mutableStateOf(0) }; var selfHours by remember { mutableStateOf(0) }; var selfMinutes by remember { mutableStateOf(25) }
    var parentPass by remember { mutableStateOf("") }; var parentPass2 by remember { mutableStateOf("") }; var passError by remember { mutableStateOf("") }
    val longTextPassage = "Read carefully and type: Time is the most precious resource in our lives. Every moment that passes never returns. The person who respects their time moves forward in life. Distraction is our greatest enemy. Focus is power, discipline is freedom. Stay committed to your goals and make progress every day. Success does not come overnight; it is the fruit of patience and perseverance."

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.94f).background(BG_CARD, RoundedCornerShape(20.dp)).border(1.dp, DIVIDER, RoundedCornerShape(20.dp)).padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                    Text(if (step == 0) "Select Mode" else "Configure", color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
                    Text("✕", color = TEXT_SEC, fontSize = 14.sp, modifier = Modifier.clickable { onDismiss() }.padding(4.dp))
                }
                Spacer(Modifier.height(16.dp))
                if (step == 0) {
                    val modes = listOf(ModeItem("self", "⏱", "Self Mode", "Set day / hour / minute"), ModeItem("parents", "🔐", "Parents Mode", "Password protected lock"), ModeItem("longtext", "📝", "Long Text Mode", "Unlock by typing 100 words"))
                    modes.forEach { modeItem ->
                        val sel = selectedMode == modeItem.code
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).background(if (sel) ACCENT.copy(0.1f) else com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surfaceVariant, RoundedCornerShape(12.dp)).border(1.5.dp, if (sel) ACCENT else Color(0xFF2A3040), RoundedCornerShape(12.dp)).clickable { selectedMode = modeItem.code }.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = modeItem.icon, fontSize = 22.sp); Spacer(Modifier.width(12.dp))
                                Column { Text(text = modeItem.title, color = if (sel) ACCENT else TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(text = modeItem.sub, color = TEXT_SEC, fontSize = 11.sp) }
                                if (sel) { Spacer(Modifier.weight(1f)); Text(text = "✓", color = ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FocusDialogButton("Cancel", true, modifier = Modifier.weight(1f)) { onDismiss() }
                        FocusDialogButton("Next →", selectedMode.isNotEmpty(), modifier = Modifier.weight(1f)) { step = 1 }
                    }
                }
                if (step == 1) {
                    when (selectedMode) {
                        "self" -> {
                            Text("Set your focus duration:", color = TEXT_SEC, fontSize = 12.sp); Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusNumberPicker("Day", selfDays, 0, 30, { selfDays = it }, Modifier.weight(1f))
                                FocusNumberPicker("Hour", selfHours, 0, 23, { selfHours = it }, Modifier.weight(1f))
                                FocusNumberPicker("Min", selfMinutes, 0, 59, { selfMinutes = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(16.dp))
                            val totalMs = (selfDays * 86400L + selfHours * 3600L + selfMinutes * 60L) * 1000L
                            val valid = totalMs > 0
                            if (!valid) { Text("Set at least 1 minute", color = ACCENT_RED, fontSize = 11.sp); Spacer(Modifier.height(8.dp)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusDialogButton("← Back", true, modifier = Modifier.weight(1f)) { step = 0 }
                                FocusDialogButton("🔒 Lock", valid, modifier = Modifier.weight(1f)) { onActivated("self", System.currentTimeMillis() + totalMs) }
                            }
                        }
                        "parents" -> {
                            Text("Enter password:", color = TEXT_SEC, fontSize = 12.sp); Spacer(Modifier.height(10.dp))
                            FocusPasswordField(value = parentPass, placeholder = "Password", onValueChange = { parentPass = it; passError = "" }); Spacer(Modifier.height(8.dp))
                            FocusPasswordField(value = parentPass2, placeholder = "Confirm password", onValueChange = { parentPass2 = it; passError = "" })
                            if (passError.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(passError, color = ACCENT_RED, fontSize = 11.sp) }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusDialogButton("← Back", true, modifier = Modifier.weight(1f)) { step = 0 }
                                FocusDialogButton("🔒 Lock", parentPass.length >= 4, modifier = Modifier.weight(1f)) {
                                    if (parentPass.length < 4) passError = "Minimum 4 characters"
                                    else if (parentPass != parentPass2) passError = "Passwords do not match"
                                    else { prefs.focusLockPassword = parentPass.hashCode().toString(); onActivated("parents", 0L) }
                                }
                            }
                        }
                        "longtext" -> {
                            Text("Remember this passage — you must type 100 words to unlock:", color = TEXT_SEC, fontSize = 11.sp, lineHeight = 16.sp); Spacer(Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117), RoundedCornerShape(10.dp)).border(1.dp, ACCENT.copy(0.3f), RoundedCornerShape(10.dp)).padding(12.dp)) { Text(longTextPassage, color = ACCENT.copy(0.9f), fontSize = 11.sp, lineHeight = 17.sp) }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusDialogButton("← Back", true, modifier = Modifier.weight(1f)) { step = 0 }
                                FocusDialogButton("🔒 Lock", true, modifier = Modifier.weight(1f)) { prefs.focusLockLongText = longTextPassage; onActivated("longtext", 0L) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusLockUnlockDialog(prefs: BlockerPrefs, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val mode = prefs.focusLockMode; var error by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    val requiredText = prefs.focusLockLongText; var textInput by remember { mutableStateOf("") }
    val inputWordCount = textInput.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    val requiredWordCount = requiredText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    val timeUp = mode == "self" && System.currentTimeMillis() >= prefs.focusLockEndTime

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.94f).background(BG_CARD, RoundedCornerShape(20.dp)).border(1.5.dp, ACCENT_RED.copy(0.5f), RoundedCornerShape(20.dp)).padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔓", fontSize = 20.sp); Spacer(Modifier.width(8.dp)); Text("Unlock Focus Lock", color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("✕", color = TEXT_SEC, fontSize = 14.sp, modifier = Modifier.clickable { onDismiss() }.padding(4.dp))
                }
                Spacer(Modifier.height(16.dp))
                when (mode) {
                    "self" -> {
                        if (timeUp) {
                            Text("✅ Time's up! You can unlock now.", color = ACCENT, fontSize = 13.sp); Spacer(Modifier.height(16.dp)); FocusDialogButton("🔓 Unlock", true) { onUnlocked() }
                        } else {
                            val remaining = maxOf(0L, prefs.focusLockEndTime - System.currentTimeMillis()); val h = TimeUnit.MILLISECONDS.toHours(remaining); val m = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60; val s = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                            Text("⏳ %02d:%02d:%02d remaining. Cannot unlock until time is up.".format(h, m, s), color = ACCENT_AMBER, fontSize = 12.sp, lineHeight = 18.sp); Spacer(Modifier.height(16.dp)); FocusDialogButton("OK", true) { onDismiss() }
                        }
                    }
                    "parents" -> {
                        Text("Enter password:", color = TEXT_SEC, fontSize = 12.sp); Spacer(Modifier.height(10.dp))
                        FocusPasswordField(value = passInput, placeholder = "Password", onValueChange = { passInput = it; error = "" })
                        if (error.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(error, color = ACCENT_RED, fontSize = 11.sp) }
                        Spacer(Modifier.height(16.dp))
                        FocusDialogButton("🔓 Unlock", passInput.isNotEmpty()) { if (passInput.hashCode().toString() == prefs.focusLockPassword) onUnlocked() else error = "❌ Wrong password!" }
                    }
                    "longtext" -> {
                        Text("Type the full passage below ($requiredWordCount words):", color = TEXT_SEC, fontSize = 11.sp); Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117), RoundedCornerShape(10.dp)).border(1.dp, ACCENT.copy(0.3f), RoundedCornerShape(10.dp)).padding(10.dp).heightIn(max = 120.dp)) { Text(requiredText, color = TEXT_SEC, fontSize = 10.sp, lineHeight = 15.sp) }
                        Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().background(com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surface, RoundedCornerShape(10.dp)).border(1.dp, if (inputWordCount >= requiredWordCount) ACCENT else Color(0xFF2A3040), RoundedCornerShape(10.dp)).padding(10.dp).heightIn(min = 80.dp, max = 140.dp)) {
                            BasicTextField(value = textInput, onValueChange = { textInput = it }, textStyle = TextStyle(color = TEXT_PRIMARY, fontSize = 12.sp, lineHeight = 18.sp), modifier = Modifier.fillMaxWidth(), decorationBox = { inner -> if (textInput.isEmpty()) Text("Type here...", color = TEXT_SEC, fontSize = 12.sp); inner() })
                        }
                        Spacer(Modifier.height(6.dp))
                        Row { Text("Words: $inputWordCount / $requiredWordCount", color = if (inputWordCount >= requiredWordCount) ACCENT else TEXT_SEC, fontSize = 11.sp) }
                        if (error.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(error, color = ACCENT_RED, fontSize = 11.sp) }
                        Spacer(Modifier.height(16.dp))
                        FocusDialogButton("🔓 Unlock", inputWordCount >= requiredWordCount) {
                            val normalize: (String) -> String = { s -> s.trim().replace("\\s+".toRegex(), " ").lowercase() }
                            if (normalize(textInput) == normalize(requiredText)) onUnlocked() else error = "❌ Text doesn't match exactly!"
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusDialogButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.background(if (enabled) Brush.horizontalGradient(listOf(ACCENT, ACCENT2)) else Brush.horizontalGradient(listOf(Color(0xFF2A2F3D), Color(0xFF2A2F3D))), RoundedCornerShape(12.dp)).then(if (enabled) Modifier.clickable { onClick() } else Modifier).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) Color.Black else TEXT_SEC, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun FocusPasswordField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surface, RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF2A3040), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        BasicTextField(value = value, onValueChange = onValueChange, textStyle = TextStyle(color = TEXT_PRIMARY, fontSize = 14.sp), modifier = Modifier.fillMaxWidth(), decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = TEXT_SEC, fontSize = 14.sp); inner() })
    }
}

@Composable
fun FocusNumberPicker(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surfaceVariant, RoundedCornerShape(12.dp)).border(1.dp, DIVIDER, RoundedCornerShape(12.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TEXT_SEC, fontSize = 10.sp, letterSpacing = 0.5.sp); Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.size(32.dp).background(Color(0xFF1E2330), CircleShape).clickable { if (value < max) onValueChange(value + 1) }, contentAlignment = Alignment.Center) { Text("▲", color = ACCENT, fontSize = 10.sp) }
        Spacer(Modifier.height(4.dp))
        Text("%02d".format(value), color = TEXT_PRIMARY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.size(32.dp).background(Color(0xFF1E2330), CircleShape).clickable { if (value > min) onValueChange(value - 1) }, contentAlignment = Alignment.Center) { Text("▼", color = ACCENT, fontSize = 10.sp) }
    }
}

// ════════════════════════════════════════════════════════════════════
// SERVICE RESTART RECEIVER
// ════════════════════════════════════════════════════════════════════

class ServiceRestartReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val isRunning = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningServices(100).any { it.service.className == RasFocusBlockingService::class.java.name }
        } catch (_: Exception) { false }

        if (!isRunning) {
            try {
                val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(settingsIntent)
            } catch (_: Exception) {}
            showServiceDeadNotification(context)
        }
    }

    private fun showServiceDeadNotification(context: Context) {
        try {
            val channelId = "rasfocus_service_dead"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(channelId, "RasFocus Service Alert", android.app.NotificationManager.IMPORTANCE_HIGH).apply { description = "Service restart required"; lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC }
                nm.createNotificationChannel(channel)
            }
            val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            val pi = android.app.PendingIntent.getActivity(context, 2001, settingsIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, channelId).setContentTitle("⚠️ RasFocus বন্ধ হয়ে গেছে!").setContentText("Blocking বন্ধ। চালু করতে ট্যাপ করুন।").setSmallIcon(R.drawable.ic_notification_shield).setContentIntent(pi).setAutoCancel(false).setOngoing(true).build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context).setContentTitle("⚠️ RasFocus বন্ধ হয়ে গেছে!").setContentText("Blocking বন্ধ। চালু করতে ট্যাপ করুন।").setSmallIcon(R.drawable.ic_notification_shield).setContentIntent(pi).setAutoCancel(false).setOngoing(true).build()
            }
            nm.notify(9001, notification)
        } catch (_: Exception) {}
    }
}

// ============================================================
// FirebaseKeywordSync stub removed — real implementation lives in
// FirebaseKeywordSync.kt (was causing a Redeclaration compile error).
// ============================================================