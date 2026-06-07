# Omni-IP: Tactical Endpoint Security

## Executive Summary
Omni-IP is a zero-allocation, native-layer Android Endpoint Detection and Response (EDR) tactical firewall designed by Mobisec Cloud. Operating entirely on-device via a local loopback VPN interface, Omni-IP delivers deep packet forensics, runtime self-protection, and contextual threat mitigation without relying on external cloud processing.

## Core Capabilities
*   **Contextual Network Policies:** Dynamically swaps firewall rules based on active network topology (Wi-Fi vs. Cellular) using zero-allocation C++ rule caches.
*   **Live DNS Sinkholing:** High-speed interception and resolution blocking of malicious domains using MurmurHash3 and local Bloom Filters.
*   **App-Level (UID) Attribution:** Correlates network traffic to specific applications in real-time, enforcing per-app bandwidth constraints and behavioral heuristics.
*   **JSON Rule Portability:** Seamlessly export and import firewall rules and threat feeds via JSON, enabling rapid policy deployment.
*   **PCAP & CSV Data Exportation:** Captures and securely exports granular packet captures (PCAP) and connection logs (CSV) for external analysis via the Android Storage Access Framework.

## The Anti-Modding & RASP Enclave
Omni-IP is heavily armored against reverse engineering and runtime modification. Core security mechanisms include:
*   **C++ Atomic Baseline Integrity Checks:** Prevents Time-of-Check to Time-of-Use (TOCTOU) exploits and race conditions.
*   **Compile-Time XOR String Obfuscation:** Ensures sensitive constants and strings are never exposed in plaintext within the compiled binary (`.rodata`).
*   **Dynamic Keystore Hashing:** Performs active verification of the installer and APK signatures directly within the native layer using OS-agnostic cryptographic routines.
*   **Cryptographic PIN-Locked Teardown Gate:** Prevents unauthorized suspension or termination of the firewall service.

## Technology Stack
*   **Android NDK (C++):** Powers the core zero-allocation packet processing and deep packet inspection engine.
*   **Jetpack Compose:** Drives the tactical "Pocket SOC" dark theme UI.
*   **Kotlin Coroutines:** Manages asynchronous data flows, database interactions, and UI state.
*   **VpnService Loopback Interface:** Facilitates the local interception and routing of all device network traffic without external servers.

## Paygate Premium Features
The following features define the upcoming Premium tier for enterprise and power users:
*   **Shizuku Capability Unlock:** Leverages the Shizuku API to perform rootless, privileged system-level operations. This bypasses standard Android sandbox limitations, enabling raw packet injection, active process termination (PID killing), and deep port scanning.
*   **BYOB (Bring Your Own Backend):** Enables enterprise organizations to securely pipeline live tactical telemetry directly into their own custom threat-intelligence backends or SIEM systems (e.g., Splunk, ELK stack) using secure mTLS.
*   **Automated Continuous PCAP Sync:** Unlocks the ability to continuously stream live packet captures (PCAP) and forensic CSV logs to a remote server or secure cloud bucket for continuous monitoring.
