/*
 * The MIT License
 *
 * Copyright 2019 gwluc.
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
 * 10/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

/**
 * Defines an interface for predictor-corrector implementations.
 */
public interface IPredictorCorrector {

  /**
   * Decodes the specified data using the predictor-corrector model to compute
   * output values from the specified input.
   *
   * @param seed a seed value to provide the initial value for the encoding
   * @param encoding the input values for the encoding, usually dimensions to
   * nRows*nColumns
   * @param nRows the number of rows in the raster data grid
   * @param nColumns the number of columns in the raster data grid
   * @param offset the starting offset in the encoding array from which
   * data is to be taken.
   * @param length the length of the valid section of the encoding array
   * from which data is to be taken.
   * @param output array to accept the output data values, dimensions to at
   * least nRows*nColumns
   */
  void decode(
          int seed,
          int nRows,
          int nColumns,
          byte[] encoding,
          int offset, 
          int length,
          int[] output);

  /**
   * Encodes the specified data using the predictor-corrector model to develop
   * computed adjustments for the data grid.
   * <p>
   * This routine returns the seed value detected in the data. This value is
   * usually the first entry in the input value. In cases where the data
   * contains nulls, the seed is sometimes the mean value of the non-null
   * entries.
   * <p>
   * The length of the encoded output will be a maximum of 5 times the
   * total number of entries in the input (it is usually much shorter than
   * that). The encoding array should be dimensioned large
   * enough to accept the maximum sized output.
   *
   * @param nRows the number of rows in the raster data grid
   * @param nColumns the number of columns in the data data grid
   * @param values the input values to be encoded
   * @param encoding the output encoding
   * @return if successful, the number of bytes in the output encoding;
   * otherwise, a value -1 to indicate an encoding failure.
   */
  int encode(
          int nRows,
          int nColumns,
          int[] values,
          byte[] encoding);

  /**
   * Indicates that the predictor-corrector is intended to handle null values.
   * 
   * @return true if null values are permitted; otherwise false.
   */
  boolean isNullDataSupported();

  /**
   * Gets the seed value determined by the most recent call to the encoding
   * method. This value is defined only if the encoding was successful.
   *
   * @return an arbitrary integer value
   */
  int getSeed();
  
  
  /**
   * Gets the predictor type.
   * @return a valid enumeration corresponding the the type of predictor
   */
    PredictorCorrectorType getPredictorType();
}
