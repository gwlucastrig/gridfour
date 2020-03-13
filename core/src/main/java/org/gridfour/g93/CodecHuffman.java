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
 * 12/2019  G. Lucas     Refactored from previous RasterCodec class
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;
import java.io.PrintStream;
import static org.gridfour.g93.G93FileConstants.NULL_DATA_CODE;
import org.gridfour.io.BitInputStore;
import org.gridfour.io.BitOutputStore;


/**
 * Provides a codec for data compression using Huffman codes and the
 * predictive-transform models.
 */
public class CodecHuffman implements IG93CompressorCodec {

  private final IPredictiveTransform[] predictiveTransform;

  private CodecStats[] codecStats;

  /**
   * Standard constructor
   */
  public CodecHuffman() {
    predictiveTransform = new IPredictiveTransform[4];
    predictiveTransform[0] = new PredictiveTransformConstantModel();
    predictiveTransform[1] = new PredictiveTransformLinearModel();
    predictiveTransform[2] = new PredictiveTransformTriangleModel();
    predictiveTransform[3] = new PredictiveTransformConstantWithNulls();

  }

  @Override
  public byte[] encode(int codecIndex, int nRows, int nCols, int[] values) {
    boolean containsNullValue = false;
    boolean containsValidData = false;
    for (int i = 0; i < values.length; i++) {
      if (values[i] == NULL_DATA_CODE) {
        containsNullValue = true;
      } else {
        containsValidData = true;
      }
    }
    if (!containsValidData) {
      return null;
    }
  
    byte[] mCode = new byte[5 * nRows * nCols];

    int resultLength = Integer.MAX_VALUE;
    BitOutputStore resultStore = null;

    for (IPredictiveTransform testModel : predictiveTransform) {
      if (containsNullValue) {
        if (!testModel.isNullDataSupported()) {
          continue;
        }
      } else {
        if (testModel.isNullDataSupported()) {
          continue;
        }
      }
      int mCodeLength = testModel.encode(nRows, nCols, values, mCode);
      if (mCodeLength > 0) {
        BitOutputStore testStore = compress(
                codecIndex,
                testModel,
                mCode,
                mCodeLength);
        int testLength = testStore.getEncodedTextLengthInBytes();
        if (testLength < resultLength) {
          resultLength = testLength;
          resultStore = testStore;
        }
      }
    }

    if (resultStore == null) {
      return null;
    }

    return resultStore.getEncodedText();
  }

  BitOutputStore compress(int codecIndex, IPredictiveTransform pcc, byte[] mCodes, int nM32) {
    HuffmanEncoder huffman = new HuffmanEncoder();
    BitOutputStore store = new BitOutputStore();
    store.appendBits(8, codecIndex);
    store.appendBits(8, pcc.getPredictorType().getCodeValue());
    store.appendBits(32, pcc.getSeed());
    store.appendBits(32, nM32);
    huffman.encode(store, nM32, mCodes);
    return store;
  }

  @Override
  public int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
    IPredictiveTransform pcc = this.decodePredictorCorrector(packing[1]);
    int seed
            = (packing[2] & 0xff)
            | ((packing[3] & 0xff) << 8)
            | ((packing[4] & 0xff) << 16)
            | ((packing[5] & 0xff) << 24);
    int nM32 = (packing[6] & 0xff)
            | ((packing[7] & 0xff) << 8)
            | ((packing[8] & 0xff) << 16)
            | ((packing[9] & 0xff) << 24);

    HuffmanDecoder decoder = new HuffmanDecoder();
    BitInputStore inputStore = new BitInputStore(packing, 10, packing.length - 10);
    byte[] codeM32s = new byte[nM32];
    decoder.decode(inputStore, nM32, codeM32s);

    int[] output = new int[nRows * nColumns];
    pcc.decode(seed, nRows, nColumns, codeM32s, 0, nM32, output);
    return output;
  }

  private IPredictiveTransform decodePredictorCorrector(int code) throws IOException {
    PredictiveTransformType pcType = PredictiveTransformType.valueOf(code);
    switch (pcType) {
      case Constant:
        return new PredictiveTransformConstantModel();
      case Linear:
        return new PredictiveTransformLinearModel();
      case Triangle:
        return new PredictiveTransformTriangleModel();
      case ConstantWithNulls:
        return new PredictiveTransformConstantWithNulls();
      default:
        throw new IOException("Unknown PredictorCorrector type");
    }
  }

  @Override
  public void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
    if (codecStats == null) {
      PredictiveTransformType[] pcArray = PredictiveTransformType.values();
      codecStats = new CodecStats[pcArray.length];
      for (int i = 0; i < pcArray.length; i++) {
        codecStats[i] = new CodecStats(pcArray[i]);
      }
    }
        
    int nM32 = (packing[6] & 0xff)
            | ((packing[7] & 0xff) << 8)
            | ((packing[8] & 0xff) << 16)
            | ((packing[9] & 0xff) << 24);

    HuffmanDecoder decoder = new HuffmanDecoder();
    BitInputStore inputStore = new BitInputStore(packing, 10, packing.length - 10);
    byte[] codeM32s = new byte[nM32];
    decoder.decode(inputStore, nM32, codeM32s);



    CodecStats stats = codecStats[packing[1] & 0xff];
    int nValues = nRows * nColumns;
    stats.addToCounts(packing.length - 10, nValues, decoder.nBitsInTree);
    stats.addCountsForM32(nM32, codeM32s);

  }

  @Override
  public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {
    ps.println("Codec G93_Huffman");
    if (codecStats == null || nTilesInRaster == 0) {
      ps.format("   Tiles Compressed:  0");
      return;
    }

    ps.format("  Predictor                Times Used        bits/sym    bits/tile  |  m32 avg-len   avg-unique  entropy | bits in tree%n");
 
    for (CodecStats stats : codecStats) {
      String label = stats.getLabel();
      if (label.equalsIgnoreCase("None")) {
        continue;
      }
      long tileCount = stats.getTileCount();
      double bitsPerSymbol = stats.getBitsPerSymbol();
      double avgBitsInTree = stats.getAverageOverhead();
      double avgBitsInText = stats.getAverageLength()*8;
      double avgUniqueSymbols = stats.getAverageObservedMCodes();
      double avgMCodeLength = stats.getAverageMCodeLength();
      double percentTiles = 100.0 * (double) tileCount / nTilesInRaster;
      double entropy = stats.getEntropy();
      ps.format("   %-20.20s %8d (%4.1f %%)      %4.1f  %12.1f   | %10.1f      %6.1f    %6.1f   | %6.1f/%n",
              label, tileCount, percentTiles, 
              bitsPerSymbol, avgBitsInText,
              avgMCodeLength, 
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
  public boolean implementsFloatEncoding() {
   return false;
  }
}
