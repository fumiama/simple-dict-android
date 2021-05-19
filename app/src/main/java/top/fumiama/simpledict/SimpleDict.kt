package top.fumiama.simpledict

import android.util.Log

class SimpleDict(private val client: Client, private val pwd: String, private val spwd: String?) {   //must run in thread
    private var dict = HashMap<String, String?>()
    val size get() = dict.size
    val keys get() = dict.keys
    var latestKeys = arrayOf<String>()
    private val raw: ByteArray?
        get() {
            var times = 3
            var re: ByteArray
            var firstRecv: ByteArray
            do {
                re = byteArrayOf()
                if(initDict()) {
                    sendMessage("cat")
                    try {
                        firstRecv = client.receiveRawMessage()
                        val firstStr = firstRecv.decodeToString()
                        var length = ""
                        Log.d("MySD", "first str: $firstStr")
                        for ((i, c) in firstStr.withIndex()) {
                            if(c.isDigit()) length += c
                            else {
                                if(i + 2 < firstRecv.size) re = firstRecv.copyOfRange(i + 1, firstRecv.size)
                                break
                            }
                        }
                        Log.d("MySD", "length: $length")
                        re += client.receiveRawMessage(length.toInt() - re.size)
                        closeDict()
                        break
                    } catch (e: Exception){
                        e.printStackTrace()
                        closeDict()
                    }
                }
            } while (times-- > 0)
            return if(re.isEmpty()) null else re
        }
    
    private fun sendMessage(msg: CharSequence) = client.sendMessage(msg)

    private fun initDict(): Boolean {
        if(client.initConnect()){
            if(client.sendMessage(pwd)) {
                client.receiveRawMessage(31)
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

    fun filterValues(predicate: (String?) -> Boolean) = dict.filterValues(predicate)

    fun fetchDict(doOnLoadFailure: ()->Unit = {
        Log.d("MySD", "Fetch dict success")
    }, doOnLoadSuccess: ()->Unit = {
        Log.d("MySD", "Fetch dict success")
    }, doCommon: (() -> Unit)? = null) {
        dict = hashMapOf()
        latestKeys = arrayOf()
        raw?.let {
            SimpleProtobuf.getDictArray(it).forEach { d ->
                d?.apply {
                    val k = key.decodeToString()
                    dict[k] = data.decodeToString()
                    latestKeys += k
                }
            }
            doOnLoadSuccess()
        }?:doOnLoadFailure()
        doCommon?.let { it() }
    }

    fun del(key: String): Boolean {
        if(spwd == null) return false
        else if(initDict()) {
            val delPass = "del$spwd"
            sendMessage(delPass)
            client.receiveRawMessage(delPass.length)
            sendMessage(key)
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

    operator fun get(key: String) = dict[key]

    fun set(key: String, value: String): Boolean {
        //if(spwd == null) return false
        val contain = dict.containsKey(key)
        if((contain && del(key)) || !contain) {
            if(initDict()) {
                val setPass = "set$spwd"
                sendMessage(setPass)
                client.receiveRawMessage(setPass.length)
                sendMessage(key)
                if(client.receiveMessage(4) == "data") {
                    sendMessage(value)
                    client.receiveMessage(4)
                    if(closeDict()) dict[key] = value
                    return true
                } else closeDict()
            }
            return false
        } else return false
    }
}