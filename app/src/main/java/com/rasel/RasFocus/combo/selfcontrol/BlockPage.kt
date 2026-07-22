// ============================================================
// BlockPage.kt
// Central Block UI — সব blocking service এখান থেকে UI দেখাবে
//
// Usage (যেকোনো AccessibilityService এর ভেতর থেকে):
//   BlockPage.show(service, BlockPage.Type.ADULT,    "Adult Content", "Porn site detected")
//   BlockPage.show(service, BlockPage.Type.WEBSITE,  "Website Blocked", "facebook.com")
//   BlockPage.show(service, BlockPage.Type.APP,      "App Blocked", "Instagram is blocked")
//   BlockPage.show(service, BlockPage.Type.REELS,    "Reels Blocked", "Facebook Reels")
//   BlockPage.show(service, BlockPage.Type.SHORTS,   "Shorts Blocked", "YouTube Shorts")
//   BlockPage.show(service, BlockPage.Type.FOCUS,    "Focus Mode", "Distraction blocked")
//   BlockPage.show(service, BlockPage.Type.PANIC,    "Panic Mode", "All browsers locked")
//   BlockPage.show(service, BlockPage.Type.SYSTEM,   "System Locked", "Settings blocked")
//   BlockPage.dismiss(service)
// ============================================================

package com.rasel.RasFocus.combo.selfcontrol

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.rasel.RasFocus.DataManager

object BlockPage {

    // ── Block Types ─────────────────────────────────────────────────────
    enum class Type {
        ADULT,    // পর্ন / adult content — লাল, ধর্মীয় quote, 5s delay
        WEBSITE,  // website blocklist — teal
        APP,      // app blocklist — নীল
        REELS,    // Facebook/Instagram Reels — বেগুনি
        SHORTS,   // YouTube Shorts — লাল-গোলাপী
        FOCUS,    // Deep Study / Focus mode — ইন্ডিগো
        PANIC,    // Panic mode — কমলা
        SYSTEM,   // Settings/Uninstall block — ধূসর
        REMINDER  // Periodic reminder — সবুজ
    }

    // ── State ────────────────────────────────────────────────────────────
    @Volatile private var isVisible = false
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Islamic quotes (adult block এ দেখাবে) ───────────────────────────
    private val islamicQuotesBn = listOf(
        Pair("আস্তাগফিরুল্লাহ! নিশ্চয়ই আল্লাহ সবকিছু দেখছেন।", "আল কুরআন"),
        Pair("মুমিনদেরকে বলুন, তারা যেন তাদের দৃষ্টি অবনত রাখে।", "সূরা আন-নূর: ৩০"),
        Pair("যিনা এর ধারে কাছেও যেও না, নিশ্চয়ই এটা অশ্লীল কাজ।", "সূরা বনী ইসরাঈল: ৩২"),
        Pair("দৃষ্টির হেফাজত করা ঈমানের পূর্ণতার অন্যতম প্রধান লক্ষণ।", "আল হাদিস"),
        Pair("ক্ষণস্থায়ী পাপের জন্য চিরস্থায়ী জান্নাত হারিও না।", "RasFocus+"),
        Pair("যে ব্যক্তি নিজের প্রবৃত্তিকে নিয়ন্ত্রণ করে, জান্নাতই তার ঠিকানা।", "আল কুরআন")
    )
    private val motivationalQuotesBn = listOf(
        Pair("সময়ের মূল্য বোঝো, জীবন তোমার মূল্য বুঝবে।", "RasFocus+"),
        Pair("নিজেকে সম্মান করো — তোমার মস্তিষ্ক এর চেয়ে ভালো কিছুর যোগ্য।", "RasFocus+"),
        Pair("যে নিজেকে নিয়ন্ত্রণ করতে পারে, সে সবকিছু জয় করতে পারে।", "এপিকটেটাস"),
        Pair("মনোযোগ হলো নতুন যুগের মুদ্রা — তা সাবধানে ব্যয় করো।", "ক্যাল নিউপোর্ট"),
        Pair("ছোট ছোট অভ্যাসই বড় পরিবর্তন আনে।", "জেমস ক্লিয়ার"),
        Pair("একটা বিক্ষিপ্ত মন কখনো মহৎ কাজ করতে পারে না।", "রবীন্দ্রনাথ ঠাকুর")
    )

    // ── Type → Visual config ─────────────────────────────────────────────
    private data class VisualConfig(
        val headerTopColor: Int,
        val headerBotColor: Int,
        val accentColor: Int,
        val icon: String,
        val autoDismissMs: Long,     // 0 = no auto dismiss (adult)
        val adultMode: Boolean = false
    )

    private fun configFor(type: Type, ctx: Context): VisualConfig = when (type) {
        Type.ADULT   -> VisualConfig(Color.parseColor("#000000"), Color.parseColor("#000000"), Color.parseColor("#FF0000"), "🚫",    0L, true)
        Type.WEBSITE -> VisualConfig(Color.parseColor("#00695C"), Color.parseColor("#00BFA5"), Color.parseColor("#00BFA5"), "🌐", 4000L)
        Type.APP     -> VisualConfig(Color.parseColor("#1565C0"), Color.parseColor("#1E88E5"), Color.parseColor("#42A5F5"), "📵", 4000L)
        Type.REELS   -> VisualConfig(Color.parseColor("#6A1B9A"), Color.parseColor("#AB47BC"), Color.parseColor("#CE93D8"), "🎬", 4000L)
        Type.SHORTS  -> VisualConfig(Color.parseColor("#AD1457"), Color.parseColor("#E91E63"), Color.parseColor("#F48FB1"), "📱", 4000L)
        Type.FOCUS   -> VisualConfig(Color.parseColor("#283593"), Color.parseColor("#3F51B5"), Color.parseColor("#7986CB"), "📚", 4000L)
        Type.PANIC   -> VisualConfig(Color.parseColor("#E65100"), Color.parseColor("#FF6D00"), Color.parseColor("#FFAB40"), "🚨", 4000L)
        Type.SYSTEM  -> VisualConfig(Color.parseColor("#37474F"), Color.parseColor("#546E7A"), Color.parseColor("#90A4AE"), "🔒", 4000L)
        Type.REMINDER-> VisualConfig(Color.parseColor("#1B5E20"), Color.parseColor("#2E7D32"), Color.parseColor("#66BB6A"), "💡", 4000L)
    }

    // ── Main API: show ───────────────────────────────────────────────────
    // ✅ FIX: আগে এখানে mainHandler.post{} দিয়ে wrap করা হতো, কিন্তু এই
    // ফাংশন সবসময় AccessibilityService এর onAccessibilityEvent chain থেকে
    // call হয় — যেটা ইতিমধ্যেই main thread এ চলে। অপ্রয়োজনীয় Handler
    // round-trip প্রতিটা extra মিলিসেকেন্ড latency যোগ করছিল, যার ফলে
    // browser এর নিজের page (Google homepage) block overlay এর আগেই
    // ভিজিবল হয়ে যাচ্ছিল। এখন already main thread এ থাকলে সরাসরি call
    // করি — কোনো future caller ভুলে অন্য thread থেকে call করলে (অসম্ভাব্য
    // কিন্তু safety এর জন্য) তখনই Handler ব্যবহার হবে।
    fun show(
        service: AccessibilityService,
        type: Type,
        title: String,
        reason: String
    ) {
        if (isVisible) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (isVisible) return
            isVisible = true
            buildAndShow(service, type, title, reason)
        } else {
            mainHandler.post {
                if (isVisible) return@post
                isVisible = true
                buildAndShow(service, type, title, reason)
            }
        }
    }

    // ── dismiss (from outside) ───────────────────────────────────────────
    fun dismiss(service: AccessibilityService) {
        mainHandler.post { removeOverlay() }
    }

    // ── Internal builder ─────────────────────────────────────────────────
    private fun buildAndShow(
        service: AccessibilityService,
        type: Type,
        title: String,
        reason: String
    ) {
        val ctx: Context = service
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val dp = ctx.resources.displayMetrics.density
        val sh = ctx.resources.displayMetrics.heightPixels
        val cfg = configFor(type, ctx)

        // ── Root — ✅ FIX: root এখনই তৈরি ও attach করব, খালি কিন্তু solid
        // রঙে (আগে পুরো UI — icon, quote, progress bar, ~১৫টা nested view —
        // সম্পূর্ণ তৈরি হওয়ার পরে attach হতো, ততক্ষণে browser এর নিজের
        // page (Google homepage) already ভিজিবল হয়ে যেত)। এখন screen
        // সাথে সাথে ঢেকে যাবে; বাকি সব content পরে এই already-attached
        // root এ যোগ হবে — ততক্ষণে underlying page আর দেখা যাবে না।
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(if (cfg.adultMode) Color.BLACK else Color.WHITE)
        }

        val wmType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            wmType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }

        overlayView = root
        try {
            wm.addView(root, params)
        } catch (e: Exception) {
            // root attach-ই ব্যর্থ — screen এ কিছুই নেই, তাই safely reset করা যায়
            isVisible = false
            overlayView = null
            return
        }

        // ── root এখন attach হয়ে গেছে, screen ইতিমধ্যে ঢাকা ──
        // এখন থেকে বাকি সব rich content (icon, quote, progress bar) নিরাপদে
        // বসাতে পারি — user আর কখনো underlying page দেখতে পাবে না, কারণ
        // solid color cover ইতিমধ্যে screen ঢেকে রেখেছে।
        try {

        // ── Quote pick ──────────────────────────────────────────────────
        val quotePair = if (cfg.adultMode)
            islamicQuotesBn.random()
        else
            motivationalQuotesBn.random()

        // ── Header (top 38% — gradient) ─────────────────────────────────
        val headerGrad = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(cfg.headerTopColor, cfg.headerBotColor)
        )
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = headerGrad
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (sh * 0.38f).toInt()
            ).apply { gravity = Gravity.TOP }
        }

        // Badge row (RasFocus+ badge + dismiss)
        val badgeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((20 * dp).toInt(), (48 * dp).toInt(), (20 * dp).toInt(), 0) }
        }
        val badgeBg = GradientDrawable().apply {
            setColor(Color.parseColor("#33FFFFFF")); cornerRadius = 100f * dp
        }
        val appBadge = TextView(ctx).apply {
            text = "🛡  RasFocus+"; textSize = 10f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f; background = badgeBg
            val hp = (12 * dp).toInt(); val vp = (5 * dp).toInt(); setPadding(hp, vp, hp, vp)
        }
        val spacer = Space(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val dismissBtn = TextView(ctx).apply {
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            val p = (10 * dp).toInt(); setPadding(p, p, p, p)
            if (cfg.adultMode) {
                isEnabled = false; text = "⏳ 5s"
                setTextColor(Color.parseColor("#AAFFFFFF"))
                var t = 5
                val tick = object : Runnable {
                    override fun run() {
                        t--
                        if (t > 0) { text = "⏳ ${t}s"; mainHandler.postDelayed(this, 1000) }
                        else { isEnabled = true; text = "✕ বন্ধ"; setTextColor(Color.WHITE)
                            setOnClickListener { removeOverlay() } }
                    }
                }
                mainHandler.postDelayed(tick, 1000)
            } else {
                text = "✕"; setTextColor(Color.parseColor("#CCFFFFFF"))
                setOnClickListener { removeOverlay() }
            }
        }
        badgeRow.addView(appBadge); badgeRow.addView(spacer); badgeRow.addView(dismissBtn)
        header.addView(badgeRow)

        // Icon circle
        val iconCircleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(Color.parseColor("#33FFFFFF"))
        }
        val iconTv = TextView(ctx).apply {
            text = cfg.icon; textSize = 36f; gravity = Gravity.CENTER; background = iconCircleBg
            val s = (90 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                setMargins(0, (20 * dp).toInt(), 0, (12 * dp).toInt())
            }
        }
        val blockedLabel = TextView(ctx).apply {
            text = "BLOCKED"; textSize = 11f; setTextColor(Color.parseColor("#CCFFFFFF"))
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.3f; gravity = Gravity.CENTER
        }
        val titleTv = TextView(ctx).apply {
            text = title; textSize = 22f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((24 * dp).toInt(), (4 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt()) }
        }
        header.addView(iconTv); header.addView(blockedLabel); header.addView(titleTv)

        // ── Body scroll ─────────────────────────────────────────────────
        val bodyScroll = ScrollView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = (sh * 0.38f).toInt() }
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * dp).toInt(); setPadding(p, (20 * dp).toInt(), p, (32 * dp).toInt())
        }

        // Reason chip
        val accentFaded = Color.argb(51, Color.red(cfg.accentColor), Color.green(cfg.accentColor), Color.blue(cfg.accentColor))
        val accentLight = Color.argb(30, Color.red(cfg.accentColor), Color.green(cfg.accentColor), Color.blue(cfg.accentColor))
        val reasonChipBg = GradientDrawable().apply {
            setColor(accentLight); cornerRadius = 12f * dp
            setStroke((1f * dp).toInt(), accentFaded)
        }
        val reasonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = reasonChipBg
            val p = (14 * dp).toInt(); val vp = (12 * dp).toInt(); setPadding(p, vp, p, vp)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val reasonIcon = TextView(ctx).apply {
            text = "ℹ"; textSize = 16f; setTextColor(cfg.accentColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, (10 * dp).toInt(), 0) }
        }
        val reasonTv = TextView(ctx).apply {
            text = reason; textSize = 13f; setTextColor(if (cfg.adultMode) Color.parseColor("#B0BEC5") else Color.parseColor("#4A6080")); setLineSpacing(0f, 1.4f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        reasonRow.addView(reasonIcon); reasonRow.addView(reasonTv); body.addView(reasonRow)

        // Divider
        body.addView(View(ctx).apply {
            setBackgroundColor(if (cfg.adultMode) Color.parseColor("#333333") else Color.parseColor("#E8EFF5"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1f * dp).toInt())
                .apply { setMargins(0, (20 * dp).toInt(), 0, (16 * dp).toInt()) }
        })

        // Quote section
        val quoteLabel = TextView(ctx).apply {
            text = if (cfg.adultMode) "☪  কুরআন ও হাদিস থেকে" else "💭  অনুপ্রেরণা"
            textSize = 11f; setTextColor(cfg.accentColor); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, (4 * dp).toInt(), 0, (10 * dp).toInt()) }
        }
        body.addView(quoteLabel)

        val quoteBg = GradientDrawable().apply {
            setColor(if (cfg.adultMode) Color.parseColor("#111111") else Color.parseColor("#F0FAFA")); cornerRadius = 14f * dp
            setStroke((1f * dp).toInt(), if (cfg.adultMode) Color.parseColor("#333333") else Color.parseColor("#CCE8E5"))
        }
        val quoteRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; background = quoteBg
            val p = (14 * dp).toInt(); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val bar = View(ctx).apply {
            background = GradientDrawable().apply { setColor(cfg.accentColor); cornerRadius = 3f * dp }
            layoutParams = LinearLayout.LayoutParams((3f * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { setMargins(0, 0, (12 * dp).toInt(), 0) }
        }
        val quoteCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val quoteText = TextView(ctx).apply {
            text = "\u201c${quotePair.first}\u201d"; textSize = 13f
            setTextColor(if (cfg.adultMode) Color.parseColor("#B0BEC5") else Color.parseColor("#4A6080"))
            setTypeface(typeface, Typeface.ITALIC); setLineSpacing(0f, 1.4f)
        }
        val authorText = TextView(ctx).apply {
            text = "— ${quotePair.second}"; textSize = 11f; setTextColor(cfg.accentColor)
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, (5 * dp).toInt(), 0, 0)
        }
        quoteCol.addView(quoteText); quoteCol.addView(authorText)
        quoteRow.addView(bar); quoteRow.addView(quoteCol); body.addView(quoteRow)

        // Progress bar (animated)
        val progressTrack = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E0F2F1")); cornerRadius = 6f * dp
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (6f * dp).toInt())
                .apply { setMargins(0, (24 * dp).toInt(), 0, 0) }
        }
        val progressFill = View(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cfg.accentColor, cfg.headerTopColor)
            ).apply { cornerRadius = 6f * dp }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        progressTrack.addView(progressFill); body.addView(progressTrack)

        // Home button
        body.addView(TextView(ctx).apply {
            text = "🏠  হোমে ফিরুন"; textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cfg.accentColor, cfg.headerTopColor)
            ).apply { cornerRadius = 14f * dp }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * dp).toInt())
                .apply { setMargins(0, (20 * dp).toInt(), 0, 0) }
            val p = (16 * dp).toInt(); setPadding(p, p, p, p)
            setOnClickListener {
                removeOverlay()
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
        })

        bodyScroll.addView(body)
        root.addView(header); root.addView(bodyScroll)

        // Animated progress bar
        val totalMs = cfg.autoDismissMs.takeIf { it > 0 } ?: 5000L
        val updateMs = 50L; var elapsed = 0L
        val progressTick = object : Runnable {
            override fun run() {
                elapsed += updateMs
                val fraction = 1f - (elapsed.toFloat() / totalMs)
                progressFill.scaleX = fraction.coerceIn(0f, 1f)
                progressFill.pivotX = 0f
                if (elapsed < totalMs) mainHandler.postDelayed(this, updateMs)
            }
        }
        mainHandler.postDelayed(progressTick, updateMs)

        // Auto-dismiss (except adult)
        if (cfg.autoDismissMs > 0) {
            mainHandler.postDelayed({ removeOverlay() }, cfg.autoDismissMs)
        }

        } catch (e: Exception) {
            // root ইতিমধ্যে attach করা এবং screen ঢেকে আছে — সেটা untouched
            // রাখব (uncover করলে ঠিক যে bug fix করছি সেটাই আবার ঘটবে)।
            // শুধু rich content আংশিক বসতে ব্যর্থ হয়েছে, blocking ঠিকই কাজ করছে।
        }
    }

    // ── Remove overlay ───────────────────────────────────────────────────
    private fun removeOverlay() {
        overlayView?.let { v ->
            try { windowManager?.removeView(v) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
        isVisible = false
    }
}
