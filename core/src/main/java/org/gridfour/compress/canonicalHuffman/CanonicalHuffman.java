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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.gridfour.io.BitInputStore;
import org.gridfour.io.BitOutputStore;
import org.gridfour.util.GridfourConstants;

/**
 * An implementation of the canonical Huffman encoding customized for
 * storing integer data in the Gridfour Virtual Raster Store (GVRS) API.
 * While traditional Huffman encoders operate on text defined by bytes,
 * this implementation operates over integer values.
 * <p>
 * When the Gridfour API stores raster data, it uses a predictor-based system
 * in which values in a raster are predicted by the values of neighboring
 * cells. The resulting residuals are typically of small magnitude with
 * the majority falling near zero. To support that pattern, this class
 * using canonical Huffman codes to represent values in the range -128 to 127.
 * Values outside this range are represented using special escape-codes
 * in which additional, non-compressed, bits are added the output.
 */
public class CanonicalHuffman {

  /**
   * The number of symbols defined by the Gridfour implementation of
   * canonical Huffman coding. Symbols are assigned values starting
   * with zero, giving a range of values from zero to N_SYMBOLS_TOTAL-1.
   * An array dimensions to N_SYMBOLS_TOTAL is sufficient to hold one element
   * per symbol.
   */
  static final int N_SYMBOLS_TOTAL = 260;

  private static final int N_SYMBOLS_STANDARD = 256;
  private static final int I_NULL_DATA_CODE = 256;
  private static final int I_ESCAPE_1BYTE = 257;
  private static final int I_ESCAPE_4BITS = 258;
  private static final int I_END_OF_TEXT = 259;


  final private SymbolNode[] symbolNodes;
  final private int [] count4bit = new int[16];
  final private int [] count8bit = new int[256];

  private int nSymbolsInText;
  private int nUniqueSymbols;

  private int nBitsInText;
  private int nBitsInCodeTable;
  private int nBitsInEncoding;
  private int escapeCountBits4;
  private int escapeCountBits8;
  private int escapeCountBits16;
  private int escapeCountBits24;

  private boolean maxCodeLengthLimited;

  /**
   * Standard constructor.
   */
  public CanonicalHuffman() {
    symbolNodes = new SymbolNode[N_SYMBOLS_TOTAL];
    for (int i = 0; i < symbolNodes.length; i++) {
      symbolNodes[i] = new SymbolNode(i);
    }
  }

  /**
   * Clear any counts and statistics gathered during the most recent
   * input processing.
   */
  public void clear() {
    nSymbolsInText = 0;
    nUniqueSymbols = 0;
    nBitsInText = 0;
    nBitsInCodeTable = 0;
    escapeCountBits4 = 0;
    escapeCountBits8 = 0;
    escapeCountBits16 = 0;
    escapeCountBits24 = 0;

    maxCodeLengthLimited = false;

    for (SymbolNode symbolNode : symbolNodes) {
      symbolNode.clear();
    }

    Arrays.fill(count4bit, 0);
    Arrays.fill(count8bit, 0);
  }


  /**
   * Encodes an array of integer data using the Gridfour implementation of the
   * canonical Huffman encoding. Because this class was written to support
   * GVRS operations, it assumes that the input data (the "text")
   * is integral numerical data with the majority of values falling in the
   * range between -128 and +127 (the range of a signed byte). This
   * implementation
   * supports values that require more than a single byte representation using
   * non-compressed "escape" codes.
   *
   * @param nSymbolsInText the number of symbols to be compressed
   * @param offset the starting position within the text array
   * @param text an array of integers to be compressed.
   * @return if successful, a valid array of bytes; otherwise a null
   */
  public byte[] encode(int nSymbolsInText, int offset, int[] text) {
      BitOutputStore output = new BitOutputStore();
      encode(output, nSymbolsInText, offset, text);
      return output.getEncodedText();
  }


   /**
   * Encodes an array of integer data using the Gridfour implementation of the
   * canonical Huffman encoding. Because this class was written to support
   * GVRS operations, it assumes that the input data (the "text")
   * is integral numerical data with the majority of values falling in the
   * range between -128 and +127 (the range of a signed byte). This
   * implementation supports values that require more than a single byte
   * representation using non-compressed "escape" codes.
   *
   * @param output a valid instance to receive the compressed results.
   * @param nSymbolsInText the number of symbols to be compressed
   * @param offset the starting position within the text array
   * @param text an array of integers to be compressed.
   * @return if successful, the number of bytes in the output; otherwise, zero.
   */
   public int encode(BitOutputStore output, int nSymbolsInText, int offset, int[] text) {
    if (nUniqueSymbols > 0) {
      // this instance was used at least once before, clear it out.
      clear();
    }

    if (nSymbolsInText <= 0 || offset < 0 || text == null) {
      throw new IllegalArgumentException("Empty or null data input data");
    }
    if (nSymbolsInText + offset > text.length) {
      throw new IllegalArgumentException(
        "Text array too small for offset and symbol-count specifications");
    }

    countSymbols(nSymbolsInText, offset, text);
    TreeBuilder textTree = new TreeBuilder();
    textTree.buildTree(symbolNodes);
    maxCodeLengthLimited = textTree.isMaxCodeLengthLimited();

    int[] textCodeLengths = getCodeLengths(symbolNodes);


    buildCodeLengthTree(output, textCodeLengths);

    nBitsInCodeTable = output.getEncodedTextLength();

    // write the text
    for (int iSymbol = 0; iSymbol < nSymbolsInText; iSymbol++) {
      int symbol = text[iSymbol];
      if (-128 <= symbol && symbol <= 127) {
        // the symbol is in the single-byte range
        // because we expect that these will be the substantial majority
        // of symbols we encounter, implement special coding to expedite
        // processing.
        SymbolNode node = symbolNodes[symbol + 128];
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
      } else {
        // symbol requires special processing for multi-byte representation
        //     one byte to regular encoding
        //     and one or three bytes based on magnitude of symbol
        if (-2048 <= symbol && symbol <= 2047) {
          int target = (symbol >> 4) + 128;
          textTree.writeOneSymbol(output, target);
          textTree.writeOneSymbol(output, I_ESCAPE_4BITS);
          output.appendBits(4, symbol & 0x0f);
        } else if (-32768 <= symbol && symbol <= 32767) {
          int target = (symbol >> 8) + 128;
          textTree.writeOneSymbol(output, target);
          textTree.writeOneSymbol(output, I_ESCAPE_1BYTE);
          output.appendBits(8, symbol & 0xff);
        } else if (symbol == GridfourConstants.INT4_NULL_CODE) {
          textTree.writeOneSymbol(output, I_NULL_DATA_CODE);
        } else if (-8333608 <= symbol && symbol <= 8388607) {
          int target = (symbol >> 16) + 128;
          textTree.writeOneSymbol(output, target);
          textTree.writeOneSymbol(output, I_ESCAPE_1BYTE);
          output.appendBits(8, (symbol >> 8) & 0xff);
          textTree.writeOneSymbol(output, I_ESCAPE_1BYTE);
          output.appendBits(8, symbol & 0xff);
        } else {
          int target = (symbol >> 24) + 128;
          textTree.writeOneSymbol(output, target);
          textTree.writeOneSymbol(output, I_ESCAPE_1BYTE);
          output.appendBits(8, (symbol >> 16) & 0xff);
          textTree.writeOneSymbol(output, I_ESCAPE_1BYTE);
          output.appendBits(8, (symbol >> 8) & 0xff);
          textTree.writeOneSymbol(output, I_ESCAPE_1BYTE);
          output.appendBits(8, symbol & 0xff);
        }
      }
    }

    textTree.writeOneSymbol(output, I_END_OF_TEXT);

    nBitsInEncoding = output.getEncodedTextLength();
    nBitsInText = nBitsInEncoding - nBitsInCodeTable;
    return output.getEncodedTextLength();
  }

  private void buildCodeLengthTree(BitOutputStore output, int[] textCodeLengths) {
    LengthEncoder textCodeLengthPack = LengthEncoder.encodeLengths(
      textCodeLengths.length, textCodeLengths);

    SymbolNode[] nodes = new SymbolNode[LengthEncoder.SYMBOL_SET_SIZE + 1];
    for (int i = 0; i < nodes.length; i++) {
      nodes[i] = new SymbolNode(i);
    }

    nodes[LengthEncoder.SYMBOL_SET_SIZE].count = 1; // end-of-text
    for (int i = 0; i < textCodeLengthPack.nCodes; i++) {
      nodes[textCodeLengthPack.codes[i]].count++;
    }

    TreeBuilder codeTableTree = new TreeBuilder();
    codeTableTree.buildTree(nodes);
    int[] codeTableTreeLengths = getCodeLengths(nodes);
    LengthEncoder codeTableTreeLengthPack = LengthEncoder.encodeLengths(
      codeTableTreeLengths.length, codeTableTreeLengths);

    // Write one reserved bit for future development
    output.appendBit(0);

    // Write the non-Huffman coded introduction. This gives the
    // first set of Huffman code lengths that can be used to
    // build the code-table tree.
    LengthEncoder.writeEncodedLengths(output, 
      codeTableTreeLengthPack.nCodes,
      codeTableTreeLengthPack.codes,
      codeTableTreeLengthPack.runLengths);

    // Write a canonical Huffman encoding giving the code-lengths
    // for the symbol set obtained from the input text.  The lengths from
    // this output will be used to construct the canonical Huffman encoding
    // for the main body of the text encoding.  Some of the symbols
    // to be written to the output also require extension bits to
    // specify run-lengths.
    int i;
    for (i = 0; i < textCodeLengthPack.nCodes; i++) {
      int code = textCodeLengthPack.codes[i];
      codeTableTree.writeOneSymbol(output, code);
      if (code > LengthEncoder.MAX_STANDARD_SYMBOL) {
        int runLength = textCodeLengthPack.runLengths[i];
        switch (code) {
          case LengthEncoder.REPEAT_PREV_2BITS:
            output.appendBits(2, runLength);
            break;
          case LengthEncoder.REPEAT_ZERO_3BITS:
            output.appendBits(3, runLength);
            break;
          case LengthEncoder.REPEAT_ZERO_7BITS:
            output.appendBits(7, runLength);
            break;
          default:
            break; // a failure
        }
      }
    }
  }

  /**
   * Count the symbols in the input text.
   *
   * @param nSymbolsInText the number of symbols in the text.
   * @param offset an offset to the starting position within the text
   * @param text the text
   */
  public void countSymbols(int nSymbolsInText, int offset, int[] text) {
    this.nSymbolsInText = nSymbolsInText;

    symbolNodes[I_END_OF_TEXT].count = 1;
    for (int iSymbol = 0; iSymbol < nSymbolsInText; iSymbol++) {
      int symbol = text[iSymbol + offset];
      if (-128 <= symbol && symbol <= 127) {
        // the symbol is in the range of standard (one byte) symbols
        int target = symbol + 128;
        symbolNodes[target].count++;
      } else if (-2048 <= symbol && symbol <= 2047) {
        int target = (symbol >> 4) + 128;
        escapeCountBits4++;
        symbolNodes[I_ESCAPE_4BITS].count++;
        symbolNodes[target].count++;
        count4bit[(symbol >> 4) & 0x0f]++;
      } else if (-32768 <= symbol && symbol <= 32767) {
        int target = (symbol >> 8) + 128;
        escapeCountBits8++;
        symbolNodes[I_ESCAPE_1BYTE].count++;
        symbolNodes[target].count++;
        count8bit[(symbol >> 8) & 0xff]++;
      } else if (symbol == GridfourConstants.INT4_NULL_CODE) {
        symbolNodes[I_NULL_DATA_CODE].count++;
      } else if (-8388608 <= symbol && symbol <= 8388607) {
        int target = (symbol >> 16) + 128;
        escapeCountBits16++;
        symbolNodes[I_ESCAPE_1BYTE].count += 2;
        symbolNodes[target].count++;
        count8bit[(symbol >> 8) & 0xff]++;
        count8bit[(symbol >> 16) & 0xff]++;
      } else {
        int target = (symbol >> 24) + 128;
        escapeCountBits24++;
        symbolNodes[I_ESCAPE_1BYTE].count += 3;
        symbolNodes[target].count++;
        count8bit[(symbol >> 8) & 0xff]++;
        count8bit[(symbol >> 16) & 0xff]++;
        count8bit[(symbol >> 24) & 0xff]++;
      }
    }

    for (SymbolNode node : symbolNodes) {
      if (node.count > 0) {
        nUniqueSymbols++;
      }
    }
  }

  int[] getCodeLengths(SymbolNode[] nodes) {
    int[] result = new int[nodes.length];
    for (int i = 0; i < nodes.length; i++) {
      result[i] = nodes[i].nBitsInCode;
    }
    return result;
  }

  /**
   * Decodes an input bit store containing data compressed in the Gridfour
   * implementation of canonical Huffman coding.
   * <p>
   * The number of symbols to be read from the source may be less than
   * the number of symbols available in the text, but must not exceed it.
   *
   * @param input a valid bit source positioned at the beginning of the
   * canonical Huffman code sequence
   * @param nSymbolsInText the number of symbols to be read from the source.
   * @param text a location to tore the output data.
   * @return if successful, true; otherwise, false.
   */
  public boolean decode(BitInputStore input, int nSymbolsInText, int[] text) {
    if (nUniqueSymbols > 0) {
      // this instance was used at least once before, clear it out.
      clear();
    }
    if (nSymbolsInText <= 0) {
      return false;
    }
    
    // Read the one reserved bit (not currently used)
    input.getBit();

    int[] codeTableLengths = new int[LengthEncoder.SYMBOL_SET_SIZE + 1];
    LengthEncoder.readEncodedLengths(input, LengthEncoder.SYMBOL_SET_SIZE + 1, codeTableLengths);

    CanonHuffTreeDecoder codeTable = new CanonHuffTreeDecoder(codeTableLengths);
    int[] textTreeLengths = new int[N_SYMBOLS_TOTAL + 1];
    codeTable.decodeTree(input, N_SYMBOLS_TOTAL, textTreeLengths);
    nBitsInCodeTable = input.getPosition();

    CanonHuffTreeDecoder textTree = new CanonHuffTreeDecoder(textTreeLengths);
    nUniqueSymbols = textTree.nUniqueSymbols;
    decodeText(textTree, input, nSymbolsInText, text);
    return true;
  }

  boolean decodeText(CanonHuffTreeDecoder textTree, BitInputStore input, int nSymbolsInText, int[] text) {
    int[] nodeIndex = textTree.nodeIndex;
    int prior = 0;
    int part;
    int iSymbol = 0;
    // This loop terminates on an end of text.  We take this approach
    // because the last symbol in the encoding could be an escape cpde
    // which modifies the prior value.
    while (true) {
      int offset = nodeIndex[1 + input.getBit()]; // start from the root node
      while (nodeIndex[offset] == -1) {
        offset = nodeIndex[offset + 1 + input.getBit()];
      }
      int symbol = nodeIndex[offset];
      if (symbol == I_END_OF_TEXT) {
        break;
      }
      if (symbol < N_SYMBOLS_STANDARD) {
        symbol -= 128;
        text[iSymbol++] = symbol;
        prior = symbol;
      } else {
        switch (symbol) {
          case I_ESCAPE_4BITS:
            part = input.getBits(4);
            prior = (prior << 4) | part;
            text[iSymbol - 1] = prior;
            break;
          case I_ESCAPE_1BYTE:
            part = input.getBits(8);
            prior = (prior << 8) | part;
            text[iSymbol - 1] = prior;
            break;
          case I_NULL_DATA_CODE:
            prior = GridfourConstants.INT4_NULL_CODE;
            text[iSymbol++] = GridfourConstants.INT4_NULL_CODE;
            break;
          case I_END_OF_TEXT:
            iSymbol = nSymbolsInText;
            break;
          default:
            break;
        }
      }
    }

    return true;
  }

  /**
   * Print diagnostics describing the results of the most recently
   * encoded text.
   *
   * @param ps a valid instance to receive the output.
   */
  public void printDiagnostics(PrintStream ps) {

    if (nBitsInEncoding == 0) {
      ps.println("No data encoded");
      return;
    }

    ps.println("Storage for Huffman Encoding");
    ps.format("  Symbols in text:    %12d%n", nSymbolsInText);
    ps.format("  Unique symbols:     %12d%n", this.nUniqueSymbols);
    ps.format("  Bits in code table: %12d%n", nBitsInCodeTable);
    ps.format("  Bits in text:       %12d%n", nBitsInText);
    ps.format("  Bits in encoding:   %12d   (%d bytes) %n",
      nBitsInEncoding, (nBitsInEncoding + 7) / 8);
    ps.format("  Bits per symbol:       %12.4f%n",
      (double) nBitsInEncoding / (double) nSymbolsInText);
    ps.format("  Code length truncated:       %s%n ", maxCodeLengthLimited ? "true" : "false");
    ps.println();
    ps.println("Escape sequences");
    ps.format("   4 bits: %8d%n", escapeCountBits4);
    ps.format("   8 bits: %8d%n", escapeCountBits8);
    ps.format("  16 bits: %8d%n", escapeCountBits16);
    ps.format("  24 bits: %8d%n", escapeCountBits24);
  }

  /**
   * A diagnostic tool for printing out the canonical Huffman code
   * sequences for each symbol.
   *
   * @param ps a valid instance to receive the output.
   */
  public void printCodeBits(PrintStream ps) {
    List<SymbolNode> sortNodes = new ArrayList<>();
    for (SymbolNode symbolNode : symbolNodes) {
      if (symbolNode.count > 0) {
        sortNodes.add(symbolNode);
      }
    }

    Comparator<SymbolNode> countSymbolComp = new Comparator<SymbolNode>() {
      @Override
      public int compare(SymbolNode o1, SymbolNode o2) {
        int test = Integer.compare(o1.nBitsInCode, o2.nBitsInCode);
        if (test == 0) {
          test = Integer.compare(o1.symbol, o2.symbol);
        }
        return test;
      }
    };

    Collections.sort(sortNodes, countSymbolComp);

    for (SymbolNode node : sortNodes) {
      BitOutputStore output = new BitOutputStore();
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

      byte[] encoding = output.getEncodedText();
      BitInputStore input = new BitInputStore(encoding);
      ps.format("%4d %9d %3d    ",
        node.symbol, node.count, node.nBitsInCode);
      for (int j = 0; j < node.nBitsInCode; j++) {
        int bit = input.getBit();
        ps.format("%d", bit);
      }
      ps.format("%n");
    }
    ps.flush();
  }

  /**
   * Get the number of bits required to store the code-table
   * section of the encoded text.  This class populates a value for this
   * method when input data is processed by the encode method or the
   * countSymbols method.
   *
   * @return if populated, a positive integer; otherwise, zero.
   */
  public int getBitsInCodeTableCount() {
    return nBitsInCodeTable;
  }


  /**
   * Get the number of unique symbols identified in the most recently
   * encoded text. This value includes special symbols such as the
   * end-of-text code defined by this class.
   * <p>
   * In the case of integer values outside the range of a single
   * byte (-128 to +127), a value is treated as a symbol in the range
   * 0 to 255 followed by a non-Huffman encoded set of "escape bits".
   *
   * @return a positive integer in the range 0 to N_SYMBOLS_TOTAL.
   */
  public int getUniqueSymbolCount() {
    return nUniqueSymbols;
  }

  /**
   * Gets the total number of symbols counted from the most recent input
   * set, including special symbols and the end-of-text symbol.
   * @return a positive integer value.
   */
  public int getTotalSymbolCount(){
    int n=0;
    for (SymbolNode symbolNode : symbolNodes) {
      n += symbolNode.count;
    }
    return n;
  }

  /**
   * Gets the number of escape bits needed to represent the most recently
   * encoded Huffman text. This class populates a value for this
   * method when input data is processed by the encode method or the
   * countSymbols method. If neither of these methods was called,
   * the return value for this method is undefined.
   *
   * @return a positive integer.
   */
  public int getEscapeBitCounts() {
    return escapeCountBits4 * 4
      + escapeCountBits8 * 8
      + escapeCountBits16 * 16
      + escapeCountBits24 * 24;
  }


  /**
   * Gets the entropy based on the most recently processed sample.
   * @return a positive floating-point value; zero if no-data is available.
   */
  public double getEntropy() {
    if (this.nSymbolsInText == 0) {
      return 0;
    }
    double e = 0;
    double e4 = 0;
    double e8 = 0;
    double d = getTotalSymbolCount();
    for (SymbolNode symbolNode : symbolNodes) {
        int n = symbolNode.count;
        if (n > 0) {
          double p = n / d;
          e += p * Math.log(p);
      }
    }

    // The contribition from the escape sequences is based on the
    // conditional probability of the numerical value of each escape bit set.
    // So we use the product of the probability of the escape symbol,
    // followed by the individual contribitions for each possible escape
    // bit set.
    if (symbolNodes[I_ESCAPE_4BITS].count > 0) {
      double n4 = symbolNodes[I_ESCAPE_4BITS].count;
      double p = n4 / d;
      for (int j = 0; j < count4bit.length; j++) {
        if (count4bit[j] > 0) {
          double q = (double) count4bit[j] / n4; // conditional p(j|i4)
          e4 += p * q*Math.log(q);
        }
      }
    }

    if (symbolNodes[I_ESCAPE_1BYTE].count > 0) {
      double n8 = symbolNodes[I_ESCAPE_1BYTE].count;
      double p = n8 / d;
      for (int j = 0; j < count8bit.length; j++) {
        if (count8bit[j] > 0) {
          double q = (double) count8bit[j] / n8; // conditional p(j|i8)
          e8 += p * q*Math.log(q);
        }
      }
    }

    return -(e + e4 + e8) / Math.log(2.0);
  }
}
