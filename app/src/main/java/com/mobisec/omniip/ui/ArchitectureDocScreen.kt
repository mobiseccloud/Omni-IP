package com.mobisec.omniip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.ui.theme.MatrixGreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mobisec.omniip.ui.theme.AlertRed
import com.mobisec.omniip.ui.theme.PureBlack
import com.mobisec.omniip.ui.theme.SurfaceLevel1

@Composable
fun ArchitectureDocScreen(onBack: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLevel1)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = "OMNI-IP: SYSTEM LEDGER & ARCHITECTURE",
                color = MatrixGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AlertRed)) {
                Text("CLOSE", color = PureBlack)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "DOCUMENT BLOCK A: THE PERMISSION LEDGER",
            color = MatrixGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "1. POST_NOTIFICATIONS (API 33+)\n- Reason: Required to maintain unkillable foreground packet-inspection loops and surface immediate RASP/Tamper violation alerts.\n- Impact if denied: Android OS will aggressively terminate the background firewall service.\n\n2. FOREGROUND_SERVICE_SPECIAL_USE (API 34+)\n- Reason: Required to instantiate the persistent local loopback interface. Allows interception and sinkholing without background process eviction.\n- Impact if denied: VpnService drops execution.\n\n3. VPN_PREPARATION\n- Reason: Binds the local loopback routing engine (10.0.0.2 / fd00:1::1) for real-time EDR parsing without rooting.\n- Impact if denied: Packet routing fails.",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "DOCUMENT BLOCK B: ARCHITECTURAL DISCLAIMER",
            color = AlertRed,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AlertRed, RoundedCornerShape(4.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "ARCHITECTURAL DISCLAIMER: LOCAL VPN INTERFACE LIMITATIONS\n" +
                        "Omni-IP operates as an unrooted local Endpoint Detection and Response (EDR) system utilizing Android's internal VpnService loopback loop.\n" +
                        "Outbound Traffic (Tx Processing): Omni-IP completely intercepts, inspects, and enforces security constraints on all data transmissions originating from local applications before they exit the device.\n" +
                        "Inbound Traffic (Rx Response Processing): Omni-IP allows legitimate outbound requests to escape, but intercepts and drops incoming server responses at the virtual loopback layer (C2 Server Starvation). This prevents malicious payloads from detecting inspection mechanisms by avoiding standard socket exceptions.\n" +
                        "Unsolicited Inbound Network Traffic (LAN Restrictions): Due to the mathematical routing topology of unrooted Linux kernels, unsolicited incoming packets originating directly from the physical network architecture (e.g., an external local area network Nmap port scan targeting an open listening port on the device) bypass the virtual loopback adapter entirely. Omni-IP cannot intercept or mitigate unsolicited inbound physical network traffic without root access or custom Netfilter kernel hooks.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }
    }
}
