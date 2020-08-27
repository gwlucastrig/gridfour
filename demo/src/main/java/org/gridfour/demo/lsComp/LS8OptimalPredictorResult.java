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
package org.gridfour.demo.lsComp;

/**
 *
 */
class LS8OptimalPredictorResult {

  int seed;
  float[] coefficients;
  int nInitializerCodes;
  byte[] initializerCodes;
  int nInteriorCodes;
  byte[] interiorCodes;

  LS8OptimalPredictorResult(
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
}
