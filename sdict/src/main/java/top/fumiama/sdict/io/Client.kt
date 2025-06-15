/*
 * Client.kt
 *
 * Copyright (C) 2025 Minamoto Fumiama
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */
package top.fumiama.sdict.io

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Thread.sleep
import java.net.Socket

import android.util.Log

import top.fumiama.sdict.utils.Utils.toHexStr

/**
 * A simple TCP client that connects to a server, sends/receives messages, and optionally reports progress.
 *
 * @param ip the server IP address
 * @param port the server port
 */
class Client(private val ip: String, private val port: Int) {
    private var sc: Socket? = null
    private var dout: OutputStream? = null
    private var din: InputStream? = null

    private val isConnect get() = sc != null && din != null && dout != null

    /**
     * Attempts to establish a TCP connection to the server.
     * Retries up to 3 times before giving up.
     *
     * @param depth current retry count, no need to fill this value when call it
     * @return true if connection is successful, false otherwise
     */
    fun initConnect(depth: Int = 0): Boolean {
        if (depth > 3) {
            Log.d("Client", "connect server failed after $depth tries")
        } else try {
            sc = Socket(ip, port)
            din = sc?.getInputStream()
            dout = sc?.getOutputStream()
            sc?.soTimeout = 10000
            return if (isConnect) {
                Log.d("Client", "connect server successful")
                true
            } else {
                Log.d("Client", "connect server failed, now retry...")
                initConnect(depth + 1)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Sends a UTF-8 encoded string message to the server.
     *
     * @param message the string to send
     * @return true if the message was sent successfully, false otherwise
     */
    fun sendMessage(message: String?): Boolean = sendMessage(message?.toByteArray())

    /**
     * Sends a byte array message to the server.
     *
     * @param message the raw byte array to send
     * @return true if the message was sent successfully, false otherwise
     */
    fun sendMessage(message: ByteArray?): Boolean {
        try {
            if (isConnect) {
                if (message != null) {
                    dout?.write(message)
                    dout?.flush()
                    Log.d("Client", "send msg: ${toHexStr(message)}")
                    return true
                } else {
                    Log.d("Client", "skip empty message")
                }
            } else {
                Log.d("Client", "send message failed: no connect")
            }
        } catch (e: IOException) {
            Log.d("Client", "send message failed: crash")
            e.printStackTrace()
        }
        return false
    }

    /**
     * Reads one character from the input stream.
     *
     * @return the character read, or null if disconnected
     */
    fun read(): Char? = din?.read()?.toChar()

    private var buffer = ByteArrayQueue()
    private val receiveBuffer = ByteArray(65536)

    /**
     * Receives a raw byte array of the specified total size from the server.
     *
     * @param totalSize expected size in bytes
     * @param setProgress whether to report progress via [progress] listener
     * @return the byte array received, or an empty array on failure
     */
    fun receiveRawMessage(totalSize: Int, setProgress: Boolean = false): ByteArray {
        if (totalSize == buffer.size) return buffer.drain()
        try {
            if (isConnect) {
                Log.d("Client", "Start receiving from server")
                var prevP = 0
                while (totalSize > buffer.size) {
                    val count = din?.read(receiveBuffer) ?: 0
                    if (count > 0) {
                        buffer += receiveBuffer.copyOfRange(0, count)
                        Log.d("Client", "reply length: $count")
                        if (setProgress && totalSize > 0) {
                            val p = 100 * buffer.size / totalSize
                            if (prevP != p) {
                                progress?.notify(p)
                                prevP = p
                            }
                        }
                    } else {
                        sleep(10)
                    }
                }
            } else {
                Log.d("Client", "no connect to receive message")
            }
        } catch (e: IOException) {
            Log.d("Client", "receive message failed")
            e.printStackTrace()
        }
        return if (totalSize > 0) buffer.dequeue(totalSize) ?: byteArrayOf() else buffer.drain()
    }

    /**
     * Receives a message from the server and decodes it as UTF-8 text.
     *
     * @param totalSize expected size in bytes
     * @return the decoded string
     */
    fun receiveMessage(totalSize: Int): String = receiveRawMessage(totalSize).decodeToString()

    /**
     * Closes the connection and all related resources.
     *
     * @return true if closed successfully, false otherwise
     */
    fun closeConnect(): Boolean = try {
        din?.close()
        dout?.close()
        sc?.close()
        sc = null
        din = null
        dout = null
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }

    /**
     * Optional interface for reporting progress while receiving messages.
     */
    var progress: Progress? = null

    interface Progress {
        /**
         * Called to report percentage of received data.
         *
         * @param progressPercentage an integer between 0 and 100
         */
        fun notify(progressPercentage: Int)
    }
}
