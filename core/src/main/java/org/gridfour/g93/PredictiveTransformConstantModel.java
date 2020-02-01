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
 *  The original inspiration for the constant-value predictor
 * was a short description of "Differential Modulation" in
 * Nelson, Mark (1991). "The Data Compression Book", M&T Publishing, Inc.
 * Redwood City, CA, pg. 350.  In his book, Mr. Nelson traces the concept
 * back to a technique used in audio data compression systems.
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import org.gridfour.util.CodecM32;

/**
 * Applies to predictive-transform constant-value model to the data. The
 * constant-value model always predicts that the current value in a series will
 * have the same value as the prior value.
 * <p>
 * This technique is used in several well-established raster data formats. For
 * example, it is specified for the Gridded Binary 2 (GRIB2) data format used
 * for meteorology data products (see GRIB 2 specification, Section 5-Template
 * 3: "Grid point data 0 complex packing and spatial differencing"). It is also
 * used in the TIFF specification (see TIFF Tag Predictor horizontal
 * differencing, tag 0x013D).
 */
public class PredictiveTransformConstantModel implements IPredictiveTransform {

  int encodedSeed;

  @Override
  public int getSeed() {
    return encodedSeed;
  }

  @Override
  public int encode(
          int nRows,
          int nColumns,
          int[] values,
          byte[] output) {
    CodecM32 mCodec = new CodecM32(output, 0, output.length);
    encodedSeed = values[0];
    long prior = encodedSeed;
    for (int i = 1; i < nColumns; i++) {
      long test = values[i];
      long delta = test - prior;
      if (isDeltaOutOfBounds(delta)) {
        return -1;
      }
      mCodec.encode((int) delta);
      prior = test;
    }

    for (int iRow = 1; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      prior = values[index - nColumns];
      for (int i = 0; i < nColumns; i++) {
        long test = values[index++];
        long delta = test - prior;
        if (isDeltaOutOfBounds(delta)) {
          return -1;
        }
        mCodec.encode((int) delta);
        prior = test;
      }

    }

    return mCodec.getEncodedLength();

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
  public void decode(
          int seed,
          int nRows,
          int nColumns,
          byte[] encoding, int offset, int length,
          int[] output) {
    CodecM32 mCodec = new CodecM32(encoding, offset, length);
    output[0] = seed;
    int prior = seed;
    for (int i = 1; i < nColumns; i++) {
      prior += mCodec.decode();
      output[i] = prior;
    }

    for (int iRow = 1; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      prior = output[index - nColumns];
      for (int iCol = 0; iCol < nColumns; iCol++) {
        prior += mCodec.decode();
        output[index++] = prior;
      }
    }
  }

  @Override
  public boolean isNullDataSupported() {
    return false;
  }

  @Override
  public PredictiveTransformType getPredictorType() {
    return PredictiveTransformType.Constant;
  }
}
