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
 * 06/2022  G. Lucas     Refactored to use an integer-based index to
 *                         represent the Huffman tree.
 *
 * Notes:
 *
 *  The reference implementation of the Huffman decoder implements the
 * Huffman tree using a ordinary class for nodes.  That is a completely
 * reasonable approach, but it is perplexingly slow.  So we refactored
 * this class to represent the Huffman tree in an array of integers.
 * It's a very old-school way of doing things and I am currently
 * baffled as to why it should be necessary.
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress;

import org.gridfour.io.BitInputStore;

/**
 * Implements method and elements for encoding data using Huffman's algorithm.
 */
public class HuffmanDecoder {

  int nLeafNodes;
  int nBitsInTree;

  private void clear() {
    nLeafNodes = 0;
    nBitsInTree = 0;
  }

  private int[] decodeTree(BitInputStore store) {
    int bit0 = store.getPosition();
    int nLeafsToDecode = store.getBits(8) + 1;
    nLeafNodes = nLeafsToDecode;
    int rootBit = store.getBit();
    if (rootBit == 1) {
      // This is the special case where a non-zero root
      // bit indicates that there is only one symbol in the whole
      // encoding.   There is not a proper tree, only a single root node.
      int[] nodeIndex = new int[1];
      nodeIndex[0] = store.getBits(8);
      nBitsInTree = store.getPosition() - bit0;
      return nodeIndex;
    }

    // The array based representation of the Huffman tree
    // is laid out as triplets of integer values each
    // representing a node
    //     [offset+0] symbol code (or -1 for a branch node)
    //     [offset+1] index to left child node (zero if leaf)
    //     [offset+2] index to right child node (zero if leaf)
    // The maximum number of symbols is 256.  The number of nodes in
    // a Huffman tree is always 2*n-1.  We allocate 3 integers per
    // node, or 2*n*3.
    int[] nodeIndex = new int[nLeafsToDecode * 6];
    int nodeIndexCount;

    // The maximum depth of a Huffman tree is
    // the number of symbols minus 1. Since there are a maximum of 256
    // symbols, we set the stack size to 256 to ensure that it will accomodate
    // even the deepest tree. The probability of that actually happening
    // is extremely small and such a tree would never be accepted as a
    // compressed encoding by Gridfour because it would result in a
    // encode text larger than its source.
    //
    // Initialization:
    //    The root node is virtually placed on the base of the stack.
    // It's left and right child-node references are zeroed out.
    // The node type is set to -1 to indicate a branch node.
    // The iStack variable is always set to the index of the element
    // on top of the stack.
    int[] stack = new int[nLeafsToDecode + 1];
    int iStack = 0;
    nodeIndex[0] = -1;
    nodeIndex[1] = 0;
    nodeIndex[2] = 0;
    nodeIndexCount = 3;

    int nLeafsDecoded = 0;
    while (nLeafsDecoded < nLeafsToDecode) {
      int offset = stack[iStack];
      // We are going to generate a new node. It will be stored
      // in the node-index array starting at position nodeIndexCount.
      // We are going to store an integer reference to the new node as
      // one of the child nodes of the node at the current position
      // on the stack.  If the left-side node is already populates (offset+1),
      // we will store the reference as the right-side child node (offset+2).
      if (nodeIndex[offset + 1] == 0) {
        nodeIndex[offset + 1] = nodeIndexCount;
      } else {
        nodeIndex[offset + 2] = nodeIndexCount;
      }

      int bit = store.getBit();
      if (bit == 1) {
        // leaf node
        nLeafsDecoded++;
        nodeIndex[nodeIndexCount++] = store.getBits(8);
        nodeIndex[nodeIndexCount++] = 0; // not required, just a diagnostic aid
        nodeIndex[nodeIndexCount++] = 0; // not required, just a diagnostic aid

        if (nLeafsDecoded == nLeafsToDecode) {
          // the tree will be fully populated, all nodes saturated.
          // there will be no open indices left to populate.
          // no further processing is required for the tree.
          break;
        }
        // pop upwards on the stack until you find the first node with a
        // non-populated right-side node reference. This may, in fact,
        // be the current node on the stack.
        while (nodeIndex[offset + 2] != 0) {
          iStack--;
          offset = stack[iStack];
        }
      } else {
        // branch node, create a new branch node an push it on the stack
        iStack++;
        stack[iStack] = nodeIndexCount;
        nodeIndex[nodeIndexCount++] = -1;
        nodeIndex[nodeIndexCount++] = 0; // left node not populated
        nodeIndex[nodeIndexCount++] = 0; // right node not populated
      }
    }

    nBitsInTree = store.getPosition() - bit0;
    return nodeIndex;
  }

  public boolean decode(BitInputStore input, int nSymbols, byte[] symbols) {
    clear();

    // Decode the tree.  If the decoding detects a uniform encoding
    // (all values are the same), just copy out the symbol at nodeIndex[0]
    // and then return.
    int[] nodeIndex = decodeTree(input);
    if (nodeIndex.length == 1) {
      // uniform encoding
      byte symbol = (byte) nodeIndex[0];
      for (int i = 0; i < nSymbols; i++) {
        symbols[i] = symbol;
      }
      return true;
    }

    for (int i = 0; i < nSymbols; i++) {
      int offset = nodeIndex[1 + input.getBit()]; // start from the root node
      while (nodeIndex[offset] == -1) {
        offset = nodeIndex[offset + 1 + input.getBit()];
      }
      symbols[i] = (byte) nodeIndex[offset];
    }
    return true;
  }

  /**
   * Gets the number of bits in the Huffman encoding tree for
   * the most recently processed data set.
   *
   * @return a positive value, or zero if nothing was ever processed.
   */
  public int getBitsInTreeCount() {
    return nBitsInTree;
  }
}
