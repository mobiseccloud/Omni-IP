package com.mobisec.omniip.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.TacticalAmber

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("threat_feeds", Context.MODE_PRIVATE)

    var adTrackerEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ad_tracker_enabled", false)) }
    var adTrackerAction by remember { mutableStateOf(Action.valueOf(sharedPrefs.getString("ad_tracker_action", Action.BLOCK.name) ?: Action.BLOCK.name)) }

    var malwareEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("malware_enabled", false)) }
    var malwareAction by remember { mutableStateOf(Action.valueOf(sharedPrefs.getString("malware_action", Action.BLOCK.name) ?: Action.BLOCK.name)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Automated Threat Feeds", fontSize = 20.sp, color = MatrixGreen)
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
