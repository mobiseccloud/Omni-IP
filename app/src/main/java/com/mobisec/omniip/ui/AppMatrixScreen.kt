package com.mobisec.omniip.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobisec.omniip.model.AppMatrixItem
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.PureBlack
import com.mobisec.omniip.ui.theme.SurfaceLevel1
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.viewmodel.AppMatrixViewModel

@Composable
fun AppMatrixScreen(viewModel: AppMatrixViewModel = viewModel()) {
    val apps by viewModel.apps.collectAsState()
    val showSystemApps by viewModel.showSystemApps.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("APP FIREWALL MATRIX", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sys", color = TacticalAmber, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = showSystemApps,
                    onCheckedChange = { viewModel.toggleSystemApps() },
                    colors = SwitchDefaults.colors(checkedThumbColor = MatrixGreen, checkedTrackColor = SurfaceLevel1, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = SurfaceLevel1)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                AppMatrixRow(app, viewModel)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AppMatrixRow(item: AppMatrixItem, viewModel: AppMatrixViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(max = 56.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLevel1),
        shape = androidx.compose.foundation.shape.CutCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Image(
                    bitmap = item.iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(item.label, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${item.packageName} [${item.uid}]", color = MatrixGreen.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WI-FI", color = if (item.wifiBlocked) AlertRed else MatrixGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Switch(
                    checked = !item.wifiBlocked,
                    onCheckedChange = { viewModel.toggleWifiBlock(item) },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MatrixGreen,
                        checkedTrackColor = SurfaceLevel1,
                        uncheckedThumbColor = AlertRed,
                        uncheckedTrackColor = SurfaceLevel1
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))
                Text("CELL", color = if (item.cellularBlocked) AlertRed else MatrixGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Switch(
                    checked = !item.cellularBlocked,
                    onCheckedChange = { viewModel.toggleCellularBlock(item) },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MatrixGreen,
                        checkedTrackColor = SurfaceLevel1,
                        uncheckedThumbColor = AlertRed,
                        uncheckedTrackColor = SurfaceLevel1
                    )
                )
            }
        }
    }
}
