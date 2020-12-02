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
import org.gridfour.interpolation.InterpolationResult;
import org.gridfour.interpolation.InterpolationTarget;
import org.gridfour.interpolation.InterpolatorBSpline;

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

    private final InterpolatorBSpline bSpline;

    private double u;  // parameter for interpolation point, x-axis direction
    private double v;  // parameter for interpolation point, y-axis direction
    private double du;
    private double dv;

    /**
     * Radius of a sphere of same surface area as Earth
     */
    private static final double rEarth = 6371007.2;

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
            du = rEarth * Math.toRadians(spec.cellSizeX);
            dv = rEarth * Math.toRadians(spec.cellSizeY);
        } else {
            geoWrapsLongitude = false;
            geoBracketsLongitude = false;
            du = spec.cellSizeX;
            dv = spec.cellSizeY;
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

        bSpline = new InterpolatorBSpline();
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
     * @return a valid array, if successful populated with floating point
     * values;
     * otherwise, populated with NaN.
     * @throws IOException in the event of an IO error
     */
    public InterpolationResult zNormal(double x, double y) throws IOException {

        double[] g;

        double dx = du;
        double dy = dv;
        if (geoCoordinates) {
            g = g93.mapGeographicToGrid(y, x);
            double s = Math.cos(Math.toRadians(y));
            dx = s * du;
            if (dx < 1) {
                dx = 1;
            }
        } else {
            g = g93.mapCartesianToGrid(x, y);
        }
        double row = g[0];
        double col = g[1];

        float z[] = loadSamples(row, col);
        if (z == null) {
            InterpolationResult result = new InterpolationResult();
            result.nullify();
            return result;
        }

        return bSpline.interpolate(1.0 + v, 1.0 + u, 4, 4, z, dy, dx, InterpolationTarget.FirstDerivatives, null);
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

        return bSpline.interpolateValue(1.0 + v, 1.0 + u, 4, 4, z);

    }

    /**
     * A test method to perform a simple bi-linear interpolation. Used for
     * comparing results with the standard interpolation
     *
     * @param x the x coordinate of the interpolation point
     * @param y the y coordinate of the interpolation point
     * @param index a value in the range 0 to dimension-1, giving the index for
     * the
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
