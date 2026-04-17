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
 * 04/2026  G. Lucas     Initial implementation
 *
 * Notes:
 *  The Package-Merge algorithm builds a length-limited prefix-free
 *  binary code table simular to the Huffman code scheme. Package-Merge
 *  was originally presented by  Larmore and Hirschberg in 1987.
 *
 *  An excellent description of the algorithm was provided by
 *  Stephan Brumme, 2021, at https://create.stephan-brumme.com/length-limited-prefix-codes/
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress.canonicalHuffman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Constructs a length-limited prefix-free code table similar to the
 * Huffman encoding. The maximum length code for any symbol in the code table
 * is limited based on an arbitrary specification. For a reasonable choice
 * of maximum length, the result is close to that of the Huffman tree.
 */
class PackageMerge {

  private static class Entry {

    final int symbol;
    final int count;
    int nBits;

    Entry(int symbol, int count) {
      this.symbol = symbol;
      this.count = count;
    }

  }

  /**
   * Standard constructor
   */
  PackageMerge() {

  }

  /**
   * Use the counts specified in the input symbols to construct a length-limited
   * code table.  The resulting codes-lengths are stored in the symbol nodes.
   * The binary code sequences are not populated.
   * Any previously existing codes stored in the nodes will be overwritten.
   * <p>
   * This particular implementation is designed to be used when the standard
   * Huffman algorithm produces code lengths that are longer than a
   * desired value.
   * @param maxCodeLength an integer value sufficiently large for encoding
   * the specified number of symbols.
   * @param input a valid set of symbols pre-populated with counts.
   */
  void merge(int maxCodeLength, SymbolNode[] input) {
    List<Entry> list = new ArrayList<>();
    for (int iInput = 0; iInput < input.length; iInput++) {
      SymbolNode node = input[iInput];
      if (node.count > 0) {
        node.nBitsInCode = 0;
        node.code = null;
        list.add(new Entry(iInput, node.count));
      }
    }

    // The sorting order is designed to keep the input nodes in the
    // same order as a secondary key (primary key is based on count)
        //     primary key:    the count, ascending
        //     secondary key:  the symbol, ascending
    Collections.sort(list, (Entry o1, Entry o2) -> {
      int test = Integer.compare(o1.count, o2.count);
      if (test == 0) {
        test = Integer.compare(o1.symbol, o2.symbol);
      }
      return test;
    });


    // Phase 1:  Build the packages to define the structure of the
    // prefix-free tree hierarchy. Packages include the combined counts
    // for pairs of symbols.
    Entry[] base = list.toArray(Entry[]::new);
    Entry entries[][] = new Entry[maxCodeLength][];
    entries[0] = base;

    for (int iDepth = 1; iDepth < maxCodeLength; iDepth++) {
      Entry[] ix = entries[iDepth - 1];
      int nPair = ix.length / 2;
      Entry[] pair = new Entry[nPair];
      for (int iPair = 0; iPair < nPair; iPair++) {
        int count = ix[iPair * 2].count + ix[iPair * 2 + 1].count;
        pair[iPair] = new Entry(-1, count);
      }

      int k = 0;
      int iBase = 0;
      Entry[] m = new Entry[base.length + nPair];
      for (int iPair = 0; iPair < nPair; iPair++) {
        while (iBase < base.length) {
          if (base[iBase].count <= pair[iPair].count) {
            m[k++] = base[iBase];
            iBase++;
          } else {
            break;
          }
        }
        m[k++] = pair[iPair];
      }
      if (base[base.length - 1].count > pair[nPair - 1].count) {
        m[m.length - 1] = base[base.length - 1];
      }
      entries[iDepth] = m;
    }

    // Phase 2 -------------------------------------------
    // Tabulate the package-merge entries, going from bottom to top.
    int n = base.length * 2 - 2;
    for (int iEntry = entries.length - 1; iEntry >= 0; iEntry--) {
      int nMerged = 0;
      Entry[] ix = entries[iEntry];
      for (int i = 0; i < n; i++) {
        if (ix[i].symbol == -1) {
          // it's a pair
          nMerged++;
        } else {
          ix[i].nBits++;
        }
      }
      n = nMerged * 2;
    }

    // Phase 3 --------------------
    // Transfer the code lengths to the input.
    for (int i = 0; i < base.length; i++) {
      Entry e = base[i];
      input[e.symbol].nBitsInCode = e.nBits;
    }

  }
}
