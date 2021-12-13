/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2021 Gary W. Lucas.

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
 * 12/2021  G. Lucas     Created  
 *
 * -----------------------------------------------------------------------
 */
 
package org.gridfour.lsop;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

public class LsOptimalPredictor12Test {

  public LsOptimalPredictor12Test() {
  }

  /**
   * Test of encode and decode methods, of class LsOptimalPredictor12.
   */
  @Test
  public void testRoundTrip() {

    double u1 = 0.3;
    double u2 = 0.3;
    double u3 = 0.3;
    double u4 = 0.3;
    double u5 = 0.3;
    double u6 = 0.3;
    double u7 = 0.3;
    double u8 = 0.3;
    double u9 = 0.3;
    double u10 = 0.3;
    double u11 = 0.3;
    double u12 = 0.3;
    
      // initialize the values with 1's.  This will populate
    // the "unreachable" parts of the raster.  We will then overwrite
    // them with values that can be used to test the compressor.
        int nRows = 10;
    int nColumns = 10;
    int[] values = new int[nRows * nColumns];
    Arrays.fill(values, 1);
  
    for (int iRow = 2; iRow < nRows; iRow++) {
      for (int iCol = 2; iCol < nColumns-2; iCol++) {
       int index = iRow * nColumns + iCol;
        double  p
          = u1 * values[index - 1]
          + u2 * values[index - nColumns - 1]
          + u3 * values[index - nColumns]
          + u4 * values[index - nColumns + 1]
          + u5 * values[index - nColumns + 2]
          + u6 * values[index - 2]
          + u7 * values[index - nColumns - 2]
          + u8 * values[index - 2 * nColumns - 2]
          + u9 * values[index - 2 * nColumns - 1]
          + u10 * values[index - 2 * nColumns]
          + u11 * values[index - 2 * nColumns + 1]
          + u12 * values[index - 2 * nColumns + 2];
        values[index] = (int)(p+0.5);
      }
    }
 

    LsEncoder12 encoder = new LsEncoder12();
    byte [] encoding = encoder.encode(1, nRows, nColumns, values);
  
    assertFalse(encoding==null, "Null result from encoder");
    
    int[] result = null;
    try {
      LsDecoder12 decoder = new LsDecoder12();
      result = decoder.decode(nRows, nColumns, encoding);
    } catch (IOException ioex) {
      fail("IOException " + ioex.getMessage());
    }
    
    for(int i=0; i<nRows*nColumns; i++){
      assertEquals(values[i], result[i], "failed at index "+i);
    }
     
  }
 
  

}
