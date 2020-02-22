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
import org.gridfour.io.BufferedRandomAccessFile;

/**
 * Provides methods and elements for accessing a tile from a raster data set.
 */
abstract class RasterTile {

  final int nRows;
  final int nCols;
  final int tileRow;
  final int tileCol;
  final int nValues;
  final int dimension;
  final float valueScale;
  final float valueOffset;

  // elements related to maintaining a linked-list 
  // of tiles and managing I/O.  These elements are scoped to package
  // level to permit access by associated classes.
  final int tileIndex;
  RasterTile next;
  RasterTile prior;
  boolean writingRequired;

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
  RasterTile(
          int tileIndex,
          int tileRow,
          int tileColumn,
          int nRows,
          int nColumns,
          int dimension,
          float valueScale,
          float valueOffset) {
    this.tileIndex = tileIndex;
    this.tileRow = tileRow;
    this.tileCol = tileColumn;
    this.nRows = nRows;
    this.nCols = nColumns;
    this.nValues = nRows * nColumns;
    this.dimension = dimension;
    this.valueScale = valueScale;
    this.valueOffset = valueOffset;

  }
  
  byte[] getCompressedPacking(CodecMaster codec) throws IOException {
    // recall that compression is only defined for integers.
    // compress each element of the tile data and collect
    // the packings.  If successful, concatentate them into
    // a single array of bytes for storage in the output file.
    byte[][] results = new byte[dimension][];
    int[][] codings = getIntCoding();
    int nBytesTotal = 0;
    for (int iVariable = 0; iVariable < dimension; iVariable++) {

      results[iVariable] = codec.encode(nRows, nCols, codings[iVariable]);
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

  /**
   * Gets the standard size of the data when stored in non-compressed format.
   * This size is the product of dimension, number of rows and columns, and 4 bytes
   * for integer or float formats.
   *
   * @return a positive value.
   */
  abstract int getStandardSize();

  abstract void writeStandardFormat(BufferedRandomAccessFile braf) throws IOException;

  abstract void readStandardFormat(BufferedRandomAccessFile braf) throws IOException;

  abstract void readCompressedFormat(CodecMaster codec, BufferedRandomAccessFile braf, int payloadSize) throws IOException;

  

  abstract void setIntValue(int tileRow, int tileColumn, int value);

  abstract int getIntValue(int tileRow, int tileColumn);

  abstract void setValue(int tileRow, int tileColumn, float value);

  abstract float getValue(int tileRow, int tileColumn);

  abstract void setValues(int tileRow, int tileColumn, float[] input);

  abstract void getValues(int tileRow, int tileColumn, float[] output);

  boolean isWritingRequired() {
    return writingRequired;
  }

  int getTileIndex() {
    return tileIndex;
  }

  void clearWritingRequired() {
    writingRequired = false;
  }

  void clear() {
    next = null;
    prior = null;
    writingRequired = false;
  }

  abstract boolean hasNullDataValues();

  abstract boolean hasValidData();

  abstract void setToNullState();

  abstract int[][] getIntCoding();

  @Override
  public String toString() {
    return String.format("tile %8d (%4d, %4d)%s",
            tileIndex, tileRow, tileCol,
            writingRequired ? " dirty" : "");
  }

}
