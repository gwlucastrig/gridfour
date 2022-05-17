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
 * Provides a concrete definition of a GvrsElementSpecification that defines
 * an integer-based encoding for floating point values. In cases where
 * input data does not require the full precision supported by floating-point
 * data types, it can be useful to store their values as integer values.
 * Data compression techniques for integer based values are often more
 * successful than data compression techniques for floating point values.
 * <p>
 * For example, consider a data set that gives floating-point values in
 * meters, but is only accurate to the nearest centimeter. By multiplying the
 * input data by a factor of 100, it is possible to store it in an integer
 * format without a loss of significant precision. Additionally, if
 * an approximate mean value for the input set is known, it is possible
 * to reduce the magnitude of the integer terms by treating the mean
 * value as an "offset".
 * <p>
 * Thus Gridfour treats integer encoding for a floating point value as:
 * <pre>
 *     encodedInteger = (int)((floatingPointValue - offset) * scale + 0.5)
 *     floatingPointValue = encodedInteger/scale + offset
 * </pre>
 * <p>
 * Clearly, when storing data as an integer-coded float
 * the scale and offset must be selected so that the resulting
 * integral values must be within the range of a standard Java 4-byte
 * integer. Gridfour applies an additional restriction that the
 * computed integral values must be within the range:
 * <pre>
 *     [Integer.MIN_VALUE+1,   Integer.MAX_VALUE-1]
 * </pre>
 * The +1 and -1 adjustments are chosen to ensure that values are not
 * computed incorrectly due to round off.
 * <p>
 * <strong>Special considerations</strong>
 * <p>
 * The key to using the integer-coded float specification is to realize that,
 * at its core, it is an integer data format. This the range and fill-value
 * specifications in the constructors are given as integers.
 * <p>
 * Special logic is applied for mapping the fill-value to a Float.NaN
 * and vice versa.
 *
 */
public class GvrsElementSpecificationIntCodedFloat extends GvrsElementSpecification {

  final float scale;
  final float offset;
  final int minValueI;
  final int maxValueI;
  final int fillValueI;
  final float minValue;
  final float maxValue;
  final float fillValue;

  /**
   * Constructs a specification instance giving parameters for a
   * the integer encoding of floating point values.Floating point
   * values are computed based on the following:
   * <pre>
   *    encodedInteger = (int)((floatingPointValue - offset) * scale + 0.5)
   * </pre>
   * The limits for the input range are computed based on the
   * largest-magnitude values that can be represented by the
   * integer encoding. Fill values are treated as being Float.NaN.
   *
   * @param name a valid, non-blank identifier for the intended element.
   * @param scale a non-zero value used to scale floating point values for
   * encoding as integers
   * @param offset a valid for adjusting an input value.
   */
  public GvrsElementSpecificationIntCodedFloat(String name, float scale, float offset) {
    super(name, GvrsElementType.INT_CODED_FLOAT);
    this.scale = scale;
    this.offset = offset;
    this.minValueI = Integer.MIN_VALUE + 1;
    this.maxValueI = Integer.MAX_VALUE - 1;
    this.minValue = minValueI / scale + offset;
    this.maxValue = maxValueI / scale + offset;
    this.fillValue = Float.NaN;
    this.fillValueI = Integer.MIN_VALUE;
  }

  /**
   * Constructs a specification instance giving parameters for a
   * the integer encoding of floating point values. Floating point
   * values are computed based on the following:
   * <pre>
   *    encodedInteger = (int)((floatingPointValue - offset) * scale + 0.5)
   * </pre>
   * The limits for the input range are computed based on the
   * largest-magnitude values that can be represented by the
   * integer encodings.
   *
   * @param name a valid, non-blank identifier for the intended element.
   * @param scale a non-zero value used to scale floating point values for
   * encoding as integers.
   * @param offset a valid for adjusting an input value.
   * @param fillValueI the value assigned to unpopulated raster cells,
   * does not necessarily have to be with the range of the minimum and
   * maximum values.
   *
   */
  public GvrsElementSpecificationIntCodedFloat(
    String name, float scale, float offset, int fillValueI) {
    super(name, GvrsElementType.INT_CODED_FLOAT);
    this.scale = scale;
    this.offset = offset;
    this.minValueI = Integer.MIN_VALUE + 1;
    this.maxValueI = Integer.MAX_VALUE - 1;
    this.minValue = minValueI / scale + offset;
    this.maxValue = maxValueI / scale + offset;
    this.fillValueI = fillValueI;
    this.fillValue = fillValueI / scale + offset;
  }

  //No longer used but retained for future reference
  //private int testValue(String parameter, float v) {
  //  if (Float.isNaN(v)) {
  //    return Integer.MIN_VALUE;
  //  }
  //  if (!Float.isFinite(v)) {
  //    throw new IllegalArgumentException("Specified " + parameter + " " + v
  //      + " is not finite and not supported");
  //  }
  //  float test = (v - offset) * scale;
  //  if (test < Integer.MIN_VALUE + 1 || test > Integer.MAX_VALUE - 1) {
  //    throw new IllegalArgumentException("Specified " + parameter + " " + v
  //      + " is out-of-range when scaled to an integer value");
  //  }
  //  return (int) Math.floor(test + 0.5);
  //}
  /**
   * Constructs a specification instance giving parameters for a
   * the integer encoding of floating point valuesFloating point
   * values are computed based on the following:
   * <pre>
   *    encodedInteger = (int)Math.floor((floatingPointValue - offset) * scale + 0.5)
   * </pre>
   * The limits for the input range are computed based on the
   * largest-magnitude values that can be represented by the
   * integer encoding.
   * <p>
   * This boolean fillValueIsNull parameters supplied to this constructor
   * allows an application to specify that the integer
   * fill value be treated as a Float.NaN when converted.
   *
   * @param name a valid, non-blank identifier for the intended element
   * @param scale a non-zero value used to scale floating point values for
   * encoding as integers
   * @param offset a valid for adjusting an input value.
   * @param minValueI the minimum value allowed for input
   * @param maxValueI the maximum value allowed for input
   * @param fillValueI the value assigned to unpopulated raster cells,
   * does not necessarily have to be with the range of the minimum and
   * maximum values.
   * @param fillValueIsNull indicates that the floating-point equivalent
   * for the fill value is to be treated as Float&#46;NaN.
   *
   */
  public GvrsElementSpecificationIntCodedFloat(
    String name, float scale, float offset,
    int minValueI, int maxValueI, int fillValueI,
    boolean fillValueIsNull) {
    super(name, GvrsElementType.INT_CODED_FLOAT);
    this.scale = scale;
    this.offset = offset;
    this.minValueI = minValueI;
    this.maxValueI = maxValueI;
    this.fillValueI = fillValueI;

    this.minValue = minValueI / scale + offset;
    this.maxValue = maxValueI / scale + offset;
    if (fillValueIsNull) {
      if (minValueI <= fillValueI && fillValueI <= maxValueI) {
        throw new IllegalArgumentException(
          "Fill value must not be in range of normal values when it is treated as null");
      }
      this.fillValue = Float.NaN;
    } else {
      this.fillValue = fillValueI / scale + offset;
    }
  }

  GvrsElementSpecificationIntCodedFloat(
    String name,
    float scale,
    float offset,
    int minValueI,
    int maxValueI,
    int fillValueI,
    float minValue,
    float maxValue,
    float fillValue
  ) {
    super(name, GvrsElementType.INT_CODED_FLOAT);
    this.scale = scale;
    this.offset = offset;
    this.minValueI = minValueI;
    this.maxValueI = maxValueI;
    this.fillValueI = fillValueI;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.fillValue = fillValue;
  }

  @Override
  GvrsElementSpecification copy() {
    GvrsElementSpecificationIntCodedFloat spec
      = new GvrsElementSpecificationIntCodedFloat(
        name, scale, offset,
        minValueI, maxValueI, fillValueI,
        minValue, maxValue, fillValue);
    spec.copyApplicationData(this);
    return spec;
  }

  /**
   * Maps the specified floating-point value to its encoded integer equivalent.
   *
   * @param value a value in the range specified by this instance
   * @return an encoded integer value.
   */
  public int mapFloatToInt(float value) {
    if (Float.isNaN(value) || value == fillValue) {
      return this.fillValueI;
    }
    if (value < minValue || value > maxValue) {
      throw new IllegalArgumentException(
        "Specified value is out of range [" + minValue + ", " + maxValue + "]");
    }
    return (int) Math.floor((value - offset) * scale + 0.5);
  }

  /**
   * Maps the specified integer value to its decoded floating-point equivalent.
   *
   * @param i a value in the range specified by this instance
   * @return a decloded floating point value
   */
  public float mapIntToFloat(int i) {
    if (i == this.fillValueI) {
      return this.fillValue;
    }
    if (i < minValueI || i > maxValueI) {
      throw new IllegalArgumentException(
        "Specified value is out of range [" + minValueI + ", " + maxValueI + "]");
    }
    return i / scale + offset;
  }

  /**
   * Get the minimum integral value supported by this instance
   *
   * @return an integer value
   */
  public int getMinValueInt() {
    return minValueI;
  }

  /**
   * Get the maximum integral value supported by this instance
   *
   * @return an integer value
   */
  public int getMaxValueInt() {
    return minValueI;
  }

  /**
   * Get the minimum floating-point value supported by this instance
   *
   * @return an integer value
   */
  public float getMinValue() {
    return minValue;
  }

  /**
   * Get the maximum floating-point value supported by this instance
   *
   * @return an integer value
   */
  public float getMaxValue() {
    return maxValue;
  }

  @Override
  GvrsElement makeElement(GvrsFile file) {
    return new GvrsElementIntCodedFloat(this, file);
  }

  @Override
  public String toString() {
    return String.format(
      "GVRS Element Specification: integer-coded float, range [%f,%f], fill %f, scale %f, offset %f",
      minValue, maxValue, fillValue, scale, offset);
  }
}
