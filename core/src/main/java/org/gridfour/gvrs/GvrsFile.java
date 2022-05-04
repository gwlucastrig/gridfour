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

import org.gridfour.coordinates.GeoPoint;
import org.gridfour.coordinates.GridPoint;
import org.gridfour.coordinates.ModelPoint;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.UUID;
import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;

import org.gridfour.io.BufferedRandomAccessFile;
import org.gridfour.util.GridfourCRC32C;

/**
 * Provides methods and data elements for managing raster data in a file.
 * <p>
 * <strong>A Caution Regarding Thread Safety</strong>
 * <p>
 * GvrsFile and its related classes are not thread safe. While a file
 * may be opened on a read-only basis using multiple instances of
 * GvrsFile, the individual objects implement state-variables and data
 * caches that are not protected for concurrent access. Application requiring
 * multi-threaded access to a single GvrsFile object must manage
 * concurrency issues themselves.
 */
public class GvrsFile implements Closeable, AutoCloseable {

  private final static long FILEPOS_MODIFICATION_TIME = 32;
  private final static long FILEPOS_OPEN_FOR_WRITING_TIME = 40;

  // Gives the offset to the field in the header that is used to
  // store the file position for the storage of content.
  // The value in stored at this file position is also the size
  // of the file header, in bytes.
  private final static long FILEPOS_OFFSET_TO_CONTENT = 48;

  // Gives the offset to the field in the header that is used to
  // store the file positions for the index records.
  private final static long FILEPOS_OFFSET_TO_FREESPACE_INDEX = 56;
  private final static long FILEPOS_OFFSET_TO_METADATA_INDEX = 64;
  private final static long FILEPOS_OFFSET_TO_TILE_INDEX = 80;

  private final File file;
  private final GvrsFileSpecification spec;
  private final CodecMaster codecMaster;
  private final BufferedRandomAccessFile braf;
  private final UUID uuid;
  private boolean isClosed;
  private boolean openedForWriting;
  private boolean deleteOnClose;
  private boolean deleteOnCloseStatus;

  private long timeModified;

  // Content begins immediately after the header, so the position
  // of the content is also the size of the header.
  private long filePosContent;
  private int sizeOfHeaderInBytes;

  private final RecordManager recordMan;
  private final RasterTileCache tileCache;
  private final List<GvrsElement> elements = new ArrayList<>();



  private static File tempFile() throws IOException {
    Path filePath = Files.createTempFile("gvrstemp", ".gvrs");
    File file = filePath.toFile();
    file.deleteOnExit();
    return file;
  }

  private static GvrsFileSpecification tempSpec(int nRows, int nCols, GvrsElementType e){
    GvrsFileSpecification spec = new GvrsFileSpecification(nRows, nCols);
    GvrsElementSpecification eSpec = null;
    switch(e){
      case INTEGER:
        eSpec = new GvrsElementSpecificationInt("z");
        break;
      case SHORT:
         eSpec = new GvrsElementSpecificationShort("z");
         break;
      case FLOAT:
         eSpec = new GvrsElementSpecificationFloat("z");
         break;
      case INT_CODED_FLOAT:
         eSpec = new GvrsElementSpecificationIntCodedFloat("z", 1f, 0f);
         break;
      default:
        throw new IllegalArgumentException("Unsupported element type "+e);
    }
    spec.addElementSpecification(eSpec);
    return spec;
  }

  /**
   * Constructs a raster store backed by a temporary file that will
   * be deleted when the close() method is called or the program terminates
   * successfully.  Note that the Java Virtual Machine will not be able
   * to remove the file in the event of improper termination.
   * @param specification a valid specification
   * @throws IOException when an unhandled I/O exception occurs.
   */
  public GvrsFile(GvrsFileSpecification specification)
    throws IOException {
    this(tempFile(), specification);
    deleteOnClose = true;
  }


   /**
   * Constructs a raster store backed by a temporary file that will
   * be deleted when the close() method is called or the program terminates
   * successfully.  Note that the Java Virtual Machine will not be able
   * to remove the file in the event of improper termination.
   * @param nRowsInRaster an integer value greater than zero
   * @param nColumnsInRaster an integer value greater than zero
   * @param elementType a valid instance
   * @throws IOException when an unhandled I/O exception occurs.
   */
  public GvrsFile(int nRowsInRaster, int nColumnsInRaster, GvrsElementType elementType)
    throws IOException {
    this(tempFile(), tempSpec(nRowsInRaster, nColumnsInRaster, elementType));
    deleteOnClose = true;
  }



  /**
   * Creates a new raster file using the specified file and specifications. If
   * the file reference points to an existing file, the old file will be
   * deleted and replaced. The dimensions and structure of the raster
   * file will be taken from the specification argument.
   * <p>
   * When a new instance of GvrsFile is constructed, the specification
   * object will be copied. Therefore, any subsequent changes to the
   * specification object supplied by the application will not affect
   * the internal elements in the GvrsFile element.
   *
   * @param file a valid file reference giving the path to a new output file
   * in a location with write access.
   * @param specification a valid GVRS raster specification
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

    this.openedForWriting = true;
    this.file = file;
    this.spec = new GvrsFileSpecification(specification);
    this.codecMaster = new CodecMaster(specification.codecList);
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

    braf.leWriteLong(timeModified);  // pos 32: time modified
    braf.leWriteLong(timeModified);    // pos 40: time opened
    braf.leWriteLong(0); // pos 48: offset to content, also length of the header
    braf.leWriteLong(0); // pos 56: offset to freespace index
    braf.leWriteLong(0); // pos 64: offset to metadata index

    braf.leWriteShort(1); // number of levels. Currently fixed at 1
    byte[] zeroes = new byte[6];
    braf.writeFully(zeroes);
    braf.leWriteLong(0); // pos 80: offset to first (currently, only) tile index

    // write a block of two reserved longs for future use.
    braf.leWriteLong(0);
    braf.leWriteLong(0);

    // write the specification
    spec.write(braf);

    // write out 8 bytes reserved for future use
    zeroes = new byte[8];
    braf.writeFully(zeroes);  // reserved

    // The offset to the end of the header needs to be a multiple
    // of 8 in order to support file position compression. The size is
    // not known a priori because it will depend on the structure
    // of the elements in the specification.  At this point,
    // we will also need to reserve 4 extra bytes for the checksum
    // and then pad out the record.
    long filePos = braf.getFilePosition();
    filePosContent = (filePos + 4 + 7) & 0xfffffff8;
    sizeOfHeaderInBytes = (int) filePosContent;
    int padding = (int) (filePosContent - filePos);
    for (int i = 0; i < padding; i++) {
      braf.writeByte(0);
    }

    braf.seek(FILEPOS_OFFSET_TO_CONTENT);
    braf.leWriteLong(filePosContent);
    braf.flush();

    recordMan = new RecordManager(spec, codecMaster, braf, filePosContent);
    tileCache = new RasterTileCache(spec, recordMan);
    setTileCacheSize(GvrsCacheSize.Medium);

    List<CodecHolder> csList = spec.getCompressionCodecs();
    if (!csList.isEmpty()) {
      String scratch = CodecHolder.formatSpecificationString(csList);
      GvrsMetadata codecMetadata = GvrsMnc.GvrsJavaCodecs.newInstance(0);
      codecMetadata.setString(scratch);
      codecMetadata.setDescription("Class paths for Java compressors");
      writeMetadata(codecMetadata);
      StringBuilder sb = new StringBuilder();
      for (CodecHolder holder : csList) {
        if (sb.length() > 0) {
          sb.append('|');
        }
        sb.append(holder.getIdentification());
      }
      GvrsMetadata compCodecMetadata = GvrsMnc.GvrsCompressionCodecs.newInstance(0);
      compCodecMetadata.setString(sb.toString());
      compCodecMetadata.setDescription("Compession codecs");
      writeMetadata(compCodecMetadata);
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
    if(!GvrsFileSpecification.isVersionSupported(version, subversion)){
      throw new IOException("Incompatible version " + version + "." + subversion
        + ".  Latest version is "
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

    long filePosFreeSpaceIndexRecord = braf.leReadLong();
    long filePosMetadataIndexRecord = braf.leReadLong();
    int nLevels = braf.leReadShort(); // right now, always 1.
    if(nLevels!=1){
      throw new IOException("Unsupported number of levels "+nLevels);
    }
    braf.skipBytes(6);
    // for now, there is only one tile index record.  This may
    // change in the future if we support raster pyramids.
    long filePosTileIndexRecord = braf.leReadLong();

    // skip the currently reserved block of 16 bytes
    braf.skipBytes(16);
    spec = new GvrsFileSpecification(braf, version, subversion);

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

    codecMaster = new CodecMaster(spec.codecList);
    recordMan = new RecordManager(spec, codecMaster, braf, filePosContent);
    long savePos = braf.getFilePosition();
    if (filePosFreeSpaceIndexRecord > 0) {
      recordMan.readFreespaceIndexRecord(filePosFreeSpaceIndexRecord);
      if (writingEnabled) {
        // presumably, the content is going to change and the existing
        // index data will become obsolete.  So dispose of it and zero out
        // the file position for the index record.

        braf.seek(FILEPOS_OFFSET_TO_FREESPACE_INDEX);
        braf.leWriteLong(0);
        recordMan.fileSpaceDealloc(filePosFreeSpaceIndexRecord);
      }
    }

    if (filePosMetadataIndexRecord > 0) {
      recordMan.readMetadataIndexRecord(filePosMetadataIndexRecord);
      if (writingEnabled) {
        braf.seek(FILEPOS_OFFSET_TO_METADATA_INDEX);
        braf.leWriteLong(0);
        recordMan.fileSpaceDealloc(filePosMetadataIndexRecord);
      }
    }

    if (filePosTileIndexRecord > 0) {
      recordMan.readTileIndexRecord(filePosTileIndexRecord);
      if (writingEnabled) {
        braf.seek(FILEPOS_OFFSET_TO_TILE_INDEX);
        braf.leWriteLong(0);
        recordMan.fileSpaceDealloc(filePosTileIndexRecord);
      }
    }
    braf.seek(savePos);

    tileCache = new RasterTileCache(spec, recordMan);
    setTileCacheSize(GvrsCacheSize.Medium);

    // See if the source file specified Java codecs.
    List<CodecSpecification> codecSpecificationList = new ArrayList<>();
    GvrsMetadata codecMetadata
      = readMetadata(GvrsMnc.GvrsJavaCodecs.name(), 0);
    if (codecMetadata != null) {
      String codecStr = codecMetadata.getString();
      codecSpecificationList
        = CodecSpecification.specificationStringParse(codecStr);
    }
    spec.integrateCodecSpecificationsFromFile(codecSpecificationList);
    codecMaster.setCodecs(spec.codecList);

    for (GvrsElementSpecification eSpec : spec.elementSpecifications) {
      GvrsElement e = eSpec.makeElement(this);
      elements.add(e);
    }

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
      codecMaster.shutdown();

      if(openedForWriting && deleteOnClose){
          openedForWriting = false;
          isClosed = true;
          braf.close();
          deleteOnCloseStatus = file.delete();
          return;
      }

      if (openedForWriting) {
        tileCache.flush();
        braf.seek(FILEPOS_MODIFICATION_TIME);
        long closingTime = System.currentTimeMillis();
        braf.leWriteLong(closingTime);
        braf.leWriteLong(0); // opened for writing time

        long freeSpaceIndexPos = recordMan.writeFreeSpaceIndexRecord();
        braf.seek(FILEPOS_OFFSET_TO_FREESPACE_INDEX);
        braf.leWriteLong(freeSpaceIndexPos);

        long metadataIndexPos = recordMan.writeMetadataIndexRecord();
        braf.seek(FILEPOS_OFFSET_TO_METADATA_INDEX);
        braf.leWriteLong(metadataIndexPos);

        // At present, there is only one tile index record.
        // In the future, addition records may be added.
        long tileIndexPos = recordMan.writeTileIndexRecord();
        braf.seek(FILEPOS_OFFSET_TO_TILE_INDEX);
        braf.leWriteLong(tileIndexPos);

        if (spec.isChecksumEnabled) {
          long checksum = tabulateChecksumFromHeader();
          braf.leWriteInt((int) checksum);
        }
        braf.flush();
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
   * Indicates if the backing file associated with this instance
   * was successfully deleted on close.  This method will return true
   * if and only if the file is configured to be deleted on close,
   * the file has been closed, and the delete operation was successful.
   * @return true if the file was deleted on close.
   */
  public boolean wasFileDeletedOnClose(){
    return deleteOnCloseStatus;
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
    UUID uuid = getUUID();
    ps.println("UUID:              " + uuid.toString());
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
   * specified enumeration. This method will attempt to set tile cache
   * size in terms of the number of tiles that can fit within the memory
   * limit specified by the GvrsCacheSize enumeration.  the following
   * lists the target number of tiles for each specification. If the enumeration
   * specifies enough memory for these target values, they will be used.
   * Otherwise, they will be limited by memory.
   * <ul>
   * <li><strong>Small</strong> 9 tiles. </li>
   * <li><strong>Medium</strong> Enough tiles to fill an entire row.</li>
   * <li><strong>Large</strong> Enough tiles to fill two rows.</li>
   * </ul>
   * In general, the Large size should be used when creating new data products.
   * Size should be based on the anticipated pattern of access for the file.
   * <p>
   * If the sizes specified using the enumeration are inadequate for the
   * needs of an application, an application can set the specific number
   * of tiles in the cache by using the alternate version of this method.
   *
   * @param cacheSize a valid instance
   * @throws IOException in the event of a non-recoverable I/P exception.
   */
  public void setTileCacheSize(GvrsCacheSize cacheSize) throws IOException {
    if(cacheSize==null){
      throw new IllegalArgumentException("Null cache size not allowed");
    }

    int standardTileSize = spec.getStandardTileSizeInBytes();
    if(standardTileSize == 0){
      // no elements have been established.  The cache size is
      // irrelevant. Simply exit.
      return;
    }

    // In the general case, this code will try to set up a buffer
    // buffer large enough to cover all tiles in a row of a column of the
    // data set (which ever is largest).  This target value will be limited
    // but the maximum memory allocated for the size specification.
    int nC = spec.nColsOfTiles;
    int nR = spec.nRowsOfTiles;
    int nT = nC > nR ? nC : nR;
    if (nT < 9) {
      nT = 9;
    }
    int target;
    switch (cacheSize) {
      case Small:
        // The target size is based on the idea that we want to have enough tiles in
        // the buffer to support a cluster of queries taken near a single point.
        // Nine supports all points in a single tile and all of its neighbors.
        target = 9;
        break;
      case Medium:
        target = nT;
        break;
      case Large:
        target = 2 * nT;
        break;
      default:
        target = 9; // again. the small size;
    }
    int maxAllowed = cacheSize.maxBytesInCache/standardTileSize;
    if (maxAllowed < 2) {
      target = 2;
    } else if (target > maxAllowed) {
      target = maxAllowed;
    }
    setTileCacheSize(target);
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
   * Map grid coordinates to model coordinates storing the resulting x and y
   * values in a GvrsModelPoint instance. If the row or column values are outside
   * the ranges defined for those parameters, the resulting x and y values may
   * be outside the bounds of the standard Cartesian coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the setCartesianCoordinates{} method.
   *
   * @param row a row (may be a non-integral value)
   * @param column a column (may be a non-integral value)
   * @return a valid instance
   */
  public ModelPoint mapGridToModelPoint(double row, double column) {
    return spec.mapGridToModelPoint(row, column);
  }


  /**
   * Map model coordinates to grid coordinates storing the computed row and
   * column in an instance of GvrsGridPoint. If the x or y coordinate is outside
   * the ranges defined for these parameters, the resulting rows and columns
   * may be outside the range of the valid grid coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the setCartesianCoordinates() method.
   *
   * @param x a valid floating-point coordinate
   * @param y a valid floating-point coordinate
   * @return an array giving row and column in that order; the results may be
   * non-integral values.
   */
  public GridPoint mapModelToGridPoint(double x, double y) {
   return spec.mapModelToGridPoint(x, y);
  }



  /**
   * Map geographic coordinates to grid coordinates storing the row and column
   * in an array in that order. If the latitude or longitude is outside the
   * ranges defined for these parameters, the resulting rows and columns may
   * be
   * outside the range of the valid grid coordinates
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the setGeographicCoordinates{} method.
   * Longitudes may be adjusted according to the bounds established by the
   * specification and in recognition of the cyclic nature of longitude
   * coordinates (i.e. 450 degrees is equivalent to 90 degrees, etc.).
   *
   * @param latitude a valid floating-point coordinate
   * @param longitude a valid floating-point coordinate
   * @return a valid instance.
   */
  public GridPoint mapGeographicToGridPoint(double latitude, double longitude) {
    return spec.mapGeographicToGridPoint(latitude, longitude);
  }


  /**
   * Map grid coordinates to geographic coordinates storing the resulting
   * latitude and longitude in an instance of GvrsGeoPoint.
   * If the specified row or column values are outside
   * the ranges defined for those parameters, the resulting latitude and
   * longitude values may be outside the bounds of the standard Geographic
   * coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the setCartesianCoordinates{} method.
   *
   * @param row the row coordinate (may be non-integral)
   * @param column the column coordinate (may be non-integral)
   * @return a valid instance.
   */
  public GeoPoint mapGridToGeoPoint(double row, double column) {
    return spec.mapGridToGeoPoint(row, column);
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
      braf.seek(tracker.offset);
      GvrsMetadata mData = new GvrsMetadata(braf);
      result.add(mData);
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
      throw new IllegalArgumentException(
        "Unable to retrieve metadata for null or empty name");
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
   * Reads a set of metadata objects that match the name of the specified
   * enumeration.If no such metadata objects exist,
   * the resulting list will be empty. Because no record ID is specified,
   * it is possible that the list may contain multiple entries.
   *
   * @param gmConstant a valid, non-null enumeration instance
   * @return a valid, potentially empty list
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  public List<GvrsMetadata> readMetadata(GvrsMnc gmConstant) throws IOException {
        if (gmConstant == null) {
      throw new IllegalArgumentException(
        "Unable to retrieve metadata for a null enumeration specification");
    }
    return readMetadata(gmConstant.name());
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
   * @throws IOException in the event of an unrecoverable I/O exception
   */
  public void writeMetadata(String name, String content) throws IOException {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException(
        "Attempt to store metadata with null or empty name specification");
    }
    GvrsMetadata metadata = new GvrsMetadata(name, GvrsMetadataType.STRING);
    metadata.setString(content);
    recordMan.writeMetadata(metadata);
  }

  /**
   * A convenience method for storing a GVRS metadata object with string
   * content. This method will interpret a metadata-constant.
   * While it provides a more compact way of writing metadata,
   * it requires that the constant specifies a string type.
   *
   * @param gmConstant valid instance.
   * @param content the string to be stored.
   * @throws IOException in the event of an unrecoverable I/O exception
   */
  public void writeMetadata(
    GvrsMnc gmConstant, String content) throws IOException {
    String name = gmConstant.name();
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException(
        "Attempt to store metadata with null or empty name specification");
    }
    GvrsMetadataType gmType = gmConstant.getDataType();
    if (gmType != GvrsMetadataType.STRING && gmType != GvrsMetadataType.ASCII) {
      throw new IllegalArgumentException(
        "Specified constant must specify a string type");
    }

    GvrsMetadata metadata = new GvrsMetadata(name, 0, GvrsMetadataType.STRING);
    metadata.setString(content);
    writeMetadata(metadata);
  }

  /**
   * Delete a metadata element from the file.  If no metadata element
   * can be matched to the specified name and record ID, no action is
   * performed.
   * @param name The name of the metadata to be deleted.
   * @param recordID The record ID of the metadata to be deleted.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  public void deleteMetadata(String name, int recordID) throws IOException{
    recordMan.deleteMetadata(name, recordID);
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
   * Gets the GVRS element by index (order created).
   *
   * @param index A value of zero or greater
   * @return if defined, a valid instance; otherwise a null.
   */
  public GvrsElement getElement(int index) {
    if(elements.size()>index){
      return elements.get(index);
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
   * @return a valid UUID
   */
  public UUID getUUID() {
    return uuid;
  }

  /**
   * Gets the record manager for this instance. Note that this method
   * is intended for testing only and is <i>not</i> public.
   *
   * @return a valid instance.
   */
  RecordManager getRecordManager() {
    return recordMan;
  }

  /**
   * Gets the time that the content of the GVRS file was last modified.
   * Note that this value is based on the time of internal operations
   * on a GVRS file and will not necessarily match the information
   * obtained by operating on the underlying file using the Java API.
   * @return a value in milliseconds from the epoch January 1, 1970.
   */
  public long getModificationTimeMillis(){
    return timeModified;
  }

  /**
   * Gets the time that the GVRS file was created..
   * @return a value in milliseconds from the epoch January 1, 1970.
   */
  public long getCreationTimeMillis(){
    return timeModified;
  }




  /**
   * Map Cartesian coordinates to grid coordinates storing the row and column in
   * an array in that order.
   * <p>
   * This method is deprecated. Please use mapModelToGridPoint() instead.
   *
   * @param x a valid floating-point coordinate
   * @param y a valid floating-point coordinate
   * @return an array giving row and column in that order; the results may be
   * non-integral values.
   */
  @Deprecated
  public double[] mapCartesianToGrid(double x, double y) {
    return spec.mapCartesianToGrid(x, y);
  }

  /**
   * Map grid coordinates to Cartesian coordinates storing the resulting
   * x and y values in an array in that order.
   * <p>
   * This method is deprecated. Please use mapGridToModelPoint() instead.
   *
   * @param row a row (may be a non-integral value)
   * @param column a column (may be a non-integral value)
   * @return a valid array giving the Cartesian x and y coordinates in that
   * order.
   */
  @Deprecated
  public double[] mapGridToCartesian(double row, double column) {
    return spec.mapGridToCartesian(row, column);
  }

  /**
   * Map geographic coordinates to grid coordinates storing the row and column
   * in an array in that order.
   * <p>
   * This method is deprecated. Please use mapGeographicToGridPoint() instead.
   *
   * @param latitude a valid floating-point coordinate
   * @param longitude a valid floating-point coordinate
   * @return an array giving row and column in that order; the results may be
   * non-integral values.
   */
  @Deprecated
  public double[] mapGeographicToGrid(double latitude, double longitude) {
    return spec.mapGeographicToGrid(latitude, longitude);
  }

  /**
   * Map grid coordinates to Geographic coordinates storing the resulting
   * latitude and longitude values in an array in that order.
   * <p>
   * This method is deprecated. Please use mapModelToGridPoint() instead.
   *
   * @param row the row coordinate (may be non-integral)
   * @param column the column coordinate (may be non-integral)
   * @return a valid array giving row and column in that order.
   */
  @Deprecated
  public double[] mapGridToGeographic(double row, double column) {
    return spec.mapGridToGeographic(row, column);
  }

  /**
   * Gets an instance of the compression encoder that matches the
   * specified CODEC name, if any. In practice, a GvrsFile object will
   * create only one instance of a particular compression encoder.
   * Thus multiple calls to this method will return the same instance
   * of a compression encoder.
   * <p>
   * The purpose of this method is to provide encoder instances
   * to application code to support operations such as statistics
   * and analysis reporting, and post-compression logging.
   * It may be particularly useful to developers implementing their
   * own compression classes.
   *
   * @param name a valid, non-empty string.
   * @return if matched, a valid instance; otherwise, a null
   */
  public ICompressionEncoder getCompressionEncoder(String name) {
    return codecMaster.getCompressionEncoder(name);
  }

  /**
   * Gets an instance of the compression decoder that matches the
   * specified CODEC name, if any. In practice, a GvrsFile object will
   * create only one instance of a particular compression decoder.
   * Thus multiple calls to this method will return the same instance
   * of a compression decoder.
   * <p>
   * The purpose of this method is to provide decoder instances
   * to application code to support operations such as statistics
   * and analysis reporting, and post-compression logging.
   * It may be particularly useful to developers implementing their
   * own compression classes.
   *
   * @param name a valid, non-empty string.
   * @return if matched, a valid instance; otherwise, a null
   */
  public ICompressionDecoder getCompressionDecoder(String name) {
    return codecMaster.getCompressionDecoder(name);
  }


  /**
   * Sets multi-threading enabled. At this time, multi-threaded processing
   * is only supported in the compressing of data. If an application
   * is not compressing data, this setting will be ignored.
   * <p>
   * Future development for the GVRS API may expand the use of multi-threaded
   * processing.
   * <p>
   * Multi-threading can expedite processing, but is sometimes inconvenient
   * when developing, profiling, or debugging an application.  Multi-threaded
   * applications may also consume more resources than single threaded
   * processes. Therefore, the default setting for this class is to
   * treat multi-threading as disabled.
   *
   * @param multiThreadingEnabled true if multiple threads are enabled;
   * otherwise, false (default false).
   */
  public void setMultiThreadingEnabled(boolean multiThreadingEnabled) {
    codecMaster.setMultiThreadingEnabled(multiThreadingEnabled);
  }

}
