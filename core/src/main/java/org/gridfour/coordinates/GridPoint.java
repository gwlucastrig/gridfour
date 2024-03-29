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
  public GridPoint(double row, double column) {
    this.row = row;
    this.column = column;
    this.iRow = (int) Math.floor(row + 0.5);
    this.iColumn = (int) Math.floor(column + 0.5);
  }

  /**
   * Standard constructor.
   * <p>
   * The grid coordinates are a real-valued and potentially fractional
   * row and column value.
   *
   * @param row a finite, real-valued coordinate in the grid system
   * @param column a finite, real-valued coordinate in the grid system
   * @param iRow an integer value, usually derived from the row value
   * @param iColumn an integer value, usual derived from the column value
   */
  public GridPoint(double row, double column, int iRow, int iColumn) {
    this.row = row;
    this.column = column;
    this.iRow = iRow;
    this.iColumn = iColumn;
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

  /**
   * Returns the floating point value associated with the columnar position
   * of the grid point. This value is provided as a convenience for developers
   * who find it useful to specify coordinates in terms of (x, y)
   * rather than row and column.
   *
   * @return a potentially non-integral floating-point value.
   */
  public double getX() {
    return column;
  }

  /**
   * Returns the floating point value associated with the row position
   * of the grid point. This value is provided as a convenience for developers
   * who find it useful to specify coordinates in terms of (x, y)
   * rather than row and column.
   *
   * @return a potentially non-integral floating-point value.
   */
  public double getY() {
    return row;
  }

  @Override
  public String toString() {
    return String.format("GridPoint row,col: (%f,%f)  (%d,%d)",
      row, column, iRow, iColumn);
  }
}
