import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

import com.apple.dnssd.DNSSD
import com.apple.dnssd.DNSSDRegistration
import com.apple.dnssd.DNSSDService
import com.apple.dnssd.RegisterListener
import com.apple.dnssd.ResolveListener
import com.apple.dnssd.TXTRecord

class ConnectionManagerDesktop(): ConnectionManager() {
    override fun unregisterService() {
        name.update { null }
    }
    override fun registerService(name: String, resolvedPort: Int) {
        val registration = DNSSD.register("HelloDesktop", "_initiative_tracker._tcp", resolvedPort, registerListener)
        val browser = DNSSD.browse("_initiative_tracker._tcp", serviceListener)
    }

    val registerListener = object: RegisterListener {
        override fun serviceRegistered(
            registration: DNSSDRegistration?,
            flags: Int,
            serviceName: String?,
            regType: String?,
            domain: String?
        ) {
            Napier.i("Service Registered")
            name.update { serviceName }
        }

        override fun operationFailed(service: DNSSDService?, errorCode: Int) {
            Napier.i("Service Registration Failed")
        }
    }

    val serviceListener = object: BrowseListener {
        override fun serviceFound(
            browser: DNSSDService?,
            flags: Int,
            ifIndex: Int,
            serviceName: String?,
            regType: String?,
            domain: String?
        ) {
            Napier.i("Found $serviceName of $regType in $domain on $ifIndex")
            DNSSD.resolve(flags, 0, serviceName!!, regType!!, domain!!, object: ResolveListener {
                override fun serviceResolved(
                    resolver: DNSSDService?,
                    flags: Int,
                    ifIndex: Int,
                    fullName: String?,
                    hostName: String?,
                    port: Int,
                    txtRecord: TXTRecord?
                ) {
                    Napier.i("service resolved $fullName to $hostName:$port")
                    val info = ServiceInfo(
                        name = serviceName,
                        clientId = null,
                        connectionInformation = ConnectionInformation(
                            port = port,
                            hosts = listOf(hostName!!)
                        )
                    )

                    serviceInfoState.update {
                        it + (serviceName to info)
                    }
                }

                override fun operationFailed(service: DNSSDService?, errorCode: Int) {
                    Napier.i("operation failed")
                }
            })
        }

        override fun serviceLost(
            browser: DNSSDService?,
            flags: Int,
            ifIndex: Int,
            serviceName: String?,
            regType: String?,
            domain: String?
        ) {
            Napier.i("service removed")
            serviceInfoState.update {
                it - serviceName!!
            }
        }

        override fun operationFailed(service: DNSSDService?, errorCode: Int) {
            Napier.i("service browser operation failed")
        }
    }
}

var connectionManager: ConnectionManager? = null

fun main() {
    Napier.base(DebugAntilog())
    val snapshot = Snapshot(State())
    connectionManager = ConnectionManagerDesktop()
    val server = Server("Unnamed", snapshot, connectionManager!!)
    val clientConnections = ClientConnections(snapshot, connectionManager!!)

    thread {
        runBlocking {
            launch {
                server.run()
            }
            launch {
                clientConnections.run(this)
            }
            launch {
                connectionManager!!.run()
            }
        }
    }

    thread {
        sleep(1000)
        server.toggle(true)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Initiative Tracker",
        ) {
            val _model = viewModel { Model(snapshot, null) }
            App()
        }
    }
}