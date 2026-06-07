package com.mobisec.omniip.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.TacticalAmber
import com.mobisec.omniip.viewmodel.DashboardViewModel

/**
 * BatteryShield — Brutalist wireframe battery status indicator.
 *
 * GREEN (UNRESTRICTED): Firewall service has full OS-level background execution rights.
 * AMBER/RED (RESTRICTED): OS battery optimization is active; VPN service may be killed.
 *
 * Tapping the restricted shield opens the OEM-specific guide sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryShield(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val batteryStatus = remember { mutableStateOf(viewModel.checkBatteryOptimizationStatus()) }
    val showOemSheet = remember { mutableStateOf(false) }

    // Refresh battery status when returning from settings
    LaunchedEffect(Unit) {
        batteryStatus.value = viewModel.checkBatteryOptimizationStatus()
    }

    val isRestricted = batteryStatus.value == DashboardViewModel.BatteryStatus.RESTRICTED

    val infiniteTransition = rememberInfiniteTransition(label = "battery_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRestricted) 0.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "battery_alpha"
    )

    val shieldColor = if (isRestricted) AlertRed else MatrixGreen
    val statusLabel = if (isRestricted) "BATTERY RESTRICTED" else "BATTERY EXEMPT"
    val statusDesc = if (isRestricted) "OS may kill firewall — tap to fix" else "Firewall survive-kill guaranteed"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isRestricted) {
                    showOemSheet.value = true
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    statusLabel,
                    color = shieldColor.copy(alpha = if (isRestricted) pulseAlpha else 1f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                Text(
                    statusDesc,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            // Brutalist wireframe pentagon status indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .alpha(if (isRestricted) pulseAlpha else 1f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val strokeW = 2.5f
                    val color = if (isRestricted) AlertRed else MatrixGreen
                    // Pentagon wireframe (5 points)
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = minOf(w, h) / 2f * 0.88f
                    val path = androidx.compose.ui.graphics.Path()
                    for (i in 0 until 5) {
                        val angle = Math.toRadians((-90 + i * 72).toDouble()).toFloat()
                        val x = cx + r * kotlin.math.cos(angle)
                        val y = cy + r * kotlin.math.sin(angle)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                    )
                    if (isRestricted) {
                        // Draw "!" inside
                        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(cx, cy - r * 0.4f), end = androidx.compose.ui.geometry.Offset(cx, cy + r * 0.1f), strokeWidth = strokeW)
                        drawCircle(color = color, radius = strokeW / 2f, center = androidx.compose.ui.geometry.Offset(cx, cy + r * 0.3f))
                    } else {
                        // Draw checkmark inside
                        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(cx - r * 0.3f, cy), end = androidx.compose.ui.geometry.Offset(cx - r * 0.05f, cy + r * 0.3f), strokeWidth = strokeW)
                        drawLine(color = color, start = androidx.compose.ui.geometry.Offset(cx - r * 0.05f, cy + r * 0.3f), end = androidx.compose.ui.geometry.Offset(cx + r * 0.35f, cy - r * 0.25f), strokeWidth = strokeW)
                    }
                }
            }
        }
    }

    if (showOemSheet.value) {
        OemGuideSheet(
            onDismiss = {
                showOemSheet.value = false
                // Refresh status after returning
                batteryStatus.value = viewModel.checkBatteryOptimizationStatus()
            },
            onRequestExemption = {
                viewModel.requestBatteryOptimizationExemption()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OemGuideSheet(onDismiss: () -> Unit, onRequestExemption: () -> Unit) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.uppercase()

    val (oemName, oemInstructions, oemDeepLink) = remember(manufacturer) {
        getOemInfo(manufacturer)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "BACKGROUND INTEGRITY",
                color = AlertRed,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "The OS is actively restricting background processes. Without exemption, the firewall engine may be killed during heavy usage.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (oemName.isNotEmpty()) {
                Text(
                    "Device: $oemName",
                    color = TacticalAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(oemInstructions, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onRequestExemption,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("REQUEST STANDARD EXEMPTION", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (oemDeepLink != null) {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(oemDeepLink).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TacticalAmber)
                ) {
                    Text("OPEN $oemName BATTERY SETTINGS")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    val uri = Uri.parse("https://dontkillmyapp.com/${manufacturer.lowercase()}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OEM GUIDE: dontkillmyapp.com")
            }
        }
    }
}

private data class OemInfo(val name: String, val instructions: String, val deepLinkAction: String?)

private fun getOemInfo(manufacturer: String): OemInfo {
    return when {
        manufacturer.contains("SAMSUNG") -> OemInfo(
            name = "Samsung One UI",
            instructions = "Go to Settings → Battery → Background Usage Limits → Disable for Omni-IP. Also check Settings → Device Care → Battery → App Power Management.",
            deepLinkAction = "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
        manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || manufacturer.contains("POCO") -> OemInfo(
            name = "Xiaomi/MIUI",
            instructions = "Go to Settings → Apps → Omni-IP → Battery Saver → No Restrictions. Also go to Security App → Battery → App Battery Saver → No Restrictions.",
            deepLinkAction = "miui.intent.action.POWER_HIDE_MODE_APP_LIST"
        )
        manufacturer.contains("HUAWEI") || manufacturer.contains("HONOR") -> OemInfo(
            name = "Huawei/EMUI",
            instructions = "Go to Settings → Battery → App Launch → Manage Manually for Omni-IP. Enable all three toggles: Auto-launch, Secondary launch, Run in background.",
            deepLinkAction = null
        )
        manufacturer.contains("SONY") -> OemInfo(
            name = "Sony/Xperia",
            instructions = "Go to Settings → Battery → Battery Saver → Omni-IP → set to Unrestricted. Also check Settings → Battery → STAMINA mode exclusions.",
            deepLinkAction = null
        )
        manufacturer.contains("ONEPLUS") || manufacturer.contains("OPPO") -> OemInfo(
            name = "OnePlus/ColorOS",
            instructions = "Go to Settings → Battery → Battery Optimization → Omni-IP → Don't Optimize. Also check Settings → Apps → Omni-IP → Battery → Allow background activity.",
            deepLinkAction = null
        )
        else -> OemInfo(
            name = "",
            instructions = "",
            deepLinkAction = null
        )
    }
}
