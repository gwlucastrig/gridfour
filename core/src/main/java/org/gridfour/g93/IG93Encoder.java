/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.

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
 * 11/2019  G. Lucas     Created to unify codec usage.
 * 12/2019  G. Lucas     Added analysis logic
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

/**
 * An interface defining a coder-decoder (codec) for use by a
 * G93File.
 */
public interface IG93Encoder {

  /**
   * Encodes the specified tile data in a compressed form.
   *
   * @param codecIndex the index assigned by the application to
   * associate a codec with an entry in the raster file.
   * @param nRows a value of 1 or greater giving the number of rows in the tile
   * @param nCols a value of 1 or greater giving the number of columns in the
   * tile
   * @param values the values of the tile in row-major order
   * @return if successful, an array of bytes of length greater than 1;
   * if unsuccessful, a null.
   */
  byte[] encode(int codecIndex, int nRows, int nCols, int[] values);


   /**
   * Encodes the specified tile data in a compressed form.
   *
   * @param codecIndex the index assigned by the application to
   * associate a codec with an entry in the raster file.
   * @param nRows a value of 1 or greater giving the number of rows in the tile
   * @param nCols a value of 1 or greater giving the number of columns in the
   * tile
   * @param values the values of the tile in row-major order
   * @return if successful, an array of bytes of length greater than 1;
   * if unsuccessful, a null.
   */
  byte[] encodeFloats(int codecIndex, int nRows, int nCols, float[] values);

  /**
   * Indicates whether the implementation can encode floating-point values
     *
     * @return true if direct encoding of floats is supported, otherwise false.
   */
  boolean implementsFloatingPointEncoding();


  /**
   * Indicates whether the implementation can encode integral data types
     *
     * @return true if the encoding of integral data types is supported,
   * otherwise false.
   */
  boolean implementsIntegerEncoding();
}
