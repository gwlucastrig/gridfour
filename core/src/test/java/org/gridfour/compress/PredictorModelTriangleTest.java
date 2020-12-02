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
 * 02/2020  G. Lucas     Created  
 *
 * -----------------------------------------------------------------------
 */
 
package org.gridfour.compress;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PredictorModelTriangleTest {

  public PredictorModelTriangleTest() {
  }

  /**
   * Test of encode and decode methods, of class PredictorModelTriangle.
   */
  @Test
  public void testRoundTrip() {
    int nRows = 10;
    int nColumns = 10;
    int[] values = new int[nRows * nColumns];
    for (int iRow = 0; iRow < nRows; iRow++) {
      int offset = iRow * nColumns;
      int v = iRow;
      for (int iCol = 0; iCol < 10; iCol += 2) {
        values[offset + iCol] = v;
        v++;
      }
    }

    byte[] encoding = new byte[nRows * nColumns * 6];
    PredictorModelTriangle instance
            = new PredictorModelTriangle();

    int encodedLength = instance.encode(nRows, nColumns, values, encoding);
    int seed = instance.getSeed();

    int[] decoding = new int[values.length];
    instance.decode(seed, nRows, nColumns, encoding, 0, encodedLength, decoding);
    for (int i = 0; i < decoding.length; i++) {
      assertEquals(values[i], decoding[i],
             "Failure to decode at index "+i+", input="+values[i]+", output="+decoding[i]);
    }

  }
 
  

}
