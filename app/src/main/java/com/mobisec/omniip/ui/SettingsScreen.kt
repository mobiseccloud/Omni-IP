package com.mobisec.omniip.ui

import kotlinx.coroutines.launch

import android.content.Context
import android.content.Intent
import com.mobisec.omniip.DocumentViewerActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.ui.theme.SurfaceLevel1
import com.mobisec.omniip.ui.theme.PureBlack
import java.io.File
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * F-07 fix: Singleton wrapper for OSINT API keys backed by EncryptedSharedPreferences.
 * Keys are stored using AES256-GCM/SIV so they cannot be read from a rooted device
 * by simply inspecting the shared_prefs XML file.
 */
object OsintPreferences {
    private const val FILE_NAME = "osint_prefs_encrypted"

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAlienVaultKey(context: Context): String =
        getPrefs(context).getString("alienvault_key", "") ?: ""

    fun setAlienVaultKey(context: Context, key: String) =
        getPrefs(context).edit().putString("alienvault_key", key).apply()

    fun getAbuseIpDbKey(context: Context): String =
        getPrefs(context).getString("abuseipdb_key", "") ?: ""

    fun setAbuseIpDbKey(context: Context, key: String) =
        getPrefs(context).edit().putString("abuseipdb_key", key).apply()
}
@Composable
fun SettingsScreen(onShowArchitectureDoc: () -> Unit = {}) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("threat_feeds", Context.MODE_PRIVATE)

    var adTrackerEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ad_tracker_enabled", false)) }
    var adTrackerAction by remember { mutableStateOf(Action.valueOf(sharedPrefs.getString("ad_tracker_action", Action.BLOCK.name) ?: Action.BLOCK.name)) }

    var malwareEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("malware_enabled", false)) }
    var malwareAction by remember { mutableStateOf(Action.valueOf(sharedPrefs.getString("malware_action", Action.BLOCK.name) ?: Action.BLOCK.name)) }

    // F-07 fix: API keys loaded from EncryptedSharedPreferences via OsintPreferences singleton
    var alienVaultKey by remember { mutableStateOf(OsintPreferences.getAlienVaultKey(context)) }
    var abuseIpDbKey by remember { mutableStateOf(OsintPreferences.getAbuseIpDbKey(context)) }

    // F-04 (doc drift) fix: GeoIP existence check uses cacheDir to match where OmniVpnService writes it
    var geoIpCityExists by remember { mutableStateOf(File(context.cacheDir, "GeoLite2-City.mmdb").exists()) }
    var geoIpAsnExists by remember { mutableStateOf(File(context.cacheDir, "GeoLite2-ASN.mmdb").exists()) }
    var ouiExists by remember { mutableStateOf(File(context.filesDir, "oui.txt").exists()) }
    var malwareFeedExists by remember { mutableStateOf(File(context.filesDir, "threat_bloom.bin").exists()) }

    val securityPrefs = remember { com.mobisec.omniip.core.SecurityPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBatteryOptimizations by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }


    var pinLockEnabled by remember { mutableStateOf(securityPrefs.isPinLockEnabled()) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    if (showSetPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("Set Protection PIN", color = MatrixGreen) },
            text = {
                Column {
                    Text("Enter a 4-to-6 digit PIN to protect the firewall.", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it },
                        label = { Text("PIN") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 6) confirmPinInput = it },
                        label = { Text("Confirm PIN") },
                        isError = pinError,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                    )
                    if (pinError) {
                        Text("PINs must match and be 4-6 digits.", color = AlertRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pinInput == confirmPinInput && pinInput.length in 4..6) {
                        coroutineScope.launch {
                            securityPrefs.setPin(pinInput)
                            pinLockEnabled = true
                            showSetPinDialog = false
                            pinError = false
                        }
                    } else {
                        pinError = true
                    }
                }) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showSetPinDialog = false
                    pinError = false
                }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showRemovePinDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            title = { Text("Remove Protection PIN", color = MatrixGreen) },
            text = {
                Column {
                    Text("Enter your current PIN to disable protection.", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Current PIN") },
                        isError = pinError,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                    )
                    if (pinError) {
                        Text("Incorrect PIN.", color = AlertRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        if (securityPrefs.verifyPin(pinInput)) {
                            securityPrefs.removePin()
                            pinLockEnabled = false
                            showRemovePinDialog = false
                            pinError = false
                        } else {
                            pinError = true
                        }
                    }
                }) {
                    Text("REMOVE")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showRemovePinDialog = false
                    pinError = false
                }) {
                    Text("CANCEL")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        var autoStartEnabled by remember { mutableStateOf(securityPrefs.isAutoStartEnabled()) }

        Text("Firewall Boot Persistence", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(16.dp))


        if (!isIgnoringBatteryOptimizations) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = TacticalAmber.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Warning: Doze Mode may kill the firewall.", color = TacticalAmber, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
                    ) {
                        Text("Allow Background Execution (Recommended)", color = SurfaceLevel1)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {

            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

                Text("Firewall Auto-Start on Boot", modifier = Modifier.weight(1f), fontSize = 16.sp)
                Switch(
                    checked = autoStartEnabled,
                    onCheckedChange = {
                        autoStartEnabled = it
                        securityPrefs.setAutoStartEnabled(it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = TacticalAmber)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Live Telemetry Configuration", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(16.dp))

        val sharedPrefs = context.getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
        var inactiveInLimit by remember { mutableFloatStateOf(sharedPrefs.getInt("inactive_in_records_limit", 50).toFloat()) }
        var inactiveOutLimit by remember { mutableFloatStateOf(sharedPrefs.getInt("inactive_out_records_limit", 50).toFloat()) }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Max Inactive Inbound Records: ${inactiveInLimit.toInt()}", fontSize = 16.sp)
                Slider(
                    value = inactiveInLimit,
                    onValueChange = { inactiveInLimit = it },
                    onValueChangeFinished = {
                        sharedPrefs.edit().putInt("inactive_in_records_limit", inactiveInLimit.toInt()).apply()
                    },
                    valueRange = 0f..500f,
                    steps = 49
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Max Inactive Outbound Records: ${inactiveOutLimit.toInt()}", fontSize = 16.sp)
                Slider(
                    value = inactiveOutLimit,
                    onValueChange = { inactiveOutLimit = it },
                    onValueChangeFinished = {
                        sharedPrefs.edit().putInt("inactive_out_records_limit", inactiveOutLimit.toInt()).apply()
                    },
                    valueRange = 0f..500f,
                    steps = 49
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = TacticalAmber)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Firewall Teardown Protection", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Require PIN to Disable Firewall", modifier = Modifier.weight(1f), fontSize = 16.sp)
                Switch(
                    checked = pinLockEnabled,
                    onCheckedChange = {
                        if (it) {
                            showSetPinDialog = true
                        } else {
                            showRemovePinDialog = true
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = TacticalAmber)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Automated Threat Feeds", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onShowArchitectureDoc()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
        ) {
            Text("Open Documentation Portal", color = PureBlack)
        }
        Spacer(modifier = Modifier.height(16.dp))

        FeedToggleItem(
            title = "Ad/Tracker Domains",
            enabled = adTrackerEnabled,
            onEnabledChange = {
                adTrackerEnabled = it
                sharedPrefs.edit().putBoolean("ad_tracker_enabled", it).apply()
            },
            action = adTrackerAction,
            onActionChange = {
                adTrackerAction = it
                sharedPrefs.edit().putString("ad_tracker_action", it.name).apply()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        FeedToggleItem(
            title = "Malware IPs/Domains",
            enabled = malwareEnabled,
            onEnabledChange = {
                malwareEnabled = it
                sharedPrefs.edit().putBoolean("malware_enabled", it).apply()
            },
            action = malwareAction,
            onActionChange = {
                malwareAction = it
                sharedPrefs.edit().putString("malware_action", it.name).apply()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = TacticalAmber)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Telemetry Settings", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(16.dp))

        var inactiveRecordsLimit by remember {
            mutableStateOf(
                context.getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
                    .getInt("inactive_records_limit", 50)
                    .toString()
            )
        }

        OutlinedTextField(
            value = inactiveRecordsLimit,
            onValueChange = { newValue ->
                inactiveRecordsLimit = newValue
                newValue.toIntOrNull()?.let {
                    context.getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("inactive_records_limit", it).apply()
                }
            },
            label = { Text("Inactive Connections per App") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = TacticalAmber)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Data Management", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(16.dp))

        DatasetManagerItem("GeoIP City Database", geoIpCityExists, onDelete = {
            File(context.filesDir, "GeoLite2-City.mmdb").delete()
            geoIpCityExists = false
        })
        DatasetManagerItem("GeoIP ASN Database", geoIpAsnExists, onDelete = {
            File(context.filesDir, "GeoLite2-ASN.mmdb").delete()
            geoIpAsnExists = false
        })
        DatasetManagerItem("MAC OUI Database", ouiExists, onDelete = {
            File(context.filesDir, "oui.txt").delete()
            ouiExists = false
        })
        DatasetManagerItem(
            title = "Threat Bloom Filter",
            exists = malwareFeedExists,
            onDelete = {
                File(context.filesDir, "threat_bloom.bin").delete()
                malwareFeedExists = false
            },
            onUpdate = {
                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                scope.launch {
                    val file = File(context.filesDir, "threat_bloom.bin")
                    file.writeText("dummy_bloom_data")
                    // Dummy compilation for Bloom Filter synchronization
                    val dummyBitArray = LongArray(100) { it.toLong() }
                    val dummyHashCount = 3
                    com.mobisec.omniip.core.NativeEngine.syncThreatBloomFilter(dummyBitArray, dummyHashCount)
                    malwareFeedExists = true
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = TacticalAmber)
        Spacer(modifier = Modifier.height(24.dp))

        Text("OSINT Integration", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = alienVaultKey,
            onValueChange = {
                alienVaultKey = it
                OsintPreferences.setAlienVaultKey(context, it)
            },
            label = { Text("AlienVault OTX API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = abuseIpDbKey,
            onValueChange = {
                abuseIpDbKey = it
                OsintPreferences.setAbuseIpDbKey(context, it)
            },
            label = { Text("AbuseIPDB API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = TacticalAmber)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Compliance Documentation", fontSize = 20.sp, color = MatrixGreen)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val intent = Intent(context, DocumentViewerActivity::class.java).apply {
                    putExtra("DOC_NAME", "PrivacyPolicy.md")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceLevel1)
        ) {
            Text("Privacy Policy", color = MatrixGreen)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(context, DocumentViewerActivity::class.java).apply {
                    putExtra("DOC_NAME", "VpnServiceDisclosure.md")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceLevel1)
        ) {
            Text("VpnService Disclosure", color = MatrixGreen)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(context, DocumentViewerActivity::class.java).apply {
                    putExtra("DOC_NAME", "DataHandling.md")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceLevel1)
        ) {
            Text("Data Handling Policy", color = MatrixGreen)
        }

    }
}

@Composable
fun DatasetManagerItem(title: String, exists: Boolean, onDelete: () -> Unit, onUpdate: () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp)
                Text(
                    text = if (exists) "Installed" else "Missing at present",
                    color = if (exists) MatrixGreen else AlertRed,
                    fontSize = 12.sp
                )
            }
            if (exists) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Delete")
                }
            } else {
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
                ) {
                    Text("Update")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedToggleItem(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    action: Action,
    onActionChange: (Action) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontSize = 18.sp)
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Action:", modifier = Modifier.padding(end = 8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = if (action == Action.BLOCK) "AUTO-BLOCK" else "AUTO-FLAG",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("AUTO-BLOCK") },
                                onClick = { onActionChange(Action.BLOCK); expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("AUTO-FLAG") },
                                onClick = { onActionChange(Action.FLAG); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
