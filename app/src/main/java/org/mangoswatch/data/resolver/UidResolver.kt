package org.mangoswatch.data.resolver

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import android.system.OsConstants
import android.util.Log
import org.mangoswatch.domain.model.StreamModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class UidResolver: KoinComponent {

    private val pm by inject<PackageManager>()
    private val connectivityManager by inject<ConnectivityManager>()

    private val pending = AtomicInteger(0)
    private var scope: CoroutineScope? = null
    private fun scope(): CoroutineScope = synchronized(this) {
        scope ?: CoroutineScope(
            SupervisorJob() + Dispatchers.IO.limitedParallelism(MAX_PARALLEL)
        ).also { scope = it }
    }

    fun shouldAttempt(stream: StreamModel, now: Long): Boolean =
        stream.uid == Process.INVALID_UID &&
                stream.uidAttempts < MAX_UID_ATTEMPTS &&
                now - stream.lastUidAttempt >= UID_RETRY_MS

    /** @return false se la coda è piena (tentativo non accodato) */
    fun resolveAsync(stream: StreamModel, onResult: (uid: Int, pkg: String?) -> Unit): Boolean {
        if (pending.incrementAndGet() > MAX_PENDING) {
            pending.decrementAndGet()
            return false
        }
        scope().launch {
            try {
                val uid = connectivityManager.getConnectionOwnerUid(
                    if (stream.protocol == 6) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP,
                    InetSocketAddress(stream.srcIpStr, stream.srcPort),
                    InetSocketAddress(stream.dstIpStr, stream.dstPort)
                )
                if (uid != Process.INVALID_UID) onResult(uid, pm.getNameForUid(uid))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "UID resolution error: ${e.message}")
            }
        }.invokeOnCompletion { pending.decrementAndGet() }
        return true
    }

    fun shutdown() {
        synchronized(this) {
            scope?.cancel()
            scope = null
        }
    }

    companion object {
        const val TAG = "[MANGOS_WATCH] - UID"
        private const val MAX_UID_ATTEMPTS = 3
        private const val UID_RETRY_MS = 1000L
        private const val MAX_PARALLEL = 2
        private const val MAX_PENDING = 256
    }

}