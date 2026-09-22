package com.blue.hush

import com.blue.hush.processing.SignalProcessor
import com.choosemuse.libmuse.MuseDataPacketType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalProcessorTest {
    @Test
    fun aggregatesAndSmoothsValidSecond() {
        val processor = SignalProcessor(smoothingFactor = 0.5)
        repeat(4) {
            processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4, 0.6))
            processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.2, 0.4))
            processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.1, 0.3))
            processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0))
        }
        val sample = processor.nextSample(1)
        assertTrue(sample.valid)
        assertTrue(sample.alpha!! in 0.49..0.51)
        assertTrue(sample.stillness!! > 0.99)
    }

    @Test
    fun sensorPacketsRemainUsableWhenMuseReportsPoorSignalQuality() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0))
        processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0))
        assertTrue(processor.nextSample(1).valid)
    }

    @Test
    fun noSensorPacketsProduceExplicitGap() {
        assertFalse(SignalProcessor().nextSample(1).valid)
    }
}
