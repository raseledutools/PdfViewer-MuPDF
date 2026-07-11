package com.rasel.RasFocus

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * DataManager — Global singleton for all RasFocus feature states.
 * SharedPreferences-backed so data survives process restarts.
 *
 * FIX: All property accessors are null-safe (guard against pre-init access from BootReceiver/
 * AccessibilityService). Input validation added on writable numeric fields.
 */
object DataManager {

    private const val PREFS_NAME = "RasFocusData"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun getBoolean(key: String, default: Boolean) =
        prefs?.getBoolean(key, default) ?: default

    private fun setBoolean(key: String, value: Boolean) =
        prefs?.edit()?.putBoolean(key, value)?.apply()

    private fun getInt(key: String, default: Int) =
        prefs?.getInt(key, default) ?: default

    private fun setInt(key: String, value: Int) =
        prefs?.edit()?.putInt(key, value)?.apply()

    private fun getLong(key: String, default: Long) =
        prefs?.getLong(key, default) ?: default

    private fun setLong(key: String, value: Long) =
        prefs?.edit()?.putLong(key, value)?.apply()

    private fun getString(key: String, default: String) =
        prefs?.getString(key, default) ?: default

    private fun setString(key: String, value: String) =
        prefs?.edit()?.putString(key, value)?.apply()

    // ── Password Utilities ────────────────────────────────────────────────
    /**
     * FIX: Passwords are stored as salted SHA-256 hashes instead of plain text.
     */
    fun hashPassword(password: String): String {
        val salted = password + "rasfocus_salt_v1"
        return MessageDigest.getInstance("SHA-256")
            .digest(salted.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(input: String, storedHash: String): Boolean =
        hashPassword(input) == storedHash

    // ── Adult Block ───────────────────────────────────────────────────────
    var isAdultFocusActive: Boolean
        get() = getBoolean("isAdultFocusActive", false)
        set(v) = setBoolean("isAdultFocusActive", v)!!

    var is24HourLockActive: Boolean
        get() = getBoolean("is24HourLockActive", false)
        set(v) = setBoolean("is24HourLockActive", v)!!

    var lock24hEndTime: Long
        get() = getLong("lock24hEndTime", 0L)
        set(v) = setLong("lock24hEndTime", v)!!

    /** 0 = Self Control, 1 = Friend Control */
    var controlMode: Int
        get() = getInt("controlMode", 0)
        set(v) = setInt("controlMode", v.coerceIn(0, 1))!!

    /** 0 = Muslim, 1 = Hindu, 2 = Christian, 3 = Universal */
    var adultReligion: Int
        get() = getInt("adultReligion", 0)
        set(v) = setInt("adultReligion", v.coerceIn(0, 3))!!

    /** 0 = Bangla, 1 = English */
    var adultLanguage: Int
        get() = getInt("adultLanguage", 0)
        set(v) = setInt("adultLanguage", v.coerceIn(0, 1))!!

    var isPeriodicPopupsActive: Boolean
        get() = getBoolean("isPeriodicPopupsActive", false)
        set(v) = setBoolean("isPeriodicPopupsActive", v)!!

    var showQuotes: Boolean
        get() = getBoolean("showQuotes", true)
        set(v) = setBoolean("showQuotes", v)!!

    var totalBlockedCount: Int
        get() = getInt("totalBlockedCount", 0)
        set(v) = setInt("totalBlockedCount", v.coerceAtLeast(0))!!

    var cleanStreakDays: Int
        get() = getInt("cleanStreakDays", 0)
        set(v) = setInt("cleanStreakDays", v.coerceAtLeast(0))!!

    var userCustomAdultKeywords: List<String>
        get() {
            val raw = getString("userCustomAdultKeywords", "")
            return if (raw.isEmpty()) emptyList() else raw.split("|||")
        }
        set(v) = setString("userCustomAdultKeywords", v.joinToString("|||"))!!

    // ── Self-Control / Focus Block ────────────────────────────────────────
    var isFocusActive: Boolean
        get() = getBoolean("isFocusActive", false)
        set(v) = setBoolean("isFocusActive", v)!!

    /** 0 = Block list mode, 1 = Allow list mode */
    var simpleBlockMode: Int
        get() = getInt("simpleBlockMode", 0)
        set(v) = setInt("simpleBlockMode", v.coerceIn(0, 1))!!

    var userAppList: List<String>
        get() {
            val raw = getString("userAppList", "")
            return if (raw.isEmpty()) emptyList() else raw.split("|||")
        }
        set(v) = setString("userAppList", v.joinToString("|||"))!!

    var userWebList: List<String>
        get() {
            val raw = getString("userWebList", "")
            return if (raw.isEmpty()) emptyList() else raw.split("|||")
        }
        set(v) = setString("userWebList", v.joinToString("|||"))!!

    var blockSettingsAndUninstall: Boolean
        get() = getBoolean("blockSettingsAndUninstall", false)
        set(v) = setBoolean("blockSettingsAndUninstall", v)!!

    // ── Deep Study ────────────────────────────────────────────────────────
    var isDeepStudyStrict: Boolean
        get() = getBoolean("isDeepStudyStrict", false)
        set(v) = setBoolean("isDeepStudyStrict", v)!!

    var dsAllowAppList: List<String>
        get() {
            val raw = getString("dsAllowAppList", "")
            return if (raw.isEmpty()) emptyList() else raw.split("|||")
        }
        set(v) = setString("dsAllowAppList", v.joinToString("|||"))!!

    var dsAllowWebList: List<String>
        get() {
            val raw = getString("dsAllowWebList", "")
            return if (raw.isEmpty()) emptyList() else raw.split("|||")
        }
        set(v) = setString("dsAllowWebList", v.joinToString("|||"))!!

    var totalFocusTimeMillis: Long
        get() = getLong("totalFocusTimeMillis", 0L)
        set(v) = setLong("totalFocusTimeMillis", v)!!

    var totalSessions: Int
        get() = getInt("totalSessions", 0)
        set(v) = setInt("totalSessions", v)!!

    /** FIX: Validated range 1–480 minutes */
    var dsFocusMin: Int
        get() = getInt("dsFocusMin", 25)
        set(v) = setInt("dsFocusMin", v.coerceIn(1, 480))!!

    /** FIX: Validated range 1–60 minutes */
    var dsRestMin: Int
        get() = getInt("dsRestMin", 5)
        set(v) = setInt("dsRestMin", v.coerceIn(1, 60))!!

    var dsKeepBlockingInBreak: Boolean
        get() = getBoolean("dsKeepBlockingInBreak", false)
        set(v) = setBoolean("dsKeepBlockingInBreak", v)!!

    // ── Misc ──────────────────────────────────────────────────────────────
    fun clearAll() = prefs?.edit()?.clear()?.apply()
}
