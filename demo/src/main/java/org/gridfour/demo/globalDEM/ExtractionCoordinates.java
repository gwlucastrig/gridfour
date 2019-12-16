/* --------------------------------------------------------------------
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
 * OUT OF OR IN CONNECTI
 * ---------------------------------------------------------------------
 */
 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 09/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.globalDEM;

import java.io.IOException;
import java.io.PrintStream;
import ucar.ma2.Array;
import ucar.nc2.Variable;

/**
 * Provides utilities for extracting coordinate data from the NetCDF-format
 * files for ETOPO1 and GEBCO_2019 global-scale elevation and bathymetry data
 * sets.
 */
class ExtractionCoordinates {

  // Specify the radius of the earth in kilometers.  Because
  // this constant will be used for surface area computations,
  // use the "Authalic Radius", which is the radius of a sphere with equal
  // area to the Earth ellipsoid.
  private static final double rEarth = 6371.0072;
  private static final double rEarth2 = rEarth * rEarth;

  // specify the Earth radius at the equator (the semi-major axis).
  private static final double rEquatorial = 6378.1370;

  String latVariableName;
  double latRowMin;
  double latRowMax;
  double latSpacingDeg;

  String lonVariableName;
  double lonSpacingDeg;
  double lonColMin;
  double lonColMax;

  ExtractionCoordinates(Variable latitude, Variable longitude) throws IOException {
    // both the latitude and longitude Variable instances should
    // define their respective coordinates as an one-dimensional array. In both
    // GEBCO_2019 and ETOP01, the angular spacing for these coordinates
    // is uniform.  There is no guarantee that other data sources will
    // use the same convention. But for these products, the uniform spacing
    // simplifies things considerably.
    //
    // NetCDF uses an concept named "rank" to define the rank of a
    // Variable.  A variable of rank 1 is essentially an array.  A variable
    // of rank 2 has rows and columns, and is essentially a matrix.
    // Higher rank variables are not uncommon.
    // In ETOPO1 and GEBCO_2019, the latitude/longitude variables have rank of 1.
    // 
    // The "shape" element tells you the number of values in the variable.
    // The getShape() method returns an array of integers dimensioned to
    // the rank.  Since the latitude and longitude variables are of rank
    // one, the getShape() method will return one-dimension integer arrays
    // for both varibles.  And shape[0] tells us how many values there
    // are in those arrays.  
    //     For example, ETOPO1 has 1 minute spacing.  There are 60 minutes
    // in a degree, and 180 degrees from south to north (90 degrees in
    // both hemispheres).  So the shape[0] returned for the latitude
    // variable from ETOPO1 will be 180*60 = 10800.   The longitude
    // covers 360 degrees, so the shape[0] returned from longitude will
    // be 360*60 = 21600.

    latVariableName = latitude.getShortName();
    int[] shape = latitude.getShape();
    int n = shape[0]; // will be the number of rows in the NetCDF file
    Array array = latitude.read();
    latRowMin = array.getDouble(0);
    latRowMax = array.getDouble(n - 1);
    // the spacing is just the difference between two adjacent rows.
    latSpacingDeg = array.getDouble(1) - latRowMin;

    lonVariableName = longitude.getShortName();
    shape = longitude.getShape();
    n = shape[0]; // will be the number of columns in the NetCDF file.
    array = longitude.read();
    lonColMin = array.getDouble(0);
    lonColMax = array.getDouble(n - 1);
    // the spacing is just the difference between two adjacent columns
    lonSpacingDeg = array.getDouble(1) - lonColMin;
  }

  /**
   * Get the surface area of a single cell in the row. All cells in the row have
   * the same area. The area of the cells in each row varies as a function of
   * latitude due to the convergence of the meridians (longitude lines). Both
   * ETOPO1 and GEBCO_2019 give the rows from south to north, so this
   * calculation would yield a positive number, but this code takes the absolute
   * value in case of future changes.
   *
   * @param iRow the row of interest
   * @return the surface area of each cell in the row, in square meters.
   */
  double getAreaOfEachCellInRow(int iRow) {
    double lat1 = latRowMin + iRow * latSpacingDeg;
    double lat0 = latRowMin + (iRow + 1) * latSpacingDeg;
    double phi1 = Math.toRadians(lat1);
    double phi0 = Math.toRadians(lat0);
    double cos1 = Math.cos(Math.PI / 2 - phi1);
    double cos0 = Math.cos(Math.PI / 2 - phi0);
    double a = rEarth2 * Math.toRadians(lonSpacingDeg) * (cos1 - cos0);
    return Math.abs(a);
  }

  /**
   * Print a simple summary of the latitude and longitude coordinates
   *
   * @param ps a valid print stream.
   */
  void summarizeCoordinates(PrintStream ps) {
    coord(ps, "latitude", latVariableName, latRowMin, latRowMax, latSpacingDeg);
    coord(ps, "longitude", lonVariableName, lonColMin, lonColMax, lonSpacingDeg);
  }

  private void coord(
          PrintStream ps,
          String label,
          String variableName,
          double vMin, double vMax, double vSpacing) {

    String s = label + " (" + variableName + ")";
    double m = rEquatorial * Math.toRadians(vSpacing);
    ps.format(
            "%-18s: %12.6f to %12.6f --- "
            + "Spacing %5.3f minutes or %5.3f seconds"
            + ", %6.3f km at the equator%n",
            s, vMin, vMax, vSpacing * 60, vSpacing * 3600, m);
  }
  
  
  /**
   * Gets the coordinate bounds defined by the lat/lon variables. The results
   * are stored in an array giving (in order) latitude for the first row,
   * longitude for the first column, latitude for last row, and the longitude
   * for the last column.
   *
   * @param bounds a valid array of length 4.
   */
  double[] getGeographicCoordinateBounds() {
    double[] bounds = new double[4];
    bounds[0] = this.latRowMin;
    bounds[1] = this.lonColMin;
    bounds[2] = this.latRowMax;
    bounds[3] = this.lonColMax;
    return bounds;
  }

}
