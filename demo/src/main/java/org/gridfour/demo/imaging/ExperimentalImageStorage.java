/*
 * The MIT License
 *
 * Copyright 2022 G. W. Lucas.
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.gridfour.demo.imaging;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;
import javax.imageio.ImageIO;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.gridfour.gvrs.GvrsCacheSize;
import org.gridfour.gvrs.GvrsElement;
import org.gridfour.gvrs.GvrsElementSpecification;
import org.gridfour.gvrs.GvrsElementSpecificationInt;
import org.gridfour.gvrs.GvrsFile;
import org.gridfour.gvrs.GvrsFileSpecification;
import org.gridfour.lsop.LsCodecUtility;

/**
 * Provides and experimental application intended to explore options for
 * using the GVRS API to process and store image data.
 * <p>
 * This class was designed to handle large-scale photographic images
 * and is optimized to the structure typical of such images. It
 * is an experimental application and does not necessarily represent
 * the best solution for general use.
 */
public class ExperimentalImageStorage {

  /**
   * Process the specified file storing it in different image formats
   * as a way of testing data processing concepts for GVRS.
   *
   * @param args the command line arguments, the first of which give the path
   * to the input file
   * @throws java.io.IOException in the event of an unhandled IO exception
   * @throws org.apache.commons.imaging.ImageReadException in the event of an
   * unhandled exception reading an image
   */
  public static void main(String[] args) throws IOException, ImageReadException {
    File input = new File(args[0]);
    Date date = new Date();
    SimpleDateFormat sdFormat
      = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
    sdFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    System.out.format("Processing image from %s%n", input.getName());
    System.out.format("Date/time of test: %s (UTC)%n", sdFormat.format(date));
    System.out.println("");

    long time0, time1;

    // -------------------------------------------------------------
    // Load the specified file to obtain sample data for processing.
    time0 = System.currentTimeMillis();
    BufferedImage bImage = Imaging.getBufferedImage(input);
    time1 = System.currentTimeMillis();
    int width = bImage.getWidth();
    int height = bImage.getHeight();
    int nPixels = width * height;
    int nRows = height; // GVRS API uses row, column as grid coordinates
    int nCols = width;
    System.out.println("Image loaded");
    System.out.format("   Width:                 %7d%n", width);
    System.out.format("   Height:                %7d%n", height);
    report(time0, time1, input, nPixels);

    int[] argb = new int[width * height];
    bImage.getRGB(0, 0, width, height, argb, 0, width);
    bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    bImage.setRGB(0, 0, width, height, argb, 0, width);

    // ---------------------------------------------------------------
    // As a basis for comparison, store the image as a PNG
    // and report the time required to do so.
    File refPNG = new File("ReferenceImage.png");
    if (refPNG.exists()) {
      refPNG.delete();
    }
    time0 = System.currentTimeMillis();
    ImageIO.write(bImage, "PNG", refPNG);
    time1 = System.currentTimeMillis();
    System.out.println("ImageIO writing PNG");
    report(time0, time1, refPNG, nPixels);

    // ---------------------------------------------------------------
    File refJPEG = new File("ReferenceImage.jpg");
    if (refJPEG.exists()) {
      refJPEG.delete();
    }
    time0 = System.currentTimeMillis();
    ImageIO.write(bImage, "JPEG", refJPEG);
    time1 = System.currentTimeMillis();
    System.out.println("ImageIO writing JPEG");
    report(time0, time1, refJPEG, nPixels);

    // Note:
    //   In the following code blocks, there are references to the GvrsFile
    //   summarize() method.  These are commented out because they would
    //   interfere with timing measurements and also because they produce
    //   considerable output text which would clutter the report.
    //   Note also that calls to flush() are not normally required since
    //   GvrsFile performs a flush as part of its close() operation.
    // ---------------------------------------------------------------
    GvrsFileSpecification gvrsFileSpec;

    // The first test stores the specified data in an uncompressed format.
    // This is the fastest option for processing pixel data and is recommended
    // for high-performance processing.     
    System.out.println("Storing pixels as integers in uncompressed GVRS file");
    gvrsFileSpec = new GvrsFileSpecification(nRows, nCols, 200, 200);
    GvrsElementSpecification pSpec = new GvrsElementSpecificationInt("pixel");
    gvrsFileSpec.addElementSpecification(pSpec);
    File output0 = new File("IntPixelNoComp.gvrs");
    time0 = System.currentTimeMillis();
    try (GvrsFile gvrs = new GvrsFile(output0, gvrsFileSpec)) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsElement pixel = gvrs.getElement("pixel");
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          int index = iRow * nCols + iCol;
          pixel.writeValueInt(iRow, iCol, argb[index]);
        }
      }
    }
    time1 = System.currentTimeMillis();
    report(time0, time1, output0, nPixels);

    // --------------------------------------------------------------- 
    // Store the pixels in compressed format, but do not make any special
    // processing to improve the results. In most cases, compression using
    // this approach will not yield a substantial saving in storage.
    System.out.println("Storing pixels as integers in compressed GVRS file");
    File output1 = new File("IntPixelComp.gvrs");
    gvrsFileSpec.setDataCompressionEnabled(true);
    LsCodecUtility.addLsopToSpecification(gvrsFileSpec, true);
    time0 = System.currentTimeMillis();
    try (GvrsFile gvrs = new GvrsFile(output1, gvrsFileSpec)) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsElement pixel = gvrs.getElement("pixel");
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          int index = iRow * nCols + iCol;
          pixel.writeValueInt(iRow, iCol, argb[index]);
        }
      }
      // gvrs.flush();
      // gvrs.summarize(System.out, true);
    }
    time1 = System.currentTimeMillis();
    report(time0, time1, output1, nPixels);

    // --------------------------------------------------------------- 
    // Separate the pixels into separate RGB components, store each component
    // in a separate GVRS Element. This approach should improve compression
    // ratios.
    System.out.println("Storing RGB components in compressed GVRS file");
    gvrsFileSpec = new GvrsFileSpecification(nRows, nCols, 200, 200);
    gvrsFileSpec.setDataCompressionEnabled(true);
    LsCodecUtility.addLsopToSpecification(gvrsFileSpec, true);
    gvrsFileSpec.addElementSpecification(new GvrsElementSpecificationInt("r"));
    gvrsFileSpec.addElementSpecification(new GvrsElementSpecificationInt("g"));
    gvrsFileSpec.addElementSpecification(new GvrsElementSpecificationInt("b"));
    File output2 = new File("PixelsCompRGB.gvrs");
    gvrsFileSpec.setDataCompressionEnabled(true);
    time0 = System.currentTimeMillis();
    try (GvrsFile gvrs = new GvrsFile(output2, gvrsFileSpec)) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsElement rElem = gvrs.getElement("r");
      GvrsElement gElem = gvrs.getElement("g");
      GvrsElement bElem = gvrs.getElement("b");
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          int rgb = argb[iRow * nCols + iCol];
          int r = (rgb >> 16) & 0xff;
          int g = (rgb >> 8) & 0xff;
          int b = rgb & 0xff;
          rElem.writeValueInt(iRow, iCol, r);
          gElem.writeValueInt(iRow, iCol, g);
          bElem.writeValueInt(iRow, iCol, b);
        }
      }
//       gvrs.flush();
//       gvrs.summarize(System.out, true);
    }
    time1 = System.currentTimeMillis();
    report(time0, time1, output2, nPixels);

    // --------------------------------------------------------------- 
    // Convert RGB color values to the YCoCg-R color space before storage.
    // For photographic images, this approach should further reduce storage
    // size.  For charts, graphs, line drawings, and other such graphic art
    // this approach will usually not produce a gain and sometimes degrades
    // compression.
    System.out.println("Storing YCoCg-R components in compressed GVRS file");
    gvrsFileSpec = new GvrsFileSpecification(nRows, nCols, 200, 200);
    gvrsFileSpec.setDataCompressionEnabled(true);
    LsCodecUtility.addLsopToSpecification(gvrsFileSpec, true);
    gvrsFileSpec.addElementSpecification(new GvrsElementSpecificationInt("Y"));
    gvrsFileSpec.addElementSpecification(new GvrsElementSpecificationInt("Co"));
    gvrsFileSpec.addElementSpecification(new GvrsElementSpecificationInt("Cg"));
    File output3 = new File("PixelsCompYCoCg.gvrs");
    time0 = System.currentTimeMillis();
    try (GvrsFile gvrs = new GvrsFile(output3, gvrsFileSpec)) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsElement YElem = gvrs.getElement("Y");
      GvrsElement CoElem = gvrs.getElement("Co");
      GvrsElement CgElem = gvrs.getElement("Cg");
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          int rgb = argb[iRow * nCols + iCol];
          int r = (rgb >> 16) & 0xff;
          int g = (rgb >> 8) & 0xff;
          int b = rgb & 0xff;
          int Co = r - b;
          int tmp = b + (Co >> 1);  // Co>>1 is equivalent to Co/2
          int Cg = g - tmp;
          int Y = tmp + (Cg >> 1);
          YElem.writeValueInt(iRow, iCol, Y);
          CoElem.writeValueInt(iRow, iCol, Co);
          CgElem.writeValueInt(iRow, iCol, Cg);
        }
      }
      //gvrs.flush();
      //gvrs.summarize(System.out, true);
    }
    time1 = System.currentTimeMillis();
    report(time0, time1, output3, nPixels);

    // ---------------------------------------------------------------     
    // Finally, test the time required to load a YCoCg image
    // Then write the results to a JPEG file for inspection.
    // This test code also illustrates how the YCoCg values may be
    // mapped back to RGB.
    System.out.println("Testing time to read the YCoCg compressed file");
    time0 = System.currentTimeMillis();
    try (GvrsFile gvrs = new GvrsFile(output3, "r")) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsElement YElem = gvrs.getElement("Y");
      GvrsElement CoElem = gvrs.getElement("Co");
      GvrsElement CgElem = gvrs.getElement("Cg");
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          int Y = YElem.readValueInt(iRow, iCol);
          int Co = CoElem.readValueInt(iRow, iCol);
          int Cg = CgElem.readValueInt(iRow, iCol);
          int tmp = Y - (Cg >> 1);
          int g = Cg + tmp;
          int b = tmp - (Co >> 1);
          int r = b + Co;
          argb[iRow * nCols + iCol] = (((0xff00 | r) << 8 | g) << 8) | b;
        }
      }
    }
    time1 = System.currentTimeMillis();
    report(time0, time1, output3, nPixels);

    File resultsJPEG = new File("ResultsForInspection.jpg");
    if (resultsJPEG.exists()) {
      resultsJPEG.delete();
    }
    bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    bImage.setRGB(0, 0, width, height, argb, 0, width);

    time0 = System.currentTimeMillis();
    ImageIO.write(bImage, "JPEG", resultsJPEG);
    time1 = System.currentTimeMillis();
    System.out.println("ImageIO writing JPEG");
  }

  /**
   * A general utility for reporting the storage size and processing time.
   *
   * @param time0 the start time for processing, in milliseconds
   * @param time1 the end time for processing, in milliseconds
   * @param file the GVRS output file
   * @param nPixel the number of pixels in the source image.
   */
  private static void report(long time0, long time1, File file, int nPixel) {
    double processingTimeS = (time1 - time0) / 1000.0;
    long fileSize = file.length();
    double bitsPerPixel = (fileSize * 8.0) / nPixel;
    double sizeMB = fileSize / (1024.0 * 1024.0);
    System.out.format("   File name:                  %s%n", file.getName());
    System.out.format("   Time to process (s):   %10.3f%n", processingTimeS);
    System.out.format("   Bits per pixel:        %9.2f%n", bitsPerPixel);
    System.out.format("   File Size (MB):        %8.1f%n", sizeMB);
    System.out.println("");
  }

}
