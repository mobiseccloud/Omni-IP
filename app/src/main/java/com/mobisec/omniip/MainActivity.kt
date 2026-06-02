package com.mobisec.omniip

import android.app.Activity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.mobisec.omniip.vpn.OmniVpnService

class MainActivity : ComponentActivity() {

    private val viewModel: TelemetryViewModel by viewModels()

    private val vpnServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, OmniVpnService::class.java)
            startService(intent)
        }
    }

    private val rulesViewModel: RulesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniIPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentTab by remember { mutableStateOf(0) }
                    val mainTabs = listOf("Telemetry", "Firewall Matrix")

                    Column(modifier = Modifier.fillMaxSize()) {
                        TopBar(
                            onStartVpn = { startVpn() },
                            onStopVpn = { stopVpn() }
                        )

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
                        } else {
                            RulesScreen(rulesViewModel)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TelemetryScreen(viewModel: TelemetryViewModel) {
        val connections by viewModel.connections.collectAsState()
        var selectedTelemetry by remember { mutableStateOf<ConnectionTelemetry?>(null) }
        var showBottomSheet by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(connections) { telemetry ->
                    TelemetryItem(telemetry) {
                        selectedTelemetry = telemetry
                        showBottomSheet = true
                    }
                }
            }
        }

        if (showBottomSheet && selectedTelemetry != null) {
            ActionBottomSheet(
                telemetry = selectedTelemetry!!,
                onDismiss = { showBottomSheet = false },
                onAction = { targetType, targetValue, action ->
                    viewModel.addRule(targetType, targetValue, action)
                    showBottomSheet = false
                }
            )
        }
    }


    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnServiceLauncher.launch(intent)
        } else {
            val startIntent = Intent(this, OmniVpnService::class.java)
            startService(startIntent)
        }
    }

    private fun stopVpn() {
        val stopIntent = Intent(this, OmniVpnService::class.java)
        stopService(stopIntent)
    }
}

@Composable
fun TopBar(onStartVpn: () -> Unit, onStopVpn: () -> Unit) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onStartVpn,
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text("START")
            }
            Button(
                onClick = onStopVpn,
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text("STOP")
            }
        }
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
