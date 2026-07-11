package com.rasel.RasFocus.combo.selfcontrol

import java.util.UUID

/**
 * FocusProfile — একটি focus session এর সব settings।
 * SharedProfileViewModel এ List<FocusProfile> হিসেবে store হয়।
 */
data class FocusProfile(
    val id: String = UUID.randomUUID().toString(),
    val isActive: Boolean = false,

    // Schedule
    val startHour: Int = 9,
    val startMin: Int = 0,
    val endHour: Int = 17,
    val endMin: Int = 0,

    // Blocked content
    val blockedApps: List<String> = emptyList(),
    val blockedWebsites: List<String> = emptyList(),

    // Quick toggles
    val quickBlockYtShorts: Boolean = false,
    val quickBlockFbReels: Boolean = false,
    val quickBlockIgReels: Boolean = false,

    // Lock settings
    val lockMode: String = "self",        // "self" | "parents" | "longtext"
    val blockUninstall: Boolean = false
)
