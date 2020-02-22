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
 *   At this time, the file space alloc and dealloc has serious shortcomings
 * in handling the case of variable size blocks of file space. Typically, 
 * this happens when handling compressed data.  When the file-space
 * management is unable to fullfil an allocation using free-nodes,
 * it leaves behind a small block of unused space. Over time, these 
 * can accumulate until the file is mostly unused space.
 *  It appears that some mechanism is needed for consolating sections
 * of free space to create blocks large enough to store data.
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.gridfour.io.BufferedRandomAccessFile;

/**
 * Provides utilities for managing file resources related to the storage and
 * access of tiles and supplemental records in a G93 file
 */
class G93TileStore {

  private static class FreeNode {

    FreeNode next;
    int blockSize;
    long filePos;

    FreeNode(long filePos, int blockSize) {
      this.filePos = filePos;
      this.blockSize = blockSize;
    }
  }

  private static final int RECORD_HEADER_SIZE = 12;  // 3 4-byte integers
  private static final int MIN_FREE_BLOCK_SIZE = 1024;

  private final G93FileSpecification spec;
  private final CodecMaster codecMaster;
  private final BufferedRandomAccessFile braf;
  private final long basePosition;
  private final int[] tilePositions;
  private final int standardTileSizeInBytes;

  private FreeNode freeList;

  int nTileReads;
  int nTileWrites;

  LinkedHashMap<VariableLengthRecord, VariableLengthRecord> vlrRecordMap
          = new LinkedHashMap<>();

  G93TileStore(
          G93FileSpecification spec,
          CodecMaster rasterCodec,
          BufferedRandomAccessFile braf,
          long filePosTileStore) {
    this.spec = spec;
    this.codecMaster = rasterCodec;
    this.braf = braf;
    this.basePosition = filePosTileStore;
    int nTiles = spec.nRowsOfTiles * spec.nColsOfTiles;
    tilePositions = new int[nTiles];
    standardTileSizeInBytes = spec.getStandardTileSizeInBytes();
  }

  /**
   * Rounds the specified value up to the nearest multiple of 8. Intended to
   * support the requirement that record sizes be a multiple of 8. Undefined for
   * negative numbers.
   *
   * @param value positive integer to be rounded up to a multiple of 8
   * @return a positive integer
   */
  private int multipleOf8(int value) {
    assert value > 0 : "Attempt to round-up a negative or zero value";
    return (value + 7) & 0x7ffffff8;
  }

  private void setTilePosition(int tileIndex, long filePos) {
    tilePositions[tileIndex] = (int) (filePos / 8L);
  }

  private long getTilePosition(int tileIndex) {
    return ((long) tilePositions[tileIndex] & 0xffffffffL) * 8L;
  }

  /**
   * Indicates whether a tile exists in the file-based tile store.
   *
   * @param tileIndex a positive integer
   * @return true if the tile exists in the tile store, otherwise false.
   */
  boolean doesTileExist(int tileIndex) {
    return tilePositions[tileIndex] != 0;
  }

  long fileSpaceAlloc(int sizeToStore) throws IOException {
    assert multipleOf8(sizeToStore) == sizeToStore : "allocate invalid size " + sizeToStore;
    int minSizeForSplit = sizeToStore + MIN_FREE_BLOCK_SIZE;
    //   We look for a free node that is either the perfect size to store
    // this data or sufficiently large to split.  We do not want too many
    // tiny-sized free blocks to accumulate.  So a block that is only
    // a little bigger than our target will not work.
    //   We search the list for a first found strategy.  We don't look for
    // the best fit, just the first feasible fit.
    FreeNode prior = null;
    FreeNode node = freeList;
    while (node != null) {
      if (node.blockSize == sizeToStore || node.blockSize >= minSizeForSplit) {
        break;
      }
      prior = node;
      node = node.next;
    }

    if (node == null) {
//      long fileSize = braf.getFileSize();
//      braf.seek(fileSize);
//      byte []zeroes = new byte[sizeToStore];
//      braf.writeFully(zeroes, 0, sizeToStore);
//      braf.seek(fileSize);
//      return fileSize;
      assert (braf.getFileSize() & 0x07L) == 0 : "File size not multiple of 8";
      return braf.getFileSize();
    }

    // Remove the node from the free list
    if (prior == null) {
      freeList = node.next;
    } else {
      prior.next = node.next;
    }

    node.next = null; // pro forma
    long posToStore = node.filePos;

    // check the existing file block and make sure that
    // the data is correct (it should be correct).  If the packing
    // is sufficiently smaller than the available space, we should be
    // able to split it.  If we don't have sufficient surplus, we
    // will record the block on disk to be the size of the original
    // storage, not the size of the packing.
    braf.seek(node.filePos);
    int foundSize = braf.leReadInt();
    assert foundSize < 0 : "alloc found positive block size in file";
    foundSize = -foundSize;
    assert foundSize >= sizeToStore : "alloc found insufficient block size";
    int surplus = foundSize - sizeToStore;
    if (surplus > 0) {
      long surplusPos = node.filePos + sizeToStore;
      FreeNode surplusNode = new FreeNode(surplusPos, surplus);
      braf.seek(surplusPos);
      braf.leWriteInt(-surplus);
      prior = null;
      FreeNode next = freeList;
      while (next != null) {
        if (next.filePos > surplusPos) {
          break;
        }
        prior = next;
        next = next.next;
      }
      if (prior == null) {
        freeList = surplusNode;
      } else {
        prior.next = surplusNode;
      }
      surplusNode.next = next;
    }
    braf.seek(posToStore);
    assert (posToStore & 0x07L) == 0 : "Post to store  size not multiple of 8";
    return posToStore;
  }

  void fileSpaceDealloc(long releasePos) throws IOException {
    // the tile was previously written to the file.
    // replace it with the current tile
    braf.seek(releasePos);
    int releaseSize = braf.leReadInt();
    assert releaseSize > 0 : "read negative or zero number at tile position";
    braf.seek(releasePos);
    braf.leWriteInt(-releaseSize);

    // we will insert the file-space management information for the
    // existing record located at position filePos into the free list.
    // the free list is organized in order of file position.  so we must 
    // traverse the list to find the appropriate place for this free node.
    // when we do, it may turn out that the file-space we are freeing is
    // adjacent to a previously freed block.  If so, we can merge the two
    // into a single free node.
    FreeNode prior = null;
    FreeNode next = freeList;
    while (next != null) {
      if (next.filePos > releasePos) {
        break;
      }
      prior = next;
      next = next.next;
    }

    if (prior != null) {
      // see if we can merge the prior block with the released block.
      if (prior.filePos + prior.blockSize == releasePos) {
        prior.blockSize += releaseSize;
        // extending the prior block may have led to an opportunity to merge
        // the prior block with the next block
        if (next != null && prior.filePos + prior.blockSize == next.filePos) {
          // merge prior with next, remove next from the free list.
          prior.blockSize += next.blockSize;
          prior.next = next.next;
          next.next = null;
        }
        braf.seek(prior.filePos);
        braf.leWriteInt(-prior.blockSize);
        return;
      }
    }

    // the released block was not merged with the prior, see if it should
    // be merged with the next
    if (next != null) {
      if (releasePos + releaseSize == next.filePos) {
        // for the merger, we don't create a new node or modify the
        // links in the list...  we just adjust the file posiiton
        // of the next node back to the released position
        next.filePos = releasePos;
        next.blockSize += releaseSize;
        braf.seek(next.filePos);
        braf.leWriteInt(-next.blockSize);
        return;
      }
    }

    // if we got here, no mergers were accomplished.  insert a new
    // node into the list
    FreeNode node = new FreeNode(releasePos, releaseSize);
    if (prior == null) {
      freeList = node;
    } else {
      prior.next = node;
    }
    node.next = next;
  }

  void storeTile(RasterTile tile) throws IOException {
    // the payload includes  nValues integers giving the content.
    // the size-to-store value is the record header size, plus the
    // payload size.  because all records must start on file position
    // which is a multiple of 8, we round the sizeToStore up to the 
    // nearset multiple of 8 (if necessary).
    int tileIndex = tile.tileIndex;
    int payloadSize = standardTileSizeInBytes;
    int sizeToStore = multipleOf8(RECORD_HEADER_SIZE + payloadSize);
    long posToStore;
    nTileWrites++;

    long initialFilePos = getTilePosition(tileIndex);
    assert initialFilePos >= 0 : "Invalid file position";

    if (spec.isDataCompressionEnabled()) {
      // whether the compression succeeds or not, it is likely that the
      // size of the compressed block will change.  So we deallocate the
      // current file storage immediately.  I chose to do this right away
      // because it simplifies the code and help ensures a correct implementaiton.
      // I could have delayed this action until I was absolutely sure, but
      // the probability of saving some file I/O operations was small
      // and not worth the extra code complexity.
      if (initialFilePos > 0) {
        fileSpaceDealloc(initialFilePos);
        setTilePosition(tileIndex, 0);
      }
      byte[] packing = tile.getCompressedPacking(codecMaster);
      if (packing != null) {
        // The compression was successful.  Usually, it will be much smaller
        // than the native form of the data. But, if the data is noisy,
        // it is possible that the post-compression form might even be larger
        // than the source.  we will store the data in compression
        // form only if it is smaller than the uncompressed version.
        // FUTURE STUDY:
        //        since decompressing data adds overhead on the read side,
        //        performance might be better served by not saving the
        //        compressed format unless it saves some substantial
        //        portion of the storage space.  25 percent? 10 percent? 5?
        //        should this decision be a file-creation specification or set
        //        at run-time in a manner similar to the cache size setting
        int compressedSize = multipleOf8(RECORD_HEADER_SIZE + packing.length);
        if (compressedSize < sizeToStore) {
          posToStore = fileSpaceAlloc(compressedSize);
          setTilePosition(tileIndex, posToStore);
          braf.seek(posToStore);
          // store header
          braf.leWriteInt(compressedSize);
          braf.leWriteInt(tileIndex);
          braf.leWriteInt(1); // low-byte and 3 spares
          braf.writeFully(packing, 0, packing.length);
          int sizeStoredSoFar = RECORD_HEADER_SIZE + packing.length;
          for (int i = sizeStoredSoFar; i < compressedSize; i++) {
            braf.writeByte(0);
          }
          braf.flush();
          return;
        }
      }
    }

    if (initialFilePos == 0) {
      posToStore = fileSpaceAlloc(sizeToStore);
      // set the position, seek the start of the record,
      // and write the header
      setTilePosition(tileIndex, posToStore);
      braf.seek(posToStore);
      braf.leWriteInt(sizeToStore);
      braf.leWriteInt(tileIndex);
      braf.leWriteInt(0); // low-byte and 3 spares
    } else {
      // we will be re-writing the record in its same position
      // position file just past the header
      posToStore = initialFilePos;
      braf.seek(posToStore + RECORD_HEADER_SIZE);
    }

    tile.writeStandardFormat(braf);

    // it is not absolutely necessary to store zeroes to the rest of
    // the tile block, but we do so as a diagnostic procedure.
    int sizeStoredSoFar = RECORD_HEADER_SIZE + payloadSize;
    for (int i = sizeStoredSoFar; i < sizeToStore; i++) {
      braf.writeByte(0);
    }
    braf.flush();
  }

  void readTile(RasterTile tile) throws IOException {
    int tileIndex = tile.tileIndex;

    long filePos = getTilePosition(tileIndex);
    if (filePos == 0) {
      tile.setToNullState();
      return;
    }

    nTileReads++;
    braf.seek(filePos);
    int recordSize = braf.leReadInt();
    assert recordSize >= 0 :
            "negative packing size for tile on file, tile.index=" + tileIndex;
    int tileIndexFromFile = braf.leReadInt();
    int compressionFlag = braf.leReadInt() & 0xff; // low-byte, 3 spares
    assert tileIndexFromFile == tileIndex : "incorrect tile index on file";
    int paddedPayloadSize = recordSize - RECORD_HEADER_SIZE;
    if (compressionFlag == 0) {
      // it's not compressed
      tile.readStandardFormat(braf);
    } else {
      // it's compressed
      tile.readCompressedFormat(codecMaster, braf, paddedPayloadSize);
    }
  }

  void scanFileForTiles() throws IOException {
    freeList = null;  // for diagnostic use
    FreeNode freeListEnd = null;
    int maxTileIndex = spec.nRowsOfTiles * spec.nColsOfTiles;
    long fileSize = braf.getFileSize();
    long filePos = basePosition;
    while (filePos < fileSize - RECORD_HEADER_SIZE) {
      braf.seek(filePos);
      int recordSize = braf.leReadInt();
      if (recordSize == 0) {
        break;
      }
      if (recordSize < 0) {
        // add the block of file space to the free list.
        // the free list is ordered by file position, so the new node
        // goes on the end of the list.
        recordSize = -recordSize;
        FreeNode node = new FreeNode(filePos, recordSize);
        if (freeListEnd == null) {
          freeList = node;
          freeListEnd = node;
        } else {
          freeListEnd.next = node;
          freeListEnd = node;
        }
      } else {
        int tileIndex = braf.leReadInt();
        if (tileIndex < 0) {
          // negative tile indexes are used to introduce non-tile
          // records.  
          if (tileIndex != -1) {
            throw new IOException("Undefined record code " + (-tileIndex));
          }
          int contentSize = recordSize - RECORD_HEADER_SIZE;
          braf.skipBytes(8);
          VariableLengthRecord vlr = new VariableLengthRecord(braf, contentSize);
          vlrRecordMap.put(vlr, vlr);
        } else if (tileIndex >= maxTileIndex) {
          throw new IOException("Incorrect tile index read from file " + tileIndex);
        } else {
          setTilePosition(tileIndex, filePos);
        }
      }
      filePos += recordSize;
    }
  }

  /**
   * Write the tile positions, free list, and variable length record indices to
   * an index file.
   *
   * @param indexRaf the random access file instance for the index file.
   * @throws IOException in the event of an I/O error
   */
  void writeTilePositionsToIndexFile(
          BufferedRandomAccessFile indexRaf) throws IOException {

    indexRaf.writeBoolean(spec.isExtendedFileSizeEnabled);
    indexRaf.writeByte(0); // reserved
    indexRaf.writeByte(0); // reserved
    indexRaf.writeByte(0);// reserved
    indexRaf.leWriteInt(spec.nRowsOfTiles);
    indexRaf.leWriteInt(spec.nColsOfTiles);
    for (int i = 0; i < tilePositions.length; i++) {
      indexRaf.leWriteInt(tilePositions[i]);
    }

    indexRaf.flush();
    int nFreeNodes = 0;
    FreeNode node = freeList;
    while (node != null) {
      nFreeNodes++;
      node = node.next;
    }
    indexRaf.leWriteInt(nFreeNodes);
    if (nFreeNodes > 0) {
      node = freeList;
      while (node != null) {
        int filePos = (int) (node.filePos / 8L);
        indexRaf.leWriteInt(filePos);
        indexRaf.leWriteInt(node.blockSize);
        node = node.next;
      }
    }

    // Note that the offsets for the data are reduced in size in a manner
    // consistent with the tile positions.  The savings offered by this approach
    // is less important than for tiles since there are relatively few
    // VLR's compared to the number of tiles. But we use this approach
    // in order to have a consistent treatment.
    List<VariableLengthRecord> vlrList = this.getVariableLengthRecords();
    int nVariableLengthRecords = vlrList.size();
    indexRaf.leWriteInt(nVariableLengthRecords);
    for (VariableLengthRecord vlr : vlrList) {
      // the offset in a VLR is the file address of it's content.
      // by we need to store the file position of the entire record.
      long filePos = vlr.offset - RECORD_HEADER_SIZE;
      indexRaf.leWriteInt((int) (filePos / 8));
    }

    indexRaf.flush();
  }

  @SuppressWarnings("PMD.UnusedLocalVariable")
  void readTilePositionsFromIndexFile(BufferedRandomAccessFile idxraf)
          throws IOException {
    boolean isFilePosCompressionEnabled = idxraf.readBoolean();
    idxraf.skipBytes(3);
    int nRowsOfTiles = idxraf.leReadInt();
    int nColsOfTiles = idxraf.leReadInt();
    int nTilesInTable = nRowsOfTiles * nColsOfTiles;
    if (nTilesInTable != tilePositions.length) {
      throw new IOException("G93 file and index file do not match");
    }
    for (int i = 0; i < nTilesInTable; i++) {
      tilePositions[i] = idxraf.leReadInt();
    }

    if (idxraf.getFilePosition() == idxraf.getFileSize()) {
      return;
    }

    int nFreeNodes = idxraf.leReadInt();
    for (int iFree = 0; iFree < nFreeNodes; iFree++) {
      long freePos = (((long) idxraf.leReadInt()) & 0xffffffffL) * 8L;
      int freeSize = idxraf.leReadInt();
      FreeNode node = new FreeNode(freePos, freeSize);
      node.next = freeList;
      freeList = node;
    }

    int nVariableLengthRecords = idxraf.leReadInt();
    for (int i = 0; i < nVariableLengthRecords; i++) {
      long recordPos = (((long) idxraf.leReadInt()) & 0xffffffffL) * 8L;
      braf.seek(recordPos);
      int rSize = braf.leReadInt();
      int rType = -braf.leReadInt();
      int dummy1 = braf.leReadInt();
      int dummy2 = braf.leReadInt();
      if (rSize <= VariableLengthRecord.VLR_HEADER_SIZE || rType != 1) {
        throw new IOException("Internal error, incorrectly indexed VLR");
      }
      VariableLengthRecord vlr
              = new VariableLengthRecord(braf, rSize - RECORD_HEADER_SIZE);
      vlrRecordMap.put(vlr, vlr);
    }

  }

  void summarize(PrintStream ps) {
    ps.println("Tile IO");
    ps.format("   Tile Reads:   %8d%n", nTileReads);
    ps.format("   Tile Writes:  %8d%n", nTileWrites);

    int nFreeNodes = 0;
    long freeSpace = 0;
    FreeNode node = freeList;
    while (node != null) {
      nFreeNodes++;
      freeSpace += node.blockSize;
      node = node.next;
    }
    ps.println("File Space Allocation");
    ps.format("   Free Nodes:   %8d%n", nFreeNodes);
    ps.format("   Free Space:   %8d bytes%n", freeSpace);

    ps.format("Variable Length Records:  %d%n", vlrRecordMap.size());

    if (!vlrRecordMap.isEmpty()) {
      Collection<VariableLengthRecord> vlrs = vlrRecordMap.values();
      List<VariableLengthRecord> vlrList = new ArrayList<>();
      vlrList.addAll(vlrs);
      Collections.sort(vlrList, new Comparator<VariableLengthRecord>() {
        @Override
        public int compare(VariableLengthRecord o1, VariableLengthRecord o2) {
          int test = o1.userId.compareTo(o2.userId);
          if (test == 0) {
            test = Integer.compare(o1.recordId, o2.recordId);
          }
          return test;
        }
      });
      ps.println("      User ID             Record ID       Size (bytes)");
      int k = 0;
      for (VariableLengthRecord vlr : vlrList) {
        k++;
        ps.format("%3d.  %-18.18s   %8d   %8d%n",
                k, vlr.userId, vlr.recordId, vlr.payloadSize);
      }
    }

  }

  /**
   * Allocates space for storing a record. The record type is opaque to the tile
   * store, but is assumed to not be a tile.
   *
   * @param recordType an integer value indicating the record type
   * @param recordSize the size of the record to be stored
   * @return if successful, a valid file position for writing the content of the
   * non-tile record.
   */
  long allocateNonTileRecord(int recordType, int recordSize)
          throws IOException {
    if (recordType <= 0) {
      throw new IOException("Internal error, record type must be positive number");
    }
    int n = multipleOf8(RECORD_HEADER_SIZE + 4 + recordSize);
    long filePos = fileSpaceAlloc(n);
    braf.seek(filePos);
    braf.leWriteInt(n);
    braf.leWriteInt(-recordType);
    braf.leWriteInt(0);  // 4 spare bytes for header
    braf.leWriteInt(0);  // 4 spare bytes to put it on a multiple-of-8

    // just in case we can't trust the application code to 
    // fully write its content, we write a set of zeroes to the file.
    // while this action has a small performance cost, the assumption
    // is that non-tile records are only a small part of the over all
    // file content and the overhead doesn't matter.
    n = multipleOf8(recordSize);
    byte[] zero = new byte[n];
    braf.writeFully(zero);

    // move into position to write the content
    braf.seek(filePos + RECORD_HEADER_SIZE + 4);
    return filePos + RECORD_HEADER_SIZE + 4;
  }

  List<VariableLengthRecord> getVariableLengthRecords() {
    Collection<VariableLengthRecord> values = vlrRecordMap.values();
    List<VariableLengthRecord> list = new ArrayList<>();
    for (VariableLengthRecord vlr : values) {
      list.add(vlr);
    }
    return list;
  }

  void analyzeAndReport(PrintStream ps) throws IOException {
    if (!spec.isDataCompressionEnabled()) {
      return;
    }

    for (int tileIndex = 0; tileIndex < tilePositions.length; tileIndex++) {

      long filePos = getTilePosition(tileIndex);
      if (filePos == 0) {
        continue;
      }
      braf.seek(filePos);
      int recordSize = braf.leReadInt();
      assert recordSize >= 0 : "negative packing size for tile on file";
      int tileIndexFromFile = braf.leReadInt();
      assert tileIndexFromFile == tileIndex : "incorrect tile index on file";
      int compressionFlag = braf.leReadInt() & 0xff;  // 1 byte and 3 spares
      if (recordSize < 0) {
        System.out.println("Diagnostic");
      }

      if (compressionFlag != 0) {
        // it's compressed
        int paddedPayloadSize = recordSize - RECORD_HEADER_SIZE;
        byte[] packing = new byte[paddedPayloadSize];
        for (int iRank = 0; iRank < spec.dimension; iRank++) {
          braf.readFully(packing, 0, 4);
          int a = packing[0] & 0xff;
          int b = packing[1] & 0xff;
          int c = packing[2] & 0xff;
          int d = packing[3] & 0xff;
          int n = (((((d << 8) | c) << 8) | b) << 8) | a;
          braf.readFully(packing, 0, n);
          codecMaster.analyze(spec.nRowsInTile, spec.nColsInTile, packing);
        }
      }
    }
    codecMaster.reportAndClearAnalysisData(ps, spec.nRowsOfTiles * spec.nColsOfTiles);
  }

}
