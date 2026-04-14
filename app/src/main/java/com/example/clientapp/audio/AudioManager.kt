package com.example.clientapp.audio

import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import io.socket.client.Socket
import kotlinx.coroutines.*
import android.Manifest
import android.content.Context
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.media.AudioManager as AndroidAudioManager

class AudioManager(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var audioPlayer: AudioTrack? = null
    private var isRunning = false
    private val sampleRate = 44100
    private var job: Job? = null

    fun start(socket: Socket) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        val minBuf = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)

        val bufferSize = minBuf * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioPlayer = AudioTrack(
            AndroidAudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )

        val sessionId = audioRecord?.audioSessionId ?: return

        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(sessionId)?.enabled = true
        }

        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.enabled = true
        }

        audioRecord?.startRecording()
        audioPlayer?.play()
        isRunning = true

        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) socket.emit("audio", buffer.copyOf(read))
            }
        }

        socket.on("audio") { args ->
            val data = args[0] as? ByteArray
            data?.let { audioPlayer?.write(it, 0, it.size) }
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()

        audioRecord?.release()
        audioPlayer?.release()
    }
}