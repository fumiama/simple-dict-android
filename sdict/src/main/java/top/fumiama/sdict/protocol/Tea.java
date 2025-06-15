/*
 * Tea.java
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import org.jetbrains.annotations.NotNull;

/**
 * Implementation of a modified Tiny Encryption Algorithm (TEA) with CBC-like chaining and custom padding.
 * This variant uses 128-bit keys, little-endian encoding, and a hardcoded 16-round sum table.
 * <p>
 * The encrypt/decrypt methods process data in 8-byte blocks using chained IVs and embed sequence numbers into key material.
 */
public class Tea {

    /** 128-bit TEA key stored as four 32-bit integers (low endian order). */
    private final int[] t = new int[4];

    /** Random generator for padding purposes. */
    private final Random r;

    /**
     * Constructs a TEA cipher with the given key.
     * The key is normalized to 16 bytes (padded with 0), parsed in little-endian order.
     * The last byte is masked to reserve 8 bits for the sequence number.
     *
     * @param tea raw key input (will be truncated or zero-padded to 16 bytes)
     */
    public Tea(@NotNull byte[] tea) {
        byte[] tea16 = new byte[16];
        System.arraycopy(tea, 0, tea16, 0, Math.min(tea.length, 15));
        tea16[15] = 0;
        ByteBuffer bf = ByteBuffer.wrap(tea16).order(ByteOrder.LITTLE_ENDIAN);
        t[0] = bf.getInt(0);
        t[1] = bf.getInt(4);
        t[2] = bf.getInt(8);
        t[3] = bf.getInt(12) & 0x00ffffff; // reserve highest 8 bits for sequence
        r = new Random();
    }

    /**
     * Encrypts data using TEA with CBC-like feedback and randomized padding.
     *
     * @param src the plaintext to encrypt
     * @param seq a sequence number to embed into the key (8 bits added to t[3])
     * @return the encrypted byte array, including padding
     */
    public @NotNull byte[] encryptLittleEndian(@NotNull byte[] src, byte seq) {
        int lens = src.length;
        int fill = 10 - (lens + 1) % 8; // pad to 8-byte alignment with room for 10 header bytes
        int dstlen = fill + lens + 7;
        byte[] dst = new byte[dstlen];

        byte[] randFill = new byte[fill - 1];
        t[3] = ((int) seq) << 24 | (t[3] & 0x00ffffff); // embed sequence ID into key

        r.nextBytes(randFill);
        dst[0] = (byte) ((fill - 3) | 0xF8); // encode pad length in top 3 bits
        System.arraycopy(randFill, 0, dst, 1, fill - 1);
        System.arraycopy(src, 0, dst, fill, lens);

        long iv1 = 0, iv2 = 0, holder;
        ByteBuffer bf = ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dstlen; i += 8) {
            long block = bf.getLong(i);
            holder = block ^ iv1;

            int v0 = (int) (holder >> 32);
            int v1 = (int) holder;
            for (int j = 0; j < 0x10; j++) {
                v0 += (v1 + sumtable[j]) ^ ((int) (((long) v1 << 4) & 0xfffffff0L) + t[0]) ^ ((v1 >>> 5 & 0x07ffffff) + t[1]);
                v1 += (v0 + sumtable[j]) ^ ((int) (((long) v0 << 4) & 0xfffffff0L) + t[2]) ^ ((v0 >>> 5 & 0x07ffffff) + t[3]);
            }

            iv1 = ((long) v0 << 32) | (v1 & 0xffffffffL);
            iv1 ^= iv2;
            iv2 = holder;

            bf.putLong(i, iv1);
        }

        return dst;
    }

    /**
     * Decrypts a TEA-encrypted message encoded via {@link #encryptLittleEndian}.
     * Returns null if input is malformed or padding is invalid.
     *
     * @param src the encrypted byte array
     * @param seq the sequence number to embed in key (must match encryption)
     * @return the decrypted plaintext, or null on failure
     */
    public byte[] decryptLittleEndian(@NotNull byte[] src, byte seq) {
        if (src.length < 16 || (src.length % 8) != 0) {
            return null;
        }

        byte[] dst = new byte[src.length];

        long iv1, iv2 = 0, holder = 0;
        t[3] = ((int) seq) << 24 | (t[3] & 0x00ffffff);

        ByteBuffer sbf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dbf = ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < src.length; i += 8) {
            iv1 = sbf.getLong(i);
            iv2 ^= iv1;

            int v0 = (int) (iv2 >> 32);
            int v1 = (int) iv2;
            for (int j = 0x0f; j >= 0; j--) {
                v1 -= (v0 + sumtable[j]) ^ ((int) (((long) v0 << 4) & 0xfffffff0L) + t[2]) ^ ((v0 >>> 5 & 0x07ffffff) + t[3]);
                v0 -= (v1 + sumtable[j]) ^ ((int) (((long) v1 << 4) & 0xfffffff0L) + t[0]) ^ ((v1 >>> 5 & 0x07ffffff) + t[1]);
            }

            iv2 = ((long) v0 << 32) | (v1 & 0xffffffffL);
            dbf.putLong(i, iv2 ^ holder);
            holder = iv1;
        }

        int start = (dst[0] & 7) + 3;
        int dataLen = src.length - 7 - start;
        if (dataLen <= 0) return null;

        byte[] dat = new byte[dataLen];
        System.arraycopy(dst, start, dat, 0, dataLen);
        return dat;
    }

    /**
     * TEA 16-round precomputed delta sum table.
     * Values: delta * (1 to 16), where delta = 0x9e3779b9 (golden ratio)
     */
    private static final int[] sumtable = {
            0x9e3579b9, 0x3c6ef172, 0xd2a66d2b, 0x78dd36e4,
            0x17e5609d, 0xb54fda56, 0x5384560f, 0xf1bb77c8,
            0x8ff24781, 0x2e4ac13a, 0xcc653af3, 0x6a9964ac,
            0x08d12965, 0xa708081e, 0x451221d7, 0xe37793d0,
    };
}