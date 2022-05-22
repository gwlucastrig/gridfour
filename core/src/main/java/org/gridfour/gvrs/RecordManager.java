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
  static final int RECORD_OVERHEAD_SIZE = 12;  // 3 4-byte integers

  private static final int MIN_FREE_BLOCK_SIZE = 32;

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
  private final int standardTileDataSizeInBytes;
  private final ITilePositionIndex tilePositionIndex;

  private FreeNode freeList;
  private long expectedFileSize;
  private long allocMostRecentPos;
  private int allocMostRecentSize;

  int nTileReads;
  int nTileWrites;

  final HashMap<String, GvrsMetadataReference> metadataRefMap = new HashMap<>();

  boolean writeFailure;

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
    if (spec.isExtendedFileSizeEnabled()) {
      tilePositionIndex = new TilePositionExtendedIndex(spec);
    } else {
      tilePositionIndex = new TilePositionIndex(spec);
    }
    standardTileDataSizeInBytes = spec.getStandardTileSizeInBytes();
    expectedFileSize = braf.getFileSize();

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

  /**
   * Initialize a non-free-space record and set the position to
   * the position at which content is to be written. This will
   * 8 bytes past the allocated file space position.
   *
   * @param recordPos the position at which to store the record header
   * @param recordSize the full size of the record to store
   * @param recordType the type of record to store
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  private void fileSpaceInitRecord(long recordPos, int recordSize, RecordType recordType) throws IOException {
    // write the record header
    allocMostRecentPos = recordPos;
    allocMostRecentSize = recordSize;

    braf.seek(recordPos);
    braf.leWriteInt(recordSize);
    braf.leWriteInt(recordType.getCodeValue());
    if (recordType != RecordType.Tile && recordType != RecordType.Freespace) {
      byte[] zero = new byte[recordSize - RECORD_HEADER_SIZE];
      braf.writeFully(zero);
      braf.seek(recordPos + RECORD_HEADER_SIZE);
    }
  }

  private void fileSpaceFinishRecord(long contentPos, int contentSize) throws IOException {
    long recordPos = contentPos - RECORD_HEADER_SIZE;
    if (allocMostRecentPos != recordPos) {
      allocMostRecentPos = recordPos;
      braf.seek(allocMostRecentPos);
      allocMostRecentSize = braf.leReadInt();
      //int typeCode = braf.leReadInt();
      braf.skipBytes(4);
    }

    // The allocation size may be larger than was actually needed
    //      a.  If the needed size was not a multiple of 8
    //      b.  If the allocation re-used a free record that was
    //          slightly larger than needed
    int nPad = allocMostRecentSize - (contentSize + RECORD_HEADER_SIZE);
    byte[] zeroes = new byte[nPad];
    braf.seek(allocMostRecentPos + RECORD_HEADER_SIZE + contentSize);
    braf.writeFully(zeroes);

    if (spec.isChecksumEnabled()) {
      braf.seek(allocMostRecentPos);
      byte[] bytes = new byte[allocMostRecentSize - 4];
      braf.readFully(bytes);
      GridfourCRC32C crc32 = new GridfourCRC32C();
      crc32.update(bytes);
      braf.leWriteInt((int) crc32.getValue());
    }
  }

  /**
   * Allocates a block of the specified size. The input value is treated
   * as the size of the content to be stored in a record, excluding
   * the overhead for record space management (the record header) and
   * the checksum.
   * <p>
   *
   * @param sizeOfContent a valid size specification.
   * @param recordType the record type being stored.
   * @return the position where the record content is to be written.
   * @throws IOException in the event of an unrecoverable IO Exception
   */
  long fileSpaceAlloc(int sizeOfContent, RecordType recordType) throws IOException {
    int sizeToStore = multipleOf8(sizeOfContent + RECORD_OVERHEAD_SIZE);

    int minSizeForSplit = sizeToStore + MIN_FREE_BLOCK_SIZE;
    // We look for a free node that is either the perfect size to store
    // this data or sufficiently large to split.  We do not want too many
    // tiny-sized free blocks to accumulate.  So a block that is only
    // a little bigger than our target will not work.
    //   We search the list for a first found strategy.  We don't look for
    // the best fit, just the first feasible fit.
    FreeNode priorPrior = null;
    FreeNode prior = null;
    FreeNode node = freeList;
    while (node != null) {
      if (node.blockSize == sizeToStore || node.blockSize >= minSizeForSplit) {
        break;
      }
      priorPrior = prior;
      prior = node;
      node = node.next;
    }

    if (node == null) {
      // The free-space search did not find a block large enough to
      // hold the stored element.  Extend the file size.
      //   But first, check the last free node in the list.  If it is
      // at the end of the file, we can reuse the space that it occupies
      // and just extend the file size as necessary
      long fileSize = braf.getFileSize();
      if (prior != null && prior.filePos + prior.blockSize == fileSize) {
        if (priorPrior != null) {
          priorPrior.next = null;
        } else {
          freeList = null;
        }
        expectedFileSize = prior.filePos + sizeToStore;
        fileSpaceInitRecord(prior.filePos, sizeToStore, recordType);
        return prior.filePos + RECORD_HEADER_SIZE;
      }

      expectedFileSize = fileSize + sizeToStore;
      fileSpaceInitRecord(fileSize, sizeToStore, recordType);
      return fileSize + RECORD_HEADER_SIZE;
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
    assert foundSize > 0 : "alloc found negative or zero block size in file";
    assert foundSize >= sizeToStore : "alloc found insufficient block size";
    int surplus = foundSize - sizeToStore;
    if (surplus > 0) {
      long surplusPos = node.filePos + sizeToStore;
      FreeNode surplusNode = new FreeNode(surplusPos, surplus);
      fileSpaceInitRecord(surplusPos, surplus, RecordType.Freespace);
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
    fileSpaceInitRecord(posToStore, sizeToStore, recordType);
    //assert (posToStore & 0x07L) == 0 : "Post to store  size not multiple of 8";
    return posToStore + RECORD_HEADER_SIZE;
  }

  void fileSpaceDealloc(long contentPos) throws IOException {
    long releasePos = contentPos - RECORD_HEADER_SIZE;
    // the tile was previously written to the file.
    // replace it with the current tile
    braf.seek(releasePos);
    int releaseSize = braf.leReadInt();
    assert releaseSize > 0 : "read negative or zero number at tile position";
    braf.seek(releasePos + 4);
    braf.leWriteInt(RecordType.Freespace.getCodeValue());

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
      braf.leWriteInt(RecordType.Freespace.codeValue); // should already have been set
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
      braf.leWriteInt(RecordType.Freespace.codeValue);
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
    // output content is as follows:
    //     1.  Tile index (positive integer)
    //     2.  N-element sets of
    //            Length of data for element
    //               a. If length==standardSize, uncompressed element data
    //               b. If length<standardSize, compressed element data
    //
    // The compressed element data is in an opaque format.  Note that even
    // when compression is enabled, data with a high degree of randomness
    // (high information entropy) will not compress well. In fact, sometimes
    // the "compressed" form of the data can be larger than the standard size.
    // In such cases, the code uses an uncompressed form instead.  So it is
    // possible to have a mix of compressed and non-compressed data within
    // the same tile.
    //
    int tileIndex = tile.tileIndex;
    int payloadSize
      = 4 + 4 * tile.elements.length
      + standardTileDataSizeInBytes;
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
        int compressedSize = 4 + packing.length;
        if (compressedSize < payloadSize) {
          posToStore = fileSpaceAlloc(compressedSize, RecordType.Tile);
          if (posToStore > MAX_NON_EXTENDED_FILE_POS
            && !spec.isExtendedFileSizeEnabled) {
            // see explanation above
            writeFailure = true;
            throw new IOException(FILE_POS_TOO_BIG);
          }
          tilePositionIndex.setFilePosition(tileIndex, posToStore);
          braf.seek(posToStore);
          braf.leWriteInt(tileIndex);
          braf.writeFully(packing, 0, packing.length);
          fileSpaceFinishRecord(posToStore, compressedSize);
          return;
        }
      }
    }

    if (initialFilePos == 0) {
      posToStore = fileSpaceAlloc(payloadSize, RecordType.Tile);
      if (posToStore > MAX_NON_EXTENDED_FILE_POS && !spec.isExtendedFileSizeEnabled) {
        // see explanation above
        writeFailure = true;
        throw new IOException(FILE_POS_TOO_BIG);
      }
      // set the position, seek the start of the record,
      // and write the header
      tilePositionIndex.setFilePosition(tileIndex, posToStore);
      braf.seek(posToStore);
      braf.leWriteInt(tileIndex);
    } else {
      // we will be re-writing the record in its same position
      // position file just past the header
      posToStore = initialFilePos;
      braf.seek(posToStore + 4);
    }

    tile.writeStandardFormat(braf);
    fileSpaceFinishRecord(posToStore, payloadSize);

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
    //braf.skipBytes(4);  // skip tileIndex, could be used for diagnostics.
    int tileIndexFromFile = braf.leReadInt();
    assert tileIndexFromFile == tileIndex : "incorrect tile index on file";
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
      int recordTypeCode = braf.leReadInt();
      RecordType recordType = RecordType.valueOf(recordTypeCode);
      if (recordType == null) {
        throw new IOException("Invalid record-type code " + recordTypeCode);
      }

      if (recordType == RecordType.Tile) {
        // it's a tile record
        int tileIndex = braf.leReadInt();
        if (tileIndex >= maxTileIndex) {
          throw new IOException("Incorrect tile index read from file " + tileIndex);
        } else {
          tilePositionIndex.setFilePosition(tileIndex, filePos);
        }
      } else if (recordType == RecordType.Freespace) {
        // add the block of file space to the free list.
        // the free list is ordered by file position, so the new node
        // goes on the end of the list.
        FreeNode node = new FreeNode(filePos, recordSize);
        if (freeListEnd == null) {
          freeList = node;
          freeListEnd = node;
        } else {
          freeListEnd.next = node;
          freeListEnd = node;
        }
      } else if (recordType == RecordType.Metadata) {
        GvrsMetadataReference gmr = GvrsMetadata.readMetadataRef(braf, filePos);
        metadataRefMap.put(gmr.getKey(), gmr);
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
    braf.seek(ref.offset);
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
        if (ref.name.equals(metadata.name) && ref.recordID > maxRecordID) {
            maxRecordID = ref.recordID;
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
    long offset = fileSpaceAlloc(nBytes, RecordType.Metadata);
    GvrsMetadataReference mRef = new GvrsMetadataReference(
      metadata.name, recordID, metadata.dataType, offset);
    metadataRefMap.put(key, mRef);

    braf.seek(offset);
    metadata.write(braf, recordID);
    fileSpaceFinishRecord(offset, nBytes);
  }

  void deleteMetadata(String name, int recordID) throws IOException {
      String key = GvrsMetadataReference.formatKey(name, recordID);
      GvrsMetadataReference tracker = metadataRefMap.get(key);
      if (tracker != null) {
        // remove the old metadata
        fileSpaceDealloc(tracker.offset);
        metadataRefMap.remove(key);
      }
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
    int populatedTiles = nCompressedTiles + nNonCompressedTiles;
    codecMaster.reportAndClearAnalysisData(ps, populatedTiles);
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

  void readMetadataIndexRecord(long filePosMetadataIndexRecord) throws IOException {
    if (filePosMetadataIndexRecord == 0) {
      return;
    }
    braf.seek(filePosMetadataIndexRecord);
    int nMetadataRecord = braf.leReadInt();
    for (int i = 0; i < nMetadataRecord; i++) {
      long recordPos = braf.leReadLong();
      String name = braf.leReadUTF();
      int recordID = braf.leReadInt();
      int typeCode = braf.readByte();
      GvrsMetadataType metadataType = GvrsMetadataType.valueOf(typeCode);
      GvrsMetadataReference g = new GvrsMetadataReference(name, recordID, metadataType, recordPos);
      metadataRefMap.put(g.getKey(), g);
    }

  }

  void readTileIndexRecord(long filePosTileIndexRecord) throws IOException {
    // 4 bytes are reserved for future use
    // eventually, we may have different kinds of tile indexes and
    // they will tell us which variation is in use.
    braf.seek(filePosTileIndexRecord + 4);
    tilePositionIndex.readTilePositions(braf);
  }

  /**
   * Write the index record for the tile-positions, free-space, and
   * metadata.
   *
   * @return the file position of the index record
   * @throws IOException in the event of an unhandled I/O exception
   */
  long writeTileIndexRecord() throws IOException {
    int sizeTileIndex = tilePositionIndex.getStorageSize();
    sizeTileIndex += 4;
    long indexPos = fileSpaceAlloc(sizeTileIndex, RecordType.TileIndex);
    braf.leWriteInt(0);
    tilePositionIndex.writeTilePositions(braf);
    fileSpaceFinishRecord(indexPos, sizeTileIndex);
    return indexPos;
  }

  void readFreespaceIndexRecord(long filePosFreeSpaceIndexRecord) throws IOException {
    if(filePosFreeSpaceIndexRecord == 0){
      return;
    }
    braf.seek(filePosFreeSpaceIndexRecord);
    int nFreeNodes = braf.leReadInt();
    FreeNode lastNode = null;
    for (int iFree = 0; iFree < nFreeNodes; iFree++) {
      long freePos = braf.leReadLong();
      int freeSize = braf.leReadInt();
      FreeNode node = new FreeNode(freePos, freeSize);
      if (lastNode == null) {
        freeList = node;
      } else {
        lastNode.next = node;
      }
      lastNode = node;
    }
  }

  long writeFreeSpaceIndexRecord() throws IOException {
    int nFreeNodes = 0;
    FreeNode node = freeList;
    while (node != null) {
      nFreeNodes++;
      node = node.next;
    }
    if(nFreeNodes == 0){
      return 0;
    }
    int sizeFreeNodes = 4 + nFreeNodes * 12;  // 4 for count, 12 for data
    long indexPos = fileSpaceAlloc(sizeFreeNodes, RecordType.FreespaceIndex);

    // Allocating space to store this record may have claimed free space
    // that was covered by one of the free nodes.  Thus the number of
    // free nodes could decrease by 1.  So it is necessary to count free nodes
    // again.  The storage size actually used may be 12 bytes less than
    // allocated, but we accept the wasted space for the sake of simplicity.
    nFreeNodes = 0;
    node = freeList;
    while (node != null) {
      nFreeNodes++;
      node = node.next;
    }
    sizeFreeNodes = 4 + nFreeNodes * 12;

    braf.leWriteInt(nFreeNodes);
    node = freeList;
    for (int i = 0; i < nFreeNodes; i++) {
      braf.leWriteLong(node.filePos);
      braf.leWriteInt(node.blockSize);
      node = node.next;
    }

    fileSpaceFinishRecord(indexPos, sizeFreeNodes);
    return indexPos;
  }

  long writeMetadataIndexRecord() throws IOException {
    List<GvrsMetadataReference> gmrList = this.getMetadataReferences(true);
    if (gmrList.isEmpty()) {
      return 0;
    }

    int sizeMetadata = 4;  // 4 bytes for count of metadata records
    for (GvrsMetadataReference gmr : gmrList) {
      sizeMetadata += 8; // gmr.offset
      sizeMetadata += 2 + gmr.name.length();
      sizeMetadata += 4; // size of record ID
      sizeMetadata++; // size of data-type code value
    }

    long indexPos = fileSpaceAlloc(sizeMetadata, RecordType.MetadataIndex);
    braf.leWriteInt(gmrList.size());
    for (GvrsMetadataReference gmr : gmrList) {
      braf.leWriteLong(gmr.offset);
      braf.leWriteUTF(gmr.name);
      braf.leWriteInt(gmr.recordID);
      braf.writeByte((byte) gmr.dataType.getCodeValue());
    }

    fileSpaceFinishRecord(indexPos, sizeMetadata);
    return indexPos;
  }

  void readMetadataIndex(long filePosMetadataIndexRecord) throws IOException {
    int nMetadataRecord = braf.leReadInt();
    for (int i = 0; i < nMetadataRecord; i++) {
      long recordPos = braf.leReadLong();
      String name = braf.leReadUTF();
      int recordID = braf.leReadInt();
      int typeCode = braf.readByte();
      GvrsMetadataType metadataType = GvrsMetadataType.valueOf(typeCode);
      GvrsMetadataReference gmr = new GvrsMetadataReference(name, recordID, metadataType, recordPos);
      metadataRefMap.put(gmr.getKey(), gmr);
    }

  }

  long getExpectedFileSize() {
    return expectedFileSize;
  }

  /**
   * Performs a scan of a GVRS file to collect statistics on file space
   * allocation. Intended to support testing and development
   *
   * @return a valid instance
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  RecordManagerStats scanForFileSpaceStats() throws IOException {

    long fileSize = braf.getFileSize();
    long filePos = basePosition;
    long sizeFreeSpace = 0;
    long sizeAllocatedSpace = 0;
    while (filePos < fileSize - RECORD_HEADER_SIZE) {
      braf.seek(filePos);
      int recordSize = braf.leReadInt();
      if (recordSize == 0) {
        break;
      }
      int recordType = braf.leReadInt();
      if (recordType == RecordType.Freespace.codeValue) {
        sizeFreeSpace += recordSize;
      } else {
        sizeAllocatedSpace += recordSize;
      }

      filePos += recordSize;
    }
    return new RecordManagerStats(sizeFreeSpace, sizeAllocatedSpace);
  }
}
