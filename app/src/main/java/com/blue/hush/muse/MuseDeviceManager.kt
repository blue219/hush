package com.blue.hush.muse

import android.content.Context
import com.choosemuse.libmuse.ConnectionState
import com.choosemuse.libmuse.Muse
import com.choosemuse.libmuse.MuseArtifactPacket
import com.choosemuse.libmuse.MuseConnectionListener
import com.choosemuse.libmuse.MuseConnectionPacket
import com.choosemuse.libmuse.MuseDataListener
import com.choosemuse.libmuse.MuseDataPacket
import com.choosemuse.libmuse.MuseDataPacketType
import com.choosemuse.libmuse.MuseListener
import com.choosemuse.libmuse.MuseManagerAndroid
import com.choosemuse.libmuse.MusePreset

/**
 * Small application-facing adapter around LibMuse's Android manager.
 *
 * LibMuse invokes callbacks from its own worker threads. Consumers should move
 * callbacks to the UI thread when updating Compose state or views.
 */
class MuseDeviceManager(
    context: Context,
    private val listener: Listener,
) : AutoCloseable {

    interface Listener {
        fun onDevicesChanged(devices: List<MuseDevice>)

        fun onConnectionStateChanged(
            device: MuseDevice,
            previous: ConnectionState,
            current: ConnectionState,
        )

        fun onDataPacket(packet: MusePacket)

        fun onArtifact(packet: MuseArtifact)
    }

    data class MuseDevice(
        internal val nativeMuse: Muse,
        val name: String,
        val macAddress: String,
    )

    data class MusePacket(
        val device: MuseDevice,
        val type: MuseDataPacketType,
        val values: List<Double>,
        val timestamp: Long,
    )

    data class MuseArtifact(
        val device: MuseDevice,
        val blink: Boolean,
        val headbandOn: Boolean,
        val jawClench: Boolean,
        val timestamp: Long,
    )

    private val manager = MuseManagerAndroid.getInstance()
    private var connectedMuse: Muse? = null

    private val museListener = object : MuseListener() {
        override fun museListChanged() {
            publishDevices()
        }
    }

    private val connectionListener = object : MuseConnectionListener() {
        override fun receiveMuseConnectionPacket(packet: MuseConnectionPacket, muse: Muse) {
            val device = deviceFor(muse)
            listener.onConnectionStateChanged(
                device = device,
                previous = packet.getPreviousConnectionState(),
                current = packet.getCurrentConnectionState(),
            )
            if (packet.getCurrentConnectionState() == ConnectionState.DISCONNECTED &&
                connectedMuse?.getMacAddress() == muse.getMacAddress()
            ) {
                connectedMuse = null
            }
        }
    }

    private val dataListener = object : MuseDataListener() {
        override fun receiveMuseDataPacket(packet: MuseDataPacket, muse: Muse) {
            listener.onDataPacket(
                MusePacket(
                    device = deviceFor(muse),
                    type = packet.packetType(),
                    values = packet.values().map { it.toDouble() },
                    timestamp = packet.timestamp(),
                ),
            )
        }

        override fun receiveMuseArtifactPacket(packet: MuseArtifactPacket, muse: Muse) {
            listener.onArtifact(
                MuseArtifact(
                    device = deviceFor(muse),
                    blink = packet.getBlink(),
                    headbandOn = packet.getHeadbandOn(),
                    jawClench = packet.getJawClench(),
                    timestamp = packet.getTimestamp(),
                ),
            )
        }
    }

    init {
        // LibMuse requires the context to be set before any other SDK operation.
        manager.setContext(context.applicationContext)
        manager.setMuseListener(museListener)
    }

    fun startScanning() {
        manager.stopListening()
        manager.startListening()
        publishDevices()
    }

    fun stopScanning() {
        manager.stopListening()
    }

    fun connect(device: MuseDevice) {
        manager.stopListening()
        connectedMuse?.disconnect()

        val muse = device.nativeMuse
        muse.unregisterAllListeners()
        muse.registerConnectionListener(connectionListener)
        DATA_PACKET_TYPES.forEach { type ->
            muse.registerDataListener(dataListener, type)
        }
        // Use Muse 2's stable core preset for EEG and IMU streaming.
        // PPG remains registered for firmware that exposes the optional stream.
        muse.setPreset(MusePreset.PRESET_21)
        connectedMuse = muse
        val currentState = muse.getConnectionState()
        if (currentState == ConnectionState.CONNECTED) {
            // A foreground service can claim the already-running Muse instance
            // after the activity has selected it. Notify the new owner without
            // forcing a disconnect/reconnect handoff.
            listener.onConnectionStateChanged(device, currentState, currentState)
        } else {
            muse.runAsynchronously()
        }
    }

    fun disconnect() {
        connectedMuse?.disconnect()
        connectedMuse = null
    }

    override fun close() {
        disconnect()
        stopScanning()
    }

    private fun publishDevices() {
        listener.onDevicesChanged(manager.getMuses().map(::deviceFor))
    }

    private fun deviceFor(muse: Muse): MuseDevice = MuseDevice(
        nativeMuse = muse,
        name = muse.getName(),
        macAddress = muse.getMacAddress(),
    )

    private companion object {
        val DATA_PACKET_TYPES = listOf(
            MuseDataPacketType.EEG,
            MuseDataPacketType.GYRO,
            MuseDataPacketType.ALPHA_RELATIVE,
            MuseDataPacketType.BETA_RELATIVE,
            MuseDataPacketType.DELTA_RELATIVE,
            MuseDataPacketType.THETA_RELATIVE,
            MuseDataPacketType.GAMMA_RELATIVE,
            MuseDataPacketType.ALPHA_ABSOLUTE,
            MuseDataPacketType.BETA_ABSOLUTE,
            MuseDataPacketType.DELTA_ABSOLUTE,
            MuseDataPacketType.THETA_ABSOLUTE,
            MuseDataPacketType.GAMMA_ABSOLUTE,
            MuseDataPacketType.ALPHA_SCORE,
            MuseDataPacketType.BETA_SCORE,
            MuseDataPacketType.DELTA_SCORE,
            MuseDataPacketType.THETA_SCORE,
            MuseDataPacketType.GAMMA_SCORE,
            MuseDataPacketType.ACCELEROMETER,
            MuseDataPacketType.PPG,
            MuseDataPacketType.IS_PPG_GOOD,
            MuseDataPacketType.IS_HEART_GOOD,
            MuseDataPacketType.IS_GOOD,
            MuseDataPacketType.HSI,
            MuseDataPacketType.HSI_PRECISION,
            MuseDataPacketType.BATTERY,
            MuseDataPacketType.ARTIFACTS,
        )
    }
}
