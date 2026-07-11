package com.rasel.RasFocus.combo.selfcontrol

// ════════════════════════════════════════════════════════════════════════════
//  BpWebViewActivity.kt
//
//  BlockingProfile session এ Chrome allowed না হলে এই in-app WebView ব্যবহার হয়।
//  শুধুমাত্র allowed_sites এ navigate করা যাবে, অন্য সব block।
// ════════════════════════════════════════════════════════════════════════════

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class BpWebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var allowedSites: List<String> = emptyList()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: run {
            finish()
            return
        }
        allowedSites = intent.getStringArrayExtra("allowed_sites")?.toList() ?: emptyList()

        // ── Root Layout ──────────────────────────────────────────────────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1117"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ── Top Bar ──────────────────────────────────────────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1D2E"))
            gravity = Gravity.CENTER_VERTICAL
            val dp = resources.displayMetrics.density
            setPadding(
                (12 * dp).toInt(), (8 * dp).toInt(),
                (12 * dp).toInt(), (8 * dp).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dp = resources.displayMetrics.density

        // Back button
        val backBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#9CA3AF"))
            layoutParams = LinearLayout.LayoutParams(
                (36 * dp).toInt(), (36 * dp).toInt()
            ).apply { setMargins(0, 0, (8 * dp).toInt(), 0) }
            contentDescription = "Back"
            setOnClickListener {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        }

        // Title / URL
        val titleText = TextView(this).apply {
            text = Uri.parse(url).host ?: url
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Close button
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 16f
            gravity = Gravity.CENTER
            val p = (10 * dp).toInt()
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { finish() }
        }

        topBar.addView(backBtn)
        topBar.addView(titleText)
        topBar.addView(closeBtn)

        // ── Progress Bar ──────────────────────────────────────────────────
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (3 * dp).toInt()
            )
        }

        // ── WebView ───────────────────────────────────────────────────────
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            setBackgroundColor(Color.parseColor("#0F1117"))

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val requestedHost = request.url.host ?: return false
                    return if (isAllowed(requestedHost)) {
                        false // allow navigation
                    } else {
                        Toast.makeText(
                            this@BpWebViewActivity,
                            "🚫 এই সাইট এই session এ allowed নয়",
                            Toast.LENGTH_SHORT
                        ).show()
                        true // block
                    }
                }

                override fun onPageStarted(
                    view: WebView?,
                    loadedUrl: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    progressBar.visibility = View.VISIBLE
                    loadedUrl?.let {
                        titleText.text = Uri.parse(it).host ?: it
                    }
                }

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    progressBar.visibility = View.GONE
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // SSL error → block
                    handler?.cancel()
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                }
            }
        }

        root.addView(topBar)
        root.addView(progressBar)
        root.addView(webView)
        setContentView(root)

        // ── Load the URL ──────────────────────────────────────────────────
        val finalUrl = if (url.startsWith("http")) url else "https://$url"
        val startHost = Uri.parse(finalUrl).host ?: ""
        if (isAllowed(startHost)) {
            webView.loadUrl(finalUrl)
        } else {
            Toast.makeText(this, "🚫 এই সাইট allowed নয়", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun isAllowed(host: String): Boolean {
        if (allowedSites.isEmpty()) return true
        return allowedSites.any { allowed ->
            val clean = allowed.removePrefix("https://").removePrefix("http://")
                .removePrefix("www.").trimEnd('/')
            host == clean || host.endsWith(".$clean") ||
                    host.removePrefix("www.") == clean
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
