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
 * Provides the abstract base for a set of classes intended to specify
 * parameters for GVRS elements.
 * <p>
 * The syntax for the application-defined element name
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
 * The limit to the length of an element name name is 32 characters.
 */
public abstract class GvrsElementSpecification {

  public static final int GVRS_ELEMENT_MAX_IDENTIFIER_LENGTH = 32;

  final String name;
  final GvrsElementType dataType;
  String description;
  String unitOfMeasure;
  String label;

  /**
   * Standard constructor used to populate base elements.
   *
   * @param name The name of the element specified by instances.
   * @param elementType The type of the element specified by instances.
   */
  GvrsElementSpecification(String name, GvrsElementType elementType) {
    GvrsIdentifier.checkIdentifier(name, GVRS_ELEMENT_MAX_IDENTIFIER_LENGTH);
    if (elementType == null) {
      throw new IllegalArgumentException(
        "A null element type is not supported");
    }
    this.name = name.trim();
    this.dataType = elementType;
  }

  /**
   * Gets the unique identifier (the "name") for this specification instance.
   * The name is a simple ASCII string that conforms to the definitions
   * for a GVRS identifier and is 32 characters or less in length.
   * @return a valid, non-empty string.
   */
  public String getName(){
    return name;
  }
  
  
  /**
   * Sets an arbitrary description string. Intended to allow applications to
   * provide documentation for elements.
   *
   * @param description a valid non-empty string, or a null if no description
   * is to be supplied.
   */
  public void setDescription(String description) {
    if (description == null || description.isEmpty()) {
      this.description = null;
    } else {
      this.description = description;
    }
  }

  /**
   * Gets the arbitrary description string. Intended to allow applications to
   * provide documentation for elements.
   *
   * @return a valid non-empty string, or a null if no description
   * is to be supplied.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets an arbitrary unit of measure string. Intended to provide
   * applications with information about the unit of measure for elements.
   *
   * @param unitOfMeasure a valid non-empty string, or a null if no unit of
   * measure is to be specified.
   */
  public void setUnitOfMeasure(String unitOfMeasure) {
    if (unitOfMeasure == null || unitOfMeasure.isEmpty()) {
      this.unitOfMeasure = null;
    } else {
      this.unitOfMeasure = unitOfMeasure.trim();
    }
  }

  /**
   * Gets the arbitrary unit of measure string. Intended to allow applications
   * to provide documentation for elements.
   *
   * @return a valid non-empty string, or a null if no description
   * is to be supplied.
   */
  public String getUnitOfMeasure() {
    return unitOfMeasure;
  }

  
  /**
   * Sets an arbitrary unit of label string. Intended to provide
   * applications with that ability to label elements using the full
   * range of UTF-8 character sets. In particular, this method is useful
   * for applications requiring specifications in non-western
   * character sets.
   *
   * @param label valid non-empty string, or null if no label
   * is to be specified.
   */
  public void setLabel(String label) {
    if (label == null || label.isEmpty()) {
      this.label = null;
    } else {
      this.label = label;
    }
  }

  /**
   * Gets the arbitrary label string.  Intended to provide
   * applications with that ability to label elements using the full
   * range of UTF-8 character sets. In particular, this method is useful
   * for applications requiring specifications in non-western
   * character sets.
   *
   * @return a valid non-empty string, or a null if no description
   * is to be supplied.
   */
  public String getLabel() {
    return label;
  }

  
  protected void copyApplicationData(GvrsElementSpecification spec) {
    description = spec.description;
    unitOfMeasure = spec.unitOfMeasure;
    label = spec.label;
  }

  /**
   * Makes a copy of the instance. Primarily used to support building
   * safe copies of the GvrsFileSpecificaiton
   *
   * @return a valid object with content identical to the current instance.
   */
  abstract GvrsElementSpecification copy();

  /**
   * Makes an instance of a GVRS element based on the data type and
   * related specifications supplied by this element specification.
   *
   * @param file the GVRS file associated with this specification.
   * @return a valid instance of one of the GvrsElement derived classes.
   */
  abstract GvrsElement makeElement(GvrsFile file);
}
