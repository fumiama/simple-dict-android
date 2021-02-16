package top.fumiama.simpledict

import java.lang.Thread.sleep

class SimpleDict(private val client: Client): HashMap<String, String>() {   //must run in thread
    var pattern: CharSequence = "a"
    private var isInit = true
    override val keys: MutableSet<String>
        get() {
            val re = mutableSetOf<String>()
            if(isInit) {
                isInit = false
                return re
            } else {
                client.initConnect()
                client.sendMessage("lst")
                sleep(233)
                client.receiveMessage()
                client.sendMessage(pattern)
                client.receiveMessage()?.substringBeforeLast('\n')?.split('\n')?.forEach {
                    re.add(it)
                }
                client.sendMessage("quit")
                client.closeConnect()
                return re
            }
        }

    override fun get(key: String): String? {
        client.initConnect()
        client.sendMessage("get")
        sleep(233)
        client.receiveMessage()
        client.sendMessage(key)
        val re = client.receiveMessage()
        client.sendMessage("quit")
        client.closeConnect()
        return re
    }

    override fun put(key: String, value: String): String? {
        val p = this[key]
        client.initConnect()
        client.sendMessage("set")
        sleep(233)
        client.receiveMessage()
        client.sendMessage(key)
        client.receiveMessage()
        client.sendMessage(value)
        client.receiveMessage()
        client.sendMessage("quit")
        client.closeConnect()
        return p
    }
}