package com.mobisec.omniip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobisec.omniip.ui.theme.OmniIPTheme
import io.noties.markwon.Markwon
import java.io.InputStreamReader

class DocumentViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val docName = intent.getStringExtra("DOC_NAME") ?: "PrivacyPolicy.md"

        val markdownContent = try {
            assets.open("docs/$docName").use {
                InputStreamReader(it).readText()
            }
        } catch (e: Exception) {
            "Error loading document: ${e.message}"
        }

        setContent {
            OmniIPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        TopBarWithBack(title = docName) { finish() }
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())) {
                            MarkdownText(markdown = markdownContent)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithBack(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, color = MaterialTheme.colorScheme.primary) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun MarkdownText(markdown: String) {
    val context = LocalContext.current
    val markwon = remember { Markwon.create(context) }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(android.graphics.Color.parseColor("#E0E0E0")) // TextPrimary
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        }
    )
}
