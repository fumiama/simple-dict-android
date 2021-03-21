package top.fumiama.simpledict

import android.util.Log
import java.lang.Thread.sleep

class SimpleDict(private val client: Client, private val pwd: String) {   //must run in thread
    private var dict = HashMap<String, String?>()
    val size get() = dict.size
    val keys get() = dict.keys
    var latestKeys = arrayOf<String>()
    private val raw: ByteArray
        get() {
            var times = 3
            var re: ByteArray
            var firstRecv: ByteArray
            do {
                re = byteArrayOf()
                if(initDict()) {
                    sendMessageWithDelay("cat", 2333)
                    try {
                        firstRecv = client.receiveRawMessage()
                        val firstStr = firstRecv.decodeToString()
                        var length = ""
                        for ((i, c) in firstStr.withIndex()) {
                            if(c.isDigit()) length += c
                            else {
                                if(i + 1 < firstRecv.size) re = firstRecv.copyOfRange(i, firstRecv.size)
                                break
                            }
                        }
                        re += client.receiveRawMessage(length.toInt() - re.size)
                        break
                    } catch (e: Exception){
                        e.printStackTrace()
                    }
                    closeDict()
                }
            } while (times-- > 0)
            return re
        }
    
    private fun sendMessageWithDelay(msg: CharSequence, delay: Long = 233) = Thread{
        client.sendMessage(msg)
        sleep(delay)
    }.start()

    private fun initDict(): Boolean {
        if(client.initConnect()){
            if(client.sendMessage(pwd)) {
                client.receiveRawMessage()
                sleep(233)
                return true
            }
        }
        return false
    }

    private fun closeDict(): Boolean {
        if(client.sendMessage("quit")) {
            if (client.closeConnect()) return true
        }
        return false
    }

    private fun analyzeDictBlk(dictBlock: ByteArray) {
        Log.d("MySD", "Read block: ${dictBlock.decodeToString()}")
        val keyLen = dictBlock[63].toInt().let { if (it > 63) 63 else it }
        val dataEnd = 64 + dictBlock[127].toInt().let { if (it > 63) 63 else it }
        val key = dictBlock.copyOf(keyLen).decodeToString()
        val data = if (dataEnd > 64) dictBlock.copyOfRange(64, dataEnd).decodeToString() else null
        if(key != "") {
            dict[key] = data
            latestKeys += key
        }
        Log.d("MySD", "Fetch $key=$data")
    }

    fun filterValues(predicate: (String?) -> Boolean) = dict.filterValues(predicate)

    fun fetchDict(doOnLoadSuccess: ()->Unit = {
        Log.d("MySD", "Fetch dict success")
    }) {
        val dictBlock = ByteArray(128)
        dict = hashMapOf()
        latestKeys = arrayOf()
        raw.inputStream().let {
            while (it.read(dictBlock, 0, 128) == 128) analyzeDictBlk(dictBlock)
            doOnLoadSuccess()
        }
    }

    operator fun minusAssign(key: String) {
        if(initDict()) {
            sendMessageWithDelay("del")
            client.receiveMessage()
            sendMessageWithDelay(key)
            client.receiveMessage()
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
            }
        }
    }

    operator fun get(key: String) = dict[key]

    operator fun set(key: String, value: String): String? {
        val p = dict[key]
        if(initDict()) {
            sendMessageWithDelay("set")
            client.receiveMessage()
            sendMessageWithDelay(key)
            client.receiveMessage()
            sendMessageWithDelay(value)
            client.receiveMessage()
            if(closeDict()) dict[key] = value
        }
        return p
    }
}