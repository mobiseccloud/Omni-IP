package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.SurfaceLevel2
import com.mobisec.omniip.ui.theme.TacticalAmber

@Composable
fun StartupPermissionDialog(missingPermissions: List<String>, onRequestPermissions: () -> Unit) {
    Dialog(onDismissRequest = { }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(4.dp, AlertRed, RoundedCornerShape(4.dp))
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLevel2),
            shape = RoundedCornerShape(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "CRITICAL PERMISSIONS MISSING",
                    color = AlertRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (missingPermissions.contains("POST_NOTIFICATIONS")) {
                    Text(
                        text = "Notifications:",
                        color = TacticalAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Required to maintain unkillable foreground packet-inspection loops and surface immediate RASP/Tamper violation alerts. Without this, the Android OS will aggressively terminate the background firewall service.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (missingPermissions.contains("FOREGROUND_SERVICE_SPECIAL_USE")) {
                    Text(
                        text = "Foreground Service (Special Use):",
                        color = TacticalAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Required to instantiate the persistent local loopback interface. Allows Omni-IP to intercept, parse, and sinkhole network traffic entirely on-device without background process eviction.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (missingPermissions.contains("ACCESS_FINE_LOCATION")) {
                    Text(
                        text = "Location Services:",
                        color = TacticalAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Required for LAN scanning, identifying BSSID/SSID properties, and applying geo-fenced firewall rules. Location data is never transmitted remotely.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (missingPermissions.contains("VPN_PREPARATION")) {
                    Text(
                        text = "VPN Preparation:",
                        color = TacticalAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Required to bind the local loopback routing engine (10.0.0.2 / fd00:1::1). Grants Omni-IP structural access to local socket emissions to execute real-time EDR parsing without rooting your device.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("GRANT PERMISSIONS", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
