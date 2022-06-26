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
 * 06/2022  G. Lucas     Streamlined some code, improved processing speed
 *                       by about 10 percent and added better comments.
 *
 * Notes:
 *
 *  Please see the companion class, BitWriter, for documentation about
 *  the bit packing order used by this routine.
 * -----------------------------------------------------------------------
 */
package org.gridfour.io;

/**
 * Writes a series of bits to an internal memory buffer.
 */
public class BitInputStore {

  private static final long mask[] = new long[65];

  static {
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


  private final byte []text;
    private final int nBits;
    private int nBytesProcessed;

  private long scratch;
  private int iBit;
  private int nBitsInScratch;

  /**
   * Construct a reader that will extract bits from the specified input.
   *
   * @param input a valid array of bytes storing the content.
   */
  public BitInputStore(byte[] input) {
    nBits = input.length * 8;
    scratch = 0;
    nBitsInScratch = 0;
    text = input;
  }


    /**
   * Construct a reader that will extract bits from the specified input.
   *
   * @param input a valid array of bytes storing the content.
   * @param offset the starting offset within the input
   * @param length the number of bytes from the input that are valid;
   * it is assumed that input is at least length+offset bytes long.
   */
  public BitInputStore(byte[] input, int offset, int length) {
    if(length+offset>input.length){
      throw new IllegalArgumentException("Insufficient input.length="+input.length
              +" to support specified offset="+offset+", length="+length);
    }
    nBits = length * 8;
    scratch = 0;
    nBitsInScratch = 0;
    text = input;
    nBytesProcessed = offset;
  }

  /**
   * Gets a single bit to the bit-writer content.
   *
   * @return a value of 1 or 0
   */
  public int getBit() {
    if (nBitsInScratch == 0) {
      if (iBit >= nBits) {
        throw new ArrayIndexOutOfBoundsException("Attempt to read past end of data");
      }
      moveTextToScratch();
    }

    int bit = (int) (scratch & 1L);
    scratch >>>= 1;
    nBitsInScratch--;
    iBit++;
    return bit;
  }

  /**
   * Gets the specified number of bits from the context
   *
   * @param nBitsInValue number of bits in the range 1 to 32.
   * @return a valid integer value composed using the specified number of bits
   * from the content.
   */
  public int getBits(int nBitsInValue) {
    if(nBitsInValue<1 || nBitsInValue>32){
      throw new IllegalArgumentException(
              "Attempt to get a number of bits not in range [1..32]: "
                      +nBitsInValue);
    }

    // The assumption here is that there will usually be sufficient
    // bits remaining in scratch to satisfy the request.  So we test
    // for a simplified-processing condition.
    if (nBitsInScratch >= nBitsInValue) {
      int v = (int) (scratch & mask[nBitsInValue]);
      scratch >>>= nBitsInValue;
      nBitsInScratch -= nBitsInValue;
      iBit += nBitsInValue;
      return v;
    }

    // There are insufficient bits remaining in scratch.
    // We will have to fetch some from the text.  Verify that there will
    // be sufficient bits to satisfy the request.
    if (iBit + nBitsInValue > nBits) {
      throw new ArrayIndexOutOfBoundsException("Attempt to read past end of data");
    }


    // if we get here, we know that nBitsInScratch < nBitsInValue
    // Use what we have (nBitsInScratch could be zero) and shift
    // in additional data from the text
    long v = scratch;
    int nBitsShort = nBitsInValue - nBitsInScratch;
    int nBitsCopied = nBitsInScratch;
    moveTextToScratch();
    v |= (scratch & mask[nBitsShort]) << nBitsCopied;
    scratch >>>= nBitsShort;
    nBitsInScratch -= nBitsShort;
    iBit += nBitsInValue;

    return (int) v;
  }


  /**
   * Transfers the content of the scratch buffer to the main text arrays. If
   * necessary, the storage for the text will be expanded. The marker element
   * will be reset to 1.
   * <p>
   * This method assumes that a check has already been performed to verify
   * that there is data remaining in the text.
   */
  private void moveTextToScratch() {

    if (nBytesProcessed + 8 <= text.length) {
      // there are enough bytes to populate an entire long
      // use a variation on Horners rule to unpack the content
      scratch = ((((((text[nBytesProcessed + 7] << 8
        | (text[nBytesProcessed + 6] & 0xffL)) << 8
        | (text[nBytesProcessed + 5] & 0xffL)) << 8
        | (text[nBytesProcessed + 4] & 0xffL)) << 8
        | (text[nBytesProcessed + 3] & 0xffL)) << 8
        | (text[nBytesProcessed + 2] & 0xffL)) << 8
        | (text[nBytesProcessed + 1] & 0xffL)) << 8
        | (text[nBytesProcessed] & 0xffL);
      nBytesProcessed += 8;
      nBitsInScratch = 64;
    } else {
      int k = 0;
      scratch = 0;
      for (int i = text.length - 1; i >= nBytesProcessed; i--) {
        scratch <<= 8;
        scratch |= text[i] & 0xff;
        k++;
      }
      nBytesProcessed += k;
      nBitsInScratch = k * 8;
    }
  }


  /**
   * Gets the current bit position within the input store.
   * This is the position from which the next bit will be read.
   * @return a value of zero or greater.
   */
  public int getPosition(){
    return iBit;
  }
}
