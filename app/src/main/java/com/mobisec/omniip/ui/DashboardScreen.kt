package com.mobisec.omniip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.TacticalAmber

@Composable
fun DashboardScreen(
    targetIp: String,
    initialAction: String,
    terminalOutput: String,
    isExecuting: Boolean,
    onExecuteAction: (String, String) -> Unit // (ip, actionName)
) {
    var ipInput by remember { mutableStateOf(targetIp) }
    var actionInput by remember { mutableStateOf(initialAction) }

    LaunchedEffect(initialAction, targetIp) {
        if (targetIp.isNotEmpty() && initialAction.isNotEmpty() && initialAction != "NONE") {
            ipInput = targetIp
            actionInput = initialAction
            onExecuteAction(targetIp, initialAction)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("DASHBOARD", color = MatrixGreen, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ipInput,
            onValueChange = { ipInput = it },
            label = { Text("Target IP", color = TacticalAmber) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixGreen,
                unfocusedTextColor = MatrixGreen,
                focusedBorderColor = TacticalAmber,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actionInput = "PING"; onExecuteAction(ipInput, "PING") },
                enabled = !isExecuting && ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen)
            ) {
                Text("PING")
            }
            Button(
                onClick = { actionInput = "TRACEROUTE"; onExecuteAction(ipInput, "TRACEROUTE") },
                enabled = !isExecuting && ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
            ) {
                Text("TRACEROUTE")
            }
            Button(
                onClick = { actionInput = "PORTSCAN_FAST"; onExecuteAction(ipInput, "PORTSCAN_FAST") },
                enabled = !isExecuting && ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("SCAN")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(8.dp)
        ) {
            Text(
                text = if (isExecuting && terminalOutput.isEmpty()) "Executing..." else terminalOutput,
                color = MatrixGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
    }
}
