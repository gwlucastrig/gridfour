/*
 * The MIT License
 *
 * Copyright 2021 G. W. Lucas.
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.gridfour.gvrs;

import java.io.IOException;

/**
 * GVRS Element implementations provide the access point for reading
 * from or writing to GVRS data stores. Elements are defined when
 * the initial specification for a GVRS file is created. When a GVRS
 * file is opened, one GvrsElement instance is created for each named
 * element defined in the specification. The method getElements()
 * in the GvrsFile class allows applications to obtain these instances and
 * use them to exchange data with the file.
 * <p>
 * This abstract class serves as the base for a set of derived classes
 * that are associated with each GVRS data type (Integer, Float, Short, and
 * Integer-coded Float).
 */
public abstract class GvrsElement {

  final String name;
  final GvrsElementType dataType;
  final String description;
  final String unitOfMeasure;

  final GvrsFile gvrsFile;
  final TileAccessIndices accessIndices;

  int tileIndex;
  TileElement tileElement;
  
  /**
   * Standard constructor used to populate base elements.
   * Since this class is only instantiated in the GVRS package, it
   * is assumed that the arguments will always be correctly specified.
   *
   * @param eSpec The element specification for this instance.
   * @param elementType The type of the element specified by instances.
   * @param file The GVRS file with which this element is associated.
   */
  GvrsElement(GvrsElementSpec eSpec, GvrsElementType elementType, GvrsFile file) {
    this.name = eSpec.name;
    this.dataType = elementType;
    this.description = eSpec.description;
    this.unitOfMeasure = eSpec.unitOfMeasure;
    this.gvrsFile = file;
    this.accessIndices = file.getAccessIndices();
    tileIndex = -1;  // e.g. no data loaded
  }

  /**
   * Gets the GVRS file associated with this element.
   *
   * @return a valid GVRS file instance
   */
  GvrsFile getFile() {
    return gvrsFile;
  }

  void setTileElement(int tileIndex, TileElement tileElement) {
    this.tileIndex = tileIndex;
    this.tileElement = tileElement;
  }

  /**
   * Gets the name associated with the element.
   *
   * @return a valid, non-empty string
   */
  public String getName() {
    return name;
  }

  /**
   * Get the data type associated with this element.
   *
   * @return a valid instance
   */
  public GvrsElementType getDataType() {
    return dataType;
  }

  /**
   * Gets the arbitrary description string. Intended to allow applications to
   * provide documentation for elements.
   *
   * @return a valid non-empty string, or a null if no description
   * is to be supplied.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the arbitrary unit of measure string. Intended to allow applications
   * to provide documentation for elements.
   *
   * @return a valid non-empty string, or a null if no description
   * is to be supplied.
   */
  public String getUnitOfMeasure() {
    return unitOfMeasure;
  }

  /**
   * Read an integer value from the GvrsFile. If no data exists for the
   * specified row and column, the fill value will be returned.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @return an integer value.
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  abstract public int readValueInt(int row, int column) throws IOException;

  /**
   * Write an integer value in the GVRS raster file. Because write operations
   * are buffered, this data may be retained in memory for some time before
   * actually being written to the file. However, any data lingering
   * in memory will be recorded when the flush() or close() methods are called.
   * <p>
   * The value GvrsFileConstants.NULL_DATA_CODE is reserved for the
   * representation of null data.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @param value an integer value.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  abstract public void writeValueInt(int row, int column, int value) throws IOException;

  /**
   * Reads a floating-point value from the GVRS raster file. If no data exists
   * for the specified row and column, the fill value will be returned.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @return an floating-point value or the fill value if there is no data
   * stored at the specified row and column
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  abstract public float readValue(int row, int column) throws IOException;

  /**
   * Write a floating-point value to the GVRS raster file. Because write
   * operations are buffered, this data may be retained in memory for some
   * time before actually being written to the file. However, any data lingering
   * in memory will be recorded when the flush() or close() methods are called.
   * <p>
   * The value Float.NaN is reserved for the representation of null data.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param col a positive value in the range defined by the file
   * specifications.
   * @param value an floating-point value.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  abstract public void writeValue(int row, int col, float value) throws IOException;

    /**
   * Reads a block (sub-grid) of integer values from the GVRS file based
   * on the grid row, column, and block-size specifications. If successful,
   * the return value from this method is an array giving a sub-grid of values
   * in row-major order. The index into the array for a particular
   * row and column within the sub-grid would be:
   * <pre>
   *   index = row * nColumns + column
   *   where rows and columns are all numbered starting at zero.
   * </pre>
   * Accessing data in a block is often more efficient that accessing data
   * one grid-value-at-a-time.
   *
   * @param row the grid row index for the starting row of the block
   * @param column the grid column index for the starting column of the block
   * @param nRows the number of rows in the block to be retrieved
   * @param nColumns the number of columns in the block to be retrieved
   * @return if successful, a valid array of size nRow*nColumn in row-major
   * order.
   * @throws IOException in the event of an I/O error.
   */
  public int[] readBlockInt(int row, int column, int nRows, int nColumns)
    throws IOException {

    // The indexing used here is a little complicated. To keep it managable,
    // this code adheres to a variable naming convention defined as follows:
    //   variables starting with
    //      t  means tile coordinates;  tr, tc are the row and column
    //            within a tile.
    //      b  block (result) coordinates; br, bc are the row and column
    //            within the result block.
    //      g  grid (main raster) coordinates; gr, gc  grid row and column
    //
    //   tr will always be in the range 0 <= tr < spec.nRowsInTile
    //   tc will always be in the range 0 <= tc < spec.nColsInTile.
    //   tr0 is the first row of interest in the tile.
    //   tr1 is the last row of interest in the tile.
    //   similar for gr, gc and br, bc.
    //       When the variable name is prefixed with a letter, it means that
    //   it gives the equivalent position in the indicated system.
    //   for example  gbr0  is the grid (g) coordinate for the first
    //   row of interest in the result block, etc.  Note that the gtr0, gtr1,
    //   etc. values change depending on which tile is currently being read
    //   and it's relationship to the overall grid.
    //       Special names are used for tileRow0, tileCol0, tileRow1, tileCol0
    //   the are the row number and column numbers for the tiles.
    //   For example to find the row of tiles associated with
    //   a particular grid coordinate:  tileRow0 = gr0/spec.nRowsInTile, etc.
    if (gvrsFile.isClosed()) {
      throw new IOException("Raster file is closed");
    }
    if (nRows < 1 || nColumns < 1) {
      throw new IOException(
        "Invalid dimensions: nRows=" + nRows + ", nColumns=" + nColumns);
    }
    // bounds checking for resulting grid row and column computations
    // are performed in the tileAccessIndices.computeAccessIndices() method
    // which will throw an exception if bounds are violated.
    int nValuesInSubBlock = nRows * nColumns;
    int[] block = new int[nValuesInSubBlock];
    int gr0 = row;
    int gc0 = column;
    int gr1 = row + nRows - 1;
    int gc1 = column + nColumns - 1;
    accessIndices.computeAccessIndices(gr0, gc0);
    int tileRow0 = accessIndices.tileRow;
    int tileCol0 = accessIndices.tileCol;
    accessIndices.computeAccessIndices(gr1, gc1);
    int tileRow1 = accessIndices.tileRow;
    int tileCol1 = accessIndices.tileCol;

    for (int tileRow = tileRow0; tileRow <= tileRow1; tileRow++) {
      // find the tile row limits tr0 and tr1 for this row of tiles.
      // because the tiles in this row may extend beyond the requested
      // range of grid rows, we need to enforce limits.
      int gtRowOffset = tileRow * accessIndices.nRowsInTile;
      int gtr0 = gtRowOffset;
      int gtr1 = gtRowOffset + accessIndices.nRowsInTile - 1;
      // enforce limits
      if (gtr0 < gr0) {
        gtr0 = gr0;
      }
      if (gtr1 > gr1) {
        gtr1 = gr1;
      }
      int tr0 = gtr0 - gtRowOffset; // must be in range 0 to spec.nRowsInTile.
      int tr1 = gtr1 - gtRowOffset; //    ""        ""          ""
      for (int tileCol = tileCol0; tileCol <= tileCol1; tileCol++) {
        int gtColOffset = tileCol * accessIndices.nColsInTile;
        int gtc0 = gtColOffset;
        int gtc1 = gtColOffset + accessIndices.nColsInTile - 1;
        // enforce limits
        if (gtc0 < gc0) {
          gtc0 = gc0;
        }
        if (gtc1 > gc1) {
          gtc1 = gc1;
        }
        int tc0 = gtc0 - gtColOffset;
        int tc1 = gtc1 - gtColOffset;

        int targetTileIndex = tileRow * accessIndices.nColsOfTiles + tileCol;
        if (gvrsFile.loadTile(targetTileIndex, false)) {
          for (int tr = tr0; tr <= tr1; tr++) {
            int br = tr + gtRowOffset - gr0;
            int bc = tc0 + gtColOffset - gc0;
            int bIndex = br * nColumns + bc;
            int tIndex = tr * accessIndices.nColsInTile;
            for (int tc = tc0; tc <= tc1; tc++) {
              block[bIndex] = tileElement.getValueInt(tIndex + tc);
              bIndex++;
            }
          }
        } else {
          for (int tr = tr0; tr <= tr1; tr++) {
            int br = tr + gtRowOffset - gr0;
            int bc = tc0 + gtColOffset - gc0;
            int bIndex = br * nColumns + bc;
            for (int tc = tc0; tc <= tc1; tc++) {
              block[bIndex] = tileElement.getFillValueInt();
              bIndex++;
            }
          }
        }
      }
    }
    return block;
  }

  /**
   * Reads a block (sub-grid) of floating-point values from the GVRS file based
   * on the grid row, column, and block-size specifications. If successful,
   * the return value from this method is an array giving a sub-grid of values
   * in row-major order. The index into the array for a particular
   * row and column within the sub-grid would be:
   * <pre>
   *   index = row * nColumns + column
   *   where rows and columns are all numbered starting at zero.
   * </pre>
   * Accessing data in a block is often more efficient that accessing data
   * one grid-value-at-a-time.
   *
   * @param row the grid row index for the starting row of the block
   * @param column the grid column index for the starting column of the block
   * @param nRows the number of rows in the block to be retrieved
   * @param nColumns the number of columns in the block to be retrieved
   * @return if successful, a valid array of size nRow*nColumn in row-major
   * order.
   * @throws IOException in the event of an I/O error.
   */
  public float[] readBlock(int row, int column, int nRows, int nColumns)
    throws IOException {

    // The indexing used here is a little complicated. To keep it managable,
    // this code adheres to a variable naming convention defined as follows:
    //   variables starting with
    //      t  means tile coordinates;  tr, tc are the row and column
    //            within a tile.
    //      b  block (result) coordinates; br, bc are the row and column
    //            within the result block.
    //      g  grid (main raster) coordinates; gr, gc  grid row and column
    //
    //   tr will always be in the range 0 <= tr < spec.nRowsInTile
    //   tc will always be in the range 0 <= tc < spec.nColsInTile.
    //   tr0 is the first row of interest in the tile.
    //   tr1 is the last row of interest in the tile.
    //   similar for gr, gc and br, bc.
    //       When the variable name is prefixed with a letter, it means that
    //   it gives the equivalent position in the indicated system.
    //   for example  gbr0  is the grid (g) coordinate for the first
    //   row of interest in the result block, etc.  Note that the gtr0, gtr1,
    //   etc. values change depending on which tile is currently being read
    //   and it's relationship to the overall grid.
    //       Special names are used for tileRow0, tileCol0, tileRow1, tileCol0
    //   the are the row number and column numbers for the tiles.
    //   For example to find the row of tiles associated with
    //   a particular grid coordinate:  tileRow0 = gr0/spec.nRowsInTile, etc.
    if (gvrsFile.isClosed()) {
      throw new IOException("Raster file is closed");
    }
    if (nRows < 1 || nColumns < 1) {
      throw new IOException(
        "Invalid dimensions: nRows=" + nRows + ", nColumns=" + nColumns);
    }
    // bounds checking for resulting grid row and column computations
    // are performed in the tileAccessIndices.computeAccessIndices() method
    // which will throw an exception if bounds are violated.
    int nValuesInSubBlock = nRows * nColumns;
    float[] block = new float[nValuesInSubBlock];
    int gr0 = row;
    int gc0 = column;
    int gr1 = row + nRows - 1;
    int gc1 = column + nColumns - 1;
    accessIndices.computeAccessIndices(gr0, gc0);
    int tileRow0 = accessIndices.tileRow;
    int tileCol0 = accessIndices.tileCol;
    accessIndices.computeAccessIndices(gr1, gc1);
    int tileRow1 = accessIndices.tileRow;
    int tileCol1 = accessIndices.tileCol;

    for (int tileRow = tileRow0; tileRow <= tileRow1; tileRow++) {
      // find the tile row limits tr0 and tr1 for this row of tiles.
      // because the tiles in this row may extend beyond the requested
      // range of grid rows, we need to enforce limits.
      int gtRowOffset = tileRow * accessIndices.nRowsInTile;
      int gtr0 = gtRowOffset;
      int gtr1 = gtRowOffset + accessIndices.nRowsInTile - 1;
      // enforce limits
      if (gtr0 < gr0) {
        gtr0 = gr0;
      }
      if (gtr1 > gr1) {
        gtr1 = gr1;
      }
      int tr0 = gtr0 - gtRowOffset; // must be in range 0 to spec.nRowsInTile.
      int tr1 = gtr1 - gtRowOffset; //    ""        ""          ""
      for (int tileCol = tileCol0; tileCol <= tileCol1; tileCol++) {
        int gtColOffset = tileCol * accessIndices.nColsInTile;
        int gtc0 = gtColOffset;
        int gtc1 = gtColOffset + accessIndices.nColsInTile - 1;
        // enforce limits
        if (gtc0 < gc0) {
          gtc0 = gc0;
        }
        if (gtc1 > gc1) {
          gtc1 = gc1;
        }
        int tc0 = gtc0 - gtColOffset;
        int tc1 = gtc1 - gtColOffset;

        int targetTileIndex = tileRow * accessIndices.nColsOfTiles + tileCol;
        if (gvrsFile.loadTile(targetTileIndex, false)) {
          for (int tr = tr0; tr <= tr1; tr++) {
            int br = tr + gtRowOffset - gr0;
            int bc = tc0 + gtColOffset - gc0;
            int bIndex = br * nColumns + bc;
            int tIndex = tr * accessIndices.nColsInTile;
            for (int tc = tc0; tc <= tc1; tc++) {
              block[bIndex] = tileElement.getValue(tIndex + tc);
              bIndex++;
            }
          }
        } else {
          for (int tr = tr0; tr <= tr1; tr++) {
            int br = tr + gtRowOffset - gr0;
            int bc = tc0 + gtColOffset - gc0;
            int bIndex = br * nColumns + bc;
            for (int tc = tc0; tc <= tc1; tc++) {
              block[bIndex] = tileElement.getFillValue();
              bIndex++;
            }
          }
        }
      }
    }
    return block;
  }


  
}
