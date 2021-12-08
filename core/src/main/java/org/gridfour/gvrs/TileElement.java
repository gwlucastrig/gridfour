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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.gridfour.io.BufferedRandomAccessFile;

/**
 * Provides methods and elements for accessing a tile from a raster data set.
 */
abstract class TileElement {

  final RasterTile parent;

  final int nRows;
  final int nColumns;
  final int nCells;

  // At this time, name and data type are largely diagnostic
  final String name;
  final GvrsElementType dataType;
  final int standardSizeInBytes;
 

  /**
   * Constructs a element and allocates memory for storage.
   *
   * @param nRows the number of rows in the tile.
   * @param nColumns the number of columns in the tile.
   */
  TileElement(
    RasterTile parent,
    int nRows,
    int nColumns,
    GvrsElementSpecification elementSpec) {
    this.parent = parent;
    this.nRows = nRows;
    this.nColumns = nColumns;
    this.nCells = nRows * nColumns;
    this.name = elementSpec.name;
    this.dataType = elementSpec.dataType;
    
    // The standard size computation requires special treatment.
    // Most of the data types use 4 bytes per value, but the short type
    // uses 2 bytes per cell.  When we have a tile that mixes elements
    // of different types, we need to ensure byte-alignment between
    // elements on a 4-byte boundary.  So the standard size in bytes
    // needs to be a multiple of 4.  For shorts, if there is an
    // odd number of cells, we need to pad the collection with two
    // extra bytes to ensure that the standard size if a multiple of 4.
    int n = nRows*nColumns*dataType.bytesPerSample;
    if(dataType.bytesPerSample!=4){
       n = (n + 3) & 0x7ffffffc;
    }
    this.standardSizeInBytes = n;
  }

  protected ByteBuffer wrapEncodingInByteBuffer(byte[] encoding) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(encoding);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return byteBuffer;
  }
  
  /**
   * Set references to the appropriate tile array elements and index.
   *
   * @param tileIndex the index of the tile for which data references
   * is being transferred
   * @param gElement a valid reference to a GVRS element.
   */
  abstract void transcribeTileReferences(int tileIndex, GvrsElement gElement);

  /**
   * Gets the standard size of the data when stored in non-compressed format.
   * This size is the product of dimension, number of rows and columns, and 4
   * bytes for integer or float formats or 2 bytes for short formats.
   *
   * @return a positive value.
   */
  
  int getStandardSize() {
    return standardSizeInBytes;
  }

  
  

  abstract void writeStandardFormat(BufferedRandomAccessFile braf) throws IOException;

  abstract void readStandardFormat(BufferedRandomAccessFile braf) throws IOException;

  abstract void readCompressedFormat(
    CodecMaster codec,
    BufferedRandomAccessFile braf,
    int payloadSize) throws IOException;

  abstract void setIntValue(int index, int value);

  abstract int getValueInt(int index);

  abstract void setValue(int index, float value);

  abstract float getValue(int index);

  abstract float getFillValue();
  
  abstract int getFillValueInt();
  
  /**
   * Indicates that the data cells for the element include at least one
   * cell with a fill value.
   * @return true if one fill value is found; otherwise false.
   */
  abstract public boolean hasFillDataValues();

  abstract public boolean hasValidData();

  abstract void setToNullState();

  abstract byte[]encode(CodecMaster codec);
  abstract void decode(CodecMaster codec, byte []encoding) throws IOException;
}
