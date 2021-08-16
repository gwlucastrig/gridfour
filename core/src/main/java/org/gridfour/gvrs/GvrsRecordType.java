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
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

/**
 * Defines record types for non-tile records
 */
public enum GvrsRecordType {

    /**
     * Data is stored using the Java 4-byte integer data type.
     */
    VariableLengthRecord(-1),
    /**
     * An undefined record type
     */
    Undefined(-2);

    final int codeValue;

    GvrsRecordType(int codeValue) {
        this.codeValue = codeValue;
    }

    /**
     * Gets the code value to be stored in a data file to indicate what
     * data type was used for the non-compressed storage representation.
     *
     * @return gets an integer code value indicating the data type; used
     * internally.
     */
    public int getCodeValue() {
        return codeValue;
    }

    static GvrsRecordType valueOf(int codeValue) {
        if (codeValue == -1) {
            return VariableLengthRecord;
        } else {
            return Undefined;
        }
    }
}
