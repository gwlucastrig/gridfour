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
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
public class GvrsInspector {

  final File file;
  final int version;
  final int subversion;
  final int combinedVersion;

  boolean headerIsBad;
  boolean headerFailedChecksum;

  boolean tileDirectoryPassedChecksum;
  boolean tileDirectoryLocated;

  boolean inspectionFailed;
  boolean inspectionPassed;
  boolean inspectionComplete;

  boolean invalidRecordSize;
  boolean badTileIndex;
  long terminationPosition;
  long fileSize;

  List<Integer> badTiles = new ArrayList<>();

  private GvrsFileSpecification spec;
  private long offsetToContent;
  private long offsetToTileDirectory;

  static final int RECORD_HEADER_SIZE = 8;

  /**
   * Reads a file for inspection
   *
   * @param file a valid file reference
   * @throws IOException in the event of a unrecoverable I/O exception.
   */
  public GvrsInspector(File file) throws IOException {
    if (file == null) {
      throw new NullPointerException("Null file reference not supported");
    }

    // The constructor does a pre-test, opening the file and checking
    // the header to see if it will pass checksum (if checksums are activated).
    this.file = file;
    this.fileSize = file.length();
    terminationPosition = 16; // beginning of the header

    try ( BufferedRandomAccessFile braf = new BufferedRandomAccessFile(file, "r")) {
      String identification = braf.readASCII(12);
      if (!RasterFileType.GvrsRaster.getIdentifier().equals(identification)) {
        throw new IOException("Incompatible file type " + identification);
      }
      version = braf.readUnsignedByte();
      subversion = braf.readUnsignedByte();
      combinedVersion = version * 100 + subversion;
      braf.skipBytes(2); // unused, reserved bytes
      if (!GvrsFileSpecification.isVersionSupported(version, subversion)) {
        throw new IOException("Incompatible version " + version + "." + subversion
          + ".  Latest version is "
          + GvrsFileSpecification.VERSION
          + "."
          + GvrsFileSpecification.SUB_VERSION);
      }

      // test the header for checksum
      long headerPos0, headerPos1;
      if (combinedVersion < 104) {
        headerPos0 = 0;
        braf.seek(GvrsFile.FILEPOS_OFFSET_TO_CONTENT_PRE104);
        headerPos1 = braf.leReadLong();
        braf.seek(GvrsFile.FILEPOS_OFFSET_TO_TILE_DIR_PRE104);
        offsetToTileDirectory = braf.leReadLong();
      } else {
        headerPos0 = 16;
        braf.seek(16);
        int sizeOfHeaderInBytes = braf.leReadInt();
        headerPos1 = headerPos0 + sizeOfHeaderInBytes;
        braf.seek(GvrsFile.FILEPOS_OFFSET_TO_TILE_DIR_PRE104);
        offsetToTileDirectory = braf.leReadLong();
      }

      // The header will never be especially large, so we allow a one megabyte
      // cut-off.
      long n = headerPos1 - headerPos0;
      if (n <= 4 || n > 1000000 || headerPos1 > braf.getFileSize()) {
        headerIsBad = true;
        return;
      }
      byte[] scratch = new byte[(int) n - 4];
      braf.seek(headerPos0);
      braf.readFully(scratch);
      long checkSum = braf.leReadInt() & 0xffffffffL;
      if (checkSum != 0) {
        GridfourCRC32C crc32 = new GridfourCRC32C();
        crc32.update(scratch);
        long test = crc32.getValue();
        if (checkSum != test) {
          headerIsBad = true;
          headerFailedChecksum = true;
          terminationPosition = braf.getFilePosition();
        }
      }
    }

    if (headerIsBad) {
      inspectionFailed = true;
    } else {
      // there is no recovery from a bad header
      inspect();
    }

  }

  /**
   * Inspects the content of the file. If the inspection is successful
   * (no errors are detected), this method returns true.
   *
   * @return true if the file passed inspection; otherwise, false
   * @throws IOException in the event of an unrecoverable I/O error.
   */
  private boolean inspect() throws IOException {
    if (inspectionComplete) {
      // the may have called this multiple times.
      return !inspectionFailed;
    }
    if (headerIsBad) {
      return false;
    }
    // if checksums are enabled, reading a file with a bad header
    // will throw a checksum error.
    boolean headerReadSuccessfully = false;
    try ( GvrsFile gvrsFile = new GvrsFile(file, "r")) {
      headerReadSuccessfully = true;
      spec = gvrsFile.getSpecification();
      offsetToContent = gvrsFile.getFilePositionOfContent();
      BufferedRandomAccessFile braf = gvrsFile.getOpenFile();
      inspectContent(braf);
    } catch (IOException ioex) {
      if (!headerReadSuccessfully) {
        headerIsBad = true;
      }
      inspectionFailed = true;
    }

    inspectionPassed = !inspectionFailed;
    return inspectionPassed;

  }

  private void inspectContent(BufferedRandomAccessFile braf) throws IOException {
    int maxTileRecordSize
      = 4
      + spec.getNumberOfElements() * 4
      + spec.getStandardTileSizeInBytes()
      + RECORD_OVERHEAD_SIZE;
    maxTileRecordSize = (maxTileRecordSize + 7) & 0x7ffffff8;
    braf.seek(offsetToContent);

    int maxTileIndex = spec.nRowsOfTiles * spec.nColsOfTiles;
    long fileSize = braf.getFileSize();
    long filePos = offsetToContent;

    if (offsetToTileDirectory != 0) {
      // see if the tile directory passes checksum
      tileDirectoryLocated = true;
      tileDirectoryPassedChecksum
        = testRecordChecksum(braf, offsetToTileDirectory);
    }

    boolean previousCheckPassed = true; // at the start, we know that the header passed.
    while (filePos < fileSize - RECORD_OVERHEAD_SIZE) {
      braf.seek(filePos);
      int recordSize = braf.leReadInt();
      if (recordSize == 0) {
        break;
      }

      int recordTypeCode = braf.readUnsignedByte();
      braf.skipBytes(3); // reserved for future use

      RecordType recordType = RecordType.valueOf(recordTypeCode);
      if (recordType == null) {
        throw new IOException("Invalid record-type code " + recordTypeCode);
      }

      int tileIndex = 0;
      if (recordType == recordType.Tile) {
        tileIndex = braf.leReadInt();
        if (tileIndex < 0 || tileIndex >= maxTileIndex) {
          badTileIndex = true;
          inspectionFailed = true;
          terminationPosition = filePos;
          return;
        }
        if (recordSize > maxTileRecordSize) {
          terminationPosition = filePos;
          badTiles.add(tileIndex);
          invalidRecordSize = true;
          inspectionFailed = true;
          return;
        }
      }

      if (spec.isChecksumEnabled()) {
        GridfourCRC32C crc32 = new GridfourCRC32C();
        if (recordType == RecordType.Freespace) {
          // because the content of a free-space record does not
          // matter, the checksum is computed from just the record header
          braf.seek(filePos);
          byte[] bytes = new byte[8];
          braf.readFully(bytes);
          crc32.update(bytes);
          braf.seek(filePos + recordSize - 4);
        } else {
          braf.seek(filePos);
          byte[] bytes = new byte[recordSize - 4];
          braf.readFully(bytes);
          crc32.update(bytes);
        }
        long checksum0 = crc32.getValue();
        long checksum1 = braf.leReadInt() & 0xffffffffL;
        if (checksum0 != checksum1) {
          if (recordType == RecordType.Tile) {
            badTiles.add(tileIndex);
          }
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
          if (recordType == RecordType.TileDirectory) {
            tileDirectoryPassedChecksum = true;
          }
        }
      }

      filePos += recordSize;
    }

    inspectionComplete = true;
    terminationPosition = filePos;

  }

  boolean testRecordChecksum(BufferedRandomAccessFile braf, long offsetToRecordContent) throws IOException {
    long offsetToRecord = offsetToRecordContent - RECORD_HEADER_SIZE;
    braf.seek(offsetToRecord);

    int recordSize = braf.leReadInt();
    int recordType = braf.readUnsignedByte();
    if (recordType > 6) {
      return false;   // invalid record type
    }
    if (offsetToRecord + recordSize > braf.getFileSize()) {
      return false;
    }

    // TO DO: The number of bytes in a record could be quite large.
    // Perhaps we should read through the record in blocks of 8K.
    braf.seek(offsetToRecord);
    byte[] b = new byte[recordSize - 4];
    braf.readFully(b);
    GridfourCRC32C crc32 = new GridfourCRC32C();
    crc32.update(b);
    long checksum0 = crc32.getValue();
    long checksum1 = braf.leReadInt() & 0xffffffffL;
    return checksum0 == checksum1;
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
    return headerIsBad;
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

  public void summarize(PrintStream ps) {
    ps.println("Inspection results for " + file.getName());
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm z", locale);
    ps.format("Date of Inspection: %s%n", sdFormat.format(date));
    ps.format("Inspected entire file: %s%n", Boolean.toString(inspectionComplete));
    ps.format("Inspection passed:     %s%n", Boolean.toString(!inspectionFailed));
    ps.println("");
    ps.format("Tile directory located: %s%n",
      Boolean.toString(tileDirectoryLocated));
    ps.format("Tile directory passed checksum: %s%n",
      Boolean.toString(tileDirectoryPassedChecksum));
    int nBadTiles = badTiles.size();
    if (nBadTiles > 0) {
      ps.format("Number of bad tiles:            %d%n", nBadTiles);
    }
    if (terminationPosition > 0 && terminationPosition < fileSize) {
      ps.format("Errors prevented survey of entire file, terminated at offset %s%n",
        terminationPosition);
    }
  }

}
