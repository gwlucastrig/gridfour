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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gridfour.io.BitInputStore;

/**
 * Provides a utility for decoding a canonical Huffman code.
 */
 class CanonHuffTreeDecoder {

  final int[] nodeIndex;
  final int nUniqueSymbols;

  /**
   * Given an array of symbol lengths, constructs a representation of the
   * corresponding canonical Huffman tree that is suitable for efficient
   * decoding of the corresponding encoded text.
   * <p>
   * The symbol lengths are given as an array corresponding to the complete
   * symbol set (alphabet) in the encoding.  In some cases, symbols may
   * be encoded with zero-lengths to indicate that they are not used in
   * the encoding.
   * @param symbolLengths a valid array of lengths with a one-to-one correspondence
   * to the elements of the symbol set.
   */
  CanonHuffTreeDecoder(int[] symbolLengths) {
    int nSymbols = symbolLengths.length; // will include end-of-text symbol
    List<SymbolNode> list = new ArrayList<>();
    SymbolNode[] symbolNodes = new SymbolNode[nSymbols];
    for (int i = 0; i < nSymbols; i++) {
      symbolNodes[i] = new SymbolNode(i);
      symbolNodes[i].nBitsInCode = symbolLengths[i];
      if (symbolLengths[i] > 0) {
        list.add(symbolNodes[i]);
      }
    }
    nUniqueSymbols = list.size();
    SymbolNode[] sortNodes = list.toArray(SymbolNode[]::new);
    Arrays.sort(sortNodes, (SymbolNode o1, SymbolNode o2) -> {
      int test = o1.nBitsInCode - o2.nBitsInCode;
      if (test == 0) {
        return o1.symbol - o2.symbol;
      }
      return test;
    });

    HuffmanCodeBits[] codeBits = new HuffmanCodeBits[sortNodes.length];
    codeBits[0] = new HuffmanCodeBits(sortNodes[0].nBitsInCode);
    for (int i = 1; i < sortNodes.length; i++) {
      codeBits[i] = new HuffmanCodeBits(codeBits[i - 1], sortNodes[i].nBitsInCode);
    }

    int n = nSymbols * 2 + 2;
    nodeIndex = new int[n * 3];
    int nUsed = 3;
    Arrays.fill(nodeIndex, -1);

    for (int iNode = 0; iNode < sortNodes.length; iNode++) {
      SymbolNode node = sortNodes[iNode];
      int index = 0;
      long bits = codeBits[iNode].bits;
      for (int i = node.nBitsInCode - 1; i >= 0; i--) {
        int bit = (int) ((bits >> i) & 1);
        int test = nodeIndex[index + 1 + bit];
        if (test < 0) {
          nodeIndex[index + 1 + bit] = nUsed;
          index = nUsed;
          nUsed += 3;
        } else {
          index = test;
        }
      }
      nodeIndex[index] = node.symbol;

    }
  }

   boolean decodeTree(BitInputStore input, int nSymbols, int[] symbols) {

    // Decode the tree.
    int prior = 0;
    int n;
    int i;
    for (i = 0; i < nSymbols; i++) {
      int offset = nodeIndex[1 + input.getBit()]; // start from the root node
      while (nodeIndex[offset] == -1) {
        offset = nodeIndex[offset + 1 + input.getBit()];
      }
      int test = nodeIndex[offset];
      if (test <= LengthEncoder.MAX_STANDARD_SYMBOL) {
        symbols[i] = test;
        prior = test;
      } else {
        switch (test) {
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

   /**
    * Decode the specified number of symbols from the bit store.
    * The number of symbols to be extracted is not necessarily the
    * complete number of symbols in the input set.
    * @param input a valid instance to provide bits for decoding.
    * @param nSymbolsInText the number of symbols to be extracted.
    * @param text an array dimensioned large enought to receive
    * the indicated number of symbols.
    * @return if successful, true; false values are not returned at this time.
    */
   boolean decode(BitInputStore input, int nSymbolsInText, int[] text) {

    for (int i = 0; i < nSymbolsInText; i++) {
      int offset = nodeIndex[1 + input.getBit()]; // start from the root node
      while (nodeIndex[offset] == -1) {
        offset = nodeIndex[offset + 1 + input.getBit()];
      }
      text[i] = nodeIndex[offset];
    }

    return true;
  }
}
