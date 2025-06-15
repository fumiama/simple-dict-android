/*
 * CmdPacket.java
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.jetbrains.annotations.NotNull;
import android.util.Log;

import top.fumiama.sdict.utils.Utils;

/**
 * Represents a command packet in the sdict protocol.
 * A CmdPacket contains:
 * <ul>
 *     <li>a command byte</li>
 *     <li>raw data</li>
 *     <li>an MD5 checksum of the data</li>
 * </ul>
 * It supports encryption and decryption using a TEA cipher with an embedded sequence number.
 *
 * <p>
 * Packet layout when encrypted:
 * <pre><code>
 * [0]      cmd (1 byte)
 * [1]      encrypted data length (1 byte)
 * [2–17]   MD5 hash of original data (16 bytes)
 * [18–N]   encrypted data payload
 * </code></pre>
 * </p>
 */
public class CmdPacket {
    private final byte cmd;
    private final byte[] data;
    private final byte[] md5;
    private final Tea t;

    /**
     * Constructs a command packet from command and data.
     * Calculates the MD5 digest of the data and stores it.
     *
     * @param cmd the command identifier
     * @param data the unencrypted payload
     * @param t the TEA cipher to use for encryption
     * @throws NoSuchAlgorithmException if MD5 digest is unavailable
     */
    public CmdPacket(byte cmd, @NotNull byte[] data, @NotNull Tea t) throws NoSuchAlgorithmException {
        this.cmd = cmd;
        this.data = data;
        this.t = t;
        md5 = MessageDigest.getInstance("MD5").digest(data);
        Log.d("CmdPacket", "md5: " + Utils.INSTANCE.toHexStr(md5));
    }

    /**
     * Constructs a command packet from an already encrypted byte array.
     * Extracts the command, MD5 hash, and encrypted data segment.
     *
     * @param raw the full encrypted packet
     * @param t the TEA cipher for later decryption
     */
    public CmdPacket(@NotNull byte[] raw, @NotNull Tea t) {
        this.cmd = raw[0];
        this.t = t;
        md5 = new byte[16];
        Log.d("CmdPacket", "build from raw packet: " + Utils.INSTANCE.toHexStr(raw));
        System.arraycopy(raw, 2, md5, 0, 16);
        Log.d("CmdPacket", "md5: " + Utils.INSTANCE.toHexStr(md5));
        data = new byte[raw.length - 1 - 1 - 16];
        System.arraycopy(raw, 1 + 1 + 16, data, 0, data.length);
        Log.d("CmdPacket", "data length: " + data.length);
    }

    /**
     * Encrypts the data and formats the full command packet.
     *
     * @param seq the sequence ID to inject into TEA cipher
     * @return the complete encrypted command packet
     */
    public @NotNull byte[] encrypt(byte seq) {
        byte[] dat = t.encryptLittleEndian(data, seq);
        byte[] d = new byte[1 + 1 + 16 + dat.length];
        d[0] = cmd;
        d[1] = (byte) dat.length;
        System.arraycopy(md5, 0, d, 2, 16);
        System.arraycopy(dat, 0, d, 1 + 1 + 16, dat.length);
        return d;
    }

    /**
     * Decrypts the embedded data and verifies its MD5 hash.
     *
     * @param seq the sequence ID that must match the encryption phase
     * @return the original data if hash verification passes; null otherwise
     * @throws NoSuchAlgorithmException if MD5 digest is unavailable
     */
    public byte[] decrypt(byte seq) throws NoSuchAlgorithmException {
        byte[] dat = t.decryptLittleEndian(data, seq);
        if (dat != null && Arrays.equals(MessageDigest.getInstance("MD5").digest(dat), md5)) {
            return dat;
        }
        return null;
    }

    /**
     * Command type enums for use with {@link CmdPacket}.
     */
    public final static byte
            CMD_GET = 0,  // Request value by key
            CMD_CAT = 1,  // Request all raw dictionary data
            CMD_MD5 = 2,  // Request MD5 of the raw dictionary data
            CMD_ACK = 3,  // Acknowledge reception
            CMD_END = 4,  // End of transmission
            CMD_SET = 5,  // Start to set key-value pair
            CMD_DEL = 6,  // Delete key-value
            CMD_DAT = 7;  // Push value data after CMD_SET
}
