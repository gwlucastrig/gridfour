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
package org.gridfour.imaging.palette;

import java.awt.Color;

/**
 * The abstract base class for color-palette entries using various color models.
 */
public abstract class ColorPaletteRecord implements Comparable<ColorPaletteRecord> {

  final double range0;
  final double range1;
  boolean termination;
  String label;

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

  abstract ColorPaletteRecord copyWithModifiedRange(
    double minRangeSpec, double maxRangeSpec);

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
   * Gets an ARGB value for the specified parameters, if available.
   * Implementations of this method generally expect that the value
   * for z will be in the range of values that was specified to their
   * constructor. The behavior of this method when supplied values out
   * of range is undefined, though in many cases classes will simply
   * constrain the input value to the supported range.
   * <p>
   * The shade value is intended to support applications that vary the
   * intensity of the color based on a shade value.  It value is expected
   * to be in the range 0 (dark) to 1.0 (fully illuminated).  When the
   * shade value is set to 1.0, the results from this method are identical
   * to those from the standard getArgb.   Note that the behavior of this
   * method is undefined for cases where it receives an out-of-range shade value.
   * For reasons of efficiency, some implementations may elect to not
   * add extra operations for range-checking.  Thus the onus is on the
   * calling application to always pass in valid shade values.
   *
   * @param z a valid floating point value
   * @param shade a value in the range 0 to 1
   * @return if a color is defined for z, its associated ARGB value;
   * otherwise the null-value code.
   */
  public abstract int getArgbWithShade(double z, double shade);



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
   * Gets the color for the minimum value in the range specified
   * by this record.  This method is intended primarily to support
   * categorical palettes. It can also be useful in creating
   * legends and keys in graphical presentations.
   * @return a valid Color instance.
   */
  public abstract Color getBaseColor();

    /**
   * Gets the color for the maximum value in the range specified
   * by this record.
   * @return a valid Color instance.
   */
  public abstract Color getTopColor();



  /**
   * Indicates whether the specified value z is within the
   * range of values covered by this instance.
   *
   * @param z a valid floating-point value
   * @return true if the value is within range; otherwise, false.
   */
  public boolean isCovered(double z) {
    return range0 <= z && (z < range1 || z == range1 && termination);
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

  /**
   * Sets an arbitrary label string for the record
   * @param label a valid string, or a null if not specified.
   */
  public void setLabel(String label){
    if(label==null || label.isEmpty()){
      this.label = null;
    }
    this.label = label;
  }

  /**
   * Gets a label for the record.  If the record does not have a label,
   * returns an empty string.
   * @return a valid, potentially empty instance.
   */
  public String getLabel(){
    if(label==null){
      return "";
    }
    return label;
  }

  /**
   * Get the maximum for the range of values specified by this record.
   *
   * @return a valid floating point value.
   */
  public double getRangeMax() {
    return range1;
  }

  /**
   * Get the minimum for the range of values specified by this record.
   *
   * @return a valid floating point value.
   */
  public double getRangeMin() {
    return range0;
  }
}
