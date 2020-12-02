/*
 * The MIT License
 *
 * Copyright 2019 Gary W. Lucas.
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
 *   the first row and then the first column are pre-populated using
 * simple differences. After that, the remainder of the grid is populated
 * using the triangle predictor.
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress;

/**
 * Applies the triangle model to the data. The
 * triangle model assumes that the data lies on a plane that can be defined by
 * three control points spaced at unit distances across the horizontal
 * coordinates.
 * <p>
 * The method used for this class is based on the published work:
 * <cite>Kidner, D.B. and Smith, D. H. (1992). "Compression of digital elevation
 * models by Huffman coding", Computers &amp; Geosciences, 18(8),
 * 1013-1024</cite>
 */
public class PredictorModelTriangle implements IPredictorModel {

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
    CodecM32 mCodec = new CodecM32(encoding, offset, length);

    // The zeroeth row and column are populated using simple differences.
    // All other columns are populated using the triangle-predictor
    output[0] = seed;
    int prior = seed;
    for (int i = 1; i < nColumns; i++) {
      prior += mCodec.decode();
      output[i] = prior;
    }
    prior = seed;
    for (int i = 1; i < nRows; i++) {
      prior += mCodec.decode();
      output[i * nColumns] = prior;
    }

    for (int iRow = 1; iRow < nRows; iRow++) {
      int k1 = iRow * nColumns;
      int k0 = k1 - nColumns;
      for (int i = 1; i < nColumns; i++) {
        long za = output[k0++];
        long zb = output[k1++];
        long zc = output[k0];
        output[k1] = (int) (mCodec.decode() + (zb + zc - za));
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
    CodecM32 mCodec = new CodecM32(encoding, 0, encoding.length);
    // The zeroeth row and column are populated using simple differences.
    // All other grid cells are populated using the triangle-predictor
    encodedSeed = values[0];
    long prior = encodedSeed;
    for (int i = 1; i < nColumns; i++) {
      long test = values[i];
      long delta = test - prior;
      mCodec.encode((int) delta);
      prior = test;
    }

    prior = encodedSeed;
    for (int i = 1; i < nRows; i++) {
      long test = values[i * nColumns];
      long delta = test - prior;
      mCodec.encode((int) delta);
      prior = test;
    }

    // populate the rest of the grid using the triangle-predictor model
    for (int iRow = 1; iRow < nRows; iRow++) {
      int k1 = iRow * nColumns;
      int k0 = k1 - nColumns;
      for (int i = 1; i < nColumns; i++) {
        long za = values[k0++];
        long zb = values[k1++];
        long zc = values[k0];
        long zs = values[k1];  // the source value
        long delta = zs - (zc + zb - za);
        mCodec.encode((int) delta);
      }
    }

    return mCodec.getEncodedLength();
  }

  @Override
  public boolean isNullDataSupported() {
    return false;
  }


  @Override
  public PredictorModelType getPredictorType() {
    return PredictorModelType.Triangle;
  }
}
