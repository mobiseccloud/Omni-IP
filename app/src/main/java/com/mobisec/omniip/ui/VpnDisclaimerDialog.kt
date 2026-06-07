package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.SurfaceLevel2
import com.mobisec.omniip.ui.theme.TacticalAmber

@Composable
fun VpnDisclaimerDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    val termsText = remember {
        try {
            context.assets.open("docs/VpnServiceDisclosure.md").use {
                java.io.InputStreamReader(it).readText()
            }
        } catch (e: Exception) {
            "Omni-IP uses the Android VpnService to establish a local loopback interface for on-device firewalling and packet forensics. Your traffic never leaves the device. We do not collect, transmit, or log your network data remotely."
        }
    }

    Dialog(onDismissRequest = onDecline) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .border(2.dp, TacticalAmber, RoundedCornerShape(4.dp))
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLevel2),
            shape = RoundedCornerShape(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "VPN SERVICE DISCLOSURE",
                    color = TacticalAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = termsText,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onDecline,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("DECLINE", color = Color.White)
                    }
                    Button(
                        onClick = onAgree,
                        colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("I AGREE", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
