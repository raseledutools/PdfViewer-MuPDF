package com.rasel.RasFocus.selfcontrol.familybrowser

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Home button press হলে কী হবে সেটার setting।
 * FLOAT_YOUTUBE  = শুধু YouTube হলে floating (default, আগের behaviour)
 * FLOAT_ANY_TAB  = যেকোনো tab floating হবে
 * NO_FLOAT       = কোনো floating নেই, সরাসরি home/minimize
 */
enum class HomeFloatMode {
    FLOAT_YOUTUBE,   // শুধু YouTube → floating
    FLOAT_ANY_TAB,   // যেকোনো tab → floating
    NO_FLOAT         // কোনো floating নেই
}

/**
 * BrowserViewModel.kt
 * Central ViewModel — owns all browser state and orchestrates managers.
 *
 * Managed state:
 * - Active WebView reference (for imperative calls)
 * - UI overlay state (tab switcher, menu, settings, etc.)
 * - Address bar input
 * - Search suggestions
 * - Loading / progress
 * - Find-in-page
 * - Dark mode injection
 * - Desktop mode UA
 * - Privacy stats (trackers/ads blocked)
 */

data class SearchSuggestion(val text: String, val type: SuggestionType)
enum class SuggestionType { SEARCH, URL, HISTORY, BOOKMARK }

enum class BrowserScreen {
    BROWSER, TAB_SWITCHER, BOOKMARKS, HISTORY, SETTINGS,
    DOWNLOADS, PROFILES, READER_MODE, FIND_IN_PAGE
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    val context: Context get() = getApplication<Application>()

    // ─── Prefs ────────────────────────────────────────────────────────────────
    private val browserPrefs: SharedPreferences =
        application.getSharedPreferences("family_browser_prefs", Context.MODE_PRIVATE)

    // ─── Home Float Mode ──────────────────────────────────────────────────────
    @get:JvmName("getHomeFloatMode")
    @set:JvmName("setHomeFloatModeInternal")
    var homeFloatMode by mutableStateOf(
        HomeFloatMode.valueOf(
            browserPrefs.getString("home_float_mode", HomeFloatMode.FLOAT_YOUTUBE.name)
                ?: HomeFloatMode.FLOAT_YOUTUBE.name
        )
    )

    fun setHomeFloatMode(mode: HomeFloatMode) {
        homeFloatMode = mode
        browserPrefs.edit().putString("home_float_mode", mode.name).apply()
    }

    // ─── Managers ─────────────────────────────────────────────────────────────
    val tabManager = TabManager(context)
    val profileManager = UserProfileManager(context)
    val adBlocker = AdBlocker(context)
    val downloadManager = BrowserDownloadManager(context)

    // ─── WebView Reference ────────────────────────────────────────────────────
    // Keyed by tab ID so each tab has its own WebView
    private val webViews = mutableMapOf<String, WebView>()

    fun registerWebView(tabId: String, webView: WebView) {
        webViews[tabId] = webView
    }

    fun unregisterWebView(tabId: String) {
        webViews.remove(tabId)
    }

    val activeWebView: WebView?
        get() = tabManager.activeTab?.let { webViews[it.id] }

    // ─── UI State ─────────────────────────────────────────────────────────────
    var currentScreen by mutableStateOf(BrowserScreen.BROWSER)
    var isAddressBarFocused by mutableStateOf(false)
    var addressBarText by mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))

    var showTabSwitcher by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var showSettingsPanel by mutableStateOf(false)
    var showProfileSwitcher by mutableStateOf(false)
    var showAdultUnlockDialog by mutableStateOf(false)
    var showBookmarkDialog by mutableStateOf(false)
    var showDownloads by mutableStateOf(false)
    var isFullscreen   by mutableStateOf(false)
    // Chrome-style: scroll down → nav buttons hide, scroll up → show
    var showNavButtons by mutableStateOf(true)
    var isReaderMode by mutableStateOf(false)
    var isFindInPage by mutableStateOf(false)
    var isPipMode    by mutableStateOf(false)      // PiP mode active কিনা
    var floatingTabId by mutableStateOf<String?>(null) // কোন tab floating এ আছে

    // ── In-App MiniPlayer state ───────────────────────────────────────────────
    // Back press করলে YouTube video কোণায় ছোট হয়ে যায়, browser scroll করা যায়
    var isMiniPlayer by mutableStateOf(false)
    var miniPlayerTitle by mutableStateOf("")
    var findQuery by mutableStateOf("")
    var findResultCount by mutableStateOf(0)
    var findCurrentIndex by mutableStateOf(0)

    // ─── Loading State ────────────────────────────────────────────────────────
    var isLoading by mutableStateOf(false)
    var loadProgress by mutableStateOf(0)
    var pageTitle by mutableStateOf("")
    var currentUrl by mutableStateOf("")
    var isSecureConnection by mutableStateOf(false)

    // ─── Suggestions ──────────────────────────────────────────────────────────
    var suggestions by mutableStateOf<List<SearchSuggestion>>(emptyList())

    // ─── Privacy Stats ────────────────────────────────────────────────────────
    var adsBlockedCount by mutableStateOf(0)
    var trackersBlockedCount by mutableStateOf(0)

    init {
        // FIX: activeProfile observe করো — profile null থাকলে বা পরে load হলেও sync হবে।
        // shouldInterceptRequest-এও per-request sync করা হয়েছে (MainActivity fix),
        // এটা extra safety হিসেবে রাখা।
        viewModelScope.launch {
            snapshotFlow { profileManager.activeProfile.value }
                .collect { profile ->
                    if (profile != null) {
                        adBlocker.isAdBlockEnabled = profile.adBlockEnabled
                        adBlocker.isTrackerBlockEnabled = profile.trackerBlockEnabled
                        adBlocker.isAdultBlockEnabled = profile.adultBlockEnabled
                    }
                }
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    fun navigate(input: String) {
        val url = profileManager.normalizeUrl(input)
        addressBarText = androidx.compose.ui.text.input.TextFieldValue(url)
        isAddressBarFocused = false
        suggestions = emptyList()

        val tab = tabManager.activeTab
        if (tab != null) {
            tabManager.updateTabUrl(tab.id, url)
            activeWebView?.loadUrl(url)
        } else {
            tabManager.addNewTab(url = url)
        }
    }

    fun goBack() {
        if (activeWebView?.canGoBack() == true) activeWebView?.goBack()
    }

    fun goForward() {
        if (activeWebView?.canGoForward() == true) activeWebView?.goForward()
    }

    fun reload() {
        activeWebView?.reload()
    }

    fun stopLoading() {
        activeWebView?.stopLoading()
    }

    fun goHome() {
        // FIX: আগে এখানে buildHomePage() এর raw HTML সরাসরি navigate() এ পাঠানো হতো,
        // আর navigate() সেটাকে normalizeUrl() দিয়ে "URL" ভেবে normalize করতে গিয়ে
        // পুরো HTML টেক্সটকে Google search query বানিয়ে loadUrl() করতো!
        // ফলে real home page (speed-dial বাটনগুলো) আসলে কখনোই render হতো না —
        // এজন্যই "home page এর বাটন কাজ করে না"। এখন HTML কে সরাসরি
        // loadDataWithBaseURL দিয়ে লোড করা হচ্ছে (buildNewTabPage() এর মতোই)।
        val homePage = profileManager.activeProfile.value?.homePage ?: "about:blank"
        if (homePage == "about:blank") {
            val html = buildHomePage()
            val tab = tabManager.activeTab
            isAddressBarFocused = false
            suggestions = emptyList()
            if (tab != null) {
                tabManager.updateTabUrl(tab.id, "about:blank")
                activeWebView?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            } else {
                tabManager.addNewTab(url = "about:blank")
            }
        } else {
            navigate(homePage)
        }
    }

    // ─── Tab Operations ───────────────────────────────────────────────────────

    fun openNewTab(url: String = "about:blank", incognito: Boolean = false, background: Boolean = false) {
        val profile = profileManager.activeProfile.value
        tabManager.addNewTab(
            url = url,
            incognito = incognito,
            profileId = profile?.id ?: "default",
            background = background
        )
        if (!background) {
            showTabSwitcher = false
            isAddressBarFocused = !url.contains("about:blank")
        }
    }

    fun closeTab(tabId: String) {
        tabManager.closeTab(tabId)
        if (tabManager.tabs.isEmpty()) openNewTab()
    }

    fun switchToTab(tabId: String) {
        tabManager.switchToTab(tabId)
        showTabSwitcher = false
        val tab = tabManager.activeTab
        if (tab != null) {
            currentUrl = tab.url
            addressBarText = androidx.compose.ui.text.input.TextFieldValue(tab.url)
            pageTitle = tab.title
        }
    }

    // ─── Address Bar ──────────────────────────────────────────────────────────

    fun onAddressBarFocused() {
        isAddressBarFocused = true
        // Chrome এর মতো — focus করলে পুরো URL select হয়
        val url = if (currentUrl.startsWith("about:")) "" else currentUrl
        addressBarText = androidx.compose.ui.text.input.TextFieldValue(
            text      = url,
            selection = androidx.compose.ui.text.TextRange(0, url.length)
        )
    }

    fun onAddressBarTextChanged(text: androidx.compose.ui.text.input.TextFieldValue) {
        addressBarText = text
        updateSuggestions(text.text)
    }

    fun onAddressBarSubmitted() {
        navigate(addressBarText.text)
    }

    fun onAddressBarDismissed() {
        isAddressBarFocused = false
        val url = if (currentUrl.startsWith("about:")) "" else currentUrl
        addressBarText = androidx.compose.ui.text.input.TextFieldValue(url)
        suggestions = emptyList()
    }

    private fun updateSuggestions(query: String) {
        if (query.length < 2) { suggestions = emptyList(); return }
        viewModelScope.launch(Dispatchers.Default) {
            val results = mutableListOf<SearchSuggestion>()

            // History matches
            profileManager.searchHistory(query)
                .take(3)
                .forEach { results.add(SearchSuggestion(it.url, SuggestionType.HISTORY)) }

            // Bookmark matches
            profileManager.getBookmarksForProfile()
                .filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
                .take(2)
                .forEach { results.add(SearchSuggestion(it.title, SuggestionType.BOOKMARK)) }

            // URL suggestion
            if (query.contains(".") && !query.contains(" ")) {
                results.add(0, SearchSuggestion("https://$query", SuggestionType.URL))
            }

            // Search suggestion
            results.add(SearchSuggestion(query, SuggestionType.SEARCH))

            suggestions = results.take(6)
        }
    }

    // ─── Page Events (from WebView callbacks) ─────────────────────────────────

    fun onPageStarted(tabId: String, url: String) {
        tabManager.updateTabUrl(tabId, url)
        tabManager.updateTabLoading(tabId, true, 0)
        if (tabManager.activeTab?.id == tabId) {
            currentUrl = url
            // focused না থাকলেই update করো — user টাইপ করতে থাকলে interrupt করব না
            if (!isAddressBarFocused) {
                addressBarText = androidx.compose.ui.text.input.TextFieldValue(url)
            }
            isLoading = true
            loadProgress = 0
            isSecureConnection = url.startsWith("https://")
        }
    }

    fun onPageFinished(tabId: String, url: String) {
        tabManager.updateTabLoading(tabId, false, 100)
        if (tabManager.activeTab?.id == tabId) {
            isLoading = false
            loadProgress = 100
            profileManager.addHistoryEntry(url, pageTitle)
        }

        // Sync block counts
        adsBlockedCount = adBlocker.adBlockCount
        trackersBlockedCount = adBlocker.trackerBlockCount

        // Inject dark mode CSS if enabled
        injectDarkModeIfNeeded()
    }

    fun onPageTitleChanged(tabId: String, title: String) {
        tabManager.updateTabTitle(tabId, title)
        if (tabManager.activeTab?.id == tabId) pageTitle = title
    }

    fun onProgressChanged(tabId: String, progress: Int) {
        tabManager.updateTabLoading(tabId, progress < 100, progress)
        if (tabManager.activeTab?.id == tabId) {
            loadProgress = progress
            isLoading = progress < 100
        }
    }

    fun onNavStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        tabManager.updateTabNavState(tabId, canGoBack, canGoForward)
    }

    // ─── Features ─────────────────────────────────────────────────────────────

    fun toggleBookmark() {
        val url = currentUrl.ifEmpty { return }
        if (profileManager.isBookmarked(url)) {
            val bm = profileManager.getBookmarksForProfile().find { it.url == url }
            bm?.let { profileManager.removeBookmark(it.id) }
        } else {
            profileManager.addBookmark(url, pageTitle.ifEmpty { url })
            showBookmarkDialog = true
            viewModelScope.launch {
                delay(2000)
                showBookmarkDialog = false
            }
        }
    }

    fun toggleFindInPage() {
        isFindInPage = !isFindInPage
        if (!isFindInPage) {
            activeWebView?.clearMatches()
            findQuery = ""
        }
    }

    fun findNext(query: String) {
        findQuery = query
        activeWebView?.findAllAsync(query)
        activeWebView?.findNext(true)
    }

    fun findPrevious() {
        activeWebView?.findNext(false)
    }

    fun toggleReaderMode() {
        isReaderMode = !isReaderMode
        if (isReaderMode) {
            injectReaderMode()
        } else {
            activeWebView?.reload()
        }
    }

    fun toggleDesktopMode() {
        val profile = profileManager.activeProfile.value ?: return
        val newValue = !profile.desktopModeEnabled
        profileManager.updateProfileSettings(profile.id) { desktopModeEnabled = newValue }

        val ua = if (newValue) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        activeWebView?.settings?.userAgentString = ua
        activeWebView?.reload()
    }

    fun toggleDarkMode() {
        val profile = profileManager.activeProfile.value ?: return
        profileManager.updateProfileSettings(profile.id) { darkModeEnabled = !darkModeEnabled }
        injectDarkModeIfNeeded()
    }

    fun shareCurrentPage() {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, currentUrl)
            putExtra(android.content.Intent.EXTRA_SUBJECT, pageTitle)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share").apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun takeFullPageScreenshot(callback: (android.graphics.Bitmap?) -> Unit) {
        val wv = activeWebView ?: return callback(null)
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(
                wv.width, wv.contentHeight, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            wv.draw(canvas)
            callback(bitmap)
        } catch (e: Exception) { callback(null) }
    }

    // ─── JavaScript Injection ─────────────────────────────────────────────────

    private fun injectDarkModeIfNeeded() {
        val enabled = profileManager.activeProfile.value?.darkModeEnabled ?: return
        if (!enabled) return

        val css = """
            :root { filter: invert(1) hue-rotate(180deg) !important; }
            img, video, canvas, svg, [style*="background-image"] {
                filter: invert(1) hue-rotate(180deg) !important;
            }
        """.trimIndent().replace("\n", " ")

        val js = """
            (function() {
                var id = '__familybrowser_dark';
                var el = document.getElementById(id);
                if (el) return;
                el = document.createElement('style');
                el.id = id;
                el.textContent = '$css';
                document.head.appendChild(el);
            })();
        """.trimIndent()

        activeWebView?.evaluateJavascript(js, null)
    }

    private fun injectReaderMode() {
        val js = """
            (function() {
                var body = document.body;
                var article = document.querySelector('article') ||
                              document.querySelector('[role="main"]') ||
                              document.querySelector('.content') ||
                              document.querySelector('.post') ||
                              body;
                var content = article ? article.innerHTML : body.innerHTML;
                document.body.innerHTML = '<div style="max-width:680px;margin:0 auto;padding:24px 16px;font-family:Georgia,serif;font-size:20px;line-height:1.7;color:#222;background:#FFFDF7;">' +
                    '<style>img{max-width:100%;height:auto;border-radius:8px;}h1,h2,h3{font-family:-apple-system,sans-serif;line-height:1.3;}a{color:#1a73e8;}</style>' +
                    content + '</div>';
            })();
        """.trimIndent()
        activeWebView?.evaluateJavascript(js, null)
    }


    // ─── YouTube Shorts Block ────────────────────────────────────────────────
    fun applyYoutubeShortsBlock(enabled: Boolean) {
        val js = if (enabled) """
            (function() {
                var style = document.getElementById('__rfb_shorts_block');
                if (!style) {
                    style = document.createElement('style');
                    style.id = '__rfb_shorts_block';
                    document.head.appendChild(style);
                }
                style.textContent = 'ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts], ' +
                    '[overlay-style=SHORTS], ytd-shorts, [page-subtype=shorts], ' +
                    'a[href*="/shorts/"] { display: none !important; }';
            })();
        """.trimIndent() else """
            (function() {
                var s = document.getElementById('__rfb_shorts_block');
                if (s) s.remove();
            })();
        """.trimIndent()
        activeWebView?.evaluateJavascript(js, null)
    }

    // ─── Dark Mode WebView ───────────────────────────────────────────────────
    fun applyDarkModeToWebView(enable: Boolean) {
        val wv = activeWebView ?: return
        val js = if (enable) """
            (function() {
                var id = '__rfb_dark_mode';
                var el = document.getElementById(id);
                if (el) return;
                el = document.createElement('style');
                el.id = id;
                el.textContent = ':root { filter: invert(1) hue-rotate(180deg) !important; } img, video, canvas, svg { filter: invert(1) hue-rotate(180deg) !important; }';
                document.head.appendChild(el);
            })();
        """.trimIndent() else """
            (function() {
                var el = document.getElementById('__rfb_dark_mode');
                if (el) el.remove();
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    // ─── Clear All Browsing Data ─────────────────────────────────────────────
    fun clearAllBrowsingData(context: android.content.Context) {
        activeWebView?.clearCache(true)
        activeWebView?.clearHistory()
        activeWebView?.clearFormData()
        profileManager.clearHistory()
        android.webkit.CookieManager.getInstance().removeSessionCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    fun syncAdBlockerWithProfile() {
        val profile = profileManager.activeProfile.value ?: return
        adBlocker.isAdBlockEnabled = profile.adBlockEnabled
        adBlocker.isTrackerBlockEnabled = profile.trackerBlockEnabled
        adBlocker.isAdultBlockEnabled = profile.adultBlockEnabled
    }

    // ─── Home Page ────────────────────────────────────────────────────────────

    private fun buildHomePage(): String {
        val profile = profileManager.activeProfile.value
        val greeting = profile?.name?.let { "Hello, $it" } ?: "Welcome"
        val speedDials = listOf(
            "Google" to "https://google.com",
            "YouTube" to "https://youtube.com",
            "Wikipedia" to "https://wikipedia.org",
            "Reddit" to "https://reddit.com",
            "GitHub" to "https://github.com",
            "News" to "https://bbc.com/news"
        )
        val dials = speedDials.joinToString("") { (name, url) ->
            """<a href="$url" class="dial"><div class="icon">${name[0]}</div><div class="label">$name</div></a>"""
        }
        return """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>New Tab</title>
            <style>
              * { margin:0; padding:0; box-sizing:border-box; }
              body { font-family:-apple-system,sans-serif; background:linear-gradient(135deg,#667eea,#764ba2);
                     min-height:100vh; display:flex; flex-direction:column;
                     align-items:center; justify-content:center; padding:24px; }
              h1 { color:white; font-size:28px; font-weight:300; margin-bottom:32px; opacity:0.9; }
              .dials { display:grid; grid-template-columns:repeat(3,1fr); gap:16px; max-width:360px; width:100%; }
              .dial { display:flex; flex-direction:column; align-items:center; gap:8px;
                      text-decoration:none; padding:16px 8px; background:rgba(255,255,255,0.15);
                      border-radius:16px; backdrop-filter:blur(10px); transition:transform 0.2s; }
              .dial:active { transform:scale(0.95); }
              .icon { width:48px; height:48px; background:white; border-radius:12px;
                      display:flex; align-items:center; justify-content:center;
                      font-size:22px; font-weight:700; color:#764ba2; }
              .label { color:white; font-size:12px; font-weight:500; }
            </style></head><body>
            <h1>$greeting</h1>
            <div class="dials">$dials</div>
            </body></html>
        """.trimIndent()
    }

    override fun onCleared() {
        super.onCleared()
        adBlocker.saveSettings()
        downloadManager.cleanup()
    }
}
