# Omni-IP VpnService Disclosure

**Local Packet Sinkhole Policy**

Omni-IP uses the Android VpnService exclusively for local on-device packet filtering, sinkholing, and local PCAP generation.

* **No Remote Routing:** The VpnService is never used to route your traffic to remote servers or third-party VPN providers.
* **On-Device Only:** All inspection and filtering occur locally on your device.
* **Data Privacy:** Your network traffic is not collected, stored, or transmitted by Mobisec Cloud.
