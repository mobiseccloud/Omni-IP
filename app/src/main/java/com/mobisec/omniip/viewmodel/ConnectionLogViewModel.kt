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
import kotlinx.coroutines.launch

class ConnectionLogViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).connectionLogDao()

    private val _selectedActions = MutableStateFlow(setOf("ALLOW", "BLOCK", "FLAG"))
    val selectedActions: StateFlow<Set<String>> = _selectedActions

    private val _refreshTrigger = MutableStateFlow(0)

    private val sharedPrefs = application.getSharedPreferences("telemetry_prefs", android.content.Context.MODE_PRIVATE)

    val maxLogs = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(sharedPrefs.getInt("connection_log_max_size", 1000))
            kotlinx.coroutines.delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1000)

    val logs: StateFlow<List<ConnectionLog>> = kotlinx.coroutines.flow.combine(
        _selectedActions,
        _refreshTrigger
    ) { actions, _ -> actions }
        .flatMapLatest { actions ->
            dao.getLogsByActions(actions.toList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLogPoolFull: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(logs, maxLogs) { logsList, max ->
        logsList.size >= max
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
            out.write("Timestamp,App,Protocol,Source IP,Source Port,Destination IP,Destination Port,Domain,Action,Direction,ASN,Country,City\n")
            logs.value.forEach { log ->
                out.write("${log.timestamp},${log.appName},${log.protocol ?: ""},${log.sourceIp ?: ""},${log.sourcePort ?: ""},${log.destIp},${log.destPort},${log.domainName ?: ""},${log.action},${log.direction ?: ""},${log.asn ?: ""},${log.country ?: ""},${log.city ?: ""}\n")
            }
        }
        return file
    }

    fun clearLogs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dao.clearLogs()
        }
    }

    fun refreshLogs() {
        _refreshTrigger.value += 1
    }
}
