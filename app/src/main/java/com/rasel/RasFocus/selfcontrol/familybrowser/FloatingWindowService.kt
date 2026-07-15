package com.rasel.RasFocus.selfcontrol.familybrowser.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import com.rasel.RasFocus.selfcontrol.familybrowser.FamilyBrowserActivity
import kotlin.math.max
import kotlin.math.min

/**
 * FloatingWindowService
 *
 * যেকোনো tab এর URL কে একটা draggable, resizable floating WebView এ দেখায়।
 * SYSTEM_ALERT_WINDOW permission দরকার।
 *
 * Usage:
 *   FloatingWindowService.launch(context, url, title)
 *   FloatingWindowService.dismiss(context)
 */
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_LAUNCH  = "com.rasel.familybrowser.FLOAT_LAUNCH"
        // Browser header-এ floating indicator dot-এর জন্য
        var isRunning: Boolean = false
        const val ACTION_DISMISS = "com.rasel.familybrowser.FLOAT_DISMISS"
        const val EXTRA_URL      = "float_url"
        const val EXTRA_TITLE    = "float_title"
        private const val NOTIF_ID   = 8888
        private const val CHANNEL_ID = "float_window_channel"

        fun launch(context: Context, url: String, title: String) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun dismiss(context: Context) {
            context.startService(
                Intent(context, FloatingWindowService::class.java).apply {
                    action = ACTION_DISMISS
                })
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatRoot: View?    = null
    private var webView:  WebView? = null

    // Window size & position
    private var winW = 0
    private var winH = 0
    private var posX = 100
    private var posY = 200

    // Resize state
    private var resizeStartW = 0
    private var resizeStartH = 0
    private var resizeStartX = 0f
    private var resizeStartY = 0f

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Default size = 60% of screen width, 50% height
        val dm = resources.displayMetrics
        winW = (dm.widthPixels  * 0.60f).toInt()
        winH = (dm.heightPixels * 0.50f).toInt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        when (intent?.action) {
            ACTION_DISMISS -> { removeWindow(); stopSelf(); return START_NOT_STICKY }
            ACTION_LAUNCH  -> {
                val url   = intent.getStringExtra(EXTRA_URL)   ?: "about:blank"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Floating Tab"
                removeWindow()        // পুরনো থাকলে সরাও
                addWindow(url, title)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        removeWindow()
        super.onDestroy()
    }

    // ── Window creation ────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled", "InflateParams")
    private fun addWindow(url: String, title: String) {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            winW, winH, posX, posY,
            overlayType,
            // FLAG_NOT_FOCUSABLE সরানো হয়েছে — WebView এ keyboard আসার জন্য
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // ── Root container ─────────────────────────────────────────────────────
        val root = buildRootView(url, title, params)
        floatRoot = root
        windowManager.addView(root, params)
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildRootView(
        url: String,
        title: String,
        params: WindowManager.LayoutParams
    ): View {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Container
        val container = object : android.widget.LinearLayout(this) {}.apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            clipToOutline = true
        }

        isRunning = true
        // ── Title bar ──────────────────────────────────────────────────────────
        val titleBar = android.widget.LinearLayout(this).apply {
            orientation  = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFF2563EB.toInt()) // BrandBlue
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = android.widget.TextView(this).apply {
            text      = title.take(30)
            textSize  = 13f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // ── Minimize button — window ছোট করে রাখে, close করে না ─────────────
        var isMinimized = false
        val btnMinimize = buildIconButton("−") {
            if (isMinimized) {
                // Restore
                floatRoot?.visibility = android.view.View.VISIBLE
                params.width  = winW
                params.height = winH
                isMinimized   = false
            } else {
                // Minimize → শুধু title bar দেখাবে
                floatRoot?.visibility = android.view.View.VISIBLE
                params.width  = (screenW * 0.55f).toInt()
                params.height = dp(44)
                isMinimized   = true
            }
            floatRoot?.let { windowManager.updateViewLayout(it, params) }
        }

        // ── Size toggle button (small ↔ large) ──────────────────────────────
        val btnSize = buildIconButton("⊡") {
            if (isMinimized) {
                // Minimized থেকে restore করো
                params.width  = winW
                params.height = winH
                isMinimized   = false
                floatRoot?.let { windowManager.updateViewLayout(it, params) }
                return@buildIconButton
            }
            val newW: Int
            val newH: Int
            if (winW < screenW * 0.85f) {
                newW = (screenW * 0.92f).toInt()
                newH = (screenH * 0.82f).toInt()
            } else {
                newW = (screenW * 0.60f).toInt()
                newH = (screenH * 0.52f).toInt()
            }
            winW = newW; winH = newH
            params.width = winW; params.height = winH
            clampPosition(screenW, screenH, params)
            floatRoot?.let { windowManager.updateViewLayout(it, params) }
        }

        // ── "Open in App" button — floating বন্ধ করে main browser-এ ─────────
        // Facebook/YouTube native app-এর floating window-এর মতো behavior:
        // floating close হয়, main app-এ ওই exact page-টা খোলে।
        val btnOpen = buildIconButton("⤢") {
            val currentUrl = webView?.url ?: url
            val i = Intent(this@FloatingWindowService, FamilyBrowserActivity::class.java).apply {
                data  = android.net.Uri.parse(currentUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(i)
            removeWindow()
            stopSelf()
        }

        // ── Close button ──────────────────────────────────────────────────────
        val btnClose = buildIconButton("✕") {
            removeWindow()
            stopSelf()
        }

        // Layout: [Title] [−] [⊡] [⤢] [✕]
        titleBar.addView(titleTv)
        titleBar.addView(btnMinimize)
        titleBar.addView(btnSize)
        titleBar.addView(btnOpen)
        titleBar.addView(btnClose)

        // ── Drag: titleBar দিয়ে drag ──────────────────────────────────────────
        var dragStartX = 0f
        var dragStartY = 0f
        var dragParamsX = 0
        var dragParamsY = 0
        var dragging = false

        titleBar.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    dragStartX = ev.rawX
                    dragStartY = ev.rawY
                    dragParamsX = params.x
                    dragParamsY = params.y
                    // Drag এর সময় keyboard সরিয়ে দাও
                    params.flags = params.flags or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    floatRoot?.let { windowManager.updateViewLayout(it, params) }
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    params.x = dragParamsX + (ev.rawX - dragStartX).toInt()
                    params.y = dragParamsY + (ev.rawY - dragStartY).toInt()
                    clampPosition(screenW, screenH, params)
                    floatRoot?.let { windowManager.updateViewLayout(it, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    // Drag শেষে keyboard আবার আসতে পারবে
                    params.flags = params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    floatRoot?.let { windowManager.updateViewLayout(it, params) }
                }
            }
            true
        }

        // ── WebView ────────────────────────────────────────────────────────────
        val wv = WebView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.apply {
                javaScriptEnabled    = true
                domStorageEnabled    = true
                loadWithOverviewMode = true
                useWideViewPort      = true
                builtInZoomControls  = true
                displayZoomControls  = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
            }

            // ── WebView focusable — keyboard কাজ করবে ────────────────────
            isFocusable = true
            isFocusableInTouchMode = true

            webViewClient = object : WebViewClient() {

                // ── Adult block (shouldInterceptRequest) ──────────────────
                // Sub-resource silent block, main frame → block page (আগে
                // empty HTML দিচ্ছিল → white screen হত)
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val reqUrl = request.url.toString()
                    // FIX: was domain-only (isAdultSite) — missed adult
                    // keywords typed into a search box on an otherwise
                    // non-adult domain. Same FirebaseKeywordSync check the
                    // full-page browser already has.
                    val isDomainBlocked  = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(reqUrl)
                    val isKeywordBlocked = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.containsAdultKeyword(reqUrl)
                    if (isDomainBlocked || isKeywordBlocked) {
                        return if (request.isForMainFrame) {
                            android.webkit.WebResourceResponse(
                                "text/html", "UTF-8",
                                buildFloatingBlockPage().byteInputStream(Charsets.UTF_8)
                            )
                        } else {
                            android.webkit.WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                // ── Adult block + Safe Search (shouldOverrideUrlLoading) ──
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): Boolean {
                    val reqUrl = request.url.toString()

                    // Adult site or keyword → block page দেখাও
                    val isDomainBlocked  = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(reqUrl)
                    val isKeywordBlocked = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.containsAdultKeyword(reqUrl)
                    if (isDomainBlocked || isKeywordBlocked) {
                        view.loadDataWithBaseURL(null, buildFloatingBlockPage(), "text/html", "UTF-8", null)
                        return true
                    }

                    // Safe Search enforce
                    val safeUrl = com.rasel.RasFocus.selfcontrol.familybrowser.SafeSearchEnforcer
                        .enforceIfNeeded(reqUrl)
                    if (safeUrl != null) {
                        view.loadUrl(safeUrl)
                        return true
                    }

                    return false
                }

                override fun onPageFinished(view: WebView, pageUrl: String) {
                    titleTv.text = (view.title ?: pageUrl).take(30)
                    // FIX: floating mode-এ JS inject হচ্ছিল না — তাই "Open in App"
                    // banner পুরো screen ঢেকে white screen দেখাত।
                    injectBannerRemover(view)
                    injectFooterRemover(view)
                }
            }
            loadUrl(url)
        }
        webView = wv

        // ── Resize handle (bottom-right corner) ───────────────────────────────
        val resizeHandle = android.widget.TextView(this).apply {
            text      = "⠿"
            textSize  = 18f
            setTextColor(0xFF888888.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            gravity   = Gravity.END
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        resizeHandle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartW = params.width
                    resizeStartH = params.height
                    resizeStartX = ev.rawX
                    resizeStartY = ev.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dX = (ev.rawX - resizeStartX).toInt()
                    val dY = (ev.rawY - resizeStartY).toInt()
                    params.width  = max(dp(200), min(screenW, resizeStartW + dX))
                    params.height = max(dp(150), min(screenH - params.y, resizeStartH + dY))
                    winW = params.width; winH = params.height
                    floatRoot?.let { windowManager.updateViewLayout(it, params) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {}
            }
            true
        }

        container.addView(titleBar)
        container.addView(wv)
        container.addView(resizeHandle)
        return container
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun clampPosition(screenW: Int, screenH: Int, p: WindowManager.LayoutParams) {
        p.x = max(0, min(screenW - p.width,  p.x))
        p.y = max(0, min(screenH - p.height, p.y))
        posX = p.x; posY = p.y
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildIconButton(label: String, onClick: () -> Unit) =
        android.widget.TextView(this).apply {
            text     = label
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { onClick() }
        }

    private fun removeWindow() {
        webView?.destroy(); webView = null
        floatRoot?.let { runCatching { windowManager.removeView(it) } }
        floatRoot = null
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, FamilyBrowserActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingWindowService::class.java).apply { action = ACTION_DISMISS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("RasBrowser — Floating Tab")
            .setContentText("Tap to open browser")
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Close", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Floating Browser Tab",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating browser window"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    // ── Block page — floating mode-এ adult site block হলে এই HTML দেখাবে ──
    private fun buildFloatingBlockPage(): String = """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
            body{background:#f0f2f5;display:flex;align-items:center;justify-content:center;
                 height:100vh;margin:0;font-family:sans-serif;text-align:center;padding:16px;box-sizing:border-box;}
            h2{color:#e41e3f;font-size:18px;margin-bottom:8px;}
            p{color:#444;font-size:13px;}
            button{margin-top:14px;padding:12px 24px;border:none;border-radius:8px;
                   background:#1877F2;color:#fff;font-size:14px;font-weight:700;}
        </style></head>
        <body><div>
            <div style="font-size:40px">🔒</div>
            <h2>Adult Content Blocked</h2>
            <p>RasFocus Safe Mode এ এই কনটেন্ট দেখানো যাবে না।</p>
            <button onclick="history.back()">← ফিরে যান</button>
        </div></body></html>
    """.trimIndent()

    // ── "Open in App" banner remove — floating-এ white screen-এর মূল কারণ ──
    private fun injectBannerRemover(view: android.webkit.WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasFloatBannerRemoved__) return;
                window.__rasFloatBannerRemoved__ = true;
                var phrases = ['open in app','use app','get the app','open app',
                               'get app','download the app','try the app','faster experience',
                               'continue in app','switch to app','view in app'];
                function remove() {
                    try {
                        document.querySelectorAll('a[href^="fb://"],a[href^="intent://"]').forEach(function(el) {
                            var p = el.closest('[class*="banner"],[id*="banner"],[data-testid*="app"]');
                            if (p) p.remove(); else el.remove();
                        });
                        document.querySelectorAll('div,a,button,span').forEach(function(el) {
                            var txt = (el.innerText||'').toLowerCase().trim();
                            if (!txt || txt.length > 60) return;
                            if (phrases.some(function(p){return txt.indexOf(p)!==-1;})) {
                                var par = el.closest('[class*="banner"],[id*="banner"],[data-testid*="app"]') || el;
                                par.style.display = 'none';
                            }
                        });
                    } catch(e) {}
                }
                remove();
                try { new MutationObserver(remove).observe(document.body||document.documentElement,{childList:true,subtree:true}); } catch(e){}
                setInterval(remove, 1500);
            })();
        """.trimIndent(), null)
    }

    // ── Facebook footer/bottom-nav layer remove ───────────────────────────
    // FB mobile-এ নিচে একটা sticky footer থাকে যেটা floating window-এর
    // ছোট সাইজে content area খেয়ে ফেলে। এটা hide করলে বেশি content দেখা যায়।
    private fun injectFooterRemover(view: android.webkit.WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasFloatFooterRemoved__) return;
                window.__rasFloatFooterRemoved__ = true;
                function removeFooter() {
                    try {
                        // FB mobile bottom nav bar
                        var selectors = [
                            '[role="navigation"]',
                            '[data-sigil="MBackPlaceholder"]',
                            '[data-testid="tab-bar"]',
                            '._56be', '._56bf',
                            'footer', '[role="contentinfo"]'
                        ];
                        selectors.forEach(function(sel) {
                            document.querySelectorAll(sel).forEach(function(el) {
                                var rect = el.getBoundingClientRect();
                                if (rect.bottom >= window.innerHeight - 80 && rect.height < 120) {
                                    el.style.display = 'none';
                                }
                            });
                        });
                        // যেকোনো sticky/fixed bottom element
                        document.querySelectorAll('*').forEach(function(el) {
                            var s = window.getComputedStyle(el);
                            if ((s.position === 'fixed' || s.position === 'sticky') && s.bottom === '0px') {
                                var rect = el.getBoundingClientRect();
                                if (rect.height < 80) el.style.display = 'none';
                            }
                        });
                    } catch(e) {}
                }
                removeFooter();
                setTimeout(removeFooter, 1000);
                setTimeout(removeFooter, 3000);
                try { new MutationObserver(removeFooter).observe(document.body||document.documentElement,{childList:true,subtree:true}); } catch(e){}
            })();
        """.trimIndent(), null)
    }
}
