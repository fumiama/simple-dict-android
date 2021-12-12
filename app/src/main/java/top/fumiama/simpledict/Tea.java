package top.fumiama.simpledict;

import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class Tea {
    private final int[] t = new int[4];
    private final Random r;
    public Tea(@NonNull byte[] tea) {
        byte[] tea16 = new byte[16];
        System.arraycopy(tea, 0, tea16, 0, Math.min(tea.length, 15));
        tea16[15] = 0;
        ByteBuffer bf = ByteBuffer.wrap(tea16).order(ByteOrder.LITTLE_ENDIAN);
        t[0] = bf.getInt(0);
        t[1] = bf.getInt(4);
        t[2] = bf.getInt(8);
        t[3] = bf.getInt(12) & 0x00ffffff;
        r = new Random();
        //Log.d("MyTEA", "t: "+ Arrays.toString(t));
    }

    public @NonNull byte[] encryptLittleEndian(@NonNull byte[] src, byte seq) {
        int lens = src.length;
        int fill = 10 - (lens+1)%8;
        int dstlen = fill+lens+7;
        byte[] dst = new byte[dstlen];
        byte[] randfill = new byte[fill-1];
        t[3] = ((int)seq)<<24 | (t[3]&0x00ffffff);
        Log.d("MyTEA", "encrypt seq: "+ seq);
        r.nextBytes(randfill);
        //Log.d("MyTEA", "rand fill: "+ Utils.INSTANCE.toHexStr(randfill));
        System.arraycopy(randfill, 0, dst, 1, fill-1);
        dst[0] = (byte)((fill-3)|0xF8); // 存储pad长度
        System.arraycopy(src, 0, dst, fill, lens);
        //Log.d("MyTEA", "dst before enc: "+Utils.INSTANCE.toHexStr(dst));

        long iv1 = 0, iv2 = 0, holder;
        ByteBuffer bf = ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN);
        for(int i = 0; i < dstlen; i += 8) {
            long block = bf.getLong(i);
            holder = block ^ iv1;

            int v0 = (int)(holder>>32);
            int v1 = (int)holder;
            for (int j = 0; j < 0x10; j++) {
                v0 += (v1 + sumtable[j]) ^ ((int)(((long) v1 << 4)&0x00000000fffffff0L) + t[0]) ^ ((int)((v1 >> 5)&0x07ffffff) + t[1]);
                v1 += (v0 + sumtable[j]) ^ ((int)(((long) v0 << 4)&0x00000000fffffff0L) + t[2]) ^ ((int)((v0 >> 5)&0x07ffffff) + t[3]);
            }
            //Log.d("MyTEA", "v0: "+Integer.toHexString(v0)+", v1: "+Integer.toHexString(v1));
            iv1 = (((long)v0)<<32) | (((long)v1)&0x00000000ffffffffL);
            //Log.d("MyTEA", "iv1: "+Long.toHexString(iv1));

            iv1 = iv1 ^ iv2;
            iv2 = holder;
            //Log.d("MyTEA", "put: "+Long.toHexString(iv1));
            bf.putLong(i, iv1);
        }

        //Log.d("MyTEA", "dst after enc: "+Utils.INSTANCE.toHexStr(dst));
        return dst;
    }

    public byte[] decryptLittleEndian(@NonNull byte[] src, byte seq) {
        if (src.length < 16 || (src.length)%8 != 0) {
            return null;
        }
        byte[] dst = new byte[src.length];

        long iv1, iv2 = 0, holder = 0;
        t[3] = ((int)seq)<<24 | (t[3]&0x00ffffff);
        Log.d("MyTEA", "decrypt seq: "+ seq);
        ByteBuffer sbf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dbf = ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN);
        for(int i = 0; i < src.length; i += 8) {
            iv1 = sbf.getLong(i);

            iv2 ^= iv1;

            int v0 = (int)(iv2>>32);
            int v1 = (int)iv2;
            for (int j = 0x0f; j >= 0; j--) {
                v1 -= (v0 + sumtable[j]) ^ ((int)(((long) v0 << 4)&0x00000000fffffff0L) + t[2]) ^ ((int)((v0 >> 5)&0x07ffffff) + t[3]);
                v0 -= (v1 + sumtable[j]) ^ ((int)(((long) v1 << 4)&0x00000000fffffff0L) + t[0]) ^ ((int)((v1 >> 5)&0x07ffffff) + t[1]);
            }
            iv2 = (((long)v0)<<32) | (((long)v1)&0x00000000ffffffffL);

            dbf.putLong(i, iv2^holder);

            holder = iv1;
        }

        int start = (dst[0]&7)+3;
        Log.d("MyTEA", "decrypt start: "+ start);
        int datlen = src.length-7-start;
        if(datlen <= 0) return null;
        byte[] dat = new byte[datlen];
        Log.d("MyTEA", "decrypt data length: "+datlen);
        System.arraycopy(dst, start, dat, 0, datlen);
        return dat;
    }

    // TEA encoding sumtable
    private static final int[] sumtable = {
            0x9e3579b9,
            0x3c6ef172,
            0xd2a66d2b,
            0x78dd36e4,
            0x17e5609d,
            0xb54fda56,
            0x5384560f,
            0xf1bb77c8,
            0x8ff24781,
            0x2e4ac13a,
            0xcc653af3,
            0x6a9964ac,
            0x08d12965,
            0xa708081e,
            0x451221d7,
            0xe37793d0,
    };
}
