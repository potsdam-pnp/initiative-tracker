package io.github.potsdam_pnp.initiative_tracker

actual fun highestOneBit(value: Int): Int {
  var result = 0
  var high = 1
  while (value > high) {
    high = high shl 1
    result += 1
  }
  return result
}
