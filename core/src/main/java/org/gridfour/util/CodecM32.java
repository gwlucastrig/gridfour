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
 * The logic for this coding scheme was suggested by S. Martin
 * In many regards, it resembles the variable-length integer code used
 * by SQLLite4 (see https://sqlite.org/src4/doc/trunk/www/varint.wiki)
 *
 * This class was designed to support the Predictor classes in the
 * Gridfour data compression suite. The predictors compute integer residuals
 * that are then serialized into bytes using M32 and then handed to
 * general-purpose compressors such as Huffman coding or Deflate.
 * The process strongly favors a serialization that is dominated
 * by a small number of symbols (values). A good predictor tends
 * to produce values close to zero with a frequency of occurance
 * tapering off as the magnitude of the value increases. So M32 is
 * designed to support that pattern. It makes a tradeoff allowing
 * smaller (and more frequent) values to be represented with shorter
 * byte sequences while larger (and less frequent) values use longer
 * byte sequences.
 * 
 * When integer values are encoded, they are serialized into a sequence
 * of one or more bytes. The first byte in the sequence indicates whether
 * the sequence is of length 1 byte or includes multiple bytes
 *    1.  Values in the range -126 to +126 are coded using a single byte.
 *    2.  If the initial value is -127 or +127, it indicates a multi-byte
 *        sequence. -127 indicates that the serialized value is negative.
 *        +127 indicates that the serialized value is positive.
 *    3.  -128 is a special code indicating Integer.MIN_VALUE.
 * 
 * Clearly, if we are encoding using a multi-byte sequence, we need some
 * mechanizm for indicating the end of the sequence.  In M32, the bytes
 * that follow the introducer are bitmapped as follows
 *      high bit                           |  low-order 7 bits
 *      more bytes follow (1) / end(0)     |    data
 * As we are decoding a sequence, we check the high-order bit of each
 * byte.  If the bit is clear, we know that the current byte is the last
 * in the sequence.   As we process bytes, the low-order 7 bits are collected
 * to create a positive "delta" value. To decode the value for a sequence,
 * the delta values are added to a "segment base value" which is assigned
 * depending on how many bytes are in the sequence. The delta is specified
 * as a set of 7-bit fragments given in big-endian order.
 * As the code advances through the bytes, it shifts the accumulated
 * delta value up by 7 to make room for the next 7 bits.
 * 
 * Recall that M32 is based on the assumption that the higher the magnitude
 * of a residual, the less probable it is to occur. So this scheme is designed
 * to use the smallest number of bytes for smaller numbers. Here are some examples
 * value     |      byte  1      | byte 2              |  byte 3   | byte 4
 *    126    |    126 (content)  | ---                 | ---       | ---
 *    127    |    127 (intro)    | 0000_0000 (content) | ---       | ---
 *    128    |    127 (intro)    | 0000_0001 (content) | ---       | ---
 *    255    |    127 (intro)    | 1000_0000 (multi)   | 0000_0000 | ---
 *  16638    |    127            | 1111_1111           | 0111_1111 | ---
 *  16639    |    127            | 1000_0000           | 1000_0000 | 0000_0000
 *  16640    |    127            | 1000_0000           | 1000_0000 | 0000_0001 
 * 
 * For multi-byte sequences, he first byte is an introducer.  It tells us
 * that a multi-byte sequence is to follow and also the sign of the value.
 * For value 127, the high bit of the second byte is clear,
 * indicating the end of a sequence.  Because we only resort to multi-byte
 * sequences when we are serializing values greater than 126, we know that
 * if we see a a second byte of zero, the serialized value is 127.
 * In the example above, the value 255 requires an additional content
 * byte, so the high bit of byte 2 is set.  
 * 
 * In order to avoid redundancy, we want to make sure that we never use a 
 * three byte sequence to store a value that would fit into two bytes.
 * So, there is a threshold criteria that tells the encoder how many bytes
 * to used based on the absolute value of the input value:
 * 
 *    Bytes    MinVal        MaxVal
 *    1             0           126
 *    2           127           254
 *    3           255         16638
 *    4         16639       2113790
 *    5       2113791     270549246
 *    6     270549247    2147483647
 * 
 * In understanding these design choices, it is worth noting the following:
 *    1.  Large values are considered rare.  So the design is biased to
 *        storing the smaller values most efficiently.
 *    2.  Zero values are considered desireable.
 *    3.  The encoding is symmetric for positive and negative numbers with
 *        special treatment for Integer.MIN_VALUE.
 * 
 * For example, an earlier version of the M32 concept used the value of 
 * the introducer to indicate how many bytes were to follow (126 for 2 bytes, 
 * 127 for 3 bytes, etc.).  But doing so meant that the number of special
 * introducer code that were required for the first byte was increased.
 * And, the potential values that could be represented by just one byte
 * was reduced.  The new concept also focuses on increasing the
 * probability that if a 2 byte sequence is required, the second by has
 * a low order of magnitude (which will foster better data compression).
 * By switching to the current concept, we achieved an average
 * of 1/2 percent reduction on the size of a compressed data set. 
 * While not an huge improvement, it does contribute to the
 * overall effectiveness of the compression.
 *  
 * Byte Order
 * In other parts of the Gridfour code base, the little-endian byte order is used
 * (low-order byte in the lowest memory address).  The M32 code
 * uses the opposite convention (highest order bits in lowest memory
 * address). This approach yields better compression ratios when working
 * with the Deflate algorithm.  It appears to be related to the presence
 * of multi-byte M32 codes in the message body. I believe that the reason
 * for this behavior is that the amount of  noise (entropy) in the
 * high-order bytes tends to be lower than in the low-order bytes.
 * In other words, the range-of-values in the high-order byte tends
 * to be small compared to that of the low. Recall that the Deflate
 * algorithm operates by identifying frequent “sequences” of values
 * in the encoded message. The first byte in a multi-byte M32 code
 * is always one of either +127 or -127 introducing the encoding to follow.
 * So by placing the byte with the least variation immediately after
 * the introducer, we create a two-byte sequence that likely matches
 * other two-byte sequences in the message.    I have confirmed this
 * speculation in tests with elevation data that showed a modest (1 percent)
 * improvement by ordering the bytes from high-order byte first.
 *
 * -----------------------------------------------------------------------
 */
package  org.gridfour.util;

/**
 * Provides utilities for coding integer values into a series of one or more by
 * values using the Martin coding scheme.
 * <p>
 * The M32 coding scheme provides a way of encoding standard integer values into
 * a sequence of between 1 and 5 bytes, depending on the magnitude of the input
 * value. The scheme is advantageous integer distributions with substantially
 * more one-byte values than multi-byte values. In fact, for values greater than
 * 126 or less than -126, the resulting storage will be at least 2 bytes.
 * And values greater than 254 or less than -254, require at least 3 bytes.
 * So for those integers that could be represented by the two-byte "short" 
 * data type, this method is actually disadvantageous.
 * <p>
 * The M32 coding scheme resembles the variable-length integer coding scheme
 * used in SQLLite, except that it supports both positive and negative integer
 * values.
 */
public class CodecM32 {

    /**
     * The maximum number of bytes that would be needed to encode
     * large-magnitude symbols
     */
    public static final int MAX_BYTES_PER_VALUE = 6;
    
    private final byte[] buffer;
    private final int bufferLimit;
    private int offset;
    private int offset0;

    /**
     * Construct an instance with a buffer large enough to hold the specified
     * number of integer values.
     *
     * @param symbolCount the number of values to be stored in the buffer.
     */
    public CodecM32(int symbolCount) {
        this.buffer = new byte[symbolCount * MAX_BYTES_PER_VALUE];
        this.bufferLimit = buffer.length;
        // offset will be zero
        // offset0 will be zero
    }

    /**
     * Construct an instance using the specified buffer for storing data.
     *
     * @param buffer a valid byte array dimensioned to at least offset+length
     * @param offset the starting position for storing data in the buffer.
     * @param length the limit of the number bytes to be stored in the buffer.
     */
    public CodecM32(byte[] buffer, int offset, int length) {
        this.buffer = buffer;
        this.offset = offset;
        this.offset0 = offset;
        this.bufferLimit = offset + length;
    }

    /**
     * Reset the buffer position to its beginning position
     */
    public void rewind() {
        offset = offset0;
    }

    /**
     * Get a safe copy of the content of the buffer.
     *
     * @return an array of size zero or greater.
     */
    public byte[] getEncoding() {
        int n = offset - offset0;
        byte[] b = new byte[n];
        System.arraycopy(buffer, offset0, b, 0, n);
        return b;
    }

    /**
     * Gets the the length of the encoded data stored in the buffer
     *
     * @return a positive integer, potentially zero
     */
    public int getEncodedLength() {
        return offset - offset0;
    }

    private static final int loMask = 0b0111_1111; // 0x7f
    private static final int hiBit = 0b1000_0000;  // 0x80

    /**
     * Encodes the specified value and appends it to the buffer. The buffer
     * position is advanced by the number of bytes needed for the encoding.
     * <p>
     * Because this method is written to be used in tight loops with intense
     * processing, there is no bounds checking performed on the internal
     * buffer.  Application developers must take responsibility for their
     * own safety of implementation issues.
     *
     * @param value any integer value to be encoded (a signed integer that can
     * be stored with 5 bytes)
     */
    public void encode(int value) {
        //    1             0           126 
        //    2           127           254   
        //    3           255         16638   
        //    4         16639       2113790 
        //    5       2113791     270549246 
        //    6     270549247    2147483647 
        //
        int absValue;
        if (value < 0) {
            if (value == Integer.MIN_VALUE) {
                buffer[offset++] = (byte) -128;   // 0x80
                return;
            } else if (value > -127) {
                buffer[offset++] = (byte) value;
                return;
            }
            buffer[offset++] = (byte) (-127);
            absValue = -value;
        } else {
            if (value < 127) {
                buffer[offset++] = (byte) value;
                return;
            }
            buffer[offset++] = (byte) 127;
            absValue = value;
        }

        if (absValue <= 254) {
            int delta = absValue - 127;
            buffer[offset++] = (byte) delta;
        } else if (absValue <= 16638) {
            int delta = absValue - 255;
            buffer[offset++] = (byte) (((delta >> 7) & loMask) | hiBit);
            buffer[offset++] = (byte) (delta & loMask);
        } else if (absValue <= 2113790) {
            int delta = absValue - 16639;
            buffer[offset++] = (byte) (((delta >> 14) & loMask) | hiBit);
            buffer[offset++] = (byte) (((delta >> 7) & loMask) | hiBit);
            buffer[offset++] = (byte) (delta & loMask);
        } else if (absValue <= 270549246) {
            int delta = absValue - 2113791;
            buffer[offset++] = (byte) (((delta >> 21) & loMask) | hiBit);
            buffer[offset++] = (byte) (((delta >> 14) & loMask) | hiBit);
            buffer[offset++] = (byte) (((delta >> 7) & loMask) | hiBit);
            buffer[offset++] = (byte) (delta & loMask);
        } else {
            int delta = absValue - 270549247;
            buffer[offset++] = (byte) (((delta >> 28) & loMask) | hiBit);
            buffer[offset++] = (byte) (((delta >> 21) & loMask) | hiBit);
            buffer[offset++] = (byte) (((delta >> 14) & loMask) | hiBit);
            buffer[offset++] = (byte) (((delta >> 7) & loMask) | hiBit);
            buffer[offset++] = (byte) (delta & loMask);
        }
    }

    private static final int[] segmentBaseValue = {
                127, 255, 16639, 2113791, 270549247
            };

    /**
     * Decodes the next value in the buffer, advancing the buffer position as
     * appropriate.
     * <p>
     * Because this method is written to be used in tight loops with intense
     * processing, there is not bounds checking performed on the internal
     * buffer.  Application developers must take responsibility for their
     * own safety of implementation issues.
     *
     * @return an arbitrary integer value.
     */
    public int decode() {
  
        //assert offset < bufferLimit : "failed sequence in encoding";

        int symbol = buffer[offset++];
        if (symbol == -128) {
            return Integer.MIN_VALUE;
        } else if (-127 < symbol && symbol < 127) {
            return symbol;
        }
  
        int delta = 0;
        for (int i = 0; i < segmentBaseValue.length; i++) {
            int sample = buffer[offset++];
            delta = (delta << 7) | (sample & loMask);
            if ((sample & hiBit) == 0) {
                // we need special handling for the case where
                // the return value would be Integer.MIN_VALUE
                // because of the asymmetric nature of signed integer.
                // and -Integer.MIN_VALUE does not equal Integer.MAX_VALUE.
                if (symbol == -127) {
                    delta = -delta - segmentBaseValue[i];
                } else {
                    delta += segmentBaseValue[i];
                }
                break;
            }
        }
        // we're done
        return delta;
    }
    
    /**
     * Gets the number of bytes remaining in the buffer.  This is not the
     * number of symbols remaining, but rather the available bytes.
     * @return a positive value, potentially zero.
     */
    public int remaining(){
        return bufferLimit-offset;
    }

    //public static void main(String[] args) {
    //
    //    // Print a table of the ranges of values for each element
    //    int mask = 0b0111_1111;
    //    int b = 0;
    //    int b0 = 127;
    //    int b1 = 126;
    //    String fmt = "%d %13d %13d%n";
    //    System.out.format("Bytes    MinVal        MaxVal%n");
    //    System.out.format(fmt, 1, 0, 126, 0);
    //    for (int i = 2; i <= 6; i++) {
    //        b = (b << 7) | mask;
    //        b0 = b1 + 1;
    //        b1 = b0 + b;
    //        if (i == 6) {
    //            b1 = Integer.MAX_VALUE;
    //        }
    //        System.out.format(fmt, i, b0, b1);
    //    }
    //
    //    byte buffer[] = new byte[6];
    //    CodecM32 m32 = new CodecM32(buffer, 0, buffer.length);
    //
    //    // These are essentially Unit Tests for encode, checking to see if various
    //    // known numbers map to the correct size and can be correctly interpreted
    //    testSize(m32, 0, 1);
    //    testSize(m32, 126, 1);
    //    testSize(m32, 127, 2);
    //    testSize(m32, -128, 2);
    //    testSize(m32, -127, 2);
    //    testSize(m32, 128, 2);
    //    testSize(m32, -129, 2);
    //    testSize(m32, 254, 2);
    //    testSize(m32, 255, 3);
    //    testSize(m32, 16638, 3);
    //    testSize(m32, 16639, 4);
    //    testSize(m32, 2113790, 4);
    //    testSize(m32, 2113791, 5);
    //    testSize(m32, 270549246, 5);
    //    testSize(m32, 270549247, 6);
    //    testSize(m32, Integer.MAX_VALUE, 6);
    //    testSize(m32, Integer.MIN_VALUE + 1, 6);
    //    testSize(m32, Integer.MIN_VALUE, 1);
    //
    //    // this is essentially an integration test.  test the entire
    //    // set end-to-end.  It can take a couple of minutes depending on
    //    // your computer.
    //    for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
    //        m32.rewind();
    //        m32.encode(i);
    //        m32.rewind();
    //        int test = m32.decode();
    //        if (i != test) {
    //            System.out.println("Round-trip failure for " + i);
    //            break;
    //        }
    //    }
    //}
    //
    //private static void testSize(CodecM32 m32, int target, int size) {
    //    m32.rewind();
    //    m32.encode(target);
    //    int length = m32.getEncodedLength();
    //    if (size != length) {
    //        System.out.println("Failed to match expected size "
    //                + target + ", " + size + "( " + length + ")");
    //    }
    //    m32.rewind();
    //    int test = m32.decode();
    //    if (test != target) {
    //        System.out.println("Round-trip failure for " + target + " (" + test + ")");
    //    }
    //}
}
