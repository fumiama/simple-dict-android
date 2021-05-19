package top.fumiama.simpledict;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.util.Stack;

public class SimpleProtobuf {
    public static class Dict {
        public byte[] key;
        public byte[] data;
    }

    private static final DictStack ds = new DictStack();

    public static Dict[] getDictArray(@NotNull byte[] raw) {
        int offset = 0;
        SLLE s;
        while (offset < raw.length) {
            offset += getSLLE(raw, offset).len; //struct_len
            offset += getSLLE(raw, offset).len; //type
            s = getSLLE(raw, offset);           //data len
            Log.d("MySPB", "Data len:" + s.value);
            Dict d = new Dict();
            d.key = new byte[s.value];
            offset += s.len;
            System.arraycopy(raw, offset, d.key, 0, s.value);
            offset += s.value;
            offset += getSLLE(raw, offset).len; //type
            s = getSLLE(raw, offset);           //data len
            Log.d("MySPB", "Data len:" + s.value);
            d.data = new byte[s.value];
            offset += s.len;
            System.arraycopy(raw, offset, d.data, 0, s.value);
            offset += s.value;
            ds.push(d);
        }
        return ds.popAllData();
    }

    @NotNull
    private static SLLE getSLLE(byte[] p, int start) {
        SLLE s = new SLLE();
        s.value = 0;
        for (int i = 0; i < 4; i++) {
            s.value += (p[start + i] & 0x7f) << (i * 7);
            if ((p[start + i] & 0x80) == 0) {        //无更高位
                s.len = i + 1;
                break;
            }
        }
        return s;
    }

    private static class SLLE {
        int value;
        int len;
    }

    private static class DictStack extends PopAllStack<Dict> {
        public Dict[] popAllData() {
            Object[] t = popAll();
            if (t != null) {
                Dict[] d = new Dict[t.length];
                for (int i = 0; i < t.length; i++) {
                    d[i] = (Dict) t[i];
                }
                return d;
            } else return null;
        }
    }

    private static class PopAllStack<T> extends Stack<T> {
        public Object[] popAll() {
            if (size() > 0) {
                Object[] t = new Object[size()];
                System.arraycopy(elementData, 0, t, 0, size());
                setSize(0);
                return t;
            } else return null;
        }
    }
}
