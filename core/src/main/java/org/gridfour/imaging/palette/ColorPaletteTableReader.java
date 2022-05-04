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
 *  Unfortunately, the CPT specification is messy.  It has evolved over
 *  time and been adopted by multiple projects who hava modified it
 *  to suit their needs.  Thus, the parsing rules are also complicated.
 *
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.imaging.palette;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides methods for reading specifications from a
 * Color Palette Table file (usually, files with the extension &#46;cpt).
 * <p>
 * Unfortunately, the CPT specification is messy. It has evolved over
 * time and been adopted by multiple projects who modified it
 * to suit their needs. Thus, the parsing rules are also complicated.
 * <p>
 * The logic for this class was developed using examples from the
 * Generic Mapping Tools (GMT) project at
 * https://github.com/GenericMappingTools/gmt under the directory
 * gmt-master/share/cpt.   Also, from the cpycmap.m project at
 * https://github.com/kakearney/cptcmap-pkg,
 *
 * <p>
 * At this time, the ColorPaletteTable class does not support CYCLIC
 * palettes.
 * <p>
 * A discussion of the CPT file format can be found at the
 * "CPT Designer" web page in an article by Tim Makins and MapAbility.com
 * https://www.mapability.com/cptd/help/hs70.htm
 */
public class ColorPaletteTableReader {

  private enum ColorModel {
    RGB,
    HSV
  }


  private Color background = Color.white;
  private Color foreground = Color.black;
  private Color colorForNull;

  private boolean hingeSpecified;
  private double hingeValue;

  private boolean rangeSpecified;
  private double range0;
  private double range1;

  private final List<ColorPaletteRecord> records = new ArrayList<>();

  ColorModel colorModel = ColorModel.RGB;
  int lineIndex;

  Pattern assignmentPattern
    = Pattern.compile("\\#.\\s*([a-zA-Z0-9_]+)\\s*=\\s*(\\S+)");

  Pattern hardHingePattern
    = Pattern.compile("\\#.\\s*[Hh][Aa][Rr][Dd]_[Hh][Ii][Nn][Gg][Ee]");

  Pattern softHingePattern
    = Pattern.compile("\\#.\\s*[Ss][Oo][Ff][Tt]_[Hh][Ii][Nn][Gg][Ee]");

  ColorNameParser nameParser = new ColorNameParser();

  /**
   * Constructs a ColorPaletteTable instance using the specifications obtained
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
         Reader in = new InputStreamReader(fins, StandardCharsets.ISO_8859_1);
         BufferedReader reader = new BufferedReader(in)){
      return read(reader);
    }
  }

  /**
   * Constructs a ColorPaletteTable instance with specifications obtained
   * from the specified input stream.
   *
   * @param ins a valid input stream reference
   * @return a valid instance populated from the stream
   * @throws IOException in the event of an unsuccessful I/O operation
   * or specification-format error
   */
  public ColorPaletteTable read(InputStream ins) throws IOException {
    InputStreamReader insReader = new InputStreamReader(ins, StandardCharsets.UTF_8);
    BufferedReader reader = new BufferedReader(insReader);
    return read(reader);
  }

  ColorPaletteTable read(BufferedReader reader) throws IOException {
    // In order to enable this class to be reused to read multiple
    // files, reeset state variables.
    lineIndex = 0;
    colorModel = ColorModel.RGB;
    background = Color.white;
    foreground = Color.black;
    colorForNull = null;
    hingeSpecified = false;
    hingeValue = 0;
    rangeSpecified = false;
    range0 = 0;
    range1 = 0;
    records.clear();

    // read each line of the file and parse as appropriate
    String line;
    while ((line = reader.readLine()) != null) {
      lineIndex++;
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c == '#') {
          // it's a comment
          processComment(line);
          break;
        } else if (!Character.isWhitespace(c)) {
          processSpecification(line);
          break;
        }
      }
    }

    if (records.isEmpty()) {
      throw new IOException("Empty specification");
    }

    // Records are always sorted by range, but just in case
    // we perform a sort.
    Collections.sort(records);

    boolean normalizationTestFlag = testForNormalization(records);

    if (rangeSpecified) {
      if (!normalizationTestFlag) {
        throw new IOException(
          "Range specification not value for non-nomalized color table");
      }
    } else {
      ColorPaletteRecord record0 = records.get(0);
      ColorPaletteRecord record1 = records.get(records.size() - 1);
      range0 = record0.range0;
      range1 = record1.range1;
    }

    return new ColorPaletteTable(
      records, background, foreground, colorForNull,
      hingeSpecified, hingeValue, normalizationTestFlag, range0, range1);
  }

  private boolean testForNormalization(List<ColorPaletteRecord> records) {
    ColorPaletteRecord record0 = records.get(0);
    ColorPaletteRecord record1 = records.get(records.size() - 1);
    if (record0.range0 == -1 && record1.range1 == 1 && hingeSpecified) {
      // this may be a hinged palette.
      // we need to verify uniform coverage of the range of values.
      return testForContinuity(records);
    }

    if (record0.range0 == 0 && record1.range1 == 1.0) {
      // the records meet the [0,1] range criteria.
      // we need to verify uniform coverage of the range of values.
      return testForContinuity(records);
    }
    return false;
  }

  private boolean testForContinuity(List<ColorPaletteRecord> records) {
    ColorPaletteRecord record0 = records.get(0);
    ColorPaletteRecord record1;
    for (int i = 1; i < records.size(); i++) {
      record1 = records.get(i);
      if (record0.range1 != record1.range0) {
        return false;
      }
      record0 = record1;
    }
    return true;
  }

  void processComment(String line) throws IOException {
    // Pretty much anything can go in a comment.
    // The only kind of line we're interested in is an assignment
    // However, there is one special syntax (of course there is)
    // for HARD_HINGE or SOFT_HINGE.  At this time, I have not been able
    // to find documentation for what the difference is between these
    // two specifications, so they are treated identically.
    Matcher matcher = hardHingePattern.matcher(line);
    if (matcher.matches()) {
      hingeSpecified = true;
      hingeValue = 0;
      return;
    }

    matcher = softHingePattern.matcher(line);
    if (matcher.matches()) {
      hingeSpecified = true;
      hingeValue = 0;
      return;
    }

    matcher = assignmentPattern.matcher(line);
    if (!matcher.matches()) {
      return;
    }

    String key = matcher.group(1).toUpperCase();
    String value = matcher.group(2).toUpperCase();

    if ("COLOR_MODEL".equals(key)) {
      switch (value) {
        case "RGB":
          colorModel = ColorModel.RGB;
          break;
        case "HSV":
          colorModel = ColorModel.HSV;
          break;
        default:
          throw new IOException("Unsupported color model " + value);
      }
    } else if ("HINGE".equals(key)) {
      hingeSpecified = true;
      try {
        hingeValue = Double.parseDouble(value);
      } catch (NumberFormatException nex) {
        throw new IOException("Invalid HINGE specification");
      }
    } else if ("RANGE".equals(key)) {
      // Range could be two numbers separated by space or /
      int i = line.indexOf('=');
      String s = line.substring(i + 1, line.length()).trim();
      String[] a = s.split("[\\s/]+");
      if (a.length != 2 || a[1].isEmpty()) {
        throw new IOException("Invalid RANGE specification");
      }
      if (a.length != 2) {
        throw new IOException("Invalid RANGE specification");
      }

      try {
        range0 = Double.parseDouble(a[0]);
        range1 = Double.parseDouble(a[1]);
        rangeSpecified = true;
      } catch (NumberFormatException nex) {
        throw new IOException("Invalid RANGE specification");
      }
    }
  }

  void processSpecification(String line) throws IOException {
    // Even with a fancier regular expression, the Java String.split("[\\s]+")
    // method wasn't quite working out here, so I implemented logic
    // to do this by hand
    //    Note that a line may include special syntax introduced by a
    // semi-colon to specify a label for the color range
    int lineLength = line.length();
    StringBuilder sb = new StringBuilder();
    int nString = 0;
    String[] a = new String[10];
    String label = null;
    for (int i = 0; i < lineLength; i++) {
      char c = line.charAt(i);
      if (c == ';') {
        // protect against pathological case where there is a semi-colon,
        // but no label
        if (i < lineLength - 1) {
          label = line.substring(i + 1, lineLength).trim();
        }
        // Any text in the builder will be handled post-loop
        break;
      } else if (Character.isWhitespace(c)) {
        if (sb.length() > 0) {
          if (nString < 8) {
            a[nString++] = sb.toString();
          }
          sb.setLength(0);
        }
      } else {
        sb.append(c);
      }
    }
    if (sb.length() > 0 && nString < 8) {
      a[nString++] = sb.toString();
    }

    if (a.length == 0) {
      // empty line
      return;
    }

    String name = "line " + lineIndex;

    char c = a[0].charAt(0);
    if (Character.isLowerCase(c)) {
      c = Character.toUpperCase(c);
    }
    if (c == 'B' || c == 'F' || c == 'N') {
      Color color = null;
      if (nString == 2) {
        color = parseSingleColorString(name, a[1]);
      } else if (nString == 4) {
        color = parseColor(name, a, 1);
      }
      if (c == 'B') {
        background = color;
      } else if (c == 'F') {
        foreground = color;
      } else {
        colorForNull = color;
      }
      return;
    }

    double v0, v1;
    Color rgb0, rgb1;

    ColorPaletteRecord record;
    if (nString == 2) {
      // This would be a case where a color is assigned to
      // single values rather than a range of values.
      // The ColorPaletteRecord classes support this imperfectly,
      // but this should suffice until improvements are available in the future
      // This allows for syntax that might include named colors
      try {
        v0 = Double.parseDouble(a[0]);
      } catch (NumberFormatException nex) {
        throw new IOException("Misformed value on line " + lineIndex);
      }
      if (colorModel == ColorModel.RGB) {
        rgb0 = parseSingleColorString(name, a[1]);
        record = new ColorPaletteRecordRGB(v0, v0, rgb0, rgb0);
      } else {
        double[] hsv0 = parseSingleHsvString(name, a[1]);
        record = new ColorPaletteRecordHSV(v0, v0, hsv0, hsv0);
      }
    } else if (nString == 4) {
      // This allows for syntax that might include named colors
      try {
        v0 = Double.parseDouble(a[0]);
        v1 = Double.parseDouble(a[2]);
      } catch (NumberFormatException nex) {
        throw new IOException("Misformed range values on line " + lineIndex);
      }
      if (colorModel == ColorModel.RGB) {
        rgb0 = parseSingleColorString(name, a[1]);
        rgb1 = parseSingleColorString(name, a[3]);
        record = new ColorPaletteRecordRGB(v0, v1, rgb0, rgb1);
      } else {
        double[] hsv0 = parseSingleHsvString(name, a[1]);
        double[] hsv1 = parseSingleHsvString(name, a[3]);
        record = new ColorPaletteRecordHSV(v0, v1, hsv0, hsv1);
      }
    } else if (nString == 8) {
      try {
        v0 = Double.parseDouble(a[0]);
        v1 = Double.parseDouble(a[4]);
      } catch (NumberFormatException nex) {
        throw new IOException("Misformed range values on line " + lineIndex);
      }

      if (colorModel == ColorModel.RGB) {
        rgb0 = parseRGB(name, a, 1);
        rgb1 = parseRGB(name, a, 5);
        record = new ColorPaletteRecordRGB(v0, v1, rgb0, rgb1);
      } else {
        double[] hsv0 = parseHSV(name, a, 1);
        double[] hsv1 = parseHSV(name, a, 5);
        record = new ColorPaletteRecordHSV(v0, v1, hsv0, hsv1);
      }
    } else {
      throw new IOException("Unsupported syntax on line " + lineIndex
        + ", found " + nString + " parameters where expecting either 4 or 8");
    }
    record.setLabel(label);
    records.add(record);
  }

  private Color parseRGB(String name, String[] strings, int index) throws IOException {
    // allegedly, the CPT specification allows single-values and also
    // named colors.  Hopefully application developers have the good sense
    // to avoid these problematic specifications.
    // If we encounter these in the future, it may be necessary to
    // upgrade this logic.
    int n = strings.length - index;
    if (n == 1) {
      // it may be a named color
      Color color = nameParser.parse(strings[index]);
      if (color != null) {
        return color;
      }

      int gray = -1;
      try {
        gray = Integer.parseInt(strings[index]);
      } catch (NumberFormatException nex) {
        gray = -1;
      }

      if (0 <= gray && gray <= 255) {
        return new Color(gray, gray, gray);
      }
      throw new IOException("Error in " + name
        + " unable to resolve single argument where expecting RGB");
    }

    if (n < 3) {
      throw new IOException("Error in " + name
        + " insufficient parameters where 3 expected for RGB");
    }

    int r = parsePartRGB(name, strings, index);
    int g = parsePartRGB(name, strings, index + 1);
    int b = parsePartRGB(name, strings, index + 2);

    return new Color(r, g, b);
  }

  private double[] parseHSV(String name, String[] strings, int index) throws IOException {
    int n = strings.length - index;
    if (n < 3) {
      throw new IOException("Error in " + name
        + " insufficient parameters where 3 expected");
    }
    double[] p = new double[3];

    p[0] = parsePart(name, strings, index);
    p[1] = parsePart(name, strings, index + 1);
    p[2] = parsePart(name, strings, index + 2);
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

  private int parsePartRGB(String name, String[] strings, int index) throws IOException {
    double d = parsePart(name, strings, index);
    if (d >= 0 && d < 256) {
      return (int) d;
    }
    throw new IOException("RGB specification for " + name + " is not in range [0..255]");
  }

  private double parsePart(String name, String[] strings, int index) throws IOException {
    try {
      if (strings[index] == null) {
        System.out.println("ouch");
      }
      return Double.parseDouble(strings[index]);
    } catch (NumberFormatException nex) {
      throw new IOException("Bad specification for " + name + ": " + nex.getMessage(), nex);
    }
  }

  private Color parseColor(String name, String[] strings, int index) throws IOException {
    if (colorModel == ColorModel.RGB) {
      return parseRGB(name, strings, index);
    } else {
      // Java's HSB color model is similar to the CPT's HSV model
      // scale the source H value (p[0]) down to range 0 to 1 for Java
      double[] p = parseHSV(name, strings, index);
      float hue = (float) (p[0] / 360.0);
      float sat = (float) p[1];
      float brt = (float) p[2];
      int argb = Color.HSBtoRGB(hue, sat, brt) | 0xff000000;
      return new Color(argb);
    }
  }

  private Color parseSingleColorString(String name, String string) throws IOException {
    String[] s = null;
    if (string.indexOf('/') > 0) {
      s = string.split("/");
    } else if (string.indexOf('-') > 0) {
      s = string.split("-");
    }
    if (s != null) {
      if (s.length != 3) {
        throw new IOException(
          "Illegal syntax where color specification expected for " + name);
      }
      return parseColor(name, s, 0);
    }

    if (Character.isAlphabetic(string.charAt(0))) {
      Color test = nameParser.parse(string);
      if (test == null) {
        throw new IOException("Unrecognized color value \"" + string + "\" at " + name);
      }
      return test;
    }

    if (colorModel == ColorModel.RGB) {
      try {
        int gray = Integer.parseInt(string);
        return new Color(gray, gray, gray);
      } catch (NumberFormatException nex) {
        throw new IOException("Bad value where integer gray value expected at " + name);
      }
    }
    throw new IOException("Gray tone not supported for non-RGB color model at " + name);
  }

  private double[] parseSingleHsvString(String name, String string) throws IOException {
    String[] s = null;
    if (string.indexOf('/') > 0) {
      s = string.split("/");
    } else if (string.indexOf('-') > 0) {
      s = string.split("-");
    }
    if (s != null) {
      if (s.length != 3) {
        throw new IOException(
          "Illegal syntax where color specification expected for " + name);
      }
      return parseHSV(name, s, 0);

    }

    if (Character.isAlphabetic(string.charAt(0))) {
      Color test = nameParser.parse(string);
      if (test == null) {
        throw new IOException("Unrecognized color value \"" + string + "\" at " + name);
      }
      int r = test.getRed();
      int g = test.getGreen();
      int b = test.getBlue();
      float[] hsbvals = new float[3];
      Color.RGBtoHSB(r, g, b, hsbvals);
      double[] hsv = new double[3];
      hsv[0] = hsbvals[0];
      hsv[1] = hsbvals[1];
      hsv[2] = hsbvals[2];
      return hsv;
    }

    throw new IOException(
      "Value not supported for HSV color model at " + name);
  }

}
