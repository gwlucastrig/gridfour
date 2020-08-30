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
 * 08/2020  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93.lsop.decompressor;

/**
 * Provides utilities for reading the LsHeader from a packing.
 * <p>
 * Currently, the header definition is hard-wired for 8 predictor
 * coefficients. But this value is subject to change in the future.
 */
public class LsHeader {

  protected final int codecIndex;
  protected final int nPredictor;
  protected final int seed;
  protected final float[] u;
  protected final int nInitializerCodes;
  protected final int nInteriorCodes;
  protected final int compressionType;
  protected final int headerSize;

  public LsHeader(byte[] packing, int packingOffset) {

    // currently, the preface is 47 bytes and the number of predictors
    // is expected to always be 8.  This may be expanded in the future.
    //    1 byte     codecIndex
    //    1 bye      Number of predictors (currently 8)
    //    4 bytes    seed
    //    N*4 bytes  coefficients (currently N=8)
    //    4 bytes    nInitializationCodes
    //    4 bytes    nInteriorCodes
    //    1 byte     method
    int offset = packingOffset;
    codecIndex = packing[offset++];
    nPredictor = packing[offset++];
    seed = unpackInteger(packing, offset);
    offset += 4;
    u = new float[nPredictor];
    for (int i = 0; i < nPredictor; i++) {
      u[i] = unpackFloat(packing, offset);
      offset += 4;
    }
    nInitializerCodes = unpackInteger(packing, offset);
    offset += 4;
    nInteriorCodes = unpackInteger(packing, offset);
    offset += 4;
    compressionType = packing[offset++];
    headerSize = offset - packingOffset;
  }

  private int unpackInteger(byte[] packing, int offset) {
    return (packing[offset] & 0xff)
      | ((packing[offset + 1] & 0xff) << 8)
      | ((packing[offset + 2] & 0xff) << 16)
      | ((packing[offset + 3] & 0xff) << 24);
  }

  private float unpackFloat(byte[] output, int offset) {
    int bits = unpackInteger(output, offset);
    return Float.intBitsToFloat(bits);
  }

  /**
   * Get the codec index from the packing
   *
   * @return the codecIndex
   */
  public int getCodecIndex() {
    return codecIndex;
  }

  /**
   * Gets the number of predictors defined for the encoding
   *
   * @return a positive integer (always 8 at this time,
   * subject to change in the future)
   */
  public int getPredictorCount() {
    return nPredictor;
  }

  /**
   * Get the seed value from the packing
   *
   * @return the seed
   */
  public int getSeed() {
    return seed;
  }

  /**
   * Get the optimal predictor coefficients from the packing
   *
   * @return the u
   */
  public float[] getOptimalPredictorCoefficients() {
    return u;
  }

  /**
   * Get the length of the encoded text (M32 codes) for the initializers
   *
   * @return a positive integer greater than zero, in bytes
   */
  public int getCodedInitializerLength() {
    return nInitializerCodes;
  }

  /**
   * Get the length of the encoded text (M32 codes) for the interior.
   *
   * @return a positive integer greater than zero, in bytes
   */
  public int getCodedInteriorLength() {
    return nInteriorCodes;
  }

  /**
   * Indicates whether the data was compresses using Huffman coding or
   * Deflate.
   *
   * @return a value of zero (for Huffman coding) or 1 (for Deflate).
   */
  public int getCompressionType() {
    return compressionType;
  }

  /**
   * Get the size of the header, in bytes
   *
   * @return a positive integer (47 at this time, subject to change in future)
   */
  public int getHeaderSize() {
    return headerSize;
  }
}
