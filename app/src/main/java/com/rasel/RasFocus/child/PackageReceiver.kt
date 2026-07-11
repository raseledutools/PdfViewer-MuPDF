package com.rasel.RasFocus.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            
            val prefs = context.getSharedPreferences("rasfocus_prefs", Context.MODE_PRIVATE)
            val isChildMode = prefs.getString("selected_persona", "") == "CHILD"
            if (!isChildMode) return
            
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val deviceId = prefs.getString("device_id", "") ?: return
            if (deviceId.isEmpty()) return
            
            val pm = context.packageManager
            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val docRef = db.collection("pc_commands").document(deviceId)
                    
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        val currentCsv = snapshot.getString("new_installed_apps_csv") ?: ""
                        
                        val newEntry = "$appName ($packageName)"
                        val updatedCsv = if (currentCsv.isEmpty()) newEntry else "$newEntry,$currentCsv"
                        
                        // Keep only latest 10 apps to avoid massive strings
                        val limitedCsv = updatedCsv.split(",").take(10).joinToString(",")
                        
                        transaction.set(
                            docRef,
                            mapOf("new_installed_apps_csv" to limitedCsv),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
