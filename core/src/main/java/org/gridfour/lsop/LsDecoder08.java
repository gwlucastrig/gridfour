/* --------------------------------------------------------------------
 * Copyright (C) 2020  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
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
 * -----------------------------------------------------------------------
 */
package org.gridfour.lsop;

import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.gridfour.compress.CodecM32;
import org.gridfour.compress.HuffmanDecoder;
import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.PredictorModelType;
import org.gridfour.g93.CodecStats;


import org.gridfour.io.BitInputStore;

/**
 * Provides methods and data elements used to decode data compressed
 * using the G93-LS format based on the methods of Smith and Lewis'
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
 */
public class LsDecoder08 implements ICompressionDecoder {

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
        unpackInitializers(initializerCodes, seed, nRows, nColumns, values);
        unpackInterior(interiorCodes, u, nRows, nColumns, values);
        return values;
    }

    private void unpackInitializers(byte[] packing, int seed, int nRows, int nColumns, int values[]) {
        CodecM32 m32 = new CodecM32(packing, 0, packing.length);
        values[0] = seed;
        int v = seed;
        for (int i = 1; i < nColumns; i++) {
            v += m32.decode();
            values[i] = v;
        }
        v = seed;
        for (int i = 0; i < nColumns; i++) {
            v += m32.decode();
            values[nColumns + i] = v;
        }
        for (int i = 2; i < nRows; i++) {
            int offset = i * nColumns;
            values[offset] = values[offset - nColumns] + m32.decode();
            values[offset + 1] = values[offset] + m32.decode();
        }
    }

    void unpackInterior(byte[] packing, float[] u, int nRows, int nColumns, int[] values) {
        float u0 = u[0];
        float u1 = u[1];
        float u2 = u[2];
        float u3 = u[3];
        float u4 = u[4];
        float u5 = u[5];
        float u6 = u[6];
        float u7 = u[7];
        CodecM32 m32 = new CodecM32(packing, 0, packing.length);
        for (int iRow = 2; iRow < nRows; iRow++) {
            for (int iCol = 2; iCol < nColumns; iCol++) {
                int index = iRow * nColumns + iCol;
                float p
                    = u0 * values[index - 1]
                    + u1 * values[index - nColumns - 1]
                    + u2 * values[index - nColumns]
                    + u3 * values[index - 2]
                    + u4 * values[index - nColumns - 2]
                    + u5 * values[index - 2 * nColumns - 2]
                    + u6 * values[index - 2 * nColumns - 1]
                    + u7 * values[index - 2 * nColumns];
                values[index] = (int) (p + 0.5f) + m32.decode();
            }
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

        int k = format * 3;
        CodecStats stats = codecStats[k];
        int nSymbols = 2 * (nColumns + nRows) - 4;
        stats.addToCounts((int) nBytesForInitializers, nSymbols, 0);
        stats.addCountsForM32(nInitializerCodes, initializerCodes);

        stats = codecStats[k + 1];
        nSymbols = (nRows - 2) * (nColumns - 2);
        stats.addToCounts((int) nBytesForInterior, nSymbols, 0);
        stats.addCountsForM32(nInteriorCodes, interiorCodes);

        int nBytesTotal = packing.length - 49;
        stats = codecStats[k + 2];
        nSymbols = nRows * nColumns;
        stats.addToCounts(nBytesTotal, nSymbols, 0);
        stats.addCountsForM32(nInitializerCodes, initializerCodes);
        stats.addCountsForM32(nInteriorCodes, interiorCodes);
    }

    @Override
    public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {
        ps.println("Codec G93_LS");
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
                ps.format("   %-20.20s %s      %4.1f  %12.1f   | %10.1f      %6.1f    %6.1f%n",
                    label, timesUsed,
                    bitsPerSymbol, avgBitsInText,
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
