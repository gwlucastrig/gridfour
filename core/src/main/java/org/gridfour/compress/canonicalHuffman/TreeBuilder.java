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
import java.util.Comparator;
import org.gridfour.io.BitOutputStore;

/**
 * A utility class for building a Huffman tree from a set of symbol nodes.
 */
class TreeBuilder {

  SymbolNode[] symbolNodes;

  boolean treeDepthLimited;

  TreeBuilder() {

  }

  /**
   * Builds the Huffman tree based on the count values in the
   * input symbol nodes and populates them with canonical Huffman
   * codes. This method assumes that the symbols nodes include an
   * end-of-text node of count 1, as well as at least one other symbol
   * node with a count of 1 or greater. Nodes with zero-counts will not
   * be assigned Huffman codes.
   * <p>
   * At this time, this method restricts the maximum length of the
   * canonical Huffman codes to 63 bits.
   *
   * @param symbolNodes a set of symbol nodes representing the
   * alphabet for the text to be encoded with counts populated.
   * @return
   */
  int buildTree(SymbolNode[] symbolNodes) {
    // The symbol counts have already been established.
    // Use them to populate the canonical Huffman coding sequences.
    treeDepthLimited = false;

    this.symbolNodes = symbolNodes;
    int k = 0;
    for (int i = 0; i < symbolNodes.length; i++) {
      if (symbolNodes[i].count == 0) {
        // pro-forma set code length to zero.  In most cases,
        // this value should already be set by the calling module.
        symbolNodes[i].nBitsInCode = 0;
      } else {
        k++;
      }
    }

    SymbolNode[] sortNodes = new SymbolNode[k];
    k = 0;
    for (int i = 0; i < symbolNodes.length; i++) {
      if (symbolNodes[i].count > 0) {
        sortNodes[k++] = symbolNodes[i];
      }
    }

    Comparator<SymbolNode> countSymbolComp = new Comparator<SymbolNode>() {
      @Override
      public int compare(SymbolNode o1, SymbolNode o2) {
        // The sorting order is designed to support both the initial
        // non-canonical Huffman code tree and the final canonical tree.
        // The traditional algorithm depends on an initial sort giving the
        // leaf nodes in the order least-priority to most-priority.
        // The first node in the sorted list will eventually be assigned the
        // longest bit sequence (sometimes, there may be others of the same length,
        // but none should be longer). The end-of-text symbol has a count of one.
        // There may be other symbols with the same count, but the end-of-text
        // node has the higest integer symbol code (one-greater than the maximum
        // ordinary symbol). To ensure it is first in the list, we use
        // the following sorting keys
        //     primary key:    the count, ascending
        //     secondary key:  the symbol, descending
        int test = Integer.compare(o1.count, o2.count);
        if (test == 0) {
          test = Integer.compare(o2.symbol, o1.symbol);
        }
        return test;
      }
    };

    Arrays.sort(sortNodes, countSymbolComp);
    SymbolNode firstNode = sortNodes[0];
    for (int i = 0; i < sortNodes.length - 1; i++) {
      sortNodes[i].next = sortNodes[i + 1];
    }
    sortNodes[sortNodes.length - 1].next = null;

    SymbolNode root = null;
    while (true) {
      SymbolNode left = firstNode;
      SymbolNode right = firstNode.next;
      firstNode = right.next;
      left.next = null;
      right.next = null;
      SymbolNode branch = new SymbolNode(left, right);
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

    int maxCodeLength = establishCodeLengths(root, sortNodes.length);
    if (maxCodeLength > 15) {
      treeDepthLimited = true;
      PackageMerge pm = new PackageMerge();
      pm.merge(15, sortNodes);
    }

    Arrays.sort(sortNodes, countSymbolComp);
    for (int i = 0; i < sortNodes.length - 1; i++) {
      sortNodes[i].next = sortNodes[i + 1];
    }
    sortNodes[sortNodes.length - 1].next = null;

    populateCanonicalCodes(sortNodes);
    return maxCodeLength;
  }

  /**
   * Traverses the Huffman tree and uses it to
   * compute the lengths of the bit sequence for each symbol.
   * At the end of this method call, symbols will be populated with
   * the number of bits required to encode them.
   *
   * @param root a valid branch node giving the root of the tree.
   * @param nSymbols the number of populated symbols in the symbol set.
   * @return the maximum code length determined computed
   */
  private int establishCodeLengths(SymbolNode root, int nSymbols) {
    // Although this traversal could be accomplished more cleanly
    // using recursion, the depth of recursion could potentially get too
    // large. So we manage the stack the hard way.
    // The maximum depth of the tree is one less than the number of symbols
    // This would occur only for an unusual combination of nodes with
    // counts resembling the Fibonacci sequence.

    // the maximum possible code length should be one less than the
    // number of symbols.  In practice we hope that the maximum code
    // length is rather less than that.  We allocate memory for tracing
    // through the tree based on nSymbols+2 to allow a bit of extra storage.
    int maxCodeLength = 0;
    SymbolNode[] path = new SymbolNode[nSymbols + 2];
    int[] pathBranch = new int[nSymbols + 2];
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
            // pop the stack
            depth--;
            index--;
            pNode.nBitsInCode = depth;
            if (depth > maxCodeLength) {
              maxCodeLength = depth;
            }
            // pro-forma, clear the stack variables
            // this is used for diagnostic purposes
            pathBranch[depth] = 0;
            path[depth] = null;
          } else {
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

    return maxCodeLength;
  }

  /**
   * Populates the bit sequences for the canonical Huffman codes.
   * Note that the input nodes will be sorted based on increasing
   * code length (primary key) and lexical symbol order (secondary key).
   *
   * @param sortNodes the array of symbol nodes with count greater than zero.
   */
  private void populateCanonicalCodes(SymbolNode[] sortNodes) {
    Arrays.sort(sortNodes, (SymbolNode o1, SymbolNode o2) -> {
      int test = o1.nBitsInCode - o2.nBitsInCode;
      if (test == 0) {
        test = o1.symbol - o2.symbol;
      }
      return test;
    });

    HuffmanCodeBits[] codeBits = new HuffmanCodeBits[sortNodes.length];
    codeBits[0] = new HuffmanCodeBits(sortNodes[0].nBitsInCode);
    for (int i = 1; i < sortNodes.length; i++) {
      codeBits[i] = new HuffmanCodeBits(codeBits[i - 1], sortNodes[i].nBitsInCode);
    }

    for (int i = 0; i < sortNodes.length; i++) {
      sortNodes[i].code = codeBits[i].getCodeBytes();
    }
  }

  boolean writeOneSymbol(BitOutputStore output, int symbol) {
    SymbolNode node = symbolNodes[symbol & 0xffff];
    int nFullBytesInCode = node.nBitsInCode / 8;
    for (int j = 0; j < nFullBytesInCode; j++) {
      output.appendBits(8, node.code[j]);
    }
    int remainder = node.nBitsInCode - nFullBytesInCode * 8;
    if (remainder > 0) {
      int test = node.code[nFullBytesInCode];
      for (int j = nFullBytesInCode * 8; j < node.nBitsInCode; j++) {
        output.appendBit(test & 1);
        test >>= 1;
      }
    }
    return true;
  }

  boolean isTreeDepthLimited() {
    return treeDepthLimited;
  }
}
