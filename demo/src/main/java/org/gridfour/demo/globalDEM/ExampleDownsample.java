/* --------------------------------------------------------------------
 * Copyright (C) 2022  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 05/2022  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.globalDEM;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.gridfour.demo.utils.TestOptions;
import org.gridfour.gvrs.GvrsElement;
import org.gridfour.gvrs.GvrsElementInt;
import org.gridfour.gvrs.GvrsElementIntCodedFloat;
import org.gridfour.gvrs.GvrsElementShort;
import org.gridfour.gvrs.GvrsElementSpecification;
import org.gridfour.gvrs.GvrsElementType;
import org.gridfour.gvrs.GvrsFile;
import org.gridfour.gvrs.GvrsFileSpecification;
import org.gridfour.gvrs.GvrsMetadata;

/**
 * Provides an example of a simple down-sample operation in which an data set
 * is reduced by an integral multiple of its size. The down sample
 * is performed by averaging together groups of neighboring pixels.
 * <p>
 * Although the ability to down sample data is useful, the primary purpose
 * of this class is to illustrate the way Gridfour defines raster coordinates.
 * <p>
 * <strong>Note:</strong> This class will not correctly handle coordinates
 * for GVRS files that have a skewed or rotated model coordinate system.
 */
public class ExampleDownsample {

  public static void main(String[] args) {
    ExampleDownsample downsample = new ExampleDownsample();
    try {
      downsample.process(System.out, args);
    } catch (IOException | IllegalArgumentException ex) {
      System.err.println("Error performing down-sample operation " + ex.getMessage());
      System.err.println(ex.getMessage());
      ex.printStackTrace(System.err);
    }

  }

  boolean isOptionSpecified(String []args, String target){
    for(int i=0; i<args.length; i++){
      String s = args[i];
      int n = s.length();
      if(s.startsWith("-no") && n>3 && target.equals(s.substring(3, n))){
        return true;
      }else if(s.startsWith("-") && n>1 && target.equals(s.substring(1,n))){
        return true;
      }
    }
    return false;
  }

  private void process(PrintStream ps, String[] args) throws IOException  {
    TestOptions options = new TestOptions();
    options.argumentScan(args);
    ps.format("%nGVRS example down-sample application%n");
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm z", locale);
    ps.format("Date of Execution: %s%n", sdFormat.format(date));

    File inputFile = options.getInputFile();
    if (inputFile == null) {
      ps.format("Missing specification for input file%n");
      ps.format("Application terminated%n");
      return;
    }

    File outputFile = options.getOutputFile();
    if (outputFile == null) {
      ps.format("Missing specification for output file%n");
      ps.format("Application terminated%n");
      return;
    }
    if (outputFile.exists()) {
      ps.println("Output file exists. Removing old file");
      boolean status = outputFile.delete();
      if(!status){
        ps.println("Failed to remove old file");
        return;
      }
    }

    boolean[] matched = new boolean[args.length];

    int factor =
      options.scanIntOption(args, "-factor", matched, 2);

    Boolean compression = null;
    if(isOptionSpecified(args, "compress")){
      compression = options.scanBooleanOption(args, "-compress", matched, Boolean.FALSE);
    }

    ps.format("Input file:        %s%n", inputFile.getPath());
    ps.format("Output file:       %s%n", outputFile.getPath());
    ps.format("Downsample factor: %d%n", factor);

    boolean enableMultiThreading
      = options.scanBooleanOption(args, "-multithread", matched, true);

    // Open the input file and use it, along with the factor,
    // to create a specification for the down-sampled file.
    try ( GvrsFile input = new GvrsFile(inputFile, "r");
      GvrsFile output = new GvrsFile(outputFile,
        makeSpec(input, factor, compression)))
    {
      // Even though the metadata may not necessarily be defined for
      // the down-sampled data, this application transcribes it
      // in order to demonstrate how it would be done.
      List<GvrsMetadata>metadataList = input.readMetadata();
      for(GvrsMetadata m: metadataList){
        output.writeMetadata(m);
      }


      output.setMultiThreadingEnabled(enableMultiThreading);
      GvrsFileSpecification outSpec = output.getSpecification();
      int nRows = outSpec.getRowsInGrid();
      int nCols = outSpec.getColumnsInGrid();
      List<GvrsElement> inputElements = input.getElements();
      List<GvrsElement> outputElements = output.getElements();

      GvrsElement[] inArray = inputElements.toArray(new GvrsElement[0]);
      GvrsElement[] outArray = outputElements.toArray(new GvrsElement[0]);
      // For integral type elements, populate an array of fill values
      // to be used for comparison in loop below.
      int []iFill = new int[inArray.length];
       for (int iElement = 0; iElement < inArray.length; iElement++) {
         GvrsElement e = inArray[iElement];
         GvrsElementType eType = inArray[iElement].getDataType();
         switch(eType){
           case FLOAT:
             break;
           case INTEGER:
             iFill[iElement] = ((GvrsElementInt)e).getFillValue();
             break;
           case SHORT:
              iFill[iElement] = ((GvrsElementShort)e).getFillValue();
              break;
           case INT_CODED_FLOAT:
             iFill[iElement] = ((GvrsElementIntCodedFloat)e).getFillValueInt();
             break;
           default:
             // This shouldn't happen...
             iFill[iElement] = Integer.MIN_VALUE;
         }
       }

      // Perform the downsample operation -----------------------------
      long time0 = System.currentTimeMillis();
      for (int iRow = 0; iRow < nRows; iRow++) {
        if (iRow % 1000 == 999) {
          long time1 = System.currentTimeMillis();
          double deltaT = time1 - time0;
          double rate = (iRow + 1) / deltaT;  // rows per millis
          int nRemaining = nRows - iRow;
          long remainingT = (long) (nRemaining / rate);
          Date d = new Date(time1 + remainingT);
          ps.format("Completed %d rows, %4.1f%% of total, est completion at %s%n",
            iRow + 1, 100.0 * (double) iRow / (nRows - 1.0), d);
          ps.flush();
        }
        for (int iCol = 0; iCol < nCols; iCol++) {
          int inputRow = iRow * factor;
          int inputCol = iCol * factor;
          elementLoop:
          for (int iElement = 0; iElement < inArray.length; iElement++) {
            GvrsElement inE = inArray[iElement];
            GvrsElement outE = outArray[iElement];
            if (inE.getDataType() == GvrsElementType.FLOAT) {
              float[] f = inE.readBlock(inputRow, inputCol, factor, factor);
              float fSum = 0;
              for (int i = 0; i < f.length; i++) {
                fSum += f[i];
              }
              outE.writeValue(iRow, iCol, fSum / f.length);
            } else {
              int[] s = inE.readBlockInt(inputRow, inputCol, factor, factor);
              int sSum = 0;
              for (int i = 0; i < s.length; i++) {
                if(s[i]==iFill[iElement]){
                  // for many source data types, the fill value will not
                  // follow the rules for averaging, so the cell is
                  // just left unpopulated.
                  continue elementLoop;
                }
                sSum += s[i];
              }
              double avg = ((double)sSum)/s.length;
              int iAvg = (int)Math.floor(avg+0.5);
              outE.writeValueInt(iRow, iCol, iAvg);
            }
          }
        }
      }
      output.flush();
      output.summarize(ps, true);
    } catch (IOException ioex) {
      throw ioex;
    }

  }

  /**
   * Creates a specification for a down-sampled raster based on the
   * input raster.  The down-sampled version is a match in terms
   * of elements and data types, but uses a grid reduced by the
   * specified factor.
   * @param input a valid GVRS file instance
   * @param factor a positive integer
   * @param compression if non-null, indicates a value to override the
   * specifications from the input file.
   * @return a valid specification instance
   */
  private GvrsFileSpecification makeSpec(GvrsFile input, int factor, Boolean compression) {

    // Obtain the source input specification and use it to construct
    // a compatible output specification

    GvrsFileSpecification inSpec = input.getSpecification();
    int nRowsIn = inSpec.getRowsInGrid();
    int nColsIn = inSpec.getColumnsInGrid();
    int nRowsOut = nRowsIn / factor;
    int nColsOut = nColsIn / factor;
    GvrsFileSpecification outSpec
      = new GvrsFileSpecification(nRowsOut, nColsOut);

    // compute the coordinates for the downsampled product
    boolean isGeographic = inSpec.isGeographicCoordinateSystemSpecified();

    // Recall that the coordinates (x0, y0) are the coordinates of
    // a point at the center of the first cell in the raster grid.
    // We compute the center of the down-sampled cell so that it is
    // coextensive with a set of cells in the source file.
    // To do so, compute the corner of the first input cell and then
    // use it as the corner of the first output cell.
    //    If the specified factor evenly divides the number of rows and
    // columns in the source grid, then the domain of the input raster
    // and output raster will be the same.
    double[] cellSizes = inSpec.getCellSizes();
    double inW = cellSizes[0];
    double inH = cellSizes[1];
    double outW = inW * factor;
    double outH = inH * factor;
    double inX0 = inSpec.getX0();
    double inY0 = inSpec.getY0();
    double cornerX0 = inX0 - inW / 2; // same corner point for both
    double cornerY0 = inY0 - inH / 2; // input and output
    double outX0 = cornerX0 + outW / 2;
    double outY0 = cornerY0 + outH / 2;
    if (isGeographic) {
      // note that geographic takes point coordinates as latitude, longitude
      outSpec.setGeographicModel(outY0, outX0, outW, outH);
    } else {
      outSpec.setCartesianModel(outX0, outY0, outW, outH);
    }

    // ----------------------------------------------------------
    // Copy the element specifications. These are necessary to ensure
    // that the output file is compatible with the input
    for (GvrsElementSpecification eSpec : inSpec.getElementSpecifications()) {
      outSpec.addElementSpecification(eSpec);
    }

    // ----------------------------------------------------------------
    // Copy optional elements that have a functional role in the output
    boolean dataCompressionEnabled = inSpec.isDataCompressionEnabled();
    if(compression!=null){
      dataCompressionEnabled = compression;
    }
    outSpec.setDataCompressionEnabled(dataCompressionEnabled);
    boolean checksumEnabled = inSpec.isChecksumEnabled();
    outSpec.setChecksumEnabled(checksumEnabled);


    // ------------------------------------------------
    // Copy optional metadata. These are useful for identifying the product
    // but do not have a functional role in the down-sample operation.
    String label = inSpec.getLabel();
    outSpec.setLabel("Downsample (" + factor + ") -- " + label);

    return outSpec;
  }
}
