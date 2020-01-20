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
 *   The first two columns are pre-populated using simple differences,
 * then the remainder of the data is filled in using the linear-predictor.
 * 
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
  public void decode(
          int seed, 
          int nRows, 
          int nColumns,
          byte[] encoding, 
          int offset, 
          int length, 
          int[] output)
  {
    CodecM32 mCodec = new CodecM32(encoding, offset, length);
    long prior = seed;
    output[0] = seed;
    output[1] = (int) (mCodec.decode() + prior);
    for (int iRow = 1; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      long test = mCodec.decode() + prior;
      output[index] = (int) test;
      prior = test;
      output[index + 1] = (int) (mCodec.decode() + test);
    }

    for (int iRow = 0; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      long a = output[index];
      long b = output[index + 1];

      //accumulate second differences starting at column 2 for row
      for (int iCol = 2; iCol < nColumns; iCol++) {
        long delta = mCodec.decode();
        long c = delta + 2 * b - a;  // delta = c - 2 * b + a;
        a = b;
        b = c;
        output[index + iCol] = (int) c;
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

    long delta, test;
    long prior = values[0];
    delta = (long) values[1] - prior;
    if (isDeltaOutOfBounds(delta)) {
      return -1;
    }
    mCodec.encode((int) delta);
    for (int iRow = 1; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      test = values[index];
      delta = test - prior;
      if (isDeltaOutOfBounds(delta)) {
        return -1;
      }
      mCodec.encode((int) delta);
      prior = test;

      test = values[index + 1];
      delta = test - prior;
      if (isDeltaOutOfBounds(delta)) {
        return -1;
      }
      mCodec.encode((int) delta);
    }

    for (int iRow = 0; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      long a = values[index];
      long b = values[index + 1];
      //accumulate second differences starting at column 2
      for (int iCol = 2; iCol < nColumns; iCol++) {
        long c = values[index + iCol];
        delta = c - 2 * b + a;
        if (isDeltaOutOfBounds(delta)) {
          return -1;
        }
        mCodec.encode((int) delta);
        a = b;
        b = c;
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
