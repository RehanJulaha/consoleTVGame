package com.retrogamestick.console.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.retrogamestick.console.BuildConfig
import org.webrtc.*
import retro.gamestick.network.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

class ConsoleNetworkManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onInputFrame: (InputFrame) -> Unit,
    private val onPlayerConnected: (Int, String) -> Unit,
    private val onPlayerDisconnected: (Int) -> Unit
) {
    private val TAG = "ConsoleNetworkManager"
    
    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                networkIgnoreMask = 0
            })
            .createPeerConnectionFactory()
    }
    
    private val peerConnections = mutableMapOf<Int, PeerConnection>()
    private val dataChannels = mutableMapOf<Int, DataChannel>()
    private val controlChannels = mutableMapOf<Int, DataChannel>()
    
    private var localSdp: SessionDescription? = null
    private var isOfferCreated = false
    
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").create(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").create()
    )
    
    private val pcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
        bundlePolicy = PeerConnection.BundlePolicy.MAX_BUNDLE
        rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        iceTransportPolicy = PeerConnection.IceTransportPolicy.ALL
    }
    
    private val pcConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }
    
    private val dcInit = DataChannel.Init().apply {
        isOrdered = false
        maxRetransmits = 0
        protocol = "retro-input"
    }
    
    private val controlDcInit = DataChannel.Init().apply {
        isOrdered = true
        protocol = "retro-control"
    }
    
    fun startDiscovery() {
        scope.launch {
            MdnsAdvertiser.start(context, scope) { port ->
                // mDNS advertises the WebRTC signaling port
            }
        }
    }
    
    fun createOffer(): String {
        val pc = createPeerConnection(0) // P1
        val dc = pc.createDataChannel("input-p1", dcInit)
        val controlDc = pc.createDataChannel("control-p1", controlDcInit)
        
        dataChannels[0] = dc
        controlChannels[0] = controlDc
        
        setupDataChannel(dc, 0)
        setupControlChannel(controlDc, 0)
        
        val offer = factory.createOffer(pc, pcConstraints)
        pc.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                localSdp = sessionDescription
                isOfferCreated = true
                if (BuildConfig.DEBUG) Log.d(TAG, "Offer created: ${sessionDescription.description}")
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(s: String) { Log.e(TAG, "Create offer failed: $s") }
            override fun onSetFailure(s: String) { Log.e(TAG, "Set local desc failed: $s") }
        }, offer)
        
        return localSdp?.description ?: ""
    }
    
    fun handleAnswer(playerSlot: Int, answerSdp: String): Boolean {
        val pc = peerConnections[playerSlot] ?: return false
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { if (BuildConfig.DEBUG) Log.d(TAG, "Remote answer set for P$playerSlot") }
            override fun onSetFailure(s: String) { Log.e(TAG, "Set remote answer failed: $s") }
            override fun onCreateSuccess(sessionDescription: SessionDescription) {}
            override fun onCreateFailure(s: String) {}
        }, answer)
        return true
    }
    
    fun addIceCandidate(playerSlot: Int, candidate: String, sdpMid: String, sdpMLineIndex: Int): Boolean {
        val pc = peerConnections[playerSlot] ?: return false
        pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
        return true
    }
    
    private fun createPeerConnection(playerSlot: Int): PeerConnection {
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                // Send to phone via control channel
                sendIceCandidate(playerSlot, candidate)
            }
            override fun onDataChannel(channel: DataChannel) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (BuildConfig.DEBUG) Log.d(TAG, "ICE state P$playerSlot: $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        onPlayerConnected(playerSlot, "Player ${playerSlot + 1}")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        onPlayerDisconnected(playerSlot)
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onRemoveTrack(receiver: RtpReceiver) {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {}
        }
        
        return factory.createPeerConnection(pcConfig, pcConstraints, observer)!!
    }
    
    private fun setupDataChannel(channel: DataChannel, playerSlot: Int) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (BuildConfig.DEBUG) Log.d(TAG, "DataChannel P$playerSlot state: ${channel.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary.data != null) {
                    try {
                        val frame = InputFrame.parseFrom(buffer.binary.data)
                        if (frame.playerSlot == playerSlot) {
                            onInputFrame(frame)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse input frame failed", e)
                    }
                }
            }
        })
    }
    
    private fun setupControlChannel(channel: DataChannel, playerSlot: Int) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() {
                if (BuildConfig.DEBUG) Log.d(TAG, "ControlChannel P$playerSlot state: ${channel.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary.data != null) {
                    try {
                        val msg = ControlMessage.parseFrom(buffer.binary.data)
                        handleControlMessage(playerSlot, msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse control msg failed", e)
                    }
                }
            }
            override fun onBufferedAmountChange(previousAmount: Long) {}
        })
    }
    
    private fun handleControlMessage(playerSlot: Int, msg: ControlMessage) {
        when (msg.type) {
            ControlMessage.Type.PAIR_REQUEST -> {
                val req = PairRequest.parseFrom(msg.payload)
                val pin = String.format("%04d", (1000..9999).random())
                val resp = PairResponse.newBuilder()
                    .setPlayerSlot(playerSlot)
                    .setPin(pin)
                    .setSessionId(UUID.randomUUID().toString())
                    .build()
                sendControl(playerSlot, ControlMessage.newBuilder()
                    .setType(ControlMessage.Type.PAIR_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build())
            }
            ControlMessage.Type.PING -> {
                sendControl(playerSlot, ControlMessage.newBuilder()
                    .setType(ControlMessage.Type.PONG)
                    .build())
            }
            ControlMessage.Type.GAME_LOAD -> {
                // Handle game load request
            }
        }
    }
    
    private fun sendIceCandidate(playerSlot: Int, candidate: IceCandidate) {
        val json = """{"candidate":"${candidate.sdp}","sdpMid":"${candidate.sdpMid}","sdpMLineIndex":${candidate.sdpMLineIndex}}"""
        sendControl(playerSlot, ControlMessage.newBuilder()
            .setType(ControlMessage.Type.PAIR_RESPONSE) // Reuse for ICE
            .setPayload(json.toByteString())
            .build())
    }
    
    private fun sendControl(playerSlot: Int, msg: ControlMessage) {
        controlChannels[playerSlot]?.let { channel ->
            if (channel.state() == DataChannel.State.OPEN) {
                channel.send(DataChannel.Buffer(msg.toByteArray(), false))
            }
        }
    }
    
    fun disconnect(playerSlot: Int) {
        dataChannels.remove(playerSlot)?.close()
        controlChannels.remove(playerSlot)?.close()
        peerConnections.remove(playerSlot)?.close()
        onPlayerDisconnected(playerSlot)
    }
    
    fun disconnectAll() {
        peerConnections.keys.forEach { disconnect(it) }
    }
    
    fun getLocalIp(): String {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            return String.format("%d.%d.%d.%d", ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
        } catch (e: Exception) {
            return "127.0.0.1"
        }
    }
}

object MdnsAdvertiser {
    private const val SERVICE_TYPE = "_retroconsole._tcp.local."
    private const val SERVICE_NAME = "RetroGameStick"
    
    fun start(context: Context, scope: CoroutineScope, onPortAssigned: (Int) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val jmdns = javax.jmdns.JmDNS.create()
                val serviceInfo = javax.jmdns.ServiceInfo.create(
                    SERVICE_TYPE,
                    SERVICE_NAME,
                    8080,
                    mapOf(
                        "version" to "1",
                        "players" to "2",
                        "name" to "RetroGameStick"
                    ),
                    true
                )
                jmdns.registerService(serviceInfo)
                onPortAssigned(8080)
            } catch (e: Exception) {
                Log.e("MdnsAdvertiser", "Failed to start mDNS", e)
            }
        }
    }
}