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

import java.io.IOException;
import java.io.PrintStream;
import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;
import org.gridfour.compress.IPredictorModel;
import org.gridfour.compress.PredictorModelDifferencing;
import org.gridfour.compress.PredictorModelDifferencingWithNulls;
import org.gridfour.compress.PredictorModelLinear;
import org.gridfour.compress.PredictorModelTriangle;
import org.gridfour.compress.PredictorModelType;
import org.gridfour.io.BitInputStore;
import org.gridfour.io.BitOutputStore;
import static org.gridfour.util.GridfourConstants.INT4_NULL_CODE;

/**
 * Provides a codec for data compression using Huffman codes and the
 * predictor models.
 */
public class CodecCanonHuffman implements ICompressionEncoder, ICompressionDecoder {

  private final IPredictorModel[] predictorModel;

  private CanonHuffmanStats[] codecStats;

  /**
   * Standard constructor
   */
  public CodecCanonHuffman() {
    predictorModel = new IPredictorModel[4];
    predictorModel[0] = new PredictorModelDifferencing();
    predictorModel[1] = new PredictorModelLinear();
    predictorModel[2] = new PredictorModelTriangle();
    predictorModel[3] = new PredictorModelDifferencingWithNulls();
  }

  @Override
  public byte[] encode(int codecIndex, int nRows, int nCols, int[] values) {
    boolean containsNullValue = false;
    boolean containsValidData = false;
    for (int i = 0; i < values.length; i++) {
      if (values[i] == INT4_NULL_CODE) {
        containsNullValue = true;
      } else {
        containsValidData = true;
      }
    }
    if (!containsValidData) {
      return null;
    }

    int resultLength = Integer.MAX_VALUE;
    byte[] result = null;

    for (IPredictorModel testModel : predictorModel) {
      if (containsNullValue) {
        if (!testModel.isNullDataSupported()) {
          continue;
        }
      } else {
        if (testModel.isNullDataSupported()) {
          continue;
        }
      }
      int[] residuals = new int[nRows * nCols];
      int nResiduals = testModel.encodeInt(nRows, nCols, values, residuals);

      byte[] testPacking = compress(
        codecIndex,
        testModel,
        nResiduals,
        residuals);

      int testLength = testPacking.length;
      if (testLength < resultLength) {
        resultLength = testLength;
        result = testPacking;
      }
    }

    return result;
  }

  byte[] compress(int codecIndex, IPredictorModel pcc, int nValues, int[] values) {
    CanonicalHuffman huffman = new CanonicalHuffman();
    BitOutputStore store = new BitOutputStore();
    store.appendBits(8, codecIndex);
    store.appendBits(8, pcc.getPredictorType().getCodeValue());
    store.appendBits(32, pcc.getSeed());
    byte[] header = store.getEncodedText();
    byte[] body = huffman.encode(nValues, 0, values);
    if (body != null) {
      byte[] result = new byte[header.length + body.length];
      System.arraycopy(header, 0, result, 0, 6);
      System.arraycopy(body, 0, result, 6, body.length);
      return result;
    }
    return null;
  }

  @Override
  public int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
    IPredictorModel pcc = this.decodePredictorCorrector(packing[1]);
    int seed
      = (packing[2] & 0xff)
      | ((packing[3] & 0xff) << 8)
      | ((packing[4] & 0xff) << 16)
      | ((packing[5] & 0xff) << 24);

    CanonicalHuffman decoder = new CanonicalHuffman();
    BitInputStore inputStore = new BitInputStore(packing, 6, packing.length - 6);

    int nSymbolsInText = nRows * nColumns;
    int[] residuals = new int[nRows * nColumns];
    decoder.decode(inputStore, nSymbolsInText, residuals);

    int[] output = new int[nRows * nColumns];
    pcc.decodeInt(seed, nRows, nColumns, residuals, 0, residuals.length, output);
    return output;
  }

  private IPredictorModel decodePredictorCorrector(int code) throws IOException {
    PredictorModelType pcType = PredictorModelType.valueOf(code);
    switch (pcType) {
      case Differencing:
        return new PredictorModelDifferencing();
      case Linear:
        return new PredictorModelLinear();
      case Triangle:
        return new PredictorModelTriangle();
      case DifferencingWithNulls:
        return new PredictorModelDifferencingWithNulls();
      default:
        throw new IOException("Unknown PredictorCorrector type");
    }
  }

  @Override
  public void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
    if (codecStats == null) {
      PredictorModelType[] pcArray = PredictorModelType.values();
      codecStats = new CanonHuffmanStats[pcArray.length + 1];
      for (int i = 0; i < pcArray.length; i++) {
        codecStats[i] = new CanonHuffmanStats(pcArray[i]);
      }
      codecStats[pcArray.length] = new CanonHuffmanStats("All Predictors");
    }

    int nSymbolsInText = nRows * nColumns-1;

    CanonicalHuffman decoder = new CanonicalHuffman();
    BitInputStore inputStore = new BitInputStore(packing, 6, packing.length - 6);
    int[] residuals = new int[nSymbolsInText];
    decoder.decode(inputStore, nSymbolsInText, residuals);

    CanonicalHuffman canHuff = new CanonicalHuffman();
    canHuff.countSymbols(residuals.length, 0, residuals);
    int escapeBitCounts = canHuff.getEscapeBitCounts();
    double entropy = canHuff.getEntropy();

    CanonHuffmanStats stats = codecStats[packing[1] & 0xff];
    stats.addToCounts(packing.length - 6, nSymbolsInText, decoder.getBitsInCodeTableCount());
    stats.addCountsForSymbols(nSymbolsInText, residuals, entropy);
    CanonHuffmanStats total = codecStats[codecStats.length - 1];
    total.addToCounts(packing.length - 6, nSymbolsInText, decoder.getBitsInCodeTableCount());
    total.addCountsForSymbols(nSymbolsInText, residuals, entropy);

    stats.sumEscapeBits += escapeBitCounts;
    total.sumEscapeBits += escapeBitCounts;
  }

  @Override
  public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {
    ps.println("GVRS Canonical Huffman                          Compressed Output    |       Predictor Residuals");
    if (codecStats == null || nTilesInRaster == 0) {
      ps.format("   Tiles Compressed:  0%n");
      return;
    }

    ps.format("  Predictor                Times Used         bits/sym    bits/tile  |    ext-bits    avg-unique  entropy | bits in tree%n");

    for (CanonHuffmanStats stats : codecStats) {
      String label = stats.getLabel();
      if (label.equalsIgnoreCase("None")) {
        continue;
      }
      long tileCount = stats.getTileCount();
      double bitsPerSymbol = stats.getBitsPerSymbol();
      double avgBitsInTree = stats.getAverageOverhead();
      double avgBitsInText = stats.getAverageLength() * 8;
      double avgUniqueSymbols = stats.getAverageUniqueSymbols();
      double percentTiles = 100.0 * (double) tileCount / nTilesInRaster;
      double entropy = stats.getEntropy();
      double avgEscapeBits = stats.getAverageEscapeBits();
      String lineLabel = String.format("%-20.20s %8d (%4.1f %%)", label, tileCount, percentTiles);
      ps.format("   %-39.39s     %5.2f  %12.1f   | %10.1f      %6.1f    %6.2f   | %6.1f%n",
        lineLabel,
        bitsPerSymbol, avgBitsInText,
        avgEscapeBits,
        avgUniqueSymbols,
        entropy,
        avgBitsInTree);
    }

  }

  @Override
  public void clearAnalysisData() {
    codecStats = null;
  }

  @Override
  public byte[] encodeFloats(int codecIndex, int nRows, int nCols, float[] values) {
    return null;
  }

  @Override
  public float[] decodeFloats(int nRows, int nColumns, byte[] packing) throws IOException {
    return null;
  }

  @Override
  public boolean implementsFloatingPointEncoding() {
    return false;
  }

  @Override
  public boolean implementsIntegerEncoding() {
    return true;
  }
}
