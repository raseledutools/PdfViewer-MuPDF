package com.rasel.RasFocus.combo.selfcontrol.familybrowser.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import com.rasel.RasFocus.combo.selfcontrol.familybrowser.FamilyBrowserActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/**
 * ═══════════════════════════════════════════════════════════════════════
 * YoutubeFloatingWindowService — Mini Player + Back Press Support
 * ═══════════════════════════════════════════════════════════════════════
 *
 * নতুন Feature: Mini Player (Back Button Corner Floating)
 * ───────────────────────────────────────────────────────
 *
 * MINI PLAYER MODE (isMiniPlayer = true):
 *   YoutubeActivity তে back press করলে এই mode এ launch হয়।
 *   Native YouTube app এর মতো behavior:
 *   - Screen এর corner এ ছোট (240×135dp) floating window
 *   - Video চলতে থাকে, user যেকোনো app scroll করতে পারে
 *   - Floating tap করলে → ACTION_RETURN_TO_ACTIVITY broadcast →
 *     YoutubeActivity আবার সামনে আসে + floating বন্ধ হয়
 *   - Mini player এ drag করে position বদলানো যায়
 *   - Mini player এ ✕ বাটন → সব বন্ধ
 *
 * FULL FLOATING MODE (isMiniPlayer = false):
 *   Home button press করলে এই mode এ launch হয় (আগের মতো)।
 *   - বড় resizable floating window
 *   - Minimize → bubble, Restore → full
 *   - Back press → WebView history navigate বা minimize
 *
 * WebView Return Flow (Mini Player → YoutubeActivity):
 *   1. User mini player tap করে
 *   2. ACTION_RETURN_TO_ACTIVITY broadcast → YoutubeActivity receive করে
 *   3. YoutubeActivity.onResume() → stopService() call করে
 *   4. onDestroy() এ WebView destroy করো না — pendingWebView এ রেখে দাও
 *   5. YoutubeActivity.onResume() → pendingWebView থেকে WebView নিয়ে re-attach
 *
 * ═══════════════════════════════════════════════════════════════════════
 * পুরনো Bug Fixes (আগের version থেকে):
 * ═══════════════════════════════════════════════════════════════════════
 *
 * FIX 1 — Minimize (▬) করলে audio বন্ধ: Ghost container দিয়ে WebView
 *          সবসময় live view hierarchy তে রাখা।
 * FIX 2 — Resize করলে pause: Minimum size dp(280) + throttle।
 * FIX 3 — Bubble → Full restore করলে reload: ResizeObserver block।
 */
class YoutubeFloatingWindowService : Service() {

    // ── Constants ──────────────────────────────────────────────────────────────
    companion object {
        const val ACTION_LAUNCH            = "com.rasel.yt_float.LAUNCH"
        const val ACTION_DISMISS           = "com.rasel.yt_float.DISMISS"
        const val ACTION_MINIMIZE          = "com.rasel.yt_float.MINIMIZE"
        const val ACTION_RESTORE           = "com.rasel.yt_float.RESTORE"
        const val ACTION_PLAY_PAUSE        = "com.rasel.yt_float.PLAY_PAUSE"
        const val ACTION_PREV              = "com.rasel.yt_float.PREV"
        const val ACTION_NEXT              = "com.rasel.yt_float.NEXT"

        // ── নতুন: Mini Player Actions ─────────────────────────────────────────
        // YoutubeActivity back press এ LAUNCH_MINI দিয়ে launch হবে
        const val ACTION_LAUNCH_MINI       = "com.rasel.yt_float.LAUNCH_MINI"
        // Floating tap করলে এই broadcast যাবে → YoutubeActivity resume
        const val ACTION_RETURN_TO_ACTIVITY = "com.rasel.yt_float.RETURN_TO_ACTIVITY"
        const val ACTION_ACTIVITY_RESUMED   = "com.rasel.yt_float.ACTIVITY_RESUMED"

        const val EXTRA_URL       = "yt_url"
        const val EXTRA_TITLE     = "yt_title"
        const val EXTRA_NO_RELOAD = "yt_no_reload"

        /**
         * pendingWebView — MainActivity/YoutubeActivity থেকে set করা হয়।
         * Service এর onStartCommand এ consume করা হয়।
         *
         * ★ Mini Player Return:
         * onDestroy এ (mini player mode এ) WebView destroy করা হয় না।
         * পরিবর্তে pendingWebView এ রেখে দেওয়া হয়।
         * YoutubeActivity.onResume() এটা নিয়ে re-attach করে।
         */
        @Volatile
        var pendingWebView: WebView? = null

        // ── Memory trim support (Itel/low-RAM device fix) ──────────────────
        @Volatile
        private var activeInstance: YoutubeFloatingWindowService? = null

        /**
         * Application.onTrimMemory() থেকে কল হয় যখন system memory pressure এ থাকে।
         * শুধু minimized/mini-player mode এ থাকলে WebView cache হালকা করে —
         * full floating window active থাকলে touch করা হয় না (user দেখছে)।
         */
        fun trimMemoryIfBackground() {
            val instance = activeInstance ?: return
            if (instance.isMinimized || instance.isMiniPlayer) {
                try {
                    instance.webView?.clearCache(false)
                    instance.webView?.freeMemory()
                } catch (_: Exception) { }
            }
        }

        private const val NOTIF_ID   = 7777
        private const val CHANNEL_ID = "yt_float_channel"
        private const val PREFS_NAME = "yt_float_recovery"
        private const val KEY_URL    = "last_url"
        private const val KEY_TITLE  = "last_title"
        private const val KEY_WAS_OPEN = "was_open"

        private const val YT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        fun launch(context: Context, url: String, title: String) {
            val i = Intent(context, YoutubeFloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL,   url)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }

        /**
         * launchNoReload — Home button থেকে (Full Floating Mode)।
         * pendingWebView set করে তারপর call করো।
         */
        fun launchNoReload(context: Context, url: String, title: String) {
            val i = Intent(context, YoutubeFloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL,       url)
                putExtra(EXTRA_TITLE,     title)
                putExtra(EXTRA_NO_RELOAD, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }

        /**
         * launchMiniPlayer — Back button থেকে (Mini Player Mode)।
         * pendingWebView set করে তারপর call করো।
         * Native YouTube corner mini player এর মতো।
         */
        fun launchMiniPlayer(context: Context, url: String, title: String) {
            val i = Intent(context, YoutubeFloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH_MINI
                putExtra(EXTRA_URL,       url)
                putExtra(EXTRA_TITLE,     title)
                putExtra(EXTRA_NO_RELOAD, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }

        fun dismiss(context: Context) {
            context.startService(Intent(context, YoutubeFloatingWindowService::class.java)
                .apply { action = ACTION_DISMISS })
        }

        fun minimize(context: Context) {
            context.startService(Intent(context, YoutubeFloatingWindowService::class.java)
                .apply { action = ACTION_MINIMIZE })
        }

        fun restore(context: Context) {
            context.startService(Intent(context, YoutubeFloatingWindowService::class.java)
                .apply { action = ACTION_RESTORE })
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private var fullWindow:    View? = null
    private var bubbleView:    View? = null
    private var miniWindow:    View? = null   // ★ নতুন: mini player window
    private var webView:       WebView? = null

    // ★ নতুন: Mini Player mode flag
    // true  → back press থেকে এসেছে, corner mini player
    // false → home press থেকে এসেছে, full floating
    private var isMiniPlayer = false

    // FIX 1: Ghost container
    private var ghostContainer: android.widget.FrameLayout? = null
    private var ghostParams: WindowManager.LayoutParams? = null

    private var winW = 0
    private var winH = 0
    private var posX = 40
    private var posY = 120

    // ★ Mini player default position — bottom-right corner (native YouTube এর মতো)
    private var miniPosX = 0
    private var miniPosY = 0

    private var isMinimized = false
    private var currentUrl   = "https://m.youtube.com"
    private var currentTitle = "YouTube"

    private var titleTvRef: android.widget.TextView? = null

    // Resize tracking
    private var resizeStartW    = 0
    private var resizeStartH    = 0
    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f

    private var lastResizeMs    = 0L
    private val RESIZE_THROTTLE = 32L

    private var mediaSession: MediaSession? = null
    private var isPlaying = true
    private var isIntentionalClose = false

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val dm = resources.displayMetrics
        winW = (dm.widthPixels  * 0.92f).toInt()
        winH = (dm.heightPixels * 0.58f).toInt()
        posX = (dm.widthPixels  - winW) / 2
        posY = (dm.heightPixels * 0.12f).toInt()

        // ★ Mini player default position: bottom-right corner (YouTube native এর মতো)
        val miniW = dp(240)
        val miniH = dp(135)  // 16:9 ratio
        miniPosX = dm.widthPixels  - miniW - dp(12)
        miniPosY = dm.heightPixels - miniH - dp(180)

        setupMediaSession()
        setupGhostContainer()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "YtFloat").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    webView?.evaluateJavascript(JS_PLAY, null)
                    updateMediaState()
                    updateNotification(currentTitle)
                }
                override fun onPause() {
                    isPlaying = false
                    webView?.evaluateJavascript(JS_PAUSE, null)
                    updateMediaState()
                    updateNotification(currentTitle)
                }
                override fun onSkipToNext() {
                    webView?.evaluateJavascript(JS_NEXT, null)
                }
                override fun onSkipToPrevious() {
                    webView?.evaluateJavascript(JS_PREV, null)
                }
                override fun onSeekTo(pos: Long) {
                    val seconds = pos / 1000
                    webView?.evaluateJavascript(
                        "try { document.querySelector('video').currentTime = $seconds; } catch(e) {}",
                        null
                    )
                }
            })
            setActive(true)
        }
        updateMediaState()
    }

    private fun syncPlaybackState() {
        webView?.evaluateJavascript("""
            (function() {
                var v = document.querySelector('video');
                if (!v) return 'unknown';
                return v.paused ? 'paused' : 'playing';
            })();
        """.trimIndent()) { result ->
            val playing = result?.trim('"') == "playing"
            if (isPlaying != playing) {
                isPlaying = playing
                updateMediaState()
                updateNotification(currentTitle)
            }
        }
    }

    private fun updateMediaState() {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING
                    else           PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO
            )
            .build()
        mediaSession?.setPlaybackState(playbackState)

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE,  currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "YouTube")
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private val JS_PLAY  = "try { document.querySelector('video')?.play(); } catch(e) {}"
    private val JS_PAUSE = "try { document.querySelector('video')?.pause(); } catch(e) {}"
    private val JS_NEXT  = """
        try {
            var btn = document.querySelector('.ytp-next-button') ||
                      document.querySelector('[aria-label*="next" i]') ||
                      document.querySelector('[aria-label*="Next" i]');
            if (btn) btn.click();
            else { window.history.forward(); }
        } catch(e) {}
    """.trimIndent()
    private val JS_PREV  = """
        try {
            var v = document.querySelector('video');
            if (v && v.currentTime > 3) {
                v.currentTime = 0;
            } else {
                var btn = document.querySelector('.ytp-prev-button') ||
                          document.querySelector('[aria-label*="previous" i]') ||
                          document.querySelector('[aria-label*="Previous" i]');
                if (btn) btn.click();
                else { window.history.back(); }
            }
        } catch(e) {}
    """.trimIndent()

    private fun setupGhostContainer() {
        ghostContainer = android.widget.FrameLayout(this)
        val dm = resources.displayMetrics
        ghostParams = WindowManager.LayoutParams(
            dm.widthPixels,
            dm.heightPixels,
            0, 0,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha   = 0f
        }
        try {
            windowManager.addView(ghostContainer, ghostParams)
        } catch (e: Exception) { /* permission নেই */ }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(currentTitle))

        when (intent?.action) {

            ACTION_DISMISS -> {
                tearDown()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_MINIMIZE -> {
                if (!isMinimized && !isMiniPlayer) showMinimized()
            }

            ACTION_RESTORE -> {
                if (isMinimized) showFull()
            }

            ACTION_PLAY_PAUSE -> {
                if (isPlaying) {
                    isPlaying = false
                    webView?.evaluateJavascript(JS_PAUSE, null)
                } else {
                    isPlaying = true
                    webView?.evaluateJavascript(JS_PLAY, null)
                }
                updateMediaState()
                updateNotification(currentTitle)
            }

            ACTION_PREV -> webView?.evaluateJavascript(JS_PREV, null)
            ACTION_NEXT -> webView?.evaluateJavascript(JS_NEXT, null)

            // ══════════════════════════════════════════════════════════════════
            // ★ নতুন: ACTION_RETURN_TO_ACTIVITY
            // Mini player tap করলে YoutubeActivity কে foreground এ আনো।
            // Activity resume হলে stopService() call করবে → onDestroy() এ
            // WebView pendingWebView এ রেখে দেওয়া হবে।
            // ══════════════════════════════════════════════════════════════════
            ACTION_RETURN_TO_ACTIVITY -> {
                returnToActivity()
            }

            // ══════════════════════════════════════════════════════════════════
            // ★ Recent apps থেকে ফিরলে MainActivity.onResume() এই action পাঠাবে।
            // Mini bubble/player যেমন আছে তেমনই থাকবে (user experience অপরিবর্তিত)।
            // শুধু window layout params refresh করা হয় যাতে নতুন Activity window
            // এর সাথে stale z-order থেকে race না হয় (black screen এর কারণ)।
            // ══════════════════════════════════════════════════════════════════
            ACTION_ACTIVITY_RESUMED -> {
                try {
                    val container = ghostContainer
                    val params = ghostParams
                    if (container != null && params != null && container.isAttachedToWindow) {
                        windowManager.updateViewLayout(container, params)
                    }
                } catch (_: Exception) { }
            }

            // ══════════════════════════════════════════════════════════════════
            // ★ নতুন: ACTION_LAUNCH_MINI — Back press থেকে Corner Mini Player
            // ══════════════════════════════════════════════════════════════════
            ACTION_LAUNCH_MINI -> {
                val newUrl   = intent.getStringExtra(EXTRA_URL)   ?: "https://m.youtube.com"
                val newTitle = intent.getStringExtra(EXTRA_TITLE) ?: "YouTube"

                isMiniPlayer = true
                currentUrl   = newUrl
                currentTitle = newTitle

                // আগের যেকোনো window সরাও
                removeFull()
                removeBubble()
                removeMiniWindow()

                // WebView সেটআপ (pendingWebView থেকে নেবে — reload নেই)
                buildMiniPlayerWindow()
            }

            // Full Floating Launch (আগের behavior — Home press)
            ACTION_LAUNCH -> {
                val newUrl    = intent.getStringExtra(EXTRA_URL)   ?: "https://m.youtube.com"
                val newTitle  = intent.getStringExtra(EXTRA_TITLE) ?: "YouTube"
                val noReload  = intent.getBooleanExtra(EXTRA_NO_RELOAD, false)

                isMiniPlayer = false  // Full floating mode

                if (webView != null && (fullWindow != null || isMinimized)) {
                    currentUrl   = newUrl
                    currentTitle = newTitle
                    if (isMinimized) {
                        removeBubble()
                        isMinimized = false
                        if (!noReload) webView?.loadUrl(normalizeYoutubeUrl(newUrl))
                        showMinimized()
                    } else {
                        if (!noReload) webView?.loadUrl(normalizeYoutubeUrl(newUrl))
                        updateNotification(newTitle)
                    }
                } else {
                    currentUrl   = newUrl
                    currentTitle = newTitle
                    removeFull()
                    removeBubble()
                    removeMiniWindow()
                    showMinimized()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // ══════════════════════════════════════════════════════════════════════
        // ★ FIX: WebView destroy করো না — সবসময় pendingWebView এ রাখো।
        // Mini Player (back press) বা Lock → Floating দুটোতেই
        // YoutubeActivity.onResume() WebView নিয়ে re-attach করবে।
        // শুধু tearDown() (✕ close button) এ WebView destroy হয়।
        // ══════════════════════════════════════════════════════════════════════
        val wv = webView
        if (wv != null) {
            // Ghost/floating থেকে detach করো
            (wv.parent as? ViewGroup)?.removeView(wv)
            // pendingWebView এ রেখে দাও — YoutubeActivity নেবে
            pendingWebView = wv
            webView = null
        }

        removeFull()
        removeBubble()
        removeMiniWindow()

        ghostContainer?.let { runCatching { windowManager.removeView(it) } }
        ghostContainer = null

        mediaSession?.setActive(false)
        mediaSession?.release()
        mediaSession = null

        if (activeInstance === this) activeInstance = null

        // ══════════════════════════════════════════════════════════════════
        // ★ Low-RAM device fix: process পুরো kill হয়ে গেলে (Itel Android 10,
        // ~2-3GB RAM এ multiple WebView চললে এটা হতে পারে), recent apps
        // থেকে ফিরলে MainActivity.onCreate() cold-start হবে — তখন এই
        // service ও নতুন করে তৈরি হবে কোনো state ছাড়া। তাই এখানে current
        // URL persist করে রাখছি যাতে পরের launch এ সেই একই page এ ফেরা যায়।
        //
        // ✕ button দিয়ে ইচ্ছাকৃতভাবে বন্ধ করলে (isIntentionalClose) save
        // করা হয় না — তখন user নিজেই বন্ধ করেছে, পরের বার আবার সেই video
        // এ ফিরে যাওয়া উচিত না।
        //
        // সীমাবদ্ধতা: Android hard kill (extreme memory pressure) এ
        // onDestroy() নাও call হতে পারে — সেক্ষেত্রে এই save ঘটবে না।
        // এই fix সব process-kill case কভার করে না, তবে normal
        // background-swipe-kill case গুলোতে কাজ করবে।
        // ══════════════════════════════════════════════════════════════════
        if (!isIntentionalClose) {
            try {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_URL, currentUrl)
                    .putString(KEY_TITLE, currentTitle)
                    .putBoolean(KEY_WAS_OPEN, true)
                    .apply()
            } catch (_: Exception) { }
        }

        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ★ নতুন: Mini Player Window Builder
    // Native YouTube corner mini player এর মতো ছোট floating video window।
    // ══════════════════════════════════════════════════════════════════════════
    @SuppressLint("ClickableViewAccessibility")
    private fun buildMiniPlayerWindow() {
        val dm      = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Mini player size: 240×135 dp (16:9 ratio, corner তে বসার মতো)
        val miniW = dp(240)
        val miniH = dp(135)

        val params = WindowManager.LayoutParams(
            miniW, miniH,
            miniPosX, miniPosY,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        // ── Root: rounded card ────────────────────────────────────────────────
        val root = android.widget.FrameLayout(this).apply {
            background    = buildRoundedBg()
            clipToOutline = true
            elevation     = dp(8).toFloat()
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                }
            }
        }

        // ── WebView (video area) ──────────────────────────────────────────────
        val wv = getOrBuildWebView()
        webView = wv
        (wv.parent as? ViewGroup)?.removeView(wv)
        root.addView(wv, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // ── Overlay controls (video এর উপরে) ────────────────────────────────
        val overlay = android.widget.FrameLayout(this)

        // Close button — top-right
        val btnClose = android.widget.TextView(this).apply {
            text     = "✕"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = buildCircleBg(0xAA000000.toInt())
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, dp(6), dp(6), 0) }
            setOnClickListener {
                // WebView destroy করো (user explicitly close করেছে)
                webView?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                    it.destroy()
                    webView = null
                }
                pendingWebView = null
                stopSelf()
            }
        }

        // Tap anywhere on video → YoutubeActivity resume
        // (Close button click আলাদা — এটা শুধু ✕ এর বাইরে tap)
        val tapArea = android.view.View(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // tap → activity resume, drag → position change
        }

        overlay.addView(tapArea)
        overlay.addView(btnClose)
        root.addView(overlay, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // ── Touch: drag to move + tap to return to activity ──────────────────
        var startRawX = 0f
        var startRawY = 0f
        var startParamX = miniPosX
        var startParamY = miniPosY
        var hasMoved    = false

        root.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX   = ev.rawX
                    startRawY   = ev.rawY
                    startParamX = params.x
                    startParamY = params.y
                    hasMoved    = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - startRawX).toInt()
                    val dy = (ev.rawY - startRawY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        hasMoved    = true
                        params.x    = (startParamX + dx).coerceIn(0, screenW - miniW)
                        params.y    = (startParamY + dy).coerceIn(0, screenH - miniH - dp(40))
                        miniPosX    = params.x
                        miniPosY    = params.y
                        runCatching { windowManager.updateViewLayout(miniWindow, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // Tap → Activity resume
                        returnToActivity()
                    }
                    true
                }
                else -> false
            }
        }

        miniWindow = root

        try {
            windowManager.addView(root, params)
        } catch (e: Exception) {
            // Overlay permission নেই — teardown
            tearDown()
            stopSelf()
        }

        updateNotification("▶ $currentTitle")
    }

    /**
     * ★ নতুন: YoutubeActivity কে foreground এ ফিরিয়ে আনো।
     * Mini player tap করলে call হয়।
     * Activity.onResume() → stopService() → onDestroy() → pendingWebView set।
     */
    private fun returnToActivity() {
        // YoutubeActivity কে FLAG_ACTIVITY_REORDER_TO_FRONT দিয়ে resume করো
        val resumeIntent = Intent(
            this,
            com.rasel.RasFocus.selfcontrol.familybrowser.YoutubeActivity::class.java
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            // YoutubeActivity.onResume() এ mini player mode detect করার জন্য
            putExtra("from_mini_player", true)
        }
        try {
            startActivity(resumeIntent)
        } catch (e: Exception) {
            // Activity না পেলে শুধু service বন্ধ করো
            stopSelf()
        }
        // Note: service এখনই বন্ধ করো না।
        // YoutubeActivity.onResume() → stopService() call করবে।
        // stopService() → onDestroy() → pendingWebView এ WebView রাখবে।
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Full Floating Window (আগের behavior — Home press)
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildFullWindow() {
        isMinimized = false
        val dm      = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val overlayType = overlayWindowType()

        val params = WindowManager.LayoutParams(
            winW, winH, posX, posY,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val root = android.widget.LinearLayout(this).apply {
            orientation   = android.widget.LinearLayout.VERTICAL
            background    = buildRoundedBg()
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }
            isFocusableInTouchMode = true
            isFocusable            = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    val wv = webView
                    if (wv != null && wv.canGoBack()) wv.goBack()
                    else showMinimized()
                    true
                } else false
            }
        }

        val titleBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF0F0F0F.toInt())
            gravity     = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
        }

        val ytLogo = android.widget.TextView(this).apply {
            text     = "▶ YouTube"
            textSize = 13f
            setTextColor(0xFFFF0000.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(6), 0)
        }

        val titleTv = android.widget.TextView(this).apply {
            text      = currentTitle.take(28)
            textSize  = 12f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleTvRef = titleTv

        val btnMinimize = buildIconBtn("▬", 0xFFAAAAAA.toInt()) { showMinimized() }

        val btnSize = buildIconBtn("⊡", 0xFFAAAAAA.toInt()) {
            val newW: Int; val newH: Int
            if (winW < screenW * 0.87f) {
                newW = (screenW * 0.96f).toInt()
                newH = (screenH * 0.72f).toInt()
            } else {
                newW = (screenW * 0.92f).toInt()
                newH = (screenH * 0.58f).toInt()
            }
            winW = newW; winH = newH
            params.width = winW; params.height = winH
            clampPosition(screenW, screenH, params)
            windowManager.updateViewLayout(fullWindow, params)
        }

        // "Open in App" — floating বন্ধ করে main app এ ঐ page চালু করে
        val btnOpenApp = buildIconBtn("⤤", 0xFF4CAF50.toInt()) {
            val i = Intent(this@YoutubeFloatingWindowService, FamilyBrowserActivity::class.java).apply {
                data  = android.net.Uri.parse(currentUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(i)
            tearDown()
            stopSelf()
        }

        val btnClose = buildIconBtn("✕", 0xFFFF5555.toInt()) { tearDown(); stopSelf() }

        titleBar.addView(ytLogo)
        titleBar.addView(titleTv)
        titleBar.addView(btnMinimize)
        titleBar.addView(btnOpenApp)
        titleBar.addView(btnSize)
        titleBar.addView(btnClose)

        attachDragListener(titleBar, params, screenW, screenH) {
            windowManager.updateViewLayout(fullWindow, params)
        }

        val wv = getOrBuildWebView()
        webView = wv
        (wv.parent as? ViewGroup)?.removeView(wv)

        val resizeHandle = android.widget.TextView(this).apply {
            text      = "⠿"
            textSize  = 18f
            setTextColor(0xFF555555.toInt())
            setPadding(dp(4), dp(2), dp(10), dp(6))
            gravity   = Gravity.END
            setBackgroundColor(0xFF0F0F0F.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        attachResizeListener(resizeHandle, params, screenW, screenH)

        root.addView(titleBar)
        root.addView(wv, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(resizeHandle)

        fullWindow = root
        windowManager.addView(root, params)
        updateNotification(currentTitle)
    }

    // ── Minimized bubble ───────────────────────────────────────────────────────

    private fun showMinimized() {
        if (isMiniPlayer) return  // Mini player এ minimize নেই
        isMinimized = true

        val wv = webView
        if (wv != null && ghostContainer != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            ghostContainer?.addView(wv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
            injectVisibilitySpoof(wv)
        }

        removeFull()

        val dm    = resources.displayMetrics
        val bSize = dp(60)

        val bParams = WindowManager.LayoutParams(
            bSize, bSize,
            dm.widthPixels - bSize - dp(12),
            dm.heightPixels - bSize - dp(180),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val bubble = android.widget.FrameLayout(this).apply {
            background    = buildCircleBg(0xFFFF0000.toInt())
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            elevation = dp(8).toFloat()
        }

        val icon = android.widget.TextView(this).apply {
            text     = "▶"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity  = Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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
                        windowManager.updateViewLayout(bubbleView, bParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showFull()
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        windowManager.addView(bubble, bParams)
        updateNotification("▶ Minimized — tap to restore")
    }

    private fun showFull() {
        removeBubble()
        webView?.evaluateJavascript("""
            (function() {
                try {
                    if (!window.__rasOrigResizeObserver) {
                        window.__rasOrigResizeObserver = window.ResizeObserver;
                    }
                    window.ResizeObserver = function(cb) {
                        var obs = new window.__rasOrigResizeObserver(cb);
                        obs.__rasSuppressed = true;
                        return obs;
                    };
                    setTimeout(function() {
                        if (window.__rasOrigResizeObserver) {
                            window.ResizeObserver = window.__rasOrigResizeObserver;
                        }
                    }, 500);
                } catch(e) {}
            })();
        """.trimIndent(), null)
        buildFullWindow()
    }

    private fun getOrBuildWebView(): WebView {
        val existing = webView
        if (existing != null) return existing

        val pending = pendingWebView
        if (pending != null) {
            pendingWebView = null
            (pending.parent as? ViewGroup)?.removeView(pending)
            injectVisibilitySpoof(pending)
            return pending
        }

        return buildYoutubeWebView().also {
            it.loadUrl(normalizeYoutubeUrl(currentUrl))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildYoutubeWebView(): WebView {
        return WebView(this).apply {
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
                setSupportZoom(true)
                cacheMode                        = WebSettings.LOAD_DEFAULT
                mixedContentMode                 = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString                  = YT_USER_AGENT
            }

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // FIX: LAYER_TYPE_HARDWARE video render-কে black করে দিচ্ছিল
            // (audio চলে, screen black) — floating window-এ HTML5 video
            // surface texture-এর সাথে conflict করে। LAYER_TYPE_NONE ব্যবহার
            // করলে WebView নিজে সঠিক compositing বেছে নেয়।
            setLayerType(android.view.View.LAYER_TYPE_NONE, null)

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val reqUrl = request.url.toString()
                    // FIX: this only ever checked the DOMAIN (isAdultSite) —
                    // so a keyword typed into YouTube's own search box (still
                    // under the youtube.com domain, not an "adult site" by
                    // domain) never triggered anything, unlike the full-page
                    // browser which also checks the URL text for adult
                    // keywords via FirebaseKeywordSync. Added that same check
                    // here, and upgraded the response from blank HTML to a
                    // real blocked page.
                    val isDomainBlocked  = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(reqUrl)
                    val isKeywordBlocked = com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync.containsAdultKeyword(reqUrl)
                    if (isDomainBlocked || isKeywordBlocked) {
                        val blockedHtml = com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker
                            .buildBlockedPage(reqUrl, com.rasel.RasFocus.selfcontrol.familybrowser.BlockReason.ADULT)
                        return android.webkit.WebResourceResponse(
                            "text/html", "UTF-8",
                            blockedHtml.byteInputStream()
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    val t = view.title?.takeIf { it.isNotEmpty() } ?: url
                    titleTvRef?.text = t.take(28)
                    currentTitle = t

                    updateMediaState()
                    updateNotification(t)

                    injectVisibilitySpoof(view)
                    injectYoutubeHacks(view, url)
                    injectRemoveOpenInAppButton(view)

                    if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        view.evaluateJavascript(
                            com.rasel.RasFocus.selfcontrol.familybrowser.YouTubeAdPruner
                                .getJsInjectScript(),
                            null
                        )
                    }

                    view.postDelayed({ syncPlaybackState() }, 1000L)
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

                    val safeUrl = com.rasel.RasFocus.selfcontrol.familybrowser.SafeSearchEnforcer
                        .enforceIfNeeded(url)
                    if (safeUrl != null) {
                        view.loadUrl(safeUrl)
                        return true
                    }

                    if (!url.startsWith("http://") && !url.startsWith("https://")) return true
                    currentUrl = url
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {}
                override fun onReceivedTitle(view: WebView, title: String) {
                    titleTvRef?.text = title.take(28)
                    currentTitle     = title
                    updateNotification(title)
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    (fullWindow as? ViewGroup)?.addView(view, 1)
                }
                override fun onHideCustomView() {}
            }
        }
    }

    // ── JS Injection helpers ───────────────────────────────────────────────────

    private fun injectVisibilitySpoof(view: WebView) {
        view.evaluateJavascript("""
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
                    var _add = document.addEventListener.bind(document);
                    document.addEventListener = function(type, fn, opts) {
                        if (type === 'visibilitychange' || type === 'webkitvisibilitychange') return;
                        _add(type, fn, opts);
                    };
                    window.addEventListener = (function(original) {
                        return function(type, fn, opts) {
                            if (type === 'pagehide') return;
                            original.call(this, type, fn, opts);
                        };
                    })(window.addEventListener);
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        try {
                            if (videos[i].paused) videos[i].play().catch(function(){});
                        } catch(e) {}
                    }
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun injectYoutubeHacks(view: WebView, url: String) {
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) return
        view.evaluateJavascript("""
            (function() {
                if (window.__rasBgAudioInjected__) return;
                window.__rasBgAudioInjected__ = true;
                try {
                    Object.defineProperty(document, 'hidden',
                        { get: function(){ return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState',
                        { get: function(){ return 'visible'; }, configurable: true });
                    Object.defineProperty(document, 'webkitHidden',
                        { get: function(){ return false; }, configurable: true });
                    Object.defineProperty(document, 'webkitVisibilityState',
                        { get: function(){ return 'visible'; }, configurable: true });

                    var _origAdd = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, fn, opts) {
                        if (type === 'visibilitychange'       ||
                            type === 'webkitvisibilitychange' ||
                            type === 'pagehide'               ||
                            type === 'blur') return;
                        return _origAdd.call(this, type, fn, opts);
                    };

                    var _desc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'pause');
                    if (_desc && _desc.value) {
                        HTMLMediaElement.prototype.pause = function() { return; };
                    }

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

                    if ('mediaSession' in navigator) {
                        navigator.mediaSession.setActionHandler('play', function() {
                            try { var v = document.querySelector('video'); if (v) v.play(); } catch(e) {}
                        });
                        navigator.mediaSession.setActionHandler('pause', function() {});
                        navigator.mediaSession.setActionHandler('stop', null);
                    }

                    try {
                        var yt = window.yt || {};
                        if (yt.player && yt.player.Application) {
                            var app = yt.player.Application.create__(null, null);
                            if (app) { try { app.unmuteBackgroundAudio_(); } catch(e) {} }
                        }
                    } catch(e) {}

                    try {
                        document.querySelectorAll('video').forEach(function(v) {
                            try { if (v.paused) v.play().catch(function(){}); } catch(e) {}
                        });
                    } catch(e) {}

                    var _origDispatch = document.dispatchEvent.bind(document);
                    document.dispatchEvent = function(event) {
                        if (event && event.type &&
                            (event.type === 'visibilitychange' ||
                             event.type === 'webkitvisibilitychange')) return true;
                        return _origDispatch(event);
                    };
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun injectRemoveOpenInAppButton(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__rasOpenAppRemoverReady__) return;
                window.__rasOpenAppRemoverReady__ = true;
                function removeOpenAppElements() {
                    var selectors = [
                        '#app-related-ytcutter', '.ytm-action-button', 'ytm-action-button',
                        '[class*="open-in-app"]', '[class*="openInApp"]', '[class*="open_in_app"]',
                        '#external-app-banner', '.external-app-banner',
                        'ytm-app-related-endscreen-renderer', '[data-layer="5"]',
                        '.app-badge-container', '.ytp-app-related',
                        'meta[name="apple-itunes-app"]', 'link[rel="alternate"][media]'
                    ];
                    selectors.forEach(function(sel) {
                        try { document.querySelectorAll(sel).forEach(function(el) { el.remove(); }); } catch(e) {}
                    });
                    try {
                        document.querySelectorAll('button, a, .yt-spec-button-shape-next, [role="button"]')
                            .forEach(function(el) {
                                var txt = (el.innerText || el.textContent || '').toLowerCase();
                                if (txt.includes('open app')     || txt.includes('open in app') ||
                                    txt.includes('watch in app') || txt.includes('use the app') ||
                                    txt.includes('get the app')  || txt.includes('open youtube')) {
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
                    }).observe(document.body || document.documentElement, { childList: true, subtree: true });
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    // ── Touch helpers ──────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(
        handle: View,
        params: WindowManager.LayoutParams,
        screenW: Int,
        screenH: Int,
        onUpdate: () -> Unit
    ) {
        var startX = 0f; var startY = 0f
        var pX = 0;      var pY = 0
        var dragging = false

        handle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    startX   = ev.rawX; startY = ev.rawY
                    pX       = params.x; pY    = params.y
                    params.flags = params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachResizeListener(
        handle: View,
        params: WindowManager.LayoutParams,
        screenW: Int,
        screenH: Int
    ) {
        handle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartW    = params.width
                    resizeStartH    = params.height
                    resizeStartRawX = ev.rawX
                    resizeStartRawY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val now = System.currentTimeMillis()
                    if (now - lastResizeMs < RESIZE_THROTTLE) return@setOnTouchListener true
                    lastResizeMs = now
                    val dX = (ev.rawX - resizeStartRawX).toInt()
                    val dY = (ev.rawY - resizeStartRawY).toInt()
                    params.width  = max(dp(260), min(screenW, resizeStartW + dX))
                    params.height = max(dp(280), min(screenH - params.y - dp(40), resizeStartH + dY))
                    winW = params.width; winH = params.height
                    runCatching { windowManager.updateViewLayout(fullWindow, params) }
                    true
                }
                else -> false
            }
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    private fun normalizeYoutubeUrl(url: String): String {
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") ->
                url.replace("www.youtube.com", "m.youtube.com")
                   .replace("://youtube.com", "://m.youtube.com")
            url == "about:blank" || url.isEmpty() -> "https://m.youtube.com"
            else -> url
        }
    }

    private fun clampPosition(sw: Int, sh: Int, p: WindowManager.LayoutParams) {
        p.x = max(0, min(sw - p.width,  p.x))
        p.y = max(0, min(sh - p.height - dp(40), p.y))
        posX = p.x; posY = p.y
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayWindowType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun buildIconBtn(label: String, color: Int, onClick: () -> Unit) =
        android.widget.TextView(this).apply {
            text      = label
            textSize  = 16f
            setTextColor(color)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            gravity   = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    private fun buildRoundedBg(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(0xFF0F0F0F.toInt())
            setStroke(dp(1), 0xFF333333.toInt())
        }
    }

    private fun buildCircleBg(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun removeFull() {
        fullWindow?.let { runCatching { windowManager.removeView(it) } }
        fullWindow = null
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    private fun removeMiniWindow() {
        miniWindow?.let { runCatching { windowManager.removeView(it) } }
        miniWindow = null
    }

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
        removeFull()
        removeBubble()
        removeMiniWindow()
        runCatching { stopService(Intent(this, BackgroundAudioService::class.java)) }
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private fun updateNotification(title: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title))
    }

    private fun buildNotification(title: String): Notification {
        fun svcIntent(action: String, reqCode: Int) = PendingIntent.getService(
            this, reqCode,
            Intent(this, YoutubeFloatingWindowService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ★ Mini player mode: notification tap → Activity resume
        val contentIntent = if (isMiniPlayer) {
            PendingIntent.getService(
                this, 10,
                Intent(this, YoutubeFloatingWindowService::class.java).apply {
                    this.action = ACTION_RETURN_TO_ACTIVITY
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            svcIntent(ACTION_RESTORE, 1)
        }

        val stopIntent      = svcIntent(ACTION_DISMISS,    2)
        val minimizeIntent  = svcIntent(ACTION_MINIMIZE,   3)
        val playPauseIntent = svcIntent(ACTION_PLAY_PAUSE, 4)
        val prevIntent      = svcIntent(ACTION_PREV,       5)
        val nextIntent      = svcIntent(ACTION_NEXT,       6)

        val playPauseIcon  = if (isPlaying) android.R.drawable.ic_media_pause
                             else           android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        val session = mediaSession
        val style = android.app.Notification.MediaStyle()
            .setShowActionsInCompactView(1, 2, 3)
        if (session != null) style.setMediaSession(session.sessionToken)

        val notifTitle = if (isMiniPlayer) "YouTube — Mini Player" else "YouTube — Floating"
        val subText    = if (isMiniPlayer) "Tap to expand" else "Tap to restore"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return android.app.Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(notifTitle)
                .setContentText(title.take(60))
                .setSubText(subText)
                .setContentIntent(contentIntent)
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_menu_view, "Minimize", minimizeIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Previous", prevIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    playPauseIcon, playPauseLabel, playPauseIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Next", nextIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_delete, "Close", stopIntent).build())
                .setStyle(style)
                .setOngoing(true)
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
                .setCategory(android.app.Notification.CATEGORY_TRANSPORT)
                .build()
        } else {
            @Suppress("DEPRECATION")
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(notifTitle)
                .setContentText(title.take(60))
                .setSubText(subText)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_menu_view,       "Minimize",     minimizeIntent)
                .addAction(android.R.drawable.ic_media_previous,  "Previous",     prevIntent)
                .addAction(playPauseIcon,                          playPauseLabel, playPauseIntent)
                .addAction(android.R.drawable.ic_media_next,      "Next",         nextIntent)
                .addAction(android.R.drawable.ic_delete,          "Close",        stopIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "YouTube Floating Window",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description          = "YouTube floating player controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }
}