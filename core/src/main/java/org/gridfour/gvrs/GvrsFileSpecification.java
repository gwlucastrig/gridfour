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
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import org.gridfour.compress.CodecDeflate;
import org.gridfour.compress.CodecHuffman;
import org.gridfour.compress.CodecFloat;
import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.PrintStream;
import static java.lang.Double.isFinite;
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
  static final byte VERSION = 1;
  /**
   * The sub-version identifier to be used by all raster-file and related
   * implementations in this package.
   */
  static final byte SUB_VERSION = 1;

  /**
   * Major version for this instance (set by constructor or when read from a file)
   */
  final int version;

  /**
   * Minor version for this instance (set by constructor or when read from file).
   */
  final int subversion;

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

  // Parameters for the model-to-raster transform
  double m2r00 = 1;
  double m2r01 = 0;
  double m2r02 = 0;
  double m2r10 = 0;
  double m2r11 = 1;
  double m2r12 = 0;

  // Parameters for the raster-to-model transform
  double r2m00 = 1;
  double r2m01 = 0;
  double r2m02 = 0;
  double r2m10 = 0;
  double r2m11 = 1;
  double r2m12 = 0;

  AffineTransform modelToRaster = new AffineTransform(); // identity
  AffineTransform rasterToModel = new AffineTransform(); // identify


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
  String productLabel;


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

    version = VERSION;
    subversion = SUB_VERSION;

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
      nRowsInTile = this.nRowsInTile;
      nColumnsInTile = this.nColsInTile;
    } else {
      this.nRowsInTile = nRowsInTile;
      this.nColsInTile = nColumnsInTile;
    }

    if (nRowsInRaster <= 0 || nColumnsInRaster <= 0) {
      throw new IllegalArgumentException(
        "Invalid dimensions for raster "
        + "(" + nRowsInRaster + "," + nColumnsInRaster + ")");
    }
    if (this.nRowsInTile <= 0 || this.nColsInTile <= 0) {
      throw new IllegalArgumentException(
        "Invalid tile dimensions for raster "
        + "(" + nRowsInTile + "," + nColumnsInTile + ")");
    }

    if (nRowsInTile > nRowsInRaster || nColsInTile > nColumnsInRaster) {
      throw new IllegalArgumentException(
        "Dimensions of tile "
        + "(" + nRowsInTile + "," + nColsInTile + ")"
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
    version = VERSION;
    subversion = SUB_VERSION;

    timeCreated = s.timeCreated;
    nRowsInRaster = s.nRowsInRaster;
    nColsInRaster = s.nColsInRaster;
    nRowsInTile = s.nRowsInTile;
    nColsInTile = s.nColsInTile;
    nRowsOfTiles = s.nRowsOfTiles;
    nColsOfTiles = s.nColsOfTiles;
    nCellsInTile = s.nCellsInTile;

    productLabel = s.productLabel;
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

    m2r00 = s.m2r00;
    m2r01 = s.m2r01;
    m2r02 = s.m2r02;
    m2r10 = s.m2r10;
    m2r11 = s.m2r11;
    m2r12 = s.m2r12;

    r2m00 = s.r2m00;
    r2m01 = s.r2m01;
    r2m02 = s.r2m02;
    r2m10 = s.r2m10;
    r2m11 = s.r2m11;
    r2m12 = s.r2m12;

    modelToRaster = s.modelToRaster;
    rasterToModel = s.rasterToModel;

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
   * raster cell.  Logic is applied to ensure this value is a multiple of 4.
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
   * coordinate system setting. The last setting applied replaces any
   * earlier settings.
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
   * <p>
   * This method also populates the internal AffineTransform that can be
   * used for transforming coordinates between the geographic and grid
   * coordinate systems.
   *
   * @param latRow0 the latitude of the center point in the cell
   * in the first row and first column in the raster
   * @param lonCol0 the longitude of the center point in the cell
   * in the first row and first column in the raster
   * @param latRowLast the latitude of the center point in the cell
   * in the last row and last column in the raster
   * @param lonColLast the longitude of the center point in the cell
   * in the last row and last column in the raster
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
    checkLatitudeRange();

    m2r00 = 1 / cellSizeX;
    m2r01 = 0;
    m2r02 = -x0 * m2r00;
    m2r10 = 0;
    m2r11 = 1 / cellSizeY;
    m2r12 = -y0 * m2r11;
    modelToRaster = new AffineTransform(m2r00, m2r10, m2r01, m2r11, m2r02, m2r12);
    try {
      rasterToModel = modelToRaster.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }

    double[] m = new double[6];
    rasterToModel.getMatrix(m);
    r2m00 = m[0];
    r2m10 = m[1];
    r2m01 = m[2];
    r2m11 = m[3];
    r2m02 = m[4];
    r2m12 = m[5];
  }


  /**
   * Set a geographic coordinate system to be used for interpreting the data.
   * Note that this setting is mutually exclusive with the Cartesian
   * coordinate system setting. The last setting applied replaces any
   * earlier settings.
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
   * <p>
   * This method also populates the internal AffineTransform that can be
   * used for transforming coordinates between the geographic and grid
   * coordinate systems.
   *
   * @param latRow0 the latitude of the center point in the cell
   * in the first row and first column in the raster
   * @param lonCol0 the longitude of the center point in the cell
   * in the first row and first column in the raster
   * @param latCellSpace the angular distance between rows in the raster
   * (e.g. the delta latitude) measured from cell center to cell center
   * @param lonCellSpace the angular distance between columns in the raster
   * (e.g. the delta longitude) measured from cell center to cell center
   */
  public void setGeographicSystem(
    double latRow0, double lonCol0, double latCellSpace, double lonCellSpace) {
    this.isGeographicCoordinateSystemSet = true;
    this.isCartesianCoordinateSystemSet = false;
    if (!isFinite(latRow0)
      || !isFinite(lonCol0)
      || !isFinite(latCellSpace)
      || !isFinite(lonCellSpace)) {
      throw new IllegalArgumentException("Invalid floating-point value");
    }

    double gxDelta = lonCellSpace*(nColsInRaster-1);
    if (gxDelta == 0) {
      gxDelta = 360;
    }
    double gx0 = Angle.to180(lonCol0);
    double gx1 = gx0 + gxDelta;

    x0 = gx0;
    y0 = latRow0;
    x1 = gx1;
    y1 = y0 + latCellSpace*(nRowsInRaster-1);
    cellSizeX = lonCellSpace;
    cellSizeY = latCellSpace;

    checkGeographicCoverage();
    checkLatitudeRange();

    m2r00 = 1 / cellSizeX;
    m2r01 = 0;
    m2r02 = -x0 * m2r00;
    m2r10 = 0;
    m2r11 = 1 / cellSizeY;
    m2r12 = -y0 * m2r11;
    modelToRaster = new AffineTransform(m2r00, m2r10, m2r01, m2r11, m2r02, m2r12);
    try {
      rasterToModel = modelToRaster.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }

    double[] m = new double[6];
    rasterToModel.getMatrix(m);
    r2m00 = m[0];
    r2m10 = m[1];
    r2m01 = m[2];
    r2m11 = m[3];
    r2m02 = m[4];
    r2m12 = m[5];
  }



  private void checkLatitudeRange(){
    if(Math.abs(y0)>90 || Math.abs(y1)>90){
       throw new IllegalArgumentException("Latitudes must not exceed +/- 90 degrees");
    }
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
   * Sets the real-valued model to a Cartesian coordinate system.
   * Note that this setting is mutually exclusive with the geographic
   * coordinate system setting. The last setting applied replaces
   * any earlier settings.
   * <p>
   * This method populates the internal AffineTransform that can be
   * used for transforming coordinates between the model and grid
   * coordinate systems.
   * <p>
   * The key assumption of this method is that the points (x0, y0) and
   * (x1, y1) represent the real-valued coordinate at the <i>center</i> of
   * their associated raster cells.  Although (x0, y0) represents the
   * first row and column in the raster and (x1, y1) represents the last,
   * there is no requirement that x0 &lt; x1 or y0 &lt; y1. However,
   * the coordinates for x0,x1 and y0,y1 must not be equal. That restriction
   * means that this specification cannot be used for a raster containing
   * only one row or column of cells.
   *
   * @param x0 the X coordinate of the center point in the cell
   * in the first row and first column of the raster.
   * @param y0 the Y coordinate of the center point in the cell
   * in the first row and first column of the raster.
   * @param x1 the X coordinate of the center point in the cell
   * in the last row and last column of the raster.
   * @param y1 the Y coordinate of the center point in the cell
   * in the last row and last column of the raster.
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

    // It would be easy enough to compute the r2m matric directly, but
    // testing showed that when we used the Java createInverse() method
    // and multiplied the two matrices togther, the values from createInverse
    // actually produced a result closer to the identify matrix.

    m2r00 = 1 / cellSizeX;
    m2r01 = 0;
    m2r02 = -x0 * m2r00;
    m2r10 = 0;
    m2r11 = 1 / cellSizeY;
    m2r12 = -y0 * m2r11;
    modelToRaster = new AffineTransform(m2r00, m2r10, m2r01, m2r11, m2r02, m2r12);
    try {
      rasterToModel = modelToRaster.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }

    double[] m = new double[6];
    rasterToModel.getMatrix(m);
    r2m00 = m[0];
    r2m10 = m[1];
    r2m01 = m[2];
    r2m11 = m[3];
    r2m02 = m[4];
    r2m12 = m[5];
  }


  /**
   * Sets the real-valued model to a Cartesian coordinate system.
   * Note that this setting is mutually exclusive with the geographic
   * coordinate system setting. The last setting applied replaces
   * any earlier settings.
   * <p>
   * This method populates the internal AffineTransform that can be
   * used for transforming coordinates between the model and grid
   * coordinate systems.
   * <p>
   * The key assumption of this method is that the points (x0, y0) and
   * (x1, y1) represent the real-valued coordinate at the <i>center</i> of
   * their associated raster cells.
   *
   * @param x0 the X coordinate of the center point in the cell
   * in the first row and first column of the raster.
   * @param y0 the Y coordinate of the center point in the cell
   * in the first row and first column of the raster.
   * @param xCellSpace the distance across each raster cell in the direction
   * of the X coordinate axis
   * @param yCellSpace the distance across each raster cell in the direction
   * of the Y coordinate axis
   */
  public void setCartesianSystem(double x0, double y0, double xCellSpace, double yCellSpace) {
    isGeographicCoordinateSystemSet = false;
    isCartesianCoordinateSystemSet = true;
    geoWrapsLongitude = false;
    if (!isFinite(x0) || !isFinite(y0) || !isFinite(xCellSpace) || !isFinite(yCellSpace)) {
      throw new IllegalArgumentException("Invalid floating-point value");
    }
    if (xCellSpace<=0) {
      throw new IllegalArgumentException(
        "X cell spacing must be greater than zero");
    }
    if (yCellSpace<=0) {
      throw new IllegalArgumentException(
        "Y cell spacing must be greater than zero");
    }

    this.x0 = x0;
    this.y0 = y0;
    this.x1 = x0 + xCellSpace * (nColsInRaster - 1);
    this.y1 = y0 + yCellSpace * (nRowsInRaster - 1);
    cellSizeX = xCellSpace;
    cellSizeY = yCellSpace;

    // It would be easy enough to compute the r2m matric directly, but
    // testing showed that when we used the Java createInverse() method
    // and multiplied the two matrices togther, the values from createInverse
    // actually produced a result closer to the identify matrix.

    m2r00 = 1 / cellSizeX;
    m2r01 = 0;
    m2r02 = -x0 * m2r00;
    m2r10 = 0;
    m2r11 = 1 / cellSizeY;
    m2r12 = -y0 * m2r11;
    modelToRaster = new AffineTransform(m2r00, m2r10, m2r01, m2r11, m2r02, m2r12);
    try {
      rasterToModel = modelToRaster.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }

    double[] m = new double[6];
    rasterToModel.getMatrix(m);
    r2m00 = m[0];
    r2m10 = m[1];
    r2m01 = m[2];
    r2m11 = m[3];
    r2m02 = m[4];
    r2m12 = m[5];
  }


  /**
   * Set an arbitrary label string for the raster file. The label
   * may be a string of characters in UTF-8 encoding. This text is intended
   * for identifying GVRS products in user interfaces or printed material.
   * No explicit syntax is applied to its content.
   *
   * @param label an application-defined string for labeling a GVRS file
   */
  public void setLabel(String label) {
    this.productLabel = label;
  }


  /**
   * Gets the product-label string associated with this specification and the
   * GvrsFile that is created from it. The label is supplied by the
   * application that creates a GVRS file and is not required to be populated.
   *
   * @return an arbitrary string, potentially null.
   */
  public String getLabel() {
    return productLabel;
  }

  /**
   * Construct a specification based on content read from the specified file
   *
   * @param version the major-version value obtained from the source file
   * @param subversion the minor-version value obtained from the source file
   * @param braf a valid instance, positioned to start of the specification
   * data.
   * @throws IOException in the event of an unrecoverable I/O error
   */
  @SuppressWarnings("PMD.UnusedLocalVariables")
  GvrsFileSpecification(BufferedRandomAccessFile braf, int version, int subversion) throws IOException {
    this.version = version;
    this.subversion = subversion;

    initDefaultCodecList();

    timeCreated = System.currentTimeMillis();

    nRowsInRaster = braf.leReadInt();
    nColsInRaster = braf.leReadInt();
    nRowsInTile = braf.leReadInt();
    nColsInTile = braf.leReadInt();
    nCellsInTile = nRowsInTile * nColsInTile;

    // Skip the space reserved for future variations of the tile index
    braf.skipBytes(5*4);

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

    m2r00 = braf.leReadDouble();
    m2r01 = braf.leReadDouble();
    m2r02 = braf.leReadDouble();
    m2r10 = braf.leReadDouble();
    m2r11 = braf.leReadDouble();
    m2r12 = braf.leReadDouble();

    r2m00 = braf.leReadDouble();
    r2m01 = braf.leReadDouble();
    r2m02 = braf.leReadDouble();
    r2m10 = braf.leReadDouble();
    r2m11 = braf.leReadDouble();
    r2m12 = braf.leReadDouble();

    modelToRaster = new AffineTransform(m2r00, m2r10, m2r01, m2r11, m2r02, m2r12);
    rasterToModel = new AffineTransform(r2m00, r2m10, r2m01, r2m11, r2m02, r2m12);

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
        String codecID = braf.leReadUTF();
        codecIdentificationList.add(codecID);
      }
    }

    int nElements = braf.leReadInt();
    for (int iElement = 0; iElement < nElements; iElement++) {
      int dataTypeCode = braf.readByte();
      boolean hasDescription = braf.readBoolean();
      boolean hasUnitOfMeasure = braf.readBoolean();
      boolean hasLabel = braf.readBoolean();
      boolean isContinuous;
      if(version==1 && subversion==0){
         // the legacy version does not have flags
         isContinuous = true;
      }else{
         isContinuous = braf.readBoolean();
         braf.skipBytes(7); // reserved for future use
      }

      if (dataTypeCode < 0 || dataTypeCode > 3) {
        throw new IOException(
          "Unsupported value for data-type code: " + dataTypeCode);
      }
      GvrsElementType dataType = GvrsElementType.valueOf(dataTypeCode);

      GvrsElementSpecification spec;
      String name = braf.leReadUTF();
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
        case INT_CODED_FLOAT: {
          float fMinValue = braf.leReadFloat();
          float fMaxValue = braf.leReadFloat();
          float fFillValue = braf.leReadFloat();
          float scale = braf.leReadFloat();
          float offset = braf.leReadFloat();
          int iMinValue = braf.leReadInt();  // NO PMD diagnostic
          int iMaxValue = braf.leReadInt(); // NO PMD diagnostic
          int iFillValue = braf.leReadInt(); // NO PMD diagnostic
          GvrsElementSpecificationIntCodedFloat icfSpec
            = new GvrsElementSpecificationIntCodedFloat(
              name, scale, offset,
              iMinValue, iMaxValue, iFillValue,
              fMinValue, fMaxValue, fFillValue);
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
        spec.setDescription(braf.leReadUTF());
      }
      if(hasUnitOfMeasure){
        spec.setUnitOfMeasure(braf.leReadUTF());
      }

      if(hasLabel){
        spec.setLabel(braf.leReadUTF());
      }

      spec.setContinuous(isContinuous);
    }

    productLabel = braf.leReadUTF();
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
    braf.leWriteInt(0); // specify type of tile index (for future use)
    braf.leWriteInt(0); // reserved for future use
    braf.leWriteInt(0);
    braf.leWriteInt(0);
    braf.leWriteInt(0);

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

    // write the AffineTransform parameters.  We store the parameters
    // for both the real-value-to-grid (m2r) and the grid-to-real (r2m)
    // matrices even though one could easily be computed from the other.
    // The reason for this is that we want to be able to ensure that
    // the parameters used on whatever system reads this file will be
    // identical to those used on the system that writes it.
    braf.leWriteDouble(m2r00);
    braf.leWriteDouble(m2r01);
    braf.leWriteDouble(m2r02);
    braf.leWriteDouble(m2r10);
    braf.leWriteDouble(m2r11);
    braf.leWriteDouble(m2r12);

    braf.leWriteDouble(r2m00);
    braf.leWriteDouble(r2m01);
    braf.leWriteDouble(r2m02);
    braf.leWriteDouble(r2m10);
    braf.leWriteDouble(r2m11);
    braf.leWriteDouble(r2m12);

    if (isDataCompressionEnabled()) {
      List<CodecHolder> sList = getCompressionCodecs();
      braf.leWriteInt(sList.size());
      for (CodecHolder srcs : sList) {
        braf.leWriteUTF(srcs.getIdentification());
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
      braf.writeBoolean(e.label!=null); // has a label
      braf.writeBoolean(e.continuous);
      byte []zeroes = new byte[7];
      braf.writeFully(zeroes);
      braf.leWriteUTF(e.name);
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
        case INT_CODED_FLOAT:
          GvrsElementSpecificationIntCodedFloat icfSpec = (GvrsElementSpecificationIntCodedFloat) e;
          braf.leWriteFloat(icfSpec.minValue);
          braf.leWriteFloat(icfSpec.maxValue);
          braf.leWriteFloat(icfSpec.fillValue);
          braf.leWriteFloat(icfSpec.scale);
          braf.leWriteFloat(icfSpec.offset);
          braf.leWriteInt(icfSpec.minValueI);
          braf.leWriteInt(icfSpec.maxValueI);
          braf.leWriteInt(icfSpec.fillValueI);
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
        braf.leWriteUTF(e.description);
      }
      if(e.unitOfMeasure!=null){
        braf.leWriteUTF(e.unitOfMeasure);
      }
      if(e.label!=null){
        braf.leWriteUTF(e.label);
      }
    }

    if(productLabel==null){
      braf.leWriteShort(0);
    }else{
      braf.leWriteUTF(productLabel);
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
   * the raster. If the geometry type is Point, the value at a row and column is
   * intended to represent the value at a particular point on the surface
   * described by the raster. If the geometry type is Area, the value at a row
   * and column is intended to represent the value for an entire cell centered
   * at the coordinates associated with the row and column. In either case,
   * GVRS treats real-valued coordinates (Cartesian or geographic) as giving the
   * position at which the data value for a grid point exactly matches the
   * value at the surface that is represented by the data.
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
      productLabel == null || productLabel.isEmpty()
      ? "Not Specified" : productLabel);

    long cellsInRaster = (long) nRowsInRaster * (long) nColsInRaster;

    ps.format("Rows in Raster:    %12d%n", nRowsInRaster);
    ps.format("Columns in Raster: %12d%n", nColsInRaster);
    ps.format("Rows in Tile:      %12d%n", nRowsInTile);
    ps.format("Columns in Tile:   %12d%n", nColsInTile);
    ps.format("Rows of Tiles:     %12d%n", nRowsOfTiles);
    ps.format("Columns of Tiles:  %12d%n", nColsOfTiles);
    ps.format("Tiles in Raster:   %12d%n", nRowsOfTiles*nColsOfTiles);
    ps.format("Cells in Raster:   %12d%n", cellsInRaster);
    ps.format("Cells in Tile:     %12d%n", nCellsInTile);

    ps.println("");
    ps.format("Range x values:      %11.6f, %11.6f, (%f)%n", x0, x1, x1 - x0);
    ps.format("Range y values:      %11.6f, %11.6f, (%f)%n", y0, y1, y1 - y0);
    ps.format("Data compression:       %s%n",
      isDataCompressionEnabled() ? "enabled" : "disabled");
    ps.println("");

    ps.println("Elements");
    int namLen = 4; // for "name"
    int labLen = 5; // for "label"
    int typLen = 4; // for "type"
     for(GvrsElementSpecification eSpec: this.elementSpecifications){
      String dType = eSpec.dataType.name();
      if(dType.length()>typLen){
        typLen = dType.length();
      }
      String name = eSpec.getName();
      if(name.length()>namLen){
        namLen = name.length();
      }
      String label = eSpec.getLabel();
      if(label!=null && label.length()>labLen){
        labLen = label.length();
      }
     }

    String elmfmt = String.format(
      "   %%-%d.%ds   %%-%d.%ds   %%-%d.%ds   %%s%%n",
      namLen, namLen, labLen, labLen, typLen,typLen);
    ps.format(elmfmt, "Name", "Label", "Type", "Description");

    for(GvrsElementSpecification eSpec: this.elementSpecifications){
      String dType = eSpec.dataType.name();
      String name = eSpec.getName();
      String label = eSpec.getLabel();
      if(label==null){
        label = "";
      }
      String description = eSpec.getDescription();
      if(description==null){
        description="";
      }
      ps.format(elmfmt, name, label, dType, description);
      ps.println("");
    }
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


  /**
   * Gets an affine transform for mapping real-valued "model" coordinates
   * to the raster grid. The model coordinates may be based on either the
   * Cartesian or Geographic specifications, or specified arbitrarily by
   * the application.
   * @return a valid instance
   */
  public AffineTransform getTransformModelToRaster(){
    return modelToRaster;
  }

    /**
   * Gets an affine transform for mapping grid (raster) coordinates to
   * real-valued "model" coordinates. The model coordinates may be based
   * on either the Cartesian or Geographic specifications, or
   * specified arbitrarily by  the application.
   * @return a valid instance
   */
  public AffineTransform getTransformRasterToModel(){
    return modelToRaster;
  }


  private void applyTransforms(){
    double [] m =new double[6];
    modelToRaster.getMatrix(m);
    m2r00 = m[0];
    m2r10 = m[1];
    m2r01 = m[2];
    m2r11 = m[3];
    m2r02 = m[4];
    m2r12 = m[5];

    rasterToModel.getMatrix(m);
    r2m00 = m[0];
    r2m10 = m[1];
    r2m01 = m[2];
    r2m11 = m[3];
    r2m02 = m[4];
    r2m12 = m[5];

    double [] c = new double[16];
    c[0] = 0;
    c[1] = 0;
    c[2] = nColsInRaster - 1;
    c[3] = 0;
    c[4] = nColsInRaster-1;
    c[5] = nRowsInRaster-1;
    c[6] = 0;
    c[7] = nRowsInRaster-1;
    rasterToModel.transform(c, 0, c, 8, 4);

    x0 = c[8];
    y0 = c[9];
    x1 = x0;
    y1 = y0;
    for(int i=1; i<4; i++){
      double x = c[8+i*2];
      double y = c[8+i*2+1];
      if(x<x0){
        x0 = x;
      }else  if(x>x1){
        x1 = x;
      }
      if(y<y0){
        y0 = y;
      }else if(y>y1){
        y1 = y;
      }
    }
  }
  /**
   * Set the transform for mapping model coordinates to the raster grid.
   * @param a  a valid, well-conditioned transformation matrix.
   */
  public void setTransformModelToRaster(AffineTransform a){
    if(a==null){
      throw new IllegalArgumentException(
        "Null specification for model-to-raster transform");
    }

    modelToRaster = a;
    try{
      rasterToModel = a.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException("Specified transform is not invertible");
    }

    applyTransforms();

  }


  /**
   * Set the transform for mapping model coordinates to the raster grid.
   * @param a  a valid, well-conditioned transformation matrix.
   */
  public void setTransformRasterToModel(AffineTransform a){
    if(a==null){
      throw new IllegalArgumentException(
        "Null specification for raster-to-model transform");
    }

    rasterToModel = a;
    try{
      modelToRaster = a.createInverse();
    } catch (NoninvertibleTransformException ex) {
      throw new IllegalArgumentException("Specified transform is not invertible");
    }

     applyTransforms();
  }


  /**
   * Gets the minimum X coordinate in the model-coordinate-system
   * (a Cartesian coordinate system, or longitude for a geographic coordinate
   * system).
   * @return a finite floating-point value.
   */
  public double getX0(){
    return x0;
  }


  /**
   * Gets the minimum Y coordinate in the model-coordinate-system
   * (a Cartesian coordinate system, or latitude for a geographic coordinate
   * system).
   * @return a finite floating-point value.
   */
  public double getY0(){
    return y0;
  }


  /**
   * Gets the maximum X coordinate in the model-coordinate-system
   * (a Cartesian coordinate system, or longitude for a geographic coordinate
   * system).
   * @return a finite floating-point value.
   */
  public double getX1(){
    return x1;
  }


  /**
   * Gets the maximum Y coordinate in the model-coordinate-system
   * (a Cartesian coordinate system, or latitude for a geographic coordinate
   * system).
   * @return a finite floating-point value.
   */
  public double getY1(){
    return y1;
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
    double x = column*r2m00 + row*r2m01 + r2m02;
    double y = column*r2m10 + row*r2m11 + r2m12;
    return new ModelPoint(x,y);
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
    double col = x*m2r00 + y*m2r01 + m2r02; // left-and-right direction
    double row = x*m2r10 + y*m2r11 + m2r12; // up-and-down direction
    return new GridPoint(row, col);
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
    double row = (nRowsInRaster - 1) * (latitude - y0) / (y1 - y0);  // row
    double delta = Angle.to360(longitude - x0);
    double column = (nColsInRaster - 1) * delta / (x1 - x0);
    GridPoint point = new GridPoint(row, column);
    return point;
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
    double lat = (y1 - y0) * row / (nRowsInRaster - 1) + y0;
    double lon = (x1 - x0) * column / (nColsInRaster - 1) + x0;
    return new GeoPoint(lat, lon);
  }




   /**
   * Map Cartesian coordinates to grid coordinates storing the row and column
   * in an array in that order.
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
    double[] grid = new double[2];
    grid[1] = x*m2r00 + y*m2r01 + m2r02;
    grid[0] = x*m2r10 + y*m2r11 + m2r12;
    return grid;
  }

  /**
   * Map grid coordinates to Cartesian coordinates storing the resulting x and
   * y values in an array in that order.
   * <p>
   * This method is deprecated. Please use mapGridToModelPoint() instead.
   * @param row a row (may be a non-integral value)
   * @param column a column (may be a non-integral value)
   * @return a valid array giving the Cartesian x and y coordinates in that
   * order.
   */
  @Deprecated
  public double[] mapGridToCartesian(double row, double column) {
    double[] c = new double[2];
    c[0] = column*r2m00 + row*r2m01 + r2m02;
    c[1] = column*r2m10 + row*r2m11 + r2m12;
    return c;
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
    double[] grid = new double[2];
    grid[0] = (nRowsInRaster - 1) * (latitude - y0) / (y1 - y0);  // row
    double delta = Angle.to360(longitude - x0);
    grid[1] = (nColsInRaster - 1) * delta / (x1 - x0);
    return grid;
  }

  /**
   * Map grid coordinates to Geographic coordinates storing the resulting
   * row and column values in an array in that order.
   * <p>
   * This method is deprecated. Please use mapGridToGeoPoint() instead.
   *
   * @param row the row coordinate (may be non-integral)
   * @param column the column coordinate (may be non-integral)
   * @return a valid array giving latitude and longitude in that order.
   */
  @Deprecated
  public double[] mapGridToGeographic(double row, double column) {
    double[] c = new double[2];
    c[0] = (y1 - y0) * row / (nRowsInRaster - 1) + y0;
    c[1] = (x1 - x0) * column / (nColsInRaster - 1) + x0;
    return c;
  }

  /**
   * Determines whether a specified version is supported by the GVRS library
   * @param version the major-version specification
   * @param subversion the minor-version specification
   * @return true if the version is supported; otherwise false.
   */
  static boolean isVersionSupported(int version, int subversion){
    return version==1 && (subversion==0 || subversion==1);
  }

}
