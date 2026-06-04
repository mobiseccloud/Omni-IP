package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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


import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.VpnService
import com.mobisec.omniip.vpn.OmniVpnService
import com.mobisec.omniip.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    targetIp: String,
    initialAction: String,
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
    onToggleFirewall: (Boolean) -> Unit = {}
) {
    var ipInput by remember { mutableStateOf(targetIp) }
    var actionInput by remember { mutableStateOf(initialAction) }
    var showAppSelectionSheet by remember { mutableStateOf(false) }

    val showPinAuthDialog by viewModel.showPinAuthDialog.collectAsState()
    val pinAuthError by viewModel.pinAuthError.collectAsState()

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
                            unfocusedBorderColor = if (pinAuthError) AlertRed else Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.submitTeardownPin(pinInput) }) {
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
            val intent = Intent(context, OmniVpnService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            onToggleFirewall(true)
        }
    }

    LaunchedEffect(initialAction, targetIp) {
        if (targetIp.isNotEmpty() && initialAction.isNotEmpty() && initialAction != "NONE") {
            ipInput = targetIp
            actionInput = initialAction
            onExecuteAction(targetIp, initialAction)
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Tactical Toggle Component
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFirewallActive) "FIREWALL: ENGAGED" else "FIREWALL: STANDBY",
                    color = if (isFirewallActive) MatrixGreen else Color.DarkGray,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 18.sp
                )
                Switch(
                    checked = isFirewallActive,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            val intent = VpnService.prepare(context)
                            if (intent != null) {
                                vpnLauncher.launch(intent)
                            } else {
                                val startIntent = Intent(context, OmniVpnService::class.java)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(startIntent)
                                } else {
                                    context.startService(startIntent)
                                }
                                onToggleFirewall(true)
                            }
                        } else {
                            viewModel.requestStopFirewall()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MatrixGreen,
                        checkedTrackColor = MatrixGreen.copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }

        if (isRecording) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ACTIVE RECORDING", color = AlertRed, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("TARGET: $targetName", color = MatrixGreen, fontSize = 14.sp)
                    val sizeMb = pcapSize / (1024.0 * 1024.0)
                    Text("PCAP SIZE: ${String.format("%.2f MB", sizeMb)}", color = MatrixGreen, fontSize = 14.sp)
                    Text("RX: $rxBytes Bytes | TX: $txBytes Bytes", color = TacticalAmber, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val pcapFile = java.io.File(context.filesDir, "capture.pcap")
                            if (pcapFile.exists()) {
                                com.mobisec.omniip.core.ExportEngine.shareFile(context, pcapFile, "application/vnd.tcpdump.pcap")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
                    ) {
                        Text("EXPORT DATA")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DASHBOARD", color = MatrixGreen, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    Box(modifier = Modifier.size(12.dp).background(AlertRed.copy(alpha = pulseAlpha), shape = androidx.compose.foundation.shape.CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
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
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Record Traffic", color = if (isRecording) AlertRed else Color.Gray, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ipInput,
            onValueChange = { ipInput = it },
            label = { Text("Target IP", color = TacticalAmber) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixGreen,
                unfocusedTextColor = MatrixGreen,
                focusedBorderColor = TacticalAmber,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actionInput = "PING"; onExecuteAction(ipInput, "PING") },
                enabled = !isExecuting && ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("PING")
            }
            Button(
                onClick = { actionInput = "TRACEROUTE"; onExecuteAction(ipInput, "TRACEROUTE") },
                enabled = !isExecuting && ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
            ) {
                Text("TRACEROUTE")
            }
            Button(
                onClick = { actionInput = "PORTSCAN_FAST"; onExecuteAction(ipInput, "PORTSCAN_FAST") },
                enabled = !isExecuting && ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("SCAN")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(8.dp)
        ) {
            Text(
                text = if (isExecuting && terminalOutput.isEmpty()) "Executing..." else terminalOutput,
                color = MatrixGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
    }

    if (showAppSelectionSheet) {
        ModalBottomSheet(onDismissRequest = { showAppSelectionSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Select Target for Capture", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp, color = MatrixGreen)
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
                androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                androidx.compose.foundation.lazy.LazyColumn {
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
