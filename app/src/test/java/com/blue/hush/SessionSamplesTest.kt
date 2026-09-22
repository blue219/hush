package com.blue.hush

import com.blue.hush.session.SessionSamples
import com.blue.hush.session.StateSample
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSamplesTest {
    @Test fun gapsKeepTheLastValidVisualWithoutIncreasingTheValidCount() {
        val samples = SessionSamples()
        val first = StateSample(elapsedSeconds = 1)
        val valid = StateSample(elapsedSeconds = 2, alpha = 0.4, valid = true)
        val gap = StateSample(elapsedSeconds = 3)

        assertEquals(listOf(first), samples.record(first))
        assertEquals(listOf(valid), samples.record(valid))
        assertEquals(listOf(gap), samples.record(gap))
        assertEquals(valid, samples.visualSample)
        assertEquals(3, samples.count)
        assertEquals(1, samples.validCount)
        assertEquals(listOf(first, valid, gap), samples.all)

        samples.clear()
        assertEquals(0, samples.count)
        assertEquals(0, samples.validCount)
        assertEquals(listOf(first, StateSample(2), gap), samples.record(gap))
        assertEquals(gap, samples.visualSample)
        assertEquals(3, samples.count)
        assertEquals(0, samples.validCount)
    }

    @Test fun delayedTickPersistsExplicitInvalidSeconds() {
        val samples = SessionSamples()
        val current = StateSample(elapsedSeconds = 4, valid = true)

        assertEquals(listOf(StateSample(1), StateSample(2), StateSample(3), current), samples.record(current))
        assertEquals(4, samples.lastSecond)
        assertEquals(1, samples.validCount)
        assertEquals(current, samples.visualSample)
    }
}
