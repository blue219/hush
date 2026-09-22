@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.blue.hush

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blue.hush.audio.AmbientAudioEngine
import com.blue.hush.muse.MuseDeviceManager
import com.blue.hush.replay.ReplayCursor
import com.blue.hush.service.MeditationService
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionRuntime
import com.blue.hush.session.SessionState
import com.blue.hush.session.SessionSummary
import com.blue.hush.session.StateSample
import com.blue.hush.storage.HushDatabase
import com.blue.hush.ui.theme.HushTheme
import com.choosemuse.libmuse.ConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors

private enum class AppTab(val title: String, val icon: String) {
    MEDITATE("Meditate", "◌"),
    HISTORY("History", "◴"),
    MUSIC("Music", "♫"),
}

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val observedDataPacketCount = AtomicInteger()
    private val observedDataPacketType = AtomicReference<String?>(null)
    private val dataStatusUpdateScheduled = AtomicBoolean()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private lateinit var database: HushDatabase
    private var sessionState by mutableStateOf(SessionRuntime.current)
    private var history by mutableStateOf<List<SessionSummary>>(emptyList())
    private var activeTab by mutableStateOf(AppTab.MEDITATE)
    private var selectedDurationSeconds by mutableIntStateOf(20 * 60)
    private var selectedTrack by mutableStateOf(MusicTrack.MIST)
    private var detailSummary by mutableStateOf<SessionSummary?>(null)
    private var detailSamples by mutableStateOf<List<StateSample>>(emptyList())
    private var replayProgress by mutableFloatStateOf(0f)
    private var connectionStateUi by mutableStateOf(ConnectionUiState())
    private var museManager: MuseDeviceManager? = null
    private val previewEngine = AmbientAudioEngine()
    private var previewTrack by mutableStateOf<MusicTrack?>(null)
    private var removeSessionListener: (() -> Unit)? = null

    private val publishDataStatus = Runnable {
        dataStatusUpdateScheduled.set(false)
        connectionStateUi = connectionStateUi.copy(
            dataPacketCount = observedDataPacketCount.get(),
            lastDataPacketType = observedDataPacketType.get(),
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (requiredBluetoothPermissions().all { granted[it] == true || hasPermission(it) }) {
            initializeMuseManager()
        } else {
            connectionStateUi = connectionStateUi.copy(
                hasBluetoothPermission = false,
                errorMessage = "Bluetooth permission is required to scan for Muse 2. Allow it in Settings and try again.",
            )
        }
    }

    private val museListener = object : MuseDeviceManager.Listener {
        override fun onDevicesChanged(devices: List<MuseDeviceManager.MuseDevice>) {
            mainHandler.post {
                connectionStateUi = connectionStateUi.copy(devices = devices, isScanning = true)
            }
        }

        override fun onConnectionStateChanged(
            device: MuseDeviceManager.MuseDevice,
            previous: ConnectionState,
            current: ConnectionState,
        ) {
            mainHandler.post {
                connectionStateUi = connectionStateUi.copy(
                    connectionState = current.name,
                    connectedDeviceAddress = if (current == ConnectionState.DISCONNECTED) null else device.macAddress,
                    isScanning = false,
                    errorMessage = null,
                )
            }
        }

        override fun onDataPacket(packet: MuseDeviceManager.MusePacket) {
            observedDataPacketCount.incrementAndGet()
            observedDataPacketType.set(packet.type.name)
            if (dataStatusUpdateScheduled.compareAndSet(false, true)) {
                mainHandler.postDelayed(publishDataStatus, 500L)
            }
        }

        override fun onArtifact(packet: MuseDeviceManager.MuseArtifact) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = HushDatabase(applicationContext)
        removeSessionListener = SessionRuntime.subscribe { state ->
            mainHandler.post {
                sessionState = state
                if (state.phase == SessionPhase.FINISHED) refreshHistory()
            }
        }
        refreshHistory()
        setContent {
            HushTheme {
                HushApp(
                    sessionState = sessionState,
                    history = history,
                    activeTab = activeTab,
                    selectedDurationSeconds = selectedDurationSeconds,
                    selectedTrack = selectedTrack,
                    detailSummary = detailSummary,
                    detailSamples = detailSamples,
                    replayProgress = replayProgress,
                    connectionState = connectionStateUi,
                    previewTrack = previewTrack,
                    onTabSelected = { activeTab = it },
                    onDurationSelected = { selectedDurationSeconds = it },
                    onTrackSelected = {
                        selectedTrack = it
                        previewTrack = null
                        previewEngine.stop()
                    },
                    onStartScanning = ::startScanning,
                    onConnect = ::connectMuse,
                    onDisconnect = {
                        museManager?.disconnect()
                        connectionStateUi = connectionStateUi.copy(
                            connectionState = ConnectionState.DISCONNECTED.name,
                            connectedDeviceAddress = null,
                        )
                    },
                    onStartSession = ::startSession,
                    onPause = { MeditationService.command(this, MeditationService.ACTION_PAUSE) },
                    onResume = { MeditationService.command(this, MeditationService.ACTION_RESUME) },
                    onFinish = { MeditationService.command(this, MeditationService.ACTION_FINISH) },
                    onStartNewSession = ::resetCompletedSession,
                    onVolumeChanged = {
                        MeditationService.setVolume(this, it)
                        sessionState = sessionState.copy(volume = it)
                    },
                    onOpenDetail = ::openDetail,
                    onCloseDetail = { detailSummary = null },
                    onReplayProgressChanged = { replayProgress = it },
                    onPreviewTrack = ::togglePreview,
                )
            }
        }
        if (hasBluetoothPermission()) initializeMuseManager()
    }

    override fun onDestroy() {
        removeSessionListener?.invoke()
        mainHandler.removeCallbacksAndMessages(null)
        previewEngine.stop()
        museManager?.close()
        museManager = null
        database.close()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun requestBluetoothPermission() {
        permissionLauncher.launch(requiredRequestPermissions())
    }

    private fun initializeMuseManager() {
        if (museManager != null) return
        museManager = MuseDeviceManager(applicationContext, museListener)
                    connectionStateUi = connectionStateUi.copy(hasBluetoothPermission = true, isInitialized = true, errorMessage = null)
    }

    private fun startScanning() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }
        initializeMuseManager()
        museManager?.startScanning()
        connectionStateUi = connectionStateUi.copy(isScanning = true, errorMessage = null)
    }

    private fun connectMuse(device: MuseDeviceManager.MuseDevice) {
        initializeMuseManager()
        connectionStateUi = connectionStateUi.copy(
            connectionState = "CONNECTING",
            connectedDeviceAddress = device.macAddress,
            errorMessage = null,
        )
        museManager?.connect(device)
    }

    private fun startSession() {
        val address = connectionStateUi.connectedDeviceAddress
        if (address == null || connectionStateUi.connectionState != ConnectionState.CONNECTED.name) {
            connectionStateUi = connectionStateUi.copy(errorMessage = "Connect Muse 2 before starting meditation.")
            return
        }
        val deviceName = connectionStateUi.devices.firstOrNull { it.macAddress == address }?.name ?: "Muse 2"
        // Leave the native Muse instance running. The service creates its own
        // adapter and claims the existing connection without a disconnect gap.
        museManager = null
        connectionStateUi = connectionStateUi.copy(connectionState = "DISCONNECTED", connectedDeviceAddress = null)
        MeditationService.start(this, address, deviceName, selectedDurationSeconds, selectedTrack, sessionState.volume)
    }

    private fun resetCompletedSession() {
        SessionRuntime.resetToIdle(
            plannedSeconds = selectedDurationSeconds,
            track = selectedTrack,
            volume = sessionState.volume,
        )
    }

    private fun openDetail(summary: SessionSummary) {
        ioExecutor.execute {
            val samples = database.loadSamples(summary.id)
            mainHandler.post {
                detailSummary = summary
                detailSamples = samples
                replayProgress = 0f
            }
        }
    }

    private fun refreshHistory() {
        ioExecutor.execute {
            val summaries = database.loadSummaries()
            mainHandler.post { history = summaries }
        }
    }

    private fun togglePreview(track: MusicTrack) {
        if (previewTrack == track) {
            previewTrack = null
            previewEngine.stop()
        } else {
            previewTrack = track
            previewEngine.play(track)
        }
    }

    private fun hasBluetoothPermission(): Boolean = requiredBluetoothPermissions().all(::hasPermission)

    private fun hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requiredBluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requiredRequestPermissions(): Array<String> = buildList {
        addAll(requiredBluetoothPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}

@Composable
private fun HushApp(
    sessionState: SessionState,
    history: List<SessionSummary>,
    activeTab: AppTab,
    selectedDurationSeconds: Int,
    selectedTrack: MusicTrack,
    detailSummary: SessionSummary?,
    detailSamples: List<StateSample>,
    replayProgress: Float,
    connectionState: ConnectionUiState,
    previewTrack: MusicTrack?,
    onTabSelected: (AppTab) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onTrackSelected: (MusicTrack) -> Unit,
    onStartScanning: () -> Unit,
    onConnect: (MuseDeviceManager.MuseDevice) -> Unit,
    onDisconnect: () -> Unit,
    onStartSession: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onStartNewSession: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onOpenDetail: (SessionSummary) -> Unit,
    onCloseDetail: () -> Unit,
    onReplayProgressChanged: (Float) -> Unit,
    onPreviewTrack: (MusicTrack) -> Unit,
) {
    if (detailSummary != null) {
        SessionDetailScreen(detailSummary, detailSamples, replayProgress, onCloseDetail, onReplayProgressChanged)
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hush", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (sessionState.phase == SessionPhase.RUNNING || sessionState.phase == SessionPhase.PAUSED) {
                        Text("Running in background", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 16.dp))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Text(tab.icon, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (activeTab) {
            AppTab.MEDITATE -> MeditateScreen(
                Modifier.padding(innerPadding), sessionState, connectionState, selectedDurationSeconds, selectedTrack,
                onDurationSelected, onTrackSelected, onStartScanning, onConnect, onStartSession, onPause, onResume,
                onFinish, onStartNewSession, onVolumeChanged,
                { id -> history.firstOrNull { it.id == id }?.let(onOpenDetail) }, onDisconnect,
            )
            AppTab.HISTORY -> HistoryScreen(Modifier.padding(innerPadding), history, onOpenDetail)
            AppTab.MUSIC -> MusicScreen(Modifier.padding(innerPadding), selectedTrack, previewTrack, onTrackSelected, onPreviewTrack)
        }
    }
}

@Composable
private fun MeditateScreen(
    modifier: Modifier,
    sessionState: SessionState,
    connectionState: ConnectionUiState,
    selectedDurationSeconds: Int,
    selectedTrack: MusicTrack,
    onDurationSelected: (Int) -> Unit,
    onTrackSelected: (MusicTrack) -> Unit,
    onStartScanning: () -> Unit,
    onConnect: (MuseDeviceManager.MuseDevice) -> Unit,
    onStartSession: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onStartNewSession: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onDisconnect: () -> Unit,
) {
    val isActive = sessionState.phase == SessionPhase.RUNNING || sessionState.phase == SessionPhase.PAUSED || sessionState.phase == SessionPhase.CONNECTING
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (sessionState.phase == SessionPhase.FINISHED) {
            item { CompletionCard(sessionState, onOpenDetail, onStartNewSession) }
        } else if (isActive) {
            item { ActiveSessionCard(sessionState, onPause, onResume, onFinish, onVolumeChanged) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Make space for stillness", style = MaterialTheme.typography.headlineMedium)
                    Text("Let change emerge slowly. There is nothing to chase.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { ConnectionCard(connectionState, onStartScanning, onConnect, onDisconnect) }
            item {
                Text("Choose a duration", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(10 * 60, 20 * 60, 30 * 60).forEach { seconds ->
                        FilterChip(selected = selectedDurationSeconds == seconds, onClick = { onDurationSelected(seconds) }, label = { Text("${seconds / 60} min") })
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Soundscape · ${selectedTrack.title}", style = MaterialTheme.typography.titleMedium)
                        Text(selectedTrack.subtitle, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MusicTrack.entries.forEach { track ->
                                FilterChip(selected = selectedTrack == track, onClick = { onTrackSelected(track) }, label = { Text(track.title) })
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onStartSession,
                    enabled = connectionState.connectedDeviceAddress != null && connectionState.connectionState == ConnectionState.CONNECTED.name,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start meditation") }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: ConnectionUiState,
    onStartScanning: () -> Unit,
    onConnect: (MuseDeviceManager.MuseDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Muse 2", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !state.hasBluetoothPermission -> "Bluetooth permission required"
                    state.connectionState == ConnectionState.CONNECTED.name -> "Connected and ready"
                    state.isScanning -> "Looking for nearby devices…"
                    else -> "Connect your headband"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.dataPacketCount > 0) {
                Text(
                    "Data packets received: ${state.dataPacketCount} · ${state.lastDataPacketType ?: "unknown"}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartScanning, enabled = !state.isScanning) { Text(if (state.hasBluetoothPermission) "Scan" else "Grant permission") }
                if (state.connectedDeviceAddress != null) {
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                }
            }
            state.devices.take(3).forEach { device ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name.ifBlank { "Muse 2" }, style = MaterialTheme.typography.bodyLarge)
                        Text(device.macAddress, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { onConnect(device) }, enabled = state.connectedDeviceAddress != device.macAddress) { Text(if (state.connectedDeviceAddress == device.macAddress) "Connected" else "Connect") }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(state: SessionState, onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit, onVolumeChanged: (Float) -> Unit) {
    val sample = state.latestSample
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (state.phase == SessionPhase.PAUSED) "Paused" else "Meditating", style = MaterialTheme.typography.headlineSmall)
                Text(if (state.connected) "Muse connected" else "Muse disconnected · timer continues", style = MaterialTheme.typography.bodySmall)
            }
            Text(formatDuration(state.elapsedSeconds), style = MaterialTheme.typography.titleLarge)
        }
        ParticlePanel(sample, state.dataGap)
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Soundscape: ${state.track.title}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = state.volume, onValueChange = onVolumeChanged, valueRange = 0f..1f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = if (state.phase == SessionPhase.PAUSED) onResume else onPause, modifier = Modifier.weight(1f)) { Text(if (state.phase == SessionPhase.PAUSED) "Resume" else "Pause") }
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("Finish early") }
        }
        Text("Valid samples ${state.validSampleCount}/${state.sampleCount} · only per-second trends are saved, not raw EEG/PPG", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompletionCard(
    state: SessionState,
    onOpenDetail: (Long) -> Unit,
    onStartNewSession: () -> Unit,
) {
    val result = state.result ?: ResultLabel.INSUFFICIENT
    Card {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Meditation complete", style = MaterialTheme.typography.headlineSmall)
            Text(result.title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(result.description, style = MaterialTheme.typography.bodyLarge)
            Text("Actual duration ${formatDuration(state.elapsedSeconds)} · valid samples ${state.validSampleCount}/${state.sampleCount}", style = MaterialTheme.typography.bodySmall)
            Text("Mindprint", style = MaterialTheme.typography.titleMedium)
            ParticlePanel(state.latestSample, state.latestSample?.valid != true)
            OutlinedButton(onClick = onStartNewSession, modifier = Modifier.fillMaxWidth()) { Text("Meditate again") }
            state.sessionId?.let { Button(onClick = { onOpenDetail(it) }, modifier = Modifier.fillMaxWidth()) { Text("View this session") } }
            Text("This describes relative trends in this session and is not a medical assessment or absolute score.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, history: List<SessionSummary>, onOpenDetail: (SessionSummary) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            Text("Sessions are saved only on this device.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (history.isEmpty()) item { Text("Your first completed meditation will appear here.", modifier = Modifier.padding(top = 24.dp)) }
        else items(history, key = { it.id }) { summary ->
            Card(onClick = { onOpenDetail(summary) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(summary.result.title, style = MaterialTheme.typography.titleMedium)
                        Text(formatDate(summary.startedAt), style = MaterialTheme.typography.bodySmall)
                        Text("${summary.actualSeconds / 60} min · ${summary.track.title}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${summary.validSampleCount}/${summary.sampleCount} sec valid", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SessionDetailScreen(summary: SessionSummary, samples: List<StateSample>, replayProgress: Float, onBack: () -> Unit, onReplayProgressChanged: (Float) -> Unit) {
    val cursor = remember(samples) { ReplayCursor(samples) }
    val replaySample = cursor.sampleAt(replayProgress)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Session details") }, navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } })
    }) { innerPadding ->
        LazyColumn(Modifier.padding(innerPadding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(summary.result.title, style = MaterialTheme.typography.headlineMedium)
                Text("${formatDate(summary.startedAt)} · ${summary.track.title} · ${summary.validSampleCount}/${summary.sampleCount} sec valid", style = MaterialTheme.typography.bodyMedium)
            }
            item { ParticlePanel(replaySample, replaySample?.valid != true) }
            item {
                Text("Particle replay", style = MaterialTheme.typography.titleMedium)
                Slider(value = replayProgress, onValueChange = onReplayProgressChanged)
                Text("${replaySample?.elapsedSeconds ?: 0} sec", style = MaterialTheme.typography.labelSmall)
            }
            item {
                Text("Relative trends", style = MaterialTheme.typography.titleMedium)
                TrendChart(samples)
                Text("Alpha · Theta · Beta · stillness; blank areas indicate missing valid data.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MusicScreen(modifier: Modifier, selectedTrack: MusicTrack, previewTrack: MusicTrack?, onTrackSelected: (MusicTrack) -> Unit, onPreviewTrack: (MusicTrack) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Music", style = MaterialTheme.typography.headlineMedium)
            Text("Plays during meditation and pauses or stops with the session.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        items(MusicTrack.entries) { track ->
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium)
                        Text(track.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                    FilterChip(selected = selectedTrack == track, onClick = { onTrackSelected(track) }, label = { Text("Select") })
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { onPreviewTrack(track) }) { Text(if (previewTrack == track) "Stop" else "Preview") }
                }
            }
        }
    }
}

@Composable
private fun ParticlePanel(sample: StateSample?, dataGap: Boolean) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val alpha = sample?.alpha?.toFloat() ?: 0.2f
                val theta = sample?.theta?.toFloat() ?: 0.2f
                val beta = sample?.beta?.toFloat() ?: 0.2f
                val stillness = sample?.stillness?.toFloat() ?: 0f
                val radius = 42f + stillness * 38f
                for (index in 0 until 72) {
                    val angle = index * 0.47f + alpha * 2.4f
                    val distance = radius + (index % 9) * (8f + theta * 10f) + beta * 12f
                    val x = center.x + kotlin.math.cos(angle.toDouble()).toFloat() * distance
                    val y = center.y + kotlin.math.sin(angle.toDouble()).toFloat() * distance
                    val color = Color(
                        red = (0.42f + theta * 0.35f).coerceIn(0f, 1f),
                        green = (0.68f + stillness * 0.25f).coerceIn(0f, 1f),
                        blue = (0.72f + alpha * 0.2f).coerceIn(0f, 1f),
                        alpha = if (sample?.valid == true) 0.72f else 0.18f,
                    )
                    drawCircle(color, radius = 2.2f + (index % 3), center = Offset(x, y))
                }
                drawCircle(Color(0xFF9ADBCB).copy(alpha = if (sample?.valid == true) 0.18f else 0.08f), radius = radius, center = center, style = Stroke(width = 18f))
            }
            if (dataGap) Text("Data gap", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendChart(samples: List<StateSample>) {
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        if (samples.count { it.valid } < 2) return@Canvas
        val colors = listOf(Color(0xFF8ED5C5), Color(0xFFB7A7E8), Color(0xFFE7B578), Color(0xFF8FB9DE))
        val values = listOf<(StateSample) -> Double?>({ it.alpha }, { it.theta }, { it.beta }, { it.stillness })
        values.forEachIndexed { seriesIndex, selector ->
            var path: Path? = null
            samples.forEachIndexed { index, sample ->
                if (!sample.valid) {
                    path?.let { drawPath(it, colors[seriesIndex], style = Stroke(width = 4f)) }
                    path = null
                    return@forEachIndexed
                }
                val x = index.toFloat() / (samples.lastIndex).coerceAtLeast(1) * size.width
                val y = size.height - (selector(sample)?.toFloat()?.coerceIn(0f, 1f) ?: 0f) * size.height
                if (path == null) path = Path().also { it.moveTo(x, y) } else path?.lineTo(x, y)
            }
            path?.let { drawPath(it, colors[seriesIndex], style = Stroke(width = 4f)) }
        }
    }
}

private data class ConnectionUiState(
    val hasBluetoothPermission: Boolean = false,
    val isInitialized: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<MuseDeviceManager.MuseDevice> = emptyList(),
    val connectionState: String? = null,
    val connectedDeviceAddress: String? = null,
    val dataPacketCount: Int = 0,
    val lastDataPacketType: String? = null,
    val errorMessage: String? = null,
)

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun formatDate(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(timestamp))

@Preview(showBackground = true)
@Composable
private fun HushPreview() {
    HushTheme { ParticlePanel(StateSample(1, alpha = 0.4, theta = 0.3, beta = 0.2, stillness = 0.8, valid = true), false) }
}
