package com.mobisec.omniip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.OmniIPTheme
import com.mobisec.omniip.ui.theme.PureBlack
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasAgreed = sharedPrefs.getBoolean("has_agreed_to_terms", false)

        setContent {
            OmniIPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PureBlack
                ) {
                    var showTerms by remember { mutableStateOf(!hasAgreed) }
                    var terminalText by remember { mutableStateOf("> SYSTEM BOOT SEQUENCE INITIATED...\n") }
                    var bootComplete by remember { mutableStateOf(false) }

                    LaunchedEffect(showTerms) {
                        if (!showTerms && !bootComplete) {
                            delay(500)
                            terminalText += "> LOADING KERNEL MODULES...\n"
                            delay(500)
                            terminalText += "> INITIALIZING CRYPTO ENGINE...\n"
                            delay(500)
                            terminalText += "> VERIFYING INTEGRITY...\n"
                            delay(500)
                            terminalText += "> SYSTEM READY.\n"
                            bootComplete = true
                            delay(500)
                            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                            finish()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(modifier = Modifier.size(100.dp).background(MatrixGreen)) {
                                Text("OMNI-IP", modifier = Modifier.align(Alignment.Center), color = PureBlack, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = terminalText,
                                color = MatrixGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().height(150.dp)
                            )
                        }

                        if (showTerms) {
                            FullScreenTermsModal(
                                onAgree = {
                                    sharedPrefs.edit().putBoolean("has_agreed_to_terms", true).apply()
                                    showTerms = false
                                    terminalText += "> AUTHORISATION ACCEPTED.\n"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenTermsModal(onAgree: () -> Unit) {
    val context = LocalContext.current
    var termsText by remember { mutableStateOf("Loading terms...") }

    LaunchedEffect(Unit) {
        termsText = try {
            context.assets.open("docs/VpnServiceDisclosure.md").use {
                java.io.InputStreamReader(it).readText()
            }
        } catch (e: Exception) {
            "Error loading terms."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Prominent Disclosure", color = MatrixGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(termsText, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen, contentColor = PureBlack),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I Agree & Initialize")
            }
        }
    }
}
