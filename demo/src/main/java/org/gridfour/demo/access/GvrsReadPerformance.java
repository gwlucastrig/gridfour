/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.
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
import org.gridfour.gvrs.GvrsElementType;
import org.gridfour.gvrs.GvrsFile;
import org.gridfour.gvrs.GvrsFileSpecification;
import org.gridfour.gvrs.GvrsMetadata;
import org.gridfour.gvrs.GvrsMnc;

/**
 * A simple demonstration application that reads the entire content of a Gvrs
 * file. Intended to test read operations and measure access time.
 */
public class GvrsReadPerformance {

  private final static String[] usage = {
    "PackageData  -- create a Gvrs file from from ETOPO1 or GEBCO_2019 Global DEM files",
    "Arguments:",
    "    GvrsReadPerformance  <input_file>  [-multithread]",
    "Input file is mandatory.  Multi-threading option can be used to test",
    "the effects of multi-threading when processing compressed files",};

  final File inputFile;
  final GvrsFileSpecification spec;
  final int nRowsInRaster;
  final int nColsInRaster;
  final int nRowsInTile;
  final int nColsInTile;
  final int nRowsOfTiles;
  final int nColsOfTiles;

  boolean multiThreadingEnabled;

  GvrsReadPerformance(PrintStream ps, File inputFile) throws IOException {
    this.inputFile = inputFile;
    try ( GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      spec = gvrs.getSpecification();
      nRowsInRaster = spec.getRowsInGrid();
      nColsInRaster = spec.getColumnsInGrid();
      nRowsInTile = spec.getRowsInTile();
      nColsInTile = spec.getColumnsInTile();
      nRowsOfTiles = spec.getRowsOfTilesInGrid();
      nColsOfTiles = spec.getColumnsOfTilesInGrid();
      ps.format("Number of samples in grid:  %12d%n", spec.getNumberOfCellsInGrid());
      ps.format("Number of tiles in file:    %12d%n",
        spec.getRowsOfTilesInGrid() * spec.getColumnsOfTilesInGrid());

      ps.println("Data compression enabled: " + spec.isDataCompressionEnabled());
      if (spec.isDataCompressionEnabled()) {
        GvrsMetadata metadata
          = gvrs.readMetadata(GvrsMnc.GvrsCompressionCodecs.name(), 0);
        ps.println("Compression codecs:");
        if (metadata == null) {
          ps.println("    Not specified");
        } else {
          ps.println("    " + metadata.getString());
        }
      }
    }

  }

  void setMultiThreadingEnabled(boolean multiThreadingEnabled) {
    this.multiThreadingEnabled = multiThreadingEnabled;
  }

  void report(PrintStream ps, String label, double deltaT, double avgValue, long nSamples) {
    ps.format("%-15s %10.3f %15.3f %15d  %15.3f%n",
      label, deltaT, avgValue, nSamples, nSamples / deltaT / 1000000.0);
  }

  void testRowMajorScan(PrintStream ps) throws IOException {
    try ( GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      if (multiThreadingEnabled) {
        gvrs.setMultiThreadingEnabled(true);
      }
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      List<GvrsElement> elementList = gvrs.getElements();
      GvrsElement element = elementList.get(0);
      GvrsElementType dType = element.getDataType();
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();
      if (dType == GvrsElementType.INTEGER) {
        long sum = 0;
        for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
          for (int iCol = 0; iCol < nColsInRaster; iCol++) {
            int sample = element.readValueInt(iRow, iCol);
            if (sample != Integer.MIN_VALUE) {
              sum += sample;
              nSample++;
            }
          }
        }
        if (nSample > 0) {
          avgValue = (double) sum / (double) nSample;
        }
      } else {
        double sum = 0;
        for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
          for (int iCol = 0; iCol < nColsInRaster; iCol++) {
            float sample = element.readValue(iRow, iCol);
            if (!Float.isNaN(sample)) {
              sum += sample;
              nSample++;
            }
          }
        }
        if (nSample > 0) {
          avgValue = sum / (double) nSample;
        }
      }
      long time1 = System.nanoTime();
      double deltaT = (time1 - time0) / 1.0e+9;
      report(ps, "Row Major", deltaT, avgValue, nSample);
    }
  }

  void testColumnMajorScan(PrintStream ps) throws IOException {
    try ( GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      if (multiThreadingEnabled) {
        gvrs.setMultiThreadingEnabled(true);
      }
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      List<GvrsElement> elementList = gvrs.getElements();
      GvrsElement element = elementList.get(0);
      GvrsElementType dType = element.getDataType();
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();
      if (dType == GvrsElementType.INTEGER) {
        long sum = 0;
        for (int iCol = 0; iCol < nColsInRaster; iCol++) {
          for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
            int sample = element.readValueInt(iRow, iCol);
            if (sample != Integer.MIN_VALUE) {
              sum += sample;
              nSample++;
            }
          }
        }
        if (nSample > 0) {
          avgValue = (double) sum / (double) nSample;
        }
      } else {
        double sum = 0;
        for (int iCol = 0; iCol < nColsInRaster; iCol++) {
          for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
            float sample = element.readValue(iRow, iCol);
            if (!Float.isNaN(sample)) {
              sum += sample;
              nSample++;
            }
          }
        }
        if (nSample > 0) {
          avgValue = sum / (double) nSample;
        }
      }
      long time1 = System.nanoTime();
      double deltaT = (time1 - time0) / 1.0e+9;
      report(ps, "Column Major", deltaT, avgValue, nSample);
    }
  }

  void testRowBlockScan(PrintStream ps) throws IOException {
    try ( GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      if (multiThreadingEnabled) {
        gvrs.setMultiThreadingEnabled(true);
      }
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      List<GvrsElement> elementList = gvrs.getElements();
      GvrsElement element = elementList.get(0);
      GvrsElementType dType = element.getDataType();
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();
      if (dType == GvrsElementType.INTEGER || dType == GvrsElementType.SHORT) {
        long sum = 0;
        for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
          int[] block = element.readBlockInt(iRow, 0, 1, nColsInRaster);
          for (int iCol = 0; iCol < nColsInRaster; iCol++) {
            int sample = block[iCol];
            sum += sample;
            nSample++;
          }
        }
        if (nSample > 0) {
          avgValue = (double) sum / (double) nSample;
        }
      } else {
        double sum = 0;
        for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
          float[] block = element.readBlock(iRow, 0, 1, nColsInRaster);
          for (int iCol = 0; iCol < nColsInRaster; iCol++) {
            float sample = block[iCol];
            if (!Float.isNaN(sample)) {
              sum += sample;
              nSample++;
            }
          }
        }
        if (nSample > 0) {
          avgValue = sum / (double) nSample;
        }
      }
      long time1 = System.nanoTime();
      double deltaT = (time1 - time0) / 1.0e+9;
      report(ps, "Row Block", deltaT, avgValue, nSample);
    }
  }

  void testTileBlockScan(PrintStream ps) throws IOException {
    try ( GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      if (multiThreadingEnabled) {
        gvrs.setMultiThreadingEnabled(true);
      }
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      List<GvrsElement> elementList = gvrs.getElements();
      GvrsElement element = elementList.get(0);
      GvrsElementType dType = element.getDataType();
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();
      double sum = 0;
      if (dType == GvrsElementType.INTEGER || dType == GvrsElementType.SHORT) {
        for (int iRow = 0; iRow < nRowsOfTiles; iRow++) {
          for (int iCol = 0; iCol < nColsOfTiles; iCol++) {
            int row0 = iRow * nRowsInTile;
            int col0 = iCol * nColsInTile;
            int[] block = element.readBlockInt(row0, col0, nRowsInTile, nColsInTile);
            for (int sample : block) {
              sum += sample;
              nSample++;
            }
          }
        }
      } else {
        for (int iRow = 0; iRow < nRowsOfTiles; iRow++) {
          for (int iCol = 0; iCol < nColsOfTiles; iCol++) {
            int row0 = iRow * nRowsInTile;
            int col0 = iCol * nColsInTile;
            float[] block = element.readBlock(row0, col0, nRowsInTile, nColsInTile);
            for (float sample : block) {
              if (!Float.isNaN(sample)) {
                sum += sample;
                nSample++;
              }
            }
          }
        }
      }
      if (nSample > 0) {
        avgValue = sum / (double) nSample;
      }

      long time1 = System.nanoTime();
      double deltaT = (time1 - time0) / 1.0e+9;
      report(ps, "Block Test", deltaT, avgValue, nSample);
    }
  }

  void testTileLoadTime(PrintStream ps) throws IOException {
    try ( GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      if (multiThreadingEnabled) {
        gvrs.setMultiThreadingEnabled(true);
      }
      gvrs.setTileCacheSize(GvrsCacheSize.Small);
      List<GvrsElement> elementList = gvrs.getElements();
      GvrsElement element = elementList.get(0);

      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();

      double sum = 0;
      for (int iRow = 0; iRow < nRowsOfTiles; iRow++) {
        for (int iCol = 0; iCol < nColsOfTiles; iCol++) {
          int row0 = iRow * nRowsInTile;
          int col0 = iCol * nColsInTile;
          float sample = element.readValue(row0 + 1, col0 + 1);
          if (!Float.isNaN(sample)) {
            sum += sample;
            nSample++;
          }
        }
      }
      if (nSample > 0) {
        avgValue = sum / (double) nSample;
      }

      long time1 = System.nanoTime();
      double deltaT = (time1 - time0) / 1.0e+9;
      report(ps, "Tile Load", deltaT, avgValue, nSample);
    }
  }

  public static void main(String[] args) throws IOException {

    PrintStream ps = System.out;

    if (args.length == 0) {
      for (String s : usage) {
        System.out.println(s);
      }
      System.exit(0);
    }

    File file = new File(args[0]);
    ps.println("Reading file " + file.getPath());
    boolean multiThreadingEnabled = false;
    for (int i = 1; i < args.length; i++) {
      if ("-multithread".equalsIgnoreCase(args[i])) {
        multiThreadingEnabled = true;
        break;
      }
    }

    GvrsReadPerformance reader = new GvrsReadPerformance(ps, file);
    reader.setMultiThreadingEnabled(multiThreadingEnabled);
    ps.format("Multi-threading enabled: " + multiThreadingEnabled);

    // Note:  Each of the following tests opens the file,
    // processes its content, and then closes it.  The reason that
    // an open GvrsFile object is not retained between test is that
    // we wish to ensure that each test is clean and has no lingering data
    // retained in its cache from previous tests.
    ps.println("");
    ps.println(
      "Test           Total time (s)    "
      + "Mean value       Samples     Million sample/sec");
    for (int iTest = 0; iTest < 3; iTest++) {
      reader.testRowMajorScan(ps);
      reader.testColumnMajorScan(ps);
      reader.testRowBlockScan(ps);
      reader.testTileBlockScan(ps);
      reader.testTileLoadTime(ps);
      ps.println("");
    }
  }

}
