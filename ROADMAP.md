# Omni-IP Strategic Roadmap

This roadmap outlines the development phases for Omni-IP. A core tenet of this project is our **Zero-Backend Monetization Strategy**, meaning all premium features run client-side, eliminating recurring server costs while maximizing user privacy and margin.

## Phase 1: The Tactical Skeleton (Core MVP)
The foundation of the Omni-IP application.
*   Setup of the baseline repository structure.
*   Implementation of the core UI using Jetpack Compose (Tactical Dark Theme).
*   Integration of basic network utilities: Ping and WiFi scanning.

## Phase 2: The Deep Engines
Integration of the low-level execution engines to provide true tactical capabilities.
*   **Nmap NDK:** Compilation and integration of the C/C++ engine for SYN scans, OS fingerprinting, and rapid port scanning.
*   **ICMP Traceroutes:** Integration of low-level C++ socket handling via JNI for high-performance traceroutes, bypassing standard Android Java limitations.
*   **VpnService Sinkhole:** Establishing the foundational local packet interception logic for the no-root firewall.

## Phase 3: Client-Side Premium Tiers (Monetization)
Unlocking advanced, high-value features powered entirely on the device.
*   **Premium Threat Intel:** Allow users to supply their own API keys for integrations with AlienVault OTX, AbuseIPDB, and Shodan.
*   **Advanced Firewall:** Expand the VpnService sinkhole with PCAP export capabilities for deep analysis and granular regex-based blocking.
*   **Offline Intelligence:** Bundling of MaxMind GeoLite2 databases and comprehensive MAC OUI tables for instantaneous, offline lookups without leaking queries to third parties.
*   **Corporate Reporting:** Generation of beautifully formatted tactical PDFs and CSVs for professional security audits and compliance reporting.