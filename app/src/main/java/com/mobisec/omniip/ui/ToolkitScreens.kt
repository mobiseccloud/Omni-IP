package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.PureBlack
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.core.NativeEngine
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import org.xbill.DNS.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobisec.omniip.viewmodel.ConnectionLogViewModel
import com.mobisec.omniip.viewmodel.NetworkStatsViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.viewmodel.IpCalculatorViewModel
import com.mobisec.omniip.viewmodel.IpConverterViewModel
import com.mobisec.omniip.viewmodel.RouterSetupViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView


@Composable
fun GenericStubScreen(title: String) {
    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text(title, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        Text("> Module initialized.", color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Text("> Awaiting operator input...", color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun PingScreen() {
    var target by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter=painterResource(id=com.mobisec.omniip.R.drawable.ic_tactical_terminal), contentDescription=null, tint=MatrixGreen, modifier=Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("PING MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target IP/Domain", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixGreen,
                unfocusedTextColor = MatrixGreen,
                focusedBorderColor = MatrixGreen,
                unfocusedBorderColor = MatrixGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    output = "Pinging $target..."
                    output = withContext(Dispatchers.IO) {
                        NativeEngine.executeRawPing(target)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("Execute Ping", color = PureBlack)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(output, color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun WhoisScreen() {
    var target by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("WHOIS MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target IP/Domain", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixGreen,
                unfocusedTextColor = MatrixGreen,
                focusedBorderColor = MatrixGreen,
                unfocusedBorderColor = MatrixGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    output = "Looking up WHOIS for $target..."
                    // Simulated Whois for now as no engine is specified
                    output = withContext(Dispatchers.IO) {
                        "WHOIS results for $target:\nDomain Name: $target\nRegistry Domain ID: 123456789_DOMAIN_COM-VRSN\nRegistrar WHOIS Server: whois.registrar.com\nRegistrar URL: http://www.registrar.com"
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("Execute WHOIS", color = PureBlack)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(output, color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun TracerouteScreen() {
    var target by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter=painterResource(id=com.mobisec.omniip.R.drawable.ic_tactical_terminal), contentDescription=null, tint=MatrixGreen, modifier=Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("TRACEROUTE MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target IP/Domain", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixGreen,
                unfocusedTextColor = MatrixGreen,
                focusedBorderColor = MatrixGreen,
                unfocusedBorderColor = MatrixGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    output = "Tracing route to $target..."
                    output = withContext(Dispatchers.IO) {
                        NativeEngine.executeTraceroute(target)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("Execute Traceroute", color = PureBlack)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(output, color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun DnsLookupScreen() {
    var target by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("DNS LOOKUP MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Domain", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixGreen,
                unfocusedTextColor = MatrixGreen,
                focusedBorderColor = MatrixGreen,
                unfocusedBorderColor = MatrixGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    output = "Looking up $target..."
                    output = withContext(Dispatchers.IO) {
                        try {
                            val lookup = Lookup(target, Type.A)
                            lookup.run()
                            if (lookup.result == Lookup.SUCCESSFUL) {
                                val records = lookup.answers
                                records?.joinToString("\n") { it.toString() } ?: "No records found"
                            } else {
                                "Lookup failed: ${lookup.errorString}"
                            }
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("Execute DNS Lookup", color = PureBlack)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(output, color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun PortScannerScreen(onRequirePremium: () -> Unit = {}) {
    var target by rememberSaveable { mutableStateOf("") }
    var output by rememberSaveable { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter=painterResource(id=com.mobisec.omniip.R.drawable.ic_recon_crosshair), contentDescription=null, tint=MatrixGreen, modifier=Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("PORT SCANNER MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target IP", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixGreen,
                unfocusedTextColor = MatrixGreen,
                focusedBorderColor = MatrixGreen,
                unfocusedBorderColor = MatrixGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(
                onClick = {
                    coroutineScope.launch {
                        output = "Starting Fast Scan on $target..."
                        output = withContext(Dispatchers.IO) {
                            NativeEngine.executeNmapScan(target)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("Fast Scan", color = PureBlack)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        output = "Starting Deep Scan on $target..."
                        output = withContext(Dispatchers.IO) {
                            NativeEngine.executeNmapScan("$target -p- -A")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
            ) {
                Text("Deep Scan (Premium)", color = PureBlack)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(output, color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun IpCalculatorScreen(viewModel: IpCalculatorViewModel = viewModel()) {
    var ip by remember { mutableStateOf("") }
    var cidr by remember { mutableStateOf("") }
    val networkAddress by viewModel.networkAddress.collectAsState()
    val broadcastAddress by viewModel.broadcastAddress.collectAsState()
    val totalHosts by viewModel.totalHosts.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("IP CALCULATOR MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it; viewModel.calculate(ip, cidr.toIntOrNull() ?: -1) },
            label = { Text("IP Address", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MatrixGreen, unfocusedTextColor = MatrixGreen, focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreen),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = cidr,
            onValueChange = { cidr = it; viewModel.calculate(ip, cidr.toIntOrNull() ?: -1) },
            label = { Text("CIDR (e.g., 24)", color = MatrixGreen) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MatrixGreen, unfocusedTextColor = MatrixGreen, focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreen),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Network Address: $networkAddress", color = TacticalAmber, fontSize = 16.sp)
        Text("Broadcast Address: $broadcastAddress", color = TacticalAmber, fontSize = 16.sp)
        Text("Total Usable Hosts: $totalHosts", color = TacticalAmber, fontSize = 16.sp)
    }
}
@Composable
fun ConnectionLogScreen(viewModel: ConnectionLogViewModel = viewModel()) {
    val logs by viewModel.logs.collectAsState()
    val selectedActions by viewModel.selectedActions.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("CONNECTION LOG MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("ALLOW", "BLOCK", "FLAG").forEach { action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(
                        value = selectedActions.contains(action),
                        onValueChange = { viewModel.toggleActionFilter(action) }
                    )
                ) {
                    Checkbox(
                        checked = selectedActions.contains(action),
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = MatrixGreen)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(action, color = MatrixGreen, fontSize = 14.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                val cardColor = when (log.action) {
                    "BLOCK" -> AlertRed.copy(alpha = 0.2f)
                    "FLAG" -> TacticalAmber.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                }
                val textColor = when (log.action) {
                    "BLOCK" -> AlertRed
                    "FLAG" -> TacticalAmber
                    else -> MatrixGreen
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.appName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(log.action, color = textColor, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(painter=painterResource(id=com.mobisec.omniip.R.drawable.ic_firewall_block), contentDescription=null, tint=textColor, modifier=Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${log.destIp}:${log.destPort}", color = textColor, fontSize = 14.sp)
                        if (log.countryCode != null || log.city != null || log.asn != null) {
                            val geoStr = listOfNotNull(log.countryCode, log.city, log.asn).joinToString(" - ")
                            Text(geoStr, color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun RouterSetupScreen(viewModel: RouterSetupViewModel = viewModel()) {
    val gatewayIp by viewModel.gatewayIp.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchGateway()
    }

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("ROUTER SETUP MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        if (gatewayIp == null) {
            Text("Unable to detect gateway IP. Please ensure you are connected to WiFi.", color = AlertRed, fontSize = 16.sp)
        } else {
            Text("Gateway IP: $gatewayIp", color = MatrixGreen, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            loadUrl("http://$gatewayIp")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
@Composable
fun IpConverterScreen(viewModel: IpConverterViewModel = viewModel()) {
    val ipv4 by viewModel.ipv4.collectAsState()
    val ipv6 by viewModel.ipv6.collectAsState()
    val hex by viewModel.hex.collectAsState()
    val decimal by viewModel.decimal.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("IP CONVERTER MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ipv4,
            onValueChange = { viewModel.convertFromIpv4(it) },
            label = { Text("IPv4 Address", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MatrixGreen, unfocusedTextColor = MatrixGreen, focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreen),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = decimal,
            onValueChange = { viewModel.convertFromDecimal(it) },
            label = { Text("Decimal", color = MatrixGreen) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MatrixGreen, unfocusedTextColor = MatrixGreen, focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreen),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { viewModel.convertFromHex(it) },
            label = { Text("Hexadecimal", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MatrixGreen, unfocusedTextColor = MatrixGreen, focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreen),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("IPv6: $ipv6", color = TacticalAmber, fontSize = 16.sp)
    }
}

@Composable
fun WifiScannerScreen(onRequirePremium: () -> Unit = {}) {
    var output by rememberSaveable { mutableStateOf("Ready to scan WiFi networks...") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("WIFI SCANNER MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    output = "Scanning WiFi networks..."
                    // Simulated WiFi scan
                    output = withContext(Dispatchers.IO) {
                        "SSID: Corporate-Secure (BSSID: 00:11:22:33:44:55)\nSignal: -45 dBm\nSecurity: WPA3\n\nSSID: Guest (BSSID: 00:11:22:33:44:56)\nSignal: -70 dBm\nSecurity: Open"
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("Scan WiFi", color = PureBlack)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(output, color = MatrixGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun NetworkStatsScreen(viewModel: NetworkStatsViewModel = viewModel()) {
    val interfaces by viewModel.interfaces.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("NETWORK STATS MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(interfaces) { iface ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(iface.name, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RX: ${formatBytes(iface.rxBytes)}", color = Color.LightGray, fontSize = 14.sp)
                            Text("TX: ${formatBytes(iface.txBytes)}", color = Color.LightGray, fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Speed: ${formatBytes(iface.rxSpeed)}/s", color = MatrixGreen, fontSize = 14.sp)
                            Text("Speed: ${formatBytes(iface.txSpeed)}/s", color = MatrixGreen, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
