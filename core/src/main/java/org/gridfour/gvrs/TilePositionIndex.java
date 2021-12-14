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
 * 12/2021  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

import java.io.IOException;
import org.gridfour.io.BufferedRandomAccessFile;

/**
 * Provides an index configured to store file positions of up to
 * 32 gigabytes (34,359,738,368 bytes).
 * <p>
 * Internally, the file positions are stored in a 4-byte integer array.
 * This class makes the assumption that the position is always a multiple
 * of either and always a positive long integer. Long integers are converted
 * to 4-byte integers by dividing them by 8 when they are stored and multiplying
 * them by 8 when they are retrieved.
 * <p>
 * Note that it for files larger than 16 gigabytes, the internal values
 * for the tile positions may be expressed as negative numbers. WHen these
 * values are cast to longs, they are converted to positive values
 * by applying a bit mask when.
 */
class TilePositionIndex{

  private int[][] offsets;
  private int row0;
  private int col0;
  private int row1;
  private int col1;
  private int nRows;
  private int nCols;
  private final int nRowsOfTiles;
  private final int nColsOfTiles;

  /**
   * Creates an empty tile index with potential dimensions
   * of the specifications.
   *
   * @param spec a valid instance
   */
  TilePositionIndex(GvrsFileSpecification spec) {
    this.nRowsOfTiles = spec.nRowsOfTiles;
    this.nColsOfTiles = spec.nColsOfTiles;
    //  this.preAllocate = preAllocate;
    //  if (preAllocate) {
    //    offsets = new int[nRowsOfTiles][nColsOfTiles];
    //    row1 = nRowsOfTiles - 1;
    //    col1 = nColsOfTiles - 1;
    //    nRows = nRowsOfTiles;
    //    nCols = nColsOfTiles;
    //  }
  }

  /**
   * Store the specified offset at the position given by the
   * tile row and column. If necessary, the index is resized
   * to accommodate the specified coordinates.
   * <p>
   * File positions must be positive values and a multiple of eight.
   *
   * @param index the index of the tile to be stored.
   * @param offset the file position, in bytes.
   */
  void setFilePosition(int tileIndex, long offset) {
    int row = tileIndex / nColsOfTiles;
    int col = tileIndex - row * nColsOfTiles;

    // to simplify re-allocation (if any), we process the
    // columns first.
    if (nCols == 0) {
      // first call, nRows is also zero
      nCols = 1;
      nRows = 1;
      col0 = col;
      col1 = col;
      row0 = row;
      row1 = row;
      offsets = new int[1][1];
      offsets[0][0] = (int) (offset / 8L); // may be negative
      return;
    }

    if (col < col0) {
      int nAdded = col0 - col;
      int n = nCols + nAdded;
      for (int i = 0; i < nRows; i++) {
        int[] x = new int[n];
        System.arraycopy(offsets[i], 0, x, nAdded, nCols);
        offsets[i] = x;
      }
      nCols = n;
      col0 = col;
    } else if (col > col1) {
      int nAdded = col - col1;
      int n = nCols + nAdded;
      for (int i = 0; i < nRows; i++) {
        int[] x = new int[n];
        System.arraycopy(offsets[i], 0, x, 0, nCols);
        offsets[i] = x;
      }
      nCols = n;
      col1 = col;
    }

    if (row < row0) {
      int nAdded = row0 - row;
      int n = nRows + nAdded;
      int[][] x = new int[n][];
      System.arraycopy(offsets, 0, x, nAdded, nRows);
      offsets = x;
      for (int i = 0; i < nAdded; i++) {
        offsets[i] = new int[nCols];
      }
      nRows = n;
      row0 = row;
    } else if (row > row1) {
      int nAdded = row - row1;
      int n = nRows + nAdded;
      int[][] x = new int[n][];
      System.arraycopy(offsets, 0, x, 0, nRows);
      offsets = x;
      for (int i = 0; i < nAdded; i++) {
        offsets[nRows + i] = new int[nCols];
      }
      nRows = n;
      row1 = row;
    }

    offsets[row - row0][col - col0] = (int) (offset / 8L); // may be negative
  }

  /**
   * Gets the file position for the specified tile.
   *
   * @param tileIndex a positive integer
   * @return if found, a value greater than zero; otherwise, zero.
   */
  long getFilePosition(int tileIndex) {
    int row = tileIndex / nColsOfTiles;
    int col = tileIndex - row * nColsOfTiles;
    if (nCols == 0 || row < row0 || col < col0 || row > row1 || col > col1) {
      return 0;
    }

    // for files larger than 16 GB, the offset value may be negative.
    // so a mask is applied to convert it to a positive value.
    return (((long) offsets[row - row0][col - col0]) & 0xffffffffL) * 8L;
  }

  /**
   * Indicates whether a file position is set for the specified tile index.
   *
   * @param tileIndex a positive integer
   * @return true if a valid file position is stored for a tile;
   * otherwise, false
   */
  boolean isFilePositionSet(int tileIndex) {
    int row = tileIndex / nColsOfTiles;
    int col = tileIndex - row * nColsOfTiles;
    if (nCols == 0 || row < row0 || col < col0 || row > row1 || col > col1) {
      return false;
    }
    return offsets[row - row0][col - col0] != 0;
  }

  /**
   * Reads the tile position data from the specified file.
   *
   * @param braf a valid file reference
   * @throws IOException in the event of an unhandled I/O exception.
   */
  void readTilePositions(BufferedRandomAccessFile braf) throws IOException {
    row0 = braf.leReadInt();
    col0 = braf.leReadInt();
    nRows = braf.leReadInt();
    nCols = braf.leReadInt();
    this.row1 = row0 + nRows - 1;
    this.col1 = col0 + nCols - 1;
    if (nCols == 0) {
      offsets = null;
      // no position records follow in file.
    } else {
      offsets = new int[nRows][];
      for (int i = 0; i < nRows; i++) {
        offsets[i] = new int[nCols];
      }
      for (int i = 0; i < nRows; i++) {
        for (int j = 0; j < nCols; j++) {
          offsets[i][j] = braf.leReadInt();
        }
      }
    }
  }

  /**
   * Writes the tile position data from the specified file.
   *
   * @param braf a valid file reference
   * @throws IOException in the event of an unhandled I/O exception.
   */
  void writeTilePositions(BufferedRandomAccessFile braf) throws IOException {
    braf.leWriteInt(row0);
    braf.leWriteInt(col0);
    braf.leWriteInt(nRows);
    braf.leWriteInt(nCols);
    if (nCols > 0) {
      for (int i = 0; i < nRows; i++) {
        for (int j = 0; j < nCols; j++) {
          braf.leWriteInt(offsets[i][j]);
        }
      }
    }
  }

  /**
   * Gets the amount of storage space required to store the tile index, in
   * bytes.
   *
   * @return a positive value, in bytes.
   */
  public int getStorageSize() {
    int nCell = nRows * nCols;
    return 4 * (4 + nCell);
  }

}
