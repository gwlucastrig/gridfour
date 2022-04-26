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
package org.gridfour.imaging.palette;

import java.awt.Color;

/**
 * Defines a color palette record based on the HSV model.
 * Note, this model is not to be confused with the Java HSB model.
 * The Java model expects hue in the range 0 to 1, while the
 * HSV model expects hue in the range 0 to 360.
 * <p>
 * Note that the value for hues may be either increasing or decreasing.
 * <p>
 * This class interpolates S and V (saturation and value) parameters
 * independently
 * using linear interpolation. Hues are also interpolated using linear
 * methods, but special rules apply for cases where the specified
 * hue values cross the zero-angle. For example,
 * if hues range from 350 to 10, then the midpoint between the angles
 * is assumed to be zero (because the shortest path between 350 degrees
 * and 10 degrees is a span of 20 degrees). Similar logic would apply
 * for hues spanning from 10 to 350. The shortest path would place
 * zero degrees at the midpoint between the two parameters.
 * <p>
 * Note that because the class assumes the shortest angular path between
 * hues, a span of hues can never exceed 180 degrees. Application developers
 * should take care to avoid ambiguous cases by never allowing a span greater
 * than or equal to 180 degrees (i.e. should use multiple sequential records
 * to avoid ambiguity).
 */
public class ColorPaletteRecordHSV extends ColorPaletteRecord {

  final Color rgb0;
  final Color rgb1;

  final double h0;
  final double s0;
  final double v0;
  final double h1;
  final double s1;
  final double v1;

  final double deltaS;
  final double deltaV;
  final double deltaH;
  final boolean wrapAround;

  /**
   * Constructs a record for the specified ranges and selections
   * of HSV. HSV values are expected to be supplied as an array of dimension 3,
   * giving values in the order: hue, saturation, and value.
   *
   * @param range0 the minimum for the record's range of values
   * @param range1 the maximum for the record's range of values
   * @param hsv0 the color values associated with range0
   * @param hsv1 the color values associated with range1
   */
  public ColorPaletteRecordHSV(double range0, double range1, double[] hsv0, double[] hsv1) {
    super(range0, range1);

    h0 = hsv0[0];
    s0 = hsv0[1];
    v0 = hsv0[2];
    h1 = hsv1[0];
    s1 = hsv1[1];
    v1 = hsv1[2];
    deltaS = s1 - s0;
    deltaV = v1 - v0;

    // we don't really know which direction the interpolation should
    // go, clockwise or counterclockwise.  So we assume that we will use
    // the direction which is the shortest angular distance.  we do this
    // by constraing the change to be within the range -180 to 180
    double dH = h1 - h0;
    if (dH <= -180) {
      dH += 360;
    } else if (dH > 180) {
      dH -= 360;
    }
    if (dH == 0) {
      dH = 360;
    }
    deltaH = dH;

    wrapAround = h0 + deltaH > 360.0 || h0 + deltaH < 0;

    int rgb = Color.HSBtoRGB((float) h0, (float) s0, (float) v0) | 0xff000000;
    rgb0 = new Color(rgb);
    rgb = Color.HSBtoRGB((float) h1, (float) s1, (float) v1) | 0xff000000;
    rgb1 = new Color(rgb);
  }

  @Override
  ColorPaletteRecord copyWithModifiedRange(double minRangeSpec, double maxRangeSpec) {
    double[] hsv0 = new double[]{h0, s0, v0};
    double[] hsv1 = new double[]{h1, s1, v1};
    ColorPaletteRecord record =  new ColorPaletteRecordHSV(
      minRangeSpec, maxRangeSpec, hsv0, hsv1);
    record.setLabel(label);
    return record;
  }

  
  
  @Override
  public int getArgb(double z) {
    double t = (z - range0) / (range1 - range0);
    if (t < 0) {
      t = 0;
    } else if (t > 1) {
      t = 1;
    }

    double a = deltaH * t + h0;
    // check for wrap-around cases
    if (wrapAround) {
      if (a < 0.0) {
        a += 360.0;
      } else if (a > 360.0) {
        a -= 360.0;
      }
    }

    float s = (float) (deltaS * t + s0);
    float v = (float) (deltaV * t + v0);
    float h = (float) (a / 360.0);
    return Color.HSBtoRGB(h, s, v);
  }

  @Override
  public Color getColor(double v) {
    return new Color(getArgb(v));
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
