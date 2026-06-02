package com.mobisec.omniip.vpn

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.maxmind.geoip2.DatabaseReader
import com.mobisec.omniip.model.ConnectionDirection
import com.mobisec.omniip.model.ConnectionTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val dnsCache = ConcurrentHashMap<String, String>() // IP -> Hostname

    private var cityDbReader: DatabaseReader? = null
    private var asnDbReader: DatabaseReader? = null

    companion object {
        private const val TAG = "OmniVpnService"

        private val _telemetryFlow = MutableSharedFlow<ConnectionTelemetry>(extraBufferCapacity = 100)
        val telemetryFlow = _telemetryFlow.asSharedFlow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (vpnInterface != null) return START_STICKY

        val builder = Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .setSession("Omni-IP Telemetry Engine")
            .setMtu(1500)

        vpnInterface = builder.establish()

        vpnInterface?.let {
            vpnJob = scope.launch {
                initGeoIp()
                startInterception(it)
            }
        } ?: run {
            Log.e(TAG, "Failed to establish VPN interface")
            stopSelf()
        }

        return START_STICKY
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
        val buffer = ByteBuffer.allocate(32767)

        while (scope.isActive) {
            try {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)
                    processPacket(buffer, length, outputStream)
                }
            } catch (e: Exception) {
                if (e.message?.contains("EBADF") == true) break
                Log.e(TAG, "Error reading packet", e)
            }
        }
    }

    private suspend fun processPacket(packet: ByteBuffer, length: Int, outputStream: FileOutputStream) {
        if (length < 20) return // Min IPv4 header length
        val version = (packet.get(0).toInt() shr 4) and 0x0F
        if (version != 4) return // Only handle IPv4 for now

        val protocol = PacketUtils.getProtocol(packet)
        val ihl = PacketUtils.getIHL(packet)
        val sourceIp = PacketUtils.getSourceIP(packet)
        val destIp = PacketUtils.getDestIP(packet)

        if (protocol == 17) { // UDP
            val sourcePort = PacketUtils.getSourcePort(packet, ihl)
            val destPort = PacketUtils.getDestPort(packet, ihl)

            if (destPort == 53) {
                handleDnsRequest(packet, length, ihl, sourceIp, destIp, sourcePort, outputStream)
            } else {
                handleGenericPacket(protocol, sourceIp, destIp, sourcePort, destPort, null)
            }
        } else if (protocol == 6) { // TCP
            val sourcePort = PacketUtils.getSourcePort(packet, ihl)
            val destPort = PacketUtils.getDestPort(packet, ihl)

            var direction: String? = null
            if (PacketUtils.isTcpSyn(packet, ihl)) {
                if (sourceIp.hostAddress == "10.0.0.2") {
                    direction = ConnectionDirection.OUTBOUND
                } else if (destIp.hostAddress == "10.0.0.2") {
                    direction = ConnectionDirection.INBOUND
                }
            }

            handleGenericPacket(protocol, sourceIp, destIp, sourcePort, destPort, direction)
        }
    }

    private suspend fun handleGenericPacket(protocol: Int, sourceIp: InetAddress, destIp: InetAddress, sourcePort: Int, destPort: Int, direction: String?) {
        val uid = resolveUid(protocol, sourceIp, sourcePort, destIp, destPort)
        if (uid != -1) {
            val appInfo = getAppInfo(uid)
            val hostname = destIp.hostAddress?.let { dnsCache[it] }

            var countryName: String? = null
            var countryIsoCode: String? = null
            var cityName: String? = null
            var asnName: String? = null

            val targetIp = if (direction == ConnectionDirection.INBOUND) sourceIp else destIp

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
                            asnName = "AS$num" + (if (org != null) " $org" else "")
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore exceptions (e.g., AddressNotFoundException)
            }

            val telemetry = ConnectionTelemetry(
                appName = appInfo.first,
                packageName = appInfo.second,
                appIcon = appInfo.third,
                protocol = if (protocol == 6) "TCP" else "UDP",
                destPort = destPort,
                destIp = targetIp.hostAddress ?: "",
                resolvedHostname = hostname,
                country = countryName,
                countryCode = countryIsoCode,
                city = cityName,
                asn = asnName,
                direction = direction
            )
            _telemetryFlow.tryEmit(telemetry)
        }
        // Packets are dropped (sinkholed) simply by not writing them back to outputStream
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
        val pm = packageManager
        val packages = pm.getPackagesForUid(uid)

        if (!packages.isNullOrEmpty()) {
            val packageName = packages[0]
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                return Triple(appName, packageName, icon)
            } catch (e: PackageManager.NameNotFoundException) {
                return Triple("Unknown ($packageName)", packageName, null)
            }
        }
        return Triple("Unknown (UID $uid)", "uid:$uid", null)
    }

    private fun handleDnsRequest(packet: ByteBuffer, length: Int, ihl: Int, sourceIp: InetAddress, destIp: InetAddress, sourcePort: Int, outputStream: FileOutputStream) {
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
                val dnsSocket = DatagramSocket()
                protect(dnsSocket)

                // Use a real DNS server for forwarding
                val dnsServer = InetAddress.getByName("8.8.8.8")
                val request = DatagramPacket(dnsPayload, dnsPayload.size, dnsServer, 53)
                dnsSocket.send(request)

                val buffer = ByteArray(1024)
                val response = DatagramPacket(buffer, buffer.size)
                dnsSocket.soTimeout = 5000
                dnsSocket.receive(response)

                val responsePayload = response.data.copyOfRange(0, response.length)
                val responseDnsPacket = DnsPacket(responsePayload, 0, responsePayload.size)

                val domain = responseDnsPacket.domain
                if (domain != null) {
                    val resolvedIps = responseDnsPacket.getResolvedIps()
                    for (ip in resolvedIps) {
                        dnsCache[ip] = domain
                    }
                }

                // Reconstruct IP/UDP header and write back
                val responsePacket = IPPacketBuilder.buildUdpResponse(
                    sourceIp = originalDestIp,
                    destIp = originalSourceIp,
                    sourcePort = 53,
                    destPort = originalSourcePort,
                    payload = responsePayload
                )

                outputStream.write(responsePacket)

                dnsSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "DNS forwarding failed", e)
            }
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
