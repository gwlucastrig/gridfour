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
package org.gridfour.compress;

import java.util.Arrays;
import org.gridfour.io.BitOutputStore;

/**
 * Implements method and elements for encoding data using Huffman's algorithm.
 */
public class HuffmanEncoder {

  private static class SymbolNode implements Comparable<SymbolNode> {

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
    SymbolNode parent;

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
      SymbolNode self = this;
      left.parent = self;
      right.parent = self;
      left.bit = 0;
      right.bit = 1;
    }

    void setParent(SymbolNode parent) {
      this.parent = parent;
    }

    @Override
    public int compareTo(SymbolNode o) {
      int test = Integer.compare(count, o.count);
      if (test == 0) {
        test = Integer.compare(symbol, o.symbol);
      }
      return test;
    }
  }

  int nLeafNodes;
  int nBranchNodes;
  int nBitsInTree;
  int nBitsInText;
  int nBitsTotal;

  public HuffmanEncoder() {

  }

  SymbolNode makeLeaf(int symbol) {
    nLeafNodes++;
    return new SymbolNode(symbol);
  }

  SymbolNode makeBranch(SymbolNode left, SymbolNode right) {
    nBranchNodes++;
    return new SymbolNode(left, right);
  }

  SymbolNode makeBranch() {
    nBranchNodes++;
    return new SymbolNode();
  }

  void clear() {
    nLeafNodes = 0;
    nBranchNodes = 0;
    nBitsInTree = 0;
    nBitsInText = 0;
    nBitsTotal = 0;
  }

  public boolean encode(BitOutputStore output, int nSymbols, byte[] symbols) {

    clear();
    SymbolNode[] symbolNodes;

    SymbolNode[] sortNodes = new SymbolNode[256];
    symbolNodes = new SymbolNode[256];
    for (int i = 0; i < symbolNodes.length; i++) {
      symbolNodes[i] = new SymbolNode(i);
      sortNodes[i] = symbolNodes[i];
    }
    for (int i = 0; i < nSymbols; i++) {
      symbolNodes[(symbols[i] & 0xff)].count++;
    }
    Arrays.sort(sortNodes);

    int firstIndex = -1;
    for (int i = 0; i < sortNodes.length; i++) {
      if (sortNodes[i].count > 0) {
        firstIndex = i;
        break;
      }
    }
    if (firstIndex == 255) {
      // there is only one symbol in the collection.
      //use a special code to indicate uniform values
      output.appendBits(8, 0);
      output.appendBit(1);
      output.appendBits(8, sortNodes[firstIndex].symbol);
      nBitsInTree = 9;
      nBitsInText = 0;
      nBitsTotal = 9;
      return true;
    }
    SymbolNode firstNode = sortNodes[firstIndex];
    for (int i = firstIndex; i < sortNodes.length - 1; i++) {
      sortNodes[i].next = sortNodes[i + 1];
    }

    nLeafNodes = 256 - firstIndex;
    SymbolNode root = null;
    while (true) {
      SymbolNode left = firstNode;
      SymbolNode right = firstNode.next;
      firstNode = right.next;
      left.next = null;
      right.next = null;
      SymbolNode branch = makeBranch(left, right);
      if (firstNode == null) {
        root = branch;
        break;
      } else if (firstNode.count >= branch.count) {
        branch.next = firstNode;
        firstNode = branch;
      } else {
        SymbolNode node = firstNode.next;
        SymbolNode prior = firstNode;
        while (node != null && node.count < branch.count) {
          prior = node;
          node = node.next;
        }
        prior.next = branch;
        if (node == null) {
          // the branch goes to the end of the chain
          prior.next = branch;
        } else {
          // the branch gets inserted into the chain
          branch.next = node;
        }
      }
    }

    encodeTree(output, root);
    nBitsInTree = output.getEncodedTextLength();
    for (int i = 0; i < nSymbols; i++) {
      int test = symbols[i] & 0xff;
      SymbolNode node = symbolNodes[test];
      int nFullBytesInCode = node.nBitsInCode / 8;
      for (int j = 0; j < nFullBytesInCode; j++) {
        output.appendBits(8, node.code[j]);
      }
      int remainder = node.nBitsInCode - nFullBytesInCode * 8;
      if (remainder > 0) {
        test = node.code[nFullBytesInCode];
        for (int j = nFullBytesInCode * 8; j < node.nBitsInCode; j++) {
          output.appendBit(test & 1);
          test >>= 1;
        }
      }
    }
    nBitsTotal = output.getEncodedTextLength();
    nBitsInText = nBitsTotal - nBitsInTree;

    return true;

  }

  private void encodeTree(BitOutputStore output, SymbolNode root) {
    // Although this traversal could be accomplished much more cleanly
    // using recursion, the depth of recursion could potentially get too
    // large. So we manage the stack the hard way.
    // The maximum depth of the tree is one less than the number of symbols
    // This would occur only for an unusual combination of nodes with
    // counts resembling the Fibonacci sequence.

    // Store the number of leaf nodes.  This value could be as hight as 256,
    // which would ordinarily require 9 bits of storage. But since zero
    // is impossible, we store one less to save a bit.
    output.appendBits(8, nLeafNodes - 1);
    SymbolNode[] path = new SymbolNode[256];
    int[] pathBranch = new int[256];
    path[0] = root;
    pathBranch[0] = 0;
    int depth = 1;
    while (depth > 0) {
      int index = depth - 1;
      SymbolNode pNode = path[index];
      int pBranch = pathBranch[index];
      // pBranch is set as follows:
      //    0  we've just arrived at the node and have not yet
      //       identified whether it is a branch or a leaf.
      //       we have not yet traversed any of its children
      //
      //    1  we traversed down the left branch and need to traverse
      //       down the right
      //
      //    2  we have traversed both branches and are on our way up

      switch (pBranch) {
        case 0:
          // we've just pushed the node on the stack and have not yet
          // identified whether it is a leaf or a branch.
          if (pNode.isLeaf) {
            output.appendBit(1); // terminal
            output.appendBits(8, pNode.symbol);
            BitOutputStore bitpath = encodePath(depth, path);
            pNode.nBitsInCode = bitpath.getEncodedTextLength();
            pNode.code = bitpath.getEncodedText();
            // pop the stack
            depth--;
            index--;
            // pro-forma, clear the stack variables
            pathBranch[depth] = 0;
            path[depth] = null;

          } else {
            output.appendBit(0); // non-terminal
            pathBranch[index] = 1;
            pathBranch[depth] = 0;
            path[depth] = pNode.left;
            depth++;
          }
          break;
        case 1:
          pathBranch[index] = 2;
          pathBranch[depth] = 0;
          path[depth] = pNode.right;
          depth++;
          break;
        case 2:
          // we're on our way up
          pathBranch[index] = 0;
          path[index] = null;
          depth--;
          break;
        default:
          // error condition, won't happen
          throw new IllegalStateException("Internal error encoding tree");
      }
    }
  }

  // TO DO:  This would be better if we could reuse the bitpath
  //         object rather than allocating a new one
  BitOutputStore encodePath(int depth, SymbolNode[] path) {
    BitOutputStore bitpath = new BitOutputStore();
    for (int i = 1; i < depth; i++) {
      SymbolNode node = path[i];
      bitpath.appendBit(node.bit);
    }
    return bitpath;
  }

}
