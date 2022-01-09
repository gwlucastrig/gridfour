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
 * 01/2022  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

/**
 * Defines the possible types of records stored in a GVRS file.
 */
enum RecordType {

  /**
   * The record contains free space, available for reuse.
   */
  Freespace(0),
  /**
   * The record contains metadaa.
   */
  Metadata(1),
  /**
   * The record contains a tile.
   */
  Tile(2),
  /**
   * The record contains an index of freespace records.
   */
  FreespaceIndex(3),
  
  /** 
   * The record contains an index of metadata records.
   */
  MetadataIndex(4),
  
  /**
   * The record contains an index of tile records.
   */
  TileIndex(5);

  final int codeValue;

  RecordType(int codeValue) {
    this.codeValue = codeValue;
  }

  /**
   * Gets the code value to be stored in a data file to indicate what
   * record type was specified..
   *
   * @return gets an integer code value indicating the record type; used
   * internally.
   */
  int getCodeValue() {
    return codeValue;
  }
 

  /**
   * Gets the enumeration type associated with the specified code value.
   * Will return a null for an invalid code value.
   * @param codeValue a value in the range 0 to 5
   * @return if successful, a valid enumeration; otherwise, a null.
   */
  static RecordType valueOf(int codeValue) {
    switch (codeValue) {
      case 0:
        return Freespace;
      case 1:
        return Metadata;
      case 2:
        return Tile;
      case 3:
        return FreespaceIndex;
      case 4:
        return MetadataIndex;
      case 5:
        return TileIndex;
      default:
        return null; // invalid type
    }
  }
 
}
