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
 * 11/2021  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.gridfour.io.BufferedRandomAccessFile;

/**
 * Provides definitions and elements for storing and reading metadata
 * in a GVRS file.
 * <p>
 * The syntax for the application-defined ID string (the "name")
 * must conform to the rules for the GVRS identifier:
 * <ol>
 * <li>The first character must be an upper or lowercase ASCII letter.
 * This restriction is applied to support languages such as Python where
 * an initial underscore indicates a special interpretation for
 * an identifier.</li>
 * <li>A combination of ASCII characters including upper and lowercase
 * letters, numeric digits, underscores.</li>
 * </ol>
 * <p>
 * <strong>Note:</strong> GVRS identifiers are intended to be consistent
 * with naming conventions across a broad range of computer languages
 * (including C/C++, Java, C#, and Python). So the allowable character set
 * for identifiers is limited.
 * <p>
 * The limit to metadata name lengths is 32 characters.
 */
public class GvrsMetadata implements Comparable<GvrsMetadata>{
  
  public static final int GVRS_METADATA_MAX_ID_LENGTH = 32;
  final String name;
  final int recordID;
  final GvrsMetadataType dataType;
  byte[] content = new byte[0];

  int descriptionLength;
  String description;
  boolean uniqueRecordID;

  /**
   * Constructs a GVRS metadata instance with the specified parameters.
   * This constructor is intended for specifying a unique metadata instances.
   * A GVRS file may contain one, and only one, metadata record with
   * a specific name and record ID.
   *
   * @param name an application-defined ID string
   * @param recordID an application-defined numeric ID
   * @param dataType the datatype for the metadata.
   */
  public GvrsMetadata(String name, int recordID, GvrsMetadataType dataType) {
    this.name = name;
    this.recordID = recordID;
    this.dataType = dataType;
    GvrsIdentifier.checkIdentifier(name, GVRS_METADATA_MAX_ID_LENGTH);
    uniqueRecordID = true;
  }

  
  /**
   * Constructs a GVRS metadata instance with the specified data type,
   * but no restriction for record ID. This constructor may be used when
   * a application intends to store multiple metadata records with the
   * same name.
   * <p>
   * The syntax for the application-defined ID string (the "name")
   * must conform to the rules for the GVRS identifier (see class notes above)
   * @param name an application-defined ID string conforming to the
   * rules for GVRS identifiers.
   * @param dataType the datatype for the metadata.
   */
  public GvrsMetadata(String name,  GvrsMetadataType dataType) {
    this.name = name;
    this.recordID = 0;
    this.dataType = dataType;
    GvrsIdentifier.checkIdentifier(name, GVRS_METADATA_MAX_ID_LENGTH);
    uniqueRecordID = false;
  }

  
  
  /**
   * Sets an optional description string.
   *
   * @param description a valid string with an UTF-8 encoding.
   */
  public void setDescription(String description) {
    if (description == null || description.isEmpty()) {
      this.descriptionLength = 0;
      this.description = null;
    } else {
      this.description = description.trim();
      byte[] b = description.getBytes(StandardCharsets.UTF_8);
      descriptionLength = b.length;
    }
  }

  /**
   * Gets the metadata type associated with this instanced.
   * @return a valid enumeration instance.
   */
  public GvrsMetadataType getDataType(){
    return dataType;
  }
  
  
  /**
   * Gets the description string stored with the metadata.
   *
   * @return if set, a valid string with a UTF-8 encoding; otherwise, a null.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the amount of storage space required to store the metadata, in bytes.
   *
   * @return a positive value, in bytes.
   */
  public int getStorageSize() {
    // since the User ID can only include ASCII characters, its
    // length and its storage size are the same
    int sumStorage =
      2 + name.length()
      + 4 // recordID
      + 1 // data type
      + 1 // content provided
      + 1 // description provided
      + 1; // reserved
    if (content.length > 0) {
      sumStorage +=  (4 + content.length);
    }
    if (descriptionLength > 0) {
      sumStorage += (2 + descriptionLength);
    }
    return sumStorage;
  }

  int computePaddedSize2(int n) {
    return n + (n & 1);
  }

  int computePaddedSize4(int n) {
    return (n + 3) & 0x7ffffffc;
  }

  int computePaddedSize8(int n) {
    return (n + 7) & 0x7ffffff8;
  }

  static int computePaddingFor2(int n) {
    return n & 1;
  }

  static int computePaddingFor4(int n) {
    return (4 - n) & 0x03;
  }

  static int computePaddingFor8(int n) {
    return (8 - n) & 0x07;
  }

  void writePadding(BufferedRandomAccessFile braf, int n) throws IOException {
    for (int i = 0; i < n; i++) {
      braf.write(0);
    }
  }

  void write(BufferedRandomAccessFile braf) throws IOException {
    braf.writeUTF(name);
    braf.leWriteInt(recordID);
    braf.write(dataType.getCodeValue());
    braf.writeBoolean(content.length > 0);
    braf.writeBoolean(descriptionLength > 0);
    braf.write(0); // reserved
    if (content.length > 0) {
      braf.leWriteInt(content.length);
      braf.write(content);
    }
    if (descriptionLength > 0) {
      braf.writeUTF(description);
    }
  }

  static GvrsMetadataReference  readMetadataRef(BufferedRandomAccessFile braf, long offset) throws IOException {
    String metadataName = braf.readUTF();
    int metadataRecordID = braf.leReadInt();
    int codeValue = braf.readByte();

    GvrsMetadataType  metadataType = GvrsMetadataType.valueOf(codeValue);
    return new GvrsMetadataReference(
      metadataName, metadataRecordID, metadataType, offset);
  }
  
  
  public void setInteger(int value) {
    setIntegers(new int[]{value});
  }

  public int getInteger() {
    int[] values = getIntegers();
    if (values.length == 0) {
      throw new IllegalArgumentException(
        "Attempt to get integer from empty content");
    }
    return values[0];
  }

  public void setIntegers(int[] values) {
    checkTypeCompatibility(GvrsMetadataType.INTEGER);
    if (values == null || values.length == 0) {
      this.content = new byte[0];
      return;
    }
    ByteBuffer bb = bbInit(GvrsMetadataType.INTEGER, values.length);
    for (int i = 0; i < values.length; i++) {
      bb.putInt(values[i]);
    }
  }

  public int[] getIntegers() {
    checkTypeCompatibility(GvrsMetadataType.INTEGER);
    int n = content.length / GvrsMetadataType.INTEGER.bytesPerValue;
    if (n == 0) {
      return new int[0];
    }
    ByteBuffer bb = bbWrap( );
    int[] values = new int[n];
    for (int i = 0; i < n; i++) {
      values[i] = bb.getInt();
    }
    return values;
  }

  public void setShort(short value) {
    setShorts(new short[]{value});
  }

  public short getShort() {
    short[] values = getShorts();
    if (values.length == 0) {
      throw new IllegalArgumentException(
        "Attempt to get short from empty content");
    }
    return values[0];
  }

  public void setShorts(short[] values) {
    checkTypeCompatibility(GvrsMetadataType.SHORT);
    if (values == null || values.length == 0) {
      this.content = new byte[0];
      return;
    }
    ByteBuffer bb = bbInit(GvrsMetadataType.SHORT, values.length);
    for (int i = 0; i < values.length; i++) {
      bb.putShort(values[i]);
    }
  }

  public short[] getShorts() {
    checkTypeCompatibility(GvrsMetadataType.SHORT);
    int n = content.length / GvrsMetadataType.SHORT.bytesPerValue;
    if (n == 0) {
      return new short[0];
    }
    ByteBuffer bb = bbWrap();
    short[] values = new short[n];
    for (int i = 0; i < n; i++) {
      values[i] = bb.getShort();
    }
    return values;
  }

  public void setDouble(double value) {
    setDoubles(new double[]{value});
  }

  public double getDouble() {
    double[] values = getDoubles();
    if (values.length == 0) {
      throw new IllegalArgumentException(
        "Attempt to get double from empty content");
    }
    return values[0];
  }

  public void setDoubles(double[] values) {
    checkTypeCompatibility(GvrsMetadataType.DOUBLE);
    if (values == null || values.length == 0) {
      this.content = new byte[0];
      return;
    }
    ByteBuffer bb = bbInit(GvrsMetadataType.DOUBLE, values.length);
    for (int i = 0; i < values.length; i++) {
      bb.putDouble(values[i]);
    }
  }

  public double[] getDoubles() {
    checkTypeCompatibility(GvrsMetadataType.DOUBLE);
    int n = content.length / GvrsMetadataType.DOUBLE.bytesPerValue;
    if (n == 0) {
      return new double[0];
    }
    ByteBuffer bb = bbWrap( );
    double[] values = new double[n];
    for (int i = 0; i < n; i++) {
      values[i] = bb.getDouble();
    }
    return values;
  }

  
  public void setString(String string){
    checkTypeCompatibility(GvrsMetadataType.STRING);
    if(string==null || string.isEmpty()){
      content = new byte[0];
    }
    byte []b = string.getBytes(StandardCharsets.UTF_8);
    ByteBuffer bb = bbInit(GvrsMetadataType.STRING, 4+b.length);
    bb.putInt(b.length);
    bb.put(b);
  }
  
  public String getString(){
     checkTypeCompatibility(GvrsMetadataType.STRING);
     if(content.length==0){
       return ""; // empty string
     }
     ByteBuffer bb = bbWrap();
     int n = bb.getInt();
     byte [] b = new byte[n];
     bb.get(b);
     return new String(b, StandardCharsets.UTF_8);
  }
  
  
  public void setUnsignedShort(int value) {
    setShorts(new short[]{(short)value});
  }

  public int getUnsignedShort() {
    int [] values = getUnsignedShorts();
    if (values.length == 0) {
      throw new IllegalArgumentException(
        "Attempt to get unsigned short from empty content");
    }
    return values[0];
  }

  public void setUnsignedShorts(short[] values) {
    checkTypeCompatibility(GvrsMetadataType.SHORT);
    if (values == null || values.length == 0) {
      this.content = new byte[0];
      return;
    }
    ByteBuffer bb = bbInit(GvrsMetadataType.SHORT, values.length);
    for (int i = 0; i < values.length; i++) {
      bb.putShort(values[i]);
    }
  }

    public void setUnsignedShorts(int[] values) {
    checkTypeCompatibility(GvrsMetadataType.UNSIGNED_SHORT);
    if (values == null || values.length == 0) {
      this.content = new byte[0];
      return;
    }
    ByteBuffer bb = bbInit(GvrsMetadataType.SHORT, values.length);
    for (int i = 0; i < values.length; i++) {
      int test = values[i] >>> 16;
      if (test == 0 || test == 0xffff) {
        bb.putShort((short) values[i]);
      } else {
        String hex = String.format("0x%08x", values[i]);
        throw new IllegalArgumentException("Integer value " + hex
          + " cannot be correctly cast to short");
      }
    }
  }

  
  
  public int[] getUnsignedShorts() {
    checkTypeCompatibility(GvrsMetadataType.SHORT);
    int n = content.length / GvrsMetadataType.SHORT.bytesPerValue;
    if (n == 0) {
      return new int[0];
    }
    ByteBuffer bb = bbWrap();
    int [] values = new int[n];
    for (int i = 0; i < n; i++) {
      values[i] = bb.getShort()&0x0000ffff;
    }
    return values;
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

  @Override
  public String toString() {
    return String.format("GVRS metadata: %s:%d %s",
      name, recordID,
      description == null ? "" : description);
  }

  private ByteBuffer bbInit(GvrsMetadataType contentType, int length) {
    int n = length * contentType.bytesPerValue;
    content = new byte[n];
    ByteBuffer bb = ByteBuffer.wrap(content);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    return bb;
  }

  private ByteBuffer bbWrap( ) {
    ByteBuffer bb = ByteBuffer.wrap(content);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    return bb;
  }

  private void checkTypeCompatibility(GvrsMetadataType contentType) {
    // special rules for strings
    if(contentType==dataType){
      return;
    }
    
    if (contentType == GvrsMetadataType.STRING 
      && dataType == GvrsMetadataType.ASCII)
    {
      // ASCII and STRING are compatible
      return;
    }else if(contentType==GvrsMetadataType.ASCII 
      && dataType==GvrsMetadataType.ASCII){
      return;
    }
    
    if(contentType==GvrsMetadataType.SHORT && dataType==GvrsMetadataType.UNSIGNED_SHORT){
      return;
    }

    if(dataType==GvrsMetadataType.UNSPECIFIED){
      // The responsibility for ensuring correct use is left
      // to the calling application.
      return;
    }
    
   
      throw new IllegalArgumentException(
        "Specified data type " + contentType.name()
        + " is not compatible with this instance's specified data-type "
        + dataType.name());
    
  }

   GvrsMetadata(BufferedRandomAccessFile braf) throws IOException {

    name = braf.readUTF();
    recordID = braf.leReadInt();
    int codeValue = braf.readByte();
    dataType = GvrsMetadataType.valueOf(codeValue);
    boolean hasContent = braf.readBoolean();
    boolean hasDescription = braf.readBoolean();
    braf.skipBytes(1); // reserved

    if (hasContent) {
      int contentLength = braf.leReadInt();
      content = new byte[contentLength];
      braf.readFully(content);
    }
    if (hasDescription) {
      long offset1 = braf.getFilePosition();
      descriptionLength = braf.readShort(); // note, this is Big-Endian
      braf.seek(offset1);
      description = braf.readUTF();
    }

  }

  @Override
  public int compareTo(GvrsMetadata o) {
      int test = name.compareTo(o.name);
      if(test==0){
        test = Integer.compare(recordID, o.recordID);
      }
      return test;
  }

}
