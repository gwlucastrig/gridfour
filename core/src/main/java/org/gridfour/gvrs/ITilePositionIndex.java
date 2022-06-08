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
 * Defines an interface for creating index elements to allow applications
 * to access tiles in a random-access data file.
 */
public interface ITilePositionIndex {

  /**
   * Gets the file position for the specified tile.
   *
   * @param tileIndex a positive integer
   * @return if found, a value greater than zero; otherwise, zero.
   */
  long getFilePosition(int tileIndex);

  /**
   * Gets the amount of storage space required to store the tile index, in
   * bytes.
   *
   * @return a positive value, in bytes.
   */
  int getStorageSize();

  /**
   * Indicates whether a file position is set for the specified tile index.
   *
   * @param tileIndex a positive integer
   * @return true if a valid file position is stored for a tile;
   * otherwise, false
   */
  boolean isFilePositionSet(int tileIndex);

  /**
   * Reads the tile position data from the specified file.
   *
   * @param braf a valid file reference
   * @throws IOException in the event of an unhandled I/O exception.
   */
  void readTilePositions(BufferedRandomAccessFile braf) throws IOException;

  /**
   * Store the specified offset at the position given by the
   * tile row and column. If necessary, the index is resized
   * to accommodate the specified coordinates.
   * <p>
   * File positions must be positive values and a multiple of eight.
   *
   * @param tileIndex the index of the tile to be stored.
   * @param offset the file position, in bytes.
   */
  void setFilePosition(int tileIndex, long offset);

  /**
   * Writes the tile position data from the specified file.
   *
   * @param braf a valid file reference
   * @throws IOException in the event of an unhandled I/O exception.
   */
  void writeTilePositions(BufferedRandomAccessFile braf) throws IOException;


  /**
   * Gets the count of the number of tiles that are currently populated.
   * This count does not include tiles that may have been written to, but are
   * still being retained in the tile cache and so have
   * not yet been committed to the output file.
   * @return a positive integer.
   */
  int getCountOfPopulatedTiles();
}
