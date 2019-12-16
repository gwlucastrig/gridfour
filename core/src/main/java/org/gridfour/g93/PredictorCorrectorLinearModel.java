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
 * Applies the predictor-corrector linear function model to the data. The linear
 * model assumes that the data lies on a constant slope and predicts that the
 * value of a sample can be computed from a line passing through of the previous
 * two samples.
 */
public class PredictorCorrectorLinearModel implements IPredictorCorrector {

  int encodedSeed;

  @Override
  public int getSeed() {
    return encodedSeed;
  }

  @Override
  public void decode(int seed, int nRows, int nColumns, byte[] encoding, int offset, int length, int[] output) {
    CodecM32 mCodec = new CodecM32(encoding, offset, length);
    int prior = seed;
    for (int iRow = 0; iRow < nRows; iRow++) {
      int k = iRow * nColumns;
      prior += mCodec.decode();
      output[k++] = prior;
      output[k++] = mCodec.decode() + prior;

      //accumulate second differences for remaining columns in row
      for (int iCol = 2; iCol < nColumns; iCol++, k++) {
        output[k] = mCodec.decode() + 2 * output[k - 1] - output[k - 2];
      }
    }
  }

  @Override
  public int encode(
          int nRows,
          int nColumns,
          int[] values,
          byte[] encoding) {
    CodecM32 mCodec = new CodecM32(encoding, 0, encoding.length);
    encodedSeed = values[0];

    long delta;
    long prior = values[0];

    for (int iRow = 0; iRow < nRows; iRow++) {
      // column zero uses the constant predictor taking the
      // prior value from the previous row 
      int k = iRow * nColumns;
      int test0 = values[k++];
      delta = test0 - prior;
      prior = test0;
      if (isDeltaOutOfBounds(delta)) {
        return -1;
      }
      mCodec.encode((int) delta);

      // column 1 uses the constant predictor taking the prior value from
      // column 0
      int test1 = values[k++];
      delta = test1 - test0;
      if (isDeltaOutOfBounds(delta)) {
        return -1;
      }
      mCodec.encode((int) delta);

      //accumulate second differences for remaining columns in row
      for (int iCol = 2; iCol < nColumns; iCol++, k++) {
        delta = (long) values[k] - 2 * (long) values[k - 1] + (long) values[k - 2];
        if (isDeltaOutOfBounds(delta)) {
          return -1;
        }
        mCodec.encode((int) delta);
      }
    }
    return mCodec.getEncodedLength();
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
    return PredictorCorrectorType.Linear;
  }
}
