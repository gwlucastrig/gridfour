/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2020  Gary W. Lucas.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribte, sublicense, and/or sell
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
package org.gridfour.interpolation;

/**
 * Provides an interface for implementing interpolations that operate
 * over a raster grid.
 * <p>
 * <strong>Row and Column Scale Factors</strong>
 * <p>
 * In order to produce meaningful computations for derivatives and surface
 * normal vectors, it is necessary that the three coordinate axes (x, y, and z)
 * used in the interpolation have a uniform scale. In other words, one unit of
 * change in the coordinate system associated with the input (the row-column
 * coordinates) should cover the same distance as one unit of change in the
 * output coordinates. Thus, when computing derivatives or the surface normal,
 * the interpolation method implemented by this class requires the specification
 * “spacing” values for the rows and columns. These values allow the application
 * to assign appropriate scales to the input grid and to treat the interpolation
 * coordinate system as “isotropic” (eg. As having a uniform scale).
 *
 * The rowSpace and columnSpace specifications are required in order to produce
 * meaningful results for surface normal and derivatives. If your application is
 * only using the interpolated value, and you do not wish to compute
 * derivatives, you may supply zeroes for these input. For testing purposes, you
 * may also try specifying values of 1.0 for these inputs.
 * <p>
 * <strong>Using and reusing an IntepolationResult object</strong>
 * <p>
 * In raster-based applications, it is common for applications to process
 * gridded data sets containing a very large number of samples. Therefore
 * implementations of this class are designed to operate efficiently.
 * In particular, they may opt to re-use InterpolationResult objects 
 * across multiple interpolations.  Implementations of this interface
 * are expected to accept a reference to an InterpolationResult object.
 * If the reference is not null (valid), the interpolate method populates
 * it with the resulting computations and returns the same reference that
 * was input. If the reference is null, the interpolate method constructs
 * a new instance which is returned to the calling method.
 * 
 */
public interface IRasterInterpolator {

  /**
   * Compute the interpolated value, the derivatives, and the normal vector to
   * the surface at the specified grid coordinates.
   * <p>
   * The row and column specifications represent
   * real-valued coordinates and may use non-integral values to
   * indicate positions between sample points.
   * The interpolation coordinates may be given for any point within the grid.
   * <p>
   * The row and column spacing parameters are necessary for this routine to
   * compute derivatives and surface normal vectors. If derivatives are not
   * required, the spacing parameters will be ignored and applications are free
   * to supply zeroes.
   *
   * @param row a real-valued, potentially non-integral row coordinate
   * @param column a real-valued, potentially non-integral column coordinate
   * @param nRowsInGrid the number of rows in the input grid.
   * @param nColumnsInGrid the number of columns in the input grid
   * @param grid the input grid, given in row-major order
   * @param rowSpacing a scale factor required for computing derivatives, or
   * zero if derivative and surface normal vector computations are not required
   * @param columnSpacing a scale factor required for computing derivatives, or
   * zero if derivative and surface normal vector computations are not required
   * @param target indicates which product is required for the interpolation,
   * including value, value and first derivative, or value and first and
   * second derivatives.
   * @param outputResult an instance to store the results, or null if the method
   * should construct a new instance.
   * @return a valid instance of InterpolationResult or a null reference.
   * If a valid reference to a result object is specified as an input,
   * that instance will be populated and returned. If a null is specified,
   * a new object will be constructed.
   */
  InterpolationResult interpolate(
          double row, double column,
          int nRowsInGrid, int nColumnsInGrid, float[] grid, 
          double rowSpacing, double columnSpacing, 
          InterpolationTarget target, 
          InterpolationResult outputResult);

  /**
   * Compute the interpolated value at the specified grid coordinates.
   * Derivatives are not computed by this method.
   * <p>
   * The row and column specifications represent
   * real-valued coordinates and may use non-integral values to
   * indicate positions between sample points.
   * The interpolation coordinates may be given for any point within the grid.
   *
   * @param row a real-valued, potentially non-integral row coordinate
   * @param column a real-valued, potentially non-integral column coordinate
   * @param nRowsInGrid the number of rows in the input grid.
   * @param nColumnsInGrid the number of columns in the input grid
   * @param grid the input grid, given in row-major order
   * @return if successful, a valid floating-point number; otherwise,
   * a NaN.
   */
  double interpolateValue(double row, double column, int nRowsInGrid, int nColumnsInGrid, float[] grid);

  /**
   * Indicates whether the interpolation implementation supports the
   * specified target. Not all interpolation techniques support the production
   * of first and second derivatives. So this method provides applications
   * the ability to assess the capabilities of the interpolator.
   * @param target a valid enumeration value.
   * @return true if the target is supported; otherwise, false.
   */
  boolean isInterpolationTargetSupported(InterpolationTarget target);
  
}
