/*
 * SimpleDict.kt
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

import java.io.File
import java.lang.Thread.sleep
import java.security.MessageDigest

import android.util.Log

import top.fumiama.sdict.io.Client
import top.fumiama.sdict.protocol.CmdPacket
import top.fumiama.sdict.protocol.SimpleProtobuf
import top.fumiama.sdict.protocol.Tea

/**
 * A high-level dictionary manager that communicates with a remote [Simple Dict Server](https://github.com/fumiama/simple-dict)
 * over a custom protocol via [Client].
 *
 * This class supports fetching, storing, deleting, and checking remote dictionary entries. It maintains a local cache,
 * synchronizes updates, and verifies integrity using MD5.
 *
 * @param client the network client used to communicate with the remote dictionary service
 * @param password used to encrypt/decrypt data (query operations)
 * @param externalCacheDir directory used to store persistent cache files
 * @param setPassword optional password used for modifying the dictionary (set/delete)
 *
 * **NOTE:** All operations are blocking and must be run in a background thread.
 */
class SimpleDict(
    private val client: Client,
    password: String,
    private val externalCacheDir: File?,
    setPassword: String?
) {
    /** In-memory map of the dictionary data. */
    private var dict = HashMap<String, String?>()

    /** Number of keys in the dictionary. */
    val size get() = dict.size

    /** All keys in the dictionary. */
    val keys get() = dict.keys

    /** Keys by last-update-time order. */
    var latestKeys = arrayOf<String>()

    /** Current TEA encryption sequence number. */
    private var seq: Byte = 0

    /** TEA cipher for read-only operations. */
    private val teaPassword = Tea(password.toByteArray())

    /** TEA cipher for modification operations. May be null if not permitted. */
    private val teaSetPassword = setPassword?.let { Tea(it.toByteArray()) }

    /** Cache file storing the latest simple-protobuf data. */
    private val dspFile = File(externalCacheDir, "dsp")

    /** Cache file storing the MD5 of the simple-protobuf data snapshot. */
    private val md5File = File(externalCacheDir, "md5")

    /** Dummy payload used when sending control packets. */
    private val filler = "fill".toByteArray()

    /**
     * Retrieves and decrypts the dictionary snapshot from the server.
     * Retries up to 3 times on failure. Sequence is incremented by 2 if successful.
     */
    private val raw: ByteArray?
        get() {
            var times = 3
            var re: ByteArray? = null
            var exit = false
            while (times-- > 0 && !exit) {
                if (initDict()) {
                    client.sendMessage(
                        CmdPacket(CmdPacket.CMD_CAT, filler, teaPassword).encrypt(seq)
                    )
                    try {
                        var length = ""
                        var c = client.read()
                        while (c?.isDigit() == true) {
                            length += c
                            c = client.read()
                        }
                        Log.d("SimpleDict", "length: $length")
                        re = teaPassword.decryptLittleEndian(
                            client.receiveRawMessage(length.toInt()),
                            (seq + 1).toByte()
                        )
                        if (re != null) seq = (seq + 2).toByte()
                        exit = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    closeDict()
                } else sleep(233)
            }
            return re
        }

    /**
     * Receives and verifies an ack packet. If valid, increments [seq] and returns decrypted payload.
     */
    private val ack: String?
        get() {
            var re = client.receiveRawMessage(1 + 1 + 16)
            re += client.receiveRawMessage(re[1].toInt())
            val r = CmdPacket(re, teaPassword).decrypt(seq)
            if (r != null) seq++
            Log.d("SimpleDict", "ack: ${r?.decodeToString()}")
            return r?.decodeToString()
        }

    /** Establishes connection to the remote dictionary service. */
    private fun initDict() = client.initConnect()

    /**
     * Sends termination packet and closes the connection.
     * Resets [seq] to 0.
     */
    private fun closeDict(): Boolean {
        client.sendMessage(
            CmdPacket(CmdPacket.CMD_END, filler, teaPassword).encrypt(seq)
        )
        seq = 0
        return client.closeConnect()
    }
 
    /**
     * Saves the given dictionary data to cache, along with its MD5 hash.
     *
     * @param data the dictionary data to persist
     */
    private fun saveDict(data: ByteArray) {
        if (externalCacheDir?.exists() != true) externalCacheDir?.mkdirs()
        if (externalCacheDir?.exists() == true) {
            dspFile.writeBytes(data)
            md5File.writeBytes(MessageDigest.getInstance("md5").digest(data))
        }
    }

    /**
     * Compares local MD5 against server MD5 to determine whether new data is available.
     *
     * @param md5 the locally stored MD5
     * @return true if server indicates the data is newer
     */
    private fun hasNewItem(md5: ByteArray): Boolean =
        if (initDict()) {
            client.sendMessage(
                CmdPacket(CmdPacket.CMD_MD5, md5, teaPassword).encrypt(seq++)
            )
            val cp = ack
            Log.d("SimpleDict", "check md5: $cp")
            closeDict()
            cp == "nequ"
        } else false

    /**
     * Parses raw dictionary entries and stores them in memory.
     *
     * @param dictData the raw protobuf dictionary byte array
     * @param saveDict whether to persist the dictionary locally
     */
    private fun analyzeDict(dictData: ByteArray, saveDict: Boolean) {
        SimpleProtobuf.getDictArray(dictData).forEach { d ->
            d?.apply {
                val k = key.decodeToString()
                if (saveDict) {
                    if (k.toByteArray().contentEquals(key)) {
                        dict[k] = data.decodeToString()
                        latestKeys += k
                    } else {
                        sendDel(key) // purge invalid
                    }
                } else if (!dict.containsKey(k)) {
                    dict[k] = data.decodeToString()
                    latestKeys += k
                } else {
                    sendDel(key) // deduplicate
                }
            }
        }
        if (saveDict) saveDict(dictData)
    }

    /**
     * Filters current dictionary values by a predicate.
     *
     * @param predicate the predicate to apply
     * @return a map of matching entries
     */
    fun filterValues(predicate: (String?) -> Boolean) = dict.filterValues(predicate)

    /**
     * Loads the dictionary from cache or server, applies update logic, and calls user-defined callbacks.
     *
     * @param doOnLoadFailure called if loading fails
     * @param doOnLoadSuccess called if loading succeeds
     * @param doCommon always called after attempt
     */
    suspend fun fetch(
        doOnLoadFailure: suspend () -> Unit,
        doOnLoadSuccess: suspend () -> Unit,
        doCommon: (suspend () -> Unit)? = null
    ) {
        val noChange = md5File.exists() && dspFile.exists() &&
                !hasNewItem(md5File.readBytes())
        val data = if (noChange) dspFile.readBytes() else raw
        dict.clear()
        latestKeys = arrayOf()
        if (data == null) doOnLoadFailure()
        else {
            analyzeDict(data, !noChange)
            doOnLoadSuccess()
        }
        doCommon?.invoke()
    }

    /**
     * Deletes an entry from the dictionary both remotely and locally.
     *
     * @param key the key to delete
     * @return true if successful
     */
    fun del(key: String): Boolean {
        if (teaSetPassword == null) return false
        else if (initDict()) {
            client.sendMessage(
                CmdPacket(CmdPacket.CMD_DEL, key.toByteArray(), teaSetPassword).encrypt(seq++)
            )
            if (ack == "succ") {
                if (closeDict()) {
                    dict.remove(key)
                    val end = latestKeys.size - 1
                    if (end > 0) latestKeys = latestKeys.let { oldArr ->
                        var index = -1
                        Array(end) {
                            if (oldArr[it] == key) index = it
                            return@Array if (index < 0 || (index > 0 && it < index)) oldArr[it] else oldArr[it + 1]
                        }
                    }
                    return true
                }
            } else closeDict()
        }
        return false
    }

    /**
     * Sends a deletion request for the given key (as bytes), without updating local state.
     *
     * @param key raw byte representation of the key
     * @return true if deletion and disconnect succeed
     */
    private fun sendDel(key: ByteArray): Boolean {
        if (teaSetPassword == null) return false
        else if (initDict()) {
            client.sendMessage(
                CmdPacket(CmdPacket.CMD_DEL, key, teaSetPassword).encrypt(seq++)
            )
            if (ack == "succ") {
                return closeDict()
            } else closeDict()
        }
        return false
    }

    /**
     * Gets the value of a key.
     *
     * @param key the dictionary key
     * @return the value or null
     */
    operator fun get(key: String) = dict[key]

    /**
     * Sets or updates a key-value pair on the remote server.
     * Will delete existing key before inserting new one.
     *
     * @param key the dictionary key
     * @param value the string value to set
     * @return true if the operation succeeds
     */
    fun set(key: String, value: String): Boolean {
        if (teaSetPassword == null) return false
        val contain = dict.containsKey(key)
        if ((contain && sendDel(key.toByteArray())) || !contain) {
            if (initDict()) {
                client.sendMessage(
                    CmdPacket(CmdPacket.CMD_SET, key.toByteArray(), teaSetPassword).encrypt(seq++)
                )
                if (ack == "data") {
                    client.sendMessage(
                        CmdPacket(CmdPacket.CMD_DAT, value.toByteArray(), teaSetPassword).encrypt(seq++)
                    )
                    val s = ack == "succ"
                    if (s) dict[key] = value
                    return closeDict() && s
                } else closeDict()
            }
            return false
        } else return false
    }
}