package com.example.clientapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.webrtc.*
import com.example.clientapp.audio.AudioManager
import com.example.clientapp.video.VideoManager
import com.example.clientapp.network.SocketManager
import com.example.clientapp.ui.mainscreen.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var eglBase: EglBase
    private lateinit var socketManager: SocketManager
    private lateinit var audioManager: AudioManager
    private var videoManager: VideoManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 ESSENCIAL: inicializa WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()
        socketManager = SocketManager()
        audioManager = AudioManager(this)

        setContent {
            var isStreaming by remember { mutableStateOf(false) }

            // 🎯 CONTROLE DE PERMISSÃO
            var hasPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                )
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                hasPermission =
                    it[Manifest.permission.CAMERA] == true &&
                            it[Manifest.permission.RECORD_AUDIO] == true
            }

            LaunchedEffect(Unit) {
                if (!hasPermission) {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                }
            }

            // 🧠 UI PRINCIPAL
            if (hasPermission) {

                MainScreen(
                    isStreaming = isStreaming,
                    onClick = {
                        if (!isStreaming) {

                            socketManager.connect("http://192.168.15.6:3000") {
                                socketManager.getSocket()?.let {
                                    audioManager.start(it)
                                }
                            }

                            isStreaming = true

                        } else {
                            audioManager.stop()
                            socketManager.disconnect()
                            videoManager?.stop()
                            isStreaming = false
                        }
                    }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            SurfaceViewRenderer(context).apply {
                                init(eglBase.eglBaseContext, null)

                                videoManager = VideoManager(eglBase) {
                                    socketManager.emitFrame(it)
                                }

                                videoManager?.start(this@MainActivity, this)
                            }
                        }
                    )
                }

            } else {
                // 🔒 Tela sem permissão
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Permissão de câmera e áudio necessária")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        audioManager.stop()
        socketManager.disconnect()
        videoManager?.stop()
        eglBase.release()
    }
}