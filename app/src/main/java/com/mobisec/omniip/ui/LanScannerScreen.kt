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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    viewModel: LanScannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val devices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanFeedback by viewModel.scanFeedback.collectAsState()

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

        if (scanFeedback != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = scanFeedback!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (isScanning && devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MatrixGreen, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pinging subnet...", color = MatrixGreen, fontWeight = FontWeight.Bold)
                }
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
            onDismiss = { showBottomSheet = false }
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
                if (device.hostname != "Unknown") {
                    Text(text = device.hostname, color = Color.White, fontSize = 14.sp)
                }
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Target Actions: ${device.ipAddress}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MAC: ${device.macAddress}", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            HorizontalDivider(color = TextSecondary, thickness = 1.dp)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
