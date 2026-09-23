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
 * 10/2026  G. Lucas     Created to allow BitInputStore elements to be integrated
 *                       directly  into calling modules to reduce overhead.
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.io;

/**
 * Provides elements from a BitInputStore instance to support direct
 * integration of bit access into a calling application.
 */
public class BitInputState {
  /**
   * The array containing the bits to be extracted.
   */
  public final byte[] source;
  /**
   * The offset specified by the calling module to skip
   * the initial bytes in the source array.
   */
  public final int sourceOffset;
  /**
   * The array index for the next byte to be extracted from
   * the source data.  The total number of bytes used is
   * sIndex+sourceOffset.
   */
  public final int sIndex;
  /**
   * A temporary field used to marshal bits from the source
   * array for access.  In practice, this field never
   * contains more than 31 bits (the sign bit is never set).
   */
  public final int scratch;

  /**
   * The number of bits in the scratch array.
   */
  public final int nBitsInScratch;


  BitInputState(byte []source, int sourceIndex, int sIndex, int scratch, int nBitsInScratch){
    this.source = source;
    this.sourceOffset = sourceIndex;
    this.sIndex = sIndex;
    this.nBitsInScratch = nBitsInScratch;
    this.scratch = scratch;
  }
}
