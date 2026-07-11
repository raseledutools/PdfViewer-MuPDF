package com.rasel.RasFocus.selfcontrol.familybrowser

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * UserProfileManager.kt
 * Manages up to 5 user profiles with isolated data, Kids mode, and Guest mode.
 *
 * Each profile has:
 * - Isolated bookmarks, history, cookies, settings
 * - Custom avatar (emoji) and display name
 * - Optional PIN protection
 *
 * Special Profiles:
 * - Kids: Whitelist-only, cannot change settings, extra strict adult block
 * - Guest: No history saved, auto-clears on exit
 */

enum class ProfileType { STANDARD, KIDS, GUEST }

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val type: ProfileType = ProfileType.STANDARD,
    var name: String = "User",
    var avatar: String = "😀",  // Emoji avatar
    var pin: String = "",
    var isActive: Boolean = false,
    // Per-profile settings
    var adBlockEnabled: Boolean = true,
    var trackerBlockEnabled: Boolean = true,
    var adultBlockEnabled: Boolean = true,
    var javaScriptEnabled: Boolean = true,
    var darkModeEnabled: Boolean = false,
    var desktopModeEnabled: Boolean = false,
    var searchEngine: SearchEngine = SearchEngine.GOOGLE,
    var homePage: String = "about:blank",
    var youtubeShortsHidden: Boolean = false,
    var autoDeleteOnExit: Boolean = false,
    var kidsWhitelist: MutableSet<String> = mutableSetOf(
        "youtube.com", "khanacademy.org", "pbs.org", "nasa.gov",
        "nationalgeographic.com", "bbc.co.uk/cbbc", "sesamestreet.org",
        "starfall.com", "coolmath-games.com", "funbrain.com"
    ),
    val createdAt: Long = System.currentTimeMillis()
) {
    val isKids: Boolean get() = type == ProfileType.KIDS
    val isGuest: Boolean get() = type == ProfileType.GUEST
    val canChangeSettings: Boolean get() = type != ProfileType.KIDS
}

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    var url: String,
    var title: String,
    var favicon: String = "",   // Base64 encoded
    var folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class BookmarkFolder(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    var name: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

enum class SearchEngine(val displayName: String, val searchUrl: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave", "https://search.brave.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/search?q="),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=")
}

class UserProfileManager(private val context: Context) {

    companion object {
        const val MAX_PROFILES = 5
        const val MAX_HISTORY_ENTRIES = 5000
        private const val PREFS_PROFILES = "user_profiles"
        private const val PREFS_BOOKMARKS = "bookmarks"
        private const val PREFS_HISTORY = "history"
    }

    val profiles = mutableStateListOf<UserProfile>()
    val activeProfile = mutableStateOf<UserProfile?>(null)
    val bookmarks = mutableStateListOf<Bookmark>()
    val bookmarkFolders = mutableStateListOf<BookmarkFolder>()
    val history = mutableStateListOf<HistoryEntry>()

    private val profilePrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE)
    private val bookmarkPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_BOOKMARKS, Context.MODE_PRIVATE)
    private val historyPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)

    init {
        loadProfiles()
        if (profiles.isEmpty()) createDefaultProfile()
        loadBookmarks()
        loadHistory()
    }

    // ─── Profile Management ───────────────────────────────────────────────────

    fun createDefaultProfile() {
        val profile = UserProfile(
            id = "default",
            name = "Me",
            avatar = "😊",
            isActive = true
        )
        profiles.add(profile)
        activeProfile.value = profile
        saveProfiles()
    }

    fun createProfile(
        name: String,
        avatar: String,
        type: ProfileType = ProfileType.STANDARD,
        pin: String = ""
    ): UserProfile? {
        if (profiles.size >= MAX_PROFILES) return null
        val profile = UserProfile(
            name = name,
            avatar = avatar,
            type = type,
            pin = pin,
            adultBlockEnabled = type == ProfileType.KIDS || type == ProfileType.STANDARD
        )
        profiles.add(profile)
        saveProfiles()
        return profile
    }

    fun switchProfile(profileId: String, pin: String = ""): Boolean {
        val profile = profiles.find { it.id == profileId } ?: return false

        // Guest clears on switch-away
        activeProfile.value?.let { current ->
            if (current.isGuest) clearGuestData(current.id)
        }

        // PIN check
        if (profile.pin.isNotEmpty() && profile.pin != pin) return false

        profiles.forEach { it.isActive = it.id == profileId }
        activeProfile.value = profile

        loadBookmarks()
        loadHistory()
        saveProfiles()
        return true
    }

    fun updateProfile(profileId: String, name: String? = null, avatar: String? = null, pin: String? = null) {
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index == -1) return
        val updated = profiles[index].copy(
            name = name ?: profiles[index].name,
            avatar = avatar ?: profiles[index].avatar,
            pin = pin ?: profiles[index].pin
        )
        profiles[index] = updated
        if (activeProfile.value?.id == profileId) activeProfile.value = updated
        saveProfiles()
    }

    /** Update profile by replacing the entire UserProfile object */
    fun updateProfile(updated: UserProfile) {
        val index = profiles.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            profiles[index] = updated
            if (activeProfile.value?.id == updated.id) activeProfile.value = updated
            saveProfiles()
        }
    }

    fun deleteProfile(profileId: String): Boolean {
        if (profileId == "default") return false  // Can't delete default
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index == -1) return false

        profiles.removeAt(index)
        // Clear associated data
        bookmarks.removeAll { it.profileId == profileId }
        history.removeAll { it.profileId == profileId }

        if (activeProfile.value?.id == profileId) {
            switchProfile("default")
        }
        saveProfiles()
        saveBookmarks()
        saveHistory()
        return true
    }

    fun updateProfileSettings(profileId: String, block: UserProfile.() -> Unit) {
        val index = profiles.indexOfFirst { it.id == profileId }
        if (index == -1) return
        profiles[index].block()
        if (activeProfile.value?.id == profileId) activeProfile.value = profiles[index]
        saveProfiles()
    }

    // ─── Bookmarks ────────────────────────────────────────────────────────────

    fun addBookmark(url: String, title: String, folderId: String? = null): Bookmark {
        val profileId = activeProfile.value?.id ?: "default"
        val bookmark = Bookmark(profileId = profileId, url = url, title = title, folderId = folderId)
        bookmarks.add(0, bookmark)
        saveBookmarks()
        return bookmark
    }

    fun removeBookmark(bookmarkId: String) {
        bookmarks.removeAll { it.id == bookmarkId }
        saveBookmarks()
    }

    fun removeBookmarkByUrl(url: String) {
        bookmarks.removeAll { it.url == url }
        saveBookmarks()
    }

    fun isBookmarked(url: String): Boolean {
        val profileId = activeProfile.value?.id ?: return false
        return bookmarks.any { it.profileId == profileId && it.url == url }
    }

    fun getBookmarksForProfile(): List<Bookmark> {
        val profileId = activeProfile.value?.id ?: return emptyList()
        return bookmarks.filter { it.profileId == profileId }
    }

    fun createBookmarkFolder(name: String): BookmarkFolder {
        val profileId = activeProfile.value?.id ?: "default"
        val folder = BookmarkFolder(profileId = profileId, name = name)
        bookmarkFolders.add(folder)
        saveBookmarks()
        return folder
    }

    fun exportBookmarks(): String {
        val profileId = activeProfile.value?.id ?: return ""
        val myBookmarks = bookmarks.filter { it.profileId == profileId }
        return buildString {
            appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
            appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
            appendLine("<TITLE>Bookmarks</TITLE>")
            appendLine("<H1>Bookmarks Menu</H1>")
            appendLine("<DL><p>")
            myBookmarks.forEach { bm ->
                appendLine("    <DT><A HREF=\"${bm.url}\" ADD_DATE=\"${bm.createdAt}\">${bm.title}</A>")
            }
            appendLine("</DL><p>")
        }
    }

    // ─── History ──────────────────────────────────────────────────────────────

    fun addHistoryEntry(url: String, title: String) {
        val profile = activeProfile.value ?: return
        if (profile.isGuest) return  // No history for guest
        if (url == "about:blank" || url.startsWith("data:")) return

        val profileId = profile.id
        // Remove duplicate
        history.removeAll { it.profileId == profileId && it.url == url }
        history.add(0, HistoryEntry(profileId = profileId, url = url, title = title))

        // Trim to max
        while (history.count { it.profileId == profileId } > MAX_HISTORY_ENTRIES) {
            val oldest = history.lastOrNull { it.profileId == profileId }
            if (oldest != null) history.remove(oldest)
        }
        saveHistory()
    }

    fun clearHistory(profileId: String? = null) {
        val id = profileId ?: activeProfile.value?.id ?: return
        history.removeAll { it.profileId == id }
        saveHistory()
    }

    fun searchHistory(query: String): List<HistoryEntry> {
        val profileId = activeProfile.value?.id ?: return emptyList()
        return history.filter {
            it.profileId == profileId &&
            (it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true))
        }
    }

    fun getHistoryForProfile(): List<HistoryEntry> {
        val profileId = activeProfile.value?.id ?: return emptyList()
        return history.filter { it.profileId == profileId }
    }

    // ─── Guest / Kids helpers ─────────────────────────────────────────────────

    private fun clearGuestData(guestProfileId: String) {
        history.removeAll { it.profileId == guestProfileId }
        bookmarks.removeAll { it.profileId == guestProfileId }
        // Clear cookies for guest — handled via CookieManager in WebView layer
    }

    fun getKidsWhitelist(): Set<String> {
        return activeProfile.value?.kidsWhitelist ?: emptySet()
    }

    fun addToKidsWhitelist(domain: String) {
        activeProfile.value?.kidsWhitelist?.add(domain)
        saveProfiles()
    }

    // ─── Search Helpers ───────────────────────────────────────────────────────

    fun buildSearchUrl(query: String): String {
        val engine = activeProfile.value?.searchEngine ?: SearchEngine.GOOGLE
        return engine.searchUrl + android.net.Uri.encode(query)
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> buildSearchUrl(trimmed)
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun saveProfiles() {
        try {
            val arr = JSONArray()
            profiles.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("type", p.type.name)
                    put("name", p.name)
                    put("avatar", p.avatar)
                    put("pin", p.pin)
                    put("isActive", p.isActive)
                    put("adBlock", p.adBlockEnabled)
                    put("trackerBlock", p.trackerBlockEnabled)
                    put("adultBlock", p.adultBlockEnabled)
                    put("js", p.javaScriptEnabled)
                    put("dark", p.darkModeEnabled)
                    put("desktop", p.desktopModeEnabled)
                    put("searchEngine", p.searchEngine.name)
                    put("homePage", p.homePage)
                    put("whitelist", JSONArray(p.kidsWhitelist.toList()))
                    put("createdAt", p.createdAt)
                })
            }
            profilePrefs.edit().putString("profiles", arr.toString()).apply()
        } catch (e: Exception) { /* best-effort */ }
    }

    private fun loadProfiles() {
        try {
            val json = profilePrefs.getString("profiles", null) ?: return
            val arr = JSONArray(json)
            val loaded = mutableListOf<UserProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val whitelist = mutableSetOf<String>()
                val wl = obj.optJSONArray("whitelist")
                if (wl != null) repeat(wl.length()) { whitelist.add(wl.getString(it)) }
                val profile = UserProfile(
                    id = obj.getString("id"),
                    type = ProfileType.valueOf(obj.optString("type", "STANDARD")),
                    name = obj.getString("name"),
                    avatar = obj.getString("avatar"),
                    pin = obj.optString("pin", ""),
                    isActive = obj.optBoolean("isActive", false),
                    adBlockEnabled = obj.optBoolean("adBlock", true),
                    trackerBlockEnabled = obj.optBoolean("trackerBlock", true),
                    adultBlockEnabled = obj.optBoolean("adultBlock", true),
                    javaScriptEnabled = obj.optBoolean("js", true),
                    darkModeEnabled = obj.optBoolean("dark", false),
                    desktopModeEnabled = obj.optBoolean("desktop", false),
                    searchEngine = SearchEngine.valueOf(obj.optString("searchEngine", "GOOGLE")),
                    homePage = obj.optString("homePage", "about:blank"),
                    kidsWhitelist = whitelist,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                loaded.add(profile)
            }
            profiles.addAll(loaded)
            activeProfile.value = profiles.find { it.isActive } ?: profiles.firstOrNull()
        } catch (e: Exception) { /* start fresh */ }
    }

    private fun saveBookmarks() {
        try {
            val arr = JSONArray()
            bookmarks.forEach { bm ->
                arr.put(JSONObject().apply {
                    put("id", bm.id)
                    put("profileId", bm.profileId)
                    put("url", bm.url)
                    put("title", bm.title)
                    put("folderId", bm.folderId ?: "")
                    put("createdAt", bm.createdAt)
                })
            }
            bookmarkPrefs.edit().putString("bookmarks", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun loadBookmarks() {
        bookmarks.clear()
        try {
            val profileId = activeProfile.value?.id ?: return
            val json = bookmarkPrefs.getString("bookmarks", null) ?: return
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("profileId") == profileId) {
                    bookmarks.add(Bookmark(
                        id = obj.getString("id"),
                        profileId = obj.getString("profileId"),
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        folderId = obj.optString("folderId").takeIf { it.isNotEmpty() },
                        createdAt = obj.getLong("createdAt")
                    ))
                }
            }
        } catch (e: Exception) { }
    }

    private fun saveHistory() {
        try {
            val arr = JSONArray()
            history.take(MAX_HISTORY_ENTRIES).forEach { entry ->
                arr.put(JSONObject().apply {
                    put("id", entry.id)
                    put("profileId", entry.profileId)
                    put("url", entry.url)
                    put("title", entry.title)
                    put("visitedAt", entry.visitedAt)
                })
            }
            historyPrefs.edit().putString("history", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun loadHistory() {
        history.clear()
        try {
            val profileId = activeProfile.value?.id ?: return
            val json = historyPrefs.getString("history", null) ?: return
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("profileId") == profileId) {
                    history.add(HistoryEntry(
                        id = obj.getString("id"),
                        profileId = obj.getString("profileId"),
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        visitedAt = obj.getLong("visitedAt")
                    ))
                }
            }
        } catch (e: Exception) { }
    }
}
