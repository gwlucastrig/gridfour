/*
 * The MIT License
 *
 * Copyright 2020 G. W. Lucas.
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
 * 07/2020  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.lsop;


/**
 * Provides a simple class for holding the results from an LSOP predictor
 * analysis operation.
 */
public class LsOptimalPredictorResult {

  final int seed;
  final int nCoefficients;
  final float[] coefficients;
  final int nInitializerCodes;
  final byte[] initializerCodes;
  final int nInteriorCodes;
  final byte[] interiorCodes;

  public LsOptimalPredictorResult(
    int seed,
    int nCoefficients,
    float[] coefficients,
    int nInitializerCodes,
    byte[] initializerCodes,
    int nInteriorCodes,
    byte[] interiorCodes) {
    this.seed = seed;
    this.nCoefficients = nCoefficients;
    this.coefficients = coefficients;
    this.nInitializerCodes = nInitializerCodes;
    this.initializerCodes = initializerCodes;
    this.nInteriorCodes = nInteriorCodes;
    this.interiorCodes = interiorCodes;
  }

  /**
   * Get an array of the M32 codes computed by the encoder.
   *
   * @return a valid array of bytes giving Gridfour M32 codes.
   */
  public byte[] getInteriorCodes() {
    return interiorCodes;
  }

  /**
   * Gets the number of unique symbols that comprise the interior
   * portion of the encoded data.
   *
   * @return a positive value greater than zero.
   */
  public int getInteriorCodeCount() {
    return this.nInteriorCodes;
  }


  /**
   * Gets the initial "seed" value for the encoding sequence.
   * The seed a literal copy of initial value from the original raster.
   * @return the initial value in the original raster data.
   */
  public int getSeed() {
    return seed;
  }

  /**
   * Gets the number of coefficients stored for the predictor that produced
   * these results.
   * @return a positive integer.
   */
  public int getCoefficientCount(){
    return nCoefficients;
  }
  /**
   * Get the computed coefficients for the optimal predictors.
   * Note: the length of the coefficient array may exceed the coefficient
   * count.
   * @return an array of a length as large or larger than the number of
   * coefficients produced by the predictor.
   */
  public float[] getCoefficients() {
    return coefficients;
  }

  /**
   * Gets the number of unique symbols that comprise the initialization
   * portion of the encoded data.
   *
   * @return a positive value greater than zero.
   */
  public int getInitializerCodeCount() {
    return nInitializerCodes;
  }

  /**
   * Get the sequence of symbols for the initialization sequence.
   * @return a valid array of bytes giving Gridfour M32 codes.
   */
  public byte[] getInitializerCodes() {
    return initializerCodes;
  }

}
