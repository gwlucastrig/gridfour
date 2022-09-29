/* --------------------------------------------------------------------
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
 * OUT OF OR IN CONNECTI
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
package org.gridfour.demo.access;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import org.gridfour.gvrs.GvrsCacheSize;
import org.gridfour.gvrs.GvrsElement;
import org.gridfour.gvrs.GvrsFile;
import org.gridfour.gvrs.GvrsFileSpecification;
import org.gridfour.gvrs.GvrsMetadata;

/**
 * A simple demonstration application that reads the entire content of a GVRS
 * file. Intended to test read operations and measure access time.
 */
public class ReadGvrs {

  public static void main(String[] args) throws IOException {

    PrintStream ps = System.out;
    long time0, time1;

    if (args.length == 0) {
      System.out.println("No input file specified");
      System.exit(0);
    }
    File file = new File(args[0]);
    System.out.println("Reading file " + file.getPath());
    boolean oneTestPerTile = args.length > 1;

    // Open the file.  The time required to open the file depends, in part,
    // on whether a supplemental index file (.gvrs) is available.  To test the
    // difference, simply delete the .gvrx file.   Deleting the index file
    // will also allow you to test whether the .gvrs file can be opened
    // successfully when an index file is not availble.
    GvrsFileSpecification spec = null;
    int nRows, nCols, nRowsOfTiles, nColsOfTiles, nTiles;

    time0 = System.nanoTime();
    try ( GvrsFile gvrs = new GvrsFile(file, "r")) {
      time1 = System.nanoTime();
      double timeForOpeningFile = (time1 - time0) / 1.0e+6;

      // GvrsFile implements a method that allows an application to obtain
      // a safe copy of the specification that was used to create the
      // original GVRS file.  The specification element is the primary
      // method for obtaining descriptive metadata about the organization
      // of the file.   The example that follows demonstrates the use of
      // the specification to get some descriptive data.
      //    Of course, if an application just wants to print that
      // metadata, the summarize function is the most efficient way of
      // doing so.
      spec = gvrs.getSpecification();
      nRows = spec.getRowsInGrid();
      nCols = spec.getColumnsInGrid();
      nRowsOfTiles = spec.getRowsOfTilesInGrid();
      nColsOfTiles = spec.getColumnsOfTilesInGrid();
      nTiles = nRowsOfTiles * nColsOfTiles;
      ps.format("File dimensions%n");
      ps.format("  Rows:      %8d%n", nRows);
      ps.format("  Columns:   %8d%n", nCols);
      ps.format("  Tiles:     %8d%n", nTiles);
      ps.format("Time to read header and index %10.1f ms%n", timeForOpeningFile);
      gvrs.summarize(ps, true);

      // Variable length records can contain either binary or text data.
      // The VLR's are read during initial access, though their payload
      // (which may be quite large) is not read until requested by the
      // application code.
      ps.println("\n\nGVRS Metadata");
      ps.println("------------------------------------------------");
      List<GvrsMetadata> metadataList = gvrs.readMetadata();
      for (GvrsMetadata metadata : metadataList) {
        String description = metadata.getDescription();
        ps.format("  %-24.24s  %6d:  %s%n",
          metadata.getName(),
          metadata.getRecordID(),
          description == null ? "" : description);
      }
    } // end of try-with-resources for GvrsFile

    // we collect a sum of the samples.  This value serves as a diagnostic
    // for ensuring consistent implementations when making code changes.
    // It also provides a way of exercising the data-reading logic.
    int nTest = 4;
    double sumSample = 0;
    long nSample = 0;
    int rowStep = 1;
    int colStep = 1;
    if (oneTestPerTile) {
      rowStep = spec.getRowsInTile();
      colStep = spec.getColumnsInTile();

    }
    for (int iTest = 0; iTest < nTest; iTest++) {
      time0 = System.nanoTime();
      try ( GvrsFile gvrs = new GvrsFile(file, "r")) {
        List<GvrsElement> elementList = gvrs.getElements();
        GvrsElement zElement = elementList.get(0);
        gvrs.setTileCacheSize(GvrsCacheSize.Large);
        gvrs.setMultiThreadingEnabled(true);
        for (int iRow = 0; iRow < nRows; iRow += rowStep) {
          for (int iCol = 0; iCol < nCols; iCol += colStep) {
            double sample = zElement.readValue(iRow, iCol);
            sumSample += sample;
            nSample++;
          }
        }
        time1 = System.nanoTime();
        double timeForReadingFile = (time1 - time0) / 1.0e+6;
        System.out.format("Time to read all tiles        %10.1f ms%n",
          timeForReadingFile);

        if (iTest == nTest - 1) {
          // on the last test, summarize
          gvrs.summarize(ps, false);
        }
      } // end of try-with-resources for GvrsFile
    }

    ps.println("Avg Samples " + sumSample / (double) nSample);

  }

}
