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

/**
 * Defines a color palette record based on the RGB model (sRGB model).
 * <p>
 * This class interpolates RGB values using linear interpolation.
 */
public class ColorPaletteRecordRGB extends ColorPaletteRecord {

  final Color rgb0;
  final Color rgb1;
  final int r0;
  final int g0;
  final int b0;
  final int r1;
  final int g1;
  final int b1;

  final double deltaR;
  final double deltaG;
  final double deltaB;

  /**
   * Constructs a record for the specified ranges and selections of color
   * using the RGB model.
   *
   * @param range0 the minimum for the record's range of values
   * @param range1 the maximum for the record's range of values
   * @param rgb0 the color values associated with range0
   * @param rgb1 the color values associated with range1
   */
  public ColorPaletteRecordRGB(double range0, double range1, Color rgb0, Color rgb1) {
    super(range0, range1);
    this.rgb0 = rgb0;
    this.rgb1 = rgb1;
    r0 = rgb0.getRed();
    g0 = rgb0.getGreen();
    b0 = rgb0.getBlue();
    r1 = rgb1.getRed();
    g1 = rgb1.getGreen();
    b1 = rgb1.getBlue();
    deltaR = r1 - r0;
    deltaG = g1 - g0;
    deltaB = b1 - b0;
  }

  @Override
  ColorPaletteRecord copyWithModifiedRange(double minRangeSpec, double maxRangeSpec){
       ColorPaletteRecord record = new ColorPaletteRecordRGB(
         minRangeSpec, maxRangeSpec, rgb0, rgb1);
       record.setLabel(label);
       return  record;
  }
  
  @Override
  public int getArgb(double z) {
    double t = (z - range0) / (range1 - range0);
    if (t < 0) {
      t = 0;
    } else if (t > 1) {
      t = 1;
    }
    int r = (int) (deltaR * t + 0.5) + r0;
    int g = (int) (deltaG * t + 0.5) + g0;
    int b = (int) (deltaB * t + 0.5) + b0;
    return 0xff000000 | (r << 16) | (g << 8) | b;
  }

  @Override
  public Color getColor(double z) {
    return new Color(getArgb(z));
  }

  @Override
  public Color getBaseColor() {
    return rgb0;
  }

  @Override
  public Color getTopColor() {
    return rgb1;
  }
}
