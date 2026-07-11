package com.rasel.RasFocus.combo.selfcontrol.familybrowser.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ═══════════════════════════════════════════════════════════════════════
 * FacebookFloatingWindowService — Messenger chat-head স্টাইল floating bubble
 * ═══════════════════════════════════════════════════════════════════════
 *
 * YoutubeFloatingWindowService এর একই প্যাটার্নে বানানো, তবে media session/
 * play-pause যুক্ত না — কারণ এটা video না, পুরো Facebook app.
 *
 *  • Home button / screen lock → FacebookActivity WebView কে এই service এ
 *    handover করে (reload ছাড়াই, তাই login session/scroll position বজায় থাকে)
 *  • Bubble ট্যাপ করলে → FacebookActivity আবার foreground এ আসে
 *  • Full floating window মোডেও resizable/draggable — YoutubeFloatingWindowService
 *    এর মতোই আচরণ
 */
class FacebookFloatingWindowService : Service() {

    companion object {
        const val ACTION_LAUNCH   = "com.rasel.fb_float.LAUNCH"
        const val ACTION_DISMISS  = "com.rasel.fb_float.DISMISS"
        const val ACTION_MINIMIZE = "com.rasel.fb_float.MINIMIZE"
        const val ACTION_RESTORE  = "com.rasel.fb_float.RESTORE"
        const val ACTION_RETURN_TO_ACTIVITY = "com.rasel.fb_float.RETURN_TO_ACTIVITY"
        const val ACTION_ACTIVITY_RESUMED   = "com.rasel.fb_float.ACTIVITY_RESUMED"

        const val EXTRA_URL       = "fb_url"
        const val EXTRA_TITLE     = "fb_title"
        const val EXTRA_NO_RELOAD = "fb_no_reload"

        @Volatile
        var pendingWebView: WebView? = null

        // ── Memory trim support (Itel/low-RAM device fix) ──────────────────
        @Volatile
        private var activeInstance: FacebookFloatingWindowService? = null

        /**
         * Application.onTrimMemory() থেকে কল হয় যখন system memory pressure এ থাকে।
         * শুধু minimized (bubble) mode এ থাকলে WebView cache হালকা করে —
         * full floating window active থাকলে touch করা হয় না।
         */
        fun trimMemoryIfBackground() {
            val instance = activeInstance ?: return
            if (instance.isMinimized) {
                try {
                    instance.webView?.clearCache(false)
                    instance.webView?.freeMemory()
                } catch (_: Exception) { }
            }
        }

        private const val NOTIF_ID   = 7778
        private const val PREFS_NAME_FB   = "fb_float_recovery"
        private const val KEY_URL_FB      = "last_url"
        private const val KEY_WAS_OPEN_FB = "was_open"
        private const val CHANNEL_ID = "fb_float_channel"

        private const val FB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        fun launchNoReload(context: Context, url: String, title: String) {
            val i = Intent(context, FacebookFloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_NO_RELOAD, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun dismiss(context: Context) {
            context.startService(Intent(context, FacebookFloatingWindowService::class.java)
                .apply { action = ACTION_DISMISS })
        }
    }

    private lateinit var windowManager: WindowManager
    private var fullWindow: View? = null
    private var bubbleView: View? = null
    private var webView: WebView? = null
    private var ghostContainer: android.widget.FrameLayout? = null
    private var bubbleParamsRef: WindowManager.LayoutParams? = null
    private var fullParamsRef: WindowManager.LayoutParams? = null

    private var winW = 0
    private var winH = 0
    private var posX = 40
    private var posY = 120
    private var isMinimized = false
    private var isIntentionalClose = false
    private var currentUrl   = "https://m.facebook.com/"
    private var currentTitle = "Facebook"
    private var titleTvRef: android.widget.TextView? = null

    private var resizeStartW = 0
    private var resizeStartH = 0
    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f
    private var lastResizeMs = 0L
    private val RESIZE_THROTTLE = 32L

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val dm = resources.displayMetrics
        winW = (dm.widthPixels  * 0.92f).toInt()
        winH = (dm.heightPixels * 0.72f).toInt()
        posX = (dm.widthPixels  - winW) / 2
        posY = (dm.heightPixels * 0.08f).toInt()

        setupGhostContainer()
    }

    private fun setupGhostContainer() {
        ghostContainer = android.widget.FrameLayout(this)
        val dm = resources.displayMetrics
        val ghostParams = WindowManager.LayoutParams(
            dm.widthPixels, dm.heightPixels, 0, 0,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; alpha = 0f }
        try { windowManager.addView(ghostContainer, ghostParams) } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(currentTitle))

        when (intent?.action) {
            ACTION_DISMISS -> { tearDown(); stopSelf(); return START_NOT_STICKY }
            ACTION_MINIMIZE -> if (!isMinimized) showMinimized()
            ACTION_RESTORE  -> if (isMinimized) showFull()
            ACTION_RETURN_TO_ACTIVITY -> returnToActivity()
            // Recent apps থেকে ফিরলে MainActivity.onResume() পাঠাবে — bubble/full
            // window যেমন আছে তেমনই থাকবে, শুধু window z-order refresh হবে যাতে
            // নতুন Activity window এর সাথে race না করে (black screen fix)
            ACTION_ACTIVITY_RESUMED -> {
                try {
                    if (isMinimized) {
                        bubbleView?.let { v ->
                            bubbleParamsRef?.let { p ->
                                if (v.isAttachedToWindow) windowManager.updateViewLayout(v, p)
                            }
                        }
                    } else {
                        fullWindow?.let { v ->
                            fullParamsRef?.let { p ->
                                if (v.isAttachedToWindow) windowManager.updateViewLayout(v, p)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
            ACTION_LAUNCH -> {
                val newUrl   = intent.getStringExtra(EXTRA_URL)   ?: "https://m.facebook.com/"
                val newTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Facebook"
                currentUrl = newUrl
                currentTitle = newTitle
                removeFull(); removeBubble()
                // প্রথমে ছোট bubble আকারে দেখাও — Messenger chat-head এর মতো,
                // ট্যাপ করলে full floating window খুলবে
                showMinimized()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val wv = webView
        if (wv != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            pendingWebView = wv
            webView = null
        }
        removeFull(); removeBubble()
        ghostContainer?.let { runCatching { windowManager.removeView(it) } }
        ghostContainer = null
        if (activeInstance === this) activeInstance = null

        // ══════════════════════════════════════════════════════════════════
        // ★ Low-RAM device fix: process kill এ recent apps থেকে ফিরলে
        // FacebookActivity একই URL এ ফেরাতে পারে। ✕ button দিয়ে ইচ্ছাকৃত
        // close করলে save হয় না।
        // ══════════════════════════════════════════════════════════════════
        if (!isIntentionalClose) {
            try {
                getSharedPreferences(PREFS_NAME_FB, Context.MODE_PRIVATE).edit()
                    .putString(KEY_URL_FB, currentUrl)
                    .putBoolean(KEY_WAS_OPEN_FB, true)
                    .apply()
            } catch (_: Exception) { }
        }

        super.onDestroy()
    }

    private fun returnToActivity() {
        val resumeIntent = Intent(
            this,
            com.rasel.RasFocus.selfcontrol.familybrowser.FacebookActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(resumeIntent) } catch (_: Exception) { stopSelf() }
        // Activity.onResume() এ stopService() call হবে, তখন onDestroy() এ
        // WebView pendingWebView এ রেখে দেওয়া হবে
    }

    // ── Bubble (chat-head) ─────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun showMinimized() {
        isMinimized = true
        val wv = getOrBuildWebView()
        webView = wv
        if (ghostContainer != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            ghostContainer?.addView(wv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        removeFull()

        val dm = resources.displayMetrics
        val bSize = dp(58)
        val bParams = WindowManager.LayoutParams(
            bSize, bSize,
            dm.widthPixels - bSize - dp(12),
            dm.heightPixels - bSize - dp(180),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        bubbleParamsRef = bParams

        val bubble = android.widget.FrameLayout(this).apply {
            background = buildCircleBg(0xFF1877F2.toInt())
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            elevation = dp(8).toFloat()
        }
        val icon = android.widget.TextView(this).apply {
            text = "f"
            textSize = 26f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        bubble.addView(icon)

        var dStartX = 0f; var dStartY = 0f
        var bParamX = 0;  var bParamY = 0
        var moved = false
        val dm2 = resources.displayMetrics

        bubble.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dStartX = ev.rawX; dStartY = ev.rawY
                    bParamX = bParams.x; bParamY = bParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - dStartX).toInt()
                    val dy = (ev.rawY - dStartY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                        bParams.x = bParamX + dx
                        bParams.y = bParamY + dy
                        clampPosition(dm2.widthPixels, dm2.heightPixels, bParams)
                        runCatching { windowManager.updateViewLayout(bubbleView, bParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Bubble ট্যাপ করলে full floating window খুলবে (Messenger chat-head
                    // এর মতো) — আগে এখানে ভুলবশত activity তে ফিরিয়ে দিতো, ফলে
                    // "floating window" ফিচারটা আসলে কখনো দেখা যেত না।
                    if (!moved) showFull()
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        try { windowManager.addView(bubble, bParams) } catch (_: Exception) { tearDown(); stopSelf() }
        updateNotification("Facebook — চলছে, ট্যাপ করে খুলুন")
    }

    private fun showFull() {
        removeBubble()
        buildFullWindow()
    }

    // ── Full floating window ─────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun buildFullWindow() {
        isMinimized = false
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val params = WindowManager.LayoutParams(
            winW, winH, posX, posY,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        fullParamsRef = params

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = buildRoundedBg()
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }
            isFocusableInTouchMode = true
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    val wv = webView
                    if (wv != null && wv.canGoBack()) wv.goBack() else showMinimized()
                    true
                } else false
            }
        }

        val titleBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF1877F2.toInt())
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
        }

        val fbLogo = android.widget.TextView(this).apply {
            text = "facebook"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(6), 0)
        }
        val titleTv = android.widget.TextView(this).apply {
            text = currentTitle.take(24)
            textSize = 12f
            setTextColor(0xFFE4E6EB.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleTvRef = titleTv

        val btnMinimize = buildIconBtn("▬", 0xFFFFFFFF.toInt()) { showMinimized() }
        val btnClose = buildIconBtn("✕", 0xFFFFCDD2.toInt()) { tearDown(); stopSelf() }

        titleBar.addView(fbLogo)
        titleBar.addView(titleTv)
        titleBar.addView(btnMinimize)
        titleBar.addView(btnClose)

        attachDragListener(titleBar, params, screenW, screenH) {
            runCatching { windowManager.updateViewLayout(fullWindow, params) }
        }

        val wv = getOrBuildWebView()
        webView = wv
        (wv.parent as? ViewGroup)?.removeView(wv)

        val resizeHandle = android.widget.TextView(this).apply {
            text = "⠿"
            textSize = 18f
            setTextColor(0xFF999999.toInt())
            setPadding(dp(4), dp(2), dp(10), dp(6))
            gravity = Gravity.END
            setBackgroundColor(0xFFF0F2F5.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        attachResizeListener(resizeHandle, params, screenW, screenH)

        root.addView(titleBar)
        root.addView(wv, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(resizeHandle)

        fullWindow = root
        try { windowManager.addView(root, params) } catch (_: Exception) { tearDown(); stopSelf() }
        updateNotification(currentTitle)
    }

    private fun getOrBuildWebView(): WebView {
        webView?.let { return it }
        val pending = pendingWebView
        if (pending != null) {
            pendingWebView = null
            (pending.parent as? ViewGroup)?.removeView(pending)
            return pending
        }
        return buildFacebookWebView().also { it.loadUrl(currentUrl) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildFacebookWebView(): WebView {
        return WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = FB_USER_AGENT
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    // FIX: same gap as YoutubeFloatingWindowService — domain-only
                    // check missed adult keywords typed into Facebook's own
                    // search box. Added the same FirebaseKeywordSync check the
                    // full-page browser already has, and a real blocked page
                    // instead of blank content.
                    val isDomainBlocked  = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(url)
                    val isKeywordBlocked = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.containsAdultKeyword(url)
                    if (isDomainBlocked || isKeywordBlocked) {
                        return if (request.isForMainFrame) {
                           val blockedHtml = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker
                               .buildBlockedPage(url, com.rasel.RasFocus.selfcontrol.familybrowser.BlockReason.ADULT)
                           WebResourceResponse("text/html", "UTF-8", blockedHtml.byteInputStream())
                       } else {
                           WebResourceResponse("text/plain", "UTF-8", null)
                       }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    val t = view.title?.takeIf { it.isNotEmpty() } ?: "Facebook"
                    titleTvRef?.text = t.take(24)
                    currentTitle = t
                    updateNotification(t)
                    try { CookieManager.getInstance().flush() } catch (_: Exception) {}
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val isDomainBlocked  = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(url)
                    val isKeywordBlocked = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.containsAdultKeyword(url)
                    if (isDomainBlocked || isKeywordBlocked) {
                        val blockedHtml = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker
                            .buildBlockedPage(url, com.rasel.RasFocus.selfcontrol.familybrowser.BlockReason.ADULT)
                        view.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null)
                        return true
                    }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) return true
                    currentUrl = url
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String) {
                    titleTvRef?.text = title.take(24)
                    currentTitle = title
                    updateNotification(title)
                }
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }
        }
    }

    // ── Touch helpers (YoutubeFloatingWindowService থেকে reuse করা) ─────────
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(
        handle: View, params: WindowManager.LayoutParams, screenW: Int, screenH: Int, onUpdate: () -> Unit
    ) {
        var startX = 0f; var startY = 0f
        var pX = 0; var pY = 0
        var dragging = false
        handle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    startX = ev.rawX; startY = ev.rawY
                    pX = params.x; pY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        params.x = pX + (ev.rawX - startX).toInt()
                        params.y = pY + (ev.rawY - startY).toInt()
                        clampPosition(screenW, screenH, params)
                        onUpdate()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; true }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachResizeListener(handle: View, params: WindowManager.LayoutParams, screenW: Int, screenH: Int) {
        handle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartW = params.width; resizeStartH = params.height
                    resizeStartRawX = ev.rawX; resizeStartRawY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val now = System.currentTimeMillis()
                    if (now - lastResizeMs < RESIZE_THROTTLE) return@setOnTouchListener true
                    lastResizeMs = now
                    val dX = (ev.rawX - resizeStartRawX).toInt()
                    val dY = (ev.rawY - resizeStartRawY).toInt()
                    params.width = max(dp(260), min(screenW, resizeStartW + dX))
                    params.height = max(dp(280), min(screenH - params.y - dp(40), resizeStartH + dY))
                    winW = params.width; winH = params.height
                    runCatching { windowManager.updateViewLayout(fullWindow, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun clampPosition(sw: Int, sh: Int, p: WindowManager.LayoutParams) {
        p.x = max(0, min(sw - p.width, p.x))
        p.y = max(0, min(sh - p.height - dp(40), p.y))
        posX = p.x; posY = p.y
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayWindowType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun buildIconBtn(label: String, color: Int, onClick: () -> Unit) =
        android.widget.TextView(this).apply {
            text = label; textSize = 16f
            setTextColor(color)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    private fun buildRoundedBg() = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(0xFFFFFFFF.toInt())
        setStroke(dp(1), 0xFFDDDDDD.toInt())
    }

    private fun buildCircleBg(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color)
    }

    private fun removeFull() { fullWindow?.let { runCatching { windowManager.removeView(it) } }; fullWindow = null }
    private fun removeBubble() { bubbleView?.let { runCatching { windowManager.removeView(it) } }; bubbleView = null }

    private fun tearDown() {
        isIntentionalClose = true
        val wv = webView
        if (wv != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
            webView = null
        }
        pendingWebView = null
        removeFull(); removeBubble()
    }

    // ── Notification ───────────────────────────────────────────────────────
    private fun updateNotification(title: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title))
    }

    private fun buildNotification(title: String): Notification {
        val contentIntent = PendingIntent.getService(
            this, 10,
            Intent(this, FacebookFloatingWindowService::class.java).apply { action = ACTION_RETURN_TO_ACTIVITY },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, FacebookFloatingWindowService::class.java).apply { action = ACTION_DISMISS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Facebook — চলছে")
                .setContentText(title.take(60))
                .setSubText("ট্যাপ করে খুলুন")
                .setContentIntent(contentIntent)
                .addAction(Notification.Action.Builder(android.R.drawable.ic_delete, "Close", stopIntent).build())
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Facebook — চলছে")
                .setContentText(title.take(60))
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_delete, "Close", stopIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Facebook Floating Window", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Facebook floating bubble controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
}
