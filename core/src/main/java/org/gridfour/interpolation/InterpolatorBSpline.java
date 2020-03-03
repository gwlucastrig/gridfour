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
 * Performs interpolations over a raster (grid) data set using the classic
 * B-Spline algorithm.
 * <p>
 * The B-Spline interpolation treats the input grid as representing a
 * continuous, real-valued surface defined over a 2D coordinate plane. The input
 * grid is specified as an one-dimensional array of floating point values given
 * in row-major order. For purposes of interpolation, this class treats the rows
 * and columns in the grid as having a uniform, unit spacing. While this
 * approach is provides efficiency when computing interpolated values, an
 * adjustment for row and column spacing is required when computing derivatives.
 * This adjustment is discussed below.
 * <p>
 * <strong>Using and reusing an IntepolationResult object</strong>
 * <p>
 * In raster-based applications, it is common for applications to process
 * gridded data sets containing a very large number of samples. Therefore this
 * class is designed to operate efficiently across multiple operations. In
 * particular, it allows an application to construct a single instance of the
 * InterpolationResults class that can be reused across multiple interpolations.
 * If the reference is not null (valid), the interpolate method populates it
 * with the resulting computations and returns the same reference that was
 * input. If the reference is null, the interpolate method constructs a new
 * instance which is returned to the calling method. This approach avoids the
 * overhead of constructing multiple objects.
 * <p>
 * At this time, however, there is some question about how significant
 * this savings may be.  In repeated tests performing 1 million interpolations
 * over a 1000-by-1000 grid, the interpolator required 30 milliseconds when
 * creating new result object for each operation and 20 milliseconds when
 * reusing the result object. So in practice, the savings may be of limited
 * value. However, the testing was not performed on systems in which the
 * JVM was operating under conditions of heavy memory use. In cases where the
 * heap memory grows large, reusing result objects may become important.
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
 */
public class InterpolatorBSpline implements IRasterInterpolator {

  /**
   * Compute the interpolated value at the specified grid coordinates.
   * Derivatives are not computed by this method.
   * <p>
   * The row and column specifications represent real-valued coordinates and may
   * use non-integral values to indicate positions between sample points. The
   * interpolation coordinates may be given for any point within the grid.
   *
   * @param row a real-valued, potentially non-integral row coordinate
   * @param column a real-valued, potentially non-integral column coordinate
   * @param nRowsInGrid the number of rows in the input grid.
   * @param nColumnsInGrid the number of columns in the input grid
   * @param grid the input grid, given in row-major order
   * @return if successful, a valid floating-point number; otherwise, a NaN.
   */
  @Override
  public double interpolateValue(
          double row,
          double column,
          int nRowsInGrid,
          int nColumnsInGrid,
          float[] grid) {
    InterpolationResult r = interpolate(
            row, column, nRowsInGrid, nColumnsInGrid,
            grid, 0, 0, InterpolationTarget.Value, null);
    if (r == null) {
      return Double.NaN;
    } else {
      return r.z;
    }
  }

  /**
   * Compute the interpolated value, the derivatives, and the normal vector to
   * the surface at the specified grid coordinates.
   * <p>
   * The row and column specifications represent real-valued coordinates and may
   * use non-integral values to indicate positions between sample points. The
   * interpolation coordinates may be given for any point within the grid.
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
   * including value, value and first derivative, or value and first and second
   * derivatives.
   * @param outputResult an instance to store the results, or null if the method
   * should construct a new instance.
   * @return a valid instance of InterpolationResult or a null reference. If a
   * valid reference to a result object is specified as an input, that instance
   * will be populated and returned. If a null is specified, a new object will
   * be constructed.
   */
  @Override
  public InterpolationResult interpolate(
          double row,
          double column,
          int nRowsInGrid,
          int nColumnsInGrid,
          float[] grid,
          double rowSpacing,
          double columnSpacing,
          InterpolationTarget target,
          InterpolationResult outputResult) {
    InterpolationResult result;
    if (outputResult == null) {
      result = new InterpolationResult();
    } else {
      result = outputResult;
    }
    result.row = row;
    result.column = column;
    result.interpolationTarget
            = target == null ? InterpolationTarget.Value : target;
    result.z = Double.NaN;

    if (Double.isNaN(column) || Double.isNaN(row)) {
      throw new IllegalArgumentException("Input coordinates specific NaN");
    }

    if (nColumnsInGrid < 4) {
      throw new IllegalArgumentException("Input grid must be at least 4 columns wide");
    }
    if (nRowsInGrid < 4) {
      throw new IllegalArgumentException("Input grid must contain at least 4 rows");
    }

    double uCol = Math.floor(column);
    double vRow = Math.floor(row);
    double u = column - uCol;
    double v = row - vRow;

    int iCol = (int) uCol;
    int iRow = (int) vRow;
    int col0 = iCol - 1;
    int row0 = iRow - 1;

    if (iCol < 0 || iCol > nColumnsInGrid - 1) {
      throw new IllegalArgumentException(
              "Column coordinate " + column + " not in range 0 to " + (nColumnsInGrid - 1));
    }

    if (iRow < 0 || iRow > nRowsInGrid - 1) {
      throw new IllegalArgumentException(
              "Row coordinate " + row + " not in range 0 to " + (nRowsInGrid - 1));
    }

    // make special adjustments for query points on the outer band
    // of the raster.
    if (col0 < 0) {
      col0 = 0;
      u = column - 1.0; // will be a negative number
    } else if (col0 > nColumnsInGrid - 4) {
      col0 = nColumnsInGrid - 4;
      u = column - 1.0 - col0;
    }

    if (row0 < 0) {
      row0 = 0;
      v = row - 1.0;
    } else if (row0 > nRowsInGrid - 4) {
      row0 = nRowsInGrid - 4;
      v = row - 1.0 - row0;
    }

    int offset = row0 * nColumnsInGrid + col0;
    double z00 = grid[offset];
    double z01 = grid[offset + 1];
    double z02 = grid[offset + 2];
    double z03 = grid[offset + 3];
    offset += nColumnsInGrid;
    double z10 = grid[offset];
    double z11 = grid[offset + 1];
    double z12 = grid[offset + 2];
    double z13 = grid[offset + 3];
    offset += nColumnsInGrid;
    double z20 = grid[offset];
    double z21 = grid[offset + 1];
    double z22 = grid[offset + 2];
    double z23 = grid[offset + 3];
    offset += nColumnsInGrid;
    double z30 = grid[offset];
    double z31 = grid[offset + 1];
    double z32 = grid[offset + 2];
    double z33 = grid[offset + 3];

    //    In the code below, we use the variables b and p to represent
    // the "basis functions" for the B-Spline. Traditionally, the variable
    // b is used for ordinary B-Splines, but since we are performing a 
    // 2-D computation, we need a second variable.  
    //    For derivatives, we use the variable bu for db/du and
    // pv for dp/dv.   Second derivatives would be buu, pvv, etc.
    // compute basis weighting factors b(u) in direction of column axis
    //    In the case of the derivatives, note that the computations
    // all involve one or two divisions by the rowScale and columnScale.
    // These are a consequence of the chain rule from calculus
    // and the fact that we need to ensure coordinate axes are isotropic
    // (see Javadoc above).  So the grid coordinates can be viewed as 
    // functions of x, and y, with grid column u(x) and row v(y).
    // For example, recall that the column scale factor is the distance
    // between columns.  So we have an x coordinate x = u*columnScale.
    // Thus u(x) = x/columnScale and du/dx = 1/columnScale.
    // So when we take a derivative of b(u), we have
    //    db/dx  = (db/du)*(du/dx)  =   (db/du)/columnScale.
    //
    double um1 = 1.0 - u;
    double b0 = um1 * um1 * um1 / 6.0;
    double b1 = (3 * u * u * (u - 2) + 4) / 6.0;
    double b2 = (3 * u * (1 + u - u * u) + 1) / 6.0;
    double b3 = u * u * u / 6.0;

    // comnpute basis weighting factors p(v) in direction of row axis
    double vm1 = 1.0 - v;
    double p0 = vm1 * vm1 * vm1 / 6.0;
    double p1 = (3 * v * v * (v - 2) + 4) / 6.0;
    double p2 = (3 * v * (1 + v - v * v) + 1) / 6.0;
    double p3 = v * v * v / 6.0;

    // combine sample points using the basis weighting functions
    // and create four partial results, one for each row of data.
    double s0 = b0 * z00 + b1 * z01 + b2 * z02 + b3 * z03;
    double s1 = b0 * z10 + b1 * z11 + b2 * z12 + b3 * z13;
    double s2 = b0 * z20 + b1 * z21 + b2 * z22 + b3 * z23;
    double s3 = b0 * z30 + b1 * z31 + b2 * z32 + b3 * z33;

    // combine the 4 partial results, computing in the y direction
    result.z = p0 * s0 + p1 * s1 + p2 * s2 + p3 * s3;
    if (target == null || target == InterpolationTarget.Value) {
      result.firstDerivativesSet = false;
      result.secondDerivativesSet = false;
      result.zx = Double.NaN;
      result.zy = Double.NaN;
      result.zxx = Double.NaN;
      result.zxy = Double.NaN;
      result.zyx = Double.NaN;
      result.zyy = Double.NaN;
      return result;
    }

    if (columnSpacing == 0 || rowSpacing == 0) {
      throw new IllegalArgumentException(
              "Non-zero spacing values are required to compute derivatives");
    }

    // compute derivatives of basis functions b, bu(i)=(db/du)(i)
    double bu0 = -um1 * um1 / 2.0 / columnSpacing;
    double bu1 = (3.0 * u / 2.0 - 2.0) * u / columnSpacing;
    double bu2 = (0.5 - (3.0 * u / 2.0 - 1.0) * u) / columnSpacing;
    double bu3 = u * u / 2.0 / columnSpacing;

    // compute derivatives of basis functions pv(i) = (dp/dv)(i)
    double pv0 = -vm1 * vm1 / 2.0 / rowSpacing;
    double pv1 = (3.0 * v / 2.0 - 2.0) * v / rowSpacing;
    double pv2 = (0.5 - (3.0 * v / 2.0 - 1.0) * v) / rowSpacing;
    double pv3 = v * v / 2.0 / rowSpacing;

    // using the partial derivatives of the basis functions db/bu,
    // interpolate dz/bu at u for each row, then combine the interplations
    // the compute partial derivative dz/bu at (u, v).
    s0 = bu0 * z00 + bu1 * z01 + bu2 * z02 + bu3 * z03;
    s1 = bu0 * z10 + bu1 * z11 + bu2 * z12 + bu3 * z13;
    s2 = bu0 * z20 + bu1 * z21 + bu2 * z22 + bu3 * z23;
    s3 = bu0 * z30 + bu1 * z31 + bu2 * z32 + bu3 * z33;
    result.zx = p0 * s0 + p1 * s1 + p2 * s2 + p3 * s3;

    // using the partial derivatives of the basis functions db/pv,
    // interpolate dz/bu at u for each row, then combine the interplations
    // the compute partial derivative dz/bu at (u, v).
    double t0 = pv0 * z00 + pv1 * z10 + pv2 * z20 + pv3 * z30;
    double t1 = pv0 * z01 + pv1 * z11 + pv2 * z21 + pv3 * z31;
    double t2 = pv0 * z02 + pv1 * z12 + pv2 * z22 + pv3 * z32;
    double t3 = pv0 * z03 + pv1 * z13 + pv2 * z23 + pv3 * z33;

    result.zy = b0 * t0 + b1 * t1 + b2 * t2 + b3 * t3;

    result.firstDerivativesSet = true;

    if (target == InterpolationTarget.SecondDerivatives) {
      result.secondDerivativesSet = true;
      result.zxy = pv0 * s0 + pv1 * s1 + pv2 * s2 + pv3 * s3;
      result.zyx = result.zxy;

      double buu0 = (1 - u) / (columnSpacing * columnSpacing);
      double buu1 = (3 * u - 2) / (columnSpacing * columnSpacing);
      double buu2 = (1 - 3 * u) / (columnSpacing * columnSpacing);
      double buu3 = u / (columnSpacing * columnSpacing);

      s0 = buu0 * z00 + buu1 * z01 + buu2 * z02 + buu3 * z03;
      s1 = buu0 * z10 + buu1 * z11 + buu2 * z12 + buu3 * z13;
      s2 = buu0 * z20 + buu1 * z21 + buu2 * z22 + buu3 * z23;
      s3 = buu0 * z30 + buu1 * z31 + buu2 * z32 + buu3 * z33;
      result.zxx = p0 * s0 + p1 * s1 + p2 * s2 + p3 * s3;

      double pvv0 = (1 - v) / (rowSpacing * rowSpacing);
      double pvv1 = (3 * v - 2) / (rowSpacing * rowSpacing);
      double pvv2 = (1 - 3 * v) / (rowSpacing * rowSpacing);
      double pvv3 = v / (rowSpacing * rowSpacing);

      t0 = pvv0 * z00 + pvv1 * z10 + pvv2 * z20 + pvv3 * z30;
      t1 = pvv0 * z01 + pvv1 * z11 + pvv2 * z21 + pvv3 * z31;
      t2 = pvv0 * z02 + pvv1 * z12 + pvv2 * z22 + pvv3 * z32;
      t3 = pvv0 * z03 + pvv1 * z13 + pvv2 * z23 + pvv3 * z33;

      result.zyy = b0 * t0 + b1 * t1 + b2 * t2 + b3 * t3;

    } else {
      result.secondDerivativesSet = false;
      result.zxx = Double.NaN;
      result.zxy = Double.NaN;
      result.zyx = Double.NaN;
      result.zyy = Double.NaN;
    }

    return result;
  }

  /**
   * Indicates whether the interpolation implementation supports the specified
   * target. Not all interpolation techniques support the production of first
   * and second derivatives. So this method provides applications the ability to
   * assess the capabilities of the interpolator.
   *
   * @param target a valid enumeration value.
   * @return true if the target is supported; otherwise, false.
   */
  @Override
  public boolean isInterpolationTargetSupported(InterpolationTarget target) {
    return true;  // supports all currently defined targets.
  }
}
