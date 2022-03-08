/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2022  Gary W. Lucas.

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
 * 03/2022  G. Lucas     Created
 *
 * Notes:
 *
 *  Unfortunately, the CPT specification is messy.  It has evolved over
 *  time and been adopted by multiple projects who hava modified it
 *  to suit their needs.  Thus, the parsing rules are also complicated.
 * <p>
 * The logic for this class was developed using examples from the
 * Generic Mapping Tools (GMT) project at
 * https://github.com/GenericMappingTools/gmt under the directory
 * gmt-master/share/cpt.   Also, from the cpycmap.m project at
 * https://github.com/kakearney/cptcmap-pkg,
 * <p>
 * At this time, the ColorPaletteTable class does not support CYCLIC
 * palettes.
 * <p>
 * A discussion of the CPT file format can be found at the
 * "CPT Designer" web page in an article by Tim Makins and MapAbility.com
 * https://www.mapability.com/cptd/help/hs70.htm
 *  
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.utils.palette;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.gridfour.util.palette.ColorPaletteRecord;
import org.gridfour.util.palette.ColorPaletteTable;
import org.gridfour.util.palette.ColorPaletteTableReader;

/**
 * A simple demo to parse and plot the content of one or more
 * Color Palette Table (CPT) files.
 */
public class ColorPaletteTableRender {

  /**
   * @param args Command-line arguments listing file or folder to be processed
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println(
        "Provide file or folder for processing as command-line arguments");
      System.err.println("Usage <file or folder>  [optional output file path]");
      System.exit(-1);
    }

    // ----------------------------------------------------------------
    // Based on the user input, obtain an array of Java File references
    // to CPT files.  Parse files and render color bars based on their content.
    // Storing the resulting image in either the default output file or the
    // user-specified output.   Label the color bars with file name and either
    //      n  -- normalized
    //      c  -- categorical
    //      h  -- hinge specified
    File[] files = loadFileLists(args[0]);
    if (files.length == 0) {
      System.exit(0);
    }

    String outputImagePath = "ColorPaletteTableRender.png";
    if (args.length > 1) {
      outputImagePath = args[1];
    }
    System.out.println("Writing image output to " + outputImagePath);

    File outputImageFile = new File(outputImagePath);
    ColorPaletteTableRender renderer = new ColorPaletteTableRender();
    renderer.renderAndWriteImageFIle(files, outputImageFile);
  }

  /**
   * Load a list of Color Palette Image files from the specified path.
   * If the path is an individual file, then that value will be returned
   * irrespective of whether it has a ".cpt" file extension or not.
   * If the path is for a folder, then the method will return an
   * array of references to all files within that folder that have the
   * extension ".cpt".
   *
   * @param sourceFilePath a path to the file or folder giving source
   * Color Palette Table specifications.
   * @return a valid, potentially zero-sized array.
   */
  private static File[] loadFileLists(String sourceFilePath) {
    File file = new File(sourceFilePath);
    if (file.isFile()) {
      return new File[]{file};
    }

    if (!file.isDirectory()) {
      return new File[0];
    }

    // It's a folder. Look for all files ending in ".cpt"
    File[] files = file.listFiles();
    int nFound = 0;
    File[] found = new File[files.length];
    for (File f : files) {
      String name = f.getName();
      if (name.length() > 5 && name.toLowerCase().endsWith(".cpt")) {
        found[nFound++] = f;
      }
    }

    return Arrays.copyOf(found, nFound);
  }

  /**
   * Create and image showing the content of color bars and writes
   * the result to an image file.
   *
   * @param files an array of one or more files
   * @param outputImageFile specification for an output file to write the image.
   */
  private void renderAndWriteImageFIle(File[] files, File outputImageFile) {
    int hBar = 15;
    int wBar = 185;
    int h = 10 + files.length * (hBar + 10) + 10;
    int w = wBar + 200;
    BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    int[] argb = new int[h * w];
    Arrays.fill(argb, 0xffffffff);

    boolean[] success = new boolean[files.length];
    boolean[] normalized = new boolean[files.length];
    boolean[] hinged = new boolean[files.length];
    boolean[] categorical = new boolean[files.length];

    for (int k = 0; k < files.length; k++) {
      File f = files[k];
      String name = f.getName();
      System.out.println(name);
      try {
        ColorPaletteTableReader cptReader = new ColorPaletteTableReader();
        ColorPaletteTable cpt = cptReader.read(f);
        double zMin = cpt.getRangeMin();
        double zMax = cpt.getRangeMax();
        success[k] = true;
        normalized[k] = cpt.isNormalized();
        hinged[k] = cpt.isHinged();
        int row0 = 10 + k * (hBar + 10);
        int row1 = row0 + hBar;

        boolean categoricalPalette = cpt.isCategoricalPalette();
        categorical[k] = categoricalPalette;
        if (!categoricalPalette) {
          for (int col = 0; col <= wBar; col++) {
            double z = (zMax - zMin) * col / (double) wBar + zMin;
            int p = cpt.getArgb(z);
            for (int row = row0; row < row1; row++) {
              int index = row * w + col + 10;
              argb[index] = p;
            }
          }
        } else {
          List<ColorPaletteRecord> records = cpt.getRecords();
          int n = records.size();
          for (int col = 0; col <= wBar; col++) {
            int recordIndex = (int) ((n - 1) * col / (double) wBar);
            int p = records.get(recordIndex).getBaseColor().getRGB();
            for (int row = row0; row < row1; row++) {
              int index = row * w + col + 10;
              argb[index] = p;
            }
          }
        }

        // Some example uses of the accessor methods for testing, debugging
        //cpt.getArgb(Double.POSITIVE_INFINITY);
        //cpt.getArgb(Double.NaN);
        //double z = cpt.getRangeMin() - 1.0;
        //int test = cpt.getArgb(z);
        //z = cpt.getRangeMax() + 1;
        //test = cpt.getArgb(z);
      } catch (IOException ioex) {
        System.out.println("    " + ioex.getMessage());
      }
    }

    bImage.setRGB(0, 0, w, h, argb, 0, w);
    Graphics2D g2d = bImage.createGraphics();
    g2d.setFont(new Font("Arial", Font.BOLD, 12));
    g2d.setColor(Color.black);
    for (int i = 0; i < files.length; i++) {
      String name = files[i].getName();
      if (normalized[i] || hinged[i]) {
        name += " (";
        if (normalized[i]) {
          name += "n";
        }
        if (hinged[i]) {
          name += "h";
        }
        if (categorical[i]) {
          name += "c";
        }
        name += ")";
      }
      int y = 10 + i * (hBar + 10);
      g2d.drawString(name, 10 + wBar + 10, y + hBar - 4);
      if (success[i]) {
        g2d.drawRect(10, y, wBar, hBar);
      }
    }

    try {
      ImageIO.write(bImage, "PNG", outputImageFile);
    } catch (IOException ioex) {
      System.err.println("Error processing " + outputImageFile);
      ioex.printStackTrace(System.out);
    }

  }

}
