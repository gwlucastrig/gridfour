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
 * 08/2020  G. Lucas     Created
 *
 * About the optional checksum:
 *   While working on Gridfour release 1.0.3, we identified a requirement
 * to be able to include a checksum for the original value set that could
 * be stored as part of a compressed sequence.  This feature would be useful
 * when creating Gridfour libraries in other languages because it would
 * aid developers in assessing the correctness of their decompression
 * sequence.  This is important because the LSOP algorithm depends on the ability
 * of a non-Java program to match exactly the floating-point calculations performed
 * by the Java compressor (or vice versa). Having a checksum gives a way
 * to see it the output of a decompressor matches the original input even
 * when the input data is not immediately available.
 *    Because we did not want to break compatibility with existing files
 * we specified this checksum as optional.  There were unused bits in the
 * compression-type indicator in the header, so we appropriated the high-order
 * bit to determine whether a checksum is supplied.
 * -----------------------------------------------------------------------
 */
package org.gridfour.lsop;

import org.gridfour.util.GridfourCRC32C;

/**
 * Provides utilities for reading the LsHeader from a packing.
 * <p>
 * Currently, the header definition is hard-wired for 8 predictor
 * coefficients. But this value is subject to change in the future.
 */
public class LsHeader {

  /**
   * A code value indicating that the post-prediction code sequence was
   * compressed using Huffman coding in the Gridfour format
   */
  public final static int COMPRESSION_TYPE_HUFFMAN = 0;
  /**
   * A code value indicating that the post-prediction code sequence was compressed
   * using the Deflate library.
   */
  public final static int COMPRESSION_TYPE_DEFLATE = 1;
  /**
   * A mask for extracting the compression type from a packing.
   */
  public final static int COMPRESSION_TYPE_MASK = 0x0f;
  /*
   * A bit flag indicating that the packing includes a checksum.
  */
  public final static int VALUE_CHECKSUM_INCLUDED = 0x80;

  protected final int codecIndex;
  protected final int nCoefficients;
  protected final int seed;
  protected final float[] u;
  protected final int nInitializerCodes;
  protected final int nInteriorCodes;
  protected final int compressionType;
  protected final int headerSize;
  protected boolean valueChecksumIncluded;
  protected int valueChecksum;

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
    compressionType = packing[offset]&COMPRESSION_TYPE_MASK;
    valueChecksumIncluded = (packing[offset]&VALUE_CHECKSUM_INCLUDED)!=0;
    offset++;
    if(valueChecksumIncluded){
      valueChecksum = unpackInteger(packing, offset);
      offset += 4;
    }
    headerSize = offset - packingOffset;
  }

  /**
   * Packs the metadata ("header") for a LSOP compression into an array of bytes
   *
   * @param codecIndex the index of the CODEC as defined by the calling application.
   * @param nCoefficients the number of coefficients
   * @param seed the seed value
   * @param u the compression coefficients, should be dimensioned
   * to at least nCoefficients
   * @param nInitializationCodes the number of M32 codes in the initializer
   * @param nInteriorCodes the number of M32 codes in the interior
   * @param compressionTypeCode indicates which standard compression method
   * (Huffman or Deflate) was used to compress the M32 code sequence
   * @param valueChecksumIncluded indicates that the header includes a
   * CRC32C checksum computed from the source data.
   * @param valueChecksum a checksum, if included
   * @return an array in the exact size of the packing.
   */
  public static byte[] packHeader(
    int codecIndex,
    int nCoefficients,
    int seed,
    float[] u,
    int nInitializationCodes,
    int nInteriorCodes,
    int compressionTypeCode,
    boolean valueChecksumIncluded,
    int valueChecksum) {
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
    int packsize = 15+nCoefficients*4 + (valueChecksumIncluded? 4:0);
    byte[] packing = new byte[packsize];
    packing[0] = (byte) (codecIndex & 0xff);
    packing[1] = (byte) nCoefficients;
    int offset = packInteger(packing, 2, seed);
    for (int i = 0; i < nCoefficients; i++) {
      offset = packFloat(packing, offset, u[i]);
    }
    offset = packInteger(packing, offset, nInitializationCodes);
    offset = packInteger(packing, offset, nInteriorCodes);
    int typeCode = compressionTypeCode;
    if(valueChecksumIncluded){
      typeCode|=VALUE_CHECKSUM_INCLUDED;
    }
    packing[offset++] = (byte)typeCode;
    if(valueChecksumIncluded){
      offset = packInteger(packing, offset, valueChecksum);
    }
    return packing;
  }


  public static byte[] packHeader(
    int codecIndex,
     LsOptimalPredictorResult result,
     int compressionTypeCode){

    return LsHeader.packHeader(codecIndex,
      result.nCoefficients,
      result.seed,
      result.coefficients,
      result.nInitializerCodes,
      result.nInteriorCodes,
      compressionTypeCode,
      false,
      0);
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

  /**
   * Computes the checksums for a set of values to be stored using the
   * LSOP compressor format.
   * @param nRows the number of rows in the raster
   * @param nColumns the number of columns in the raster
   * @param values an array giving the values in the raster
   * @return a computed CRC32C checksum value.
   */
   public static int computeChecksum(int nRows, int nColumns, int[] values) {
    int n = nRows * nColumns;
    byte[] b = new byte[n * 4];
    int k = 0;
    for (int i = 0; i < n; i++) {
      int v = values[i];
      b[k++] = (byte) (v & 0xff);
      b[k++] = (byte) ((v >> 8) & 0xff);
      b[k++] = (byte) ((v >> 16) & 0xff);
      b[k++] = (byte) ((v >> 24) & 0xff);
    }

    GridfourCRC32C crc32 = new GridfourCRC32C();
    crc32.update(b);
    return (int) crc32.getValue();
  }

   /**
    * Indicates whether the value checksum was calculated and stored as
    * part of the header. The value checksum is a diagnostic intended to
    * assist in ensuring correct floating-point calculations when porting
    * Gridfour to other languages and development environments.
    * @return true if the checksum is included; otherwise, zero.
    */
   public boolean isValueChecksumIncluded(){
     return valueChecksumIncluded;
   }

   /**
    * Gets the value checksum stored in this header (if enabled).
    * The value checksum is a CRC32C checksum computed when the data
    * is stored. It is intended to support verification of floating-point
    * calculations when porting Gridfour to other languages and
    * development environments. It can also be used when archiving data
    * as a second level of checksum support. Because it adds overhead,
    * it is often omitted from compressed files.
    * @return if enabled, a valid CRC32C checksum; otherwise, a zero
    */
   public int getValueChecksum(){
     return valueChecksum;
   }

}
