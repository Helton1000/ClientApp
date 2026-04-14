package com.example.clientapp.video

import android.graphics.*
import org.webrtc.*
import java.io.ByteArrayOutputStream

class VideoManager(
    private val eglBase: EglBase,
    private val sendFrame: (ByteArray) -> Unit
) {

    private var capturer: VideoCapturer? = null
    private var lastFrameTime = 0L

    fun start(context: android.content.Context, renderer: SurfaceViewRenderer) {
        val enumerator = Camera2Enumerator(context)
        val device = enumerator.deviceNames.first()
        capturer = enumerator.createCapturer(device, null)

        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val source = factory.createVideoSource(false)

        capturer?.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
            context,
            source.capturerObserver
        )

        capturer?.startCapture(320, 240, 15)

        val track = factory.createVideoTrack("101", source)
        track.addSink(renderer)

        track.addSink { frame ->
            val now = System.currentTimeMillis()

            if (now - lastFrameTime > 100) {
                lastFrameTime = now
                processFrame(frame)
            }
        }
    }

    private fun processFrame(frame: VideoFrame) {
        val i420 = frame.buffer.toI420() ?: return
        val nv21 = ByteArray(i420.width * i420.height * 3 / 2)

        val y = i420.dataY
        val u = i420.dataU
        val v = i420.dataV

        var pos = 0

        for (row in 0 until i420.height) {
            y.position(row * i420.strideY)
            y.get(nv21, pos, i420.width)
            pos += i420.width
        }

        for (row in 0 until i420.height / 2) {
            for (col in 0 until i420.width / 2) {
                nv21[pos++] = v.get(row * i420.strideV + col)
                nv21[pos++] = u.get(row * i420.strideU + col)
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, i420.width, i420.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, i420.width, i420.height), 30, out)

        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val matrix = Matrix().apply {
            postRotate(frame.rotation.toFloat())
        }

        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val finalOut = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, finalOut)

        sendFrame(finalOut.toByteArray())
        i420.release()
    }

    fun stop() {
        capturer?.stopCapture()
    }
}