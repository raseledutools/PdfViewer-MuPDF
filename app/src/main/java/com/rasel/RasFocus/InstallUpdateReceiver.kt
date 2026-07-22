package com.rasel.RasFocus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File

class InstallUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val filePath = intent.getStringExtra(EXTRA_APK_PATH) ?: return
        val apkFile = File(filePath)

        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "Update file not found. Please wait for re-download.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "APK file missing: $filePath")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = context.packageManager
            val canInstall = pm.canRequestPackageInstalls()
            if (!canInstall) {
                // Permission not granted — send user to settings
                Toast.makeText(
                    context,
                    "Please allow 'Install unknown apps' for RasFocus, then tap the notification again.",
                    Toast.LENGTH_LONG
                ).show()
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(settingsIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot open unknown sources settings", e)
                }
                return
            }
        }

        // Permission OK — proceed to install
        AutoUpdater.installDownloadedUpdate(context, apkFile)
    }

    companion object {
        const val ACTION_INSTALL_UPDATE = "com.rasel.RasFocus.ACTION_INSTALL_UPDATE"
        const val EXTRA_APK_PATH = "extra_apk_path"
        private const val TAG = "InstallUpdateReceiver"
    }
}
