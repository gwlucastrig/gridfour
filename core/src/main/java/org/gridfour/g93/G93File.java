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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.UUID;
import org.gridfour.io.BufferedRandomAccessFile;
import static org.gridfour.g93.G93FileConstants.NULL_DATA_CODE;

/**
 * Provides methods and data elements for managing raster data in a file.
 */
public class G93File implements Closeable, AutoCloseable {

  private final static long FILEPOS_MODIFICATION_TIME = 16;
  private final static long FILEPOS_OPEN_FOR_WRITING_TIME = 24;
  private final static long FILEPOS_OFFSET_TO_TILE_STORE = 32;

  private final File file;
  private final G93FileSpecification spec;
  private final CodecMaster rasterCodec;
  private final BufferedRandomAccessFile braf;
  private boolean isClosed;
  private boolean openedForWriting;
  private boolean indexCreationEnabled;
  private long timeModified;

  private long filePosTileStore;

  private final G93TileStore tileStore;
  private final RasterTileCache tileCache;

  private class TileAccessElements {

    int tileIndex;
    int rowInTile;
    int colInTile;
    int tileRow;
    int tileCol;

    void computeElements(int row, int col) throws IOException {
      if (row < 0 || row >= spec.nRowsInRaster) {
        throw new IOException("Row out of bounds " + row);
      }
      if (col < 0 || col >= spec.nColsInRaster) {
        throw new IOException("Row out of bounds " + row);
      }

      tileRow = row / spec.nRowsInTile;
      tileCol = col / spec.nColsInTile;
      tileIndex = tileRow * spec.nColsOfTiles + tileCol;
      rowInTile = row - tileRow * spec.nRowsInTile;
      colInTile = col - tileCol * spec.nColsInTile;
    }
  }

  private TileAccessElements accessElements = new TileAccessElements();

  /**
   * Creates a new raster file using the specified file and specifications. If
   * the file reference points to an existing file, the old file will be deleted
   * and replaced. The dimensions and structure of the raster file will be taken
   * from the specification argument.
   *
   * @param file a valid file reference giving the path to a new output file in
   * a location with write access.
   * @param specification a valid g93 raster specification
   * @throws IOException in the event of an unrecoverable I/O error.
   */
  public G93File(File file, G93FileSpecification specification)
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
    this.spec = new G93FileSpecification(specification);
    this.rasterCodec = new CodecMaster();
    braf = new BufferedRandomAccessFile(file, "rw");

    timeModified = System.currentTimeMillis();

    braf.writeASCII(RasterFileType.G93raster.getIdentifier(), 12);
    braf.writeUnsignedByte(G93FileSpecification.VERSION);
    braf.writeUnsignedByte(G93FileSpecification.SUB_VERSION);
    braf.writeUnsignedByte(0); // reserved  
    braf.writeUnsignedByte(0); // reserved 

    braf.leWriteLong(timeModified);  // time modified
    braf.leWriteLong(timeModified);    // time opened
    braf.leWriteLong(0); // offset to tile store
    spec.write(braf);

    // write out 8 bytes reserved for future use
    byte[] b = new byte[8];
    braf.writeFully(b);  // reserved

    // The offset to the end of the header needs to be a multiple
    // of 8 in order to support file position compression. During
    // development, it is computed, but eventually, it will be coded
    // as a final static variable.
    long filePos = braf.getFilePosition();
    filePosTileStore = (filePos + 7) & 0xfffffff8;
    int padding = (int) (filePosTileStore - filePos);
    if (padding > 0) {
      braf.writeFully(b, 0, padding);
    }
    braf.seek(FILEPOS_OFFSET_TO_TILE_STORE);
    braf.leWriteLong(filePosTileStore);
    braf.flush();

    tileStore = new G93TileStore(spec, rasterCodec, braf, filePosTileStore);
    tileCache = new RasterTileCache(spec, tileStore);

    List<G93SpecificationForCodec> csList = spec.getCompressionCodecs();
    if (csList.size() > 0) {
      rasterCodec.setCodecs(csList);
      StringBuilder sb = new StringBuilder();
      for (G93SpecificationForCodec cs : csList) {
        String codecID = cs.getIdentification();
        Class csClass = cs.getCodec();
        sb.append(codecID).append(',')
                .append(csClass.getCanonicalName()).append('\n');
      }
      String scratch = sb.toString();

      storeVariableLengthRecord(
              "G93_Java_Codecs",
              0,
              "Class paths for Java compressors",
              scratch.toString());
    }

  }

  /**
   * Open an existing raster file for read or write access.
   * <p>
   * Only one application may write to a file at once. If the existing file was
   * previously opened for writing and not properly closed, it may not be
   * accessible.
   *
   * @param file a valid file
   * @param access a valid access control following the general contract of the
   * Java RandomAccessFile class (valid values, "r", "rw", etc&#46;)
   * @throws IOException in the event of an unrecoverable I/O error
   */
  public G93File(File file, String access) throws IOException {
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
    if (!RasterFileType.G93raster.getIdentifier().equals(identification)) {
      throw new IOException("Incompatible file type " + identification);
    }
    int version = braf.readUnsignedByte();
    int subversion = braf.readUnsignedByte();
    braf.skipBytes(2); // unused, reserved bytes
    if (version != G93FileSpecification.VERSION
            || subversion != G93FileSpecification.SUB_VERSION) {
      throw new IOException("Incompatible version " + version + "." + subversion);
    }

    timeModified = braf.leReadLong(); // time modified from old file
    long timeOpenedForWriting = braf.leReadLong();
    if (timeOpenedForWriting != 0) {
      throw new IOException(
              "Attempt to access a file currently opened for writing"
              + " or not properly closed by previous application: "
              + file.getPath());
    }

    filePosTileStore = braf.leReadLong();

    spec = new G93FileSpecification(braf);
    if (spec.isExtendedFileSizeEnabled) {
      throw new IOException(
              "Unable to access file with extended file size option set,"
              + " feature not implemented");
    }

    rasterCodec = new CodecMaster();

    if (access.contains("w")) {
      braf.seek(FILEPOS_OPEN_FOR_WRITING_TIME);
      braf.leWriteLong(System.currentTimeMillis());
      braf.flush();
      openedForWriting = true;
    }

    tileStore = new G93TileStore(spec, rasterCodec, braf, filePosTileStore);
    if (!readIndexFile(timeModified)) {
      tileStore.scanFileForTiles();
    }
    tileCache = new RasterTileCache(spec, tileStore);

    List<VariableLengthRecord> vlrList = this.getVariableLengthRecords();
    for (VariableLengthRecord vlr : vlrList) {
      if ("G93_Java_Codecs".equals(vlr.getUserId())) {
        byte[] codecBytes = vlr.readPayload();
        String codecStr = new String(codecBytes);
        List<G93SpecificationForCodec> codecList
                = G93SpecificationForCodec.parseSpecificationString(codecStr);
        rasterCodec.setCodecs(codecList);
      }
    }
  }

  /**
   * Gets the file in which the data for this raster is stored.
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
   * Write any in-memory data to the file. This call is required to ensure that
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
   * Closes the file and releases all associated resources. If the file is open
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
        braf.flush();
        if (indexCreationEnabled) {
          writeIndexFile(closingTime);
        }
        timeModified = closingTime;
      }
      openedForWriting = false;
      isClosed = true;
      braf.close();
    }
  }

  /**
   * Scans the file and writes a summary of its content to the specified
   * PrintStream.
   * <p>
   * The analyze option will allow an application to print a report of
   * statistics related to data compression. This process requires accessing the
   * content of tiles and may be somewhat time consuming.
   *
   * @param ps a valid print-stream
   * @param analyze performs an analysis of compressed data
   */
  public void summarize(PrintStream ps, boolean analyze) {
    ps.format("G93 Raster File:   " + file.getPath());
    ps.println("");

    spec.summarize(ps);
    Locale locale = Locale.getDefault();
    Date date = new Date(timeModified);
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", locale);

    ps.format("Time last modified:     %s%n", sdFormat.format(date));
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    ps.format("Time last modified:     %s (UTC)%n", sdFormat.format(date));

    tileStore.summarize(ps);
    tileCache.summarize(ps);
    if (analyze && !braf.isClosed()) {
      try {
        tileStore.analyzeAndReport(ps);
      } catch (IOException ioex) {
        ps.format("IOException encountered during analysis: " + ioex.getMessage());
      }
    }
  }

  /**
   * Store an integer value in the g93 raster file. Because write operations are
   * buffered, this data may be retained in memory for some time before actually
   * being written to the file. However, any data lingering in memory will be
   * recorded when the flush() or close() methods are called.
   * <p>
   * The value G93FileConstants.NULL_DATA_CODE is reserved for the
   * representation of null data.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @param value an integer value.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  public void storeIntValue(int row, int column, int value) throws IOException {
    if (!openedForWriting) {
      throw new IOException("Raster file not opened for writing");
    }
    accessElements.computeElements(row, column);

    RasterTile tile = tileCache.getTile(accessElements.tileIndex);
    if (tile == null) {
      tile = tileCache.allocateNewTile(accessElements.tileIndex);
    }
    tile.setIntValue(accessElements.rowInTile, accessElements.colInTile, value);
  }

  /**
   * Read an integer value from the G93File. If no data exists for the specified
   * row and column, the value G93FileContants.NULL_DATA_CODE will be returned.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @return an integer value or a NULL_DATA_CODE if there is no data defined
   * for the specified row and column
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  public int readIntValue(int row, int column) throws IOException {
    if (this.isClosed) {
      throw new IOException("Raster file is closed");
    }
    accessElements.computeElements(row, column);

    RasterTile tile = tileCache.getTile(accessElements.tileIndex);
    if (tile == null) {
      return NULL_DATA_CODE;
    }
    return tile.getIntValue(accessElements.rowInTile, accessElements.colInTile);
  }

  /**
   * Store an floating-point value in the g93 raster file. Because write
   * operations are buffered, this data may be retained in memory for some time
   * before actually being written to the file. However, any data lingering in
   * memory will be recorded when the flush() or close() methods are called.
   * <p>
   * The value Float.NaN is reserved for the representation of null data.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param col a positive value in the range defined by the file
   * specifications.
   * @param value an floating-point value.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  public void storeValue(int row, int col, float value) throws IOException {
    if (!openedForWriting) {
      throw new IOException("Raster file not opened for writing");
    }
    accessElements.computeElements(row, col);

    RasterTile tile = tileCache.getTile(accessElements.tileIndex);
    if (tile == null) {
      tile = tileCache.allocateNewTile(accessElements.tileIndex);
    }
    tile.setValue(accessElements.rowInTile, accessElements.colInTile, value);
  }

  /**
   * Reads a floating-point value from the G93File. If no data exists for the
   * specified row and column, the value Float.NaN will be returned.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @return an floating-point value or a Float.NaN if there is no data defined
   * for the specified row and column
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  public float readValue(int row, int column) throws IOException {
    if (this.isClosed) {
      throw new IOException("Raster file is closed");
    }
    accessElements.computeElements(row, column);

    RasterTile tile = tileCache.getTile(accessElements.tileIndex);
    if (tile == null) {
      return Float.NaN;
    }
    return tile.getValue(accessElements.rowInTile, accessElements.colInTile);
  }

  /**
   * Stores an array of values in the g93 raster file. The array should be
   * defined to be at least the size of the "rank" value given in the
   * SimpleRasterSpecification used to create this file. Extra elements will be
   * ignored.
   * <p>
   * Because write operations are buffered, this data may be retained in memory
   * for some time before actually being written to the file. However, any data
   * lingering in memory will be recorded when the flush() or close() methods
   * are called.
   * <p>
   * The value G93FileConstants.NULL_DATA_CODE is reserved for the
   * representation of null data.
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @param values an array of at least the rank of the file, used to supply
   * values for storage.
   * @throws IOException in the event of an unrecoverable I/O exception.
   */
  public void storeValues(int row, int column, float[] values) throws IOException {
    if (!openedForWriting) {
      throw new IOException("Raster file not opened for writing");
    }
    accessElements.computeElements(row, column);

    RasterTile tile = tileCache.getTile(accessElements.tileIndex);
    if (tile == null) {
      tile = tileCache.allocateNewTile(accessElements.tileIndex);
    }

    tile.setValues(accessElements.rowInTile, accessElements.colInTile, values);
  }

  /**
   * Reads a floating-point value from the G93File. If no data exists for the
   * specified row and column, the value Float.NaN will be returned. This method
   * is intended to support cases where the file definition has a rank greater
   * than 1 (i.e. in cases where the file stores vector elements).
   *
   * @param row a positive value in the range defined by the file
   * specifications.
   * @param column a positive value in the range defined by the file
   * specifications.
   * @param values an array of at least the rank of the file, used to store the
   * results of a read operation.
   * @throws IOException in the event of a non-recoverable I/O exception.
   */
  public void readValues(int row, int column, float[] values) throws IOException {
    if (this.isClosed) {
      throw new IOException("Raster file is closed");
    }
    accessElements.computeElements(row, column);

    RasterTile tile = tileCache.getTile(accessElements.tileIndex);
    if (tile == null) {
      Arrays.fill(values, Float.NaN);
    }
    tile.getValues(accessElements.rowInTile, accessElements.colInTile, values);
  }

  /**
   * Sets the tile cache size. Tile cache can have a significant effect on both
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
   * creating data products. Size should be based on the anticipated pattern of
   * access for the file.
   *
   * @param cacheSize a valid instance
   * @throws IOException in the event of a non-recoverable I/P exception.
   */
  public void setTileCacheSize(G93CacheSize cacheSize) throws IOException {
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
   * Gets a safe copy of the g93 raster specification associated with this file.
   *
   * @return a valid instance.
   */
  public G93FileSpecification getSpecification() {
    return new G93FileSpecification(spec);
  }

  /**
   * Sets or clears a flag indicating that the instance should generate an index
   * file when a writable file is closed.
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
      extension = RasterFileType.G93index.getExtension().toLowerCase();
    } else if (Character.isUpperCase(c)) {
      extension = RasterFileType.G93index.getExtension().toUpperCase();
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
    idxraf.writeASCII(RasterFileType.G93index.getIdentifier(), 12);
    idxraf.writeUnsignedByte(G93FileSpecification.VERSION);
    idxraf.writeUnsignedByte(G93FileSpecification.SUB_VERSION);
    idxraf.writeUnsignedByte(0); // reserved  
    idxraf.writeUnsignedByte(0); // reserved 

    idxraf.leWriteLong(closingTime);  // time modified
    idxraf.leWriteLong(spec.uuid.getLeastSignificantBits());
    idxraf.leWriteLong(spec.uuid.getMostSignificantBits());
    tileStore.writeTilePositionsToIndexFile(idxraf);
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

    try (BufferedRandomAccessFile idxraf
            = new BufferedRandomAccessFile(indexFile, "r");) {

      String s = idxraf.readASCII(12);
      if (!RasterFileType.G93index.getIdentifier().equals(s)) {
        throw new IOException("Improper identifier found in file "
                + file.getPath() + ": " + s);
      }
      int version = idxraf.readUnsignedByte();
      int subVersion = idxraf.readUnsignedByte();
      if (version != G93FileSpecification.VERSION
              || subVersion != G93FileSpecification.SUB_VERSION) {
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
      UUID uuid = new UUID(mostSigBits, leastSigBits);
      if (!uuid.equals(spec.uuid)) {
        // not the same file as the index
        return false;
      }

      tileStore.readTilePositionsFromIndexFile(idxraf);
      idxraf.close();
    }

    return true;
  }

  /**
   * Maps the specified floating point value to the integer value that would be
   * used for the internal representation of data when storing integral data.
   * Normally, the G93File will convert floating point values to integers if the
   * file is defined with an integer data type or if the data is being stored in
   * compressed form.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through the setValueTransform() method when the associated file
   * was created.
   *
   * @param value a valid floating point value or Float.NaN
   * @return an integer value.
   */
  public int mapValueToInt(float value) {
    return spec.mapValueToInt(value);
  }

  /**
   * Maps the specified integer value to the equivalent floating point value as
   * defined for the G93File.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through the setValueTransform() method when the associated file
   * was created.
   *
   * @param intValue an integer value
   * @return the equivalent floating point value, or NaN if appropriate.
   */
  public float mapIntToValue(int intValue) {
    return spec.mapIntToValue(intValue);
  }

  /**
   * Map Cartesian coordinates to grid coordinates storing the row and column in
   * an array in that order. If the x or y coordinate is outside the ranges
   * defined for these parameters, the resulting rows and columns may be outside
   * the range of the valid grid coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * SimpleRasterSpecification.setCartesianCoordinates{} method when the
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
   * Map grid coordinates to Cartesian coordinates storing the resulting x and y
   * values in an array in that order. If the row or column values are outside
   * the ranges defined for those parameters, the resulting x and y values may
   * be outside the bounds of the standard Cartesian coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * SimpleRasterSpecification.setCartesianCoordinates{} method when the
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
   * ranges defined for these parameters, the resulting rows and columns may be
   * outside the range of the valid grid coordinates
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * SimpleRasterSpecification.setGeographicCoordinates{} method when the
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
   * Map grid coordinates to Geographic coordinates storing the resulting x and
   * y values in an array in that order. If the row or column values are outside
   * the ranges defined for those parameters, the resulting x and y values may
   * be outside the bounds of the standard Geographic coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the
   * SimpleRasterSpecification.setCartesianCoordinates{} method when the
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
   * Adds a variable-length record (VLR) to the file. Variable-length records
   * are used to store application-defined metadata that is outside the scope of
   * the G93File format specification.
   * <p>
   * The structure for VLRs is modeled on the VLR specification used for the
   * Lidar LAS file specification promulgated by the American Society for
   * Photogrammetry and Remote Sensing (ASPRS). VLRs may be used to store data
   * extracted from LAS files when building Digital Elevation Models (DEMs) from
   * Lidar.
   *
   * @param userID an application-defined user ID string, an application-defined
   * ID, up to 16 ASCII characters in length
   * @param recordID an application defined ID, in the range 0 to 65535
   * @param description an application-defined description, up to 32 ASCII
   * characters in length
   * @param payload an array of bytes giving the payload to be stored in the
   * file.
   * @param offset the offset into the payload byte array
   * @param payloadSize the length of the payload.
   * @param isPayloadText true if the payload is text data; otherwise false.
   * @throws java.io.IOException in the event of an I/O error
   */
  public void storeVariableLengthRecord(
          String userID,
          int recordID,
          String description,
          byte[] payload,
          int offset,
          int payloadSize,
          boolean isPayloadText) throws IOException {
    if (userID == null || userID.trim().isEmpty()) {
      throw new IOException("An empty user ID specification is not supported");
    }
    if (recordID < 0 || recordID > 65535) {
      throw new IOException("Record ID " + recordID + " out of range 0 to 65536");
    }
    if (payloadSize > 0) {
      if (payload == null || payload.length < offset + payloadSize) {
        throw new IOException(
                "Payload does not contain the indicated number of bytes");
      }
    }
    int nBytesRequired = VariableLengthRecord.VLR_HEADER_SIZE + payloadSize;
    long filePos = tileStore.allocateNonTileRecord(1, nBytesRequired);
    braf.seek(filePos);
    braf.writeASCII(userID, VariableLengthRecord.USER_ID_SIZE);
    braf.leWriteInt(recordID);
    braf.leWriteInt(payloadSize);
    braf.writeASCII(description, VariableLengthRecord.DESCRIPTION_SIZE);
    braf.writeBoolean(isPayloadText);
    byte[] spare = new byte[7];
    braf.writeFully(spare);
    if (payloadSize > 0) {
      braf.writeFully(payload, offset, payloadSize);
    }

    VariableLengthRecord vlr = new VariableLengthRecord(
            braf,
            filePos,
            userID,
            recordID,
            payloadSize,
            description,
            isPayloadText);
    tileStore.vlrRecordMap.put(vlr, vlr);
  }

  /**
   * Adds a variable-length record (VLR) containt a text payload to the file.
   * Variable-length records are used to store application-defined metadata that
   * is outside the scope of the G93File format specification.
   *
   * @param userID an application-defined user ID string, an application-defined
   * ID, up to 16 ASCII characters in length
   * @param recordID an application defined ID, in the range 0 to 65535
   * @param description an application-defined description, up to 32 ASCII
   * characters in length
   * @param text a valid string giving the payload to be stored in the file.
   * @throws java.io.IOException in the event of an I/O error
   */
  public void storeVariableLengthRecord(
          String userID,
          int recordID,
          String description,
          String text) throws IOException {

    if (text == null || text.isEmpty()) {
      throw new IOException(
              "Attempt to store text VLR with null or empty payload");
    }

    byte[] payload = text.getBytes("UTF-8");
    storeVariableLengthRecord(
            userID,
            recordID,
            description,
            payload, 0, payload.length,
            true);
  }

  /**
   * Gets a list of variable length records stored in the file
   *
   * @return a valid, potentially empty, instance.
   */
  public List<VariableLengthRecord> getVariableLengthRecords() {
    return tileStore.getVariableLengthRecords();
  }

  /**
   * Reads a block (sub-grid) of values from the G93 file based on the grid row,
   * column, and block-size specifications. If successful, the return value from
   * this method is an array giving a sub-grid of values in row-major order. If
   * the source file has a rank greater than 1, than the sub-grids for each
   * layer are given one at a time. Thus, the index into the array for a
   * particular row, column, and layer within the sub-grid would be
   * <p>
   * index = row*nColumns + column + layer*nRows*nColumns
   * <p>
   * where rows, columns, and layers are all numbered starting at zero.
   * <p>
   * Accessing data in a block is often more efficient that accessing data
   * one grid-value-at-a-time.
   * 
   * @param row the grid row index for the starting row of the block
   * @param column the grid column index for the starting column of the block
   * @param nRows the number of rows in the block to be retrieved
   * @param nColumns the number of columns in the block to be retrievd
   * @return if successful, a valid array of size nRow*nColumns*rank.
   * @throws IOException in the event of an I/O error.
   */
  public float[] readBlock(int row, int column, int nRows, int nColumns)
          throws IOException {
    // The indexing used here is a little complicated. To keep it managable,
    // this code adheres to a variable naming convention defined as follows:
    //   variables starting with
    //      t  means tile coordinates;  tr, tc are the row and column
    //            within a tile.
    //      b  block (result) coordinates; br, bc are the row and column
    //            within the result block.
    //      g  grid (main raster) coordinates; gr, gc  grid row and column
    //    
    //   tr will always be in the range 0 <= tr < spec.nRowsInTile
    //   tc will always be in the range 0 <= tc < spec.nColsInTile.
    //   tr0 is the first row of interest in the tile.
    //   tr1 is the last row of interest in the tile.
    //   similar for gr, gc and br, bc.
    //       When the variable name is prefixed with a letter, it means that
    //   it gives the equivalent position in the indicated system.
    //   for example  gbr0  is the grid (g) coordinate for the first
    //   row of interest in the result block, etc.  Note that the gtr0, gtr1,
    //   etc. values change depending on which tile is currently being read
    //   and it's relationship to the overall grid.
    //       Special names are used for tileRow0, tileCol0, tileRow1, tileCol0
    //   the are the row number and column numbers for the tiles.
    //   For example to find the row of tiles associated with
    //   a particular grid coordinate:  tileRow0 = gr0/spec.nRowsInTile, etc.

    if (this.isClosed) {
      throw new IOException("Raster file is closed");
    }
    if (nRows < 1 || nColumns < 1) {
      throw new IOException(
              "Invalid dimensions: nRows=" + nRows + ", nColumns=" + nColumns);
    }
    // bounds checking for resulting grid row and column computations
    // are performed in the accessElements.computeElements() method
    // which will throw an exception if bounds are violated.
    int nValuesInSubBlock = nRows * nColumns;
    float[] block = new float[nValuesInSubBlock * spec.rank];
    int gr0 = row;
    int gc0 = column;
    int gr1 = row + nRows - 1;
    int gc1 = column + nColumns - 1;
    accessElements.computeElements(gr0, gc0);
    int tileRow0 = accessElements.tileRow;
    int tileCol0 = accessElements.tileCol;
    accessElements.computeElements(gr1, gc1);
    int tileRow1 = accessElements.tileRow;
    int tileCol1 = accessElements.tileCol;

    for (int tileRow = tileRow0; tileRow <= tileRow1; tileRow++) {
      // find the tile row limits tr0 and tr1 for this row of tiles.
      // because the tiles in this row may extend beyond the requested
      // range of grid rows, we need to enforce limits.
      int gtRowOffset = tileRow * spec.nRowsInTile;
      int gtr0 = gtRowOffset;
      int gtr1 = gtRowOffset + spec.nRowsInTile - 1;
      // enforce limits
      if (gtr0 < gr0) {
        gtr0 = gr0;
      }
      if (gtr1 > gr1) {
        gtr1 = gr1;
      }
      int tr0 = gtr0 - gtRowOffset; // must be in range 0 to spec.nRowsInTile.
      int tr1 = gtr1 - gtRowOffset; //    ""        ""          ""
      for (int tileCol = tileCol0; tileCol <= tileCol1; tileCol++) {

        int gtColOffset = tileCol * spec.nColsInTile;
        int gtc0 = gtColOffset;
        int gtc1 = gtColOffset + spec.nColsInTile - 1;
        // enforce limits
        if (gtc0 < gc0) {
          gtc0 = gc0;
        }
        if (gtc1 > gc1) {
          gtc1 = gc1;
        }
        int tc0 = gtc0 - gtColOffset;
        int tc1 = gtc1 - gtColOffset;

        int tileIndex = tileRow * spec.nColsOfTiles + tileCol;
        RasterTile tile = tileCache.getTile(tileIndex);
        if (tile instanceof RasterTileFloat) {
          for (int iRank = 0; iRank < spec.rank; iRank++) {
            float[] v = ((RasterTileFloat) tile).valuesArray[iRank];
            for (int tr = tr0; tr <= tr1; tr++) {
              int br = tr + gtRowOffset - gr0;
              int bc = tc0 + gtColOffset - gc0;
              int bIndex = br * nColumns + bc + iRank * nValuesInSubBlock;
              int tIndex = tr * spec.nColsInTile + tc0;
              for (int tc = tc0; tc <= tc1; tc++) {
                block[bIndex] = v[tIndex];
                bIndex++;
                tIndex++;
              }
            }
          }
        } else if (tile instanceof RasterTileInt) {
          for (int iRank = 0; iRank < spec.rank; iRank++) {
            int[] v = ((RasterTileInt) tile).valuesArray[iRank];
            for (int tr = tr0; tr <= tr1; tr++) {
              int br = tr + gtRowOffset - gr0;
              int bc = tc0 + gtColOffset - gc0;
              int bIndex = br * nColumns + bc + iRank * nValuesInSubBlock;
              int tIndex = tr * spec.nColsInTile + tc0;
              for (int tc = tc0; tc <= tc1; tc++) {
                int s = v[tIndex];
                if (s == NULL_DATA_CODE) {
                  block[bIndex] = Float.NaN;
                } else {
                  block[bIndex] = v[tIndex] / tile.valueScale + tile.valueOffset;
                }
                bIndex++;
                tIndex++;
              }
            }
          }
        } else {
          for (int iRank = 0; iRank < spec.rank; iRank++) {
            for (int tr = tr0; tr <= tr1; tr++) {
              int br = tr + gtRowOffset - gr0;
              int bc = tc0 + gtColOffset - gc0;
              int bIndex = br * nColumns + bc + iRank * nValuesInSubBlock;
              for (int tc = tc0; tc <= tc1; tc++) {
                block[bIndex] = Float.NaN;
                bIndex++;
              }
            }
          }
        }
      }
    }
    return block;
  }

}
