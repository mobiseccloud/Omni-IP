package com.mobisec.omniip.core

import android.content.Context
import android.net.Uri
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.FirewallRule
import com.mobisec.omniip.db.TargetType
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

object ImportEngine {
    fun importRulesFromJson(
        context: Context,
        uri: Uri,
        existingRules: List<FirewallRule>
    ): List<FirewallRule> {
        val newRules = mutableListOf<FirewallRule>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                val jsonArray = JSONArray(jsonString)
                val existingValues = existingRules.map { it.targetValue }.toSet()

                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val targetTypeStr = jsonObj.optString("targetType")
                    val targetValue = jsonObj.optString("targetValue")
                    val actionStr = jsonObj.optString("action")
                    val timestamp = jsonObj.optLong("timestamp", System.currentTimeMillis())

                    if (targetValue.isNotEmpty() && !existingValues.contains(targetValue)) {
                        val targetType = try {
                            TargetType.valueOf(targetTypeStr)
                        } catch (e: Exception) {
                            continue
                        }
                        val action = try {
                            Action.valueOf(actionStr)
                        } catch (e: Exception) {
                            continue
                        }

                        val rule = FirewallRule(
                            targetType = targetType,
                            targetValue = targetValue,
                            action = action,
                            timestamp = timestamp
                        )
                        newRules.add(rule)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return newRules
    }
}
