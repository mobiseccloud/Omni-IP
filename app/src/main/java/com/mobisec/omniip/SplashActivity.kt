package com.mobisec.omniip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobisec.omniip.ui.StartupPermissionDialog
import com.mobisec.omniip.ui.theme.MatrixGreen
import com.mobisec.omniip.ui.theme.OmniIPTheme
import com.mobisec.omniip.ui.theme.PureBlack
import com.mobisec.omniip.viewmodel.StartupViewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.delay
import java.io.InputStreamReader
import kotlin.system.exitProcess

class SplashActivity : ComponentActivity() {

    private val startupViewModel: StartupViewModel by viewModels()

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

                    val missingPermissions by startupViewModel.missingPermissions.collectAsState()

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) {
                        startupViewModel.checkPermissions(this@SplashActivity)
                    }

                    val vpnLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        startupViewModel.checkPermissions(this@SplashActivity)
                    }

                    LaunchedEffect(Unit) {
                        if (!bootComplete) {
                            if (!hasAgreed) {
                                delay(500)
                                terminalText += "> LOADING KERNEL MODULES...\n"
                                delay(500)
                                terminalText += "> INITIALIZING CRYPTO ENGINE...\n"
                                delay(500)
                                terminalText += "> VERIFYING INTEGRITY...\n"
                                delay(500)
                                terminalText += "> SYSTEM READY.\n"
                                delay(500)
                            }
                            bootComplete = true
                            
                            if (hasAgreed) {
                                startupViewModel.checkPermissions(this@SplashActivity)
                            }
                        }
                    }

                    // Once boot is complete, if they agreed but we have missing permissions, show them.
                    // If no missing permissions, launch MainActivity.
                    LaunchedEffect(bootComplete, showTerms, missingPermissions) {
                        if (bootComplete && !showTerms) {
                            if (missingPermissions.isEmpty()) {
                                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                                finish()
                            }
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

                        if (bootComplete && showTerms) {
                            WizardTermsModal(
                                onAgreeAll = {
                                    sharedPrefs.edit().putBoolean("has_agreed_to_terms", true).apply()
                                    showTerms = false
                                    terminalText += "> AUTHORISATION ACCEPTED.\n"
                                    startupViewModel.checkPermissions(this@SplashActivity)
                                },
                                onDecline = {
                                    finishAffinity()
                                    exitProcess(0)
                                }
                            )
                        }

                        if (bootComplete && !showTerms && missingPermissions.isNotEmpty()) {
                            StartupPermissionDialog(
                                missingPermissions = missingPermissions,
                                onRequestPermissions = {
                                    val perms = mutableListOf<String>()
                                    if (missingPermissions.contains("POST_NOTIFICATIONS") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    if (missingPermissions.contains("ACCESS_FINE_LOCATION")) {
                                        perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                    if (missingPermissions.contains("FOREGROUND_SERVICE_SPECIAL_USE") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        perms.add(android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
                                    }
                                    if (perms.isNotEmpty()) {
                                        permissionLauncher.launch(perms.toTypedArray())
                                    }
                                    
                                    if (missingPermissions.contains("VPN_PREPARATION")) {
                                        val intent = startupViewModel.requestVpnPreparation(this@SplashActivity)
                                        if (intent != null) {
                                            vpnLauncher.launch(intent)
                                        }
                                    }
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
fun WizardTermsModal(onAgreeAll: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    val docs = listOf(
        Pair("docs/VpnServiceDisclosure.md", "VPN Service Disclosure"),
        Pair("docs/PrivacyPolicy.md", "Privacy Policy"),
        Pair("docs/DataHandling.md", "Data Handling Policy"),
        Pair("docs/LEGAL.md", "Legal Terms")
    )

    var currentDocIndex by remember { mutableStateOf(0) }
    
    val currentDoc = docs[currentDocIndex]
    
    val markdownContent = remember(currentDocIndex) {
        try {
            context.assets.open(currentDoc.first).use {
                InputStreamReader(it).readText()
            }
        } catch (e: Exception) {
            "Error loading document: ${e.message}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(currentDoc.second, color = MatrixGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownText(markdown = markdownContent)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        if (currentDocIndex < docs.size - 1) {
                            currentDocIndex++
                        } else {
                            onAgreeAll()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen, contentColor = PureBlack),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (currentDocIndex < docs.size - 1) "Accept & Next" else "Accept All")
                }
            }
        }
    }
}
