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
 * 09/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * In choosing the bit order for packing, I started with the working
 * principle that the design choice should be one that ensured efficient
 * processing when reading data.  Typically, a data-packing application 
 * writes data only a once but reads it over-and-over again.  However, 
 * after experimenting with different approaches, I did not identify one that
 * was unambiguously better than the other.  So I decided to go with a
 * packing order that would give good performance and would also make it
 * easier to inspect the content of variables when debugging.
 * 
 * Convention
 *   the low-order bits, or the "right-side bits" are given first.
 *   bits are numbered 0 to n.  Within an integral data type (byte, int, long),
 *     the 0 bit has a numeric value of 2 to the power zero, or 1.  
 *     the 1 bit has a numeric value of 2 to the power 1, or 2.
 *     the 2 bit has a numeric value of 2 to the power 2, or 4.
 *     Et cetera.
 *                     High Bit              Low-Bit
 *   For one byte:     7   6   5   4   3   2   1   0    
 *
 *  The data is eventually transcribed to an array of bytes such that 
 *  the arithmetically lowest-order byte is given as b[0], the next as b[1],
 *  etc.  Presumably, applications will store these bytes in files in
 *  that order.
 *
 *  Processing
 *     During processing, data is stored in an temporary "scratch" field of type
 *  long. As we add symbols to the output store, the number of bits in the
 *  scratch field grows until it is full and needs to be moved to the
 *  internal byte buffer. The symbols can be either single bits or clusters
 *  of bits. The maximum size of a symbol is 32 bits.
 *  When a symbol is larger than the room remaining in the scratch field,
 *  it is split into two pieced.  The low order piece is added to the
 *  scratch field, which is then moved to the byte buffer.  The high-order
 *  piece is then written to scratch.  Since the maximum size of the
 *  symbol is smaller than the scratch field, we never have to split
 *  the symbol more than once.
 *
 * Layout
 *   long text[1..n][n_LONG_IN_PAGE];
 *   text buffer increases size as necessary
 *
 *   long page[N_LONG_IN_PAGE] is a temporary array pointing to text.
 *                            high bits                 low bits
 *     page[0] contains bits 63, 62, 61, 60, ..., 3, 2, 1, 0
 *     page[1] contains bits 127, 126, 125,  ..., 66, 65, 64
 *
 *   Remember that >>> is the unsigned right shift operator, >> is signed.
 * -----------------------------------------------------------------------
 */
package org.gridfour.io;

import java.util.ArrayList;

/**
 * Writes a series of bits to an internal memory buffer (a "store").
 */
public class BitOutputStore {

  private static final long mask[] = new long[65];

  static {
    // masks are defined so that the number of bits in the mask
    // is the bit index.
    // mask[0] = 00000000
    // masl[1] = 00000001
    // mask[2] = 00000011
    // mask[3] = 00000111
    // etc.   
    long m = 1L;
    for (int i = 1; i < 64; i++) {
      mask[i] = m;
      m = (m << 1) | 1L;
    }
  }

  private static final int BLOCK_SIZE = 1024;

  private static class ByteBuffer {

    private int iByte;
    private byte[] block; // the current block
    private final ArrayList<byte[]> blockList;

    ByteBuffer() {
      blockList = new ArrayList<>();
      block = new byte[BLOCK_SIZE];
      blockList.add(block);
    }

    void addByte(byte b) {
      if (iByte == BLOCK_SIZE) {
        iByte = 0;
        block = new byte[BLOCK_SIZE];
        blockList.add(block);
      }
      block[iByte++] = b;
    }

    void addLong(long value) {
      long s = value;
      addByte((byte) (s & 0xffL));
      s >>= 8;
      addByte((byte) (s & 0xffL));
      s >>= 8;
      addByte((byte) (s & 0xffL));
      s >>= 8;
      addByte((byte) (s & 0xffL));
      s >>= 8;
      addByte((byte) (s & 0xffL));
      s >>= 8;
      addByte((byte) (s & 0xffL));
      s >>= 8;
      addByte((byte) (s & 0xffL));
      s >>= 8;
      addByte((byte) (s & 0xffL));
    }

    int getByteCount() {
      return (blockList.size() - 1) * BLOCK_SIZE + iByte;
    }

    byte[] getBytes(int nBytesRequired) {
      int n = getByteCount();
      int nAlloc = n;
      if(nBytesRequired>n){
        nAlloc = nBytesRequired;
      }

      byte b[] = new byte[nAlloc];
      if (n == 0) {
        return b;
      }
      int nFullBlocks = n / BLOCK_SIZE;
      for (int i = 0; i < nFullBlocks; i++) {
        byte[] s = blockList.get(i);
        System.arraycopy(s, 0, b, i * BLOCK_SIZE, BLOCK_SIZE);
      }
      int bOffset = nFullBlocks * BLOCK_SIZE;
      if (bOffset < n && nFullBlocks < blockList.size()) {
          System.arraycopy(block, 0, b, bOffset, n - bOffset);
      }

      return b;
    }
    
 
  }

  private long marker;
  private long scratch;
  int nBits;

  ByteBuffer byteBuffer = new ByteBuffer();

  /**
   * Construct bit-writer instance with empty content
   */
  public BitOutputStore() {
    marker = 1;
  }

  /**
   * Add a single bit to the bit-writer content. If the input value is zero, a
   * bit value of zero will be added to the content. If the input value is
   * non-zero, a bit value of 1 will be added to the content.
   *
   * @param value an integer value.
   */
  public void appendBit(int value) {
    if (value != 0) {
      scratch |= marker;
    }
    marker <<= 1;
    nBits++;
    if (marker == 0) {
      // the marker rotated out of the variable
      moveScratchToText();
    }
  }

  /**
   * Adds the specified number of bits from the specified value to the content.
   * The input value may be a signed or unsigned quantity.
   *
   * @param nBitsInValue number of bits in the range 1 to 32.
   * @param value an integer containing the bits to be stored.
   */
  public void appendBits(int nBitsInValue, int value) {

    if (nBitsInValue < 1 || nBitsInValue > 32) {
      throw new IllegalArgumentException(
              "Attempt to add number of bits not in range (1, 32): "
              + nBitsInValue);
    }
    
        // presumably, the application passed in a value that does not 
    // have non-zero bits in the high-order positions greater than nBitsInValue.
    // but just in case, we mask the value.
    long v = ((long) (value)) & mask[nBitsInValue];

    // scratch contains up to 64 bits.  we can compute how many bits it contains
    // by looking at the low-order 6 bits of nBits (where nBits is the total
    // number of bits stored in the text.
    int nBitsInScratch = nBits & 0x3F;
    int nFreeInScratch = 64 - nBitsInScratch;

    if (nFreeInScratch < nBitsInValue) {
      // there are not enough available bits in the scratch buffer to store
      // the value, so we need to split it across multiple words.
      int nBitsShort = nBitsInValue - nFreeInScratch;
      long lowPart = v & mask[nFreeInScratch];
      long highPart = v >>> nFreeInScratch;
      scratch |= lowPart << nBitsInScratch;
      nBits += nFreeInScratch;
      moveScratchToText();
      scratch = highPart;
      nBits += nBitsShort;
      marker = 1L<<nBitsShort;
    } else {
      // there is enough room in scratch to just add the bits
      scratch |= v << nBitsInScratch;
      nBits += nBitsInValue;
      marker <<= nBitsInValue;
      if (marker == 0) {
        moveScratchToText();
      }
    }
  }

  /**
   * Gets an array of bytes populated with the content of the encoded text.
   *
   * @return a valid, potentially zero-length, array of bytes.
   */
  public byte[] getEncodedText() {
    int nBytesToEncode = (nBits+7)/8;
    byte [] b = byteBuffer.getBytes(nBytesToEncode);
    
    int nBitsInScratch = nBits & 0x3F;
    if(nBitsInScratch>0){
      long s = scratch;
      int nBytesInScratch = (nBitsInScratch+7)/8;
      int iByte = byteBuffer.getByteCount();
      for(int i=0;i<nBytesInScratch; i++){
        b[iByte++] = (byte)(s&0xffL);
        s>>=8;
      }
    }
    
    
    return b;
  }

  /**
   * Gets the number of bits stored in the BitOutputStore's content.
   *
   * @return a positive integer value.
   */
  public int getEncodedTextLength() {
    return nBits;
  }
  
  public int getEncodedTextLengthInBytes(){
    return (getEncodedTextLength()+7)/8;
  }

  /**
   * Transfers the content of the scratch buffer to the byte buffer and resets
   * the scratch tracking elements
   */
  private void moveScratchToText() {
    byteBuffer.addLong(scratch);
    scratch = 0;  // not strictly necessary, but may help in debugging
    marker = 1;
  }
  
  
  @Override
  public String toString(){
    return "BitOutputStore nBits="+nBits;
  }
}
