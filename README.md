# Omni-IP

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

