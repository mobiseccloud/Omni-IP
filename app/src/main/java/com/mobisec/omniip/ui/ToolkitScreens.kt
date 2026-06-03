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
        Text("PING MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
        Text("TRACEROUTE MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
        Text("PORT SCANNER MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
                    if (NativeEngine.isPremiumUnlockedNative()) {
                        coroutineScope.launch {
                            output = "Starting Deep Scan on $target..."
                            output = withContext(Dispatchers.IO) {
                                NativeEngine.executeNmapScan("$target -p- -A")
                            }
                        }
                    } else {
                        onRequirePremium()
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

@Composable fun IpCalculatorScreen() { GenericStubScreen("IP CALCULATOR MODULE") }
@Composable fun ConnectionLogScreen() { GenericStubScreen("CONNECTION LOG MODULE") }
@Composable fun RouterSetupScreen() { GenericStubScreen("ROUTER SETUP MODULE") }
@Composable fun IpConverterScreen() { GenericStubScreen("IP CONVERTER MODULE") }

@Composable
fun WifiScannerScreen(onRequirePremium: () -> Unit = {}) {
    var output by rememberSaveable { mutableStateOf("Ready to scan WiFi networks...") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("WIFI SCANNER MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (NativeEngine.isPremiumUnlockedNative()) {
                    coroutineScope.launch {
                        output = "Scanning WiFi networks..."
                        // Simulated WiFi scan
                        output = withContext(Dispatchers.IO) {
                            "SSID: Corporate-Secure (BSSID: 00:11:22:33:44:55)\nSignal: -45 dBm\nSecurity: WPA3\n\nSSID: Guest (BSSID: 00:11:22:33:44:56)\nSignal: -70 dBm\nSecurity: Open"
                        }
                    }
                } else {
                    onRequirePremium()
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

@Composable fun NetworkStatsScreen() { GenericStubScreen("NETWORK STATS MODULE") }
