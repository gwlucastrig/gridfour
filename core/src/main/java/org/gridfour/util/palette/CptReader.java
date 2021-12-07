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
 * 08/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.util.palette;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods for reading specifications from a
 * Color Palette Table file (usually, files with the extension &#46;cpt).
 */
public class CptReader {

  private enum ColorModel {
    RGB,
    HSV
  }

  private Color background = Color.white;
  private Color foreground = Color.black;
  private Color colorForNull;
 
  ColorModel colorModel = ColorModel.RGB;
  int lineIndex;
  
 

  /**
   * Constructs a ColorPaletteTile with specifications obtained
   * from the specified file reference.
   *
   * @param file a valid input file reference
   * @return a valid instance of a color palette table based on
   * data parsed from the specified file.
   * @throws IOException in the event of an unsuccessful I/O operation
   * or specification-format error
   */
  public ColorPaletteTable read(File file) throws IOException {
    try (FileInputStream fins = new FileInputStream(file);
      BufferedInputStream bins = new BufferedInputStream(fins)) {
      return read(bins);
    }
  }

  /**
   * Constructs a ColorPaletteTile with specifications obtained
   * from the specified input stream.
   *
   * @param ins a valid input stream reference
   * @return a valid instance populated from the stream
   * @throws IOException in the event of an unsuccessful I/O operation
   * or specification-format error
   */
  public ColorPaletteTable read(InputStream ins) throws IOException {
    // reset state variables
    lineIndex = 0;
    colorModel = ColorModel.RGB;
    background = Color.white;
    foreground = Color.black;
    colorForNull = null;
    List<ColorPaletteRecord> records = new ArrayList<>();

    // read each line of the file and parse as appropriate
    List<String> sList = new ArrayList<>();
    while (readStrings(ins, sList) > 0) {
      String s = sList.get(0);
      char c = s.charAt(0);
      if (c == '#') {
        // comment, we are interested in the color specification scheme
        //   the documentation indicates acceptable values are
        //        RGB 
        //        HSV
        //        CMYK  (extra parameters for this one?)
        //   One problem is that I don't yet know the specifics for
        //   the HSV and CMYK and haven't found samples
        s = s.toUpperCase();
        if (s.contains("COLOR_MODEL")) {
          String[] a = s.split("=");
          if (a.length != 2 || a[1].isEmpty()) {
            throw new IOException("Invalid COLOR_MODEL specification");
          }
          String modelName = a[1].trim();
          switch (modelName) {
            case "RGB":
              colorModel = ColorModel.RGB;
              break;
            case "HSV":
              colorModel = ColorModel.HSV;
              break;
            default:
              throw new IOException("Unsupported color model " + a[1]);
          }
        }
      } else if (c == 'B') {
        background = parseColor("B", sList, 1);
      } else if (c == 'F') {
        foreground = parseColor("F", sList, 1);
      } else if (c == 'N') {
        colorForNull = parseColor("N", sList, 1);
      } else {
        if (sList.size() < 8) {
          throw new IOException("Unsupported syntax on line " + lineIndex
            + " where expecting 8 parameters");
        }
        double v0, v1;
        try {
          v0 = Double.parseDouble(sList.get(0));
          v1 = Double.parseDouble(sList.get(4));
        } catch (NumberFormatException nex) {
          throw new IOException("Misformed range values on line " + lineIndex);
        }
        String name = "line " + lineIndex;
        ColorPaletteRecord record;
        if (colorModel == ColorModel.RGB) {
          Color rgb0 = parseRGB(name, sList, 1);
          Color rgb1 = parseRGB(name, sList, 5);
          record = new ColorPaletteRecordRGB(v0, v1, rgb0, rgb1);
        } else {
          double[] hsv0 = parseHSV(name, sList, 1);
          double[] hsv1 = parseHSV(name, sList, 5);
          record = new ColorPaletteRecordHSV(v0, v1, hsv0, hsv1);
        }
        records.add(record);

      }
    }
    if (records.isEmpty()) {
      throw new IOException("Empty specification");
    }
    return new ColorPaletteTable(records, background, foreground ,colorForNull);

  }

  /**
   * Read a row of strings from the file, storing the results in
   * a reusable list. Each call to this routine clears any content
   * that may already be in the list before extracting it from the file.
   *
   * @param sList a list in which the strings will be stored
   * @return the number of strings that were stored in the list;
   * zero at the end of the file.
   * @throws IOException in the event of an unsuccessful I/O operation.
   */
  private int readStrings(InputStream bins, final List<String> sList) throws IOException {
    int c;
    final StringBuilder sb = new StringBuilder();
    sList.clear();
    // read past any leading whitespace characters
    while (true) {
      c = bins.read();
      if (c <= 0) {
        // end of file
        return 0;
      } else if (Character.isWhitespace(c)) {
        if (c == '\n') {
          // blank line
          lineIndex++;
        }
      } else {
        break;
      }
    }

    if (c == '#') {
      // the first character on the line is a comment-introducer
      sb.append((char) c);
      while (true) {
        c = bins.read();
        if (c < 0 || c == '\n') {
          // end of line
          lineIndex++;
          sList.add(sb.toString());
          return 1;
        }
        sb.append((char) c);
      }
    }

    // The line is not a comment.  Assume it is a valid line
    sb.append((char) c);
    while (true) {
      // build the string
      c = bins.read();
      while (c > 0 && !Character.isWhitespace(c)) {
        sb.append((char) c);
        c = bins.read();
      }
      sList.add(sb.toString());
      sb.setLength(0);
      while (c > 0 && c != '\n' && Character.isWhitespace(c)) {
        c = bins.read();
      }
      if (c <= 0 || c == '\n') {
        lineIndex++;
        return sList.size();
      }
      sb.append((char) c);
    }

  }

 
  private Color parseRGB(String name, List<String> sList, int index) throws IOException {
    // allegedly, the CPT specification allows single-values and also
    // named colors.  Hopefully application developers have the good sense
    // to avoid these problematic specifications.
    // If we encounter these in the future, it may be necessary to
    // upgrade this logic.
    int n = sList.size() - index;
    if (n < 3) {
      throw new IOException("Error in " + name
        + " insufficient parameters where 3 expected for RGB");
    }

    int r = parsePartRGB(name, sList, index);
    int g = parsePartRGB(name, sList, index + 1);
    int b = parsePartRGB(name, sList, index + 2);
 
    return new Color(r,g, b);
  }

  private double[] parseHSV(String name, List<String> sList, int index) throws IOException {
    int n = sList.size() - index;
    if (n < 3) {
      throw new IOException("Error in " + name
        + " insufficient parameters where 3 expected");
    }
    double[] p = new double[3];

    p[0] = parsePart(name, sList, index);
    p[1] = parsePart(name, sList, index + 1);
    p[2] = parsePart(name, sList, index + 2);
    if (p[0] < 0 || p[0] > 360) {
      throw new IOException("HSV value for Hue out of range [0..360] for " + name);
    }
    for (int i = 1; i < 3; i++) {
      if (p[i] < 0 || p[i] > 1) {
        throw new IOException("HSV value out of range [0..1] for " + name + ": " + p[i]);
      }
    }
    return p;
  }

  private int parsePartRGB(String name, List<String> sList, int index) throws IOException {
    double d = this.parsePart(name, sList, index);
    if(d>=0 && d<256){
      return (int)d;
    }
     throw new IOException("RGB specification for " + name + " is not in range [0..255]");
  }
  
  private double parsePart(String name, List<String> sList, int index) throws IOException {
    try {
      return Double.parseDouble(sList.get(index));
    } catch (NumberFormatException nex) {
      throw new IOException("Bad specification for " + name + ": " + nex.getMessage(), nex);
    }
  }

  private Color parseColor(String name, List<String> sList, int index) throws IOException {
    if (colorModel == ColorModel.RGB) {
      return  parseRGB(name, sList, index);
    } else {
      // Java's HSB color model is similar to the CPT's HSV model
      // scale the source H value (p[0]) down to range 0 to 1 for Java
      double[] p = parseHSV(name, sList, index);
      float hue = (float) (p[0] / 360.0);
      float sat = (float) p[1];
      float brt = (float) p[2];
      int argb = Color.HSBtoRGB(hue, sat, brt) | 0xff000000;
      return new Color(argb);
    }
  }
}
