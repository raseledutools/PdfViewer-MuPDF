package com.rasel.RasFocus.selfcontrol.familybrowser

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rasel.RasFocus.selfcontrol.FirebaseKeywordSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

// ─── ১. মূল ফিল্টারিং এবং লজিক ক্লাস (AdBlocker) ──────────────────────────────
class AdBlocker(private val context: Context) {

    companion object {
        // ── Adult URL keywords / domains / TLDs ──────────────────────────────
        // FIX: এই তিনটা list আগে এখানেই hardcoded ছিল (app update ছাড়া কোনো নতুন
        // keyword/domain যোগ করা যেত না)। এখন এগুলো FirebaseKeywordSync থেকে আসে —
        // ওই object টা app চালু হওয়ার সময়েই (RasFocusApplication.onCreate) Firebase
        // Realtime Database এর "keyword_data" node থেকে fetch করে এবং SharedPreferences
        // এ cache করে রাখে (offline এও কাজ করবে, শেষ sync করা list দিয়ে)।
        // Firebase console এ keyword_data/adult_keywords, adult_domains, adult_tlds
        // update করলেই — কোনো app update ছাড়াই — নতুন keyword/domain block হয়ে যাবে।
        val ADULT_URL_KEYWORDS: Set<String>
            get() = FirebaseKeywordSync.getAdultKeywords()

        private val ADULT_TLDS: Set<String>
            get() = FirebaseKeywordSync.getAdultTlds()

        // ── Remote Blocklist prefs keys ──
        private const val REMOTE_LIST_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
        private const val PREF_REMOTE_DOMAINS   = "remote_adult_domains"
        private const val PREF_LAST_UPDATE_TIME = "remote_list_last_update"

        // ─── Ad Network Domains ───────────────────────────────────────────────
        private val AD_DOMAINS = setOf(
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "googleadservices.com", "pagead2.googlesyndication.com", "tpc.googlesyndication.com",
            "securepubads.g.doubleclick.net", "stats.g.doubleclick.net", "cm.g.doubleclick.net",
            "ad.doubleclick.net", "googleads.g.doubleclick.net", "imasdk.googleapis.com",
            "static.doubleclick.net", "www.googleadservices.com", "amazon-adsystem.com",
            "adsystem.amazon.com", "fls-na.amazon.com", "an.facebook.com", "connect.facebook.net",
            "adnxs.com", "ib.adnxs.com", "secure.adnxs.com", "acdn.adnxs.com", "rubiconproject.com",
            "pixel.rubiconproject.com", "pubmatic.com", "ads.pubmatic.com", "simage2.pubmatic.com",
            "openx.net", "criteo.com", "criteo.net", "adsrvr.org", "advertising.com", "appnexus.com",
            "bidswitch.net", "casalemedia.com", "indexexchange.com", "lijit.com", "sovrn.com",
            "yieldmo.com", "media.net", "mathtag.com", "pixel.mathtag.com", "adsafeprotected.com",
            "eyeota.net", "moatads.com", "pixel.moatads.com", "taboola.com", "cdn.taboola.com",
            "trc.taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "zergnet.com",
            "adblade.com", "ads.twitter.com", "static.ads-twitter.com", "analytics.twitter.com",
            "bat.bing.com", "hotjar.com", "mouseflow.com", "fullstory.com", "logrocket.com",
            "scorecardresearch.com", "quantserve.com", "semasio.net", "exelate.com", "bluekai.com",
            "demdex.net", "turn.com", "agkn.com", "segment.io", "banner.siteimprove.com"
        )

        // ─── Tracker Domains ──────────────────────────────────────────────────
        private val TRACKER_DOMAINS = setOf(
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "analytics.google.com", "ssl.google-analytics.com", "www.google-analytics.com",
            "stats.wp.com", "pixel.wp.com", "bat.bing.com", "analytics.twitter.com",
            "t.co", "connect.facebook.net", "graph.facebook.com", "analytics.yahoo.com",
            "beacon.yahoo.com", "clicks.beap.bc.yahoo.com", "piwik.org", "matomo.org",
            "statcounter.com", "clicktale.net", "clicktale.com", "crazyegg.com", "trackjs.com",
            "raygun.io", "bugsnag.com", "newrelic.com", "nr-data.net", "amplitude.com",
            "api.amplitude.com", "cdn.amplitude.com", "mixpanel.com", "cdn4.mxpnl.com",
            "segment.com", "cdn.segment.com", "api.segment.io", "cdn.heapanalytics.com",
            "heapanalytics.com", "rollbar.com", "sentry.io", "ingest.sentry.io",
            "browser.sentry-cdn.com", "intercom.io", "widget.intercom.io", "nexus.ensighten.com"
        )

        // ─── Adult Content Domains ────────────────────────────────────────────
        // FIX: এই বিশাল hardcoded domain list এখন Firebase থেকে আসে (দেখো উপরের
        // ADULT_URL_KEYWORDS/ADULT_TLDS এর কমেন্ট) — নতুন domain block করতে শুধু
        // Firebase console/keyword_data/adult_domains আপডেট করলেই হবে।
        private val ADULT_DOMAINS: Set<String>
            get() = FirebaseKeywordSync.getAdultDomains()

        fun buildBlockedPage(url: String, reason: BlockReason): String {
            val (icon, title, subtitle, color) = when (reason) {
                BlockReason.ADULT    -> Quadruple("🔒", "Site Blocked",    "This site contains adult content and has been blocked for safe browsing.", "#E53E3E")
                BlockReason.AD       -> Quadruple("🛡️", "Ad Blocked",      "An advertisement or tracker was blocked.",                                 "#38A169")
                BlockReason.TRACKER  -> Quadruple("👁️", "Tracker Blocked", "A tracking script was prevented from loading.",                            "#3182CE")
                BlockReason.KIDS_MODE-> Quadruple("👶", "Not Allowed",     "This site is not on the approved list for Kids Mode.",                     "#805AD5")
            }
            return """
                <!DOCTYPE html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0">
                <style>
                  * { margin:0; padding:0; box-sizing:border-box; }
                  html, body { width: 100%; height: 100vh; overflow: hidden; }
                  body { font-family: -apple-system, sans-serif; background: #F7FAFC;
                         display:flex; align-items:center; justify-content:center; padding:24px; }
                  .card { background:white; border-radius:20px; padding:40px 32px;
                          text-align:center; max-width:400px; width:100%;
                          box-shadow: 0 10px 30px rgba(0,0,0,0.1); }
                  .icon { font-size:64px; margin-bottom:20px; }
                  h1 { font-size:26px; font-weight:700; color:#1A202C; margin-bottom:12px; }
                  p { color:#718096; font-size:16px; line-height:1.6; margin-bottom:24px; }
                  .url { background:#EDF2F7; border-radius:8px; padding:12px;
                         font-size:13px; color:#A0AEC0; word-break:break-all; margin-bottom:24px; }
                  .badge { display:inline-block; background:$color; color:white;
                           border-radius:20px; padding:8px 18px; font-size:14px; font-weight:600; }
                  .back-btn { display:block; margin-top:24px; padding:16px;
                              background:#E2E8F0; border-radius:12px; color:#4A5568;
                              font-size:16px; font-weight:600; text-decoration:none; }
                </style></head><body>
                <div class="card">
                  <div class="icon">$icon</div>
                  <h1>$title</h1>
                  <p>$subtitle</p>
                  <div class="url">$url</div>
                  <span class="badge">Family Browser Protection</span>
                  <a href="javascript:history.back()" class="back-btn">← Go Back</a>
                </div></body></html>
            """.trimIndent()
        }

        // ── Internal: domain match helper (Updated with TLDs) ──
        private fun isAdultHost(host: String): Boolean {
            if (ADULT_TLDS.any { host.endsWith(it) }) return true
            return ADULT_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
        }

        // ── Public helper — FloatingWindowService + YoutubeFloatingWindowService ব্যবহার করে ──
        fun isAdultSite(url: String): Boolean {
            return try {
                val host = android.net.Uri.parse(url).host?.lowercase()
                    ?.removePrefix("www.") ?: return false
                isAdultHost(host)
            } catch (e: Exception) { false }
        }
    }

    // ─── Remote Blocklist (StevenBlack Adult List) ────────────────────────────
    private val remoteDomainSet = mutableSetOf<String>()

    var remoteListDomainCount: Int = 0
        private set
    var remoteListLastUpdated: Long = 0L
        private set

    // App start হলে call করো — BrowserViewModel এর init{} এ
    fun initRemoteBlocklist() {
        loadCachedDomainsFromDisk()
        CoroutineScope(Dispatchers.IO).launch {
            val lastUpdate = prefs.getLong(PREF_LAST_UPDATE_TIME, 0L)
            val now = System.currentTimeMillis()
            if (now - lastUpdate > TimeUnit.DAYS.toMillis(7)) {
                fetchAndCacheRemoteList()
            }
        }
    }

    // Settings থেকে "Update Now" button এ call করো
    fun forceUpdateRemoteBlocklist(onDone: (success: Boolean, count: Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = fetchAndCacheRemoteList()
            withContext(Dispatchers.Main) {
                onDone(success, remoteListDomainCount)
            }
        }
    }

    private suspend fun fetchAndCacheRemoteList(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val conn = URL(REMOTE_LIST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            if (conn.responseCode != 200) return@withContext false

            val domains = mutableSetOf<String>()
            conn.inputStream.bufferedReader().forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val domain = parts[1].lowercase().trim()
                    if (domain.isNotEmpty() && domain != "localhost" &&
                        domain != "0.0.0.0" && domain != "broadcasthost" &&
                        !domain.startsWith("#")) {
                        domains.add(domain)
                    }
                }
            }
            conn.disconnect()
            if (domains.isEmpty()) return@withContext false

            remoteDomainSet.clear()
            remoteDomainSet.addAll(domains)
            remoteListDomainCount = domains.size
            remoteListLastUpdated = System.currentTimeMillis()

            prefs.edit()
                .putString(PREF_REMOTE_DOMAINS, domains.joinToString("\n"))
                .putLong(PREF_LAST_UPDATE_TIME, remoteListLastUpdated)
                .apply()
            true
        } catch (e: Exception) { false }
    }

    private fun loadCachedDomainsFromDisk() {
        val saved = prefs.getString(PREF_REMOTE_DOMAINS, null) ?: return
        val domains = saved.split("\n").filter { it.isNotBlank() }.toSet()
        remoteDomainSet.clear()
        remoteDomainSet.addAll(domains)
        remoteListDomainCount = domains.size
        remoteListLastUpdated = prefs.getLong(PREF_LAST_UPDATE_TIME, 0L)
    }

    private fun isInRemoteBlocklist(host: String): Boolean {
        if (remoteDomainSet.isEmpty()) return false
        if (remoteDomainSet.contains(host)) return true
        val parent = host.split(".").drop(1).joinToString(".")
        return parent.isNotEmpty() && remoteDomainSet.contains(parent)
    }

    // ─── State ────────────────────────────────────────────────────────────────
    var isAdBlockEnabled: Boolean = true
    var isTrackerBlockEnabled: Boolean = true
    var isAdultBlockEnabled: Boolean = true
    var isKeywordBlockEnabled: Boolean = true
    var isDohEnabled: Boolean = true
    var isSafeSearchEnabled: Boolean = true

    @get:JvmName("getAdultBlockPinValue")
    @set:JvmName("setAdultBlockPinValue")
    var adultBlockPin: String = ""

    var trackerBlockCount: Int = 0
        private set
    var adBlockCount: Int = 0
        private set

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "adblocker_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE)
        }
    }

    init { loadSettings(); initRemoteBlocklist() }

    /**
     * Layer 4,5,6: Dynamic content scanner — feed/search/reels-এ visible হওয়া
     * DOM text, image alt/src, meta rating সব scan করে adult content detect করে,
     * পাওয়া গেলে সাথে সাথেই full-screen block page দেখিয়ে দেয়।
     *
     * এটা public করা হয়েছে যাতে YoutubeActivity ও FacebookActivity — যারা
     * FamilyWebViewClient ব্যবহার না করে নিজস্ব WebViewClient বানায় — তারাও
     * নিজেদের onPageFinished/onPageStarted থেকে এটা সরাসরি call করতে পারে।
     */
    fun injectContentScanner(view: WebView?) {
        if (view == null || !isAdultBlockEnabled) return

        val jsKeywordsArray = ADULT_URL_KEYWORDS.joinToString("','", "['", "']")

        val jsScannerCode = """
            javascript:(function() {
                const badWords = $jsKeywordsArray;

                const QUOTES = [
                    ["তোমার সময় তোমার সবচেয়ে বড় সম্পদ।", "Your time is your greatest asset."],
                    ["এক মুহূর্তের সিদ্ধান্ত, ভবিষ্যতের রাস্তা তৈরি করে।", "One decision shapes the road ahead."],
                    ["যা এড়িয়ে গেলে, সেটাই তোমাকে এগিয়ে নেবে।", "What you avoid today builds you tomorrow."],
                    ["ফোকাস মানেই স্বাধীনতা।", "Focus is freedom."],
                    ["তুমি যা বারবার করো, তুমি তাই হয়ে ওঠো।", "You become what you repeatedly do."]
                ];

                function executeBlock() {
                    window.stop();
                    if (window.__rasBlockOverlayShown) return;
                    window.__rasBlockOverlayShown = true;

                    if (!document.getElementById('ras-block-style')) {
                        var styleTag = document.createElement('style');
                        styleTag.id = 'ras-block-style';
                        styleTag.innerHTML = `
                            @keyframes rasFadeIn { from { opacity:0; } to { opacity:1; } }
                            @keyframes rasPopIn { from { opacity:0; transform:scale(0.92) translateY(14px); } to { opacity:1; transform:scale(1) translateY(0); } }
                            @keyframes rasPulse { 0%,100% { transform:scale(1); } 50% { transform:scale(1.06); } }
                            #ras-block-overlay * { box-sizing:border-box; font-family:-apple-system, 'Segoe UI', Roboto, sans-serif; }
                        `;
                        document.head.appendChild(styleTag);
                    }

                    var pick = QUOTES[Math.floor(Math.random() * QUOTES.length)];

                    var overlay = document.createElement('div');
                    overlay.id = 'ras-block-overlay';
                    overlay.style.cssText =
                        'position:fixed; top:0; left:0; width:100%; height:100%; z-index:2147483647;' +
                        'background:linear-gradient(160deg, #0f2027 0%, #203a43 45%, #2c5364 100%);' +
                        'display:flex; align-items:center; justify-content:center;' +
                        'animation:rasFadeIn .35s ease-out; padding:24px;';

                    overlay.innerHTML =
                        '<div style="width:100%; max-width:380px; text-align:center; animation:rasPopIn .4s cubic-bezier(.2,.8,.3,1);">' +
                            '<div style="font-size:58px; margin-bottom:14px; animation:rasPulse 2.2s ease-in-out infinite;">🛡️</div>' +
                            '<div style="color:#ffffff; font-size:21px; font-weight:700; letter-spacing:.3px; margin-bottom:6px;">RasFocus Safe Mode</div>' +
                            '<div style="color:rgba(255,255,255,0.55); font-size:12.5px; letter-spacing:1.5px; text-transform:uppercase; margin-bottom:22px;">Content Blocked</div>' +
                            '<div style="background:rgba(255,255,255,0.08); border:1px solid rgba(255,255,255,0.12); border-radius:18px; padding:22px 20px; margin-bottom:22px; backdrop-filter:blur(6px);">' +
                                '<div style="color:#7EE8C7; font-size:11px; font-weight:600; letter-spacing:1px; text-transform:uppercase; margin-bottom:10px;">✨ Motivation</div>' +
                                '<div style="color:#ffffff; font-size:16px; line-height:1.55; font-weight:600; margin-bottom:6px;">' + pick[0] + '</div>' +
                                '<div style="color:rgba(255,255,255,0.6); font-size:12.5px; line-height:1.5; font-style:italic;">' + pick[1] + '</div>' +
                            '</div>' +
                            '<div style="display:flex; align-items:center; justify-content:center; gap:8px; color:rgba(255,255,255,0.5); font-size:12px; margin-bottom:26px;">' +
                                '<span>⏱️</span><span>এই মুহূর্তটা তুমি নিজের জন্য বাঁচালে</span>' +
                            '</div>' +
                            '<div style="display:flex; flex-direction:column; gap:10px;">' +
                                '<button id="ras-block-close-home" style="width:100%; padding:15px; border:none; border-radius:14px; background:linear-gradient(135deg,#43e97b,#38f9d7); color:#0f2027; font-size:15px; font-weight:700; letter-spacing:.2px; cursor:pointer;">🏠 Close &amp; Go Home</button>' +
                                '<button id="ras-block-close" style="width:100%; padding:15px; border:1px solid rgba(255,255,255,0.18); border-radius:14px; background:rgba(255,255,255,0.06); color:rgba(255,255,255,0.85); font-size:14.5px; font-weight:600; cursor:pointer;">Close</button>' +
                            '</div>' +
                        '</div>';

                    document.documentElement.appendChild(overlay);
                    document.body && (document.body.style.overflow = 'hidden');

                    document.getElementById('ras-block-close').addEventListener('click', function() {
                        if (window.RasBlockBridge) { window.RasBlockBridge.onClose(); }
                    });
                    document.getElementById('ras-block-close-home').addEventListener('click', function() {
                        if (window.RasBlockBridge) { window.RasBlockBridge.onCloseAndHome(); }
                    });
                }

                function checkContent() {
                    let shouldBlock = false;

                    const metaRating = document.querySelector('meta[name="rating" i]');
                    const metaRTA = document.querySelector('meta[name="RATING" i]');
                    if ((metaRating && metaRating.content.toLowerCase() === 'adult') ||
                        (metaRTA && metaRTA.content.includes('RTA-5042'))) {
                        shouldBlock = true;
                    }

                    if (!shouldBlock) {
                        const titleText = document.title.toLowerCase();
                        const bodyText = document.body ? document.body.innerText.substring(0, 5000).toLowerCase() : "";
                        const contentToScan = titleText + " " + bodyText;

                        shouldBlock = badWords.some(word => {
                            const regex = new RegExp('\\b' + word + '\\b');
                            return regex.test(contentToScan);
                        });
                    }

                    if (!shouldBlock) {
                        const images = document.getElementsByTagName('img');
                        const maxImages = Math.min(images.length, 100);
                        for (let i = 0; i < maxImages; i++) {
                            const imgSrc = images[i].src ? images[i].src.toLowerCase() : "";
                            const imgAlt = images[i].alt ? images[i].alt.toLowerCase() : "";

                            const hasBadImage = badWords.some(word => imgSrc.includes(word) || imgAlt.includes(word));
                            if (hasBadImage) {
                                shouldBlock = true;
                                break;
                            }
                        }
                    }

                    if (shouldBlock) {
                        executeBlock();
                    }
                }

                checkContent();

                if (!window.hasFamilyBlockerObserver) {
                    window.hasFamilyBlockerObserver = true;
                    const observer = new MutationObserver(function(mutations) {
                        checkContent();
                    });
                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true });
                    }
                }
            })();
        """.trimIndent()

        view.evaluateJavascript(jsScannerCode, null)
    }

    /**
     * Block overlay-এর "Close" ও "Close & Go Home" বাটনের জন্য JS bridge।
     * WebView-তে addJavascriptInterface("RasBlockBridge", ...) দিয়ে attach করো।
     *
     * onClose      → overlay সরিয়ে দাও, আগের (নিরাপদ) পেজেই থাকো
     * onCloseHome  → overlay সরিয়ে homeUrl reload করো (Facebook/YouTube হোম)
     */
    class BlockOverlayBridge(
        private val webView: WebView,
        private val homeUrl: String,
        private val runOnUi: (() -> Unit) -> Unit
    ) {
        @android.webkit.JavascriptInterface
        fun onClose() {
            runOnUi {
                webView.evaluateJavascript(
                    "(function(){var o=document.getElementById('ras-block-overlay'); if(o) o.remove(); document.body.style.overflow='';})();",
                    null
                )
            }
        }

        @android.webkit.JavascriptInterface
        fun onCloseAndHome() {
            runOnUi {
                webView.loadUrl(homeUrl)
            }
        }
    }

    // ─── Navigation Block (shouldOverrideUrlLoading এ call করো) ─────────────
    fun shouldBlockNavigation(url: String): String? {
        if (!isAdultBlockEnabled) return null
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return null
            val lowerUrl = url.lowercase()

            if (isAdultHost(host) || isInRemoteBlocklist(host)) return buildBlockedPage(url, BlockReason.ADULT)

            if (isKeywordBlockEnabled) {
                val keywordBlocked = ADULT_URL_KEYWORDS.any { keyword -> lowerUrl.contains(keyword) }
                if (keywordBlocked) return buildBlockedPage(url, BlockReason.ADULT)
            }

            null
        } catch (e: Exception) { null }
    }

    // ─── Main Intercept (shouldInterceptRequest এ call করো) ──────────────────
    fun shouldBlock(
        request: WebResourceRequest,
        isKidsMode: Boolean = false,
        kidsWhitelist: Set<String> = emptySet()
    ): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        val host = request.url?.host?.lowercase() ?: return null
        val lowerUrl = url.lowercase()
        val isMainFrame = request.isForMainFrame

        if (isKidsMode && isMainFrame) {
            val allowed = kidsWhitelist.any { host.endsWith(it) || host == it }
            if (!allowed) return blockedPageResponse(url, BlockReason.KIDS_MODE)
        }

        if (isAdultBlockEnabled) {
            if (isAdultHost(host) || isInRemoteBlocklist(host)) {
                return if (isMainFrame) blockedPageResponse(url, BlockReason.ADULT) else emptyResponse()
            }
        }

        if (isAdultBlockEnabled && isKeywordBlockEnabled && isMainFrame) {
            if (ADULT_URL_KEYWORDS.any { keyword -> lowerUrl.contains(keyword) }) {
                return blockedPageResponse(url, BlockReason.ADULT)
            }
        }

        if (isAdBlockEnabled && AD_DOMAINS.any { domain -> host.endsWith(domain) || host == domain }) {
            adBlockCount++
            return emptyResponse()
        }

        if (isTrackerBlockEnabled && TRACKER_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            // FIX: আগে host.contains(it) ব্যবহার হতো — এটা loose substring match,
            // যেমন কোনো site এর সাবডোমেইনে কাকতালীয়ভাবে কোনো tracker domain এর
            // substring মিলে গেলে সেই দরকারি resource block হয়ে যেতো এবং
            // ChatGPT/Claude এর মতো heavy JS site সাদা স্ক্রিন দেখাতো। এখন শুধু
            // exact host বা proper subdomain match হলেই block হবে (AD_DOMAINS এর
            // মতোই নিরাপদ পদ্ধতি)।
            trackerBlockCount++
            return emptyResponse()
        }

        return null
    }

    // ─── Safe Search ──────────────────────────────────────────────────────────
    fun applySafeSearch(webView: WebView, url: String): Boolean {
        if (!isSafeSearchEnabled) return false
        val safeUrl = buildSafeSearchUrl(url) ?: return false
        if (safeUrl == url) return false
        webView.post { webView.loadUrl(safeUrl) }
        return true
    }

    fun buildSafeSearchUrl(url: String): String? {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return null

            if (host.contains("google.") && url.contains("/search")) {
                return when {
                    url.contains("safe=strict") -> null
                    url.contains("safe=")        -> url.replace(Regex("safe=(off|images|moderate|active)"), "safe=strict")
                    else                         -> url + (if (url.contains("?")) "&" else "?") + "safe=strict"
                }
            }
            if (host.contains("bing.com") && url.contains("/search")) {
                return when {
                    url.contains("adlt=strict") -> null
                    url.contains("adlt=")        -> url.replace(Regex("adlt=(off|moderate)"), "adlt=strict")
                    else                         -> url + (if (url.contains("?")) "&" else "?") + "adlt=strict"
                }
            }
            if (host.contains("duckduckgo.com") && url.contains("q=")) {
                return when {
                    url.contains("kp=1")  -> null
                    url.contains("kp=")   -> url.replace(Regex("kp=(-2|-1|0)"), "kp=1")
                    else                  -> url + (if (url.contains("?")) "&" else "?") + "kp=1"
                }
            }
            null
        } catch (e: Exception) { null }
    }

    // ─── Configuration & Persistence ──────────────────────────────────────────
    fun setAdultBlockPin(pin: String) {
        adultBlockPin = pin
        prefs.edit().putString("adult_pin", pin).apply()
    }
    fun verifyPin(pin: String): Boolean = pin == adultBlockPin
    fun disableAdultBlockWithPin(pin: String): Boolean {
        return if (verifyPin(pin)) { isAdultBlockEnabled = false; saveSettings(); true } else false
    }

    fun saveSettings() {
        prefs.edit().putBoolean("ad_block", isAdBlockEnabled)
            .putBoolean("tracker_block", isTrackerBlockEnabled)
            .putBoolean("adult_block", isAdultBlockEnabled)
            .putBoolean("keyword_block", isKeywordBlockEnabled)
            .putBoolean("doh_enabled", isDohEnabled)
            .putBoolean("safe_search", isSafeSearchEnabled)
            .putString("adult_pin", adultBlockPin).apply()
    }

    private fun loadSettings() {
        isAdBlockEnabled = prefs.getBoolean("ad_block", true)
        isTrackerBlockEnabled = prefs.getBoolean("tracker_block", true)
        isAdultBlockEnabled = prefs.getBoolean("adult_block", true)
        isKeywordBlockEnabled = prefs.getBoolean("keyword_block", true)
        isDohEnabled = prefs.getBoolean("doh_enabled", true)
        isSafeSearchEnabled = prefs.getBoolean("safe_search", true)
        adultBlockPin = prefs.getString("adult_pin", "") ?: ""
    }

    fun resetCounts() { adBlockCount = 0; trackerBlockCount = 0 }
    fun getDohSetupInstructions(): String = "Settings → Network → Private DNS → Hostname: family.cloudflare-dns.com"
    fun isPrivateDnsLikelyEnabled(): Boolean = false

    private fun blockedPageResponse(url: String, reason: BlockReason): WebResourceResponse {
        val html = buildBlockedPage(url, reason)
        return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Content-Type" to "text/html"), ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
    private fun emptyResponse(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

// ─── SafeSearchEnforcer — top-level object (fully qualified access) ───────────
object SafeSearchEnforcer {
    fun enforceIfNeeded(url: String): String? {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return null
            when {
                host.contains("google.") && url.contains("/search") -> when {
                    url.contains("safe=strict") -> null
                    url.contains("safe=") -> url.replace(Regex("safe=(off|images|moderate|active)"), "safe=strict")
                    else -> url + (if (url.contains("?")) "&" else "?") + "safe=strict"
                }
                host.contains("bing.com") && url.contains("/search") -> when {
                    url.contains("adlt=strict") -> null
                    url.contains("adlt=") -> url.replace(Regex("adlt=(off|moderate)"), "adlt=strict")
                    else -> url + (if (url.contains("?")) "&" else "?") + "adlt=strict"
                }
                host.contains("duckduckgo.com") && url.contains("q=") -> when {
                    url.contains("kp=1") -> null
                    url.contains("kp=") -> url.replace(Regex("kp=(-2|-1|0)"), "kp=1")
                    else -> url + (if (url.contains("?")) "&" else "?") + "kp=1"
                }
                else -> null
            }
        } catch (e: Exception) { null }
    }
}


// ─── ২. WebViewClient ক্লাস (JavaScript Injector সহ) ────────────────────────
class FamilyWebViewClient(private val adBlocker: AdBlocker) : WebViewClient() {

    // ── Layer 2 & 3: URL Intercept & SafeSearch ──
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // 1. Adult URL / Keyword Blocker
        val blockedHtml = adBlocker.shouldBlockNavigation(url)
        if (blockedHtml != null) {
            view.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null)
            return true
        }

        // 2. SafeSearch Enforcer
        if (adBlocker.applySafeSearch(view, url)) {
            return true
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return adBlocker.shouldBlock(request) ?: super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        injectMultiLayerScanner(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectMultiLayerScanner(view)
    }

    // ── Layer 4, 5, 6: Dynamic Content Scanner (Meta, Image, DOM) ──
    private fun injectMultiLayerScanner(view: WebView?) {
        adBlocker.injectContentScanner(view)
    }
}

// ─── ৩. ডেটা ক্লাস এবং এনাম (Data Class & Enum) ──────────────────────────────
enum class BlockReason { ADULT, AD, TRACKER, KIDS_MODE }
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


// ─── ৪. YouTube Ad Pruner — uBlock Origin json-prune approach ────────────────
/**
 * YouTubeAdPruner — uBlock Origin এর json-prune এর exact equivalent
 *
 * Layer 1: shouldInterceptRequest → POST body read করে ad fields prune
 * Layer 2: JS inject → fetch()/XHR intercept (page-side, real-time)
 *
 * YouTube এর /youtubei/v1/player POST response থেকে adPlacements, playerAds
 * ইত্যাদি delete করলে YouTube player মনে করে ad নেই — request-ই পাঠায় না।
 */
object YouTubeAdPruner {

    private val YT_PLAYER_ENDPOINTS = listOf(
        "/youtubei/v1/player",
        "/youtubei/v1/next",
        "/youtubei/v1/browse",
        "/youtubei/v1/reel/reel_watch_sequence"
    )

    // uBlock Origin এর exact field list + নতুন fields (2024-2025 YouTube)
    private val AD_FIELDS = listOf(
        "adPlacements",
        "playerAds",
        "adSlots",
        "adBreakHeartbeatParams",
        "auxiliaryUi",
        "adMessagingConfig",
        "adVideoId",
        "adBreakParams",
        "adClientInfoExtension",
        "adCpnExtension",
        "adDurationRemaining",
        "adLayoutLoggingData",
        "adMetadataRenderer",
        "adPlacementConfig",
        "adRendererType",
        "adResponseDecoder",
        "adSurvey",
        "adSystem",
        "adTimeOffset",
        "adVideoDuration",
        "companionData",
        "instreamVideoAdRenderer",
        "linearAdSequenceRenderer",
        "fullyAdFree",         // premium indicator — ওরা hide করে রাখে
        "promotedSparkles"
    )

    /**
     * shouldInterceptRequest এ call করো।
     * YouTube /player এ POST করে — body read করে prune করে ফিরিয়ে দাও।
     */
    fun interceptPlayerResponse(
        request: android.webkit.WebResourceRequest
    ): android.webkit.WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        if (YT_PLAYER_ENDPOINTS.none { url.contains(it) }) return null

        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

            // Original headers copy — cookie, auth, X-Goog-* সব দরকার
            request.requestHeaders.forEach { (key, value) ->
                try { connection.setRequestProperty(key, value) } catch (_: Exception) {}
            }

            // YouTube /player endpoint এ POST করে — GET fallback নয়
            val method = request.method ?: "POST"
            connection.requestMethod = method
            connection.doOutput = method == "POST"
            connection.doInput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.useCaches = false

            // POST body — WebResourceRequest এ body নেই (Android limitation)
            // তাই POST body ছাড়াই connect করি — YouTube 400 দেবে না, partial response দেবে
            // আসল body JS-side fetch intercept ধরবে
            connection.connect()

            if (connection.responseCode !in 200..299) return null

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (body.isBlank() || !body.trimStart().startsWith("{")) return null

            val pruned = pruneAdFields(body)

            android.webkit.WebResourceResponse(
                connection.contentType ?: "application/json; charset=UTF-8",
                "UTF-8",
                200,
                "OK",
                mapOf(
                    "Content-Type" to "application/json; charset=UTF-8",
                    "Access-Control-Allow-Origin" to "*"
                ),
                pruned.byteInputStream(Charsets.UTF_8)
            )
        } catch (_: Exception) { null }
    }

    fun pruneAdFields(json: String): String {
        if (json.isBlank()) return json
        return try {
            val obj = org.json.JSONObject(json)
            removeAdFields(obj)
            obj.toString()
        } catch (_: Exception) { json }
    }

    private fun removeAdFields(obj: org.json.JSONObject) {
        AD_FIELDS.forEach { obj.remove(it) }
        obj.optJSONObject("playerResponse")?.let { pr ->
            AD_FIELDS.forEach { pr.remove(it) }
        }
        // streamingData এ আর ad segment থাকলে সরাও
        obj.optJSONObject("streamingData")?.remove("adTimingDataByAdPodId")
        // contents tree তে sponsored items সরাও
        obj.optJSONObject("contents")?.let { pruneContents(it) }
        // nested objects recursive
        obj.keys().forEach { key ->
            when (val v = obj.opt(key)) {
                is org.json.JSONObject -> removeAdFields(v)
                is org.json.JSONArray  -> pruneArray(v)
            }
        }
    }

    private fun pruneContents(obj: org.json.JSONObject) {
        val sponsoredKeys = listOf(
            "promotedVideoRenderer", "searchPyvRenderer",
            "promotedSparklesWebRenderer", "adSlotRenderer",
            "compactPromotedVideoRenderer", "universalWatchCardRenderer"
        )
        val iter = obj.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            if (key in sponsoredKeys) { iter.remove(); continue }
            when (val v = obj.opt(key)) {
                is org.json.JSONObject -> pruneContents(v)
                is org.json.JSONArray  -> pruneArray(v)
            }
        }
    }

    private fun pruneArray(arr: org.json.JSONArray) {
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is org.json.JSONObject -> pruneContents(item)
                is org.json.JSONArray  -> pruneArray(item)
            }
        }
    }

    /**
     * JS inject script — page load এ একবার inject হলেই চলে।
     *
     * ৩ layer:
     *   1. fetch() intercept → /player response prune
     *   2. XHR intercept → same
     *   3. ytInitialPlayerResponse patch (inline JSON)
     */
    fun getJsInjectScript(): String = """
(function() {
    if (window.__rasAdPrunerInstalled__) return;
    window.__rasAdPrunerInstalled__ = true;

    var AD_FIELDS = [
        'adPlacements','playerAds','adSlots','adBreakHeartbeatParams',
        'auxiliaryUi','adMessagingConfig','adVideoId','adBreakParams',
        'adClientInfoExtension','adCpnExtension','adDurationRemaining',
        'adLayoutLoggingData','adMetadataRenderer','adPlacementConfig',
        'adRendererType','adSurvey','adSystem','adTimeOffset',
        'adVideoDuration','companionData','instreamVideoAdRenderer',
        'linearAdSequenceRenderer','promotedSparkles'
    ];

    function removeFields(obj) {
        if (!obj || typeof obj !== 'object') return;
        AD_FIELDS.forEach(function(f) { delete obj[f]; });
        if (obj.playerResponse) {
            AD_FIELDS.forEach(function(f) { delete obj.playerResponse[f]; });
        }
        if (obj.streamingData) {
            delete obj.streamingData.adTimingDataByAdPodId;
        }
        Object.keys(obj).forEach(function(k) {
            var v = obj[k];
            if (Array.isArray(v)) {
                v.forEach(function(item) { removeFields(item); });
            } else if (v && typeof v === 'object') {
                removeFields(v);
            }
        });
    }

    function pruneJson(text) {
        try {
            var obj = JSON.parse(text);
            removeFields(obj);
            return JSON.stringify(obj);
        } catch(e) { return text; }
    }

    function isPlayerUrl(url) {
        if (!url) return false;
        return url.indexOf('/youtubei/v1/player') !== -1 ||
               url.indexOf('/youtubei/v1/next') !== -1 ||
               url.indexOf('/youtubei/v1/browse') !== -1 ||
               url.indexOf('/youtubei/v1/reel/reel_watch_sequence') !== -1;
    }

    // ── 1. fetch() intercept ──────────────────────────────────────────────────
    var origFetch = window.fetch;
    window.fetch = function(input, init) {
        var url = (typeof input === 'string') ? input : (input && input.url) || '';
        if (!isPlayerUrl(url)) return origFetch.call(this, input, init);
        return origFetch.call(this, input, init).then(function(resp) {
            return resp.clone().text().then(function(text) {
                var pruned = pruneJson(text);
                return new Response(pruned, {
                    status: resp.status, statusText: resp.statusText, headers: resp.headers
                });
            });
        });
    };

    // ── 2. XMLHttpRequest intercept ───────────────────────────────────────────
    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(m, url) {
        this._rasUrl__ = url;
        return origOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
        if (isPlayerUrl(this._rasUrl__)) {
            var xhr = this;
            this.addEventListener('readystatechange', function() {
                if (xhr.readyState === 4) {
                    try {
                        var desc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
                        Object.defineProperty(xhr, 'responseText', {
                            get: function() {
                                var raw = desc ? desc.get.call(xhr) : '';
                                return pruneJson(raw);
                            },
                            configurable: true
                        });
                    } catch(e) {}
                }
            });
        }
        return origSend.apply(this, arguments);
    };

    // ── 3. ytInitialPlayerResponse patch (inline JSON in page HTML) ───────────
    // YouTube page HTML এ window.ytInitialPlayerResponse = {...} inline থাকে।
    // এটাও patch করতে হবে।
    try {
        Object.defineProperty(window, 'ytInitialPlayerResponse', {
            get: function() { return this.__ytIPR__; },
            set: function(val) {
                if (val && typeof val === 'object') removeFields(val);
                this.__ytIPR__ = val;
            },
            configurable: true
        });
    } catch(e) {}

    // ── 4. yt.setConfig / ytcfg patch ────────────────────────────────────────
    // YouTube runtime config এও ad data থাকে
    var origSetConfig = window.yt && window.yt.setConfig;
    if (origSetConfig) {
        window.yt.setConfig = function(cfg) {
            try { removeFields(cfg); } catch(e) {}
            return origSetConfig.call(this, cfg);
        };
    }

    console.log('[RasFocus] YouTubeAdPruner v2 installed — 4 layers active');
})();
""".trimIndent()
}
