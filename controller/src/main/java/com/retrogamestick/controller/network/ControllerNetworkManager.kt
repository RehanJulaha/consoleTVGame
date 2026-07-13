package com.retrogamestick.controller.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.retrogamestick.controller.BuildConfig
import org.webrtc.*
import retro.gamestick.network.*
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class ControllerNetworkManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onConnected: (Int, String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onPinRequired: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "ControllerNetworkManager"
    
    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }
    
    private var peerConnection: PeerConnection? = null
    private var inputChannel: DataChannel? = null
    private var controlChannel: DataChannel? = null
    
    private var myPlayerSlot = -1
    private var sessionId = ""
    private var isConnected = false
    
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").create(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").create()
    )
    
    private val pcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
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
    
    fun discoverAndConnect() {
        scope.launch(Dispatchers.IO) {
            try {
                val jmdns = javax.jmdns.JmDNS.create()
                jmdns.addServiceListener("_retroconsole._tcp.local.", object : javax.jmdns.ServiceListener {
                    override fun serviceAdded(event: javax.jmdns.ServiceEvent) {
                        jmdns.requestServiceInfo(event.getType(), event.getName(), 1)
                    }
                    override fun serviceRemoved(event: javax.jmdns.ServiceEvent) {}
                    override fun serviceResolved(event: javax.jmdns.ServiceEvent) {
                        val info = event.getInfo()
                        val addresses = info.getInetAddresses()
                        if (addresses.isNotEmpty()) {
                            val host = addresses[0].hostAddress
                            val port = info.getPort()
                            scope.launch(Dispatchers.Main) {
                                connectToConsole(host, port)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onError("mDNS discovery failed: ${e.message}")
            }
        }
    }
    
    private fun connectToConsole(host: String, port: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Create peer connection
                peerConnection = factory.createPeerConnection(pcConfig, pcConstraints, object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        sendIceCandidate(candidate)
                    }
                    override fun onDataChannel(channel: DataChannel) {}
                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "ICE state: $newState")
                        when (newState) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED -> {
                                isConnected = true
                                scope.launch(Dispatchers.Main) { onConnected(myPlayerSlot, sessionId) }
                            }
                            PeerConnection.IceConnectionState.DISCONNECTED,
                            PeerConnection.IceConnectionState.FAILED,
                            PeerConnection.IceConnectionState.CLOSED -> {
                                isConnected = false
                                scope.launch(Dispatchers.Main) { onDisconnected() }
                            }
                        }
                    }
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                    override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                    override fun onAddStream(stream: MediaStream) {}
                    override fun onRemoveStream(stream: MediaStream) {}
                    override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {}
                    override fun onRemoveTrack(receiver: RtpReceiver) {}
                })!!
                
                // Create data channels
                inputChannel = peerConnection!!.createDataChannel("input", dcInit)
                controlChannel = peerConnection!!.createDataChannel("control", controlDcInit)
                
                setupDataChannel()
                setupControlChannel()
                
                // Create offer
                val offer = factory.createOffer(peerConnection!!, pcConstraints)
                peerConnection!!.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        // Send offer via HTTP to console
                        sendOfferViaHttp(host, port, sessionDescription.description)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(s: String) { onError("Create offer failed: $s") }
                    override fun onSetFailure(s: String) { onError("Set local failed: $s") }
                }, offer)
                
            } catch (e: Exception) {
                onError("Connection failed: ${e.message}")
            }
        }
    }
    
    private fun setupDataChannel() {
        inputChannel?.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() {
                if (BuildConfig.DEBUG) Log.d(TAG, "Input channel state: ${inputChannel?.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {}
            override fun onBufferedAmountChange(previousAmount: Long) {}
        })
    }
    
    private fun setupControlChannel() {
        controlChannel?.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() {
                if (BuildConfig.DEBUG) Log.d(TAG, "Control channel state: ${controlChannel?.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary.data != null) {
                    try {
                        val msg = ControlMessage.parseFrom(buffer.binary.data)
                        handleControlMessage(msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse control msg failed", e)
                    }
                }
            }
            override fun onBufferedAmountChange(previousAmount: Long) {}
        })
    }
    
    private fun handleControlMessage(msg: ControlMessage) {
        when (msg.type) {
            ControlMessage.Type.PAIR_RESPONSE -> {
                val resp = PairResponse.parseFrom(msg.payload)
                myPlayerSlot = resp.playerSlot.toInt()
                sessionId = resp.sessionId
                if (BuildConfig.DEBUG) Log.d(TAG, "Assigned player slot: $myPlayerSlot, PIN: ${resp.pin}")
                scope.launch(Dispatchers.Main) { onPinRequired(resp.pin) }
            }
            ControlMessage.Type.PONG -> {
                // Keep alive
            }
            ControlMessage.Type.PLAYER_ASSIGN -> {
                val assign = PlayerAssign.parseFrom(msg.payload)
                myPlayerSlot = assign.assignedSlot.toInt()
            }
        }
    }
    
    private fun sendOfferViaHttp(host: String, port: Int, sdp: String) {
        // HTTP POST to console's signaling endpoint
        // For now, we'll use a simple approach - in production use WebSocket
        scope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$host:$port/api/signaling/offer")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val json = """{"sdp":"${sdp.replace("\n", "\\n")}","type":"offer"}"""
                conn.outputStream.write(json.toByteArray())
                conn.outputStream.flush()
                
                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val inputStream = conn.inputStream.bufferedReader()
                    val response = inputStream.readText()
                    // Parse answer SDP
                    val answerSdp = parseAnswerFromResponse(response)
                    if (answerSdp.isNotEmpty()) {
                        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onSetSuccess() { if (BuildConfig.DEBUG) Log.d(TAG, "Remote answer set") }
                            override fun onSetFailure(s: String) { onError("Set remote failed: $s") }
                            override fun onCreateSuccess(sessionDescription: SessionDescription) {}
                            override fun onCreateFailure(s: String) {}
                        }, answer)
                    }
                }
            } catch (e: Exception) {
                onError("HTTP signaling failed: ${e.message}")
            }
        }
    }
    
    private fun parseAnswerFromResponse(response: String): String {
        // Parse JSON response for answer SDP
        try {
            val json = org.json.JSONObject(response)
            return json.getString("sdp").replace("\\n", "\n")
        } catch (e: Exception) {
            return ""
        }
    }
    
    private fun sendIceCandidate(candidate: IceCandidate) {
        val json = """{"candidate":"${candidate.sdp}","sdpMid":"${candidate.sdpMid}","sdpMLineIndex":${candidate.sdpMLineIndex}}"""
        sendControl(ControlMessage.newBuilder()
            .setType(ControlMessage.Type.PAIR_RESPONSE)
            .setPayload(json.toByteString())
            .build())
    }
    
    fun sendInputFrame(frame: InputFrame) {
        inputChannel?.let { channel ->
            if (channel.state() == DataChannel.State.OPEN) {
                channel.send(DataChannel.Buffer(frame.toByteArray(), false))
            }
        }
    }
    
    private fun sendControl(msg: ControlMessage) {
        controlChannel?.let { channel ->
            if (channel.state() == DataChannel.State.OPEN) {
                channel.send(DataChannel.Buffer(msg.toByteArray(), false))
            }
        }
    }
    
    fun sendPairRequest(deviceName: String, deviceId: String) {
        val req = PairRequest.newBuilder()
            .setDeviceName(deviceName)
            .setDeviceId(deviceId)
            .build()
        sendControl(ControlMessage.newBuilder()
            .setType(ControlMessage.Type.PAIR_REQUEST)
            .setPayload(req.toByteString())
            .build())
    }
    
    fun disconnect() {
        inputChannel?.close()
        controlChannel?.close()
        peerConnection?.close()
        isConnected = false
    }
    
    fun getPlayerSlot(): Int = myPlayerSlot
    fun isConnected(): Boolean = isConnected
}