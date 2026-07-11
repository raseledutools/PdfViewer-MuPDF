package com.rasel.RasFocus.selfcontrol.familybrowser

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * RasBrowserTileService
 *
 * Quick Settings panel এ (WiFi, Bluetooth এর মতো জায়গায়)
 * "RasBrowser" নামের একটা tile দেখাবে।
 * Tile এ tap করলে সরাসরি FamilyBrowserActivity খুলবে।
 *
 * Setup:
 *   1. এই file টা project এ রাখো
 *   2. AndroidManifest.xml এ নিচের entry যোগ করো (দেখো manifest_entry.txt)
 *   3. Phone এর Quick Settings edit করে tile টা add করো
 */
@RequiresApi(Build.VERSION_CODES.N)
class RasBrowserTileService : TileService() {

    // ── Tile active হলে (panel খোলা থাকলে) ──────────────────────────────────
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    // ── Tile এ tap করলে ───────────────────────────────────────────────────────
    override fun onClick() {
        super.onClick()

        // Panel collapse করে browser open করো
        val intent = Intent(this, FamilyBrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = Intent.ACTION_MAIN
        }

        // Android 9+ এ startActivityAndCollapse এ Intent দেওয়া যায়
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            // Android 7–13
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    // ── Tile UI update ────────────────────────────────────────────────────────
    private fun updateTile() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE   // সবসময় active (নীল) দেখাবে
            label = "RasBrowser"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "খুলুন"
            }
            updateTile()
        }
    }
}
