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
package org.gridfour.gvrs;

/**
 * Specifies the size of a tile cache for the GvrsFile class. In
 * general, large caches support faster data access but require more memory.
 * Smaller caches conserve memory, but may result in reduced performance for
 * data queries and storage.
 */
public enum GvrsCacheSize {
  /**
   * The smallest cache size, makes conservative use of memory. May result in
   * reduced performance compared to the larger cache sizes.
   * <p>
   * The maximum memory size for this specification is currently 2 megabytes,
   * but that value is subject to change in future versions of this code.
   */
  Small(2 * 1024 * 1024),
  /**
   * A moderate cache size providing better performance than the smaller size,
   * though consuming additional memory, this setting is the default value.
   * <p>
   * The maximum memory size for this specification is currently 12 megabytes,
   * but that value is subject to change in future versions of this code.
   */
  Medium(12 * 1024 * 1024),
  /**
   * A larger cache size intended to support higher performance applications
   * and creating new raster files. This cache size is recommended for
   * high-performance applications that are willing to dedicate a
   * substantial block of memory to a single GvrsFile instance.
   * <p>
   * The maximum memory size for this specification is currently 256 megabytes,
   * but that value is subject to change in future versions of this code.
   */
  Large(256 * 1024 * 1024);

  final int maxBytesInCache;

  GvrsCacheSize(int maxBytesInCache) {
    this.maxBytesInCache = maxBytesInCache;
  }
}
