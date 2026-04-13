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
 *
 * @author gwluc
 */
class SymbolNode {

  final boolean isLeaf;
  final int symbol;
  int count;
  int bit;
  int nBitsInCode;
  byte[] code;

  // Node for a linked list used to build the Huffman tree
  SymbolNode next;

  // Nodes for organizing the Huffman tree
  SymbolNode left;
  SymbolNode right;

  void clear() {
    count = 0;
    bit = 0;
    next = null;
    left = null;
    right = null;
    nBitsInCode = 0;
    code = null;
  }

  SymbolNode() {
    isLeaf = false;
    symbol = -1;
  }

  SymbolNode(int symbol) {
    this.isLeaf = true;
    this.symbol = symbol;
  }

  SymbolNode(SymbolNode left, SymbolNode right) {
    this.isLeaf = false;
    this.symbol = -1;
    this.left = left;
    this.right = right;
    this.count = right.count + left.count;
    left.bit = 0;
    right.bit = 1;
  }

  @Override
  public String toString() {
    return String.format("%4d %9d %s %3d",
      symbol, count, isLeaf ? "leaf  " : "branch", this.nBitsInCode);
  }

  void appendToOutput(BitOutputStore output) {
    int nFullBytesInCode = nBitsInCode / 8;
    for (int j = 0; j < nFullBytesInCode; j++) {
      output.appendBits(8, code[j]);
    }
    int remainder = nBitsInCode - nFullBytesInCode * 8;
    if (remainder > 0) {
      int test = code[nFullBytesInCode];
      for (int j = nFullBytesInCode * 8; j < nBitsInCode; j++) {
        output.appendBit(test & 1);
        test >>= 1;
      }
    }

  }
}
