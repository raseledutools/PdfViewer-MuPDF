@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rasel.RasFocus.selfcontrol

// ─────────────────────────────────────────────────────────────────────────────
// IMPORTS
// ─────────────────────────────────────────────────────────────────────────────
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

// ─────────────────────────────────────────────────────────────────────────────
// CONSTANTS  (BlockerHero-এর preference key naming follow করা)
// ─────────────────────────────────────────────────────────────────────────────
private const val PREFS_NAME          = "adult_blocker_prefs"
private const val KEY_BLOCKER_ENABLED = "KEY_IS_PORN_BLOCKER_ENABLED"
private const val KEY_KEYWORDS        = "KEY_ADULT_KEYWORDS"   // comma-separated
private const val KEY_DOMAINS         = "KEY_BLOCKED_DOMAINS"  // comma-separated

// ─────────────────────────────────────────────────────────────────────────────
// DEFAULT KEYWORDS & DOMAINS  (testing purpose — code-এর ভেতরেই)
// ─────────────────────────────────────────────────────────────────────────────
private val DEFAULT_KEYWORDS = listOf(
    "porn", "xxx", "sex", "nude", "nsfw",
    "adult content", "18+", "+18", "erotic", "hentai", "onlyfans"
)
private val DEFAULT_DOMAINS = listOf(
    "pornhub.com", "xvideos.com", "xnxx.com",
    "redtube.com", "youporn.com", "onlyfans.com"
)

// ─────────────────────────────────────────────────────────────────────────────
// PACKAGE GROUPS
// ─────────────────────────────────────────────────────────────────────────────

/** YouTube — BlockerHero-এর D4.r static block থেকে */
private val YOUTUBE_PACKAGES = setOf(
    "com.google.android.youtube",
    "com.vanced.android.youtube",
    "app.revanced.android.youtube"
)

/** Facebook */
private val FACEBOOK_PACKAGES = setOf("com.facebook.katana", "com.facebook.lite")

/** Instagram */
private val INSTAGRAM_PACKAGES = setOf("com.instagram.android", "com.instagram.lite")

/**
 * Browser package → URL bar view ID mapping
 * BlockerHero-এ এটা server থেকে আসে (GlobalBlockedItem type=10)।
 * আমরা hardcode করছি testing-এর জন্য।
 * Format: packageName to urlBarViewId
 */
private val BROWSER_URL_BAR_IDS = mapOf(
    "com.android.chrome"                 to "com.android.chrome:id/url_bar",
    "com.chrome.beta"                    to "com.chrome.beta:id/url_bar",
    "org.mozilla.firefox"                to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
    "org.mozilla.firefox_beta"           to "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
    "org.mozilla.rocket"                 to "org.mozilla.rocket:id/url_bar",        // Firefox Lite
    "com.brave.browser"                  to "com.brave.browser:id/url_bar",
    "com.opera.browser"                  to "com.opera.browser:id/url_field",
    "com.opera.mini.native"              to "com.opera.mini.native:id/url_field",
    "com.duckduckgo.mobile.android"      to "com.duckduckgo.mobile.android:id/omnibarTextInput",
    "com.sec.android.app.sbrowser"       to "com.sec.android.app.sbrowser:id/location_bar_edit_text", // Samsung
    "org.torproject.torbrowser"          to "org.torproject.torbrowser:id/mozac_browser_toolbar_url_view",
    "com.ecosia.android"                 to "com.ecosia.android:id/url_bar",
    "com.vivaldi.browser"                to "com.vivaldi.browser:id/url_bar",
    "idm.internet.download.manager"      to "idm.internet.download.manager:id/url_bar",
    "idm.internet.download.manager.plus" to "idm.internet.download.manager.plus:id/url_bar",
    "com.instantbits.cast.webvideo"      to "com.instantbits.cast.webvideo:id/url_bar",
)

/** Phone/call — এগুলো skip করতে হবে */
private val SKIP_PACKAGES = setOf(
    "android",
    "com.android.phone",
    "com.android.incallui",
    "com.android.contacts",
    "com.google.android.contacts",
    "com.samsung.android.contacts",
    "com.samsung.android.app.contacts"
)

// ─────────────────────────────────────────────────────────────────────────────
// PREFERENCES HELPER
// ─────────────────────────────────────────────────────────────────────────────
private fun prefs(ctx: Context): SharedPreferences =
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun isBlockerEnabled(ctx: Context): Boolean =
    prefs(ctx).getBoolean(KEY_BLOCKER_ENABLED, false)

// এই স্ক্রিনের টগল আগে শুধু "adult_blocker_prefs" এ লিখত, যেটা
// UnifiedBlockerService কখনো পড়েই না (ও পড়ে "blocker_prefs", BlockerPrefs
// ক্লাস দিয়ে) — তাই টগল ON করলেও কোনো detection চালু হতো না। এখন থেকে
// একই সাথে সেই আসল keys-ও লিখে দেয়, যাতে toggle সত্যিই কার্যকর হয়।
private fun setBlockerEnabled(ctx: Context, on: Boolean) {
    prefs(ctx).edit().putBoolean(KEY_BLOCKER_ENABLED, on).apply()
    val real = BlockerPrefs(ctx)
    real.blockNormalLoading  = on
    real.blockAdult          = on
    real.blockAdultSiteList  = on
    real.blockAdultImageWeb  = on
}

private fun loadKeywords(ctx: Context): List<String> {
    val raw = prefs(ctx).getString(KEY_KEYWORDS, null)
    return if (raw.isNullOrBlank()) DEFAULT_KEYWORDS
    else raw.split(",").map { it.trim() }.filter { it.length >= 3 }
}

private fun saveKeywords(ctx: Context, list: List<String>) =
    prefs(ctx).edit().putString(KEY_KEYWORDS, list.joinToString(",")).apply()

private fun loadDomains(ctx: Context): List<String> {
    val raw = prefs(ctx).getString(KEY_DOMAINS, null)
    return if (raw.isNullOrBlank()) DEFAULT_DOMAINS
    else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

private fun saveDomains(ctx: Context, list: List<String>) =
    prefs(ctx).edit().putString(KEY_DOMAINS, list.joinToString(",")).apply()

// ─────────────────────────────────────────────────────────────────────────────
// KEYWORD MATCHING ENGINE  (BlockerHero D4.z logic)
// ─────────────────────────────────────────────────────────────────────────────
private val regexCache = ConcurrentHashMap<String, Regex?>()

/**
 * Text normalize — BlockerHero D4.z.d() এর মতো।
 * Special char সরিয়ে lowercase করে।
 */
private fun normalizeText(text: String): String {
    if (text.isEmpty()) return ""
    var t = text.replace("%20", " ").replace("%23", "#")
    val has18Plus = Pattern.compile("18\\s*\\+|\\+18").matcher(t).find()
    val specialPattern = if (has18Plus)
        "[=\"\\[\\]\$%\\-\\\\,_~`;:!?/|^<>&{}()]"
    else
        "[+=\"\\[\\]\$%\\-\\\\,_~`;:!?/|^<>&{}()]"
    t = t.replace(Regex(specialPattern), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase(Locale.ROOT)
    return t
}

/**
 * Keyword থেকে Regex তৈরি — BlockerHero D4.z.e() logic।
 * ৩ অক্ষরের keyword-এ word boundary লাগায়।
 */
private fun buildRegex(keyword: String): Regex? {
    if (keyword.length < 3) return null
    return regexCache.getOrPut(keyword) {
        try {
            val cleaned = keyword.replace("\"", "").replace("\\", "")
            val escaped = Regex.escape(cleaned)
            val pattern = if (keyword.length == 3) "\\b$escaped\\b" else escaped
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) { null }
    }
}

/**
 * Text-এ keyword আছে কিনা দেখে।
 * Match হলে matched keyword return করে, না হলে null।
 */
private fun matchKeyword(text: String, keywords: List<String>): String? {
    if (text.length < 3 || keywords.isEmpty()) return null
    val normalized = normalizeText(text)
    if (normalized.length < 3) return null
    for (kw in keywords) {
        val rx = buildRegex(kw) ?: continue
        if (rx.containsMatchIn(normalized)) return kw
    }
    return null
}

/**
 * URL থেকে domain বের করে blocked domain list-এ আছে কিনা দেখে।
 */
private fun isDomainBlocked(urlText: String, blockedDomains: List<String>): Boolean {
    if (urlText.isBlank()) return false
    return try {
        val url = if (!urlText.startsWith("http")) "https://$urlText" else urlText
        val host = Uri.parse(url).host ?: return false
        blockedDomains.any { d -> host == d || host.endsWith(".$d") }
    } catch (e: Exception) { false }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACCESSIBILITY SERVICE
// ─────────────────────────────────────────────────────────────────────────────
class AdultBlockerService : AccessibilityService() {

    // In-memory keyword/domain lists — event আসলে এখান থেকে check হয়
    private var keywords: List<String> = emptyList()
    private var domains: List<String>  = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        reloadLists()

        // ── Event types ──
        // TYPE_WINDOW_STATE_CHANGED (32)  : নতুন window/activity খুললে — YouTube video open, browser navigate
        // TYPE_WINDOW_CONTENT_CHANGED (2048): page content বদলালে — URL bar update
        // TYPE_VIEW_TEXT_CHANGED (16)     : text field change — browser URL bar type করার সময়ও
        //
        // BlockerHero মূলত যেকোনো event-এ AccessibilityNodeInfo থেকে URL bar text পড়ে।
        // Type করার সময় নয়, page load হওয়ার পর URL bar-এর node text check করে।
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    private fun reloadLists() {
        keywords = loadKeywords(this)
        domains  = loadDomains(this)
    }

    // ── Main event handler ────────────────────────────────────────────────────
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Blocker বন্ধ থাকলে কিছু করি না
        if (!isBlockerEnabled(this)) return

        val pkg = event.packageName?.toString() ?: return

        // Phone/dialer skip — BlockerHero D4.r.j() এর মতো
        if (pkg in SKIP_PACKAGES) return

        // নিজের app skip
        if (pkg == packageName) return

        // Window change হলে list reload করি (user settings change catch করতে)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            reloadLists()
        }

        when {
            // ── YouTube ──
            pkg in YOUTUBE_PACKAGES   -> checkYouTube(event, pkg)

            // ── Facebook ──
            pkg in FACEBOOK_PACKAGES  -> checkFacebook(event, pkg)

            // ── Instagram ──
            pkg in INSTAGRAM_PACKAGES -> checkInstagram(event, pkg)

            // ── Browser ──
            pkg in BROWSER_URL_BAR_IDS -> checkBrowser(event, pkg)

            // ── অন্য সব app ──
            else -> checkGeneral(event, pkg)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLATFORM HANDLERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * YouTube:
     * Video open হলে (TYPE_WINDOW_STATE_CHANGED বা CONTENT_CHANGED) —
     * window-এর visible text (title, description) keyword check করে।
     * BlockerHero-এ D4.r.f() দিয়ে event text + contentDescription + source text collect করে।
     */
    private fun checkYouTube(event: AccessibilityEvent, pkg: String) {
        val texts = collectEventTexts(event)
        for (text in texts) {
            if (matchKeyword(text, keywords) != null) {
                goHome(); return
            }
        }
    }

    /**
     * Facebook:
     * Post বা content visible হলে text check।
     */
    private fun checkFacebook(event: AccessibilityEvent, pkg: String) {
        val texts = collectEventTexts(event)
        for (text in texts) {
            if (matchKeyword(text, keywords) != null) {
                goHome(); return
            }
        }
    }

    /**
     * Instagram:
     * Content visible হলে text check।
     */
    private fun checkInstagram(event: AccessibilityEvent, pkg: String) {
        val texts = collectEventTexts(event)
        for (text in texts) {
            if (matchKeyword(text, keywords) != null) {
                goHome(); return
            }
        }
    }

    /**
     * Browser:
     * BlockerHero-এর আসল logic —
     * URL bar node সরাসরি পড়ে (event text নয়)।
     * Page navigate হলে URL bar-এর AccessibilityNodeInfo.getText() = current URL।
     * সেখান থেকে domain check ও keyword check।
     *
     * BlockerHero code (MyAccessibilityService):
     *   val urlBarNode = D4.r.e(event.source, browserDetails.urlBarId)
     *   if (urlBarNode != null) D4.r.b(this, urlBarNode, isYoutube)
     *
     * D4.r.b() reads urlBarNode.getText() directly.
     */
    private fun checkBrowser(event: AccessibilityEvent, pkg: String) {
        val urlBarId   = BROWSER_URL_BAR_IDS[pkg] ?: return
        val sourceNode = event.source ?: return

        // URL bar node খোঁজো — view ID দিয়ে
        val urlBarNode = findNodeById(sourceNode, urlBarId) ?: return

        // URL bar-এর current text — এটাই loaded URL
        val urlText = urlBarNode.text?.toString()?.trim() ?: return
        if (urlText.length < 3) return

        // ১. Domain block check
        if (isDomainBlocked(urlText, domains)) {
            goHome(); return
        }

        // ২. URL-এ keyword আছে কিনা (যেমন URL path-এ "porn" লেখা)
        if (matchKeyword(urlText, keywords) != null) {
            goHome(); return
        }

        // ৩. Page-এর visible content-এ keyword check (loaded page-এর title ইত্যাদি)
        val pageTexts = collectEventTexts(event)
        for (text in pageTexts) {
            if (text.length < 3 || text.length > 300) continue
            if (matchKeyword(text, keywords) != null) {
                goHome(); return
            }
        }
    }

    /**
     * General (অন্য সব app):
     * Visible text/title check।
     * BlockerHero এখানে TextView/EditText class দেখে text read করে।
     */
    private fun checkGeneral(event: AccessibilityEvent, pkg: String) {
        val texts = collectEventTexts(event)
        for (text in texts) {
            if (text.length < 3 || text.length > 300) continue
            if (matchKeyword(text, keywords) != null) {
                goHome(); return
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Event থেকে সব relevant text collect করে।
     * BlockerHero D4.r.f() এর মতো:
     *   - event.getText()
     *   - event.contentDescription
     *   - event.source.getText()
     *   - event.source.contentDescription
     */
    private fun collectEventTexts(event: AccessibilityEvent): List<String> {
        val result = mutableListOf<String>()
        event.text?.forEach { it?.let { t -> result.add(t.toString()) } }
        event.contentDescription?.let { result.add(it.toString()) }
        event.source?.let { node ->
            node.text?.let { result.add(it.toString()) }
            node.contentDescription?.let { result.add(it.toString()) }
        }
        return result.filter { it.isNotBlank() }
    }

    /**
     * View ID দিয়ে AccessibilityNodeInfo খোঁজে।
     * BlockerHero D4.r.e() এর মতো।
     */
    private fun findNodeById(
        root: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? {
        return try {
            root.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
        } catch (e: Exception) { null }
    }

    /**
     * HOME button press — block action।
     * BlockerHero: performGlobalAction(1)
     */
    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {}
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AdultBlockerScreen() } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSE UI
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdultBlockerScreen() {
    val ctx = LocalContext.current

    var isEnabled       by remember { mutableStateOf(isBlockerEnabled(ctx)) }
    var accessOn        by remember { mutableStateOf(isAccessibilityOn(ctx)) }
    var kwInput         by remember { mutableStateOf("") }
    var domainInput     by remember { mutableStateOf("") }
    var kwList          by remember { mutableStateOf(loadKeywords(ctx)) }
    var domainList      by remember { mutableStateOf(loadDomains(ctx)) }

    // Screen-এ ফিরলে accessibility status re-check
    LaunchedEffect(Unit) {
        accessOn = isAccessibilityOn(ctx)
        // আগে টগল ON করা থাকলেও real prefs sync হয়নি এমন পুরনো state ঠিক করে দেয়,
        // যাতে fix-এর আগে যারা toggle করেছিল তাদের আবার toggle করতে না হয়।
        if (isEnabled) setBlockerEnabled(ctx, true)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Adult Blocker", fontWeight = FontWeight.Bold) }) }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Accessibility status ──
            item {
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (accessOn)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (accessOn) "✅ Accessibility চালু" else "❌ Accessibility বন্ধ",
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!accessOn)
                                Text("Settings থেকে চালু করতে হবে", fontSize = 12.sp)
                        }
                        if (!accessOn)
                            Button(onClick = {
                                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text("চালু করো") }
                    }
                }
            }

            // ── Main toggle — KEY_IS_PORN_BLOCKER_ENABLED ──
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Adult Content Block", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                if (isEnabled) "চালু — সব platform monitor হচ্ছে"
                                else "বন্ধ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { v ->
                                isEnabled = v
                                setBlockerEnabled(ctx, v)
                            }
                        )
                    }
                }
            }

            // ── Keywords ──
            item { SectionLabel("🔑 Adult Keywords") }
            item {
                InputRow(
                    value = kwInput,
                    onChange = { kwInput = it },
                    placeholder = "keyword (min 3 char)",
                    onAdd = {
                        val kw = kwInput.trim().lowercase()
                        if (kw.length >= 3 && kw !in kwList) {
                            val updated = kwList + kw
                            kwList = updated
                            saveKeywords(ctx, updated)
                        }
                        kwInput = ""
                    }
                )
            }
            items(kwList) { kw ->
                ItemRow(label = kw, onDelete = {
                    val updated = kwList - kw
                    kwList = updated
                    saveKeywords(ctx, updated)
                })
            }

            // ── Domains ──
            item { Spacer(Modifier.height(2.dp)); SectionLabel("🌐 Blocked Domains") }
            item {
                InputRow(
                    value = domainInput,
                    onChange = { domainInput = it },
                    placeholder = "example.com",
                    onAdd = {
                        val d = domainInput.trim().lowercase()
                        if (d.isNotBlank() && d !in domainList) {
                            val updated = domainList + d
                            domainList = updated
                            saveDomains(ctx, updated)
                        }
                        domainInput = ""
                    }
                )
            }
            items(domainList) { d ->
                ItemRow(label = d, onDelete = {
                    val updated = domainList - d
                    domainList = updated
                    saveDomains(ctx, updated)
                })
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Small composables ─────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) =
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun InputRow(value: String, onChange: (String) -> Unit, placeholder: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier.weight(1f), singleLine = true
        )
        Button(onClick = onAdd, enabled = value.trim().length >= 3) { Text("Add") }
    }
}

@Composable
private fun ItemRow(label: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp)
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UTILITY
// ─────────────────────────────────────────────────────────────────────────────
private fun isAccessibilityOn(ctx: Context): Boolean {
    return try {
        val services = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        // আগে শুধু packageName আছে কিনা দেখত — UnifiedBlockerService ON থাকলেই
        // (এই স্ক্রিনের ফিচার আদৌ সংযুক্ত না থাকলেও) "✅ চালু" দেখাত। এখন সঠিক
        // component name মিলিয়ে দেখে, একই check যেটা MainActivity ব্যবহার করে।
        services.contains("${ctx.packageName}/${ctx.packageName}.selfcontrol.UnifiedBlockerService", ignoreCase = true) ||
        services.contains("UnifiedBlockerService", ignoreCase = true)
    } catch (e: Exception) { false }
}

// ─────────────────────────────────────────────────────────────────────────────
// PUBLIC ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Adult_block() {
    AdultBlockerScreen()
}
