package com.rasel.pdfviewer.combo.selfcontrol.familybrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * TabManager.kt
 * Manages browser tabs — open, close, switch, restore, thumbnails.
 *
 * Features:
 * - Unlimited tabs
 * - Thumbnail previews (compressed)
 * - Tab restore (recently closed)
 * - Incognito tabs (auto-cleared)
 * - Tab state persistence across app restarts
 */

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = "default",
    var url: String = "about:blank",
    var title: String = "New Tab",
    var favicon: Bitmap? = null,
    var thumbnail: Bitmap? = null,
    var isIncognito: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    // ── WebView Virtualization ─────────────────────────────────────
    // Tab "suspend" হলে (memory বাঁচাতে WebView.destroy() করা হয়) navigation
    // history এখানে সেভ থাকে; ফেরত এলে restoreState() দিয়ে আগের অবস্থায়
    // ফিরে আসে — user টের পায় না যে WebView নতুন করে বানানো হয়েছে।
    var savedState: android.os.Bundle? = null,
    // Live WebView গুলোর মধ্যে কোনটা সবচেয়ে পুরনো (LRU) বের করার জন্য।
    var lastAccessedAt: Long = System.currentTimeMillis(),
    // এই tab এর WebView এখন actual memory তে "live" আছে কিনা।
    var isSuspended: Boolean = false
)

data class ClosedTab(
    val tab: BrowserTab,
    val closedAt: Long = System.currentTimeMillis()
)

class TabManager(private val context: Context) {

    companion object {
        const val MAX_CLOSED_TABS = 10
        const val MAX_TABS = 100
        // একসাথে সর্বোচ্চ এই কয়টা tab এর WebView memory তে "live" থাকবে
        // (active + সাম্প্রতিক ব্যবহৃত কয়েকটা)। বাকিগুলো suspend হয়ে যাবে —
        // এতে ১০-১০০টা ট্যাব খুললেও RAM/CPU বাড়ে না, Chrome যেমন করে।
        const val MAX_LIVE_WEBVIEWS = 4
        const val THUMBNAIL_WIDTH = 320
        const val THUMBNAIL_HEIGHT = 200
        const val THUMBNAIL_QUALITY = 60
        private const val PREFS_NAME = "tab_manager"
    }

    // ─── State ────────────────────────────────────────────────────────────────
    val tabs = mutableStateListOf<BrowserTab>()
    val closedTabs = mutableStateListOf<ClosedTab>()
    val activeTabIndex = mutableStateOf(0)

    val activeTab: BrowserTab?
        get() = tabs.getOrNull(activeTabIndex.value)

    val tabCount: Int get() = tabs.size
    val regularTabCount: Int get() = tabs.count { !it.isIncognito }
    val incognitoTabCount: Int get() = tabs.count { it.isIncognito }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        restoreTabsFromDisk()
        if (tabs.isEmpty()) addNewTab()
    }

    // ─── WebView Virtualization Helpers ────────────────────────────────────────

    /**
     * activeTabId + সাম্প্রতিক ব্যবহৃত (lastAccessedAt অনুযায়ী) মিলিয়ে
     * MAX_LIVE_WEBVIEWS সংখ্যক tab id রিটার্ন করে — এগুলোর WebView live থাকবে।
     * বাকি সব tab suspend করার জন্য candidate।
     */
    fun idsThatShouldStayLive(activeTabId: String?): Set<String> {
        val sorted = tabs.sortedByDescending { it.lastAccessedAt }.map { it.id }
        val result = LinkedHashSet<String>()
        if (activeTabId != null) result.add(activeTabId)
        for (id in sorted) {
            if (result.size >= MAX_LIVE_WEBVIEWS) break
            result.add(id)
        }
        return result
    }

    fun touchTab(tabId: String) {
        tabs.find { it.id == tabId }?.lastAccessedAt = System.currentTimeMillis()
    }

    // ─── Tab Operations ───────────────────────────────────────────────────────

    fun addNewTab(
        url: String = "about:blank",
        incognito: Boolean = false,
        profileId: String = "default",
        background: Boolean = false
    ): BrowserTab {
        val tab = BrowserTab(
            url = url,
            isIncognito = incognito,
            profileId = profileId
        )
        tabs.add(tab)
        if (!background) {
            activeTabIndex.value = tabs.size - 1
        }
        persistTabs()
        return tab
    }

    fun closeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val tab = tabs[index]
        if (!tab.isIncognito) {
            closedTabs.add(0, ClosedTab(tab))
            if (closedTabs.size > MAX_CLOSED_TABS) {
                closedTabs.removeAt(closedTabs.size - 1)
            }
        }

        tabs.removeAt(index)

        if (tabs.isEmpty()) {
            addNewTab()
        } else {
            activeTabIndex.value = when {
                index < tabs.size -> index
                else -> tabs.size - 1
            }
        }
        persistTabs()
    }

    fun closeAllTabs(incognitoOnly: Boolean = false) {
        if (incognitoOnly) {
            tabs.removeAll { it.isIncognito }
        } else {
            // Save to closed tabs (non-incognito)
            tabs.filter { !it.isIncognito }.forEach { tab ->
                closedTabs.add(0, ClosedTab(tab))
            }
            tabs.clear()
        }

        if (tabs.isEmpty()) addNewTab()
        else activeTabIndex.value = minOf(activeTabIndex.value, tabs.size - 1)
        persistTabs()
    }

    fun restoreClosedTab(): BrowserTab? {
        val closed = closedTabs.removeFirstOrNull() ?: return null
        val tab = closed.tab.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis()
        )
        tabs.add(tab)
        activeTabIndex.value = tabs.size - 1
        persistTabs()
        return tab
    }

    fun switchToTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index != -1) activeTabIndex.value = index
    }

    fun switchToTab(index: Int) {
        if (index in tabs.indices) activeTabIndex.value = index
    }

    fun duplicateTab(tabId: String) {
        val source = tabs.find { it.id == tabId } ?: return
        val newTab = source.copy(
            id = UUID.randomUUID().toString(),
            thumbnail = null,
            createdAt = System.currentTimeMillis()
        )
        val sourceIndex = tabs.indexOfFirst { it.id == tabId }
        tabs.add(sourceIndex + 1, newTab)
        activeTabIndex.value = sourceIndex + 1
        persistTabs()
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in tabs.indices || toIndex !in tabs.indices) return
        val tab = tabs.removeAt(fromIndex)
        tabs.add(toIndex, tab)
        activeTabIndex.value = toIndex
    }

    // ─── Tab State Updates ────────────────────────────────────────────────────

    fun updateTabUrl(tabId: String, url: String) {
        updateTab(tabId) { it.copy(url = url) }
    }

    fun updateTabTitle(tabId: String, title: String) {
        updateTab(tabId) { it.copy(title = title.ifBlank { "New Tab" }) }
    }

    fun updateTabFavicon(tabId: String, favicon: Bitmap?) {
        updateTab(tabId) { it.copy(favicon = favicon) }
    }

    fun updateTabThumbnail(tabId: String, bitmap: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(bitmap, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true)
        updateTab(tabId) { it.copy(thumbnail = scaled) }
    }

    fun updateTabLoading(tabId: String, isLoading: Boolean, progress: Int = 0) {
        updateTab(tabId) { it.copy(isLoading = isLoading, progress = progress) }
    }

    fun updateTabNavState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            tabs[index] = transform(tabs[index])
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun persistTabs() {
        try {
            val arr = JSONArray()
            tabs.filter { !it.isIncognito }.forEach { tab ->
                arr.put(JSONObject().apply {
                    put("id", tab.id)
                    put("profileId", tab.profileId)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("createdAt", tab.createdAt)
                    tab.thumbnail?.let {
                        val stream = ByteArrayOutputStream()
                        it.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, stream)
                        put("thumbnail", Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT))
                    }
                })
            }
            prefs.edit()
                .putString("tabs", arr.toString())
                .putInt("activeIndex", activeTabIndex.value)
                .apply()
        } catch (e: Exception) {
            // Silent fail — tab persistence is best-effort
        }
    }

    private fun restoreTabsFromDisk() {
        try {
            val json = prefs.getString("tabs", null) ?: return
            val arr = JSONArray(json)
            val restored = mutableListOf<BrowserTab>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val thumb = obj.optString("thumbnail").takeIf { it.isNotEmpty() }?.let {
                    val bytes = Base64.decode(it, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                restored.add(BrowserTab(
                    id = obj.getString("id"),
                    profileId = obj.optString("profileId", "default"),
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    thumbnail = thumb,
                    createdAt = obj.getLong("createdAt")
                ))
            }
            if (restored.isNotEmpty()) {
                tabs.addAll(restored)
                activeTabIndex.value = prefs.getInt("activeIndex", 0)
                    .coerceIn(0, tabs.size - 1)
            }
        } catch (e: Exception) {
            // Start fresh if restore fails
        }
    }
}
