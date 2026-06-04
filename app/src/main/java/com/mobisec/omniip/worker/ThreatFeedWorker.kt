package com.mobisec.omniip.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.ThreatFeedRule
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
            val dao = db.threatFeedRuleDao()

            val sharedPrefs = applicationContext.getSharedPreferences("threat_feeds", Context.MODE_PRIVATE)
            val adTrackerEnabled = sharedPrefs.getBoolean("ad_tracker_enabled", false)
            val malwareEnabled = sharedPrefs.getBoolean("malware_enabled", false)

            if (adTrackerEnabled) {
                val adDomains = fetchList("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                if (adDomains.isNotEmpty()) {
                    dao.deleteByFeedType("ad_tracker")
                    val rules = adDomains.map { ThreatFeedRule(it, "ad_tracker") }
                    // Insert in batches
                    rules.chunked(5000).forEach { dao.insertAll(it) }
                }
            } else {
                dao.deleteByFeedType("ad_tracker")
            }

            if (malwareEnabled) {
                val malwareIps = fetchList("https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset")
                if (malwareIps.isNotEmpty()) {
                    dao.deleteByFeedType("malware")
                    val rules = malwareIps.map { ThreatFeedRule(it, "malware") }
                    rules.chunked(5000).forEach { dao.insertAll(it) }
                }
            } else {
                dao.deleteByFeedType("malware")
            }

            // Rebuild Bloom Filter
            val allTargets = dao.getAllTargets()
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
