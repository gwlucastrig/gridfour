/*
 * The MIT License
 *
 * Copyright 2019 gwluc.
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
 * 10/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import org.gridfour.util.CodecM32;

/**
 * Applies the predictor-corrector triangle function model to the data. The
 * triangle model assumes that the data lies on a plane that can be defined by
 * three control points spaced at unit distances across the horizontal
 * coordinates.
 * <p>
 * The method used for this class is based on the published work:
 * <cite>Kidner, D.B. and Smith, D. H. (1992). "Compression of digital elevation
 * models by Huffman coding", Computers &amp; Geosciences, 18(8),
 * 1013-1024</cite>
 */
public class PredictorCorrectorTriangleModel implements IPredictorCorrector {

  int encodedSeed;

  @Override
  public int getSeed() {
    return encodedSeed;
  }

  @Override
  public void decode(
          int seed,
          int nRows,
          int nColumns,
          byte[] encoding,
          int offset,
          int length,
          int[] output) {
    CodecM32 mCode = new CodecM32(encoding, offset, length);
    output[0] = seed;
    int prior = seed;
    for (int i = 1; i < nColumns; i++) {
      prior += mCode.decode();
      output[i] = prior;
    }

    // the zeroeth column is populated using the constant-predictor model
    // all other columns are populated using the triangule-predictor
    for (int iRow = 1; iRow < nRows; iRow++) {
      int k1 = iRow * nColumns;
      int k0 = k1 - nColumns;
      output[k1] = mCode.decode() + output[k0];
      for (int i = 1; i < nColumns; i++) {
        k0++;
        k1++;
        int z0 = output[k0 - 1];
        int zb = output[k1 - 1];
        int za = output[k0];
        output[k1] = mCode.decode() + (za + zb - z0);
      }
    }

  }

  @Override
  public int encode(
          int nRows,
          int nColumns,
          int[] values,
          byte[] encoding) {

    if (nRows < 2 || nColumns < 2) {
      return -1;
    }
    CodecM32 mCode = new CodecM32(encoding, 0, encoding.length);
    // The triangle coding cannot be performed on the first row, 
    // so use the constant-predictor model approach
    encodedSeed = values[0];
    long prior = values[0];
    for (int i = 1; i < nColumns; i++) {
      long test = values[i];
      long delta = test - prior;
      if (isDeltaOutOfBounds(delta)) {
        return -1;
      }
      mCode.encode((int) delta);
      prior = test;
    }

    // start the triangle encoding with the second row
    // the first column is populated using the constant-predictor model,
    // all subsequent columns the triangle predictor
    for (int iRow = 1; iRow < nRows; iRow++) {
      int k1 = iRow * nColumns;
      int k0 = k1 - nColumns;
      long delta = (long) values[k1] - (long) values[k0];
      if (isDeltaOutOfBounds(delta)) {
        return -1;
      }
      mCode.encode((int) delta);

      for (int i = 1; i < nColumns; i++) {
        k0++;
        k1++;
        long z0 = values[k0 - 1];
        long zb = values[k1 - 1];
        long za = values[k0];
        long zc = values[k1];
        delta = zc - (za + zb - z0);
        if (isDeltaOutOfBounds(delta)) {
          return -1;
        }
        mCode.encode((int) delta);
      }
    }

    return mCode.getEncodedLength();
  }

  @Override
  public boolean isNullDataSupported() {
    return false;
  }

  private boolean isDeltaOutOfBounds(long delta) {
    if (delta < Integer.MIN_VALUE) {
      return true;
    } else if (delta > Integer.MAX_VALUE) {
      return true;
    }
    return false;
  }

  @Override
  public PredictorCorrectorType getPredictorType() {
    return PredictorCorrectorType.Triangle;
  }
}
