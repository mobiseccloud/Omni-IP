package com.mobisec.omniip.ui

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobisec.omniip.ui.theme.*
import com.mobisec.omniip.viewmodel.GeoRulesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoRulesScreen(viewModel: GeoRulesViewModel = viewModel(), onRequirePremium: () -> Unit = {}) {
    val rules by viewModel.rules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { 
                    if (viewModel.isEnterpriseUnlocked.value || viewModel.isPersonalUnlocked.value) {
                        showAddDialog = true 
                    } else {
                        onRequirePremium()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("ADD", color = PureBlack, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rules) { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLevel1)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(rule.countryCode, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Action: ${rule.action}", color = if (rule.action == "BLOCK") AlertRed else TacticalAmber, fontSize = 14.sp)
                        }
                        Button(
                            onClick = { viewModel.deleteRule(rule) },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                        ) {
                            Text("DELETE", color = PureBlack)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var selectedCountry by remember { mutableStateOf("") }
        var selectedAction by remember { mutableStateOf("BLOCK") }
        var expanded by remember { mutableStateOf(false) }
        var actionExpanded by remember { mutableStateOf(false) }

        val countries = listOf("CN", "RU", "IR", "KP", "US", "GB", "DE", "FR")

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("ADD GEOLOCATION RULE", color = MatrixGreen) },
            text = {
                Column {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCountry,
                            onValueChange = { selectedCountry = it },
                            label = { Text("Country Code") },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MatrixGreen,
                                unfocusedTextColor = MatrixGreen
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            countries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text(country) },
                                    onClick = { selectedCountry = country; expanded = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ExposedDropdownMenuBox(
                        expanded = actionExpanded,
                        onExpandedChange = { actionExpanded = !actionExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAction,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Action") },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MatrixGreen,
                                unfocusedTextColor = MatrixGreen
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = actionExpanded,
                            onDismissRequest = { actionExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("BLOCK") },
                                onClick = { selectedAction = "BLOCK"; actionExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("FLAG") },
                                onClick = { selectedAction = "FLAG"; actionExpanded = false }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedCountry.isNotBlank()) {
                            viewModel.addRule(selectedCountry, selectedAction)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
                ) {
                    Text("SAVE", color = PureBlack)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showAddDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceLevel2)
                ) {
                    Text("CANCEL", color = MatrixGreen)
                }
            },
            containerColor = SurfaceLevel1
        )
    }
}
