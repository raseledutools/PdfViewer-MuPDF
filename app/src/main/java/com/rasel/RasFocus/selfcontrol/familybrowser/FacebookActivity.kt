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
            setBackgroundColor(Color.parseColor("#f0f2f5"))  // Facebook bg — no white flash

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
                    // Navigation এর সময় white flash বন্ধ করো
                    view.setBackgroundColor(Color.parseColor("#f0f2f5"))
                    view.alpha = 1f
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.alpha = 1f
                    flushCookies()
                    // Adult keyword block — loadUrl দিয়ে redirect করো,
                    // loadDataWithBaseURL(null,...) page সাদা করে দেয়
                    val adultHtml = checkAdultSearchKeyword(url)
                    if (adultHtml != null) {
                        view.loadDataWithBaseURL("https://m.facebook.com/", adultHtml, "text/html", "UTF-8", null)
                        return
                    }
                    // JS injection — গার্ড ফ্ল্যাগ দিয়ে ensure করা যে একবারই
                    // মূল cleanup চলবে। onPageFinished এ inject করা safe
                    // কারণ DOM ready থাকে।
                    injectPageCleanup(view)
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
                        view.loadDataWithBaseURL("https://m.facebook.com/", buildAdultBlockedPage(), "text/html", "UTF-8", null)
                        return true
                    }
                    val adultBlockHtml = checkAdultSearchKeyword(url)
                    if (adultBlockHtml != null) {
                        view.loadDataWithBaseURL("https://m.facebook.com/", adultBlockHtml, "text/html", "UTF-8", null)
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
                // Floating থেকে ফেরার আগেই bg set করো যাতে 200ms গ্যাপে white না দেখায়
                rootFrame?.setBackgroundColor(Color.parseColor("#f0f2f5"))
                rootFrame?.postDelayed({
                    val pendingWv = com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.pendingWebView
                    if (pendingWv != null && webView == null) {
                        com.rasel.RasFocus.selfcontrol.familybrowser.service.FacebookFloatingWindowService.pendingWebView = null
                        webView = pendingWv
                        (pendingWv.parent as? ViewGroup)?.removeView(pendingWv)
                        pendingWv.setBackgroundColor(Color.parseColor("#f0f2f5"))
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

    // FIX: this used to be THREE separate functions, each calling
    // evaluateJavascript() and each setting up its OWN MutationObserver on
    // document.body with {childList:true, subtree:true}. On a page as
    // dynamically-updating as Facebook's feed (constant DOM mutations from
    // lazy-loaded images/content), that meant 3 independent callbacks all
    // re-scanning parts of the DOM on EVERY single mutation — the exact
    // "full DOM traverse blocks main thread → white page" pattern already
    // diagnosed once before in this file (see the old comment that used to
    // be here about removing a querySelectorAll('*') scan). Merged into one
    // function with ONE observer, and — the actual fix for the repeat —
    // DEBOUNCED: instead of running cleanup on every mutation event, it
    // waits for ~150ms of DOM quiet before running once. Also broadened the
    // "open app" banner detection with a geometry+text heuristic (bottom-of-
    // viewport + short height + "open"/"app" wording), similar in spirit to
    // the footer-removal's rect check below, so it doesn't only rely on
    // exact class/data-attribute names Facebook can change without notice.
    private fun injectPageCleanup(view: WebView) {
        val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
        val hideVideo = prefs.getBoolean("fb_hide_videos", false)
        val hideReels = prefs.getBoolean("fb_hide_reels", false)
        val hideNewsfeed = prefs.getBoolean("fb_hide_newsfeed", false)
        val grayscale = prefs.getBoolean("fb_grayscale", false)
        val textOnly = prefs.getBoolean("fb_text_only", false)

        view.evaluateJavascript("""
            (function() {
                if (window.__rasFbCleanupActive__) return;
                window.__rasFbCleanupActive__ = true;

                // One-time styles — CSS rules apply to future elements too,
                // no need to re-run these on every mutation.
                if ($grayscale) { document.documentElement.style.filter = 'grayscale(100%)'; }
                if ($textOnly) {
                    var st = document.createElement('style');
                    st.innerHTML = 'img, video, svg, i { display: none !important; }';
                    document.head.appendChild(st);
                }

                function removeFooter() {
                    try {
                        var selectors = [
                            '[role="navigation"]','[data-sigil="MBackPlaceholder"]',
                            '[data-testid="tab-bar"]','._56be','._56bf',
                            'footer','[role="contentinfo"]','[data-sigil="marea"]',
                            '[data-sigil="appbanner"]','[id*="MAppBanner"]','[class*="appBanner"]'
                        ];
                        selectors.forEach(function(sel) {
                            document.querySelectorAll(sel).forEach(function(el) {
                                var rect = el.getBoundingClientRect();
                                if (rect.bottom >= window.innerHeight - 100 && rect.height < 150)
                                    el.style.display = 'none';
                            });
                        });
                    } catch(e) {}
                }

                function removeOpenAppBanner() {
                    try {
                        // Pass 1 — known markers (href scheme, data attrs)
                        document.querySelectorAll('a[href^="fb://"], a[href^="intent://"], a[href^="market://"]')
                            .forEach(function(el) {
                                var parent = el.closest('[class*="banner"],[id*="banner"],[data-testid*="app"],[role="banner"]');
                                if (parent) parent.style.display = 'none'; else el.style.display = 'none';
                            });
                        document.querySelectorAll('[data-sigil*="appbanner"],[id*="MAppBanner"],[class*="appBanner"]')
                            .forEach(function(el) { el.style.display = 'none'; });

                        // Pass 2 — geometry+text heuristic fallback, in case
                        // Facebook's exact markup no longer matches Pass 1.
                        // Only checks direct children of body/main containers
                        // near the bottom of the viewport — bounded scope,
                        // not a full-page scan.
                        var candidates = document.querySelectorAll('body > div, [role="banner"], [id*="banner"], [class*="banner"]');
                        candidates.forEach(function(el) {
                            var rect = el.getBoundingClientRect();
                            if (rect.height === 0 || rect.height > 160) return;
                            if (rect.bottom < window.innerHeight - 120) return;
                            var txt = (el.textContent || '').toLowerCase();
                            if (txt.indexOf('open') !== -1 && (txt.indexOf('app') !== -1 || txt.indexOf('facebook') !== -1)) {
                                el.style.display = 'none';
                            }
                        });
                    } catch(e) {}
                }

                function applySettings() {
                    try {
                        if ($hideVideo || $hideReels) {
                            document.querySelectorAll('[role="tablist"] [role="tab"]').forEach(function(tab) {
                                var href = tab.getAttribute('href') || '';
                                if ($hideVideo && href.indexOf('/watch') !== -1) tab.style.display = 'none';
                                if ($hideReels && href.indexOf('/reels') !== -1) tab.style.display = 'none';
                            });
                        }
                        if ($hideReels) {
                            document.querySelectorAll('span, div').forEach(function(el) {
                                var txt = (el.textContent || '').toLowerCase();
                                if (txt.trim() === 'reels' && el.childElementCount === 0) {
                                    var parent = el.closest('[data-mcomponent]') || el.closest('div[style*="background"]');
                                    if (parent) parent.style.display = 'none';
                                }
                            });
                        }
                        if ($hideNewsfeed) {
                            document.querySelectorAll('div[data-mcomponent="MContainer"], article, [role="article"]').forEach(function(el) {
                                if (!el.closest('header') && !el.closest('[role="banner"]')) el.style.display = 'none';
                            });
                        }
                    } catch(e) {}
                }

                function runCleanup() {
                    removeFooter();
                    removeOpenAppBanner();
                    applySettings();
                }
                runCleanup();

                try {
                    // Debounced — waits for DOM mutations to settle for
                    // ~150ms before running cleanup once, instead of once
                    // PER mutation event. This is the actual fix for pages
                    // that mutate the DOM frequently (image/content lazy-load).
                    var pending = null;
                    new MutationObserver(function(mutations) {
                        if (!mutations.some(function(m) { return m.addedNodes.length > 0; })) return;
                        if (pending) clearTimeout(pending);
                        pending = setTimeout(runCleanup, 150);
                    }).observe(document.body || document.documentElement, {childList:true, subtree:true});
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun checkAdultSearchKeyword(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("facebook.com")) return null

            // Facebook search URL formats:
            // 1. m.facebook.com/search/top/?q=keyword
            // 2. m.facebook.com/search/?q=keyword
            // 3. www.facebook.com/search/top/?q=keyword
            // 4. keyword may come as path segment: /search/keyword
            val query = (
                uri.getQueryParameter("q")
                ?: uri.getQueryParameter("query")
                ?: run {
                    // path segment এ keyword: /search/top/keyword
                    val pathParts = uri.pathSegments
                    val searchIdx = pathParts.indexOfFirst { it == "search" }
                    if (searchIdx >= 0 && searchIdx + 2 < pathParts.size) pathParts[searchIdx + 2]
                    else null
                }
            )?.lowercase()?.trim() ?: return null

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