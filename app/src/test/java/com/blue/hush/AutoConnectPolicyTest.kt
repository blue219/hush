package com.blue.hush

import com.blue.hush.muse.AutoConnectPolicy
import org.junit.Assert.*
import org.junit.Test

class AutoConnectPolicyTest {
    @Test fun discoveryRequiresEveryLifecycleAndDeviceCondition() {
        assertTrue(AutoConnectPolicy.eligible(true, true, true, true, false, false))
        for (index in 0..5) {
            val flags = mutableListOf(true, true, true, true, false, false)
            flags[index] = !flags[index]
            assertFalse(AutoConnectPolicy.eligible(flags[0], flags[1], flags[2], flags[3], flags[4], flags[5]))
        }
    }
    @Test fun remembersOnlyTheChosenDeviceAndDoesNotSelectAnotherPersonsMuse() {
        assertEquals("mine", AutoConnectPolicy.choose(listOf("other", "mine"), "mine"))
        assertNull(AutoConnectPolicy.choose(listOf("other"), "mine"))
        assertNull(AutoConnectPolicy.choose(listOf("one", "two"), null))
        assertEquals("one", AutoConnectPolicy.choose(listOf("one", "one"), null))
        assertNull(AutoConnectPolicy.choose(emptyList(), null))
    }
    @Test fun failuresBackOffWithoutOverflow() {
        assertEquals(2000L, AutoConnectPolicy.retryDelay(0))
        assertEquals(4000L, AutoConnectPolicy.retryDelay(1))
        assertEquals(30000L, AutoConnectPolicy.retryDelay(20))
        assertEquals(30000L, AutoConnectPolicy.retryDelay(Int.MAX_VALUE))
    }
}
