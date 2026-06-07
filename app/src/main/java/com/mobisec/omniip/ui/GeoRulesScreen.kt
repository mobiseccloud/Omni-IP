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
                            val locationText = if (!rule.city.isNullOrBlank()) "${rule.city}, ${rule.countryCode}" else rule.countryCode
                            Text(locationText, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        var selectedCountryCode by remember { mutableStateOf("") }
        var selectedCountryName by remember { mutableStateOf("") }
        var selectedCity by remember { mutableStateOf("") }
        var selectedAction by remember { mutableStateOf("BLOCK") }
        var expanded by remember { mutableStateOf(false) }
        var actionExpanded by remember { mutableStateOf(false) }

        val countriesMap = remember { 
            java.util.Locale.getISOCountries().map { 
                it to java.util.Locale("", it).displayCountry 
            }.sortedBy { it.second } 
        }

        val filteredCountries = countriesMap.filter { 
            it.second.contains(selectedCountryName, ignoreCase = true) || 
            it.first.contains(selectedCountryName, ignoreCase = true)
        }

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
                            value = selectedCountryName,
                            onValueChange = { 
                                selectedCountryName = it
                                expanded = true
                                val exactMatch = countriesMap.find { c -> c.second.equals(it, ignoreCase = true) }
                                if (exactMatch != null) selectedCountryCode = exactMatch.first else selectedCountryCode = it
                            },
                            label = { Text("Country") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MatrixGreen,
                                unfocusedTextColor = MatrixGreen
                            )
                        )
                        if (filteredCountries.isNotEmpty() && expanded) {
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                filteredCountries.forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text("${country.second} (${country.first})") },
                                        onClick = { 
                                            selectedCountryName = country.second
                                            selectedCountryCode = country.first
                                            expanded = false 
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = selectedCity,
                        onValueChange = { selectedCity = it },
                        label = { Text("City (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MatrixGreen,
                            unfocusedTextColor = MatrixGreen
                        )
                    )

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
                        if (selectedCountryCode.isNotBlank()) {
                            viewModel.addRule(
                                countryCode = selectedCountryCode.uppercase(),
                                city = selectedCity.trim().takeIf { it.isNotBlank() },
                                action = selectedAction
                            )
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
                ) {
                    Text("ADD", color = PureBlack, fontWeight = FontWeight.Bold)
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
