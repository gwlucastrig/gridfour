/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2023  Gary W. Lucas.
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
 * 09/2023  G. Lucas     Created from ExtractionCoordinates
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.globalDEM;

import java.io.PrintStream;
import org.gridfour.gvrs.GvrsFileSpecification;

/**
 * Provides utilities for extracting coordinate data from the NetCDF-format
 * files for ETOPO1 and GEBCO_2019 global-scale elevation and bathymetry data
 * sets.
 */
interface IExtractionCoordinates {


  /**
   * Get the surface area of a single cell in the row. All cells in the row have
   * the same area. The area of the cells in each row varies as a function of
   * latitude due to the convergence of the meridians (longitude lines).
   *<p>
   * For geographic coordinates, the area value is always given in square
   * meters. For Cartesian coordinates, the area value is always given in
   * the square units defined by the data product (developers are strongly
   * encouraged to use meters, unless there is a specific reason to use
   * some other unit of measure).
   * @param iRow the row of interest, some implementations may ignore this value
   * @return a positive value giving the surface area of each cell in the row.
   */
  double getAreaOfEachCellInRow(int iRow) ;

  void summarizeCoordinates(PrintStream ps);

  /**
   * Gets the coordinate bounds defined by the coordinate system and grid
   * dimensions associated with an instance. The results
   * are stored in an array giving (in order) coordinates for the first row
   * and column and coordinates for the last row and column. Thus it is
   * possible that coordinate values may be either increasing or decreasing
   * across rows and columns. Geographic coordinates
   * are given as latitude and longitude (in that order), Cartesian coordinates
   * as x and y.
   *
   */
  double[] getCoordinateBounds() ;


  /**
   * Indicates whether the coordinates associated with the instance are
   * geographic coordinates.
   * @return true if the coordinate system is geographic, or false
   * if the coordinate system is Cartesian.
   */
  boolean isGeographicCoordinateSystem();

  /**
   * Checks the specified coordinate system with the grid specification
   * to ensure consistency.
   * @param ps a valid print stream for recording status
   * @param spec a valid specification
   */
  void checkSpecificationTransform(PrintStream ps, GvrsFileSpecification spec);
}
