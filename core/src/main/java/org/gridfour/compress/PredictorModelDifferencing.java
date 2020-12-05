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
 *  The original inspiration for the differencing predictor
 * was a short description of "Differential Modulation" in
 * Nelson, Mark (1991). "The Data Compression Book", M&T Publishing, Inc.
 * Redwood City, CA, pg. 350.  In his book, Mr. Nelson traces the concept
 * back to a technique used in audio data compression systems.
 *
 * Development note regarding integer arithmetic
 *
 *   This implementation depends on the properties of 32-bit integer
 * addition and subtraction based on the two's-complement format for
 * negative values. It is critical to note that addition and subtraction
 * is fully invertible even in the event of integer overflow.  In other words,
 *
 *      C = Sample - Prior
 *      D = Prior + C
 *      D == Sample even when overflow occurs.
 * For example, when prior is Integer.MIN_VALUE and Sample is Integer.MAX value:
 *
 *       C =  2147483647 - (-2147483648)
 *
 * ordinary arithmetic would give a value of C== 2_294_967_296, which would
 * overflow the 32-bit signed integer data type giving a value of -1.  But,
 * decause of the properties of two's complement arithmetic,
 *
 *      D = (-2147483648) - (-1)
 *
 * would also overflow, resulting in a value of 2147483647.
 *  Three other details about signed 32-bit integers that might be useful
 *  when reviewing the predictor code are
 *   1. There is no true subtraction in two's complement arithmetic.
 *      Given an expressions such as C = A-B, the computer does not actually
 *      subtract B from A.  Instead, it computes the two's complement of B
 *      and adds the result to A.  Negative numbers are expressed as the
 *      two's complement of their positive counterpart.
 *      While there is a concept of "add with carry", there is never
 *      a "subtract with borrow".  Any information that gets pushed past
 *      the high-order bit of a 32 bit integer is lost.
 *   2. The signed integers are not perfectly symmetrical. The maximum value
 *      for a signed 32 bit integer is 2147483647, but the minimum value
 *      is -2147483648. Most negative values
 *      have a positive equivalent, but -2147483648 does not
 *      Counterintuitively, if a program negates the minimum value, the
 *      result is just the minimum value itself.
 *      In other words,  - (-2147483648)  == -2147483648.
 *   3. The bit-numbering convention for the Gridfour project is based on
 *      the corresponding powers-of-two for the bits. So the low-order bit is
 *      always bit zero, the high-order bit is bit 31.  Not all software projects
 *      adopt this convention.
 *
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress;

/**
 * Applies the differencing model to the data. The
 * differencing model always predicts that the current value in a series will
 * have the same value as the prior value.
 * <p>
 * This technique is used in several well-established raster data formats. For
 * example, it is specified for the Gridded Binary 2 (GRIB2) data format used
 * for meteorology data products (see GRIB 2 specification, Section 5-Template
 * 3: "Grid point data 0 complex packing and spatial differencing"). It is also
 * used in the TIFF specification (see TIFF Tag Predictor horizontal
 * differencing, tag 0x013D).
 */
public class PredictorModelDifferencing implements IPredictorModel {

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
        int prior = encodedSeed;
        for (int i = 1; i < nColumns; i++) {
            int test = values[i];
            int delta = test - prior;
            mCodec.encode(delta);
            prior = test;
        }

        for (int iRow = 1; iRow < nRows; iRow++) {
            int index = iRow * nColumns;
            prior = values[index - nColumns];
            for (int i = 0; i < nColumns; i++) {
                int test = values[index++];
                int delta = test - prior;
                mCodec.encode(delta);
                prior = test;
            }

        }

        return mCodec.getEncodedLength();

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
    public PredictorModelType getPredictorType() {
        return PredictorModelType.Differencing;
    }
}
