package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.FirewallRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {
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
}
