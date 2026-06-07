package com.mobisec.omniip.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class InitViewModel(application: Application) : AndroidViewModel(application) {

    private val _initStatus = MutableStateFlow("Initializing...")
    val initStatus: StateFlow<String> = _initStatus

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    fun startInitialization() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

            // Check if files exist AND are larger than a realistic minimum size
            val cityFile = File(context.cacheDir, "GeoLite2-City.mmdb")
            val asnFile = File(context.cacheDir, "GeoLite2-ASN.mmdb")
            val ouiFile = File(context.filesDir, "oui.txt")

            val filesValid = cityFile.exists() && cityFile.length() > 1000 &&
                             asnFile.exists() && asnFile.length() > 1000 &&
                             ouiFile.exists() && ouiFile.length() > 1000

            if (filesValid) {
                _initStatus.value = "Datasets already initialized."
                _isInitialized.value = true
                return@launch
            }

            try {
                if (!ouiFile.exists() || ouiFile.length() <= 1000) {
                    _initStatus.value = "Extracting MAC OUI database..."
                    context.assets.open("oui.txt").use { input ->
                        FileOutputStream(ouiFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (!cityFile.exists() || cityFile.length() <= 1000) {
                     _initStatus.value = "Extracting GeoLite2-City database..."
                     context.assets.open("GeoLite2-City.mmdb").use { input ->
                         FileOutputStream(cityFile).use { output ->
                             input.copyTo(output)
                         }
                     }
                }

                if (!asnFile.exists() || asnFile.length() <= 1000) {
                     _initStatus.value = "Extracting GeoLite2-ASN database..."
                     context.assets.open("GeoLite2-ASN.mmdb").use { input ->
                         FileOutputStream(asnFile).use { output ->
                             input.copyTo(output)
                         }
                     }
                }

                _isInitialized.value = true
                _initStatus.value = "Initialization complete."

            } catch (e: Exception) {
                Log.e("InitViewModel", "Failed initialization", e)
                _isInitialized.value = true // Let app proceed with graceful degradation
            }
        }
    }
}
