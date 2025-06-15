/*
 * ByteArrayQueue.kt
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
package top.fumiama.sdict.io

/**
 * A simple FIFO queue for byte arrays.
 * Internally stores all data in a single [ByteArray] and supports popping and appending operations.
 */
class ByteArrayQueue {
    /** Internal storage for all queued bytes. */
    private var elements = byteArrayOf()

    /** Current number of bytes in the queue. */
    val size get() = elements.size

    /**
     * Removes and returns the first [num] bytes from the queue, if available.
     *
     * @param num the number of bytes to dequeue; defaults to 1
     * @return a [ByteArray] of the requested length, or `null` if no enough data is available
     */
    fun dequeue(num: Int = 1): ByteArray? {
        return if (num <= elements.size) {
            val re = elements.copyOfRange(0, num)
            elements = elements.copyOfRange(num, elements.size)
            re
        } else null
    }

    /**
     * Removes and returns all remaining bytes in the queue.
     * After this call, the queue will be empty.
     *
     * @return a [ByteArray] containing all bytes that were in the queue
     */
    fun drain(): ByteArray {
        val re = elements
        elements = byteArrayOf()
        return re
    }

    /**
     * Appends the given [items] to the end of the queue.
     *
     * @param items the [ByteArray] to append
     */
    operator fun plusAssign(items: ByteArray) {
        elements += items
    }
}