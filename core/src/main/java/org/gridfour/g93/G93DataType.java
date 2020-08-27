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
package org.gridfour.g93;

/**
 * Defines the possible representations of data stored in a simple-raster file.
 */
public enum G93DataType {

  /**
   * Data is stored using the Java 4-byte integer data type.
   */
  INTEGER(0, 4),
  /**
   * Floating point values are multiplied by a scaling factor and
   * stored as a Java 4-byte integer data type.
   */
  INTEGER_CODED_FLOAT(1, 4),
  /**
   * Data is stored using the Java 4-byte float data type, the IEEE-754
   * single-precision floating point format.
   */
  FLOAT(2, 4);

  final int codeValue;
  final int bytesPerSample;

  G93DataType(int codeValue, int bytesPerSample) {
    this.codeValue = codeValue;
    this.bytesPerSample = bytesPerSample;
  }

  /**
   * Gets the code value to be stored in a data file to indicate what
   * data type was used for the non-compressed storage representation.
   *
   * @return gets an integer code value indicating the data type; used
   * internally.
   */
  public int getCodeValue() {
    return codeValue;
  }

  /**
   * Get the number of bytes required to store a single data sample
   * of the associated type.
   *
   * @return an integer value of one or greater.
   */
  public int getBytesPerSample() {
    return bytesPerSample;
  }

  public static G93DataType valueOf(int codeValue) {
    switch (codeValue) {
      case 0:
        return INTEGER;
      case 1:
        return INTEGER_CODED_FLOAT;
      case 2:
        return FLOAT;
      default:
        return INTEGER;
    }
  }

  /**
   * Indicates whether this enumeration identifies an integer-based datatype
   * for G93 data.
   *
   * @return true if the identified data type is integral; otherwise, false.
   */
  public boolean isIntegral() {
    switch (this) {
      case INTEGER:
      case INTEGER_CODED_FLOAT:
        return true;
      default:
        return false;
    }
  }

}
