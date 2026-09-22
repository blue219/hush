package com.blue.hush.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.blue.hush.session.MusicTrack
import kotlin.math.PI
import kotlin.math.sin

/** Generates two simple original ambient beds locally, so sessions need no network or bundled audio file. */
class AmbientAudioEngine {
    private val lock = Any()
    private var audioTrack: AudioTrack? = null
    private var worker: Thread? = null
    private var running = false
    private var paused = false
    private var selectedTrack = MusicTrack.MIST
    private var volume = 0.7f

    fun play(track: MusicTrack) {
        synchronized(lock) {
            stopLocked()
            selectedTrack = track
            running = true
            paused = false
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(BUFFER_SAMPLES * 2)
            val trackOutput = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            trackOutput.setVolume(volume)
            trackOutput.play()
            audioTrack = trackOutput
            worker = Thread({ renderLoop(trackOutput, track) }, "hush-ambient-audio").also { it.start() }
        }
    }

    fun pause() {
        synchronized(lock) {
            paused = true
            audioTrack?.pause()
        }
    }

    fun resume() {
        synchronized(lock) {
            if (!running) return
            paused = false
            audioTrack?.play()
        }
    }

    fun setVolume(value: Float) {
        synchronized(lock) {
            volume = value.coerceIn(0f, 1f)
            audioTrack?.setVolume(volume)
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun renderLoop(output: AudioTrack, track: MusicTrack) {
        val buffer = ShortArray(BUFFER_SAMPLES)
        var sampleIndex = 0L
        while (synchronized(lock) { running && audioTrack === output }) {
            if (synchronized(lock) { paused }) {
                Thread.sleep(50L)
                continue
            }
            val currentVolume = synchronized(lock) { volume }
            for (i in buffer.indices) {
                val seconds = sampleIndex.toDouble() / SAMPLE_RATE
                val value = when (track) {
                    MusicTrack.MIST -> {
                        val carrier = sin(2.0 * PI * 174.0 * seconds) * 0.24
                        val overtone = sin(2.0 * PI * 261.63 * seconds) * 0.08
                        val breath = 0.78 + 0.22 * sin(2.0 * PI * 0.08 * seconds)
                        (carrier + overtone) * breath
                    }
                    MusicTrack.TIDE -> {
                        val carrier = sin(2.0 * PI * 130.81 * seconds) * 0.22
                        val overtone = sin(2.0 * PI * 196.0 * seconds) * 0.1
                        val tide = 0.7 + 0.3 * sin(2.0 * PI * 0.045 * seconds)
                        (carrier + overtone) * tide
                    }
                }
                buffer[i] = (value * currentVolume * Short.MAX_VALUE).toInt().toShort()
                sampleIndex++
            }
            if (output.write(buffer, 0, buffer.size) < 0) break
        }
    }

    private fun stopLocked() {
        running = false
        paused = false
        audioTrack?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        audioTrack = null
        worker = null
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BUFFER_SAMPLES = 4_096
    }
}
