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
package org.gridfour.gvrs;

/**
 * Defines methods for specifying a point in a real-valued
 * Cartesian coordinate system.
 */
public interface IGvrsModelPoint {

  /**
   * Gets the real-valued X coordinate for this mode point
   *
   * @return a real-valued coordinate
   */
  double getX();

  /**
   * Gets the real-valued Y coordinate for this point in the
   * model coordinate system.
   *
   * @return a real-valued coordinate
   */
  double getY();
 
}
