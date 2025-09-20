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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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

data class MessageSizes(
  val min: Int = -1,
  val max: Int = -1,
  val count: Int = 0,
  val latest: List<Int> = listOf(),
) {
  fun add(size: Int): MessageSizes {
    return copy(
      min = size.coerceAtLeast(min),
      max = size.coerceAtMost(max),
      count = count + 1,
      latest = latest.subList((latest.size - 5).coerceAtLeast(0), latest.size) + size,
    )
  }

  fun pretty(): String {
    return "($count values between $min and $max, latest ${latest.joinToString(separator = ",") }"
  }
}

data class MessageDetails(
  val isActive: Boolean = false,
  val messagesConstructed: Int = 0,
  val messagesSuccessfulSent: Int = 0,
  val messagesFailedSent: Int = 0,
  val messagesReceived: Int = 0,
  val messagesConstructedSizes: MessageSizes = MessageSizes(),
) {
  fun pretty(name: String): String {
    return "$name ${if (isActive) "up" else "down"}\n  Constructed: $messagesConstructed  Sent: $messagesSuccessfulSent  Failed: $messagesFailedSent\n  Received: $messagesReceived\n" +
      "send sizes: ${messagesConstructedSizes.pretty()}"
  }
}

enum class OngoingHeartbeat {
  TriggerSend,
  SendSucceeded,
}

data class HeartbeatState(
  val ongoingHeartbeat: Pair<Int, OngoingHeartbeat>?,
  val failedHeartbeats: Int,
  val timeoutHeartbeats: Int,
  val succesfulHeartbeats: Int,
) {
  fun pretty(): String? {
    return when {
      succesfulHeartbeats > 0 -> "connected ($succesfulHeartbeats)"
      failedHeartbeats == 0 && timeoutHeartbeats == 0 -> null
      failedHeartbeats > 0 && timeoutHeartbeats > 0 ->
        "no response to heartbeat ($failedHeartbeats times no response, $timeoutHeartbeats timeouts"
      failedHeartbeats > 0 -> "no response to heartbeat ($failedHeartbeats times no response)"
      else -> "no response to heartbeat ($timeoutHeartbeats timeouts)"
    }
  }

  fun isWaitingForReply(): Int? {
    return if (ongoingHeartbeat?.second == OngoingHeartbeat.SendSucceeded) {
      ongoingHeartbeat.first
    } else {
      null
    }
  }

  fun allowSend(): Boolean {
    return failedHeartbeats == 0 && timeoutHeartbeats == 0
  }

  fun shouldSendHeartbeat(): Int? {
    if (ongoingHeartbeat != null) {
      return null
    } else {
      val combined = failedHeartbeats + timeoutHeartbeats + succesfulHeartbeats
      return when {
        combined == 0 -> 0
        combined < 5 -> 2500
        combined < 10 -> 5000
        else -> 10000
      }
    }
  }

  fun receivedHeartbeat(id: Int): HeartbeatState {
    return if (ongoingHeartbeat?.first == id) {
      HeartbeatState(null, 0, 0, succesfulHeartbeats = succesfulHeartbeats + 1)
    } else {
      Napier.i("no match in heartbeat id")
      this
    }
  }

  fun heartbeatFailed(id: Int): HeartbeatState {
    return if (ongoingHeartbeat?.first == id) {
      HeartbeatState(
        null,
        failedHeartbeats = failedHeartbeats + 1,
        timeoutHeartbeats = timeoutHeartbeats,
        succesfulHeartbeats = 0,
      )
    } else {
      Napier.i("no match in heartbeat id")
      this
    }
  }

  fun heartbeatSucceeded(id: Int): HeartbeatState {
    return if (ongoingHeartbeat?.first == id) {
      copy(ongoingHeartbeat = id to OngoingHeartbeat.SendSucceeded)
    } else {
      Napier.i("no match in heartbeat id")
      this
    }
  }

  fun heartbeatTimeout(id: Int): HeartbeatState {
    return if (ongoingHeartbeat?.first == id) {
      HeartbeatState(
        null,
        failedHeartbeats = failedHeartbeats,
        timeoutHeartbeats = timeoutHeartbeats + 1,
        succesfulHeartbeats = 0,
      )
    } else {
      Napier.i("no match in heartbeat id")
      this
    }
  }

  fun triggerSend(): Pair<Int, HeartbeatState>? {
    if (ongoingHeartbeat != null) {
      return null
    }
    val id = Random.nextInt()
    return id to copy(ongoingHeartbeat = id to OngoingHeartbeat.TriggerSend)
  }

  companion object {
    fun new(): HeartbeatState {
      return HeartbeatState(null, 0, 0, 0)
    }
  }
}

data class PeerInfo(
  val state: VectorClock,
  val heartbeatState: HeartbeatState,
  val clientIdentifier: ClientIdentifier,
) {
  fun triggerSendHeartbeat(): Pair<Int, PeerInfo>? {
    return heartbeatState.triggerSend()?.let { it.first to copy(heartbeatState = it.second) }
  }
}

data class Details(
  val subscribe: MessageDetails = MessageDetails(),
  val publish: MessageDetails = MessageDetails(),
  val sessionConfig: MessageDetails = MessageDetails(),
  val peers: Map<PeerHandle, PeerInfo> = mapOf(),
  val terminated: Int = 0,
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
    return Encoders.encodePb(
      Message.SendVersions(publishData.clock, versions, null, repository.clientIdentifier)
    )
  }

  private data class PublishData(
    val clock: VectorClock,
    val fromPosition: Int,
    val updatingTo: Pair<VectorClock, Int>?,
    val failedUpdates: Int,
  ) {
    fun updateToNext(): PublishData {
      check(updatingTo != null)
      return PublishData(
        clock = updatingTo.first,
        fromPosition = updatingTo.second,
        updatingTo = null,
        failedUpdates = 0,
      )
    }

    fun cancelUpdate(): PublishData {
      check(updatingTo != null)
      return copy(updatingTo = null, failedUpdates = failedUpdates + 1)
    }

    fun triggerUpdate(updateTo: PublishData): PublishData {
      check(updatingTo == null)
      return copy(updatingTo = updateTo.clock to updateTo.fromPosition)
    }

    suspend fun waitBeforeNextUpdatePublish() {
      when {
        failedUpdates == 0 -> return
        failedUpdates < 2 -> delay(512 + Random.nextInt(512).toLong())
        failedUpdates < 10 -> delay(1024 + Random.nextInt(1024).toLong())
        failedUpdates < 30 -> delay(10000 + Random.nextInt(1024).toLong())
        else -> delay(60000 + Random.nextInt(1024).toLong())
      }
    }

    companion object {
      fun from(
        clientIdentifier: ClientIdentifier,
        vc: VectorClock,
        peers: Map<PeerHandle, PeerInfo>,
      ): PublishData {
        val us = vc.clock[clientIdentifier] ?: 0
        val smallestMap = mutableMapOf<ClientIdentifier, Int>()
        peers.forEach { entry ->
          val them = entry.value.state.clock[clientIdentifier] ?: 0
          if (us - them < 10 && us > them) {
            smallestMap[entry.value.clientIdentifier] =
              them.coerceAtMost(smallestMap[entry.value.clientIdentifier] ?: them)
          }
        }
        return PublishData(vc, smallestMap.values.minOrNull() ?: us, null, failedUpdates = 0)
      }
    }
  }

  private suspend fun runPublish(wifiAwareSession: WifiAwareSession) {
    val publishSession =
      MutableStateFlow<Pair<PublishDiscoverySession?, PublishData?>>(null to null)

    coroutineScope {
      launch {
        while (true) {
          val (publishData, p) =
            combine(repository.version, publishSession, details.map { it.peers }) { vc, publish, p
                ->
                val pf = publish.first
                if (pf == null || publish.second?.updatingTo != null) {
                  null
                } else {
                  val publishData = PublishData.from(repository.clientIdentifier, vc, p)
                  if (publishData == publish.second) {
                    null
                  } else {
                    publishData to Pair(pf, publish.second)
                  }
                }
              }
              .filterNotNull()
              .first()

          p.second?.waitBeforeNextUpdatePublish()

          val payload = subscribePayload(publishData)
          p.first.updatePublish(
            PublishConfig.Builder()
              .setServiceName(serviceName)
              .setServiceSpecificInfo(payload)
              .build()
          )
          publishSession.update { it.copy(second = it.second?.triggerUpdate(publishData)) }
          _details.update {
            it.copy(
              sessionConfig =
                it.sessionConfig.copy(
                  messagesConstructed = it.sessionConfig.messagesConstructed + 1,
                  messagesConstructedSizes =
                    it.sessionConfig.messagesConstructedSizes.add(payload.size),
                )
            )
          }
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
                _details.value.peers,
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
                  _details.update {
                    it.copy(terminated = it.terminated + 1)
                  }
                  publish()
                }

                override fun onPublishStarted(session: PublishDiscoverySession) {
                  _details.update { it.copy(publish = it.publish.copy(isActive = true)) }
                  publishSession.update { session to publishData }
                }

                override fun onSessionConfigUpdated() {
                  publishSession.update { it.copy(second = it.second?.updateToNext()) }
                  _details.update {
                    it.copy(
                      sessionConfig =
                        it.sessionConfig.copy(
                          messagesSuccessfulSent = it.sessionConfig.messagesSuccessfulSent + 1,
                          messagesReceived = 0,
                        )
                    )
                  }
                }

                override fun onSessionConfigFailed() {
                  val f = publishSession.updateAndGet { it.copy(second = it.second?.cancelUpdate()) }
                  _details.update {
                    it.copy(
                      sessionConfig =
                        it.sessionConfig.copy(
                          messagesFailedSent = it.sessionConfig.messagesFailedSent + 1,
                          messagesReceived = f.second?.failedUpdates ?: -1
                        )
                    )
                  }
                }

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
                          repository.clientIdentifier,
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
                              messagesConstructedSizes =
                                it.publish.messagesConstructedSizes.add(bytes.size),
                            )
                        )
                      }
                    }
                    is Message.Heartbeat ->
                      publishSession.value.first?.sendMessage(peerHandle, 17, message)
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
            _details.update { it.copy(terminated = it.terminated + 1000)}
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
      MutableStateFlow<Pair<SubscribeDiscoverySession?, Map<PeerHandle, PeerInfo>>>(null to mapOf())
    val subscribeConfig = SubscribeConfig.Builder().setServiceName(serviceName).build()
    val messageState =
      MutableStateFlow<Triple<Int, Pair<PeerInfo, Int>?, MessageState?>>(Triple(0, null, null))

    coroutineScope {
      launch {
        subscribeSession
          .map { it.second }
          .collect { ss -> _details.update { it.copy(peers = ss) } }
      }
      launch {
        while (true) {
          Napier.i("start subscribe loop")
          val (peer, value, vc) =
            subscribeSession
              .combine(repository.prefetched) { (session, peers), vc ->
                peers
                  .mapNotNull { (peer, value) ->
                    val ok =
                      !vc.contains(value.state) &&
                        peers.all { (_, v) ->
                          value.state.compare(v.state) != CompareResult.Smaller ||
                            !v.heartbeatState.allowSend()
                        }
                    if (!ok) null else Triple(session to peer, value, vc)
                  }
                  .ifEmpty { null }
              }
              .filterNotNull()
              .first()
              .random()
          Napier.i("found value to request")

          val msgIdentifier = Random.nextInt()
          val clientIdentifiers = value.state.clock.keys.toList()
          val requestMsg =
            io.github.potsdam_pnp.initiative_tracker.proto.Message(
              messageKind = MessageKind.REQUEST_VERSIONS,
              messageIdentifier = msgIdentifier,
              maxMessageLength = maxMessageSize,
              clientIdentifiers = clientIdentifiers.map { it.encodeToProto() },
              clock = clientIdentifiers.map { value.state.clock[it]?.toLong() ?: 0 },
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
                onTimeout(2000) {
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

      launch(CoroutineName("subscribe heartbeats")) {
        while (true) {
          val (session, peerInfo) =
            subscribeSession
              .mapNotNull { (session, peerInfo) ->
                if (session == null) {
                  null
                } else {
                  val peers =
                    peerInfo.mapNotNull { entry ->
                      entry.value.heartbeatState.shouldSendHeartbeat()?.let { entry.key to it }
                    }
                  if (peers.isEmpty()) {
                    null
                  } else {
                    session to peers
                  }
                }
              }
              .filterNotNull()
              .first()

          peerInfo.forEach { (peerHandle, d) ->
            var value: Int? = null
            subscribeSession.update {
              value = null
              val triggered = it.second[peerHandle]?.triggerSendHeartbeat()
              if (triggered != null) {
                value = triggered.first
                it.copy(second = it.second + (peerHandle to triggered.second))
              } else {
                it
              }
            }

            val v = value

            if (v != null) {
              launch {
                delay(d.toLong())
                session.sendMessage(
                  peerHandle,
                  v,
                  io.github.potsdam_pnp.initiative_tracker.proto
                    .Message(messageKind = MessageKind.HEARTBEAT, messageIdentifier = value)
                    .encodeToByteArray(),
                )
              }
            }
          }
        }
      }

      launch(CoroutineName("heartbeat timeouts")) {
        var timeoutJobs: Map<Int, Job> = mapOf()

        while (true) {
          val values =
            subscribeSession
              .map { it.second.mapNotNull { it.value.heartbeatState.isWaitingForReply() }.toSet() }
              .filter { timeoutJobs.keys != it }
              .first()

          val diff = (timeoutJobs - values)
          diff.values.forEach { job -> job.cancelAndJoin() }

          timeoutJobs =
            values
              .associateWith { id ->
                timeoutJobs[id]
                  ?: (launch {
                    delay(3000)
                    subscribeSession.update {
                      it.copy(
                        second =
                          it.second.mapValues {
                            it.value.copy(
                              heartbeatState = it.value.heartbeatState.heartbeatTimeout(id)
                            )
                          }
                      )
                    }
                  })
              }
              .toMap()
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
                        if (it.second[peerHandle]?.state == msg.vectorClock) {
                          it // nothing new, so don't reset failed counter
                        } else {
                          it.copy(
                            second =
                              it.second +
                                (peerHandle to
                                  PeerInfo(
                                    msg.vectorClock,
                                    HeartbeatState.new(),
                                    msg.clientIdentifier,
                                  ))
                          )
                        }
                      }
                      _details.update { it.copy(peers = subscribeSession.value.second) }
                    }
                    is Message.SendVersions -> {
                      repository.insert(msg.vectorClock, msg.versions)
                      subscribeSession.update {
                        if (it.second[peerHandle]?.state == msg.vectorClock) {
                          it
                        } else {
                          it.copy(
                            second =
                              it.second +
                                (peerHandle to
                                  PeerInfo(
                                    msg.vectorClock,
                                    HeartbeatState.new(),
                                    msg.clientIdentifier,
                                  ))
                          )
                        }
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
                          msg.vectorClock.contains(vc.first.state) || msg.msgIdentifier == vc.second
                        ) {
                          it.copy(third = MessageState.MessageReceived)
                        } else {
                          it
                        }
                      }
                    }
                    is Message.Heartbeat ->
                      subscribeSession.update {
                        val v = it.second[peerHandle]
                        if (v == null) {
                          Napier.i("handle from heartbeat not yet found")
                          return@update it
                        }
                        val vv = v.copy(heartbeatState = v.heartbeatState.receivedHeartbeat(msg.id))
                        it.copy(second = it.second + (peerHandle to vv))
                      }
                    else -> {}
                  }
                }

                override fun onMessageSendFailed(messageId: Int) {
                  do {
                    val ss = subscribeSession.value
                    val f =
                      ss.second
                        .filter { it.value.heartbeatState.ongoingHeartbeat?.first == messageId }
                        .entries
                        .firstOrNull()
                    val res =
                      if (f == null) {
                        true
                      } else {
                        val sn =
                          ss.copy(
                            second =
                              ss.second +
                                (f.key to
                                  f.value.copy(
                                    heartbeatState =
                                      f.value.heartbeatState.heartbeatFailed(messageId)
                                  ))
                          )
                        subscribeSession.compareAndSet(ss, sn)
                      }
                  } while (!res)

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
                  do {
                    val ss = subscribeSession.value
                    val f =
                      ss.second
                        .filter { it.value.heartbeatState.ongoingHeartbeat?.first == messageId }
                        .entries
                        .firstOrNull()
                    val res =
                      if (f == null) {
                        true
                      } else {
                        val sn =
                          ss.copy(
                            second =
                              ss.second +
                                (f.key to
                                  f.value.copy(
                                    heartbeatState =
                                      f.value.heartbeatState.heartbeatSucceeded(messageId)
                                  ))
                          )
                        subscribeSession.compareAndSet(ss, sn)
                      }
                  } while (!res)

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
