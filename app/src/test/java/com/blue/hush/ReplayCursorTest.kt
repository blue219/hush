package com.blue.hush

import com.blue.hush.replay.ReplayCursor
import com.blue.hush.session.StateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReplayCursorTest {
    @Test
    fun returnsSamplesAtClampedProgress() {
        val cursor = ReplayCursor(listOf(StateSample(0), StateSample(1), StateSample(2)))
        assertEquals(0, cursor.sampleAt(-1f)?.elapsedSeconds)
        assertEquals(1, cursor.sampleAt(0.5f)?.elapsedSeconds)
        assertEquals(2, cursor.sampleAt(2f)?.elapsedSeconds)
    }

    @Test
    fun preservesGapDuringReplay() {
        val sample = ReplayCursor(listOf(StateSample(0), StateSample(1))).sampleAt(1f)
        assertFalse(sample?.valid == true)
    }
}
