package com.mobisec.omniip.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import com.mobisec.omniip.billing.BillingManager
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.core.NativeEngine
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.FirewallRule
import com.mobisec.omniip.db.TargetType
import com.mobisec.omniip.model.AppMatrixItem
import com.mobisec.omniip.model.NetworkInterfaceRule
import com.mobisec.omniip.model.RuleDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppMatrixViewModel(application: Application) : AndroidViewModel(application) {

    private val billingManager = BillingManager(application, viewModelScope)
    val isPersonalUnlocked = billingManager.isPersonalUnlocked
    val isEnterpriseUnlocked = billingManager.isEnterpriseUnlocked

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.firewallRuleDao()
    private val packageManager = application.packageManager

    private val _apps = MutableStateFlow<List<AppMatrixItem>>(emptyList())
    val apps: StateFlow<List<AppMatrixItem>> = _apps

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps

    init {
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.flow.combine(
                dao.getAllRules(),
                showSystemApps
            ) { rules, showSystem ->
                Pair(rules, showSystem)
            }.collect { (rules, showSystem) ->
                val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                val ruleMap = rules.filter { it.targetType == TargetType.APPLICATION }.associateBy { it.targetValue }

                val appList = installedPackages.mapNotNull { appInfo ->
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    // Enforce strict User vs System isolation
                    if (showSystem && !isSystem) {
                        return@mapNotNull null
                    }
                    if (!showSystem && isSystem) {
                        return@mapNotNull null
                    }

                    val packageName = appInfo.packageName
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val iconBitmap = packageManager.getApplicationIcon(appInfo).toBitmap().asImageBitmap()
                    val uid = appInfo.uid

                    val rule = ruleMap[packageName]
                    val wifiBlocked = rule?.blockWifi ?: false
                    val cellularBlocked = rule?.blockMobile ?: false

                    AppMatrixItem(
                        uid = uid,
                        label = label,
                        packageName = packageName,
                        iconBitmap = iconBitmap,
                        isSystem = isSystem,
                        wifiBlocked = wifiBlocked,
                        cellularBlocked = cellularBlocked
                    )
                }.sortedBy { it.label }

                _apps.value = appList
            }
        }
    }

    fun toggleSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
    }

    fun toggleWifiBlock(item: AppMatrixItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val newBlockState = !item.wifiBlocked
            updateRuleAndSync(item, blockWifi = newBlockState, blockMobile = item.cellularBlocked)

            val updatedApps = _apps.value.map {
                if (it.packageName == item.packageName) it.copy(wifiBlocked = newBlockState) else it
            }
            _apps.value = updatedApps
        }
    }

    fun toggleCellularBlock(item: AppMatrixItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val newBlockState = !item.cellularBlocked
            updateRuleAndSync(item, blockWifi = item.wifiBlocked, blockMobile = newBlockState)

            val updatedApps = _apps.value.map {
                if (it.packageName == item.packageName) it.copy(cellularBlocked = newBlockState) else it
            }
            _apps.value = updatedApps
        }
    }

    private suspend fun updateRuleAndSync(item: AppMatrixItem, blockWifi: Boolean, blockMobile: Boolean) {
        val existingRules = dao.getAllRulesSync()
        var rule = existingRules.find { it.targetType == TargetType.APPLICATION && it.targetValue == item.packageName }

        val interfaceRule = if (blockWifi && blockMobile) {
            NetworkInterfaceRule.BLOCKED_ALL
        } else if (blockWifi) {
            NetworkInterfaceRule.CELLULAR_ONLY
        } else if (blockMobile) {
            NetworkInterfaceRule.WIFI_ONLY
        } else {
            NetworkInterfaceRule.ALL
        }

        if (rule == null) {
            rule = FirewallRule(
                targetType = TargetType.APPLICATION,
                targetValue = item.packageName,
                action = Action.BLOCK,
                blockWifi = blockWifi,
                blockMobile = blockMobile,
                direction = RuleDirection.BOTH,
                interfaceRule = interfaceRule
            )
            dao.insertRule(rule)
        } else {
            rule = rule.copy(
                blockWifi = blockWifi,
                blockMobile = blockMobile,
                interfaceRule = interfaceRule,
                action = Action.BLOCK
            )
            dao.updateRule(rule)
        }

        NativeEngine.syncRulesToNative(dao.getAllRulesSync())
    }
}
