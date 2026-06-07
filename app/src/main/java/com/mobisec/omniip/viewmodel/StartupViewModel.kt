package com.mobisec.omniip.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StartupViewModel(application: Application) : AndroidViewModel(application) {

    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions

    fun checkPermissions(context: Context) {
        val missing = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missing.add("POST_NOTIFICATIONS")
            }
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add("ACCESS_FINE_LOCATION")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE) != PackageManager.PERMISSION_GRANTED) {
                missing.add("FOREGROUND_SERVICE_SPECIAL_USE")
            }
        }

        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            missing.add("VPN_PREPARATION")
        }

        _missingPermissions.value = missing
    }

    fun requestVpnPreparation(context: Context): android.content.Intent? {
        return VpnService.prepare(context)
    }
}
