package io.github.potsdam_pnp.initiative_tracker

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import io.github.potsdam_pnp.initiative_tracker.crdt.CompareResult
import io.github.potsdam_pnp.initiative_tracker.crdt.InsertResult
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

enum class WifiAwareAvailableState {
  Unknown,
  DeviceNotSupported,
  MissingPermissions,
  NotAvailable,
  Available,
}

data class WifiAwareSession(
  val enabled: Boolean,
  val current: WifiAwareSession?,
  val isFailed: Boolean,
)

class WifiAwareConnectionManager(val repository: Repository<Action, State>) {
  private val _available =
    MutableStateFlow(WifiAwareAvailableState.Unknown to WifiAwareSession(false, null, false))

  fun available(scope: CoroutineScope): StateFlow<WifiAwareAvailableState> {
    return _available
      .map { it.first }
      .stateIn(scope, SharingStarted.Eagerly, _available.value.first)
  }

  private lateinit var wifiAwareManager: WifiAwareManager

  private fun initialize(context: Context) {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
      _available.update { (_, x) -> WifiAwareAvailableState.DeviceNotSupported to x }
      return
    }

    wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager

    if (!hasPermissions(context)) {
      _available.update { (_, x) -> WifiAwareAvailableState.MissingPermissions to x }
      return
    }

    // When initialized was already called, we need to stop here
    when (_available.value.first) {
      WifiAwareAvailableState.Available -> return
      WifiAwareAvailableState.NotAvailable -> return
      WifiAwareAvailableState.Unknown -> {}
      WifiAwareAvailableState.DeviceNotSupported -> {}
      WifiAwareAvailableState.MissingPermissions -> {}
    }

    fun updateAvailabilityState() {
      val available = wifiAwareManager.isAvailable
      _available.update { (_, x) ->
        if (available) {
          WifiAwareAvailableState.Available
        } else {
          WifiAwareAvailableState.NotAvailable
        } to x
      }
    }

    val filter = IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
    val myReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          updateAvailabilityState()
        }
      }
    context.registerReceiver(myReceiver, filter)
    updateAvailabilityState()
  }

  private fun hasPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.checkSelfPermission(NEARBY_WIFI_DEVICES) == PERMISSION_GRANTED
    } else {
      context.checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
    }
  }

  fun neededMissingPermission(context: Context): String? {
    if (_available.value.first != WifiAwareAvailableState.MissingPermissions) {
      return null
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (context.checkSelfPermission(NEARBY_WIFI_DEVICES) != PERMISSION_GRANTED) {
        NEARBY_WIFI_DEVICES
      } else {
        initialize(context)
        null
      }
    } else {
      if (context.checkSelfPermission(ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
        ACCESS_FINE_LOCATION
      } else {
        initialize(context)
        null
      }
    }
  }

  fun start() {
    _available.update { it.copy(second = it.second.copy(enabled = true)) }
  }

  fun stop() {
    _available.update { it.copy(second = it.second.copy(enabled = false, isFailed = false)) }
  }

  private suspend fun runAttach() {
    coroutineScope {
      var job: Job? = null
      launch {
        suspendCancellableCoroutine {
          it.invokeOnCancellation { _available.value.second.current?.close() }
        }
      }
      _available.collect { (state, value) ->
        val shouldBeRunning =
          state == WifiAwareAvailableState.Available && value.enabled && !value.isFailed

        if (shouldBeRunning && value.current == null) {
          job?.cancelAndJoin()
          val terminated = Channel<Unit>()
          job = launch {
            terminated.receive()
            _available.update { it.copy(second = it.second.copy(current = null)) }
          }
          val result = attach(onTerminated = { terminated.trySend(Unit) })
          if (result == null) {
            _available.update { it.copy(second = it.second.copy(isFailed = true)) }
          } else {
            _available.update { it.copy(second = it.second.copy(current = result)) }
          }
        } else if (!shouldBeRunning && value.current != null) {
          value.current.close()
          _available.update { it.copy(second = it.second.copy(current = null, isFailed = false)) }
          job?.cancelAndJoin()
          job = null
        }
      }
    }
  }

  private val serviceName = "io.github.potsdam_pnp.initiative_tracker"

  private fun subscribePayload(vc: VectorClock): ByteArray {
    return Encoders.encodePb(Message.CurrentState(vc))
  }

  private suspend fun runPublish(wifiAwareSession: WifiAwareSession) {
    val publishSession =
      MutableStateFlow<Pair<PublishDiscoverySession?, VectorClock?>>(null to null)

    coroutineScope {
      launch {
        repository.version
          .combine(publishSession) { vc, publish ->
            if (vc == publish.second) null else vc to publish.first
          }
          .filterNotNull()
          .collect { (vc, publishSession) ->
            publishSession?.updatePublish(
              PublishConfig.Builder()
                .setServiceName(serviceName)
                .setServiceSpecificInfo(subscribePayload(vc))
                .build()
            )
          }
      }

      suspendCancellableCoroutine<Unit> { continuation ->
        @SuppressLint("MissingPermission")
        fun publish() {
          if (continuation.isActive) {
            val vc = repository.version.value
            val publishConfig =
              PublishConfig.Builder()
                .setServiceName(serviceName)
                .setServiceSpecificInfo(subscribePayload(vc))
                .build()
            wifiAwareSession.publish(
              publishConfig,
              object : DiscoverySessionCallback() {
                override fun onSessionTerminated() {
                  publishSession.update { null to null }
                  publish()
                }

                override fun onPublishStarted(session: PublishDiscoverySession) {
                  publishSession.update { session to vc }
                }

                override fun onSessionConfigUpdated() {}

                override fun onSessionConfigFailed() {}

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                  when (val msg = Encoders.decodePb(message)) {
                    is Message.RequestVersions -> {
                      val versions = msg.dots.mapNotNull { repository.fetchVersion(it) }
                      val bytes = Encoders.encodePb(Message.SendVersions(msg.vectorClock, versions))
                      publishSession.value.first?.sendMessage(peerHandle, 0, bytes)
                    }
                    else -> {}
                  }
                }
              },
              null,
            )
          }
        }

        publish()
        continuation.invokeOnCancellation { publishSession.value.first?.close() }
      }
    }
  }

  private suspend fun runSubscribe(wifiAwareSession: WifiAwareSession) {
    val subscribeSession =
      MutableStateFlow<Pair<SubscribeDiscoverySession?, Map<PeerHandle, VectorClock>>>(
        null to mapOf()
      )
    val subscribeConfig = SubscribeConfig.Builder().setServiceName(serviceName).build()
    val messageState =
      MutableStateFlow<Triple<Int, VectorClock?, MessageState?>>(Triple(0, null, null))

    coroutineScope {
      launch {
        subscribeSession.collect { (session, peers) ->
          val vc = repository.version.value
          val v =
            peers.firstNotNullOfOrNull { (peer, value) ->
              val ok =
                !vc.contains(value) &&
                  peers.all { (_, v) -> value.compare(v) != CompareResult.Smaller }
              if (!ok) null else (peer to value)
            }
          if (v != null) {
            when (val r = repository.insert(v.second, listOf())) {
              is InsertResult.MissingVersions -> {
                val requestMsg = Encoders.encodePb(Message.RequestVersions(v.second, r.missingDots))
                val (messageNr, _, _) =
                  messageState.updateAndGet { previous ->
                    Triple(previous.first + 1, v.second, null)
                  }
                session?.sendMessage(v.first, messageNr, requestMsg)

                when (messageState.first { it.third != null }.third) {
                  MessageState.MessageSentFailed -> {}
                  MessageState.MessageReceived -> {}
                  MessageState.MessageSentSucceeded -> {
                    withTimeout(1000) {
                      messageState.first { it.third == MessageState.MessageReceived }
                    }
                  }
                  null -> {}
                }
              }

              is InsertResult.Success -> {}
            }
          }
        }
      }

      suspendCancellableCoroutine<Unit> { continuation ->
        @SuppressLint("MissingPermission")
        fun subscribe() {
          if (continuation.isActive) {
            wifiAwareSession.subscribe(
              subscribeConfig,
              object : DiscoverySessionCallback() {
                override fun onSessionTerminated() {
                  subscribeSession.update { null to mapOf() }
                  subscribe()
                }

                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                  subscribeSession.update { session to mapOf() }
                }

                override fun onServiceDiscovered(
                  peerHandle: PeerHandle,
                  serviceSpecificInfo: ByteArray,
                  matchFilter: MutableList<ByteArray>?,
                ) {
                  when (val msg = Encoders.decodePb(serviceSpecificInfo)) {
                    is Message.CurrentState -> {
                      subscribeSession.update {
                        it.copy(second = it.second + (peerHandle to msg.vectorClock))
                      }
                    }

                    else -> {}
                  }
                }

                override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                  subscribeSession.update { it.copy(second = it.second - peerHandle) }
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                  when (val msg = Encoders.decodePb(message)) {
                    is Message.SendVersions -> {
                      repository.insert(msg.vectorClock, msg.versions)
                      messageState.update {
                        val vc = it.second
                        if (vc == null) {
                          it
                        } else if (msg.vectorClock.contains(vc)) {
                          it.copy(third = MessageState.MessageReceived)
                        } else {
                          it
                        }
                      }
                    }
                    else -> {}
                  }
                }

                override fun onMessageSendFailed(messageId: Int) {
                  messageState.update {
                    if (it.first == messageId && it.third == null) {
                      it.copy(third = MessageState.MessageSentFailed)
                    } else {
                      it
                    }
                  }
                }

                override fun onMessageSendSucceeded(messageId: Int) {
                  messageState.update {
                    if (it.first == messageId && it.third == null) {
                      it.copy(third = MessageState.MessageSentSucceeded)
                    } else {
                      it
                    }
                  }
                }
              },
              null,
            )
          }
        }

        subscribe()

        continuation.invokeOnCancellation { subscribeSession.value.first?.close() }
      }
    }
  }

  private suspend fun attach(onTerminated: () -> Unit): WifiAwareSession? {
    return suspendCoroutine { continuation ->
      wifiAwareManager.attach(
        object : AttachCallback() {
          override fun onAttached(session: WifiAwareSession) {
            continuation.resume(session)
          }

          override fun onAttachFailed() {
            continuation.resume(null)
          }

          override fun onAwareSessionTerminated() {
            onTerminated()
          }
        },
        null,
      )
    }
  }

  fun run(context: Context, scope: CoroutineScope) {
    initialize(context)
    scope.launch { runAttach() }

    scope.launch {
      var job: Job? = null
      _available
        .map { it.second.current }
        .distinctUntilChanged()
        .collect { session ->
          job?.cancelAndJoin()
          if (session != null) {
            job = launch {
              launch { runPublish(session) }
              launch { runSubscribe(session) }
            }
          }
        }
    }
  }
}

enum class MessageState {
  MessageSentFailed,
  MessageSentSucceeded,
  MessageReceived,
}
