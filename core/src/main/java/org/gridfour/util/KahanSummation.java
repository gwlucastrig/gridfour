/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2020  Gary W. Lucas.

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
 * 11/2018  G. Lucas     Created for Tinfour project
 * 04/2020  G. Lucas     Adapted for Gridfour project
 *
 * Notes:
 *
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.util;

/**
 * Provides methods and elements for Kahan's algorithm for
 * summing a set of numerical values with extended precision arithmetic.
 * Often, when adding a large set of small values to a large value,
 * the limited precision of computer arithmetic results in the contribution
 * of the small values being lost.  This limitation may result in a loss
 * of valuable data if the total sum of the collected small values is
 * large enough enough to make a meaningful contribution to the
 * large value. Kahan's algorithm extends the precision of the computation
 * so that the contribution of small values is preserved.
 *
 */
public class KahanSummation {
 private double c;  // compensator for Kahan summation
 private double s;  // summand
 private long n;

 /**
  * Add the value to the summation
  * @param a a valid floating-point number
  */
 public void add(double a){
       double y, t;
    y = a - c;
    t = s + y;
    c = (t - s ) - y;
    s = t;
    n++;
 }

 /**
  * The current value of the summation.
  * @return the standard-precision part of the sum,
  * a valid floating-point number.
  */
 public double getSum(){
   return s;
 }

 /**
  * Gets the mean value of the summands.
  * @return a valid floating-point value.
  */
 public double getMean(){
   if(n==0){
     return 0;
   }
   return s/n;
 }

 /**
  * Gets the number of values that were added to the summation.
   *
   * @return a value of zero or greater.
  */
  public long getSummandCount() {
   return n;
 }

}
