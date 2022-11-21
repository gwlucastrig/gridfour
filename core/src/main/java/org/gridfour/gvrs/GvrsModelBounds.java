/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2022  Gary W. Lucas.
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
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2022  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

/**
 * Provides elements and methods to obtain the bounds of a model
 * coordinate system for a raster data product. The member elements of
 * this class represent two coordinate bounds. The coordinates (x0, y0)
 * and (x1, y1) represent the real-valued coordinates tied to the centers
 * of the first and last cells in the raster. The dimensions
 * xMin, xMax, yMin, and yMax represent the maximum and minimum values
 * covered by the raster. Normally, these are based on the four corners
 * of the raster grid, or the outer edges of the cells. Recall that a
 * model coordinate system may be skewed or rotated, so the range of these
 * values may be adjusted as appropriate.
 *
 */
public class GvrsModelBounds {

  final private double x0, y0, x1, y1, xMin, xMax, yMin, yMax;

  GvrsModelBounds(double x0, double y0, double x1, double y1,
    double xMin, double yMin, double xMax, double yMax) {
    this.x0 = x0;
    this.y0 = y0;
    this.x1 = x1;
    this.y1 = y1;
    this.xMin = xMin;
    this.xMax = xMax;
    this.yMin = yMin;
    this.yMax = yMax;
  }

  /**
   * Get the minimum x coordinate covered by a raster's model coordinate
   * system.
   *
   * @return a valid floating-point value.
   */
  public double getMinX() {
    return xMin;
  }

  /**
   * Get the minimum y coordinate covered by a raster's model coordinate
   * system.
   *
   * @return a valid floating-point value.
   */
  public double getMinY() {
    return yMin;
  }

  /**
   * Get the maximum x coordinate covered by a raster's model coordinate
   * system.
   *
   * @return a valid floating-point value.
   */
  public double getMaxX() {
    return xMax;
  }

  /**
   * Get the maximum y coordinate covered by a raster's model coordinate
   * system.
   *
   * @return a valid floating-point value.
   */
  public double getMaxY() {
    return yMax;
  }

  /**
   * Get the overall width of coverage for the model coordinate system,
   * equivalent to xMax-xMin.
   *
   * @return a valid floating-point value
   */
  public double getWidth() {
    return xMax - xMin;
  }

  /**
   * Get the overall width of coverage for the model coordinate system,
   * equivalent to xMax-xMin.
   *
   * @return a valid floating-point value
   */
  public double getHeight() {
    return yMax - yMin;
  }

  /**
   * Gets the X coordinate in the model coordinate system
   * for the first cell in the grid (the first row and
   * first column in the grid). Note that this value is not necessarily
   * the minimum X coordinate in the domain of the model coordinate
   * system. Its value will depend on how the application code
   * overlaid the model coordinates onto the grid.
   *
   * @return a finite floating-point value.
   */
  public double getX0() {
    return x0;
  }

  /**
   * Gets the Y coordinate in the model coordinate system
   * for the first cell in the grid (the first row and
   * first column in the grid). Note that this value is not necessarily
   * the minimum Y coordinate in the domain of the model coordinate
   * system. Its value will depend on how the application code
   * overlaid the model coordinates onto the grid.
   *
   * @return a finite floating-point value.
   */
  public double getY0() {
    return y0;
  }

  /**
   * Gets the X coordinate in the model coordinate system
   * for the last cell in the grid (the last row and
   * last column in the grid). Note that this value is not necessarily
   * the maximum X coordinate in the domain of the model coordinate
   * system. Its value will depend on how the application code
   * overlaid the model coordinates onto the grid.
   *
   * @return a finite floating-point value.
   */
  public double getX1() {
    return x1;
  }

  /**
   * Gets the Y coordinate in the model coordinate system
   * for the last cell in the grid (the last row and
   * last column in the grid). Note that this value is not necessarily
   * the maximum Y coordinate in the domain of the model coordinate
   * system. Its value will depend on how the application code
   * overlaid the model coordinates onto the grid.
   *
   * @return a finite floating-point value.
   */
  public double getY1() {
    return y1;
  }

}
