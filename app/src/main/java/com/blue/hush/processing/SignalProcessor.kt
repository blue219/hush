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
    private var signalGood = true
    private var smoothedAlpha: Double? = null
    private var smoothedTheta: Double? = null
    private var smoothedBeta: Double? = null
    private var smoothedStillness: Double? = null

    fun accept(type: MuseDataPacketType, values: List<Double>) {
        if (values.isEmpty()) return
        when (type) {
            MuseDataPacketType.ALPHA_RELATIVE -> alphaValues += values.average().coerceIn(0.0, 1.0)
            MuseDataPacketType.THETA_RELATIVE -> thetaValues += values.average().coerceIn(0.0, 1.0)
            MuseDataPacketType.BETA_RELATIVE -> betaValues += values.average().coerceIn(0.0, 1.0)
            MuseDataPacketType.ACCELEROMETER -> {
                if (values.size >= 3) {
                    accelerationMagnitudes += sqrt(
                        values[0] * values[0] + values[1] * values[1] + values[2] * values[2],
                    )
                }
            }
            MuseDataPacketType.IS_GOOD -> signalGood = values.average() >= 0.5
            else -> Unit
        }
    }

    fun nextSample(elapsedSeconds: Int): StateSample {
        val rawAlpha = alphaValues.averageOrNull()
        val rawTheta = thetaValues.averageOrNull()
        val rawBeta = betaValues.averageOrNull()
        val rawStillness = accelerationMagnitudes.averageOrNull()?.let { magnitude ->
            // Muse accelerometer values are expressed around 1 g while still.
            (1.0 - abs(magnitude - 1.0) / 0.5).coerceIn(0.0, 1.0)
        }
        val valid = signalGood && rawAlpha != null && rawTheta != null && rawBeta != null && rawStillness != null

        val sample = if (valid) {
            smoothedAlpha = smooth(smoothedAlpha, rawAlpha)
            smoothedTheta = smooth(smoothedTheta, rawTheta)
            smoothedBeta = smooth(smoothedBeta, rawBeta)
            smoothedStillness = smooth(smoothedStillness, rawStillness)
            StateSample(
                elapsedSeconds = elapsedSeconds,
                alpha = smoothedAlpha,
                theta = smoothedTheta,
                beta = smoothedBeta,
                stillness = smoothedStillness,
                valid = true,
            )
        } else {
            // Keep the gap explicit. The UI must not turn a missing packet into a state.
            StateSample(elapsedSeconds = elapsedSeconds)
        }
        alphaValues.clear()
        thetaValues.clear()
        betaValues.clear()
        accelerationMagnitudes.clear()
        signalGood = true
        return sample
    }

    private fun smooth(previous: Double?, current: Double): Double =
        previous?.let { it + smoothingFactor * (current - it) } ?: current

    private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()
}
