/* --------------------------------------------------------------------
 * Copyright (C) 2022  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.coordinates;

import org.gridfour.coordinates.IGeoPoint;

/**
 * Defines methods and elements for specifying a point in geographic
 * coordinates.
 * <p>
 * <b>About Datums</b>
 * <p>
 * At this time, this interface makes no provision for specifying datum.
 * It is assumed that all coordinates are given in a consistent datum and
 * that the management of datums is left to the application.
 */
public class GeoPoint implements IGeoPoint {

  /**
   * A latitude value, in degrees.
   */
  final double latitude;

  /**
   * A longitude value, in degrees.
   */
  final double longitude;
  
  /**
   * Standard constructor.
   * @param latitude a coordinate in degrees
   * @param longitude a coordinate in degrees
   */
  public GeoPoint(double latitude, double longitude){
    this.latitude = latitude;
    this.longitude = longitude;
  }

  @Override
  public double getLatitude() {
    return latitude;
  }

  @Override
  public double getLongitude() {
    return longitude;
  }


}
