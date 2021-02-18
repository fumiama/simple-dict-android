package top.fumiama.simpledict

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class Client(private val ip: String, private val port: Int) {
    //普通数据交互接口
    private var sc: Socket? = null

    //普通交互流
    private var dout: OutputStream? = null
    private var din: InputStream? = null

    //已连接标记
    private val isConnect get() = sc != null && din != null && dout != null

    /**
     * 初始化普通交互连接
     */
    fun initConnect(depth: Int = 0){
        if(depth > 3) Log.d("MyC", "connect server failed after $depth tries")
        else try {
            sc = Socket(ip, port) //通过socket连接服务器
            din = sc?.getInputStream()  //获取输入流并转换为StreamReader，约定编码格式
            dout = sc?.getOutputStream()    //获取输出流
            sc?.soTimeout = 2333  //设置连接超时限制
            if (isConnect) Log.d("MyC", "connect server successful")
            else {
                Log.d("MyC", "connect server failed, now retry...")
                initConnect(depth + 1)
            }
        } catch (e: IOException) {      //获取输入输出流是可能报IOException的，所以必须try-catch
            e.printStackTrace()
        }
    }

    /**
     * 发送数据至服务器
     * @param message 要发送至服务器的字符串
     */
    fun sendMessage(message: CharSequence?) {
        try {
            if (isConnect) {
                if (message != null) {        //判断输出流或者消息是否为空，为空的话会产生nullpoint错误
                    dout?.write(message.toString().toByteArray())
                    dout?.flush()
                    Log.d("MyC", "Send msg: $message")
                } else Log.d("MyC", "The message to be sent is empty")
                Log.d("MyC", "send message succeed")
            } else Log.d("MyC", "send message failed: no connect")
        } catch (e: IOException) {
            Log.d("MyC", "send message failed: crash")
            e.printStackTrace()
        }
    }

    fun receiveRawMessage(totalSize: Int = -1, bufferSize: Int = 4096) : ByteArray {
        var re = byteArrayOf()
        try {
            if (isConnect) {
                Log.d("MyC", "开始接收服务端信息")
                val inMessage = ByteArray(bufferSize)     //设置接受缓冲，避免接受数据过长占用过多内存
                var a: Int
                do {
                    a = din?.read(inMessage)?:0 //a存储返回消息的长度
                    re += inMessage.copyOf(a)
                    Log.d("MyC", "reply length:$a: ${re.decodeToString()}")
                } while (a == bufferSize || totalSize > re.size)
            } else Log.d("MyC", "no connect to receive message")
        } catch (e: IOException) {
            Log.d("MyC", "receive message failed")
            e.printStackTrace()
        }
        return re
    }

    fun receiveMessage() = receiveRawMessage().decodeToString()

    /**
     * 关闭连接
     */
    fun closeConnect() {
        try {
            din?.close()
            dout?.close()
            sc?.close()
            sc = null
            din = null
            dout = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.d("MyC", "关闭连接")
    }
}
