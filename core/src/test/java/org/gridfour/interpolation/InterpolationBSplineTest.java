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
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.gridfour.interpolation.InterpolationTarget;
import org.gridfour.interpolation.InterpolationResult;
import org.gridfour.interpolation.InterpolatorBSpline;


/**
 * Performs unit tests for the B-Spline interpolation implementation
 */
public class InterpolationBSplineTest {


  static double f(double x, double y) {
    double x2 = x * x;
    double x3 = x * x2;
    double y2 = y * y;
    double y3 = y * y2;
    return x3 + x2 * y + y2 * x + y3;
  }

  static double fx(double x, double y) {
    double x2 = x * x;
    double y2 = y * y;
    return 3 * x2 + 2 * x * y + y2;
  }

  static double fxx(double x, double y) {
    return 6 * x + 2 * y;
  }

  static double fy(double x, double y) {
    double x2 = x * x;
    double y2 = y * y;
    return x2 + 2 * x * y + 3 * y2;
  }

  static double fyy(double x, double y) {
    return 2 * x + 6 * y;
  }

  static double fxy(double x, double y) {
    return 2 * x + 2 * y;
  }


  public InterpolationBSplineTest() {
  
  }
  
	/**
	* Test of interpolationAll method.
	*/
	@Test
	public void testInterpolationAll() {
		InterpolatorBSpline interp = new InterpolatorBSpline();
		InterpolationResult result = new InterpolationResult();
		
		assertTrue(
			interp.isInterpolationTargetSupported(InterpolationTarget.SecondDerivatives), 
			"Implementation indicates that second derivatives are not supported");

		float z[] = new float[121];
		int k = 0;
		for (int i = 0; i <= 10; i++) {
			for (int j = 0; j <= 10; j++) {
				double x = j / 10.0;
				double y = i / 10.0;
				z[k++] = (float) f(x, y);
			}
		}


		// The following threshold values are based on experimentation
		// rather than any theory-based criteria.  They are 
		// are rather a bit larger than absolutely needed. 
		for (double row = 0; row<= 10; row += 0.25) {
			for (double col = 0; col<= 10; col += 0.25) {
				double x = col / 10.0;
				double y = row / 10.0;
				result = interp.interpolate(
				    row, col, 
					11, 11, z, 0.1, 0.1, 
					InterpolationTarget.SecondDerivatives, result);

			    assertEquals(result.row,    row, 0, "Row not transcribed to result");
				assertEquals(result.column, col, 0, "Column not transcribed to result");
				assertTrue(result.firstDerivativesSet, "First derivative flag is not set to true");
				assertTrue(result.secondDerivativesSet, "First derivative flag is not set to true");
				
				assertEquals(result.z,   f(x, y),   3.0e-2,  "Interpolated z out of bounds");
				assertEquals(result.zx,  fx(x, y),  2.0e-2,  "Interpolated dz/dx out of bounds");
				assertEquals(result.zy,  fy(x, y),  2.0e-2,  "Interpolated dz/dy out of bounds");
				assertEquals(result.zxx, fxx(x, y), 1.0e-4,  "Interpolated d(dz/dx)/dx out of bounds");
				assertEquals(result.zyy, fyy(x, y), 1.0e-4,  "Interpolated d(dz/dy)/dy out of bounds");
				assertEquals(result.zxy, fxy(x, y), 1.0e-4,  "Interpolated d(dz/dx)/dy out of bounds");
				
				double []n = result.getUnitNormal();
				assertNotNull(n, "Computed normal is null");
				
				double m = n[0]*n[0] + n[1]*n[1] + n[2]*n[2];
				assertEquals(m, 1.0, 1.0e-6, "Computed normal is not a unit vector");
			}
		}
	}

}