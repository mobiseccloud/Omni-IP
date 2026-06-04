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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mobisec.omniip.core.ExportEngine
import com.mobisec.omniip.core.ImportEngine

@Composable
fun RulesScreen(viewModel: RulesViewModel) {
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
                    Text("SHARE RULES")
                }
                Button(
                    onClick = { importLauncher.launch("application/json") },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
                ) {
                    Text("IMPORT RULES")
                }
                var showAddDialog by remember { mutableStateOf(false) }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("ADD MANUAL RULE")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleItem(rule: FirewallRule, viewModel: RulesViewModel) {
    var showOptions by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showOptions = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Type: ${rule.targetType.name}", color = TextSecondary, fontSize = 12.sp)
                Text(text = rule.targetValue, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            val color = when (rule.action) {
                Action.BLOCK -> AlertRed
                Action.FLAG -> TacticalAmber
                Action.IGNORE -> MatrixGreen
            }
            Text(text = rule.action.name, color = color, fontWeight = FontWeight.Bold)

            if (rule.targetType == TargetType.APPLICATION) {
                IconButton(onClick = {
                    try {
                        val pm = context.packageManager
                        val packages = pm.getPackagesForUid(rule.targetValue.toInt())
                        if (!packages.isNullOrEmpty()) {
                            val packageName = packages[0]
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        // UID might not exist anymore or parse error
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "App Info",
                        tint = TacticalAmber
                    )
                }
            }
        }
    }

    if (showOptions) {
        ModalBottomSheet(onDismissRequest = { showOptions = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Manage Rule", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

                if (rule.action != Action.IGNORE) {
                    Button(
                        onClick = {
                            viewModel.updateRuleAction(rule, Action.IGNORE)
                            showOptions = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
                    ) {
                        Text("Move to Ignore List")
                    }
                }

                Button(
                    onClick = {
                        viewModel.deleteRule(rule)
                        showOptions = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Rule")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
