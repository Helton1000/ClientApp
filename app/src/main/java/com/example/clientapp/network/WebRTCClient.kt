package com.example.clientapp.network

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRTCClient(
    private val context: Context,
    private val eglBase: EglBase,
    private val socketManager: SocketManager
) {

    private var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        // Configuração do módulo de áudio (Padrão estável)
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        audioDeviceModule.release()
        setupSocket()
    }

    private fun setupSocket() {
        val socket = socketManager.getSocket() ?: return

        socket.on("answer") { args ->
            val sdp = args[0] as String
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { Log.d("WebRTC", "SDP remoto aplicado") }
                override fun onSetFailure(s: String?) { Log.e("WebRTC", "Erro SDP: $s") }
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onCreateFailure(s: String?) {}
            }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }

        socket.on("candidate") { args ->
            val data = args[0] as JSONObject
            val candidate = IceCandidate(
                data.getString("sdpMid"),
                data.getInt("sdpMLineIndex"),
                data.getString("candidate")
            )
            peerConnection?.addIceCandidate(candidate)
        }
    }

    // Força o viva-voz e volume máximo
    private fun enableSpeakerphone() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
            Log.d("WebRTC", "🔊 Viva-voz ativado e volume no máximo")
        } catch (e: Exception) {
            Log.e("WebRTC", "Erro ao ativar viva-voz: ${e.message}")
        }
    }

    fun startStreaming(renderer: SurfaceViewRenderer) {
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val config = PeerConnection.RTCConfiguration(iceServers)

        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val data = JSONObject()
                data.put("sdpMid", candidate.sdpMid)
                data.put("sdpMLineIndex", candidate.sdpMLineIndex)
                data.put("candidate", candidate.sdp)
                socketManager.getSocket()?.emit("candidate", data)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) track.addSink(renderer)
                if (track is AudioTrack) {
                    Log.d("WebRTC", "🔊 Áudio remoto detectado")
                    track.setEnabled(true)
                    track.setVolume(10.0) // Ganho extra via software se suportado
                    enableSpeakerphone() // Força saída pelo alto-falante principal
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    enableSpeakerphone() // Garante o volume ao conectar
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
        })

        // Captura de Vídeo
        val enumerator = Camera2Enumerator(context)
        val device = enumerator.deviceNames.first { enumerator.isBackFacing(it) }
        videoCapturer = enumerator.createCapturer(device, null)
        val videoSource = factory.createVideoSource(false)
        val helper = SurfaceTextureHelper.create("capture", eglBase.eglBaseContext)
        videoCapturer?.initialize(helper, context, videoSource.capturerObserver)
        videoCapturer?.startCapture(640, 480, 30)
        val videoTrack = factory.createVideoTrack("video_track", videoSource)
        videoTrack.addSink(renderer)
        peerConnection?.addTrack(videoTrack)

        // Áudio Local (Seu microfone)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val audioSource = factory.createAudioSource(audioConstraints)
        val audioTrack = factory.createAudioTrack("audio_track", audioSource)
        peerConnection?.addTrack(audioTrack)

        // Criar Oferta
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                socketManager.getSocket()?.emit("offer", sdp.description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    fun stopStreaming() {
        try { videoCapturer?.stopCapture() } catch (e: Exception) {}
        videoCapturer?.dispose()
        peerConnection?.close()
        peerConnection = null
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    fun dispose() {
        factory.dispose()
    }
}
