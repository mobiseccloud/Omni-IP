package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.TacticalAmber
import androidx.compose.ui.Alignment
import com.mobisec.omniip.ui.theme.AlertRed
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.VpnService
import com.mobisec.omniip.vpn.OmniVpnService
import com.mobisec.omniip.viewmodel.DashboardViewModel
import com.mobisec.omniip.viewmodel.TelemetryViewModel
import com.mobisec.omniip.ui.theme.TextSecondary
import com.mobisec.omniip.ui.theme.SurfaceLevel1
import com.mobisec.omniip.model.ConnectionDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    targetIp: String,
    terminalOutput: String,
    isExecuting: Boolean,
    isRecording: Boolean,
    pcapSize: Long,
    targetName: String,
    rxBytes: Long,
    txBytes: Long,
    activeApps: List<Pair<Int, String>>,
    isFirewallActive: Boolean = false,
    onExecuteAction: (String, String) -> Unit, // (ip, actionName)
    onToggleRecording: (Boolean, Int?) -> Unit,
    onToggleFirewall: (Boolean) -> Unit = {},
    telemetryViewModel: TelemetryViewModel
) {
    var showAppSelectionSheet by remember { mutableStateOf(false) }

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var addRuleInitialIp by remember { mutableStateOf("") }
    var addRuleInitialDomain by remember { mutableStateOf<String?>(null) }
    var addRuleInitialPackage by remember { mutableStateOf<String?>(null) }
    var addRuleInitialCountry by remember { mutableStateOf<String?>(null) }
    var addRuleInitialType by remember { mutableStateOf(com.mobisec.omniip.db.TargetType.IP_ADDRESS) }

    if (showAddRuleDialog) {
        AddRuleDialog(
            initialTargetIp = addRuleInitialIp,
            initialTargetDomain = addRuleInitialDomain,
            initialPackageName = addRuleInitialPackage,
            initialCountryCode = addRuleInitialCountry,
            initialType = addRuleInitialType,
            onDismiss = { showAddRuleDialog = false },
            onAdd = { type, value, action ->
                telemetryViewModel.addRule(type, value, action)
            }
        )
    }


    val showPinAuthDialog by viewModel.showPinAuthDialog.collectAsState()
    val pinAuthError by viewModel.pinAuthError.collectAsState()
    
    // Busy indicator state for firewall toggle
    var isFirewallToggling by remember { mutableStateOf(false) }

    LaunchedEffect(isFirewallActive) {
        isFirewallToggling = false
    }

    if (showPinAuthDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissPinAuthDialog() },
            title = { Text("Firewall Teardown Protection", color = MatrixGreen) },
            text = {
                Column {
                    Text("Enter PIN to disable firewall.", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        isError = pinAuthError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (pinAuthError) AlertRed else MatrixGreen,
                            unfocusedBorderColor = if (pinAuthError) AlertRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.submitTeardownPin(pinInput)
                    if (!viewModel.showPinAuthDialog.value) { // If auth succeeded
                        isFirewallToggling = true
                    }
                }) {
                    Text("AUTHORIZE")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.dismissPinAuthDialog() }) {
                    Text("CANCEL")
                }
            }
        )
    }

    val context = LocalContext.current


    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tactical Toggle Component (Firewall Active)
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FIREWALL", color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    
                    if (isFirewallToggling) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MatrixGreen)
                    } else {
                        Switch(
                            checked = isFirewallActive,
                            onCheckedChange = { isActive ->
                                if (!isActive) {
                                    isFirewallToggling = true
                                }
                                onToggleFirewall(isActive)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MatrixGreen,
                                checkedTrackColor = MatrixGreen.copy(alpha = 0.5f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            // Tactical Toggle Component (PCAP Record)
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("REC PCAP", color = if (isRecording) AlertRed else MatrixGreen, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    if (isRecording || pcapSize > 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRecording) "● ${pcapSize / 1024} KB" else "${pcapSize / 1024} KB", color = if (isRecording) AlertRed else TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isRecording,
                        onCheckedChange = {
                            if (it) {
                                showAppSelectionSheet = true
                            } else {
                                onToggleRecording(false, null)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AlertRed,
                            checkedTrackColor = AlertRed.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    IconButton(
                        onClick = {
                            val pcapFile = java.io.File(context.filesDir, "capture.pcap")
                            if (pcapFile.exists()) {
                                com.mobisec.omniip.core.ExportEngine.shareFile(context, pcapFile, "application/vnd.tcpdump.pcap")
                            }
                        },
                        enabled = pcapSize > 0L,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export PCAP", tint = if (pcapSize > 0L) MatrixGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Battery integrity shield
        BatteryShield(viewModel = viewModel)

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "ACTIVE CONNECTIONS", 
            color = MatrixGreen, 
            fontSize = 14.sp, 
            fontWeight = FontWeight.Bold, 
            fontFamily = FontFamily.Monospace,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Telemetry View Embedded
        val appSummaries by telemetryViewModel.appSummaries.collectAsState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(appSummaries, key = { it.uid }) { summary ->
                AppSummaryCard(
                    summary = summary,
                    onAddRuleClick = { targetIp, targetDomain, packageName, countryCode, initialType ->
                        addRuleInitialIp = targetIp
                        addRuleInitialDomain = targetDomain
                        addRuleInitialPackage = packageName
                        addRuleInitialCountry = countryCode
                        addRuleInitialType = initialType
                        showAddRuleDialog = true
                    }
                )
            }
        }
    }

    if (showAppSelectionSheet) {
        ModalBottomSheet(onDismissRequest = { showAppSelectionSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Select Target for Capture", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MatrixGreen)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        showAppSelectionSheet = false
                        onToggleRecording(true, null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("All Traffic (Global)")
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {
                    items(activeApps.size) { index ->
                        val app = activeApps[index]
                        Button(
                            onClick = {
                                showAppSelectionSheet = false
                                onToggleRecording(true, app.first)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(app.second, color = MatrixGreen)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSummaryCard(
    summary: com.mobisec.omniip.model.AppConnectionSummary,
    onAddRuleClick: (String, String?, String?, String?, com.mobisec.omniip.db.TargetType) -> Unit
) {
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
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = summary.appIcon
                if (icon != null) {
                    Image(
                        bitmap = icon.toBitmap().asImageBitmap(),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(24.dp).background(TextSecondary))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.appName, color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Package: ${summary.packageName}", color = TextSecondary, fontSize = 8.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Active: ${summary.activeConnections.size}", color = TacticalAmber, fontSize = 10.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { 
                            onAddRuleClick("", null, summary.packageName, null, com.mobisec.omniip.db.TargetType.APPLICATION)
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Add Rule", tint = MatrixGreen)
                        }
                    }
                }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(8.dp).background(SurfaceLevel1).fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(
                            selected = selectedFilter == "All",
                            onClick = { selectedFilter = "All" },
                            label = { Text("All", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == "IN",
                            onClick = { selectedFilter = "IN" },
                            label = { Text("Inbound", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == "OUT",
                            onClick = { selectedFilter = "OUT" },
                            label = { Text("Outbound", fontSize = 10.sp) }
                        )
                    }

                    val filteredConnections = summary.activeConnections.filter { 
                        selectedFilter == "All" ||
                        (selectedFilter == "IN" && it.direction == com.mobisec.omniip.model.ConnectionDirection.INBOUND_STR) ||
                        (selectedFilter == "OUT" && it.direction == com.mobisec.omniip.model.ConnectionDirection.OUTBOUND_STR)
                    }.sortedByDescending { it.lastActive }

                    if (filteredConnections.isEmpty()) {
                        Text("No active connections matching filter.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    } else {
                        filteredConnections.forEach { conn ->
                            ConnectionTelemetryRow(
                                conn = conn,
                                onAddRuleClick = onAddRuleClick
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionTelemetryRow(
    conn: com.mobisec.omniip.model.ConnectionDetails,
    onAddRuleClick: (String, String?, String?, String?, com.mobisec.omniip.db.TargetType) -> Unit
) {
    val dirColor = if (conn.direction == com.mobisec.omniip.model.ConnectionDirection.OUTBOUND_STR) MatrixGreen else TacticalAmber
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = timeFormat.format(Date(conn.lastActive))
    val isInactive = System.currentTimeMillis() - conn.lastActive > 30000

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp).alpha(if (isInactive) 0.5f else 1f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(conn.direction ?: "UNKNOWN", color = dirColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                if (isInactive) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("[INACTIVE]", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 8.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(conn.protocol, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(conn.destIp, color = TextSecondary, fontSize = 10.sp)
                if (conn.destPort > 0) {
                    Text(":${conn.destPort}", color = TacticalAmber, fontSize = 10.sp)
                }
            }
            if (!conn.domainName.isNullOrBlank()) {
                Text(conn.domainName, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 10.sp)
            }
            val locationParts = listOfNotNull(conn.city, conn.country).filter { it.isNotBlank() }
            if (locationParts.isNotEmpty()) {
                Text(locationParts.joinToString(", "), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 8.sp)
            } else if (!conn.countryCode.isNullOrBlank()) {
                Text(conn.countryCode, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 8.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text(timeString, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), fontSize = 10.sp)
                Text("${conn.bytesTx} Tx / ${conn.bytesRx} Rx", color = TextSecondary, fontSize = 8.sp)
                val statusColor = if (conn.isBlocked) AlertRed else MatrixGreen
                Text(if (conn.isBlocked) "BLOCKED" else "ALLOWED", color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                onAddRuleClick(conn.destIp, conn.domainName, null, conn.countryCode, if (conn.domainName != null) com.mobisec.omniip.db.TargetType.DOMAIN else com.mobisec.omniip.db.TargetType.IP_ADDRESS)
            }, modifier = Modifier.size(24.dp)) {
                Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Add Rule", tint = MatrixGreen)
            }
        }
    }
}

@Composable
fun AddRuleDialog(
    initialTargetIp: String,
    initialTargetDomain: String?,
    initialPackageName: String?,
    initialCountryCode: String?,
    initialType: com.mobisec.omniip.db.TargetType,
    onDismiss: () -> Unit,
    onAdd: (com.mobisec.omniip.db.TargetType, String, com.mobisec.omniip.db.Action) -> Unit
) {
    var ruleTargetType by remember { mutableStateOf(initialType) }
    var useDomain by remember { mutableStateOf(initialType == com.mobisec.omniip.db.TargetType.DOMAIN) }
    var action by remember { mutableStateOf(com.mobisec.omniip.db.Action.BLOCK) }

    val actualTargetType = if (ruleTargetType == com.mobisec.omniip.db.TargetType.IP_ADDRESS && useDomain) com.mobisec.omniip.db.TargetType.DOMAIN else ruleTargetType

    val targetValue = when (actualTargetType) {
        com.mobisec.omniip.db.TargetType.APPLICATION -> initialPackageName ?: ""
        com.mobisec.omniip.db.TargetType.IP_ADDRESS -> initialTargetIp
        com.mobisec.omniip.db.TargetType.DOMAIN -> initialTargetDomain ?: initialTargetIp
        com.mobisec.omniip.db.TargetType.GEOLOCATION -> initialCountryCode ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Rule", color = MatrixGreen) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Target Type", fontWeight = FontWeight.Bold, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(
                        onClick = { ruleTargetType = com.mobisec.omniip.db.TargetType.APPLICATION },
                        enabled = initialPackageName != null
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Menu, "Application", tint = if (ruleTargetType == com.mobisec.omniip.db.TargetType.APPLICATION) MatrixGreen else TextSecondary.copy(alpha = if (initialPackageName != null) 1f else 0.38f))
                    }
                    IconButton(onClick = { ruleTargetType = com.mobisec.omniip.db.TargetType.IP_ADDRESS }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Share, "Network", tint = if (ruleTargetType == com.mobisec.omniip.db.TargetType.IP_ADDRESS || ruleTargetType == com.mobisec.omniip.db.TargetType.DOMAIN) MatrixGreen else TextSecondary)
                    }
                    IconButton(
                        onClick = { ruleTargetType = com.mobisec.omniip.db.TargetType.GEOLOCATION },
                        enabled = initialCountryCode != null
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.LocationOn, "Geolocation", tint = if (ruleTargetType == com.mobisec.omniip.db.TargetType.GEOLOCATION) MatrixGreen else TextSecondary.copy(alpha = if (initialCountryCode != null) 1f else 0.38f))
                    }
                }

                if ((ruleTargetType == com.mobisec.omniip.db.TargetType.IP_ADDRESS || ruleTargetType == com.mobisec.omniip.db.TargetType.DOMAIN) && initialTargetDomain != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !useDomain, onClick = { useDomain = false })
                            Text("IP Address", color = TextSecondary, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = useDomain, onClick = { useDomain = true })
                            Text("Domain Name", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Text("Action", fontWeight = FontWeight.Bold, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(selected = action == com.mobisec.omniip.db.Action.BLOCK, onClick = { action = com.mobisec.omniip.db.Action.BLOCK })
                        Text("Block", color = TextSecondary, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(selected = action == com.mobisec.omniip.db.Action.FLAG, onClick = { action = com.mobisec.omniip.db.Action.FLAG })
                        Text("Flag", color = TextSecondary, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(selected = action == com.mobisec.omniip.db.Action.IGNORE, onClick = { action = com.mobisec.omniip.db.Action.IGNORE })
                        Text("Ignore", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = targetValue,
                    onValueChange = {},
                    label = { Text("Target") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onAdd(actualTargetType, targetValue, action)
                onDismiss()
            }, enabled = targetValue.isNotBlank()) { Text("Add Rule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
