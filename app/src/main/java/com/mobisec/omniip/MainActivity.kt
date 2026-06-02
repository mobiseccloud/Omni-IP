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
import com.mobisec.omniip.model.ConnectionDirection
import com.mobisec.omniip.model.ConnectionTelemetry
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.OmniIPTheme
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.ui.theme.TextSecondary
import com.mobisec.omniip.viewmodel.TelemetryViewModel
import com.mobisec.omniip.vpn.OmniVpnService

class MainActivity : ComponentActivity() {

    private val viewModel: TelemetryViewModel by viewModels()

    private val vpnServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, OmniVpnService::class.java)
            startService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniIPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val connections by viewModel.connections.collectAsState()

                    Column(modifier = Modifier.fillMaxSize()) {
                        TopBar(
                            onStartVpn = { startVpn() },
                            onStopVpn = { stopVpn() }
                        )

                        HorizontalDivider(color = MatrixGreen, thickness = 1.dp)

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(connections) { telemetry ->
                                TelemetryItem(telemetry)
                            }
                        }
                    }
                }
            }
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
fun TelemetryItem(telemetry: ConnectionTelemetry) {
    val isHighRisk = HighRiskJurisdictions.contains(telemetry.countryCode)

    val cardColor = if (isHighRisk) AlertRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        color = if (isHighRisk) AlertRed else MatrixGreen,
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
