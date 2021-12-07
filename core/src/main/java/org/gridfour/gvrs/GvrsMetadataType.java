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

/**
 * Defines the possible representations of data stored as an element
 * in a GVRS file.
 */
public enum GvrsMetadataType {
  /**
   * Data type is unspecified and is stored as bytes
   */
  UNSPECIFIED(0, 1),
  /**
   * Data is stored using the byte type.
   */
  BYTE(1, 1),
  
    /**
   * Data is stored using the 2-byte signed integer data type.
   */
  SHORT(2, 4),
  
      /**
   * Data is stored using the 2-byte unsigned integer data type.
   */
  UNSIGNED_SHORT(3, 4),
  
  /**
   * Data is stored using the 4-byte signed integer data type.
   */
  INTEGER(4, 4),
  
    /**
   * Data is stored using the 4-byte unsigned integer data type.
   */
  UNSIGNED_INTEGER(5, 4),
  
 
  /**
   * Data is stored using the IEEE-754 four-byte
   * single-precision floating point format.
   */
  FLOAT(6, 4),
 
    /**
   * Data is stored using the IEEE-754 eight-byte
   * double-precision floating point format.
   */
  DOUBLE (7, 8),
  
  /**
   * Data is stored as a UTF-8 formatted string
   */
  STRING(8, 1),
  
  /**
   * Data is stored as an ASCII formatted string.
   */
  ASCII(9, 1);

  final int codeValue;
  final int bytesPerValue;

  GvrsMetadataType(int codeValue, int bytesPerValue) {
    this.codeValue = codeValue;
    this.bytesPerValue = bytesPerValue;
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
   * Get the number of bytes required to store a single data value
   * of the associated type.
   *
   * @return an integer value of one or greater.
   */
  public int getBytesPerValue() {
    return bytesPerValue;
  }

  public static GvrsMetadataType valueOf(int codeValue) {
    for(GvrsMetadataType v: values()){
      if(v.codeValue==codeValue){
        return v;
      }
    }
    return null;
  }
 
  /**
   * Determines whether the specified type can be treated as
   * compatible with this instance for purposes of data storage.
   * This condition is true for unsigned and signed variations of
   * integer types.
   * @param testType the type to be considered
   * @return true if the types can be treated as compatible; otherwise, false.
   */
  public boolean isTypeCompatible(GvrsMetadataType testType){
    if(this==testType){
      return true;
    }
    switch(this){
      case INTEGER:
        return testType==UNSIGNED_INTEGER;
      case UNSIGNED_INTEGER:
        return testType == INTEGER;
      case SHORT:
        return testType == UNSIGNED_SHORT;
      case UNSIGNED_SHORT:
        return testType==SHORT;
      default:
        return false;
    }
  }

}
