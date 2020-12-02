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
package org.gridfour.lsop;

/**
 * Provides utilities for reading the LsHeader from a packing.
 * <p>
 * Currently, the header definition is hard-wired for 8 predictor
 * coefficients. But this value is subject to change in the future.
 */
public class LsHeader {

  protected final int codecIndex;
  protected final int nCoefficients;
  protected final int seed;
  protected final float[] u;
  protected final int nInitializerCodes;
  protected final int nInteriorCodes;
  protected final int compressionType;
  protected final int headerSize;

  /**
   * Constructs a instance populated with parameters extracted from the
   * packing
   *
   * @param packing an array of bytes containing the encoded parameters;
   * it is assumed that the packing is at least as long as the required storage
   * @param packingOffset the starting position within the encoded packing
   */
  public LsHeader(byte[] packing, int packingOffset) {
// the header is 15+N*4 bytes:
    //   for 8 predictor coefficients:  47 bytes
    //   for 12 predictor coefficients: 63 bytes
    //    1 byte     codecIndex
    //    1 byte     number of predictor coefficients (N)
    //    4 bytes    seed
    //    N*4 bytes  predictor coefficients
    //    4 bytes    nInitializationCodes
    //    4 bytes    nInteriorCodes
    //    1 byte     method

    int offset = packingOffset;
    codecIndex = packing[offset++];
    nCoefficients = packing[offset++];
    seed = unpackInteger(packing, offset);
    offset += 4;
    u = new float[nCoefficients];
    for (int i = 0; i < nCoefficients; i++) {
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

  /**
   * Packs the metadata ("header") for a LSOP compression into an array of bytes
   *
   * @param codecIndex the index of the CODEC used for G93 file
   * @param nCoefficients the number of coefficients
   * @param seed the seed value
   * @param u the compression coefficients, should be dimensioned
   * to at least nCoefficients
   * @param nInitializationCodes the number of M32 codes in the initializer
   * @param nInteriorCodes the number of M32 codes in the interior
   * @param genericCompression which generic compression method was used
   * to compress the M32 code sequence
   * @return an array in the exact size of the packing.
   */
  public static byte[] packHeader(
    int codecIndex,
    int nCoefficients,
    int seed,
    float[] u,
    int nInitializationCodes,
    int nInteriorCodes,
    int genericCompression) {
    // the header is 15+N*4 bytes:
    //   for 8 predictor coefficients:  47 bytes
    //   for 12 predictor coefficients: 63 bytes
    //    1 byte     codecIndex
    //    1 byte     number of predictor coefficients (N)
    //    4 bytes    seed
    //    N*4 bytes  coefficients
    //    4 bytes    nInitializationCodes
    //    4 bytes    nInteriorCodes
    //    1 byte     method

    byte[] packing = new byte[15 + nCoefficients * 4];
    packing[0] = (byte) (codecIndex & 0xff);
    packing[1] = (byte) nCoefficients;
    int offset = packInteger(packing, 2, seed);
    for (int i = 0; i < nCoefficients; i++) {
      offset = packFloat(packing, offset, u[i]);
    }
    offset = packInteger(packing, offset, nInitializationCodes);
    offset = packInteger(packing, offset, nInteriorCodes);
    packing[offset++] = 1;
    return packing;
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

  private static int packInteger(byte[] output, int offset, int iValue) {
    output[offset] = (byte) (iValue & 0xff);
    output[offset + 1] = (byte) ((iValue >> 8) & 0xff);
    output[offset + 2] = (byte) ((iValue >> 16) & 0xff);
    output[offset + 3] = (byte) ((iValue >> 24) & 0xff);
    return offset + 4;
  }

  private static int packFloat(byte[] output, int offset, float f) {
    int iValue = Float.floatToRawIntBits(f);
    return packInteger(output, offset, iValue);
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
   * Gets the number of predictors coefficients defined for the encoding
   *
   * @return a positive integer (currently, 8 or 12)
   */
  public int getPredictorCoefficientCount() {
    return nCoefficients;
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
