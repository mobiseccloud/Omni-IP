# Omni-IP

## Project Vision
Omni-IP is the ultimate, unified tactical security and network diagnostics toolkit for Android. It brings together high-performance network scanning, offline intelligence, and deep-packet inspection into a single, cohesive, highly secure tool for professionals.

## Mobisec Ecosystem Integration
Omni-IP is a core pillar of the Mobisec Cloud and Omnitrust ecosystem. It complements OmniDNS (secure, private resolution) and Omnitrust (device integrity and zero-trust policy enforcement) by providing the raw, actionable network intelligence and sinkhole capabilities required for total endpoint dominance.

## Architecture Overview
Omni-IP enforces a strict decoupling of concerns:
*   **The UI Layer:** Built exclusively with Jetpack Compose, featuring our Tactical Dark Theme for uncompromised visibility in the field.
*   **The Execution Engines:** Low-level, high-performance engines power the core features. We utilize C++ NDK for rapid scanning and raw socket control, and Java `VpnService` for our local packet interception and firewalling sinkhole.

## Build Instructions
To build the Omni-IP skeleton and the underlying sample engines, you will need:
*   Android Studio (latest stable recommended)
*   Android NDK (installed via SDK Manager) for compiling the C/C++ engines (e.g., nmap, icmpenguin, libsocket).
*   Minimum SDK: API 26 (Android 8.0)
*   Target SDK: API 34+

Detailed compilation instructions for the individual NDK modules will be provided in subsequent phases.