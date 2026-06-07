package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.FirewallRule
import com.mobisec.omniip.db.TargetType
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.ui.theme.TextSecondary
import com.mobisec.omniip.viewmodel.RulesViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mobisec.omniip.core.ExportEngine
import com.mobisec.omniip.core.ImportEngine

@Composable
fun RulesScreen(viewModel: RulesViewModel, onRequirePremium: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Blocked", "Flagged", "Ignored")
    val rules by viewModel.rules.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val newRules = ImportEngine.importRulesFromJson(context, uri, rules)
            viewModel.importRules(newRules)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Imported ${newRules.size} rules. Duplicates skipped.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        val file = ExportEngine.exportRulesToJson(context, rules)
                        ExportEngine.shareFile(context, file, "application/json")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SHARE")
                }
                Button(
                    onClick = { importLauncher.launch("application/json") },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowDown, contentDescription = "Import", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("IMPORT")
                }
                var showAddDialog by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { 
                        if (viewModel.isEnterpriseUnlocked.value || viewModel.isPersonalUnlocked.value) {
                            showAddDialog = true 
                        } else {
                            onRequirePremium()
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "ADD RULE", tint = androidx.compose.ui.graphics.Color.White)
                }

                if (showAddDialog) {
                    var ipInput by remember { mutableStateOf("") }
                    var portInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showAddDialog = false },
                        title = { Text("Add Manual Block Rule") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = ipInput,
                                    onValueChange = { ipInput = it },
                                    label = { Text("IP Address") }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = portInput,
                                    onValueChange = { portInput = it },
                                    label = { Text("Port (Optional)") }
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val target = if (portInput.isNotEmpty()) "$ipInput:$portInput" else ipInput
                                val rule = FirewallRule(
                                    targetType = TargetType.IP_ADDRESS,
                                    targetValue = target,
                                    action = Action.BLOCK
                                )
                                val success = viewModel.addManualRule(rule)
                                if (!success) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("RULE ALREADY ACTIVE")
                                    }
                                }
                                showAddDialog = false
                            }) {
                                Text("ADD")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { showAddDialog = false }) { Text("CANCEL") }
                        }
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MatrixGreen
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val filteredRules = when (selectedTab) {
                0 -> rules.filter { it.action == Action.BLOCK }
                1 -> rules.filter { it.action == Action.FLAG }
                else -> rules.filter { it.action == Action.IGNORE }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRules) { rule ->
                    RuleItem(rule = rule, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun RuleItem(rule: FirewallRule, viewModel: RulesViewModel) {
    var showOptions by remember { mutableStateOf(false) }

    when (rule.targetType) {
        TargetType.APPLICATION -> ApplicationRuleCard(rule, onInfoClick = { showOptions = true })
        TargetType.IP_ADDRESS, TargetType.DOMAIN -> NetworkRuleCard(rule, onInfoClick = { showOptions = true })
        TargetType.GEOLOCATION -> GeolocationRuleCard(rule, onInfoClick = { showOptions = true })
    }

    if (showOptions) {
        ActionDialog(
            rule = rule,
            viewModel = viewModel,
            onDismiss = { showOptions = false }
        )
    }
}

@Composable
fun ApplicationRuleCard(rule: FirewallRule, onInfoClick: () -> Unit) {
    val context = LocalContext.current
    var appName = "Unknown App"
    var packageName = rule.targetValue
    
    try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        appName = pm.getApplicationLabel(appInfo).toString()
    } catch (e: Exception) {}

    RuleCardBase(rule, onInfoClick) {
        Text(text = appName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = packageName, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun NetworkRuleCard(rule: FirewallRule, onInfoClick: () -> Unit) {
    var mainText = rule.targetValue
    var subText = "All Ports"
    
    if (rule.targetValue.contains(":")) {
        val parts = rule.targetValue.split(":")
        if (parts.size >= 2) {
            mainText = parts[0]
            subText = "Port: ${parts[1]}"
        }
    }
    
    RuleCardBase(rule, onInfoClick) {
        Text(text = mainText, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = subText, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun GeolocationRuleCard(rule: FirewallRule, onInfoClick: () -> Unit) {
    val country = rule.targetValue
    RuleCardBase(rule, onInfoClick) {
        Text(text = "Country: $country", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = "All Apps & IPs", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun RuleCardBase(rule: FirewallRule, onInfoClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormat.format(Date(rule.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onInfoClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                content()
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rule.direction == com.mobisec.omniip.model.RuleDirection.OUTBOUND) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Outbound", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    } else if (rule.direction == com.mobisec.omniip.model.RuleDirection.INBOUND) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Inbound", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    } else {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Both", tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Both", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = timeString, color = TextSecondary, fontSize = 10.sp)
                }
                
                if (rule.action == Action.BLOCK) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (rule.blockWifi) Icons.Default.Close else Icons.Default.Share, contentDescription = "WiFi", tint = if (rule.blockWifi) AlertRed else MatrixGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(if (rule.blockMobile) Icons.Default.Close else Icons.Default.Call, contentDescription = "Mobile", tint = if (rule.blockMobile) AlertRed else MatrixGreen, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = TacticalAmber)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDialog(rule: FirewallRule, viewModel: RulesViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Manage Rule", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

            if (rule.action == Action.BLOCK) {
                BlocklistDialogContent(rule, viewModel, onDismiss)
            } else {
                FlagIgnoreDialogContent(rule, viewModel, onDismiss)
            }
            
            Button(
                onClick = {
                    viewModel.deleteRule(rule)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
            ) {
                Text("Delete Rule")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BlocklistDialogContent(rule: FirewallRule, viewModel: RulesViewModel, onDismiss: () -> Unit) {
    DialogActionRow(Icons.Default.Warning, "Add to Flag list") {
        viewModel.updateRuleAction(rule, Action.FLAG)
        onDismiss()
    }
    DialogActionRow(Icons.Default.Search, "Add to Ignore list") {
        viewModel.updateRuleAction(rule, Action.IGNORE)
        onDismiss()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Block WiFi", color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = rule.blockWifi,
            onCheckedChange = { viewModel.updateRuleContext(rule, it, rule.blockMobile) }
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Block Mobile Data", color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = rule.blockMobile,
            onCheckedChange = { viewModel.updateRuleContext(rule, rule.blockWifi, it) }
        )
    }
}

@Composable
fun FlagIgnoreDialogContent(rule: FirewallRule, viewModel: RulesViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager

    if (rule.targetType == TargetType.APPLICATION) {
        DialogActionRow(Icons.Default.Info, "View App Info") {
            try {
                val packageName = rule.targetValue
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                context.startActivity(intent)
            } catch (e: Exception) {}
            onDismiss()
        }
    }

    DialogActionRow(Icons.Default.Lock, "Move to Blocklist") {
        viewModel.updateRuleAction(rule, Action.BLOCK)
        onDismiss()
    }

    if (rule.action == Action.FLAG) {
        DialogActionRow(Icons.Default.Search, "Move to Ignore list") {
            viewModel.updateRuleAction(rule, Action.IGNORE)
            onDismiss()
        }
    } else if (rule.action == Action.IGNORE) {
        DialogActionRow(Icons.Default.Warning, "Move to Flag list") {
            viewModel.updateRuleAction(rule, Action.FLAG)
            onDismiss()
        }
    }

    if (rule.targetType == TargetType.IP_ADDRESS || rule.targetType == TargetType.DOMAIN) {
        DialogActionRow(Icons.Default.Share, "Copy Target Value") {
            val clip = ClipData.newPlainText("Target Value", rule.targetValue)
            clipboardManager.setPrimaryClip(clip)
            onDismiss()
        }
    }
}

@Composable
fun DialogActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = text, tint = TacticalAmber)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface)
    }
}
