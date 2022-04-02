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
import org.gridfour.gvrs.GvrsMnc;
import org.gridfour.io.FastByteArrayOutputStream;
import org.gridfour.lsop.LsCodecUtility;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 * A simple demonstration application showing how to create a Gvrs file from the
 * ETOPO1 and GEBCO global elevation/bathymetry data sets. These data sets are
 * distributed in the NetCDF file format.
 */
public class PackageData {

  private static String[] usage = {
    "PackageData  -- create a Gvrs file from from ETOPO1 or GEBCO_2019 Global DEM files",
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
    "Note: the zScale option instructs the packager to use the",
    "      integer-scaled-float data type when storing values.",
    "      If it is not specified, the data type will be selected",
    "      based on the data-type specification of the original data",};

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

    // The packaging of data in a Gvrs file can be thought of in terms of
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
    //    3.  Extract the data from its source and store in the Gvrs file.
    //
    ps.format("%nGvrs Packaging Application for NetCDF-format Global DEM files%n");
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm z", locale);
    ps.format("Date of Execution: %s%n", sdFormat.format(date));

    String inputPath = options.getInputFile().getPath();
    File outputFile = options.getOutputFile();
    if (outputFile == null) {
      ps.format("Missing specification for output file%n");
      ps.format("Packaging application terminated%n");
      return;
    }
    ps.format("Input file:  %s%n", inputPath);
    ps.format("Output file: %s%n", outputFile.getPath());
    boolean[] matched = new boolean[args.length];
    boolean useLsop = options.scanBooleanOption(args, "-lsop", matched, false);
    boolean enableMultiTheading =
      options.scanBooleanOption(args, "-multithread", matched, true);

    // Open the NetCDF file -----------------------------------
    ps.println("Opening NetCDF input file");
    NetcdfFile ncfile = NetcdfFile.open(inputPath);

    // Identify which Variable instances carry information about the
    // geographic (latitude/longitude) coordinate system and also which
    // carry information for elevation and bathymetry.
    Variable lat;   // the Variable that carries row-latitude information
    Variable lon;   // the Variable that carries column-longitude information
    Variable z;     // the variable that carries elevation and bathymetry

    lat = ncfile.findVariable("lat");
    lon = ncfile.findVariable("lon");
    z = ncfile.findVariable("elevation");
    int[] tileSize;

    // Use the input file name to format a product label
    File inputFile = new File(inputPath);
    String productLabel = inputFile.getName();
    if(productLabel.toLowerCase().endsWith(".nc")){
      productLabel = productLabel.substring(0, productLabel.length()-3);
    }


    if (lat == null) {
      // ETOPO1 specification
      tileSize = options.getTileSize(90, 120);
      lat = ncfile.findVariable("y");
      lon = ncfile.findVariable("x");
      z = ncfile.findVariable("z");
    } else {
      tileSize = options.getTileSize(90, 120);
    }
    if (lat == null || lon == null || z == null) {
      throw new IllegalArgumentException(
        "Input does not contain valid lat,lon, and elevation Variables");
    }

    // using the variables from above, extract coordinate system
    // information for the product and print it to the output.
    ExtractionCoordinates extractionCoords = new ExtractionCoordinates(lat, lon);
    extractionCoords.summarizeCoordinates(ps);

    // Get the dimensions of the raster (grid) elevation/bathymetry data.
    int rank = z.getRank(); // should be 1.
    int[] shape = z.getShape();

    int nRows = shape[0];
    int nCols = shape[1];
    ps.format("Rows:      %8d%n", nRows);
    ps.format("Columns:   %8d%n", nCols);
    int nRowsInTile = tileSize[0];
    int nColsInTile = tileSize[1];

    // Initialize the specification used to initialize the Gvrs file -------
    GvrsFileSpecification spec
      = new GvrsFileSpecification(nRows, nCols, nRowsInTile, nColsInTile);
    spec.setLabel(productLabel);

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
      int encodedLimitDepth =  (int)((LIMIT_DEPTH-zOffset)*zScale);
      int encodedLimitElev  =  (int)((LIMIT_ELEVATION-zOffset)*zScale);

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
    elementSpec.setLabel("die H\u00f6henlage"); // Example with special character
    elementSpec.setContinuous(true);
    
    ps.println("Source date type " + sourceDataType + ", stored as " + gvrsDataType);
    ps.println("");
    ps.println("Multi-threading enabled: "+enableMultiTheading);

    // Determine whether data compression is used -------------------
    boolean compressionEnabled = options.isCompressionEnabled();
    spec.setDataCompressionEnabled(compressionEnabled);
    boolean checksumsEnalbed = options.isChecksumComputationEnabled();
    spec.setChecksumEnabled(checksumsEnalbed);
    boolean bigAddressSpaceEnabled = options.isBigAddressSpaceEnabled();
    spec.setExtendedFileSizeEnabled(bigAddressSpaceEnabled);

    double[] geoCoords = extractionCoords.getGeographicCoordinateBounds();

    spec.setGeographicCoordinates(
      geoCoords[0],
      geoCoords[1],
      geoCoords[2],
      geoCoords[3]);

    // Check to verify that the geographic coordinates and grid coordinate
    // are correctly implemented. This test is not truly part of the packaging
    // process (since it should always work), but is included here as a
    // diagnostic.
    extractionCoords.checkSpecificationTransform(ps, spec);

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
      gvrs.setMultiThreadingEnabled(enableMultiTheading);

      gvrs.writeMetadata(GvrsMnc.Copyright,
        "This data is in the public domain and may be used free of charge");

      gvrs.writeMetadata(GvrsMnc.TermsOfUse,
        "This data should not be used for navigation");

      GvrsElement zElement = gvrs.getElement("z");
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      storeGeoreferencingInformation(gvrs);

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
          // and store it in the Gvrs file.
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
      ps.format("Storage used (including overhead) %6.4f bits/sample%n",
        bitsPerSymbol);

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
        GvrsFileSpecification testSpec = gvrs.getSpecification();
        String testLabel = testSpec.getLabel();
                  ps.println("Label:     "+testLabel);
        GvrsMetadata m = gvrs.readMetadata("Copyright", 0);
        if (m != null) {

        ps.println("Copyright: " + m.getString());
        }
        GvrsElement zElement = gvrs.getElement("z");
        ps.println("Element:   " + zElement.getName() + ", " + zElement.getDescription());

        gvrs.setTileCacheSize(GvrsCacheSize.Large);
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
                  int iSample = (int) ((sample - zOffset) * zScale + 0.5);
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

    ncfile.close();

  }

  /**
   * Stores a GVRS tag (metadata) that gives coordinate/projection
   * data in the Well-Known Text (WKT) format used in many GIS systems. This
   * setting will allow applications to find out what kind of coordinate system
   * is stored in the GVRS file using an industry-standard text format.
   *
   * @param gvrs a valid GVRS file opened for writing
   * @throws IOException in the event of an unhandled I/O exception.
   */
  void storeGeoreferencingInformation(GvrsFile gvrs) throws IOException {
    // Note:  At this time, the Well-Known Text (WKT) data for this
    // demo may not be complete. In particular, it does not include the
    // TOWGS84 node (the "to WGS 1984" node which specifies transformations
    // for the ellipsoid).  Because the products supported by this demonstration
    // are derived from multiple data sources, the included specifications
    // may be adequate for their intrinsic accuracy.  However, suggestions
    // from knowledgeable users are welcome.
    InputStream ins = PackageData.class.getResourceAsStream("GlobalMSL.prj");
    FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
    byte[] b = new byte[8196];
    int nBytesRead;
    while ((nBytesRead = ins.read(b)) > 0) {
      fbaos.write(b, 0, nBytesRead);
    }
    b = fbaos.toByteArray();
    String wkt = new String(b, StandardCharsets.UTF_8);

    GvrsMetadata metadataWKT = GvrsMnc.WKT.newInstance();
    metadataWKT.setDescription("Well-Known Text, geographic metadata");
    metadataWKT.setString(wkt);
    gvrs.writeMetadata(metadataWKT);
  }

}
