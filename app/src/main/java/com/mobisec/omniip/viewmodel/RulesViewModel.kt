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
            syncRuleToNative()
        }
    }

    fun updateRuleAction(rule: FirewallRule, newAction: Action) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedRule = if (newAction == Action.FLAG || newAction == Action.IGNORE) {
                rule.copy(action = newAction, blockWifi = false, blockMobile = false)
            } else {
                rule.copy(action = newAction)
            }
            dao.updateRule(updatedRule)
            syncRuleToNative(updatedRule)
        }
    }
    fun updateRuleContext(rule: FirewallRule, blockWifi: Boolean, blockMobile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedRule = if (!blockWifi && !blockMobile) {
                rule.copy(action = Action.FLAG, blockWifi = false, blockMobile = false)
            } else {
                rule.copy(blockWifi = blockWifi, blockMobile = blockMobile)
            }
            dao.updateRule(updatedRule)
            syncRuleToNative(updatedRule)
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

    private suspend fun syncRuleToNative(rule: FirewallRule? = null) {
        try {
            val allRules = dao.getAllRulesSync()
            com.mobisec.omniip.core.NativeEngine.syncRulesToNative(allRules)
        } catch (e: Exception) {
            android.util.Log.e("RulesViewModel", "Error syncing rules to native layer", e)
        }
    }
}
