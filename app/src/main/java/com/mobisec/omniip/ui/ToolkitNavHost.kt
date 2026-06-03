package com.mobisec.omniip.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ToolkitNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "toolkit") {
        composable("toolkit") { ToolkitScreen(navController) }
        composable("ping") { PingScreen() }
        composable("whois") { WhoisScreen() }
        composable("traceroute") { TracerouteScreen() }
        composable("dns") { DnsLookupScreen() }
        composable("portscan") { PortScannerScreen() }
        composable("ipcalc") { IpCalculatorScreen() }
        composable("connlog") { ConnectionLogScreen() }
        composable("router") { RouterSetupScreen() }
        composable("ipconv") { IpConverterScreen() }
        composable("wifi") { WifiScannerScreen() }
        composable("netstats") { NetworkStatsScreen() }
    }
}
