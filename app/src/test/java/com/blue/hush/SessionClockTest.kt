package com.blue.hush

import com.blue.hush.session.SessionClock
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionClockTest {
    @Test
    fun pauseExcludesPausedTime() {
        val clock = SessionClock()
        clock.start(1_000L)
        assertEquals(4_000L, clock.elapsedMillis(5_000L))
        clock.pause(5_000L)
        assertEquals(4_000L, clock.elapsedMillis(20_000L))
        clock.resume(20_000L)
        assertEquals(7_000L, clock.elapsedMillis(23_000L))
    }
}
