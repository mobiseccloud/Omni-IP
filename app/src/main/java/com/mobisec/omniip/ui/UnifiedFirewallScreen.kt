package com.mobisec.omniip.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.viewmodel.AppMatrixViewModel
import com.mobisec.omniip.viewmodel.RulesViewModel
import com.mobisec.omniip.viewmodel.GeoRulesViewModel

@Composable
fun UnifiedFirewallScreen(
    rulesViewModel: RulesViewModel,
    appMatrixViewModel: AppMatrixViewModel,
    geoRulesViewModel: GeoRulesViewModel,
    onRequirePremium: () -> Unit
) {
    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        Pair("Apps", Icons.Default.Menu),
        Pair("Network", Icons.Default.Share),
        Pair("Geolocation", Icons.Default.LocationOn)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MatrixGreen,
                modifier = Modifier.border(width = 1.dp, color = MatrixGreen.copy(alpha = 0.5f), shape = androidx.compose.ui.graphics.RectangleShape)
            ) {
                tabs.forEachIndexed { index, pair ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = { Text(pair.first) },
                        icon = { Icon(pair.second, contentDescription = pair.first) }
                    )
                }
            }
        }

        when (currentTab) {
            0 -> AppMatrixScreen(viewModel = appMatrixViewModel, onRequirePremium = onRequirePremium)
            1 -> RulesScreen(viewModel = rulesViewModel, onRequirePremium = onRequirePremium)
            2 -> GeoRulesScreen(viewModel = geoRulesViewModel, onRequirePremium = onRequirePremium)
        }
    }
}
