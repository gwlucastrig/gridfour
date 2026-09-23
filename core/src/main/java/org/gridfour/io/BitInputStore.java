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
 * 10/2026  G. Lucas     Refactored to allow logic to be integrated directly
 *                       into calling modules to reduce overhead.
 *
 * Notes:
 *
 *  Please see the companion class, BitWriter, for documentation about
 *  the bit packing order used by this routine.
 * -----------------------------------------------------------------------
 */
package org.gridfour.io;

/**
 * Writes a series of bits to an internal memory source.
 */
public class BitInputStore {

  private static final int mask[] = {
	0x00,
	0x01,
	0x03,
	0x07,
	0x0f,
	0x1f,
	0x3f,
	0x7f,
	0xff
};

  private final byte[] source; // the byte source
  private final int sourceOffset;  // initial byte offset

  private int scratch;
  private int nBitsInScratch;
  private int sIndex;

  int scratchMark;
  int nBitsInSourceMark;
  int sIndexMark;

  public void mark(){
    scratchMark = scratch;
    nBitsInSourceMark = nBitsInScratch;
    sIndexMark = sIndex;
  }

  public void reset(){
    scratch = scratchMark;
    nBitsInScratch = nBitsInSourceMark;
    sIndex = sIndexMark;
  }
  /**
   * Construct a reader that will extract bits from the specified input.
   *
   * @param input a valid array of bytes storing the content.
   */
  public BitInputStore(byte[] input) {
    sourceOffset = 0;
    source = input;

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
    if (length + offset > input.length) {
      throw new IllegalArgumentException("Insufficient input.length=" + input.length
        + " to support specified offset=" + offset + ", length=" + length);
    }
    source = input;
    sourceOffset = offset;
    sIndex = offset;
  }

  /**
   * Gets a single bit to the bit-writer content.
   *
   * @return a value of 1 or 0
   */
  public int getBit() {
    if (nBitsInScratch == 0) {
      scratch = source[sIndex++]&0xff;
      nBitsInScratch = 8;
    }

    int bit = scratch & 1;
    scratch >>=1;
    nBitsInScratch--;
    return bit;
  }

  public int getByte(){

	if (nBitsInScratch == 0) {
		// note that the value of nBitsInScratch will remain as nBitsInScratch = 0;
		// scratch is already invalid, and it will remain so.
		return source[sIndex++]&0xff;
	}
	else if (nBitsInScratch < 8) {
		scratch = ((source[sIndex++]&0xff) << nBitsInScratch) | scratch;
		nBitsInScratch += 8;
	}

	int result = scratch & 0xff;
	scratch >>= 8;
	nBitsInScratch -= 8;

	return result;
  }



  /**
   * Gets the specified number of bits from the context
   *
   * @param nBitsInValue number of bits in the range 1 to 8.
   * @return a valid integer value composed using the specified number of bits
   * from the content.
   */
  public int getBits(int nBitsInValue) {
    //    assert nBitsInValue<1 || nBitsInValue>8 :
    //              "Get number of bits not in range [1..8]: " + nBitsInValue;

    if (nBitsInValue > 8 || nBitsInValue < 1) {
      return 0;
    }
    if (nBitsInScratch < nBitsInValue) {
      scratch = (((source[sIndex++]&0xff) << nBitsInScratch) | scratch);
      nBitsInScratch += 8;
    }
    int result = scratch & mask[nBitsInValue];
    scratch >>= nBitsInValue;
    nBitsInScratch -= nBitsInValue;
    return result;
  }

  /**
   * Gets the current bit position within the input store.
   * This is the position from which the next bit will be read.
   *
   * @return a value of zero or greater.
   */
  public int getPosition() {
    if(nBitsInScratch==0){
      // sIndex is the index of the next byte we will read
      return (sIndex-sourceOffset)*8;
    }else{
      // sIndex has been advanced to point at the next byte to be taken.
    return (sIndex-1-sourceOffset)*8+nBitsInScratch;
    }
  }

  /**
   * Gets the state elements from an input store in order to allow its
   * content to be accessed directly by a calling application. This approach
   * is useful in cases where a very large number of bits are read from
   * a store. It avoids the overhead due to method calls.
   * @return a valid instance.
   */
  public BitInputState getState(){
  return new BitInputState(source,  sourceOffset,  sIndex,  scratch,  nBitsInScratch);
  }

   /**
   * Sets the state elements for an input store to allow an application
   * to return control to the instance when it is processing the
   * content directly.  This approach
   * is useful in cases where a very large number of bits are read from
   * a store. It avoids the overhead due to method calls.
   * @param sIndex the number of bytes processed
   * @param scratch the current scratch bits
   * @param nBitsInScratch the number of bits in scratch
   */
  public void setState(int sIndex, int scratch, int nBitsInScratch){
    this.sIndex = sIndex;
    this.scratch = scratch;
    this.nBitsInScratch = nBitsInScratch;
  }

}
