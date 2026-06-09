package com.mobisec.omniip.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    UNAVAILABLE,
    PENDING_PERMISSION,
    GRANTED
}

object ShizukuStateProvider {
    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.UNAVAILABLE)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _shizukuStatus.value = ShizukuStatus.GRANTED
        } else {
            _shizukuStatus.value = ShizukuStatus.PENDING_PERMISSION
        }
    }

    init {
        updateStatus()
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (e: Exception) {
            // Shizuku might not be available
        }
    }

    fun updateStatus() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    _shizukuStatus.value = ShizukuStatus.GRANTED
                } else {
                    _shizukuStatus.value = ShizukuStatus.PENDING_PERMISSION
                }
            } else {
                _shizukuStatus.value = ShizukuStatus.UNAVAILABLE
            }
        } catch (e: Exception) {
             _shizukuStatus.value = ShizukuStatus.UNAVAILABLE
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
