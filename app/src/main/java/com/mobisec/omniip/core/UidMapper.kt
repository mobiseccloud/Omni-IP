package com.mobisec.omniip.core

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import java.net.InetSocketAddress
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

object UidMapper {
    private const val TAG = "UidMapper"
    fun resolveUid(
        context: Context,
        protocol: Int,
        sourceIp: InetAddress,
        sourcePort: Int,
        destIp: InetAddress,
        destPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val local = InetSocketAddress(sourceIp, sourcePort)
                val remote = InetSocketAddress(destIp, destPort)
                return cm.getConnectionOwnerUid(protocol, local, remote)
            } catch (e: android.os.RemoteException) {
                Log.e(TAG, "RemoteException resolving UID", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException resolving UID", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "IllegalArgumentException resolving UID", e)
            }
        }
        return -2 // Fallback or unsupported
    }

    private val appInfoCache = ConcurrentHashMap<Int, Pair<String, String>>()

    fun getAppInfo(context: Context, uid: Int): Triple<String, String, Drawable?> {
        if (uid == -2) {
            return Triple("Error", "uid:error", null)
        }

        if (appInfoCache.containsKey(uid)) {
            val cached = appInfoCache[uid]!!
            return Triple(cached.first, cached.second, null)
        }

        val pm = context.packageManager
        val packages = pm.getPackagesForUid(uid)

        var info: Triple<String, String, Drawable?>

        if (!packages.isNullOrEmpty()) {
            val packageName = packages[0]
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                info = Triple(appName, packageName, icon)
            } catch (e: PackageManager.NameNotFoundException) {
                info = Triple("Unknown ($packageName)", packageName, null)
            }
        } else {
            info = Triple("Unknown (UID $uid)", "uid:$uid", null)
        }

        appInfoCache[uid] = Pair(info.first, info.second)
        return info
    }
}
