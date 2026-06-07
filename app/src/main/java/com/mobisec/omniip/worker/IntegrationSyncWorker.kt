package com.mobisec.omniip.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.IntegrationEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class IntegrationSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.integrationDao()
            val endpoints = dao.getAllEndpoints()

            for (endpoint in endpoints) {
                // Ignore flat file legacy endpoints that don't support delta sync
                if (!endpoint.baseUrl.startsWith("http") || endpoint.baseUrl.contains("githubusercontent")) continue

                syncEndpoint(endpoint, dao)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("IntegrationSyncWorker", "Error in sync loop", e)
            Result.retry()
        }
    }

    private suspend fun syncEndpoint(endpoint: IntegrationEndpoint, dao: com.mobisec.omniip.db.IntegrationDao) {
        try {
            val baseUrl = endpoint.baseUrl.removeSuffix("/")
            // 1. Check
            val checkUrl = URL("$baseUrl/sync/check?endpoint_id=${endpoint.id}")
            val checkConn = checkUrl.openConnection() as HttpURLConnection
            endpoint.apiKey?.let { checkConn.setRequestProperty("Authorization", "Bearer $it") }
            
            if (checkConn.responseCode != 200) return
            
            val checkResp = BufferedReader(InputStreamReader(checkConn.inputStream)).readText()
            val latestSequence = JSONObject(checkResp).getLong("latest_sequence")
            
            if (latestSequence <= endpoint.sequenceId) return

            // 2. Fetch
            val fetchUrl = URL("$baseUrl/sync/delta?type=${endpoint.endpointType}&since_sequence=${endpoint.sequenceId}")
            val fetchConn = fetchUrl.openConnection() as HttpURLConnection
            endpoint.apiKey?.let { fetchConn.setRequestProperty("Authorization", "Bearer $it") }
            
            if (fetchConn.responseCode != 200) return
            
            val fetchResp = BufferedReader(InputStreamReader(fetchConn.inputStream)).readText()
            val json = JSONObject(fetchResp)
            val newSequenceId = json.getLong("sequence_id")
            val addsArray = json.getJSONArray("adds")
            val adds = mutableListOf<String>()
            for (i in 0 until addsArray.length()) adds.add(addsArray.getString(i))
            
            val removesArray = json.getJSONArray("removes")
            val removes = mutableListOf<String>()
            for (i in 0 until removesArray.length()) removes.add(removesArray.getString(i))

            // 3. Transaction
            dao.processDeltaSync(endpoint.id, newSequenceId, adds, removes)

            // 4. Ack
            val ackUrl = URL("$baseUrl/sync/ack")
            val ackConn = ackUrl.openConnection() as HttpURLConnection
            ackConn.requestMethod = "POST"
            ackConn.setRequestProperty("Content-Type", "application/json")
            endpoint.apiKey?.let { ackConn.setRequestProperty("Authorization", "Bearer $it") }
            ackConn.doOutput = true
            
            val ackBody = JSONObject().apply {
                put("endpoint_id", endpoint.id.toString())
                put("committed_sequence", newSequenceId)
            }
            OutputStreamWriter(ackConn.outputStream).use { it.write(ackBody.toString()) }
            ackConn.responseCode // execute
            
        } catch (e: Exception) {
            Log.e("IntegrationSyncWorker", "Error syncing endpoint ${endpoint.id}", e)
        }
    }
}
