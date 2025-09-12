package io.github.potsdam_pnp.initiative_tracker.crdt

import kotlin.random.Random

class ClientIdentifier private constructor(private val data: Int) {
  @OptIn(ExperimentalStdlibApi::class)
  fun pretty(): String {
    return data.toHexString()
  }

  fun encodeToProto(): Int = data

  @OptIn(ExperimentalStdlibApi::class)
  fun enocdeInUrl(): String {
    return data.toHexString()
  }

  override fun equals(other: Any?): Boolean {
    return (other as? ClientIdentifier)?.data?.let { data == it } ?: false
  }

  override fun hashCode(): Int {
    return data.hashCode()
  }

  override fun toString(): String {
    return "ClientIdentifier(${pretty()})"
  }

  companion object {
    val totalOrder = compareBy<ClientIdentifier> { it.data }

    fun new(): ClientIdentifier {
      return ClientIdentifier(Random.nextInt())
    }

    fun decodeFromProto(proto: Int): ClientIdentifier = ClientIdentifier(proto)

    @OptIn(ExperimentalStdlibApi::class)
    fun decodeFromUrl(content: String): ClientIdentifier? {
      return try {
        ClientIdentifier(content.hexToInt())
      } catch (_: IllegalArgumentException) {
        null
      }
    }
  }
}
