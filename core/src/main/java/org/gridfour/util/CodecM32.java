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
 *  The logic for this coding scheme was suggested by S. Martin
 *  In many regards, it resembles the variable-length integer code used
 *  by SQLLite4 (see https://sqlite.org/src4/doc/trunk/www/varint.wiki)
 * 
 * In other parts of the code base, the little-endian byte order is used
 * (low-order byte in the lowest memory address).  The M32 code
 * uses the opposite convention (highest order byte in lowest memory
 * address). This approach yields better compression ratios when working
 * with the Deflate algorithm.  It appears to be related to the presence
 * of multi-byte M32 codes in the message body. I believe that the reason
 * for this behavior is that the amount of  noise (entropy) in the
 * high-order bytes tends to be lower than in the low-order bytes.
 * In other words, the range-of-values in the high-order byte tends
 * to be small compared to that of the low. Recall that the Deflate
 * algorithm operates by identifying frequent “sequences” of values
 * in the encoded message. The first byte in a multi-byte M32 code
 * is always one of 4 symbols introducing the size of the encoding.
 * So by placing the byte with the least variation immediately after
 * the introducer, we create a two-byte sequence that likely matches
 * other two-byte sequences in the message.    I have confirmed this
 * speculation in tests with elevation data that showed a modest (1 percent)
 * improvement by ordering the bytes from high-order byte first.
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.util;

/**
 * Provides utilities for coding integer values into a series of one or more by
 * values using the Martin coding scheme.
 * <p>
 * The M32 coding scheme provides a way of encoding standard integer values into
 * a sequence of between 1 and 5 bytes, depending on the magnitude of the input
 * value. The scheme is advantageous integer distributions with substantially
 * more one-byte values than multi-byte values.  In fact, for values greater
 * than 126 or less than -127, the resulting storage will be at least
 * 3 bytes.  So for those integers that could be represented by the 
 * two-byte "short" data type, this method is actually disadvantageous.
 * <p>
 * The M32 coding scheme resembles the variable-length integer coding scheme
 * used in SQLLite, except that it supports both positive and negative integer
 * values.
 */
public class CodecM32 {

  private static final byte M2 = (byte) 0b01111110; // 126, introduces 2 bytes
  private static final byte M3 = (byte) 0b01111111; // 127, introduces 3 bytes 
  private static final byte M4 = (byte) 0b10000001; // -127, introduces 4 bytes
  private static final byte MN = (byte) 0b10000000; // -128, null code
  private static final int NULLVALUE = Integer.MIN_VALUE;

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
    this.buffer = new byte[symbolCount * 5];
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

  /**
   * Encodes the specified value and appends it to the buffer. The buffer
   * position is advanced by the number of bytes needed for the encoding.
   *
   * @param value any integer value to be encoded (a signed integer that can be
   * stored with 5 bytes)
   */
  public void encode(int value) {

    // first test to see if the value is in the one-byte range.
    // if the predictor/correctors are effective, most of the data 
    // will be in this range, with the remainder being mostly in the
    // two-byte range, with just a small incident of 3 or 4 byte values.
    int test = value & 0xffffff80;
    if (test == 0) {
      // the input is in the range 0 to 127, inclusive
      // check for special codes m2 and m3
      if (value < M2) {
        buffer[offset++] = (byte) value;
      } else {
        // the value is either M2 (126) or M3 (127).  Code it as a two-byte
        // sequence introduced by M2 and followed by the value itself.
        buffer[offset++] = M2;
        buffer[offset++] = 0;
        buffer[offset++] = (byte) value;
      }
    } else if (test == 0xffffff80) {
      // the input is in the range -128 to -1, inclusive.
      // Test to be sure it is not M4 or MN.
      if (value > M4) {
        buffer[offset++] = (byte) value;
      } else {
        // the value is either M4 (-127) or MN (-128). Code it as a two-byte
        // sequence introduced by M2 and followed by the value itself.
        buffer[offset++] = M2;
        buffer[offset++] = (byte) 0xff;
        buffer[offset++] = (byte) value;
      }
    } else {
      // at this point, we know that we need to encode a positive number as
      // a multi-byte sequence.  It is either greater than 127 or less than
      // -128.
      if (value > 0) {
        // the first mask here is set up for bits 0 to 14, with bit 15
        // (the high bit of the high byte) being treated as a sign bit.
        // thus the "FF80" pattern that we see in the next few entries.
        if ((value & 0xffff8000) == 0) {
          // 2 symbols required
          buffer[offset++] = M2;
          buffer[offset++] = (byte) (value >> 8);
          buffer[offset++] = (byte) (value & 0xff);
        } else if ((value & 0xff800000) == 0) {
          // 3 symbols required
          buffer[offset++] = M3;
          buffer[offset++] = (byte) ((value >> 16) & 0xff);
          buffer[offset++] = (byte) ((value >> 8) & 0xff);
          buffer[offset++] = (byte) (value & 0xff);
        } else {
          // 4 symbols required
          buffer[offset++] = M4;
          buffer[offset++] = (byte) ((value >> 24) & 0xff);
          buffer[offset++] = (byte) ((value >> 16) & 0xff);
          buffer[offset++] = (byte) ((value >> 8) & 0xff);
          buffer[offset++] = (byte) (value & 0xff);
        }
      } else {
        // value is negative.  again, recall that the pattern
        // "ff80" is intended to capture the sign bit.
        if (value == NULLVALUE) {
          buffer[offset++] = MN;
        } else if ((value & 0xffff8000) == 0xffff8000) {
          // 2 symbols required
          buffer[offset++] = M2;
          buffer[offset++] = (byte) ((value >> 8) & 0xff);
          buffer[offset++] = (byte) (value & 0xff);
        } else if ((value & 0xff800000) == 0xff800000) {
          // 3 symbols required
          buffer[offset++] = M3;
          buffer[offset++] = (byte) ((value >> 16) & 0xff);
          buffer[offset++] = (byte) ((value >> 8) & 0xff);
          buffer[offset++] = (byte) (value & 0xff);
        } else {
          // 4 symbols required
          buffer[offset++] = M4;
          buffer[offset++] = (byte) ((value >> 24) & 0xff);
          buffer[offset++] = (byte) ((value >> 16) & 0xff);
          buffer[offset++] = (byte) ((value >> 8) & 0xff);
          buffer[offset++] = (byte) (value & 0xff);
        }
      }
    }

  }

  /**
   * Decodes the next value in the buffer, advancing the buffer position as
   * appropriate
   *
   * @return an arbitrary integer value.
   */
  public int decode() {

    // Java does not support unsigned types.  In most cases, this limitation
    // is a major pain when dealing with byte-packing.  But in this case, 
    // we use it to advantage by allowing the symbols to sign-extend
    // when casting.  Thus when processing a sequence, we do not
    // and the first entry with the 0xff mask.  
    assert offset < bufferLimit : "failed sequence in encoding";
    int symbol = buffer[offset++];
    if (symbol < 0) {
      if (symbol > M4) {
        // simple decode
        return symbol;
      } else if (symbol == M4) {
        // four bytes follow
        int a = buffer[offset++];  // do not AND this, let the sign bit extend
        a = (a << 8) | (buffer[offset++] & 0xff);
        a = (a << 8) | (buffer[offset++] & 0xff);
        return (a << 8) | (buffer[offset++] & 0xff);
      } else {
        // by process of elimination, the symbol must be the null code
        return NULLVALUE;
      }
    } else {
      // value is greater than zero
      if (symbol < M2) {
        return symbol;
      } else if (symbol == M2) {
        int a = buffer[offset++]; // do not AND this, let the sign bit extend
        return (a << 8) | (buffer[offset++] & 0xff);
      } else {
        // value == M3, three bytes follow
        int a = buffer[offset++];  // do not AND this, let the sign bit extend
        a = (a << 8) | (buffer[offset++] & 0xff);
        return (a << 8) | (buffer[offset++] & 0xff);
      }
    }
  }

  //public static void main(String[] args) {
  //
  //  byte buffer[] = new byte[5];
  //  CodecM32 m32 = new CodecM32(buffer, 0, buffer.length);
  //
  //  // These are essentially Unit Tests for encode, checking to see if various
  //  // known numbers map to the correct size.
  //  for (int i = -126; i < 126; i++) {
  //    testSize(m32, i, 1);
  //  }
  //
  //  testSize(m32, 126, 3);
  //  testSize(m32, 127, 3);
  //  testSize(m32, -128, 3);
  //  testSize(m32, -127, 3);
  //
  //  testSize(m32, 128, 3);
  //  testSize(m32, -129, 3);
  //
  //  testSize(m32, 32767, 3);
  //  testSize(m32, 32768, 4);
  //
  //  testSize(m32, -32768, 3);
  //  testSize(m32, -32769, 4);
  //
  //  testSize(m32, (1 << 23) - 1, 4);
  //  testSize(m32, (1 << 23), 5);
  //
  //  testSize(m32, -(1 << 23), 4);
  //
  //  // this is essentially an integration test.  test the entire
  //  // set end-to-end.  It can take a couple of minutes depending on
  //  // your computer.
  //      for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
  //    m32.rewind();
  //    m32.encode(i);
  //    m32.rewind();
  //    int test = m32.decode();
  //    if (i != test) {
  //      System.out.println("Round-trip failure for " + i);
  //      break;
  //    }
  //  }
  //
  //}
  //
  //private static void testSize(CodecM32 m32, int target, int size) {
  //  m32.rewind();
  //  m32.encode(target);
  //  int length = m32.getEncodedLength();
  //  if (size != length) {
  //    System.out.println("Failed to match expected size "
  //            + target + ", " + size + "( " + length + ")");
  //  }
  //}
}
