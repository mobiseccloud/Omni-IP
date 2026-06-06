package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mobisec.omniip.billing.BillingManager
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.FirewallRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {

    private val billingManager = BillingManager(application, viewModelScope)
    val isPersonalUnlocked = billingManager.isPersonalUnlocked
    val isEnterpriseUnlocked = billingManager.isEnterpriseUnlocked

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.firewallRuleDao()

    private val _rules = MutableStateFlow<List<FirewallRule>>(emptyList())
    val rules: StateFlow<List<FirewallRule>> = _rules

    init {
        viewModelScope.launch {
            dao.getAllRules().collect { ruleList ->
                _rules.value = ruleList
            }
        }
    }

    fun deleteRule(rule: FirewallRule) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRule(rule)
        }
    }

    fun updateRuleAction(rule: FirewallRule, newAction: Action) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateRule(rule.copy(action = newAction))
        }
    }
    fun updateRuleContext(rule: FirewallRule, blockWifi: Boolean, blockMobile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateRule(rule.copy(blockWifi = blockWifi, blockMobile = blockMobile))
        }
    }


    fun addManualRule(rule: FirewallRule): Boolean {
        val existingValues = _rules.value.map { it.targetValue }.toSet()
        if (existingValues.contains(rule.targetValue)) {
            return false
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRule(rule)
            syncRuleToNative(rule)
        }
        return true
    }

    fun importRules(newRules: List<FirewallRule>) {
        viewModelScope.launch(Dispatchers.IO) {
            newRules.forEach { rule ->
                dao.insertRule(rule)
                syncRuleToNative(rule)
            }
        }
    }

    private fun syncRuleToNative(rule: FirewallRule) {
        try {
            var ipInt = 0
            var portInt = 0
            val key = rule.targetValue
            if (rule.targetType == com.mobisec.omniip.db.TargetType.IP_ADDRESS) {
                if (key.contains(":")) {
                    val parts = key.split(":")
                    val ipStr = parts[0]
                    portInt = parts[1].toInt()
                    val ipAddr = java.net.InetAddress.getByName(ipStr)
                    val bytes = ipAddr.address
                    if (bytes.size == 4) {
                        ipInt = ((bytes[0].toInt() and 0xFF) shl 24) or
                                ((bytes[1].toInt() and 0xFF) shl 16) or
                                ((bytes[2].toInt() and 0xFF) shl 8) or
                                (bytes[3].toInt() and 0xFF)
                    }
                } else {
                    val ipAddr = java.net.InetAddress.getByName(key)
                    val bytes = ipAddr.address
                    if (bytes.size == 4) {
                        ipInt = ((bytes[0].toInt() and 0xFF) shl 24) or
                                ((bytes[1].toInt() and 0xFF) shl 16) or
                                ((bytes[2].toInt() and 0xFF) shl 8) or
                                (bytes[3].toInt() and 0xFF)
                    }
                }
            }
            val actionInt = when (rule.action) {
                Action.BLOCK -> 0
                Action.IGNORE -> 1
                Action.FLAG -> 2
            }
            com.mobisec.omniip.core.NativeEngine.updateNativeRule(key, ipInt, portInt, actionInt)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
