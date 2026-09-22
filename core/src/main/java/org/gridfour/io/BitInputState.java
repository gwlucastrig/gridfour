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
  public final byte[] buffer;
  public final int byteOffset0;
  public int scratch;
  public int nBitsInScratch;
  public int nBytesProcessed;

  BitInputState(byte []buffer, int byteOffset0, int iByte, int scratch, int nBitsInScratch){
    this.buffer = buffer;
    this.byteOffset0 = byteOffset0;
    this.nBytesProcessed = iByte;
    this.nBitsInScratch = nBitsInScratch;
    this.scratch = scratch;
  }
}
