package com.example.clientapp.network

import io.socket.client.IO
import io.socket.client.Socket

class SocketManager {

    private var socket: Socket? = null

    fun connect(url: String, onConnect: () -> Unit) {
        socket = IO.socket(url)

        socket?.on(Socket.EVENT_CONNECT) {
            onConnect()
        }

        socket?.connect()
    }

    fun emitFrame(data: ByteArray) {
        socket?.emit("frame", data)
    }

    fun emitAudio(data: ByteArray) {
        socket?.emit("audio", data)
    }

    fun getSocket(): Socket? = socket

    fun disconnect() {
        socket?.disconnect()
    }
}