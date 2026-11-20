package org.mangoswatch.data.resolver

import android.content.pm.PackageManager
import org.mangoswatch.domain.model.AppIdentity
import org.mangoswatch.domain.repository.AppInfoResolver
import java.util.concurrent.ConcurrentHashMap

class AppInfoResolverImpl(private val pm: PackageManager) : AppInfoResolver {

    private val cache = ConcurrentHashMap<Int, AppIdentity>()

    override fun resolve(uid: Int): AppIdentity = cache.getOrPut(uid) {
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