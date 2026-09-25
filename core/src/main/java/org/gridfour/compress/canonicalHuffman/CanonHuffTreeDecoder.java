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

import java.util.Arrays;
import org.gridfour.io.BitInputStore;

/**
 * Provides a utility for decoding a canonical Huffman code.
 */
class CanonHuffTreeDecoder {

  final int nUniqueSymbols;
  final int[] firstCode = new int[17];
  final int[] maxCode = new int[17];
  final int[] firstSymbolIndex = new int[17];
  final int[] connellSymbol;

  final int[] qLen;
  final int[] qSymbol;
  final int[] qBits;

  /**
   * Given an array of symbol lengths, constructs a representation of the
   * corresponding canonical Huffman tree that is suitable for efficient
   * decoding of the corresponding encoded text.
   * <p>
   * The symbol lengths are given as an array corresponding to the complete
   * symbol set (alphabet) in the encoding. In some cases, symbols may
   * be encoded with zero-lengths to indicate that they are not used in
   * the encoding.
   *
   * @param symbolLengths a valid array of lengths with a one-to-one
   * correspondence
   * to the elements of the symbol set.
   * @param isQuickEntryRequested indicates whether the quick-entry elements
   * should be populated.
   */
  CanonHuffTreeDecoder(int[] symbolLengths, boolean isQuickEntryRequested) {
    int nSymbols = symbolLengths.length; // will include end-of-text symbol
    if (isQuickEntryRequested) {
      qLen = new int[256];
      qSymbol = new int[256];
      qBits = new int[256];
    } else {
      qLen = new int[0];
      qSymbol = new int[0];
      qBits = new int[0];
    }
    // Because the maximum length of a bit code is 16, the total number
    // of combined lengths and symbol codes is managable. So we can avoid
    // a Java sort and instead use an array based approach.
    // In order to sort the symbol nodes, we create an array of booleans
    // indexed on length and symbol code (in that order).  We mark all the
    // ones that exist.  Then we populate the sortNodes array by looping
    // through the "populated" array to see which ones actually occur.
    // Testing revealed a saving of about 2% on total run time.
    //
    boolean[] populated = new boolean[nSymbols * 16];
    int n = 0;
    SymbolNode[] symbolNodes = new SymbolNode[nSymbols];
    for (int i = 0; i < nSymbols; i++) {
      symbolNodes[i] = new SymbolNode(i);
      symbolNodes[i].nBitsInCode = symbolLengths[i];
      if (symbolLengths[i] > 0) {
        n++;
        int index = (symbolLengths[i] - 1) * nSymbols + i;
        populated[index] = true;
      }
    }

    nUniqueSymbols = n;
    SymbolNode[] sortNodes = new SymbolNode[nUniqueSymbols];
    int nSort = 0;
    for (int i = 0; i < populated.length; i++) {
      if (populated[i]) {
        int index = i % nSymbols;
        sortNodes[nSort++] = symbolNodes[index];
      }
    }

    int[] codeBits = new int[sortNodes.length];
    int length = sortNodes[0].nBitsInCode;
    int bits = 0;
    for (int i = 1; i < sortNodes.length; i++) {
      int s = sortNodes[i].nBitsInCode;
      bits++;
      if (s > length) {
        bits = bits << (s - length);
        length = s;
      }
      codeBits[i] = bits;
    }

    // Populate the elements related to Connell's algorithm ------------
    connellSymbol = new int[sortNodes.length];
    for (int i = 0; i < sortNodes.length; i++) {
      connellSymbol[i] = sortNodes[i].symbol;
    }

    Arrays.fill(maxCode, -1);

    for (int i = 0; i < sortNodes.length; i++) {
      int len = sortNodes[i].nBitsInCode;
      int q = codeBits[i];
      firstCode[len] = q; // (int)codeBits[i].bits;
      firstSymbolIndex[len] = i;
      n = 1;
      for (int j = i + 1; j < sortNodes.length; j++) {
        if (sortNodes[j].nBitsInCode == len) {
          n = j - i + 1;
        } else {
          break;
        }
      }
      maxCode[len] = firstCode[len] + n - 1;
      i += (n - 1);
    }

    if (isQuickEntryRequested) {
      // populate the quick-entry elements ----------------
      // xmit variable is the bit code formatted in the same order
      // as appears in the BitInputStream class.  It is the mirror
      // image of the code-bits, except that we only capture the
      // first 8 bits max.
      for (int i = 0; i < sortNodes.length; i++) {
        int symbol = sortNodes[i].symbol;
        int len = sortNodes[i].nBitsInCode;
        int q = codeBits[i];
        n = len > 8 ? 8 : len;
        int xmit = (q >> (len - 1)) & 1;
        for (int j = 1; j < n; j++) {
          int bit = (q >> (len - 1 - j)) & 1;
          xmit |= (bit << j);
        }
        int jStep = 1 << n;
        for (int j = xmit; j < 256; j += jStep) {
          qLen[j] = len;
          qSymbol[j] = symbol;
          // qBits will be used only when len>8
          qBits[j] = len>8 ? (q >> (len - 8)) & 0xff : 0;
        }
      }
    }
  }

  boolean decodeTree(BitInputStore input, int nSymbols, int[] symbols) {
    // Extract the symbols with lengths from the Huffman-coded tree structure
    // which will subsequently be used to decode the main body of the encoded
    // text.  Symbols that are not included in the text will have lengths
    // of zero and will be skipped. Because there are only 260 possible symbols
    // in the Huffman encoded tree, we have elected to use Connell's algorithm
    // without the quick-entry table.  We do so to illustrate how the algorithm
    // works.  We could use the quick-entry approach used in the main body
    // code (in the CanonicalHuffman class).  While it would be faster, this
    // method executes fast enough that the overhead reduction would be small.
    int prior = 0;
    int n;
    int i;
    for (i = 0; i < nSymbols; i++) {
      int codeVal = 0;
      int length = 0;
      int symbol = 0;
      while (true) {
        int bit = input.getBit();
        codeVal = (codeVal << 1) | bit;
        length++;
        if (codeVal <= maxCode[length]) {
          int offset = codeVal - firstCode[length];
          symbol = connellSymbol[firstSymbolIndex[length] + offset];
          break;
        }
      }
      if (symbol <= LengthEncoder.MAX_STANDARD_SYMBOL) {
        symbols[i] = symbol;
        prior = symbol;
      } else {
        switch (symbol) {
          case LengthEncoder.REPEAT_PREV_2BITS:
            n = input.getBits(2) + 3;
            for (int j = 0; j < n; j++) {
              symbols[i + j] = prior;
            }
            i += n - 1;
            break;
          case LengthEncoder.REPEAT_ZERO_3BITS:
            prior = 0;
            n = input.getBits(3) + 3;
            for (int j = 0; j < n; j++) {
              symbols[i + j] = 0;
            }
            i += n - 1; // the loop-control will increment i
            break;
          case LengthEncoder.REPEAT_ZERO_7BITS:
            prior = 0;
            n = input.getBits(7) + 11;
            for (int j = 0; j < n; j++) {
              symbols[i + j] = 0;
            }
            i += n - 1;
            break;
          default:
            break;
        }
      }
    }
    return true;
  }

}
