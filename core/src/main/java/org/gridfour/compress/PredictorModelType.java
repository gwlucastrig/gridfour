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
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress;

/**
 * Used to represent the type of predictor used to encode data
 */
public enum PredictorModelType {
    /**
     * No predictor is applied; the data is stored as literals
     */
    None(0),
    /**
     * The differencing-predictor model is applied. This model assumes that
     * the value for an element in a sequence is predicted by its predecessor.
     */
    Differencing(1),
    /**
     * The linear-predictor model is applied
     */
    Linear(2),
    /**
     * The triangle-predictor model is applied
     */
    Triangle(3),
    /**
     * Used when the data includes null values.
     */
    DifferencingWithNulls(4);

    final int codeValue;

    PredictorModelType(int codeValue) {
        this.codeValue = codeValue;
    }

    /**
     * Gets the code value to be stored in a data file to indicate what kind of
     * predictor was used to store data
     *
     * @return an integer in the range 0 to 4.
     */
    public int getCodeValue() {
        return codeValue;
    }

    /**
     * Gets the enumeration instance associated with the specified code value
     *
     * @param codeValue a valid integer code value
     * @return the associated enumeration instance.
     */
    public static PredictorModelType valueOf(int codeValue) {
        switch (codeValue) {
            case 0:
                return None;
            case 1:
                return Differencing;
            case 2:
                return Linear;
            case 3:
                return Triangle;
            case 4:
                return DifferencingWithNulls;
            default:
                return None;  // technically, this is an invalid value.
        }
    }
}
