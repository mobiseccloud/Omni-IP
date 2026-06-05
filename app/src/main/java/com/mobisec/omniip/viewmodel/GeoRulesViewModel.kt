package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.core.NativeEngine
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.GeoRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GeoRulesViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).geoRuleDao()

    val rules: StateFlow<List<GeoRule>> = dao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(countryCode: String, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rule = GeoRule(countryCode = countryCode.uppercase(), action = action, timestamp = System.currentTimeMillis())
            dao.insertRule(rule)
            NativeEngine.updateGeoRule(rule.countryCode, action)
        }
    }

    fun deleteRule(rule: GeoRule) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRule(rule)
            NativeEngine.updateGeoRule(rule.countryCode, "REMOVE")
        }
    }
}
