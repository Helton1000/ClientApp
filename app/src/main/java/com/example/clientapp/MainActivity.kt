package com.example.clientapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager as AndroidAudioManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.clientapp.network.SocketManager
import com.example.clientapp.network.WebRTCClient
import androidx.compose.ui.unit.dp
import org.webrtc.*

class MainActivity : ComponentActivity() {

    private var eglBase: EglBase? = null
    private var webRTCClient: WebRTCClient? = null
    private var socketManager: SocketManager? = null
    private var surfaceViewRenderer: SurfaceViewRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eglBase = EglBase.create()
        socketManager = SocketManager()

        // 🔊 SOM LIMPO (IMPORTANTE PARA QUALIDADE)
        val audioManager = getSystemService(AUDIO_SERVICE) as AndroidAudioManager
        audioManager.mode = AndroidAudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.isMicrophoneMute = false

        setContent {

            var hasPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                )
            }

            var isConnected by remember { mutableStateOf(false) }
            var isStreaming by remember { mutableStateOf(false) }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                hasPermission =
                    permissions[Manifest.permission.CAMERA] == true &&
                            permissions[Manifest.permission.RECORD_AUDIO] == true
            }

            LaunchedEffect(Unit) {
                if (!hasPermission) {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                } else {
                    socketManager?.connect("http://192.168.15.6:3000") {
                        Log.d("ClientApp", "Socket conectado")
                        runOnUiThread { isConnected = true }
                    }
                }
            }

            fun createWebRTCIfNeeded() {
                if (webRTCClient == null && isConnected) {
                    webRTCClient = WebRTCClient(
                        this,
                        eglBase!!,
                        socketManager!!
                    )
                }
            }

            if (hasPermission) {

                Column(Modifier.fillMaxSize()) {

                    Box(Modifier.weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    init(eglBase!!.eglBaseContext, null)
                                    setMirror(false)
                                    surfaceViewRenderer = this
                                }
                            }
                        )
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        onClick = {
                            if (!isStreaming) {
                                createWebRTCIfNeeded()
                                surfaceViewRenderer?.let {
                                    webRTCClient?.startStreaming(it)
                                    isStreaming = true
                                }
                            } else {
                                webRTCClient?.stopStreaming()
                                isStreaming = false
                            }
                        }
                    ) {
                        Text(if (isStreaming) "Parar" else "Iniciar")
                    }
                }

            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Permissões necessárias")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webRTCClient?.stopStreaming()
        socketManager?.disconnect()
        eglBase?.release()
    }
}