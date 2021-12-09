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
     * reduced
     * performance compared to the larger cache sizes.
     */
    Small,
    /**
     * A moderate cache size providing better performance than the smaller size,
     * though consuming additional memory, this setting is the default value.
     */
    Medium,
    /**
     * A larger cache size intended to support higher performance applications
     * and
     * creating new raster files. The large cache size will generally include
     * enough tiles to span an entire row of tiles with a few more tiles added
     * to
     * improve transitions across portions of the data collection. This cache
     * size is recommend when writing data and can also be beneficial when
     * reading data.
     */
    Large;
}
