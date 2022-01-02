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
import java.util.HashMap;

/**
 * Provides a way of parsing color names and resolving them to a
 * color instance.  Valid color names are based on the specifications
 * in Java's Color class.
 * <p>
 * In general, the use of named colors in palette files is discouraged
 * due to their inherent English-language bias. This class in intended to
 * support those cases where a source palette file uses named colors.
 */
public class ColorPaletteNameParser {

 
  static final HashMap<String, Color> colorMap = new HashMap<>();

  static {
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
   * @param name a valid string
   * @return if successful, a valid string; otherwise, a null.
   */
  public static Color parse(String name){
    Color color = null;
    if(name!=null){
      String key = name.trim().toLowerCase();
      color = colorMap.get(key);
    }
    return color;
  }
}
