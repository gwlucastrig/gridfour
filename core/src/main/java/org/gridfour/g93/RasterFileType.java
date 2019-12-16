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
package org.gridfour.g93;

/**
 * Defines types for raster-related files used in the G93 system.
 */
 enum RasterFileType {
  /**
   * The simple g93 raster file type
   */
  G93raster("g93", "g93 raster"),
  /**
   * The optional index file for g93 rasters
   */
  G93index("g9x", "g93 index");

  private final String extension;
  private final String identifier;

  RasterFileType(String extension, String identifier) {
    this.extension = extension;
    this.identifier = identifier;
  }

  /**
   * Gets the 3 character extension associated with this enumeration type.
   *
   * @return a valid string consisting of lowercase letters and numerals.
   */
  public String getExtension() {
    return extension;
  }

  /**
   * Gets the identifier string that is embedded into g93 raster and
   * raster-related files.
   *
   * @return a valid string consisting of lower case letters and numerals,
   * beginning with the string "g93" and of maximum length 12 characters.
   */
  public final String getIdentifier() {
    return identifier;
  }

}
