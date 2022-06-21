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
class TileElementFloat extends TileElement {

  final float[] values;
  final float minValue;
  final float maxValue;
  final float fillValue;
  final Float fillValueRef;

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
  TileElementFloat(
    RasterTile parent,
    int nRows,
    int nColumns,
    GvrsElementSpecification elementSpec,
    boolean initializeValues) {
    super(parent, nRows, nColumns, elementSpec);
    GvrsElementSpecificationFloat fSpec = (GvrsElementSpecificationFloat) elementSpec;
    minValue = fSpec.minValue;
    maxValue = fSpec.maxValue;
    fillValue = fSpec.fillValue;
    fillValueRef = fillValue; // automatically boxed to instance of Float

    values = new float[nCells];
    if (initializeValues) {
      Arrays.fill(values, fillValue);
    }
  }

  @Override
  void writeStandardFormat(BufferedRandomAccessFile braf) throws IOException {
    for (int i = 0; i < values.length; i++) {
      braf.leWriteFloat(values[i]);
    }
  }

  @Override
  void readStandardFormat(BufferedRandomAccessFile braf) throws IOException {
    braf.leReadFloatArray(values, 0, values.length);
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
    setValue(index, (float) value);
  }

  @Override
  int getValueInt(int index) {
    if (Float.isNaN(values[index])) {
      return Integer.MIN_VALUE;
    } else {
      return (int) values[index];
    }
  }

  @Override
  void setValue(int index, float value) {
    // using the fillValueRef (an instance of Float) handles the
    // case where both the input and fill values are Float.NaN

    if (minValue <= value && value <= maxValue || fillValueRef.equals(value)) {
      values[index] = value;
      parent.writingRequired = true;
    } else if (Float.isNaN(value)) {
      // we've already established that the fill value is not NaN
      throw new IllegalArgumentException(
        "Value of NaN is not supported by this instance");
    } else {
      throw new IllegalArgumentException("Value " + value
        + " is out of range [" + minValue + ", " + maxValue + "]");
    }
  }

  @Override
  float getValue(int index) {
    return values[index];

  }

  @Override
  public boolean hasFillDataValues() {
    if (Float.isNaN(fillValue)) {
      for (int i = 0; i < values.length; i++) {
        if (Float.isNaN(values[i])) {
          return true;
        }
      }
      return false;
    }
    for (int i = 0; i < values.length; i++) {
      if (values[i] == fillValue) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasValidData() {
    if (Float.isNaN(fillValue)) {
      for (int i = 0; i < values.length; i++) {
        if (!Float.isNaN(values[i])) {
          return true;
        }
      }
      return false;
    }

    for (int i = 0; i < values.length; i++) {
      if (values[i]!=fillValue) {
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
    return "Float Tile";
  }

  @Override
  void transcribeTileReferences(int tileIndex, GvrsElement gElement) {
    gElement.setTileElement(tileIndex, this);
  }

  @Override
  byte[] encode(CodecMaster codec) {
    byte[] encoding = codec.encodeFloats(nRows, nColumns, values);
    if (encoding == null || encoding.length >= standardSizeInBytes) {
      encoding = new byte[standardSizeInBytes];
      ByteBuffer byteBuffer = wrapEncodingInByteBuffer(encoding);
      for (int i = 0; i < values.length; i++) {
        byteBuffer.putFloat(values[i]);
      }
    }
    return encoding;
  }

  @Override
  void decode(CodecMaster codec, byte[] encoding) throws IOException {
    if (encoding.length == standardSizeInBytes) {
      ByteBuffer byteBuffer = wrapEncodingInByteBuffer(encoding);
      for (int i = 0; i < values.length; i++) {
        values[i] = byteBuffer.getFloat();
      }
    } else {
      float[] f = codec.decodeFloats(nRows, nColumns, encoding);
      System.arraycopy(f, 0, values, 0, values.length);
    }
  }

  @Override
  float getFillValue(){
    return fillValue;
  }


  @Override
  int getFillValueInt(){
    return Integer.MIN_VALUE;
  }
}
