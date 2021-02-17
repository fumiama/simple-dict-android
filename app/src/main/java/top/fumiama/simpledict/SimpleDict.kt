package top.fumiama.simpledict

import android.util.Log
import java.lang.Thread.sleep

class SimpleDict(private val client: Client, private val pwd: String) {   //must run in thread
    private var dict = HashMap<String, String?>()
    val keys get() = dict.keys
    val values get() = dict.values
    //val size get() = dict.size
    private val raw: ByteArray?
        get() {
            initDict()
            client.sendMessage("cat")
            sleep(233)
            val re = client.receiveRawMessage()
            closeDict()
            return re
        }

    init {
        Thread{ fetchDict() }.start()
    }

    private fun initDict() {
        client.initConnect()
        client.sendMessage(pwd)
    }

    private fun closeDict() {
        client.sendMessage("quit")
        client.closeConnect()
    }

    private fun analyzeDictBlk(dictBlock: ByteArray) {
        Log.d("MySD", "Read block: ${dictBlock.decodeToString()}")
        val keyLen = dictBlock[63].toInt().let { if (it > 63) 63 else it }
        val dataEnd = 64 + dictBlock[127].toInt().let { if (it > 63) 63 else it }
        val key = dictBlock.copyOf(keyLen).decodeToString()
        val data = if (dataEnd > 64) dictBlock.copyOfRange(64, dataEnd).decodeToString() else null
        dict[key] = data
        Log.d("MySD", "Fetch $key=$data")
    }

    //fun filterKeys(predicate: (String) -> Boolean) = dict.filterKeys(predicate)
    fun filterValues(predicate: (String?) -> Boolean) = dict.filterValues(predicate)

    fun fetchDict() {
        val dictBlock = ByteArray(128)
        raw?.inputStream()?.let {
            var c = '1'
            while (!it.read().toChar().isDigit()) Log.d("MySD", "Skip banner.")
            while (c.isDigit()) {
                c = it.read().toChar()
                Log.d("MySD", "Skip digit $c.")
            }
            dictBlock[0] = c.toByte()
            if(it.read(dictBlock, 1, 127) == 127) {
                analyzeDictBlk(dictBlock)
                while (it.read(dictBlock, 0, 128) == 128) analyzeDictBlk(dictBlock)
            }
        }
    }

    /*fun keysWithPattern(pattern: String): MutableSet<String>{
        val re = mutableSetOf<String>()
        initDict()
        client.sendMessage("lst")
        sleep(233)
        client.receiveMessage()
        client.sendMessage(pattern)
        client.receiveMessage()?.substringBeforeLast('\n')?.split('\n')?.forEach {
            re.add(it)
        }
        closeDict()
        return re
    }

    fun getDirectly(key: String): String? {
        initDict()
        client.sendMessage("get")
        sleep(233)
        client.receiveMessage()
        client.sendMessage(key)
        val re = client.receiveMessage()
        closeDict()
        return re
    }*/

    operator fun get(key: String) = dict[key]

    operator fun set(key: String, value: String): String? {
        val p = dict[key]
        initDict()
        client.sendMessage("set")
        sleep(233)
        client.receiveMessage()
        client.sendMessage(key)
        client.receiveMessage()
        client.sendMessage(value)
        client.receiveMessage()
        closeDict()
        dict[key] = value
        return p
    }
}