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
package org.gridfour.gvrs;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods and elements for accessing a tile from a raster data set.
 */
class RasterTile {

  final int nRows;
  final int nCols;
  final int tileRow;
  final int tileCol;
  final int nCellsInTile;
  final List<GvrsElementSpecification> elementSpecifications;
  final TileElement[] elements;
  final int standardSize;

  // elements related to maintaining a linked-list
  // of tiles and managing I/O.  These elements are scoped to package
  // level to permit access by associated classes.
  final int tileIndex;
  RasterTile next;
  RasterTile prior;
  boolean writingRequired;

  /**
   * Constructs a tile and allocates memory for storage.
   * <p>
   * The initializeValues setting allows an application to
   * control whether the elements are initialized when the
   * tile is constructed. In cases where the tile is to be
   * used for reading data and will quickly overwrite the content
   * of the elements array, the application may choose to skip the
   * initialization operation.
   *
   * @param tileIndex the index of the tile within the raster grid.
   * @param tileRow the row of the tile within the overall raster grid
   * (strictly
   * for diagnostic purposes).
   * @param tileColumn the column of the tile within the overall raster grid
   * (strictly for diagnostic purposes).
   * @param nRows the number of rows in the tile.
   * @param nColumns the number of columns in the tile.
   * @param elementSpecification list specifying the structure of one
   * or more elements.
   * @param initializeValues specifies whether the element content should
   * be initialized.
   */
  RasterTile(
    int tileIndex,
    int tileRow,
    int tileColumn,
    int nRows,
    int nColumns,
    List<GvrsElementSpecification> elementSpecifications,
    boolean initializeValues) {

    this.tileIndex = tileIndex;
    this.tileRow = tileRow;
    this.tileCol = tileColumn;
    this.nRows = nRows;
    this.nCols = nColumns;
    this.nCellsInTile = nRows * nColumns;
    this.elementSpecifications = elementSpecifications;
    elements = new TileElement[elementSpecifications.size()];
    int k = 0;
    int sumStandardSize = 0;
    for (GvrsElementSpecification spec : elementSpecifications) {
      TileElement e;
      switch (spec.dataType) {
        case INTEGER:
          e = new TileElementInt(this, nRows, nColumns, spec, initializeValues);
          break;
        case FLOAT:
          e = new TileElementFloat(this, nRows, nColumns, spec, initializeValues);
          break;
        case SHORT:
          e = new TileElementShort(this, nRows, nColumns, spec, initializeValues);
          break;
        case INT_CODED_FLOAT:
          e = new TileElementIntCodedFloat(this, nRows, nColumns, spec, initializeValues);
          break;
        default:
          throw new IllegalArgumentException("Unimplemented data type");
      }
      sumStandardSize += e.getStandardSize();
      elements[k++] = e;
    }
    standardSize = sumStandardSize;
  }

  /**
   * Gets the standard size of the data when stored in non-compressed format.
   * This size is the product of dimension, number of rows and columns, and 4
   * bytes
   * for integer or float formats.
   *
   * @return a positive value.
   */
  int getStandardSize() {
    return standardSize;
  }

  void setIntValue(int tileRow, int tileColumn, int value) {
    writingRequired = true;
    int index = tileRow * nCols + tileColumn;
    elements[0].setIntValue(index, value);
  }

  int getIntValue(int tileRow, int tileColumn) {
    int index = tileRow * nCols + tileColumn;
    return elements[0].getValueInt(index);
  }

  void setValue(int tileRow, int tileColumn, float value) {
    writingRequired = true;
    int index = tileRow * nCols + tileColumn;
    elements[0].setValue(index, value);
  }

  float getValue(int tileRow, int tileColumn) {
    int index = tileRow * nCols + tileColumn;
    return elements[0].getValue(index);
  }

  void setIntValue(int tileRow, int tileColumn, int iElement, int value) {
    writingRequired = true;
    int index = tileRow * nCols + tileColumn;
    elements[iElement].setIntValue(index, value);
  }

  int getIntValue(int tileRow, int tileColumn, int iElement) {
    int index = tileRow * nCols + tileColumn;
    return elements[iElement].getValueInt(index);
  }

  void setValue(int tileRow, int tileColumn, int iElement, float value) {
    writingRequired = true;
    int index = tileRow * nCols + tileColumn;
    elements[iElement].setValue(index, value);
  }

  float getValue(int tileRow, int tileColumn, int iElement) {
    int index = tileRow * nCols + tileColumn;
    return elements[iElement].getValueInt(index);
  }

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

  boolean hasNullDataValues() {
    for (int i = 0; i < elements.length; i++) {
      if (elements[i].hasFillDataValues()) {
        return true;
      }
    }
    return false;
  }

  boolean hasValidData() {
    for (int i = 0; i < elements.length; i++) {
      if (elements[i].hasValidData()) {
        return true;
      }
    }
    return false;
  }

  void setToNullState() {
    for (int i = 0; i < elements.length; i++) {
      elements[i].setToNullState();
    }
  }

  int[][] getIntCoding() {
    throw new UnsupportedOperationException("Not implemented");
  }

  byte[] getCompressedPacking(CodecMaster codec) {
    List<byte[]> packingList = new ArrayList<>();
    int nBytesTotal = 0;
    for (TileElement e : this.elements) {
      byte[] test = e.encode(codec);
      packingList.add(test);
      nBytesTotal += test.length + 4;
    }

    byte[] packing = new byte[nBytesTotal];
    int k = 0;
    for (byte[] test : packingList) {
      int n = test.length;
      packing[k++] = (byte) (n & 0xff);
      packing[k++] = (byte) ((n >> 8) & 0xff);
      packing[k++] = (byte) ((n >> 16) & 0xff);
      packing[k++] = (byte) ((n >> 24) & 0xff);
      System.arraycopy(test, 0, packing, k, n);
      k += n;
    }

    return packing;
  }

  @Override
  public String toString() {
    return String.format("tile %8d (%4d, %4d)%s",
      tileIndex, tileRow, tileCol,
      writingRequired ? " dirty" : "");
  }

}
