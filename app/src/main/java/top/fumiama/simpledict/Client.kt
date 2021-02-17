package top.fumiama.simpledict

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class Client(val ip: String, val port: Int) {
    //普通数据交互接口
    private var sc: Socket? = null

    //普通交互流
    private var dout: OutputStream? = null
    private var din: InputStream? = null

    //已连接标记
    var isConnect = false

    /**
     * 初始化普通交互连接
     */
    fun initConnect(){
        try {
            sc = Socket(ip, port) //通过socket连接服务器
            din = sc?.getInputStream()  //获取输入流并转换为StreamReader，约定编码格式
            dout = sc?.getOutputStream()    //获取输出流
            sc?.soTimeout = 10000  //设置连接超时限制
            if (sc != null && din != null && dout != null) {    //判断一下是否都连上，避免NullPointException
                isConnect = true
                Log.d("MyC", "connect server successful")
            } else {
                Log.d("MyC", "connect server failed,now retry...")
                initConnect()
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
                if (dout != null && message != null) {        //判断输出流或者消息是否为空，为空的话会产生nullpoint错误
                    dout!!.write(message.toString().toByteArray())
                    dout!!.flush()
                } else Log.d("MyC", "The message to be sent is empty or have no connect")
                Log.d("MyC", "send message succeed")
            } else Log.d("MyC", "no connect to send message")
        } catch (e: IOException) {
            Log.d("MyC", "send message to cilent failed")
            e.printStackTrace()
        }
    }

    fun receiveRawMessage() : ByteArray {
        var re = byteArrayOf()
        try {
            if (isConnect) {
                Log.d("MyC", "开始接收服务端信息")
                val inMessage = ByteArray(4096)     //设置接受缓冲，避免接受数据过长占用过多内存
                var a: Int
                do {
                    a = din?.read(inMessage)?:0 //a存储返回消息的长度
                    Log.d("MyC", "reply length:$a: ${inMessage.decodeToString()}")
                    re += inMessage.copyOf(a)
                } while (a == 4096)
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
        } catch (e: IOException) {
            e.printStackTrace()
        }
        isConnect = false
        Log.d("MyC", "关闭连接")
    }
}
