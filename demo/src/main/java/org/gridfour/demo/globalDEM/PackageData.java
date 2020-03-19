/* --------------------------------------------------------------------
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.gridfour.demo.utils.TestOptions;
import org.gridfour.g93.G93CacheSize;
import org.gridfour.g93.G93DataType;
import org.gridfour.g93.G93File;
import org.gridfour.g93.G93FileSpecification;
import org.gridfour.io.FastByteArrayOutputStream;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 * A simple demonstration application showing how to create a G93 file from the
 * ETOPO1 and GEBCO global elevation/bathymetry data sets. These data sets are
 * distributed in the NetCDF file format.
 */
public class PackageData {

  private static String[] usage = {
    "PackageData  -- create a G93 file from from ETOPO1 or GEBCO_2019 Global DEM files",
    "Arguments:",
    "   -in     <input_file_path>",
    "   -out    <output_file_path>",
    "   -zScale <value>  apply a scale factor for data compression",
    "   -tileSize <###x###> width and height of tile (i.e. 90x90)",
    "   -compress (-nocompress)  apply compression to file (default: false)",
    "   -verify (-noconfirm)     test file to verify that it is correct (default: false)",
    "Note: the zScale option instructs the packager to use the",
    "      integer-scaled-float data type when storing values.",
    "      If it is not specified, the data type will be selected",
    "      based on the data-type specification of the original data",
    };

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
      extractor.process(System.out, options);
    } catch (IOException | IllegalArgumentException ex) {
      System.err.println("Error processing " + args[0] + " file " + args[1]);
      System.err.println(ex.getMessage());
      ex.printStackTrace(System.err);
    }

  }

  void process(PrintStream ps, TestOptions options)
          throws IOException {

    // The packaging of data in a G93 file can be thought of in terms of
    // the steps shown below.
    //
    //    0.  Obtain descriptive parameters about source data.  In this
    //        case, the application is packing data from a NetCDF source
    //        and most of the descriptive parameters follow the pattern
    //        established in the earlier ExtractData.java demonstration
    //
    //    1.  Define the fixed metadata about the file (it's dimensions,
    //        data type, tile organization, etc.) using a G93FileSpecification
    //        object.
    //    
    //    2.  Open a new G93File object using the settings created in step 1.
    //        Adjust any run-time parameters (such as the tile-cache size)
    //        according to the needs of the application.
    //  
    //    3.  Extract the data from its source and store in the G93 file.
    //
    ps.format("%nG93 Packaging Application for NetCDF-format Global DEM files%n");
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm z", locale);
    ps.format("Data of Execution: %s%n", sdFormat.format(date));

    String inputPath = options.getInputFile().getPath();
    File outputFile = options.getOutputFile();
    ps.format("Input file:  %s%n", inputPath);
    ps.format("Output file: %s%n", outputFile.getPath());

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
    String identification;
    if (lat == null) {
      // ETOPO1 specification
      tileSize = options.getTileSize(90, 120);
      lat = ncfile.findVariable("y");
      lon = ncfile.findVariable("x");
      z = ncfile.findVariable("z");
      identification = "ETOPO1";
    } else {
      tileSize = options.getTileSize(90, 120);
      identification = "GEBCO 2019";
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

    // Initialize the specification used to initialize the G93 file -------
    G93FileSpecification spec
            = new G93FileSpecification(nRows, nCols, nRowsInTile, nColsInTile);
    spec.setIdentification(identification);

    // Initialize the data type.  If a zScale option was specified,
    // use integer-coded floats.  Otherwise, pick the data type
    // based on whether the NetCDF file gives integral or floating point
    // data.
    boolean isZScaleSpecified = options.isZScaleSpecified();
    float zScale = (float) options.getZScale();
    float zOffset = (float) options.getZOffset();
    DataType sourceDataType = z.getDataType();  // data type from NetCDF file
    G93DataType g93DataType;
    if (isZScaleSpecified) {
      // the options define our data type
      g93DataType = G93DataType.IntegerCodedFloat;
      spec.setDataModelIntegerScaledFloat(1, zScale, zOffset);
    } else if (sourceDataType.isIntegral()) {
      g93DataType = G93DataType.Int4;
      spec.setDataModelInt(1);
    } else {
      g93DataType = G93DataType.Float4;
      spec.setDataModelFloat(1);
    }
    ps.println("Source date type "+sourceDataType+", stored as "+g93DataType);
    ps.println("");

    // Determine whether data compression is used -------------------
    boolean compressionEnabled = options.isCompressionEnabled();
    spec.setDataCompressionEnabled(compressionEnabled);

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
    
    
    try (G93File g93 = new G93File(outputFile, spec)) {
      g93.setTileCacheSize(G93CacheSize.Large);
      g93.setIndexCreationEnabled(true);
      storeGeoreferencingInformation(g93);

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
          // and store it in the G93 file.
          switch (g93DataType) {
            case Int4:
              for (int iCol = 0; iCol < nCols; iCol++) {
                int sample = array.getInt(iCol);
                g93.storeIntValue(iRow, iCol, sample);
                stats.addSample(sample);
              }
              break;
            case IntegerCodedFloat:
            case Float4:
            default:
              for (int iCol = 0; iCol < nCols; iCol++) {
                float sample = array.getFloat(iCol);
                g93.storeValue(iRow, iCol, sample);
                stats.addSample(sample);
              }

          }
        } catch (InvalidRangeException irex) {
          throw new IOException(irex.getMessage(), irex);
        }
      }

      g93.flush();
      long time1 = System.currentTimeMillis();
      double timeToProcess = (time1 - time0) / 1000.0;
      ps.format("Finished processing file in %4.1f seconds%n", timeToProcess);
      ps.format("Entropy for input data %4.1f bits/sample%n", stats.getEntropy());
      long outputSize = outputFile.length();
      long nCells = (long) nRows * (long) nCols;
      double bitsPerSymbol = 8.0 * (double) outputSize / (double) nCells;
      ps.format("Storage used (including overhead) %4.2f bits/sample%n",
              bitsPerSymbol);

      ps.format("%nSummary of file content and packaging actions------------%n");
      g93.summarize(ps, true);
    }

    // If the calling application desires that we do so, verify the
    // newly created file by re-opening it and comparing its content
    // to those of the source data.
    if (options.isVerificationEnabled()) {
      int[] readOrigin = new int[rank];
      int[] readShape = new int[rank];

      ps.println("\nTesting product for data consistency with source");
      ps.println("Opening g93 file for reading");
      long time0 = System.currentTimeMillis();
      try (G93File g93 = new G93File(outputFile, "r")) {
        long time1 = System.currentTimeMillis();
        ps.println("Opening complete in " + (time1 - time0) + " ms");
        g93.setTileCacheSize(G93CacheSize.Large);
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
            switch (g93DataType) {
              case Int4:
                for (int iCol = 0; iCol < nCols; iCol++) {
                  int sample = array.getInt(iCol);
                  int test = g93.readIntValue(iRow, iCol);
                  if (sample != test) {
                    ps.println("Failure at " + iRow + ", " + iCol);
                    System.exit(-1);
                  }
                }
                break;
              case IntegerCodedFloat:
                for (int iCol = 0; iCol < nCols; iCol++) {
                  double sample = array.getDouble(iCol);
                  int sTest = (int) Math.floor(sample * zScale + 0.5);
                  int test = g93.readIntValue(iRow, iCol);
                  if (sTest != test) {
                    ps.println("Failure at " + iRow + ", " + iCol);
                    System.exit(-1);
                  }
                }
                break;
              case Float4:
              default:
                for (int iCol = 0; iCol < nCols; iCol++) {
                  float sample = array.getFloat(iCol);
                  float test = g93.readValue(iRow, iCol);
                  if (sample != test) {
                    ps.println("Failure at " + iRow + ", " + iCol);
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
        g93.summarize(System.out, false);
      }

    }

    ncfile.close();

  }

  /**
   * Stores a G93 Variable-Length Record (VLR) that gives coordinate/projection
   * data in the Well-Known Text (WKT) format used in many GIS systems. This
   * setting will allow applications to find out what kind of coordinate system
   * is stored in the G93 file using an industry-standard text format.
   *
   * @param g93 a valid G93 file
   * @throws IOException in the event of an IO error
   */
  void storeGeoreferencingInformation(G93File g93) throws IOException {
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

    g93.storeVariableLengthRecord(
            "G93_Projection",
            2111,
            "WKT Projection Metadata",
            b, 0, b.length, true);
  }

}
