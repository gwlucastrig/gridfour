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
 * 10/2019  G. Lucas     Created
 *
 * Notes:
 *
 *   TO DO:  we need to implement logic for the case where a value
 *           scales to an adjusted value that is outside the range of
 *           an integer.  When this happens, we cannot compress the data.
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;
import java.util.Arrays;
import org.gridfour.io.BufferedRandomAccessFile;
import static org.gridfour.util.GridfourConstants.INT4_NULL_CODE;

/**
 * Provides methods and elements for accessing a tile from a raster data set.
 */
class RasterTileFloat extends RasterTile {

    final float[] values;
    final float[][] valuesArray;

    /**
     * Constructs a tile and allocates memory for storage.
     *
     * @param tileIndex the index of the tile within the raster grid.
     * @param tileRow the row of the tile within the overall raster grid
     * (strictly
     * for diagnostic purposes).
     * @param tileColumn the column of the tile within the overall raster grid
     * (strictly for diagnostic purposes).
     * @param nRows the number of rows in the tile.
     * @param nColumns the number of columns in the tile.
     */
    RasterTileFloat(
        int tileIndex,
        int tileRow,
        int tileColumn,
        int nRows,
        int nColumns,
        int dimension,
        float valueScale,
        float valueOffset,
        boolean initializeValues) {
        super(tileIndex,
            tileRow,
            tileColumn,
            nRows,
            nColumns,
            dimension,
            valueScale,
            valueOffset);

        valuesArray = new float[dimension][nValues];
        for (int i = 0; i < dimension; i++) {
            valuesArray[i] = new float[nValues];
            if (initializeValues) {
                Arrays.fill(valuesArray[i], INT4_NULL_CODE);
            }
        }
        values = valuesArray[0];
    }

    /**
     * Gets the standard size of the data when stored in non-compressed format.
     * This size is the product of dimension, number of rows and columns, and 4
     * bytes
     * for integer or float formats.
     *
     * @return a positive value.
     */
    @Override
    int getStandardSize() {
        return dimension * nRows * nCols * 4;
    }

    @Override
    void writeStandardFormat(BufferedRandomAccessFile braf) throws IOException {
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            float[] f = valuesArray[iVariable];
            for (int i = 0; i < nValues; i++) {
                braf.leWriteFloat(f[i]);
            }
        }
    }

    @Override
    void readStandardFormat(BufferedRandomAccessFile braf) throws IOException {
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            float[] f = valuesArray[iVariable];
            braf.leReadFloatArray(f, 0, nValues);
        }
    }

    @Override
    void readCompressedFormat(CodecMaster codec, BufferedRandomAccessFile braf, int payloadSize) throws IOException {

        byte[] packing = new byte[payloadSize];
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            braf.readFully(packing, 0, 4);
            int a = packing[0] & 0xff;
            int b = packing[1] & 0xff;
            int c = packing[2] & 0xff;
            int d = packing[3] & 0xff;
            int n = (((((d << 8) | c) << 8) | b) << 8) | a;
            braf.readFully(packing, 0, n);
            if (codec.implementsFloatEncoding()) {
                float[] v = codec.decodeFloats(nRows, nCols, packing);
                System.arraycopy(v, 0, valuesArray[iVariable], 0, values.length);
            } else {
                int[] v = codec.decode(nRows, nCols, packing);
                float[] f = valuesArray[iVariable];
                for (int i = 0; i < values.length; i++) {
                    f[i] = v[i] / valueScale + valueOffset;
                }
            }
        }

    }

    @Override
    void setIntValue(int tileRow, int tileColumn, int value) {
        int index = tileRow * nCols + tileColumn;
        if (value == INT4_NULL_CODE) {
            values[index] = Float.NaN;
        } else {
            values[index] = value / valueScale + valueOffset;
        }
        writingRequired = true;
    }

    @Override
    int getIntValue(int tileRow, int tileColumn) {
        int index = tileRow * nCols + tileColumn;
        if (Float.isNaN(values[index])) {
            return INT4_NULL_CODE;
        }
        return (int) Math.floor((values[index] - valueOffset) * valueScale + 0.5);
    }

    @Override
    void setValue(int tileRow, int tileColumn, float value) {
        int index = tileRow * nCols + tileColumn;
        values[index] = value;
        writingRequired = true;
    }

    @Override
    float getValue(int tileRow, int tileColumn) {
        int index = tileRow * nCols + tileColumn;
        return values[index];
    }

    @Override
    boolean isWritingRequired() {
        return writingRequired;
    }

    @Override
    public boolean hasNullDataValues() {
        for (int i = 0; i < values.length; i++) {
            if (Float.isNaN(values[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasValidData() {
        for (int i = 0; i < values.length; i++) {
            if (!Float.isNaN(values[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    void setToNullState() {
        Arrays.fill(values, Float.NaN);
    }

    @Override
    int[][] getIntCoding() {
        int[][] coding = new int[dimension][];
        for (int iRank = 0; iRank < dimension; iRank++) {
            int[] v = new int[nValues];
            coding[iRank] = v;
            float[] f = valuesArray[iRank];
            for (int i = 0; i < nValues; i++) {
                float test = f[i];
                if (Float.isNaN(test)) {
                    v[i] = INT4_NULL_CODE;
                } else {
                    v[i] = (int) Math.floor((test - valueOffset) * valueScale + 0.5);
                }
            }
        }
        return coding;
    }

    @Override
    public String toString() {
        return String.format("tile (int) %8d (%4d, %4d)%s",
            tileIndex, tileRow, tileCol,
            writingRequired ? " dirty" : "");
    }

    @Override
    void setValues(int tileRow, int tileColumn, float[] input) {
        int index = tileRow * nCols + tileColumn;
        for (int iRank = 0; iRank < dimension; iRank++) {
            valuesArray[iRank][index] = input[iRank];
        }
        writingRequired = true;
    }

    @Override
    void getValues(int tileRow, int tileColumn, float[] output) {
        int index = tileRow * nCols + tileColumn;
        for (int iRank = 0; iRank < dimension; iRank++) {
            output[iRank] = valuesArray[iRank][index];
        }
    }

    @Override
    byte[] getCompressedPacking(CodecMaster codec) throws IOException {
        if (!codec.implementsFloatEncoding()) {
            return super.getCompressedPacking(codec);
        }
        byte[][] results = new byte[dimension][];
        int nBytesTotal = 0;
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            results[iVariable] = codec.encodeFloats(nRows, nCols, valuesArray[iVariable]);
            if (results[iVariable] == null) {
                return null;
            }
            nBytesTotal += results[iVariable].length;
        }

        int k = 0;
        byte b[] = new byte[nBytesTotal + dimension * 4];
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            int n = results[iVariable].length;
            b[k++] = (byte) ((n & 0xff));
            b[k++] = (byte) ((n >> 8) & 0xff);
            b[k++] = (byte) ((n >> 16) & 0xff);
            b[k++] = (byte) ((n >> 24) & 0xff);
            System.arraycopy(results[iVariable], 0, b, k, n);
            k += n;
        }
        return b;
    }
}
