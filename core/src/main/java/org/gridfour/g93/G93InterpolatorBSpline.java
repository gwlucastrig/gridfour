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
 * 01/2020  G. Lucas     Created  
 *
 * Notes:
 *   The design of this class attempts to provide a simple API
 * for the most frequently used calls while supporting a complete
 * set of data access routines.
 *    Some developers have asked me why I didn't use Java's Optional in this
 * class instead of methods that return null array references. 
 * While Optional has its place, in this case using Optional resulted in
 * a measureable increase in access time when processing millions of
 * interpolations (and millions of interpolations is not an unusual workload).
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;

/**
 * Performs interpolations over a G93 raster file using the classic B-Spline
 * algorithm.
 * <p>
 * For geographic coordinates, logic is implemented to support cases where the
 * interpolated values span the coordinate-system division (usually at +/-180
 * degrees longitude)
 * <p>
 * This class is intended to be used for applications that may require millions
 * of calculations. Therefore, emphasis has been placed on speed.
 * <strong>Under development</strong> Module has undergone some testing and
 * seems to reliably compute correct values. Development is required to add more
 * more methods, including the computation of surface derivatives and proper
 * access to G93 files of dimension higher than 1.
 */
public class G93InterpolatorBSpline {

  private final G93File g93;
  private final G93FileSpecification spec;
  private final int nRowsInRaster;
  private final int nColsInRaster;
  private final int dimension;
  private final int nColsForWrap;
  private final int standardHandlingLeft;
  private final int standardHandlingRight;
  private final boolean geoCoordinates;
  private final boolean geoWrapsLongitude;
  private final boolean geoBracketsLongitude;

  private double u;  // parameter for interpolation point, x-axis direction
  private double v;  // parameter for interpolation point, y-axis direction

  /**
   * Constructs an instance that will operate over the specified G93 File.
   *
   * @param g93 a valid G93 file opened for read access.
   * @throws java.io.IOException in the event of an I/O error
   */
  public G93InterpolatorBSpline(G93File g93) throws IOException {
    this.g93 = g93;
    spec = g93.getSpecification();
    nRowsInRaster = spec.getRowsInGrid();
    nColsInRaster = spec.getColumnsInGrid();
    if (spec.getRowsInGrid() < 4 || spec.getColumnsInGrid() < 4) {
      throw new IllegalArgumentException(
              "Unable to perform B-Spline interpolation on grid smaller than 4x4");
    }

    dimension = spec.getDimension();

    geoCoordinates = spec.isGeographicCoordinateSystemSpecified();
    if (geoCoordinates) {
      geoWrapsLongitude = spec.doGeographicCoordinatesWrapLongitude();
      geoBracketsLongitude = spec.doGeographicCoordinatesBracketLongitude();
    } else {
      geoWrapsLongitude = false;
      geoBracketsLongitude = false;
    }
    // The standard-handling parameters specify the range 
    // in which the interpolation can be performed without any special checks
    // for bounds or, in the case of geographic coordinates, longitude wrapping
    // cases.  We assume this will be the majority of the cases, so always
    // check for them first.
    standardHandlingLeft = 1;
    standardHandlingRight = nColsInRaster - 3;
    if (geoBracketsLongitude) {
      nColsForWrap = nColsInRaster - 1;
    } else {
      nColsForWrap = nColsInRaster;
    }

  }

  /**
   * Interpolates a value for the specified x and y coordinates.
   *
   * @param x the x coordinate for interpolation
   * @param y the y coordinate for interpolation
   * @return if successful, a valid floating point number; otherwise, a NaN.
   * @throws java.io.IOException in the event of an IO error
   */
  public double z(double x, double y) throws IOException {
    double[] g;
    if (geoCoordinates) {
      g = g93.mapGeographicToGrid(y, x);
    } else {
      g = g93.mapCartesianToGrid(x, y);
    }
    double r = g[0];
    double c = g[1];

    return zInterp(r, c, 0);
  }

  /**
   * Interpolates a value at the indicated position and also computes the unit
   * surface normal vector. Intended for rendering purposes.
   * <p>
   * The result is stored in an array of 4 elements giving, in order, the z
   * coordinate, and the x, y, and z components of the normal vector.
   *
   * @param x the x coordinate for interpolation
   * @param y the y coordinate for interpolation
   * @return a valid array, if successful populated with floating point values;
   * otherwise, populated with NaN.
   * @throws IOException in the event of an IO error
   */
  public double[] zNormal(double x, double y) throws IOException {
    double[] result = new double[4];
    double[] g;
    if (geoCoordinates) {
      g = g93.mapGeographicToGrid(y, x);
    } else {
      g = g93.mapCartesianToGrid(x, y);
    }
    double row = g[0];
    double col = g[1];

    float z[] = loadSamples(row, col);
    if (z == null) {
      result[0] = Double.NaN;
      result[1] = Double.NaN;
      result[2] = Double.NaN;
      result[3] = Double.NaN;
      return result;
    }

    // compute basis weighting factors b(u) in direction of x axis
    double um1 = 1.0 - u;
    double bu0 = um1 * um1 * um1 / 6.0;
    double bu1 = (3 * u * u * (u - 2) + 4) / 6.0;
    double bu2 = (3 * u * (1 + u - u * u) + 1) / 6.0;
    double bu3 = u * u * u / 6.0;

    // combine sample points z[] using the basis weighting functions
    // and create four partial results, one for each row of data.
    double s0 = z[0] * bu0 + z[1] * bu1 + z[2] * bu2 + z[3] * bu3;
    double s1 = z[4] * bu0 + z[5] * bu1 + z[6] * bu2 + z[7] * bu3;
    double s2 = z[8] * bu0 + z[9] * bu1 + z[10] * bu2 + z[11] * bu3;
    double s3 = z[12] * bu0 + z[13] * bu1 + z[14] * bu2 + z[15] * bu3;

    // comnpute basis weighting factors b(v) in direction of y axis
    double vm1 = 1.0 - v;
    double bv0 = vm1 * vm1 * vm1 / 6.0;
    double bv1 = (3 * v * v * (v - 2) + 4) / 6.0;
    double bv2 = (3 * v * (1 + v - v * v) + 1) / 6.0;
    double bv3 = v * v * v / 6.0;

    // combine the 4 partial results, computing in the y direction
    result[0] = bv0 * s0 + bv1 * s1 + bv2 * s2 + bv3 * s3;

    // compute derivatives of basis functions b(u)
    double du0 = -um1 * um1 / 2.0;
    double du1 = (3.0 * u / 2.0 - 2.0) * u;
    double du2 = -(3.0 * u / 2.0 - 1.0) * u + 0.5;
    double du3 = u * u / 2.0;

    // combine sample points z[] using the derivatives of the basis weighting 
    // functions and create four partial results, one for each row of data.
    s0 = z[0] * du0 + z[1] * du1 + z[2] * du2 + z[3] * du3;
    s1 = z[4] * du0 + z[5] * du1 + z[6] * du2 + z[7] * du3;
    s2 = z[8] * du0 + z[9] * du1 + z[10] * du2 + z[11] * du3;
    s3 = z[12] * du0 + z[13] * du1 + z[14] * du2 + z[15] * du3;

    // combine the partial results to compute partial derivative dz/dx
    double dzdx = bv0 * s0 + bv1 * s1 + bv2 * s2 + bv3 * s3;

    double dv0 = -vm1 * vm1 / 2.0;
    double dv1 = (3.0 * v / 2.0 - 2.0) * v;
    double dv2 = -(3.0 * v / 2.0 - 1.0) * v + 0.5;
    double dv3 = v * v / 2.0;

    // combine sample points z[] using the derivatives of the basis weighting 
    // functions  and create four partial results, one for each COLUMN of data.
    s0 = z[0] * dv0 + z[4] * dv1 + z[8] * dv2 + z[12] * dv3;
    s1 = z[1] * dv0 + z[5] * dv1 + z[9] * dv2 + z[13] * dv3;
    s2 = z[2] * dv0 + z[6] * dv1 + z[10] * dv2 + z[14] * dv3;
    s3 = z[3] * dv0 + z[7] * dv1 + z[11] * dv2 + z[15] * dv3;

    double dzdy = bu0 * s0 + bu1 * s1 + bu2 * s2 + bu3 * s3;
    double d = Math.sqrt(1.0 + dzdx * dzdx + dzdy * dzdy);
    result[1] = -dzdx / d;
    result[2] = -dzdy / d;
    result[3] = 1 / d;

    return result;
  }

  private int blockLimit(int i, int n) {
    if (i < 0) {
      return 0;
    } else if (i > n - 3) {
      return n - 3;
    }
    return i;
  }

  private double zInterp(double row, double col, int index) throws IOException {
    float z[] = loadSamples(row, col);
    if (z == null) {
      return Double.NaN;
    }
    if (index > 0) {
      // for convenience, copy the data down to the start of the array
      System.arraycopy(z, index * 16, z, 0, 16);
    }

    double tm1 = 1.0 - u;
    double b0 = tm1 * tm1 * tm1 / 6.0;
    double b1 = (3 * u * u * (u - 2) + 4) / 6.0;
    double b2 = (3 * u * (1 + u - u * u) + 1) / 6.0;
    double b3 = u * u * u / 6.0;

    double s0 = z[0] * b0 + z[1] * b1 + z[2] * b2 + z[3] * b3;
    double s1 = z[4] * b0 + z[5] * b1 + z[6] * b2 + z[7] * b3;
    double s2 = z[8] * b0 + z[9] * b1 + z[10] * b2 + z[11] * b3;
    double s3 = z[12] * b0 + z[13] * b1 + z[14] * b2 + z[15] * b3;

    double sm1 = 1.0 - v;
    b0 = sm1 * sm1 * sm1 / 6.0;
    b1 = (3 * v * v * (v - 2) + 4) / 6.0;
    b2 = (3 * v * (1 + v - v * v) + 1) / 6.0;
    b3 = v * v * v / 6.0;

    return b0 * s0 + b1 * s1 + b2 * s2 + b3 * s3;
  }

  /**
   * A test method to perform a simple bi-linear interpolation. Used for
   * comparing results with the standard interpolation
   *
   * @param x the x coordinate of the interpolation point
   * @param y the y coordinate of the interpolation point
   * @param index a value in the range 0 to dimension-1, giving the index for the
   * data element to be retrieved.
   * @return if successful, a valid floating point value; otherwise, NaN
   * @throws IOException in the event of an IO error
   */
  public double zTest(double x, double y, int index) throws IOException {
    double[] g;
    if (geoCoordinates) {
      g = g93.mapGeographicToGrid(y, x);
    } else {
      g = g93.mapCartesianToGrid(x, y);
    }
    double row = g[0];
    double col = g[1];
    int row0 = (int) row;
    int col0 = (int) col;
    double ct = col - col0;  // cell t
    double cs = row - row0; // cell s

    int c0 = (col0 + nColsInRaster) % nColsInRaster;
    int c1 = (col0 + nColsInRaster + 1) % nColsInRaster;
    double z0 = g93.readValue(row0, c0);
    double z1 = g93.readValue(row0, c1);
    double z2 = g93.readValue(row0 + 1, c0);
    double z3 = g93.readValue(row0 + 1, c1);

    double y0 = (1 - ct) * z0 + ct * z1;
    double y1 = (1 - ct) * z2 + ct * z3;
    return (1 - cs) * y0 + cs * y1;
  }

  private float[] loadSamples(double row, double col) throws IOException {
    int iRow = (int) row;
    int iCol = (int) col;
    if (standardHandlingLeft <= iCol && iCol <= standardHandlingRight) {
      int col0 = iCol - 1;
      int row0 = blockLimit(iRow - 1, nRowsInRaster);
      u = col - col0 - 1; // x parameter
      v = row - row0 - 1; // y parameter

      return g93.readBlock(row0, col0, 4, 4);

    }

    if (geoWrapsLongitude) {
      return loadWrappingSamples(row, col, iRow, iCol);
    }
    // either it's a Cartesian coordinate system or a geographic system
    // with limited coverage
    if (iCol < 0 || iCol > nColsInRaster - 1) {
      return null;
    }
    if (row < 0 || row > nRowsInRaster - 1) {
      return null;
    }

    int col0 = blockLimit(iCol - 1, nColsInRaster);
    int row0 = blockLimit(iRow - 1, nRowsInRaster);
    u = col - col0 - 1; // x parameter
    v = row - row0 - 1; // y parameter

    return g93.readBlock(row0, col0, 4, 4);

  }

  private float[] loadWrappingSamples(double row, double col, int iRow, int iCol) throws IOException {

    // iCol will be in the range nColsInRaster-2 to nColsInRaster-1
    // or it will be equal to zero.
    int row0 = blockLimit(iRow - 1, nRowsInRaster);
    int col0;
    int n1, n2;
    float[] z1;
    float[] z2;
    if (iCol == 0) {
      col0 = nColsForWrap - 1;
      n1 = 1;
      n2 = 3;
      z1 = g93.readBlock(row0, col0, 4, n1);
      z2 = g93.readBlock(row0, 0, 4, n2);
    } else {
      col0 = iCol - 1;
      n1 = nColsForWrap - col0;
      n2 = 4 - n1;
      z1 = g93.readBlock(row0, col0, 4, n1);
      z2 = g93.readBlock(row0, 0, 4, n2);
    }
    float[] z = new float[16 * dimension];
    for (int iVariable = 0; iVariable < dimension; iVariable++) {
      int variableOffset = iVariable * 16;
      for (int i = 0; i < 4; i++) {
        System.arraycopy(z1, i * n1, z, variableOffset + i * 4, n1);
        System.arraycopy(z2, i * n2, z, variableOffset + i * 4 + n1, n2);
      }
    }

    u = col - iCol; // x parameter
    v = row - row0 - 1; // y parameter
    return z;
  }
}
