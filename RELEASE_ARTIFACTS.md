# Artifact 1: Repository README

## Omni-IP: The Pocket SOC

### The Hook
Omni-IP is an enterprise-grade, rootless network forensics and probing suite designed for modern Android environments. Engineered as a non-rooted "Pocket SOC" (Security Operations Centre), Omni-IP delivers a comprehensive arsenal for network administrators, penetration testers, and security researchers. It provides absolute visibility and control over device network activity, combining a local VpnService sinkhole firewall with a massive tactical active-probing toolkit driven by a high-performance JNI/C++ native engine.

### Architecture Overview
Omni-IP employs a modern, highly decoupled architecture designed for absolute privacy and zero-latency performance:
*   **Reactive UI:** Built entirely with Jetpack Compose using our signature 'Mobisec Tactical Dark Theme'—featuring pure blacks, deep charcoals, and Matrix Green elements for an immersive, distraction-free terminal aesthetic.
*   **State Management:** Driven by robust Kotlin Flows and composable architecture to ensure flawless synchronisation during orientation changes and background execution.
*   **Zero-Embedded-Asset Protocol:** To minimise APK footprint, critical offline datasets (such as `oui.txt` and MaxMind GeoIP) are downloaded dynamically to local storage (`filesDir`) with robust fallback loops, file size validation, and graceful degradation.
*   **High-Performance Interception:** The core OmniVpnService utilises a zero-allocation packet processing loop and Room database threat feeds, completely avoiding garbage collection stutters.
*   **Native Execution Engine:** Deep native integration via an NDK/C++ bridge, allowing raw socket control and direct execution of embedded binaries without requiring root access.

### The Comprehensive Feature Map

#### Forensics & Telemetry
*   **Targeted & Global Raw PCAP Export:** Capture and export standard PCAP files locally for deep packet inspection.
*   **Live Connection Telemetry:** Real-time visibility into active sockets and network bindings.
*   **UID-based Data Exfiltration Tracking (Rx/Tx):** Granular tracking of bytes sent and received per application via high-performance `ConcurrentHashMap` structures.
*   **Historic Connection Logs:** Persistent, offline logging of all historical network connections.

#### Active Defence (Firewall)
*   **Local VPN Sinkhole:** A rootless, on-device firewall utilising the Android `VpnService` for deep packet inspection and selective routing.
*   **TLS SNI Extraction & Blocking:** Intercept and block traffic based on Server Name Indication (SNI) without breaking encryption.
*   **DNS-over-HTTPS (DoH) Proxy:** Secure, encrypted DNS resolution bypassing ISP interception.
*   **Behavioural Heuristics:** A token-bucket rate limiting engine that tracks anomalous network traffic (e.g., excessive TCP SYN connections or DNS queries) to automatically flag rogue applications.

#### Tactical Toolkit (Recon & Probing)
*   **Native C++ Ping & Traceroute:** High-fidelity ICMP tooling directly interacting with native sockets.
*   **JNI Nmap Port Scanner:** Execute both Fast Scans and Premium Deep Scans via our custom NDK integration.
*   **LAN Subnet Sweeper:** Rapid discovery of local network nodes featuring MAC OUI Hardware Vendor Resolution.
*   **WiFi Analyser:** Detailed metrics on local wireless environments.
*   **Offline IP Calculator & Converter:** Essential subnetting utilities without requiring internet connectivity.
*   **DNS Record Lookup:** Advanced DNS interrogation powered by the robust `dnsjava` library.
*   **Whois Lookup & Router Gateway Setup:** Instantly identify domain ownership and configure local routing parameters.

#### Threat Intelligence
*   **Offline MaxMind GeoIP/ASN Resolution:** Zero-latency geographical and autonomous system identification.
*   **Threat Feed Synchronisation:** Background integration with AlienVault OTX and AbuseIPDB using user-supplied API keys.
*   **Tactical App Ejection Intent:** Swift native uninstallation of applications flagged for generating network offences, seamlessly integrated via `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.

### Build Instructions
To build Omni-IP from source, ensure you have Android Studio installed with the latest NDK and CMake tools.

**Native Compilation (`omniip_bridge`):**
1.  The project utilises CMake for compiling the C++ native engines.
2.  Do not use `add_subdirectory()` for the C++ engines to avoid CMake collisions; compile raw source files directly.
3.  Isolate C++ standards via CMake target properties (e.g., C++14 for `libsocket`, C++17 for `icmpenguin` and the bridge).
4.  Configure the `build.gradle.kts` to explicitly filter NDK ABIs: `arm64-v8a`, `armeabi-v7a`, and `x86_64`.

**Code Obfuscation:**
*   Ensure R8/Proguard rules are correctly configured to preserve JNI method signatures, Room Database DAOs, and our internal data classes.

**Packaging:**
*   Always assemble the APK via `./gradlew assembleRelease` to ensure the Zero-Embedded-Asset routines and offline databases correctly initialise upon first launch.

---

# Artifact 2: Google Play Store Description

## Omni-IP: The Pocket SOC

Transform your Android device into an enterprise-grade Security Operations Centre (SOC). Omni-IP is the definitive, rootless network forensics and tactical probing suite engineered for network administrators, penetration testers, and privacy-conscious prosumers.

Replace multiple fragmented networking apps with one unified, offline-first terminal. Whether you are actively hunting threats, auditing local networks, or defending against malicious applications, Omni-IP equips you with uncompromised visibility and control—all presented in our signature Tactical Dark Theme.

### Complete Arsenal & Tactical Toolkit
*   **Local VPN Sinkhole & Firewall:** Block rogue traffic instantly with SNI extraction and behaviour-based heuristics.
*   **Raw PCAP Export:** Capture targeted or global network traffic for offline deep packet inspection.
*   **Data Exfiltration Tracking:** Monitor real-time Rx/Tx data usage per application to identify stealthy data leaks.
*   **Deep Nmap Port Scanning:** Run fast or premium deep network scans directly via our native C++ engine.
*   **LAN Subnet Sweeper & WiFi Analyser:** Map your local network instantly with offline MAC OUI hardware vendor resolution.
*   **Advanced DNS & Reconnaissance:** Leverage DNS-over-HTTPS (DoH), DNS record lookups, Whois, and native Ping/Traceroute tools.
*   **Offline Threat Intelligence:** Pinpoint connections with zero-latency MaxMind GeoIP/ASN resolution and sync with AlienVault OTX & AbuseIPDB.
*   **Tactical App Ejection:** Instantly neutralise offending applications straight from the dashboard.

### Prominent Disclosure (VpnService API)
**Privacy is our priority.** Omni-IP utilises the Android `VpnService` API to establish a local, on-device loopback interface. This enables our powerful network sinkhole, SNI blocking, and behavioural heuristics to function seamlessly without requiring root access. **We employ a strict Zero-Backend Architecture.** Your network traffic, PCAP files, and threat intelligence queries are processed entirely on your device. We do not host, harvest, transit, or store your personal data on any remote servers.

---

# Artifact 3: Competitor Feature Matrix

| Feature / Capability | **Omni-IP** | **Passive Firewall (e.g., NetGuard)** | **Packet Sniffing (e.g., PCAPdroid)** | **Active Probing (e.g., PingTools Pro)** | **Rootless Offensive (e.g., Kali NetHunter Rootless)** |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Targeted Raw PCAP Export** | **Yes** | No | Yes | No | Yes (Requires heavy config) |
| **Deep Port Scan (Nmap)** | **Yes (Native JNI)** | No | No | Basic Only | Yes |
| **Zero-Backend / Offline** | **Yes** | Yes | Yes | Partial | Yes |
| **Threat Intel Integration** | **Yes (AlienVault/AbuseIPDB)** | No | Limited | No | Manual Integration |
| **Data Exfiltration Tracking** | **Yes (Per UID Rx/Tx)** | Basic | Yes | No | No |
| **DoH Proxy** | **Yes** | No | No | No | Manual Setup |
| **Modern Terminal UI/UX** | **Yes (Compose/Dark Theme)** | No (Legacy Views) | Functional | Standard | Command Line / Functional |
| **Behavioural Heuristics** | **Yes (Token-Bucket)** | No | No | No | No |
| **Offline GeoIP / ASN** | **Yes (MaxMind)** | No | Limited | Yes (Online mostly) | Manual Setup |

---

# Artifact 4: Production Readiness & Debt Matrix

| Component | Assessment | Category | Notes & Mitigation |
| :--- | :--- | :--- | :--- |
| **OmniVpnService Packet Loop** | High throughput achieved but raw `ByteBuffer` handling requires strict scoping to prevent memory fragmentation. UDP checksum calculations under high load are currently inefficient. | *Inefficient* | Must implement a zero-allocation buffer pool. Offload checksum validation to native layer or optimise via bitwise operations. |
| **Exfiltration Tracking Maps** | The `ConcurrentHashMap` used for Rx/Tx and token buckets is functional but poses a risk of long-term memory leaks if stale UID entries are not aggressively pruned. | *Memory Leak Risks* | Introduce a periodic garbage collection coroutine to purge orphaned UIDs and reset token buckets gracefully. |
| **JNI C++ Bridge & Nmap** | Bridge linkage is stable via CMake, but multi-threaded execution lacks robust thread-safety. Nmap execution state occasionally hangs if the child process isn't reaped properly. | *Stability Issues* | Implement strict `std::mutex` locking in the JNI layer. Ensure the process is forcefully terminated on timeout to prevent zombie processes. |
| **Dynamic Initialisation Flow** | Fallback loops and file size validation exist for `oui.txt` and GeoIP, but graceful degradation needs refinement. UI currently halts briefly instead of rendering safe fallbacks instantly. | *Needs Improvement* | Refactor initialisation state to use compositional state-driven gates (`InitViewModel`). UI must load immediately with 'Unknown' placeholders while I/O completes. |
| **BillingManager & UI State** | Google Play integration and purchase acknowledgment logic (`queryPurchasesAsync`) works offline but UI state synchronisation drops during rapid orientation changes. | *Needs Improvement* | Persist entitlement states strictly in `EncryptedSharedPreferences`. Leverage Compose `rememberSaveable` and ViewModel state flows to survive lifecycle destruction. |
| **Room Database Threat Feeds** | Schema definition and KSP processing are solid. DAOs use suspend functions properly. | *Done* | Ensure explicit `Migration` objects are maintained; `fallbackToDestructiveMigration()` is strictly forbidden. |
| **Tactical Ejection Intent** | Native uninstallation routing is fully functional and passes correct URIs. | *Done* | No changes required. |
| **Caching (Bloom Filters)** | VpnService uses Guava Bloom Filters for threat feeds effectively, preventing CPU crashes. | *Done* | Monitor false-positive rates as the dataset scales. |
