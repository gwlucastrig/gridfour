/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2021  Gary W. Lucas.

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
 * 01/2022  G. Lucas     Created
 *
 * To see a truly clever way to obtain set of color specifications,
 * please visit an implementation by Larry Ogrodnek at
 *
 *  http://www.java2s.com/Code/Java/2D-Graphics-GUI/Mapcolorsintonamesandviceversa.htm
 * -----------------------------------------------------------------------
 */
package org.gridfour.util.palette;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Provides a way of parsing color names and resolving them to a
 * color instance. Valid color names are based on the specifications
 * in Java's Color class.
 * <p>
 * In general, the use of named colors in palette files is discouraged
 * due to their inherent English-language bias. This class in intended to
 * support those cases where a source palette file uses named colors.
 */
public class ColorPaletteNameParser {

  private final HashMap<String, Color> colorMap = new HashMap<>();
  private boolean resourceLoaded = false;

  public ColorPaletteNameParser() {
    colorMap.put("white", Color.white);
    colorMap.put("black", Color.black);
    colorMap.put("gray", Color.gray);
    colorMap.put("lightgray", Color.LIGHT_GRAY);
    colorMap.put("light_gray", Color.LIGHT_GRAY);
    colorMap.put("darkgray", Color.DARK_GRAY);
    colorMap.put("dark_gray", Color.DARK_GRAY);
    colorMap.put("red", Color.red);
    colorMap.put("pink", Color.pink);
    colorMap.put("orange", Color.orange);
    colorMap.put("yellow", Color.yellow);
    colorMap.put("green", Color.green);
    colorMap.put("magenta", Color.magenta);
    colorMap.put("cyan", Color.cyan);
    colorMap.put("blue", Color.blue);
  }

  /**
   * Compares the specified a name to recognized colors and, if available,
   * resolve it into a valid instance.
   *
   * @param name a valid string
   * @return if successful, a valid string; otherwise, a null.
   */
  public Color parse(String name) {
    Color color = null;
    if (name != null) {
      String key = name.trim().toLowerCase();
      color = colorMap.get(key);
      if (color == null) {
        if (!resourceLoaded) {
          loadResource();
          color = colorMap.get(key);
        }
      }
    }
    return color;
  }

  private void loadResource() {
    resourceLoaded = true;
    try (InputStream ins = getClass().getResourceAsStream("rgb.txt");
      BufferedInputStream bins = new BufferedInputStream(ins)) {
      int c;
      int[] rgb = new int[3];
      StringBuilder sb = new StringBuilder();
      while (true) {
        int v = 0;
        for (int i = 0; i < 3; i++) {
          // read stream skipping spaces until finding a digit
          c = bins.read();
          while (c == 32) {
            c = bins.read();
          }
          if (c == -1) {
            return; // End of File, we're done
          }
          
          v = c - 48; // 48 is ASCII code for zero
          c = bins.read();
          while (48 <= c && c <= 57) {
            v = v * 10 + c - 48;
            c = bins.read();
          }
          rgb[i] = v;
        }
        // read stream skipping tabs until finding a letter
        c = bins.read();
        while (c == '\t') {
          c = bins.read();
        }
        sb.append((char) c);
        c = bins.read();
        while (c != '\n') {
          // The rgb.txt a Unix file and will not include carriage returns
          sb.append((char) c);
          c = bins.read();
        }

        String key = sb.toString().toLowerCase();
        if (!colorMap.containsKey(key)) {
          Color color = new Color(rgb[0], rgb[1], rgb[2]);
          colorMap.put(key, color);
        }
        sb.setLength(0);
      }
    } catch (IOException ioex) {
      // Not expected, internal error.  no action required.
    }

  }
}
