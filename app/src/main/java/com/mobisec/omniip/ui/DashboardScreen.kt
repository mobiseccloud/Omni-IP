package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isFirewallToggling = true
            val intent = Intent(context, OmniVpnService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            onToggleFirewall(true)
        } else {
            isFirewallToggling = false
        }
    }

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
        
        Text("DASHBOARD", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        // Tactical Toggle Component (Firewall Active)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FIREWALL ENGINE", color = MatrixGreen, fontWeight = FontWeight.Bold)
                    Text(if (isFirewallActive) "Intercepting & Filtering" else "System Exposed", 
                         color = if (isFirewallActive) MatrixGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), 
                         fontSize = 12.sp)
                }
                
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

        // Tactical Toggle Component (PCAP Record) - Styled exactly like Firewall toggle
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecording) {
                            Box(modifier = Modifier.size(8.dp).background(AlertRed.copy(alpha = pulseAlpha), shape = androidx.compose.foundation.shape.CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("TRAFFIC CAPTURE (PCAP)", color = if (isRecording) AlertRed else MatrixGreen, fontWeight = FontWeight.Bold)
                    }
                    Text(if (isRecording) "Recording: $targetName (${pcapSize}B)" else "PCAP Disabled", 
                         color = if (isRecording) AlertRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), 
                         fontSize = 12.sp)
                }
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
            }
            if (isRecording) {
                Button(
                    onClick = {
                        val pcapFile = java.io.File(context.filesDir, "capture.pcap")
                        if (pcapFile.exists()) {
                            com.mobisec.omniip.core.ExportEngine.shareFile(context, pcapFile, "application/vnd.tcpdump.pcap")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("EXPORT DATA")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Battery integrity shield
        BatteryShield(viewModel = viewModel)

        Spacer(modifier = Modifier.height(8.dp))
        Text("ACTIVE CONNECTIONS", color = MatrixGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))

        // Telemetry View Embedded
        val appSummaries by telemetryViewModel.appSummaries.collectAsState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(appSummaries, key = { it.uid }) { summary ->
                AppSummaryCard(summary)
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
                    Box(modifier = Modifier.size(48.dp).background(TextSecondary))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.appName, color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Package: ${summary.packageName}", color = TextSecondary, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active: ${summary.activeConnections.size}", color = TacticalAmber, fontSize = 12.sp)
                    }
                }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(12.dp).background(SurfaceLevel1).fillMaxWidth()) {
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
                            ConnectionTelemetryRow(conn)
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionTelemetryRow(conn: com.mobisec.omniip.model.ConnectionDetails) {
    val dirColor = if (conn.direction == com.mobisec.omniip.model.ConnectionDirection.OUTBOUND_STR) MatrixGreen else TacticalAmber
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = timeFormat.format(Date(conn.lastActive))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(conn.direction ?: "UNKNOWN", color = dirColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(conn.protocol, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(timeString, color = TextSecondary, fontSize = 10.sp)
            }
            Text("${conn.destIp}:${conn.destPort}", color = MatrixGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        
        Column(horizontalAlignment = Alignment.End) {
            val statusColor = if (conn.isBlocked) AlertRed else MatrixGreen
            Text(if (conn.isBlocked) "BLOCKED" else "ALLOWED", color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (conn.countryCode != null) {
                Text(conn.countryCode, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), fontSize = 10.sp)
            }
        }
    }
}
