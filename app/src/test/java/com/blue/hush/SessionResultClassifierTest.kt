package com.blue.hush

import com.blue.hush.processing.SessionResultClassifier
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.StateSample
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionResultClassifierTest {
    @Test
    fun reportsInsufficientData() {
        val samples = List(3) { StateSample(it, stillness = 0.8, valid = true) }
        assertEquals(ResultLabel.INSUFFICIENT, SessionResultClassifier.classify(samples, 20))
    }

    @Test
    fun reportsSettlingWhenLaterSamplesAreMoreStable() {
        val samples = List(4) { StateSample(it, stillness = 0.3, valid = true) } +
            List(4) { StateSample(it + 4, stillness = 0.7, valid = true) }
        assertEquals(ResultLabel.SETTLING, SessionResultClassifier.classify(samples, 20))
    }
}
