/*
 * Utils.kt
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
package top.fumiama.sdict.utils

/**
 * A utility object providing byte array formatting functions.
 */
object Utils {

    /**
     * Converts a [ByteArray] to its hexadecimal string representation.
     *
     * Each byte is represented by exactly two hexadecimal characters (e.g., `0F`, `A0`, `FF`).
     * The resulting string contains no delimiters and is all lowercase (as produced by [Integer.toHexString]).
     *
     * @param byteArray the input array of bytes
     * @return a hexadecimal string representing the byte contents
     *
     * Example:
     * ```
     * val input = byteArrayOf(0x0F, 0xA0.toByte())
     * val hex = Utils.toHexStr(input) // "0fa0"
     * ```
     */
    fun toHexStr(byteArray: ByteArray): String =
        with(StringBuilder()) {
            byteArray.forEach {
                val hex = it.toInt() and 0xFF
                val hexStr = Integer.toHexString(hex)
                if (hexStr.length == 1) append("0").append(hexStr)
                else append(hexStr)
            }
            toString()
        }
}