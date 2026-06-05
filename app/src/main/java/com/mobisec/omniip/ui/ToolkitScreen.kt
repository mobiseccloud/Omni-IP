package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.SurfaceLevel1
import com.mobisec.omniip.ui.theme.TacticalAmber

@Composable
fun ToolkitScreen(navController: NavController) {
    val tools = listOf(
        "Ping" to "ping",
        "Whois" to "whois",
        "Traceroute" to "traceroute",
        "DNS Lookup" to "dns",
        "Port Scanner" to "portscan",
        "IP Calculator" to "ipcalc",
        "Connection Log" to "connlog",
        "Router Setup" to "router",
        "IP Converter" to "ipconv",
        "WiFi Scanner" to "wifi",
        "Network Stats" to "netstats",
        "DNS Threat Feeds" to "dnsthreats",
        "Architecture Doc" to "archdoc"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("TACTICAL TOOLKIT", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tools) { (name, route) ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(route) },
                    colors = CardDefaults.cardColors(containerColor = SurfaceLevel1)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(name, color = TacticalAmber, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
