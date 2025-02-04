/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.
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
 * OUT OF OR IN CONNECTI
 * ---------------------------------------------------------------------
 */
 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 12/2019  G. Lucas     Created
 * 12/2024  G. Lucas     Updated for newer NetCDF API, ETOPO 2022
 *
 * Notes:
 *
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.globalDEM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.gridfour.demo.utils.TestOptions;
import org.gridfour.gvrs.GvrsCacheSize;
import org.gridfour.gvrs.GvrsElement;

import org.gridfour.gvrs.GvrsElementSpecification;
import org.gridfour.gvrs.GvrsElementSpecificationFloat;

import org.gridfour.gvrs.GvrsElementSpecificationIntCodedFloat;
import org.gridfour.gvrs.GvrsElementSpecificationShort;
import org.gridfour.gvrs.GvrsElementType;
import org.gridfour.gvrs.GvrsFile;
import org.gridfour.gvrs.GvrsFileSpecification;
import org.gridfour.gvrs.GvrsMetadata;
import org.gridfour.gvrs.GvrsMetadataNames;
import org.gridfour.io.FastByteArrayOutputStream;
import org.gridfour.lsop.LsCodecUtility;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

/**
 * A simple demonstration application showing how to create a GVRS file from the
 * ETOPO1 and GEBCO global elevation/bathymetry data sets. These data sets are
 * distributed in the NetCDF file format.
 */
public class PackageData {

  private static String[] usage = {
    "PackageData  -- create a GVRS file from from ETOPO1 or GEBCO_2019 Global DEM files",
    "Arguments:",
    "   -in     <input_file_path>",
    "   -out    <output_file_path>",
    "",
    "   -zScale <value>                Apply a scale factor for floating-point data",
    "   -tileSize <###x###>            Specify n_rows and n_columns of tile (i.e. 90x120)",
    "   -compress (-nocompress)        Apply compression to file (default: false)",
    "   -checksums (-nochecksums)      Compute checksums (default: false)",
    "   -verify (-noconfirm)           Verify that output is correct (default: false)",
    "   -lsop (-nolsop)                Enable LS encoder when compressing data (default:false)",
    "   -multithread (-nomultithread)  Enable multple threads to expedite data compression (default: true)",
    "",
    "   -geographic (-nogeographic)    Specifies that horizontal coordinates are geographic (latitude,longitude)",
    "   -cartesian (-nocartesian)      Specifies that horizontal coordinates are Cartesian (x,y)",
    "",
    "Notes: ",
    "  The zScale option instructs the packager to use the",
    "  integer-scaled-float data type when storing values.",
    "  If it is not specified, the data type will be selected",
    "  based on the data-type specification of the original data.",
    "",
    "  Unless explictly set, the determination of whether horizontal coordinates",
    "  are geographic or Cartesian is based on the names of the horizontal variables",
    "  in the source data. If the names are \"latitude\" and \"longitude\",",
    "  (or partial matches thereof), the coordinates are assumed geographic.",};

  private final static short LIMIT_DEPTH = -11000;   // Challenger deep, 10,929 wikipedia
  private final static short LIMIT_ELEVATION = 8848; // Everest, wikipedia
  private final static short FILL_VALUE = -32768;

  private static void printUsageAndExit() {
    for (String s : usage) {
      System.err.println(s);
    }
    System.exit(0);
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      printUsageAndExit();
    }

    TestOptions options = new TestOptions();
    options.argumentScan(args);

    PackageData extractor = new PackageData();

    try {
      extractor.process(System.out, options, args);
    } catch (IOException | IllegalArgumentException ex) {
      System.err.println("Error processing " + args[0] + " file " + args[1]);
      System.err.println(ex.getMessage());
      ex.printStackTrace(System.err);
    }

  }

  void process(PrintStream ps, TestOptions options, String[] args)
    throws IOException {

    // The packaging of data in a GVRS file can be thought of in terms of
    // the steps shown below.
    //
    //    0.  Obtain descriptive parameters about source data.  In this
    //        case, the application is packing data from a NetCDF source
    //        and most of the descriptive parameters follow the pattern
    //        established in the earlier ExtractData.java demonstration
    //
    //    1.  Define the fixed metadata about the file (it's dimensions,
    //        data type, tile organization, etc.) using a GvrsFileSpecification
    //        object.
    //
    //    2.  Open a new GvrsFile object using the settings created in step 1.
    //        Adjust any run-time parameters (such as the tile-cache size)
    //        according to the needs of the application.
    //
    //    3.  Extract the data from its source and store in the GVRS file.
    //
    ps.format("%nGVRS Packaging Application for NetCDF-format Global DEM files%n");
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm z", locale);
    ps.format("Date of Execution: %s%n", sdFormat.format(date));

    String inputPath = options.getInputFile().getPath();
    File inputFile = new File(inputPath);
    File outputFile = options.getOutputFile();
    if (outputFile == null) {
      ps.format("Missing specification for output file%n");
      ps.format("Packaging application terminated%n");
      return;
    }
    ps.format("Input file:  %s%n", inputPath);
    ps.format("Output file: %s%n", outputFile.getPath());
    boolean[] matched = new boolean[args.length];

    // Some options are not captured by TestOptions and are specific
    // to this application
    boolean useLsop = options.scanBooleanOption(args, "-lsop", matched, false);
    boolean enableMultiThreading
      = options.scanBooleanOption(args, "-multithread", matched, true);

    // Open the NetCDF file -----------------------------------
    ps.println("Opening NetCDF input file");
    try (NetcdfFile ncfile = NetcdfFiles.open(inputPath)) {
      // Identify which Variable instances carry information about the
      // geographic or Cartesian (latitude/longitude) coordinate system and also which
      // carry information for elevation and bathymetry.
      Variable rowCoordinate;   // the Variable that carries row-latitude information
      Variable colCoordinate;   // the Variable that carries column-longitude information
      Variable z;     // the variable that carries elevation and bathymetry
      String wkt = null; // well-known text for Coordinate Reference System (CRS)
      z = ncfile.findVariable("elevation");
      if (z == null) {
        z = ncfile.findVariable("z");
      }
      if (z == null) {
        throw new IllegalArgumentException(
          "Input file does not contain recognized vertical coordinate variable (must be either z or elevation)");
      }

      // Get the dimensions of the raster (grid) elevation/bathymetry data.
      ps.format("Using NetCDF variable: \"%s\"\n", z.getFullName());
      int rank = z.getRank(); // should be 1.
      int[] shape = z.getShape();
      int nRows = shape[0];
      int nCols = shape[1];
      ps.format("   Rows:          %8d%n", nRows);
      ps.format("   Columns:       %8d%n", nCols);

      int[] chunkShape = null;
      Attribute a = z.findAttribute("_ChunkSizes");
      if (a != null) {
        Array chunkSizes = a.getValues();
        chunkShape = new int[2];
        chunkShape[0] = chunkSizes.getInt(0);
        chunkShape[1] = chunkSizes.getInt(1);
        ps.format("   Chunk Rows:    %8d%n", chunkShape[0]);
        ps.format("   Chunk Columns: %8d%n", chunkShape[1]);
      }

      // NetCDF specifies a "chunk size" that is conceptually similar to
      // the GVRS tile size.  If the chunk size does not cover an entire
      // row, then reading the source data by looping across rows of grid cells
      // would normally be inefficient and SLOW.  However, the NetCDF API does
      // allow an application set a cache which, again, is similar in concept
      // to the GVRS tile cache.
      //    This application scans the source file on a row-by-row basis.
      // It would be more efficient to scan the file based on its chunk size,
      // but the current NetCDF version (5.6.0) does not provide an API for
      // obtaining those parameters. Even if it did, the loops to access chunks
      // would be a bit more complicated that the simple row loop one used here.
      // To compensate for that, we turn on the cache.  Unfortunately, the
      // setCaching() method is deprecated and we were unable to find an
      // alternate way of activating the cache. So we will have to watch
      // this feature to see if it changes in the future.
      if (chunkShape != null && (chunkShape[0] != 1 || chunkShape[1] != nCols)) {
        ps.format("Applying cache on input variable%n");
        z.setCaching(true);
      }

      rowCoordinate = ncfile.findVariable("lat");
      colCoordinate = ncfile.findVariable("lon");
      if (rowCoordinate == null) {
        rowCoordinate = ncfile.findVariable("latitude");
      }
      if (colCoordinate == null) {
        colCoordinate = ncfile.findVariable("longitude");
      }

      boolean geographicCoordinates = options.isGeographicCoordinateSystemSet();
      if (inputFile.getName().toUpperCase().contains("ETOP")) {
        // special rule: ETOPO1 gives latitde and longitude as variables
        // x and y.  If the command-line arguments don't explictly indicate
        // geographic or cartesian, default to geographic... we do this
        // just in case the user forgot.
        if (!options.isCartesianOptionSpecified() && !options.isGeographicOptionSpecified()) {
          geographicCoordinates = true;
        }
      }

      IExtractionCoordinates extractionCoords = null;
      if (rowCoordinate != null && colCoordinate != null) {
        // geographic coordinates are in place
        // no matter what the command-line arguments specified
        // because the horizontal coordinates are geographic (latitude, longitude),
        // the row coordinate is passed in first.
        geographicCoordinates = true;
        extractionCoords = new ExtractionCoordinatesGeographic(rowCoordinate, colCoordinate);
      } else {
        // some products, such as ETOPO1 use geographic coordinate,
        // but give them in using the variables x,y
        rowCoordinate = ncfile.findVariable("y");
        colCoordinate = ncfile.findVariable("x");
        if (rowCoordinate == null || colCoordinate == null) {
          throw new IllegalArgumentException(
            "Input file does not contain recognized horizontal coordinate variables (must be either lat,lon or x,y)");
        }

        if (geographicCoordinates) {
          // the horizontal coordinates are geographic (latitude, longitude)
          // so the row coordinate is passed in first.
          extractionCoords = new ExtractionCoordinatesGeographic(rowCoordinate, colCoordinate);
        } else {
          // the horizontal coordinates are cartesian, so the column corresponds
          // to x coordinates and the row to y coordinates.
          extractionCoords = new ExtractionCoordinatesCartesian(colCoordinate, rowCoordinate);
        }
      }

      // using the variables from above, extract coordinate system
      // information for the product and print it to the output.
      extractionCoords.summarizeCoordinates(ps);

      // Later ETOPO sources (2022 and after) include a coordinate
      // reference system (CRS) specification.  If one is available,
      // use it to obtain a well-known text string.  Otherwise, this
      // application supplies one from the bundled resources.
      Variable crs = ncfile.findVariable("crs");
      if (crs == null) {
        crs = ncfile.findVariable("CRS");
      }
      if (crs != null) {
        Attribute att = crs.findAttribute("spatial_ref");
        if (att != null) {
          wkt = att.getStringValue();
        }
      }

      int[] tileSize = options.getTileSize(90, 120);
      int nRowsInTile = tileSize[0];
      int nColsInTile = tileSize[1];

      // Use the input file name to format a product label
      String productLabel = inputFile.getName();
      if (productLabel.toLowerCase().endsWith(".nc")) {
        productLabel = productLabel.substring(0, productLabel.length() - 3);
      }

      // Initialize the specification used to initialize the GVRS file -------
      GvrsFileSpecification spec
        = new GvrsFileSpecification(nRows, nCols, nRowsInTile, nColsInTile);
      spec.setLabel(productLabel);

      // Finish adding information related to coordinate transforms
      double[] coordinateBounds = extractionCoords.getCoordinateBounds();
      if (geographicCoordinates) {
        spec.setGeographicCoordinates(
          coordinateBounds[0],
          coordinateBounds[1],
          coordinateBounds[2],
          coordinateBounds[3]);
      } else {
        spec.setCartesianCoordinates(
          coordinateBounds[0],
          coordinateBounds[1],
          coordinateBounds[2],
          coordinateBounds[3]);
      }
      // Check to verify that the geographic coordinates and grid coordinate
      // are correctly implemented. This test is not truly part of the packaging
      // process (since it should always work), but is included here as a
      // diagnostic to verify that this code is properly implemented.
      extractionCoords.checkSpecificationTransform(ps, spec);

      // Initialize the data type.  If a zScale option was specified,
      // use integer-coded floats.  Otherwise, pick the data type
      // based on whether the NetCDF file gives integral or floating point
      // data.
      boolean isZScaleSpecified = options.isZScaleSpecified();
      float zScale = (float) options.getZScale();
      float zOffset = (float) options.getZOffset();
      DataType sourceDataType = z.getDataType();  // data type from NetCDF file
      GvrsElementSpecification elementSpec = null;
      GvrsElementType gvrsDataType;
      if (isZScaleSpecified) {
        // the options define our data type
        int encodedLimitDepth = (int) ((LIMIT_DEPTH - zOffset) * zScale);
        int encodedLimitElev = (int) ((LIMIT_ELEVATION - zOffset) * zScale);

        elementSpec = new GvrsElementSpecificationIntCodedFloat(
          "z", zScale, zOffset,
          encodedLimitDepth, encodedLimitElev, Integer.MIN_VALUE, true);
        spec.addElementSpecification(elementSpec);
        gvrsDataType = GvrsElementType.INT_CODED_FLOAT;
      } else if (sourceDataType.isIntegral()) {
        elementSpec = new GvrsElementSpecificationShort("z",
          LIMIT_DEPTH, LIMIT_ELEVATION, FILL_VALUE);
        spec.addElementSpecification(elementSpec);
        gvrsDataType = GvrsElementType.SHORT;
      } else {
        elementSpec = new GvrsElementSpecificationFloat("z",
          LIMIT_DEPTH, LIMIT_ELEVATION, Float.NaN);
        spec.addElementSpecification(elementSpec);
        gvrsDataType = GvrsElementType.FLOAT;
      }
      elementSpec.setDescription("Elevation (positive values) or depth (negative), in meters");
      elementSpec.setUnitOfMeasure("m");
      elementSpec.setLabel("Elevation"); // Example with special character
      elementSpec.setContinuous(true);

      ps.println("Source date type " + sourceDataType + ", stored as " + gvrsDataType);
      ps.println("");
      ps.println("Multi-threading enabled: " + enableMultiThreading);
      if (enableMultiThreading) {
        ps.println("Available processors:    "
          + Runtime.getRuntime().availableProcessors());
      }

      // Determine whether data compression is used -------------------
      boolean compressionEnabled = options.isCompressionEnabled();
      spec.setDataCompressionEnabled(compressionEnabled);
      boolean checksumsEnabled = options.isChecksumComputationEnabled();
      spec.setChecksumEnabled(checksumsEnabled);

      // Add the LSOP optimal predictor codec to the specification.
      // This enhanced compression technique will be used only if compression
      // is enabled and the data type is integral.
      if (useLsop) {
        LsCodecUtility.addLsopToSpecification(spec, false);
      }

      // ---------------------------------------------------------
      // Create the output file and store the content from the input file.
      if (outputFile.exists()) {
        ps.println("Output file exists. Removing old file");
        boolean status = outputFile.delete();
        if (!status) {
          ps.println("Removal attempt failed");
          return;
        }
      }

      ps.println("Begin processing");
      double zMin = Double.POSITIVE_INFINITY;
      double zMax = Double.NEGATIVE_INFINITY;
      double zSum = 0;
      long nSum = 0;
      try (GvrsFile gvrs = new GvrsFile(outputFile, spec)) {
        gvrs.setMultiThreadingEnabled(enableMultiThreading);
        gvrs.setTileCacheSize(GvrsCacheSize.Large);

        gvrs.writeMetadata(GvrsMetadataNames.Copyright,
          "This data is in the public domain and may be used free of charge");
        gvrs.writeMetadata(GvrsMetadataNames.TermsOfUse,
          "This data should not be used for navigation");
        storeGeoreferencingInformation(gvrs, wkt);

        // Initialize data-statistics collection ---------------------------
        // we happen to know the range of values for the global DEM a-priori.
        // it ranges from about -11000 to 8650.  This allows us to tabulate counts
        // of which values we find in the data source.  We can use this information
        // to estimate the entropy of the source data and make a realistic
        // assessment of how many bytes would be needed to store them.
        InputDataStatCollector stats
          = new InputDataStatCollector(-11000, 8650, zScale);

        int[] readOrigin = new int[rank];
        int[] readShape = new int[rank];

        // -----------------------------------------------------------------
        // Package the data
        GvrsElement zElement = gvrs.getElement("z");
        long time0 = System.currentTimeMillis();
        for (int iRow = 0; iRow < nRows; iRow++) {
          if (iRow % 1000 == 999) {
            long time1 = System.currentTimeMillis();
            double deltaT = time1 - time0;
            double rate = (iRow + 1) / deltaT;  // rows per millis
            int nRemaining = nRows - iRow;
            long remainingT = (long) (nRemaining / rate);
            Date d = new Date(time1 + remainingT);
            ps.format("Completed %d rows, %4.1f%% of total, est completion at %s%n",
              iRow + 1, 100.0 * (double) iRow / (nRows - 1.0), d);
            ps.flush();
          }

          int row0 = iRow;
          int col0 = 0;
          readOrigin[0] = row0;
          readOrigin[1] = col0;
          readShape[0] = 1;
          readShape[1] = nCols;
          // The NetCDF access routines can throw an invalid range exception
          // if given indexing values that are out-of-range.  That shouldn't
          // happen in this application unless the input file is corrupt.
          try {
            Array array = z.read(readOrigin, readShape);
            // Loop on each column, obtain the data from the NetCDF file
            // and store it in the GVRS file.
            switch (gvrsDataType) {
              case INTEGER:
              case SHORT:
                for (int iCol = 0; iCol < nCols; iCol++) {
                  int sample = array.getInt(iCol);
                  zElement.writeValueInt(iRow, iCol, sample);
                  stats.addSample(sample);
                  if (sample < zMin) {
                    zMin = sample;
                  }
                  if (sample > zMax) {
                    zMax = sample;
                  }
                  zSum += sample;
                  nSum++;
                }
                break;
              case INT_CODED_FLOAT:
              case FLOAT:
              default:
                for (int iCol = 0; iCol < nCols; iCol++) {
                  float sample = array.getFloat(iCol);
                  zElement.writeValue(iRow, iCol, sample);
                  stats.addSample(sample);
                  if (sample < zMin) {
                    zMin = sample;
                  }
                  if (sample > zMax) {
                    zMax = sample;
                  }
                  zSum += sample;
                  nSum++;
                }

            }
          } catch (InvalidRangeException irex) {
            throw new IOException(irex.getMessage(), irex);
          }
        }

        gvrs.flush();
        long time1 = System.currentTimeMillis();
        double timeToProcess = (time1 - time0) / 1000.0;
        ps.format("Finished processing file in %4.1f seconds%n", timeToProcess);
        ps.format("Entropy for input data %4.1f bits/sample%n", stats.getEntropy());
        long outputSize = outputFile.length();
        long nCells = (long) nRows * (long) nCols;
        double bitsPerSymbol = 8.0 * (double) outputSize / (double) nCells;
        ps.format("Storage used (including overhead) %6.4f bits/sample%n", bitsPerSymbol);

        ps.format("%nSummary of file content and packaging actions------------%n");
        gvrs.summarize(ps, true);
        ps.format("Range of z values:%n");
        ps.format("  Min z: %8.3f%n", zMin);
        ps.format("  Max z: %8.3f%n", zMax);
        ps.format("  Avg z: %8.3f%n", zSum / (nSum > 0 ? nSum : 1));
      }

      // If the calling application desires that we do so, verify the
      // newly created file by re-opening it and comparing its content
      // to those of the source data.
      if (options.isVerificationEnabled()) {
        int[] readOrigin = new int[rank];
        int[] readShape = new int[rank];

        ps.println("\nTesting product for data consistency with source");
        ps.println("Opening gvrs file for reading");
        long time0 = System.currentTimeMillis();
        try (GvrsFile gvrs = new GvrsFile(outputFile, "r")) {
          long time1 = System.currentTimeMillis();
          ps.println("Opening complete in " + (time1 - time0) + " ms");

          gvrs.setMultiThreadingEnabled(enableMultiThreading);
          gvrs.setTileCacheSize(GvrsCacheSize.Large);
          GvrsFileSpecification testSpec = gvrs.getSpecification();
          String testLabel = testSpec.getLabel();
          ps.println("Label:     " + testLabel);
          GvrsMetadata m = gvrs.readMetadata("Copyright", 0);
          if (m != null) {

            ps.println("Copyright: " + m.getString());
          }
          GvrsElement zElement = gvrs.getElement("z");
          ps.println("Element:   " + zElement.getName() + ", " + zElement.getDescription());

          for (int iRow = 0; iRow < nRows; iRow++) {
            if (iRow % 10000 == 9999) {
              time1 = System.currentTimeMillis();
              double deltaT = time1 - time0;
              double rate = (iRow + 1) / deltaT;  // rows per millis
              int nRemaining = nRows - iRow;
              long remainingT = (long) (nRemaining / rate);
              Date d = new Date(time1 + remainingT);
              ps.format("Completed %d rows, %4.1f%% of total, est completion at %s%n",
                iRow + 1, 100.0 * (double) iRow / (nRows - 1.0), d);
              ps.flush();
            }

            int row0 = iRow;
            int col0 = 0;
            readOrigin[0] = row0;
            readOrigin[1] = col0;
            readShape[0] = 1;
            readShape[1] = nCols;
            try {
              Array array = z.read(readOrigin, readShape);
              switch (gvrsDataType) {
                case INTEGER:
                  for (int iCol = 0; iCol < nCols; iCol++) {
                    int sample = array.getInt(iCol);
                    int test = zElement.readValueInt(iRow, iCol);
                    if (sample != test) {
                      ps.println("Failure at " + iRow + ", " + iCol);
                      test = zElement.readValueInt(iRow, iCol);
                      System.exit(-1);
                    }
                  }
                  break;
                case INT_CODED_FLOAT:
                  for (int iCol = 0; iCol < nCols; iCol++) {
                    double sample = array.getDouble(iCol);
                    int iSample = (int) Math.floor((sample - zOffset) * zScale + 0.5);
                    float fSample = iSample / zScale + zOffset;
                    float test = zElement.readValue(iRow, iCol);
                    double delta = Math.abs(fSample - test);
                    if (delta > 1.01 / zScale) {
                      ps.println("Failure at " + iRow + ", " + iCol);
                      System.exit(-1);
                    }
                  }
                  break;
                case FLOAT:
                default:
                  for (int iCol = 0; iCol < nCols; iCol++) {
                    float sample = array.getFloat(iCol);
                    float test = zElement.readValue(iRow, iCol);
                    if (sample != test) {
                      ps.println("Failure at " + iRow + ", " + iCol);
                      test = zElement.readValueInt(iRow, iCol);
                      System.exit(-1);
                    }
                  }
              }
            } catch (InvalidRangeException irex) {
              throw new IOException(irex.getMessage(), irex);
            }
          }

          time1 = System.currentTimeMillis();
          ps.println("Exhaustive cross check complete in " + (time1 - time0) + " ms");
          gvrs.summarize(ps, false);
        }

      }
    }
  }

  /**
   * Stores a GVRS tag (metadata) that gives coordinate/projection
   * data in the Well-Known Text (WKT) format used in many GIS systems. This
   * setting will allow applications to find out what kind of coordinate system
   * is stored in the GVRS file using an industry-standard text format.
   *
   * @param gvrs a valid GVRS file opened for writing
   * @param sourceWKT if Well-Known Text was supplied from the source data,
   * a valid string; otherwise, a null.
   * @throws IOException in the event of an unhandled I/O exception.
   */
  void storeGeoreferencingInformation(GvrsFile gvrs, String sourceWKT) throws IOException {
    // Note:  At this time, the Well-Known Text (WKT) data for this
    // demo may not be complete. In particular, it does not include the
    // TOWGS84 node (the "to WGS 1984" node which specifies transformations
    // for the ellipsoid).  Because the products supported by this demonstration
    // are derived from multiple data sources, the included specifications
    // may be adequate for their intrinsic accuracy.  However, suggestions
    // from knowledgeable users are welcome.
    String wkt = null;
    if (sourceWKT != null && !sourceWKT.isBlank()) {
      wkt = sourceWKT;
    }
    if (wkt == null) {
      InputStream ins = PackageData.class.getResourceAsStream("GlobalMSL.prj");
      FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
      byte[] b = new byte[8196];
      int nBytesRead;
      while ((nBytesRead = ins.read(b)) > 0) {
        fbaos.write(b, 0, nBytesRead);
      }
      b = fbaos.toByteArray();
      wkt = new String(b, StandardCharsets.UTF_8);
    }
    if (wkt != null) {
      GvrsMetadata metadataWKT = GvrsMetadataNames.WKT.newInstance();
      metadataWKT.setDescription("Well-Known Text, geographic metadata");
      metadataWKT.setString(wkt);
      gvrs.writeMetadata(metadataWKT);
    }
  }

}
