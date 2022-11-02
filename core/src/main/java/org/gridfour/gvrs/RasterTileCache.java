/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.
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
package org.gridfour.gvrs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;

/**
 * Provides a cache for managing tiles
 */
class RasterTileCache {

  private static final int DEFAULT_TILE_CACHE_SIZE = 16;

  int tileCacheSize;
  int nTilesInCache;
  RasterTile firstTile;
  RasterTile lastTile;
  final RecordManager recordManager;
  final GvrsFileSpecification spec;

  TileDecompressionAssistant tileDecompAssistant;

  HashMap<Integer, RasterTile> cachedTilesMap = new HashMap<>();
  int priorUnsatistiedRequest = -1;

  // Counters for gathering access statistics
  private long nTileGets;
  private long nTileFoundInCache;
  private long nTileRead;
  private long nTilesWritten;
  private long nTilesDiscarded;
  private long nTileFirst;

  /**
   * Constructs a tile-cache tied to the GvrsFile from which the file
   * specification and record manager were taken.
   *
   * @param spec a valid instance
   * @param recordManager a valid instance
   */
  RasterTileCache(GvrsFileSpecification spec, RecordManager recordManager) {
    tileCacheSize = DEFAULT_TILE_CACHE_SIZE;
    this.recordManager = recordManager;
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

  /**
   * Fetch the specified tile from the cache. If the tile is in the
   * cache, move it to the head of the linked list of tiles and
   * return the tile.
   * <p>
   * If a tile is not in the cache, but exists in the data file,
   * read it and store it at the head of the linked list of tiles.
   * If the cache is already full when a new tile is read, the last tile
   * in the linked list is discarded.
   *
   * @param tileIndex the index of the tile to be read from the file
   * @return if tile exists in the cache or reference file, a valid
   * instance; otherwise, a null.
   * @throws IOException in the event of an unrecoverable IO exception while
   * reading or writing a tile.
   */
  RasterTile getTile(int tileIndex) throws IOException {
    nTileGets++;
    //assert tileIndex >= 0 : "Invalid tile index " + tileIndex;

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
      tile = cachedTilesMap.get(tileIndex);
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
      if (!recordManager.doesTileExist(tileIndex)) {
        priorUnsatistiedRequest = tileIndex;
        return null;
      }
    }

    if (this.tileDecompAssistant != null) {
      return readTileUsingAssistant(tileIndex);
    }

    // tile is not in the cache.  allocate a new tile instance,
    // read its content from the reference file and add it to the cache.
    int tileRow = tileIndex / spec.nColsOfTiles;
    int tileCol = tileIndex - tileRow * spec.nColsOfTiles;

    tile = new RasterTile(
      tileIndex,
      tileRow,
      tileCol,
      spec.nRowsInTile,
      spec.nColsInTile,
      spec.elementSpecifications,
      false);

    nTileRead++;
    recordManager.readTile(tile);

    addTileToCache(tile);
    return tile;
  }

  /**
   * Adds a tile to the head of the cache. If the cache is full the last tile
   * is discarded. If writing is enabled and the tile to be discarded
   * is marked for storage, then it will be written to the output file.
   *
   * @param tile a valid tile
   * @throws IOException if writing is enabled and the writing operation
   * encounters a non-recoverable IOException.
   */
  void addTileToCache(RasterTile tile) throws IOException {
    // the cache is full, make room for the new tile
    if (nTilesInCache == tileCacheSize) {
      discardLastTile();
    }

    // add to head of linked list
    cachedTilesMap.put(tile.tileIndex, tile);
    nTilesInCache++;
    //assert nTilesInCache == cachedTilesMap.size() : "cache size mismatch";
    if (firstTile == null) {
      firstTile = tile;
      lastTile = tile;
    } else {
      tile.next = firstTile;
      firstTile.prior = tile;
      firstTile = tile;
    }
  }

  /**
   * Allocate a new tile, populate it with initial values and store
   * it at the head of the linked list.
   * <p>
   * For efficiency, this method assumes that the calling application
   * has already determined that the tile does not exist in the cache
   * or in the reference file. This method does not test for that
   * necessary condition.
   *
   * @param tileIndex the index of the tile
   * @return a valid tile
   * @throws IOException in the event of an unrecoverable IO exception.
   */
  RasterTile allocateNewTile(int tileIndex) throws IOException {

    RasterTile tile;
    this.priorUnsatistiedRequest = -1;

    int tileRow = tileIndex / spec.nColsOfTiles;
    int tileCol = tileIndex - tileRow * spec.nColsOfTiles;
    tile = new RasterTile(
      tileIndex,
      tileRow,
      tileCol,
      spec.nRowsInTile,
      spec.nColsInTile,
      spec.elementSpecifications,
      true);

    addTileToCache(tile);

    return tile;

  }

  /**
   * Discard the last tile in the linked list. If the file is opened
   * for writing and the content of the tile has changed since the
   * last time it was read from the file (if ever), then the file
   * will be written to the file.
   *
   * @throws IOException in the event of an unrecoverable IO exception.
   */
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
      cachedTilesMap.clear();
    } else {
      nTilesInCache--;
      lastTile = lastTile.prior;
      lastTile.next = null;
      cachedTilesMap.remove(temp.tileIndex);
    }

    if (temp.isWritingRequired()) {
      writeTile(temp);
    }

    temp.clear(); // nullifies links from linked list, ensures garbage collection.
  }

  void writeTile(RasterTile tile) throws IOException {
    nTilesWritten++;
    recordManager.writeTile(tile);
    tile.clearWritingRequired();
  }

  void flush() throws IOException {
    RasterTile tile = firstTile;
    while (tile != null) {
      if (tile.isWritingRequired()) {
        writeTile(tile);
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
    ps.format("   Tiles In Cache:            %12d%n", cachedTilesMap.size());
    ps.format("   Tiles Fetched:             %12d%n", nTileGets);
    ps.format("   Tiles Fetched from Cache:  %12d (%4.1f%%)%n", nTileFoundInCache, percentInCache);
    ps.format("   Repeated Fetches:          %12d (%4.1f%%)%n", nTileFirst, percentFirst);
    ps.format("   Tiles Read:                %12d%n", nTileRead);
    ps.format("   Tiles Written:             %12d%n", nTilesWritten);
    ps.format("   Tiles Dropped From Cache:  %12d%n", nTilesDiscarded);
  }

  /**
   * Sets the decompression-assistant element to enable the use of backing
   * threads to decompress tiles.
   *
   * @param tileDecompAssistant a valid instance
   */
  void setTileDecompAssistant(TileDecompressionAssistant tileDecompAssistant) {
    this.tileDecompAssistant = tileDecompAssistant;
  }

  private RasterTile readTileUsingAssistant(int targetIndex) throws IOException {
    // If the target tile is currently on the list of tiles submitted
    // to the decomp assistent for processing, the assistant will wait
    // until it is fully processed.  It returns a list of tiles that
    // are available for storage in the cache.  If the target index
    // is included in the list, then it will be stored at the head of
    // the linked-list of tiles maintained by the cache.

    if(!recordManager.doesTileExist(targetIndex)){
      return null;
    }

    RasterTile targetTile = null;
    List<RasterTile> tList = tileDecompAssistant.getTilesWithWaitForIndex(targetIndex);
    for (RasterTile t : tList) {
      if (t.tileIndex == targetIndex) {
        targetTile = t;
      } else {
        this.addTileToCache(t);
      }
    }
    if (targetTile != null) {
      addTileToCache(targetTile);
      return targetTile;
    }

    // --------------------------------------------------------------
    // The tile of interest was not available from the assistant.
    // We will process it and also one predicted tile.  One slight
    // complication here is that we read the packing for the target
    // tile first, then the packing for predicted tile.  Presumably,
    // if the tiles are stored in the file in order, then this approach
    // we reduce the number of random-access seek operations that the
    // RecordManager and BufferedRandomAccessFile need to make.
    // In testing with a fast Solid-State Drive, this reduced access
    // time but about 5 percent, but with a slower electro-mechanical drive,
    // it might be even more important.
    //   Also note that we read the target tile packing, but do not
    // perform the more time-consuming decompression until after we
    // have read and submitted the predicted tile. Thus the predicted
    // tile can be processed in the assistant thread rather while
    // the target tile is processed in the application thread.
    int targetTileRow = targetIndex / spec.nColsOfTiles;
    int targetTileCol = targetIndex - targetTileRow * spec.nColsOfTiles;

    RasterTile target = new RasterTile(
      targetIndex,
      targetTileRow,
      targetTileCol,
      spec.nRowsInTile,
      spec.nColsInTile,
      spec.elementSpecifications,
      false);
    nTileRead++;
    byte[][] targetPacking = recordManager.readTilePacking(target);

    // ------------------------------------------------------------
    int nTilesInRaster = spec.nRowsOfTiles * spec.nColsOfTiles;
    int predictedIndex = targetIndex + 1;
    if (tileDecompAssistant.getPendingTaskCount() < 2
      && predictedIndex < nTilesInRaster
      && !cachedTilesMap.containsKey(predictedIndex)
      && recordManager.doesTileExist(predictedIndex)) {
      int predictedTileRow = predictedIndex / spec.nColsOfTiles;
      int predictedTileCol = predictedIndex - predictedTileRow * spec.nColsOfTiles;
      RasterTile predictedTile = new RasterTile(
        predictedIndex,
        predictedTileRow,
        predictedTileCol,
        spec.nRowsInTile,
        spec.nColsInTile,
        spec.elementSpecifications,
        false);
      nTileRead++;
      byte[][] predictedPacking = recordManager.readTilePacking(predictedTile);
      tileDecompAssistant.submitDecompression(predictedTile, predictedPacking);
    }

    // Now decode the target packing
    int k = 0;
    for (TileElement e : target.elements) {
      e.decode(recordManager.codecMaster, targetPacking[k++]);
    }

    addTileToCache(target);
    return target;

  }

}
