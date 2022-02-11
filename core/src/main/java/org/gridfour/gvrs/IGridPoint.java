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
 * Defines methods for specifying a point in the
 * grid coordinate system.
 */
public interface IGridPoint {

  /**
   * Get the column value associated with the grid point
   *
   * @return a potentially non-integral column value
   */
  double getColumn();

  /**
   * Gets the column index associated with the grid point
   *
   * @return an integer index
   */
  int getIntegerColumn();

  /**
   * Gets the row index associated with the grid point
   *
   * @return an integer index
   */
  int getIntegerRow();

  /**
   * Get the row value associated with the grid point
   *
   * @return a potentially non-integral row value
   */
  double getRow();
 
}
