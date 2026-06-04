package com.mobisec.omniip.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mobisec.omniip.db.FirewallRule
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ExportEngine {
    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Data").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun exportRulesToJson(context: Context, rules: List<FirewallRule>): File {
        val jsonArray = JSONArray()
        rules.forEach { rule ->
            val jsonObject = JSONObject().apply {
                put("targetType", rule.targetType.name)
                put("targetValue", rule.targetValue)
                put("action", rule.action.name)
                put("timestamp", rule.timestamp)
            }
            jsonArray.put(jsonObject)
        }
        val file = File(context.cacheDir, "omni_rules.json")
        file.writeText(jsonArray.toString(4))
        return file
    }
}
