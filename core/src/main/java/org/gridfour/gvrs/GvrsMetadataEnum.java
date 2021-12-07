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

/**
 * Provides specifications for the GVRS predefined metadata.
 * The purpose of this enumeration is to support uniform and consistent
 * specifications across all applications that use the GVRS file format.
 */
public enum GvrsMetadataEnum {
  /**
   * Defines a specification for the author of a data set.
   */
  Author(GvrsMetadataType.STRING),
  /**
   * Defines a specification for copyrights. In general practice, it is
   * possible for a data set to be published using multiple copyrights
   * (such as copyrights for different nations).
   * <p>
   * When data is released in the public domain (without copyright control),
   * application developers are encouraged to indicate that the data is
   * free for use by marking a file with a notation
   * such as "This data is in the public domain".
   */
  Copyright(GvrsMetadataType.STRING),
  /**
   * Defines a specification for indicating instructions for use or
   * limitations for applicability. For example: "not intended
   * for navigation".
   */
  InstructionsForUse(GvrsMetadataType.STRING),
  /**
   * Defines a specification for bundling tags from the industry-standard
   * Tagged Image File Format (TIFF) into a GVRS file.
   * By convention, the record ID used when creating GvrsTags for TIFF
   * specifications should reflect the integer TIFF tag ID.
   */
  TIFF(GvrsMetadataType.UNSPECIFIED),
  /**
   * Defines a specification for bundling "Well Known Text" specifications
   * from Geographic Information System (GIS) applications.
   */
  WKT(GvrsMetadataType.STRING),
  /**
   * Defined for use by the GVRS Java implementation for tracking
   * data compression codecs. Metadata included under this specification
   * will include Java-specific information. Non-Java implementation should
   * ignore metadata this specification.
   */
  GvrsJavaCodecs(GvrsMetadataType.ASCII),
  GvrsCompressionCodecs(GvrsMetadataType.ASCII);

  private final GvrsMetadataType dataType;

  GvrsMetadataEnum(GvrsMetadataType dataType) {
    this.dataType = dataType;
  }

  /**
   * Gets the data type associated with this instance.
   *
   * @return a valid data type.
   */
  public GvrsMetadataType getDataType() {
    return dataType;
  }

  /**
   * Constructs a new instance of a GvrsMetadata with the name and
   * data type specified by the enumeration.
   * <p>
   * This method is suitable for use in cases where the name assigned
   * to the new instance may be used for multiple metadata instances
   * with the same name. For example, some data sources may carry multiple
   * Author or Copyright specifications.
   *
   * @return a valid instance
   */
  public GvrsMetadata newInstance() {
    return new GvrsMetadata(name(), dataType);
  }

  /**
   * Constructs a new instance of a GvrsMetadata object with the name,
   * data type, specified by the enumeration and the record ID
   * assigned to it by the calling application.
   *
   * @param recordID an integer value uniquely identifying the metadata
   * object to be constructed.
   * @return a valid instance
   */
  public GvrsMetadata newInstance(int recordID) {
    return new GvrsMetadata(name(), recordID, dataType);
  }
  
    /**
   * Constructs a new instance of a GvrsMetadata object with the name,
   * data type, specified by the enumeration and the record ID
   * assigned to it by the calling application.
   *
   * @param recordID an integer value uniquely identifying the metadata
   * object to be constructed.
   * @param metadataType the type assigned to the metedata instance.
   * @return a valid instance
   */
  public GvrsMetadata newInstance( int recordID, GvrsMetadataType metadataType) {
    return new GvrsMetadata(name(), recordID, metadataType);
  }


}
