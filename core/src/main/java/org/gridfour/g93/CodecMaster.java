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
 * 10/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs coding and decoding of g93 data
 */
class CodecMaster {

  int seed;

  private final List<IG93CompressorCodec> codecs = new ArrayList<>();
  
  private boolean implementsFloats;

  CodecMaster() {
    codecs.add(new CodecHuffman());
    codecs.add(new CodecDeflate());
    implementsFloats = false;
  }

  void setCodecs(List<G93SpecificationForCodec> csList) throws IOException {
    codecs.clear();
    implementsFloats = false;
    for (G93SpecificationForCodec csSpec : csList) {
      Class<?> c = csSpec.getCodec();
      try {
        Constructor<?> constructor = c.getConstructor();
        Object obj = constructor.newInstance();
        IG93CompressorCodec q = (IG93CompressorCodec)obj;
        codecs.add(q);
        if(q.implementsFloatEncoding()){
          implementsFloats = true;
        }
      } catch (NoSuchMethodException ex) {
        throw new IOException("Missing no-argument constructor for codec " + csSpec.getIdentification());
      } catch (SecurityException ex) {
        throw new IOException("Security exception for codec " + csSpec.getIdentification() + ", " + ex.getMessage(), ex);
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        throw new IOException("Failed to construct codec " + csSpec.getIdentification() + ", " + ex.getMessage(), ex);
      }

    }
  }

  byte[] encode(int nRows, int nCols, int[] values) {
    byte[] result = null;
    int resultLength = Integer.MAX_VALUE;
    int k = 0;
    for (IG93CompressorCodec codec : codecs) {
      byte[] test = codec.encode(k, nRows, nCols, values);
      if (test != null && test.length < resultLength) {
        result = test;
        resultLength = test.length;
      }
      k++;
    }
    return result;
  }

  int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
    int index = packing[0] & 0xff;
    if (index > codecs.size()) {
      throw new IOException("Invalid compression-type code " + index);
    }
    IG93CompressorCodec codec = codecs.get(index);
    return codec.decode(nRows, nColumns, packing);
  }

  void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
    int index = packing[0] & 0xff;
    if (index > codecs.size()) {
      throw new IOException("Invalid compression-type code " + index);
    }
    IG93CompressorCodec codec = codecs.get(index);
    codec.analyze(nRows, nColumns, packing);
  }

  void reportAndClearAnalysisData(PrintStream ps, int nTilesInRaster) {
    for (IG93CompressorCodec codec : codecs) {
      codec.reportAnalysisData(ps, nTilesInRaster);
      codec.clearAnalysisData();
    }
  }
  
  
  
  /**
   * Encodes the specified tile data in a compressed form.
   *
   * @param nRows a value of 1 or greater giving the number of rows in the tile
   * @param nCols a value of 1 or greater giving the number of columns in the
   * tile
   * @param values the values of the tile in row-major order
   * @return if successful, an array of bytes of length greater than 1; if
   * unsuccessful, a null.
   */
  byte[] encodeFloats(int nRows, int nCols, float[] values) {
    byte[] result = null;
    int resultLength = Integer.MAX_VALUE;
    int k = 0;
    for (IG93CompressorCodec codec : codecs) {
      if (codec.implementsFloatEncoding()) {
        byte[] test = codec.encodeFloats(k, nRows, nCols, values);
        if (test != null && test.length < resultLength) {
          result = test;
          resultLength = test.length;
        }
      }
      k++;
    }
    return result;
  }

    
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
  float[] decodeFloats(int nRows, int nColumns, byte[] packing) throws IOException {
    int index = packing[0] & 0xff;
    if (index > codecs.size()) {
      throw new IOException("Invalid compression-type code " + index);
    }
    IG93CompressorCodec codec = codecs.get(index);
    return codec.decodeFloats(nRows, nColumns, packing);
  }
  
  
  /**
   * Indicates whether at least one of the codecs registerd with this instance
   * supports direct encoding of floating point formats
   * @return true is direct encoding of floats is supported, otherwise false.
   */
  boolean implementsFloatEncoding(){
    return implementsFloats;
  }
  

}
