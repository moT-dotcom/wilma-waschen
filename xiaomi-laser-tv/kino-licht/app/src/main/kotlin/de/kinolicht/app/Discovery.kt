package de.kinolicht.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

data class FoundServer(val name: String, val host: String, val port: Int)

/** Findet Hyperion-Server im Netz über mDNS (Dienst _hyperiond-json._tcp). */
class HyperionDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val _servers = MutableStateFlow<List<FoundServer>>(emptyList())
    val servers: StateFlow<List<FoundServer>> = _servers.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null || nsd == null) return
        _servers.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { _searching.value = true }
            override fun onDiscoveryStopped(serviceType: String) { _searching.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Suche fehlgeschlagen: $errorCode")
                _searching.value = false
                listener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { _searching.value = false }
            override fun onServiceFound(info: NsdServiceInfo) = enqueue(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _servers.value = _servers.value.filterNot { it.name == info.serviceName }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { listener = null; _searching.value = false }
    }

    fun stop() {
        listener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        listener = null
        _searching.value = false
    }

    @Synchronized
    private fun enqueue(info: NsdServiceInfo) {
        queue.add(info)
        resolveNext()
    }

    // NsdManager kann nur einen Dienst gleichzeitig auflösen.
    @Synchronized
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving || nsd == null) return
        val next = queue.poll() ?: return
        resolving = true
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = done()
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (host != null) {
                    val server = FoundServer(info.serviceName, host, info.port)
                    _servers.value = (_servers.value.filterNot { it.host == host && it.port == info.port } + server)
                        .sortedBy { it.name }
                }
                done()
            }
        })
    }

    @Synchronized
    private fun done() {
        resolving = false
        resolveNext()
    }

    private companion object {
        const val TAG = "KinoLichtDiscovery"
        const val SERVICE_TYPE = "_hyperiond-json._tcp."
    }
}
