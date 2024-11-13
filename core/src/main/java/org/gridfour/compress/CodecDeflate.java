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
package org.gridfour.compress;

import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import static org.gridfour.util.GridfourConstants.INT4_NULL_CODE;

/**
 * Provides a coder-decoder (codec) for data compression using the standard
 * Deflate (gzip) compressor from the Java API and the predictor
 * models.
 */
public class CodecDeflate implements ICompressionEncoder, ICompressionDecoder {

    private final IPredictorModel[] predictor;

    private CodecStats[] codecStats;

    /**
     * Standard constructor
     */
    public CodecDeflate() {
        predictor = new IPredictorModel[4];
        predictor[0] = new PredictorModelDifferencing();
        predictor[1] = new PredictorModelLinear();
        predictor[2] = new PredictorModelTriangle();
        predictor[3] = new PredictorModelDifferencingWithNulls();
    }

    @Override
    public void analyze(int nRows, int nColumns, byte[] packing) throws IOException {

        if (codecStats == null) {
            PredictorModelType[] pcArray = PredictorModelType.values();
            codecStats = new CodecStats[pcArray.length+1];
            for (int i = 0; i < pcArray.length; i++) {
                codecStats[i] = new CodecStats(pcArray[i]);
            }
            codecStats[pcArray.length] = new CodecStats("All Predictors");
        }

        CodecStats stats = codecStats[packing[1] & 0xff];
        CodecStats total = codecStats[codecStats.length-1];
        int nValues = nRows * nColumns;
        stats.addToCounts(packing.length - 10, nValues, 0);
        total.addToCounts(packing.length - 10, nValues, 0);
        int nM32 = (packing[6] & 0xff)
            | ((packing[7] & 0xff) << 8)
            | ((packing[8] & 0xff) << 16)
            | ((packing[9] & 0xff) << 24);

        byte[] codeM32s = new byte[nM32];
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(packing, 10, packing.length - 10);
            int test = inflater.inflate(codeM32s, 0, nM32);
            inflater.end();
            if (test > 0) {
                stats.addCountsForM32(nM32, codeM32s);
                total.addCountsForM32(nM32, codeM32s);
            }
        } catch (DataFormatException dfe) {
            throw new IOException(dfe.getMessage(), dfe);
        }

    }

    @Override
    public int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
        PredictorModelType predictorType = PredictorModelType.valueOf(packing[1]);
        IPredictorModel pcc = null;
        switch (predictorType) {
            case Differencing:
                pcc = new PredictorModelDifferencing();
                break;
            case Linear:
                pcc = new PredictorModelLinear();
                break;
            case Triangle:
                pcc = new PredictorModelTriangle();
                break;
            case DifferencingWithNulls:
                pcc = new PredictorModelDifferencingWithNulls();
                break;
            default:
                throw new IOException("Unknown PredictorCorrector type");
        }

        int seed
            = (packing[2] & 0xff)
            | ((packing[3] & 0xff) << 8)
            | ((packing[4] & 0xff) << 16)
            | ((packing[5] & 0xff) << 24);
        int nM32 = (packing[6] & 0xff)
            | ((packing[7] & 0xff) << 8)
            | ((packing[8] & 0xff) << 16)
            | ((packing[9] & 0xff) << 24);

        byte[] codeM32s = new byte[nM32];
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(packing, 10, packing.length - 10);
            int test = inflater.inflate(codeM32s);
            inflater.end();
            if (test > 0) {
                int[] output = new int[nRows * nColumns];
                pcc.decode(seed, nRows, nColumns, codeM32s, 0, nM32, output);
                return output;
            }
        } catch (DataFormatException dfe) {
            throw new IOException(dfe.getMessage(), dfe);
        }
        return null;
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
        byte[] resultBytes = null;

        for (int i = 0; i < predictor.length; i++) {
            IPredictorModel testModel = predictor[i];
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
                byte[] testBytes = compress(
                    codecIndex,
                    testModel,
                    mCode,
                    mCodeLength);
                if (testBytes != null && testBytes.length < resultLength) {
                    resultLength = testBytes.length;
                    resultBytes = testBytes;
                }
            }
        }
        return resultBytes;

    }

    byte[] compress(int codecIndex, IPredictorModel pcc, byte[] mCodes, int nM32) {
        int seed = pcc.getSeed();
        Deflater deflater = new Deflater(6);
        deflater.setInput(mCodes, 0, nM32);
        deflater.finish();
        byte[] deflaterResult = new byte[nM32 + 128];
        int dN = deflater.deflate(deflaterResult, 10, deflaterResult.length - 10, Deflater.FULL_FLUSH);
        if (dN <= 0) {
            // deflate failed
            return null;
        }
        deflaterResult[0] = (byte) codecIndex;
        deflaterResult[1] = (byte) pcc.getPredictorType().getCodeValue();
        deflaterResult[2] = (byte) (seed & 0xff);
        deflaterResult[3] = (byte) ((seed >> 8) & 0xff);
        deflaterResult[4] = (byte) ((seed >> 16) & 0xff);
        deflaterResult[5] = (byte) ((seed >> 24) & 0xff);
        deflaterResult[6] = (byte) ((nM32 & 0xff));
        deflaterResult[7] = (byte) ((nM32 >> 8) & 0xff);
        deflaterResult[8] = (byte) ((nM32 >> 16) & 0xff);
        deflaterResult[9] = (byte) ((nM32 >> 24) & 0xff);
        byte[] b = new byte[dN + 10];
        System.arraycopy(deflaterResult, 0, b, 0, b.length);
        return b;
    }

    @Override
    public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {
        ps.println("Gridfour_Deflate                               Compressed Output    |       Predictor Residuals");
        if (codecStats == null || nTilesInRaster == 0) {
            ps.format("   Tiles Compressed:  0%n");
            return;
        }

        ps.format("  Predictor                Times Used        bits/sym    bits/tile  |  m32 avg-len   avg-unique  entropy%n");

        for (CodecStats stats : codecStats) {
            String label = stats.getLabel();
            if (label.equalsIgnoreCase("None")) {
                continue;
            }
            long tileCount = stats.getTileCount();
            double bitsPerSymbol = stats.getBitsPerSymbol();
            double avgBitsInText = stats.getAverageLength() * 8;
            double avgUniqueSymbols = stats.getAverageObservedMCodes();
            double avgMCodeLength = stats.getAverageMCodeLength();
            double percentTiles = 100.0 * (double) tileCount / nTilesInRaster;
            double entropy = stats.getEntropy();
            ps.format("   %-20.20s %8d (%4.1f %%)     %5.2f  %12.1f   | %10.1f      %6.1f    %6.2f%n",
                label, tileCount, percentTiles,
                bitsPerSymbol, avgBitsInText,
                avgMCodeLength,
                avgUniqueSymbols,
                entropy);
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
