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
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.crdt.ClientIdentifier
import io.github.potsdam_pnp.initiative_tracker.crdt.CompareResult
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.Message
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import io.github.potsdam_pnp.initiative_tracker.proto.MessageKind
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import pbandk.encodeToByteArray

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

data class MessageDetails(
  val isActive: Boolean = false,
  val messagesConstructed: Int = 0,
  val messagesSuccessfulSent: Int = 0,
  val messagesFailedSent: Int = 0,
  val messagesReceived: Int = 0,
  val messagesConstructedSizes: List<Int> = listOf()
) {
  fun pretty(name: String): String {
    return "$name ${if (isActive) "up" else "down"}\n  Constructed: $messagesConstructed  Sent: $messagesSuccessfulSent  Failed: $messagesFailedSent\n  Received: $messagesReceived\n" + "send sizes: ${messagesConstructedSizes}"
  }
}

data class Details(
  val subscribe: MessageDetails = MessageDetails(),
  val publish: MessageDetails = MessageDetails(),
  val peers: Map<PeerHandle, VectorClock> = mapOf(),
)

class WifiAwareConnectionManager(val repository: Repository<Action, State>) {
  val _available =
    MutableStateFlow(WifiAwareAvailableState.Unknown to WifiAwareSession(false, null, false))

  private val _details = MutableStateFlow(Details())
  val details: StateFlow<Details>
    get() = _details

  fun available(scope: CoroutineScope): StateFlow<WifiAwareAvailableState> {
    return _available
      .map { it.first }
      .stateIn(scope, SharingStarted.Eagerly, _available.value.first)
  }

  private lateinit var wifiAwareManager: WifiAwareManager
  var maxMessageSize: Int? = null

  private fun initialize(context: Context) {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
      _available.update { (_, x) -> WifiAwareAvailableState.DeviceNotSupported to x }
      return
    }

    wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
    maxMessageSize = wifiAwareManager.characteristics?.maxServiceSpecificInfoLength

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
        } to x.copy(isFailed = false)
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
          it.invokeOnCancellation {
            _available.value.second.current?.close()
            _available.update { it.copy(second = it.second.copy(current = null, isFailed = false)) }
          }
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

  private fun subscribePayload(publishData: PublishData): ByteArray {
    val dots =
      (publishData.fromPosition until (publishData.clock.clock[repository.clientIdentifier] ?: 0))
        .map { Dot(repository.clientIdentifier, it + 1) }
    val versions = dots.map { repository.fetchVersion(it)!! }
    return Encoders.encodePb(Message.SendVersions(publishData.clock, versions))
  }

  private data class PublishData(val clock: VectorClock, val fromPosition: Int) {
    companion object {
      fun from(clientIdentifier: ClientIdentifier, vc: VectorClock, d: Details): PublishData {
        val us = vc.clock[clientIdentifier] ?: 0
        var smallest = us
        d.peers.forEach { entry ->
          val them = entry.value.clock[clientIdentifier] ?: 0
          if (us - them < 10 && smallest > them) {
            smallest = them
          }
        }
        return PublishData(vc, smallest)
      }
    }
  }

  private suspend fun runPublish(wifiAwareSession: WifiAwareSession) {
    val publishSession =
      MutableStateFlow<Pair<PublishDiscoverySession?, PublishData?>>(null to null)

    coroutineScope {
      launch {
        combine(repository.version, publishSession, _details) { vc, publish, d ->
            val publishData = PublishData.from(repository.clientIdentifier, vc, d)
            if (publishData == publish.second) null else publishData to publish.first
          }
          .filterNotNull()
          .collect { (publishData, publishSession) ->
            publishSession?.updatePublish(
              PublishConfig.Builder()
                .setServiceName(serviceName)
                .setServiceSpecificInfo(subscribePayload(publishData))
                .build()
            )
          }
      }

      suspendCancellableCoroutine<Unit> { continuation ->
        @SuppressLint("MissingPermission")
        fun publish() {
          _details.update { it.copy(publish = it.publish.copy(isActive = false)) }
          if (continuation.isActive) {
            val publishData =
              PublishData.from(
                repository.clientIdentifier,
                repository.version.value,
                _details.value,
              )
            val publishConfig =
              PublishConfig.Builder()
                .setServiceName(serviceName)
                .setServiceSpecificInfo(subscribePayload(publishData))
                .build()
            wifiAwareSession.publish(
              publishConfig,
              object : DiscoverySessionCallback() {
                override fun onSessionTerminated() {
                  publishSession.update { null to null }
                  publish()
                }

                override fun onPublishStarted(session: PublishDiscoverySession) {
                  _details.update { it.copy(publish = it.publish.copy(isActive = true)) }
                  publishSession.update { session to publishData }
                }

                override fun onSessionConfigUpdated() {}

                override fun onSessionConfigFailed() {}

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                  _details.update {
                    it.copy(
                      publish = it.publish.copy(messagesReceived = it.publish.messagesReceived + 1)
                    )
                  }
                  when (val msg = Encoders.decodePb(message)) {
                    is Message.RequestVersions -> {
                      val maxSize = (maxMessageSize ?: 128).coerceAtMost(msg.maxMessageSize ?: 128)
                      val bytes =
                        Encoders.encodeSendVersionsMaxSize(
                          maxSize,
                          msg.fromVectorClock,
                          msg.vectorClock,
                          msg.msgIdentifier,
                        ) {
                          repository.fetchVersion(it)!!
                        }
                      publishSession.value.first?.sendMessage(peerHandle, 0, bytes)
                      _details.update {
                        it.copy(
                          publish =
                            it.publish.copy(
                              messagesConstructed = it.publish.messagesConstructed + 1,
                              messagesConstructedSizes = it.publish.messagesConstructedSizes + listOf(bytes.size)
                            )
                        )
                      }
                    }
                    else -> {}
                  }
                }

                override fun onMessageSendSucceeded(messageId: Int) {
                  _details.update {
                    it.copy(
                      publish =
                        it.publish.copy(
                          messagesSuccessfulSent = it.publish.messagesSuccessfulSent + 1
                        )
                    )
                  }
                }

                override fun onMessageSendFailed(messageId: Int) {
                  _details.update {
                    it.copy(
                      publish =
                        it.publish.copy(messagesFailedSent = it.publish.messagesFailedSent + 1)
                    )
                  }
                }
              },
              null,
            )
          }
        }

        publish()
        continuation.invokeOnCancellation {
          try {
            publishSession.value.first?.close()
          } catch (_: SecurityException) {}
          _details.update { it.copy(publish = it.publish.copy(isActive = false)) }
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun runSubscribe(wifiAwareSession: WifiAwareSession) {
    val subscribeSession =
      MutableStateFlow<Pair<SubscribeDiscoverySession?, Map<PeerHandle, VectorClock>>>(
        null to mapOf()
      )
    val subscribeConfig = SubscribeConfig.Builder().setServiceName(serviceName).build()
    val messageState =
      MutableStateFlow<Triple<Int, Pair<VectorClock, Int>?, MessageState?>>(Triple(0, null, null))

    coroutineScope {
      launch {
        while (true) {
          Napier.i("start subscribe loop")
          val (peer, value, vc) =
            subscribeSession
              .combine(repository.prefetched) { (session, peers), vc ->
                peers
                  .mapNotNull { (peer, value) ->
                    val ok =
                      !vc.contains(value) &&
                        peers.all { (_, v) -> value.compare(v) != CompareResult.Smaller }
                    if (!ok) null else Triple(session to peer, value, vc)
                  }
                  .ifEmpty { null }
              }
              .filterNotNull()
              .first()
              .random()
          Napier.i("found value to request")

          val msgIdentifier = Random.nextInt()
          val clientIdentifiers = value.clock.keys.toList()
          val requestMsg =
            io.github.potsdam_pnp.initiative_tracker.proto.Message(
              messageKind = MessageKind.REQUEST_VERSIONS,
              messageIdentifier = msgIdentifier,
              maxMessageLength = maxMessageSize,
              clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
              clock = clientIdentifiers.map { value.clock[it]?.toLong() ?: 0 },
              requestClock = clientIdentifiers.map { vc.clock[it] ?: 0 },
            )

          val (messageNr, _, _) =
            messageState.updateAndGet { previous ->
              Triple(previous.first + 1, value to msgIdentifier, null)
            }
          peer.first?.sendMessage(peer.second, messageNr, requestMsg.encodeToByteArray())
          _details.update {
            it.copy(
              subscribe =
                it.subscribe.copy(messagesConstructed = it.subscribe.messagesConstructed + 1)
            )
          }
          Napier.i("sent message")
          when (messageState.first { it.third != null }.third) {
            MessageState.MessageSentFailed -> {}
            MessageState.MessageReceived -> {}
            MessageState.MessageSentSucceeded -> {
              Napier.i("message sent succeeded")

              val nextValue = async {
                messageState.first { it.third == MessageState.MessageReceived }
              }

              select<Boolean> {
                nextValue.onAwait {
                  Napier.i("request processed")
                  true
                }
                onTimeout(1000) {
                  Napier.i("request timed out")
                  false
                }
              }

              nextValue.cancelAndJoin()
            }

            null -> {}
          }
          Napier.i("end of subscribe loop")
        }
      }

      suspendCancellableCoroutine<Unit> { continuation ->
        @SuppressLint("MissingPermission")
        fun subscribe() {
          _details.update {
            it.copy(subscribe = it.subscribe.copy(isActive = false), peers = mapOf())
          }
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
                  _details.update { it.copy(subscribe = it.subscribe.copy(isActive = true)) }
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
                      _details.update { it.copy(peers = subscribeSession.value.second) }
                    }
                    is Message.SendVersions -> {
                      repository.insert(msg.vectorClock, msg.versions)
                      subscribeSession.update {
                        it.copy(second = it.second + (peerHandle to msg.vectorClock))
                      }
                      _details.update { it.copy(peers = subscribeSession.value.second) }
                    }
                    else -> {}
                  }
                }

                override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                  subscribeSession.update { it.copy(second = it.second - peerHandle) }
                  _details.update { it.copy(peers = subscribeSession.value.second) }
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                  _details.update {
                    it.copy(
                      subscribe =
                        it.subscribe.copy(messagesReceived = it.subscribe.messagesReceived + 1)
                    )
                  }
                  when (val msg = Encoders.decodePb(message)) {
                    is Message.SendVersions -> {
                      repository.insert(msg.vectorClock, msg.versions)
                      messageState.update {
                        val vc = it.second
                        if (vc == null) {
                          it
                        } else if (
                          msg.vectorClock.contains(vc.first) || msg.msgIdentifier == vc.second
                        ) {
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
                  _details.update {
                    it.copy(
                      subscribe =
                        it.subscribe.copy(messagesFailedSent = it.subscribe.messagesFailedSent + 1)
                    )
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
                  _details.update {
                    it.copy(
                      subscribe =
                        it.subscribe.copy(
                          messagesSuccessfulSent = it.subscribe.messagesSuccessfulSent + 1
                        )
                    )
                  }
                }
              },
              null,
            )
          }
        }

        subscribe()

        continuation.invokeOnCancellation {
          try {
            subscribeSession.value.first?.close()
          } catch (_: SecurityException) {}
          _details.update {
            it.copy(subscribe = it.subscribe.copy(isActive = false), peers = mapOf())
          }
        }
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
