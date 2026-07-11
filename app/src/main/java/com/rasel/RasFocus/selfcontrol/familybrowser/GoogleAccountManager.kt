package com.rasel.RasFocus.selfcontrol.familybrowser

/**
 * GoogleAccountManager.kt
 * ─────────────────────────────────────────────────────────────────────────────
 * Google Sign-In integration for the browser header avatar.
 *
 * Features:
 *  - Multiple Google accounts (add/remove/switch)
 *  - Profile photo URL fetched from Google OAuth
 *  - Persistent storage via SharedPreferences (JSON)
 *  - Works with Credential Manager API (Android 14+) and legacy GoogleSignIn
 *
 * HOW TO SET UP (one-time):
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. Go to https://console.cloud.google.com
 * 2. Create an OAuth 2.0 Client ID → Android (package: com.rasel.RasFocus…)
 * 3. Add SHA-1 fingerprint of your keystore (debug + release)
 * 4. In build.gradle (app):
 *      implementation("com.google.android.gms:play-services-auth:21.2.0")
 *      implementation("androidx.credentials:credentials:1.3.0")
 *      implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
 *      implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
 *      implementation("io.coil-kt:coil-compose:2.6.0")   // photo loading
 *
 * REQUIRED PERMISSIONS (AndroidManifest.xml):
 *   <uses-permission android:name="android.permission.GET_ACCOUNTS"/>
 *
 * REQUIRED STRINGS (strings.xml):
 *   <string name="default_web_client_id">YOUR_CLIENT_ID.apps.googleusercontent.com</string>
 *
 * BUILD.GRADLE (app-level) — additional dependencies:
 *   implementation("com.google.android.gms:play-services-auth:21.2.0")
 *   implementation("io.coil-kt:coil-compose:2.6.0")
 * ─────────────────────────────────────────────────────────────────────────────
 */

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

// ── Data model ────────────────────────────────────────────────────────────────

data class GoogleAccount(
    val id: String,              // Google sub (unique user ID)
    val email: String,
    val displayName: String,
    val photoUrl: String?,       // HTTPS URL to profile photo
    val isActive: Boolean = false
)

// ── Manager ───────────────────────────────────────────────────────────────────

class GoogleAccountManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("google_accounts_prefs", Context.MODE_PRIVATE)

    // ── Observable state ──────────────────────────────────────────────────────
    var accounts by mutableStateOf<List<GoogleAccount>>(emptyList())
        private set

    val activeAccount: GoogleAccount?
        get() = accounts.firstOrNull { it.isActive }

    val isSignedIn: Boolean
        get() = accounts.isNotEmpty()

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        loadFromPrefs()
    }

    // ── Add / update an account (called after successful OAuth) ───────────────

    fun addOrUpdateAccount(
        id: String,
        email: String,
        displayName: String,
        photoUrl: String?
    ) {
        val existing = accounts.find { it.id == id }
        val updated = if (existing != null) {
            existing.copy(
                email       = email,
                displayName = displayName,
                photoUrl    = photoUrl,
                isActive    = true
            )
        } else {
            GoogleAccount(
                id          = id,
                email       = email,
                displayName = displayName,
                photoUrl    = photoUrl,
                isActive    = true
            )
        }

        // Deactivate all others, activate the new/updated one
        accounts = accounts
            .filter { it.id != id }
            .map { it.copy(isActive = false) }
            .plus(updated)
            .sortedByDescending { it.isActive }

        saveToPrefs()
    }

    // ── Switch active account ─────────────────────────────────────────────────

    fun switchTo(id: String) {
        accounts = accounts.map { it.copy(isActive = it.id == id) }
        saveToPrefs()
    }

    // ── Remove an account ─────────────────────────────────────────────────────

    fun removeAccount(id: String) {
        val wasCurrent = accounts.find { it.id == id }?.isActive == true
        accounts = accounts.filter { it.id != id }

        // If we removed the active account, activate the first remaining one
        if (wasCurrent && accounts.isNotEmpty()) {
            accounts = listOf(accounts.first().copy(isActive = true)) +
                       accounts.drop(1).map { it.copy(isActive = false) }
        }
        saveToPrefs()
    }

    // ── Sign out all ──────────────────────────────────────────────────────────

    fun signOutAll() {
        accounts = emptyList()
        prefs.edit().remove("accounts_json").apply()
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveToPrefs() {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(JSONObject().apply {
                put("id",          acc.id)
                put("email",       acc.email)
                put("displayName", acc.displayName)
                put("photoUrl",    acc.photoUrl ?: "")
                put("isActive",    acc.isActive)
            })
        }
        prefs.edit().putString("accounts_json", arr.toString()).apply()
    }

    private fun loadFromPrefs() {
        val json = prefs.getString("accounts_json", null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<GoogleAccount>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    GoogleAccount(
                        id          = obj.getString("id"),
                        email       = obj.getString("email"),
                        displayName = obj.getString("displayName"),
                        photoUrl    = obj.getString("photoUrl").ifEmpty { null },
                        isActive    = obj.getBoolean("isActive")
                    )
                )
            }
            accounts = list
        } catch (_: Exception) {
            accounts = emptyList()
        }
    }

    // ── Initials fallback (shown when no photo URL) ───────────────────────────

    fun initialsFor(account: GoogleAccount): String {
        val parts = account.displayName.trim().split(" ")
        return if (parts.size >= 2) {
            "${parts.first().first()}${parts.last().first()}".uppercase()
        } else {
            account.displayName.take(2).uppercase()
        }
    }
}
