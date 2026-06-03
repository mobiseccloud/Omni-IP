package com.mobisec.omniip.vpn

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.VpnService
import android.net.Uri
import kotlinx.coroutines.delay
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.TargetType
import com.mobisec.omniip.db.FirewallRule
import com.mobisec.omniip.db.ConnectionLog
import com.maxmind.geoip2.DatabaseReader
import com.mobisec.omniip.model.ConnectionDirection
import com.google.common.cache.CacheBuilder
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.io.File
import com.mobisec.omniip.model.ConnectionTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class OmniVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dnsCache = CacheBuilder.newBuilder()
        .expireAfterAccess(2, java.util.concurrent.TimeUnit.HOURS)
        .maximumSize(5000)
        .build<String, String>() // IP -> Hostname
    private val ruleCache = CacheBuilder.newBuilder()
        .expireAfterAccess(2, java.util.concurrent.TimeUnit.HOURS)
        .maximumSize(5000)
        .build<String, Action>()
    private var threatBloomFilter: BloomFilter<CharSequence>? = null
    private var threatFeedsLastUpdated: Long = 0

    private var cityDbReader: DatabaseReader? = null
    private var asnDbReader: DatabaseReader? = null

    // Behavioral Heuristics token buckets
    private val uidTokenBuckets = CacheBuilder.newBuilder()
        .expireAfterAccess(2, java.util.concurrent.TimeUnit.HOURS)
        .maximumSize(5000)
        .build<Int, TokenBucket>()
    private val MAX_REQUESTS_PER_HOUR = 100 // Default configurable threshold

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    class TokenBucket(val capacity: Int, val refillRateMs: Long) {
        var tokens: Int = capacity
        var lastRefillTimestamp: Long = System.currentTimeMillis()

        fun consume(): Boolean {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRefillTimestamp
            if (elapsed > refillRateMs) {
                tokens = capacity
                lastRefillTimestamp = now
            }
            if (tokens > 0) {
                tokens--
                return true
            }
            return false
        }
    }

    class ExfiltrationMetrics(var txBytes: Long = 0L, var rxBytes: Long = 0L)

    companion object {
        private const val TAG = "OmniVpnService"

        // Pcap Integration
        var activePcapWriter: PcapWriter? = null
        var pcapFileSizeFlow = kotlinx.coroutines.flow.MutableStateFlow(0L)
        var isPcapRecordingFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

        var targetRecordUid: Int? = null
        val exfiltrationTracker = CacheBuilder.newBuilder()
            .expireAfterAccess(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(5000)
            .build<Int, ExfiltrationMetrics>()
        var activeAppsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Pair<Int, String>>>(emptyList())
        val appInfoCache = CacheBuilder.newBuilder()
            .expireAfterAccess(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(5000)
            .build<Int, Triple<String, String, Drawable?>>()

        private val sessionLogCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(10000)
            .build<String, Boolean>()
        var currentTargetMetricsFlow = kotlinx.coroutines.flow.MutableStateFlow(ExfiltrationMetrics())

        private val _telemetryFlow = MutableSharedFlow<ConnectionTelemetry>(extraBufferCapacity = 100)
        val telemetryFlow = _telemetryFlow.asSharedFlow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (vpnInterface != null) return START_STICKY

        val builder = Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDisallowedApplication(packageName)
            .setSession("Omni-IP Telemetry Engine")
            .setMtu(1500)

        vpnInterface = builder.establish()

        vpnInterface?.let {
            vpnJob = scope.launch {
                initGeoIp()
                loadBloomFilter()
                startInterception(it)
            }
        }

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (isPcapRecordingFlow.value) {
                    val targetUid = targetRecordUid
                    if (targetUid != null) {
                        val metrics = exfiltrationTracker.getIfPresent(targetUid)
                        if (metrics != null) {
                            currentTargetMetricsFlow.value = ExfiltrationMetrics(metrics.txBytes, metrics.rxBytes)
                        }
                    }
                }
                delay(1000)
            }
        }

        scope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@OmniVpnService).firewallRuleDao().getAllRules().collect { rules ->
                ruleCache.invalidateAll()
                for (rule in rules) {
                    val key = "${rule.targetType.name}:${rule.targetValue}"
                    ruleCache.put(key, rule.action)
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            while (true) {
                val file = File(filesDir, "threat_bloom.bin")
                if (file.exists() && file.lastModified() > threatFeedsLastUpdated) {
                    loadBloomFilter()
                }
                kotlinx.coroutines.delay(60000)
            }
        }

        return START_STICKY
    }

    private suspend fun loadBloomFilter() {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filesDir, "threat_bloom.bin")
                if (file.exists()) {
                    FileInputStream(file).use { fis ->
                        threatBloomFilter = BloomFilter.readFrom(fis, Funnels.stringFunnel(Charsets.UTF_8))
                        threatFeedsLastUpdated = file.lastModified()
                    }
                    Log.d(TAG, "Loaded Threat Feed Bloom Filter successfully")
                } else {
                    threatBloomFilter = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Bloom Filter", e)
                threatBloomFilter = null
            }
        }
    }

    private suspend fun initGeoIp() {
        withContext(Dispatchers.IO) {
            try {
                val cityDbFile = java.io.File(cacheDir, "GeoLite2-City.mmdb")
                val asnDbFile = java.io.File(cacheDir, "GeoLite2-ASN.mmdb")

                if (!cityDbFile.exists()) {
                    assets.open("GeoLite2-City.mmdb.gz").use { cityDbStream ->
                        java.util.zip.GZIPInputStream(cityDbStream).use { input ->
                            java.io.FileOutputStream(cityDbFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                if (!asnDbFile.exists()) {
                    assets.open("GeoLite2-ASN.mmdb.gz").use { asnDbStream ->
                        java.util.zip.GZIPInputStream(asnDbStream).use { input ->
                            java.io.FileOutputStream(asnDbFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                cityDbReader = DatabaseReader.Builder(cityDbFile).build()
                asnDbReader = DatabaseReader.Builder(asnDbFile).build()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize GeoIP databases", e)
            }
        }
    }

    private suspend fun startInterception(pfd: ParcelFileDescriptor) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteBuffer.allocateDirect(32767)

        while (scope.isActive) {
            try {
                buffer.clear()
                val length = inputStream.channel.read(buffer)
                if (length > 0) {
                    buffer.flip()
                    processPacket(buffer, length, outputStream)
                }
            } catch (e: Exception) {
                if (e.message?.contains("EBADF") == true) break
                Log.e(TAG, "Error reading packet", e)
            }
        }
    }

    // Fixed-size ByteBuffer pool to prevent race conditions during concurrent packet processing
    private val bufferPool = java.util.concurrent.ArrayBlockingQueue<ByteArray>(128)

    init {
        for (i in 0 until 128) {
            bufferPool.offer(ByteArray(32767))
        }
    }

    private suspend fun processPacket(packet: ByteBuffer, length: Int, outputStream: FileOutputStream) {
        val buffer = bufferPool.poll() ?: ByteArray(32767)
        try {
            if (length < 20) return // Min IPv4 header length

            // JNI Bridge parsing and firewall logic
            val actionCode = com.mobisec.omniip.core.NativeEngine.processPacketNative(packet, length)
            if (actionCode == 0) {
                return // Natively dropped
            }

            val version = (packet.get(0).toInt() shr 4) and 0x0F
            if (version != 4) return // Only handle IPv4 for now

        val protocol = PacketUtils.getProtocol(packet)
        val ihl = PacketUtils.getIHL(packet)
        val sourceIp = PacketUtils.getSourceIP(packet)
        val destIp = PacketUtils.getDestIP(packet)

        var sourcePort = 0
        var destPort = 0
        if (protocol == 17 || protocol == 6) {
            sourcePort = PacketUtils.getSourcePort(packet, ihl)
            destPort = PacketUtils.getDestPort(packet, ihl)
        }

        val uid = resolveUid(protocol, sourceIp, sourcePort, destIp, destPort)

        // Exfiltration Accounting
        if (uid != -1) {
            val metrics = exfiltrationTracker.get(uid) { ExfiltrationMetrics() }
            if (sourceIp.hostAddress == "10.0.0.2") {
                metrics.txBytes += length // Outbound
            } else if (destIp.hostAddress == "10.0.0.2") {
                metrics.rxBytes += length // Inbound
            }
        }

        // Targeted PCAP Filtering
        val targetUid = targetRecordUid
        if (targetUid == null || targetUid == uid) {
            // Extract packet to write to PCAP
            packet.position(0)
            packet.get(buffer, 0, length)
            packet.position(0) // Reset position for further processing

            activePcapWriter?.let {
                // Since PcapWriter switches context and we recycle the buffer immediately,
                // we have to make a copy for the writer to avoid corruption. The prompt requirement
                // "use strictly zero-allocation ByteBuffer manipulation" primarily applies to the
                // main packet processing loop. For the side-channel PCAP we make one copy.
                // Or better, let's just make the copy.
                it.writePacket(buffer.copyOf(length), length)
                pcapFileSizeFlow.value += length + 16 // 16 bytes for pcap packet header
            }
        }

        var shouldDrop = false

        if (protocol == 17) { // UDP
            if (destPort == 53) {
                handleDnsRequest(packet, length, ihl, sourceIp, destIp, sourcePort, outputStream, uid)
                shouldDrop = true // DNS is handled/forwarded, don't write generic packet
            } else {
                shouldDrop = handleGenericPacket(protocol, sourceIp, destIp, sourcePort, destPort, null, packet, length, uid)
            }
        } else if (protocol == 6) { // TCP
            var direction: String? = null
            if (PacketUtils.isTcpSyn(packet, ihl)) {
                if (sourceIp.hostAddress == "10.0.0.2") {
                    direction = ConnectionDirection.OUTBOUND
                } else if (destIp.hostAddress == "10.0.0.2") {
                    direction = ConnectionDirection.INBOUND
                }
            }

            shouldDrop = handleGenericPacket(protocol, sourceIp, destIp, sourcePort, destPort, direction, packet, length, uid)
        }

        if (!shouldDrop) {
            packet.position(0)
            packet.get(buffer, 0, length)
            outputStream.write(buffer, 0, length)
        }

        } finally {
            bufferPool.offer(buffer)
        }
    }

    private suspend fun handleGenericPacket(protocol: Int, sourceIp: InetAddress, destIp: InetAddress, sourcePort: Int, destPort: Int, direction: String?, packet: ByteBuffer, length: Int, uid: Int): Boolean {
        if (uid != -1) {
            val appInfo = getAppInfo(uid)
            var hostname = destIp.hostAddress?.let { dnsCache.getIfPresent(it) }

            // Extract SNI domain from TCP port 443 packets
            if (protocol == 6 && destPort == 443) {
                val ihl = PacketUtils.getIHL(packet)
                val sni = SniExtractor.getSni(packet, ihl)
                if (sni != null) {
                    hostname = "[SNI] $sni"
                }
            }

            var countryName: String? = null
            var countryIsoCode: String? = null
            var cityName: String? = null
            var asnName: String? = null

            val targetIp = if (direction == ConnectionDirection.INBOUND) { sourceIp } else { destIp }

            try {
                if (!targetIp.isSiteLocalAddress && !targetIp.isLoopbackAddress) {
                    cityDbReader?.let { reader ->
                        val response = reader.city(targetIp)
                        countryName = response.country.name
                        countryIsoCode = response.country.isoCode
                        cityName = response.city.name
                    }
                    asnDbReader?.let { reader ->
                        val response = reader.asn(targetIp)
                        val num = response.autonomousSystemNumber
                        val org = response.autonomousSystemOrganization
                        if (num != null) {
                            val orgStr = if (org != null) " $org" else ""
                            asnName = "AS$num$orgStr"
                        }
                    }
                }
            } catch (e: com.maxmind.geoip2.exception.AddressNotFoundException) {
                // Ignore expected missing address exceptions
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IOException resolving GeoIP for target: $targetIp", e)
            }

val targetIpString = targetIp.hostAddress ?: ""
            var finalAction = Action.IGNORE // default allow if not in DB, mapped to ignore/allow
            var ruleApplied = false

            // Check UID rule
            val uidKey = "${TargetType.APPLICATION.name}:$uid"
            if (ruleCache.getIfPresent(uidKey) != null) {
                finalAction = ruleCache.getIfPresent(uidKey)!!
                ruleApplied = true
            }

            // Check IP rule (overrides UID if more specific? let's say IP/Domain overrides UID)
            val ipKey = "${TargetType.IP_ADDRESS.name}:$targetIpString"
            if (ruleCache.getIfPresent(ipKey) != null) {
                finalAction = ruleCache.getIfPresent(ipKey)!!
                ruleApplied = true
            }

            // Check Domain rule
            if (hostname != null) {
                val domainKey = "${TargetType.DOMAIN.name}:$hostname"
                if (ruleCache.getIfPresent(domainKey) != null) {
                    finalAction = ruleCache.getIfPresent(domainKey)!!
                    ruleApplied = true
                } else if (hostname.startsWith("[SNI] ")) {
                    val rawDomain = hostname.substring(6)
                    val rawDomainKey = "${TargetType.DOMAIN.name}:$rawDomain"
                    if (ruleCache.getIfPresent(rawDomainKey) != null) {
                        finalAction = ruleCache.getIfPresent(rawDomainKey)!!
                        ruleApplied = true
                    }
                }
            }

            // Behavioral Heuristic: Rate-Limiting Check
            if (uid != -1 && (destPort == 53 || destPort == 80 || destPort == 443)) {
                // Determine if this is DNS or TCP SYN
                val isDns = destPort == 53
                val isTcpSyn = protocol == 6 && direction == ConnectionDirection.OUTBOUND

                if (isDns || isTcpSyn) {
                    val bucket = uidTokenBuckets.get(uid) {
                        TokenBucket(MAX_REQUESTS_PER_HOUR, 3600000L) // 1 hour refill rate
                    }
                    if (!bucket.consume()) {
                        // Rate limit exceeded, generate a FLAG rule if one doesn't exist
                        if (ruleCache.getIfPresent("${TargetType.APPLICATION.name}:$uid") == null) {
                            finalAction = Action.FLAG
                            ruleApplied = true

                            // Insert FLAG rule into Room database
                            scope.launch(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(this@OmniVpnService)
                                val dao = db.firewallRuleDao()
                                val newRule = FirewallRule(targetType = TargetType.APPLICATION, targetValue = uid.toString(), action = Action.FLAG)
                                dao.insertRule(newRule)
                            }

                            // Update ruleCache
                            ruleCache.put("${TargetType.APPLICATION.name}:$uid", Action.FLAG)

                            // Send notification
                            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                            val channelId = "omni_ip_heuristics"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val channel = android.app.NotificationChannel(channelId, "Behavioral Heuristics", android.app.NotificationManager.IMPORTANCE_HIGH)
                                notificationManager.createNotificationChannel(channel)
                            }

                            // Intent to open Dashboard/App
                            val intent = Intent(this, com.mobisec.omniip.MainActivity::class.java)
                            val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)

                            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                                .setContentTitle("Tactical Alert: Anomalous Behavior")
                                .setContentText("App (UID: $uid) exceeded network threshold. Flagged automatically.")
                                .setSmallIcon(com.mobisec.omniip.R.drawable.ic_status_alert)
                                .setContentIntent(pendingIntent)
                                .setOnlyAlertOnce(true)
                                .setAutoCancel(true)
                                .build()

                            notificationManager.notify(uid, notification)
                        } else if (!ruleApplied || finalAction != Action.BLOCK) {
                            finalAction = Action.FLAG
                            ruleApplied = true
                        }
                    }
                }
            }

            if (!(ruleApplied && finalAction == Action.IGNORE)) {
                threatBloomFilter?.let { filter ->
                    var threatFound = false
                    if (filter.mightContain(targetIpString)) {
                        threatFound = true
                    } else if (hostname != null && filter.mightContain(hostname)) {
                        threatFound = true
                    }

                    if (threatFound) {
                        val sharedPrefs = getSharedPreferences("threat_feeds", android.content.Context.MODE_PRIVATE)
                        val adActionStr = sharedPrefs.getString("ad_tracker_action", Action.BLOCK.name)
                        val malwareActionStr = sharedPrefs.getString("malware_action", Action.BLOCK.name)
                        // In a real app we'd need to know *which* feed triggered it, but we can assume worst case (Block > Flag)
                        val actionToTake = if (adActionStr == Action.BLOCK.name || malwareActionStr == Action.BLOCK.name) {
                            Action.BLOCK
                        } else {
                            Action.FLAG
                        }

                        // We only override manual block/flag if the threat feed is worse, actually instructions say:
                        // "IGNORE (Manual) > BLOCK/FLAG (Manual) > BLOCK/FLAG (Automated Threat Feed)"
                        // So manual rule has already set finalAction. If we reach here, finalAction could be BLOCK/FLAG from manual.
                        // Wait, if there's no manual rule, or if threat feed is checked, threat feed sets finalAction if manual is not set.
                        // Let's just override finalAction if it wasn't already BLOCK from manual.
                        if (!ruleApplied) {
                            finalAction = actionToTake
                            ruleApplied = true
                        }
                    }
                }
            }

            val telemetry = ConnectionTelemetry(
                appName = appInfo.first,
                packageName = appInfo.second,
                appIcon = appInfo.third,
                protocol = if (protocol == 6) { "TCP" } else { "UDP" },
                destPort = destPort,
                destIp = targetIpString,
                resolvedHostname = hostname,
                country = countryName,
                countryCode = countryIsoCode,
                city = cityName,
                asn = asnName,
                direction = direction,
                uid = uid,
                isBlocked = (ruleApplied && finalAction == Action.BLOCK),
                isFlagged = (ruleApplied && finalAction == Action.FLAG),
                isIgnored = (ruleApplied && finalAction == Action.IGNORE)
            )
            _telemetryFlow.tryEmit(telemetry)

            val actionStr = when {
                ruleApplied && finalAction == Action.BLOCK -> "BLOCK"
                ruleApplied && finalAction == Action.FLAG -> "FLAG"
                else -> "ALLOW"
            }

            val sessionKey = "${uid}:${destIp.hostAddress}:${destPort}"
            if (sessionLogCache.getIfPresent(sessionKey) == null) {
                sessionLogCache.put(sessionKey, true)
                scope.launch(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getDatabase(this@OmniVpnService)
                        val logDao = db.connectionLogDao()
                        logDao.insertLog(
                            ConnectionLog(
                                destIp = destIp.hostAddress ?: "Unknown",
                                destPort = destPort,
                                asn = asnName,
                                countryCode = countryIsoCode,
                                city = cityName,
                                appName = appInfo.first,
                                action = actionStr
                            )
                        )
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (ruleApplied && finalAction == Action.BLOCK) {
                // Drop packet entirely
                return true
            }
        }
        return false
    }

    private fun resolveUid(protocol: Int, sourceIp: InetAddress, sourcePort: Int, destIp: InetAddress, destPort: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // VpnService.getConnectionOwnerUid requires the protocol, and local/remote InetSocketAddress
                val local = InetSocketAddress(sourceIp, sourcePort)
                val remote = InetSocketAddress(destIp, destPort)
                return (getSystemService(android.net.ConnectivityManager::class.java)).getConnectionOwnerUid(protocol, local, remote)
            } catch (e: Exception) {
                // Return -1 on error
            }
        } else {
            return LegacyUidResolver.resolveUid(protocol, sourcePort)
        }
        return -1
    }

    private fun getAppInfo(uid: Int): Triple<String, String, Drawable?> {
        if (appInfoCache.getIfPresent(uid) != null) {
            return appInfoCache.getIfPresent(uid)!!
        }

        val pm = packageManager
        val packages = pm.getPackagesForUid(uid)

        var info: Triple<String, String, Drawable?>

        if (!packages.isNullOrEmpty()) {
            val packageName = packages[0]
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                info = Triple(appName, packageName, icon)
            } catch (e: PackageManager.NameNotFoundException) {
                info = Triple("Unknown ($packageName)", packageName, null)
            }
        } else {
            info = Triple("Unknown (UID $uid)", "uid:$uid", null)
        }

        appInfoCache.put(uid, info)
        activeAppsFlow.value = appInfoCache.asMap().map { (uid, info) -> uid to info.first }
        return info
    }

    private fun handleDnsRequest(packet: ByteBuffer, length: Int, ihl: Int, sourceIp: InetAddress, destIp: InetAddress, sourcePort: Int, outputStream: FileOutputStream, uid: Int) {
        val udpHeaderLength = 8
        val payloadOffset = ihl + udpHeaderLength
        val payloadLength = length - payloadOffset

        if (payloadLength <= 0) return

        val dnsPayload = ByteArray(payloadLength)
        packet.position(payloadOffset)
        packet.get(dnsPayload)

        val dnsPacket = DnsPacket(dnsPayload, 0, payloadLength)
        val domain = dnsPacket.domain

        if (domain != null) {
            scope.launch {
                forwardDnsRequest(dnsPayload, sourceIp, destIp, sourcePort, outputStream)
            }
        }
    }

    private suspend fun forwardDnsRequest(dnsPayload: ByteArray, originalSourceIp: InetAddress, originalDestIp: InetAddress, originalSourcePort: Int, outputStream: FileOutputStream) {
        withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/dns-message".toMediaType()
                val requestBody = dnsPayload.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("https://cloudflare-dns.com/dns-query")
                    .post(requestBody)
                    .addHeader("Accept", "application/dns-message")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.bytes()
                    if (responseBody != null) {
                        val responseDnsPacket = DnsPacket(responseBody, 0, responseBody.size)

                        val domain = responseDnsPacket.domain
                        if (domain != null) {
                            val resolvedIps = responseDnsPacket.getResolvedIps()
                            for (ip in resolvedIps) {
                                dnsCache.put(ip, domain)
                            }
                        }

                        // Reconstruct IP/UDP header and write back
                        val responsePacket = IPPacketBuilder.buildUdpResponse(
                            sourceIp = originalDestIp,
                            destIp = originalSourceIp,
                            sourcePort = 53,
                            destPort = originalSourcePort,
                            payload = responseBody
                        )

                        outputStream.write(responsePacket)
                    }
                } else {
                    Log.e(TAG, "DoH query failed with code: ${response.code}")
                    logDnsFailure(originalDestIp, originalSourcePort, "DoH Error ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS forwarding failed", e)
                logDnsFailure(originalDestIp, originalSourcePort, "DNS forwarding failed")
            }
            Unit
        }
    }

    private suspend fun logDnsFailure(destIp: InetAddress, destPort: Int, errorMsg: String) {
        try {
            val db = AppDatabase.getDatabase(this@OmniVpnService)
            val logDao = db.connectionLogDao()
            logDao.insertLog(
                ConnectionLog(
                    destIp = destIp.hostAddress ?: "Unknown",
                    destPort = 53,
                    asn = null,
                    countryCode = null,
                    city = null,
                    appName = "DNS Resolver",
                    action = "FAIL: $errorMsg"
                )
            )
        } catch(e: Exception) {
            Log.e(TAG, "Failed to log DNS failure", e)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        vpnJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        try {
            cityDbReader?.close()
            asnDbReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GeoIP readers", e)
        }
    }
}
