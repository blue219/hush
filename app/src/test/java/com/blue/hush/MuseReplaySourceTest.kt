package com.blue.hush

import com.blue.hush.replay.MuseReplaySource
import com.blue.hush.session.StateSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuseReplaySourceTest {
    private val completeReplay = (1..MuseReplaySource.DURATION_SECONDS).map { second ->
        StateSample(second, alpha = 0.3, theta = 0.3, beta = 0.3, stillness = 0.9, valid = true)
    }

    @Test fun requiresOneValidFiniteSampleForEverySecond() {
        assertTrue(MuseReplaySource.isUsable(completeReplay))
        assertFalse(MuseReplaySource.isUsable(completeReplay.drop(10) + completeReplay[10]))
        assertFalse(MuseReplaySource.isUsable(completeReplay.toMutableList().apply { this[5] = this[5].copy(valid = false) }))
        assertFalse(MuseReplaySource.isUsable(completeReplay.toMutableList().apply { this[5] = this[5].copy(alpha = Double.NaN) }))
    }
}
