package com.blue.hush.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.blue.hush.R
import com.blue.hush.audio.AmbientAudioEngine
import com.blue.hush.muse.MuseDeviceManager
import com.blue.hush.processing.SessionResultClassifier
import com.blue.hush.processing.SignalProcessor
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.SessionClock
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionRuntime
import com.blue.hush.session.SessionState
import com.blue.hush.session.StateSample
import com.blue.hush.storage.HushDatabase
import com.choosemuse.libmuse.ConnectionState
import com.choosemuse.libmuse.MuseDataPacketType

class MeditationService : Service(), MuseDeviceManager.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val clock = SessionClock()
    private val processor = SignalProcessor()
    private val samples = mutableListOf<StateSample>()
    private lateinit var database: HushDatabase
    private var museManager: MuseDeviceManager? = null
    private var audioEngine: AmbientAudioEngine? = null
    private var desiredMacAddress: String = ""
    private var desiredDeviceName: String = "Muse 2"
    private var sessionId: Long? = null
    private var plannedSeconds = 20 * 60
    private var selectedTrack = MusicTrack.MIST
    private var currentVolume = 0.7f
    private var lastSavedSecond = 0
    private var isConnecting = false
    private var currentState = SessionState()

    private val tick = object : Runnable {
        override fun run() {
            if (currentState.phase == SessionPhase.RUNNING) {
                val elapsedSeconds = (clock.elapsedMillis(SystemClock.elapsedRealtime()) / 1_000L).toInt()
                if (elapsedSeconds > lastSavedSecond) {
                    val sample = processor.nextSample(elapsedSeconds)
                    sessionId?.let { database.insertSample(it, sample) }
                    samples += sample
                    lastSavedSecond = elapsedSeconds
                    publish(
                        currentState.copy(
                            elapsedSeconds = elapsedSeconds,
                            dataGap = !currentState.connected || !sample.valid,
                            sampleCount = samples.size,
                            validSampleCount = samples.count { it.valid },
                            latestSample = sample,
                            message = if (sample.valid) null else "Not enough valid sensor data for this second",
                        ),
                    )
                    if (elapsedSeconds >= plannedSeconds) {
                        finishSession()
                        return
                    }
                } else {
                    publish(
                        currentState.copy(
                            elapsedSeconds = elapsedSeconds,
                            dataGap = !currentState.connected,
                        ),
                    )
                }
                handler.postDelayed(this, 250L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = HushDatabase(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_FINISH -> finishSession()
            ACTION_SET_VOLUME -> {
                currentVolume = intent.getFloatExtra(EXTRA_VOLUME, currentVolume).coerceIn(0f, 1f)
                audioEngine?.setVolume(currentVolume)
                publish(currentState.copy(volume = currentVolume))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        audioEngine?.stop()
        audioEngine = null
        museManager?.close()
        museManager = null
        database.close()
        super.onDestroy()
    }

    override fun onDevicesChanged(devices: List<MuseDeviceManager.MuseDevice>) {
        if (currentState.phase != SessionPhase.RUNNING && currentState.phase != SessionPhase.PAUSED) return
        if (isConnecting || currentState.connected) return
        devices.firstOrNull { it.macAddress == desiredMacAddress }?.let {
            isConnecting = true
            museManager?.connect(it)
        }
    }

    override fun onConnectionStateChanged(
        device: MuseDeviceManager.MuseDevice,
        previous: ConnectionState,
        current: ConnectionState,
    ) {
        if (currentState.phase == SessionPhase.FINISHED || sessionId == null) return
        when (current) {
            ConnectionState.CONNECTED -> {
                isConnecting = false
                museManager?.stopScanning()
                publish(currentState.copy(connected = true, deviceName = device.name, dataGap = false, message = null))
            }
            ConnectionState.DISCONNECTED -> {
                isConnecting = false
                publish(
                    currentState.copy(
                        connected = false,
                        dataGap = true,
                        message = "Muse 2 disconnected; timer continues while reconnecting",
                    ),
                )
                if (currentState.phase == SessionPhase.RUNNING || currentState.phase == SessionPhase.PAUSED) {
                    runCatching { museManager?.startScanning() }
                }
            }
            else -> publish(currentState.copy(message = "Connecting to Muse 2…"))
        }
    }

    override fun onDataPacket(packet: MuseDeviceManager.MusePacket) {
        processor.accept(packet.type, packet.values)
    }

    override fun onArtifact(packet: MuseDeviceManager.MuseArtifact) = Unit

    private fun startSession(intent: Intent) {
        if (sessionId != null) return
        desiredMacAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS).orEmpty()
        desiredDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty().ifBlank { "Muse 2" }
        plannedSeconds = intent.getIntExtra(EXTRA_PLANNED_SECONDS, 20 * 60).coerceIn(10 * 60, 30 * 60)
        selectedTrack = intent.getStringExtra(EXTRA_TRACK)?.let { runCatching { MusicTrack.valueOf(it) }.getOrNull() }
            ?: MusicTrack.MIST
        currentVolume = intent.getFloatExtra(EXTRA_VOLUME, 0.7f).coerceIn(0f, 1f)
        sessionId = database.insertSession(System.currentTimeMillis(), plannedSeconds, selectedTrack)
        samples.clear()
        lastSavedSecond = 0
        clock.start(SystemClock.elapsedRealtime())
        audioEngine = AmbientAudioEngine().also {
            it.setVolume(currentVolume)
            it.play(selectedTrack)
        }
        museManager = MuseDeviceManager(applicationContext, this)
        publish(
            SessionState(
                phase = SessionPhase.RUNNING,
                sessionId = sessionId,
                plannedSeconds = plannedSeconds,
                connected = false,
                deviceName = desiredDeviceName,
                track = selectedTrack,
                volume = currentVolume,
                message = "Connecting to Muse 2…",
            ),
        )
        runCatching { museManager?.startScanning() }.onFailure {
            publish(currentState.copy(message = "Could not start Muse scanning. Check Bluetooth permission."))
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun pauseSession() {
        if (currentState.phase != SessionPhase.RUNNING) return
        clock.pause(SystemClock.elapsedRealtime())
        audioEngine?.pause()
        publish(currentState.copy(phase = SessionPhase.PAUSED, message = "Paused; timing and collection are temporarily stopped"))
        updateNotification()
    }

    private fun resumeSession() {
        if (currentState.phase != SessionPhase.PAUSED) return
        clock.resume(SystemClock.elapsedRealtime())
        audioEngine?.resume()
        publish(currentState.copy(phase = SessionPhase.RUNNING, message = null))
        handler.post(tick)
        updateNotification()
    }

    private fun finishSession() {
        val id = sessionId ?: return
        val elapsedSeconds = (clock.elapsedMillis(SystemClock.elapsedRealtime()) / 1_000L).toInt()
        val result = SessionResultClassifier.classify(samples, plannedSeconds)
        database.finishSession(id, System.currentTimeMillis(), elapsedSeconds, result)
        clock.pause(SystemClock.elapsedRealtime())
        publish(
            currentState.copy(
                phase = SessionPhase.FINISHED,
                elapsedSeconds = elapsedSeconds,
                dataGap = false,
                result = result,
                message = result.description,
            ),
        )
        audioEngine?.stop()
        audioEngine = null
        museManager?.close()
        museManager = null
        sessionId = null
        handler.removeCallbacksAndMessages(null)
        stopSelf()
    }

    private fun publish(state: SessionState) {
        currentState = state
        SessionRuntime.publish(state)
        updateNotification()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val text = when (currentState.phase) {
            SessionPhase.PAUSED -> "Meditation paused"
            SessionPhase.RUNNING -> "Meditation in progress · ${formatElapsed(currentState.elapsedSeconds)}"
            else -> "Ready to meditate"
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Hush")
            .setContentText(text)
            .setOngoing(currentState.phase == SessionPhase.RUNNING || currentState.phase == SessionPhase.PAUSED)
            .build()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Meditation session", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun formatElapsed(seconds: Int): String =
        "%02d:%02d".format(seconds / 60, seconds % 60)

    companion object {
        private const val CHANNEL_ID = "meditation_session"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.blue.hush.action.START"
        const val ACTION_PAUSE = "com.blue.hush.action.PAUSE"
        const val ACTION_RESUME = "com.blue.hush.action.RESUME"
        const val ACTION_FINISH = "com.blue.hush.action.FINISH"
        const val ACTION_SET_VOLUME = "com.blue.hush.action.SET_VOLUME"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_PLANNED_SECONDS = "planned_seconds"
        const val EXTRA_TRACK = "track"
        const val EXTRA_VOLUME = "volume"

        fun start(context: Context, deviceAddress: String, deviceName: String, plannedSeconds: Int, track: MusicTrack, volume: Float) {
            val intent = Intent(context, MeditationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_PLANNED_SECONDS, plannedSeconds)
                putExtra(EXTRA_TRACK, track.name)
                putExtra(EXTRA_VOLUME, volume)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun command(context: Context, action: String) {
            context.startService(Intent(context, MeditationService::class.java).setAction(action))
        }

        fun setVolume(context: Context, volume: Float) {
            context.startService(
                Intent(context, MeditationService::class.java)
                    .setAction(ACTION_SET_VOLUME)
                    .putExtra(EXTRA_VOLUME, volume),
            )
        }
    }
}
