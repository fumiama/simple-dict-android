package top.fumiama.simpledict;

import android.util.Log;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class CmdPacket {
    private final byte cmd;
    private final byte[] data;
    private final byte[] md5;
    private final Tea t;

    public CmdPacket(byte cmd, @NonNull byte[] data, @NonNull Tea t) throws NoSuchAlgorithmException {
        this.cmd = cmd;
        this.data = data;
        this.t = t;
        md5 = MessageDigest.getInstance("MD5").digest(data);
        Log.d("MyCP", "md5: "+Utils.INSTANCE.toHexStr(md5));
    }

    public CmdPacket(@NonNull byte[] raw, @NonNull Tea t)  {
        this.cmd = raw[0];
        this.t = t;
        md5 = new byte[16];
        Log.d("MyCP", "build from raw packet: "+Utils.INSTANCE.toHexStr(raw));
        System.arraycopy(raw, 2, md5, 0, 16);
        Log.d("MyCP", "md5: "+Utils.INSTANCE.toHexStr(md5));
        data = new byte[raw.length-1-1-16];
        System.arraycopy(raw, 1+1+16, data, 0, data.length);
        Log.d("MyCP", "data length: "+data.length);
    }

    public @NonNull byte[] encrypt(byte seq) {
        byte[] dat = t.encryptLittleEndian(data, seq);
        byte[] d = new byte[1+1+16+dat.length];
        d[0] = cmd;
        d[1] = (byte) dat.length;
        System.arraycopy(md5, 0, d, 2, 16);
        System.arraycopy(dat, 0, d, 1+1+16, dat.length);
        return d;
    }

    public byte[] decrypt(byte seq) throws NoSuchAlgorithmException {
        byte[] dat = t.decryptLittleEndian(data, seq);
        if (dat != null && Arrays.equals(MessageDigest.getInstance("MD5").digest(dat), md5)) {
            return dat;
        }
        return null;
    }

    public final static byte CMDGET = 0, CMDCAT = 1, CMDMD5 = 2, CMDACK = 3, CMDEND = 4, CMDSET = 5, CMDDEL = 6, CMDDAT = 7;
}
