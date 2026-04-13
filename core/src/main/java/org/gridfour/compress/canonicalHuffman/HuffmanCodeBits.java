/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2026  Gary W. Lucas.

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
 * 03/2026  G. Lucas     Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress.canonicalHuffman;

import org.gridfour.io.BitOutputStore;

/**
 * Provides temporary storage for a sequence of bits while building a
 * Huffman tree.
 */
class HuffmanCodeBits {

  int nBitsInCode;
  long bits;

  HuffmanCodeBits(int length) {
    nBitsInCode = length;
    //  recall that bits = 0;
  }

  HuffmanCodeBits(HuffmanCodeBits source, int length) {
    bits = source.bits + 1;
    if (length > source.nBitsInCode) {
      bits = bits << (length - source.nBitsInCode);
      nBitsInCode = length;
    } else {
      nBitsInCode = source.nBitsInCode;
    }
  }

  byte[] getCodeBytes() {
    BitOutputStore bitpath = new BitOutputStore();
    for (int i = nBitsInCode - 1; i >= 0; i--) {
      bitpath.appendBit((int) (bits >> i) & 1);
    }
    return bitpath.getEncodedText();
  }
}
