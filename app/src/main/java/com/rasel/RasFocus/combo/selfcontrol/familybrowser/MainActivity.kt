package com.rasel.pdfviewer.combo.selfcontrol.familybrowser

/**
 * ============================================================
 * FamilyBrowser — Production-Ready Android Browser
 * ============================================================
 *
 * REQUIRED PERMISSIONS (AndroidManifest.xml):
 * ─────────────────────────────────────────────
 * <uses-permission android:name="android.permission.INTERNET"/>
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
 * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
 *     android:maxSdkVersion="28"/>
 * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
 *     android:maxSdkVersion="32"/>
 * <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
 * <uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
 * <uses-permission android:name="android.permission.DOWNLOAD_WITHOUT_NOTIFICATION"/>
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
 * <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
 * <uses-permission android:name="android.permission.VIBRATE"/>
 * <uses-permission android:name="android.permission.CAMERA"/>
 * <uses-permission android:name="android.permission.RECORD_AUDIO"/>
 * <uses-permission android:name="android.permission.PICTURE_IN_PICTURE"/>
 *
 * REQUIRED MANIFEST ATTRIBUTES:
 * ─────────────────────────────────────────────
 * <activity
 *   android:name=".MainActivity"
 *   android:exported="true"
 *   android:configChanges="keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize"
 *   android:hardwareAccelerated="true"
 *   android:launchMode="singleTask"
 *   android:supportsPictureInPicture="true"
 *   android:windowSoftInputMode="adjustResize">
 *   <intent-filter>
 *     <action android:name="android.intent.action.MAIN"/>
 *     <category android:name="android.intent.category.LAUNCHER"/>
 *   </intent-filter>
 *   <intent-filter>
 *     <action android:name="android.intent.action.VIEW"/>
 *     <data android:scheme="http"/>
 *     <data android:scheme="https"/>
 *   </intent-filter>
 * </activity>
 *
 * REQUIRED PROVIDER (for file sharing / downloads):
 * <provider
 *   android:name="androidx.core.content.FileProvider"
 *   android:authorities="${applicationId}.fileprovider"
 *   android:grantUriPermissions="true"
 *   android:exported="false">
 *   <meta-data
 *     android:name="android.support.FILE_PROVIDER_PATHS"
 *     android:resource="@xml/file_paths"/>
 * </provider>
 *
 * PROGUARD RULES (proguard-rules.pro):
 * ─────────────────────────────────────────────
 * -keep class com.familybrowser.** { *; }
 * -keepclassmembers class * extends android.webkit.WebViewClient { *; }
 * -keepclassmembers class * extends android.webkit.WebChromeClient { *; }
 * -keepattributes JavascriptInterface
 * -keepclassmembers class * {
 *     @android.webkit.JavascriptInterface <methods>;
 * }
 *
 * BUILD.GRADLE (app-level) — KEY DEPENDENCIES:
 * ─────────────────────────────────────────────
 * implementation("androidx.compose.ui:ui:1.6.0")
 * implementation("androidx.compose.material3:material3:1.2.0")
 * implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
 * implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
 * implementation("androidx.activity:activity-compose:1.8.2")
 * implementation("androidx.security:security-crypto:1.1.0-alpha06")
 * implementation("androidx.core:core-ktx:1.12.0")
 * implementation("androidx.webkit:webkit:1.10.0")
 * implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
 *
 * VERSION INFO:
 * versionCode = 1
 * versionName = "1.0.0"
 * minSdk = 26 (Android 8.0)
 * targetSdk = 34 (Android 14)
 * compileSdk = 34
 */

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rasel.pdfviewer.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState

// ─── Color Palette (Firefox-inspired) ─────────────────────────────────────────
private val BrandBlue    = Color(0xFF2563EB)   // Legacy — কিছু জায়গায় এখনও ব্যবহার হয়
private val BrandBlueDark = Color(0xFF1D4ED8)
private val SurfaceDark  = Color(0xFF1C1C1E)
private val SurfaceLight = Color(0xFFF2F2F7)
private val OnSurfaceDark = Color(0xFFECECEC)

// Firefox UI Colors
private val FxPurple     = Color(0xFF592ACB)   // Firefox purple accent
private val FxOrange     = Color(0xFFFF9500)   // Firefox orange
private val FxSurface    = Color(0xFF2B2A33)   // Firefox dark toolbar bg
private val FxSurfaceL   = Color(0xFFFFFFFF)   // Firefox light toolbar bg
private val FxBarDark    = Color(0xFF1C1B22)   // Firefox dark address bar
private val FxBarLight   = Color(0xFFF0F0F4)   // Firefox light address bar
private val FxBorder     = Color(0xFF42414D)   // Firefox dark border
private val FxTextDark   = Color(0xFFFBFBFE)   // Firefox dark text
private val FxTextLight  = Color(0xFF15141A)   // Firefox light text

class FamilyBrowserActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Handle results if needed */ }

    // ── File Chooser ───────────────────────────────────────────────────────────
    var filePathCallback: ValueCallback<Array<Uri>>? = null

    val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                // single file
                data.data?.let { arrayOf(it) }
                // multiple files (clipData)
                    ?: data.clipData?.let { clip ->
                        Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    }
            }
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // App আগে থেকে running থাকলে (shortcut ট্যাপ করলে নতুন Activity তৈরি না
        // হয়ে এই instance টাই reuse হতে পারে) — সরাসরি navigate করে দাও, নাহলে
        // onCreate এর one-shot LaunchedEffect(Unit) আর চলবে না।
        intent.getStringExtra("start_url")?.let { url ->
            if (url.isNotBlank()) viewModelInstance?.navigate(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Hardware Acceleration ──────────────────────────────────────────────
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // Edge-to-edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // ── Speed Optimizations ────────────────────────────────────────────────

        // 1. Cookie — third-party cookie enable (login session ঠিক রাখে)
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }

        // 3. WebView warm-up — প্রথম launch এ জিরো delay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.webkit.WebView.enableSlowWholeDocumentDraw()
        }
        // Renderer process আগে থেকে জাগিয়ে রাখা
        val warmup = android.webkit.WebView(this)
        warmup.settings.javaScriptEnabled = true
        warmup.destroy()

        requestRuntimePermissions()

        setContent {
            BrowserApp()
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) requestPermissionLauncher.launch(needed.toTypedArray())
    }

    // ══════════════════════════════════════════════════════════════════════
    // FIX 2: Lock screen এ audio বন্ধ হওয়ার সমস্যার সমাধান
    //
    // আগের সমস্যা:
    //   onPause()  → startBgAudioService()   ← ঠিক আছে
    //   onResume() → stopBgAudioService()    ← ভুল! lock screen থেকে
    //                                           ফিরলেও এটা fire করে
    //                                           এবং service বন্ধ করে দেয়
    //   onStop()   → startBgAudioService()   ← duplicate, onPause এই হয়
    //
    // নতুন logic:
    //   onPause()   → শুধু isActivityVisible = false রাখো
    //   onStop()    → তখনই service start করো (truly background গেলে)
    //   onRestart() → service বন্ধ করো (app সত্যিই foreground এ ফিরলে)
    //   onResume()  → service বন্ধ করো না (lock থেকে ফিরতে পারে)
    // ══════════════════════════════════════════════════════════════════════

    private var isActivityVisible = false

    // Home button → floating window এ যাওয়ার সময় true হয়
    // এই flag থাকলে onPause/onStop এ কোনো JS inject বা service start হবে না
    // কারণ WebView ইতিমধ্যে floating service এ transfer হয়ে গেছে
    internal var isGoingToFloat = false

    override fun onStart() {
        super.onStart()
        isActivityVisible = true
        startInBrowserMediaSession()
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false

        // Floating window এ transfer হচ্ছে — onPause এ কিছু করার দরকার নেই।
        // WebView ইতিমধ্যে floating service এ আছে, JS inject বা service start
        // করলে video pause হয়ে যাবে।
        if (isGoingToFloat) return

        val currentUrl = viewModelInstance?.currentUrl ?: ""
        val isYouTube = currentUrl.contains("youtube.com") || currentUrl.contains("youtu.be")

        if (isYouTube) {
            // ── Step 1: service start এর আগেই WebView force-play inject ───
            // Lock screen হলে onPause → onStop নয়, শুধু onPause।
            // তাই এখানেই JS inject করতে হবে।
            viewModelInstance?.activeWebView?.let { wv ->
                wv.post {
                    wv.resumeTimers()
                    wv.onResume()
                    // Delay ছাড়াই inject — YouTube pause হওয়ার আগেই
                    wv.evaluateJavascript("""
                        (function() {
                            try {
                                Object.defineProperty(document, 'hidden', {
                                    get: function() { return false; }, configurable: true
                                });
                                Object.defineProperty(document, 'visibilityState', {
                                    get: function() { return 'visible'; }, configurable: true
                                });
                                Object.defineProperty(document, 'webkitHidden', {
                                    get: function() { return false; }, configurable: true
                                });
                                Object.defineProperty(document, 'webkitVisibilityState', {
                                    get: function() { return 'visible'; }, configurable: true
                                });
                                // video force play
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    try { videos[i].play().catch(function(){}); } catch(e) {}
                                }
                                var p = document.getElementById('movie_player');
                                if (p && typeof p.playVideo === 'function') p.playVideo();
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                }
            }
            // ── Step 2: AudioFocus ধরে রাখার জন্য service start ─────────
            startBgAudioService()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        // Unlock হলে video force play করো
        viewModelInstance?.activeWebView?.let { wv ->
            wv.post {
                wv.resumeTimers()
                wv.onResume()
                wv.postDelayed({
                    wv.evaluateJavascript("""
                        (function() {
                            try {
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    try {
                                        if (videos[i].paused) videos[i].play().catch(function(){});
                                    } catch(e) {}
                                }
                                var p = document.getElementById('movie_player');
                                if (p && typeof p.playVideo === 'function') p.playVideo();
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                }, 100)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // App সত্যিই background থেকে foreground এ ফিরলে (task switcher থেকে)
        // এই method call হয়। YouTube চলছে না হলে background service বন্ধ করো।
        if (!isFinishing) {
            val currentUrl = viewModelInstance?.currentUrl ?: ""
            val isYouTube = currentUrl.contains("youtube.com") || currentUrl.contains("youtu.be")
            if (!isYouTube) stopBgAudioService()
        }
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        // Floating window এ transfer হচ্ছে — service start করলে WebView disturb হবে
        if (isGoingToFloat) {
            isGoingToFloat = false  // flag reset
            return
        }
        // onStop মানে app সত্যিই অদৃশ্য হয়ে গেছে (home press, অন্য app)
        // lock screen এ onStop call হয় না — তাই এখানে service start করা safe
        startBgAudioService()
    }

    override fun onDestroy() {
        // ── Auto Delete on Exit ────────────────────────────────────────────
        // autoDeleteOnExit on থাকলে: সব tabs close করো, cache/history মুছো
        // Cookies রাখা হবে না — সব site থেকে logout হয়ে যাবে
        val vm = viewModelInstance
        if (vm != null) {
            val profile = vm.profileManager.activeProfile.value
            if (profile?.autoDeleteOnExit == true) {
                try {
                    // ── সব tabs বন্ধ করো ──────────────────────────────────
                    vm.tabManager.closeAllTabs()

                    // ── WebView cache মুছো ─────────────────────────────────
                    vm.activeWebView?.clearCache(true)
                    vm.activeWebView?.clearHistory()
                    vm.activeWebView?.clearFormData()

                    // ── History database মুছো ──────────────────────────────
                    vm.profileManager.clearHistory()

                    // ── Cookies মুছো (private tab এর মতো) ─────────────────
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()

                    // ── WebStorage মুছো ────────────────────────────────────
                    android.webkit.WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {}
            }
        }
        stopBgAudioService()
        stopInBrowserMediaSession()
        super.onDestroy()
    }

    // ── Home button press → homeFloatMode অনুযায়ী floating / PiP ──────────────
    // onUserLeaveHint শুধু HOME button এ fire করে।
    // Recent (Recents/Overview) button এ fire করে না — তাই recent এ floating আসবে না।
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        val vm          = viewModelInstance ?: return
        val activeUrl   = vm.currentUrl
        val activeTitle = vm.pageTitle
        val isYouTube   = activeUrl.contains("youtube.com") || activeUrl.contains("youtu.be")
        val floatMode   = vm.homeFloatMode

        // NO_FLOAT mode → floating কিছু করো না, সরাসরি return
        if (floatMode == HomeFloatMode.NO_FLOAT) {
            enterPipIfNeeded()
            return
        }

        // FLOAT_YOUTUBE → শুধু YouTube হলে floating
        // FLOAT_ANY_TAB → যেকোনো active tab floating
        val shouldFloat = when (floatMode) {
            HomeFloatMode.FLOAT_YOUTUBE -> isYouTube
            HomeFloatMode.FLOAT_ANY_TAB -> activeUrl.isNotEmpty() && !activeUrl.startsWith("about:")
            HomeFloatMode.NO_FLOAT      -> false
        }

        if (shouldFloat && hasOverlayPermission()) {
            val activeWebView = viewModelInstance?.activeWebView

            if (activeWebView != null) {
                // ── FLAG: onPause/onStop কে জানাও যে floating এ যাচ্ছি ──
                // এতে ওই দুটো lifecycle callback এ কোনো JS inject বা
                // background service start হবে না — video pause হওয়ার risk নেই
                isGoingToFloat = true

                // ── WebView কে active রাখো transfer এর আগেই ──────────────
                activeWebView.resumeTimers()
                activeWebView.onResume()

                // Visibility spoof — page hide হওয়ার আগেই inject করো
                activeWebView.evaluateJavascript("""
                    (function() {
                        try {
                            Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                            Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                            Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
                            Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
                            // force play এখনই — transfer এর আগে
                            var videos = document.querySelectorAll('video');
                            for (var i = 0; i < videos.length; i++) {
                                try { videos[i].play().catch(function(){}); } catch(e) {}
                            }
                            var p = document.getElementById('movie_player');
                            if (p && typeof p.playVideo === 'function') p.playVideo();
                        } catch(e) {}
                    })();
                """.trimIndent(), null)

                // ── KEY: actual WebView pass করো — service নতুন বানাবে না, reload নেই ──
                com.rasel.pdfviewer.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                    .pendingWebView = activeWebView

                com.rasel.pdfviewer.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                    .launchNoReload(this, activeUrl, activeTitle.ifEmpty { "YouTube" })
            } else {
                com.rasel.pdfviewer.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                    .launch(this, activeUrl, activeTitle.ifEmpty { "YouTube" })
            }
        } else {
            enterPipIfNeeded()
        }
    }

    /**
     * URL এ YouTube timestamp parameter যোগ বা replace করো।
     * যেমন: https://m.youtube.com/watch?v=abc123  →  ...&t=42s
     *        https://m.youtube.com/watch?v=abc&t=10s  →  ...&t=42s
     */
    private fun appendOrReplaceTimestamp(url: String, seconds: Long): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val params = uri.queryParameterNames.toList()
            val newQuery = StringBuilder()
            for (key in params) {
                if (key == "t") continue
                val value = uri.getQueryParameter(key) ?: continue
                if (newQuery.isNotEmpty()) newQuery.append("&")
                newQuery.append("$key=${android.net.Uri.encode(value)}")
            }
            if (newQuery.isNotEmpty()) newQuery.append("&")
            newQuery.append("t=${seconds}s")
            uri.buildUpon().encodedQuery(newQuery.toString()).build().toString()
        } catch (e: Exception) {
            url
        }
    }

    fun enterPipIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (isInPictureInPictureMode) return
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .build()
        enterPictureInPictureMode(params)
    }

    override fun onPictureInPictureModeChanged(isInPicture: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPicture, newConfig)
        if (isInPicture) stopBgAudioService()
        // ViewModel কে জানাই — UI hide/show করবে
        viewModelInstance?.isPipMode = isInPicture
    }

    // Overlay permission check — FloatingWindow এর আগে call করতে হয়
    fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.provider.Settings.canDrawOverlays(this)
        else true

    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    // ViewModel reference — PiP state sync এর জন্য
    internal var viewModelInstance: BrowserViewModel? = null

    // ── In-browser YouTube MediaSession ──────────────────────────────────────
    // Floating window এর মতো browser এ YouTube চললেও notification এ
    // Play/Pause control আসবে। JS দিয়ে WebView এর playback state sync হবে।
    private var inBrowserMediaSession: MediaSession? = null
    private var inBrowserIsPlaying = false
    private val mediaHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val mediaStatePoller = object : Runnable {
        override fun run() {
            val vm  = viewModelInstance ?: run { mediaHandler.postDelayed(this, 2000); return }
            val url = vm.currentUrl
            val isYT = url.contains("youtube.com") || url.contains("youtu.be")
            if (!isYT) {
                // YouTube চলছে না — session inactive করো
                inBrowserMediaSession?.setActive(false)
                mediaHandler.postDelayed(this, 3000)
                return
            }
            // YouTube চলছে — playback state poll করো
            vm.activeWebView?.evaluateJavascript("""
                (function() {
                    var v = document.querySelector('video');
                    if (!v) return JSON.stringify({playing: false, title: '', time: 0, duration: 0});
                    return JSON.stringify({
                        playing: !v.paused && !v.ended,
                        title: document.title || '',
                        time: Math.floor(v.currentTime * 1000),
                        duration: Math.floor(v.duration * 1000)
                    });
                })();
            """.trimIndent()) { result ->
                try {
                    val clean = result?.trim('"')?.replace("\\\"", "\"") ?: return@evaluateJavascript
                    val obj   = org.json.JSONObject(clean)
                    val playing  = obj.optBoolean("playing", false)
                    val title    = obj.optString("title", "YouTube").take(60)
                    val timeMs   = obj.optLong("time",     0L)
                    val durMs    = obj.optLong("duration", 0L)

                    if (inBrowserIsPlaying != playing) {
                        inBrowserIsPlaying = playing
                    }
                    updateInBrowserMediaSession(title, playing, timeMs, durMs)
                } catch (_: Exception) {}
            }
            mediaHandler.postDelayed(this, 2000)
        }
    }

    private fun startInBrowserMediaSession() {
        if (inBrowserMediaSession != null) return
        inBrowserMediaSession = MediaSession(this, "InBrowserYT").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    inBrowserIsPlaying = true
                    viewModelInstance?.activeWebView?.evaluateJavascript(
                        "try{document.querySelector('video')?.play();}catch(e){}", null)
                    updateInBrowserMediaSession(
                        viewModelInstance?.pageTitle ?: "YouTube", true, 0, 0)
                }
                override fun onPause() {
                    inBrowserIsPlaying = false
                    viewModelInstance?.activeWebView?.evaluateJavascript(
                        "try{document.querySelector('video')?.pause();}catch(e){}", null)
                    updateInBrowserMediaSession(
                        viewModelInstance?.pageTitle ?: "YouTube", false, 0, 0)
                }
                override fun onSkipToNext() {
                    viewModelInstance?.activeWebView?.evaluateJavascript("""
                        try{
                            var btn=document.querySelector('.ytp-next-button')||
                                    document.querySelector('[aria-label*="next" i]');
                            if(btn)btn.click();
                        }catch(e){}
                    """.trimIndent(), null)
                }
                override fun onSkipToPrevious() {
                    viewModelInstance?.activeWebView?.evaluateJavascript("""
                        try{
                            var v=document.querySelector('video');
                            if(v&&v.currentTime>3)v.currentTime=0;
                            else{
                                var btn=document.querySelector('.ytp-prev-button')||
                                        document.querySelector('[aria-label*="previous" i]');
                                if(btn)btn.click();
                            }
                        }catch(e){}
                    """.trimIndent(), null)
                }
                override fun onSeekTo(pos: Long) {
                    val sec = pos / 1000
                    viewModelInstance?.activeWebView?.evaluateJavascript(
                        "try{document.querySelector('video').currentTime=$sec;}catch(e){}", null)
                }
            })
            setActive(true)
        }
        mediaHandler.post(mediaStatePoller)
    }

    private fun updateInBrowserMediaSession(title: String, playing: Boolean, timeMs: Long, durMs: Long) {
        val session = inBrowserMediaSession ?: return
        session.setActive(true)
        val stateVal = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(stateVal, timeMs, 1f)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO
                ).build()
        )
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE,  title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "YouTube")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durMs)
                .build()
        )
    }

    private fun stopInBrowserMediaSession() {
        mediaHandler.removeCallbacks(mediaStatePoller)
        inBrowserMediaSession?.setActive(false)
        inBrowserMediaSession?.release()
        inBrowserMediaSession = null
    }


    private fun startBgAudioService() {
        val svc = Intent(this, com.rasel.pdfviewer.selfcontrol.familybrowser.service.BackgroundAudioService::class.java).apply {
            putExtra(
                com.rasel.pdfviewer.selfcontrol.familybrowser.service.BackgroundAudioService.EXTRA_TITLE,
                "Playing in background"
            )
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        } catch (_: Exception) {}
    }

    private fun stopBgAudioService() {
        try {
            stopService(Intent(this, com.rasel.pdfviewer.selfcontrol.familybrowser.service.BackgroundAudioService::class.java))
        } catch (_: Exception) {}
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ROOT COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(vm: BrowserViewModel = viewModel()) {
    val isDark = vm.profileManager.activeProfile.value?.darkModeEnabled ?: false
    val activity = LocalContext.current as? FamilyBrowserActivity

    // Activity এর viewModelInstance sync করা — PiP state এর জন্য
    LaunchedEffect(vm) {
        activity?.viewModelInstance = vm
    }

    // ── start_url Intent extra — YouTube বা অন্য URL দিয়ে launch হলে handle করো ──
    LaunchedEffect(Unit) {
        val startUrl = activity?.intent?.getStringExtra("start_url")
        if (!startUrl.isNullOrBlank()) {
            // একটু delay দাও যাতে WebView init হয়
            kotlinx.coroutines.delay(300)
            vm.navigate(startUrl)
        }
    }

    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme(
            primary = BrandBlue,
            surface = SurfaceDark,
            background = Color(0xFF000000)
        ) else lightColorScheme(
            primary = BrandBlue,
            surface = Color.White,
            background = SurfaceLight
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            BrowserScaffold(vm)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCAFFOLD
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScaffold(vm: BrowserViewModel) {
    val tabs = vm.tabManager.tabs
    val activeTab = vm.tabManager.activeTab
    val context = LocalContext.current
    val activity = context as? FamilyBrowserActivity
    val prefs = remember { context.getSharedPreferences("ras_focus_mode", android.content.Context.MODE_PRIVATE) }

    // ── Reader Mode state ─────────────────────────────────────────────────────
    var isReaderMode by remember { mutableStateOf(false) }

    // ── Focus/Block Mode — URL check ──────────────────────────────────────────
    fun isUrlBlocked(url: String): Boolean {
        if (vm.adBlocker.shouldBlockNavigation(url) != null) return true
        val mode = prefs.getString("mode", "none") ?: "none"
        if (mode == "none") return false
        val domain = runCatching {
            java.net.URI(url).host?.removePrefix("www.") ?: ""
        }.getOrElse { "" }
        if (domain.isEmpty() || url.startsWith("about:")) return false

        return when (mode) {
            "block" -> {
                val list = prefs.getStringSet("block_list", emptySet()) ?: emptySet()
                list.any { domain.endsWith(it) || it.endsWith(domain) }
            }
            "allow" -> {
                val list = prefs.getStringSet("allow_list", emptySet()) ?: emptySet()
                list.none { domain.endsWith(it) || it.endsWith(domain) }
            }
            "self_control" -> {
                val locked  = prefs.getBoolean("self_locked", false)
                val endTime = prefs.getLong("self_end_time", 0L)
                if (locked && System.currentTimeMillis() < endTime) {
                    val list = prefs.getStringSet("block_list", emptySet()) ?: emptySet()
                    list.any { domain.endsWith(it) || it.endsWith(domain) }
                } else false
            }
            "parental" -> {
                val list = prefs.getStringSet("block_list", emptySet()) ?: emptySet()
                list.any { domain.endsWith(it) || it.endsWith(domain) }
            }
            else -> false
        }
    }

    // ── Floating window launch ─────────────────────────────────────────────────
    val floatingTabId = vm.floatingTabId
    LaunchedEffect(floatingTabId) {
        val tabId = floatingTabId ?: return@LaunchedEffect
        val tab   = vm.tabManager.tabs.find { it.id == tabId } ?: return@LaunchedEffect
        vm.floatingTabId = null   // reset

        if (activity?.hasOverlayPermission() == true) {
            com.rasel.pdfviewer.selfcontrol.familybrowser.service.FloatingWindowService.launch(
                context, tab.url, tab.title.ifEmpty { "Tab" }
            )
            vm.showTabSwitcher = false
        } else {
            activity?.requestOverlayPermission()
        }
    }

    // ── Overlay panel বন্ধ করা ────────────────────────────────────────────────
    BackHandler(enabled = vm.showTabSwitcher || vm.showMenu || vm.isFindInPage || vm.isAddressBarFocused) {
        when {
            vm.showTabSwitcher -> vm.showTabSwitcher = false
            vm.showMenu -> vm.showMenu = false
            vm.isFindInPage -> vm.toggleFindInPage()
            vm.isAddressBarFocused -> vm.onAddressBarDismissed()
        }
    }

    // ── WebView history back, নাহলে RasFocus এ ফেরা ─────────────────────────
    // ── WebView history back ──────────────────────────────────────────────────
    // YouTube চলছে + history নেই → in-app miniplayer (কোণায় ছোট হবে)
    // অন্য site + history নেই → activity finish
    BackHandler(enabled = !vm.showTabSwitcher && !vm.showMenu) {
        when {
            // MiniPlayer চলছে → expand করো
            vm.isMiniPlayer -> {
                vm.isMiniPlayer = false
            }
            // WebView history আছে → normal back
            vm.activeWebView?.canGoBack() == true -> {
                // YouTube video চলছে কিনা চেক করো — চললে floating এ পাঠাও
                val isYouTubeVideo = (vm.currentUrl.contains("youtube.com/watch") ||
                                      vm.currentUrl.contains("youtu.be/"))
                val hasOverlay = (activity?.hasOverlayPermission() ?: false)

                if (isYouTubeVideo && hasOverlay) {
                    // Step 1: visibility spoof — pause না হোক
                    vm.activeWebView?.evaluateJavascript("""
                        (function() {
                            try {
                                Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                                Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                                Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
                                Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
                                var _origAdd = EventTarget.prototype.addEventListener;
                                EventTarget.prototype.addEventListener = function(type, fn, opts) {
                                    if (type === 'visibilitychange' || type === 'webkitvisibilitychange' ||
                                        type === 'pagehide') return;
                                    return _origAdd.call(this, type, fn, opts);
                                };
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)

                    // Step 2: same WebView floating service এ পাঠাও
                    val currentUrl   = vm.currentUrl
                    val currentTitle = vm.pageTitle.ifEmpty { "YouTube" }
                    vm.activeWebView?.let { wv ->
                        com.rasel.pdfviewer.selfcontrol.familybrowser.service
                            .YoutubeFloatingWindowService.pendingWebView = wv
                    }
                    com.rasel.pdfviewer.selfcontrol.familybrowser.service
                        .YoutubeFloatingWindowService.launchNoReload(
                            context, currentUrl, currentTitle
                        )

                    // Step 3: browser এ normal back যাও — browser পুরনো page এ যাবে
                    vm.goBack()
                } else {
                    vm.goBack()
                }
            }
            // YouTube চলছে + history শেষ → in-app miniplayer
            vm.currentUrl.contains("youtube.com") || vm.currentUrl.contains("youtu.be") -> {
                // Visibility spoof inject করো — pause না হোক
                vm.activeWebView?.evaluateJavascript("""
                    (function() {
                        try {
                            Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                            Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                            Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
                            Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
                vm.miniPlayerTitle = vm.pageTitle.ifEmpty { "YouTube" }
                vm.isMiniPlayer = true
            }
            // অন্য সব ক্ষেত্রে → activity close
            else -> {
                activity?.finish()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {


        if (!vm.isFullscreen) {
            TopBrowserBar(vm)
        }

        // ── Find In Page Bar ──────────────────────────────────────────
        AnimatedVisibility(visible = vm.isFindInPage) {
            FindInPageBar(vm)
        }

        // ── Progress Bar (top এ slim line) ───────────────────────────
        AnimatedVisibility(visible = vm.isLoading) {
            LinearProgressIndicator(
                progress = { vm.loadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = FxOrange,
                trackColor = Color.Transparent
            )
        }

        // ── WebView Area ──────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            // ── WebView Virtualization ──────────────────────────────────────
            // আগে প্রতিটা tab এর জন্য BrowserWebView compose হতো, মানে ১০০টা
            // ট্যাব খুললে ১০০টা real WebView native memory তে বসে থাকতো
            // (শুধু onPause() suppress হতো, object destroy হতো না) — এটাই
            // সবচেয়ে বড় slow/RAM-খেকো কারণ। এখন শুধু active tab + সাম্প্রতিক
            // কয়েকটা (MAX_LIVE_WEBVIEWS, LRU) এর জন্যই WebView compose হয়।
            // বাকি tab suspend থাকে — শুধু thumbnail/title দেখানো হয়, WebView
            // memory তে থাকে না। ফিরে গেলে savedState দিয়ে instant restore হয়।
            val activeTab = vm.tabManager.activeTab
            val liveIds = remember(activeTab?.id, tabs.size) {
                vm.tabManager.idsThatShouldStayLive(activeTab?.id)
            }
            tabs.forEachIndexed { index, tab ->
                if (tab.id !in liveIds) return@forEachIndexed // suspended — compose করার দরকার নাই

                val isActive = index == vm.tabManager.activeTabIndex.value
                // MiniPlayer active থাকলে main WebView hide করো
                // (same instance mini player এ আছে)
                val isMiniActive = isActive && vm.isMiniPlayer
                // FIX (flicker): আগে inactive tab কে requiredSize(1.dp) দিয়ে resize করা হতো —
                // এতে Compose পুরো WebView relayout করতো এবং tab switch করার সময়
                // এক ফ্রেমের জন্য blank/black flash (flicker) দেখা যেতো।
                // এখন size সবসময় fillMaxSize() — শুধু visibility (INVISIBLE/VISIBLE) টগল হয়,
                // relayout হয় না, তাই কোনো flicker হয় না।
                // FIX (slow/lag): active/mini-player tab "alive" থাকে, বাকি live-set এর
                // মধ্যে থাকা inactive tab গুলো আসলে pause হয় — কিন্তু liveIds এর বাইরে
                // থাকা tab গুলো এখন compose-ই হয় না, তাদের WebView instance থাকে না।
                BrowserWebView(
                    tab = tab,
                    vm = vm,
                    isActive = isActive && !isMiniActive,
                    keepAlive = isActive || isMiniActive,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── Block Screen overlay ──────────────────────────────────
            val currentUrl = vm.currentUrl
            val shouldBlock = remember(currentUrl) { isUrlBlocked(currentUrl) }
            if (shouldBlock && !currentUrl.startsWith("about:")) {
                BlockedScreen(
                    url      = currentUrl,
                    onGoBack = { vm.goBack() }
                )
            }

            // ── Reader Mode overlay ───────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = isReaderMode,
                enter   = fadeIn() + slideInVertically { it / 4 },
                exit    = fadeOut()
            ) {
                ReaderModeView(vm, onClose = { isReaderMode = false })
            }

            // ── Overlays ──────────────────────────────────────────────
            if (vm.isAddressBarFocused) {
                AddressBarSuggestions(vm)
            }

            // ── Tab Switcher ──────────────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = vm.showTabSwitcher,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                TabSwitcherScreen(vm)
            }

            // ── In-App MiniPlayer ─────────────────────────────────────
            // Back press করলে YouTube video কোণায় ছোট হয়
            // tap করলে আবার full হয়
            if (vm.isMiniPlayer) {
                InAppMiniPlayer(vm = vm)
            }

            // ── Bookmark saved snack ──────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = vm.showBookmarkDialog,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF2B2A33),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Bookmark, null, tint = FxOrange, modifier = Modifier.size(18.dp))
                        Text("Bookmark saved", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

    // ── Floating Menu ─────────────────────────────────────────────────────────
    if (vm.showMenu) {
        BrowserMenu(vm, onReaderMode = { isReaderMode = !isReaderMode }) { vm.showMenu = false }
    }

    // ── Download Panel ────────────────────────────────────────────────────────
    // Downloads menu item ক্লিক করলে vm.showDownloads = true হয়
    // ModalBottomSheet দিয়ে download list দেখাও
    if (vm.showDownloads) {
        DownloadPanel(vm = vm, onDismiss = { vm.showDownloads = false })
    }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TOP ADDRESS BAR
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBrowserBar(vm: BrowserViewModel) {
    val profile        = vm.profileManager.activeProfile.value
    val focusRequester = remember { FocusRequester() }
    val keyboardCtrl   = LocalSoftwareKeyboardController.current
    val focusMgr       = LocalFocusManager.current
    val isDark         = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // ── Chrome-style: সবসময় black background ────────────────────────────
    val bgColor        = Color(0xFF1A1A1A)
    val context        = LocalContext.current
    val activity       = context as? FamilyBrowserActivity

    Surface(
        color           = bgColor,
        shadowElevation = 0.dp
    ) {
        Column {
            Spacer(Modifier.statusBarsPadding())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ── Home / Back / Forward — তিনটা icon ──────────────────
                if (!vm.isAddressBarFocused) {
                    val canGoBack    = vm.activeWebView?.canGoBack()  ?: false
                    val canGoForward = vm.activeWebView?.canGoForward() ?: false
                    val iconDisabled = Color.White.copy(0.28f)
                    val iconEnabled  = Color.White.copy(0.85f)

                    // Home
                    IconButton(
                        onClick  = { vm.goHome() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Home, null,
                            modifier = Modifier.size(19.dp),
                            tint     = iconEnabled
                        )
                    }

                    // Back
                    if (!vm.currentUrl.startsWith("about:")) {
                        IconButton(
                            onClick  = { if (canGoBack) vm.goBack() },
                            modifier = Modifier.size(32.dp),
                            enabled  = canGoBack
                        ) {
                            Icon(
                                Icons.Default.ArrowBack, null,
                                modifier = Modifier.size(19.dp),
                                tint     = if (canGoBack) iconEnabled else iconDisabled
                            )
                        }

                        // Forward
                        IconButton(
                            onClick  = { if (canGoForward) vm.goForward() },
                            modifier = Modifier.size(32.dp),
                            enabled  = canGoForward
                        ) {
                            Icon(
                                Icons.Default.ArrowForward, null,
                                modifier = Modifier.size(19.dp),
                                tint     = if (canGoForward) iconEnabled else iconDisabled
                            )
                        }
                    }
                }

                // ── Chrome-style address bar — dark pill ──────────────────
                val barBg = if (vm.isAddressBarFocused)
                    Color(0xFF2C2C2E)
                else
                    Color(0xFF2C2C2E)

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape           = RoundedCornerShape(20.dp),
                    color           = barBg,
                    border          = if (vm.isAddressBarFocused)
                        androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF5E5CE6).copy(0.7f))
                    else null,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (vm.isAddressBarFocused) {
                            Icon(Icons.Default.Search, null,
                                modifier = Modifier.size(16.dp),
                                tint     = Color.White.copy(0.5f))
                        } else {
                            Icon(
                                if (vm.isSecureConnection) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                null,
                                modifier = Modifier.size(13.dp),
                                tint     = if (vm.isSecureConnection) Color(0xFF30D158)
                                           else Color(0xFFFF453A)
                            )
                        }

                        if (vm.isAddressBarFocused) {
                            androidx.compose.foundation.text.BasicTextField(
                                value         = vm.addressBarText,
                                onValueChange = { vm.onAddressBarTextChanged(it) },
                                modifier      = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                singleLine    = true,
                                textStyle     = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    color    = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction    = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = { keyboardCtrl?.hide(); vm.onAddressBarSubmitted() }
                                ),
                                cursorBrush   = androidx.compose.ui.graphics.SolidColor(FxPurple),
                                decorationBox = { inner -> inner() }
                            )
                            if (vm.addressBarText.text.isNotEmpty()) {
                                IconButton(
                                    onClick  = { vm.onAddressBarTextChanged(
                                        androidx.compose.ui.text.input.TextFieldValue("")) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White.copy(0.5f))
                                }
                            }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        } else {
                            val displayText = if (vm.currentUrl.startsWith("about:")) ""
                            else {
                                runCatching {
                                    java.net.URI(vm.currentUrl).host
                                        ?.removePrefix("www.") ?: vm.currentUrl
                                }.getOrElse { vm.currentUrl }.take(34)
                            }
                            Text(
                                text       = displayText,
                                modifier   = Modifier.weight(1f).clickable { vm.onAddressBarFocused() },
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color      = if (vm.currentUrl.startsWith("about:"))
                                    Color.White.copy(0.45f)
                                else
                                    Color.White
                            )
                            Icon(
                                if (vm.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                null,
                                modifier = Modifier.size(16.dp).clickable {
                                    if (vm.isLoading) vm.stopLoading() else vm.reload()
                                },
                                tint = Color.White.copy(0.5f)
                            )
                        }
                    }
                }

                // ── Cancel / Tab count + Menu ──────────────────────────────
                if (vm.isAddressBarFocused) {
                    TextButton(
                        onClick        = { keyboardCtrl?.hide(); focusMgr.clearFocus(); vm.onAddressBarDismissed() },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = FxPurple)
                    }
                } else {
                    // ── Float-to-window icon ───────────────────────────────
                    // Click করলে active tab টি floating window এ যাবে।
                    // YouTube হলে reload ছাড়াই YoutubeFloatingWindowService দিয়ে,
                    // অন্য যেকোনো tab হলে FloatingWindowService দিয়ে।
                    IconButton(
                        onClick = {
                            val activeUrl   = vm.currentUrl
                            val activeTitle = vm.pageTitle
                            val isYouTube   = activeUrl.contains("youtube.com") ||
                                              activeUrl.contains("youtu.be")
                            val hasOverlay  = activity?.hasOverlayPermission() ?: false

                            if (!hasOverlay) {
                                activity?.requestOverlayPermission()
                                return@IconButton
                            }

                            if (isYouTube) {
                                // visibility spoof — pause না হোক
                                vm.activeWebView?.evaluateJavascript("""
                                    (function() {
                                        try {
                                            Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                                            Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                                            Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
                                            Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
                                        } catch(e) {}
                                    })();
                                """.trimIndent(), null)
                                // actual WebView pass করো → service reload করবে না
                                vm.activeWebView?.let {
                                    com.rasel.pdfviewer.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                                        .pendingWebView = it
                                }
                                com.rasel.pdfviewer.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                                    .launchNoReload(context, activeUrl, activeTitle.ifEmpty { "YouTube" })
                                // FIX: WebView টা floating window এ চলে গেছে —
                                // browser এ এই tab এর জায়গায় placeholder inject করো
                                // নাহলে screen সম্পূর্ণ সাদা হয়ে যায়।
                                vm.activeWebView?.loadDataWithBaseURL(null, """
                                    <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                                    <style>
                                        body{margin:0;background:#0f0f0f;display:flex;flex-direction:column;
                                             align-items:center;justify-content:center;height:100vh;
                                             font-family:-apple-system,sans-serif;color:#aaa;text-align:center;gap:16px;}
                                        .icon{font-size:64px;opacity:.6;}
                                        .title{font-size:18px;font-weight:600;color:#fff;}
                                        .sub{font-size:14px;color:#888;max-width:260px;line-height:1.5;}
                                    </style></head><body>
                                    <div class="icon">▶️</div>
                                    <div class="title">Playing in floating window</div>
                                    <div class="sub">YouTube is running in the floating player.<br>Tap the floating window to return.</div>
                                    </body></html>
                                """.trimIndent(), "text/html", "UTF-8", null)
                            } else {
                                // non-YouTube tab → FloatingWindowService
                                val activeTab = vm.tabManager.activeTab
                                if (activeTab != null) {
                                    com.rasel.pdfviewer.selfcontrol.familybrowser.service.FloatingWindowService.launch(
                                        context, activeTab.url, activeTab.title.ifEmpty { "Tab" }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Float tab",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(0.75f)
                        )
                    }

                    // Tab count box — Firefox style
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                2.dp,
                                Color.White.copy(0.6f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { vm.showTabSwitcher = !vm.showTabSwitcher },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = vm.tabManager.tabCount.toString(),
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                    // Menu (3-dot)
                    IconButton(onClick = { vm.showMenu = !vm.showMenu }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White)
                    }
                }
            }
        }
    }

    if (vm.showProfileSwitcher) {
        ProfileSwitcherDialog(vm) { vm.showProfileSwitcher = false }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// WEBVIEW COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: BrowserTab,
    vm: BrowserViewModel,
    isActive: Boolean = true,
    keepAlive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = vm.profileManager.activeProfile.value
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val browserActivity = context as? FamilyBrowserActivity

    // FIX (slow/CPU): keepAlive = false হলে এই tab টা সত্যিকারের onPause() পাবে,
    // ফলে JS/animation/video decode বন্ধ হয়ে CPU-GPU বেঁচে যাবে।
    // active বা mini-player tab (keepAlive = true) এর জন্য আগের মতোই
    // pause suppress থাকবে (background audio চালু রাখার জন্য)।
    val keepAliveState = remember { androidx.compose.runtime.mutableStateOf(true) }

    // ─── FIX: Compose AndroidView internally calls webView.onPause() via its own
    // LifecycleObserver — subclass override alone cannot intercept this because
    // Compose calls the method through the View hierarchy, bypassing polymorphism.
    //
    // Solution: register a LifecycleObserver with priority so it fires before
    // Compose's observer. We call resumeTimers() immediately after any pause
    // to restore JS execution and keep audio alive.
    val webViewRef = remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }

    // FIX (virtualization): active হলে LRU timestamp আপডেট করো, যাতে
    // idsThatShouldStayLive() ঠিকমতো বুঝতে পারে কোন tab সাম্প্রতিক ব্যবহৃত।
    LaunchedEffect(isActive) {
        if (isActive) vm.tabManager.touchTab(tab.id)
    }

    // FIX (slow/CPU): tab inactive হলে আসল onPause() কল করো — animation/video/JS timer
    // বন্ধ হয়ে যাবে, অন্য tab গুলো অযথা ব্যাটারি/CPU খাবে না।
    // active বা mini-player (audio) tab হলে suppress থাকবে — আগের আচরণ অক্ষুণ্ণ।
    LaunchedEffect(keepAlive) {
        keepAliveState.value = keepAlive
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (keepAlive) {
            wv.onResume()
        } else {
            wv.onPause()
        }
    }

    // FIX (flicker): visibility টগল হয় AndroidView-র update{} ব্লকে (নিচে),
    // size কখনো বদলায় না — তাই relayout/flicker হয় না।

    DisposableEffect(lifecycle) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                // FIX: এই observer activity-level — আগে এটা সব tab এর জন্যই
                // fire হতো এবং প্রতিটা WebView কে resume + JS inject করতো,
                // ফলে inactive tab গুলো (যেগুলো আমরা ইচ্ছাকৃতভাবে pause করেছি)
                // আবার জেগে উঠে CPU/battery খেতে শুরু করতো। এখন কেবল active/
                // mini-player tab (keepAliveState == true) এর জন্যই চলবে।
                if (!keepAliveState.value) return
                val wv = webViewRef.value ?: return
                wv.post {
                    // ── Step 1: টাইমার আর WebView resume করো ──────────────
                    wv.resumeTimers()
                    wv.onResume()

                    // ── Step 2: YouTube pause হওয়ার আগেই JS inject ────────
                    // delay ছাড়াই — YouTube player এর pause event fire হওয়ার
                    // আগেই visibility spoof + force play করতে হবে
                    wv.evaluateJavascript("""
                        (function() {
                            try {
                                // Visibility spoof — hidden = false সবসময়
                                Object.defineProperty(document, 'hidden', {
                                    get: function() { return false; }, configurable: true
                                });
                                Object.defineProperty(document, 'visibilityState', {
                                    get: function() { return 'visible'; }, configurable: true
                                });
                                Object.defineProperty(document, 'webkitHidden', {
                                    get: function() { return false; }, configurable: true
                                });
                                Object.defineProperty(document, 'webkitVisibilityState', {
                                    get: function() { return 'visible'; }, configurable: true
                                });

                                // visibilitychange event কে block করো
                                // YouTube এই event শুনে pause করে
                                if (!window.__rasVisibilityBlocked) {
                                    window.__rasVisibilityBlocked = true;
                                    var _origAdd = EventTarget.prototype.addEventListener;
                                    EventTarget.prototype.addEventListener = function(type, fn, opts) {
                                        if (type === 'visibilitychange' ||
                                            type === 'webkitvisibilitychange' ||
                                            type === 'pagehide') {
                                            return; // block
                                        }
                                        return _origAdd.call(this, type, fn, opts);
                                    };
                                }

                                // video force play
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    try { videos[i].play().catch(function(){}); } catch(e) {}
                                }

                                // YouTube movie_player API
                                try {
                                    var p = document.getElementById('movie_player');
                                    if (p && typeof p.playVideo === 'function') p.playVideo();
                                } catch(e) {}

                            } catch(e) {}
                        })();
                    """.trimIndent(), null)

                    // ── Step 3: 200ms পরে আবার force play ──────────────────
                    // YouTube অনেক সময় async pause করে — দ্বিতীয়বার নিশ্চিত করো
                    wv.postDelayed({
                        wv.evaluateJavascript("""
                            (function() {
                                try {
                                    var videos = document.querySelectorAll('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        try {
                                            if (videos[i].paused) {
                                                videos[i].play().catch(function(){});
                                            }
                                        } catch(e) {}
                                    }
                                    var p = document.getElementById('movie_player');
                                    if (p && typeof p.playVideo === 'function') p.playVideo();
                                } catch(e) {}
                            })();
                        """.trimIndent(), null)
                    }, 200)

                    // ── Step 4: 600ms পরে তৃতীয়বার — BackgroundAudioService
                    // start হওয়ার পরে final force play
                    wv.postDelayed({
                        wv.evaluateJavascript("""
                            (function() {
                                try {
                                    var videos = document.querySelectorAll('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        try {
                                            if (videos[i].paused) {
                                                videos[i].play().catch(function(){});
                                            }
                                        } catch(e) {}
                                    }
                                    var p = document.getElementById('movie_player');
                                    if (p && typeof p.playVideo === 'function') p.playVideo();
                                } catch(e) {}
                            })();
                        """.trimIndent(), null)
                    }, 600)
                }
            }

            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                // FIX: এটাও activity-level — inactive tab গুলোকে app foreground
                // হওয়ার সময় জোর করে resume করানো ঠিক নয়, তাই keepAliveState
                // অনুযায়ী filter করা হলো (active/mini-player tab ছাড়া বাকিরা
                // pause-অবস্থায় থাকবে, যতক্ষণ না সেই tab টা সিলেক্ট করা হয়)।
                if (!keepAliveState.value) return
                val wv = webViewRef.value ?: return
                wv.resumeTimers()
                wv.onResume()
                wv.postDelayed({
                    wv.evaluateJavascript("""
                        (function() {
                            try {
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    try {
                                        if (videos[i].paused) videos[i].play().catch(function(){});
                                    } catch(e) {}
                                }
                                var p = document.getElementById('movie_player');
                                if (p && typeof p.playVideo === 'function') p.playVideo();
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                }, 100)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            object : WebView(ctx) {
                // FIX: আগে onPause() পুরোপুরি suppress করা থাকতো — মানে কোনো tab
                // (active/inactive নির্বিশেষে) কখনোই আসলে pause হতো না, ফলে background
                // tab গুলোও সারাক্ষণ render/JS চালাতে থাকতো (= slow + battery drain)।
                // এখন keepAliveState অনুযায়ী সিদ্ধান্ত হয়: audio চালু রাখতে হলে (active/
                // mini-player tab) suppress, বাকি সব tab তে আসল pause।
                override fun onPause() {
                    if (keepAliveState.value) {
                        /* suppressed — background audio চালু রাখার জন্য */
                    } else {
                        super.onPause()
                    }
                }
                override fun onResume() { super.onResume() }
            }.apply {
                webViewRef.value = this
                visibility = if (isActive) View.VISIBLE else View.INVISIBLE
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // ── Hardware Acceleration ──────────────────────────────────
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                // ── WebSettings ────────────────────────────────────────────
                settings.apply {
                    javaScriptEnabled = profile?.javaScriptEnabled ?: true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    loadsImagesAutomatically = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSaveFormData(!tab.isIncognito)
                    setSavePassword(!tab.isIncognito)

                    // ── Private Tab: cache বন্ধ করো ───────────────────────
                    // Incognito হলে কোনো cache disk এ লেখা হবে না
                    cacheMode = if (tab.isIncognito)
                        WebSettings.LOAD_NO_CACHE
                    else
                        WebSettings.LOAD_DEFAULT   // নেট থাকলে fresh, না থাকলে cache

                    // ── Rendering Performance ──────────────────────────────
                    // NOTE: setRenderPriority()/setEnableSmoothTransition() modern
                    // Chromium WebView (M50+) এ no-op — তাই বাদ দেওয়া হলো, effect
                    // ছিল না, শুধু dead code ছিল।

                    // ── Offscreen pre-raster (scroll smooth করে) ──────────
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
                        WebSettingsCompat.setOffscreenPreRaster(this, true)
                    }

                    // ── User Agent — Chrome 136 latest (June 2026) ────────────
                    // Chrome 136 = current stable. এই UA দিলে sites WebView বলে
                    // detect করতে পারে না, real Chrome মনে করে।
                    userAgentString = when {
                        profile?.desktopModeEnabled == true ->
                            // Desktop mode: Windows Chrome UA
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
                        else ->
                            // Mobile: Pixel 9 Pro + Chrome 136
                            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.125 Mobile Safari/537.36"
                    }
                }

                // ── Third-party cookie (login session ঠিক রাখে) ──────────
                android.webkit.CookieManager.getInstance()
                    .setAcceptThirdPartyCookies(this, true)

                // ── Dark Mode: ALGORITHMIC_DARKENING বন্ধ রাখো ────────────
                // কারণ: এটা color invert করে — images, videos, colored UI ভেঙে যায়।
                // পরিবর্তে onPageFinished এ JS দিয়ে CSS inject করা হয়।
                // CSS approach: site নিজে dark support করলে সেটা use করে,
                // না করলে smart filter দিয়ে শুধু background/text বদলায়।
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                }

                // ── WebViewClient ──────────────────────────────────────────
                webViewClient = object : WebViewClient() {

                    // ── WebView Detection Bypass ───────────────────────────────
                    // অনেক site navigator.webdriver, window.chrome check করে।
                    // এই JS inject করলে WebView কে real Chrome মনে করবে।
                    private val bypassJs = """
                        (function() {
                            // webdriver flag সরাও
                            try {
                                Object.defineProperty(navigator, 'webdriver', {
                                    get: () => undefined,
                                    configurable: true
                                });
                            } catch(e) {}

                            // window.chrome inject (Chrome browser এ থাকে, WebView এ থাকে না)
                            if (!window.chrome) {
                                window.chrome = {
                                    app: { isInstalled: false, InstallState: {}, RunningState: {} },
                                    runtime: {
                                        OnInstalledReason: {},
                                        OnRestartRequiredReason: {},
                                        PlatformArch: {},
                                        PlatformNaclArch: {},
                                        PlatformOs: {},
                                        RequestUpdateCheckStatus: {}
                                    }
                                };
                            }

                            // plugins array (real browser এ থাকে)
                            try {
                                Object.defineProperty(navigator, 'plugins', {
                                    get: () => [1, 2, 3, 4, 5],
                                    configurable: true
                                });
                            } catch(e) {}

                            // languages
                            try {
                                Object.defineProperty(navigator, 'languages', {
                                    get: () => ['en-US', 'en', 'bn'],
                                    configurable: true
                                });
                            } catch(e) {}

                            // permission query spoof
                            const origQuery = window.navigator.permissions?.query;
                            if (origQuery) {
                                window.navigator.permissions.query = (params) =>
                                    params.name === 'notifications'
                                        ? Promise.resolve({ state: Notification.permission })
                                        : origQuery.call(window.navigator.permissions, params);
                            }
                        })();
                    """.trimIndent()

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        // FIX: প্রতিটা request-এ current profile থেকে পড়ো,
                        // যাতে profile switch বা settings change সঙ্গে সঙ্গে কাজ করে।
                        val currentProfile = vm.profileManager.activeProfile.value
                        val prefs = view.context.getSharedPreferences("browser_settings", android.content.Context.MODE_PRIVATE)
                        val forceAdBlock = prefs.getBoolean("rb_block_ads", false)
                        vm.adBlocker.isAdBlockEnabled = forceAdBlock || (currentProfile?.adBlockEnabled ?: true)
                        vm.adBlocker.isTrackerBlockEnabled = currentProfile?.trackerBlockEnabled ?: true
                        vm.adBlocker.isAdultBlockEnabled = currentProfile?.adultBlockEnabled ?: true

                        // ── YouTube Ad Pruner (uBlock json-prune approach) ──────
                        // /youtubei/v1/player response থেকে adPlacements, playerAds
                        // সরিয়ে দাও — YouTube player ad চালাবেই না
                        if (vm.adBlocker.isAdBlockEnabled) {
                            val pruned = YouTubeAdPruner.interceptPlayerResponse(request)
                            if (pruned != null) return pruned
                        }

                        return vm.adBlocker.shouldBlock(
                            request,
                            isKidsMode = currentProfile?.isKids ?: false,
                            kidsWhitelist = currentProfile?.kidsWhitelist ?: emptySet()
                        ) ?: super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // WebView detection bypass — প্রতিটা page load এ inject
                        view.evaluateJavascript(bypassJs, null)
                        vm.onPageStarted(tab.id, url)

                        // ── YouTube Ad Pruner JS inject (fetch/XHR intercept) ──
                        // Page load শুরু হওয়ার সাথে সাথেই inject করো
                        // যাতে YouTube এর first player call এ আগেই interceptor বসে যায়
                        if (url.contains("youtube.com") || url.contains("youtu.be")) {
                            if (vm.adBlocker.isAdBlockEnabled) {
                                view.evaluateJavascript(
                                    YouTubeAdPruner.getJsInjectScript(), null
                                )
                            }
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        vm.onPageFinished(tab.id, url)
                        vm.onNavStateChanged(tab.id, view.canGoBack(), view.canGoForward())

                        // ── YouTube ad pruner re-inject (SPA navigation এর জন্য) ──
                        if ((url.contains("youtube.com") || url.contains("youtu.be")) &&
                            vm.adBlocker.isAdBlockEnabled) {
                            view.evaluateJavascript(
                                YouTubeAdPruner.getJsInjectScript(), null
                            )
                        }

                        // ── YouTube search box adult block (SPA navigation safe) ──
                        // shouldOverrideUrlLoading() শুধু hard navigation এ call হয়।
                        // YouTube SPA এ user search box এ type করে Enter দিলে JS দিয়ে
                        // navigate হয় — URL intercept হয় না।
                        // এখানে প্রতি page/SPA transition এ JS inject করি যা:
                        // 1) search submit intercept করে safe search force করে
                        // 2) যেকোনো /results URL এ sp= param যোগ করে (এমনকি SPA navigate এও)
                        val adultBlockEnabled = vm.adBlocker.isAdultBlockEnabled
                        if (adultBlockEnabled && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                            view.evaluateJavascript("""
                                (function() {
                                    if (window.__rasYtAdultBlock__) return;
                                    window.__rasYtAdultBlock__ = true;

                                    // ── 1. Search form submit intercept ───────────────
                                    // YouTube search box form ধরো এবং submit এ safe search force করো
                                    function interceptSearchForms() {
                                        var forms = document.querySelectorAll('form');
                                        forms.forEach(function(form) {
                                            if (form.__rasIntercepted__) return;
                                            form.__rasIntercepted__ = true;
                                            form.addEventListener('submit', function(e) {
                                                var action = form.action || '';
                                                if (action.includes('results') || action.includes('search')) {
                                                    var spInput = form.querySelector('input[name="sp"]');
                                                    if (!spInput) {
                                                        spInput = document.createElement('input');
                                                        spInput.type = 'hidden';
                                                        spInput.name = 'sp';
                                                        form.appendChild(spInput);
                                                    }
                                                    spInput.value = 'EgIQAQ==';
                                                }
                                            }, true);
                                        });
                                    }

                                    // ── 2. pushState/replaceState intercept (SPA nav) ─
                                    // YouTube SPA navigation এ URL টা /results?search_query=X
                                    // এ যায় — এটা intercept করে sp= যোগ করো
                                    var _origPush    = history.pushState.bind(history);
                                    var _origReplace = history.replaceState.bind(history);

                                    function addSafeSearch(url) {
                                        try {
                                            var u = new URL(url, location.href);
                                            if ((u.pathname === '/results' || u.pathname === '/search') &&
                                                u.searchParams.get('search_query') &&
                                                !u.searchParams.get('sp')) {
                                                u.searchParams.set('sp', 'EgIQAQ==');
                                                return u.toString();
                                            }
                                        } catch(e) {}
                                        return url;
                                    }

                                    history.pushState = function(state, title, url) {
                                        return _origPush(state, title, url ? addSafeSearch(url) : url);
                                    };
                                    history.replaceState = function(state, title, url) {
                                        return _origReplace(state, title, url ? addSafeSearch(url) : url);
                                    };

                                    // ── 3. Already on results page — reload with sp= ──
                                    if (location.pathname === '/results' &&
                                        location.search.includes('search_query') &&
                                        !location.search.includes('sp=')) {
                                        var safeUrl = addSafeSearch(location.href);
                                        if (safeUrl !== location.href) {
                                            history.replaceState(null, '', safeUrl);
                                        }
                                    }

                                    // ── 4. MutationObserver — dynamic form इnjection ──
                                    new MutationObserver(function() {
                                        interceptSearchForms();
                                    }).observe(document.body || document.documentElement, {
                                        childList: true, subtree: true
                                    });

                                    interceptSearchForms();
                                })();
                            """.trimIndent(), null)
                        }

                        // ─── FIX: Full Background Audio Spoof ─────────────
                        // YouTube ও অন্য site background / lock screen এ গেলে
                        // document.hidden, pagehide, blur দেখে audio pause করে।
                        // সব block করি।
                        view.evaluateJavascript("""
                            (function() {
                                if (window.__rasBgAudioInjected__) return;
                                window.__rasBgAudioInjected__ = true;
                                try {

                                    // ── 1. Page Visibility spoof ──────────────
                                    Object.defineProperty(document, 'hidden', {
                                        get: function() { return false; },
                                        configurable: true
                                    });
                                    Object.defineProperty(document, 'visibilityState', {
                                        get: function() { return 'visible'; },
                                        configurable: true
                                    });
                                    Object.defineProperty(document, 'webkitHidden', {
                                        get: function() { return false; },
                                        configurable: true
                                    });
                                    Object.defineProperty(document, 'webkitVisibilityState', {
                                        get: function() { return 'visible'; },
                                        configurable: true
                                    });

                                    // ── 2. visibilitychange + pagehide block ──
                                    // (blur বাদ দেওয়া হলো — blur ব্লক করলে যেকোনো site এর
                                    // form validation, dropdown close-on-blur, ইত্যাদি
                                    // স্বাভাবিক behavior ভেঙে যেতো)
                                    var _origAdd = EventTarget.prototype.addEventListener;
                                    EventTarget.prototype.addEventListener = function(type, fn, opts) {
                                        if (type === 'visibilitychange' ||
                                            type === 'webkitvisibilitychange' ||
                                            type === 'pagehide') {
                                            return;
                                        }
                                        return _origAdd.call(this, type, fn, opts);
                                    };

                                    // ── 3. video.pause() override — REMOVED ───
                                    // আগে এখানে HTMLMediaElement.prototype.pause কে
                                    // no-op বানিয়ে দেওয়া হতো — এর ফলে যেকোনো site এর
                                    // normal video player এর "pause" বাটনও কাজ করতো না
                                    // (Chrome এ যেটা স্বাভাবিকভাবে কাজ করে)।
                                    // background audio চালু রাখার জন্য visibility/
                                    // event-listener spoof (উপরে) যথেষ্ট — pause()
                                    // override করার দরকার নেই, এবং সেটা সরিয়ে দেওয়া হলো।

                                    // ── 4. MediaSession ───────────────────────
                                    if ('mediaSession' in navigator) {
                                        navigator.mediaSession.setActionHandler('play', function() {
                                            try {
                                                var v = document.querySelector('video');
                                                if (v) v.play();
                                            } catch(e) {}
                                        });
                                        navigator.mediaSession.setActionHandler('pause', function() {});
                                        navigator.mediaSession.setActionHandler('stop', null);
                                    }

                                } catch(e) {}
                            })();
                        """.trimIndent(), null)

                        // ── Remove "Open in App" banner / button ──────────
                        // YouTube mobile এ "Open in YouTube app" banner আসে।
                        // JS দিয়ে সব ধরনের "open app" element সরিয়ে দাও।
                        view.evaluateJavascript("""
                            (function() {
                                if (window.__rasOpenAppRemoverReady__) return;
                                window.__rasOpenAppRemoverReady__ = true;

                                function removeOpenAppElements() {
                                    var selectors = [
                                        '#app-related-ytcutter',
                                        '.ytm-action-button',
                                        'ytm-action-button',
                                        '[class*="open-in-app"]',
                                        '[class*="openInApp"]',
                                        '[class*="open_in_app"]',
                                        '#external-app-banner',
                                        '.external-app-banner',
                                        'ytm-app-related-endscreen-renderer',
                                        '[data-layer="5"]',
                                        '.app-badge-container',
                                        '.ytp-app-related',
                                        'meta[name="apple-itunes-app"]',
                                        'link[rel="alternate"][media]'
                                    ];
                                    selectors.forEach(function(sel) {
                                        try {
                                            document.querySelectorAll(sel).forEach(function(el) {
                                                el.remove();
                                            });
                                        } catch(e) {}
                                    });
                                    try {
                                        var els = document.querySelectorAll('button, a, .yt-spec-button-shape-next, [role="button"]');
                                        els.forEach(function(el) {
                                            var txt = (el.innerText || el.textContent || '').toLowerCase();
                                            if (txt.includes('open app') || txt.includes('open in app') ||
                                                txt.includes('watch in app') || txt.includes('use the app') ||
                                                txt.includes('get the app') || txt.includes('open youtube')) {
                                                el.remove();
                                            }
                                        });
                                    } catch(e) {}
                                }

                                removeOpenAppElements();

                                try {
                                    new MutationObserver(function(mutations) {
                                        mutations.forEach(function(m) {
                                            if (m.addedNodes.length > 0) removeOpenAppElements();
                                        });
                                    }).observe(document.body || document.documentElement, {
                                        childList: true, subtree: true
                                    });
                                } catch(e) {}
                            })();
                        """.trimIndent(), null)

                        // ── DNS Prefetch — page এর links আগে resolve করা ──
                        view.evaluateJavascript("""
                            (function() {
                                var links = document.querySelectorAll('a[href]');
                                var seen = new Set();
                                for (var i = 0; i < Math.min(links.length, 10); i++) {
                                    try {
                                        var h = new URL(links[i].href).hostname;
                                        if (!seen.has(h)) {
                                            seen.add(h);
                                            var l = document.createElement('link');
                                            l.rel = 'dns-prefetch';
                                            l.href = '//' + h;
                                            document.head.appendChild(l);
                                        }
                                    } catch(e) {}
                                }
                            })();
                        """.trimIndent(), null)

                        // ── Dark Mode CSS inject ───────────────────────────
                        // ALGORITHMIC_DARKENING এর বদলে CSS approach:
                        // 1. color-scheme: dark → site নিজে dark support করলে সেটা activate
                        // 2. prefers-color-scheme: dark media query trigger হয়
                        // 3. Image/video invert হয় না — শুধু background/text বদলায়
                        val currentProfile = vm.profileManager.activeProfile.value
                        val prefs = view.context.getSharedPreferences("browser_settings", android.content.Context.MODE_PRIVATE)
                        val forceDark = prefs.getBoolean("rb_force_dark", false)
                        if (forceDark || currentProfile?.darkModeEnabled == true) {
                            view.evaluateJavascript("""
                                (function() {
                                    try {
                                        var styleId = '__ras_dark_mode__';
                                        if (document.getElementById(styleId)) return;

                                        // Step 1: color-scheme meta tag — browser কে জানাও
                                        var meta = document.querySelector('meta[name="color-scheme"]');
                                        if (!meta) {
                                            meta = document.createElement('meta');
                                            meta.name = 'color-scheme';
                                            document.head.appendChild(meta);
                                        }
                                        meta.content = 'dark light';

                                        // Step 2: CSS inject
                                        var style = document.createElement('style');
                                        style.id = styleId;
                                        style.textContent = `
                                            :root {
                                                color-scheme: dark !important;
                                            }
                                            html {
                                                background-color: #141414 !important;
                                            }
                                            /* Site নিজে dark support না করলে এই fallback কাজ করে */
                                            @media (prefers-color-scheme: dark) {
                                                body {
                                                    background-color: #141414 !important;
                                                    color: #e8e8e8 !important;
                                                }
                                            }
                                            /* Image, video, canvas, svg — invert করবো না */
                                            img, video, canvas, svg, picture,
                                            [style*="background-image"] {
                                                filter: none !important;
                                            }
                                        `;
                                        document.head.appendChild(style);

                                        // Step 3: prefers-color-scheme override
                                        // যেসব site JS দিয়ে matchMedia check করে তাদের জন্য
                                        if (!window.__rasDarkOverride) {
                                            window.__rasDarkOverride = true;
                                            var origMatchMedia = window.matchMedia.bind(window);
                                            window.matchMedia = function(query) {
                                                var result = origMatchMedia(query);
                                                if (query.includes('prefers-color-scheme')) {
                                                    return {
                                                        matches: query.includes('dark'),
                                                        media: query,
                                                        onchange: null,
                                                        addListener: function(){},
                                                        removeListener: function(){},
                                                        addEventListener: function(){},
                                                        removeEventListener: function(){},
                                                        dispatchEvent: function(){ return false; }
                                                    };
                                                }
                                                return result;
                                            };
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent(), null)
                        } else {
                            // Dark mode off — inject করা style সরাও
                            view.evaluateJavascript("""
                                (function() {
                                    try {
                                        var s = document.getElementById('__ras_dark_mode__');
                                        if (s) s.remove();
                                        var m = document.querySelector('meta[name="color-scheme"]');
                                        if (m) m.content = 'light dark';
                                    } catch(e) {}
                                })();
                            """.trimIndent(), null)
                        }

                        // Capture thumbnail
                        view.post {
                            val bm = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bm)
                            view.draw(canvas)
                            vm.tabManager.updateTabThumbnail(tab.id, bm)
                        }
                    }

                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        // Show SSL error instead of proceeding
                        handler.cancel()
                        val errorHtml = """<html><body style="font-family:sans-serif;padding:24px;text-align:center;">
                            <h2>⚠️ Connection Not Secure</h2>
                            <p>This site has an SSL certificate error.<br>Your connection may not be private.</p>
                            <button onclick="history.back()">Go Back</button></body></html>"""
                        view.loadData(errorHtml, "text/html", "UTF-8")
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        
                        val prefs = view.context.getSharedPreferences("browser_settings", android.content.Context.MODE_PRIVATE)
                        if (prefs.getBoolean("rb_strict_blacklist", false)) {
                            val host = android.net.Uri.parse(url).host ?: ""
                            val badDomains = listOf("facebook.com", "instagram.com", "tiktok.com", "twitter.com", "x.com", "reddit.com", "youtube.com", "netflix.com")
                            if (badDomains.any { host.contains(it) }) {
                                view.loadDataWithBaseURL(null, "<div style='text-align:center;margin-top:50px;color:white;background:black;height:100vh;'><h1>Blocked</h1><p>Strict Blacklist is ON.</p></div>", "text/html", "UTF-8", null)
                                return true
                            }
                        }

                        if (prefs.getBoolean("rb_whitelist_mode", false)) {
                            val host = android.net.Uri.parse(url).host ?: ""
                            val allowedDomains = listOf("google.com", "wikipedia.org", "github.com", "stackoverflow.com", "medium.com")
                            if (host.isNotEmpty() && !allowedDomains.any { host.contains(it) }) {
                                view.loadDataWithBaseURL(null, "<div style='text-align:center;margin-top:50px;color:white;background:black;height:100vh;'><h1>Blocked</h1><p>Whitelist Mode is ON. This site is not allowed.</p></div>", "text/html", "UTF-8", null)
                                return true
                            }
                        }

                        val currentProfile = vm.profileManager.activeProfile.value
                        if (currentProfile?.adultBlockEnabled == true) {
                            val blocked = vm.adBlocker.shouldBlockNavigation(url)
                            if (blocked != null) {
                                view.loadDataWithBaseURL(null, blocked, "text/html", "UTF-8", null)
                                return true
                            }
                        }

                        // ── 2. Safe Search enforce ────────────────────────────
                        if (vm.adBlocker.isSafeSearchEnabled) {
                            val safeUrl = buildSafeSearchUrl(url)
                            if (safeUrl != null) {
                                view.loadUrl(safeUrl)
                                return true
                            }
                        }

                        // ── 3. YouTube → mobile URL — REMOVED ─────────────────
                        // FIX (flicker): আগে এখানে www.youtube.com এর প্রতিটা link/video
                        // click কে জোর করে m.youtube.com এ loadUrl() দিয়ে পুনরায় লোড করা
                        // হতো — মানে প্রতিবার DOUBLE full page load হতো (প্রথমে original
                        // URL, তারপর redirect reload), আর এই দুইটা reload-এর মাঝে স্ক্রিন
                        // blank হয়ে যেতো — এটাই আসলে "YouTube flicker করে" সমস্যার মূল কারণ।
                        // এই WebView এর User-Agent ইতিমধ্যে mobile Chrome স্পুফ করে রাখা
                        // আছে, তাই youtube.com নিজে থেকেই mobile UI সার্ভ করে — আলাদা করে
                        // m.youtube.com এ redirect করার দরকার নেই।

                        // ── 4. Special schemes ────────────────────────────────
                        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("data:")) {
                            try {
                                val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                                context.startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                return true
                            }
                        }
                        return false
                    }

                    private fun buildSafeSearchUrl(url: String): String? {
                        return try {
                            val uri = android.net.Uri.parse(url)
                            val host = uri.host?.lowercase() ?: return null
                            when {
                                (host == "www.google.com" || host == "google.com") -> {
                                    if (uri.getQueryParameter("q") == null) return null
                                    if (uri.getQueryParameter("safe") == "active") return null
                                    uri.buildUpon().appendQueryParameter("safe", "active").build().toString()
                                }
                                (host == "www.bing.com" || host == "bing.com") -> {
                                    if (uri.getQueryParameter("q") == null) return null
                                    if (uri.getQueryParameter("adlt") == "strict") return null
                                    uri.buildUpon().appendQueryParameter("adlt", "strict").build().toString()
                                }
                                (host == "duckduckgo.com" || host == "www.duckduckgo.com") -> {
                                    if (uri.getQueryParameter("q") == null) return null
                                    if (uri.getQueryParameter("kp") == "1") return null
                                    uri.buildUpon().appendQueryParameter("kp", "1").build().toString()
                                }
                                (host == "www.youtube.com" || host == "m.youtube.com") &&
                                uri.path?.startsWith("/results") == true -> {
                                    if (uri.getQueryParameter("sp") != null) return null
                                    uri.buildUpon().appendQueryParameter("sp", "EgIQAQ%3D%3D").build().toString()
                                }
                                else -> null
                            }
                        } catch (e: Exception) { null }
                    }
                }

                // ── WebChromeClient ────────────────────────────────────────
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        vm.onProgressChanged(tab.id, newProgress)
                    }

                    override fun onReceivedTitle(view: WebView, title: String) {
                        vm.onPageTitleChanged(tab.id, title)
                    }

                    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
                        vm.tabManager.updateTabFavicon(tab.id, icon)
                    }

                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        vm.isFullscreen = true
                        (context as? Activity)?.window?.decorView?.let {
                            (it as? ViewGroup)?.addView(view)
                        }
                    }

                    override fun onHideCustomView() {
                        vm.isFullscreen = false
                    }

                    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                        result.confirm()
                        return true
                    }

                    override fun onPermissionRequest(request: PermissionRequest) {
                        // FIX: আগে এখানে সবসময় deny() করা হতো — ফলে কোনো site এ video call/
                        // WebRTC (camera-mic) কখনোই কাজ করতো না, যেটা Chrome এ normal কাজ করে।
                        // এখন: app কে Android runtime এ যে permission (CAMERA/RECORD_AUDIO)
                        // আগে থেকেই দেওয়া আছে, কেবল সেগুলোই site কে grant করা হবে।
                        val grantable = request.resources.filter { res ->
                            when (res) {
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                        PackageManager.PERMISSION_GRANTED
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                else -> false
                            }
                        }.toTypedArray()

                        if (grantable.isNotEmpty()) {
                            request.grant(grantable)
                        } else {
                            request.deny()
                        }
                    }

                    // ── File Chooser (folder/file import) ─────────────────────
                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams
                    ): Boolean {
                        // আগের pending callback cancel করা
                        browserActivity?.filePathCallback?.onReceiveValue(null)
                        browserActivity?.filePathCallback = filePathCallback

                        val intent = fileChooserParams.createIntent().apply {
                            // multiple file select support
                            if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                        }

                        return try {
                            browserActivity?.fileChooserLauncher?.launch(intent)
                            true
                        } catch (e: Exception) {
                            browserActivity?.filePathCallback?.onReceiveValue(null)
                            browserActivity?.filePathCallback = null
                            false
                        }
                    }
                }

                // ── Download listener ──────────────────────────────────────
                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    // Cookie না পাঠালে অনেক site 403 দেয়
                    val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                    // FIX: vm.currentUrl multi-tab এ ভুল tab এর URL দিতে পারে।
                    // এই WebView এর actual URL নেওয়া হচ্ছে tab.url থেকে।
                    val tabReferer = tab.url.ifEmpty { vm.currentUrl }

                    if (url.startsWith("blob:")) {
                        // blob URL — JS দিয়ে base64 extract করে download করতে হয়
                        evaluateJavascript("""
                            (function() {
                                fetch('$url')
                                    .then(r => r.blob())
                                    .then(blob => {
                                        const reader = new FileReader();
                                        reader.onloadend = function() {
                                            Android.onBlobDownload(reader.result, '$fileName', blob.type);
                                        };
                                        reader.readAsDataURL(blob);
                                    });
                            })();
                        """.trimIndent(), null)
                        // blob download JS bridge আলাদা করে add করতে হবে (BrowserViewModel এ)
                        return@setDownloadListener
                    }

                    vm.downloadManager.startDownload(
                        url = url,
                        fileName = fileName,
                        mimeType = mimetype,
                        userAgent = userAgent,
                        referer = tabReferer,
                        cookies = cookies
                    )
                }

                // ── Register with ViewModel ────────────────────────────────
                vm.registerWebView(tab.id, this)

                // ── Load initial URL / Restore suspended state ─────────────
                // FIX (virtualization): tab টা আগে suspend হয়ে থাকলে (savedState
                // != null) fresh loadUrl() না করে restoreState() দিয়ে আগের
                // scroll position, navigation history (back/forward) সব ফিরিয়ে
                // আনা হয় — user এর কাছে মনে হয় tab টা কখনো বন্ধই হয়নি।
                val saved = tab.savedState
                if (saved != null) {
                    restoreState(saved)
                    tab.savedState = null
                } else if (tab.url != "about:blank") {
                    loadUrl(tab.url)
                } else {
                    loadDataWithBaseURL(null, buildNewTabPage(), "text/html", "UTF-8", null)
                }
            }
        },
        update = { webView ->
            // FIX (flicker): resize এর বদলে visibility টগল — size একই থাকায়
            // Chromium compositor surface relayout হয় না, কোনো flash দেখা যায় না।
            webView.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
        },
        onRelease = { webView ->
            // FIX (virtualization): destroy করার আগে navigation state সেভ করো,
            // যাতে tab টা আবার live-set এ ফিরলে exact আগের অবস্থা থেকে শুরু হয় —
            // শুধু URL reload না, back/forward history সহ পুরো state restore হয়।
            try {
                val bundle = android.os.Bundle()
                webView.saveState(bundle)
                tab.savedState = bundle
            } catch (e: Exception) { /* tab already gone / no history — ignore */ }

            vm.unregisterWebView(tab.id)
            webView.stopLoading()
            webView.clearCache(false)
            webView.destroy()
        },
        modifier = modifier
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// BOTTOM NAV BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BottomNavigationBar(vm: BrowserViewModel) {
    val activeTab    = vm.tabManager.activeTab
    val canGoBack    = activeTab?.canGoBack ?: false
    val canGoForward = activeTab?.canGoForward ?: false
    val context      = LocalContext.current
    val isDark       = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgColor      = if (isDark) FxSurface else FxSurfaceL
    val iconTint     = if (isDark) FxTextDark else FxTextLight
    val iconDisabled = iconTint.copy(alpha = 0.28f)

    Surface(
        color           = bgColor,
        shadowElevation = 0.dp
    ) {
        // top divider line — Firefox style
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(1.dp)
        ) {
            drawLine(
                color       = if (isDark) FxBorder else Color(0xFFCFCFD9),
                start       = androidx.compose.ui.geometry.Offset(0f, 0f),
                end         = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = 1f
            )
        }

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // ── Back ────────────────────────────────────────────────────
            IconButton(onClick = { vm.goBack() }, enabled = canGoBack) {
                Icon(Icons.Default.ArrowBack, null,
                    modifier = Modifier.size(22.dp),
                    tint = if (canGoBack) iconTint else iconDisabled)
            }

            // ── Forward ─────────────────────────────────────────────────
            IconButton(onClick = { vm.goForward() }, enabled = canGoForward) {
                Icon(Icons.Default.ArrowForward, null,
                    modifier = Modifier.size(22.dp),
                    tint = if (canGoForward) iconTint else iconDisabled)
            }

            // ── Home ─────────────────────────────────────────────────────
            IconButton(onClick = {
                val activeUrl   = vm.currentUrl
                val activeTitle = vm.pageTitle
                val isYouTube   = activeUrl.contains("youtube.com") || activeUrl.contains("youtu.be")
                val activity    = context as? FamilyBrowserActivity
                val hasOverlay  = activity?.hasOverlayPermission() ?: false

                if (isYouTube && hasOverlay) {
                    val wv = vm.activeWebView
                    wv?.evaluateJavascript("""
                        (function() {
                            try {
                                Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                                Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                                Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
                                Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                    com.rasel.pdfviewer.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                        .launchNoReload(context, activeUrl, activeTitle.ifEmpty { "YouTube" })
                }
                vm.goHome()
            }) {
                Icon(Icons.Default.Home, null,
                    modifier = Modifier.size(22.dp),
                    tint = iconTint)
            }

            // ── Tab Count box — Firefox style ───────────────────────────
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, iconTint.copy(0.55f), RoundedCornerShape(6.dp))
                    .clickable { vm.showTabSwitcher = !vm.showTabSwitcher },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = vm.tabManager.tabCount.toString(),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color      = iconTint
                )
            }

            // ── Menu (3-dot) ─────────────────────────────────────────────
            IconButton(onClick = { vm.showMenu = !vm.showMenu }) {
                Icon(Icons.Default.MoreVert, null,
                    modifier = Modifier.size(22.dp),
                    tint = iconTint)
            }
        }
    }
}

@Composable
fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon, null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ADDRESS BAR SUGGESTIONS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AddressBarSuggestions(vm: BrowserViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn {
            items(vm.suggestions) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (suggestion.type) {
                                SuggestionType.SEARCH -> vm.navigate(vm.profileManager.buildSearchUrl(suggestion.text))
                                else -> vm.navigate(suggestion.text)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        when (suggestion.type) {
                            SuggestionType.SEARCH -> Icons.Default.Search
                            SuggestionType.URL -> Icons.Default.Link
                            SuggestionType.HISTORY -> Icons.Default.History
                            SuggestionType.BOOKMARK -> Icons.Default.Bookmark
                        },
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(suggestion.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (suggestion.type != SuggestionType.SEARCH) {
                            Text(
                                suggestion.type.name.lowercase(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB SWITCHER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TabSwitcherScreen(vm: BrowserViewModel) {
    val tabs = vm.tabManager.tabs

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${tabs.size} Tab${if (tabs.size != 1) "s" else ""}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.tabManager.closeAllTabs() }) {
                        Text("Close All")
                    }
                    Button(onClick = {
                        vm.openNewTab()
                        vm.showTabSwitcher = false
                    }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }

            // Incognito toggle
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text("All (${vm.tabManager.regularTabCount})") }
                )
                FilterChip(
                    selected = false,
                    onClick = { vm.openNewTab(incognito = true); vm.showTabSwitcher = false },
                    label = { Text("+ Incognito") },
                    leadingIcon = { Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(16.dp)) }
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isActive = tab.id == vm.tabManager.activeTab?.id,
                        onSelect = { vm.switchToTab(tab.id) },
                        onClose = { vm.closeTab(tab.id) },
                        onFloat = { vm.floatingTabId = tab.id }
                    )
                }
            }

            // Restore closed tab
            if (vm.tabManager.closedTabs.isNotEmpty()) {
                TextButton(
                    onClick = { vm.tabManager.restoreClosedTab() },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Icon(Icons.Default.RestoreFromTrash, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore Last Closed Tab")
                }
            }
        }
    }
}

@Composable
fun TabCard(
    tab: BrowserTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onFloat: () -> Unit = {}
) {
    val borderColor = if (isActive) BrandBlue else Color.Transparent
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box {
            // Thumbnail
            tab.thumbnail?.let { bm ->
                Image(
                    bitmap = bm.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(tab.title.take(1).uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }

            // Title overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(8.dp)
            ) {
                Text(
                    tab.title.ifEmpty { "New Tab" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tab.isIncognito) {
                    Text("Incognito", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── Top-right action row: Float + Close ────────────────────────
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                // Floating window button
                IconButton(
                    onClick = onFloat,
                    modifier = Modifier.size(28.dp).padding(4.dp)
                ) {
                    Text("⊞", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp).padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Close, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Active indicator dot
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BrandBlue)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// IN-APP MINI PLAYER
// ═══════════════════════════════════════════════════════════════════════════════
//
// Back press করলে YouTube video এই mini player এ চলে আসে।
// Active WebView কে AndroidView দিয়ে embed করা হয় — same instance, reload নেই।
// Drag করে যেকোনো কোণায় নেওয়া যায়, tap করলে full হয়।
//
@Composable
fun InAppMiniPlayer(vm: BrowserViewModel) {
    // Mini player size
    val miniW = 200.dp
    val miniH = 120.dp

    // Drag offset state
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    val density = androidx.compose.ui.platform.LocalDensity.current

    // Animation — slide in থেকে কোণায়
    val animOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = offsetX,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "miniX"
    )
    val animOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = offsetY,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "miniY"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWPx = with(density) { maxWidth.toPx() }
        val maxHPx = with(density) { maxHeight.toPx() }
        val miniWPx = with(density) { miniW.toPx() }
        val miniHPx = with(density) { miniH.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }

        // প্রথমবার — bottom-right কোণায় রাখো
        if (!initialized) {
            offsetX = maxWPx - miniWPx - marginPx
            offsetY = maxHPx - miniHPx - marginPx
            initialized = true
        }

        Box(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        animOffsetX.toInt(),
                        animOffsetY.toInt()
                    )
                }
                .size(miniW, miniH)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0F0F))
                // Drag gesture
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // কোণায় snap করো
                            val snapX = if (offsetX + miniWPx / 2 < maxWPx / 2)
                                marginPx
                            else
                                maxWPx - miniWPx - marginPx
                            val snapY = offsetY.coerceIn(
                                marginPx,
                                maxHPx - miniHPx - marginPx
                            )
                            offsetX = snapX
                            offsetY = snapY
                        },
                        onDrag = { change: PointerInputChange, dragAmount: Offset ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(
                                0f, maxWPx - miniWPx
                            )
                            offsetY = (offsetY + dragAmount.y).coerceIn(
                                0f, maxHPx - miniHPx
                            )
                        }
                    )
                }
                // Tap → full screen এ ফিরে যাও
                .clickable { vm.isMiniPlayer = false }
        ) {
            // ── Active WebView embed ──────────────────────────────────────
            // Same WebView instance — video pause হয় না
            val activeWv = vm.activeWebView
            if (activeWv != null) {
                AndroidView(
                    factory = { activeWv },
                    modifier = Modifier.fillMaxSize(),
                    update = { wv ->
                        // MiniPlayer mode এ touch intercept — শুধু tap capture
                        wv.setOnTouchListener(null)
                    }
                )
            } else {
                // WebView নেই — placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 28.sp)
                }
            }

            // ── Top overlay bar — title + close ──────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vm.miniPlayerTitle.take(18),
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Close button — mini player বন্ধ করো
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0x88000000))
                        .clickable {
                            vm.isMiniPlayer = false
                            // YouTube home এ navigate করো
                            vm.activeWebView?.loadUrl("https://m.youtube.com")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 9.sp)
                }
            }

            // ── Expand icon — bottom right ────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000))
                    .clickable { vm.isMiniPlayer = false },
                contentAlignment = Alignment.Center
            ) {
                Text("⊡", color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BROWSER MENU  (3-dot click)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenu(vm: BrowserViewModel, onReaderMode: () -> Unit = {}, onDismiss: () -> Unit) {
    val profile = vm.profileManager.activeProfile.value
    var showHomeFloatSheet       by remember { mutableStateOf(false) }
    var showSettingsSheet        by remember { mutableStateOf(false) }
    var showBookmarkSheet        by remember { mutableStateOf(false) }
    var showDeleteDialog         by remember { mutableStateOf(false) }
    var showWebsiteControlSheet  by remember { mutableStateOf(false) }
    var showBlockFocusSheet      by remember { mutableStateOf(false) }
    val context             = LocalContext.current

    // ── Dark mode: toggleDarkMode() → profile.darkModeEnabled flip → BrowserApp recompose
    // delay নেই — MaterialTheme সাথে সাথে বদলে যায়
    val isDark          = profile?.darkModeEnabled ?: false
    val isAdsBlocked    = profile?.adBlockEnabled ?: true
    val isShortsHidden  = profile?.youtubeShortsHidden ?: false
    val isDesktopMode   = profile?.desktopModeEnabled ?: false
    val isAutoDelete    = profile?.autoDeleteOnExit ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {

                // ── Privacy stats ─────────────────────────────────────────
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        PrivacyStat("🛡️ Ads", vm.adsBlockedCount.toString())
                        PrivacyStat("👁️ Trackers", vm.trackersBlockedCount.toString())
                    }
                }
                Spacer(Modifier.height(6.dp))

                // ══════════════════════════════════════════════════════════
                // ── 1. YouTube & Ads Block (Toggle) ───────────────────────
                // ══════════════════════════════════════════════════════════
                MenuToggleRow(
                    icon    = Icons.Outlined.Block,
                    label   = "YouTube & Ads Block",
                    checked = isAdsBlocked,
                    onToggle = {
                        vm.profileManager.updateProfile(
                            (profile ?: return@MenuToggleRow).copy(adBlockEnabled = !isAdsBlocked)
                        )
                        vm.syncAdBlockerWithProfile()
                    }
                )

                // ══════════════════════════════════════════════════════════
                // ── 2. YouTube Shorts Hide (Toggle) ───────────────────────
                // toggle off → YouTube এ গেলেও, search করলেও Shorts আসবে না
                // JS inject করে Shorts section + button লুকিয়ে দেয়
                // ══════════════════════════════════════════════════════════
                MenuToggleRow(
                    icon    = Icons.Outlined.HideSource,
                    label   = "YouTube Shorts Hide",
                    checked = isShortsHidden,
                    onToggle = {
                        vm.profileManager.updateProfile(
                            (profile ?: return@MenuToggleRow).copy(youtubeShortsHidden = !isShortsHidden)
                        )
                        // Active tab এ সাথে সাথে inject করো
                        vm.applyYoutubeShortsBlock(!isShortsHidden)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                )

                // ── Bookmarks ─────────────────────────────────────────────
                // saved website list দেখাবে — ক্লিক করলে navigate করবে
                MenuRow(Icons.Outlined.Bookmark, "Bookmarks") {
                    showBookmarkSheet = true
                }

                // ── History ───────────────────────────────────────────────
                MenuRow(Icons.Outlined.History, "History") {
                    vm.currentScreen = BrowserScreen.HISTORY; onDismiss()
                }

                // ── Downloads ─────────────────────────────────────────────
                MenuRow(Icons.Outlined.Download, "Downloads") {
                    vm.showDownloads = true; onDismiss()
                }

                // ── Find in Page ──────────────────────────────────────────
                MenuRow(Icons.Outlined.FindInPage, "Find in Page") {
                    vm.toggleFindInPage(); onDismiss()
                }

                // ── Share Page ────────────────────────────────────────────
                MenuRow(Icons.Outlined.Share, "Share Page") {
                    vm.shareCurrentPage(); onDismiss()
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                )

                // ══════════════════════════════════════════════════════════
                // ── 3. Delete Browsing Data (manual one-time) ─────────────
                // ══════════════════════════════════════════════════════════
                MenuRow(Icons.Outlined.DeleteSweep, "Delete Browsing Data") {
                    showDeleteDialog = true
                }

                // ══════════════════════════════════════════════════════════
                // ── 4. Auto Delete on Exit (Toggle) ───────────────────────
                // on থাকলে app বন্ধ হলে: cache, history, সব tabs মুছে যাবে
                // Cookie মুছবে না — sites থেকে logout হবে না
                // ══════════════════════════════════════════════════════════
                MenuToggleRow(
                    icon    = Icons.Outlined.DeleteForever,
                    label   = "Auto Delete on Exit",
                    checked = isAutoDelete,
                    onToggle = {
                        vm.profileManager.updateProfile(
                            (profile ?: return@MenuToggleRow).copy(autoDeleteOnExit = !isAutoDelete)
                        )
                    }
                )

                // ══════════════════════════════════════════════════════════
                // ── 5. Private Tab ────────────────────────────────────────
                // browsing শেষে সব data, cookies সহ auto মুছে যাবে
                // ══════════════════════════════════════════════════════════
                MenuRow(Icons.Outlined.VisibilityOff, "Private Tab") {
                    vm.openNewTab(incognito = true)
                    vm.showTabSwitcher = false
                    onDismiss()
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                )

                // ══════════════════════════════════════════════════════════
                // ── 6. Desktop Mode (Toggle — instant UA change + reload) ─
                // toggle করলে সাথে সাথে page reload হয়ে desktop layout আসবে
                // tab bar ও desktop style এ দেখাবে
                // ══════════════════════════════════════════════════════════
                MenuToggleRow(
                    icon    = Icons.Outlined.OpenInBrowser,
                    label   = "Desktop Mode",
                    checked = isDesktopMode,
                    onToggle = {
                        vm.toggleDesktopMode()
                        // UA change হয়ে গেছে — এখন reload করো
                        vm.reload()
                        onDismiss()
                    }
                )

                // ══════════════════════════════════════════════════════════
                // ── 7. Dark Mode (Toggle — delay ছাড়া instant) ────────────
                // BrowserApp isDark → MaterialTheme সাথে সাথে recompose
                // WebView এ CSS inject করে instant dark/light করা হয়
                // ══════════════════════════════════════════════════════════
                MenuToggleRow(
                    icon    = Icons.Outlined.DarkMode,
                    label   = "Dark Mode",
                    checked = isDark,
                    onToggle = {
                        val turningOn = !isDark
                        vm.toggleDarkMode()

                        // Active WebView এ তাৎক্ষণিক CSS inject করো
                        val wv = vm.activeWebView
                        if (wv != null) {
                            if (turningOn) {
                                wv.evaluateJavascript("""
                                    (function() {
                                        try {
                                            var styleId = '__ras_dark_mode__';
                                            if (document.getElementById(styleId)) return;
                                            var meta = document.querySelector('meta[name="color-scheme"]');
                                            if (!meta) {
                                                meta = document.createElement('meta');
                                                meta.name = 'color-scheme';
                                                document.head.appendChild(meta);
                                            }
                                            meta.content = 'dark light';
                                            var style = document.createElement('style');
                                            style.id = styleId;
                                            style.textContent = `
                                                :root { color-scheme: dark !important; }
                                                html { background-color: #141414 !important; }
                                                @media (prefers-color-scheme: dark) {
                                                    body { background-color: #141414 !important; color: #e8e8e8 !important; }
                                                }
                                                img, video, canvas, svg, picture,
                                                [style*="background-image"] { filter: none !important; }
                                            `;
                                            document.head.appendChild(style);
                                            if (!window.__rasDarkOverride) {
                                                window.__rasDarkOverride = true;
                                                var origMM = window.matchMedia.bind(window);
                                                window.matchMedia = function(q) {
                                                    if (q.includes('prefers-color-scheme')) {
                                                        return { matches: q.includes('dark'), media: q, onchange: null,
                                                            addListener:function(){}, removeListener:function(){},
                                                            addEventListener:function(){}, removeEventListener:function(){},
                                                            dispatchEvent:function(){ return false; } };
                                                    }
                                                    return origMM(q);
                                                };
                                            }
                                        } catch(e) {}
                                    })();
                                """.trimIndent(), null)
                            } else {
                                wv.evaluateJavascript("""
                                    (function() {
                                        try {
                                            var s = document.getElementById('__ras_dark_mode__');
                                            if (s) s.remove();
                                            var m = document.querySelector('meta[name="color-scheme"]');
                                            if (m) m.content = 'light dark';
                                            window.__rasDarkOverride = false;
                                        } catch(e) {}
                                    })();
                                """.trimIndent(), null)
                            }
                        }
                        // onDismiss() call নেই — dialog খোলা রেখেই instant dark/light
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                )

                // ── Website Control (Block / Allow / Lock) ───────────────
                MenuRow(Icons.Outlined.Security, "Website Control") {
                    showWebsiteControlSheet = true
                }

                // ── Block / Focus Mode ────────────────────────────────────
                MenuRow(Icons.Outlined.LockClock, "Block / Focus Mode") {
                    showBlockFocusSheet = true
                    onDismiss()
                }

                // ── Reader Mode ───────────────────────────────────────────
                MenuRow(Icons.Outlined.MenuBook, "Reader Mode") {
                    onReaderMode()
                    onDismiss()
                }

                // ── Settings ──────────────────────────────────────────────
                MenuRow(Icons.Outlined.AddToHomeScreen, "Add to Home Screen") {
                    val currentUrl   = vm.currentUrl
                    val currentTitle = vm.pageTitle.ifEmpty { vm.currentUrl }
                    val favicon      = vm.activeWebView?.favicon
                    createHomeShortcut(context, currentUrl, currentTitle, favicon)
                    onDismiss()
                }

                MenuRow(Icons.Outlined.Settings, "Settings") {
                    showSettingsSheet = true
                }

                // ── Home Button Behavior ──────────────────────────────────
                val homeLabel = when (vm.homeFloatMode) {
                    HomeFloatMode.FLOAT_YOUTUBE -> "Home → Float YouTube only"
                    HomeFloatMode.FLOAT_ANY_TAB -> "Home → Float any tab"
                    HomeFloatMode.NO_FLOAT      -> "Home → No floating"
                }
                MenuRow(
                    icon    = Icons.Outlined.OpenInNew,
                    label   = homeLabel,
                    onClick = { showHomeFloatSheet = true }
                )
            }
        },
        confirmButton = {}
    )

    // ══════════════════════════════════════════════════════════════════════
    // DELETE BROWSING DATA — confirmation dialog
    // Cache + History মুছবে। Cookie মুছবে না।
    // ══════════════════════════════════════════════════════════════════════
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Browsing Data", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("নিচের data মুছে যাবে:")
                    Text("✓ Browsing history", fontSize = 14.sp)
                    Text("✓ Cache & temporary files", fontSize = 14.sp)
                    Text("✓ Form data", fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠️ Cookies মুছবে না — sites থেকে logout হবে না।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearAllBrowsingData(context)
                        showDeleteDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // BOOKMARKS BOTTOM SHEET
    // saved website list — ক্লিক করলে সেই site এ navigate করবে
    // ══════════════════════════════════════════════════════════════════════
    if (showBookmarkSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarkSheet = false }) {
            BookmarkSheetContent(
                vm         = vm,
                onNavigate = { url ->
                    vm.navigate(url)
                    showBookmarkSheet = false
                    onDismiss()
                }
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // WEBSITE CONTROL BOTTOM SHEET
    // ══════════════════════════════════════════════════════════════════════
    if (showWebsiteControlSheet) {
        ModalBottomSheet(onDismissRequest = { showWebsiteControlSheet = false }) {
            WebsiteControlSheetContent(vm = vm, onDismiss = { showWebsiteControlSheet = false })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SETTINGS BOTTOM SHEET
    // ══════════════════════════════════════════════════════════════════════
    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            SettingsSheetContent(vm = vm, onDismiss = { showSettingsSheet = false })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HOME FLOAT MODE BOTTOM SHEET
    // ══════════════════════════════════════════════════════════════════════
    if (showHomeFloatSheet) {
        ModalBottomSheet(onDismissRequest = { showHomeFloatSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Home Button Behavior",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.padding(bottom = 12.dp))

                HomeFloatOption(
                    icon     = "▶",
                    title    = "Float YouTube only",
                    subtitle = "YouTube চললে floating window আসবে",
                    selected = vm.homeFloatMode == HomeFloatMode.FLOAT_YOUTUBE,
                    onClick  = { vm.setHomeFloatMode(HomeFloatMode.FLOAT_YOUTUBE); showHomeFloatSheet = false; onDismiss() }
                )
                Spacer(Modifier.height(8.dp))
                HomeFloatOption(
                    icon     = "⧉",
                    title    = "Float any tab",
                    subtitle = "যেকোনো tab floating window এ যাবে",
                    selected = vm.homeFloatMode == HomeFloatMode.FLOAT_ANY_TAB,
                    onClick  = { vm.setHomeFloatMode(HomeFloatMode.FLOAT_ANY_TAB); showHomeFloatSheet = false; onDismiss() }
                )
                Spacer(Modifier.height(8.dp))
                HomeFloatOption(
                    icon     = "✕",
                    title    = "No floating",
                    subtitle = "Home চাপলে browser সাধারণভাবে background এ যাবে",
                    selected = vm.homeFloatMode == HomeFloatMode.NO_FLOAT,
                    onClick  = { vm.setHomeFloatMode(HomeFloatMode.NO_FLOAT); showHomeFloatSheet = false; onDismiss() }
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // BLOCK / FOCUS MODE BOTTOM SHEET
    // ══════════════════════════════════════════════════════════════════════
    if (showBlockFocusSheet) {
        BlockFocusModeSheet(vm = vm, onDismiss = { showBlockFocusSheet = false })
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS SHEET CONTENT
// সব toggle settings এক জায়গায়
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SettingsSheetContent(vm: BrowserViewModel, onDismiss: () -> Unit) {
    val profile = vm.profileManager.activeProfile.value ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Text(
            "Settings",
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            modifier   = Modifier.padding(bottom = 16.dp)
        )

        // ── Section: YouTube ──────────────────────────────────────────────
        SettingsSectionHeader("YouTube")

        SettingsToggleItem(
            title    = "YouTube Ads Block",
            subtitle = "YouTube video ads বন্ধ করো",
            checked  = profile.adBlockEnabled,
            onToggle = {
                vm.profileManager.updateProfile(profile.copy(adBlockEnabled = it))
                vm.syncAdBlockerWithProfile()
            }
        )

        SettingsToggleItem(
            title    = "YouTube Shorts Hide",
            subtitle = "Shorts tab ও search results থেকে Shorts লুকাও",
            checked  = profile.youtubeShortsHidden,
            onToggle = {
                vm.profileManager.updateProfile(profile.copy(youtubeShortsHidden = it))
                // Active tab YouTube হলে সাথে সাথে inject করো
                vm.applyYoutubeShortsBlock(it)
            }
        )

        Spacer(Modifier.height(12.dp))

        // ── Section: Privacy ──────────────────────────────────────────────
        SettingsSectionHeader("Privacy & Data")

        SettingsToggleItem(
            title    = "Adult Content Block",
            subtitle = "Adult sites redirect করে block করা হবে",
            checked  = profile.adultBlockEnabled,
            onToggle = {
                vm.profileManager.updateProfile(profile.copy(adultBlockEnabled = it))
                vm.syncAdBlockerWithProfile()
            }
        )

        SettingsToggleItem(
            title    = "Tracker Block",
            subtitle = "Tracking scripts block করো",
            checked  = profile.trackerBlockEnabled,
            onToggle = {
                vm.profileManager.updateProfile(profile.copy(trackerBlockEnabled = it))
                vm.syncAdBlockerWithProfile()
            }
        )

        SettingsToggleItem(
            title    = "Auto Delete Data on Exit",
            subtitle = "Browser বন্ধ করলে cache, history ও সব tabs মুছে যাবে। Cookies রাখা হবে না।",
            checked  = profile.autoDeleteOnExit,
            onToggle = {
                vm.profileManager.updateProfile(profile.copy(autoDeleteOnExit = it))
            }
        )

        Spacer(Modifier.height(12.dp))

        // ── Section: Home Shortcut ──────────────────────────────────────────
        SettingsSectionHeader("Home Screen Shortcut")

        val activeTab = vm.tabManager.activeTab
        var shortcutName by remember(activeTab?.id) {
            val defaultName = if (activeTab?.url == "about:blank") "RasBrowser" else (activeTab?.title?.take(20) ?: "Shortcut")
            mutableStateOf(defaultName)
        }
        val context = LocalContext.current

        Text(
            "বর্তমান site টা আলাদা app এর মতো home screen এ pin করো — নাম দিয়ে দাও:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = shortcutName,
            onValueChange = { if (it.length <= 25) shortcutName = it },
            label = { Text("Shortcut Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val url = activeTab?.url ?: return@Button
                if (url == "about:blank") {
                    android.widget.Toast.makeText(context, "আগে কোনো site ওপেন করো", android.widget.Toast.LENGTH_SHORT).show()
                    return@Button
                }
                createHomeShortcut(
                    context = context,
                    url = url,
                    name = shortcutName.ifBlank { "Shortcut" },
                    favicon = activeTab.favicon
                )
                onDismiss()
            },
            enabled = activeTab != null && activeTab.url != "about:blank",
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create Home Shortcut")
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "অথবা পুরো RasBrowser app টাকেই একটা আলাদা icon হিসেবে home screen এ pin করো:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedButton(
            onClick = { createBrowserAppShortcut(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add RasBrowser to Home Screen")
        }

        Spacer(Modifier.height(12.dp))

        // ── Section: JavaScript ───────────────────────────────────────────
        SettingsSectionHeader("Advanced")

        SettingsToggleItem(
            title    = "JavaScript",
            subtitle = "JavaScript disable করলে কিছু site কাজ করবে না",
            checked  = profile.javaScriptEnabled,
            onToggle = {
                vm.profileManager.updateProfile(profile.copy(javaScriptEnabled = it))
            }
        )

        Spacer(Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HOME SCREEN SHORTCUT ("আলাদা app এর মতো" — PWA-style pinned shortcut)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * বর্তমান site কে home screen এ একটা আলাদা icon হিসেবে pin করে দেয়। ট্যাপ করলে
 * সরাসরি সেই URL নিয়ে FamilyBrowserActivity ওপেন হবে (start_url extra দিয়ে,
 * onCreate/onNewIntent এ already handled) — user এর কাছে মনে হবে এটা একটা
 * আলাদা app।
 */
fun createHomeShortcut(context: android.content.Context, url: String, name: String, favicon: android.graphics.Bitmap?) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        android.widget.Toast.makeText(context, "এই device এ shortcut pin সাপোর্ট করে না", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val shortcutIntent = android.content.Intent(context, FamilyBrowserActivity::class.java).apply {
        action = android.content.Intent.ACTION_VIEW
        putExtra("start_url", url)
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val icon = if (favicon != null) {
        IconCompat.createWithBitmap(favicon)
    } else {
        IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    val shortcutId = "site_${url.hashCode()}"
    val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
        .setShortLabel(name.take(10))
        .setLongLabel(name)
        .setIcon(icon)
        .setIntent(shortcutIntent)
        .build()

    try {
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Shortcut তৈরি করা যায়নি: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * RasBrowser কে নিজেকেই আলাদা app icon হিসেবে home screen এ pin করে —
 * click করলে সরাসরি browser (নতুন blank tab) open হবে, কোনো নির্দিষ্ট
 * site নয়। createHomeShortcut() থেকে আলাদা: এখানে start_url পাঠানো হয় না,
 * তাই FamilyBrowserActivity স্বাভাবিক home/new-tab অবস্থায় খোলে।
 */
fun createBrowserAppShortcut(context: android.content.Context) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        android.widget.Toast.makeText(context, "এই device এ shortcut pin সাপোর্ট করে না", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val shortcutIntent = android.content.Intent(context, FamilyBrowserActivity::class.java).apply {
        action = android.content.Intent.ACTION_MAIN
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val shortcut = ShortcutInfoCompat.Builder(context, "rasbrowser_app_shortcut")
        .setShortLabel("RasBrowser")
        .setLongLabel("RasBrowser")
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(shortcutIntent)
        .build()

    try {
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Shortcut তৈরি করা যায়নি: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text     = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    title:    String,
    subtitle: String,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
}

// ═══════════════════════════════════════════════════════════════════════════════
// BOOKMARKS SHEET CONTENT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BookmarkSheetContent(vm: BrowserViewModel, onNavigate: (String) -> Unit) {
    val bookmarks = vm.profileManager.bookmarks

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bookmarks", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // Add current page as bookmark
            if (vm.currentUrl.isNotBlank() && !vm.currentUrl.startsWith("about:")) {
                val alreadySaved = bookmarks.any { it.url == vm.currentUrl }
                TextButton(onClick = {
                    if (!alreadySaved) {
                        vm.profileManager.addBookmark(
                            url   = vm.currentUrl,
                            title = vm.pageTitle.ifEmpty { vm.currentUrl }
                        )
                        vm.showBookmarkDialog = true
                    }
                }) {
                    Icon(
                        if (alreadySaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                        null,
                        tint     = if (alreadySaved) FxOrange else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (alreadySaved) "Saved" else "Save Page")
                }
            }
        }

        if (bookmarks.isEmpty()) {
            Box(
                modifier          = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment  = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔖", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No bookmarks yet",
                        fontSize = 15.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap 'Save Page' to bookmark the current site",
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(bookmarks) { bm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(bm.url) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Favicon placeholder
                        Box(
                            modifier         = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                bm.title.take(1).uppercase(),
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bm.title.ifEmpty { bm.url },
                                fontSize  = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines  = 1,
                                overflow  = TextOverflow.Ellipsis
                            )
                            Text(
                                bm.url,
                                fontSize  = 11.sp,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines  = 1,
                                overflow  = TextOverflow.Ellipsis
                            )
                        }
                        // Delete bookmark
                        IconButton(
                            onClick  = { vm.profileManager.removeBookmarkByUrl(bm.url) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, null,
                                modifier = Modifier.size(14.dp),
                                tint     = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DOWNLOAD PANEL  (3-dot → Downloads ক্লিক করলে এটা খোলে)
// DownloadManager থেকে list নেয়, প্রতিটা item এ file name, size, progress দেখায়
// "Open" বাটন দিয়ে downloaded file খোলা যায়
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPanel(vm: BrowserViewModel, onDismiss: () -> Unit) {
    val context   = LocalContext.current
    val downloads = vm.downloadManager.downloads   // List<DownloadItem> বা similar

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Downloads",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = { vm.downloadManager.clearCompleted() }) {
                        Text("Clear Done", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // ── Empty state ───────────────────────────────────────────────────
            if (downloads.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬇️", fontSize = 44.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No downloads yet",
                            fontSize = 15.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Downloaded files will appear here",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                        )
                    }
                }
            } else {
                // ── Download list ─────────────────────────────────────────────
                LazyColumn(
                    modifier        = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(downloads) { item ->
                        DownloadItemRow(
                            item    = item,
                            context = context,
                            onOpen  = {
                                // File খোলো — Android এর default app দিয়ে
                                try {
                                    val file = java.io.File(item.localPath)
                                    val uri  = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, item.mimeType ?: "*/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // file open করা গেল না — toast দেখাও
                                    android.widget.Toast.makeText(
                                        context, "Cannot open file", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDelete = { vm.downloadManager.removeDownload(item.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DownloadItemRow(
    item:     com.rasel.pdfviewer.selfcontrol.familybrowser.DownloadItem,
    context:  android.content.Context,
    onOpen:   () -> Unit,
    onDelete: () -> Unit
) {
    val isDone   = item.status == com.rasel.pdfviewer.selfcontrol.familybrowser.DownloadStatus.COMPLETED
    val isFailed = item.status == com.rasel.pdfviewer.selfcontrol.familybrowser.DownloadStatus.FAILED
    val progress = if (item.totalBytes > 0) (item.downloadedBytes.toFloat() / item.totalBytes) else 0f

    Surface(
        modifier  = Modifier.fillMaxWidth(),
        color     = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        shape     = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // File type icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDone) BrandBlue.copy(0.12f)
                            else if (isFailed) MaterialTheme.colorScheme.error.copy(0.12f)
                            else MaterialTheme.colorScheme.primary.copy(0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            item.mimeType?.startsWith("video") == true -> "🎬"
                            item.mimeType?.startsWith("audio") == true -> "🎵"
                            item.mimeType?.startsWith("image") == true -> "🖼️"
                            item.mimeType?.contains("pdf")    == true -> "📄"
                            item.mimeType?.contains("zip")    == true -> "📦"
                            else -> "📥"
                        },
                        fontSize = 18.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.fileName.take(32),
                        fontSize  = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            isDone   -> formatFileSize(item.totalBytes)
                            isFailed -> "Download failed"
                            else     -> "${formatFileSize(item.downloadedBytes)} / ${formatFileSize(item.totalBytes)}"
                        },
                        fontSize = 11.sp,
                        color    = when {
                            isFailed -> MaterialTheme.colorScheme.error
                            isDone   -> MaterialTheme.colorScheme.onSurfaceVariant
                            else     -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                // Action buttons
                if (isDone) {
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.OpenInNew, "Open",
                            modifier = Modifier.size(16.dp),
                            tint     = BrandBlue
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close, "Remove",
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                }
            }

            // Progress bar — in-progress downloads এর জন্য
            if (!isDone && !isFailed && item.totalBytes > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress        = { progress },
                    modifier        = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color           = BrandBlue,
                    trackColor      = BrandBlue.copy(0.2f)
                )
            } else if (!isDone && !isFailed) {
                // Unknown size — indeterminate
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color     = BrandBlue,
                    trackColor = BrandBlue.copy(0.2f)
                )
            }
        }
    }
}

/** Bytes কে human-readable string এ convert করো */
private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0        -> "0 B"
    bytes < 1024      -> "$bytes B"
    bytes < 1048576   -> "${bytes / 1024} KB"
    bytes < 1073741824 -> String.format("%.1f MB", bytes / 1048576.0)
    else               -> String.format("%.2f GB", bytes / 1073741824.0)
}


@Composable
fun MenuToggleRow(
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    label:    String,
    checked:  Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked         = checked,
            onCheckedChange = { onToggle() },
            modifier        = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun HomeFloatOption(icon: String, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val bg     = if (selected) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) 2.dp else 0.dp
    val borderColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        color    = bg,
        shape    = RoundedCornerShape(12.dp),
        border   = if (selected) androidx.compose.foundation.BorderStroke(border, borderColor) else null
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(icon, fontSize = 22.sp)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.CheckCircle, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Text(label, fontSize = 15.sp)
    }
}

@Composable
fun PrivacyStat(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FIND IN PAGE BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun FindInPageBar(vm: BrowserViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = vm.findQuery,
                onValueChange = { vm.findNext(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Find in page...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.findNext(vm.findQuery) })
            )
            IconButton(onClick = { vm.findPrevious() }) {
                Icon(Icons.Default.KeyboardArrowUp, null)
            }
            IconButton(onClick = { vm.findNext(vm.findQuery) }) {
                Icon(Icons.Default.KeyboardArrowDown, null)
            }
            IconButton(onClick = { vm.toggleFindInPage() }) {
                Icon(Icons.Default.Close, null)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROFILE SWITCHER DIALOG
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ProfileSwitcherDialog(vm: BrowserViewModel, onDismiss: () -> Unit) {
    val profiles = vm.profileManager.profiles

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    val isActive = profile.id == vm.profileManager.activeProfile.value?.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                vm.profileManager.switchProfile(profile.id)
                                vm.syncAdBlockerWithProfile()
                                onDismiss()
                            }
                            .border(
                                if (isActive) 2.dp else 0.dp,
                                if (isActive) BrandBlue else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(profile.avatar, fontSize = 28.sp)
                            Column {
                                Text(profile.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (profile.type) {
                                        ProfileType.KIDS -> "👶 Kids Mode"
                                        ProfileType.GUEST -> "👤 Guest Mode"
                                        ProfileType.STANDARD -> if (isActive) "Active" else "Standard"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isActive) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.CheckCircle, null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                if (profiles.size < UserProfileManager.MAX_PROFILES) {
                    OutlinedButton(
                        onClick = {
                            vm.profileManager.createProfile("New User", "🙂")
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Profile")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// WEBSITE CONTROL SHEET CONTENT
// Block/Allow individual websites for the active profile
// ═══════════════════════════════════════════════════════════════════════════════

@androidx.compose.runtime.Composable
fun WebsiteControlSheetContent(vm: BrowserViewModel, onDismiss: () -> Unit) {
    var domain by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var message by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "Website Control",
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            modifier   = Modifier.padding(bottom = 12.dp)
        )

        Text(
            "Allow a domain on the Kids profile whitelist.",
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value         = domain,
            onValueChange = { domain = it },
            label         = { Text("Domain (e.g. example.com)") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )

        if (message.isNotEmpty()) {
            Text(
                message,
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val d = domain.trim().lowercase()
                    if (d.isNotEmpty()) {
                        vm.profileManager.addToKidsWhitelist(d)
                        message = "\"$d\" added to Kids whitelist"
                        domain  = ""
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Allow Site") }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) { Text("Close") }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BLOCK / FOCUS MODE SHEET
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockFocusModeSheet(vm: BrowserViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("ras_focus_mode", android.content.Context.MODE_PRIVATE) }

    // ── State ──────────────────────────────────────────────────────────────
    var selectedMode   by remember { mutableStateOf(prefs.getString("mode", "none") ?: "none") }
    // "none" | "block" | "allow" | "self_control" | "parental"

    var blockList      by remember { mutableStateOf(
        (prefs.getStringSet("block_list", emptySet()) ?: emptySet()).toMutableList()
    )}
    var allowList      by remember { mutableStateOf(
        (prefs.getStringSet("allow_list", emptySet()) ?: emptySet()).toMutableList()
    )}
    var newDomain      by remember { mutableStateOf("") }
    var listTab        by remember { mutableStateOf(0) } // 0=block 1=allow

    // Self control — একবার set হলে change হবে না
    var selfHours      by remember { mutableStateOf(prefs.getInt("self_hours", 2)) }
    val selfLocked     by remember { mutableStateOf(prefs.getBoolean("self_locked", false)) }
    var selfEndTime    by remember { mutableStateOf(prefs.getLong("self_end_time", 0L)) }

    // Parental lock PIN
    var parentPin      by remember { mutableStateOf(prefs.getString("parent_pin", "") ?: "") }
    var pinInput       by remember { mutableStateOf("") }
    var parentUnlocked by remember { mutableStateOf(parentPin.isEmpty()) }
    var showPinDialog  by remember { mutableStateOf(false) }
    var pinError       by remember { mutableStateOf(false) }
    var newPin         by remember { mutableStateOf("") }
    var confirmPin     by remember { mutableStateOf("") }

    fun save() {
        prefs.edit()
            .putString("mode", selectedMode)
            .putStringSet("block_list", blockList.toSet())
            .putStringSet("allow_list", allowList.toSet())
            .putInt("self_hours", selfHours)
            .putBoolean("self_locked", selfLocked || selectedMode == "self_control")
            .putLong("self_end_time", selfEndTime)
            .putString("parent_pin", parentPin)
            .apply()
    }

    ModalBottomSheet(
        onDismissRequest = { save(); onDismiss() },
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Title ───────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LockClock, null,
                    modifier = Modifier.size(22.dp),
                    tint = FxPurple)
                Spacer(Modifier.width(8.dp))
                Text("Block / Focus Mode",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            HorizontalDivider()

            // ── Mode selector ────────────────────────────────────────────
            Text("Mode select করুন", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            val modes = listOf(
                Triple("none",        Icons.Outlined.Block,          "Off — কোনো block নেই"),
                Triple("block",       Icons.Outlined.RemoveCircle,   "Block List — এই site গুলো block"),
                Triple("allow",       Icons.Outlined.CheckCircle,    "Allow List — শুধু এই site গুলো চলবে"),
                Triple("self_control",Icons.Outlined.Timer,          "Self Control — নির্দিষ্ট সময় পরে block"),
                Triple("parental",    Icons.Outlined.FamilyRestroom, "Parental Control — PIN দিয়ে lock")
            )

            modes.forEach { (id, icon, label) ->
                val isSelected = selectedMode == id
                val locked = (id == "self_control" && selfLocked) ||
                             (id == "parental" && parentPin.isNotEmpty() && !parentUnlocked)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !locked) {
                            if (id == "parental" && parentPin.isNotEmpty() && !parentUnlocked) {
                                showPinDialog = true
                            } else {
                                selectedMode = id
                            }
                        },
                    color = if (isSelected) FxPurple.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = if (isSelected)
                        androidx.compose.foundation.BorderStroke(1.5.dp, FxPurple)
                    else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(icon, null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isSelected) FxPurple else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label, fontSize = 14.sp,
                            color = if (isSelected) FxPurple else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f))
                        if (locked) Icon(Icons.Default.Lock, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isSelected) Icon(Icons.Default.Check, null,
                            modifier = Modifier.size(16.dp), tint = FxPurple)
                    }
                }
            }

            // ── Block List / Allow List section ──────────────────────────
            if (selectedMode == "block" || selectedMode == "allow") {
                HorizontalDivider()

                val currentList = if (selectedMode == "block") blockList else allowList
                val listLabel   = if (selectedMode == "block") "Block List" else "Allow List"
                val listColor   = if (selectedMode == "block") Color(0xFFFF453A) else Color(0xFF30D158)

                Text(listLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = listColor)

                // Add domain input
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = newDomain,
                        onValueChange = { newDomain = it.lowercase().trim() },
                        placeholder   = { Text("example.com", fontSize = 13.sp) },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            val d = newDomain.removePrefix("https://").removePrefix("http://")
                                .removePrefix("www.").substringBefore("/")
                            if (d.isNotEmpty() && d !in currentList) {
                                if (selectedMode == "block") blockList = (blockList + d).toMutableList()
                                else allowList = (allowList + d).toMutableList()
                            }
                            newDomain = ""
                        })
                    )
                    Button(
                        onClick = {
                            val d = newDomain.removePrefix("https://").removePrefix("http://")
                                .removePrefix("www.").substringBefore("/")
                            if (d.isNotEmpty() && d !in currentList) {
                                if (selectedMode == "block") blockList = (blockList + d).toMutableList()
                                else allowList = (allowList + d).toMutableList()
                            }
                            newDomain = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = listColor)
                    ) { Text("Add") }
                }

                // Current list
                if (currentList.isEmpty()) {
                    Text("কোনো site add করা হয়নি",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        currentList.forEach { domain ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = listColor.copy(0.08f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(domain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            if (selectedMode == "block")
                                                blockList = blockList.filter { it != domain }.toMutableList()
                                            else
                                                allowList = allowList.filter { it != domain }.toMutableList()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null,
                                            modifier = Modifier.size(14.dp),
                                            tint = listColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Self Control section ──────────────────────────────────────
            if (selectedMode == "self_control") {
                HorizontalDivider()
                Text("Self Control", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = Color(0xFFFF9500))

                if (selfLocked) {
                    // Already locked — show remaining time
                    val remaining = (selfEndTime - System.currentTimeMillis()) / 60000
                    Surface(
                        color = Color(0xFFFF9500).copy(0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lock, null,
                                    modifier = Modifier.size(18.dp), tint = Color(0xFFFF9500))
                                Text("Self Control চালু আছে — পরিবর্তন করা যাবে না",
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            if (remaining > 0)
                                Text("বাকি সময়: ${remaining} মিনিট",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else
                                Text("সময় শেষ — browser বন্ধ করুন এবং পুনরায় খুলুন",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Text("একবার set করলে আর change করা যাবে না। সময় শেষ না হওয়া পর্যন্ত block list কাজ করবে।",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Hour picker
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Duration:", fontSize = 14.sp)
                        IconButton(onClick = { if (selfHours > 1) selfHours-- },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Remove, null)
                        }
                        Text("$selfHours hour${if (selfHours > 1) "s" else ""}",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 60.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { if (selfHours < 24) selfHours++ },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, null)
                        }
                    }

                    Button(
                        onClick = {
                            selfEndTime = System.currentTimeMillis() + selfHours * 3600_000L
                            prefs.edit()
                                .putBoolean("self_locked", true)
                                .putLong("self_end_time", selfEndTime)
                                .putString("mode", "self_control")
                                .apply()
                            save()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lock করুন ($selfHours ঘণ্টার জন্য)") }
                }
            }

            // ── Parental Control section ──────────────────────────────────
            if (selectedMode == "parental") {
                HorizontalDivider()
                Text("Parental Control", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = Color(0xFF5E5CE6))

                if (parentPin.isNotEmpty() && !parentUnlocked) {
                    Surface(
                        color = Color(0xFF5E5CE6).copy(0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lock, null,
                                    modifier = Modifier.size(18.dp), tint = Color(0xFF5E5CE6))
                                Text("PIN দিয়ে locked",
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            OutlinedTextField(
                                value         = pinInput,
                                onValueChange = { pinInput = it },
                                label         = { Text("PIN") },
                                singleLine    = true,
                                isError       = pinError,
                                supportingText = if (pinError) ({ Text("ভুল PIN") }) else null,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (pinInput == parentPin) {
                                        parentUnlocked = true
                                        pinError = false
                                        pinInput = ""
                                    } else {
                                        pinError = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Unlock") }
                        }
                    }
                } else {
                    // Unlocked — show PIN setup + block/allow list
                    if (parentPin.isEmpty()) {
                        Text("PIN set করুন যাতে বাচ্চারা settings change করতে না পারে",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value         = newPin,
                                onValueChange = { if (it.length <= 6) newPin = it },
                                label         = { Text("New PIN") },
                                singleLine    = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value         = confirmPin,
                                onValueChange = { if (it.length <= 6) confirmPin = it },
                                label         = { Text("Confirm") },
                                singleLine    = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                if (newPin == confirmPin && newPin.length >= 4) {
                                    parentPin = newPin
                                    parentUnlocked = true
                                    newPin = ""; confirmPin = ""
                                    save()
                                }
                            },
                            enabled  = newPin.length >= 4 && newPin == confirmPin,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("PIN Set করুন") }
                    } else {
                        // Unlocked — show change PIN + site management
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockOpen, null,
                                modifier = Modifier.size(16.dp), tint = Color(0xFF30D158))
                            Spacer(Modifier.width(6.dp))
                            Text("Unlocked", fontSize = 13.sp, color = Color(0xFF30D158))
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { parentPin = ""; save() }) {
                                Text("PIN Remove", fontSize = 12.sp, color = Color(0xFFFF453A))
                            }
                        }
                    }

                    // Block list for parental
                    HorizontalDivider()
                    Text("Block করা Sites", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = Color(0xFFFF453A))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value         = newDomain,
                            onValueChange = { newDomain = it.lowercase().trim() },
                            placeholder   = { Text("example.com", fontSize = 13.sp) },
                            singleLine    = true,
                            modifier      = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val d = newDomain.removePrefix("https://").removePrefix("http://")
                                    .removePrefix("www.").substringBefore("/")
                                if (d.isNotEmpty() && d !in blockList) {
                                    blockList = (blockList + d).toMutableList()
                                }
                                newDomain = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                        ) { Text("Block") }
                    }

                    blockList.forEach { domain ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF453A).copy(0.08f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(domain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { blockList = blockList.filter { it != domain }.toMutableList() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFFFF453A))
                                }
                            }
                        }
                    }
                }
            }

            // ── Save button ──────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { save(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = FxPurple)
            ) { Text("Save & Close") }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BLOCKED SCREEN — user blocked site এ গেলে দেখাবে
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BlockedScreen(url: String, onGoBack: () -> Unit) {
    val domain = runCatching {
        java.net.URI(url).host?.removePrefix("www.") ?: url
    }.getOrElse { url }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            // Block icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF453A).copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Block, null,
                    modifier = Modifier.size(44.dp),
                    tint = Color(0xFFFF453A))
            }

            Text("Site Blocked",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Text(domain,
                fontSize = 15.sp, color = Color(0xFFFF453A),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            Text("এই site টি RasBrowser এ block করা আছে।",
                fontSize = 13.sp,
                color = Color.White.copy(0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onGoBack,
                colors  = ButtonDefaults.buttonColors(containerColor = FxPurple)
            ) {
                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// READER MODE — Article clean view
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ReaderModeView(vm: BrowserViewModel, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val isDark  = vm.profileManager.activeProfile.value?.darkModeEnabled == true

    val bgColor   = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color(0xFFE8E8E8) else Color(0xFF1A1A1A)
    val linkColor = FxPurple

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled  = true
                    webViewClient = object : WebViewClient() {}
                }
            },
            update = { wv ->
                val readerJs = """
                    (function() {
                        var title = document.title || '';
                        var article = '';
                        var selectors = ['article', '[role=main]', 'main', '.post-content',
                            '.entry-content', '.article-body', '.content', '#content', '#main'];
                        for (var i = 0; i < selectors.length; i++) {
                            var el = document.querySelector(selectors[i]);
                            if (el && el.innerText.length > 200) {
                                article = el.innerHTML;
                                break;
                            }
                        }
                        if (!article) article = document.body ? document.body.innerHTML : '';
                        return JSON.stringify({ title: title, body: article });
                    })();
                """.trimIndent()

                vm.activeWebView?.evaluateJavascript(readerJs) { result ->
                    try {
                        val json = org.json.JSONObject(result.trim('"').replace("\\\"","\"")
                            .replace("\\n","").replace("\\\\","\\"))
                        val title = json.optString("title", "")
                        val body  = json.optString("body", "")
                        val bg    = if (isDark) "#1C1C1E" else "#FAF9F6"
                        val fg    = if (isDark) "#E8E8E8" else "#1A1A1A"
                        val html  = """<!DOCTYPE html><html><head>
                            <meta name="viewport" content="width=device-width,initial-scale=1">
                            <style>
                                * { box-sizing: border-box; }
                                body { background: $bg; color: $fg;
                                    font-family: Georgia, 'Times New Roman', serif;
                                    font-size: 18px; line-height: 1.8;
                                    max-width: 680px; margin: 0 auto;
                                    padding: 24px 20px 48px; }
                                h1 { font-size: 24px; font-weight: 700;
                                    line-height: 1.3; margin-bottom: 20px;
                                    border-bottom: 2px solid #5E5CE6; padding-bottom: 12px; }
                                h2,h3 { font-size: 20px; margin: 20px 0 8px; }
                                p { margin: 0 0 16px; }
                                img { max-width: 100%; border-radius: 8px; margin: 12px 0; }
                                a { color: #5E5CE6; }
                                blockquote { border-left: 3px solid #5E5CE6;
                                    margin: 16px 0; padding: 8px 16px;
                                    background: rgba(94,92,230,0.08); border-radius: 0 8px 8px 0; }
                                pre, code { background: rgba(0,0,0,0.15);
                                    padding: 2px 6px; border-radius: 4px; font-size: 14px; }
                                nav, header, footer, aside, .ad, [class*="sidebar"],
                                [class*="banner"], [class*="popup"], [class*="modal"],
                                [id*="sidebar"], [id*="ad"], script, style { display: none !important; }
                            </style>
                            </head><body>
                            <h1>$title</h1>
                            $body
                            </body></html>"""
                        wv.loadDataWithBaseURL(vm.currentUrl, html, "text/html", "UTF-8", null)
                    } catch (e: Exception) {
                        wv.loadData("<html><body style='padding:24px;font-family:sans-serif'>Failed to parse article.</body></html>",
                            "text/html", "UTF-8")
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close reader mode button
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            FloatingActionButton(
                onClick          = { onClose() },
                modifier         = Modifier.size(40.dp),
                containerColor   = FxPurple,
                contentColor     = Color.White
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun buildNewTabPage(): String = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
  <title>New Tab</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }

    body {
      background: #1a1f2e;
      font-family: -apple-system, 'Segoe UI', sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px 20px;
      color: #ffffff;
    }

    /* ── Logo ── */
    .logo {
      margin-bottom: 36px;
      text-align: center;
    }
    .logo h1 {
      font-size: 42px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }
    .logo h1 span.ras  { color: #14C3B2; }
    .logo h1 span.brow { color: #ffffff; }
    .logo p {
      font-size: 12px;
      letter-spacing: 2.5px;
      color: #8a9bb5;
      margin-top: 6px;
      text-transform: uppercase;
    }

    /* ── Search bar ── */
    .search-wrap {
      width: 100%;
      max-width: 560px;
      margin-bottom: 48px;
    }
    .search-box {
      width: 100%;
      display: flex;
      align-items: center;
      background: #252d3d;
      border: 1.5px solid #2e3a50;
      border-radius: 32px;
      padding: 0 20px;
      height: 54px;
      transition: border-color 0.2s;
    }
    .search-box:focus-within {
      border-color: #14C3B2;
    }
    .search-icon {
      color: #6b7a99;
      font-size: 18px;
      margin-right: 12px;
      flex-shrink: 0;
    }
    .search-box input {
      flex: 1;
      background: transparent;
      border: none;
      outline: none;
      font-size: 16px;
      color: #e0e8f5;
      caret-color: #14C3B2;
    }
    .search-box input::placeholder { color: #4a5a78; }

    /* ── Quick links ── */
    .quick-links {
      display: flex;
      gap: 28px;
      flex-wrap: wrap;
      justify-content: center;
    }
    .quick-link {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      text-decoration: none;
    }
    .quick-link .icon-wrap {
      width: 62px;
      height: 62px;
      border-radius: 50%;
      background: #252d3d;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      color: #14C3B2;
      border: 1.5px solid #2e3a50;
      transition: background 0.18s, border-color 0.18s, transform 0.15s;
    }
    .quick-link:hover .icon-wrap {
      background: #1e3040;
      border-color: #14C3B2;
      transform: scale(1.08);
    }
    .quick-link span {
      font-size: 12px;
      color: #8a9bb5;
      font-weight: 500;
    }
  </style>
</head>
<body>

  <!-- Logo -->
  <div class="logo">
    <h1><span class="ras">Ras</span><span class="brow">Browser</span></h1>
    <p>A Powerful &amp; Safe Browsing Experience</p>
  </div>

  <!-- Search -->
  <div class="search-wrap">
    <form class="search-box" onsubmit="doSearch(event)">
      <span class="search-icon">🔍</span>
      <input id="q" type="text" placeholder="Search securely or type a URL" autocomplete="off" autofocus />
    </form>
  </div>

  <!-- Quick links -->
  <div class="quick-links">
    <a class="quick-link" href="https://youtube.com">
      <div class="icon-wrap">▶</div>
      <span>YouTube</span>
    </a>
    <a class="quick-link" href="https://facebook.com">
      <div class="icon-wrap">f</div>
      <span>Facebook</span>
    </a>
    <a class="quick-link" href="https://chat.openai.com">
      <div class="icon-wrap">AI</div>
      <span>ChatGPT</span>
    </a>
    <a class="quick-link" href="https://github.com">
      <div class="icon-wrap">&lt;/&gt;</div>
      <span>GitHub</span>
    </a>
    <a class="quick-link" href="https://wikipedia.org">
      <div class="icon-wrap">W</div>
      <span>Wikipedia</span>
    </a>
  </div>

  <script>
    function doSearch(e) {
      e.preventDefault();
      var q = document.getElementById('q').value.trim();
      if (!q) return;
      var url = (q.startsWith('http://') || q.startsWith('https://') || q.includes('.'))
        ? (q.startsWith('http') ? q : 'https://' + q)
        : 'https://www.google.com/search?q=' + encodeURIComponent(q);
      window.location.href = url;
    }
  </script>
</body>
</html>
""".trimIndent()