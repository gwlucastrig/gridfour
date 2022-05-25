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
package org.gridfour.gvrs;

import org.gridfour.coordinates.GridPoint;
import java.io.IOException;
import org.gridfour.interpolation.InterpolationResult;
import org.gridfour.interpolation.InterpolationTarget;
import org.gridfour.interpolation.InterpolatorBSpline;

/**
 * Performs interpolations over a GVRS raster file using the classic B-Spline
 * algorithm. This class combines access routines for extracting sample
 * data from GVRS raster files with the general-purpose InterpolatorBSpline.java
 * class implemented as part of the Gridfour software library.
 * <p>
 * For geographic coordinates, logic is implemented to support cases where the
 * interpolated values span the coordinate-system division (usually at +/-180
 * degrees longitude)
 * <p>
 * This class is intended to be used for applications that may require millions
 * of calculations. Therefore, emphasis has been placed on speed.
 * <p>
 * The coordinate systems used for interpolation are described in the
 * Gridfour project wiki article
 * <a href="https://github.com/gwlucastrig/gridfour/wiki/Gridfour-Raster-Index-and-Coordinate-Systems">
 * Gridfour Raster Index and Coordinate Systems</a>. The article includes
 * a discussion of the concept of a "fringe" region at the periphery of
 * the interpolation domain.
 */
public class GvrsInterpolatorBSpline {

    private final GvrsFile gvrs;
    private final GvrsElement element;
    private final GvrsFileSpecification spec;
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
     * Constructs an instance that will operate over the specified GVRS File.
     *
     * @param element a valid GVRS element from a file opened for read access.
     * @throws java.io.IOException in the event of an I/O error
     */
    public GvrsInterpolatorBSpline(GvrsElement element) throws IOException {
        this.element = element;
        this.gvrs = element.gvrsFile;
        spec = gvrs.getSpecification();
        nRowsInRaster = spec.getRowsInGrid();
        nColsInRaster = spec.getColumnsInGrid();
        if (spec.getRowsInGrid() < 4 || spec.getColumnsInGrid() < 4) {
            throw new IllegalArgumentException(
                "Unable to perform B-Spline interpolation on grid smaller than 4x4");
        }

        dimension = spec.getNumberOfElements();

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
     * <p>
     * In the event that the coordinates are taken from a geographic
     * coordinate system, it is assumed that the x coordinate
     * gives a longitude and that the y coordinate gives a latitude value.
     * Thus the order of arguments for this method would be
     * <pre>
     * longitude, latitude
     * </pre>
     * This approach is a departure from that used in other parts
     * of the Gridfour API.
     *
     * @param x the x coordinate for interpolation
     * @param y the y coordinate for interpolation
     * @return if successful, a valid floating point number; otherwise, a NaN.
     * @throws java.io.IOException in the event of an IO error
     */
    public double z(double x, double y) throws IOException {
        GridPoint g;
        if (geoCoordinates) {
            g = gvrs.mapGeographicToGridPoint(y, x);
        } else {
            g = gvrs.mapModelToGridPoint(x, y);
        }
        double r = g.getRow();
        double c = g.getColumn();

        return zInterp(r, c, 0);
    }

    /**
     * Interpolates a value at the indicated position and also computes the unit
     * surface normal vector. Intended for rendering purposes.
     * <p>
     * In the event that the coordinates are taken from a geographic
     * coordinate system, it is assumed that the x coordinate
     * gives a longitude and that the y coordinate gives a latitude value.
     * Thus the order of arguments for this method would be
     * <pre>
     * longitude, latitude
     * </pre>
     * This approach is a departure from that used in other parts
     * of the Gridfour API
     * <p>
     * The result is stored in instance of the IterpolationResult class.
     * <p>
     * This method may throw an IllegalArgumentException if the specified
     * coordinates are out-of-bounds.
     *
     * @param x the x coordinate for interpolation
     * @param y the y coordinate for interpolation
     * @return An instance of InterpolationResult.
     * @throws IOException in the event of an IO error.
     */
    public InterpolationResult zNormal(double x, double y) throws IOException {
        GridPoint g;
        double dx = du;
        double dy = dv;
        if (geoCoordinates) {
            g = gvrs.mapGeographicToGridPoint(y, x);
            double s = Math.cos(Math.toRadians(y));
            dx = s * du;
            if (dx < 1) {
                dx = 1;
            }
        } else {
            g = gvrs.mapModelToGridPoint(x, y);
        }
        double row = g.getRow();
        double col = g.getColumn();

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
        } else if (i > n - 4) {
            return n - 4;
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
        GridPoint g;
        if (geoCoordinates) {
            g = gvrs.mapGeographicToGridPoint(y, x);
        } else {
            g = gvrs.mapModelToGridPoint(x, y);
        }
        double row = g.getRow();
        double col = g.getColumn();
        int row0 = (int) row;
        int col0 = (int) col;
        double ct = col - col0;  // cell t
        double cs = row - row0; // cell s

        int c0 = (col0 + nColsInRaster) % nColsInRaster;
        int c1 = (col0 + nColsInRaster + 1) % nColsInRaster;
        double z0 = element.readValue(row0, c0);
        double z1 = element.readValue(row0, c1);
        double z2 = element.readValue(row0 + 1, c0);
        double z3 = element.readValue(row0 + 1, c1);

        double y0 = (1 - ct) * z0 + ct * z1;
        double y1 = (1 - ct) * z2 + ct * z3;
        return (1 - cs) * y0 + cs * y1;
    }

    private float[] loadSamples(double pRow, double pCol) throws IOException {
      double row = pRow;
      double col = pCol;

      // check to see if the row falls within the valid range
      // if it falls outside the valid interpolation region but
      // within the limits of the "fringe area", we constrain its value.
      // otherwise, we cannot perform an interpolation.
      if(row<0){
        if(row<spec.rowFringe0){
          return null;
        }
        row =0;
      }else if(row>nRowsInRaster - 1){
        if(row>spec.rowFringe1){
          return null;
        }
        row = nRowsInRaster - 1;
      }

        int iRow = (int) Math.floor(row);
        int iCol = (int) Math.floor(col);
        // in many cases, the column falls well within the bounds
        // in which we can interpolate and no special checks are needed
        // to see if the column is in bounds.  Check for the simple case
        // with standard handling.
        if (standardHandlingLeft <= iCol && iCol <= standardHandlingRight) {
            int col0 = iCol - 1;
            int row0 = blockLimit(iRow - 1, nRowsInRaster);
            u = col - col0 - 1; // x parameter
            v = row - row0 - 1; // y parameter
            return element.readBlock(row0, col0, 4, 4);
        }

        // If we get here, the column values are out of the range
        // that can be used for a simple interpolation block.
        // If the coordinate system is geographic in nature, and the
        // data spans the entire range of longitude, it is possible
        // that we can pull up the appropriate columns by processing
        // the transition across ends of the range of longitudes.
        // This situation occurs commonly in cases where the query
        // coordinates are near the International Date Line.
        // But it can arise in other cases depending on how longitude
        // coordinates are mapped to columns.
        if (geoWrapsLongitude) {
            return loadWrappingSamples(row, col, iRow, iCol);
        }

        // If we get here, we are faced with either
        // a Cartesian coordinate system or a geographic system
        // with limited coverage.  In either case, the columnar coordinates
        // are not cyclic and no wrapping logic is applied.
        // We do perform the same fringe-related logic that was applied
        // to rows.
        if(col<spec.colFringe0 || col>spec.colFringe1){
          return null;
        }
        if(col<0){
          col = 0;
          iCol = 0;
        }else if(col>nColsInRaster-1){
          col = nColsInRaster - 1;
          iCol = nColsInRaster - 1;
        }

        int col0 = blockLimit(iCol - 1, nColsInRaster);
        int row0 = blockLimit(iRow - 1, nRowsInRaster);
        u = col - col0 - 1; // x parameter
        v = row - row0 - 1; // y parameter

        return element.readBlock(row0, col0, 4, 4);
    }

    private float[] loadWrappingSamples(double row, double col, int iRow, int iCol) throws IOException {

        // iCol is in the vacinity of the coordinate wrapping columns.
        // It will be in the range nColsInRaster-2 to nColsInRaster-1
        // or it will be equal to zero.
        int row0 = blockLimit(iRow - 1, nRowsInRaster);
        int col0;
        int n1, n2;
        float[] z1;
        float[] z2;
        // the column cooordinate should be larger than iCol
        // and less than iCol+1
        if (iCol <= 0) {
            col0 = nColsForWrap - 1 + iCol;
            n1 = nColsForWrap - col0;
            n2 = 4-n1;
            z1 = element.readBlock(row0, col0, 4, n1);
            z2 = element.readBlock(row0, 0, 4, n2);
        } else {
            col0 = iCol - 1;
            n1 = nColsForWrap - col0;
            n2 = 4 - n1;
            z1 = element.readBlock(row0, col0, 4, n1);
            z2 = element.readBlock(row0, 0, 4, n2);
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
