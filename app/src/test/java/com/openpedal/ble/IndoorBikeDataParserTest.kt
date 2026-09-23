package com.openpedal.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class IndoorBikeDataParserTest {
    @Test
    fun parsesAllIndoorBikeDataFieldsInSpecificationOrder() {
        val flags = 0x0BFE // All fields below, including instantaneous speed, are present.
        val packet = byteArrayOf(
            0xFE.toByte(), 0x0B,
            0x63, 0x0E, // Instantaneous speed: 36.83 km/h
            0xB8.toByte(), 0x0B, // Average speed: 30.00 km/h
            0xC2.toByte(), 0x00, // Instantaneous cadence: 97 rpm
            0xA0.toByte(), 0x00, // Average cadence: 80 rpm
            0x51, 0x07, 0x00, // Distance: 1873 m
            0x5E, 0x01, // Resistance: 35.0
            0x7F, 0x00, // Instantaneous power: 127 W
            0x50, 0x00, // Average power: 80 W
            0x19, 0x00, // Total energy: 25 kcal
            0x90.toByte(), 0x01, // Energy per hour: 400 kcal/h
            0x06, // Energy per minute: 6 kcal/min
            0x8C.toByte(), // Heart rate: 140 bpm
            0xEC.toByte(), 0x00, // Elapsed time: 236 s
        )

        assertEquals(0x0BFE, flags)
        val data = IndoorBikeDataParser.parse(packet)

        assertNotNull(data)
        assertEquals(36.83, data?.speedKmh!!, 0.001)
        assertEquals(30.0, data?.averageSpeedKmh!!, 0.001)
        assertEquals(97.0, data.cadenceRpm!!, 0.001)
        assertEquals(80.0, data.averageCadenceRpm!!, 0.001)
        assertEquals(1873, data.distanceMeters)
        assertEquals(35.0, data.resistance!!, 0.001)
        assertEquals(127, data.powerWatts)
        assertEquals(80, data.averagePowerWatts)
        assertEquals(25, data.calories)
        assertEquals(400, data.energyPerHourKcal)
        assertEquals(6, data.energyPerMinuteKcal)
        assertEquals(140, data.heartRate)
        assertEquals(236, data.elapsedSeconds)
        assertNull(data.remainingSeconds)
    }

    @Test
    fun respectsAbsentFieldsAndParsesSignedValues() {
        val packet = byteArrayOf(
            0x61, 0x00, // More data, resistance and instantaneous power
            0x9C.toByte(), 0xFF.toByte(), // Resistance: -10.0
            0xC7.toByte(), 0xFF.toByte(), // Power: -57 W
        )

        val data = IndoorBikeDataParser.parse(packet)

        assertNotNull(data)
        assertNull(data?.speedKmh)
        assertEquals(-10.0, data?.resistance!!, 0.001)
        assertEquals(-57, data.powerWatts)
        assertNull(data.averagePowerWatts)
        assertNull(data.distanceMeters)
    }

    @Test
    fun returnsNullForTruncatedConditionalField() {
        val packet = byteArrayOf(
            0x04, 0x00, // Instantaneous cadence is present
            0x01, // Only one of the two cadence bytes
        )

        assertNull(IndoorBikeDataParser.parse(packet))
    }

    @Test
    fun mergesValuesFromAlternatingPackets() {
        val firstPacket = BikeData(
            cadenceRpm = 48.0,
            distanceMeters = 212,
            resistance = 5.5,
            powerWatts = 65,
        )
        val secondPacket = BikeData(
            speedKmh = 18.22,
            elapsedSeconds = 7,
        )

        val merged = firstPacket.merge(secondPacket)

        assertEquals(18.22, merged.speedKmh!!, 0.001)
        assertEquals(48.0, merged.cadenceRpm!!, 0.001)
        assertEquals(212, merged.distanceMeters)
        assertEquals(5.5, merged.resistance!!, 0.001)
        assertEquals(65, merged.powerWatts)
        assertEquals(7, merged.elapsedSeconds)
    }
}
