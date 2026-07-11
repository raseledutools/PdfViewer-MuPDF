package com.rasel.RasFocus.features

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.rasel.RasFocus.selfcontrol.BlockerPrefs

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Admin active হলে BlockerPrefs-এ uninstall protection চালু করো
        BlockerPrefs(context).uninstallProtection = true
        Toast.makeText(
            context,
            "🛡️ RasFocus: Uninstall protection active!",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // System confirmation dialog-এ এই message দেখাবে
        return "⚠️ Warning: Disabling admin will remove uninstall protection and allow RasFocus to be deleted!"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Admin revoke হলে BlockerPrefs-এ protection বন্ধ করো
        BlockerPrefs(context).uninstallProtection = false
        Toast.makeText(
            context,
            "Admin disabled. Uninstall protection removed.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
