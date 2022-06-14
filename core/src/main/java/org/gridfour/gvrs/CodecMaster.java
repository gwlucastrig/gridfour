/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.
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
package org.gridfour.gvrs;

import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.gridfour.util.concurrent.TaskGroupExecutor;

/**
 * Performs coding and decoding of gvrs data
 */
class CodecMaster {

  private static class CompressorResults {

    byte[] results = null;

    void registerResults(byte[] results) {
      if (results == null) {
        // The compressor was unsuccessful
        return;
      }

      synchronized (this) {
        if (this.results == null) {
          this.results = results;
        } else if (results.length < this.results.length) {
          this.results = results;
        }
      }
    }
  }

    private static class CompressorRunnable implements Runnable {
      final CompressorResults compressorResults;
      final ICompressionEncoder compressor;
      final int codecIndex;
      final int nRows;
      final int nCols;
      final int []values;
      CompressorRunnable(
        CompressorResults compressorResults, ICompressionEncoder compressor, int codecIndex, int nRows, int nCols, int[]values){
         this.compressorResults = compressorResults;
        this.compressor = compressor;
        this.codecIndex = codecIndex;
        this.nRows = nRows;
        this.nCols = nCols;
        this.values = values;
      }

      @Override
      public void run(){
        byte []results = compressor.encode(codecIndex, nRows, nCols, values);
        compressorResults.registerResults(results);
      }
    }

    List<CodecHolder> codecList = new ArrayList<>();
    private boolean implementsFloats;

    private TaskGroupExecutor tgExecutor;
    private boolean multiThreadingEnabled;


    CodecMaster(List<CodecHolder> rasterCodecList) {
        codecList = new ArrayList<>();
        codecList.addAll(rasterCodecList);
        implementsFloats = false;
        for (CodecHolder spec : codecList) {
            if (spec.implementsFloatingPointEncoding()) {
                implementsFloats = true;
                break;
            }
        }
    }

    void setMultiThreadingEnabled(boolean multiThreadingEnabled){
      this.multiThreadingEnabled = multiThreadingEnabled;
    }

    void setCodecs(List<CodecHolder> csList) throws IOException {
        codecList = new ArrayList<>();
        codecList.addAll(csList);
        implementsFloats = false;
        for (CodecHolder spec : codecList) {
            if (spec.implementsFloatingPointEncoding()) {
                implementsFloats = true;
                break;
            }
        }

    }

    byte[] encode(int nRows, int nCols, int[] values) {
        if(multiThreadingEnabled){
           return encodeMultiThread(nRows, nCols, values);
        }else{
          return encodeSingleThread(nRows, nCols, values);
        }
    }

    byte[] encodeSingleThread(int nRows, int nCols, int[] values) {
        byte[] result = null;
        int resultLength = Integer.MAX_VALUE;
        int k = 0;
        for (CodecHolder codec : codecList) {
            if (codec.implementsIntegerEncoding()) {
                ICompressionEncoder compressor = codec.getEncoderInstance();
                byte[] test = compressor.encode(k, nRows, nCols, values);
                if (test != null && test.length < resultLength) {
                    result = test;
                    resultLength = test.length;
                }
            }
            k++;
        }
        return result;
    }


    byte[] encodeMultiThread(int nRows, int nCols, int[] values) {
      if(tgExecutor == null){
        tgExecutor   = new TaskGroupExecutor(3);
      }
        CompressorResults compressorResults = new CompressorResults();
        tgExecutor.groupClear();
        int k = 0;
        for (CodecHolder codec : codecList) {
            if (codec.implementsIntegerEncoding()) {
                ICompressionEncoder compressor = codec.getEncoderInstance();
                CompressorRunnable r = new CompressorRunnable(
                  compressorResults, compressor, k, nRows, nCols,values );
                tgExecutor.groupExecute(r);
            }
            k++;
        }
        tgExecutor.groupWaitUntilDone();
        return compressorResults.results;
    }

    int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
        int index = packing[0] & 0xff;
        if (index >= codecList.size()) {
            throw new IOException("Invalid compression-type code " + index);
        }
        CodecHolder codec = codecList.get(index);
        ICompressionDecoder decompressor = codec.getDecoderInstance();
        return decompressor.decode(nRows, nColumns, packing);
    }

    void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
        int index = packing[0] & 0xff;
        if (index >= codecList.size()) {
            throw new IOException("Invalid compression-type code " + index);
        }
        CodecHolder codec = codecList.get(index);
        ICompressionDecoder decompressor = codec.getDecoderInstance();
        decompressor.analyze(nRows, nColumns, packing);
    }

    void reportAndClearAnalysisData(PrintStream ps, int nTilesInRaster) {
        for (CodecHolder codec : codecList) {
            ps.println("");
            ICompressionDecoder decompressor = codec.getDecoderInstance();
            decompressor.reportAnalysisData(ps, nTilesInRaster);
            decompressor.clearAnalysisData();
        }
    }

    /**
     * Encodes the specified tile data in a compressed form.
     *
     * @param nRows a value of 1 or greater giving the number of rows in the
     * tile
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
        for (CodecHolder codec : codecList) {
            if (codec.implementsFloatingPointEncoding()) {
                ICompressionEncoder encoder = codec.getEncoderInstance();
                byte[] test = encoder.encodeFloats(k, nRows, nCols, values);
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
     *
     * @param nRows a value of 1 or greater giving the number of rows in the
     * tile
     * @param nColumns a value of 1 or greater giving the number of columns in
     * the
     * @param packing an array of bytes containing the encoded data to be
     * decompressed
     * @return if successful, a valid integer array giving content for the
     * tile in row-major order
     * @throws IOException in the event of an incompatible packing
     */
    float[] decodeFloats(int nRows, int nColumns, byte[] packing) throws IOException {
        int index = packing[0] & 0xff;
        if (index >= codecList.size()) {
            throw new IOException("Invalid compression-type code " + index);
        }
        CodecHolder codec = codecList.get(index);
        ICompressionDecoder decompressor = codec.getDecoderInstance();
        return decompressor.decodeFloats(nRows, nColumns, packing);
    }

    /**
     * Indicates whether at least one of the codecs registerd with this instance
     * supports direct encoding of floating point formats
     *
     * @return true is direct encoding of floats is supported, otherwise false.
     */
    boolean implementsFloatEncoding() {
        return implementsFloats;
    }


  /**
   * Gets an instance of the compression encoder that matches the
   * specified CODEC name, if any.
   * @param name a valid, non-empty string.
   * @return if matched, a valid instance; otherwise, a null
   */
  ICompressionEncoder getCompressionEncoder(String name) {
    if (name != null && !name.isEmpty()) {
      for (CodecHolder codec : codecList) {
        if (name.equals(codec.getIdentification())) {
          return codec.getEncoderInstance();
        }
      }
    }
    return null;
  }

    /**
   * Gets an instance of the compression encoder that matches the
   * specified CODEC name, if any.
   * @param name a valid, non-empty string.
   * @return if matched, a valid instance; otherwise, a null
   */
  ICompressionDecoder getCompressionDecoder(String name) {
    if (name != null && !name.isEmpty()) {
      for (CodecHolder codec : codecList) {
        if (name.equals(codec.getIdentification())) {
          return codec.getDecoderInstance();
        }
      }
    }
    return null;
  }


  void shutdown(){
    if(tgExecutor != null){
      tgExecutor.shutdown();
      tgExecutor = null;
    }
  }
}
