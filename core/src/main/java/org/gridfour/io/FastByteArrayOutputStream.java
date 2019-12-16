/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Provides a faster equivalent of Java's ByteArrayOutputStream. The stock Java
 * class implements a thread-safe behavior by including synchronization in its
 * method calls. While convenient for some applications, that approach reduces
 * throughput. Therefore this class is implemented without synchronization.
 * <p>
 * <strong>Caution:</strong> This class is not thread safe.
 */
public class FastByteArrayOutputStream extends OutputStream {

    private static final int DEFAULT_BLOCK_SIZE = 8192;

    private final List<byte[]> bufferList = new LinkedList<>();
 
    /**
     * The number of bytes in the working buffer.
     */
    private byte[] buffer;

    private boolean closed;

    /**
     * The size of the working buffer
     */
    private int bufferSize;

    /**
     * The number of bytes in the working buffer
     */
    private int nBytesInBuffer;
    /**
     * The number of bytes in the blocks that have been moved
     * into the linked list.
     */
    private int nBytesInList;

    // Constructors --------------------------------------------------
    /**
     * Standard constructor.
     */
    public FastByteArrayOutputStream() {
        this(DEFAULT_BLOCK_SIZE);
    }

    /**
     * Constructs an instance of this class with the specified initial buffer
     * size.
     *
     * @param initialAllocation the initial buffer size, in bytes.
     */
    public FastByteArrayOutputStream(int initialAllocation) {
        bufferSize = initialAllocation;
        buffer = new byte[bufferSize];
    }

    /**
     * Gets the size of the data collection, in bytes.
     *
     * @return a value of zero or greater.
     */
    public int getSize() {
        return nBytesInList + nBytesInBuffer;
    }

    @Override
    public void close() {
        closed = true;
    }

    /**
     * Gets the content of the buffer in the form of a byte array. This routine
     * always constructs a new instance of the content.
     *
     * @return A new byte array containing the content of the buffer.
     */
    public byte[] toByteArray() {
        byte[] data = new byte[getSize()];

        // any blocks in the linked list will be fully populated.
        // the current buffer may be partially populated or empty.
        int pos = 0;
        if (!bufferList.isEmpty()) {
            for (byte[] bytes : bufferList) {
                System.arraycopy(bytes, 0, data, pos, bytes.length);
                pos += bytes.length;
            }
        }

        // write the internal buffer directly
        System.arraycopy(buffer, 0, data, pos, nBytesInBuffer);

        return data;
    }

    @Override
    public String toString() {
        return "FastByteArrayOutputStream: size=" + getSize();
    }

    @Override
    public void write(int datum) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        } else {
            if (nBytesInBuffer == bufferSize) {
                addBuffer();
            }

            // store the byte
            buffer[nBytesInBuffer++] = (byte) datum;
        }
    }

    @Override
    public void write(byte[] data, int poffset, int plength) throws IOException {
        int length = plength;
        int offset = poffset;
        if (data == null) {
            throw new NullPointerException();
        } else if (offset < 0
                || offset + length > data.length
                || length < 0) {
            throw new IndexOutOfBoundsException();
        } else if (closed) {
            throw new IOException("Stream closed");
        } else if (plength == 0) {
            return;
        }
        if ((nBytesInBuffer + length) > bufferSize) {
            int copyLength;

            do {
                if (nBytesInBuffer == bufferSize) {
                    addBuffer();
                }

                copyLength = bufferSize - nBytesInBuffer;

                if (length < copyLength) {
                    copyLength = length;
                }

                System.arraycopy(
                        data, offset, buffer, nBytesInBuffer, copyLength);
                offset += copyLength;
                nBytesInBuffer += copyLength;
                length -= copyLength;
            } while (length > 0);
        } else {
            // Copy in the subarray
            System.arraycopy(data, offset, buffer, nBytesInBuffer, length);
            nBytesInBuffer += length;
        }
    }

    /**
     * Create a new buffer and store the current one in linked list
     */
    private void addBuffer() {
        bufferList.add(buffer);
        bufferSize = DEFAULT_BLOCK_SIZE;
        buffer = new byte[DEFAULT_BLOCK_SIZE];
        nBytesInList += nBytesInBuffer;
        nBytesInBuffer = 0;
    }
}
