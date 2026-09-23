package com.openpedal.ble

data class BikeData(
    val speedKmh: Double? = null,
    val averageSpeedKmh: Double? = null,
    val cadenceRpm: Double? = null,
    val averageCadenceRpm: Double? = null,
    val distanceMeters: Int? = null,
    val resistance: Double? = null,
    val powerWatts: Int? = null,
    val averagePowerWatts: Int? = null,
    val calories: Int? = null,
    val energyPerHourKcal: Int? = null,
    val energyPerMinuteKcal: Int? = null,
    val heartRate: Int? = null,
    val elapsedSeconds: Int? = null,
    val remainingSeconds: Int? = null,
) {
    /** Keeps the latest value for fields omitted from a subsequent FTMS packet. */
    fun merge(update: BikeData): BikeData = copy(
        speedKmh = update.speedKmh ?: speedKmh,
        averageSpeedKmh = update.averageSpeedKmh ?: averageSpeedKmh,
        cadenceRpm = update.cadenceRpm ?: cadenceRpm,
        averageCadenceRpm = update.averageCadenceRpm ?: averageCadenceRpm,
        distanceMeters = update.distanceMeters ?: distanceMeters,
        resistance = update.resistance ?: resistance,
        powerWatts = update.powerWatts ?: powerWatts,
        averagePowerWatts = update.averagePowerWatts ?: averagePowerWatts,
        calories = update.calories ?: calories,
        energyPerHourKcal = update.energyPerHourKcal ?: energyPerHourKcal,
        energyPerMinuteKcal = update.energyPerMinuteKcal ?: energyPerMinuteKcal,
        heartRate = update.heartRate ?: heartRate,
        elapsedSeconds = update.elapsedSeconds ?: elapsedSeconds,
        remainingSeconds = update.remainingSeconds ?: remainingSeconds,
    )
}

/** Parses the Bluetooth SIG Fitness Machine Service Indoor Bike Data characteristic. */
object IndoorBikeDataParser {
    private const val FLAG_MORE_DATA = 1 shl 0
    private const val FLAG_AVERAGE_SPEED = 1 shl 1
    private const val FLAG_INSTANTANEOUS_CADENCE = 1 shl 2
    private const val FLAG_AVERAGE_CADENCE = 1 shl 3
    private const val FLAG_TOTAL_DISTANCE = 1 shl 4
    private const val FLAG_RESISTANCE = 1 shl 5
    private const val FLAG_INSTANTANEOUS_POWER = 1 shl 6
    private const val FLAG_AVERAGE_POWER = 1 shl 7
    private const val FLAG_EXPENDED_ENERGY = 1 shl 8
    private const val FLAG_HEART_RATE = 1 shl 9
    private const val FLAG_METABOLIC_EQUIVALENT = 1 shl 10
    private const val FLAG_ELAPSED_TIME = 1 shl 11
    private const val FLAG_REMAINING_TIME = 1 shl 12

    fun parse(bytes: ByteArray): BikeData? {
        return try {
            val packet = PacketReader(bytes)
            val flags = packet.readUnsignedShort()

            val speedKmh = if (flags and FLAG_MORE_DATA == 0) {
                packet.readUnsignedShort() / 100.0
            } else {
                null
            }
            val averageSpeedKmh = if (flags and FLAG_AVERAGE_SPEED != 0) {
                packet.readUnsignedShort() / 100.0
            } else {
                null
            }
            val cadenceRpm = if (flags and FLAG_INSTANTANEOUS_CADENCE != 0) {
                packet.readUnsignedShort() * 0.5
            } else {
                null
            }
            val averageCadenceRpm = if (flags and FLAG_AVERAGE_CADENCE != 0) {
                packet.readUnsignedShort() * 0.5
            } else {
                null
            }
            val distanceMeters = if (flags and FLAG_TOTAL_DISTANCE != 0) {
                packet.readUnsignedInt24()
            } else {
                null
            }
            val resistance = if (flags and FLAG_RESISTANCE != 0) {
                packet.readSignedShort() / 10.0
            } else {
                null
            }
            val powerWatts = if (flags and FLAG_INSTANTANEOUS_POWER != 0) {
                packet.readSignedShort()
            } else {
                null
            }
            val averagePowerWatts = if (flags and FLAG_AVERAGE_POWER != 0) {
                packet.readSignedShort()
            } else {
                null
            }

            var calories: Int? = null
            var energyPerHourKcal: Int? = null
            var energyPerMinuteKcal: Int? = null
            if (flags and FLAG_EXPENDED_ENERGY != 0) {
                calories = packet.readUnsignedShort()
                energyPerHourKcal = packet.readUnsignedShort()
                energyPerMinuteKcal = packet.readUnsignedByte()
            }

            val heartRate = if (flags and FLAG_HEART_RATE != 0) {
                packet.readUnsignedByte()
            } else {
                null
            }

            if (flags and FLAG_METABOLIC_EQUIVALENT != 0) {
                packet.readUnsignedByte()
            }

            val elapsedSeconds = if (flags and FLAG_ELAPSED_TIME != 0) {
                packet.readUnsignedShort()
            } else {
                null
            }
            val remainingSeconds = if (flags and FLAG_REMAINING_TIME != 0) {
                packet.readUnsignedShort()
            } else {
                null
            }

            BikeData(
                speedKmh = speedKmh,
                averageSpeedKmh = averageSpeedKmh,
                cadenceRpm = cadenceRpm,
                averageCadenceRpm = averageCadenceRpm,
                distanceMeters = distanceMeters,
                resistance = resistance,
                powerWatts = powerWatts,
                averagePowerWatts = averagePowerWatts,
                calories = calories,
                energyPerHourKcal = energyPerHourKcal,
                energyPerMinuteKcal = energyPerMinuteKcal,
                heartRate = heartRate,
                elapsedSeconds = elapsedSeconds,
                remainingSeconds = remainingSeconds,
            )
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    private class PacketReader(private val bytes: ByteArray) {
        private var position = 0

        fun readUnsignedByte(): Int {
            requireRemaining(1)
            return bytes[position++].toInt() and 0xff
        }

        fun readUnsignedShort(): Int {
            val low = readUnsignedByte()
            val high = readUnsignedByte()
            return low or (high shl 8)
        }

        fun readSignedShort(): Int {
            val value = readUnsignedShort()
            return if (value and 0x8000 != 0) value - 0x10000 else value
        }

        fun readUnsignedInt24(): Int {
            val low = readUnsignedByte()
            val middle = readUnsignedByte()
            val high = readUnsignedByte()
            return low or (middle shl 8) or (high shl 16)
        }

        private fun requireRemaining(count: Int) {
            if (position + count > bytes.size) {
                throw IndexOutOfBoundsException("Indoor Bike Data packet is truncated")
            }
        }
    }
}
