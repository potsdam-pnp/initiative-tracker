package io.github.potsdam_pnp.initiative_tracker

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.state.State
import kotlinx.coroutines.flow.update


class ConnectionManagerAndroid(val context: Context, val repository: Repository<ActionWrapper, State>): ConnectionManager() {

    override fun registerService(name: String, port: Int) {
        // Create the NsdServiceInfo object, and populate it.
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            serviceName = name
            serviceType = "_initiative_tracker._tcp"
            setPort(port)
        }

        val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        nsdManager.discoverServices("_initiative_tracker._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun unregisterService() {
        val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
        nsdManager.unregisterService(registrationListener)
        nsdManager.stopServiceDiscovery(discoveryListener)
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(p0: NsdServiceInfo) {
            name.update { p0.serviceName }
        }

        override fun onRegistrationFailed(p0: NsdServiceInfo?, p1: Int) {
            Napier.i("registration failed")
        }

        override fun onServiceUnregistered(p0: NsdServiceInfo?) {
            name.update { null }
        }

        override fun onUnregistrationFailed(p0: NsdServiceInfo?, p1: Int) {
            Napier.i("unregistration failed")
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(p0: String?) {}

        override fun onDiscoveryStopped(p0: String?) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            when {
                service.serviceType != "_initiative_tracker._tcp." ->
                    Napier.i("Unknown Service Type: ${service.serviceType}")
                else -> {
                    val nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)

                    Napier.i("resolving")

                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(service: NsdServiceInfo) {
                            Napier.i("service resolved: $service")

                            val info = ServiceInfo(
                                name = service.serviceName,
                                clientId = null,
                                connectionInformation = ConnectionInformation(
                                    port = service.port,
                                    hosts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                        service.hostAddresses.map { it.toString().drop(1) }
                                    else
                                        listOf(service.host.toString().drop(1))
                                )
                            )

                            serviceInfoState.update {
                                it + (service.serviceName to info)
                            }
                        }

                        override fun onResolveFailed(p0: NsdServiceInfo?, p1: Int) {
                            Napier.i("service resolve failed: $p0, $p1")
                        }
                    })
                }
            }


            Napier.i("service found: $service")
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            serviceInfoState.update { it - service.serviceName }
        }

        override fun onStartDiscoveryFailed(p0: String?, p1: Int) {
        }

        override fun onStopDiscoveryFailed(p0: String?, p1: Int) {
        }
    }


}