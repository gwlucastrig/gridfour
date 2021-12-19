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

import org.gridfour.compress.CodecDeflate;
import org.gridfour.compress.CodecHuffman;
import org.gridfour.compress.CodecFloat;
import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.PrintStream;
import static java.lang.Double.isFinite;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.gridfour.io.BufferedRandomAccessFile;
import org.gridfour.util.Angle;

/**
 * Provides a specification for creating GvrsFile instances.
 */
public class GvrsFileSpecification {

  /**
   * The sub-version identifier to be used by all raster-file and related
   * implementations in this package.
   */
  static final byte VERSION = 0;
  /**
   * The sub-version identifier to be used by all raster-file and related
   * implementations in this package.
   */
  static final byte SUB_VERSION = 4;

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

  boolean isGeographicCoordinateSystemSet;
  boolean isCartesianCoordinateSystemSet;
  double x0;
  double y0;
  double x1;
  double y1;
  double cellSizeX;
  double cellSizeY;

  // the following will be set only in the case of a geographic
  // coordinate system
  boolean geoWrapsLongitude;
  boolean geoBracketsLongitude;

  // the non-extended file size option works by compressing file positions
  // so that they can be stored as a 4-byte unsigned integer.  This limits
  // the size of the file to 32 GB.  At this time, we have not implemented
  // the extended file size option.
  boolean isExtendedFileSizeEnabled = false;
  
  boolean isChecksumEnabled = false;

  /**
   * Indicates whether tile-data is to be compressed when the tile is saved.
   */
  private boolean dataCompressionEnabled;
 
  /**
   * An arbitrary, application-assigned identification string.
   */
  String identification;
 

  GvrsGeometryType geometryType  = GvrsGeometryType.Unspecified;

  List<CodecHolder> codecList = new ArrayList<>();
  List<String> codecIdentificationList = new ArrayList<>();

  List<GvrsElementSpecification> elementSpecifications = new ArrayList<>();

  private void addCodecSpec(String key, Class<?> codec) {
    GvrsIdentifier.checkIdentifier(key, 32);
    CodecHolder spec
      = new CodecHolder(key, codec, codec);
    codecList.add(spec);
  }

  private void initDefaultCodecList() {
    addCodecSpec(CodecType.GvrsHuffman.toString(), CodecHuffman.class);
    addCodecSpec(CodecType.GvrsDeflate.toString(), CodecDeflate.class);
    addCodecSpec(CodecType.GvrsFloat.toString(), CodecFloat.class);
  }

  /**
   * Updates the codec list based on information read during the file-opening
   * operation. This includes the list of specification identifiers that
   * is used to correlate a integer compression index read from a tile
   * with the actual codec. It also includes an optional list of
   * CodecSpecification elements that were read from the file.
   * CodecSpecfication elements are just a set of strings giving Java
   * class names and will be populated only if the file being read
   * was a Java file.
   *
   * @param specList a valid, potentially empty list of codec specification
   * identifiers and class-name elements.
   * @throws IOException in the event that a class matching a specified
   * class name cannot be resolved.
   */
  void integrateCodecSpecificationsFromFile(
    List<CodecSpecification> specList) throws IOException {

    List<CodecHolder> resultList = new ArrayList<>();
    for (String s : codecIdentificationList) {
      CodecHolder result;
      CodecHolder registeredHolder = null;
      for (CodecHolder holder : codecList) {
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

    // replace the current codec list with the results.
    // the current list will usually be the default list.
    codecList.clear();
    codecList.addAll(resultList);
  }

  /**
   * Construct a specification for creating a GVRS raster with the indicated
   * dimensions.
   * <p>
   * If the number of rows or columns in a tile specification does not evenly
   * divide the number of rows or columns in the overall raster, it is assumed
   * that the last row or column of tiles is partially populated.
   * <p>
   * Due to internal implementation limits, the maximum number of tiles is the
   * maximum size of a 4-byte signed integer(2147483647). This condition could
   * occur given a very large raster grid combined with an insufficiently
   * large
   * tile size.
   * <p>
   * For example, if an application were using geographic coordinates wait a
   * one second of arc cell spacing for a grid, it would require a grid of
   * dimensions 1296000 by 648000. A tile size of 60-by-60 would ensure that
   * the maximum number of tiles was just over 233 million, and well within the
   * limits imposed by this implementation. However, a tile size of 10-by-10
   * would product a maximum number of tiles of over 8 billion, and would
   * exceed the limits of the implementation.
   *
   * @param nRowsInRaster the number of rows in the raster
   * @param nColumnsInRaster the number of columns in the raster
   * @param nRowsInTile the number of rows in the tiling scheme
   * @param nColumnsInTile the number of columns in the tiling scheme
   */
  public GvrsFileSpecification(
    int nRowsInRaster,
    int nColumnsInRaster,
    int nRowsInTile,
    int nColumnsInTile) {


    timeCreated = System.currentTimeMillis();
    this.nRowsInRaster = nRowsInRaster;
    this.nColsInRaster = nColumnsInRaster;
    
     if (nRowsInTile == 0 && nColumnsInTile == 0) {
      // GVRS interprets zero values for rows-in-tile and columns-in-tile
      // as indicating that this class should compute values based
      // on its own rules.   At this time, we use simple logic.
      // In the future, we may try to develop something that ensures
      // a better fit across grids of various sizes.
      //   Note also that we use 120, rather than 128, because it has
      // more factors and, thus, a higher probability of being an integral
      // divisor of the overall grid size.
      if (nRowsInRaster < 120) {
        this.nRowsInTile = nRowsInRaster;
      } else {
        this.nRowsInTile = 120;
      }
      if (nColumnsInTile < 120) {
        this.nColsInTile = nColsInRaster;
      } else {
        this.nColsInTile = 120;
      }
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
    cellSizeX = (x1 - x0) / (nColsInRaster - 1);
    cellSizeY = (y1 - y0) / (nRowsInRaster - 1);

    nCellsInTile = nRowsInTile * nColsInTile;
    
    initDefaultCodecList();
    
  }
  
   /**
   * Construct a specification for creating a GVRS raster with the indicated
   * dimensions.  The internal tile sizes are automatically computed.
   *
   * @param nRowsInRaster the number of rows in the raster
   * @param nColumnsInRaster the number of columns in the raster
   */
  public GvrsFileSpecification(int nRowsInRaster, int nColumnsInRaster){
      this(nRowsInRaster, nColumnsInRaster, 0, 0);
  }

  /**
   * Construct a new instance copying the values from the supplied object.
   *
   * @param s a valid instance of GvrsFileSpecification.
   */
  public GvrsFileSpecification(GvrsFileSpecification s) {
    timeCreated = s.timeCreated;
    nRowsInRaster = s.nRowsInRaster;
    nColsInRaster = s.nColsInRaster;
    nRowsInTile = s.nRowsInTile;
    nColsInTile = s.nColsInTile;
    nRowsOfTiles = s.nRowsOfTiles;
    nColsOfTiles = s.nColsOfTiles;
    nCellsInTile = s.nCellsInTile;

    identification = s.identification;
    for(GvrsElementSpecification eSpec : s.elementSpecifications){
      elementSpecifications.add(eSpec.copy());
    }

    isGeographicCoordinateSystemSet = s.isGeographicCoordinateSystemSet;
    isCartesianCoordinateSystemSet = s.isCartesianCoordinateSystemSet;
    geoWrapsLongitude = s.geoWrapsLongitude;
    geoBracketsLongitude = s.geoBracketsLongitude;

    x0 = s.x0;
    y0 = s.y0;
    x1 = s.x1;
    y1 = s.y1;
    cellSizeX = s.cellSizeX;
    cellSizeY = s.cellSizeY;
  
    isExtendedFileSizeEnabled = s.isExtendedFileSizeEnabled;
    isChecksumEnabled = s.isChecksumEnabled;
    geometryType = s.geometryType;
    dataCompressionEnabled = s.dataCompressionEnabled;
    for (CodecHolder holder : s.codecList) {
      codecList.add(new CodecHolder(holder));
    }

  }

  /**
   * Gets the standard size of the data when stored in non-compressed format.
   * This size is the product of dimension, number of rows and columns, and
   * and the sum of the data sizes in bytes for the elements defined for each
   * raster cell.
   *
   * @return a positive value greater than or equal to 1.
   */
  int getStandardTileSizeInBytes() {
    int k = 0;
    for (GvrsElementSpecification eSpec : elementSpecifications) {
      int n = nRowsInTile * nColsInTile * eSpec.dataType.bytesPerSample;
      if (eSpec.dataType.bytesPerSample != 4) {
        n = (n + 3) & 0x7ffffffc;
      }
      k += n;
    }
    return k;
  }

  /**
   * Set a geographic coordinate system to be used for interpreting the data.
   * Note that this setting is mutually exclusive with the Cartesian
   * coordinate
   * system setting. The last setting applied replaces any earlier settings.
   * <p>
   * Various data sources take different approaches in terms of how they order
   * their raster data in relationship to latitude. Some start with the
   * northernmost latitude and work their way south, some start with the
   * southernmost latitude and work their way north. Thus the arguments for
   * this
   * method are based on the ordering of the raster. The first pair of
   * arguments
   * give the coordinates for the first row and column in the grid. The second
   * pair of arguments give the coordinates for the last.
   * <p>
   * Unfortunately, the possibility of longitude wrapping around the
   * International Date line limits the flexibility for longitude
   * specifications. This implementation assumes that the raster is organized
   * so
   * that the longitudes progress from west to east (longitude increases with
   * increasing grid index). Thus, if a longitude of -90 to 90 were specified,
   * it would assume that the raster columns went from 90 west to 90 east. But
   * if 90, -90 were specified, it would be assumed that the raster grid went
   * from 90 east, across the International Date Line, to 90 west.
   *
   * @param latRow0 the latitude of the first row in the grid
   * @param lonCol0 the longitude of the first column of the raster (column
   * 0).
   * @param latRowLast the latitude of the last row of the raster.
   * @param lonColLast the longitude of the the last column of the raster.
   */
  public void setGeographicCoordinates(
    double latRow0, double lonCol0, double latRowLast, double lonColLast) {
    this.isGeographicCoordinateSystemSet = true;
    this.isCartesianCoordinateSystemSet = false;
    if (!isFinite(latRow0)
      || !isFinite(lonCol0)
      || !isFinite(latRowLast)
      || !isFinite(lonColLast)) {
      throw new IllegalArgumentException("Invalid floating-point value");
    }

    double gxDelta = Angle.to360(lonColLast - lonCol0);
    if (gxDelta == 0) {
      gxDelta = 360;
    }
    double gx0 = Angle.to180(lonCol0);
    double gx1 = gx0 + gxDelta;
    x0 = gx0;
    y0 = latRow0;
    x1 = gx1;
    y1 = latRowLast;
    cellSizeX = (x1 - x0) / (nColsInRaster - 1);
    cellSizeY = (y1 - y0) / (nRowsInRaster - 1);
    checkGeographicCoverage();

  }

  /**
   * Checks to see if geoWrapsLongitude should be set.
   */
  private void checkGeographicCoverage() {
    double gxDelta = x1 - x0;
    if (gxDelta == 360) {
      this.geoWrapsLongitude = false;
      this.geoBracketsLongitude = true;
    } else {
      // see if one grid cell beyond x1 matches x0
      // (within numerical precision).
      geoBracketsLongitude = false;
      double a360 = Math.abs(Angle.to180(x1 + cellSizeX - x0));
      geoWrapsLongitude = a360 < 1.0e-6;
    }
  }

  /**
   * Set a Cartesian coordinate system to be used for interpreting the data.
   * Note that this setting is mutually exclusive with the geographic
   * coordinate
   * system setting. The last setting applied replaces any earlier settings
   *
   * @param x0 the X coordinate of the lower-left corner of the raster and the
   * first column of the raster (column 0).
   * @param y0 the Y coordinate of the lower-left corner of the raster and the
   * first row of the raster (row 0).
   * @param x1 the X coordinate of the upper-right corner of the raster and
   * the
   * last column of the raster.
   * @param y1 the Y coordinate of the upper-right corner of the raster and
   * the
   * last row of the raster.
   */
  public void setCartesianCoordinates(double x0, double y0, double x1, double y1) {
    isGeographicCoordinateSystemSet = false;
    isCartesianCoordinateSystemSet = true;
    geoWrapsLongitude = false;
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

    this.x0 = x0;
    this.y0 = y0;
    this.x1 = x1;
    this.y1 = y1;
    cellSizeX = (x1 - x0) / (nColsInRaster - 1);
    cellSizeY = (y1 - y0) / (nRowsInRaster - 1);
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

    byte[] b = identification.getBytes(StandardCharsets.UTF_8);
    if (b.length > IDENTIFICATION_SIZE) {
      throw new IllegalArgumentException(
        "Identification string exceeds "+IDENTIFICATION_SIZE+" byte limit "
        + "when encoded in UTF-8 character set (size="+ b.length + ")");
    }

  }
 
  /**
   * Gets array of bytes of length 64, intended for writing a GVRS Raster file.
   *
   * @return a valid array of length 64, potentially all zeros if empty.
   */
  byte[] getIdentificationBytes() {
    return getUtfBytes(identification, IDENTIFICATION_SIZE);
  }

  private byte[] getUtfBytes(String subject, int fixedLength) {
    byte[] output = new byte[fixedLength];
    if (subject != null && !subject.isEmpty()) {
      byte[] b = subject.getBytes(StandardCharsets.UTF_8);
      System.arraycopy(b, 0, output, 0, b.length);
    }
    return output;
  }

  /**
   * Gets the identification string associated with this specification and the
   * GvrsFile that is created from it. The identification is supplied by the
   * application that creates a GVRS file and is not required to be populated.
   *
   * @return a string of up to 64 characters, potentially null.
   */
  public String getIdentification() {
    return identification;
  }

  /**
   * Construct a specification based on content read from the specified file
   *
   * @param braf a valid instance, positioned to start of the specification
   * data.
   * @throws IOException in the event of an unrecoverable I/O error
   */
  @SuppressWarnings("PMD.UnusedLocalVariables")
  GvrsFileSpecification(BufferedRandomAccessFile braf) throws IOException {
    initDefaultCodecList();

    timeCreated = System.currentTimeMillis();

      // TO DO: proper treatment of identification is still unresolved.
    //    identification = braf.readUTF();

    nRowsInRaster = braf.leReadInt();
    nColsInRaster = braf.leReadInt();
    nRowsInTile = braf.leReadInt();
    nColsInTile = braf.leReadInt();
    nCellsInTile = nRowsInTile * nColsInTile;

    nRowsOfTiles = (nRowsInRaster + nRowsInTile - 1) / nRowsInTile;
    nColsOfTiles = (nColsInRaster + nColsInTile - 1) / nColsInTile;

    isExtendedFileSizeEnabled = braf.readBoolean();
    isChecksumEnabled = braf.readBoolean();
    int geometryCode = braf.readUnsignedByte();
    geometryType = GvrsGeometryType.valueOf(geometryCode);
 
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
    cellSizeX = (x1 - x0) / (nColsInRaster - 1);
    cellSizeY = (y1 - y0) / (nRowsInRaster - 1);
    if (isGeographicCoordinateSystemSet) {
      checkGeographicCoverage();
    }

    // The source file may supply keys for compression encoder types.
    // Some keys are part of the GVRS specification, but others may
    // have been created by other applications.  If these applications
    // were written in Java, there may be a reference to a metadata object named
    // GvrsJavaCodecs which may have given
    // classpath strings that can be used for loading codecs.  If the
    // data was created by a non-Java application, the GvrsJavaCodecs
    // element will probably not be supplied.
    int nCompressionSpecifications = braf.leReadInt();
    if (nCompressionSpecifications > 0) {
      this.dataCompressionEnabled = true;
      for (int i = 0; i < nCompressionSpecifications; i++) {
        String codecID = braf.readUTF();
        codecIdentificationList.add(codecID);
      }
    }

    int nElements = braf.leReadInt();
    for (int iElement = 0; iElement < nElements; iElement++) {
      int dataTypeCode = braf.readByte();
      boolean hasDescription = braf.readBoolean();
      boolean hasUnitOfMeasure = braf.readBoolean();
      braf.skipBytes(1); // reserved for future use
      
      if (dataTypeCode < 0 || dataTypeCode > 3) {
        throw new IOException(
          "Unsupported value for data-type code: " + dataTypeCode);
      }
      GvrsElementType dataType = GvrsElementType.valueOf(dataTypeCode);

      GvrsElementSpecification spec;
      String name = braf.readUTF();
      switch (dataType) {
        case SHORT:
          short sMinValue = braf.leReadShort();
          short sMaxValue = braf.leReadShort();
          short sFillValue = braf.leReadShort();
          GvrsElementSpecificationShort sSpec
            = new GvrsElementSpecificationShort(name, sMinValue, sMaxValue, sFillValue);
          elementSpecifications.add(sSpec);
          spec = sSpec;
          break;
        case FLOAT: {
          float fMinValue = braf.leReadFloat();
          float fMaxValue = braf.leReadFloat();
          float fFillValue = braf.leReadFloat();
          GvrsElementSpecificationFloat fSpec
            = new GvrsElementSpecificationFloat(name, fMinValue, fMaxValue, fFillValue);
          elementSpecifications.add(fSpec);
            spec = fSpec;
        }
        break;
        case INTEGER_CODED_FLOAT: {
          float fMinValue = braf.leReadFloat();
          float fMaxValue = braf.leReadFloat();
          float fFillValue = braf.leReadFloat();
          float scale = braf.leReadFloat();
          float offset = braf.leReadFloat();
          int iMinValue = braf.leReadInt();  // NO PMD diagnostic
          int iMaxValue = braf.leReadInt(); // NO PMD diagnostic
          int iFillValue = braf.leReadInt(); // NO PMD diagnostic    
          GvrsElementSpecificationIntCodedFloat icfSpec
            = new GvrsElementSpecificationIntCodedFloat(name, fMinValue, fMaxValue, fFillValue, scale, offset);
          elementSpecifications.add(icfSpec);
          spec = icfSpec;
        }
        break;

        case INTEGER:
        default:
          int iMinValue = braf.leReadInt();  // NO PMD diagnostic
          int iMaxValue = braf.leReadInt(); // NO PMD diagnostic
          int iFillValue = braf.leReadInt(); // NO PMD diagnostic
          GvrsElementSpecificationInt iSpec
            = new GvrsElementSpecificationInt(name, iMinValue, iMaxValue, iFillValue);
          elementSpecifications.add(iSpec);
          spec = iSpec;
          break;
      }
      if(hasDescription){
        spec.setDescription(braf.readUTF());
      }
      if(hasUnitOfMeasure){
        spec.setUnitOfMeasure(braf.readUTF());
      }
    }

    if (elementSpecifications.isEmpty()) {
      throw new IOException("Empty specification for variable definitions");
    }
 
  }

 String readUTF(BufferedRandomAccessFile braf) throws IOException {
    int length = braf.readUnsignedShort();
    if (length == 0) {
      return "";
    }
    int k = length + (length & 1);
    byte[] b = new byte[k];
    braf.readFully(b, 0, k);
    return new String(b, 0, length, StandardCharsets.UTF_8);
  }

  /**
   * Writes the part of the GvrsFile header that includes the parameters
   * from this specification.
   *
   * @param braf a valid instance for output
   * @throws IOException in the event of an unhandled I/O error.
   */
  void write(BufferedRandomAccessFile braf) throws IOException {

    braf.leWriteInt(nRowsInRaster);
    braf.leWriteInt(nColsInRaster);
    braf.leWriteInt(nRowsInTile);
    braf.leWriteInt(nColsInTile);

    braf.writeBoolean(isExtendedFileSizeEnabled);
     braf.writeBoolean(isChecksumEnabled);
    braf.writeByte(geometryType.getCodeValue());

    int coordinateSystem = 0;
    if (isCartesianCoordinateSystemSet) {
      coordinateSystem = 1;
    } else if (isGeographicCoordinateSystemSet) {
      coordinateSystem = 2;
    }
    braf.writeByte(coordinateSystem);

    braf.leWriteDouble(x0);
    braf.leWriteDouble(y0);
    braf.leWriteDouble(x1);
    braf.leWriteDouble(y1);

    if (isDataCompressionEnabled()) {
      List<CodecHolder> sList = getCompressionCodecs();
      braf.leWriteInt(sList.size());
      for (CodecHolder srcs : sList) {
        braf.writeUTF(srcs.getIdentification());
      }
    } else {
      braf.leWriteInt(0);
    }

    braf.leWriteInt(elementSpecifications.size());
    for (GvrsElementSpecification e : elementSpecifications) {
      GvrsElementType dataType = e.dataType;
      int codeValue = (byte) dataType.getCodeValue();
      braf.writeByte(codeValue);
      braf.writeBoolean(e.description!=null); // has description
      braf.writeBoolean(e.unitOfMeasure!=null); // has unit of measure
      braf.writeByte(0);  // reserved
      braf.writeUTF(e.name);
      switch (dataType) {
        case SHORT:
          GvrsElementSpecificationShort sSpec = (GvrsElementSpecificationShort) e;
          braf.leWriteShort(sSpec.minValue);
          braf.leWriteShort(sSpec.maxValue);
          braf.leWriteShort(sSpec.fillValue);
          break;
        case FLOAT:
          GvrsElementSpecificationFloat fSpec = (GvrsElementSpecificationFloat) e;
          braf.leWriteFloat(fSpec.minValue);
          braf.leWriteFloat(fSpec.maxValue);
          braf.leWriteFloat(fSpec.fillValue);
          break;
        case INTEGER_CODED_FLOAT:
          GvrsElementSpecificationIntCodedFloat icfSpec = (GvrsElementSpecificationIntCodedFloat) e;
          braf.leWriteFloat(icfSpec.minValue);
          braf.leWriteFloat(icfSpec.maxValue);
          braf.leWriteFloat(icfSpec.fillValue);
          braf.leWriteFloat(icfSpec.scale);
          braf.leWriteFloat(icfSpec.offset);
          braf.leWriteFloat(icfSpec.minValueI);
          braf.leWriteFloat(icfSpec.maxValueI);
          braf.leWriteFloat(icfSpec.fillValueI);
          break;
        case INTEGER:
        default:
          GvrsElementSpecificationInt iSpec = (GvrsElementSpecificationInt) e;
          braf.leWriteInt(iSpec.minValue);
          braf.leWriteInt(iSpec.maxValue);
          braf.leWriteInt(iSpec.fillValue);
          break;
      }
      if(e.description!=null){
        braf.writeUTF(e.description);
      }
      if(e.unitOfMeasure!=null){
        braf.writeUTF(e.unitOfMeasure);
      }
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
  public int getRowsInGrid() {
    return nRowsInRaster;
  }

  /**
   * Gets the number of columns in the overall raster grid.
   *
   * @return a value of 1 or greater.
   */
  public int getColumnsInGrid() {
    return nColsInRaster;
  }

  /**
   * Get the number of rows of tiles. This value is computed as the number of
   * rows in the grid divided by the number of rows in a tile, rounded up.
   *
   * @return a value of 1 or greater.
   */
  public int getRowsOfTilesInGrid() {
    return nRowsOfTiles;
  }

  /**
   * Get the number of columns of tiles. This value is computed as the number
   * of
   * columns in the grid divided by the number of columns in a tile, rounded
   * up.
   *
   * @return a value of 1 or greater.
   */
  public int getColumnsOfTilesInGrid() {
    return nColsOfTiles;
  }

  /**
   * Gets the number of rows in a tile.
   *
   * @return a value of 1 or greater.
   */
  public int getRowsInTile() {
    return nRowsInTile;
  }

  /**
   * Gets the number of columns in a tile.
   *
   * @return a value of 1 or greater
   */
  public int getColumnsInTile() {
    return nColsInTile;
  }

  /**
   * Gets the number of data values stored for each grid cell.
   * One way to view this configuration is to treat each grid cell
   * as a tuple with the specified number of elements.
   *
   * @return a value of 0 or greater.
   */
  public int getNumberOfElements() {
    return this.elementSpecifications.size();
  }

  /**
   * Indicates whether the extended file size option is enabled. If the
   * extended
   * file size option is not enabled, the maximum size of a file is 32
   * gigabytes. Larger files may be specified using this option, but doing so
   * will increase the amount of internal memory used to process the files.
   *
   * @return true if extended file sizes are enabled; otherwise false.
   */
  public boolean isExtendedFileSizeEnabled() {
    return isExtendedFileSizeEnabled;
  }

  /**
   * Indicates whether the extended file size option is enabled. If the
   * extended
   * file size option is not enabled, the maximum size of a file is 32
   * gigabytes. Larger files may be specified using this option, but doing so
   * will increase the amount of internal memory used to process the files.
   * <p>
   * <strong>Warning:</strong>At this time the extended file size option is
   * not
   * implemented by the GvrsFile class.
   *
   * @param extendedFileSizeEnabled true if extended file sizes are enabled;
   * otherwise false.
   */
  public void setExtendedFileSizeEnabled(boolean extendedFileSizeEnabled) {
    this.isExtendedFileSizeEnabled = extendedFileSizeEnabled;
  }

  /** 
   * Indicates that the computation of checksums is enabled
   * @return true if checksums are enabled; otherwise, false.
   */
  public boolean isChecksumEnabled() {
    return isChecksumEnabled;
  }

  /**
   * Enables or disables the optional checksum computation feature.
   * @param checksumEnabled true if checksums are to be computed;
   * otherwise, false
   */
  public void setChecksumEnabled(boolean checksumEnabled) {
    this.isChecksumEnabled = checksumEnabled;
  }
  
  
  /**
   * Map Cartesian coordinates to grid coordinates storing the row and column
   * in
   * an array in that order. If the x or y coordinate is outside the ranges
   * defined for these parameters, the resulting rows and columns may be
   * outside
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
   * Map grid coordinates to Cartesian coordinates storing the resulting x and
   * y
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
    c[1] = (y1 - y0) * row / (nRowsInRaster - 1) + y0;
    return c;
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
   * Map grid coordinates to Geographic coordinates storing the resulting x
   * and
   * y values in an array in that order. If the row or column values are
   * outside
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
    c[0] = (y1 - y0) * row / (nRowsInRaster - 1) + y0;
    c[1] = (x1 - x0) * column / (nColsInRaster - 1) + x0;
    return c;
  }

  /**
   * Indicates whether a geographic coordinate system has been set for mapping
   * input coordinates to raster coordinates and vice versa.
   *
   * @return true if a geographic coordinate system was specified; otherwise
   * false.
   */
  public boolean isGeographicCoordinateSystemSpecified() {
    return isGeographicCoordinateSystemSet;
  }

  /**
   * Indicates whether a geographic coordinate system covers the full range of
   * longitude but crosses a 360 degree boundary between grid points. For
   * example, a grid with a geographic coordinate system ranging from -180 to
   * +180 by increments of 1 would include 361 columns. A grid with a
   * geographic
   * coordinate system ranging from -179.5 to +179.5 by increments of 1 would
   * include 360 columns. The first case does not cross a longitude boundary.
   * The second case crosses a longitude boundary in the middle of a grid
   * cell.
   * <p>
   * In the first case, we say that the coordinate system "brackets" the
   * longitude range. The first and last grid point in each row are actually
   * at
   * the same coordinates (and should have the same values if properly
   * populated). In the second case, we say that the coordinate system
   * "crosses"
   * the longitude boundary. But, in both cases, the coordinate system does
   * offer a full 360-degrees of coverage.
   * <p>
   * This setting is useful for data queries and interpolation operations
   * applied over a geographic coordinate system that covers the entire range
   * of
   * longitudes. In the "bracket" case, mapping a longitude to a column can be
   * accomplished using simple arithmetic. But in the second case, more
   * complicated logic may be required to select columns for interpolation.
   *
   * @return true if the coordinate system covers the full range of longitude;
   * otherwise, false.
   */
  public boolean doGeographicCoordinatesWrapLongitude() {
    return geoWrapsLongitude;
  }

  /**
   * Indicates whether a geographic coordinate system covers the full range of
   * longitude with redundant grid points at the beginning and end of each
   * row.
   * See the explanation for doGeographicCoordinatesWrapLongitude() for more
   * detail.
   *
   *
   * @return true if the coordinate system covers the full range of longitude
   * with redundant grid points and the beginning and end of each row;
   * otherwise, false.
   */
  public boolean doGeographicCoordinatesBracketLongitude() {
    return geoBracketsLongitude;
  }

  /**
   * Indicates whether a Cartesian coordinate system has been set for mapping
   * input coordinates to raster (grid) coordinates and vice versa.
   *
   * @return true if a geographic coordinate system was specified; otherwise
   * false.
   */
  public boolean isCartesianCoordinateSystemSpecified() {
    return isCartesianCoordinateSystemSet;
  }

  /**
   * Sets the geometry type associated with the row and column positions in
   * the
   * raster. If the geometry type is Point, the value at a row and column is
   * intended to represent the value at a particular point on the surface
   * described by the raster. If the geometry type is Area, the value at a row
   * and column is intended to represent the value for an entire cell centered
   * at the coordinates associated with the row and column. In either case,
   * Gvrs
   * treats real-valued coordinates (Cartesian or geographic) as giving the
   * position at which the data value for a grid point exactly matches the
   * value
   * at the surface that is represented by the data.
   *
   * @param geometryType a valid instance of the enumeration.
   */
  public void setGeometryType(GvrsGeometryType geometryType) {
    this.geometryType = geometryType;
  }

  /**
   * Gets the geometry type for this specification.
   *
   * @return a valid instance of the enumeration.
   */
  public GvrsGeometryType getGeometryType() {
    return geometryType;
  }

  /**
   * Gets the sizes for the cells based on the coordinate system set in the
   * specification. These sizes are the distances measured along the x and y
   * axes in the coordinate system specified for this instance.
   *
   * @return a valid array of dimension 2 giving, respectively, the x and y
   * cell
   * sizes.
   */
  public double[] getCellSizes() {
    double[] s = new double[2];
    s[0] = cellSizeX;
    s[1] = cellSizeY;
    return s;
  }

  /**
   * Gets the number of cells (grid points) in the tile definition.
   *
   * @return a value greater than or equal to 1.
   */
  public int getNumberOfCellsInTile() {
    return nCellsInTile;
  }

  /**
   * Gets the number of cells (grid points) in the raster definition.
   *
   * @return a value greater than or equal to 1.
   */
  public long getNumberOfCellsInGrid() {
    return (long) nRowsInRaster * (long) nColsInRaster;
  }

  /**
   * Removes all compression coder-decoder (codec) definitions from the
   * specification.
   */
  public void removeAllCompressionCodecs() {
    this.codecList.clear();
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
    if (codecID == null || codecID.isEmpty()) {
      throw new NullPointerException("Missing name or codec class");
    }
    for (int i = 0; i < codecList.size(); i++) {
      if (codecID.equals(codecList.get(i).getIdentification())) {
        codecList.remove(i);
        return true;
      }
    }

    return false;
  }

  /**
   * Adds a data compression Codec to the specification. The specification
   * allows a maximum of 256 codecs.
   * Any existing codecs with the same codecID will be replaced.
   * <p>
   * The codec class must implement both the interfaces IGvrsEncoder
   * and IGvrsDecoder. It must also include an coder/decoder (e.g. a "codec")
   * that has a no-argument constructor that can be successfully invoked
   * by the GVRS classes..
   *
   * @param codecID a unique identification following the syntax of Java
   * identifier strings, 16 character maximum.
   * @param codec a valid class reference.
   */
  public final void addCompressionCodec(String codecID, Class<?> codec) {
    if (codecList.size() >= 255) {
      throw new IllegalArgumentException(
        "Maximum number of compression codecs (255) exceeded");
    }
    if (codecID == null || codecID.isEmpty() || codec == null) {
      throw new NullPointerException("Missing name or codec class");
    }

    if (codec.getCanonicalName() == null) {
      throw new IllegalArgumentException(
        "Input class must have a canonical name for " + codecID);
    }

    removeCompressionCodec(codecID);

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

    boolean encoderInterfaceConfirmed = false;
    boolean decoderInterfaceConfirmed = false;
    Class<?>[] interfaces = codec.getInterfaces();
    for (Class<?> c : interfaces) {
      if (c.equals(ICompressionEncoder.class)) {
        encoderInterfaceConfirmed = true;
      }
      if (c.equals(ICompressionDecoder.class)) {
        decoderInterfaceConfirmed = true;
      }
    }

    if (!encoderInterfaceConfirmed) {
      throw new IllegalArgumentException(
        "Codec " + codecID + "does not implement encoder interface");
    }

    if (!decoderInterfaceConfirmed) {
      throw new IllegalArgumentException(
        "Codec " + codecID + "does not implement decoder interface");
    }

    CodecHolder spec
      = new CodecHolder(codecID, codec, codec);
    codecList.add(spec);
  }

  /**
   * Adds a data compression coder and decoder (a "codec") to the
   * specification. The specification allows a maximum of 256 codecs.
   * Any existing codecs with the same codecID will be replaced.
   * <p>
   * The encoder class must implement the interfaces IGvrsEncoder
   * interface. The decoder class must implement the IGvrsDecoder
   * interface. Both classes must also include a no-argument constructor
   * that can be successfully invoked by the GVRS classes.
   * <p>
   * The rationale for specifying separate classes for the encoder and
   * decoder is that in some cases, the encoder may include substantially
   * more complexity (and library dependencies) than the decoder.
   * In such cases, it may be desirable to have some applications that
   * can access compressed GVRS files on a read-only basis. Such applications
   * may be able to operate correctly even though they do not include
   * all the libraries required for the encoding.
   *
   * @param codecID a unique identification following the syntax of Java
   * identifier strings, 16 character maximum.
   * @param encoder a valid reference to a class that implements IGvrsEncoder.
   * @param decoder a valid reference to a class that implements IGvrsDecoder.
   */
  public final void addCompressionCodec(
    String codecID, Class<?> encoder, Class<?> decoder) {
    if (codecList.size() >= 255) {
      throw new IllegalArgumentException(
        "Maximum number of compression codecs (255) exceeded");
    }
    if (codecID == null || codecID.isEmpty() || encoder == null || decoder == null) {
      throw new NullPointerException(
        "Missing name, encoder, or decoder class");
    }

    if (encoder.getCanonicalName() == null) {
      throw new IllegalArgumentException(
        "Encoder class must have a canonical name for " + codecID);
    }

    if (decoder.getCanonicalName() == null) {
      throw new IllegalArgumentException(
        "Decoder class must have a canonical name for " + codecID);
    }
    removeCompressionCodec(codecID);

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

    boolean encoderInterfaceConfirmed = false;
    boolean decoderInterfaceConfirmed = false;
    Class<?>[] interfaces = encoder.getInterfaces();
    for (Class<?> c : interfaces) {
      if (c.equals(ICompressionEncoder.class)) {
        encoderInterfaceConfirmed = true;
      }
      if (c.equals(ICompressionDecoder.class)) {
        decoderInterfaceConfirmed = true;
      }
    }

    if (!encoderInterfaceConfirmed) {
      throw new IllegalArgumentException(
        "Codec " + codecID + "does not implement encoder interface");
    }

    interfaces = decoder.getInterfaces();
    for (Class<?> c : interfaces) {
      if (c.equals(ICompressionDecoder.class)) {
        decoderInterfaceConfirmed = true;
      }
    }

    if (!decoderInterfaceConfirmed) {
      throw new IllegalArgumentException(
        "Decoder for " + codecID + "does not implement decoder interface");
    }

    CodecHolder spec
      = new CodecHolder(codecID, encoder, decoder);
    codecList.add(spec);
  }

  /**
   * Get the compression codes currently registered with the specification
   *
   * @return a valid list of specifications, potentially empty.
   */
  public List<CodecHolder> getCompressionCodecs() {
    List<CodecHolder> list = new ArrayList<>();
    list.addAll(codecList);
    return list;
  }

  /**
   * Prints a summary of the specification to the indicated output stream.
   *
   * @param ps any valid PrintStream including System&#46;out and
   * System&#46;err.
   */
  public void summarize(PrintStream ps) {
    ps.format("Identification:    %s%n",
      identification == null || identification.isEmpty()
      ? "Not Specified" : identification);

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
//    ps.format("Value scale factor:  %11.6f%n", valueScale);
//    ps.format("Value offset factor: %11.6f%n", valueOffset);
    ps.format("Data compression:       %s%n",
      isDataCompressionEnabled() ? "enabled" : "disabled");
  }

  /**
   * Gets the bounds for the coordinate system associated with the grid.
   *
   * @return a valid instance of Rectangle2D giving the bounds
   */
  public Rectangle2D getBounds() {
    return new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
  }

  /**
   * Adds an element specification to the file specification.
   * Each element added to a GVRS file must have a unique name.
   *
   * @param specification a valid instance
   */
  public void addElementSpecification(GvrsElementSpecification specification) {
    if (specification == null) {
      throw new IllegalArgumentException("Null specification not supported");
    }
    for (GvrsElementSpecification e : elementSpecifications) {
      if (e.name.equals(specification.name)) {
        throw new IllegalArgumentException("An element specification with name "
          + e.name + " already exists");
      }
    }
    elementSpecifications.add(specification);
  }
   
}
