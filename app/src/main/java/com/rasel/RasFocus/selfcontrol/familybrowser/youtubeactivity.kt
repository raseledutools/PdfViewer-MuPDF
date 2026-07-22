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
 * YoutubeActivity � ???? native YouTube app ?? ??? ????????
 */
class YoutubeActivity : ComponentActivity() {

    private var webView: WebView? = null
    // Feed/search ? visible content (thumbnails, titles, alt-text) scan ???
    // adult content ???? ???? � AdBlocker.kt ?? existing multi-layer scanner
    private val adBlocker by lazy { com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker(this) }
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // FIX: shouldOverrideUrlLoading/shouldInterceptRequest ? URL-level check ?
    // adult content block ???? ??????? ???? onPageFinished ?? title-check safety
    // net ??? ??? navigation ? ???? ???? ???? block page ?????? ??? � ??? ?????
    // ???? ?????? black block screen ?????? ?? flag ????? ??????? ??? ?? ??
    // navigation-? ???????? ????? URL-level ? block ?????? ????; ???
    // onPageFinished ?? ???????? check ????? ??? ?????
    private var adultBlockAlreadyShownForThisLoad = false

    // Mini player ???? ??? ???? track ???? ????
    private var isMiniPlayerActive = false

    // -- LAYER 2: Wake Lock -----------------------------------------------------
    private var wakeLock: PowerManager.WakeLock? = null

    // -- Notification Controls Receiver ----------------------------------------
    // BackgroundAudioService ???? broadcast ??? WebView-? JS inject ???
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

    // -- LAYER 1: Screen Off Receiver ------------------------------------------
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val wv = webView
                    if (wv != null && !isMiniPlayerActive) {
                        // ? FIX: Lock button ????? ???? visibility spoof inject ???
                        // ???? YouTube pause ?? ???
                        injectVisibilitySpoofBeforeLeave(wv)
                        injectYoutubeHacksForced(wv)
                        wv.resumeTimers()
                        wv.onResume()

                        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            android.provider.Settings.canDrawOverlays(ctx)
                        else true

                        if (hasOverlay) {
                            // ? FIX: JS check skip ??? � ?????? floating launch ???
                            // JS async ??????? screen off ?? ??? result ??? ?? ???????????
                            // ??? ?????? floating ? ???; audio ???? ?????
                            launchFloatingOnLock(wv)
                        } else {
                            // Overlay permission ??? � ???? audio service
                            startBgAudioService()
                        }
                    } else if (isMiniPlayerActive) {
                        // ? FIX: Mini player ???????? lock � audio ???? ?????,
                        // floating service ???????? WebView ??? ??????
                        startBgAudioService()
                    }
                }

                Intent.ACTION_SCREEN_ON -> {
                    // Screen on � user unlock ?? ??? ??????? ???? ???? ??
                }

                Intent.ACTION_USER_PRESENT -> {
                    // ? FIX: Unlock ???? floating ???? WebView ??????? ???
                    // isMiniPlayerActive = true ???? WebView ??? floating service ? ???
                    // onResume() ? ???????? WebView re-attach ???
                    // ????? ???? service ?????? ? flag set ????? onResume() ???? ??? ????
                    if (isMiniPlayerActive) {
                        // onResume() call ??? ??? activity visible ??? � ??????? WebView re-attach ???
                        // ????? ???? ???? ????? ???; onResume() ? isMiniPlayerActive check ??? ???
                    } else {
                        // Floating ?????? lock ??????? (overlay ??? ?? ?? video ????? ??)
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
     * ? ????: Lock button ? floating launch?
     * launchFloatingDirectly ?? ??? ?????? JS async result ?? ??? ?????? ??? ???
     * WebView ?????? service ? ??????, activity background ? ?????
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

        // WebView service ? ??? � reload ??? ??
        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView = wv
        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.launchNoReload(
            this, currentUrl, currentTitle
        )

        webView = null         // Activity ?? reference ???? ??
        isMiniPlayerActive = true

        // Activity ?? background ? ?????
        moveTaskToBack(true)

        startBgAudioService()
    }

    companion object {
        private const val YT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        // FIX: ?? list ??? ?????? hardcoded ???, ??? ???? keyword ??? ???? ???
        // app update ?????? ??? ??? FirebaseKeywordSync (Firebase Realtime DB ??
        // keyword_data/adult_keywords node) ???? ??? � main browser ?? AdBlocker
        // ??? FacebookActivity ?? ????? ??? ??? central list ?????? ???, ??? ?????
        // ????? ???????? sync ????? ?????? ???? Firebase console ? keyword ???/???
        // ????? � ???? app update ?????? � YouTube search bar ? ???? ???? reflect ????
        private val ADULT_SEARCH_KEYWORDS: Set<String>
            get() = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.getAdultKeywords()

        private val AD_SERVERS = setOf(
            // Google/YouTube ad networks
            "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "pubads.g.doubleclick.net", "adservice.google.com",
            "googleadservices.com", "googlesyndication.com",
            "doubleclick.net", "ad.doubleclick.net", "static.doubleclick.net",
            "imasdk.googleapis.com",
            // YouTube-specific ad endpoints
            "youtube.com/api/stats/ads",
            "youtube.com/pagead",
            "youtube.com/ptracking",
            "youtube.com/api/stats/qoe",
            "youtubei/v1/player/ad_break",
            "youtubei/v1/log_event",
            "youtube.com/pagead/adview",
            "youtube.com/get_video_info",
            // Analytics/trackers
            "google-analytics.com", "ssl.google-analytics.com",
            "googletagmanager.com", "googletagservices.com",
            // Other ad networks
            "amazon-adsystem.com", "adsystem.amazon.com",
            "moatads.com", "scorecardresearch.com",
            "adsafeprotected.com", "2mdn.net"
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

        // FIX (startup speed): black frame ?? ??? setContentView ????? ????????
        // ?????? ??????, ????? receiver registration ? wakelock acquire ?????
        // ??? ?? non-UI ??????? setContentView ?? ??? ???, ??? ????? ?????
        // ????? ?????? ???? ????? ??? app ????? ??? ??? ??? � ??? ????? ????
        // ????? ???? ??????? ???? (blank/white flash ?? ????), ?? WebView ??
        // load ???? ??? ???? ??????? ???? ????? ????? ?????, ???? order ?????????
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

        // Notification controls receiver register ???
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
            // Android version ???????? ???? layer type:
            // Android 10 (API 29) ? inline <video> TextureView ????? render ???,
            // ??? LAYER_TYPE_HARDWARE ???? � ?? ??? video frame black ????,
            // ???? audio ???? Android 11+ ? Chromium ????? SurfaceControl ?????
            // compositor bypass ???, ??? LAYER_TYPE_NONE ?????? ?????
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                setLayerType(View.LAYER_TYPE_NONE, null)
            }
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

            // Block page ?? "???? ???" ???? ???? ?????????? youtube.com ? ????
            // ???? ???? � ??? ?? ????? block page ??????? ?? WebView ????????
            // ???? ???? ?????, ???? navigation/back ??? ???? ???
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
                    // ???? navigation ???? � ???? load ?? block-flag ????? ???
                    adultBlockAlreadyShownForThisLoad = false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectVisibilitySpoof(view)
                    injectYoutubeHacks(view)
                    injectAdBlocker(view)
                    injectRemoveOpenInAppButton(view)
                    injectSettingsRemover(view)
                    // ⚠️ DISABLED: adBlocker.injectContentScanner — এই JS system
                    // video element এর render pipeline এ interfere করে, ফলে
                    // কিছু device এ video frame drop বা black screen হয়।
                    // Network-level block (shouldInterceptRequest) যথেষ্ট।
                    // adBlocker.injectContentScanner(view)

                    // FIX: ?? navigation ? shouldOverrideUrlLoading/shouldInterceptRequest
                    // ? URL-level check ??? ???????? ????? block page ?????? ???? ?????,
                    // ????? title-check ?? ?????? ??? ?? � ????? ??? block ???? ??????
                    // (double black screen) ???? ????
                    if (adultBlockAlreadyShownForThisLoad) return

                    // Second-layer safety net: shouldInterceptRequest only sees the
                    // request URL, not POST body � and YouTube's internal search
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

                    // ── LAYER 1: Network-level block ─────────────────────────────────────
                    // এটাই প্রধান ad block — request যাওয়ার আগেই শূন্য response দেওয়া হয়।
                    // JS-based skip এর মতো video render pipeline এ কোনো interference নেই।

                    // ── 1a. Known ad server domains ───────────────────────────────────────
                    if (AD_SERVERS.any { url.contains(it) }) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    // ── 1b. YouTube ad-specific API endpoints ─────────────────────────────
                    val ytAdEndpoints = listOf(
                        // Ad impression / tracking
                        "youtube.com/api/stats/ads",
                        "youtube.com/pagead/adview",
                        "youtube.com/ptracking",
                        "youtube.com/api/stats/qoe",
                        "youtube.com/pagead/paralleladload",
                        "youtube.com/pagead/viewthroughconversion",
                        "youtubei/v1/player/ad_break",
                        "youtubei/v1/log_event",
                        "youtubei/v1/ad_break",
                        // Companion / display ads
                        "youtube.com/get_midroll_info",
                        "youtube.com/api/stats/watchtime",
                        // Ad pod loading
                        "youtube.com/api/stats/delayplay",
                        "youtube.com/api/stats/atr",
                        // Survey / feedback related to ads
                        "youtube.com/pagead/interaction",
                        // Beacon / pixel tracking
                        "youtube.com/api/stats/playback",
                        // Ad config
                        "youtube.com/get_endscreen",
                        // Third-party ad measurement
                        "securepubads.g.doubleclick.net",
                        "cm.g.doubleclick.net",
                        "tpc.googlesyndication.com",
                        "imasdk.googleapis.com/js/sdkloader",
                        "imasdk.googleapis.com/admob",
                        // In-video overlay ads
                        "youtube.com/annotations_auth",
                        "youtube.com/pagead/adformat"
                    )
                    if (ytAdEndpoints.any { url.contains(it) }) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    // ── 1c. Ad video stream detection (googlevideo.com) ───────────────────
                    // YouTube ad video এর videoplayback URL এ নির্দিষ্ট params থাকে।
                    // ⚠️ এখানে শুধু empty response দিচ্ছি — video.currentTime
                    // বা DOM touch করছি না, তাই render pipeline safe থাকে।
                    if (url.contains("googlevideo.com/videoplayback") ||
                        url.contains("googlevideo.com/videoplayback")) {
                        val isAdStream =
                            url.contains("&oad=")         ||  // old ad token
                            url.contains("ctier=A")       ||  // ad tier marker
                            url.contains("&adformat=")    ||  // ad format param
                            url.contains("&ad_type=")     ||  // ad type
                            url.contains("&source=ytads") ||  // source = ytads
                            url.contains("&adsid=")       ||  // ad session id
                            url.contains("&pot=")         &&
                            url.contains("&c=WEB")        &&
                            !url.contains("&id=")            // ad streams lack content id
                        if (isAdStream) {
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }

                    // ── 1d. Generic ad/tracker URL pattern matching ───────────────────────
                    val adUrlPatterns = listOf(
                        "/pagead/", "/ads/", "/adview/", "adformat=",
                        "//ad.", "//ads.", "//adserver.", "//adservice.",
                        "tracking_pixel", "track/click", "ad_impression",
                        "affiliates/", "click.php?aff", "bannerfarm",
                        "adrotate", "sponsored_links"
                    )
                    if (adUrlPatterns.any { url.contains(it) } &&
                        !url.contains("youtube.com/watch") &&
                        !url.contains("googleapis.com/youtube") &&
                        !url.contains("youtube.com/results")) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    // ── END LAYER 1 ──────────────────────────────────────────────────────

                    // YouTube-?? ?????? search box ???? search ???? page navigate ??? ?? �
                    // internally ???? XHR/fetch request ??????, ???? shouldOverrideUrlLoading
                    // ? ???? ??????? ?? (???? ???? full page navigation ? ???)? ????? ??? ??
                    // ????? address bar ? ?????? URL ???? ???? block ???, ?????? app ??
                    // ????? search icon ????? search ???? ???????? bypass ???? ????
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

                    // intent:// ?? youtube:// ????? YouTube app ????? ???? ??
                    if (url.startsWith("intent://") ||
                        url.startsWith("youtube://") ||
                        url.startsWith("vnd.youtube://") ||
                        url.startsWith("market://")) {
                        return true  // block � ????? ???? ??
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
            // --------------------------------------------------------------
            // ? Low-RAM device fix: process kill ?? ?? cold-start ???
            // YoutubeFloatingWindowService ? save ??? ??? URL ? ?????,
            // ?????? default youtube.com ? ?? ??????
            // --------------------------------------------------------------
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
            // ? FIX: Lock ? Floating ? Unlock flow
            // isMiniPlayerActive = true ???? WebView ??? floating service ? ???
            // Service ???? ???? onDestroy() ? WebView pendingWebView ? ?????
            isMiniPlayerActive = false
            stopBgAudioService()

            // Floating service ???? ??? � onDestroy() ? pendingWebView set ???
            try {
                stopService(Intent(
                    this,
                    com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService::class.java
                ))
            } catch (_: Exception) {}

            if (webView == null) {
                // ? FIX: Service synchronous ??? ??, ??? postDelayed ????? WebView ???
                // pendingWebView set ??? ??????? ???? ????
                val rootFrame = getRootFrame()

                // ?????? immediately ?????? ???
                val immediateWv = com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView
                if (immediateWv != null) {
                    reattachWebView(immediateWv, rootFrame)
                } else {
                    // Fallback: ???? ??????? ??? � service onDestroy() ???? ??????
                    rootFrame?.postDelayed({
                        val pendingWv = com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView
                        if (pendingWv != null) {
                            reattachWebView(pendingWv, rootFrame)
                        }
                        // ???????? fail ???: ???? ??? YouTube load ??? (worst case)
                    }, 200)
                }
            }
            return
        }

        // Normal resume (floating ?????)
        webView?.resumeTimers()
        webView?.onResume()
        webView?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            bringToFront()
        }
    }

    /**
     * ? ???? helper: rootFrame reference ??? ????
     */
    private fun getRootFrame(): FrameLayout? {
        val contentView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        return contentView?.getChildAt(0) as? FrameLayout
            ?: contentView as? FrameLayout
    }

    /**
     * ? ???? helper: Floating ???? ???? ??? WebView ?? Activity ?? re-attach ????
     * Black screen ???? ?? ??? ???? ?????? ??????? ??? ????
     */
    private fun reattachWebView(returnedWv: WebView, rootFrame: FrameLayout?) {
        webView = returnedWv
        com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService.pendingWebView = null

        // ????? parent ???? ????
        (returnedWv.parent as? ViewGroup)?.removeView(returnedWv)

        if (rootFrame != null) {
            returnedWv.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // ? FIX: Black screen ? WebView ??? invisible ????, content load ??? visible ???
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

        // ? FIX: 150ms ??? visible ??? � WebView render ?????? ???
        // ??? black flash ???? ???? ??
        returnedWv.postDelayed({
            returnedWv.visibility = View.VISIBLE
            returnedWv.alpha = 1f
            returnedWv.bringToFront()
            returnedWv.invalidate()
        }, 150)

        // ? FIX: Video unmute + play ensure
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
        // Home button ????? floating window � same logic
        launchFloatingOnLock(wv)
    }

    override fun onPause() {
        webView?.resumeTimers()
        webView?.onResume()
        webView?.let {
            injectVisibilitySpoof(it)
            injectYoutubeHacksForced(it)
        }
        super.onPause()
        
        // ????? ???? ???? ????? ???? ???? ???? ???
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
                        setInterval(function() {
                            try {
                                var v = document.querySelector('video');
                                if (v && v.paused && !v.ended && v.readyState > 1) {
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

                // YouTube mobile "Open App" banner ?? ?? known selectors
                // (YouTube ???? class ????? text/href ????? ??? ???)
                var SELECTORS = [
                    // ????? class-based
                    '.ytm-action-button',
                    '[class*="open-in-app"]',
                    '[class*="openInApp"]',
                    '.external-app-banner',
                    '.app-badge-container',
                    // ???? YouTube mobile UI
                    'ytm-app-banner-link-renderer',
                    'ytm-interstitial-ad-renderer',
                    'ytm-open-in-app-banner',
                    '.ytp-ae-banner',
                    '.ytp-chrome-top-buttons',
                    'ytm-companion-ad-renderer',
                    // data attribute based
                    '[data-type="open-app"]',
                    // intent:// link ????? ?????? element
                ];

                function removeOpenAppElements() {
                    try {
                        // Selector ????? remove
                        document.querySelectorAll(SELECTORS.join(','))
                            .forEach(function(el) {
                                el.style.display = 'none';
                                el.remove();
                            });

                        // Intent link ??? "Watch on app" text ????? ???
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
                        // experience" ??????? phrase ????? banner ???? ????
                        // exact-match ?? ???? substring match ??????? ??? ?????,
                        // ???? YouTube ??????? variant text ??????? ????
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

                // ??????? ?????
                removeOpenAppElements();

                // MutationObserver � YouTube SPA navigation ? ???? element ???? ????
                try {
                    var observer = new MutationObserver(function(mutations) {
                        removeOpenAppElements();
                    });
                    observer.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                } catch(e) {}

                // Fallback interval (observer fail ???)
                setInterval(removeOpenAppElements, 1500);
            })();
        """.trimIndent(), null)
    }

    private fun injectAdBlocker(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                // Guard: ????? interval ???????? ?????
                if (window.__rasAdBlockerActive__) return;
                window.__rasAdBlockerActive__ = true;

                // -- BLACK SCREEN ROOT CAUSE ------------------------------------------
                // YouTube ad ???? ???? DOM ? ????? <video> ????:
                //   [0] = ad video  (src = googlevideo.com/videoplayback?...&oad=...)
                //   [1] = main video (src = googlevideo.com/videoplayback?...&id=...)
                // ???? fix: player.querySelector('video') � ??? [0] ????, ???? ad
                // skip ????, ?????? YouTube ?? player state machine ???? ad-mode ?
                // ???? � transition ? compositor ???? surface allocate ???? ????
                // ????? surface release ??? ????, ??? main video decode ???? ??????
                // ?? ??????? screen blank ?????
                //
                // -- FIX STRATEGY ----------------------------------------------------
                // 1. ?? video element ??? (querySelectorAll)
                // 2. ad video = src ?? "ctier=A" ?? "oad=" ??? ???
                //    main video = ad video ?? ???? main
                // 3. ad skip ???? ???? ???? main video ??:
                //    a. muted=false ??? (YouTube sometimes mutes it during ad)
                //    b. visibility/display force ???
                //    c. play() call ??? � renderer surface ???? ???
                // 4. 300ms ??? ???? play() � transition delay cover ????
                // --------------------------------------------------------------------

                function isAdVideo(v) {
                    try {
                        var src = v.src || '';
                        // YouTube ad videoplayback URL ? ?? params ????
                        return src.indexOf('ctier=A') !== -1 ||
                               src.indexOf('&oad=') !== -1 ||
                               src.indexOf('&adformat=') !== -1 ||
                               (v.closest ? !!v.closest('.ad-showing') : false);
                    } catch(e) { return false; }
                }

                function wakeMainVideo(player) {
                    try {
                        var allVideos = player.querySelectorAll('video');
                        var mainVideo = null;
                        for (var i = 0; i < allVideos.length; i++) {
                            if (!isAdVideo(allVideos[i])) { mainVideo = allVideos[i]; break; }
                        }
                        // fallback: ??? identify ???? ?? ????, ??????? ????? video ???
                        if (!mainVideo && allVideos.length > 1) {
                            mainVideo = allVideos[allVideos.length - 1];
                        }
                        if (!mainVideo) return;

                        // Surface wake: visibility + mute fix + play
                        mainVideo.style.visibility = 'visible';
                        mainVideo.style.display    = 'block';
                        mainVideo.style.opacity    = '1';
                        if (mainVideo.muted) mainVideo.muted = false;
                        mainVideo.play().catch(function(){});

                        // Double-tap 300ms ??? � transition buffer
                        setTimeout(function() {
                            try {
                                mainVideo.style.visibility = 'visible';
                                if (mainVideo.paused) mainVideo.play().catch(function(){});
                            } catch(e) {}
                        }, 300);
                    } catch(e) {}
                }

                var wasAdShowing = false;

                // ⚠️ Layer 3 & 4 DISABLED: video.currentTime = duration এবং
                // wakeMainVideo() — এই দুটো video element এর render pipeline এ
                // সরাসরি interfere করে। YouTube এর compositor এই ধরনের forced
                // seek কে ad-transition state হিসেবে ধরে নেয় এবং main video
                // surface allocate করতে ব্যর্থ হয় → black screen।
                // Layer 1 (network block) যথেষ্ট শক্তিশালী।

                // Layer 2 JS only: skip button click + banner hide (render-safe)
                function runAdBlock() {
                    try {
                        // Skip button — সব YouTube version এর selectors
                        var skipSelectors = [
                            '.ytp-ad-skip-button',
                            '.ytp-ad-skip-button-modern',
                            '.ytp-skip-ad-button',
                            '[class*="skip-ad"]',
                            '[class*="skipAd"]',
                            'button.ytp-ad-skip-button-container'
                        ];
                        for (var i = 0; i < skipSelectors.length; i++) {
                            var btn = document.querySelector(skipSelectors[i]);
                            if (btn && btn.offsetParent !== null) {
                                btn.click();
                                return;
                            }
                        }

                        // Banner / overlay / promoted ads — শুধু display:none, DOM touch নেই
                        document.querySelectorAll(
                            '.ytp-ad-overlay-container, ytm-promoted-video-renderer, ' +
                            '.ytp-ad-text-overlay, .ytp-ad-image-overlay, ' +
                            '.ytp-ad-overlay-slot, ytm-in-read-ad-renderer, ' +
                            'ytm-companion-ad-renderer, ytm-ads-renderer, ' +
                            '.ytm-promoted-sparkles-web-renderer, ' +
                            '[class*="ad-div"], [id*="ad_slot"]'
                        ).forEach(function(ad) { ad.style.display = 'none'; });

                    } catch(e) {}
                }

                setInterval(runAdBlock, 200);
                runAdBlock();
            })();
        """.trimIndent(), null)
    }

    private fun injectYoutubeHacksForced(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        try { if (videos[i].paused) videos[i].play().catch(function(){}); } catch(e) {}
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
            <body><h2>?? Adult Content Blocked</h2>
            <p>RasFocus Safe Mode ? ?? ??????? ?????? ???? ???</p>
            <button onclick="if(window.RasYtBlockBridge){RasYtBlockBridge.onGoHome();}">?? YouTube ???? ???? ???</button>
            </body></html>
        """.trimIndent()
    }

    /**
     * Adult-blocked page ??????? ?? WebView ?? URL stuck ???? ??? ??? ?????
     * ???? ????? ??? ?? � ?? bridge ?? attach ??? ????? block page ?? "????
     * ???" ???? ?????? Kotlin ???? youtube.com ? loadUrl ??? ?????
     */
    inner class YtBlockBridge(private val wv: WebView) {
        @android.webkit.JavascriptInterface
        fun onGoHome() {
            runOnUiThread { wv.loadUrl("https://m.youtube.com/") }
        }
    }

    private fun startBgAudioService() {
        webView?.evaluateJavascript("(function() { return document.title; })();") { titleResult ->
            val rawTitle = titleResult?.replace("\"", "")?.takeIf { it.isNotBlank() && it != "null" } ?: webView?.title ?: "YouTube � Playing"
            val title = rawTitle.removeSuffix(" - YouTube").removeSuffix(" � YouTube").trim()
            val url   = webView?.url ?: ""

            val videoId = try {
                val uri = android.net.Uri.parse(url)
                // watch?v=ID ??? youtu.be/ID ????? handle ???
                uri.getQueryParameter("v")
                    ?: if (uri.host?.contains("youtu.be") == true) uri.pathSegments.firstOrNull()
                    else uri.pathSegments.firstOrNull { it.length == 11 }
            } catch (_: Exception) { null }

            // hqdefault (480px) ??????? ??? � mqdefault ???? ???? ?? ????? blank ???
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
        val hideShorts = prefs.getBoolean("yt_hide_shorts", false)
        val hideComments = prefs.getBoolean("yt_hide_comments", false)
        val grayscale = prefs.getBoolean("yt_grayscale", false)
        
        if (!hideShorts && !hideComments && !grayscale) return
        
        val js = """
            (function() {
                if (window.__rasYtSettingsRemover__) return;
                window.__rasYtSettingsRemover__ = true;
                
                if ($grayscale) {
                    document.documentElement.style.filter = 'grayscale(100%)';
                }
                
                function applySettings() {
                    try {
                        if ($hideShorts) {
                            // Remove Shorts bottom navigation tab
                            var shortsTabs = document.querySelectorAll('ytm-pivot-bar-item-renderer');
                            shortsTabs.forEach(function(tab) {
                                var text = (tab.innerText || '').toLowerCase();
                                if (text.indexOf('shorts') !== -1) tab.style.display = 'none';
                            });
                            
                            // Remove Shorts shelf in home feed
                            var shelves = document.querySelectorAll('ytm-rich-section-renderer, ytm-reel-shelf-renderer');
                            shelves.forEach(function(shelf) {
                                var text = (shelf.innerText || '').toLowerCase();
                                if (text.indexOf('shorts') !== -1) shelf.style.display = 'none';
                            });
                        }
                        
                        if ($hideComments) {
                            var comments = document.querySelectorAll('ytm-item-section-renderer[section-identifier="comment-item-section"], ytm-comments-entry-point-header-renderer');
                            comments.forEach(function(comment) {
                                comment.style.display = 'none';
                            });
                        }
                    } catch(e) {}
                }
                applySettings();
                setInterval(applySettings, 1000);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}



