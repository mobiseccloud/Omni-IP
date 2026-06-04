package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.ConnectionLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class ConnectionLogViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).connectionLogDao()

    private val _selectedActions = MutableStateFlow(setOf("ALLOW", "BLOCK", "FLAG"))
    val selectedActions: StateFlow<Set<String>> = _selectedActions

    val logs: StateFlow<List<ConnectionLog>> = _selectedActions
        .flatMapLatest { actions ->
            dao.getLogsByActions(actions.toList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleActionFilter(action: String) {
        val current = _selectedActions.value.toMutableSet()
        if (current.contains(action)) {
            current.remove(action)
        } else {
            current.add(action)
        }
        _selectedActions.value = current
    }

    fun exportLogsToCsv(context: android.content.Context): java.io.File {
        val file = java.io.File(context.cacheDir, "connection_logs.csv")
        file.bufferedWriter().use { out ->
            out.write("Timestamp,App,Destination IP,Port,Action,ASN,Country,City\n")
            logs.value.forEach { log ->
                out.write("${log.timestamp},${log.appName},${log.destIp},${log.destPort},${log.action},${log.asn},${log.countryCode},${log.city}\n")
            }
        }
        return file
    }
}
