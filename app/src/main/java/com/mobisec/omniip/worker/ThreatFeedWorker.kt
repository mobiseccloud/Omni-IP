package com.mobisec.omniip.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.HeuristicRule
import com.mobisec.omniip.db.IntegrationEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL

class ThreatFeedWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.integrationDao()
            
            // Ensure endpoints exist
            val endpoints = dao.getAllEndpoints()
            var adEndpoint = endpoints.find { it.baseUrl == "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts" }
            if (adEndpoint == null) {
                adEndpoint = IntegrationEndpoint(baseUrl = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", apiKey = null, endpointType = "DOMAIN_LIST", actionPolicy = "BLOCK", priorityLevel = 10, sequenceId = 0)
                val id = dao.insertEndpoint(adEndpoint)
                adEndpoint = adEndpoint.copy(id = id.toInt())
            }

            var malwareEndpoint = endpoints.find { it.baseUrl == "https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset" }
            if (malwareEndpoint == null) {
                malwareEndpoint = IntegrationEndpoint(baseUrl = "https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset", apiKey = null, endpointType = "IP_LIST", actionPolicy = "BLOCK", priorityLevel = 10, sequenceId = 0)
                val id = dao.insertEndpoint(malwareEndpoint)
                malwareEndpoint = malwareEndpoint.copy(id = id.toInt())
            }

            val sharedPrefs = applicationContext.getSharedPreferences("threat_feeds", Context.MODE_PRIVATE)
            val adTrackerEnabled = sharedPrefs.getBoolean("ad_tracker_enabled", false)
            val malwareEnabled = sharedPrefs.getBoolean("malware_enabled", false)

            if (adTrackerEnabled) {
                val adDomains = fetchList(adEndpoint.baseUrl)
                if (adDomains.isNotEmpty()) {
                    dao.deleteHeuristicRulesByEndpointId(adEndpoint.id)
                    val rules = adDomains.map { HeuristicRule(endpointId = adEndpoint.id, targetValue = it) }
                    rules.chunked(5000).forEach { dao.insertHeuristicRules(it) }
                }
            } else {
                dao.deleteHeuristicRulesByEndpointId(adEndpoint.id)
            }

            if (malwareEnabled) {
                val malwareIps = fetchList(malwareEndpoint.baseUrl)
                if (malwareIps.isNotEmpty()) {
                    dao.deleteHeuristicRulesByEndpointId(malwareEndpoint.id)
                    val rules = malwareIps.map { HeuristicRule(endpointId = malwareEndpoint.id, targetValue = it) }
                    rules.chunked(5000).forEach { dao.insertHeuristicRules(it) }
                }
            } else {
                dao.deleteHeuristicRulesByEndpointId(malwareEndpoint.id)
            }

            // Rebuild Bloom Filter for C++ native
            val allRules = dao.getAllHeuristicRules()
            val allTargets = allRules.map { it.targetValue }.toSet()
            if (allTargets.isNotEmpty()) {
                val bloomFilter = BloomFilter.create(
                    Funnels.stringFunnel(Charsets.UTF_8),
                    Math.max(allTargets.size, 10000),
                    0.01
                )
                for (target in allTargets) {
                    bloomFilter.put(target)
                }

                val filterFile = File(applicationContext.filesDir, "threat_bloom.bin")
                FileOutputStream(filterFile).use { fos ->
                    bloomFilter.writeTo(fos)
                }
            } else {
                val filterFile = File(applicationContext.filesDir, "threat_bloom.bin")
                if (filterFile.exists()) {
                    filterFile.delete()
                }
            }

            Log.d("ThreatFeedWorker", "Threat feeds updated successfully. Total targets: ${allTargets.size}")
            Result.success()
        } catch (e: Exception) {
            Log.e("ThreatFeedWorker", "Error updating threat feeds", e)
            Result.retry()
        }
    }

    private fun fetchList(urlString: String): List<String> {
        val targets = mutableListOf<String>()
        try {
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    if (l.isEmpty() || l.startsWith("#")) continue

                    if (urlString.contains("hosts")) {
                        val parts = l.split(Regex("\\s+"))
                        if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                            if (parts[1] != "0.0.0.0" && parts[1] != "127.0.0.1" && parts[1] != "localhost") {
                                targets.add(parts[1])
                            }
                        }
                    } else if (urlString.contains("netset")) {
                        targets.add(l)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ThreatFeedWorker", "Error fetching list from $urlString", e)
        }
        return targets
    }
}
