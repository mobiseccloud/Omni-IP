package com.mobisec.omniip.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.core.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PortScannerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val TARGET_KEY = "target_key"
    private val OUTPUT_KEY = "output_key"

    val target: StateFlow<String> = savedStateHandle.getStateFlow(TARGET_KEY, "")
    val output: StateFlow<String> = savedStateHandle.getStateFlow(OUTPUT_KEY, "")

    fun updateTarget(newTarget: String) {
        savedStateHandle[TARGET_KEY] = newTarget
    }

    fun startFastScan() {
        val currentTarget = target.value
        savedStateHandle[OUTPUT_KEY] = "Starting Fast Scan on $currentTarget..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                NativeEngine.executeNmapScan(currentTarget)
            }
            savedStateHandle[OUTPUT_KEY] = result
        }
    }

    fun startDeepScan() {
        val currentTarget = target.value
        savedStateHandle[OUTPUT_KEY] = "Starting Deep Scan on $currentTarget..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                NativeEngine.executeNmapScan("$currentTarget -p- -A")
            }
            savedStateHandle[OUTPUT_KEY] = result
        }
    }
}
