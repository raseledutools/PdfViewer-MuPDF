package com.rasel.pdfviewer.combo.selfcontrol

// ============================================================
//  SharedProfileViewModel.kt
//  নতুন ফাইল — চারটা screen একই profiles data share করে।
//  Profiles SharedPreferences-এ JSON হিসেবে save হয়।
//  FocusProfile save হলে BlockedData-তেও sync হয়।
// ============================================================

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ─────────────────────────────────────────────────────────────────────────────
// SharedProfileViewModel — Application-scoped হওয়া উচিত
// MainActivity-তে: viewModel<SharedProfileViewModel>() দিয়ে পাবে,
// এবং navController দিয়ে সব composable-এ pass করবে।
// ─────────────────────────────────────────────────────────────────────────────

class SharedProfileViewModel(private val appContext: Context) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("rasfocus_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _profiles = MutableStateFlow<List<FocusProfile>>(emptyList())
    val profiles: StateFlow<List<FocusProfile>> = _profiles.asStateFlow()

    init {
        loadFromPrefs()
    }

    // ── Load ─────────────────────────────────────────────────────────────────
    private fun loadFromPrefs() {
        val json = prefs.getString("profiles_json", null)
        if (json != null) {
            val type = object : TypeToken<List<FocusProfile>>() {}.type
            val loaded: List<FocusProfile> = gson.fromJson(json, type) ?: emptyList()
            _profiles.value = loaded
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────
    private fun saveToPrefs(list: List<FocusProfile>) {
        prefs.edit().putString("profiles_json", gson.toJson(list)).apply()
    }

    // ── Add or Update ─────────────────────────────────────────────────────────
    fun addOrUpdateProfile(profile: FocusProfile) {
        _profiles.update { current ->
            val exists = current.any { it.id == profile.id }
            val newProfile = if (profile.id.isEmpty())
                profile.copy(id = System.currentTimeMillis().toString())
            else profile

            val updated = if (exists)
                current.map { if (it.id == newProfile.id) newProfile else it }
            else
                current + newProfile

            saveToPrefs(updated)
            // Sync active profile → BlockedData so Accessibility Service can read it
            if (newProfile.isActive) syncToBlockedData(newProfile)
            updated
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    fun deleteProfile(id: String) {
        _profiles.update { current ->
            val updated = current.filter { it.id != id }
            saveToPrefs(updated)
            updated
        }
    }

    // ── Toggle Active ─────────────────────────────────────────────────────────
    fun toggleProfile(id: String) {
        _profiles.update { current ->
            val updated = current.map { profile ->
                if (profile.id == id) {
                    val toggled = profile.copy(isActive = !profile.isActive)
                    if (toggled.isActive) syncToBlockedData(toggled)
                    else clearFromBlockedData(profile)
                    toggled
                } else profile
            }
            saveToPrefs(updated)
            updated
        }
    }

    // ── Sync profile → BlockedData (singel_apps.kt) ───────────────────────────
    // FIX: Profile save হলে Accessibility Service যে BlockedData পড়ে সেখানেও লেখা হয়
    private fun syncToBlockedData(profile: FocusProfile) {
        val defaultCfg = BlockConfig(
            method   = when (profile.lockMode) {
                "parents"  -> BlockMethod.PASSWORD
                "longtext" -> BlockMethod.LONG_TEXT
                else       -> BlockMethod.TIME_RANGE
            },
            timeFrom = "${profile.startHour.toString().padStart(2,'0')}:${profile.startMin.toString().padStart(2,'0')}",
            timeTo   = "${profile.endHour.toString().padStart(2,'0')}:${profile.endMin.toString().padStart(2,'0')}",
            longText = "To unlock this device, you must realize that focus is the key to success..."
        )

        // Block all apps from profile's blockedApps list
        profile.blockedApps.forEach { pkg ->
            BlockedData.blockApp(appContext, pkg, defaultCfg)
        }

        // Block all websites from profile's blockedWebsites list
        profile.blockedWebsites.forEach { site ->
            BlockedData.blockSite(appContext, site, defaultCfg)
        }

        // Sync quick-block flags → reels_shorts.kt BlockPrefs
        val blockPrefs = BlockPrefs(appContext)
        blockPrefs.set(Key.BLOCK_YT_SHORTS, profile.quickBlockYtShorts)
        blockPrefs.set(Key.BLOCK_IG_REELS,  profile.quickBlockIgReels)
        blockPrefs.set(Key.BLOCK_FACEBOOK,  profile.quickBlockFbReels)

        // Strict mode
        blockPrefs.set(Key.STRICT_MODE, profile.blockUninstall)

        // ─── Fix 2: প্রোফাইল অন হলে master blocking toggle অটো-অন ─────────────
        // Accessibility service সবসময় "is_blocking_active" চেক করে।
        // Profile activate করলেই এই flag true হয়ে যাবে — user কে
        // আলাদা করে BlockingToggleCard থেকে ON করতে হবে না।
        val mainPrefs = appContext.getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
        mainPrefs.edit().putBoolean("is_blocking_active", true).apply()
        // ─────────────────────────────────────────────────────────────────────────
    }

    // ── Clear profile data from BlockedData when deactivated ──────────────────
    private fun clearFromBlockedData(profile: FocusProfile) {
        profile.blockedApps.forEach { BlockedData.unblockApp(appContext, it) }
        profile.blockedWebsites.forEach { BlockedData.unblockSite(appContext, it) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory — Context চাই বলে custom factory লাগে
    // ─────────────────────────────────────────────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SharedProfileViewModel(context.applicationContext) as T
        }
    }
}
