package com.openpedal.ble

data class HeartRateData(
    val bpm: Int? = null,
)

/** Parses the Bluetooth SIG Heart Rate Measurement characteristic. */
object HeartRateParser {
    private const val FLAG_UINT16_FORMAT = 1 shl 0

    fun parse(bytes: ByteArray): HeartRateData? {
        if (bytes.isEmpty()) return null

        val flags = bytes[0].toInt() and 0xff
        val bpm = if (flags and FLAG_UINT16_FORMAT == 0) {
            if (bytes.size < 2) return null
            bytes[1].toInt() and 0xff
        } else {
            if (bytes.size < 3) return null
            (bytes[1].toInt() and 0xff) or ((bytes[2].toInt() and 0xff) shl 8)
        }

        return HeartRateData(bpm = bpm)
    }
}
