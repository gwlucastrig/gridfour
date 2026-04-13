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
 * 03/2026  G. Lucas     Adapted from legacy code.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress.canonicalHuffman;

import org.gridfour.compress.PredictorModelType;



/**
 * A simple container for collecting statistics when analyzing compressed data.
 * <p>
 * This statistics collector is suited to the standard compressors implemented
 * by the Gridfour API. If desired, it may be used for other compressor
 * implementations.
 */
public class CanonHuffmanStats {

  private static final int N_SYMBOLS_TOTAL = 261;

  final PredictorModelType pcType;
  final String name;
  long nTilesCounted;
  long nBytesTotal;
  long nSymbolsTotal;
  long nBitsOverheadTotal;

  long nSymbolsInTextCounted;
  long sumLength;
  long sumObserved;
  double sumEntropy;
  long sumEscapeBits;

  long[] sA = new long[N_SYMBOLS_TOTAL];

  /**
   * Construct a statistics object for the specified predictor model.
   *
   * @param pcType a valid predictor model type.
   */
  public CanonHuffmanStats(PredictorModelType pcType) {
    this.pcType = pcType;
    name = pcType.name();
  }

  public CanonHuffmanStats(String name) {
    this.name = name;
    pcType = PredictorModelType.None;
  }

  /**
   * Get a label for the predictor.
   *
   * @return a valid string
   */
  public String getLabel() {
    return name;
  }

  /**
   * Add metadata about the tile to the counts
   *
   * @param nBytesForTile the number of byts for the compressed version of the
   * tile.
   * @param nSymbolsInTile the number of values in the tile, typically
   * the number of cells in the grid.
   * @param nBitsOverhead Any compressor-specific overhead that an
   * implementation wishes to track.
   */
  public void addToCounts(int nBytesForTile, int nSymbolsInTile, int nBitsOverhead) {
    nTilesCounted++;
    nBytesTotal += nBytesForTile;
    nSymbolsTotal += nSymbolsInTile;
    nBitsOverheadTotal += nBitsOverhead;
  }

  private static final double log2 = Math.log(2.0);

  /**
   * Add counts for the symbols derived from the predictor
   * for the tile.
   *
   * @param nSymbolsInText the total number of symbols in the encoded data
   * @param symbols the encoded data
   */
  public void addCountsForSymbols(int nSymbolsInText, int[] symbols) {
    if (nSymbolsInText <= 0) {
      return;
    }
    this.nSymbolsInTextCounted++;
    this.sumLength += nSymbolsInText;

    int[] observed = new int[256];
    int[] mCount = new int[256];
    for (int i = 0; i < nSymbolsInText; i++) {
      int index = symbols[i] & 0xff;
      mCount[index]++;
      observed[index] = 1;
    }
    for (int i = 0; i < 256; i++) {
      sumObserved += observed[i];
    }

    double d = (double) nSymbolsInText;
    double s = 0;
    for (int i = 0; i < 256; i++) {
      if (mCount[i] > 0) {
        double p = mCount[i] / d;
        s += p * Math.log(p) / log2;
      }
    }
    this.sumEntropy -= s;

    if (nSymbolsInText < 2) {
      return;
    }

    for (int i = 1; i < nSymbolsInText; i++) {
      int value = symbols[i] & 0xff;
      sA[value]++;
    }
  }

  /**
   * Get the entropy for the data. In information theory, the term
   * "entropy" is essentially an indicator of the number of bits actually
   * needed to encode data.
   *
   * @return a positive value
   */
  public double getEntropy() {
    if (nSymbolsInTextCounted == 0) {
      return 0;
    }
    return sumEntropy / nSymbolsInTextCounted;
  }

  /**
   * Clear any accumulated counts
   */
  public void clear() {
    nTilesCounted = 0;
    nBytesTotal = 0;
    nSymbolsTotal = 0;
    nBitsOverheadTotal = 0;
  }

  /**
   * Get the number of bits per symbol in the pre-compression encoding.
   *
   * @return zero or a positive number
   */
  public double getBitsPerSymbol() {
    if (nSymbolsTotal == 0) {
      return 0;
    }
    return 8.0 * (double) nBytesTotal / (double) nSymbolsTotal;
  }

  /**
   * Get the number of tiles that have been counted.
   *
   * @return zero or a positive number
   */
  public long getTileCount() {
    return nTilesCounted;
  }

  /**
   * Gets the average symbol count across tiles.
   *
   * @return a positive floating point value, potentially zero.
   */
  public double getAverageSymbolCount() {

    if (nSymbolsInTextCounted == 0) {
      return 0;
    }
    return (double) this.sumLength / (double) nSymbolsInTextCounted;

  }

  /**
   * Gets the average number of unique symbols observed in
   * the compressed tile. The maximum number of unique M32 codes
   * is 256 (which would occur if all possible M32 codes were used).
   *
   * @return a positive floating point value, potentially zero.
   */
  public double getAverageUniqueSymbols() {
    if (nTilesCounted > 0) {
      return (double) sumObserved / (double) nTilesCounted;
    }
    return 0;
  }

  /**
   * Get the average overhead per tile. Not all compressors tabulate
   * this value.
   *
   * @return zero or a positive value.
   */
  public double getAverageOverhead() {
    if (nTilesCounted == 0) {
      return 0;
    }
    return (double) nBitsOverheadTotal / (double) nTilesCounted;
  }

  /**
   * Get the average length of encoded tiles, in bytes.
   *
   * @return zero or a positive number.
   */
  public double getAverageLength() {
    if (nTilesCounted == 0) {
      return 0;
    }
    return (double) nBytesTotal / (double) nTilesCounted;
  }

  public double getAverageEscapeBits() {
    if (nTilesCounted == 0) {
      return 0;
    }
    return (double) sumEscapeBits / (double) nTilesCounted;
  }
}
