package com.mobisec.omniip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.model.DiscoveredDevice
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.ui.theme.TextSecondary
import com.mobisec.omniip.viewmodel.LanScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScannerScreen(
    viewModel: LanScannerViewModel,
    onNavigateToDashboard: (String, String) -> Unit // IP and action
) {
    val devices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAN DISCOVERY",
                color = MatrixGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Button(
                onClick = { viewModel.executeLanSweep() },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
            ) {
                Text(if (isScanning) "SCANNING..." else "SWEEP LAN")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning && devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MatrixGreen)
            }
        } else if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No devices found. Press SWEEP LAN.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(device) {
                        selectedDevice = device
                        showBottomSheet = true
                    }
                }
            }
        }
    }

    if (showBottomSheet && selectedDevice != null) {
        DeviceActionBottomSheet(
            device = selectedDevice!!,
            onDismiss = { showBottomSheet = false },
            onAction = { ip, action ->
                showBottomSheet = false
                onNavigateToDashboard(ip, action)
            }
        )
    }
}

@Composable
fun DeviceItem(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Device",
                tint = MatrixGreen,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = device.ipAddress, color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "MAC: ${device.macAddress}", color = TextSecondary, fontSize = 12.sp)
                Text(text = device.vendor, color = TacticalAmber, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionBottomSheet(
    device: DiscoveredDevice,
    onDismiss: () -> Unit,
    onAction: (String, String) -> Unit // (ip, actionName)
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Target Actions: ${device.ipAddress}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

            HorizontalDivider(color = TextSecondary, thickness = 1.dp)

            Button(
                onClick = { onAction(device.ipAddress, "PING") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("Ping Target")
            }

            Button(
                onClick = { onAction(device.ipAddress, "TRACEROUTE") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
            ) {
                Text("Traceroute")
            }

            // Port Scan implementation with expander
            var expandScanOptions by remember { mutableStateOf(false) }

            Button(
                onClick = { expandScanOptions = !expandScanOptions },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Port Scan...")
            }

            if (expandScanOptions) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                    Button(
                        onClick = { onAction(device.ipAddress, "PORTSCAN_FAST") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Fast Scan (Top 100)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onAction(device.ipAddress, "PORTSCAN_DEEP") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Deep Scan (Requires Premium)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
