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
import java.io.UnsupportedEncodingException;
import org.gridfour.util.Angle;

import static java.lang.Double.isFinite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.gridfour.io.BufferedRandomAccessFile;
import static org.gridfour.g93.G93FileConstants.NULL_DATA_CODE;

/**
 * Provides a specification for creating G93File instances.
 */
public class G93FileSpecification {

  /**
   * The sub-version identifier to be used by all raster-file and related
   * implementations in this package.
   */
  static final byte VERSION = 0;
  /**
   * The sub-version identifier to be used by all raster-file and related
   * implementations in this package.
   */
  static final byte SUB_VERSION = 1;

  private static final int IDENTIFICATION_SIZE = 64;


  /**
   * Time of construction for the specification
   */
  long timeCreated;

  /**
   * Number of rows in the overall raster grid
   */
  final int nRowsInRaster;

  /**
   * Number of columns in the overall raster grid
   */
  final int nColsInRaster;

  /**
   * The number of rows in the tiling scheme
   */
  final int nRowsInTile;

  /**
   * The number of columns in the tiling scheme
   */
  final int nColsInTile;

  /**
   * The number of rows of tiles. If the nRowsInTile specification does not
   * evenly subdivide the nRowsInRaster specification, it is assumed that the
   * last row of tiles is partially populated.
   */
  final int nRowsOfTiles;

  /**
   * The number of columns of tiles. If the nColumnsInTile specification does
   * not evenly subdivide the nRowsInRaster specification, it is assumed that
   * the last column of tiles is partially populated.
   */
  final int nColsOfTiles;

  /**
   * The product of the number of rows and columns in the tile.
   */
  final int nCellsInTile;

  final UUID uuid;

  boolean isGeographicCoordinateSystemSet;
  boolean isCartesianCoordinateSystemSet;
  double x0;
  double y0;
  double x1;
  double y1;
  double cellSizeX;
  double cellSizeY;

  // the non-extended file size option works by compressing file positions
  // so that they can be stored as a 4-byte unsigned integer.  This limits
  // the size of the file to 32 GB.  At this time, we have not implemented
  // the extended file size option.
  boolean isExtendedFileSizeEnabled = false;

  /**
   * Indicates whether tile-data is to be compressed when the tile is saved.
   */
  private boolean dataCompressionEnabled;

  /**
   * An arbitrary, application-assigned identification string.
   */
  String identification;

  int rank = 1;
  G93DataType dataType = G93DataType.IntegerFormat;
  float valueScale = 1;
  float valueOffset = 0;
  int standardTileSizeInBytes;

  G93GeometryType geometryType
          = G93GeometryType.Unspecified;

  LinkedHashMap<String, Class<?>> rasterCodecMap = new LinkedHashMap<>();

  /**
   * Construct a specification for creating a G93 raster with the indicated
   * dimensions.
   * <p>
   * If the number of rows or columns in a tile specification does not evenly
   * divide the number of rows or columns in the overall raster, it is assumed
   * that the last row or column of tiles is partially populated.
   * <p>
   * Due to internal implementation limits, the maximum number of tiles is the
   * maximum size of a 4-byte signed integer(2147483647). This condition could
   * occur given a very large raster grid combined with an insufficiently large
   * tile size.
   * <p>
   * For example, if an application were using geographic coordinates wait a one
   * second of arc cell spacing for a grid, it would require a grid of
   * dimensions 1296000 by 648000. A tile size of 60-by-60 would ensure that the
   * maximum number of tiles was just over 233 million, and well within the
   * limits imposed by this implementation. However, a tile size of 10-by-10
   * would product a maximum number of tiles of over 8 billion, and would exceed
   * the limits of the implementation.
   *
   * @param nRowsInRaster the number of rows in the raster
   * @param nColumnsInRaster the number of columns in the raster
   * @param nRowsInTile the number of rows in the tiling scheme
   * @param nColumnsInTile the number of columns in the tiling scheme
   */
  public G93FileSpecification(
          int nRowsInRaster,
          int nColumnsInRaster,
          int nRowsInTile,
          int nColumnsInTile) {
    rasterCodecMap.put(RasterCodecType.G93_Huffman.toString(), CodecHuffman.class);
    rasterCodecMap.put(RasterCodecType.G93_Deflate.toString(), CodecDeflate.class);

    uuid = UUID.randomUUID();
    timeCreated = System.currentTimeMillis();
    this.nRowsInRaster = nRowsInRaster;
    this.nColsInRaster = nColumnsInRaster;
    if (nRowsInTile == 0 && nColumnsInTile == 0) {
      this.nRowsInTile = nRowsInRaster;
      this.nColsInTile = nColsInRaster;
    } else {
      this.nRowsInTile = nRowsInTile;
      this.nColsInTile = nColumnsInTile;
    }

    if (nRowsInRaster <= 0 || nColumnsInRaster <= 0) {
      throw new IllegalArgumentException(
              "Invalid dimensions for raster "
              + "(" + nRowsInRaster + "," + nColumnsInRaster + ")");
    }
    if (nRowsInTile <= 0 || nColumnsInTile <= 0) {
      throw new IllegalArgumentException(
              "Invalid dimensions for raster "
              + "(" + nRowsInRaster + "," + nColumnsInRaster + ")");
    }

    if (nRowsInTile > nRowsInRaster || nColumnsInTile > nColumnsInRaster) {
      throw new IllegalArgumentException(
              "Dimensions of tile "
              + "(" + nRowsInTile + "," + nColumnsInTile + ")"
              + " exceed those of overall raster ("
              + "(" + nRowsInRaster + "," + nColumnsInRaster + ")");
    }

    nRowsOfTiles = (nRowsInRaster + nRowsInTile - 1) / nRowsInTile;
    nColsOfTiles = (nColsInRaster + nColsInTile - 1) / nColsInTile;

    long nTiles = (long) nRowsOfTiles * (long) nColsOfTiles;
    if (nTiles > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
              "The number of potential tiles exceeds "
              + "the size of a signed integer (2147483647)");
    }
    x0 = 0;
    y0 = 0;
    x1 = nColsInRaster - 1;
    y1 = nRowsInRaster - 1;
    cellSizeX = (x1 - x0) / nColsInRaster;
    cellSizeY = (y1 - y0) / nRowsInRaster;

    nCellsInTile = nRowsInTile * nColsInTile;

    standardTileSizeInBytes = rank * nCellsInTile * dataType.getBytesPerSample();
  }

  /**
   * Construct a new instance copying the values from the supplied object.
   *
   * @param s a valid instance of SimpleRasterSpecification.
   */
  public G93FileSpecification(G93FileSpecification s) {
    uuid = s.uuid;
    timeCreated = s.timeCreated;
    nRowsInRaster = s.nRowsInRaster;
    nColsInRaster = s.nColsInRaster;
    nRowsInTile = s.nRowsInTile;
    nColsInTile = s.nColsInTile;
    nRowsOfTiles = s.nRowsOfTiles;
    nColsOfTiles = s.nColsOfTiles;
    nCellsInTile = s.nCellsInTile;

    rank = s.rank;
    dataType = s.dataType;
    valueScale = s.valueScale;
    valueOffset = s.valueOffset;

    isGeographicCoordinateSystemSet = s.isGeographicCoordinateSystemSet;
    isCartesianCoordinateSystemSet = s.isCartesianCoordinateSystemSet;
    x0 = s.x0;
    y0 = s.y0;
    x1 = s.x1;
    y1 = s.y1;
    cellSizeX = s.cellSizeX;
    cellSizeY = s.cellSizeY;

    standardTileSizeInBytes = s.standardTileSizeInBytes;

    isExtendedFileSizeEnabled = s.isExtendedFileSizeEnabled;
    geometryType = s.geometryType;
    dataCompressionEnabled = s.dataCompressionEnabled;
    List<G93SpecificationForCodec> sList = s.getCompressionCodecs();
    for (G93SpecificationForCodec srcs : sList) {
      rasterCodecMap.put(srcs.getIdentification(), srcs.getCodec());
    }
  }

  /**
   * Sets the data model to float with the specified rank.
   * <p>
   * This method provides the associated scale and offset parameters to be used
   * to convert floating-point data to integer values when integer-based data
   * compression is used. Because the data-compression techniques currently
   * implemented in G93File operate over integer representations of data, it is
   * necessary to convert floating point values to integers by scaling them and
   * potentially adding an offset factor.
   * <pre>
   * intValue = (floatValue-offset)*scale.
   * floatValue = (intValue/scale)+offset;
   * </pre>
   * <p>
   * In cases where no adjustment is required, simple supply a scale factor of
   * 1.0 and an offset of zero.
   *
   *
   * @param rank the rank of the data.
   * @param scale non-zero value for scaling data
   * @param offset an offset factor (or zero if desired
   */
  public void setDataModelFloat(int rank, float scale, float offset) {
    if (scale == 0 || Float.isNaN(scale)) {
      throw new IllegalArgumentException(
              "A scale value of zero or Float.NaN is not supported");
    }
    if (Float.isNaN(offset)) {
      throw new IllegalArgumentException(
              "An offset value of Float.NaN is not supported");
    }
    if (rank < 1) {
      throw new IllegalArgumentException(
              "Zero or negative rank value not supported");
    }
    this.rank = rank;
    dataType = G93DataType.FloatFormat;
    valueScale = scale;
    valueOffset = offset;
    standardTileSizeInBytes
            = rank * nRowsInTile * nColsInTile * dataType.getBytesPerSample();
  }

  public void setDataModelInt(int rank) {
    if (rank < 1) {
      throw new IllegalArgumentException(
              "Zero or negative rank value not supported");
    }
    this.rank = rank;
    dataType = G93DataType.IntegerFormat;
    valueScale = 1.0F;
    valueOffset = 0.0F;
    standardTileSizeInBytes
            = rank * nRowsInTile * nColsInTile * dataType.getBytesPerSample();
  }

  /**
   * Gets the standard size of the data when stored in non-compressed format.
   * This size is the product of rank, number of rows and columns, and the size
   * of the data element (usually 4 for integers or floats).
   *
   * @return a positive value greater than or equal to 1.
   */
  int getStandardTileSizeInBytes() {
    return standardTileSizeInBytes;
  }

  /**
   * Set a geographic coordinate system to be used for interpreting the data.
   * Note that this setting is mutually exclusive with the Cartesian coordinate
   * system setting. The last setting applied replaces any earlier settings.
   * <p>
   * Various data sources take different approaches in terms of how they order
   * their raster data in relationship to latitude. Some start with the
   * northernmost latitude and work their way south, some start with the
   * southernmost latitude and work their way north. Thus the arguments for this
   * method are based on the ordering of the raster. The first pair of arguments
   * give the coordinates for the first row and column in the grid. The second
   * pair of arguments give the coordinates for the last.
   * <p>
   * Unfortunately, the possibility of longitude wrapping around the
   * International Date line limits the flexibility for longitude
   * specifications. This implementation assumes that the raster is organized so
   * that the longitudes progress from west to east (longitude increases with
   * increasing grid index). Thus, if a longitude of -90 to 90 were specified,
   * it would assume that the raster columns went from 90 west to 90 east. But
   * if 90, -90 were specified, it would be assumed that the raster grid went
   * from 90 east, across the International Date Line, to 90 west.
   *
   * @param latRow0 the latitude of the first row in the grid
   * @param lonCol0 the longitude of the first column of the raster (column 0).
   * @param latRowMax the latitude of the last row of the raster.
   * @param lonColMax the longitude of the the last column of the raster.
   */
  public void setGeographicCoordinates(
          double latRow0, double lonCol0, double latRowMax, double lonColMax) {
    this.isGeographicCoordinateSystemSet = true;
    this.isCartesianCoordinateSystemSet = false;
    if (!isFinite(latRow0)
            || !isFinite(lonCol0)
            || !isFinite(latRowMax)
            || !isFinite(lonColMax)) {
      throw new IllegalArgumentException("Invalid floating-point value");
    }
    if (latRowMax <= latRow0) {
      throw new IllegalArgumentException(
              "Northwest latitude must be greater than Southeast latitude");
    }

    double gxDelta = Angle.to360(lonColMax - lonCol0);
    if (gxDelta == 0) {
      gxDelta = 360;
    }
    double gx0 = Angle.to180(lonCol0);
    double gx1 = gx0 + gxDelta;
    x0 = gx0;
    y0 = latRow0;
    x1 = gx1;
    y1 = latRowMax;
    cellSizeX = (x1 - x0) / nColsInRaster;
    cellSizeY = (y1 - y0) / nRowsInRaster;
  }

  /**
   * Set a Cartesian coordinate system to be used for interpreting the data.
   * Note that this setting is mutually exclusive with the geographic coordinate
   * system setting. The last setting applied replaces any earlier settings
   *
   * @param x0 the X coordinate of the lower-left corner of the raster and the
   * first column of the raster (column 0).
   * @param y0 the Y coordinate of the lower-left corner of the raster and the
   * first row of the raster (row 0).
   * @param x1 the X coordinate of the upper-right corner of the raster and the
   * last column of the raster.
   * @param y1 the Y coordinate of the upper-right corner of the raster and the
   * last row of the raster.
   */
  public void setCartesianCoordinates(double x0, double y0, double x1, double y1) {
    this.isGeographicCoordinateSystemSet = true;
    this.isCartesianCoordinateSystemSet = false;
    if (!isFinite(x0) || !isFinite(y0) || !isFinite(x1) || !isFinite(y1)) {
      throw new IllegalArgumentException("Invalid floating-point value");
    }
    if (x0 == x1) {
      throw new IllegalArgumentException(
              "Cartesian coordinate x0 must not equal x1");
    }
    if (y0 == y1) {
      throw new IllegalArgumentException(
              "Cartesian coordinate y0 must not equal y1");
    }

    x0 = x0;
    y0 = y0;
    x1 = x1;
    y1 = y1;
    cellSizeX = (x1 - x0) / nColsInRaster;
    cellSizeY = (y1 - y0) / nRowsInRaster;
  }

  /**
   * Set an arbitrary identification string for the raster. The identification
   * may be a string of characters in UTF-8 encoding of up to 64 bytes in
   * length. For most European alphabets, this size equates to 64 characters.
   *
   * @param identification the identification string
   */
  public void setIdentification(String identification) {
    this.identification = identification;

    try {
      byte[] b = identification.getBytes("UTF-8");
      if (b.length > IDENTIFICATION_SIZE) {
        throw new IllegalArgumentException(
                "Identification string exceeds 64 byte limit "
                + "when encoded in UTF-8 character set (size="
                + b.length + ")");
      }
    } catch (UnsupportedEncodingException ex) {
      throw new IllegalArgumentException(ex.getMessage());
    }
  }

  /**
   * Gets array of bytes of length 64, intended for writing a g93 Raster file.
   *
   * @return a valid array of length 64, potentially all zeros if empty.
   */
  public byte[] getIdentificationBytes() {
    byte[] output = new byte[IDENTIFICATION_SIZE];
    if (identification != null && !identification.isEmpty()) {
      try {
        byte[] b = identification.getBytes("UTF-8");
        System.arraycopy(b, 0, output, 0, IDENTIFICATION_SIZE);
      } catch (UnsupportedEncodingException ex) {
        String s = "Unsupported Encoding";
        output = new byte[IDENTIFICATION_SIZE];
        for (int i = 0; i < s.length(); i++) {
          output[i] = (byte) s.charAt(i);
        }
      }
    }
    return output;
  }

  /**
   * Construct a specification from the specified file
   *
   * @param braf a valid instance, positioned to start of the specification
   * data.
   * @throws IOException in the event of an unrecoverable I/O error
   */
  public G93FileSpecification(BufferedRandomAccessFile braf) throws IOException {
    timeCreated = System.currentTimeMillis();

    long uuidLow = braf.leReadLong();
    long uuidHigh = braf.leReadLong();
    uuid = new UUID(uuidHigh, uuidLow);

    byte[] b = new byte[IDENTIFICATION_SIZE];
    braf.readFully(b);
    if (b[0] != 0) {
      identification = new String(b, "UTF-8");
    }

    nRowsInRaster = braf.leReadInt();
    nColsInRaster = braf.leReadInt();
    nRowsInTile = braf.leReadInt();
    nColsInTile = braf.leReadInt();
    nCellsInTile = nRowsInTile * nColsInTile;

    nRowsOfTiles = (nRowsInRaster + nRowsInTile - 1) / nRowsInTile;
    nColsOfTiles = (nColsInRaster + nColsInTile - 1) / nColsInTile;

    // This loop is implemented in advance of planned changes to the
    // API.  Currently, all values are assumed to be of a consistent
    // definition between value blocks (rank is the number of blocks).
    // Furture implementations may allow variations
    rank = braf.leReadInt();
    byte[] codeValue = new byte[4];
    braf.readFully(codeValue, 0, 4);
    dataType = G93DataType.valueOf(codeValue[0]);
    // the three other bytes are spares.
    codeValue[0] = (byte) dataType.getCodeValue();
    valueScale = braf.leReadFloat();
    valueOffset = braf.leReadFloat();
    braf.skipBytes(4);  // would be an integer or a float
    // currently, the additional block definitions would be 
    // repetitions of the first one.  In future releases, that may change.
    // For now, just skip them.
    if (rank > 1) {
      braf.skipBytes((rank - 1) * 4 * 4);
    }

    isExtendedFileSizeEnabled = braf.readBoolean();
    int geometryCode = braf.readUnsignedByte();
    geometryType = G93GeometryType.valueOf(geometryCode);

    int coordinateSystem = braf.readUnsignedByte();
    if (coordinateSystem == 1) {
      isCartesianCoordinateSystemSet = true;
    } else if (coordinateSystem == 2) {
      isGeographicCoordinateSystemSet = true;
    }

    x0 = braf.leReadDouble();
    y0 = braf.leReadDouble();
    x1 = braf.leReadDouble();
    y1 = braf.leReadDouble();
    cellSizeX = (x1 - x0) / nColsInRaster;
    cellSizeY = (y1 - y0) / nRowsInRaster;

    int nCompressionSpecifications = braf.leReadInt();
    if (nCompressionSpecifications > 0) {
      this.dataCompressionEnabled = true;
      String codecID = braf.readASCII(16);
      this.addCompressionCodec(codecID, CodecPlaceHolder.class);
    }

    standardTileSizeInBytes
            = rank * nRowsInTile * nColsInTile * dataType.getBytesPerSample();
  }

  /**
   * Writes the header for the G93File
   *
   * @param braf the
   * @param g93FileType
   * @param majorVersion
   * @param minorVersion
   * @throws IOException
   */
  void write(BufferedRandomAccessFile braf) throws IOException {
    braf.leWriteLong(uuid.getLeastSignificantBits());
    braf.leWriteLong(uuid.getMostSignificantBits());
    braf.write(getIdentificationBytes());  // 64 bytes

    braf.leWriteInt(nRowsInRaster);
    braf.leWriteInt(nColsInRaster);
    braf.leWriteInt(nRowsInTile);
    braf.leWriteInt(nColsInTile);

    // This loop is implemented in advance of planned changes to the
    // API.  Currently, all values are assumed to be of a consistent
    // definition between value blocks (rank is the number of blocks).
    // Furture implementations may allow variations
    braf.leWriteInt(rank);
    for (int i = 0; i < rank; i++) {
      byte[] codeValue = new byte[4];
      codeValue[0] = (byte) dataType.getCodeValue();
      // next 3 bytes are spares
      braf.writeFully(codeValue, 0, codeValue.length);
      braf.leWriteFloat(valueScale);
      braf.leWriteFloat(valueOffset);
      if (dataType == G93DataType.FloatFormat) {
        braf.leWriteFloat(Float.NaN);
      } else {
        braf.leWriteInt(Integer.MIN_VALUE);
      }
    }

    braf.writeBoolean(isExtendedFileSizeEnabled);
    braf.writeByte(geometryType.getCodeValue());

    int coordinateSystem = 0;
    if (isCartesianCoordinateSystemSet) {
      coordinateSystem = 1;
    } else if (isGeographicCoordinateSystemSet) {
      coordinateSystem = 2;
    }

    braf.writeUnsignedByte(coordinateSystem);
    braf.leWriteDouble(x0);
    braf.leWriteDouble(y0);
    braf.leWriteDouble(x1);
    braf.leWriteDouble(y1);

    if (isDataCompressionEnabled()) {
      List<G93SpecificationForCodec> sList = getCompressionCodecs();
      braf.leWriteInt(sList.size());
      for (G93SpecificationForCodec srcs : sList) {
        braf.writeASCII(srcs.getIdentification(), 16);
      }
    } else {
      braf.leWriteInt(0);
    }

  }

  /**
   * Indicates whether data compression is enabled
   *
   * @return true if data compression is emabled; otherwise, false
   */
  public boolean isDataCompressionEnabled() {
    return dataCompressionEnabled;
  }

  /**
   * Sets data compression to enabled or disabled.
   *
   * @param dataCompressionEnabled true if data compression is to be applied;
   * otherwise, false.
   */
  public void setDataCompressionEnabled(boolean dataCompressionEnabled) {
    this.dataCompressionEnabled = dataCompressionEnabled;
  }

  /**
   * Gets the number of rows in the overall raster.
   *
   * @return a positive number
   */
  public int getRowCount() {
    return this.nRowsInRaster;
  }

  /**
   * Gets the number of columns in the overall raster.
   *
   * @return a positive number
   */
  public int getColumnCount() {
    return this.nColsInRaster;
  }

  public int getRowsOfTilesCount() {
    return this.nRowsOfTiles;
  }

  public int getColumnsOfTilesCount() {
    return this.nColsOfTiles;
  }

  public int getRowsInTileCount() {
    return this.nRowsInTile;
  }

  public int getColumnsInTileCount() {
    return this.nColsInTile;
  }

  /**
   * Indicates whether the extended file size option is enabled. If the extended
   * file size option is not enabled, the maximum size of a file is 32
   * gigabytes. Larger files may be specified using this option, but doing so
   * will increase the amount of internal memory used to process the files.
   *
   * @return true if extended file sizes are enabled; otherwise false.
   */
  public boolean isExtendedFileSizeEnabled() {
    return this.isExtendedFileSizeEnabled;
  }

  /**
   * Indicates whether the extended file size option is enabled. If the extended
   * file size option is not enabled, the maximum size of a file is 32
   * gigabytes. Larger files may be specified using this option, but doing so
   * will increase the amount of internal memory used to process the files.
   * <p>
   * <strong>Warning:</strong>At this time the extended file size option is not
   * implemented by the G93File class.
   *
   * @param extendedFileSizeEnabled true if extended file sizes are enabled;
   * otherwise false.
   */
  public void setExtendedFileSizeEnabled(boolean extendedFileSizeEnabled) {
    this.isExtendedFileSizeEnabled = extendedFileSizeEnabled;
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
    if (Float.isNaN(value)) {
      return NULL_DATA_CODE;
    }
    return (int) Math.floor((value - valueOffset) * valueScale + 0.5);
  }

  /**
   * Maps the specified integer value to the equivalent floating point value as
   * defined for the G93File.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through the setValueTransform() method when the associated file
   * was created.
   *
   * @param value an integer value
   * @return the equivalent floating point value, or NaN if appropriate.
   */
  public float mapIntToValue(int value) {
    if (value == NULL_DATA_CODE) {
      return Float.NaN;
    }
    return value / valueScale + valueOffset;
  }

  /**
   * Map Cartesian coordinates to grid coordinates storing the row and column in
   * an array in that order. If the x or y coordinate is outside the ranges
   * defined for these parameters, the resulting rows and columns may be outside
   * the range of the valid grid coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the setCartesianCoordinates{} method.
   *
   * @param x a valid floating-point coordinate
   * @param y a valid floating-point coordinate
   * @return an array giving row and column in that order; the results may be
   * non-integral values.
   */
  public double[] mapCartesianToGrid(double x, double y) {
    double[] grid = new double[2];
    grid[0] = (nRowsInRaster - 1) * (y - y0) / (y1 - y0);  // row
    grid[1] = (nColsInRaster - 1) * (x - x0) / (x1 - x0);
    return grid;
  }

  /**
   * Map grid coordinates to Cartesian coordinates storing the resulting x and y
   * values in an array in that order. If the row or column values are outside
   * the ranges defined for those parameters, the resulting x and y values may
   * be outside the bounds of the standard Cartesian coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the setCartesianCoordinates{} method.
   *
   * @param row a row (may be a non-integral value)
   * @param column a column (may be a non-integral value)
   * @return a valid array giving the Cartesian x and y coordinates in that
   * order.
   */
  public double[] mapGridToCartesian(double row, double column) {
    double[] c = new double[2];
    c[0] = (x1 - x0) * column / (nColsInRaster - 1) + x0;
    c[1] = (y1 - y0) * row / (nRowsInRaster) + y0;
    return c;
  }

  /**
   * Map geographic coordinates to grid coordinates storing the row and column
   * in an array in that order. If the latitude or longitude is outside the
   * ranges defined for these parameters, the resulting rows and columns may be
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
   * @return an array giving row and column in that order; the results may be
   * non-integral values.
   */
  public double[] mapGeographicToGrid(double latitude, double longitude) {
    double[] grid = new double[2];
    grid[0] = (nRowsInRaster - 1) * (latitude - y0) / (y1 - y0);  // row
    double delta = Angle.to360(longitude - x0);
    grid[1] = (nColsInRaster - 1) * delta / (x1 - x0);
    return grid;
  }

  /**
   * Map grid coordinates to Geographic coordinates storing the resulting x and
   * y values in an array in that order. If the row or column values are outside
   * the ranges defined for those parameters, the resulting x and y values may
   * be outside the bounds of the standard Geographic coordinates.
   * <p>
   * The transformation performed by this method is based on the parameters
   * established through a call to the setCartesianCoordinates{} method.
   *
   * @param row the row coordinate (may be non-integral)
   * @param column the column coordinate (may be non-integral)
   * @return a valid array giving latitude and longitude in that order.
   */
  public double[] mapGridToGeographic(double row, double column) {
    double[] c = new double[2];
    c[0] = (y1 - y0) * row / (nRowsInRaster) + y0;
    c[1] = (x1 - x0) * column / (nColsInRaster - 1) + x0;
    return c;
  }

  /**
   * Sets the geometry type associated with the row and column positions in the
   * raster. If the geometry type is Point, the value at a row and column is
   * intended to represent the value at a particular point on the surface
   * described by the raster. If the geometry type is Area, the value at a row
   * and column is intended to represent the value for an entire cell centered
   * at the coordinates associated with the row and column.
   *
   * @param geometryType a valid instance of the enumeration.
   */
  public void setGeometryType(G93GeometryType geometryType) {
    this.geometryType = geometryType;
  }

  /**
   * Gets the geometry type for this specification.
   *
   * @return a valid instance of the enumeration.
   */
  public G93GeometryType getGeometryType() {
    return geometryType;
  }

  /**
   * Gets the sizes for the cells based on the coordinate system set in the
   * specification.
   *
   * @return a valid array of dimension 2 giving, respectively, the x and y cell
   * sizes.
   */
  public double[] getCellSizes() {
    double[] s = new double[2];
    s[0] = cellSizeX;
    s[1] = cellSizeY;
    return s;
  }

  /**
   * Gets the count of the number of cells in a tile.
   *
   * @return a value greater than or equal to 1.
   */
  public int getCellCountForTile() {
    return nCellsInTile;
  }

  /**
   * Removes all compression coder-decoder (codec) definitions from the
   * specification.
   */
  public void removeAllCompressionCodecs() {
    this.rasterCodecMap.clear();
  }

  /**
   * Removes the indicated compression codec from the specification. Note that
   * removing a codec may alter the indexing code for other specifications
   * obtained from the getCompressionCodecs() method.
   *
   * @param codecID a valid identification string
   * @return true if the the identification was found in the specification and
   * was removed; otherwise, false.
   */
  public boolean removeCompressionCodec(String codecID) {
    if (codecID != null && rasterCodecMap.containsKey(codecID)) {
      rasterCodecMap.remove(codecID);
      return true;
    }
    return false;
  }

  /**
   * Adds a data compression Codec to the specification. The specification
   * allows a maximum of 256 codecs.
   *
   * @param codecID a unique identification following the syntax of Java
   * identifier strings, 16 character maximum.
   * @param codec a valid class reference.
   */
    public final void addCompressionCodec(String codecID, Class<?> codec) {
    if (rasterCodecMap.size() >= 255) {
      throw new IllegalArgumentException(
              "Maximum number of compression codecs (255) exceeded");
    }
    if (codecID == null || codecID.isEmpty() || codec == null) {
      throw new NullPointerException("Missing name or codec class");
    }

    if (codec.getCanonicalName() == null) {
      throw new IllegalArgumentException("Input class must have a canonical name for " + codecID);
    }

    for (int i = 0; i < codecID.length(); i++) {
      char c = codecID.charAt(i);
      if (!Character.isJavaIdentifierPart(c)) {
        throw new IllegalArgumentException(
                "Compression codec identification is not a valid"
                + " identifier string, unable to process \""
                + codecID + "\"");
      }
    }
    if (codecID.length() > 16) {
      throw new IllegalArgumentException(
              "Maximum identification length is 16 characters: " + codecID);
    }

    boolean interfaceConfirmed = false;
    Class[] interfaces = codec.getInterfaces();
    for (Class c : interfaces) {
      if (c.equals(IG93CompressorCodec.class)) {
        interfaceConfirmed = true;
        break;
      }
    }
    if (!interfaceConfirmed) {
      throw new IllegalArgumentException(
              "Codec " + codecID + "does not implement ISimpleRasterCodec");
    }
    if (rasterCodecMap.containsKey(codecID)) {
      throw new IllegalArgumentException(
              "Attempt to add pre-existing codec " + codecID);
    }

    rasterCodecMap.put(codecID, codec);
  }

  /**
   * Get the compression codes currently registered with the specification
   *
   * @return a valid list of specifications, potentially empty.
   */
  public List<G93SpecificationForCodec> getCompressionCodecs() {
    Set<String> keySet = rasterCodecMap.keySet();
    List<G93SpecificationForCodec> list = new ArrayList<>();
    int k = 0;
    for (String key : keySet) {
      Class c = rasterCodecMap.get(key);
      list.add(new G93SpecificationForCodec(key, c, k++));
    }
    return list;
  }

  public void summarize(PrintStream ps) {
    ps.format("Identification:    %s%n",
            identification == null || identification.isEmpty()
            ? "Not Specified" : identification);
    ps.format("UUID:              %s%n", uuid.toString());
    long cellsInRaster = (long) nRowsInRaster * (long) nColsInRaster;

    ps.format("Rows in Raster:    %12d%n", nRowsInRaster);
    ps.format("Columns in Raster: %12d%n", nColsInRaster);
    ps.format("Rows in Tile:      %12d%n", nRowsInTile);
    ps.format("Columns in Tile:   %12d%n", nColsInTile);
    ps.format("Rows of Tiles:     %12d%n", nRowsOfTiles);
    ps.format("Columns of Tiles:  %12d%n", nColsOfTiles);
    ps.format("Cells in Raster:   %12d%n", cellsInRaster);
    ps.format("Cells in Tile:     %12d%n", nCellsInTile);
    ps.println("");
    ps.format("Range x values:      %11.6f, %11.6f, (%f)%n", x0, x1, x1 - x0);
    ps.format("Range y values:      %11.6f, %11.6f, (%f)%n", y0, y1, y1 - y0);
    ps.format("Value scale factor:  %11.6f%n", valueScale);
    ps.format("Value offset factor: %11.6f%n", valueOffset);
    ps.format("Data compression:       %s%n",
            isDataCompressionEnabled() ? "enabled" : "disabled");
  }
}
