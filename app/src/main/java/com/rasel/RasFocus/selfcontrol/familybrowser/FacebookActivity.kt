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
 * FacebookActivity — পুরো native Facebook app এর মতো অভিজ্ঞতা (WebView ভিত্তিক)।
 *
 * YoutubeActivity এর একই প্যাটার্ন অনুসরণ করে বানানো:
 *   • Adult content block — search keyword scan + AdBlocker.isAdultSite() দিয়ে
 *     network-level URL block, দুই layer এই কাজ করে।
 *   • Home button / screen lock চাপলে floating bubble (Messenger chat-head এর
 *     মতো) এ চলে যায় — app background এ থেকেও session/notification সচল থাকে।
 *   • Login session + data (cookies, localStorage) স্বয়ংক্রিয়ভাবে local এ
 *     (phone storage) save হয় — CookieManager persistent + explicit flush().
 */
class FacebookActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Feed/search এ visible content (post caption, image alt, video title) scan
    // করে adult content ধরার জন্য — AdBlocker.kt এর existing multi-layer scanner
    private val adBlocker by lazy { com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker(this) }

    // Floating bubble মোডে চলে গেলে true — WebView তখন service এর দখলে
    private var isFloatingActive = false

    private var wakeLock: PowerManager.WakeLock? = null

    // ── Screen lock/unlock receiver — YoutubeActivity এর মতোই লজিক ───────────
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val wv = webView
                    if (wv != null && !isFloatingActive) {
                        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            android.provider.Settings.canDrawOverlays(ctx) else true

                        // Cookie/local storage কে disk এ flush করে দাও যাতে
                        // লক করার সাথে সাথেই ডেটা সেভ থাকে
                        flushCookies()

                        if (hasOverlay) {
                            launchFloating(wv, moveActivityToBack = true)
                        }
                        // Overlay permission না থাকলে activity normal background এ যাবে —
                        // WebView destroy হবে না, শুধু pause হবে, session ঠিকই থাকবে
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Unlock করলে — floating bubble ট্যাপ না করে সরাসরি app এ ফিরলে
                    // এখানে কিছু করার দরকার নেই, onResume() বাকিটা সামলাবে
                }
            }
        }
    }

    companion object {
        private const val FB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        // FIX: আগে এখানে YoutubeActivity এর সাথে "sync রাখা" আলাদা একটা copy
        // hardcoded ছিল — দুই জায়গায় আলাদা লিস্ট মেইনটেইন করতে হতো, এবং নতুন
        // keyword যোগ করতে app update লাগতো। এখন দুইটাই একই central
        // FirebaseKeywordSync.getAdultKeywords() ব্যবহার করে (Firebase Realtime DB
        // এর keyword_data/adult_keywords node), তাই আর manual sync লাগবে না —
        // Firebase console এ update করলেই সব জায়গায় সাথে সাথে reflect হবে।
        private val ADULT_SEARCH_KEYWORDS: Set<String>
            get() = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.getAdultKeywords()

        fun launch(activity: Activity) {
            val intent = Intent(activity, FacebookActivity::class.java)
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
        // Facebook এর নিজস্ব ব্র্যান্ড রঙ — native app এর মতো ফিল দেওয়ার জন্য
        window.statusBarColor = Color.parseColor("#1877F2")
        window.navigationBarColor = Color.parseColor("#f0f2f5")  // match Facebook bg

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("WakelockTimeout")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RasFocus:FacebookWakeLock"
        )

        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#f0f2f5"))  // Facebook bg — no white flash
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
        setContentView(rootFrame)

        // ── আগের floating session থেকে ফেরত আসা WebView থাকলে সেটাই ব্যবহার করো ──
        val pending = com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.pendingWebView
        if (pending != null) {
            com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.pendingWebView = null
            (pending.parent as? ViewGroup)?.removeView(pending)
            webView = pending
            rootFrame.addView(pending, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            pending.resumeTimers()
            pending.onResume()
        } else {
            webView = buildFacebookWebView(rootFrame)
            rootFrame.addView(webView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            // ══════════════════════════════════════════════════════════════
            // ★ Low-RAM device fix: pendingWebView না পাওয়া মানে process
            // পুরো kill হয়েছিল (Itel Android 10, ~2-3GB RAM এ multiple
            // WebView চললে এটা হতে পারে)। এই ক্ষেত্রে default facebook.com
            // এর বদলে শেষ save করা URL এ ফেরাও।
            // ══════════════════════════════════════════════════════════════
            val recoveryPrefs = getSharedPreferences("fb_float_recovery", Context.MODE_PRIVATE)
            val wasOpen = recoveryPrefs.getBoolean("was_open", false)
            val recoveredUrl = if (wasOpen) recoveryPrefs.getString("last_url", null) else null
            if (recoveredUrl != null) {
                recoveryPrefs.edit().putBoolean("was_open", false).apply()
                webView?.loadUrl(recoveredUrl)
            } else {
                webView?.loadUrl("https://m.facebook.com/")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildFacebookWebView(rootFrame: FrameLayout): WebView {
        return WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.TRANSPARENT)  // no white flash during navigation

            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true   // localStorage → data local এ থাকে
                databaseEnabled                  = true
                loadWithOverviewMode             = true
                useWideViewPort                  = true
                builtInZoomControls               = true
                displayZoomControls               = false
                mediaPlaybackRequiresUserGesture  = false
                allowFileAccess                   = true
                allowContentAccess                = true
                loadsImagesAutomatically          = true
                mixedContentMode                  = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode                         = WebSettings.LOAD_DEFAULT
                userAgentString                   = FB_USER_AGENT
                setSupportZoom(true)
            }

            // ── Cookie persistence — login session/data phone এ save থাকবে ──────
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // Block page এর "ফিরে যান" বাটন যাতে সত্যিকারের facebook.com এ
            // ফিরে যেতে পারে, তার জন্য bridge attach — এটা না থাকলে block
            // page দেখানোর পর WebView চিরকালের জন্য আটকে থাকতো।
            addJavascriptInterface(FbBlockBridge(this), "RasFbBlockBridge")
            addJavascriptInterface(
                com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.BlockOverlayBridge(
                    this, "https://m.facebook.com/", { block -> runOnUiThread(block) }
                ),
                "RasBlockBridge"
            )

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // Keep showing previous content while new page loads — no white blank screen.
                    // WebView already does this with TRANSPARENT background set above,
                    // but we explicitly keep alpha=1f so nothing blinks.
                    view.alpha = 1f
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.alpha = 1f
                    injectRemoveOpenInAppButton(view)
                    injectFooterRemover(view)
                    injectSettingsRemover(view)
                    adBlocker.injectContentScanner(view)
                    flushCookies()
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (AdBlocker.isAdultSite(url)) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()

                    // fb:// বা intent:// দিয়ে আসল Facebook app খুলতে দেবো না
                    if (url.startsWith("intent://") ||
                        url.startsWith("fb://") ||
                        url.startsWith("market://")) {
                        return true
                    }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) return true

                    if (AdBlocker.isAdultSite(url)) {
                        view.loadDataWithBaseURL(null, buildAdultBlockedPage(), "text/html", "UTF-8", null)
                        return true
                    }
                    val adultBlockHtml = checkAdultSearchKeyword(url)
                    if (adultBlockHtml != null) {
                        view.loadDataWithBaseURL(null, adultBlockHtml, "text/html", "UTF-8", null)
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
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    // Facebook ক্যামেরা/মাইক্রোফোন চাইলে (story/reels আপলোডের জন্য) অনুমতি দাও
                    runOnUiThread { request.grant(request.resources) }
                }
            }
        }
    }

    /**
     * Home button চাপা / স্ক্রিন লক — WebView কে floating bubble এ পাঠাও,
     * app background এ চলে যায় কিন্তু session/data ঠিকই থাকে।
     */
    private fun launchFloating(wv: WebView, moveActivityToBack: Boolean) {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.provider.Settings.canDrawOverlays(this) else true
        if (!hasOverlay) return

        val currentUrl   = wv.url   ?: "https://m.facebook.com/"
        val currentTitle = wv.title ?: "Facebook"

        com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.pendingWebView = wv
        com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.launchNoReload(
            this, currentUrl, currentTitle
        )

        // ★ FIX: Start BackgroundAudioService so Facebook video/audio keeps
        // playing after home/lock. Capture title before webView=null because
        // evaluateJavascript won't work on a detached WebView.
        val capturedTitle = wv.title?.trim()?.takeIf { it.isNotBlank() } ?: "Facebook"
        val svcFb = Intent(
            this,
            com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService::class.java
        ).apply {
            putExtra(com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService.EXTRA_TITLE, capturedTitle)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                startForegroundService(svcFb)
            else startService(svcFb)
        } catch (_: Exception) {}

        webView = null
        isFloatingActive = true

        if (moveActivityToBack) moveTaskToBack(true)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isFloatingActive) return
        val wv = webView ?: return
        launchFloating(wv, moveActivityToBack = false)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
            return
        }
        if (isFloatingActive) {
            stopFloatingAndDestroy()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun stopFloatingAndDestroy() {
        isFloatingActive = false
        try {
            stopService(Intent(
                this,
                com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService::class.java
            ))
        } catch (_: Exception) {}
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Stop background audio service — user is back in the app, WebView plays directly
        try {
            stopService(Intent(this,
                com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService::class.java))
        } catch (_: Exception) {}
        if (isFloatingActive) {
            // Floating থেকে ফিরে এলে service বন্ধ করো — WebView pendingWebView এ চলে আসবে,
            // পরের onCreate/recreate এ সেটাই re-attach হবে। যদি activity ইতিমধ্যে
            // চালু থাকে (destroy হয়নি) তাহলে সরাসরি re-attach করো।
            isFloatingActive = false
            try {
                stopService(Intent(
                    this,
                    com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService::class.java
                ))
            } catch (_: Exception) {}

            if (webView == null) {
                val rootFrame = getRootFrame()
                rootFrame?.postDelayed({
                    val pendingWv = com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.pendingWebView
                    if (pendingWv != null && webView == null) {
                        com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.pendingWebView = null
                        webView = pendingWv
                        (pendingWv.parent as? ViewGroup)?.removeView(pendingWv)
                        rootFrame.addView(pendingWv, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                        pendingWv.resumeTimers()
                        pendingWv.onResume()
                    }
                }, 200)
            }
        } else {
            webView?.resumeTimers()
            webView?.onResume()
        }
    }

    private fun getRootFrame(): FrameLayout? {
        val contentView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        return contentView?.getChildAt(0) as? FrameLayout ?: contentView as? FrameLayout
    }

    override fun onPause() {
        super.onPause()
        flushCookies()
        if (!isFloatingActive) {
            webView?.onPause()
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        flushCookies()
        if (!isFloatingActive) {
            webView?.destroy()
        }
        webView = null
        super.onDestroy()
    }

    /** সব cookie/login session সাথে সাথে disk এ লিখে দাও — data local এ থাকবে */
    private fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }

    private fun injectFooterRemover(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasFbFooterRemoved__) return;
                window.__rasFbFooterRemoved__ = true;
                function removeFooter() {
                    try {
                        var selectors = [
                            '[role="navigation"]',
                            '[data-sigil="MBackPlaceholder"]',
                            '[data-testid="tab-bar"]',
                            '._56be','._56bf',
                            'footer','[role="contentinfo"]',
                            '[data-sigil="marea"]'
                        ];
                        selectors.forEach(function(sel) {
                            document.querySelectorAll(sel).forEach(function(el) {
                                var rect = el.getBoundingClientRect();
                                if (rect.bottom >= window.innerHeight - 100 && rect.height < 150) {
                                    el.style.display = 'none';
                                }
                            });
                        });
                        document.querySelectorAll('*').forEach(function(el) {
                            var s = window.getComputedStyle(el);
                            if ((s.position === 'fixed' || s.position === 'sticky') && s.bottom === '0px') {
                                var rect = el.getBoundingClientRect();
                                // FIX: Do not hide large overlays like search that cover the screen (height > 150)
                                if (rect.height < 150) el.style.display = 'none';
                            }
                        });
                    } catch(e) {}
                }
                removeFooter();
                try { new MutationObserver(removeFooter).observe(document.body||document.documentElement,{childList:true,subtree:true}); } catch(e){}
            })();
        """.trimIndent(), null)
    }

    private fun injectSettingsRemover(view: WebView) {
        val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
        val hideVideo = prefs.getBoolean("fb_hide_videos", false)
        val hideReels = prefs.getBoolean("fb_hide_reels", false)
        val hideNewsfeed = prefs.getBoolean("fb_hide_newsfeed", false)
        val grayscale = prefs.getBoolean("fb_grayscale", false)
        val textOnly = prefs.getBoolean("fb_text_only", false)
        
        if (!hideVideo && !hideReels && !hideNewsfeed && !grayscale && !textOnly) return
        
        val js = """
            (function() {
                if (window.__rasFbSettingsRemover__) return;
                window.__rasFbSettingsRemover__ = true;
                
                if ($grayscale) {
                    document.documentElement.style.filter = 'grayscale(100%)';
                }
                
                if ($textOnly) {
                    var style = document.createElement('style');
                    style.innerHTML = 'img, video, svg, i { display: none !important; }';
                    document.head.appendChild(style);
                }
                
                function applySettings() {
                    try {
                        var hideVideo = $hideVideo;
                        var hideReels = $hideReels;
                        var hideNewsfeed = $hideNewsfeed;
                        
                        if (hideVideo || hideReels) {
                            var tabBars = document.querySelectorAll('[role="tablist"] [role="tab"]');
                            tabBars.forEach(function(tab) {
                                var href = tab.getAttribute('href') || '';
                                if (hideVideo && href.indexOf('/watch') !== -1) tab.style.display = 'none';
                                if (hideReels && href.indexOf('/reels') !== -1) tab.style.display = 'none';
                            });
                        }
                        
                        if (hideReels) {
                            // Hide Reels section in feed by finding the text "Reels"
                            document.querySelectorAll('span, div').forEach(function(el) {
                                var txt = (el.innerText || '').toLowerCase();
                                if (txt.trim() === 'reels' && el.childElementCount === 0) {
                                    var parent = el.closest('[data-mcomponent]') || el.closest('div[style*="background"]');
                                    if (parent) parent.style.display = 'none';
                                }
                            });
                        }
                        
                        if (hideNewsfeed) {
                            // Hide main feed articles
                            document.querySelectorAll('div[data-mcomponent="MContainer"], article, [role="article"]').forEach(function(el) {
                                // Exclude header/nav elements
                                if (!el.closest('header') && !el.closest('[role="banner"]')) {
                                    el.style.display = 'none';
                                }
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

    private fun injectRemoveOpenInAppButton(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasFbOpenAppRemoverActive__) return;
                window.__rasFbOpenAppRemoverActive__ = true;

                function removeOpenAppElements() {
                    try {
                        document.querySelectorAll('a[href^="fb://"], a[href^="intent://"], a[href^="market://"]')
                            .forEach(function(el) {
                                var parent = el.closest('[class*="banner"], [id*="banner"], [data-testid*="app"], [role="banner"]');
                                if (parent) parent.style.display = 'none'; else el.style.display = 'none';
                            });
                        var bannerPhrases = [
                            'open in app', 'use app', 'get the app', 'open the facebook app',
                            'open app', 'get app', 'get apps for', 'faster experience',
                            'for a faster experience', 'download the app', 'try the app',
                            'switch to the app', 'view in app', 'continue in app',
                            'get the best experience', 'best experience on the app'
                        ];
                        document.querySelectorAll('div, a, button, span').forEach(function(el) {
                            var txt = (el.innerText || '').toLowerCase().trim();
                            if (!txt || txt.length > 80) return;
                            var hit = bannerPhrases.some(function(p) { return txt.indexOf(p) !== -1; });
                            if (hit) {
                                var parent = el.closest('[class*="banner"], [id*="banner"], [data-testid*="app"]') || el;
                                parent.style.display = 'none';
                            }
                        });
                    } catch(e) {}
                }
                removeOpenAppElements();
                try {
                    new MutationObserver(function() { removeOpenAppElements(); })
                        .observe(document.body || document.documentElement, { childList: true, subtree: true });
                } catch(e) {}
                setInterval(removeOpenAppElements, 1000);
            })();
        """.trimIndent(), null)
    }

    private fun checkAdultSearchKeyword(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("facebook.com")) return null
            val query = (uri.getQueryParameter("q") ?: "").lowercase().trim()
            if (query.isEmpty()) return null
            val matched = ADULT_SEARCH_KEYWORDS.any { query.contains(it.lowercase()) }
            if (matched) buildAdultBlockedPage() else null
        } catch (e: Exception) { null }
    }

    private fun buildAdultBlockedPage(): String {
        return """
            <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { background:#f0f2f5; display:flex; align-items:center; justify-content:center;
                       height:100vh; margin:0; font-family:sans-serif; }
                .box { text-align:center; color:#1c1e21; padding:24px; }
                .box h2 { color:#e41e3f; }
                .box button { margin-top:18px; padding:14px 28px; border:none; border-radius:10px;
                              background:#1877F2; color:#fff; font-size:15px; font-weight:700;
                              -webkit-tap-highlight-color: transparent; }
            </style></head>
            <body><div class="box"><h2>🔒 Adult Content Blocked</h2>
            <p>RasFocus Safe Mode এ এই কনটেন্ট দেখানো যাবে না।</p>
            <button onclick="if(window.RasFbBlockBridge){RasFbBlockBridge.onGoHome();}">🏠 Facebook হোমে ফিরে যান</button>
            </div></body></html>
        """.trimIndent()
    }

    /**
     * Adult-blocked page দেখানোর পর WebView এর URL "about:blank"-এর মতো stuck হয়ে
     * যেত এবং কোনো ফেরার উপায় ছিল না — এই bridge টা attach করা থাকলে block
     * page এর "ফিরে যান" বাটন সরাসরি Kotlin থেকে facebook.com এ loadUrl করে
     * দেয়, ফলে block page টা আর কখনো আটকে থাকে না।
     */
    inner class FbBlockBridge(private val wv: WebView) {
        @android.webkit.JavascriptInterface
        fun onGoHome() {
            runOnUiThread { wv.loadUrl("https://m.facebook.com/") }
        }
    }
}