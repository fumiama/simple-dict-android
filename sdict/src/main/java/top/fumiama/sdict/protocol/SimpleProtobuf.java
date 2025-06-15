/*
 * SimpleProtobuf.java
 *
 * Copyright (C) 2025 Minamoto Fumiama
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */
package top.fumiama.sdict.protocol;

import java.util.Stack;

import org.jetbrains.annotations.NotNull;

/**
 * SimpleProtobuf is a minimalist decoder for a compact binary key-value format using custom SLLE (Simple Length-Length Encoding).
 * Each record consists of encoded key and data lengths, their values, and optional type tags.
 * <p>
 * The format is optimized for space and fast sequential deserialization, suitable for lightweight struct serialize/deserialize.
 */
public class SimpleProtobuf {

    /**
     * Represents a parsed dictionary entry structure with raw binary key and value.
     */
    public static class Dict {
        /** Key as raw bytes. */
        public byte[] key;

        /** Value associated with the key, as raw bytes. */
        public byte[] data;
    }

    /** Internal stack used to collect Dict entries before returning. */
    private static final DictStack ds = new DictStack();

    /**
     * Parses a raw SLLE (Simple Length-Length Encoding, LEB128-like)-encoded byte array into
     * an array of {@link Dict} entries. Expected layout per entry:
     * <pre><code>
     * [struct_len][type][key_len][key_bytes][type][data_len][data_bytes]
     * </code></pre>
     * Lengths are SLLE-encoded (1–4 bytes), values are raw.
     *
     * @param raw the simple-protobuf encoded byte array of {@link Dict} entries
     * @return an array of parsed {@link Dict} entries
     */
    public static Dict[] getDictArray(@NotNull byte[] raw) {
        int offset = 0;
        SLLE s;
        while (offset < raw.length) {
            // Skip structure length and type
            offset += getSLLE(raw, offset).len;
            offset += getSLLE(raw, offset).len;

            // Parse key
            s = getSLLE(raw, offset);      // key length
            Dict d = new Dict();
            d.key = new byte[s.value];
            offset += s.len;
            System.arraycopy(raw, offset, d.key, 0, s.value);
            offset += s.value;

            // Skip value type
            offset += getSLLE(raw, offset).len;

            // Parse data
            s = getSLLE(raw, offset);      // data length
            d.data = new byte[s.value];
            offset += s.len;
            System.arraycopy(raw, offset, d.data, 0, s.value);
            offset += s.value;

            ds.push(d);
        }
        return ds.popAllData();
    }

    /**
     * Decodes a SLLE (Simple Length-Length Encoding, LEB128-like) value from the byte stream.
     * SLLE is similar to LEB128: each byte's 7 lower bits are value, and MSB=1 means "continue".
     *
     * @param p the byte array to read from
     * @param start the starting offset
     * @return an {@link SLLE} object containing decoded value and byte length
     */
    @NotNull
    private static SLLE getSLLE(byte[] p, int start) {
        SLLE s = new SLLE();
        s.value = 0;
        for (int i = 0; i < 4; i++) {
            s.value += (p[start + i] & 0x7F) << (i * 7);
            if ((p[start + i] & 0x80) == 0) { // If MSB == 0, it's the last byte
                s.len = i + 1;
                break;
            }
        }
        return s;
    }

    /**
     * Represents a decoded SLLE (Simple Length-Length Encoding, LEB128-like) entry.
     * Contains both the decoded integer value and the number of bytes read.
     */
    private static class SLLE {
        int value;
        int len;
    }

    /**
     * Stack that accumulates Dict entries and provides a method to pop all as an array.
     */
    private static class DictStack extends PopAllStack<Dict> {
        /**
         * Pops and returns all elements in the stack as a {@link Dict} array.
         * Clears the stack after use.
         *
         * @return a {@link Dict} array, or null if the stack is empty
         */
        public Dict[] popAllData() {
            Object[] t = popAll();
            if (t != null) {
                Dict[] d = new Dict[t.length];
                for (int i = 0; i < t.length; i++) {
                    d[i] = (Dict) t[i];
                }
                return d;
            } else {
                return null;
            }
        }
    }

    /**
     * Extension of {@link Stack} that allows batch popping all elements at once.
     *
     * @param <T> the element type
     */
    private static class PopAllStack<T> extends Stack<T> {
        /**
         * Pops all elements currently in the stack.
         * Resets stack size to 0 afterward.
         *
         * @return an Object[] array of all items, or null if stack is empty
         */
        public Object[] popAll() {
            if (size() > 0) {
                Object[] t = new Object[size()];
                System.arraycopy(elementData, 0, t, 0, size());
                setSize(0);
                return t;
            } else {
                return null;
            }
        }
    }
}
