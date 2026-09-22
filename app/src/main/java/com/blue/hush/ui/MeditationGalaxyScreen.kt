package com.blue.hush.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionState
import java.util.Locale

@Composable
fun MeditationGalaxyScreen(
    state: SessionState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
) {
    var volumeExpanded by rememberSaveable { mutableStateOf(false) }
    val paused = state.phase == SessionPhase.PAUSED
    // The service can retain its last valid sample during a gap. Reject stale
    // seconds even if an intermediate connection update clears dataGap.
    val signalMissing = !state.connected || state.dataGap ||
        state.latestSample?.elapsedSeconds != state.elapsedSeconds || galaxyAgitation(state.latestSample) == null
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
            previousBehavior?.let { controller?.systemBarsBehavior = it }
        }
    }
    // Controls are always visible; Back only dismisses the expanded volume panel.
    BackHandler { volumeExpanded = false }
    Box(Modifier.fillMaxSize().background(Color(0xFF030711))) {
        GalaxyParticleField(state.latestSample, signalMissing, paused, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        String.format(Locale.US, "%02d:%02d", state.elapsedSeconds / 60, state.elapsedSeconds % 60),
                        color = Color(0xFFE7EDF7),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    val status = when {
                        paused -> "Paused"
                        state.phase == SessionPhase.CONNECTING -> "Connecting…"
                        !state.connected -> "Reconnecting…"
                        signalMissing -> "Waiting for EEG…"
                        else -> null
                    }
                    status?.let { Text(it, color = Color(0xFF9EAEC4), style = MaterialTheme.typography.labelMedium) }
                }
                IconButton(
                    onClick = { volumeExpanded = !volumeExpanded },
                    modifier = Modifier.semantics { contentDescription = if (volumeExpanded) "Hide volume" else "Adjust volume" },
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        val w = size.width
                        val h = size.height
                        val speaker = Path().apply {
                            moveTo(w * 0.12f, h * 0.38f)
                            lineTo(w * 0.3f, h * 0.38f)
                            lineTo(w * 0.52f, h * 0.18f)
                            lineTo(w * 0.52f, h * 0.82f)
                            lineTo(w * 0.3f, h * 0.62f)
                            lineTo(w * 0.12f, h * 0.62f)
                            close()
                        }
                        drawPath(speaker, Color(0xFFE7EDF7))
                        drawArc(Color(0xFFE7EDF7), -60f, 120f, false, Offset(w * 0.4f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.48f, h * 0.6f), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (volumeExpanded) {
                    Slider(
                        value = state.volume,
                        onValueChange = onVolumeChanged,
                        modifier = Modifier.semantics { contentDescription = "Meditation volume" },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = if (paused) onResume else onPause,
                        enabled = paused || state.phase == SessionPhase.RUNNING,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBBDFFF), contentColor = Color(0xFF102136)),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (paused) "Resume" else "Pause") }
                    OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE7EDF7))) { Text("Finish") }
                }
            }
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
