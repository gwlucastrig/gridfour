/*
 * The MIT License
 *
 * Copyright 2020 G. W. Lucas.
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
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 07/2020  G. Lucas     Created
 *
 * Notes:
 *
 * See the class LsEncoder12 and LsOptimalPredictor12 in the lsop module
 * for more notes on the logic used here.
 *
 * One feature of Optimal Predictors is that certain grid cells are
 * "unreachable" to the predictor and must be populated using other means.
 *    1.  The first row and first column are initialized using the
 *        simple Differencing Predictor that is used for other Gridfour encoders.
 *    2.  The second row and second column are initialized using
 *        the simple Triangle Predictor that is used for other Gridfour encoders.
 *    3.  The rows in the interior area of the grid are initialized using
 *        Optimal Predictors, except for the last two columns in each row
 *        which are also unreachable.  These are populated using
 *        the triangle predictor.
 *
 *    Note that the last two columns are populated in the same block of
 *    code as the interior.  At the end of each row of processing,
 *    the two remaining columns are populated using the Triangle Predictor
 * -----------------------------------------------------------------------
 */
package org.gridfour.lsop;

import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.gridfour.compress.CodecStats;
import org.gridfour.compress.HuffmanDecoder;
import org.gridfour.compress.PredictorModelType;
import org.gridfour.io.BitInputStore;
import org.gridfour.compress.CodecM32;
import org.gridfour.compress.ICompressionDecoder;

/**
 * Provides methods and data elements used to decode data compressed
 * using the LSOP format based on the methods of Smith and Lewis'
 * Optimal Predictors.
 * <p>
 * The LS decoder and encoder are separated into separate packages and
 * separate modules in order to manage code dependencies. The encoding
 * process requires solving a 9-variable linear system. Doing so requires
 * the use of a 3rd party Java library, so an implementation that uses the
 * LS format introduces an additional dependency to the code base.
 * But the decoding process does not use any operations that would require
 * an external dependency. Thus the decoder is specified as part of the
 * Gridfour core module, while the encoder is not.
 * <p>
 * The floating-point arithmetic operations in this class are all performed
 * using the Java strictfp specification. This design choice is essential
 * to the correct operation of this module. Because one of the goals for
 * Gridfour is to facilitate portability to other development environments,
 * it is essential that the math operations performed here be reliably
 * reproducible across platforms and programming languages.
 */
strictfp public class LsDecoder12 implements ICompressionDecoder {

    private CodecStats[] codecStats;

    @Override
    public int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {

        LsHeader header = new LsHeader(packing, 0);
        int seed = header.getSeed();
        int nInitializerCodes = header.getCodedInitializerLength();
        int nInteriorCodes = header.getCodedInteriorLength();
        int compressionType = header.getCompressionType();
        int headerSize = header.getHeaderSize();
        float[] u = header.getOptimalPredictorCoefficients();

        byte[] initializerCodes = new byte[nInitializerCodes];
        byte[] interiorCodes = new byte[nInteriorCodes];

        if (compressionType == 0) {
            // Huffman dencoding
            BitInputStore inputStore = new BitInputStore(packing, headerSize, packing.length - headerSize);
            HuffmanDecoder decoder = new HuffmanDecoder();
            decoder.decode(inputStore, nInitializerCodes, initializerCodes);
            decoder.decode(inputStore, nInteriorCodes, interiorCodes);
        } else {
            try {
                Inflater inflater = new Inflater();
                inflater.setInput(packing, headerSize, packing.length - headerSize);
                int test = inflater.inflate(initializerCodes);
                if (test < nInitializerCodes) {
                    throw new IOException("Format mismatch, unable to read initializer codes");
                }
                long nBytesRead = inflater.getBytesRead();
                inflater.end();
                inflater = new Inflater();
                int offset = headerSize + (int) nBytesRead;
                inflater.setInput(packing, offset, packing.length - offset);
                test = inflater.inflate(interiorCodes);
                inflater.end();
                if (test < nInteriorCodes) {
                    throw new IOException("Format mismatch, unable to read interior codes");
                }
            } catch (DataFormatException dfe) {
                throw new IOException(dfe.getMessage(), dfe);
            }
        }
        int[] values = new int[nRows * nColumns];
        CodecM32 m32 = unpackInitializers(initializerCodes, seed, nRows, nColumns, values);
        unpackInterior(interiorCodes, u, m32, nRows, nColumns, values);

        return values;
    }

    private CodecM32 unpackInitializers(byte[] packing, int seed, int nRows, int nColumns, int values[]) {
        CodecM32 m32 = new CodecM32(packing, 0, packing.length);

        // step 1, the first row -------------------
        values[0] = seed;
        int v = seed;
        for (int i = 1; i < nColumns; i++) {
            v += m32.decode();
            values[i] = v;
        }

        // step 2, the left column -------------------------
        v = seed;
        for (int i = 1; i < nRows; i++) {
            v += m32.decode();
            values[i * nColumns] = v;
        }

        // now use the triangle predictor ------------------------------
        // step 4, the second row
        for (int i = 1; i < nColumns; i++) {
            int index = nColumns + i;
            long a = values[index - 1];
            long b = values[index - nColumns - 1];
            long c = values[index - nColumns];
            values[index] = (int) (m32.decode() + ((a + c) - b));
        }

        // step 5, the second column ------------------------
        for (int i = 2; i < nRows; i++) {
            int index = i * nColumns + 1;
            long a = values[index - 1];
            long b = values[index - nColumns - 1];
            long c = values[index - nColumns];
            values[index] = (int) (m32.decode() + ((a + c) - b));
        }

        return m32;
    }

    void unpackInterior(byte[] packing, float[] u, CodecM32 mInit, int nRows, int nColumns, int[] values) {
        // Although the array u[] is indexed from zero, the coefficients
        // for the predictors are numbered starting at one. Here we copy them
        // out so that the code will match up with the indexing used in the
        // original papers.  There may be some small performance gain to be
        // had by not indexing the array u[] multiple times in the loop below,
        // but that is not the main motivation for using the copy variables.
        float u1 = u[0];
        float u2 = u[1];
        float u3 = u[2];
        float u4 = u[3];
        float u5 = u[4];
        float u6 = u[5];
        float u7 = u[6];
        float u8 = u[7];
        float u9 = u[8];
        float u10 = u[9];
        float u11 = u[10];
        float u12 = u[11];

        CodecM32 m32 = new CodecM32(packing, 0, packing.length);

        // in the loop below, we wish to economize on processing by copying
        // the neighbor values into local variables.  In the inner (column)
        // loop, as each raster cell is processed, the local copies of the z values
        // (z1, z2, etc.) are shifted to the left to populate the neighbor
        // values for the next cell in the loop.
        // The reason we do this is two-fold. First, due to arry bounds checking,
        // accessing an array element in Java is more expensive than reading
        // a local variable. Second, promoting an integer to a float also
        // carries a small overhead. The shifting strategy below helps
        // save that processing.  In testing, the extra coding for local variables
        // resulted in about a 10 percent reduction in processing time.
        //
        //   For a given grid cell of interest P, the layout for neighbors is
        //                              iCol
        //        iRow      z6      z1    P     --    --
        //        iRow-1    z7      z2    z3    z4    z5
        //        iRow-2    z8      z9    z10   z11   z12
        //
        //  For example, as we increment iCol, the z1 value from the first iteration
        //  becomes the z6 value for the second, the z2 value from the first becomes
        //  the z7 value for the second, etc.
        for (int iRow = 2; iRow < nRows; iRow++) {
            int index = iRow * nColumns + 2;
            float z1 = values[index - 1];
            float z2 = values[index - nColumns - 1];
            float z3 = values[index - nColumns];
            float z4 = values[index - nColumns + 1];
            float z5; // = values[index - nColumns + 2];  computed below
            float z6 = values[index - 2];
            float z7 = values[index - nColumns - 2];
            float z8 = values[index - 2 * nColumns - 2];
            float z9 = values[index - 2 * nColumns - 1];
            float z10 = values[index - 2 * nColumns];
            float z11 = values[index - 2 * nColumns + 1];
            float z12; // values[index - 2 * nColumns + 2];  computed below
            for (int iCol = 2; iCol < nColumns - 2; iCol++) {
                index = iRow * nColumns + iCol;
                z5 = values[index - nColumns + 2];
                z12 = values[index - 2 * nColumns + 2];
                float p
                    = u1 * z1
                    + u2 * z2
                    + u3 * z3
                    + u4 * z4
                    + u5 * z5
                    + u6 * z6
                    + u7 * z7
                    + u8 * z8
                    + u9 * z9
                    + u10 * z10
                    + u11 * z11
                    + u12 * z12;
                int estimate = StrictMath.round(p);
                values[index] = estimate + m32.decode();

                // perform the shifting operation for all variables so that
                // only z5 and z12 will have to be read from the values array.
                z6 = z1;
                z1 = values[index];

                z7 = z2;
                z2 = z3;
                z3 = z4;
                z4 = z5;

                z8 = z9;
                z9 = z10;
                z10 = z11;
                z11 = z12;
            }

            // The last two columns in the row are "unreachable" to
            // the Optimal Predictor and must be populated using some other
            // predictor.  In this case, we apply the Triangle Predictor.
            index = iRow * nColumns + nColumns - 2;
            long a = values[index - 1];
            long b = values[index - nColumns - 1];
            long c = values[index - nColumns];
            values[index] = (int) (mInit.decode() + ((a + c) - b));
            index++;
            a = values[index - 1];
            b = values[index - nColumns - 1];
            c = values[index - nColumns];
            values[index] = (int) (mInit.decode() + ((a + c) - b));
        }
    }

    @Override
    public void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
        if (codecStats == null) {
            codecStats = new CodecStats[6];
            codecStats[0] = new CodecStats(PredictorModelType.None);
            codecStats[1] = new CodecStats(PredictorModelType.None);
            codecStats[2] = new CodecStats(PredictorModelType.None);
            codecStats[3] = new CodecStats(PredictorModelType.None);
            codecStats[4] = new CodecStats(PredictorModelType.None);
            codecStats[5] = new CodecStats(PredictorModelType.None);
        }

        LsHeader header = new LsHeader(packing, 0);
        int nInitializerCodes = header.getCodedInitializerLength();
        int nInteriorCodes = header.getCodedInteriorLength();
        int format = header.getCompressionType();
        int headerSize = header.getHeaderSize();

        long nBytesForInitializers = 0;
        long nBytesForInterior = 0;
        byte[] initializerCodes = new byte[nInitializerCodes];
        byte[] interiorCodes = new byte[nInteriorCodes];
        if (format == 0) {
            // Huffman encoding
            BitInputStore inputStore
                = new BitInputStore(packing, headerSize, packing.length - headerSize);
            HuffmanDecoder decoder = new HuffmanDecoder();
            decoder.decode(inputStore, nInitializerCodes, initializerCodes);
            int nBitsForInitializers = inputStore.getPosition();
            decoder.decode(inputStore, nInteriorCodes, interiorCodes);
            int nBitsForInterior = inputStore.getPosition() - nBitsForInitializers;
            nBytesForInitializers = (nBitsForInitializers + 7) / 8;
            nBytesForInterior = (nBitsForInterior + 7) / 8;
        } else {
            // Deflate encoding
            try {
                Inflater inflater = new Inflater();
                inflater.setInput(packing, headerSize, packing.length - headerSize);
                int test = inflater.inflate(initializerCodes);
                if (test < nInitializerCodes) {
                    throw new IOException("Format mismatch, unable to read initializer codes");
                }
                nBytesForInitializers = inflater.getBytesRead();
                inflater.end();
                inflater = new Inflater();
                int offset = headerSize + (int) nBytesForInitializers;
                inflater.setInput(packing, offset, packing.length - offset);
                test = inflater.inflate(interiorCodes);
                nBytesForInterior = inflater.getBytesRead();
                inflater.end();
                if (test < nInteriorCodes) {
                    throw new IOException("Format mismatch, unable to read interior codes");
                }
            } catch (DataFormatException dfe) {
                throw new IOException(dfe.getMessage(), dfe);
            }
        }

        // number of symbols for interior, first and last two columns (4*nRows),
        // bottom 2 rows (2*nColumns), deducting for column overlap
        int k = format * 3;
        CodecStats stats = codecStats[k];
        int nSymbolsForInitializers = 4 * nRows + 2 * nColumns - 8;
        stats.addToCounts((int) nBytesForInitializers, nSymbolsForInitializers, 0);
        stats.addCountsForM32(nInitializerCodes, initializerCodes);

        stats = codecStats[k + 1];
        int nSymbolsForInterior = nRows * nColumns - nSymbolsForInitializers;
        stats.addToCounts((int) nBytesForInterior, nSymbolsForInterior, 0);
        stats.addCountsForM32(nInteriorCodes, interiorCodes);

        int nBytesTotal = packing.length - headerSize;
        stats = codecStats[k + 2];
        int nSymbols = nRows * nColumns;
        stats.addToCounts(nBytesTotal, nSymbols, 0);

        // a temporary solution
        byte[] temp = new byte[nInitializerCodes + nInteriorCodes];
        System.arraycopy(initializerCodes, 0, temp, 0, nInitializerCodes);
        System.arraycopy(interiorCodes, 0, temp, nInitializerCodes, nInteriorCodes);
        stats.addCountsForM32(temp.length, temp);
        //stats.addCountsForM32(nInitializerCodes, initializerCodes);
        //stats.addCountsForM32(nInteriorCodes, interiorCodes);
    }

    @Override
    public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {
        ps.println("LSOP12                                         Compressed Output    |       Predictor Residuals");
        if (codecStats == null || nTilesInRaster == 0) {
            ps.format("   Tiles Compressed:  0%n");
            return;
        }
        for (int iGroup = 0; iGroup < 2; iGroup++) {
            if (iGroup == 0) {
                ps.format("  Huffman%n");
            } else {
                ps.format("  Deflate%n");
            }
            ps.format("   Phase                   Times Used        bits/sym    bits/tile  |  m32 avg-len   avg-unique  entropy%n");

            for (int iStats = 0; iStats < 3; iStats++) {
                CodecStats stats = codecStats[iGroup * 3 + iStats];
                String label;
                switch (iStats) {
                    case 0:
                        label = "Initializers";
                        break;
                    case 1:
                        label = "Interior";
                        break;
                    default:
                        label = "Total";
                        break;
                }

                long tileCount = stats.getTileCount();
                double bitsPerSymbol = stats.getBitsPerSymbol();
                double avgBitsInText = stats.getAverageLength() * 8;
                double avgUniqueSymbols = stats.getAverageObservedMCodes();
                double avgMCodeLength = stats.getAverageMCodeLength();
                double percentTiles = 100.0 * (double) tileCount / nTilesInRaster;
                double entropy = stats.getEntropy();
                stats = codecStats[iGroup * 3 + iStats];
                String timesUsed;
                if (iStats == 0 || iStats == 1) {
                    timesUsed = "                 ";
                } else {
                    timesUsed = String.format("%8d (%4.1f %%)", tileCount, percentTiles);
                }
                ps.format("   %-20.20s %s     %5.2f  %12.1f   | %10.1f      %6.1f    %6.2f%n",
                    label,
                    timesUsed,
                    bitsPerSymbol,
                    avgBitsInText,
                    avgMCodeLength,
                    avgUniqueSymbols,
                    entropy);
            }
        }

    }

    @Override
    public void clearAnalysisData() {
        if (codecStats != null) {
            codecStats = null;
        }
    }

    @Override
    public float[] decodeFloats(int nRows, int nColumns, byte[] packing) throws IOException {
        return null;
    }

}
