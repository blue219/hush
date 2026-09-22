package com.blue.hush.processing

import com.blue.hush.session.StateSample
import com.choosemuse.libmuse.MuseDataPacketType
import kotlin.math.abs
import kotlin.math.sqrt

/** Converts noisy Muse packets into one smoothed, relative state sample per second. */
class SignalProcessor(
    private val smoothingFactor: Double = 0.2,
) {
    private val alphaValues = mutableListOf<Double>()
    private val thetaValues = mutableListOf<Double>()
    private val betaValues = mutableListOf<Double>()
    private val accelerationMagnitudes = mutableListOf<Double>()
    private var receivedSensorData = false
    private var smoothedAlpha: Double? = null
    private var smoothedTheta: Double? = null
    private var smoothedBeta: Double? = null
    private var smoothedStillness: Double? = null

    @Synchronized
    fun accept(type: MuseDataPacketType, values: List<Double>) {
        if (values.isEmpty()) return
        when (type) {
            MuseDataPacketType.ALPHA_RELATIVE -> {
                receivedSensorData = true
                values.averageFiniteOrNull()?.let { alphaValues += it.coerceIn(0.0, 1.0) }
            }
            MuseDataPacketType.THETA_RELATIVE -> {
                receivedSensorData = true
                values.averageFiniteOrNull()?.let { thetaValues += it.coerceIn(0.0, 1.0) }
            }
            MuseDataPacketType.BETA_RELATIVE -> {
                receivedSensorData = true
                values.averageFiniteOrNull()?.let { betaValues += it.coerceIn(0.0, 1.0) }
            }
            MuseDataPacketType.ACCELEROMETER -> {
                if (values.size >= 3 && values.take(3).all { it.isFinite() }) {
                    receivedSensorData = true
                    accelerationMagnitudes += sqrt(
                        values[0] * values[0] + values[1] * values[1] + values[2] * values[2],
                    )
                }
            }
            MuseDataPacketType.EEG,
            MuseDataPacketType.GYRO,
            MuseDataPacketType.PPG,
            MuseDataPacketType.ALPHA_ABSOLUTE,
            MuseDataPacketType.BETA_ABSOLUTE,
            MuseDataPacketType.DELTA_ABSOLUTE,
            MuseDataPacketType.THETA_ABSOLUTE,
            MuseDataPacketType.GAMMA_ABSOLUTE,
            MuseDataPacketType.ALPHA_SCORE,
            MuseDataPacketType.BETA_SCORE,
            MuseDataPacketType.DELTA_SCORE,
            MuseDataPacketType.THETA_SCORE,
            MuseDataPacketType.GAMMA_SCORE -> receivedSensorData = true
            else -> Unit
        }
    }

    @Synchronized
    fun nextSample(elapsedSeconds: Int): StateSample {
        val rawAlpha = alphaValues.averageOrNull()
        val rawTheta = thetaValues.averageOrNull()
        val rawBeta = betaValues.averageOrNull()
        val rawStillness = accelerationMagnitudes.averageOrNull()?.let { magnitude ->
            // Muse accelerometer values are expressed around 1 g while still.
            (1.0 - abs(magnitude - 1.0) / 0.5).coerceIn(0.0, 1.0)
        }
        val valid = receivedSensorData

        val sample = if (valid) {
            // Muse reports NaN for derived EEG bands during contact gaps. Keep
            // the visual stream alive from the fields that are available and
            // carry the last finite value for a temporarily missing field.
            smoothedAlpha = smooth(smoothedAlpha, rawAlpha ?: DEFAULT_BAND)
            smoothedTheta = smooth(smoothedTheta, rawTheta ?: DEFAULT_BAND)
            smoothedBeta = smooth(smoothedBeta, rawBeta ?: DEFAULT_BAND)
            smoothedStillness = smooth(smoothedStillness, rawStillness ?: DEFAULT_STILLNESS)
            StateSample(
                elapsedSeconds = elapsedSeconds,
                alpha = smoothedAlpha,
                theta = smoothedTheta,
                beta = smoothedBeta,
                stillness = smoothedStillness,
                valid = true,
            )
        } else {
            // Keep the gap explicit only when no sensor callback arrived.
            StateSample(elapsedSeconds = elapsedSeconds)
        }
        alphaValues.clear()
        thetaValues.clear()
        betaValues.clear()
        accelerationMagnitudes.clear()
        receivedSensorData = false
        return sample
    }

    private fun smooth(previous: Double?, current: Double): Double =
        previous?.let { it + smoothingFactor * (current - it) } ?: current

    private fun List<Double>.averageOrNull(): Double? = averageFiniteOrNull()

    private fun List<Double>.averageFiniteOrNull(): Double? =
        filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.average()

    private companion object {
        const val DEFAULT_BAND = 1.0 / 3.0
        const val DEFAULT_STILLNESS = 0.5
    }
}
