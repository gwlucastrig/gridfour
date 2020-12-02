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
class RasterTileInt extends RasterTile {

    final int[] values;
    final int[][] valuesArray;

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
    RasterTileInt(
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

        valuesArray = new int[dimension][nValues];
        for (int i = 0; i < dimension; i++) {
            valuesArray[i] = new int[nValues];
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
            int[] v = valuesArray[iVariable];
            for (int i = 0; i < nValues; i++) {
                braf.leWriteInt(v[i]);
            }
        }
    }

    @Override
    void readStandardFormat(BufferedRandomAccessFile braf) throws IOException {
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            braf.leReadIntArray(valuesArray[iVariable], 0, nValues);
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
            int[] v = codec.decode(nRows, nCols, packing);
            if (v == null) {
                // oh snap.
                v = codec.decode(nRows, nCols, packing);
            }
            System.arraycopy(v, 0, valuesArray[iVariable], 0, nValues);
        }
    }

    @Override
    void setIntValue(int tileRow, int tileColumn, int value) {
        int index = tileRow * nCols + tileColumn;
        values[index] = value;
        writingRequired = true;
    }

    @Override
    int getIntValue(int tileRow, int tileColumn) {
        int index = tileRow * nCols + tileColumn;
        return values[index];
    }

    @Override
    void setValue(int tileRow, int tileColumn, float value) {
        int index = tileRow * nCols + tileColumn;
        if (Float.isNaN(value)) {
            values[index] = INT4_NULL_CODE;
        } else {
            values[index] = (int) Math.floor((value - valueOffset) * valueScale + 0.5f);
        }
        writingRequired = true;
    }

    @Override
    float getValue(int tileRow, int tileColumn) {
        int index = tileRow * nCols + tileColumn;
        if (values[index] == INT4_NULL_CODE) {
            return Float.NaN;
        } else {
            return values[index] / valueScale + valueOffset;
        }
    }

    @Override
    boolean isWritingRequired() {
        return writingRequired;
    }

    @Override
    public boolean hasNullDataValues() {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == INT4_NULL_CODE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasValidData() {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != INT4_NULL_CODE) {
                return true;
            }
        }
        return false;
    }

    @Override
    void setToNullState() {
        Arrays.fill(values, INT4_NULL_CODE);
    }

    @Override
    int[][] getIntCoding() {
        return Arrays.copyOf(valuesArray, dimension);
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
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            if (Float.isNaN(input[iVariable])) {
                valuesArray[iVariable][index] = INT4_NULL_CODE;
            } else {
                valuesArray[iVariable][index] = (int) Math.floor((input[iVariable] - valueOffset) * valueScale + 0.5);
            }
        }
        writingRequired = true;
    }

    @Override
    void getValues(int tileRow, int tileColumn, float[] output) {
        int index = tileRow * nCols + tileColumn;
        for (int iVariable = 0; iVariable < dimension; iVariable++) {
            int v = valuesArray[iVariable][index];
            if (v == INT4_NULL_CODE) {
                output[iVariable] = Float.NaN;
            } else {
                output[iVariable] = v / valueScale + valueOffset;
            }
        }
    }

}
