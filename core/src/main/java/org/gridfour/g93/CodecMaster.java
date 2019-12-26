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

 private final  List<IG93CompressorCodec> codecs = new ArrayList<>();
 
 
 
  CodecMaster() {
    codecs.add(new CodecHuffman());
    codecs.add(new CodecDeflate());
  }
  
  void setCodecs(List<G93SpecificationForCodec>csList) throws IOException {
    codecs.clear();
    for(G93SpecificationForCodec csSpec: csList){
      Class<?> c = csSpec.getCodec();
      try {
        Constructor<?> constructor = c.getConstructor();
        Object obj = constructor.newInstance();
        codecs.add((IG93CompressorCodec)obj);
      } catch (NoSuchMethodException ex) {
        throw new IOException("Missing no-argument constructor for codec "+csSpec.getIdentification());
      } catch (SecurityException ex) {
          throw new IOException("Security exception for codec "+csSpec.getIdentification()+", "+ex.getMessage(), ex);
      } catch (InstantiationException|IllegalAccessException|IllegalArgumentException |InvocationTargetException ex) {
         throw new IOException("Failed to construct codec "+csSpec.getIdentification()+", "+ex.getMessage(), ex);
      }  
      
    }
  }

  byte [] encode(int nRows, int nCols, int [] values){   
    byte []result = null;
    int resultLength = Integer.MAX_VALUE;
    int k=0;
    for(IG93CompressorCodec codec: codecs){
      byte [] test = codec.encode(k, nRows, nCols, values);
      if(test!=null){
        if(test.length<resultLength){
          result = test;
          resultLength = test.length;
        }
      }
      k++;
    }
    return result;
  }
 
  int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
    int index = packing[0]&0xff;
    if(index>codecs.size()){
      throw new IOException("Invalid compression-type code "+index);
    }
    IG93CompressorCodec codec = codecs.get(index);
    return codec.decode(nRows, nColumns, packing);
  }

   void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
    int index = packing[0]&0xff;
    if(index>codecs.size()){
      throw new IOException("Invalid compression-type code "+index);
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
  
}
