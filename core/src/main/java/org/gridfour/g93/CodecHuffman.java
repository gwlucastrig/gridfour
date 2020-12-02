/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.
 *
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

import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;
import org.gridfour.compress.PredictorModelLinear;
import org.gridfour.compress.PredictorModelTriangle;
import org.gridfour.compress.PredictorModelDifferencingWithNulls;
import org.gridfour.compress.PredictorModelDifferencing;
import org.gridfour.compress.PredictorModelType;
import org.gridfour.compress.HuffmanDecoder;
import org.gridfour.compress.HuffmanEncoder;
import java.io.IOException;
import java.io.PrintStream;
import org.gridfour.compress.CodecM32;
import org.gridfour.io.BitInputStore;
import org.gridfour.io.BitOutputStore;
import static org.gridfour.util.GridfourConstants.INT4_NULL_CODE;
import org.gridfour.compress.IPredictorModel;

/**
 * Provides a codec for data compression using Huffman codes and the
 * predictor models.
 */
public class CodecHuffman implements ICompressionEncoder, ICompressionDecoder {

    private final IPredictorModel[] predictorModel;

    private CodecStats[] codecStats;

    /**
     * Standard constructor
     */
    public CodecHuffman() {
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

        byte[] mCode = new byte[CodecM32.MAX_BYTES_PER_VALUE * nRows * nCols];

        int resultLength = Integer.MAX_VALUE;
        BitOutputStore resultStore = null;

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

    BitOutputStore compress(int codecIndex, IPredictorModel pcc, byte[] mCodes, int nM32) {
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
        IPredictorModel pcc = this.decodePredictorCorrector(packing[1]);
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
        stats.addToCounts(packing.length - 10, nValues, decoder.getBitsInTreeCount());
        stats.addCountsForM32(nM32, codeM32s);

    }

    @Override
    public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {
        ps.println("Codec G93_Huffman");
        if (codecStats == null || nTilesInRaster == 0) {
            ps.format("   Tiles Compressed:  0%n");
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
            double avgBitsInText = stats.getAverageLength() * 8;
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
    public boolean implementsFloatingPointEncoding() {
        return false;
    }

    @Override
    public boolean implementsIntegerEncoding() {
        return true;
    }
}
