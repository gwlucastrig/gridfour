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
 * 12/2019  G. Lucas     Refactored from earlier inner classes
 *                         so that it could be used generically.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

/**
 * A simple container for collecting statistics when analyzing compressed data.
 */
class CodecStats {

  final PredictorCorrectorType pcType;
  long nTilesCounted;
  long nBytesTotal;
  long nSymbolsTotal;
  long nBitsOverheadTotal;
  long[] mCount = new long[256];

  CodecStats(PredictorCorrectorType pcType) {
    this.pcType = pcType;
  }

  String getLabel() {
    return pcType.name();
  }

  void addToCounts(int nBytesForTile, int nSymbolsInTile, int nBitsOverhead) {
    nTilesCounted++;
    nBytesTotal += nBytesForTile;
    nSymbolsTotal += nSymbolsInTile;
    nBitsOverheadTotal += nBitsOverhead;
  }

  void addCountsForM32(int nM32, byte[] m32) {
    for (int i = 0; i < nM32; i++) {
      mCount[m32[i] & 0xff]++;
    }
  }

  private static final double log2 = Math.log(2.0);

  double getEntropy() {
    long total = 0;
    for (int i = 0; i < 256; i++) {
      total += mCount[i];
    }
    if (total == 0) {
      return 0;
    }
    double d = (double) total;
    double s = 0;
    for (int i = 0; i < 256; i++) {
      if (mCount[i] > 0) {
        double p = mCount[i] / d;
        s += p * Math.log(p) / log2;
      }
    }
    return -s;
  }

  void clear() {
    nTilesCounted = 0;
    nBytesTotal = 0;
    nSymbolsTotal = 0;
    nBitsOverheadTotal = 0;
  }

  double getBitsPerSymbol() {
    if (nSymbolsTotal == 0) {
      return 0;
    }
    return 8.0 * (double) nBytesTotal / (double) nSymbolsTotal;
  }

  long getTileCount() {
    return nTilesCounted;
  }

  double getAverageOverhead() {
    if (nTilesCounted == 0) {
      return 0;
    }
    return (double) nBitsOverheadTotal / (double) nTilesCounted;
  }

  double getAverageLength() {
    if (nTilesCounted == 0) {
      return 0;
    }
    return (double) nBytesTotal / (double) nTilesCounted;
  }
}
