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
package org.gridfour.gvrs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.gridfour.io.BufferedRandomAccessFile;
import org.gridfour.util.GridfourCRC32C;

/**
 * Provides utilities for managing file resources related to the storage and
 * access of tile and metadata records in a GVRS file.
 */
class RecordManager {

  private static class FreeNode {

    FreeNode next;
    int blockSize;
    long filePos;

    FreeNode(long filePos, int blockSize) {
      this.filePos = filePos;
      this.blockSize = blockSize;
    }
  }

  static final int RECORD_HEADER_SIZE = 8;
  static final int RECORD_OVERHEAD_SIZE = 16;  // 3 4-byte integers
  static final int RECORD_TYPE_FREESPACE = -1;
  static final int RECORD_TYPE_METADATA = -2;
  static final int RECORD_TYPE_TILE_INDEX = -3;

  private static final int MIN_FREE_BLOCK_SIZE = 1024;

  // The filePosTooBig exception will be thrown when
  // the position address returned by a file-space allocation
  // is larger than what can be addressed with an 32+3 bit format.
  // Note that the file itself is allowed to
  // be larger than that, but that the addressable location must be
  // within the limits of the max-non-extended address
  private static final long MAX_NON_EXTENDED_FILE_POS = 1L << 35;
  private static String FILE_POS_TOO_BIG
    = "File size exceeds 32GB limit for non-extended format";

  private final GvrsFileSpecification spec;
  private final CodecMaster codecMaster;
  private final BufferedRandomAccessFile braf;
  private final long basePosition;
  private final int standardTileSizeInBytes;
  private final ITilePositionIndex tilePositionIndex;

  private FreeNode freeList;

  int nTileReads;
  int nTileWrites;

  final HashMap<String, GvrsMetadataReference> metadataRefMap = new HashMap<>();

  RecordManager(
    GvrsFileSpecification spec,
    CodecMaster rasterCodec,
    BufferedRandomAccessFile braf,
    long filePosBasePosition) {
    this.spec = spec;
    this.codecMaster = rasterCodec;
    this.braf = braf;
    this.basePosition = filePosBasePosition;
    //int nTiles = spec.nRowsOfTiles * spec.nColsOfTiles;
    if(spec.isExtendedFileSizeEnabled()){
    tilePositionIndex = new TilePositionExtendedIndex(spec);
    }else{
       tilePositionIndex = new TilePositionIndex(spec);
    }
    standardTileSizeInBytes = spec.getStandardTileSizeInBytes();

  }

  /**
   * Rounds the specified value up to the nearest multiple of 8. Intended to
   * support the requirement that record sizes be a multiple of 8. Undefined
   * for
   * negative numbers.
   *
   * @param value positive integer to be rounded up to a multiple of 8
   * @return a positive integer
   */
  private int multipleOf8(int value) {
    return (value + 7) & 0x7ffffff8;
  }

  /**
   * Indicates whether a tile exists in the file-based tile store.
   *
   * @param tileIndex a positive integer
   * @return true if the tile exists in the tile store, otherwise false.
   */
  boolean doesTileExist(int tileIndex) {
    return tilePositionIndex.getFilePosition(tileIndex) != 0;
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
      braf.seek(surplusPos + 4);
      braf.leWriteInt(RECORD_TYPE_FREESPACE);
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
    //assert (posToStore & 0x07L) == 0 : "Post to store  size not multiple of 8";
    return posToStore;
  }

  void fileSpaceDealloc(long releasePos) throws IOException {
    // the tile was previously written to the file.
    // replace it with the current tile
    braf.seek(releasePos);
    int releaseSize = braf.leReadInt();
    assert releaseSize > 0 : "read negative or zero number at tile position";
    braf.seek(releasePos + 4);
    braf.leWriteInt(RECORD_TYPE_FREESPACE);

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

    // see if we can merge the prior block with the released block.
    if (prior != null && prior.filePos + prior.blockSize == releasePos) {
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
      braf.leWriteInt(prior.blockSize);
      braf.leWriteInt(RECORD_TYPE_FREESPACE); // should already have been set
      return;
    }

    // the released block was not merged with the prior, see if it should
    // be merged with the next
    if (next != null && releasePos + releaseSize == next.filePos) {
      // for the merger, we don't create a new node or modify the
      // links in the list...  we just adjust the file posiiton
      // of the next node back to the released position
      next.filePos = releasePos;
      next.blockSize += releaseSize;
      braf.seek(next.filePos);
      braf.leWriteInt(next.blockSize);
      braf.leWriteInt(RECORD_TYPE_FREESPACE);
      return;
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

  void writeTile(RasterTile tile) throws IOException {
    // In its uncompressed format, the organization of the
    // output record is as follows:
    //     1.  Record size (positive integer)
    //     2.  Tile index (positive integer)
    //     3.  N-element sets of
    //            Length of data for element 
    //               a. If length==standardSize, uncompressed element data
    //               b. If length<standardSize, compressed element data
    //     4.  Checksum (zero if checksums are not enabled)
    // 
    // The compressed element data is in an opaque format.  Note that even
    // when compression is enabled, data with a high degree of randomness
    // (high information entropy) will not compress well. In fact, sometimes
    //  the "compressed" form of the data can be larger than the standard size.
    //  In such cases, the code uses an uncompressed form instead.  So it is
    // possible to have a mix of compressed and non-compressed data within
    // the same tile.
    //
    // Because all records must start on file position
    // which is a multiple of 8, we round the sizeToStore up to the
    // nearset multiple of 8 (if necessary).
    int tileIndex = tile.tileIndex;
    int payloadSize
      = 4 * tile.elements.length
      + standardTileSizeInBytes;
    // size to store includes record header, payload, and checksum
    int sizeToStore = multipleOf8(RECORD_HEADER_SIZE + payloadSize + 4);
    long posToStore;

    nTileWrites++;

    long initialFilePos = tilePositionIndex.getFilePosition(tileIndex);
    assert initialFilePos >= 0 : "Invalid file position";

    if (!tile.hasValidData()) {
      if (initialFilePos > 0) {
        fileSpaceDealloc(initialFilePos);
        tilePositionIndex.setFilePosition(tileIndex, 0);
      }
      return;
    }

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
        tilePositionIndex.setFilePosition(tileIndex, 0);
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
        int compressedSize = multipleOf8(RECORD_HEADER_SIZE + packing.length + 4);
        if (compressedSize < sizeToStore) {
          posToStore = fileSpaceAlloc(compressedSize);
          if (posToStore > MAX_NON_EXTENDED_FILE_POS
            && !spec.isExtendedFileSizeEnabled) {
            // see explanation above
            throw new IOException(FILE_POS_TOO_BIG);
          }
          tilePositionIndex.setFilePosition(tileIndex, posToStore);
          braf.seek(posToStore);
          // store header
          braf.leWriteInt(compressedSize);
          braf.leWriteInt(tileIndex);
          braf.writeFully(packing, 0, packing.length);
          int sizeStoredSoFar = RECORD_HEADER_SIZE + packing.length;
          for (int i = sizeStoredSoFar; i < compressedSize; i++) {
            braf.writeByte(0);
          }
          storeChecksumIfEnabled(posToStore, compressedSize);
          braf.flush();
          return;
        }
      }
    }

    if (initialFilePos == 0) {
      posToStore = fileSpaceAlloc(sizeToStore);
      if (posToStore > MAX_NON_EXTENDED_FILE_POS && !spec.isExtendedFileSizeEnabled) {
        // see explanation above
        throw new IOException(FILE_POS_TOO_BIG);
      }
      // set the position, seek the start of the record,
      // and write the header
      tilePositionIndex.setFilePosition(tileIndex, posToStore);
      braf.seek(posToStore);
      braf.leWriteInt(sizeToStore);
      braf.leWriteInt(tileIndex);
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
    storeChecksumIfEnabled(posToStore, sizeToStore);
    braf.flush();
  }

  void storeChecksumIfEnabled(long offset, int sizeToStore) throws IOException {
    if (spec.isChecksumEnabled()) {
      braf.seek(offset);
      byte[] bytes = new byte[sizeToStore - 4];
      braf.readFully(bytes);
      GridfourCRC32C crc32 = new GridfourCRC32C();
      crc32.update(bytes);
      braf.leWriteInt((int) crc32.getValue());
    }
  }

  void readTile(RasterTile tile) throws IOException {
    int tileIndex = tile.tileIndex;

    long filePos = tilePositionIndex.getFilePosition(tileIndex);
    if (filePos == 0) {
      tile.setToNullState();
      return;
    }

    nTileReads++;
    braf.seek(filePos);
    braf.skipBytes(8);  // skip recordSize and tileIndex, could be used for diagnostics.
    //  int recordSize = braf.leReadInt();
    //  assert recordSize >= 0 :
    //    "negative packing size for tile on file, tile.index=" + tileIndex;
    //  int tileIndexFromFile = braf.leReadInt();
    //  assert tileIndexFromFile == tileIndex : "incorrect tile index on file";
    tile.readStandardFormat(braf, codecMaster);
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
      int recordType = braf.leReadInt();
      if (recordType >= 0) {
        // it's a tile record
        int tileIndex = recordType;
        if (tileIndex >= maxTileIndex) {
          throw new IOException("Incorrect tile index read from file " + tileIndex);
        } else {
          tilePositionIndex.setFilePosition(tileIndex, filePos);
        }
      } else if (recordType == RECORD_TYPE_FREESPACE) {
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

      } else if (recordType == RECORD_TYPE_METADATA) {
        GvrsMetadataReference gmr = GvrsMetadata.readMetadataRef(braf, filePos);
        metadataRefMap.put(gmr.getKey(), gmr);
      } else {
        throw new IOException("Undefined record code " + recordType);
      }
      filePos += recordSize;
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

    ps.format("GVRS Metadata Elements:  %d%n", metadataRefMap.size());

    if (!metadataRefMap.isEmpty()) {
      List<GvrsMetadataReference> trackerList = getMetadataReferences(false);
      Collections.sort(trackerList, new Comparator<GvrsMetadataReference>() {
        @Override
        public int compare(GvrsMetadataReference o1, GvrsMetadataReference o2) {
          int test = o1.name.compareTo(o2.name);
          if (test == 0) {
            test = Integer.compare(o1.recordID, o2.recordID);
          }
          return test;
        }
      });

      ps.println("      User ID                  Record ID");
      int k = 0;
      for (GvrsMetadataReference gmd : trackerList) {
        k++;
        ps.format("%3d.  %-24.24s   %8d   %s%n",
          k, gmd.name, gmd.recordID, gmd.dataType.name());
      }
    }

  }

  /**
   * Allocates space for storing a record. The record type is opaque to the
   * tile store, but is assumed to not be a tile.
   *
   * @param recordType an integer value indicating the record type
   * @param contentSize the size of the content to be stored (not counting
   * overhead elements)
   * @return if successful, a valid file position for writing the content of
   * the non-tile record.
   */
  long allocateNonTileRecord(int recordType, int contentSize)
    throws IOException {
    if (recordType >= 0) {
      throw new IOException(
        "Internal error, non-tile record type must be negative number");
    }
    // allocatee space for the record header (8 bytes), 
    // the specified content (including its own header, if appropriate),
    // and the checksum.
    int n = multipleOf8(RECORD_HEADER_SIZE + contentSize + 4);
    long posToStore = fileSpaceAlloc(n);
    if (posToStore > MAX_NON_EXTENDED_FILE_POS && !spec.isExtendedFileSizeEnabled) {
      // see explanation above
      throw new IOException(FILE_POS_TOO_BIG);
    }
    braf.seek(posToStore);
    braf.leWriteInt(n);
    braf.leWriteInt(recordType);

    // just in case we can't trust the application code to
    // fully write its content, we write a set of zeroes to the file.
    // while this action has a small performance cost, the assumption
    // is that non-tile records are only a small part of the over all
    // file content and the overhead doesn't matter.
    //   note that this calculation also includes the checksum
 
    byte[] zero = new byte[n-RECORD_HEADER_SIZE];
    braf.writeFully(zero);

    // move into position to write the content
    braf.seek(posToStore + RECORD_HEADER_SIZE);
    return posToStore;
  }

  /**
   * Gets a list of the trackers currently stored in this instance.
   * If desired, the list can be sorted by file position (offset).
   * This feature is useful when reading a large number of objects from
   * the file because it can speed the operation by ensuring in-order
   * file access.
   *
   * @param sortByOffset true if trackers are to be sorted by file position.
   * @return a valid, potentially empty list.
   */
  List<GvrsMetadataReference> getMetadataReferences(boolean sortByOffset) {
    Collection<GvrsMetadataReference> values = metadataRefMap.values();
    List<GvrsMetadataReference> list = new ArrayList<>();
    for (GvrsMetadataReference tracker : values) {
      list.add(tracker);
    }
    if (sortByOffset) {
      // To provide efficient file access, sort the trackers
      // by file position (offset)
      Collections.sort(list, new Comparator<GvrsMetadataReference>() {
        @Override
        public int compare(GvrsMetadataReference o1, GvrsMetadataReference o2) {
          return Long.compare(o1.offset, o2.offset);
        }
      });
    }
    return list;
  }

  GvrsMetadata readMetadata(String name, int recordID) throws IOException {
    String key = GvrsMetadataReference.formatKey(name, recordID);
    GvrsMetadataReference ref = metadataRefMap.get(key);
    if (ref == null) {
      return null;
    }
    braf.seek(ref.offset + RECORD_HEADER_SIZE);
    return new GvrsMetadata(braf);
  }

  void writeMetadata(GvrsMetadata metadata) throws IOException {
    if (metadata == null) {
      throw new IllegalArgumentException("Null reference for metadata");
    }
    // If the metadata specifies a unique record ID, then there can
    // only be one matching instance in the table.  If it does not
    // specify a unique record ID, we synthesize one by searching the
    // existing records (if any) and finding the maximum record ID
    // that is already in use.
    int recordID;
    String key;
    if (metadata.uniqueRecordID) {
      recordID = metadata.recordID;
      key = GvrsMetadataReference.formatKey(metadata.name, metadata.recordID);
      GvrsMetadataReference tracker = metadataRefMap.get(key);
      if (tracker != null) {
        // remove the old metadata
        fileSpaceDealloc(tracker.offset);
        metadataRefMap.remove(key);
      }
    } else {
      int maxRecordID = Integer.MIN_VALUE;
      Collection<GvrsMetadataReference> values = metadataRefMap.values();
      for (GvrsMetadataReference ref : values) {
        if (ref.name.equals(metadata.name)) {
          if (ref.recordID > maxRecordID) {
            maxRecordID = ref.recordID;
          }
        }
      }
      if (maxRecordID == Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
          "Unable to resolve record ID conflict for "
          + metadata.name);
      }
      if (maxRecordID < 0) {
        recordID = 1;
      } else {
        recordID = maxRecordID + 1;
      }
      key = GvrsMetadataReference.formatKey(metadata.name, recordID);
    }
    int nBytes = metadata.getStorageSize();
    long offset = allocateNonTileRecord(RECORD_TYPE_METADATA, nBytes);
    GvrsMetadataReference mRef = new GvrsMetadataReference(
      metadata.name, recordID, metadata.dataType, offset);
    metadataRefMap.put(key, mRef);

    braf.seek(offset + RECORD_HEADER_SIZE);
    metadata.write(braf);

    // The block allocation already filled the extra bytes for the record
    // with zeroes. write the checksum, if enabled
    int nBytesToStore = multipleOf8(RECORD_HEADER_SIZE + nBytes + 4);
    storeChecksumIfEnabled(offset, nBytesToStore);
  }

  void analyzeAndReport(PrintStream ps) throws IOException {

    int nCompressedTiles = 0;
    int nNonCompressedTiles = 0;
    long nonCompressedBytes = 0;

    int nTiles = spec.nRowsOfTiles * spec.nColsOfTiles;
    for (int tileIndex = 0; tileIndex < nTiles; tileIndex++) {
      long filePos = tilePositionIndex.getFilePosition(tileIndex);
      if (filePos == 0) {
        continue;
      }
      braf.seek(filePos);
      int recordSize = braf.leReadInt();
      assert recordSize >= 0 : "negative packing size for tile on file";
      int tileIndexFromFile = braf.leReadInt();
      assert tileIndexFromFile == tileIndex : "incorrect tile index on file";

      boolean compressed = false;
      for (int iElement = 0; iElement < spec.getNumberOfElements(); iElement++) {
        GvrsElementSpecification eSpec = spec.elementSpecifications.get(iElement);
        int standardSize = spec.nCellsInTile * eSpec.dataType.bytesPerSample;
        standardSize = (standardSize + 3) & 0x7ffffffc; // adjustment for short type
        int n = braf.leReadInt();
        if (n == standardSize) {
          // no statistics are collected for standard size elements.
          braf.skipBytes(n);
        } else {
          compressed = true;
          byte[] packing = new byte[n];
          braf.readFully(packing);
          codecMaster.analyze(spec.nRowsInTile, spec.nColsInTile, packing);
        }
      }
      if (compressed) {
        nCompressedTiles++;
      } else {
        nNonCompressedTiles++;
      }
    }
    codecMaster.reportAndClearAnalysisData(ps, spec.nRowsOfTiles * spec.nColsOfTiles);
    if (nCompressedTiles > 0 && nNonCompressedTiles > 0) {
      int n = nCompressedTiles + nNonCompressedTiles;
      double percentNonCompressed
        = 100.0 * (double) nNonCompressedTiles / (double) n;
      ps.format("Non Compressed%n");
      ps.format("                           Times Used        bits/sym    Bytes Stored%n");

      ps.format("                        %8d (%4.1f %%)      %4.1f   %d bytes, %4.2f MB%n",
        nNonCompressedTiles, percentNonCompressed, 32.0,
        nonCompressedBytes, nonCompressedBytes / (1024.0 * 1024.0));

    }
  }

  /**
   * Gets a count of the number of tiles that have been populated with values
   * at some point during the life span of the file. Note that even a tile
   * that is populated with null values is considered "populated".
   *
   * @return a positive integer.
   */
  int getCountOfPopulatedTiles() {
    int k = 0;
    int nTiles = spec.nRowsOfTiles * spec.nColsOfTiles;
    for (int tileIndex = 0; tileIndex < nTiles; tileIndex++) {
      long tilePosition = tilePositionIndex.getFilePosition(tileIndex);
      if (tilePosition != 0) {
        k++;
      }
    }

    return k;
  }

  /**
   * Write the index record for the tile-positions, free-space, and
   * metadata.
   *
   * @return the file position of the index record
   * @throws IOException in the event of an unhandled I/O exception
   */
  long writeIndexRecord() throws IOException {

    int sizeTileIndex = tilePositionIndex.getStorageSize();

    // note that for the following computations, the
    // file positions are stored as 8 byte longs.  This approach
    // is different from that used for tiles (which may be based on
    // either 8 bytes or the 4-byte compact file positions). 
    // The reason for this variation is to simplify the code.
    // The assumption behind this design choice is that the number
    // of metadata and free-space records will be small compared
    // to the number of tiles. Thus the extra overhead is less
    // important than it would be for tiles.
    int nFreeNodes = 0;
    FreeNode node = freeList;
    while (node != null) {
      nFreeNodes++;
      node = node.next;
    }
    int sizeFreeNodes = 4 + nFreeNodes * 12;  // 4 for count, 12 for data

    List<GvrsMetadataReference> gmrList = this.getMetadataReferences(true);
    int sizeMetadata = 4;  // 4 bytes for count of metadata records
    for (GvrsMetadataReference gmr : gmrList) {
      sizeMetadata += 8; // gmr.offset
      sizeMetadata += 2 + gmr.name.length();
      sizeMetadata += 4; // size of record ID
      sizeMetadata++; // size of code value
    }

    int sizeIndexContent
      = sizeTileIndex
      + sizeFreeNodes
      + sizeMetadata;
    // allocate space for the index record and set the file
    // position to the start of the record (just past the header).
    // It is possible that the allocation would actually reduce the size
    // of the free list, and thus the size of the index record
    // might be somewhat smaller than the amount of space that
    // was computed.  That doesn't matter because the size
    // of the index shrinks rather than grows, and there will be
    // sufficient space to store it.
    long filePos
      = allocateNonTileRecord(RECORD_TYPE_TILE_INDEX, sizeIndexContent);
    tilePositionIndex.writeTilePositions(braf);

    braf.leWriteInt(nFreeNodes);
    node = freeList;
    while (node != null) {
      braf.leWriteLong(node.filePos);
      braf.leWriteInt(node.blockSize);
      node = node.next;
    }

    braf.leWriteInt(gmrList.size());
    for (GvrsMetadataReference gmr : gmrList) {
      braf.leWriteLong(gmr.offset);
      braf.writeUTF(gmr.name);
      braf.leWriteInt(gmr.recordID);
      braf.writeByte((byte) gmr.dataType.getCodeValue());
    }

    // The block allocation already filled the extra bytes for the record
    // with zeroes. write the checksum, if enabled
    int nBytesToStore = multipleOf8(RECORD_HEADER_SIZE + sizeIndexContent + 4);
    storeChecksumIfEnabled(filePos, nBytesToStore);
    braf.flush();
    return filePos;
  }

  void readIndexRecord(long filePosIndexRecord) throws IOException {

    braf.seek(filePosIndexRecord + RECORD_HEADER_SIZE);
    tilePositionIndex.readTilePositions(braf);

    int nFreeNodes = braf.leReadInt();
    for (int iFree = 0; iFree < nFreeNodes; iFree++) {
      long freePos = braf.leReadLong();
      int freeSize = braf.leReadInt();
      FreeNode node = new FreeNode(freePos, freeSize);
      node.next = freeList;
      freeList = node;
    }

    int nMetadataRecord = braf.leReadInt();
    for (int i = 0; i < nMetadataRecord; i++) {
      long recordPos = braf.leReadLong();
      String name = braf.readUTF();
      int recordID = braf.leReadInt();
      int typeCode = braf.readByte();
      GvrsMetadataType metadataType = GvrsMetadataType.valueOf(typeCode);
      GvrsMetadataReference gmr = new GvrsMetadataReference(name, recordID, metadataType, recordPos);
      metadataRefMap.put(gmr.getKey(), gmr);
    }

  }

}
