package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.FirewallRule
import com.mobisec.omniip.db.TargetType
import com.mobisec.omniip.model.AppConnectionSummary
import com.mobisec.omniip.model.ConnectionDetails
import com.mobisec.omniip.model.ConnectionDirection
import com.mobisec.omniip.model.ConnectionTelemetry
import com.mobisec.omniip.vpn.OmniVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {
    private val _appSummaries = MutableStateFlow<List<AppConnectionSummary>>(emptyList())
    val appSummaries: StateFlow<List<AppConnectionSummary>> = _appSummaries

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.firewallRuleDao()

    private val connectionStateMap = mutableMapOf<Int, AppConnectionSummary>()

    init {
        viewModelScope.launch {
            OmniVpnService.telemetryFlow.collect { telemetry: ConnectionTelemetry ->
                val uid = telemetry.uid
                val signature = "${telemetry.uid}:${telemetry.destIp}:${telemetry.destPort}:${telemetry.protocol}"

                val currentSummary = connectionStateMap[uid] ?: AppConnectionSummary(
                    uid = uid,
                    appName = telemetry.appName,
                    packageName = telemetry.packageName,
                    appIcon = telemetry.appIcon,
                    totalTx = 0,
                    totalRx = 0,
                    totalBlocked = 0,
                    activeConnections = emptyList()
                )

                val currentConnections = currentSummary.activeConnections.toMutableList()
                val existingIndex = currentConnections.indexOfFirst { it.signature == signature }

                var totalTx = currentSummary.totalTx
                var totalRx = currentSummary.totalRx
                var totalBlocked = currentSummary.totalBlocked

                if (telemetry.isBlocked) {
                    totalBlocked += 1
                } else if (telemetry.direction == ConnectionDirection.OUTBOUND_STR) {
                    totalTx += 1
                } else if (telemetry.direction == ConnectionDirection.INBOUND_STR) {
                    totalRx += 1
                }

                if (existingIndex >= 0) {
                    val existing = currentConnections[existingIndex]
                    var tx = existing.bytesTx
                    var rx = existing.bytesRx
                    if (telemetry.direction == ConnectionDirection.OUTBOUND_STR) {
                        tx += 1
                    } else if (telemetry.direction == ConnectionDirection.INBOUND_STR) {
                        rx += 1
                    }

                    currentConnections[existingIndex] = existing.copy(
                        bytesTx = tx,
                        bytesRx = rx,
                        packetCount = existing.packetCount + 1,
                        isBlocked = telemetry.isBlocked,
                        lastActive = System.currentTimeMillis(),
                        country = telemetry.country ?: existing.country,
                        countryCode = telemetry.countryCode ?: existing.countryCode,
                        city = telemetry.city ?: existing.city,
                        domainName = telemetry.resolvedHostname ?: existing.domainName
                    )
                } else {
                    var tx = 0L
                    var rx = 0L
                    if (telemetry.direction == ConnectionDirection.OUTBOUND_STR) {
                        tx = 1L
                    } else if (telemetry.direction == ConnectionDirection.INBOUND_STR) {
                        rx = 1L
                    }

                    var domainName = telemetry.resolvedHostname
                    if (domainName != null && domainName.startsWith("[SNI] ")) {
                        domainName = domainName.substring(6)
                    }

                    currentConnections.add(
                        ConnectionDetails(
                            signature = signature,
                            domainName = domainName,
                            protocol = telemetry.protocol,
                            sourceIp = telemetry.sourceIp,
                            sourcePort = telemetry.sourcePort,
                            destIp = telemetry.destIp,
                            destPort = telemetry.destPort,
                            isBlocked = telemetry.isBlocked,
                            direction = telemetry.direction,
                            bytesTx = telemetry.txBytes.toLong() + tx,
                            bytesRx = telemetry.rxBytes.toLong() + rx,
                            packetCount = 1,
                            lastActive = System.currentTimeMillis(),
                            countryCode = telemetry.countryCode,
                            country = telemetry.country,
                            city = telemetry.city
                        )
                    )
                }

                val sharedPrefs = application.getSharedPreferences("telemetry_prefs", android.content.Context.MODE_PRIVATE)
                val inactiveInLimit = sharedPrefs.getInt("inactive_in_records_limit", 50)
                val inactiveOutLimit = sharedPrefs.getInt("inactive_out_records_limit", 50)
                val now = System.currentTimeMillis()

                val sortedConns = currentConnections.sortedByDescending { it.lastActive }
                val activeList = sortedConns.filter { now - it.lastActive <= 30000 }
                val inactiveInList = sortedConns.filter { now - it.lastActive > 30000 && it.direction == ConnectionDirection.INBOUND_STR }.take(inactiveInLimit)
                val inactiveOutList = sortedConns.filter { now - it.lastActive > 30000 && it.direction != ConnectionDirection.INBOUND_STR }.take(inactiveOutLimit)

                val newSummary = currentSummary.copy(
                    totalTx = totalTx,
                    totalRx = totalRx,
                    totalBlocked = totalBlocked,
                    activeConnections = (activeList + inactiveInList + inactiveOutList).sortedByDescending { it.lastActive }
                )

                connectionStateMap[uid] = newSummary

                _appSummaries.value = connectionStateMap.values.toList().sortedByDescending { it.activeConnections.maxOfOrNull { conn -> conn.lastActive } ?: 0L }
            }
        }
    }

    fun addRule(targetType: TargetType, targetValue: String, action: Action) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRule(FirewallRule(targetType = targetType, targetValue = targetValue, action = action))
        }
    }
}
