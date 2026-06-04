package com.mobisec.omniip.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mobisec.omniip.vpn.OmniVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = SecurityPreferences(context)
            if (prefs.isAutoStartEnabled()) {
                val serviceIntent = Intent(context, OmniVpnService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
