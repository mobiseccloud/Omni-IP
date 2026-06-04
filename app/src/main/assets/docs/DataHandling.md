# Omni-IP Data Handling

**Offline OSINT/GeoIP Usage**

Omni-IP utilizes offline databases for OSINT and GeoIP resolution to ensure privacy and zero-latency rendering.

* **Dynamic Downloading:** Databases like MaxMind GeoLite2 (City and ASN) and MAC OUI are dynamically downloaded to local device storage.
* **No Embedded Assets:** To adhere to the minimal APK footprint policy, these databases are not bundled within the app itself.
* **Direct Queries:** Any queries to third-party threat intelligence services (AlienVault, AbuseIPDB, Shodan) are made directly from your device using your provided API keys.
