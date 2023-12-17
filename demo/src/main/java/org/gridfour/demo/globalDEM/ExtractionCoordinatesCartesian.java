/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2023 Gary W. Lucas.
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
 * OUT OF OR IN CONNECTI
 * ---------------------------------------------------------------------
 */
 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 09/2023  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.globalDEM;

import java.io.IOException;
import java.io.PrintStream;
import org.gridfour.gvrs.GvrsFileSpecification;
import ucar.ma2.Array;
import ucar.nc2.Variable;

/**
 * Provides utilities for extracting coordinate data from the NetCDF-format
 * files for ETOPO1 and GEBCO_2019 global-scale elevation and bathymetry data
 * sets.
 */
class ExtractionCoordinatesCartesian  implements IExtractionCoordinates {

    String yVariableName;
    double yRowMin;
    double yRowMax;
    double ySpacing;

    String xVariableName;
    double xSpacing;
    double xColMin;
    double xColMax;

    final private double[] cY;
    final private double[] cX;

    ExtractionCoordinatesCartesian(Variable x, Variable y) throws IOException {

        //
        // NetCDF uses an concept named "rank" to define the rank of a
        // Variable.  A variable of rank 1 is essentially an array.  A variable
        // of rank 2 has rows and columns, and is essentially a matrix.
        // Higher rank variables are not uncommon.
        // In ETOPO1 and GEBCO_2019, the horizontal coordinate variables have rank of 1.
        //
        // The "shape" element tells you the number of values in the variable.
        // The getShape() method returns an array of integers dimensioned to
        // the rank.  Since the horizontal coordinate variables are of rank
        // one, the getShape() method will return one-dimension integer arrays
        // for both varibles.  And shape[0] tells us how many values there
        // are in those arrays.
        //     For example, ETOPO1 has 1 minute spacing.  There are 60 minutes
        // in a degree, and 180 degrees from south to north (90 degrees in
        // both hemispheres).  So the shape[0] returned for the latitude
        // variable from ETOPO1 will be 180*60 = 10800.   The longitude
        // covers 360 degrees, so the shape[0] returned from longitude will
        // be 360*60 = 21600.
        yVariableName = y.getShortName();
        int[] shape = y.getShape();
        int n = shape[0]; // will be the number of rows in the NetCDF file
        Array array = y.read();
        yRowMin = array.getDouble(0);
        yRowMax = array.getDouble(n - 1);
        // the spacing is just the difference between two adjacent rows.
        ySpacing = array.getDouble(1) - yRowMin;
        cY = new double[n];
        double[] dLat = new double[n];
        for (int i = 0; i < n; i++) {
            cY[i] = array.getDouble(i);
            if (i > 0) {
                dLat[i] = cY[i] - cY[i - 1];
            }
        }

        xVariableName = x.getShortName();
        shape = x.getShape();
        n = shape[0]; // will be the number of columns in the NetCDF file.
        array = x.read();
        xColMin = array.getDouble(0);
        xColMax = array.getDouble(n - 1);
        // the spacing is just the difference between two adjacent columns
        xSpacing = array.getDouble(1) - xColMin;
        cX = new double[n];
        for (int i = 0; i < n; i++) {
            cX[i] = array.getDouble(i);
        }
    }

    /**
     * Print a simple summary of the x and y coordinates
     *
     * @param ps a valid print stream.
     */
    @Override
    public void summarizeCoordinates(PrintStream ps) {
        coord(ps, "x coordinates: ", xVariableName, xColMin, xColMax, xSpacing);
        coord(ps, "y coordinates: ", yVariableName, yRowMin, yRowMax, ySpacing);

    }

    private void coord(
      PrintStream ps,
      String label,
      String variableName,
      double vMin, double vMax, double vSpacing) {

        String s = label + " (" + variableName + ")";
        ps.format(
          "%-18s: %12.6f to %12.6f --- Spacing %8.6f%n",
          s, vMin, vMax, vSpacing);
    }

    /**
     * Gets the coordinate bounds defined by the x/y variables. The results
     * are stored in an array giving (in order) x and y coordinates for the
     * first row and column in the grid followed by x and y coordinates
     * for the late row and column
     *
     * @param bounds a valid array of length 4.
     */
    double[] getCartesianCoordinateBounds() {
        double[] bounds = new double[4];
        bounds[0] = this.xColMin;
        bounds[1] = this.yRowMin;
        bounds[2] = this.xColMax;
        bounds[3] = this.yRowMax;
        return bounds;
    }


    @Override
    public double getAreaOfEachCellInRow(int iRow) {
        return Math.abs(xSpacing*ySpacing);
    }

    @Override
    public double[] getCoordinateBounds() {
         return getCartesianCoordinateBounds();
    }

    @Override
    public boolean isGeographicCoordinateSystem() {
        return false;
    }

    @Override
    public void checkSpecificationTransform(PrintStream ps, GvrsFileSpecification spec) {
        // no check logic implemented at this time.
    }

}
