package com.mobisec.omniip.core

object HashUtils {
    /**
     * A lightweight, zero-allocation 64-bit MurmurHash3 implementation.
     * Mirrors the exact logic used in LwIP/Native code to hash domains and IPs to uint64_t.
     */
    fun murmurHash3(data: String): Long {
        val bytes = data.toByteArray(Charsets.UTF_8)
        val len = bytes.size
        var h1 = 0UL
        var h2 = 0UL
        val c1 = 0x87c37b91114253d5UL
        val c2 = 0x4cf5ad432745937fUL

        val nblocks = len / 16
        for (i in 0 until nblocks) {
            var k1 = 0UL
            var k2 = 0UL
            for (j in 0..7) k1 = k1 or ((bytes[i * 16 + j].toULong() and 0xFFUL) shl (j * 8))
            for (j in 0..7) k2 = k2 or ((bytes[i * 16 + 8 + j].toULong() and 0xFFUL) shl (j * 8))

            k1 *= c1
            k1 = (k1 shl 31) or (k1 shr (64 - 31))
            k1 *= c2
            h1 = h1 xor k1

            h1 = (h1 shl 27) or (h1 shr (64 - 27))
            h1 += h2
            h1 = h1 * 5uL + 0x52dce729uL

            k2 *= c2
            k2 = (k2 shl 33) or (k2 shr (64 - 33))
            k2 *= c1
            h2 = h2 xor k2

            h2 = (h2 shl 31) or (h2 shr (64 - 31))
            h2 += h1
            h2 = h2 * 5uL + 0x38495ab5uL
        }

        var k1 = 0UL
        var k2 = 0UL
        val tailIndex = nblocks * 16

        if ((len and 15) >= 15) k2 = k2 xor ((bytes[tailIndex + 14].toULong() and 0xFFUL) shl 48)
        if ((len and 15) >= 14) k2 = k2 xor ((bytes[tailIndex + 13].toULong() and 0xFFUL) shl 40)
        if ((len and 15) >= 13) k2 = k2 xor ((bytes[tailIndex + 12].toULong() and 0xFFUL) shl 32)
        if ((len and 15) >= 12) k2 = k2 xor ((bytes[tailIndex + 11].toULong() and 0xFFUL) shl 24)
        if ((len and 15) >= 11) k2 = k2 xor ((bytes[tailIndex + 10].toULong() and 0xFFUL) shl 16)
        if ((len and 15) >= 10) k2 = k2 xor ((bytes[tailIndex + 9].toULong() and 0xFFUL) shl 8)
        if ((len and 15) >= 9) {
            k2 = k2 xor ((bytes[tailIndex + 8].toULong() and 0xFFUL) shl 0)
            k2 *= c2
            k2 = (k2 shl 33) or (k2 shr (64 - 33))
            k2 *= c1
            h2 = h2 xor k2
        }

        if ((len and 15) >= 8) k1 = k1 xor ((bytes[tailIndex + 7].toULong() and 0xFFUL) shl 56)
        if ((len and 15) >= 7) k1 = k1 xor ((bytes[tailIndex + 6].toULong() and 0xFFUL) shl 48)
        if ((len and 15) >= 6) k1 = k1 xor ((bytes[tailIndex + 5].toULong() and 0xFFUL) shl 40)
        if ((len and 15) >= 5) k1 = k1 xor ((bytes[tailIndex + 4].toULong() and 0xFFUL) shl 32)
        if ((len and 15) >= 4) k1 = k1 xor ((bytes[tailIndex + 3].toULong() and 0xFFUL) shl 24)
        if ((len and 15) >= 3) k1 = k1 xor ((bytes[tailIndex + 2].toULong() and 0xFFUL) shl 16)
        if ((len and 15) >= 2) k1 = k1 xor ((bytes[tailIndex + 1].toULong() and 0xFFUL) shl 8)
        if ((len and 15) >= 1) {
            k1 = k1 xor ((bytes[tailIndex + 0].toULong() and 0xFFUL) shl 0)
            k1 *= c1
            k1 = (k1 shl 31) or (k1 shr (64 - 31))
            k1 *= c2
            h1 = h1 xor k1
        }

        h1 = h1 xor len.toULong()
        h2 = h2 xor len.toULong()
        h1 += h2
        h2 += h1

        fun fmix64(k: ULong): ULong {
            var kVar = k
            kVar = kVar xor (kVar shr 33)
            kVar *= 0xff51afd7ed558ccdUL
            kVar = kVar xor (kVar shr 33)
            kVar *= 0xc4ceb9fe1a85ec53UL
            kVar = kVar xor (kVar shr 33)
            return kVar
        }

        h1 = fmix64(h1)
        h2 = fmix64(h2)

        h1 += h2
        h2 += h1

        return h1.toLong()
    }
}
