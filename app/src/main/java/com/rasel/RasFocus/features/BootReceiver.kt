package com.rasel.RasFocus.features

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val prefs = context.getSharedPreferences("rasfocus_prefs", Context.MODE_PRIVATE)
            val hasAcceptedTerms = prefs.getBoolean("has_accepted_terms", false)
            
            if (hasAcceptedTerms) {
                try {
                    com.rasel.RasFocus.UsageNotificationService.start(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Re-register background auto-update worker
            com.rasel.RasFocus.AutoUpdater.setupBackgroundAutoUpdate(context)
        }
    }
}
