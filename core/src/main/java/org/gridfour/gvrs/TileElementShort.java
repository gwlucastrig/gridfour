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
import java.util.Arrays;
import org.gridfour.io.BufferedRandomAccessFile;

/**
 * Provides methods and elements for accessing a tile from a raster data set.
 */
class TileElementShort extends TileElement {

  final short[] values;
  final short minValue;
  final short maxValue;
  final short fillValue;

  /**
   * Constructs a element and allocates memory for storage.
   * <p>
   * The initializeValues setting allows an application to
   * control whether the values array is initialized when the
   * tile is constructed. In cases where the element is to be
   * used for reading data and will quickly overwrite the content
   * of the values array, the application may choose to skip the
   * initialization operation.
   *
   * @param nRows the number of rows in the tile.
   * @param nColumns the number of columns in the tile.
   * @param elementSpec a specification for the element including identifying
   * information, initial values, etc.
   * @param initializeValues specify whether values are to be initialized
   */
  TileElementShort(
    RasterTile parent,
    int nRows,
    int nColumns,
    GvrsElementSpec elementSpec,
    boolean initializeValues) {
    super(parent, nRows, nColumns, elementSpec);
    GvrsElementSpecShort sSpec = (GvrsElementSpecShort) elementSpec;
    minValue = sSpec.minValue;
    maxValue = sSpec.maxValue;
    fillValue = sSpec.fillValue;

    values = new short[nCells];
    if (initializeValues) {
      Arrays.fill(values, fillValue);
    }
  }

  @Override
  int getStandardSize() {
    // for compatibility with larger data types (4-byte integers and floats)
    // the standard size for a short may be larger than nCells*2.
    // See the abstract base class TileElement for an explanation.
    return standardSizeInBytes;
  }

  @Override
  void writeStandardFormat(BufferedRandomAccessFile braf) throws IOException {
    for (int i = 0; i < values.length; i++) {
      braf.leWriteShort(values[i]);
    }
    // if there are an odd number of values in the tile, we need to
    // add one more short to ensure that the overall format sizez
    // is a mutiple of 4.
    if ((values.length & 1) != 0) {
      braf.leWriteShort(0);
    }
  }

  @Override
  void readStandardFormat(BufferedRandomAccessFile braf) throws IOException {
    braf.leReadShortArray(values, 0, values.length);
    if ((values.length & 1) != 0) {
      braf.skipBytes(2);  // padding for odd number of values
    }
  }

  @Override
  void readCompressedFormat(CodecMaster codec, BufferedRandomAccessFile braf, int payloadSize) throws IOException {
    byte[] packing = new byte[payloadSize];
    braf.readFully(packing, 0, 4);
    int a = packing[0] & 0xff;
    int b = packing[1] & 0xff;
    int c = packing[2] & 0xff;
    int d = packing[3] & 0xff;
    int n = (((((d << 8) | c) << 8) | b) << 8) | a;
    braf.readFully(packing, 0, n);
    int[] v = codec.decode(nRows, nColumns, packing);
    System.arraycopy(v, 0, values, 0, values.length);

  }

  @Override
  void setIntValue(int index, int value) {
    if (minValue <= value && value <= maxValue || value == fillValue) {
      values[index] = (short) value;
      parent.writingRequired = true;
    } else {
      throw new IllegalArgumentException("Value " + value + " is not within range [" + minValue + ", " + maxValue + "]");
    }
  }

  @Override
  int getValueInt(int index) {
    return values[index];
  }

  @Override
  void setValue(int index, float value) {
    if (minValue <= value && value <= maxValue || value == fillValue) {
      values[index] = (short) value;
      parent.writingRequired = true;
    } else if (Float.isFinite(value)) {
      throw new IllegalArgumentException("Value " + value
        + " is out of range [" + minValue + ", " + maxValue + "]");
    } else if (Float.isNaN(value)) {
      throw new IllegalArgumentException("NaN value not supported");
    } else {
      throw new IllegalArgumentException("Infinite values are not supported");
    }

  }

  @Override
  float getValue(int index) {
    if (values[index] == fillValue) {
      return Float.NaN;
    } else {
      return values[index];
    }
  }

  @Override
  public boolean hasFillDataValues() {
    for (int i = 0; i < values.length; i++) {
      if (values[i] == fillValue) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasValidData() {
    for (int i = 0; i < values.length; i++) {
      if (values[i] != fillValue) {
        return true;
      }
    }
    return false;
  }

  @Override
  void setToNullState() {
    Arrays.fill(values, fillValue);
  }

  @Override
  public String toString() {
    return "Short Tile";
  }

  @Override
  void transcribeTileReferences(int tileIndex, GvrsElement gElement) {
    gElement.setTileElement(tileIndex, this);
  }

  @Override
  byte[] encode(CodecMaster codec) {
    int[] iValue = new int[nCells];
    for (int index = 0; index < nCells; index++) {
      iValue[index] = values[index];
    }
    byte[] encoding = codec.encode(nRows, nColumns, iValue);
    if (encoding == null || encoding.length >= standardSizeInBytes) {
      encoding = new byte[standardSizeInBytes];
      ByteBuffer byteBuffer = wrapEncodingInByteBuffer(encoding);
      for (int i = 0; i < values.length; i++) {
        byteBuffer.putShort(values[i]);
      }
    }
    return encoding;
  }

  @Override
  void decode(CodecMaster codec, byte[] encoding) throws IOException {
    if (encoding.length == standardSizeInBytes) {
      ByteBuffer byteBuffer = wrapEncodingInByteBuffer(encoding);
      for (int i = 0; i < values.length; i++) {
        values[i] = byteBuffer.getShort();
      }
    } else {
      int[] iDecoding = codec.decode(nRows, nColumns, encoding);
      for (int i = 0; i < values.length; i++) {
        values[i] = (short) iDecoding[i];
      }
    }
  }

  @Override
  float getFillValue() {
    return Float.NaN;
  }

  @Override
  int getFillValueInt() {
    return fillValue;
  }
}
