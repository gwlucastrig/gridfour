/*
 * The MIT License
 *
 *  Copyright (C) 2020  Gary W. Lucas.
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
 * -----------------------------------------------------------------------
 */
package org.gridfour.interpolation;

/**
 * A simple container class to carry the results from an interpolation.
 * <p>
 * The results in this class treat the dependent variable z as a function
 * evaluated over the grid coordinate system (x, y) where x is the coordinate in
 * the direction of columns and y is the coordinate in the direction of rows.
 * <p>
 * <strong>Note: </strong>This class is designed so that instances of
 * it can be used and re-used across multiple operations. Therefore it
 * offers little protection in terms of member access.  All members are
 * public.  All of them are mutable. Applications using this class
 * should apply caution to the way member elements are accessed since
 * they are subject to change between interpolation calls.
 */
public class InterpolationResult {

  /**
   * A floating-point value indicating the row coordinate that was
   * specified for the interpolation. Row and column coordinates may be non-integral.
   */
  public double row;
  
  /**
   * A floating-point value indicating the column coordinate that was
   * specified for the interpolation. Row and column coordinates may be non-integral.
   */
  public double column;
  
  /**
   * The product that was specified for interpolation, may indicate either
   * a value, a value and first derivatives, or a value and both first
   * and second derivatives.
   */
  InterpolationTarget interpolationTarget;
  
  
  /**
   * The interpolated value for the coordinate
   */
  public double z;

  /**
   * Indicates that the first derivatives were computed by the interpolator.
   */
  public boolean firstDerivativesSet;

  /**
   * The value for the partial derivative of z with respect to x (in the
   * direction of the column axis).
   */
  public double zx;

  /**
   * The value for the partial derivative of z with respect to y (in the
   * direction of the row axis).
   */
  public double zy;

  /**
   * Indicates that the second derivatives were computed by the interpolator.
   */
  public boolean secondDerivativesSet;

  /**
   * The value for the second derivative of z with respect to x (in the
   * direction of the column axis).
   */
  public double zxx;

  /**
   * The value for the second partial derivative of z with respect to x and v.
   */
  public double zxy;

  /**
   * The value for the second partial derivative of z with respect to y and u.
   */
  public double zyx;

  /**
   * The value for the second partial derivative of z with respect to v.
   */
  public double zyy;

  /**
   * Uses the derivatives (zx, zy) stored in this instance to compute a unit
   * normal.
   *
   * @return if successful, a valid array giving the x, y, and z coordinates of
   * a unit normal to the surface at the interpolation point; in the event of a
   * failure, a null.
   */
  public double[] getUnitNormal() {
    if (firstDerivativesSet) {
      double s = Math.sqrt(zx * zx + zy * zy + 1);
      double a[] = new double[3];
      a[0] = -zx / s;
      a[1] = -zy / s;
      a[2] = 1.0 / s;
      return a;
    }
    return null;
  }
  
  public void nullify(){
    this.z = Double.NaN;
     this.zx = Double.NaN;
      this.zy = Double.NaN;
       this.zxx = Double.NaN;
       this.zxy = Double.NaN;
       this.zyx = Double.NaN;
       this.zyy = Double.NaN;
       this.firstDerivativesSet = false;
       this.secondDerivativesSet = false;
  }
}
