/* --------------------------------------------------------------------
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
 * OUT OF OR IN CONNECTI
 * ---------------------------------------------------------------------
 */
 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 12/2019  G. Lucas     Created  
 *
 * Notes:
 *
 *  
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;
import java.io.PrintStream;

/**
 * A simple place-holder to be used while a file object is being initialized.
 */
class CodecPlaceHolder implements IG93CompressorCodec {


  @Override
  public void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
  }

  @Override
  public int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
    return null;
  }

  @Override
  public byte[] encode(int codecIndex, int nRows, int nCols, int[] values) {
    return null;
  }

  @Override
  public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {

  }

  @Override
  public void clearAnalysisData() {

  }
  
    @Override
  public byte[] encodeFloats(int codecIndex, int nRows, int nCols, float[] values) {
    return null;
  }

  @Override
  public float[] decodeFloats(int nRows, int nColumns, byte[] packing) throws IOException {
    return null;
  }

  @Override
  public boolean implementsFloatEncoding() {
   return false;
  }

    
  @Override
  public boolean implementsIntegerEncoding() {
    return false;
  }
}
