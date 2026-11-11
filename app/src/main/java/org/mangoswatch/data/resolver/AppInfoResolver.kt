package org.mangoswatch.data.resolver

import android.content.pm.PackageManager
import org.mangoswatch.domain.model.AppIdentity
import java.util.concurrent.ConcurrentHashMap

class AppInfoResolver(private val pm: PackageManager) {

    private val cache = ConcurrentHashMap<Int, AppIdentity>()

    fun resolve(uid: Int): AppIdentity = cache.getOrPut(uid) {
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull()
        val label = pkg?.let {
            try {
                pm.getApplicationInfo(it, 0).loadLabel(pm).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
        AppIdentity(pkg, label)
    }
}