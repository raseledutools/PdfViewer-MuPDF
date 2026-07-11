// ============================================================
// FirebaseKeywordSync.kt  — v4 (Fixed: explicit DB URL, singleValueEvent, robust array parse)
// Firebase Realtime Database থেকে তিন ধরনের data load করে:
//
//  1. Adult block
//     keyword_data/
//       adult_keywords:   ["porn", "xxx", ...]
//       adult_domains:    ["pornhub.com", ...]
//       allowed_keywords: ["sex education", ...]  ← whitelist
//
//  2. Custom block
//     keyword_data/
//       custom_blocks/
//         keywords: ["bet365", "gambling", ...]
//         domains:  ["bet365.com", "1xbet.com", ...]
//         apps:     ["com.betway.android", ...]
//
//  3. Custom messages
//     keyword_data/
//       custom_blocks/
//         app_messages/
//           com.betway.android: "Betting app blocked for focus"
//         domain_messages/
//           bet365.com: "Gambling site blocked"
//         keyword_messages/
//           gambling: "Gambling content blocked"
// ============================================================

package com.rasel.pdfviewer.combo.selfcontrol

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject

object FirebaseKeywordSync {

    private const val TAG        = "FBKeywordSync"
    private const val DB_URL     = "https://rasfocus-c746d-default-rtdb.firebaseio.com"
    private const val DB_PATH    = "keyword_data"
    private const val CACHE_PREFS = "fb_keyword_cache_v3"

    // ── Cache keys ──────────────────────────────────────────
    private const val KEY_ADULT_KW     = "cached_adult_keywords"
    private const val KEY_ADULT_DM     = "cached_adult_domains"
    private const val KEY_ADULT_TLD    = "cached_adult_tlds"
    private const val KEY_ALLOWED      = "cached_allowed"
    private const val KEY_CUSTOM_KW    = "cached_custom_keywords"
    private const val KEY_CUSTOM_DM    = "cached_custom_domains"
    private const val KEY_CUSTOM_AP    = "cached_custom_apps"
    private const val KEY_APP_MSGS     = "cached_app_messages"
    private const val KEY_DOMAIN_MSGS  = "cached_domain_messages"
    private const val KEY_KEYWORD_MSGS = "cached_keyword_messages"

    // ── In-memory — adult ────────────────────────────────────
    @Volatile private var remoteKeywords:  Set<String> = emptySet()
    @Volatile private var remoteDomains:   Set<String> = emptySet()
    @Volatile private var remoteTlds:      Set<String> = emptySet()
    @Volatile private var allowedKeywords: Set<String> = emptySet()

    // ── In-memory — custom block ─────────────────────────────
    @Volatile var customKeywords: Set<String> = emptySet()
        private set
    @Volatile var customDomains:  Set<String> = emptySet()
        private set
    @Volatile var customApps:     Set<String> = emptySet()
        private set

    // ── In-memory — custom messages ──────────────────────────
    @Volatile var appMessages:     Map<String, String> = emptyMap()
        private set
    @Volatile var domainMessages:  Map<String, String> = emptyMap()
        private set
    @Volatile var keywordMessages: Map<String, String> = emptyMap()
        private set

    @Volatile var isLoaded: Boolean = false

    private var appContext: Context? = null

    // ── FIX: listener reference রেখে দাও যাতে forceRefresh এ detach করা যায় ──
    private var activeListener: ValueEventListener? = null

    // ────────────────────────────────────────────────────────
    // init — service start এ একবার call করো
    // ────────────────────────────────────────────────────────
    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromCache()      // offline/restart এ আগের data তাৎক্ষণিক দেয়
        fetchFromFirebase()  // background এ fresh data নিয়ে আসো
    }

    // ────────────────────────────────────────────────────────
    // Adult block check
    // ────────────────────────────────────────────────────────
    fun isBlockedByRemote(rawText: String, host: String): Boolean {
        if (!isLoaded && remoteKeywords.isEmpty() && remoteDomains.isEmpty()) return false
        if (allowedKeywords.any { rawText.contains(it) }) return false
        if (host.isNotEmpty()) {
            if (remoteDomains.contains(host)) return true
            if (remoteDomains.any { host.endsWith(".$it") }) return true
        }
        if (remoteKeywords.any { rawText.contains(it) }) return true
        return false
    }

    // ────────────────────────────────────────────────────────
    // Public read-only accessors — RasBrowser / YouTube-in-app /
    // Facebook-in-app search-bar keyword scanners all read from
    // here now instead of keeping their own hardcoded lists.
    // ────────────────────────────────────────────────────────
    fun getAdultKeywords(): Set<String>  = remoteKeywords
    fun getAdultDomains(): Set<String>   = remoteDomains
    fun getAdultTlds(): Set<String>      = remoteTlds
    fun getAllowedKeywords(): Set<String> = allowedKeywords

    /** True if [text] (a URL, search query, or page title) contains a blocked adult keyword. */
    fun containsAdultKeyword(text: String): Boolean {
        if (text.isEmpty()) return false
        val lower = text.lowercase()
        if (allowedKeywords.any { it.isNotBlank() && lower.contains(it) }) return false
        return remoteKeywords.any { it.isNotBlank() && lower.contains(it) }
    }

    /** True if [host] is (or is a subdomain of) a blocked adult domain, or ends with a blocked TLD. */
    fun isAdultHost(host: String): Boolean {
        if (host.isEmpty()) return false
        val h = host.lowercase()
        if (remoteTlds.any { h.endsWith(it) }) return true
        return remoteDomains.any { h == it || h.endsWith(".$it") }
    }

    // ────────────────────────────────────────────────────────
    // Custom block checks
    // ────────────────────────────────────────────────────────
    fun isCustomBlocked(rawText: String, host: String): Boolean {
        if (customKeywords.isEmpty() && customDomains.isEmpty()) return false
        if (host.isNotEmpty()) {
            if (customDomains.contains(host)) return true
            if (customDomains.any { host.endsWith(".$it") }) return true
        }
        if (customKeywords.any { rawText.contains(it) }) return true
        return false
    }

    fun isCustomBlockedApp(packageName: String): Boolean {
        if (customApps.isEmpty()) return false
        return customApps.contains(packageName.lowercase())
    }

    fun getMatchedCustomKeyword(rawText: String): String? {
        if (customKeywords.isEmpty()) return null
        return customKeywords.firstOrNull { rawText.contains(it) }
    }

    // ────────────────────────────────────────────────────────
    // Message getters — Firebase message না থাকলে default দেয়
    // ────────────────────────────────────────────────────────
    fun getAppBlockMessage(packageName: String, default: String = "This app is blocked by admin."): String {
        return appMessages[packageName.lowercase()]?.takeIf { it.isNotBlank() } ?: default
    }

    fun getDomainBlockMessage(host: String, default: String = "This website is blocked by admin."): String {
        val exact = domainMessages[host.lowercase()]
        if (!exact.isNullOrBlank()) return exact
        val parent = host.substringAfter(".", "")
        val parentMsg = domainMessages[parent.lowercase()]
        if (!parentMsg.isNullOrBlank()) return parentMsg
        return default
    }

    fun getKeywordBlockMessage(rawText: String, default: String = "Blocked content detected."): String {
        val matchedKw = customKeywords.firstOrNull { rawText.contains(it) } ?: return default
        return keywordMessages[matchedKw]?.takeIf { it.isNotBlank() } ?: default
    }

    // ────────────────────────────────────────────────────────
    // Firebase fetch
    // FIX (this pass): addListenerForSingleValueEvent → addValueEventListener
    //
    // The single-value-event approach fetches Firebase data EXACTLY ONCE and
    // never updates again on its own — the code comment even said "data
    // change হলে forceRefresh call করো" (call forceRefresh() when data
    // changes), but nothing in the app ever actually calls forceRefresh()
    // automatically. So updating a keyword in the Firebase console had zero
    // effect until the app process was killed and restarted.
    //
    // addValueEventListener is Firebase Realtime Database's actual live-sync
    // listener — it keeps firing automatically every time the data at this
    // path changes, which is the whole point of "Realtime" Database and
    // exactly what "update keyword in Firebase, app picks it up right away"
    // needs. The earlier switch away from it was reasoning about a listener
    // *leak* — that's a real concern for a listener registered from a
    // short-lived Activity/Fragment that needs to detach on destroy, but
    // this is a permanent, app-process-lifetime singleton object with no
    // such lifecycle to leak past; there's nothing to leak here.
    // ────────────────────────────────────────────────────────
    private fun fetchFromFirebase() {
        try {
            val db  = FirebaseDatabase.getInstance(DB_URL)
            db.setPersistenceEnabled(false) // cache আমরা নিজে করি, Firebase disk cache off
            val ref = db.getReference(DB_PATH)

            // পুরনো listener থাকলে আগে detach করো (forceRefresh() একাধিকবার
            // call হলে duplicate listener জমে যাওয়া ঠেকাতে)
            activeListener?.let { ref.removeEventListener(it) }

            val listener = object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.w(TAG, "Firebase '$DB_PATH' empty or missing")
                        return
                    }

                    // ── Adult ──
                    val keywords = parseStringList(snapshot.child("adult_keywords"))
                    val domains  = parseStringList(snapshot.child("adult_domains"))
                    val tlds     = parseStringList(snapshot.child("adult_tlds"))
                    val allowed  = parseStringList(snapshot.child("allowed_keywords"))

                    remoteKeywords  = keywords.toSet()
                    remoteDomains   = domains.toSet()
                    remoteTlds      = tlds.toSet()
                    allowedKeywords = allowed.toSet()

                    // ── Custom block ──
                    val customSnap = snapshot.child("custom_blocks")
                    val ckw = parseStringList(customSnap.child("keywords"))
                    val cdm = parseStringList(customSnap.child("domains"))
                    val cap = parseStringList(customSnap.child("apps"))

                    customKeywords = ckw.toSet()
                    customDomains  = cdm.toSet()
                    customApps     = cap.toSet()

                    // ── Custom messages ──
                    val appMsgs     = parseStringMap(customSnap.child("app_messages"))
                    val domainMsgs  = parseStringMap(customSnap.child("domain_messages"))
                    val keywordMsgs = parseStringMap(customSnap.child("keyword_messages"))

                    appMessages     = appMsgs
                    domainMessages  = domainMsgs
                    keywordMessages = keywordMsgs

                    isLoaded = true

                    Log.d(TAG, "✅ Live update — Adult: ${keywords.size}kw ${domains.size}dm | " +
                               "Custom: ${ckw.size}kw ${cdm.size}dm ${cap.size}apps | " +
                               "Messages: ${appMsgs.size}app ${domainMsgs.size}domain ${keywordMsgs.size}kw")

                    saveToCache(
                        remoteKeywords, remoteDomains, remoteTlds, allowedKeywords,
                        customKeywords, customDomains, customApps,
                        appMessages, domainMessages, keywordMessages
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase cancelled: ${error.message} (code=${error.code})")
                }
            }

            activeListener = listener
            ref.addValueEventListener(listener)

        } catch (e: Exception) {
            Log.e(TAG, "fetchFromFirebase error: ${e.message}")
        }
    }

    // ────────────────────────────────────────────────────────
    // Parse helpers
    // FIX 3: দুই format support — Firebase array (map style) এবং typed List
    // ────────────────────────────────────────────────────────
    private fun parseStringList(snap: DataSnapshot): List<String> {
        val result = mutableListOf<String>()
        if (!snap.exists()) return result

        // Format A: typed List<String> — getValue দিয়ে সরাসরি নাও
        try {
            @Suppress("UNCHECKED_CAST")
            val typed = snap.getValue(List::class.java) as? List<*>
            if (typed != null) {
                typed.forEach { v ->
                    val s = v?.toString()?.trim()?.lowercase()
                    if (!s.isNullOrEmpty()) result.add(s)
                }
                return result
            }
        } catch (_: Exception) {}

        // Format B: map/object style — {"0":"porn","1":"xxx"} বা named keys
        for (child in snap.children) {
            val v = child.getValue(String::class.java)?.trim()?.lowercase()
            if (!v.isNullOrEmpty()) result.add(v)
        }
        return result
    }

    private fun parseStringMap(snap: DataSnapshot): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!snap.exists()) return result
        for (child in snap.children) {
            val k = child.key?.trim()?.lowercase() ?: continue
            val v = child.getValue(String::class.java)?.trim() ?: continue
            if (k.isNotEmpty() && v.isNotEmpty()) result[k] = v
        }
        return result
    }

    // ────────────────────────────────────────────────────────
    // JSON disk cache
    // ────────────────────────────────────────────────────────
    private fun setToJsonArrayString(set: Set<String>): String {
        val array = JSONArray()
        set.forEach { array.put(it) }
        return array.toString()
    }

    private fun mapToJsonObjectString(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun jsonArrayToSet(jsonString: String?): Set<String> {
        if (jsonString.isNullOrBlank()) return emptySet()
        val set = mutableSetOf<String>()
        return try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) set.add(array.getString(i))
            set
        } catch (e: Exception) {
            Log.e(TAG, "JSONArray parse error: ${e.message}")
            emptySet()
        }
    }

    private fun jsonObjectToMap(jsonString: String?): Map<String, String> {
        if (jsonString.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        return try {
            val obj = JSONObject(jsonString)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
            map
        } catch (e: Exception) {
            Log.e(TAG, "JSONObject parse error: ${e.message}")
            emptyMap()
        }
    }

    private fun saveToCache(
        kw: Set<String>, dm: Set<String>, tld: Set<String>, al: Set<String>,
        ckw: Set<String>, cdm: Set<String>, cap: Set<String>,
        appMsgs: Map<String, String>,
        domainMsgs: Map<String, String>,
        keywordMsgs: Map<String, String>
    ) {
        val ctx = appContext ?: return
        try {
            ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ADULT_KW,      setToJsonArrayString(kw))
                .putString(KEY_ADULT_DM,      setToJsonArrayString(dm))
                .putString(KEY_ADULT_TLD,     setToJsonArrayString(tld))
                .putString(KEY_ALLOWED,       setToJsonArrayString(al))
                .putString(KEY_CUSTOM_KW,     setToJsonArrayString(ckw))
                .putString(KEY_CUSTOM_DM,     setToJsonArrayString(cdm))
                .putString(KEY_CUSTOM_AP,     setToJsonArrayString(cap))
                .putString(KEY_APP_MSGS,      mapToJsonObjectString(appMsgs))
                .putString(KEY_DOMAIN_MSGS,   mapToJsonObjectString(domainMsgs))
                .putString(KEY_KEYWORD_MSGS,  mapToJsonObjectString(keywordMsgs))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Cache save failed: ${e.message}")
        }
    }

    private fun loadFromCache() {
        val ctx = appContext ?: return
        try {
            val p = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

            val kw  = jsonArrayToSet(p.getString(KEY_ADULT_KW, null))
            val dm  = jsonArrayToSet(p.getString(KEY_ADULT_DM, null))
            val tld = jsonArrayToSet(p.getString(KEY_ADULT_TLD, null))
            val al  = jsonArrayToSet(p.getString(KEY_ALLOWED, null))
            val ckw = jsonArrayToSet(p.getString(KEY_CUSTOM_KW, null))
            val cdm = jsonArrayToSet(p.getString(KEY_CUSTOM_DM, null))
            val cap = jsonArrayToSet(p.getString(KEY_CUSTOM_AP, null))
            val ams = jsonObjectToMap(p.getString(KEY_APP_MSGS, null))
            val dms = jsonObjectToMap(p.getString(KEY_DOMAIN_MSGS, null))
            val kms = jsonObjectToMap(p.getString(KEY_KEYWORD_MSGS, null))

            if (kw.isNotEmpty() || dm.isNotEmpty() || ckw.isNotEmpty() || cdm.isNotEmpty() || cap.isNotEmpty()) {
                remoteKeywords  = kw
                remoteDomains   = dm
                remoteTlds      = tld
                allowedKeywords = al
                customKeywords  = ckw
                customDomains   = cdm
                customApps      = cap
                appMessages     = ams
                domainMessages  = dms
                keywordMessages = kms
                isLoaded = true
                Log.d(TAG, "📦 Cache loaded — adult ${kw.size}kw ${dm.size}dm | " +
                           "custom ${ckw.size}kw ${cdm.size}dm ${cap.size}apps | " +
                           "msgs: ${ams.size}app ${dms.size}domain ${kms.size}kw")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache load failed: ${e.message}")
        }
    }

    // ── Manual refresh (পুরনো cache clear করে নতুন fetch করে) ──
    fun forceRefresh() {
        isLoaded = false
        fetchFromFirebase()
    }

    // ── Cleanup listeners ──
    fun cleanup() {
        try {
            activeListener?.let {
                FirebaseDatabase.getInstance(DB_URL)
                    .getReference(DB_PATH)
                    .removeEventListener(it)
            }
        } catch (_: Exception) {}
        activeListener = null
    }
}
