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
 * 11/2021  G. Lucas     Created  
 *
 * Notes:
 *
 *  TO DO:  Some kind of mechanism for recording messages for
 *          bad content?   Or should we go back to throwing exceptions?
 *
 *  The Plan for this Class:  The plan is to continue to add additional
 *  sanity checks to detect bad fields or other problematic elements.
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.gridfour.gvrs.RecordManager.RECORD_HEADER_SIZE;
import static org.gridfour.gvrs.RecordManager.RECORD_OVERHEAD_SIZE;
import org.gridfour.io.BufferedRandomAccessFile;
import org.gridfour.util.GridfourCRC32C;

/**
 * Provides a utility for inspecting a GVRS file to ensure that the
 * data in the file is correctly structured and readable by applications.
 * If checksums are enabled within the file, it also inspects the
 * checksum values.
 * <p>
 * This class is not fully implemented at this time
 */
public class GvrsFileInspector {

  boolean inspectionFailed;
  boolean inspectionComplete;
  boolean badHeader;
  boolean invalidRecordSize;
  boolean badTileIndex;
  long terminationPosition;
  List<Integer> badTiles = new ArrayList<>();

  private GvrsFileSpecification spec;
  private long offsetToContent;
  private int sizeOfHeaderInBytes;

  /**
   * Reads the GVRS file and checks for problematic elements including
   * checksums (if enabled) that would indicate a corrupt file.
   *
   * @param file a valid file reference
   * @throws IOException in the event of a unrecoverable I/O exception.
   */
  public GvrsFileInspector(File file) throws IOException {
    // if checksums are enabled, reading a file with a bad header
    // will throw a checksum error.
    try ( GvrsFile gvrsFile = new GvrsFile(file, "r")) {
      spec = gvrsFile.getSpecification();
      sizeOfHeaderInBytes = gvrsFile.getSizeOfHeader();
      offsetToContent = sizeOfHeaderInBytes;
    } catch (IOException ioex) {
      String s = ioex.getMessage().toLowerCase();
      if (s.contains("checksum")) {
        inspectionFailed = true;
        badHeader = true;
      }
      return;
    }

    try ( BufferedRandomAccessFile braf = new BufferedRandomAccessFile(file, "r")) {
      inspectContent(braf);
    } catch (IOException ioex) {
      inspectionFailed = true;
      throw ioex;
    }
  }

  private void inspectContent(BufferedRandomAccessFile braf) throws IOException {
    int maxTileRecordSize
      = spec.getStandardTileSizeInBytes()
      + spec.getNumberOfElements() * 4
      + RECORD_OVERHEAD_SIZE;
    braf.seek(offsetToContent);

    int maxTileIndex = spec.nRowsOfTiles * spec.nColsOfTiles;
    long fileSize = braf.getFileSize();
    long filePos = offsetToContent;

    boolean previousCheckPassed = true; // at the start, we know that the header passed.
    while (filePos < fileSize - RECORD_HEADER_SIZE) {
      braf.seek(filePos);
      int recordSize = braf.leReadInt();
      if (recordSize == 0) {
        break;
      }

      int tileIndex = braf.leReadInt();
      if (tileIndex < 0) {
        // negative tile indexes are used to introduce non-tile
        // records.
        if (tileIndex != -1 && tileIndex != -2 && tileIndex != -3) {
          throw new IOException("Undefined record code " + tileIndex);
        }
        
      } else {
        if (tileIndex >= maxTileIndex) {
          this.badTileIndex = true;
          terminationPosition = filePos;
          return;
        }
        if (recordSize > maxTileRecordSize) {
          terminationPosition = filePos;
          badTiles.add(tileIndex);
          invalidRecordSize = true;
          return;
        }
      }
      if (spec.isChecksumEnabled() && tileIndex!=-1) {
        braf.seek(filePos);
        byte[] bytes = new byte[recordSize - 4];
        braf.readFully(bytes);
        GridfourCRC32C crc32 = new GridfourCRC32C();
        crc32.update(bytes);
        long checksum0 = crc32.getValue();
        long checksum1 = braf.leReadInt() & 0xffffffffL;
        if (checksum0 != checksum1) {
          badTiles.add(tileIndex);
          inspectionFailed = true;
          // if we've seen two checksum failures in a row,
          // we assume the file is profoundly corrupt and it is not
          // safe to perform further inspection.
          if (!previousCheckPassed) {
            terminationPosition = filePos;
            return;
          }
          previousCheckPassed = false;
        } else {
          previousCheckPassed = true;
        }
      }

      filePos += recordSize;
    }

    inspectionComplete = true;
    terminationPosition = filePos;

  }

  /**
   * Indicates whether the inspection detected a failure
   *
   * @return true if the inspection detected a failure; otherwise, false.
   */
  public boolean didFileFailInspection() {
    return inspectionFailed;
  }

  /**
   * Indicates whether the inspection detected a failure in the
   * header.
   *
   * @return true if the inspection detected a failure in the header.
   */
  public boolean didFileHeaderFailInspction() {
    return badHeader;
  }

  /**
   * Indicates whether the inspection was able to process the entire file.
   * For certain kinds of failures, the inspector is not able to proceed
   * beyond the error position. In such cases, this method will indicate
   * that the entire file was not inspected.
   *
   * @return true if the entire file was inspected; otherwise, false.
   */
  public boolean wasEntireFileInspected() {
    return this.inspectionComplete;
  }

  /**
   * Indicates the last position inspected within the file. If the inspection
   * cannot process the entire file due to an error condition, the the
   * termination position will be recorded.
   *
   * @return a positive integer.
   */
  public long getPositionForInspectionTermination() {
    return this.terminationPosition;
  }

  /**
   * Gets a list of indices for tiles that failed inspection.
   *
   * @return a valid, potentially empty, list.
   */
  public List<Integer> getIndicesForFailedTiles() {
    List<Integer> list = new ArrayList<>();
    list.addAll(this.badTiles);
    return list;
  }

}
