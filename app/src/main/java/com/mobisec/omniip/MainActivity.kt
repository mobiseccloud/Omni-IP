package com.mobisec.omniip

import androidx.compose.foundation.horizontalScroll
import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.TargetType
import com.mobisec.omniip.model.ConnectionDirection
import com.mobisec.omniip.model.ConnectionTelemetry
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.OmniIPTheme
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.ui.theme.TextSecondary
import com.mobisec.omniip.viewmodel.TelemetryViewModel

import com.mobisec.omniip.viewmodel.RulesViewModel
import com.mobisec.omniip.ui.RulesScreen
import com.mobisec.omniip.ui.LanScannerScreen
import com.mobisec.omniip.ui.DashboardScreen
import com.mobisec.omniip.viewmodel.LanScannerViewModel
import com.mobisec.omniip.viewmodel.DashboardViewModel

import com.mobisec.omniip.vpn.OmniVpnService
import com.mobisec.omniip.vpn.PcapWriter
import kotlinx.coroutines.launch
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.mobisec.omniip.worker.ThreatFeedWorker
import com.mobisec.omniip.billing.BillingManager
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    lateinit var billingManager: BillingManager

    private val viewModel: TelemetryViewModel by viewModels()

    private val vpnServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, OmniVpnService::class.java)
            startService(intent)
        }
    }

    private var pendingRecordUid: Int? = null

    private val pcapCreateFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.tcpdump.pcap")) { uri ->
        if (uri != null) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val pfd = contentResolver.openFileDescriptor(uri, "w")
                    if (pfd != null) {
                        val writer = PcapWriter(pfd)
                        writer.initialize()
                        OmniVpnService.activePcapWriter = writer

                        OmniVpnService.targetRecordUid = pendingRecordUid
                        if (pendingRecordUid != null) {
                            OmniVpnService.exfiltrationTracker.invalidate(pendingRecordUid!!)
                        } else {
                            OmniVpnService.exfiltrationTracker.invalidateAll()
                        }
                        OmniVpnService.currentTargetMetricsFlow.value = OmniVpnService.ExfiltrationMetrics()

                        OmniVpnService.isPcapRecordingFlow.value = true
                        OmniVpnService.pcapFileSizeFlow.value = 24 // Global header size
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startPcapRecording(targetUid: Int?) {
        pendingRecordUid = targetUid
        val fileName = "omni_ip_capture_${System.currentTimeMillis()}.pcap"
        pcapCreateFileLauncher.launch(fileName)
    }

    private fun stopPcapRecording() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            OmniVpnService.isPcapRecordingFlow.value = false
            OmniVpnService.activePcapWriter?.close()
            OmniVpnService.activePcapWriter = null

            val pcapFile = java.io.File(cacheDir, "session_capture.pcap")
            if (pcapFile.exists()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    com.mobisec.omniip.core.ExportEngine.shareFile(this@MainActivity, pcapFile, "application/vnd.tcpdump.pcap")
                }
            }
        }
    }

    private val rulesViewModel: RulesViewModel by viewModels()
    private val lanScannerViewModel: LanScannerViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val initViewModel: com.mobisec.omniip.viewmodel.InitViewModel by viewModels()
    private val startupViewModel: com.mobisec.omniip.viewmodel.StartupViewModel by viewModels()
    private val geoRulesViewModel: com.mobisec.omniip.viewmodel.GeoRulesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mobisec.omniip.core.NativeEngine.initializeNativeEnvironment(com.mobisec.omniip.BuildConfig.DEBUG)
        com.mobisec.omniip.core.NativeEngine.executeSecuritySweep(this)
        billingManager = BillingManager(this, lifecycleScope)

        initViewModel.startInitialization()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<ThreatFeedWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ThreatFeedUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )

        setContent {
            OmniIPTheme {
                var isRaspCompromised by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    try {
                        com.mobisec.omniip.core.NativeEngine.executeSecuritySweep(this@MainActivity)
                    } catch (e: Exception) {
                        isRaspCompromised = true
                    }
                }

                val isInitialized by initViewModel.isInitialized.collectAsState()
                val initStatus by initViewModel.initStatus.collectAsState()

                LaunchedEffect(Unit) {
                    startupViewModel.checkPermissions(this@MainActivity)
                }
                val missingPermissions by startupViewModel.missingPermissions.collectAsState()

                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    startupViewModel.checkPermissions(this@MainActivity)
                }
                val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    startupViewModel.checkPermissions(this@MainActivity)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (missingPermissions.isNotEmpty()) {
                        com.mobisec.omniip.ui.StartupPermissionDialog(
                            missingPermissions = missingPermissions,
                            onRequestPermissions = {
                                val perms = mutableListOf<String>()
                                if (missingPermissions.contains("POST_NOTIFICATIONS") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (missingPermissions.contains("FOREGROUND_SERVICE_SPECIAL_USE") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    perms.add(android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
                                }
                                if (perms.isNotEmpty()) {
                                    permissionLauncher.launch(perms.toTypedArray())
                                }
                                if (missingPermissions.contains("VPN_PREPARATION")) {
                                    val intent = startupViewModel.requestVpnPreparation(this@MainActivity)
                                    if (intent != null) {
                                        vpnLauncher.launch(intent)
                                    }
                                }
                            }
                        )
                    } else if (!isInitialized) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "> ${initStatus}_",
                                color = MatrixGreen,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 18.sp
                            )
                        }
                    } else {
                        var currentTab by remember { mutableStateOf(0) }
                        var targetIp by remember { mutableStateOf("") }
                        var initialAction by remember { mutableStateOf("NONE") }
                        val mainTabs = listOf("Telemetry", "Firewall Matrix", "Threat Feeds", "Scanner", "Dashboard", "Toolkit", "GeoRules")

                        Column(modifier = Modifier.fillMaxSize()) {
                            TopBar(isRaspCompromised = isRaspCompromised)

                            TabRow(
                                selectedTabIndex = currentTab,
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = MatrixGreen
                            ) {
                                mainTabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = currentTab == index,
                                        onClick = { currentTab = index },
                                        text = { Text(title) }
                                    )
                                }
                            }

                            if (currentTab == 0) {
                                TelemetryScreen(viewModel)
                            } else if (currentTab == 1) {
                                RulesScreen(rulesViewModel)
                            } else if (currentTab == 2) {
                                var showArchitectureDoc by remember { mutableStateOf(false) }
                                if (showArchitectureDoc) {
                                    com.mobisec.omniip.ui.ArchitectureDocScreen(onBack = { showArchitectureDoc = false })
                                } else {
                                    com.mobisec.omniip.ui.SettingsScreen(onShowArchitectureDoc = { showArchitectureDoc = true })
                                }
                            } else if (currentTab == 3) {
                                LanScannerScreen(lanScannerViewModel) { ip, action ->
                                    targetIp = ip
                                    initialAction = action
                                    currentTab = 4 // Navigate to Dashboard
                                }
                            } else if (currentTab == 4) {
                                val terminalOutput by dashboardViewModel.terminalOutput.collectAsState()
                                val isExecuting by dashboardViewModel.isExecuting.collectAsState()
                                val showUpgradePrompt by dashboardViewModel.showUpgradePrompt.collectAsState()

                                if (showUpgradePrompt) {
                                    AlertDialog(
                                        onDismissRequest = { dashboardViewModel.dismissUpgradePrompt() },
                                        title = { Text("Premium Feature") },
                                        text = { Text("Port scanning is a premium feature. Upgrade to unlock.") },
                                        confirmButton = {
                                            Button(onClick = {
                                                dashboardViewModel.dismissUpgradePrompt()
                                                billingManager.launchBillingFlow(this@MainActivity, BillingManager.SKU_PERSONAL_TIER)
                                            }) {
                                                Text("OK")
                                            }
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
                                    initialAction = initialAction,
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
                                    onToggleFirewall = { isActive ->
                                        dashboardViewModel.setFirewallActive(isActive)
                                    }
                                )
                            } else if (currentTab == 5) {
                                com.mobisec.omniip.ui.ToolkitNavHost(onRequirePremium = { dashboardViewModel.triggerUpgradePrompt() })
                            } else if (currentTab == 6) {
                                com.mobisec.omniip.ui.GeoRulesScreen(geoRulesViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TelemetryScreen(viewModel: TelemetryViewModel) {
        val appSummaries by viewModel.appSummaries.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appSummaries) { summary ->
                    AppSummaryCard(summary)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppSummaryCard(summary: com.mobisec.omniip.model.AppConnectionSummary) {
        var expanded by remember { mutableStateOf(false) }
        var selectedFilter by remember { mutableStateOf("All") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = summary.appIcon
                    if (icon != null) {
                        Image(
                            bitmap = icon.toBitmap().asImageBitmap(),
                            contentDescription = "App Icon",
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Box(modifier = Modifier
                            .size(48.dp)
                            .background(TextSecondary))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.appName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("[TX: ${summary.totalTx}]", color = MatrixGreen, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
                            Text("[RX: ${summary.totalRx}]", color = TacticalAmber, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
                            Text("[BLK: ${summary.totalBlocked}]", color = AlertRed, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }

                    IconToggleButton(checked = expanded, onCheckedChange = { expanded = it }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand/Collapse",
                            tint = MatrixGreen
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All", "Tx", "Rx", "Blocked").forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MatrixGreen.copy(alpha = 0.2f),
                                        selectedLabelColor = MatrixGreen
                                    )
                                )
                            }
                        }

                        val filteredConnections = summary.activeConnections.filter { conn ->
                            when (selectedFilter) {
                                "Tx" -> conn.direction == com.mobisec.omniip.model.ConnectionDirection.OUTBOUND
                                "Rx" -> conn.direction == com.mobisec.omniip.model.ConnectionDirection.INBOUND
                                "Blocked" -> conn.isBlocked
                                else -> true
                            }
                        }

                        filteredConnections.forEach { conn ->
                            ConnectionRow(conn)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ConnectionRow(conn: com.mobisec.omniip.model.ConnectionDetails) {
        val statusColor = if (conn.isBlocked) AlertRed else MatrixGreen
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(statusColor, androidx.compose.foundation.shape.CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = conn.protocol, color = TacticalAmber, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(40.dp))

            val geoText = if (conn.countryCode != null && conn.city != null) {
                val flagOffset = 0x1F1E6
                val asciiOffset = 0x41
                val countryCodeUpper = conn.countryCode.uppercase()
                val flag = if (countryCodeUpper.length == 2) {
                    val firstChar = Character.codePointAt(countryCodeUpper, 0) - asciiOffset + flagOffset
                    val secondChar = Character.codePointAt(countryCodeUpper, 1) - asciiOffset + flagOffset
                    String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
                } else ""
                " $flag ${conn.countryCode}, ${conn.city}"
            } else ""

            Text(text = "${conn.destIp}:${conn.destPort}$geoText", color = MaterialTheme.colorScheme.onSurface, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
fun TopBar(isRaspCompromised: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "OMNI-IP TELEMETRY",
            color = MatrixGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

val HighRiskJurisdictions = listOf("CN", "RU", "IR", "KP")

@Composable
fun TelemetryItem(telemetry: ConnectionTelemetry, onClick: () -> Unit = {}) {
    val isHighRisk = HighRiskJurisdictions.contains(telemetry.countryCode)

    val baseCardColor = if (isHighRisk) AlertRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val cardColor = when {
        telemetry.isBlocked -> AlertRed.copy(alpha = 0.4f)
        telemetry.isFlagged -> AlertRed.copy(alpha = 0.3f)
        telemetry.isIgnored -> MatrixGreen.copy(alpha = 0.2f)
        else -> baseCardColor
    }

    val finalTextColor = when {
        telemetry.isBlocked || telemetry.isFlagged || isHighRisk -> AlertRed
        telemetry.isIgnored -> MatrixGreen
        else -> MatrixGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = telemetry.appIcon
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(modifier = Modifier
                    .size(48.dp)
                    .background(TextSecondary))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = telemetry.appName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = telemetry.packageName,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (telemetry.direction != null) {
                        val dirIcon = if (telemetry.direction == ConnectionDirection.OUTBOUND) "[->]" else "[<-]"
                        val dirColor = if (telemetry.direction == ConnectionDirection.OUTBOUND) MatrixGreen else TacticalAmber
                        Text(text = dirIcon, color = dirColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Text(text = telemetry.protocol, color = TacticalAmber, fontSize = 12.sp)

                    var ipDisplay = "${telemetry.destIp}:${telemetry.destPort}"
                    if (telemetry.countryCode != null && telemetry.city != null) {
                        ipDisplay += " [${telemetry.countryCode} - ${telemetry.city}]"
                    }

                    Text(
                        text = ipDisplay,
                        color = finalTextColor,
                        fontSize = 12.sp
                    )
                }
                if (telemetry.resolvedHostname != null) {
                    Text(
                        text = "Host: ${telemetry.resolvedHostname}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                }
                if (telemetry.asn != null) {
                    Text(
                        text = "ASN: ${telemetry.asn}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBottomSheet(
    telemetry: ConnectionTelemetry,
    onDismiss: () -> Unit,
    onAction: (TargetType, String, Action) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Tactical Actions", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

            HorizontalDivider(color = TextSecondary, thickness = 1.dp)

            if (telemetry.uid != -1) {
                Button(
                    onClick = { onAction(TargetType.APPLICATION, telemetry.uid.toString(), Action.BLOCK) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Block Application (UID: ${telemetry.uid})")
                }
            }

            val targetValue = telemetry.resolvedHostname ?: telemetry.destIp
            val targetType = if (telemetry.resolvedHostname != null) TargetType.DOMAIN else TargetType.IP_ADDRESS

            Button(
                onClick = { onAction(targetType, targetValue, Action.BLOCK) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
            ) {
                Text("Block ${if (targetType == TargetType.DOMAIN) "Domain" else "IP"}: $targetValue")
            }

            Button(
                onClick = { onAction(targetType, targetValue, Action.FLAG) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
            ) {
                Text("Flag Connection")
            }

            Button(
                onClick = { onAction(targetType, targetValue, Action.IGNORE) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("Add to Ignore List")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
