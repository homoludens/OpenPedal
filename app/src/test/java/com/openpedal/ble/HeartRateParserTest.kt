package com.openpedal.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {
    @Test
    fun parsesUint8HeartRate() {
        val data = HeartRateParser.parse(byteArrayOf(0x00, 105))

        assertEquals(HeartRateData(bpm = 105), data)
    }

    @Test
    fun parsesUint16HeartRate() {
        val data = HeartRateParser.parse(byteArrayOf(0x01, 0x2C, 0x01))

        assertEquals(HeartRateData(bpm = 300), data)
    }

    @Test
    fun ignoresOtherMeasurementFields() {
        val data = HeartRateParser.parse(
            byteArrayOf(0x18, 72, 0x34, 0x12, 0xAA.toByte(), 0xBB.toByte()),
        )

        assertEquals(HeartRateData(bpm = 72), data)
    }

    @Test
    fun returnsNullForTruncatedMeasurement() {
        assertNull(HeartRateParser.parse(byteArrayOf(0x01, 0x2C)))
        assertNull(HeartRateParser.parse(byteArrayOf()))
    }
}
