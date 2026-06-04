# Native Hardening & JNI Migration Roadmap: Omni-IP

## 1. CORE LOGIC MIGRATION (KOTLIN TO C++)

### Packet Processing Migration
**Implementation Complexity: High**
*   **Current State:** Packet inspection, header parsing (IP/UDP/TCP), and checksum validation are currently implemented in Kotlin (`OmniVpnService.processPacket`, `PacketUtils`, `IPPacketBuilder`). This introduces garbage collection overhead and makes the firewall logic susceptible to reverse engineering and runtime hooking via Smali manipulation or Frida.
*   **Strategy:** Move all raw byte array manipulations from `OmniVpnService.kt`, `PacketUtils.kt`, and `IPPacketBuilder.kt` into the C++ layer.
    *   Expose a JNI function: `external fun processPacketNative(packet: ByteBuffer, length: Int): Boolean`
    *   C++ implementation will use zero-copy direct buffer access (via `GetDirectBufferAddress`) to directly read and mutate the ByteBuffer.
    *   Implement high-performance struct mapping for IPv4, TCP, and UDP headers in C++ for O(1) parsing.
    *   Port the checksum calculation logic from `IPPacketBuilder` to highly optimized inline C++ functions using SIMD instructions where applicable.
*   **Performance Impact:** Significant reduction in GC pauses. C++ direct memory access is exponentially faster than JVM array bound checks.

### Threat Feed & Rule Evaluation Migration
**Implementation Complexity: Medium**
*   **Current State:** Firewall decisions (Drop/Allow/Flag) rely on a `ruleCache` and `threatBloomFilter` located in Kotlin memory.
*   **Strategy:** If the packet inspection moves to C++, the decision matrix must follow. An attacker could otherwise hook the JNI return value.
    *   Migrate the `threatBloomFilter` (currently using Guava) to a native C++ Bloom Filter implementation (e.g., using `std::hash`).
    *   Maintain the `ruleCache` as a thread-safe `std::unordered_map` in native memory.
    *   The JNI boundary should only return the final action enum (Drop/Allow) or silently drop the packet within the C++ layer directly via a tunnel FD interaction (if architecturally feasible) to ensure atomic security.

## 2. NATIVE ANTI-TAMPERING & INSTALLER VERIFICATION

### Play Store Verification
**Implementation Complexity: Low**
*   **Current State:** None currently enforced in the C++ engine.
*   **Strategy:**
    *   Use JNI Reflection within `bridge.cpp` to invoke `android.content.Context.getPackageManager().getInstallerPackageName(packageName)`.
    *   Alternatively, use `getInstallSourceInfo` for API 30+.
    *   Compare the result strictly against the obfuscated string literal for `"com.android.vending"`.
    *   If the check fails (e.g., installed via ADB or third-party store), silently neuter the premium features by mutating the `g_auth_state` bitmask.

### Signature Hashing
**Implementation Complexity: Medium**
*   **Current State:** APK signature is not currently validated natively.
*   **Strategy:**
    *   Execute a JNI call to `PackageManager.getPackageInfo(packageName, GET_SIGNATURES)`.
    *   Extract the `Signature` byte array and pass it to a native SHA-256 implementation (like mbedtls or OpenSSL/BoringSSL linked statically).
    *   Compare the computed hash against a heavily obfuscated byte array embedded in the `.so` data section.
    *   To prevent bypasses, do not return a boolean. Instead, integrate the result into the decryption key generation for critical strings or the `g_auth_state` initialization.

## 3. RUNTIME APPLICATION SELF-PROTECTION (RASP)

### Anti-Hooking & Anti-Debugging
**Implementation Complexity: Medium**
*   **Current State:** No active RASP mechanisms are present.
*   **Strategy:**
    *   **Ptrace Detection:** Create a detached background thread in `bridge.cpp` that continuously calls `ptrace(PTRACE_TRACEME, 0, 0, 0)`. If it returns `-1`, a debugger is already attached.
    *   **TracerPID Check:** Parse `/proc/self/status` and verify that `TracerPid` is `0`.
    *   **Frida/Xposed Detection:** Scan `/proc/self/maps` for known instrumentation library names (e.g., `frida-agent.so`, `XposedBridge.jar`).
    *   **Response:** If detected, purposely crash the app with a SIGSEGV or corrupt the packet processing buffer to ensure the firewall cannot be bypassed safely.

### Root Detection
**Implementation Complexity: Low**
*   **Current State:** No root detection is implemented.
*   **Strategy:**
    *   **Binary Checks:** Use `access()` to check for the existence of common su binaries (`/sbin/su`, `/system/bin/su`, `/system/xbin/su`, `/data/local/xbin/su`, `/data/local/bin/su`).
    *   **Magisk Detection:** Attempt to read specific Magisk mount points or utilize system property checks (e.g., `ro.debuggable`, `ro.secure`).
    *   **Response:** Emit a telemetry warning or UI alert indicating the network stack (and thus the VPN firewall) may be compromised by the host OS.

## 4. OBFUSCATION STRATEGY

### Opaque State Management
**Implementation Complexity: High**
*   **Current State:** The current `g_auth_state` implementation uses simple bitwise OR (`g_auth_state |= FLAG_PREMIUM`) which is trivial to patch in IDA Pro or Ghidra.
*   **Strategy:**
    *   **State-Dependent Decryption:** Instead of simple bitwise checks, the `g_auth_state` should act as a seed or key to decrypt essential strings (e.g., the string `"com.android.vending"` or the commands passed to `exec()`). If the state is forced or patched incorrectly, the strings will decrypt into garbage, causing a silent crash or failure.
    *   **Control-Flow Flattening (O-LLVM):** Implement an LLVM obfuscator pass during the CMake build process. This will transform the linear execution paths in `bridge.cpp` into complex switch statements within an infinite loop, massively increasing the cognitive load required to reverse engineer the binary.
    *   **String Encryption:** Replace all plaintext strings in `bridge.cpp` (like `"Error: Invalid characters"`, `"com.android.vending"`) with compile-time encrypted byte arrays that are only decrypted at runtime using XOR with a mutating key.
