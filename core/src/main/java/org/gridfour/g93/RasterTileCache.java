/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.

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
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;

/**
 * Provides a cache for managing tiles
 */
class RasterTileCache {

  private static final int DEFAULT_TILE_CACHE_SIZE = 16;

  int tileCacheSize;
  int nTilesInCache;
  RasterTile firstTile;
  RasterTile lastTile;
  final G93TileStore tileStore;
  final G93FileSpecification spec;

  HashMap<Integer, RasterTile> tileMap = new HashMap<>();
  int priorUnsatistiedRequest = -1;

  // Counters for gathering access statistics
  private long nTileGets;
  private long nTileFoundInCache;
  private long nTileRead;
  private long nTilesWritten;
  private long nTilesDiscarded;
  private long nTileFirst;

  RasterTileCache(G93FileSpecification spec, G93TileStore tileStore) {
    tileCacheSize = DEFAULT_TILE_CACHE_SIZE;
    this.tileStore = tileStore;
    this.spec = spec;
  }

  void setTileCacheSize(int tileCacheSize) throws IOException {
    if (tileCacheSize < 0) {
      throw new IOException(
              "Cache size less than zero is not supported");
    }
    this.tileCacheSize = tileCacheSize;
    while (nTilesInCache > tileCacheSize) {
      discardLastTile();
    }
  }

  RasterTile getTile(int tileIndex) throws IOException {
    nTileGets++;
    assert tileIndex >= 0 : "Invalid tile index " + tileIndex;

    if (tileIndex == priorUnsatistiedRequest) {
      return null;
    }

    RasterTile tile;
    if (firstTile != null) {
      // first, check for what we hope will be he most common case: the request
      // falling into the same tile that was most recently loaded.
      if (firstTile.tileIndex == tileIndex) {
        nTileFirst++;
        nTileFoundInCache++;
        return firstTile;
      }
      tile = tileMap.get(tileIndex);
      if (tile != null) {
        // we've already established that the tile is not the first tile
        tile.prior.next = tile.next;
        if (tile.next == null) {
          lastTile = tile.prior;
        } else {
          tile.next.prior = tile.prior;
        }
        tile.next = firstTile;
        firstTile.prior = tile;
        tile.prior = null;
        firstTile = tile;
        nTileFoundInCache++;
        return tile;
      }

      // the tile was not found in the cache.  If the tile exists
      // in the file, read it and add it to the cache.  Otherwise,
      // return a null to indicate "not found"
      if (!tileStore.doesTileExist(tileIndex)) {
        priorUnsatistiedRequest = tileIndex;
        return null;
      }

      // we're going to read in a new tile.  If the cache is full, we
      // need to discard the oldest tile.
      if (nTilesInCache == tileCacheSize) {
        // the cache is full, make room for the new tile
        discardLastTile();
      }
    }

    // tile is not in the cache.  allocate a new tile, read its content from the
    // file and add it to the cache.
    int tileRow = tileIndex / spec.nColsOfTiles;
    int tileCol = tileIndex - tileRow * spec.nColsOfTiles;
    switch (spec.dataType) {
      case IntegerFormat:
      case IntegerCodedFloat:
        tile = new RasterTileInt(
                tileIndex,
                tileRow,
                tileCol,
                spec.nRowsInTile,
                spec.nColsInTile,
                spec.dimension,
                spec.valueScale,
                spec.valueOffset,
                false);
        break;
      case FloatFormat:
        tile = new RasterTileFloat(
                tileIndex,
                tileRow,
                tileCol,
                spec.nRowsInTile,
                spec.nColsInTile,
                spec.dimension,
                spec.valueScale,
                spec.valueOffset,
                false);
        break;
      default:
        throw new IOException(
                "Incorrectly specified data format " + spec.dataType);
    }

    nTileRead++;
    tileStore.readTile(tile);

    // add to head of linked list
    tileMap.put(tile.tileIndex, tile);
    nTilesInCache++;
    assert nTilesInCache == tileMap.size() : "cache size mismatch";
    if (firstTile == null) {
      firstTile = tile;
      lastTile = tile;
    } else {
      tile.next = firstTile;
      firstTile.prior = tile;
      firstTile = tile;
    }

    return tile;
  }

  RasterTile allocateNewTile(int tileIndex) throws IOException {

    RasterTile tile = this.getTile(tileIndex);
    if (tile != null) {
      throw new IOException("Attempt to allocate a tile that already exists");
    }
    this.priorUnsatistiedRequest = -1;

    int tileRow = tileIndex / spec.nColsOfTiles;
    int tileCol = tileIndex - tileRow * spec.nColsOfTiles;

    switch (spec.dataType) {
      case IntegerFormat:
      case IntegerCodedFloat:
        tile = new RasterTileInt(
                tileIndex,
                tileRow,
                tileCol,
                spec.nRowsInTile,
                spec.nColsInTile,
                spec.dimension,
                spec.valueScale,
                spec.valueOffset,
                true);
        break;
      case FloatFormat:
        tile = new RasterTileFloat(
                tileIndex,
                tileRow,
                tileCol,
                spec.nRowsInTile,
                spec.nColsInTile,
                spec.dimension,
                spec.valueScale,
                spec.valueOffset,
                true);
        break;
      default:
        throw new IllegalArgumentException("Invalid data format specification "
                + spec.dataType);
    }

    if (nTilesInCache == tileCacheSize) {
      discardLastTile();
    }
    if (firstTile == null) {
      firstTile = tile;
      lastTile = tile;
    } else {
      tile.next = firstTile;
      firstTile.prior = tile;
      firstTile = tile;
    }
    nTilesInCache++;
    tileMap.put(tileIndex, tile);
    return tile;

  }

  private void discardLastTile() throws IOException {
    if (lastTile == null) {
      return;
    }
    nTilesDiscarded++;

    RasterTile temp = lastTile;

    if (nTilesInCache == 1) {
      nTilesInCache = 0;
      firstTile = null;
      lastTile = null;
      tileMap.clear();
    } else {
      nTilesInCache--;
      lastTile = lastTile.prior;
      lastTile.next = null;
      tileMap.remove(temp.tileIndex);
    }

    if (temp.isWritingRequired()) {
      storeTile(temp);
    }

    temp.clear();
  }

  void storeTile(RasterTile tile) throws IOException {
    nTilesWritten++;
    tileStore.storeTile(tile);
    tile.clearWritingRequired();
  }

  void flush() throws IOException {
    RasterTile tile = firstTile;
    while (tile != null) {
      if (tile.isWritingRequired()) {
        storeTile(tile);
      }
      tile = tile.next;
    }
  }

  void resetCounts() {
    nTileGets = 0;
    nTileFoundInCache = 0;
    nTileRead = 0;
    nTilesWritten = 0;
    nTilesDiscarded = 0;
    nTileFirst = 0;
  }

  void summarize(PrintStream ps) {
    double percentFirst = 0;
    if (nTileFoundInCache > 0) {
      percentFirst = 100.0 * ((double) nTileFirst / (double) nTileFoundInCache);
    }
    double percentInCache = 0;
    if (nTileGets > 0) {
      percentInCache = 100.0 * ((double) nTileFoundInCache / (double) nTileGets);
      // make sure it never says 100 percent.
      if (percentInCache > 99.91) {
        percentInCache = 99.91;
      }
    }
    ps.format("Tile Cache%n");
    ps.format("   Tiles In Map:              %12d%n", tileMap.size());
    ps.format("   Tiles Fetched:             %12d%n", nTileGets);
    ps.format("   Tiles Fetched from Cache:  %12d (%4.1f%%)%n", nTileFoundInCache, percentInCache);
    ps.format("   Repeated Fetches:          %12d (%4.1f%%)%n", nTileFirst, percentFirst);
    ps.format("   Tiles Read:                %12d%n", nTileRead);
    ps.format("   Tiles Written:             %12d%n", nTilesWritten);
    ps.format("   Tiles Dropped From Cache:  %12d%n", nTilesDiscarded);
  }
}
