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
fun DnsThreatFeedsScreen() {
    var customDomain by remember { mutableStateOf("") }
    var domainsList by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("DNS THREAT FEEDS (SINKHOLE)", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = customDomain,
            onValueChange = { customDomain = it },
            label = { Text("Import Domain (e.g. ad.doubleclick.net)", color = MatrixGreen) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MatrixGreen, unfocusedTextColor = MatrixGreen, focusedBorderColor = MatrixGreen, unfocusedBorderColor = MatrixGreen),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (customDomain.isNotBlank()) {
                    val newDomains = domainsList.toMutableList()
                    newDomains.add(customDomain)
                    domainsList = newDomains
                    customDomain = ""

                    scope.launch(Dispatchers.IO) {
                        val c1 = 0x87c37b91114253d5UL
                        val c2 = 0x4cf5ad432745937fUL
                        val hashes = LongArray(newDomains.size)

                        for (i in newDomains.indices) {
                            val domain = newDomains[i].lowercase()
                            var h1: ULong = 0u
                            var h2: ULong = 0u
                            val data = domain.toByteArray()
                            val len = data.size
                            val nblocks = len / 16

                            for (j in 0 until nblocks) {
                                var k1 = (data[j * 16 + 0].toULong() and 0xFFu) or ((data[j * 16 + 1].toULong() and 0xFFu) shl 8) or ((data[j * 16 + 2].toULong() and 0xFFu) shl 16) or ((data[j * 16 + 3].toULong() and 0xFFu) shl 24) or ((data[j * 16 + 4].toULong() and 0xFFu) shl 32) or ((data[j * 16 + 5].toULong() and 0xFFu) shl 40) or ((data[j * 16 + 6].toULong() and 0xFFu) shl 48) or ((data[j * 16 + 7].toULong() and 0xFFu) shl 56)
                                var k2 = (data[j * 16 + 8].toULong() and 0xFFu) or ((data[j * 16 + 9].toULong() and 0xFFu) shl 8) or ((data[j * 16 + 10].toULong() and 0xFFu) shl 16) or ((data[j * 16 + 11].toULong() and 0xFFu) shl 24) or ((data[j * 16 + 12].toULong() and 0xFFu) shl 32) or ((data[j * 16 + 13].toULong() and 0xFFu) shl 40) or ((data[j * 16 + 14].toULong() and 0xFFu) shl 48) or ((data[j * 16 + 15].toULong() and 0xFFu) shl 56)

                                k1 *= c1
                                k1 = (k1 shl 31) or (k1 shr (64 - 31))
                                k1 *= c2
                                h1 = h1 xor k1

                                h1 = (h1 shl 27) or (h1 shr (64 - 27))
                                h1 += h2
                                h1 = h1 * 5u + 0x52dce729u

                                k2 *= c2
                                k2 = (k2 shl 33) or (k2 shr (64 - 33))
                                k2 *= c1
                                h2 = h2 xor k2

                                h2 = (h2 shl 31) or (h2 shr (64 - 31))
                                h2 += h1
                                h2 = h2 * 5u + 0x38495ab5u
                            }

                            var k1: ULong = 0u
                            var k2: ULong = 0u
                            val tailOffset = nblocks * 16

                            when (len and 15) {
                                15 -> k2 = k2 xor ((data[tailOffset + 14].toULong() and 0xFFu) shl 48)
                            }
                            if ((len and 15) >= 14) k2 = k2 xor ((data[tailOffset + 13].toULong() and 0xFFu) shl 40)
                            if ((len and 15) >= 13) k2 = k2 xor ((data[tailOffset + 12].toULong() and 0xFFu) shl 32)
                            if ((len and 15) >= 12) k2 = k2 xor ((data[tailOffset + 11].toULong() and 0xFFu) shl 24)
                            if ((len and 15) >= 11) k2 = k2 xor ((data[tailOffset + 10].toULong() and 0xFFu) shl 16)
                            if ((len and 15) >= 10) k2 = k2 xor ((data[tailOffset + 9].toULong() and 0xFFu) shl 8)
                            if ((len and 15) >= 9) {
                                k2 = k2 xor (data[tailOffset + 8].toULong() and 0xFFu)
                                k2 *= c2
                                k2 = (k2 shl 33) or (k2 shr (64 - 33))
                                k2 *= c1
                                h2 = h2 xor k2
                            }

                            if ((len and 15) >= 8) k1 = k1 xor ((data[tailOffset + 7].toULong() and 0xFFu) shl 56)
                            if ((len and 15) >= 7) k1 = k1 xor ((data[tailOffset + 6].toULong() and 0xFFu) shl 48)
                            if ((len and 15) >= 6) k1 = k1 xor ((data[tailOffset + 5].toULong() and 0xFFu) shl 40)
                            if ((len and 15) >= 5) k1 = k1 xor ((data[tailOffset + 4].toULong() and 0xFFu) shl 32)
                            if ((len and 15) >= 4) k1 = k1 xor ((data[tailOffset + 3].toULong() and 0xFFu) shl 24)
                            if ((len and 15) >= 3) k1 = k1 xor ((data[tailOffset + 2].toULong() and 0xFFu) shl 16)
                            if ((len and 15) >= 2) k1 = k1 xor ((data[tailOffset + 1].toULong() and 0xFFu) shl 8)
                            if ((len and 15) >= 1) {
                                k1 = k1 xor (data[tailOffset + 0].toULong() and 0xFFu)
                                k1 *= c1
                                k1 = (k1 shl 31) or (k1 shr (64 - 31))
                                k1 *= c2
                                h1 = h1 xor k1
                            }

                            h1 = h1 xor len.toULong()
                            h2 = h2 xor len.toULong()
                            h1 += h2
                            h2 += h1

                            h1 = h1 xor (h1 shr 33)
                            h1 *= 0xff51afd7ed558ccdUL
                            h1 = h1 xor (h1 shr 33)
                            h1 *= 0xc4ceb9fe1a85ec53UL
                            h1 = h1 xor (h1 shr 33)

                            h2 = h2 xor (h2 shr 33)
                            h2 *= 0xff51afd7ed558ccdUL
                            h2 = h2 xor (h2 shr 33)
                            h2 *= 0xc4ceb9fe1a85ec53UL
                            h2 = h2 xor (h2 shr 33)

                            h1 += h2
                            h2 += h1

                            hashes[i] = h1.toLong()
                        }

                        NativeEngine.syncDnsBlocklist(hashes)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("SYNC TO NATIVE C++ ENCLAVE")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(domainsList) { domain ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(domain, modifier = Modifier.padding(12.dp), color = MatrixGreen)
                }
            }
        }
    }
}

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
fun PortScannerScreen(
    viewModel: com.mobisec.omniip.viewmodel.PortScannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onRequirePremium: () -> Unit = {}
) {
    val target by viewModel.target.collectAsState()
    val output by viewModel.output.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter=painterResource(id=com.mobisec.omniip.R.drawable.ic_recon_crosshair), contentDescription=null, tint=MatrixGreen, modifier=Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("PORT SCANNER MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { viewModel.updateTarget(it) },
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
                onClick = { viewModel.startFastScan() },
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("Fast Scan", color = PureBlack)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.startDeepScan() },
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

        val context = androidx.compose.ui.platform.LocalContext.current
        Button(
            onClick = {
                val csvFile = viewModel.exportLogsToCsv(context)
                com.mobisec.omniip.core.ExportEngine.shareFile(context, csvFile, "text/csv")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("EXPORT DATA")
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
                        val prefix = if (log.action == "BLOCK") "[BLOCKED] " else if (log.action == "FLAG") "[FLAGGED] " else ""
                        Text("$prefix${log.appName} -> ${log.destIp}:${log.destPort}", color = textColor, fontSize = 14.sp)
                        if (log.countryCode != null || log.city != null || log.asn != null) {
                            val geoStr = listOfNotNull(log.countryCode, log.city, log.asn).joinToString(" - ")
                            Text(geoStr, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
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
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("WIFI SCANNER MODULE", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    output = "Scanning WiFi networks..."
                    output = withContext(Dispatchers.IO) {
                        try {
                            val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                            val scanResults = wifiManager.scanResults
                            if (scanResults.isEmpty()) {
                                "> ERROR: Hardware scan returned no results. Ensure Location Services are enabled."
                            } else {
                                scanResults.joinToString("\n\n") { result ->
                                    val ssid = if (result.SSID.isNullOrEmpty()) "[Hidden SSID]" else result.SSID
                                    val bssid = result.BSSID ?: "Unknown BSSID"
                                    val level = result.level
                                    val capabilities = result.capabilities ?: "Unknown"
                                    "SSID: $ssid (BSSID: $bssid)\nSignal: $level dBm\nSecurity: $capabilities"
                                }
                            }
                        } catch (e: Exception) {
                            "> ERROR: Failed to scan WiFi networks: ${e.message}"
                        }
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
                            Text("RX: ${formatBytes(iface.rxBytes)}", color = MatrixGreen.copy(alpha = 0.6f), fontSize = 14.sp)
                            Text("TX: ${formatBytes(iface.txBytes)}", color = MatrixGreen.copy(alpha = 0.6f), fontSize = 14.sp)
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
