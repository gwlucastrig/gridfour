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
 * Provides a concrete definition of a GvrsElementSpecification that specifies
 * integer data
 */
public class GvrsElementSpecificationInt extends GvrsElementSpecification {

  final int minValue;
  final int maxValue;
  final int fillValue;

  /**
   * Constructs an instance giving parameters for a
   * four-byte integer element. Default values are provided
   * as follows:
   * <ul>
   * <li><strong>minimum value</strong> INTEGER.MIN_VALUE+1 (-2147483647)</li>
   * <li><strong>maximum value</strong> INTEGER.MAX_VALUE (2147483647)</li>
   * <li><strong>fill value</strong> INTEGER.MIN_VALUE (-214748368)</li>
   * </ul>
   *
   * @param name a valid, non-blank identifier for the intended element.
   */
  public GvrsElementSpecificationInt(String name) {
    super(name, GvrsElementType.INTEGER);
    this.minValue = Integer.MIN_VALUE + 1;
    this.maxValue = Integer.MAX_VALUE;
    this.fillValue = Integer.MIN_VALUE;
  }

  /**
   * Constructs a specification instance giving parameters for a
   * four-byte integer element. Default values are provided
   * as follows:
   * <ul>
   * <li><strong>minimum value</strong> INTEGER.MIN_VALUE (-2147483648)</li>
   * <li><strong>maximum value</strong> INTEGER.MAX_VALUE (2147483647)</li>
   * </ul>
   *
   * @param name a valid, non-blank identifier for the intended element.
   * @param fillValue the value assigned to unpopulated raster cells.
   */
  public GvrsElementSpecificationInt(String name, int fillValue) {
    super(name, GvrsElementType.INTEGER);
    this.minValue = Integer.MIN_VALUE;
    this.maxValue = Integer.MAX_VALUE;
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
  public GvrsElementSpecificationInt(String name, int minValue, int maxValue, int fillValue) {
    super(name, GvrsElementType.INTEGER);
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.fillValue = fillValue;
  }

  @Override
  GvrsElementSpecification copy() {
    GvrsElementSpecification spec = new GvrsElementSpecificationInt(name, minValue, maxValue, fillValue);
    spec.copyApplicationData(this);
    return spec;
  }

  @Override
  GvrsElement makeElement(GvrsFile file) {
    return new GvrsElementInt(this, minValue, maxValue, fillValue, file);
  }

  @Override
  public String toString() {
    return String.format("GVRS Element Specification: Integer, range [%d,%d], fill %d",
      minValue, maxValue, fillValue);
  }
}
