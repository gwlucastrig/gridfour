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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.UUID;
import static org.gridfour.gvrs.RecordManager.RECORD_HEADER_SIZE;

import org.gridfour.io.BufferedRandomAccessFile;
import org.gridfour.util.GridfourCRC32C;

/**
 * Provides methods and data elements for managing raster data in a file.
 */
public class GvrsFile implements Closeable, AutoCloseable {

  private final static long FILEPOS_MODIFICATION_TIME = 32;
  private final static long FILEPOS_OPEN_FOR_WRITING_TIME = 40;

  // Gives the offset to the field in the header that is used to
  // store the file position for the storage of content.
  // The value in stored at this file position is also the size
  // of the file header, in bytes. 
  private final static long FILEPOS_OFFSET_TO_CONTENT = 48;

  private final File file;
  private final GvrsFileSpecification spec;
  private final CodecMaster rasterCodec;
  private final BufferedRandomAccessFile braf;
  private final UUID uuid;
  private boolean isClosed;
  private boolean openedForWriting;
  private boolean indexCreationEnabled;
  private long timeModified;

  // Content begins immediately after the header, so the position
  // of the content is also the size of the header.
  private long filePosContent;
  private int sizeOfHeaderInBytes;

  private final RecordManager recordMan;
  private final RasterTileCache tileCache;
  private final List<GvrsElement> elements = new ArrayList<>();

  /**
   * Creates a new raster file using the specified file and specifications. If
   * the file reference points to an existing file, the old file will be
   * deleted and replaced. The dimensions and structure of the raster
   * file will be taken from the specification argument.
   *
   * @param file a valid file reference giving the path to a new output file
   * in a location with write access.
   * @param specification a valid gvrs raster specification
   * @throws IOException in the event of an unrecoverable I/O error.
   */
  public GvrsFile(File file, GvrsFileSpecification specification)
    throws IOException {
    if (file == null) {
      throw new IOException("Null file reference not supported");
    }
    if (specification == null) {
      throw new IOException("Null specificaiton not supported");
    }
    if (file.exists() && !file.delete()) {
      throw new IOException(
        "Unable to delete existing file: " + file.getPath());
    }
    if (specification.isExtendedFileSizeEnabled) {
      throw new IOException(
        "Unable to create file with extended file size option specified;"
        + " feature not implemented");
    }

    this.openedForWriting = true;
    this.file = file;
    this.spec = new GvrsFileSpecification(specification);
    this.rasterCodec = new CodecMaster(specification.codecList);
    braf = new BufferedRandomAccessFile(file, "rw");

    timeModified = System.currentTimeMillis();

    braf.writeASCII(RasterFileType.GvrsRaster.getIdentifier(), 12);
    braf.writeByte(GvrsFileSpecification.VERSION);
    braf.writeByte(GvrsFileSpecification.SUB_VERSION);
    braf.writeByte(0); // reserved
    braf.writeByte(0); // reserved

    uuid = UUID.randomUUID();
    braf.leWriteLong(uuid.getLeastSignificantBits());
    braf.leWriteLong(uuid.getMostSignificantBits());

    braf.leWriteLong(timeModified);  // time modified
    braf.leWriteLong(timeModified);    // time opened
    braf.leWriteLong(0); // offset to content, also length of the header
    // write a reserved block of 4 longs, which could be divided up in the future
    // or used for other writable file offsets or data
    braf.leWriteLong(0);
    braf.leWriteLong(0);
    braf.leWriteLong(0);
    braf.leWriteLong(0);

    spec.write(braf);

    // write out 8 bytes reserved for future use
    byte[] b = new byte[8];
    braf.writeFully(b);  // reserved

    // The offset to the end of the header needs to be a multiple
    // of 8 in order to support file position compression. The size is
    // not known a priori because it will depend on the structure
    // of the elements in the specification.  At this point,
    // we will need to reserve 4 extra bytes for the checksum
    // and then pad out the record.
    long filePos = braf.getFilePosition();
    filePosContent = ((filePos + 4) + 7) & 0xfffffff8;
    sizeOfHeaderInBytes = (int) filePosContent;
    int padding = (int) (filePosContent - filePos);
    for (int i = 0; i < padding; i++) {
      braf.writeByte(0);
    }

    braf.seek(FILEPOS_OFFSET_TO_CONTENT);
    braf.leWriteLong(filePosContent);
    braf.flush();

    recordMan = new RecordManager(spec, rasterCodec, braf, filePosContent);
    tileCache = new RasterTileCache(spec, recordMan);

    List<CodecHolder> csList = spec.getCompressionCodecs();
    if (!csList.isEmpty()) {
      String scratch = CodecHolder.formatSpecificationString(csList);
      GvrsMetadata codecMetadata = GvrsMetadataConstants.GvrsJavaCodecs.newInstance(0);
      codecMetadata.setString(scratch);
      codecMetadata.setDescription("Class paths for Java compressors");
      GvrsFile.this.writeMetadata(codecMetadata);
    }

    for (GvrsElementSpecification eSpec : specification.elementSpecifications) {
      GvrsElement e = eSpec.makeElement(this);
      elements.add(e);
    }
  }

  /**
   * Open an existing raster file for read or write access.
   * <p>
   * Only one application may write to a file at once. If the existing file
   * was
   * previously opened for writing and not properly closed, it may not be
   * accessible.
   *
   * @param file a valid file
   * @param access a valid access control following the general contract of
   * the
   * Java RandomAccessFile class (valid values, "r", "rw", etc&#46;)
   * @throws IOException in the event of an unrecoverable I/O error
   */
  public GvrsFile(File file, String access) throws IOException {
    if (file == null) {
      throw new IOException("Null file reference not supported");
    }
    if (access == null || access.isEmpty()) {
      throw new IOException(
        "Null or empty access specification not supported");
    }
    if (!file.exists()) {
      throw new IOException("File not found " + file.getPath());
    }

    this.file = file;
    braf = new BufferedRandomAccessFile(file, access);

    String identification = braf.readASCII(12);
    if (!RasterFileType.GvrsRaster.getIdentifier().equals(identification)) {
      throw new IOException("Incompatible file type " + identification);
    }
    int version = braf.readUnsignedByte();
    int subversion = braf.readUnsignedByte();
    braf.skipBytes(2); // unused, reserved bytes
    if (version != GvrsFileSpecification.VERSION
      || subversion != GvrsFileSpecification.SUB_VERSION) {
      throw new IOException("Incompatible version " + version + "." + subversion
        + ".  Expected "
        + GvrsFileSpecification.VERSION
        + "."
        + GvrsFileSpecification.SUB_VERSION);
    }

    long uuidLow = braf.leReadLong();
    long uuidHigh = braf.leReadLong();
    uuid = new UUID(uuidHigh, uuidLow);

    timeModified = braf.leReadLong(); // time modified from old file
    long timeOpenedForWriting = braf.leReadLong();
    if (timeOpenedForWriting != 0) {
      throw new IOException(
        "Attempt to access a file currently opened for writing"
        + " or not properly closed by previous application: "
        + file.getPath());
    }

    filePosContent = braf.leReadLong();
    sizeOfHeaderInBytes = (int) filePosContent;

    // skip the currently reserved block of 32 bytes
    braf.skipBytes(32);
    spec = new GvrsFileSpecification(braf);
    if (spec.isExtendedFileSizeEnabled) {
      braf.close();
      throw new IOException(
        "Unable to access file with extended file size option set,"
        + " feature not implemented");
    }

    if (spec.isChecksumEnabled) {
      braf.seek(sizeOfHeaderInBytes - 4);
      long checksum0 = braf.leReadInt() & 0xffffffffL;
      long checksum1 = tabulateChecksumFromHeader();
      if (checksum0 != checksum1) {
        braf.close();
        throw new IOException("Checksum mismatch in file header");
      }
    }

    boolean writingEnabled = access.toLowerCase().contains("w");
    if (writingEnabled) {
      braf.seek(FILEPOS_OPEN_FOR_WRITING_TIME);
      braf.leWriteLong(System.currentTimeMillis());
      braf.flush();
      openedForWriting = true;
    }

    rasterCodec = new CodecMaster(spec.codecList);
    recordMan = new RecordManager(spec, rasterCodec, braf, filePosContent);
    if (!readIndexFile(timeModified)) {
      recordMan.scanFileForTiles();
    }
    tileCache = new RasterTileCache(spec, recordMan);

    // See if the source file specified Java codecs.
    List<CodecSpecification> codecSpecificationList = new ArrayList<>();
    GvrsMetadata codecMetadata
      = GvrsFile.this.readMetadata(GvrsMetadataConstants.GvrsJavaCodecs.name(), 0);
    if (codecMetadata != null) {
      String codecStr = codecMetadata.getString();
      codecSpecificationList
        = CodecSpecification.specificationStringParse(codecStr);
    }
    spec.integrateCodecSpecificationsFromFile(codecSpecificationList);
    rasterCodec.setCodecs(spec.codecList);

    for (GvrsElementSpecification eSpec : spec.elementSpecifications) {
      GvrsElement e = eSpec.makeElement(this);
      elements.add(e);
    }

  }

  private void resolveCodecs(GvrsFileSpecification fileSpec, List<CodecSpecification> specList) throws IOException {
    List<String> identificationList = fileSpec.codecIdentificationList;
    // List<CodecHolder> holderList = fileSpec.codecList;

    List<CodecHolder> resultList = new ArrayList<>();
    for (String s : identificationList) {
      CodecHolder result;
      CodecHolder registeredHolder = null;
      for (CodecHolder holder : spec.codecList) {
        if (s.equals(holder.getIdentification())) {
          registeredHolder = holder;
          break;
        }
      }
      result = registeredHolder;
      boolean mandatory = registeredHolder == null;
      for (CodecSpecification test : specList) {
        if (s.equals(test.getIdentification())) {
          CodecHolder candidate = test.getHolder(mandatory);
          if (candidate != null) {
            result = candidate;
          }
        }
      }

      resultList.add(result);
    }
    spec.codecList.clear();
    spec.codecList.addAll(resultList);

    rasterCodec.setCodecs(resultList);

  }

  /**
   * Gets a file reference to the file in which the data
   * for this raster is stored.
   *
   * @return a valid file reference.
   */
  public File getFile() {
    return file;
  }

  /**
   * Indicates whether the raster is closed.
   *
   * @return true if the file is closed; otherwise, false
   */
  public boolean isClosed() {
    return isClosed;
  }

  /**
   * Write any in-memory data to the file. This call is required to ensure
   * that
   * any data not yet written to the file is properly recorded.
   * <p>
   * Note that the close() method calls flush() before closing a file.
   *
   * @throws IOException in the event of an I/O error.
   */
  public void flush() throws IOException {
    if (openedForWriting) {
      tileCache.flush();
      braf.flush();
    }
  }

  /**
   * Closes the file and releases all associated resources. If the file is
   * open
   * for writing, any data in the internal buffers will be written to the file
   * before it is closed.
   *
   * @throws IOException in the event of an I/O error.
   */
  @Override
  public void close() throws IOException {
    if (!isClosed) {
      if (openedForWriting) {
        tileCache.flush();
        braf.seek(FILEPOS_MODIFICATION_TIME);
        long closingTime = System.currentTimeMillis();
        braf.leWriteLong(closingTime);
        braf.leWriteLong(0); // opened for writing time
        if (spec.isChecksumEnabled) {
          long checksum = tabulateChecksumFromHeader();
          braf.leWriteInt((int) checksum);
        }
        braf.flush();

        if (indexCreationEnabled) {
          writeIndexFile(closingTime);
        }
        timeModified = closingTime;
      }
      openedForWriting = false;
      isClosed = true;

      // set the elements to an inaccessible state to indicate
      // that the file is closed.
      for (GvrsElement element : elements) {
        element.tileIndex = -1;
        element.tileElement = null;
      }
      braf.close();
    }
  }

  /**
   * Reads the header bytes and tabulates the checksum, leaving the
   * file position right before the checksum position in the file.
   *
   * @return if successful, a valid checksum.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  private long tabulateChecksumFromHeader() throws IOException {
    byte[] bytes = new byte[sizeOfHeaderInBytes - 4];
    braf.seek(0);
    braf.readFully(bytes);
    GridfourCRC32C crc32 = new GridfourCRC32C();
    crc32.update(bytes);
    return crc32.getValue();
  }

  /**
   * Indicates whether the file is opened for writing.
   *
   * @return true if the file is accepting data for storage; otherwise, false.
   */
  public boolean isOpenedForWriting() {
    return openedForWriting;
  }

  /**
   * Scans the file and writes a summary of its content to the specified
   * PrintStream.
   * <p>
   * The analyze option will allow an application to print a report of
   * statistics related to data compression. This process requires accessing
   * the
   * content of tiles and may be somewhat time consuming.
   *
   * @param ps a valid print-stream
   * @param analyze performs an analysis of compressed data
   */
  public void summarize(PrintStream ps, boolean analyze) {
    ps.format("Gvrs Raster File:   " + file.getPath());
    ps.println("");

    spec.summarize(ps);
    Locale locale = Locale.getDefault();
    Date date = new Date(timeModified);
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);

    ps.format("Time last modified:     %s%n", sdFormat.format(date));
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.format("Time last modified:     %s (UTC)%n", sdFormat.format(date));

    recordMan.summarize(ps);
    tileCache.summarize(ps);
    if (analyze && !braf.isClosed()) {
      try {
        recordMan.analyzeAndReport(ps);
      } catch (IOException ioex) {
        ps.format("IOException encountered during analysis: " + ioex.getMessage());
        ioex.printStackTrace(ps);
      }
    }

    if (!braf.isClosed()) {
      long fileSize = braf.getFileSize();
      long n = recordMan.getCountOfPopulatedTiles();
      double avgBitsPerSample = 0;
      if (n > 0) {
        long nSamples = n * spec.getNumberOfCellsInTile();
        avgBitsPerSample = fileSize * 8.0 / nSamples;
      }
      ps.format("%nFile size:                           %12d bytes, %4.2f MB%n",
        fileSize, fileSize / (1024.0 * 1024.0));
      ps.format("Average bits per sample (estimated):     %6.4f%n",
        avgBitsPerSample);
    }
  }

  /**
   * Sets the tile cache size. Tile cache can have a significant effect on
   * both
   * performance and memory use for raster file operations.
   *
   * @param tileCacheSize a positive integer greater than zero.
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  public void setTileCacheSize(int tileCacheSize) throws IOException {
    if (tileCacheSize <= 0) {
      throw new IOException("Cache size of " + tileCacheSize
        + " is not within of valid range");
    }
    tileCache.setTileCacheSize(tileCacheSize);
  }

  /**
   * Sets the tile cache size to one of the standard sizes defined by the
   * specified enumeration. In general, the Large size should be used when
   * creating data products. Size should be based on the anticipated pattern
   * of
   * access for the file.
   *
   * @param cacheSize a valid instance
   * @throws IOException in the event of a non-recoverable I/P exception.
   */
  public void setTileCacheSize(GvrsCacheSize cacheSize) throws IOException {
    switch (cacheSize) {
      case Small:
        setTileCacheSize(4);
        break;
      case Medium:
        setTileCacheSize(16);
        break;
      case Large:
        int nC = spec.nColsOfTiles;
        int nR = spec.nRowsOfTiles;
        int nT = nC > nR ? nC : nR;
        setTileCacheSize(nT + 4);
    }
  }

  /**
   * Gets a safe copy of the gvrs raster specification associated with this
   * file.
   *
   * @return a valid instance.
   */
  public GvrsFileSpecification getSpecification() {
    return new GvrsFileSpecification(spec);
  }

  /**
   * Constructs a new instance of a class that can be used to map a row and
   * column from the raster coordinate system to a tile index and row and
   * column within the tile.
   *
   * @return a valid instance.
   */
  TileAccessIndices getAccessIndices() {
    return new TileAccessIndices(spec);
  }

  /**
   * Sets or clears a flag indicating that the instance should generate an
   * index file when a writable file is closed.
   *
   * @param indexCreationEnabled true if an index is to be created; otherwise
   * false.
   */
  public void setIndexCreationEnabled(boolean indexCreationEnabled) {
    this.indexCreationEnabled = indexCreationEnabled;
  }

  private File getIndexFile() {
    String name = file.getName();
    int extensionIndex = name.lastIndexOf('.');
    if (extensionIndex <= 0 || extensionIndex == name.length() - 1) {
      return null;
    }

    String extension;
    char c = name.charAt(extensionIndex + 1);
    if (Character.isLowerCase(c)) {
      extension = RasterFileType.GvrsIndex.getExtension().toLowerCase();
    } else if (Character.isUpperCase(c)) {
      extension = RasterFileType.GvrsIndex.getExtension().toUpperCase();
    } else {
      return null;
    }

    String indexName = name.substring(0, extensionIndex + 1) + extension;
    File parent = file.getParentFile();
    if (parent == null) {
      return new File(indexName);
    } else {
      return new File(parent, indexName);
    }
  }

  private void writeIndexFile(long closingTime) throws IOException {
    File indexFile = getIndexFile();
    if (indexFile == null) {
      throw new IOException(
        "Unable to resolve index name for " + file.getPath());
    }

    if (indexFile.exists() && !indexFile.delete()) {
      throw new IOException(
        "Unable to delete old index file " + file.getPath());
    }

    BufferedRandomAccessFile idxraf
      = new BufferedRandomAccessFile(indexFile, "rw");
    idxraf.writeASCII(RasterFileType.GvrsIndex.getIdentifier(), 12);
    idxraf.writeByte(GvrsFileSpecification.VERSION);
    idxraf.writeByte(GvrsFileSpecification.SUB_VERSION);
    idxraf.writeByte(0); // reserved
    idxraf.writeByte(0); // reserved

    idxraf.leWriteLong(closingTime);  // time modified
    idxraf.leWriteLong(uuid.getLeastSignificantBits());
    idxraf.leWriteLong(uuid.getMostSignificantBits());
    recordMan.writeTilePositionsToIndexFile(idxraf);
    idxraf.flush();
    idxraf.close();
  }

  private boolean readIndexFile(long timeLastModified) throws IOException {
    File indexFile = getIndexFile();
    if (indexFile == null) {
      throw new IOException("Unable to resolve index name for " + file.getPath());
    }

    if (!indexFile.exists()) {
      return false;
    }

    try ( BufferedRandomAccessFile idxraf
      = new BufferedRandomAccessFile(indexFile, "r");) {

      String s = idxraf.readASCII(12);
      if (!RasterFileType.GvrsIndex.getIdentifier().equals(s)) {
        throw new IOException("Improper identifier found in file "
          + file.getPath() + ": " + s);
      }
      int version = idxraf.readUnsignedByte();
      int subVersion = idxraf.readUnsignedByte();
      if (version != GvrsFileSpecification.VERSION
        || subVersion != GvrsFileSpecification.SUB_VERSION) {
        return false;
      }
      idxraf.skipBytes(2); // reserved
      long indexModificationTime = idxraf.leReadLong();
      if (indexModificationTime != timeLastModified) {
        // the index does not date from the same modification time as
        // the file, so we cannot use it.
        return false;
      }
      long leastSigBits = idxraf.leReadLong();
      long mostSigBits = idxraf.leReadLong();
      UUID indexUuid = new UUID(mostSigBits, leastSigBits);
      if (!indexUuid.equals(uuid)) {
        // not the same file as the index
        return false;
      }

      recordMan.readTilePositionsFromIndexFile(idxraf);
    }

    return true;
  }

  /**
   * Map Cartesian coordinates to grid coordinates storing the row and column
   * in
   * an array in that order. If the x or y coordinate is outside the ranges
   * defined for these parameters, the resulting rows and columns may be
   * outside the range of the valid grid coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * GvrsFileSpecification.setCartesianCoordinates{} method when the
   * associated file was created.
   *
   * @param x a valid floating-point coordinate
   * @param y a valid floating-point coordinate
   * @return an array giving row and column in that order; the results may be
   * non-integral values.
   */
  public double[] mapCartesianToGrid(double x, double y) {
    return spec.mapCartesianToGrid(x, y);
  }

  /**
   * Map grid coordinates to Cartesian coordinates storing the resulting x and
   * y
   * values in an array in that order. If the row or column values are outside
   * the ranges defined for those parameters, the resulting x and y values may
   * be outside the bounds of the standard Cartesian coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * GvrsFileSpecification.setCartesianCoordinates{} method when the
   * associated file was created.
   *
   * @param row a row (may be a non-integral value)
   * @param column a column (may be a non-integral value)
   * @return a valid array giving the Cartesian x and y coordinates in that
   * order.
   */
  public double[] mapGridToCartesian(double row, double column) {
    return spec.mapGridToCartesian(row, column);
  }

  /**
   * Map geographic coordinates to grid coordinates storing the row and column
   * in an array in that order. If the latitude or longitude is outside the
   * ranges defined for these parameters, the resulting rows and columns may
   * be
   * outside the range of the valid grid coordinates
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * GvrsFileSpecification.setGeographicCoordinates{} method when the
   * associated file was created.
   *
   * @param latitude a valid floating-point coordinate
   * @param longitude a valid floating-point coordinate
   * @return an array giving row and column in that order; the results may be
   * non-integral values.
   */
  public double[] mapGeographicToGrid(double latitude, double longitude) {
    return spec.mapGeographicToGrid(latitude, longitude);
  }

  /**
   * Map grid coordinates to Geographic coordinates storing the resulting x
   * and
   * y values in an array in that order. If the row or column values are
   * outside
   * the ranges defined for those parameters, the resulting x and y values may
   * be outside the bounds of the standard Geographic coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * GvrsFileSpecification.setCartesianCoordinates{} method when the
   * associated file was created.
   *
   * @param row the row coordinate (may be non-integral)
   * @param column the column coordinate (may be non-integral)
   * @return a valid array giving row and column in that order.
   */
  public double[] mapGridToGeographic(double row, double column) {
    return spec.mapGridToGeographic(row, column);
  }

  /**
   * Reads the complete set of metadata records stored in the file.
   *
   * @return a valid, potentially empty, list instance.
   * @throws IOException in the event of unrecoverable I/O exception
   */
  public final List<GvrsMetadata> readMetadata() throws IOException {
    // To provide efficient file access, sort the trackers
    // by file position (offset)
    List<GvrsMetadataReference> trackerList = recordMan.getMetadataReferences(true);
    List<GvrsMetadata> result = new ArrayList<>();
    for (GvrsMetadataReference tracker : trackerList) {
      braf.seek(tracker.offset + RECORD_HEADER_SIZE);
      result.add(new GvrsMetadata(braf));
    }

    // The built-in metadata order is by name and recordID
    Collections.sort(result);
    return result;
  }

  /**
   * Reads the unique metadata object identified by name and record ID from
   * the GVRS file. If the file does not contain a matching object,
   * a null reference is returned.
   *
   * @param name a valid string giving a GVRS identifier
   * @param recordID the record ID for the specified metadata
   * @return if found, a safe copy of the metadata from the GVRS file;
   * if no match is found, a null.
   * @throws IOException in the event of an unrecoverable I/O exception
   */
  public final GvrsMetadata readMetadata(String name, int recordID) throws IOException {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Unable to retrieve metadata for null or empty User ID");
    }
    return recordMan.readMetadata(name, recordID);
  }

  /**
   * Reads a set of metadata objects that match the specified name
   * from the GVRS file. If no such metadata objects exist,
   * the resulting list will be empty. Because no record ID is specified,
   * it is possible that the list may contain multiple entries.
   *
   * @param name a valid string giving a GVRS identifier
   * @return a valid, potentially empty list.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  public List<GvrsMetadata> readMetadata(String name) throws IOException {
    List<GvrsMetadataReference> trackerList = recordMan.getMetadataReferences(true);
    List<GvrsMetadata> result = new ArrayList<>();
    for (GvrsMetadataReference tracker : trackerList) {
      if (name.equals(tracker.name)) {
        result.add(recordMan.readMetadata(name, tracker.recordID));
      }
    }

    // The built-in metadata order is by name and recordID
    Collections.sort(result);
    return result;
  }

  /**
   * Store a GvrsMetadata instance providing metadata in the file.
   *
   * @param metadata a valid instance
   * @throws IOException in the event of a unhandled I/O exception.
   */
  public final void writeMetadata(GvrsMetadata metadata) throws IOException {
    if (metadata == null) {
      throw new IllegalArgumentException("Attempt to store null metadata");
    }
    recordMan.writeMetadata(metadata);
  }

  /**
   * A convenience method for storing a GVRS metadata object with string
   * content.
   *
   * @param name a valid string conforming to the GVRS identifier syntax.
   * @param content the string to be stored.
   * @throws IOException in the envent of an unrecoverable I/O exception
   */
  public void writeMetadata(String name, String content) throws IOException {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException(
        "Attempt to store metadata with null or empty name specification");
    }
    GvrsMetadata metadata = new GvrsMetadata(name, 0, GvrsMetadataType.STRING);
    metadata.setString(content);
    GvrsFile.this.writeMetadata(metadata);
  }

  boolean loadTile(int tileIndex, boolean writeAccess) throws IOException {
    if (this.isClosed) {
      throw new IOException("Raster file is closed " + file.getPath());
    }
    RasterTile tile = tileCache.getTile(tileIndex);
    if (tile == null) {
      if (writeAccess) {
        tile = tileCache.allocateNewTile(tileIndex);
      } else {
        return false;
      }
    }

    // TO DO: For efficiency, replace elements list with an array?
    int k = 0;
    for (TileElement tElement : tile.elements) {
      GvrsElement gElement = elements.get(k++);
      tElement.transcribeTileReferences(tileIndex, gElement);
    }
    return true;
  }

  /**
   * Gets the GVRS element (if any) that matches the specified
   * name. Note that element names are case sensitive.
   * Note: There is one, and only one, element for each name
   * in a GVRS file. So multiple calls to this method that use the
   * same name will obtain the same object.
   *
   * @param name The name of the desired element.
   * @return if matched, a valid instance; otherwise a null.
   */
  public GvrsElement getElement(String name) {
    for (GvrsElement e : elements) {
      if (e.name.equals(name)) {
        return e;
      }
    }
    return null;
  }

  /**
   * Gets a list of all the elements that were specified for
   * the GVRS file when it was created.
   *
   * @return a valid, potentially empty list.
   */
  public List<GvrsElement> getElements() {
    List<GvrsElement> list = new ArrayList<>();
    list.addAll(elements);
    return list;
  }

  /**
   * Gets the size of the file header, in bytes. This value is
   * also the offset to the start of the file body (content).
   *
   * @return a positive value greater than zero.
   */
  int getSizeOfHeader() {
    return sizeOfHeaderInBytes;
  }

  /**
   * Gets the UUID assigned to this specification (and any GVRS files derived
   * from it). The UUID is an arbitrary value automatically assigned to the
   * specification. Its intended use it to allow GVRS to correlate files of
   * different types (such as the main GVRS file and its associated index
   * file).
   * <p>
   * The UUID is established by the GvrsFile constructor when a GVRS
   * file is first created. One set, it is never modified.
   * <p>
   * Internally, the UUID is an arbitrary set of 16 bytes. Non-Java language
   * implementations in languages/environments that do not have built-in
   * support for UUIDs are free to implement this feature as they see fit.
   *
   * @return a valid string.
   */
  public String getUUID() {
    return uuid.toString();
  }

}
