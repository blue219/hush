package com.blue.hush.replay

import com.blue.hush.session.StateSample

class ReplayCursor(private val samples: List<StateSample>) {
    val size: Int get() = samples.size

    fun sampleAt(progress: Float): StateSample? {
        if (samples.isEmpty()) return null
        val index = (progress.coerceIn(0f, 1f) * (samples.lastIndex)).toInt()
        return samples[index]
    }
}
