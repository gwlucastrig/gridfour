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

class TileAccessIndices {

  public int tileIndex;
  public int rowInTile;
  public int colInTile;
  public int tileRow;
  public int tileCol;
  public int indexInTile;

  final int nRowsInRaster;
  final int nColsInRaster;
  final int nRowsInTile;
  final int nColsInTile;
  final int nColsOfTiles;

  TileAccessIndices(GvrsFileSpecification spec) {

    this.nRowsInRaster = spec.nRowsInRaster;
    this.nColsInRaster = spec.nColsInRaster;
    this.nRowsInTile = spec.nRowsInTile;
    this.nColsInTile = spec.nColsInTile;
    this.nColsOfTiles = spec.nColsOfTiles;
  }

  public void computeAccessIndices(int row, int col) throws IOException {
    if (row < 0 || row >= nRowsInRaster) {
      throw new IOException("Row out of bounds " + row);
    }
    if (col < 0 || col >= nColsInRaster) {
      throw new IOException("Column out of bounds " + col);
    }

    tileRow = row / nRowsInTile;
    tileCol = col / nColsInTile;
    tileIndex = tileRow * nColsOfTiles + tileCol;
    rowInTile = row - tileRow * nRowsInTile;
    colInTile = col - tileCol * nColsInTile;
    indexInTile = rowInTile*nColsInTile+colInTile;
  }
}
