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

import java.io.IOException;
import java.io.PrintStream;

/**
 * An interface defining a coder-decoder (codec) for use by a 
 * G93File.
 */
public interface IG93CompressorCodec {

  
  /**
   * Decodes the content of the packing and populates an
   * integer array to store the data.
   * @param nRows a value of 1 or greater giving the number of rows in the tile
   * @param nColumns a value of 1 or greater giving the number of columns in the
   * @param packing an array of bytes containing the encoded data to be decompressed
   * @return if successful, a valid integer array giving content for the
   * tile in row-major order
   * @throws IOException in the event of an incompatible packing
   */
  int[] decode(int nRows, int nColumns, byte[] packing) throws IOException;

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
   * Analyzes the content of the packing to collect statistics on the
   * compression.
   * <p>
   * This method is intended to be used when assessing the results of
   * data compression.  It may be used for a single tile or for a collection
   * of multiple tiles. When used for multiple tiles, the results are
   * assumed to be cumulative. Thus an implementation is expected to
   * maintain statistics as state data (class member data).
   * @param nRows the number of rows in the tile to be analyzed.
   * @param nColumns the number of columns in the tile to be analyzed.
   * @param packing a valid packing for the associated codec implementation
   * @throws IOException in the event of an incompatible packing
   */
  void analyze(int nRows, int nColumns, byte[] packing) throws IOException;
  
  /**
   * Prints analysis results (if any) to specified print stream
   * @param ps a valid print stream.
   * @param nTilesInRaster the number of tiles in the raster
   */
  void reportAnalysisData(PrintStream ps, int nTilesInRaster);
  
  /**
   * Clears all accumulated analysis data.
   */
  void clearAnalysisData();
  
  
  
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
   * Decodes the content of the packing and populates an
   * integer array to store the data.
   * @param nRows a value of 1 or greater giving the number of rows in the tile
   * @param nColumns a value of 1 or greater giving the number of columns in the
   * @param packing an array of bytes containing the encoded data to be decompressed
   * @return if successful, a valid integer array giving content for the
   * tile in row-major order
   * @throws IOException in the event of an incompatible packing
   */
  float[] decodeFloats(int nRows, int nColumns, byte[] packing) throws IOException;
  
  
  /**
   * Indicates whether the codec can directly encode floating-point values
   * @return true if direct encoding of floats is supported, otherwise false.
   */
  boolean implementsFloatEncoding();
  
    
  /**
   * Indicates whether the codec can encode integral data types
   * @return true if the encoding of integral data types is supported, 
   * otherwise false.
   */
  boolean implementsIntegerEncoding();
}
