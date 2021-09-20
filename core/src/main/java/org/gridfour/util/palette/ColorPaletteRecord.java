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
 * 08/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.util.palette;

import java.awt.Color;

/**
 * The abstract base class for color-palette entries using various color models.
 */
public abstract class ColorPaletteRecord implements Comparable<ColorPaletteRecord> {

  final double range0;
  final double range1;
  boolean termination;

  /**
   * Construct a record populating the range of values for which
   * colors are defined.
   *
   * @param range0 a valid floating point value less than or equal to range1
   * @param range1 a valid floating point value greater than or equal
   * range0.
   */
  public ColorPaletteRecord(double range0, double range1) {
    // test that values are in proper order. This logic will also
    // detect Double.NaN
    if (range0 <= range1) {
      this.range0 = range0;
      this.range1 = range1;
    } else {
      throw new IllegalArgumentException("Range of values given out-of-order");
    }
  }

  /**
   * Gets an ARGB value for the specified parameter, if available.
   * Implementations of this method generally expect that the value
   * for z will be in the range of values that was specified to their
   * constructor. The behavior of this method when supplied values out
   * of range is undefined, though in many cases classes will simply
   * constrain the input value to the supported range.
   *
   * @param z a valid floating point value
   * @return if a color is defined for z, its associated ARGB value;
   * otherwise the null-value code.
   */
  public abstract int getArgb(double z);

  /**
   * Gets a Color instance for the specified parameter, if available.
   * Implementations of this method generally expect that the value
   * for z will be in the range of values that was specified to their
   * constructor. The behavior of this method when supplied values out
   * of range is undefined, though in many cases classes will simply
   * constrain the input value to the supported range.
   *
   * @param z a valid floating point value
   * @return if a color is defined for z, its associated ARGB value;
   * otherwise the null-value code.
   */
  public abstract Color getColor(double z);

  /**
   * Indicates whether the specified value z is within the
   * range of values covered by this instance.
   *
   * @param z a valid floating-point value
   * @return true if the value is within range; otherwise, false.
   */
  public boolean isCovered(double z) {
    if (range0 <= z) {
      if (z < range1 || z == range1 && termination) {
        return true;
      }
    }
    return false;
  }

  /**
   * Indicates whether the range of values for this instance is
   * to be treated as "terminated". Normally, a value z is considered
   * covered if it falls within the interval range0 &#60;= z &#60; range1.
   * However, if the color record is the last one in a sequence, it may
   * provide a color value for z = range1. In other words, if it
   * terminates a sequence (is a terminator), it will provide a color
   * value for z.
   *
   * @return true if the record terminates a sequence of records;
   * otherwise, false.
   */
  public boolean isTerminator() {
    return termination;
  }

  @Override
  public int compareTo(ColorPaletteRecord o) {
    int test = Double.compare(range0, o.range0);
    if (test == 0) {
      test = Double.compare(range1, o.range1);
    }
    return test;
  }
}
