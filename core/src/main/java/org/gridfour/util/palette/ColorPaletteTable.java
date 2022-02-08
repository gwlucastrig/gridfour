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
 * 08/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.util.palette;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/**
 * Provides methods and data elements for a color table corresponding
 * to the conventions of the CPT file format, but available for other
 * application-defined uses.
 */
public class ColorPaletteTable {

  private final ColorPaletteRecord[] records;
  private final double[] keys;
  private final boolean normalization;
    private final double normalizedRangeMin;
  private final double normalizedRangeMax;
  private final boolean hinge;
  private final double hingeValue;
  private final int hingeIndex;
  private final double rangeMin;
  private final double rangeMax;
  private final boolean allRecordsHaveSingleValue;
  
  Color background;
  Color foreground;
  Color colorForNull;
  int argbForNull;

  /**
   * Constructs an instance based on the specified list of color palette records
   * and optional background, foreground, and null-value colors.
   *
   * @param recordsList a valid, non-empty list of specifications
   * @param background an optional background color; defaults white
   * @param foreground an optional foreground color; defaults to black
   * @param colorForNull an optional color value for null; defaults to null
   */
  public ColorPaletteTable(List<ColorPaletteRecord> recordsList,
    Color background,
    Color foreground,
    Color colorForNull
  ) {
    hinge = false;
    hingeValue = 0;
    hingeIndex = 0;
    normalization = false;
    normalizedRangeMin = 0;
    normalizedRangeMax = 0;
    
    if (recordsList == null || recordsList.isEmpty()) {
      throw new IllegalArgumentException("Null or empty records list");
    }
    if (background == null) {
      this.background = Color.white;
    } else {
      this.background = background;
    }
    if (foreground == null) {
      this.foreground = Color.black;
    } else {
      this.foreground = foreground;
    }
    this.colorForNull = colorForNull;
    if (colorForNull == null) {
      argbForNull = 0;
    } else {
      argbForNull = colorForNull.getRGB();
    }

    int n = recordsList.size();
    records = new ColorPaletteRecord[n];

    for (int i = 0; i < n; i++) {
      records[i] = recordsList.get(i);
    }
    // Records should already be sorted, but sort them just in case
    Arrays.sort(records);
    keys = new double[n];
    for (int i = 0; i < n; i++) {
      keys[i] = records[i].range0;
    }

    // Populate the termination flags in cases where there
    // is a gap in ranges between records.
    for (int i = 0; i < n - 1; i++) {
      if (records[i].range1 < records[i + 1].range0) {
        records[i].termination = true;
      }
    }
    records[n - 1].termination = true;

    boolean nonZeroRange = false;
    for(int i=0; i<n; i++){
      if(records[i].range1>records[i].range0){
        nonZeroRange = true;
        break;
      }
    }
    allRecordsHaveSingleValue = !nonZeroRange;
    
    
    rangeMin = records[0].range0;
    rangeMax = records[records.length - 1].range1;
  }

   /**
   * Constructs an instance based on the specified list of color palette records
   * and optional background, foreground, and null-value colors.
   *
   * @param recordsList a valid, non-empty list of specifications
   * @param background an optional background color; defaults white
   * @param foreground an optional foreground color; defaults to black
   * @param colorForNull an optional color value for null; defaults to null
   * @param hingeFlag indicates whether a hinge value is supplied
   * @param hingeValue indicates the hinge value (ignored if hinge flag is false)
   * @param normalizedRangeMin indicates minimum range
   * @param normalizedRangeMax indicates maximum range
   */
  public ColorPaletteTable(
    List<ColorPaletteRecord> recordsList,
    Color background,
    Color foreground,
    Color colorForNull,
    boolean hingeFlag,
    double hingeValue,
    double normalizedRangeMin,
    double normalizedRangeMax ) {
    this.hinge = hingeFlag;
    this.hingeValue = hingeValue;
    this.normalization = true;
    this.normalizedRangeMin = normalizedRangeMin;
    this.normalizedRangeMax = normalizedRangeMax;

    
    if (recordsList == null || recordsList.isEmpty()) {
      throw new IllegalArgumentException("Null or empty records list");
    }
    if (background == null) {
      this.background = Color.white;
    } else {
      this.background = background;
    }
    if (foreground == null) {
      this.foreground = Color.black;
    } else {
      this.foreground = foreground;
    }
    this.colorForNull = colorForNull;
    if (colorForNull == null) {
      argbForNull = 0;
    } else {
      argbForNull = colorForNull.getRGB();
    }

    int n = recordsList.size();
    records = new ColorPaletteRecord[n];

    for (int i = 0; i < n; i++) {
      records[i] = recordsList.get(i);
    }
    // Records should already be sorted, but sort them just in case
    Arrays.sort(records);
    keys = new double[n];
    for (int i = 0; i < n; i++) {
      keys[i] = records[i].range0;
    }

    // Populate the termination flags in cases where there
    // is a gap in ranges between records.
    for (int i = 0; i < n - 1; i++) {
      if (records[i].range1 < records[i + 1].range0) {
        records[i].termination = true;
      }
    }
    records[n - 1].termination = true;

        boolean nonZeroRange = false;
    for(int i=0; i<n; i++){
      if(records[i].range1>records[i].range0){
        nonZeroRange = true;
        break;
      }
    }
    allRecordsHaveSingleValue = !nonZeroRange;
    
    rangeMin = records[0].range0;
    rangeMax = records[records.length - 1].range1;
    
    int tempHingeIndex = -1;
    if(hinge){
      for(int i=0; i<records.length; i++){
        if(records[i].range0==0){
          tempHingeIndex = i;
          break;
        }
      }
      if(tempHingeIndex==-1){
        throw new IllegalArgumentException(
          "Unable to match hinge value "
          +hingeValue+" to palette range");
          
      }
    }
    hingeIndex = tempHingeIndex;
  }
  
  
  
  /**
   * Gets the application-defined background color.
   *
   * @return a valid instance
   */
  public Color getBackground() {
    return background;
  }

  /**
   * Gets the application-defined foreground color.
   *
   * @return a valid instance
   */
  public Color getForeground() {
    return foreground;
  }

  /**
   * Gets the application-defined color for null.
   *
   * @return if supplied by the application, a valid instance;
   * otherwise, a null.
   */
  public Color getColorForNull() {
    return colorForNull;
  }

  /**
   * Gets the minimum value for which an asociated color is defined
   * by the table.
   *
   * @return a valid floating point value.
   */
  public double getRangeMin() {
    if(normalization){
      return normalizedRangeMin;
    }
    return rangeMin;
  }

  /**
   * Gets the maximum value for which an asociated color is defined
   * by the table.
   *
   * @return a valid floating point value.
   */
  public double getRangeMax() {
    if(normalization){
      return this.normalizedRangeMax;
    }
    return rangeMax;
  }

  /**
   * Gets an ARGB value for the specified parameter, if available.
   * If the color table does not define a color value for the specified
   * parameter, it will return the ARGB code for a null value.
   *
   * @param zTarget a valid floating point value
   * @return if a color is defined for z, its associated ARGB value;
   * otherwise the null-value code.
   */
  public int getArgb(double zTarget) {
    // It is expected that this method will be called for every pixel in
    // a data field.  Since the number of pixels can be quite large,
    // this method is heavily optimized for speed.  Note also, that we
    // always start with a binary search.  No special coding is implemented
    // for range checks or for small key lists since we assume:
    //     1)  The majority palettes will have at least four or five entries
    //     2)  Most of the time, the z values will be in range, otherwise
    //         we wouldn't be likely to be using the palette in the first place.
    double z = zTarget;
    if(normalization){
      if(hinge){
         if(z<hingeValue){
           double t = (z-normalizedRangeMin)/(hingeValue-normalizedRangeMin);
           z = t*(records[hingeIndex-1].range1-records[0].range0)+records[0].range0;
         }else{
           double t = (z-hingeValue)/(normalizedRangeMax-hingeValue);
           int i0 = hingeIndex;
           int i1 = records.length-1;
           z = t*(records[i1].range1-records[i0].range0)+records[i0].range0;
         }
      }else{
        double t = (z-normalizedRangeMin)/(normalizedRangeMax-normalizedRangeMin);
        z = t*(records[records.length-1].range1-records[0].range0)+records[0].range0;
      }
    }
    int index = Arrays.binarySearch(keys, z);
    if (index >= 0) {
      // an exact match for the lower range value of this color record
      // TO DO: no interpolation is needed. perhaps we could expedite this.
      return records[index].getArgb(z);
    }
    if (index == -1) {
      // the target value is less than the minimum supported value
      return argbForNull;
    }

    // The Java binary search operated on the range0 value of each record.
    // It returned a negative index, indicating
    // the the value would be inserted into the key list at array position
    // -(index+1). This means that the record at -(index+1)-1 has a range0
    // value less than z.  But we don't know if the range1 value is greater
    // than z.  So we have to check.  Note that the maximum value of
    // this adjusted index value is always between zero and keys.length-1.
    // So we don't need to check whether it is in range.
    index = -(index + 1) - 1;

    // the binary search already established that the value z is
    // greater than the range0 value of the record. but we don't
    // know if it is less than or equal to the range1.  Incidentally
    // if range1 equals the range0 of the next record, the binary search
    // would have found the next record.  So we don't need to test
    // for a termination condition.
    ColorPaletteRecord record = records[index];
    if (record.range1 >= z) {
      return record.getArgb(z);
    }

    return argbForNull;
  }

  /**
   * Gets the color associated with the parameter z; if no color is
   * defined for z, the null-data color will be returned.
   *
   * @param z a valid floating point value
   * @return if defined, a valid instance; if undefined the null-data
   * color reference (which may be null).
   */
  public Color getColor(double z) {
    int argb = getArgb(z);
    if (argb == argbForNull) {
      if (!isCovered(z)) {
        return null;
      }
    }
    return new Color(argb);
  }

  /**
   * Indicates whether this table defines a color value
   * for the specified parameter.
   *
   * @param z a valid floating point valid
   * @return true if a color is associated with the value; otherwise, false.
   */
  public boolean isCovered(double z) {
    // The logic for this method is identical to that of getArgb.
    // Please see that method for more details.
    int index = Arrays.binarySearch(keys, z);
    if (index >= 0) {
      return true;
    }
    if (index == -1) {
      return false;
    }

    index = -(index + 1) - 1;
    ColorPaletteRecord record = records[index];
    return record.range1 >= z;
  }

  /**
   * Indicates that all records in the palette provide a single
   * value rather than a range of values. This configuration would
   * occur in palettes used to color code category-based (e.g. "categorical")
   * data sets rather than real-valued data sets.
   * 
   * @return true if the palette is designed for categorical data sets;
   * otherwise, false.
   */
  public boolean isCategoricalPalette(){
    return this.allRecordsHaveSingleValue;
  }
  
  /**
   * Gets an in-order list of the records in this palette.
   * @return a valid list.
   */
  public List<ColorPaletteRecord>getRecords(){
    return Arrays.asList(records);
  }
}
