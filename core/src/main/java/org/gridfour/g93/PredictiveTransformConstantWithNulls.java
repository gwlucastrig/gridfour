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
import static org.gridfour.g93.G93FileConstants.NULL_DATA_CODE;

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
public class PredictiveTransformConstantWithNulls implements IPredictiveTransform {

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

    // we begin by traversing the data in the pattern of the
    // standard predictor.  we identify each value that immediately
    // follows a null.  the average of these values will give us an
    // optimal seed for the data.  At the beginning of this loop,
    // we initialize nullFlag to true. That way, if the first value
    // is not null, it will contribute to the average.
    long sumStart = 0;
    int nStart = 0;
    boolean nullFlag = true;
    for (int iRow = 0; iRow < nRows; iRow++) {
      int rowOffset = iRow * nColumns;
      for (int iCol = 0; iCol < nColumns; iCol++) {
        int test = values[rowOffset + iCol];
        if (test == NULL_DATA_CODE) {
          nullFlag = true;
        } else {
          if (nullFlag) {
            sumStart += test;
            nStart++;
          }
          nullFlag = false;
        }
      }
      nullFlag = values[rowOffset] == NULL_DATA_CODE;
    }

    double avgStart = (double) sumStart / nStart;
    encodedSeed = (int) Math.floor(avgStart + 0.5);

    // once the seed is established, package up the data
    // the encoded seed is never null
    long prior = encodedSeed;
    nullFlag = false;
    for (int iRow = 0; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      for (int iCol = 0; iCol < nColumns; iCol++) {
        int test = values[index++];
        if (test == NULL_DATA_CODE) {
          nullFlag = true;
          mCodec.encode(NULL_DATA_CODE);
        } else {
          if (nullFlag) {
            prior = encodedSeed;
            nullFlag = false;
          }
          long delta = test - prior;
          if (isDeltaOutOfBounds(delta)) {
            return -1;
          }
          mCodec.encode((int) delta);
          prior = test;
        }
      }
      prior = values[iRow * nColumns];
      nullFlag = prior == NULL_DATA_CODE;
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

    int prior = seed; // the seed is never null
    boolean nullFlag = true; // but we start with a null flag
    for (int iRow = 0; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      for (int iCol = 0; iCol < nColumns; iCol++) {
        int test = mCodec.decode();
        if (test == NULL_DATA_CODE) {
          nullFlag = true;
          output[index++] = NULL_DATA_CODE;
        } else {
          if (nullFlag) {
            nullFlag = false;
            prior = seed;
          }
          prior += test;
          output[index++] = prior;
        }
      }
      prior = output[iRow * nColumns];
      nullFlag = prior == NULL_DATA_CODE;
    }
  }

  @Override
  public boolean isNullDataSupported() {
    return true;
  }

  @Override
  public PredictiveTransformType getPredictorType() {
    return PredictiveTransformType.ConstantWithNulls;
  }
}
