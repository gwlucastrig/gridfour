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
 * 11/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93;

import java.io.IOException;
import java.util.Objects;
import org.gridfour.io.BufferedRandomAccessFile;
 

public class VariableLengthRecord {
  
  public static final int VLR_HEADER_SIZE=64;
  public static final int USER_ID_SIZE=16;
  public static final int DESCRIPTION_SIZE=32;

  final BufferedRandomAccessFile braf;
  final long offset;
  final String userId;
  final int recordId;
  final int payloadSize; // not including header
  final String description;
  final boolean textPayload;
  byte[] payload;


  /**
   * Constructs metadata object for a variable length record
   * @param braf a valid reference
   * @param offset the file position, in bytes
   * @param userID application-defined user ID string
   * @param recordID application-defined numeric record ID
   * @param payloadSize the size of the payload in bytes
   * @param description a textual description of the content
   * @parab textPayload true if the payload is text; false if the
   * payload may contain binary daa
   */
  VariableLengthRecord(
          BufferedRandomAccessFile braf,
          long offset,
          String userID,
          int recordID,
          int payloadSize,
          String description,
          boolean textPayload) {
    if(userID == null || userID.isEmpty()){
      throw new IllegalArgumentException("Null or empty User ID not supported");
    }
    if(payloadSize<0){
      throw new IllegalArgumentException("Negative payload size not allowed");
    }
    this.braf = braf;
    this.offset = offset;
    this.userId = userID;
    this.recordId = recordID;
    this.payloadSize = payloadSize;
    this.description = description;
    this.payload = null;
    this.textPayload = textPayload;
  }

  
    /**
   * Constructs an instance for internal tracking.  The payload is not read
   * from the database, but a reference to the random-access file is maintained
   * so that it can be read later.
   * <p>
   * The available argument indicates how much space is allocated in the
   * associated file for reading data. This value should always be
   * adequate unless an implementation error has occurred.
   * @param braf a valid reference
   * @param available indicates how much space was allocated for reading the
   * content.
   * @throws IOException in the event of an I/O error. 
   */
  VariableLengthRecord(BufferedRandomAccessFile braf, int available) throws
          IOException {
    offset = braf.getFilePosition();
    userId = braf.readASCII(USER_ID_SIZE);
    recordId = braf.leReadInt();
    payloadSize = braf.leReadInt();
    textPayload = braf.readBoolean();
    byte []dummy = new byte[7];
    braf.readFully(dummy, 0, 7); // spares
    if (VLR_HEADER_SIZE + payloadSize > available) {
      throw new IOException("Internal error, VLR record size mismatch");
    }
    description = braf.readASCII(DESCRIPTION_SIZE);
    if(payloadSize>0){
      this.braf = braf;
    }else{
      this.braf = null;
    }
  }

  
  
  /**
   * Gets the file position for the start of the data associated with this
   * record. The data is stored as a series of bytes of the length given by the
   * record-length element.
   *
   * @return a positive long integer.
   */
  public long getFilePosition() {
    return offset;
  }

  /**
   * Gets the user ID for the record.
   *
   * @return a valid, potentially empty string.
   */
  public String getUserId() {
    return userId;
  }

  /**
   * Gets the numerical ID associated with the record.
   *
   * @return a positive value in the range of an unsigned short (two-byte)
   * integer.
   */
  public int getRecordId() {
    return recordId;
  }

  /**
   * Gets the length, in bytes, of the data associated with the record. This
   * value does not include the size of the header.
   *
   * @return a positive value in the range of an unsigned short (two-byte)
   * integer; may be zero.
   */
  public int getPayloadSize() {
    return payloadSize;
  }

  /**
   * Get the description text associated with the record.
   *
   * @return a valid, potentially empty, string.
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return String.format("Variable Length Record: %6d  %6d    %-16s  %s",
            recordId, payloadSize, userId, description);
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 37 * hash + Objects.hashCode(this.userId);
    hash = 37 * hash + this.recordId;
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof VariableLengthRecord) {
      if (this == obj) {
        return true;
      }
      final VariableLengthRecord other = (VariableLengthRecord) obj;
      if (this.recordId == other.recordId && this.userId == other.userId) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reads the payload from the associated SimpleRandomAccess file
   * @return if successful a valid, potentially zero-sized array.
   * @throws IOException in the event of an I/O error of if the associated
   * file has been closed.
   */
  public byte[] readPayload() throws IOException {
    if (payloadSize == 0) {
      return new byte[0];
    }
    byte[] p = new byte[payloadSize];
    if (payload == null) {
      payload = new byte[payloadSize];
      if (braf == null || braf.isClosed()) {
        throw new IOException("Unable to read payload, file is closed");
      }
      braf.seek(offset+VLR_HEADER_SIZE);
      braf.readFully(payload);
    }

    System.arraycopy(payload, 0, p, 0, payloadSize);

    return p;
  }
  
  /**
   * Reads a text payload from the record.  If the payload is not
   * text, returns an empty string.
   * @return a valid, potentially empty, string
   * @throws IOException in the event of a IO error.
   */
  public String readPayloadText() throws IOException{
    if(!hasTextPayload()){
      return "";
    }
    byte [] p = readPayload();
    return new String(p, "UTF-8");
  }
  
  /**
   * Indicates whether the payload is text or if it contains binary data.
   * @return true if the payload is text; otherwise false.
   */
  public boolean hasTextPayload(){
    return textPayload;
  }

}
