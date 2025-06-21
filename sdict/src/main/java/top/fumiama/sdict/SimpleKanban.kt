/*
 * SimpleKanban.kt
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
package top.fumiama.sdict

import android.util.Log
import top.fumiama.sdict.io.Client

/**
 * A client-side utility class for interacting with a
 * [Simple Kanban Server](https://github.com/fumiama/simple-kanban).
 * This class is designed to fetch raw kanban data or version-specific string responses
 * from a server using a custom binary-text-mixed protocol. It must be executed in a separate thread
 * or coroutine due to network I/O operations.
 *
 * @param client A pre-configured [Client] instance used to manage the socket connection.
 * @param password A password string used for authentication when sending commands to the server.
 * Please note that the password is sent in cleartext without any encryption.
 */
class SimpleKanban(private val client: Client, private val password: String) {

    /**
     * Attempts to retrieve raw kanban data from the server.
     *
     * The client sends a specific command using the password and waits for a response,
     * expecting a 4-byte LE header to determine the full message length, then reads the full payload.
     *
     * This process is retried up to 3 times if an exception occurs during reading.
     *
     * @return A [ByteArray] containing the raw data if successful, or `null` if all attempts fail.
     */
    private val raw: ByteArray?
        get() {
            var times = 3
            var re: ByteArray
            var firstReceived: ByteArray
            do {
                re = byteArrayOf()
                if(client.initConnect()) {
                    client.sendMessage("${password}catquit")  // Send command to request raw data.
                    client.receiveRawMessage(33)          // Welcome to simple kanban server.
                    try {
                        firstReceived = client.receiveRawMessage(4)  // Read header to get length.
                        val length = convert2Int(firstReceived)
                        Log.d("SimpleKanban", "raw length: $length")
                        // Handle any additional bytes beyond the header in the same buffer.
                        if(firstReceived.size > 4)
                            re += firstReceived.copyOfRange(4, firstReceived.size)
                        // Read remaining bytes based on calculated total length.
                        re += client.receiveRawMessage(length - re.size, setProgress = true)
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    client.closeConnect()
                }
            } while (times-- > 0)
            return if(re.isEmpty()) null else re
        }

    /**
     * Converts a 4-byte little-endian byte array to an integer.
     *
     * The input is expected to be in little-endian format.
     *
     * @param buffer A 4-byte array containing the length in little-endian order.
     * @return The converted integer value.
     */
    private fun convert2Int(buffer: ByteArray) =
        (buffer[3].toInt() and 0xff shl 24) or
                (buffer[2].toInt() and 0xff shl 16) or
                (buffer[1].toInt() and 0xff shl 8) or
                (buffer[0].toInt() and 0xff)

    /**
     * Asynchronously fetches the raw kanban data and handles success or failure.
     *
     * This method is suspendable and should be called within a coroutine context.
     *
     * @param doOnLoadFailure Called if the fetch operation fails or receives no data.
     * @param doOnLoadSuccess Called with the received [ByteArray] if the fetch succeeds.
     */
    suspend fun fetch(
        doOnLoadFailure: suspend () -> Unit,
        doOnLoadSuccess: suspend (data: ByteArray) -> Unit
    ) {
        raw?.let { doOnLoadSuccess(it) } ?: doOnLoadFailure()
    }

    /**
     * Requests a specific kanban version string from the server.
     *
     * The client sends a versioned request using the password and receives a response.
     * The response may be a 4-byte "null" message or a length-prefixed string.
     * In case of errors or connection failure, `"null"` is returned.
     *
     * @param version An integer identifying local kanban board version.
     * @return The corresponding remote kanban data as a [String], or `"null"` if not found or failed.
     */
    operator fun get(version: Int): String =
        if(client.initConnect()) {
            client.sendMessage("${password}get${version}quit")  // Send version-specific request.
            client.receiveRawMessage(36)  // Welcome to simple kanban server. get
            val r = try {
                val firstReceive = client.receiveRawMessage(4)
                if(firstReceive.decodeToString() == "null") "null"
                else {
                    val length = convert2Int(firstReceive)
                    Log.d("SimpleKanban", "get length: $length")
                    var re = byteArrayOf()
                    if(firstReceive.size > 4)
                        re += firstReceive.copyOfRange(4, firstReceive.size)
                    re += client.receiveRawMessage(length - re.size)
                    if(re.isNotEmpty()) re.decodeToString() else "null"
                }
            } catch (e: Exception){
                e.printStackTrace()
                "null"
            }
            client.closeConnect()
            r
        } else "null"
}