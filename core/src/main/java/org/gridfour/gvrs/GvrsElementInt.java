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
 * four-byte signed integer data elements.
 */
public class GvrsElementInt extends GvrsElement {

  final int minValue;
  final int maxValue;
  final int fillValue;

  /**
   * Constructs a specification instance giving parameters for a
   * four-byte integer element. No default values are provided.
   *
   * @param eSpec the element specification for this instance
   * @param minValue the minimum integer value allowed for input,
   * must be less than or equal to the maximum value
   * @param maxValue the maximum integer value allowed for input,
   * must be greater than or equal to the min value.
   * @param fillValue the value assigned to unpopulated raster cells,
   * does not necessarily have to be with the range of the minimum and
   * maximum values.
   * @param file The the file with which this instance is associated.
   */
  GvrsElementInt(GvrsElementSpecification eSpec, int minValue, int maxValue, int fillValue, GvrsFile file) {
    super(eSpec, GvrsElementType.INTEGER, file);
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.fillValue = fillValue;
  }

  /**
   * Read an integer value from the GvrsFile. If no data exists for the
   * specified row and column, the fill value will be returned.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @return an integer value.
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  @Override
  public int readValueInt(int row, int column) throws IOException {

    accessIndices.computeAccessIndices(row, column);
    if (tileIndex != accessIndices.tileIndex) {
      if (!gvrsFile.loadTile(accessIndices.tileIndex, false)) {
        return fillValue;
      }
    }
    return tileElement.getValueInt(accessIndices.indexInTile);

  }

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
    return readValueInt(row, column);
  }

  @Override
  public void writeValue(int row, int col, float value) throws IOException {
    writeValueInt(row, col, (int) value);
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



  @Override
  public String toString() {
    return String.format("GVRS Element Spec: Integer, range [%d,%d], fill %d",
      minValue, maxValue, fillValue);
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
  public int getFillValue() {
    return fillValue;
  }

  /**
   * Gets the maximum value specified for the range of this instance.
   * The maximum value is specified when a GVRS file is first
   * created and may not be modified afterwards.
   *
   * @return an arbitrary integer
   */
  public int getMaxValue() {
    return maxValue;
  }

  /**
   * Gets the minimum value specified for the range of this instance.
   * The minimum value is specified when a GVRS file is first created
   * and may not be modified afterwards.
   *
   * @return an arbitrary integer
   */
  public int getMinValue() {
    return minValue;
  }

}
