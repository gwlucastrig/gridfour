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

/**
 * Provides a concrete definition of a GvrsElementSpec that specifies
 * short-integer data
 */
public class GvrsElementSpecShort extends GvrsElementSpec {

  final short minValue;
  final short maxValue;
  final short fillValue;

  /**
   * Constructs an instance giving parameters for a
   * two-byte short-integer element. Default values are provided
   * as follows:
   * <ul>
   * <li><strong>minimum value</strong> Short.MIN_VALUE+1 (-32767)</li>
   * <li><strong>maximum value</strong> Short.MAX_VALUE (32767)</li>
   * <li><strong>fill value</strong> Short.MIN_VALUE (-32768)</li>
   * </ul>
   *
   * @param name a valid, non-blank identifier for the intended element.
   */
  public GvrsElementSpecShort(String name) {
    super(name, GvrsElementType.SHORT);
    this.minValue = Short.MIN_VALUE + 1;
    this.maxValue = Short.MAX_VALUE;
    this.fillValue = Short.MIN_VALUE;
  }

  /**
   * Constructs a specification instance giving parameters for a
   * four-byte short element. Default values are provided
   * as follows:
   * <ul>
   * <li><strong>minimum value</strong> Short.MIN_VALUE (-32768)</li>
   * <li><strong>maximum value</strong> Short.MAX_VALUE (32767)</li>
   * </ul>
   *
   * @param name a valid, non-blank identifier for the intended element.
   * @param fillValue the value assigned to unpopulated raster cells.
   */
  public GvrsElementSpecShort(String name, short fillValue) {
    super(name, GvrsElementType.SHORT);
    this.minValue = Short.MIN_VALUE;
    this.maxValue = Short.MAX_VALUE;
    this.fillValue = fillValue;
  }

  /**
   * Constructs a specification instance giving parameters for a
   * two-byte short integer element. No default values are provided.
   *
   * @param name a valid, non-blank identifier for the intended element.
   * @param minValue the minimum short value allowed for input,
   * must be less than or equal to the maximum value
   * @param maxValue the maximum short value allowed for input,
   * must be greater than or equal to the min value.
   * @param fillValue the value assigned to unpopulated raster cells,
   * does not necessarily have to be with the range of the minimum and
   * maximum values.
   */
  public GvrsElementSpecShort(String name, short minValue, short maxValue, short fillValue) {
    super(name, GvrsElementType.SHORT);
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.fillValue = fillValue;
  }

  @Override
  GvrsElementSpec copy() {
    GvrsElementSpec spec = new GvrsElementSpecShort(name, minValue, maxValue, fillValue);
    spec.copyApplicationData(this);
    return spec;
  }

  @Override
  GvrsElement makeElement(GvrsFile file) {
    return new GvrsElementShort(this, minValue, maxValue, fillValue, file);
  }

  @Override
  public String toString() {
    return String.format("GVRS Element Specification: Short, range [%d,%d], fill %d",
      minValue, maxValue, fillValue);
  }
}
