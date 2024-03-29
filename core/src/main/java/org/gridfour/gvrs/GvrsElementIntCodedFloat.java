/*
 * The MIT License
 *
 * Copyright 2021 G. W. Lucas.
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
 * 10/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

import java.io.IOException;
import org.gridfour.coordinates.GridPoint;

/**
 * Provides a concrete definition of the GvrsElement class to access
 * four-byte floating-point values encoded as integers.
 */
public class GvrsElementIntCodedFloat extends GvrsElement {

  final float minValue;
  final float maxValue;
  final float fillValue;
  final int minValueI;
  final int maxValueI;
  final int fillValueI;
  final float scale;
  final float offset;

  /**
   * Constructs a GVRS element based on the integer-coded-float data format.
   *
   * @param eSpec The specification for the element
   * @param file The file with which this instance is associated.
   */
  GvrsElementIntCodedFloat(GvrsElementSpecificationIntCodedFloat eSpec, GvrsFile file) {
    super(eSpec, GvrsElementType.INT_CODED_FLOAT, file);
    this.minValue = eSpec.minValue;
    this.maxValue = eSpec.maxValue;
    this.fillValue = eSpec.fillValue;
    this.minValueI = eSpec.minValueI;
    this.maxValueI = eSpec.maxValueI;
    this.fillValueI = eSpec.fillValueI;
    this.scale = eSpec.scale;
    this.offset = eSpec.offset;
  }

  /**
   * Read the encoded integer value from the GvrsFile. If no data exists for the
   * specified row and column, the fill value will be returned.
   * <p>
   * <strong>Note:</strong> This class is unusual for GVRS because the
   * integer return value for this method is not a simple casting of the
   * floating point value. Rather, this method returns the integer-coded
   * representation of the floating-point value.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @return an integer value in the integer-coded-float format.
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  @Override
  public int readValueInt(int row, int column) throws IOException {
    accessIndices.computeAccessIndices(row, column);
    if (tileIndex != accessIndices.tileIndex) {
      if (!gvrsFile.loadTile(accessIndices.tileIndex, false)) {
        return fillValueI;
      }
    }
    return tileElement.getValueInt(accessIndices.indexInTile);
  }

  /**
   * Stores an integer value in the GvrsFile; while this method is provided
   * for compatibility with the GvrsElement base class, its use requires
   * special handling.
   * <p>
   * <strong>Note:</strong> This class is unusual for GVRS because the
   * value stored by this method is not a simple equivalent of the
   * floating point value. Rather, this method treats the input integer
   * value as the integer code for an associated floating-point value.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @param value an integer value in the integer-coded-float format.
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  @Override
  public void writeValueInt(int row, int column, int value) throws IOException {
    if (!gvrsFile.isOpenedForWriting()) {
      throw new IOException("Raster file not opened for writing");
    }
    accessIndices.computeAccessIndices(row, column);
    if (tileIndex != accessIndices.tileIndex) {
      // because write access is enabled, the load tile
      // operation will always return a value of true
      // unless an IOException was thrown while writing a new tile
      gvrsFile.loadTile(accessIndices.tileIndex, true);
    }

    tileElement.setIntValue(accessIndices.indexInTile, value);
  }

  @Override
  public float readValue(int row, int column) throws IOException {
    accessIndices.computeAccessIndices(row, column);
    if (tileIndex != accessIndices.tileIndex) {
      if (!gvrsFile.loadTile(accessIndices.tileIndex, false)) {
        return fillValue;
      }
    }
    return tileElement.getValue(accessIndices.indexInTile);
  }

  @Override
  public void writeValue(int row, int col, float value) throws IOException {
    if (!gvrsFile.isOpenedForWriting()) {
      throw new IOException("Raster file not opened for writing");
    }
    accessIndices.computeAccessIndices(row, col);
    if (tileIndex != accessIndices.tileIndex) {
      // because write access is enabled, the load tile
      // operation will always return a value of true
      // unless an IOException was thrown while writing a new tile
      gvrsFile.loadTile(accessIndices.tileIndex, true);
    }

    tileElement.setValue(accessIndices.indexInTile, value);
  }

  @Override
  public float readValue(GridPoint gridPoint) throws IOException {
    return readValue(gridPoint.getRowInt(), gridPoint.getColumnInt());
  }

  @Override
  public int readValueInt(GridPoint gridPoint) throws IOException {
    return readValueInt(gridPoint.getRowInt(), gridPoint.getColumnInt());
  }


  @Override
  public void writeValue(GridPoint gridPoint, float value) throws IOException {
    writeValue(gridPoint.getRowInt(), gridPoint.getColumnInt(), value);
  }

  @Override
  public void writeValueInt(GridPoint gridPoint, int value) throws IOException {
    writeValue(
      gridPoint.getRowInt(), gridPoint.getColumnInt(), value);
  }


  public boolean isValueSupported(float value) {
    if (minValue <= value && value <= maxValue || value == fillValue) {
      return true;
    } else if (Float.isNaN(value)) {
       return Float.isNaN(fillValue);
    } else {
      return false;
    }
  }

  /**
   * Converts a floating-point value to its associated integer code
   * based on the scale and offset parameters set for this instance.
   *
   * @param value a floating point value within the range of
   * values supported by this instance
   * @return if successful, an integer value
   */
  public int mapValueToInteger(float value) {
    if (minValue <= value && value <= maxValue || value == fillValue) {
      return (int) Math.floor((value - offset) * scale + 0.5);
    } else if (Float.isNaN(value)) {
      if (Float.isNaN(fillValue)) {
        return fillValueI;
      }
      throw new IllegalArgumentException("Value of NaN is not supported by this instance");
    } else {
      throw new IllegalArgumentException("Value " + value
        + " is out of range [" + minValue + ", " + maxValue + "]");
    }
  }

  /**
   * Converts a coded integer to its associated floating-point value
   * based on the scale and offset parameters set for this instance.
   *
   * @param integerCode a coded integer value within the range of
   * values supported by this instance
   * @return if successful, an integer value
   */
  public float mapIntegerToValue(int integerCode) {
    if (minValueI <= integerCode && integerCode <= maxValueI) {
      return integerCode / scale + offset;
    } else if (integerCode == fillValueI) {
      return fillValue;
    } else {
      throw new IllegalArgumentException("Integer code " + integerCode
        + " is out of range [" + minValueI + ", " + maxValueI + "]");
    }

  }

  @Override
  public String toString() {
    return String.format(
      "GVRS Element: integer-coded float, range [%f,%f], fill %f, scale %f, offset %f",
      minValue, maxValue, fillValue, scale, offset);
  }

  /**
   * Gets the value that is assigned to all raster cells
   * that have not been otherwise populated. Some data sources may
   * treat this value as a "no-data" value while others may treat it
   * as a meaningful default value. The fill value is specified
   * when a GVRS file is first created and may not be modified afterwards.
   * <p>
   * The fill value is not required to be within the range specified
   * by the minimum and maximum values.
   *
   * @return an arbitrary integer.
   */
  public int getFillValueInt() {
    return fillValueI;
  }

  /**
   * Gets the maximum value specified for the range of this instance.
   * The maximum value is specified when a GVRS file is first
   * created and may not be modified afterwards.
   *
   * @return an arbitrary integer
   */
  public int getMaxValueInt() {
    return maxValueI;
  }

  /**
   * Gets the minimum value specified for the range of this instance.
   * The minimum value is specified when a GVRS file is first created
   * and may not be modified afterwards.
   *
   * @return an arbitrary integer
   */
  public int getMinValueInt() {
    return minValueI;
  }

  /**
   * Gets the minimum floating-point value specified for the range of this
   * instance.
   * The minimum value is specified when a GVRS file is first created
   * and may not be modified afterwards.
   * <p>
   * The value returned from this method is computed from the
   * minimum integer value, scale, and offset specified for this instance.
   * Due to the limits of numerical precision, it may not be a perfect
   * match for an integral value even in cases where that is expected.
   *
   * @return a computed floating-point value
   */
  public float getMinValue() {
    return minValue;
  }

  /**
   * Gets the maximum floating-point value specified for the range of this
   * instance.
   * The maximum value is specified when a GVRS file is first created
   * and may not be modified afterwards.
   * <p>
   * The value returned from this method is computed from the
   * minimum integer value, scale, and offset specified for this instance.
   * Due to the limits of numerical precision, it may not be a perfect
   * match for an integral value even in cases where that is expected.
   *
   * @return a computed floating-point value
   */
  public float getMaxValue() {
    return maxValue;
  }

  /**
   * Gets the value that is assigned to all raster cells
   * that have not been otherwise populated. Some data sources may
   * treat this value as a "no-data" value while others may treat it
   * as a meaningful default value. The fill value is specified
   * when a GVRS file is first created and may not be modified afterwards.
   * <p>
   * The fill value is not required to be within the range specified
   * by the minimum and maximum values.
   * <p>
   * A fill value is allowed to be Float.NAN.
   *
   * @return an arbitrary floating-point value.
   */
  public float getFillValue() {
    return fillValue;
  }

}
