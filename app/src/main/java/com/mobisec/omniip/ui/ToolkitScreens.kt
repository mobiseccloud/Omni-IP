package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.PureBlack

@Composable
fun GenericStubScreen(title: String) {
    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text(title, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        Text("> Module initialized.", color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Text("> Awaiting operator input...", color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable fun PingScreen() { GenericStubScreen("PING MODULE") }
@Composable fun WhoisScreen() { GenericStubScreen("WHOIS MODULE") }
@Composable fun TracerouteScreen() { GenericStubScreen("TRACEROUTE MODULE") }
@Composable fun DnsLookupScreen() { GenericStubScreen("DNS LOOKUP MODULE") }
@Composable fun PortScannerScreen() { GenericStubScreen("PORT SCANNER MODULE") }
@Composable fun IpCalculatorScreen() { GenericStubScreen("IP CALCULATOR MODULE") }
@Composable fun ConnectionLogScreen() { GenericStubScreen("CONNECTION LOG MODULE") }
@Composable fun RouterSetupScreen() { GenericStubScreen("ROUTER SETUP MODULE") }
@Composable fun IpConverterScreen() { GenericStubScreen("IP CONVERTER MODULE") }
@Composable fun WifiScannerScreen() { GenericStubScreen("WIFI SCANNER MODULE") }
@Composable fun NetworkStatsScreen() { GenericStubScreen("NETWORK STATS MODULE") }
