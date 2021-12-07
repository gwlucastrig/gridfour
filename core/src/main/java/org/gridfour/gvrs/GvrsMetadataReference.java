/*
 * The MIT License
 *
 * Copyright 2021 G. W. Lucas.
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
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 10/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.gridfour.gvrs;

import java.util.Objects;

/**
 * Provides definitions and elements for managing GVRS metadata
 */
 public class GvrsMetadataReference implements Comparable<GvrsMetadataReference> {

  final String name;
  final int recordID;
  final GvrsMetadataType dataType;
  long offset;
 

  /**
   * Constructs a GVRS metadata object.
   *
   * @param name the user ID for the metadata, not null and non-empty
   * @param recordID the record ID.
   * @param long offset the file offset for the record
   */
   GvrsMetadataReference(String name, int recordID, GvrsMetadataType dataType, long offset ) {
    this.name = name;
    this.recordID = recordID;
    this.dataType = dataType;
    this.offset = offset;
  }
 
  String getKey(){
    return name+":"+Integer.toString(recordID);
  }
 
  static String formatKey(String name, int recordID){
    return name+":"+Integer.toString(recordID);
  }
  

  @Override
   public boolean equals(Object obj) {
     if (obj instanceof GvrsMetadataReference) {
       final GvrsMetadataReference other = (GvrsMetadataReference) obj;
       if (recordID == other.recordID && Objects.equals(name, other.name)) {
         return true;
       }
     }
     return false;
   }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 97 * hash + Objects.hashCode(name);
    hash = 97 * hash + recordID;
    return hash;
  }

  @Override
  public int compareTo(GvrsMetadataReference o) {
    int test = name.compareTo(o.name);
    if(test==0){
      return Integer.compare(recordID, o.recordID);
    }
    return test;
  }

 
  /**
   * Gets the application-defined "name" for the record.
   *
   * @return a valid, non-empty string.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the application-defined numerical ID associated with the record.
   *
   * @return a positive value in the range of an integer
   */
  public int getRecordID() {
    return recordID;
  }
  
  /**
   * Gets the metadata type associated with this instanced.
   * @return a valid enumeration instance.
   */
  public GvrsMetadataType getDataType(){
    return dataType;
  }
 
}
