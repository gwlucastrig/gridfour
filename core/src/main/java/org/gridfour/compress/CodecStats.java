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
package org.gridfour.compress;

/**
 * A simple container for collecting statistics when analyzing compressed data.
 * <p>
 * This statistics collector is suited to the standard compressors implemented
 * by the Gridfour API. If desired, it may be used for other compressor
 * implementations.
 */
public class CodecStats {

    final PredictorModelType pcType;
    final String name;
    long nTilesCounted;
    long nBytesTotal;
    long nSymbolsTotal;
    long nBitsOverheadTotal;

    long nM32Counted;
    long sumLengthM32;
    long sumObservedM32;
    double sumEntropyM32;

    long []sA= new long[256];
    long []sB = new long[65536];

    /**
     * Construct a statistics object for the specified predictor model.
     *
     * @param pcType a valid predictor model type.
     */
    public CodecStats(PredictorModelType pcType) {
        this.pcType = pcType;
        name = pcType.name();
    }

    public CodecStats(String name){
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
     * Add counts for the M32 symbols derived from the predictor
     * for the tile.
     *
     * @param nM32 the total number of symbols in the encoded data
     * @param m32 the encoded data
     */
    public void addCountsForM32(int nM32, byte[] m32) {
        if (nM32 <= 0) {
            return;
        }
        this.nM32Counted++;
        this.sumLengthM32 += nM32;

        int[] observed = new int[256];
        int[] mCount = new int[256];
        for (int i = 0; i < nM32; i++) {
            int index = m32[i] & 0xff;
            mCount[index]++;
            observed[index] = 1;
        }
        for (int i = 0; i < 256; i++) {
            sumObservedM32 += observed[i];
        }

        double d = (double) nM32;
        double s = 0;
        for (int i = 0; i < 256; i++) {
            if (mCount[i] > 0) {
                double p = mCount[i] / d;
                s += p * Math.log(p) / log2;
            }
        }
        this.sumEntropyM32 -= s;

        if(nM32<2){
          return;
        }

        int prior = m32[0]&0xff;
        for(int i=1; i<nM32; i++){
          int value = m32[i]&0xff;
          sA[value]++;
          sB[(prior<<8)|value]++;
          prior = value;
        }
    }

    public double getH2(){
      long k=0;
      for(int i=0; i<256; i++){
        k+=sA[i];
      }

      if(k==0){
        return 0;
      }
    double h2 = 0;
    for (int i = 0; i < 256; i++) {
      if (sA[i] > 0) {
        double pI = (double) sA[i] / (double) k;
        long n = 0;
        int j0 = i * 256;
        int j1 = j0 + 256;
        for (int j = j0; j < j1; j++) {
          n += sB[j];
        }
        double sumJ = 0;
        for (int j = j0; j < j1; j++) {
          if(sB[j]>0){
          double pJ = (double) sB[j] / (double) n;
          sumJ += pJ * Math.log(pJ);
        }
        }
        h2 += pI * sumJ;
      }
    }
    return -h2;
  }

    /**
     * Get the entropy for the data. In information theory, the term
     * "entropy" is essentially an indicator of the number of bits actually
     * needed to encode data.
     *
     * @return a positive value
     */
    public double getEntropy() {
        if (nM32Counted == 0) {
            return 0;
        }
        return sumEntropyM32 / nM32Counted;
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
     * Gets the average length of the M32 code length across tiles.
     *
     * @return a positive floating point value, potentially zero.
     */
    public double getAverageMCodeLength() {

        if (nM32Counted == 0) {
            return 0;
        }
        return (double) this.sumLengthM32 / (double) nM32Counted;

    }

    /**
     * Gets the average number of unique M32 codes observed in
     * the compressed tile. The maximum number of unique M32 codes
     * is 256 (which would occur if all possible M32 codes were used).
     *
     * @return a positive floating point value, potentially zero.
     */
    public double getAverageObservedMCodes() {
        if (nTilesCounted > 0) {
            return (double) sumObservedM32 / (double) nTilesCounted;
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
}
