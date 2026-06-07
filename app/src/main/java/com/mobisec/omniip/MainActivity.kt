
package com.mobisec.omniip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.mobisec.omniip.billing.BillingManager
import com.mobisec.omniip.core.NativeEngine
import com.mobisec.omniip.vpn.OmniVpnService
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.PureBlack
import com.mobisec.omniip.ui.theme.OmniIPTheme
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.ui.theme.TextSecondary
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.SurfaceLevel2
import com.mobisec.omniip.viewmodel.TelemetryViewModel
import com.mobisec.omniip.viewmodel.RulesViewModel
import com.mobisec.omniip.ui.RulesScreen
import com.mobisec.omniip.ui.LanScannerScreen
import com.mobisec.omniip.ui.DashboardScreen
import com.mobisec.omniip.viewmodel.LanScannerViewModel
import com.mobisec.omniip.viewmodel.DashboardViewModel
import com.mobisec.omniip.viewmodel.GeoRulesViewModel
import com.mobisec.omniip.viewmodel.AppMatrixViewModel
import com.mobisec.omniip.ui.UnifiedFirewallScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Alignment

@Composable
fun TopBar(isRaspCompromised: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceLevel2).padding(16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRaspCompromised) {
            Icon(Icons.Default.Warning, contentDescription = "Compromised", tint = AlertRed)
        }
    }
}

class MainActivity : ComponentActivity() {
    lateinit var billingManager: BillingManager
    private val telemetryViewModel: TelemetryViewModel by viewModels()
    private val rulesViewModel: RulesViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val geoRulesViewModel: GeoRulesViewModel by viewModels()
    private val appMatrixViewModel: AppMatrixViewModel by viewModels()

    private val vpnServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, OmniVpnService::class.java)
            startService(intent)
        }
    }

    private fun startPcapRecording(targetUid: Int?) {
        // Pcap logic is now handled entirely within OmniVpnService via flow mutations
        OmniVpnService.targetRecordUid = targetUid
        OmniVpnService.isPcapRecordingFlow.value = true
    }

    private fun stopPcapRecording() {
        OmniVpnService.isPcapRecordingFlow.value = false
        OmniVpnService.targetRecordUid = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billingManager = BillingManager(this, lifecycleScope)

        NativeEngine.initializeNativeEnvironment(BuildConfig.DEBUG)

        setContent {
            OmniIPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isRaspCompromised by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        try {
    NativeEngine.executeSecuritySweep(this@MainActivity)
} catch (e: Exception) {
    isRaspCompromised = true
}
                    }

                    if (isRaspCompromised) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SECURITY VIOLATION DETECTED", color = AlertRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("The environment is compromised. Omni-IP cannot run safely.", color = TextSecondary)
                        }
                    } else {
                        var currentTab by remember { mutableStateOf(0) }
                        var targetIp by remember { mutableStateOf("") }
                        var initialAction by remember { mutableStateOf("NONE") }

                        Scaffold(
                            topBar = { TopBar(isRaspCompromised = isRaspCompromised) },
                            bottomBar = {
                                NavigationBar(containerColor = PureBlack) {
                                    NavigationBarItem(
                                        selected = currentTab == 0,
                                        onClick = { currentTab = 0 },
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                                        label = { Text("Dashboard", color = if(currentTab == 0) MatrixGreen else TextSecondary) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MatrixGreen,
                                            unselectedIconColor = TextSecondary,
                                            indicatorColor = PureBlack
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == 1,
                                        onClick = { currentTab = 1 },
                                        icon = { Icon(Icons.Default.Lock, contentDescription = "Firewall") },
                                        label = { Text("Firewall", color = if(currentTab == 1) MatrixGreen else TextSecondary) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MatrixGreen,
                                            unselectedIconColor = TextSecondary,
                                            indicatorColor = PureBlack
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == 2,
                                        onClick = { currentTab = 2 },
                                        icon = { Icon(Icons.Default.Build, contentDescription = "Toolkit") },
                                        label = { Text("Toolkit", color = if(currentTab == 2) MatrixGreen else TextSecondary) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MatrixGreen,
                                            unselectedIconColor = TextSecondary,
                                            indicatorColor = PureBlack
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == 3,
                                        onClick = { currentTab = 3 },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "Configuration") },
                                        label = { Text("Configuration", color = if(currentTab == 3) MatrixGreen else TextSecondary) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MatrixGreen,
                                            unselectedIconColor = TextSecondary,
                                            indicatorColor = PureBlack
                                        )
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                if (currentTab == 0) {
                                    val terminalOutput by dashboardViewModel.terminalOutput.collectAsState()
                                    val isExecuting by dashboardViewModel.isExecuting.collectAsState()
                                    val showUpgradePrompt by dashboardViewModel.showUpgradePrompt.collectAsState()
    
                                    if (showUpgradePrompt) {
                                        AlertDialog(
                                            onDismissRequest = { dashboardViewModel.dismissUpgradePrompt() },
                                            title = { Text("Premium Feature") },
                                            text = { Text("This is a premium feature. Upgrade to unlock.") },
                                            confirmButton = {
                                                Button(onClick = {
                                                    dashboardViewModel.dismissUpgradePrompt()
                                                    billingManager.launchBillingFlow(this@MainActivity, BillingManager.SKU_PERSONAL_TIER)
                                                }) { Text("OK") }
                                            }
                                        )
                                    }
    
                                    val isRecording by OmniVpnService.isPcapRecordingFlow.collectAsState()
                                    val pcapSize by OmniVpnService.pcapFileSizeFlow.collectAsState()
                                    val targetUid = OmniVpnService.targetRecordUid
                                    val appInfo = targetUid?.let { OmniVpnService.appInfoCache.getIfPresent(it) }
                                    val targetName = if (targetUid == null) "All Traffic" else (appInfo?.first ?: "Unknown App")
                                    val metrics by OmniVpnService.currentTargetMetricsFlow.collectAsState()
                                    val activeApps by OmniVpnService.activeAppsFlow.collectAsState()
                                    val isFirewallActive by dashboardViewModel.isFirewallActive.collectAsState()
    
                                    DashboardScreen(
                                        viewModel = dashboardViewModel,
                                        targetIp = targetIp,
                                        terminalOutput = terminalOutput,
                                        isExecuting = isExecuting,
                                        isRecording = isRecording,
                                        pcapSize = pcapSize,
                                        targetName = targetName,
                                        rxBytes = metrics.rxBytes,
                                        txBytes = metrics.txBytes,
                                        activeApps = activeApps,
                                        isFirewallActive = isFirewallActive,
                                        onExecuteAction = { ip, action -> dashboardViewModel.executeAction(ip, action) },
                                        onToggleRecording = { start, uid ->
                                            if (start) {
                                                startPcapRecording(uid)
                                            } else {
                                                stopPcapRecording()
                                            }
                                        },
                                        onToggleFirewall = { start ->
                                            if (start) {
                                                val intent = android.net.VpnService.prepare(this@MainActivity)
                                                if (intent != null) {
                                                    vpnServiceLauncher.launch(intent)
                                                } else {
                                                    val startIntent = Intent(this@MainActivity, OmniVpnService::class.java)
                                                    startService(startIntent)
                                                }
                                            } else {
                                                val stopIntent = Intent(this@MainActivity, OmniVpnService::class.java).apply {
                                                    action = OmniVpnService.ACTION_STOP_VPN
                                                }
                                                startService(stopIntent)
                                            }
                                        },
                                        telemetryViewModel = telemetryViewModel
                                    )
                                } else if (currentTab == 1) {
                                    UnifiedFirewallScreen(
                                        rulesViewModel = rulesViewModel,
                                        appMatrixViewModel = appMatrixViewModel,
                                        geoRulesViewModel = geoRulesViewModel,
                                        onRequirePremium = { dashboardViewModel.triggerUpgradePrompt() }
                                    )
                                } else if (currentTab == 2) {
                                    com.mobisec.omniip.ui.ToolkitNavHost(onRequirePremium = { dashboardViewModel.triggerUpgradePrompt() })
                                } else if (currentTab == 3) {
                                    com.mobisec.omniip.ui.SettingsScreen(onShowArchitectureDoc = {})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
