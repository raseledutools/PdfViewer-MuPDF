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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * FloatingWindowService — MULTI-WINDOW সংস্করণ
 *
 * প্রতিটা tab এর জন্য আলাদা floating bubble/window। একসাথে একাধিক tab
 * floating অবস্থায় থাকতে পারে — নতুন tab float করলে আগেরটা বন্ধ হয় না।
 *
 * প্রতিটা window এর নিজস্ব state (`FloatWindow`) থাকে, তাই কোনো shared
 * class-level var নেই যেটা windows-এর মধ্যে conflict করতে পারে।
 *
 * SYSTEM_ALERT_WINDOW permission দরকার।
 *
 * Usage:
 *   FloatingWindowService.launch(context, url, title)   // নতুন id দিয়ে launch
 *   FloatingWindowService.dismissAll(context)           // সব বন্ধ করো
 */
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_LAUNCH      = "com.rasel.familybrowser.FLOAT_LAUNCH"
        const val ACTION_DISMISS     = "com.rasel.familybrowser.FLOAT_DISMISS"
        const val ACTION_DISMISS_ALL = "com.rasel.familybrowser.FLOAT_DISMISS_ALL"
        const val EXTRA_URL      = "float_url"
        const val EXTRA_TITLE    = "float_title"
        const val EXTRA_WINDOW_ID = "float_window_id"
        private const val NOTIF_ID   = 8888
        private const val CHANNEL_ID = "float_window_channel"
        private const val BUBBLE_CONTENT_TAG = 991001
        private const val BUBBLE_ICON_TAG    = 991002

        /** নতুন floating window খোলে — প্রতিটা কলের জন্য নতুন unique windowId তৈরি হয়, তাই আগেরগুলো বন্ধ হয় না। */
        fun launch(context: Context, url: String, title: String): String {
            val windowId = "fw_${System.currentTimeMillis()}_${(0..999).random()}"
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_WINDOW_ID, windowId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
            return windowId
        }

        /** একটা নির্দিষ্ট windowId এর floating window বন্ধ করে। */
        fun dismiss(context: Context, windowId: String) {
            context.startService(
                Intent(context, FloatingWindowService::class.java).apply {
                    action = ACTION_DISMISS
                    putExtra(EXTRA_WINDOW_ID, windowId)
                })
        }

        /** সব floating window বন্ধ করে (পুরনো single-window callers এর জন্য backward-compatible)। */
        fun dismissAll(context: Context) {
            context.startService(
                Intent(context, FloatingWindowService::class.java).apply {
                    action = ACTION_DISMISS_ALL
                })
        }
    }

    /** প্রতিটা floating tab-এর নিজস্ব সম্পূর্ণ state — কোনো shared var নেই, তাই একাধিক window নিরাপদে সহাবস্থান করতে পারে। */
    private inner class FloatWindow(val id: String, val url: String, val title: String) {
        var floatRoot: View? = null
        var webView: WebView? = null
        lateinit var params: WindowManager.LayoutParams

        var winW = 0
        var winH = 0
        var posX = 0
        var posY = 0

        var isMinimized = false
        var expandedW = 0
        var expandedH = 0
        var expandedX = 0
        var expandedY = 0

        var resizeStartW = 0
        var resizeStartH = 0
        var resizeStartX = 0f
        var resizeStartY = 0f
    }

    private lateinit var windowManager: WindowManager
    private val windows = LinkedHashMap<String, FloatWindow>()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        when (intent?.action) {
            ACTION_DISMISS -> {
                val id = intent.getStringExtra(EXTRA_WINDOW_ID)
                if (id != null) removeWindow(id)
                if (windows.isEmpty()) { stopSelf(); return START_NOT_STICKY }
            }
            ACTION_DISMISS_ALL -> {
                removeAllWindows()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LAUNCH -> {
                val url      = intent.getStringExtra(EXTRA_URL)   ?: "about:blank"
                val title    = intent.getStringExtra(EXTRA_TITLE) ?: "Floating Tab"
                val windowId = intent.getStringExtra(EXTRA_WINDOW_ID)
                    ?: "fw_${System.currentTimeMillis()}"
                addWindow(windowId, url, title)
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeAllWindows()
        super.onDestroy()
    }

    // ── Window creation ────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled", "InflateParams")
    private fun addWindow(windowId: String, url: String, title: String) {
        val fw = FloatWindow(windowId, url, title)
        windows[windowId] = fw

        val dm = resources.displayMetrics
        fw.winW = (dm.widthPixels  * 0.96f).toInt()
        fw.winH = (dm.heightPixels * 0.92f).toInt()

        // নতুন window গুলো একটু একটু করে cascade (সরিয়ে) বসাও, যাতে একে অপরকে পুরোপুরি না ঢাকে
        val cascadeOffset = (windows.size - 1) * dp(28)
        fw.posX = 60 + cascadeOffset
        fw.posY = 140 + cascadeOffset

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            fw.winW, fw.winH, fw.posX, fw.posY,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        fw.params = params

        val root = buildRootView(fw)
        fw.floatRoot = root
        windowManager.addView(root, params)
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildRootView(fw: FloatWindow): View {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val params = fw.params

        // Container — normal content (titlebar + webview + resize handle)
        val container = object : android.widget.LinearLayout(this) {}.apply {
            id = BUBBLE_CONTENT_TAG
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            clipToOutline = true
        }

        // ── Title bar ──────────────────────────────────────────────────────────
        val titleBar = android.widget.LinearLayout(this).apply {
            orientation  = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFF2563EB.toInt()) // BrandBlue
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = android.widget.TextView(this).apply {
            text      = fw.title.take(30)
            textSize  = 13f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // Size toggle button (small ↔ large)
        val btnSize = buildIconButton("⊡") {
            val newW: Int
            val newH: Int
            if (fw.winW < screenW * 0.85f) {
                newW = (screenW * 0.90f).toInt()
                newH = (screenH * 0.80f).toInt()
            } else {
                newW = (screenW * 0.60f).toInt()
                newH = (screenH * 0.50f).toInt()
            }
            fw.winW = newW; fw.winH = newH
            params.width = fw.winW; params.height = fw.winH
            clampPosition(screenW, screenH, params, fw)
            windowManager.updateViewLayout(fw.floatRoot, params)
        }

        // Minimize button — WebView destroy না করে ছোট bubble এ shrink করে
        val btnMinimize = buildIconButton("▬") {
            showMinimized(fw)
        }

        // Open in main browser — এই window বন্ধ করে main app এ ঐ page চালু করে
        val btnOpen = buildIconButton("⤤") {
            val i = Intent(this@FloatingWindowService, FamilyBrowserActivity::class.java).apply {
                data  = android.net.Uri.parse(fw.url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(i)
            removeWindow(fw.id)
            if (windows.isEmpty()) stopSelf()
        }

        // Close button — শুধু এই windowId টাই বন্ধ হবে, বাকিগুলো থাকবে
        val btnClose = buildIconButton("✕") {
            removeWindow(fw.id)
            if (windows.isEmpty()) stopSelf()
        }

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
                    params.flags = params.flags or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(fw.floatRoot, params)
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    params.x = dragParamsX + (ev.rawX - dragStartX).toInt()
                    params.y = dragParamsY + (ev.rawY - dragStartY).toInt()
                    clampPosition(screenW, screenH, params, fw)
                    windowManager.updateViewLayout(fw.floatRoot, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    params.flags = params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    windowManager.updateViewLayout(fw.floatRoot, params)
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

            isFocusable = true
            isFocusableInTouchMode = true

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val reqUrl = request.url.toString()
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

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): Boolean {
                    val reqUrl = request.url.toString()

                    val isDomainBlocked  = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(reqUrl)
                    val isKeywordBlocked = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.containsAdultKeyword(reqUrl)
                    if (isDomainBlocked || isKeywordBlocked) {
                        view.loadDataWithBaseURL(null, buildFloatingBlockPage(), "text/html", "UTF-8", null)
                        return true
                    }

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
                    injectBannerRemover(view)
                    injectFooterRemover(view)
                }
            }
            loadUrl(fw.url)
        }
        fw.webView = wv

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
                    fw.resizeStartW = params.width
                    fw.resizeStartH = params.height
                    fw.resizeStartX = ev.rawX
                    fw.resizeStartY = ev.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dX = (ev.rawX - fw.resizeStartX).toInt()
                    val dY = (ev.rawY - fw.resizeStartY).toInt()
                    params.width  = max(dp(200), min(screenW, fw.resizeStartW + dX))
                    params.height = max(dp(150), min(screenH - params.y, fw.resizeStartH + dY))
                    fw.winW = params.width; fw.winH = params.height
                    windowManager.updateViewLayout(fw.floatRoot, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {}
            }
            true
        }

        container.addView(titleBar)
        container.addView(wv)
        container.addView(resizeHandle)

        // ── Bubble icon — minimize করলে এটা visible হয়, ট্যাপ করলে restore, drag করেও সরানো যায় ──
        var bubbleDragStartX = 0f
        var bubbleDragStartY = 0f
        var bubbleParamsX = 0
        var bubbleParamsY = 0
        var bubbleDragging = false
        var bubbleMoved = false

        val bubbleIcon = android.widget.TextView(this).apply {
            id = BUBBLE_ICON_TAG
            text = "🌐"
            textSize = 26f
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF2563EB.toInt())
            visibility = View.GONE
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        bubbleDragging = true
                        bubbleMoved = false
                        bubbleDragStartX = ev.rawX
                        bubbleDragStartY = ev.rawY
                        bubbleParamsX = params.x
                        bubbleParamsY = params.y
                    }
                    MotionEvent.ACTION_MOVE -> if (bubbleDragging) {
                        val dX = (ev.rawX - bubbleDragStartX).toInt()
                        val dY = (ev.rawY - bubbleDragStartY).toInt()
                        if (abs(dX) > 8 || abs(dY) > 8) bubbleMoved = true
                        params.x = bubbleParamsX + dX
                        params.y = bubbleParamsY + dY
                        clampPosition(screenW, screenH, params, fw)
                        windowManager.updateViewLayout(fw.floatRoot, params)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        bubbleDragging = false
                        if (!bubbleMoved) restoreFromMinimized(fw)
                    }
                }
                true
            }
        }

        // Outer wrapper — content ও bubble icon একই জায়গায় স্ট্যাক করা, প্রয়োজনমতো toggle
        val outer = android.widget.FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                }
            }
        }
        outer.addView(container)
        outer.addView(bubbleIcon)

        return outer
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun clampPosition(screenW: Int, screenH: Int, p: WindowManager.LayoutParams, fw: FloatWindow) {
        p.x = max(0, min(screenW - p.width,  p.x))
        p.y = max(0, min(screenH - p.height, p.y))
        fw.posX = p.x; fw.posY = p.y
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

    private fun removeWindow(windowId: String) {
        val fw = windows.remove(windowId) ?: return
        fw.webView?.destroy(); fw.webView = null
        fw.floatRoot?.let { runCatching { windowManager.removeView(it) } }
        fw.floatRoot = null
    }

    private fun removeAllWindows() {
        val ids = windows.keys.toList()
        ids.forEach { removeWindow(it) }
    }

    // ── Minimize ↔ Restore ──────────────────────────────────────────────────────
    // WebView destroy না করে window কে ছোট bubble সাইজে shrink করে (YouTube/Facebook
    // floating এর মতোই আচরণ), bubble ট্যাপ করলে আগের সাইজে ফিরে যায়।
    private fun showMinimized(fw: FloatWindow) {
        if (fw.isMinimized) return
        val root = fw.floatRoot ?: return
        val params = fw.params
        val dm = resources.displayMetrics

        fw.expandedW = params.width
        fw.expandedH = params.height
        fw.expandedX = params.x
        fw.expandedY = params.y

        val bubbleSize = dp(64)
        // Minimized bubble গুলো একটার নিচে একটা stack — কোন windows আগে থেকে
        // minimized আছে তার সংখ্যা গুনে vertical offset ঠিক করা হয়
        val minimizedCountBefore = windows.values.count { it !== fw && it.isMinimized }
        params.width  = bubbleSize
        params.height = bubbleSize
        params.x = dm.widthPixels - bubbleSize - dp(16)
        params.y = dp(140) + minimizedCountBefore * (bubbleSize + dp(12))
        fw.winW = bubbleSize; fw.winH = bubbleSize
        fw.posX = params.x; fw.posY = params.y

        root.findViewById<View>(BUBBLE_CONTENT_TAG)?.visibility = View.GONE
        root.findViewById<View>(BUBBLE_ICON_TAG)?.visibility = View.VISIBLE

        windowManager.updateViewLayout(root, params)
        fw.isMinimized = true
    }

    private fun restoreFromMinimized(fw: FloatWindow) {
        if (!fw.isMinimized) return
        val root = fw.floatRoot ?: return
        val params = fw.params

        params.width  = fw.expandedW
        params.height = fw.expandedH
        params.x = fw.expandedX
        params.y = fw.expandedY
        fw.winW = fw.expandedW; fw.winH = fw.expandedH
        fw.posX = fw.expandedX; fw.posY = fw.expandedY

        root.findViewById<View>(BUBBLE_CONTENT_TAG)?.visibility = View.VISIBLE
        root.findViewById<View>(BUBBLE_ICON_TAG)?.visibility = View.GONE

        windowManager.updateViewLayout(root, params)
        fw.isMinimized = false
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
            Intent(this, FloatingWindowService::class.java).apply { action = ACTION_DISMISS_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val count = windows.size
        val text = if (count > 1) "$count floating tabs active" else "Tap to open browser"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("RasBrowser — Floating Tab${if (count > 1) "s" else ""}")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Close All", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
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
    private fun injectFooterRemover(view: android.webkit.WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasFloatFooterRemoved__) return;
                window.__rasFloatFooterRemoved__ = true;
                function removeFooter() {
                    try {
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
