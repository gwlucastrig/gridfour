/* --------------------------------------------------------------------
 * Copyright (C) 2020  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
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
