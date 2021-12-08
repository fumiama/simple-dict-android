package top.fumiama.simpledict

import android.util.Log
import java.io.File
import java.lang.Thread.sleep
import java.security.MessageDigest

class SimpleDict(private val client: Client, private val pwd: String, private val externalCacheDir: File?, private val spwd: String?) {   //must run in thread
    private var dict = HashMap<String, String?>()
    val size get() = dict.size
    val keys get() = dict.keys
    var latestKeys = arrayOf<String>()
    private val md5File = File(externalCacheDir, "md5")
    private val dspFile = File(externalCacheDir, "dsp")
    private val raw: ByteArray?
        get() {
            var times = 3
            var re: ByteArray? = null
            var exit = false
            while(times-- > 0 && !exit) {
                if(initDict()) {
                    client.sendMessage("cat")
                    try {
                        var length = ""
                        var c = client.read()
                        while (c?.isDigit() == true) {
                            length += c
                            c = client.read()
                        }
                        Log.d("MySD", "length: $length")
                        re = client.receiveRawMessage(length.toInt())
                        exit = true
                    } catch (e: Exception){
                        e.printStackTrace()
                    }
                    closeDict()
                } else sleep(233)
            }
            return re
        }

    private fun initDict(): Boolean {
        if(client.initConnect()){
            if(client.sendMessage(pwd)) {
                return client.receiveRawMessage(31).size == 31
            }
        }
        return false
    }

    private fun closeDict(): Boolean {
        client.sendMessage("quit")
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
            client.sendMessage("md5".toByteArray() + md5)
            client.receiveRawMessage(3)     //md5
            val re = client.receiveMessage(4)
            closeDict()
            Log.d("MySD", "Check md5: $re")
            re == "nequ"
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
        if(spwd == null) return false
        else if(initDict()) {
            val delPass = "del$spwd"
            client.sendMessage(delPass)
            client.receiveRawMessage(delPass.length)
            client.sendMessage(key)
            if(client.receiveMessage(4) == "succ") {
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
        if(spwd == null) return false
        else if(initDict()) {
            val delPass = "del$spwd"
            client.sendMessage(delPass)
            client.receiveRawMessage(delPass.length)
            client.sendMessage(key)
            if(client.receiveMessage(4) == "succ") {
                return closeDict()
            } else closeDict()
        }
        return false
    }

    operator fun get(key: String) = dict[key]

    fun set(key: String, value: String): Boolean {
        //if(spwd == null) return false
        val contain = dict.containsKey(key)
        if((contain && sendel(key.toByteArray())) || !contain) {
            if(initDict()) {
                val setPass = "set$spwd"
                client.sendMessage(setPass)
                client.receiveRawMessage(setPass.length)
                client.sendMessage(key)
                if(client.receiveMessage(4) == "data") {
                    client.sendMessage(value)
                    client.receiveMessage(4)
                    if(closeDict()) dict[key] = value
                    return true
                } else closeDict()
            }
            return false
        } else return false
    }
}