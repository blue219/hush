package com.blue.hush

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.BUNDLED_SIMULATION_SESSION_ID
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.StateSample
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledSimulationHistoryTest {
    @Test fun importIsIdempotentAndOlderThanExistingSessions() {
        // Device-protected storage is separate from the app's ordinary session database.
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        context.getDatabasePath("hush.db").parentFile?.mkdirs()
        try {
            HushDatabase(context).use { database ->
                val existingStart = 1_700_000_000_000L
                val existingId = database.insertSession(existingStart, 600, MusicTrack.TIDE)
                database.finishSession(existingId, existingStart + 600_000L, 600, ResultLabel.STEADY)
                val replay = (1..600).map { second ->
                    StateSample(second, alpha = 0.3, theta = 0.3, beta = 0.3, stillness = 0.9, valid = true)
                }

                database.ensureBundledSimulation(replay)
                database.ensureBundledSimulation(replay)

                val history = database.loadSummaries()
                assertEquals(2, history.size)
                assertEquals(BUNDLED_SIMULATION_SESSION_ID, history.last().id)
                assertTrue(history.last().startedAt < existingStart)
                assertEquals(600, database.loadSamples(BUNDLED_SIMULATION_SESSION_ID).size)
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }
}
