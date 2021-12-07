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

import java.util.Arrays;

/**
 * Provides a simple class for holding the results from an LSOP predictor
 * analysis operation.
 */
public class LsOptimalPredictorResult {

  final int seed;
  final float[] coefficients;
  final int nInitializerCodes;
  final byte[] initializerCodes;
  final int nInteriorCodes;
  final byte[] interiorCodes;

  public LsOptimalPredictorResult(
    int seed,
    float[] coefficients,
    int nInitializerCodes,
    byte[] initializerCodes,
    int nInteriorCodes,
    byte[] interiorCodes
  ) {
    this.seed = seed;
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
    return Arrays.copyOf(interiorCodes, interiorCodes.length);
  }

  /**
   * Gets the number of unique symbols that comprise the interior
   * portion of the encoded data.
   *
   * @return a positive value greater than zero.
   */
  public int getInteriorCodeCount() {
    return nInteriorCodes;
  }
}
