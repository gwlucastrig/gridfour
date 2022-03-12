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

import org.gridfour.coordinates.IGridPoint;

/**
 * Provides elements and methods specifying a point in the
 * grid coordinate system.
 */
public class GridPoint implements IGridPoint {

  final double row;
  final double column;
  final int iRow;
  final int iColumn;

  /**
   * Standard constructor.
   * <p>
   * The grid coordinates are a real-valued and potentially fractional
   * row and column value.
   *
   * @param row a finite, real-valued coordinate in the grid system
   * @param column a finite, real-valued coordinate in the grid system
   */
  public GridPoint (double row, double column) {
    this.row = row;
    this.column = column;
    this.iRow = (int) (row + 0.5);
    this.iColumn = (int) (column + 0.5);
  }

  /**
   * Get the row value associated with the grid point
   *
   * @return a potentially non-integral row value
   */
  @Override
  public double getRow() {
    return row;
  }

  /**
   * Get the column value associated with the grid point
   *
   * @return a potentially non-integral column value
   */
  @Override
  public double getColumn() {
    return column;
  }

  /**
   * Gets the row index associated with the grid point
   *
   * @return an integer index
   */
  @Override
  public int getRowInt() {
    return iRow;
  }

  /**
   * Gets the column index associated with the grid point
   *
   * @return an integer index
   */
  @Override
  public int getColumnInt() {
    return iColumn;
  }
}
