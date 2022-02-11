/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2022  Gary W. Lucas.
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
 * 02/2022  G. Lucas     Created  
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

/**
 * Defines methods for specifying a point in geographic coordinates.
 * <p>
 * <b>About Datums</b>
 * <p>
 * At this time, this interface makes no provision for specifying datum.
 * It is assumed that all coordinates are given in a consistent datum and
 * that the management of datums is left to the application.
 */
public interface IGvrsGeoPoint {

  /**
   * Gets the latitude stored in the GeoPoint.
   *
   * @return a valid latitude, in degrees.
   */
  double getLatitude();

  /**
   * Gets the longitude stored in the GeoPoint.
   *
   * @return a valid longitude, in degrees.
   */
  double getLongitude();
 
}
