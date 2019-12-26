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
import static org.gridfour.g93.G93FileConstants.NULL_DATA_CODE;

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
   * @param tileRow the row of the tile within the overall raster grid (strictly
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
          int rank,
          float valueScale,
          float valueOffset,
          boolean initializeValues) {
    super(
            tileIndex,
            tileRow,
            tileColumn,
            nRows,
            nColumns,
            rank,
            valueScale,
            valueOffset);

    valuesArray = new int[rank][nValues];
    for (int i = 0; i < rank; i++) {
      valuesArray[i] = new int[nValues];
      if (initializeValues) {
        Arrays.fill(valuesArray[i], NULL_DATA_CODE);
      }
    }
    values = valuesArray[0];
  }

  /**
   * Gets the standard size of the data when stored in non-compressed format.
   * This size is the product of rank, number of rows and columns, and 4 bytes
   * for integer or float formats.
   *
   * @return a positive value.
   */
  @Override
  int getStandardSize() {
    return rank * nRows * nCols * 4;
  }

  @Override
  void writeStandardFormat(BufferedRandomAccessFile braf) throws IOException {
    for (int iRank = 0; iRank < rank; iRank++) {
      int[] v = valuesArray[iRank];
      for (int i = 0; i < nValues; i++) {
        braf.leWriteInt(v[i]);
      }
    }
  }

  @Override
  void readStandardFormat(BufferedRandomAccessFile braf) throws IOException {
    for (int iRank = 0; iRank < rank; iRank++) {
      braf.leReadIntArray(valuesArray[iRank], 0, nValues);
    }
  }

  @Override
  void readCompressedFormat(CodecMaster codec, BufferedRandomAccessFile braf, int payloadSize) throws IOException {
    byte[] packing = new byte[payloadSize];
    for (int iRank = 0; iRank < rank; iRank++) {
      braf.readFully(packing, 0, 4);
      int a = packing[0] & 0xff;
      int b = packing[1] & 0xff;
      int c = packing[2] & 0xff;
      int d = packing[3] & 0xff;
      int n = (((((d << 8) | c) << 8) | b) << 8) | a;
      braf.readFully(packing, 0, n);
      int[] v = codec.decode(nRows, nCols, packing);
      if(v==null){
        // oh snap.
        v = codec.decode(nRows, nCols, packing);
      }
      System.arraycopy(v, 0, valuesArray[iRank], 0, nValues);
    }
  }


  @Override
  void setIntValue(int tileRow, int tileColumn, int value) {
    int index = tileRow * nRows + tileColumn;
    values[index] = value;
    writingRequired = true;
  }

  @Override
  int getIntValue(int tileRow, int tileColumn) {
    int index = tileRow * nRows + tileColumn;
    return values[index];
  }

  @Override
  void setValue(int tileRow, int tileColumn, float value) {
    int index = tileRow * nRows + tileColumn;
    if (Float.isNaN(value)) {
      values[index] = NULL_DATA_CODE;
    } else {
      values[index] = (int) Math.floor((value - valueOffset) * valueScale + 0.5);
    }
    writingRequired = true;
  }

  @Override
  float getValue(int tileRow, int tileColumn) {
    int index = tileRow * nRows + tileColumn;
    if (values[index] == NULL_DATA_CODE) {
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
      if (values[i] == NULL_DATA_CODE) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasValidData() {
    for (int i = 0; i < values.length; i++) {
      if (values[i] != NULL_DATA_CODE) {
        return true;
      }
    }
    return false;
  }

  @Override
  void setToNullState() {
    Arrays.fill(values, NULL_DATA_CODE);
  }

  @Override
  int[][] getIntCoding() {
    return Arrays.copyOf(valuesArray, rank);
  }

  @Override
  public String toString() {
    return String.format("tile (int) %8d (%4d, %4d)%s",
            tileIndex, tileRow, tileCol,
            writingRequired ? " dirty" : "");
  }

  @Override
  void setValues(int tileRow, int tileColumn, float[] input) {
    int index = tileRow * nRows + tileColumn;
    for (int iRank = 0; iRank < rank; iRank++) {
      if (Float.isNaN(input[iRank])) {
        valuesArray[iRank][index] = NULL_DATA_CODE;
      } else {
        valuesArray[iRank][index] = (int) Math.floor((input[iRank] - valueOffset) * valueScale + 0.5);
      }
    }
    writingRequired = true;
  }

  @Override
  void getValues(int tileRow, int tileColumn, float[] output) {
    int index = tileRow * nRows + tileColumn;
    for (int iRank = 0; iRank < rank; iRank++) {
      int v = valuesArray[iRank][index];
      if (v == NULL_DATA_CODE) {
        output[iRank] = Float.NaN;
      } else {
        output[iRank] = v / valueScale + valueOffset;
      }
    }
  }

}
