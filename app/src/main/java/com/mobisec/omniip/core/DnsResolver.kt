package com.mobisec.omniip.core

import com.google.common.cache.CacheBuilder
import java.util.concurrent.TimeUnit

object DnsResolver {
    private val dnsCache = CacheBuilder.newBuilder()
        .expireAfterAccess(2, TimeUnit.HOURS)
        .maximumSize(5000)
        .build<String, String>() // IP -> Hostname

    fun put(ip: String, hostname: String) {
        dnsCache.put(ip, hostname)
    }

    fun get(ip: String): String? {
        return dnsCache.getIfPresent(ip)
    }
}
