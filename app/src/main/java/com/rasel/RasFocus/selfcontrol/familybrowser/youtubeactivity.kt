package com.rasel.RasFocus.selfcontrol.familybrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

/**
 * YoutubeActivity — পুরো native YouTube app এর মতো অভিজ্ঞতা
 */
class YoutubeActivity : ComponentActivity() {

    private var webView: WebView? = null
    // Feed/search এ visible content (thumbnails, titles, alt-text) scan করে
    // adult content ধরার জন্য — AdBlocker.kt এর existing multi-layer scanner
    private val adBlocker by lazy { com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker(this) }
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // FIX: shouldOverrideUrlLoading/shouldInterceptRequest এ URL-level check এ
    // adult content block হয়ে যাওয়ার পরেও onPageFinished এর title-check safety
    // net সেই একই navigation এ আবার নিজে থেকে block page বসিয়ে দিত — ফলে ইউজার
    // পরপর দুইবার black block screen দেখতো। এই flag দিয়ে ট্র্যাক করি যে এই
    // navigation-এ ইতিমধ্যে একবার URL-level এ block হয়েছে কিনা; হলে
    // onPageFinished এর দ্বিতীয় check স্কিপ করে দেয়।
    private var adultBlockAlreadyShownForThisLoad = false

    // Mini player চালু আছে কিনা track করার জন্য
    private var isMiniPlayerActive = false

    // ── LAYER 2: Wake Lock ─────────────────────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Notification Controls Receiver ────────────────────────────────────────
    // BackgroundAudioService থেকে broadcast এসে WebView-এ JS inject করে
    private val playbackControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService.BROADCAST_PLAYBACK_ACTION) return
            val wv = webView ?: return
            when (intent.getStringExtra(com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService.EXTRA_PLAYBACK_CMD)) {
                "play"    -> wv.evaluateJavascript("(function(){ try{ var v=document.querySelector('video'); if(v) v.play().catch(function(){}); }catch(e){} })()", null)
                "pause"   -> wv.evaluateJavascript("(function(){ try{ var v=document.querySelector('video'); if(v) v.pause(); }catch(e){} })()", null)
                "stop"    -> wv.evaluateJavascript("(function(){ try{ var v=document.querySelector('video'); if(v) v.pause(); }catch(e){} })()", null)
                "rewind"  -> wv.evaluateJavascript("(function(){ try{ var v=document.querySelector('video'); if(v) v.currentTime=Math.max(0,v.currentTime-10); }catch(e){} })()", null)
                "forward" -> wv.evaluateJavascript("(function(){ try{ var v=document.querySelector('video'); if(v) v.currentTime=Math.min(v.duration,v.currentTime+10); }catch(e){} })()", null)
                "prev"    -> wv.evaluateJavascript("""
                    (function(){
                        try{
                            var btn = document.querySelector('.ytp-prev-button, [aria-label="Previous video"], .ytm-prev-button');
                            if(btn){ btn.click(); return; }
                            history.back();
                        }catch(e){}
                    })()""".trimIndent(), null)
                "next"    -> wv.evaluateJavascript("""
                    (function(){
                        try{
                            var btn = document.querySelector('.ytp-next-button, [aria-label="Next video"], .ytm-next-button');
                            if(btn){ btn.click(); return; }
                        }catch(e){}
                    })()""".trimIndent(), null)
            }
        }
    }

    // ── LAYER 1: Screen Off Receiver ──────────────────────────────────────────
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val wv = webView
                    if (wv != null && !isMiniPlayerActive) {
                        // ★ FIX: Lock button চাপার আগেই visibility spoof inject করো
                        // যাতে YouTube pause না করে
                        injectVisibilitySpoofBeforeLeave(wv)
                        injectYoutubeHacksForced(wv)
                        wv.resumeTimers()
                        wv.onResume()

                        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            android.provider.Settings.canDrawOverlays(ctx)
                        else true

                        if (hasOverlay) {
                            // ★ FIX: JS check skip করো — সরাসরি floating launch করো
                            // JS async হওয়ায় screen off এর পরে result আসে না নিশ্চিতভাবে
                            // তাই সবসময় floating এ দাও; audio চলতে থাকবে
                            launchFloatingOnLock(wv)
                        } else {
                            // Overlay permission নেই — শুধু audio service
                            startBgAudioService()
                        }
                    } else if (isMiniPlayerActive) {
                        // ★ FIX: Mini player চলাকালীন lock — audio চলতে থাকবে,
                        // floating service ইতিমধ্যে WebView ধরে রেখেছে
                        startBgAudioService()
                    }
                }

                Intent.ACTION_SCREEN_ON -> {
                    // Screen on — user unlock না করা পর্যন্ত কিছু করবো না
                }

                Intent.ACTION_USER_PRESENT -> {
                    // ★ FIX: Unlock করলে floating থেকে WebView ফিরিয়ে আনো
                    // isMiniPlayerActive = true মানে WebView এখন floating service এ আছে
                    // onResume() এ সঠিকভাবে WebView re-attach হবে
                    // এখানে শুধু service থামানো ও flag set করলেই onResume() বাকি কাজ করবে
                    if (isMiniPlayerActive) {
                        // onResume() call হবে যখন activity visible হবে — সেখানেই WebView re-attach হয়
                        // এখানে কিছু করার দরকার নেই; onResume() এ isMiniPlayerActive check করা আছে
                    } else {
                        // Floating ছাড়াই lock হয়েছিল (overlay ছিল না বা video চলছিল না)
                        val wv = webView
                        if (wv != null) {
                            stopBgAudioService()
                            wv.resumeTimers()
                            wv.onResume()
                            wv.visibility = View.VISIBLE
                            wv.alpha = 1f
                        }
                    }
                }
            }
        }
    }

    /**
     * ★ নতুন: Lock button এ floating launch।
     * launchFloatingDirectly এর মতো কিন্তু JS async result এর উপর নির্ভর করে না।
     * WebView সরাসরি service এ পাঠায়, activity background এ যায়।
     */
    private fun launchFloatingOnLock(wv: WebView) {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.provider.Settings.canDrawOverlays(this)
        else true
        if (!hasOverlay) {
            startBgAudioService()
            return
        }

        val currentUrl   = wv.url   ?: "https://m.youtube.com"
        val currentTitle = wv.title ?: "YouTube"

        injectVisibilitySpoofBeforeLeave(wv)

        // ★ FIX: title/videoId BEFORE webView=null — evaluateJavascript
        // needs a live webView reference. Grab everything synchronously
        // from the WebView object we still hold, then null it out.
        val capturedTitle   = wv.title?.removeSuffix(" - YouTube")
                                       ?.removeSuffix(" – YouTube")?.trim()
                               ?: "YouTube — Playing"
        val capturedUrl     = wv.url ?: currentUrl
        val capturedVideoId = try {
            val uri = android.net.Uri.parse(capturedUrl)
            uri.getQueryParameter("v")
                ?: if (uri.host?.contains("youtu.be") == true) uri.pathSegments.firstOrNull()
                else uri.pathSegments.firstOrNull { it.length == 11 }
        } catch (_: Exception) { null }
        val capturedThumb   = if (capturedVideoId != null)
            "https://img.youtube.com/vi/$capturedVideoId/hqdefault.jpg" else null

        // WebView service এ দাও — reload হবে না
        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView = wv
        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.launchNoReload(
            this, currentUrl, currentTitle
        )

        webView = null         // Activity তে reference রাখো না
        isMiniPlayerActive = true

        // Activity কে background এ পাঠাও
        moveTaskToBack(true)

        // ★ FIX: start service with captured values (webView is null now,
        // old evaluateJavascript-based path silently did nothing after null)
        val svc = Intent(
            this,
            com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService::class.java
        ).apply {
            putExtra(com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService.EXTRA_TITLE, capturedTitle)
            putExtra("extra_video_url", capturedUrl)
            if (capturedThumb  != null) putExtra("extra_thumb_url", capturedThumb)
            if (capturedVideoId != null) putExtra("extra_video_id", capturedVideoId)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                startForegroundService(svc)
            else startService(svc)
        } catch (_: Exception) {}
    }

    companion object {
        private const val YT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        // FIX: এই list আগে এখানেই hardcoded ছিল, তাই নতুন keyword যোগ করতে হলে
        // app update লাগতো। এখন এটা FirebaseKeywordSync (Firebase Realtime DB এর
        // keyword_data/adult_keywords node) থেকে আসে — main browser এর AdBlocker
        // এবং FacebookActivity এর সাথেও এখন একই central list শেয়ার হয়, তাই আলাদা
        // আলাদা জায়গায় sync রাখার ঝামেলা নেই। Firebase console এ keyword যোগ/বাদ
        // দিলেই — কোনো app update ছাড়াই — YouTube search bar এ সাথে সাথে reflect হয়।
        private val ADULT_SEARCH_KEYWORDS: Set<String>
            get() = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.getAdultKeywords()

        private val AD_SERVERS = setOf(
            "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", 
            "pubads.g.doubleclick.net", "youtube.com/api/stats/ads", "youtube.com/pagead",
            "googleadservices.com", "adservice.google.com"
        )

        fun launch(activity: Activity) {
            val intent = Intent(activity, YoutubeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.statusBarColor  = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        // FIX (startup speed): black frame টা আগে setContentView দিয়ে স্ক্রিনে
        // বসিয়ে দিচ্ছি, তারপর receiver registration ও wakelock acquire করছি।
        // আগে এই non-UI কাজগুলো setContentView এর আগে হতো, ফলে প্রথম ফ্রেম
        // আঁকতে বাড়তি সময় লাগতো এবং app খুলতে ধীর মনে হতো — এখন ইউজার সাথে
        // সাথেই কালো স্ক্রিন দেখে (blank/white flash এর বদলে), আর WebView এর
        // load শুরু হয় পরের লাইনেই। কোনো ফিচার সরানো হয়নি, শুধু order পাল্টানো।
        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, systemBars.top, 0, systemBars.bottom)
                insets
            }
        }
        setContentView(rootFrame)

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenOffReceiver, screenFilter)

        // Notification controls receiver register করো
        val playbackFilter = IntentFilter(
            com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService.BROADCAST_PLAYBACK_ACTION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackControlReceiver, playbackFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackControlReceiver, playbackFilter)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("WakelockTimeout")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RasFocus:YoutubeAudioWakeLock"
        ).apply { acquire() }

        webView = object : WebView(this) {
            override fun onPause() { /* suppress */ }
            override fun pauseTimers() { /* suppress */ }
            override fun onResume() { super.onResume() }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // FIX: LAYER_TYPE_HARDWARE এখানে সরানো হলো — এটা YouTube-এর HTML5
            // <video> element এর নিজস্ব GPU surface texture-এর সাথে conflict
            // করে অনেক device-এ, ফলে audio চলে কিন্তু video frame black থেকে
            // যায় (compositor ভুল layer draw করে)। LAYER_TYPE_NONE দিলে
            // WebView নিজেই সঠিক hardware compositing বেছে নেয়।
            setLayerType(View.LAYER_TYPE_NONE, null)
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                databaseEnabled                  = true
                loadWithOverviewMode             = true
                useWideViewPort                  = true
                builtInZoomControls              = true
                displayZoomControls              = false
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess                  = true
                allowContentAccess               = true
                loadsImagesAutomatically         = true
                mixedContentMode                 = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode                        = WebSettings.LOAD_DEFAULT
                userAgentString                  = YT_USER_AGENT
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // Block page এর "ফিরে যান" বাটন যাতে সত্যিকারের youtube.com এ ফিরে
            // যেতে পারে — এটা না থাকলে block page দেখানোর পর WebView চিরকালের
            // জন্য আটকে থাকতো, কোনো navigation/back কাজ করতো না।
            addJavascriptInterface(YtBlockBridge(this), "RasYtBlockBridge")
            addJavascriptInterface(
                com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.BlockOverlayBridge(
                    this, "https://m.youtube.com/", { block -> runOnUiThread(block) }
                ),
                "RasBlockBridge"
            )

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // নতুন navigation শুরু — আগের load এর block-flag রিসেট করি
                    adultBlockAlreadyShownForThisLoad = false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectVisibilitySpoof(view)
                    injectYoutubeHacks(view)
                    injectRemoveOpenInAppButton(view)
                    injectAdBlocker(view)
                    injectSettingsRemover(view)
                    adBlocker.injectContentScanner(view)

                    // FIX: এই navigation এ shouldOverrideUrlLoading/shouldInterceptRequest
                    // এ URL-level check করে ইতিমধ্যে একবার block page দেখানো হয়ে থাকলে,
                    // নিচের title-check আর চালানো হয় না — নাহলে একই block পরপর দুইবার
                    // (double black screen) দেখা যেত।
                    if (adultBlockAlreadyShownForThisLoad) return

                    // Second-layer safety net: shouldInterceptRequest only sees the
                    // request URL, not POST body — and YouTube's internal search
                    // sometimes sends the query inside a POST body rather than as a
                    // URL query param, which the network-level check above can't see.
                    // Re-checking the rendered page title after load catches that case,
                    // since a search-results page's title reliably reflects the query
                    // once YouTube's own JS has rendered it.
                    view.evaluateJavascript("(function(){return document.title;})();") { titleResult ->
                        val title = titleResult?.trim('"')?.lowercase() ?: return@evaluateJavascript
                        val matched = ADULT_SEARCH_KEYWORDS.any { title.contains(it.lowercase()) }
                        if (matched && !adultBlockAlreadyShownForThisLoad) {
                            adultBlockAlreadyShownForThisLoad = true
                            val blockedHtml = buildAdultSearchBlockedPage(title)
                            webView?.loadDataWithBaseURL(
                                "https://m.youtube.com/", blockedHtml, "text/html", "UTF-8", null
                            )
                        }
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()

                    if (AD_SERVERS.any { url.contains(it) }) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    // YouTube-এর নিজস্ব search box থেকে search করলে page navigate করে না —
                    // internally একটা XHR/fetch request পাঠায়, যেটা shouldOverrideUrlLoading
                    // এ কখনো পৌঁছায় না (সেটা শুধু full page navigation এ চলে)। এখানে চেক না
                    // থাকলে address bar এ সরাসরি URL টাইপ করলে block হতো, কিন্তু app এর
                    // নিজের search icon দিয়ে search করলে সম্পূর্ণ bypass হয়ে যেত।
                    val adultBlockHtml = checkAdultSearchKeyword(url)
                    if (adultBlockHtml != null) {
                        adultBlockAlreadyShownForThisLoad = true
                        runOnUiThread {
                            webView?.loadDataWithBaseURL(
                                "https://m.youtube.com/", adultBlockHtml, "text/html", "UTF-8", null
                            )
                        }
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()

                    // intent:// বা youtube:// দিয়ে YouTube app খুলতে দেবো না
                    if (url.startsWith("intent://") ||
                        url.startsWith("youtube://") ||
                        url.startsWith("vnd.youtube://") ||
                        url.startsWith("market://")) {
                        return true  // block — কিছুই করবো না
                    }

                    if (!url.startsWith("http://") && !url.startsWith("https://")) return true

                    val adultBlockHtml = checkAdultSearchKeyword(url)
                    if (adultBlockHtml != null) {
                        adultBlockAlreadyShownForThisLoad = true
                        view.loadDataWithBaseURL(
                            "https://m.youtube.com/", adultBlockHtml, "text/html", "UTF-8", null
                        )
                        return true
                    }

                    val safeUrl = buildYoutubeSafeSearchUrl(url)
                    if (safeUrl != null && safeUrl != url) {
                        view.loadUrl(safeUrl)
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) {
                        rootFrame.removeView(customView)
                        callback.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback

                    val ctrl = WindowInsetsControllerCompat(window, window.decorView)
                    ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    rootFrame.setPadding(0, 0, 0, 0)

                    rootFrame.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    webView?.visibility = View.GONE
                }

                override fun onHideCustomView() {
                    webView?.visibility = View.VISIBLE
                    customView?.let { rootFrame.removeView(it) }
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null

                    val ctrl = WindowInsetsControllerCompat(window, window.decorView)
                    ctrl.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    ViewCompat.requestApplyInsets(rootFrame)
                }
            }
            rootFrame.addView(this)
            // ══════════════════════════════════════════════════════════════
            // ★ Low-RAM device fix: process kill এর পর cold-start হলে
            // YoutubeFloatingWindowService এ save করা শেষ URL এ ফেরাও,
            // সবসময় default youtube.com এ না গিয়ে।
            // ══════════════════════════════════════════════════════════════
            val recoveryPrefs = getSharedPreferences("yt_float_recovery", Context.MODE_PRIVATE)
            val wasOpen = recoveryPrefs.getBoolean("was_open", false)
            val recoveredUrl = if (wasOpen) recoveryPrefs.getString("last_url", null) else null
            if (recoveredUrl != null) {
                recoveryPrefs.edit().putBoolean("was_open", false).apply()
                loadUrl(buildYoutubeSafeSearchUrl(recoveredUrl) ?: recoveredUrl)
            } else {
                loadUrl(buildYoutubeSafeSearchUrl("https://m.youtube.com") ?: "https://m.youtube.com")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
            return
        }
        if (isMiniPlayerActive) {
            stopFloatingAndDestroy()
            return
        }
        if (wv == null) {
            super.onBackPressed()
            return
        }

        wv.evaluateJavascript("""
            (function() {
                try {
                    var v = document.querySelector('video');
                    if (v && !v.paused && !v.ended && v.readyState > 2 && !v.muted) {
                        return 'playing';
                    }
                    return v ? 'playing' : 'not_playing';
                } catch(e) { return 'unknown'; }
            })();
        """.trimIndent()) { result ->
            if (result?.contains("not_playing") != true) {
                launchFloatingDirectly(wv, moveActivityToBack = true)
            } else {
                runOnUiThread { @Suppress("DEPRECATION") super.onBackPressed() }
            }
        }
    }

    private fun launchFloatingDirectly(wv: WebView, moveActivityToBack: Boolean) {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.provider.Settings.canDrawOverlays(this)
        else true

        if (!hasOverlay) {
            if (moveActivityToBack) runOnUiThread { @Suppress("DEPRECATION") super.onBackPressed() }
            return
        }

        val currentUrl   = wv.url   ?: "https://m.youtube.com"
        val currentTitle = wv.title ?: "YouTube"

        injectVisibilitySpoofBeforeLeave(wv)

        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView = wv
        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.launchNoReload(this, currentUrl, currentTitle)

        webView = null
        isMiniPlayerActive = true

        if (moveActivityToBack) {
            moveTaskToBack(true)
        }
        startBgAudioService()
    }

    private fun stopFloatingAndDestroy() {
        isMiniPlayerActive = false
        stopBgAudioService()
        try {
            stopService(Intent(
                this,
                com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService::class.java
            ))
        } catch (_: Exception) {}
        finish()
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (isMiniPlayerActive) {
            // ★ FIX: Lock → Floating → Unlock flow
            // isMiniPlayerActive = true মানে WebView এখন floating service এ আছে
            // Service বন্ধ করলে onDestroy() এ WebView pendingWebView এ রাখবে
            isMiniPlayerActive = false
            stopBgAudioService()

            // Floating service বন্ধ করো — onDestroy() এ pendingWebView set হবে
            try {
                stopService(Intent(
                    this,
                    com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService::class.java
                ))
            } catch (_: Exception) {}

            if (webView == null) {
                // ★ FIX: Service synchronous হয় না, তাই postDelayed দিয়ে WebView নাও
                // pendingWebView set হতে সামান্য সময় লাগে
                val rootFrame = getRootFrame()

                // প্রথমে immediately চেষ্টা করো
                val immediateWv = com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView
                if (immediateWv != null) {
                    reattachWebView(immediateWv, rootFrame)
                } else {
                    // Fallback: একটু অপেক্ষা করো — service onDestroy() সময় নিচ্ছে
                    rootFrame?.postDelayed({
                        val pendingWv = com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView
                        if (pendingWv != null) {
                            reattachWebView(pendingWv, rootFrame)
                        }
                        // পুরোপুরি fail হলে: নতুন করে YouTube load করো (worst case)
                    }, 200)
                }
            }
            return
        }

        // Normal resume (floating ছাড়া)
        webView?.resumeTimers()
        webView?.onResume()
        webView?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            bringToFront()
        }
    }

    /**
     * ★ নতুন helper: rootFrame reference বের করো।
     */
    private fun getRootFrame(): FrameLayout? {
        val contentView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        return contentView?.getChildAt(0) as? FrameLayout
            ?: contentView as? FrameLayout
    }

    /**
     * ★ নতুন helper: Floating থেকে ফেরত আসা WebView কে Activity তে re-attach করো।
     * Black screen যাতে না আসে সেটা এখানেই নিশ্চিত করা হয়।
     */
    private fun reattachWebView(returnedWv: WebView, rootFrame: FrameLayout?) {
        webView = returnedWv
        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView = null

        // পুরনো parent থেকে সরাও
        (returnedWv.parent as? ViewGroup)?.removeView(returnedWv)

        if (rootFrame != null) {
            returnedWv.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // ★ FIX: Black screen → WebView আগে invisible রাখো, content load হলে visible করো
            returnedWv.visibility = View.INVISIBLE
            returnedWv.alpha = 1f
            rootFrame.setBackgroundColor(android.graphics.Color.BLACK)
            rootFrame.addView(returnedWv)
            rootFrame.bringToFront()
            rootFrame.requestLayout()
        }

        returnedWv.resumeTimers()
        returnedWv.onResume()
        injectVisibilitySpoof(returnedWv)
        injectYoutubeHacksForced(returnedWv)

        // ★ FIX: 150ms পরে visible করো — WebView render হওয়ার পরে
        // এতে black flash দেখা যাবে না
        returnedWv.postDelayed({
            returnedWv.visibility = View.VISIBLE
            returnedWv.alpha = 1f
            returnedWv.bringToFront()
            returnedWv.invalidate()
        }, 150)

        // ★ FIX: Video unmute + play ensure
        returnedWv.postDelayed({
            injectVisibilitySpoof(returnedWv)
            returnedWv.evaluateJavascript("""
                (function() {
                    try {
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            try {
                                videos[i].muted = false;
                                if (videos[i].paused && !videos[i].ended) {
                                    videos[i].play().catch(function(){});
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                })();
            """.trimIndent(), null)
        }, 300)
    }

    private fun injectVisibilitySpoofBeforeLeave(wv: WebView) {
        wv.evaluateJavascript("""
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                    Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
                    Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
                    // Reset user-pause flag when leaving to floating/background
                    // so the keep-alive interval resumes force-play on lock screen.
                    // (If the user had paused before locking, injectYoutubeHacksForced
                    //  checks this flag separately and still won't force-play.)
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun buildYoutubeSafeSearchUrl(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("youtube.com") && !host.contains("youtu.be")) return null
            val path = uri.path ?: ""
            if (!path.contains("/results") && uri.getQueryParameter("search_query") == null && uri.getQueryParameter("q") == null) return null
            if (uri.getQueryParameter("safe") == "strict") return null
            uri.buildUpon().appendQueryParameter("safe", "strict").build().toString()
        } catch (e: Exception) { null }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isMiniPlayerActive) return
        val wv = webView ?: return
        // Home button চাপলে floating window — same logic
        launchFloatingOnLock(wv)
    }

    override fun onPause() {
        webView?.resumeTimers()
        webView?.onResume()
        webView?.let {
            // ★ FIX BUG 1: Only inject visibility spoof here — NOT
            // injectYoutubeHacksForced() which force-plays ALL paused
            // videos. If the user explicitly paused, we must NOT
            // override that. Visibility spoof alone is enough to keep
            // audio running when the app goes to background.
            injectVisibilitySpoof(it)
        }
        super.onPause()

        startBgAudioService()
        if (wakeLock?.isHeld == false) wakeLock?.acquire()
    }

    override fun onRestart() {
        super.onRestart()
        if (!isFinishing) stopBgAudioService()
    }

    override fun onStop() {
        webView?.resumeTimers()
        super.onStop()
        if (webView != null && !isFinishing) startBgAudioService()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(playbackControlReceiver) } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        if (webView != null) {
            stopBgAudioService()
            webView?.destroy()
            webView = null
        }
        super.onDestroy()
    }

    private fun injectVisibilitySpoof(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun injectYoutubeHacks(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasBgAudioInjected__) return;
                window.__rasBgAudioInjected__ = true;
                try {
                    Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                    
                    var _origAdd = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, fn, opts) {
                        if (type === 'visibilitychange' || type === 'webkitvisibilitychange' ||
                            type === 'pagehide' || type === 'blur') return;
                        return _origAdd.call(this, type, fn, opts);
                    };

                    if (!window.__rasVideoKeepAlive__) {
                        window.__rasVideoKeepAlive__ = true;

                        // Track whether the user manually paused — if so,
                        // the keep-alive interval must NOT force-play.
                        // Reset to false when user explicitly hits play.
                        if (typeof window.__rasUserPaused__ === 'undefined') {
                            window.__rasUserPaused__ = false;
                        }

                        // Listen to the video element's own pause/play events
                        // to distinguish user intent from system-initiated pauses.
                        var _attachPauseListeners = function(video) {
                            if (video.__rasListenersAttached__) return;
                            video.__rasListenersAttached__ = true;
                            video.addEventListener('pause', function() {
                                // Only mark as user-paused if the video still
                                // has content — a mid-ad pause or buffering stall
                                // should not lock out the keep-alive.
                                if (!video.ended && video.readyState > 1) {
                                    window.__rasUserPaused__ = true;
                                }
                            });
                            video.addEventListener('play', function() {
                                window.__rasUserPaused__ = false;
                            });
                            video.addEventListener('playing', function() {
                                window.__rasUserPaused__ = false;
                            });
                        };

                        setInterval(function() {
                            try {
                                var v = document.querySelector('video');
                                if (!v) return;
                                // Attach listeners to any newly created video element
                                _attachPauseListeners(v);
                                // Only force-play if user has NOT manually paused
                                if (v.paused && !v.ended && v.readyState > 1
                                        && !window.__rasUserPaused__) {
                                    v.play().catch(function(){});
                                }
                            } catch(e) {}
                        }, 2000);
                    }
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun injectRemoveOpenInAppButton(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasOpenAppRemoverActive__) return;
                window.__rasOpenAppRemoverActive__ = true;

                // YouTube mobile "Open App" banner এর সব known selectors
                // (YouTube নতুন class দিলেও text/href দিয়ে ধরা হবে)
                var SELECTORS = [
                    // পুরনো class-based
                    '.ytm-action-button',
                    '[class*="open-in-app"]',
                    '[class*="openInApp"]',
                    '.external-app-banner',
                    '.app-badge-container',
                    // নতুন YouTube mobile UI
                    'ytm-app-banner-link-renderer',
                    'ytm-interstitial-ad-renderer',
                    'ytm-open-in-app-banner',
                    '.ytp-ae-banner',
                    '.ytp-chrome-top-buttons',
                    'ytm-companion-ad-renderer',
                    // data attribute based
                    '[data-type="open-app"]',
                    // intent:// link যুক্ত যেকোনো element
                ];

                function removeOpenAppElements() {
                    try {
                        // Selector দিয়ে remove
                        document.querySelectorAll(SELECTORS.join(','))
                            .forEach(function(el) {
                                el.style.display = 'none';
                                el.remove();
                            });

                        // Intent link এবং "Watch on app" text দিয়ে ধরো
                        document.querySelectorAll('a[href^="intent://"], a[href*="youtube://"]')
                            .forEach(function(el) {
                                var parent = el.closest('[class*="banner"], [class*="Banner"], [class*="interstitial"], [class*="Interstitial"], ytm-app-banner-link-renderer');
                                if (parent) {
                                    parent.style.display = 'none';
                                    parent.remove();
                                } else {
                                    el.style.display = 'none';
                                    el.remove();
                                }
                            });

                        // "Open app" / "Watch in app" / "Get apps for a faster
                        // experience" ইত্যাদি phrase যুক্ত banner ধরার জন্য
                        // exact-match এর বদলে substring match ব্যবহার করা হচ্ছে,
                        // কারণ YouTube বিভিন্ন variant text ব্যবহার করে।
                        var ytBannerPhrases = [
                            'open app', 'watch in app', 'use the app', 'open in app',
                            'get the app', 'open youtube', 'get apps for', 'faster experience',
                            'for a faster experience', 'download the app', 'try the app',
                            'switch to the app', 'view in app', 'continue in app'
                        ];
                        document.querySelectorAll('button, a, [role="button"], div, span')
                            .forEach(function(el) {
                                var txt = (el.innerText || el.textContent || '').toLowerCase().trim();
                                if (!txt || txt.length > 60) return;
                                var hit = ytBannerPhrases.some(function(p) { return txt.indexOf(p) !== -1; });
                                if (hit) {
                                    var parent = el.closest('[class*="banner"], [class*="Banner"], [class*="interstitial"], ytm-app-banner-link-renderer') || el.parentElement;
                                    if (parent) { parent.style.display = 'none'; parent.remove(); }
                                    else { el.style.display = 'none'; el.remove(); }
                                }
                            });
                    } catch(e) {}
                }

                // প্রথমেই চালাও
                removeOpenAppElements();

                // MutationObserver — YouTube SPA navigation এ নতুন element এলেই ধরবে
                try {
                    var observer = new MutationObserver(function(mutations) {
                        removeOpenAppElements();
                    });
                    observer.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                } catch(e) {}

                // Fallback interval (observer fail হলে)
                setInterval(removeOpenAppElements, 1500);
            })();
        """.trimIndent(), null)
    }

    private fun injectAdBlocker(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                // FIX (speed bug): আগে guard ছাড়া প্রতিবার onPageFinished এ (YouTube
                // SPA navigation এ বারবার fire হয়) নতুন setInterval যোগ হতো, কখনো
                // clear হতো না — কিছুক্ষণ ব্যবহার করলে ডজন ডজন interval জমে DOM
                // scan করতেই থাকতো, এটাই মূল slow-hওয়ার কারণ ছিল। এখন একটাই
                // interval সারাজীবন চলবে (page যতবারই reload/navigate হোক না কেন)।
                if (window.__rasAdBlockerActive__) return;
                window.__rasAdBlockerActive__ = true;
                setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                    if (skipBtn) { skipBtn.click(); }
                    var ads = document.querySelectorAll('.ytp-ad-overlay-container, ytm-promoted-video-renderer, .ad-showing');
                    ads.forEach(function(ad) { ad.style.display = 'none'; });
                    var adText = document.querySelector('.ytp-ad-text');
                    var video = document.querySelector('video');
                    if (adText && video && video.duration > 0) { video.currentTime = video.duration; }
                }, 500);
            })();
        """.trimIndent(), null)
    }

    private fun injectYoutubeHacksForced(view: WebView) {
        // Called on lock / home-button press.
        // Respects __rasUserPaused__: if the user deliberately paused
        // before locking, we do NOT force-play — their intent wins.
        // If the system paused (YouTube's visibility-change handler),
        // we force-play so audio continues through lock screen.
        view.evaluateJavascript("""
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        try {
                            if (videos[i].paused && !window.__rasUserPaused__) {
                                videos[i].play().catch(function(){});
                            }
                        } catch(e) {}
                    }
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun checkAdultSearchKeyword(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("youtube.com")) return null
            val query = (uri.getQueryParameter("search_query") ?: uri.getQueryParameter("q") ?: "").lowercase().trim()
            if (query.isEmpty()) return null
            val matched = ADULT_SEARCH_KEYWORDS.any { query.contains(it.lowercase()) }
            if (matched) buildAdultSearchBlockedPage(query) else null
        } catch (e: Exception) { null }
    }

    private fun buildAdultSearchBlockedPage(query: String): String {
        return """
            <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { background:#0f0f0f; display:flex; flex-direction:column; align-items:center;
                       justify-content:center; height:100vh; margin:0; color:#fff; font-family:sans-serif;
                       text-align:center; padding:24px; box-sizing:border-box; }
                h2 { color:#ff4d4d; }
                button { margin-top:18px; padding:14px 28px; border:none; border-radius:24px;
                         background:#ff0000; color:#fff; font-size:15px; font-weight:700;
                         -webkit-tap-highlight-color: transparent; }
            </style></head>
            <body><h2>🔒 Adult Content Blocked</h2>
            <p>RasFocus Safe Mode এ এই কনটেন্ট দেখানো যাবে না।</p>
            <button onclick="if(window.RasYtBlockBridge){RasYtBlockBridge.onGoHome();}">🏠 YouTube হোমে ফিরে যান</button>
            </body></html>
        """.trimIndent()
    }

    /**
     * Adult-blocked page দেখানোর পর WebView এর URL stuck হয়ে যেত এবং ফেরার
     * কোনো উপায় ছিল না — এই bridge টা attach করা থাকলে block page এর "ফিরে
     * যান" বাটন সরাসরি Kotlin থেকে youtube.com এ loadUrl করে দেয়।
     */
    inner class YtBlockBridge(private val wv: WebView) {
        @android.webkit.JavascriptInterface
        fun onGoHome() {
            runOnUiThread { wv.loadUrl("https://m.youtube.com/") }
        }
    }

    private fun startBgAudioService() {
        webView?.evaluateJavascript("(function() { return document.title; })();") { titleResult ->
            val rawTitle = titleResult?.replace("\"", "")?.takeIf { it.isNotBlank() && it != "null" } ?: webView?.title ?: "YouTube — Playing"
            val title = rawTitle.removeSuffix(" - YouTube").removeSuffix(" – YouTube").trim()
            val url   = webView?.url ?: ""

            val videoId = try {
                val uri = android.net.Uri.parse(url)
                // watch?v=ID এবং youtu.be/ID দুটোই handle করো
                uri.getQueryParameter("v")
                    ?: if (uri.host?.contains("youtu.be") == true) uri.pathSegments.firstOrNull()
                    else uri.pathSegments.firstOrNull { it.length == 11 }
            } catch (_: Exception) { null }

            // hqdefault (480px) ব্যবহার করো — mqdefault মাঝে মাঝে না থাকলে blank আসে
            val thumbUrl = if (videoId != null)
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            else null

            val svc = Intent(
                this,
                com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService::class.java
            ).apply {
                putExtra(com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService.EXTRA_TITLE, title)
                putExtra("extra_video_url", url)
                if (thumbUrl != null) putExtra("extra_thumb_url", thumbUrl)
                if (videoId != null) putExtra("extra_video_id", videoId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
                else startService(svc)
            } catch (_: Exception) {}
        }
    }

    private fun stopBgAudioService() {
        try {
            stopService(Intent(this, com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService::class.java))
        } catch (_: Exception) {}
    }

    private fun injectSettingsRemover(view: WebView) {
        val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
        val hideShorts   = prefs.getBoolean("yt_hide_shorts", false)
        val hideComments = prefs.getBoolean("yt_hide_comments", false)
        val grayscale    = prefs.getBoolean("yt_grayscale", false)

        // ★ FIX BUG 3a: Always run — no window guard. Previously the guard
        // prevented re-application after the user toggled a setting mid-session.
        // We clear any old interval ourselves before installing a new one.
        val js = """
            (function() {
                // Clear previous interval if settings were changed
                if (window.__rasYtSettingsInterval__) {
                    clearInterval(window.__rasYtSettingsInterval__);
                    window.__rasYtSettingsInterval__ = null;
                }

                // ★ FIX BUG 3b: Apply OR remove grayscale based on current pref
                document.documentElement.style.filter = ${if (grayscale) "'grayscale(100%)'" else "''"}; 

                function applySettings() {
                    try {
                        // ── Shorts ──────────────────────────────────────────
                        var shortsTabs = document.querySelectorAll('ytm-pivot-bar-item-renderer');
                        shortsTabs.forEach(function(tab) {
                            var text = (tab.innerText || '').toLowerCase();
                            if (text.indexOf('shorts') !== -1)
                                tab.style.display = ${if (hideShorts) "'none'" else "''"}; 
                        });
                        var shelves = document.querySelectorAll(
                            'ytm-rich-section-renderer, ytm-reel-shelf-renderer');
                        shelves.forEach(function(shelf) {
                            var text = (shelf.innerText || '').toLowerCase();
                            if (text.indexOf('shorts') !== -1)
                                shelf.style.display = ${if (hideShorts) "'none'" else "''"}; 
                        });

                        // ── Comments ─────────────────────────────────────────
                        var comments = document.querySelectorAll(
                            'ytm-item-section-renderer[section-identifier="comment-item-section"],' +
                            'ytm-comments-entry-point-header-renderer');
                        comments.forEach(function(c) {
                            c.style.display = ${if (hideComments) "'none'" else "''"}; 
                        });
                    } catch(e) {}
                }

                applySettings();
                // Store interval id so next call can cancel it
                window.__rasYtSettingsInterval__ = setInterval(applySettings, 800);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}