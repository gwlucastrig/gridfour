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
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import org.gridfour.io.BitInputStore;

/**
 * Implements method and elements for encoding data using Huffman's algorithm.
 */
public class HuffmanDecoder {

  private static class SymbolNode {

    final boolean isLeaf;
    final int symbol;

    // Nodes for organizing the Huffman tree
    SymbolNode left;
    SymbolNode right;
    SymbolNode parent;

    SymbolNode() {
      isLeaf = false;
      symbol = -1;
    }

    SymbolNode(int symbol) {
      this.isLeaf = true;
      this.symbol = symbol;
    }
  }

  int nLeafNodes;
  int nBranchNodes;
  int nBitsInTree;

  public HuffmanDecoder() {

  }

  SymbolNode makeLeaf(int symbol) {
    nLeafNodes++;
    return new SymbolNode(symbol);
  }

  SymbolNode makeBranch() {
    nBranchNodes++;
    return new SymbolNode();
  }

  void clear() {
    nLeafNodes = 0;
    nBranchNodes = 0;
    nBitsInTree=0;
  }

  private SymbolNode decodeTree(BitInputStore store) {
    int nLeafsToDecode = store.getBits(8) + 1;
    int rootBit = store.getBit();
    if (rootBit == 1) {
      // This is the special case where a non-zero root
      // bit indicates that there is only one symbol in the whole
      // encoding.   There is not a proper tree, only a single root node.
      int symbol = store.getBits(8);
      return makeLeaf(symbol);
    }

    SymbolNode root = makeBranch();
    SymbolNode node = root;
    int nLeafsDecoded = 0;
    while (nLeafsDecoded < nLeafsToDecode) {
      int bit = store.getBit();
      if (bit == 1) {
        // leaf node
        int symbol = store.getBits(8);
        SymbolNode leaf = makeLeaf(symbol);
        nLeafsDecoded++;
        leaf.parent = node;
        if (node.left == null) {
          node.left = leaf;
        } else {
          node.right = leaf;
          node = node.parent;
          while (node != null && node.right != null) {
            node = node.parent;
          }
          if (node == null) {
            // we've just populated the right branch of the root node
            // with a leaf.  We are done.
            assert nLeafsDecoded == nLeafsToDecode : "Incomplete tree encoding";
            break;
          }
        }
      } else if (node.left == null) {
        SymbolNode child = makeBranch();
        child.parent = node;
        node.left = child;
        node = child;
      } else {
        SymbolNode child = makeBranch();
        child.parent = node;
        node.right = child;
        node = child;
      }
    }

    return root;
  }

  public boolean decode(BitInputStore input, int nSymbols, byte[] symbols) {
    clear();

    int bit0 = input.getPosition();
    SymbolNode root = decodeTree(input);
    int bit1 = input.getPosition();
    nBitsInTree = bit1-bit0;
    if (root.isLeaf) {
      byte symbol = (byte) (root.symbol);
      for (int i = 0; i < nSymbols; i++) {
        symbols[i] = symbol;
      }
      return true;
    }

    for (int i = 0; i < nSymbols; i++) {
      SymbolNode node = root;
      while (!node.isLeaf) {
        if (input.getBit() == 0) {
          node = node.left;
        } else {
          node = node.right;
        }
      }
      symbols[i] = (byte) node.symbol;
    }
    return true;
  }

    public int getBitsInTreeCount() {
        return this.nBitsInTree;
    }
}
