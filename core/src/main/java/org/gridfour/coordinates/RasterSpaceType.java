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
 * 10/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.coordinates;

/**
 * Describes whether the raster space associated with each value in the
 * raster should be interpreted as a single point value or an overall
 * value for an area. The notion of a raster space value is embedded into many
 * raster data formats, such as the GeoTIFF standard's PixelIsPoint and
 * PixelIsArea settings. It allows an application to precisely specify
 * how a value is derived for an arbitrary point on the surface described
 * by the raster.
 * <p>
 * Unfortunately, not all data authors indicate which form is used.
 * In such cases, if you cannot determine the appropriate representation
 * through deduction (looking at the number of rows and columns with
 * respect to the area of coverage), you may have to resort to guesswork
 * or use the "Unspecified" value of this enumeration.
 */
public enum RasterSpaceType {

  /**
   * No specification was made for the raster cell geometry type.
   */
  Unspecified(0),
  /**
   * Raster cells indicate the value at a single point.
   */
  Point(1),
  /**
   * Raster cells indicate the value for an area.
   */
  Area(2);

  final int codeValue;

  RasterSpaceType(int codeValue) {
    this.codeValue = codeValue;
  }

  /**
   * Encodes a enumeration value as an integer code. Intended for
   * use in data files that include raster space specifications,
   * but may also be applied to user interfaces and other applications.
   *
   * @return gets an integer code value indicating the enumeration type.
   */
  public int getCodeValue() {
    return codeValue;
  }

  /**
   * Derives a enumeration value from an integer code. Intended for
   * use in data files that include raster space specifications,
   * but may also be applied to user interfaces and other applications.
   * @param codeValue an integer in the range 0 to 2.
   * @return a valid enumeration value.
   */
  public static RasterSpaceType valueOf(int codeValue) {
    switch (codeValue) {
      case 0:
        return Unspecified;
      case 1:
        return Point;
      case 2:
        return Area;
      default:
        return Unspecified;
    }

  }
}
