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
 * 09/2019  G. Lucas     Created
 *
 * Notes:
 *
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.globalDEM;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import javax.imageio.ImageIO;
import org.gridfour.interpolation.InterpolationResult;
import org.gridfour.interpolation.InterpolationTarget;
import org.gridfour.interpolation.InterpolatorBSpline;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

/**
 * A simple demonstration application showing how to access the ETOPO1 and GEBCO
 * 2019 global elevation/bathymetry data sets which are distributed in the
 * NetCDF file format.
 * <p>
 * This application extracts the data from these Global-scale Digital Elevation
 * Model (DEM) products and uses it to create 720-by-360 pixel images showing
 * the content of the files. It also tabulates simple statistics about the data
 * and prints a summary.
 */
public class ExtractData {

  private static String[] usage = {
    "ExtractData  -- extracts data from ETOPO1 or GEBCO_2019 Global DEM files",
    "Arguments:",
    "   Product_label  Input_file_path",
    "   For example:   ETOPO   ETOPO_2022_v1_60s_N90W180_surface.nc"
  };

  //Equatorial radius of earth, in meters.
  private static double earthRadiusM = 6378137;

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

    ExtractData extractor = new ExtractData();

    try {
      extractor.process(System.out, args[0], args[1]);
    } catch (IOException | InvalidRangeException ex) {
      System.err.println("Error processing " + args[0] + " file " + args[1]);
      System.err.println(ex.getMessage());
    }

  }

  private ExtractData() {
    // a private constructor to deter application code
    // from making direct instances of this class.
  }

  void process(PrintStream ps, String product, String inputPath)
    throws IOException, InvalidRangeException {

    long time0 = 0;
    String label;   // will be set using args[0]
    float[][] palette;  // will be based on label

    // We are going to compile an image illustrating the data
    // using the following variables
    int imageHeight = 360;
    int imageWidth = 720;
    int pixelScale;
    double[] sampleSum;

    // we also wish to collect the minimum and maximum values of the data.
    double zMin = Double.POSITIVE_INFINITY;
    double zMax = Double.NEGATIVE_INFINITY;

    // We are going to use the input data to estimate both the
    // surface area and volume of the world's oceans.  This calculation
    // is not authoritative, and is performed here only to illustrate
    // a potential use of the data.  We perform the calculation by
    // obtaining the surface area for each "cell" in the input raster
    // (surface area per cell will vary by latitude).  Then, if the sample
    // is less than zero, we treat it as an ocean depth and add the
    // computed area and volume to a running sum.
    double areaSum = 0;
    double volumeSum = 0;
    double depthSum = 0;
    long nDepth = 0;

    // Open the NetCDF file -----------------------------------
    ps.println("Reading data from " + inputPath);
    try (NetcdfFile ncfile = NetcdfFiles.open(inputPath)) {

      // Inspect the content of the file ----------------------------
      // Start with the high-level metadata elements that describe the
      // entire file
      ps.println("NetCDF File Type: " + ncfile.getFileTypeDescription());
      ps.println("");
      ps.println("Global attributes attached to file---------------------------");
      for (Attribute a : ncfile.getRootGroup().attributes()) {
        ps.println(a.toString());
      }

      // The content of NetCDF files is accessed through the use of the
      // NetCDF class named Variable. Get all Variables defined in the
      // current file and print a summary of their metadata to the text output.
      // The Java NetCDF team implemented good "toString()" methods
      // for the variable class.  So printing their metadata is quite easy.
      ps.println("");
      ps.println("Variables found in file--------------------------------------");
      for (Variable v : ncfile.getRootGroup().getVariables()) {
        ps.println("\n" + v.toString());
      }

      // Identify which Variable instances carry information about the
      // geographic (latitude/longitude) coordinate system and also which
      // carry information for elevation and bathymetry.
      // In NetCDF, Variable instances area associated
      // with arbitrary "name" attributes assigned to the objects
      // when the data product is created. We can pull these variables
      // out of the NetCDF file using their names.  However,
      // ETOPO1 and GEBCO_2019 use different Variable names to identify
      // their content.
      Variable lat;   // the Variable that carries row-latitude information
      Variable lon;   // the Variable that carries column-longitude information
      Variable z;     // the variable that carries elevation and bathymetry

      int reportingCount;

      z = ncfile.findVariable("elevation");
      if (z == null) {
        z = ncfile.findVariable("z");
      }
      if (z == null) {
        throw new IllegalArgumentException(
          "Input file does not contain recognized vertical coordinate variable (must be either z or elevation)");
      }
      // z.createNewCache();   this does not work
      z.setCaching(true);   // this is deprecated, but it works.

      lat = ncfile.findVariable("lat");

      if (lat == null) {
        lat = ncfile.findVariable("latitude");
      }
      if (lat == null) {
        lat = ncfile.findVariable("y");
      }

      lon = ncfile.findVariable("lon");
      if (lon == null) {
        lon = ncfile.findVariable("longitude");
      }
      if (lon == null) {
        lon = ncfile.findVariable("x");
      }

      if (product.startsWith("ETOP")) {
        label = "ETOPO1";
        palette = ExamplePalettes.paletteETOPO1;
        reportingCount = 1000;
      } else if (product.startsWith("GEBCO")) {
        // the product is GEBCO
        label = "GEBCO";
        palette = ExamplePalettes.paletteGEBCO;
        reportingCount = 10000;
      } else {
        label = product;
        palette = ExamplePalettes.paletteETOPO1;
        reportingCount = 1000;
      }

      // using the variables from above, extract coordinate system
      // information for the product and print it to the output.
      // we will use this data below for computing the volume of the
      // world's oceans.
      ExtractionCoordinatesGeographic coords = new ExtractionCoordinatesGeographic(lat, lon);
      coords.summarizeCoordinates(ps);

      // Get the dimensions of the raster (grid) elevation/bathymetry data.
      //
      // NetCDF uses an concept named "rank" to define the rank of a
      // Variable.  A variable of rank 1 is essentially a vector (or one
      // dimensional array).  A variable of rank 2 has rows and columns,
      // and is essentially a matrix.  Higher rank variables are not uncommon.
      //
      // In ETOPO1 and GEBCO_2019, the elevation/bathymetry data is given
      // as a raster (grid), so the rank will be two (for rows and columns).
      // NetCDF data is always given in row-major order.
      //
      // NetCDF Variables also use the concept of "shape". In this case,
      // the "shape" element tells you the number of rows and columns
      // in the elevation variable.
      // The getShape() method returns an array of integers dimensioned to
      // the rank.  Since the "z" variable is a raster, the return from
      // getShape() will be an array of two values where shape[0] is
      // the number of rows in the product and shape[1] is the number of
      // columns.
      //    ETOPO1:        10800 rows and 21600 columns.
      //    GEBCO 2019:    43200 rows and 86400 columns
      // In both these products, the rows are arranged from south-to-north,
      // starting near the South Pole (-90 degrees) and ending near
      // the North Pole (90 degrees).
      //   ETOPO1 has a uniform point spacing a 1-minute of arc.
      //   GEBCO has a uniform point spacing of 15-seconds of arc.
      // Thus there are 4 times as many rows and 4 times as many columns
      // in GEBCO versus ETOPO1.
      int rank = z.getRank();
      int[] shape = z.getShape();

      int nRows = shape[0];
      int nCols = shape[1];
      ps.format("Rows:      %8d%n", nRows);
      ps.format("Columns:   %8d%n", nCols);

      // The output for this application is an image that is
      // 720 pixels wide and 360 pixels high.  Since the resulution of
      // the two products is much higher than that, we need to compute a
      // pixel scale value for down-sampling the source data.
      // As we read the data, we will combine blocks of elevation/bathymetry
      // values into a average value.
      imageHeight = 360;
      imageWidth = 720;
      pixelScale = nCols / imageWidth;
      sampleSum = new double[imageHeight * imageWidth];
      ps.format("Down scaling data %d to 1 for image (%d values per pixel)%n",
        pixelScale, pixelScale * pixelScale);

      // naturally, the source data products contain far too many data values
      // for us to read into memory all at once.  We could read them one-at-a-time,
      // but that would entail a lot of overhead and would slow processing
      // considerably.  So we read the data one-row-at-a-time.
      // That pattern is not always to best for some products, but it works
      // quite well for both ETOPO1 and GEBCO 2019.
      //   The readOrigin variable allows the application to specify where
      // it wants to read the data from. The readShape variable tells
      // NetCDF how many rows and columns to read.
      int[] readOrigin = new int[rank];
      int[] readShape = new int[rank];

      time0 = System.nanoTime();
      for (int iRow = 0; iRow < nRows; iRow++) {
        int imageRow = imageHeight - 1 - iRow / pixelScale;
        double areaOfCellsInRow = coords.getAreaOfEachCellInRow(iRow);
        if (iRow % reportingCount == 0) {
          System.out.println("Processing row " + iRow);
        }
        int row0 = iRow;
        int col0 = 0;
        readOrigin[0] = row0;
        readOrigin[1] = col0;
        readShape[0] = 1;
        readShape[1] = nCols;
        Array array = z.read(readOrigin, readShape);
        for (int iCol = 0; iCol < nCols; iCol++) {
          int imageCol = iCol / pixelScale;
          int index = imageRow * imageWidth + imageCol;
          double sample = array.getDouble(iCol);
          if (sample <= -32767) {
            // neither ETOPO1 or GEBCO contain "no-data" points.  However,
            // there are some similar products that do.  Treat these
            // as not-a-number
            sample = Float.NaN;
          }
          if (sample < zMin) {
            zMin = sample;
          }
          if (sample > zMax) {
            zMax = sample;
          }

          sampleSum[index] += sample;
          if (sample < 0) {
            // it's water
            areaSum += areaOfCellsInRow;
            volumeSum -= areaOfCellsInRow * sample / 1000.0;
            depthSum -= sample;
            nDepth++;
          }
        }
      }
    } catch (IOException ioex) {
      System.out.println("Encounted exception " + ioex.getMessage());
      return;
    }

    long time1 = System.nanoTime();
    double deltaT = (time1 - time0) / 1.0e+9;
    ps.format("Time to read input file %7.3f second%n", deltaT);
    ps.format("Min Value:              %7.3f meters%n", zMin);
    ps.format("Max Value:              %7.3f meters%n", zMax);
    ps.format("Surface area of oceans %20.1f km^2%n", areaSum);
    ps.format("Volume of oceans       %20.1f km^3%n", volumeSum);
    ps.format("Mean depth of oceans   %20.1f m%n", depthSum / nDepth);
    ps.flush();

    int[] argb = new int[imageHeight * imageWidth];
    float[] zPixel = new float[sampleSum.length];
    double nCellsPerPixel = pixelScale * pixelScale;
    for (int i = 0; i < sampleSum.length; i++) {
      zPixel[i] = (float) (sampleSum[i] / nCellsPerPixel);
      argb[i] = getRgb(palette, zPixel[i]);
    }

    String outputPath = "Test" + label + ".png";

    File outputImage = new File(outputPath);
    BufferedImage bImage = new BufferedImage(
      imageWidth,
      imageHeight,
      BufferedImage.TYPE_INT_ARGB);
    bImage.setRGB(0, 0, imageWidth, imageHeight, argb, 0, imageWidth);
    Graphics graphics = bImage.getGraphics();
    graphics.setColor(Color.darkGray);
    graphics.drawRect(0, 0, imageWidth - 1, imageHeight - 1);
    ImageIO.write(bImage, "PNG", outputImage);

    // Shaded Relief demo ------------------------------------------------
    // Create a shaded-relief demo by using a simple illumination model
    // and applying Gridfour's B-Spline interpolator to compute the
    // surface normal.  The x and y scales that are needed by the interpolator
    // are derived using the meters-per-pixel value which is computed from
    // the radius of the earth and the number of pixels in the image.
    // Because the distance across one degree of longitude decreases
    // as the magnitude of the latitude increases, an adjustment is
    // made to the xScale factor.  Finally, a steeping factor is added to
    // make the shading a bit more pronounced in the image.
    //
    // Specify the parameters for the illumination source (the "sun")
    double ambient = 0.2;
    double steepen = 50.0;
    double sunAzimuth = Math.toRadians(145);
    double sunElevation = Math.toRadians(60);

    // create a unit vector pointing at illumination source
    double cosA = Math.cos(sunAzimuth);
    double sinA = Math.sin(sunAzimuth);
    double cosE = Math.cos(sunElevation);
    double sinE = Math.sin(sunElevation);
    double xSun = cosA * cosE;
    double ySun = sinA * cosE;
    double zSun = sinE;

    double yScale = Math.PI * earthRadiusM / imageHeight;
    InterpolatorBSpline bSpline = new InterpolatorBSpline();
    InterpolationResult result = new InterpolationResult();
    for (int iRow = 0; iRow < imageHeight; iRow++) {
      double yRow = iRow + 0.5;
      double rowLatitude = 90 - 180.0 * yRow / imageHeight;
      double xScale = yScale * Math.cos(Math.toRadians(rowLatitude));
      for (int iCol = 0; iCol < imageWidth; iCol++) {
        double xCol = iCol + 0.5;
        bSpline.interpolate(yRow, xCol, imageHeight, imageWidth, zPixel,
          yScale, xScale,
          InterpolationTarget.FirstDerivatives,
          result);
        // double z = result.z;  not used, included for documentation
        double nx = -result.zx * steepen;
        double ny = result.zy * steepen;
        double s = Math.sqrt(nx * nx + ny * ny + 1);
        nx /= s;
        ny /= s;
        double nz = 1 / s;
        double dot = nx * xSun + ny * ySun + nz * zSun;
        double shade = ambient;
        if (dot > 0) {
          shade = dot * (1 - ambient) + ambient;
        }
        int index = iRow * imageWidth + iCol;
        argb[index] = getRgb(palette, zPixel[index], shade);
      }
    }
    outputPath = "ShadedRelief_" + label + ".png";

    outputImage = new File(outputPath);
    bImage = new BufferedImage(
      imageWidth,
      imageHeight,
      BufferedImage.TYPE_INT_ARGB);
    bImage.setRGB(0, 0, imageWidth, imageHeight, argb, 0, imageWidth);
    graphics = bImage.getGraphics();
    graphics.setColor(Color.darkGray);
    graphics.drawRect(0, 0, imageWidth - 1, imageHeight - 1);
    ImageIO.write(bImage, "PNG", outputImage);

  }

  /**
   * Gets the RGB color assignment associated with the elevation value.
   *
   * @param palette a palette chosen for the product of interest.
   * @param value an elevation value.
   * @return an integer RGB value for coloring a pixel.
   */
  static int getRgb(float[][] palette, float value) {
    if (Double.isNaN(value)) {
      return 0xffff0000;
    }
    if (value > palette[palette.length - 1][0]) {
      return 0xffffffff;
    }

    int i0 = 0;
    for (int i = 0; i < palette.length - 1; i++) {
      if (palette[i][0] <= value && value < palette[i + 1][0]) {
        i0 = i;
        break;
      }
    }
    int i1 = i0 + 1;
    float r0 = palette[i0][1];
    float g0 = palette[i0][2];
    float b0 = palette[i0][3];
    float r1 = palette[i1][1];
    float g1 = palette[i1][2];
    float b1 = palette[i1][3];
    float t = (value - palette[i0][0]) / (palette[i1][0] - palette[i0][0]);
    int r = (int) (t * (r1 - r0) + r0 + 0.5f);
    int g = (int) (t * (g1 - g0) + g0 + 0.5f);
    int b = (int) (t * (b1 - b0) + b0 + 0.5f);
    return ((((0xff00 | r) << 8) | g) << 8) | b;
  }

  /**
   * Gets the RGB color assignment associated with the elevation value.
   *
   * @param palette a palette chosen for the product of interest.
   * @param value an elevation value.
   * @param shade a value between 0 (no illumination) and 1 (full illumination).
   * @return an integer RGB value for coloring a pixel.
   */
  static int getRgb(float[][] palette, float value, double shade) {
    if (Double.isNaN(value)) {
      return 0xffff0000;
    }
    if (value > palette[palette.length - 1][0]) {
      return 0xffffffff;
    }

    int i0 = 0;
    for (int i = 0; i < palette.length - 1; i++) {
      if (palette[i][0] <= value && value < palette[i + 1][0]) {
        i0 = i;
        break;
      }
    }
    int i1 = i0 + 1;
    float r0 = palette[i0][1];
    float g0 = palette[i0][2];
    float b0 = palette[i0][3];
    float r1 = palette[i1][1];
    float g1 = palette[i1][2];
    float b1 = palette[i1][3];
    float t = (value - palette[i0][0]) / (palette[i1][0] - palette[i0][0]);
    int r = (int) ((t * (r1 - r0) + r0 + 0.5f) * shade);
    int g = (int) ((t * (g1 - g0) + g0 + 0.5f) * shade);
    int b = (int) ((t * (b1 - b0) + b0 + 0.5f) * shade);
    return ((((0xff00 | r) << 8) | g) << 8) | b;
  }

}
