package top.fumiama.simpledict

import android.util.Log
import java.io.File
import java.lang.Thread.sleep
import java.security.MessageDigest

class SimpleDict(private val client: Client, pwd: String, private val externalCacheDir: File?, spwd: String?) {   //must run in thread
    private var dict = HashMap<String, String?>()
    val size get() = dict.size
    val keys get() = dict.keys
    var latestKeys = arrayOf<String>()
    private var seq: Byte = 0
    private val ptea = Tea(pwd.toByteArray())
    private val stea = spwd?.let { Tea(it.toByteArray()) }
    private val md5File = File(externalCacheDir, "md5")
    private val dspFile = File(externalCacheDir, "dsp")
    private val filler = "fill".toByteArray()
    private val raw: ByteArray?
        get() {
            var times = 3
            var re: ByteArray? = null
            var exit = false
            while(times-- > 0 && !exit) {
                if(initDict()) {
                    client.sendMessage(CmdPacket(CmdPacket.CMDCAT, filler, ptea).encrypt(seq))
                    try {
                        var length = ""
                        var c = client.read()
                        while (c?.isDigit() == true) {
                            length += c
                            c = client.read()
                        }
                        Log.d("MySD", "length: $length")
                        re = ptea.decryptLittleEndian(client.receiveRawMessage(length.toInt()), (seq+1).toByte())
                        if(re != null) seq = (seq + 2).toByte()
                        exit = true
                    } catch (e: Exception){
                        e.printStackTrace()
                    }
                    closeDict()
                } else sleep(233)
            }
            return re
        }
    private val ack: ByteArray?
        get() {
            var re = client.receiveRawMessage(1+1+16)
            re += client.receiveRawMessage(re[1].toInt())
            val r = CmdPacket(re, ptea).decrypt(seq)
            if (r != null) seq++
            Log.d("MySD", "ack: ${r?.decodeToString()}")
            return r
        }

    private fun initDict() = client.initConnect()

    private fun closeDict(): Boolean {
        client.sendMessage(CmdPacket(CmdPacket.CMDEND, filler, ptea).encrypt(seq))
        seq = 0
        return client.closeConnect()
    }

    private fun saveDict(data: ByteArray) {
        if(externalCacheDir?.exists() != true) externalCacheDir?.mkdirs()
        if(externalCacheDir?.exists() == true) {
            dspFile.writeBytes(data)
            md5File.writeBytes(MessageDigest.getInstance("md5").digest(data))
        }
    }
    
    private fun hasNewItem(md5: ByteArray): Boolean =
        if(initDict()) {
            client.sendMessage(CmdPacket(CmdPacket.CMDMD5, md5, ptea).encrypt(seq++))
            val cp = ack
            Log.d("MySD", "Check md5: ${cp?.decodeToString()}")
            closeDict()
            cp?.decodeToString() == "nequ"
        } else false

    private fun analyzeDict(datas: ByteArray, saveDict: Boolean) {
        SimpleProtobuf.getDictArray(datas).forEach { d ->
            d?.apply {
                val k = key.decodeToString()
                if(saveDict) {
                    if(k.toByteArray().contentEquals(key)) {
                        dict[k] = data.decodeToString()
                        latestKeys += k
                    } else {
                        sendel(key) // 去错
                    }
                } else if(!dict.containsKey(k)){
                    dict[k] = data.decodeToString()
                    latestKeys += k
                } else {
                    sendel(key) // 去重
                }
            }
        }
        if(saveDict) saveDict(datas)
    }

    fun filterValues(predicate: (String?) -> Boolean) = dict.filterValues(predicate)

    fun fetchDict(doOnLoadFailure: ()->Unit, doOnLoadSuccess: ()->Unit, doCommon: (() -> Unit)? = null) {
        val noChange = md5File.exists() && dspFile.exists() && !hasNewItem(md5File.readBytes())
        val data = if(noChange) dspFile.readBytes() else raw
        dict.clear()
        latestKeys = arrayOf()
        if(data == null) doOnLoadFailure()
        else {
            analyzeDict(data, !noChange)
            doOnLoadSuccess()
        }
        doCommon?.let { it() }
    }

    fun del(key: String): Boolean {
        if(stea == null) return false
        else if(initDict()) {
            client.sendMessage(CmdPacket(CmdPacket.CMDDEL, key.toByteArray(), stea).encrypt(seq++))
            if(ack?.decodeToString() == "succ") {
                if(closeDict()) {
                    dict.remove(key)
                    val end = latestKeys.size-1
                    if(end > 0) latestKeys = latestKeys.let { oldArr ->
                        var index = -1
                        Array(end) {
                            if(oldArr[it] == key) index = it
                            return@Array if(index < 0 || (index > 0 && it < index)) oldArr[it] else oldArr[it+1]
                        }
                    }
                    return true
                }
            } else closeDict()
        }
        return false
    }

    private fun sendel(key: ByteArray): Boolean {
        if(stea == null) return false
        else if(initDict()) {
            client.sendMessage(CmdPacket(CmdPacket.CMDDEL, key, stea).encrypt(seq++))
            if(ack?.decodeToString() == "succ") {
                return closeDict()
            } else closeDict()
        }
        return false
    }

    operator fun get(key: String) = dict[key]

    fun set(key: String, value: String): Boolean {
        //if(spwd == null) return false
        if(stea == null) return false
        val contain = dict.containsKey(key)
        if((contain && sendel(key.toByteArray())) || !contain) {
            if(initDict()) {
                client.sendMessage(CmdPacket(CmdPacket.CMDSET, key.toByteArray(), stea).encrypt(seq++))
                if(ack?.decodeToString() == "data") {
                    client.sendMessage(CmdPacket(CmdPacket.CMDDAT, value.toByteArray(), stea).encrypt(seq++))
                    val s = ack?.decodeToString() == "succ"
                    if(s) dict[key] = value
                    return closeDict() && s
                } else closeDict()
            }
            return false
        } else return false
    }
}