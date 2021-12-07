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
 * floating-point data
 */
public class GvrsElementSpecFloat extends GvrsElementSpec {

  final float minValue;
  final float maxValue;
  final float fillValue;

  /**
   * Constructs a instance giving parameters for a
   * four-byte floating-point element. Default values are provided
   * as follows:
   * <ul>
   * <li><strong>minimum value</strong> -Float.MAX_VALUE
   * (-3.4028234663852886E38f)</li>
   * <li><strong>maximum value</strong> FLOAT.MAX_VALUE
   * (3.4028234663852886E38f)</li>
   * <li><strong>fill value</strong> FLOAT.NaN</li>
   * </ul>
   *
   * @param name a valid, non-blank identifier for the intended element.
   */
  public GvrsElementSpecFloat(String name) {
    super(name, GvrsElementType.FLOAT);
    this.minValue = Float.NEGATIVE_INFINITY;
    this.maxValue = Float.POSITIVE_INFINITY;
    this.fillValue = Float.NaN;
  }

  /**
   * Constructs a specification instance giving parameters for a
   * four-byte integer element. Default values are provided
   * as follows:
   * <ul>
   * <li><strong>minimum value</strong> -Float.MAX_VALUE
   * (-3.4028234663852886E38f)</li>
   * <li><strong>maximum value</strong> FLOAT.MAX_VALUE
   * (3.4028234663852886E38f)</li>
   * </ul>
   *
   * @param name a valid, non-blank identifier for the intended element.
   * @param fillValue the value assigned to unpopulated raster cells.
   */
  public GvrsElementSpecFloat(String name, float fillValue) {
    super(name, GvrsElementType.FLOAT);
    this.minValue = Float.NEGATIVE_INFINITY;
    this.maxValue = Float.POSITIVE_INFINITY;
    this.fillValue = fillValue;
  }

  /**
   * Constructs a specification instance giving parameters for a
   * four-byte integer element. No default values are provided.
   *
   * @param name a valid, non-blank identifier for the intended element.
   * @param minValue the minimum integer value allowed for input,
   * must be less than or equal to the maximum value
   * @param maxValue the maximum integer value allowed for input,
   * must be greater than or equal to the min value.
   * @param fillValue the value assigned to unpopulated raster cells,
   * does not necessarily have to be with the range of the minimum and
   * maximum values.
   */
  public GvrsElementSpecFloat(String name, float minValue, float maxValue, float fillValue) {
    super(name, GvrsElementType.FLOAT);
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.fillValue = fillValue;
  }

  @Override
  GvrsElementSpec copy() {
    GvrsElementSpec spec = new GvrsElementSpecFloat(name, minValue, maxValue, fillValue);
    spec.copyApplicationData(this);
    return spec;
  }

  @Override
  GvrsElement makeElement(GvrsFile file) {
    return new GvrsElementFloat(this, minValue, maxValue, fillValue, file);
  }

  @Override
  public String toString() {
    return String.format("GVRS Element Specification: float, range [%f,%f], fill %f",
      minValue, maxValue, fillValue);
  }
}
