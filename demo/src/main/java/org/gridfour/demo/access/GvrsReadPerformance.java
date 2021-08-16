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
import org.gridfour.gvrs.GvrsCacheSize;
import org.gridfour.gvrs.GvrsDataType;
import org.gridfour.gvrs.GvrsFile;
import org.gridfour.gvrs.GvrsFileSpecification;

/**
 * A simple demonstration application that reads the entire content of a Gvrs
 * file. Intended to test read operations and measure access time.
 */
public class GvrsReadPerformance {

  File inputFile;
  GvrsFileSpecification spec;
  int nRowsInRaster;
  int nColsInRaster;
  int nRowsInTile;
  int nColsInTile;
  int nRowsOfTiles;
  int nColsOfTiles;

  GvrsReadPerformance(File inputFile) throws IOException {
    this.inputFile = inputFile;
    try (GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      spec = gvrs.getSpecification();
      nRowsInRaster = spec.getRowsInGrid();
      nColsInRaster = spec.getColumnsInGrid();
      nRowsInTile = spec.getRowsInTile();
      nColsInTile = spec.getColumnsInTile();
      nRowsOfTiles = spec.getRowsOfTilesInGrid();
      nColsOfTiles = spec.getColumnsOfTilesInGrid();
    }
  }

  public static void main(String[] args) throws IOException {

    PrintStream ps = System.out;

    if (args.length == 0) {
      System.out.println("No input file specified");
      System.exit(0);
    }
    File file = new File(args[0]);
    ps.println("Reading file " + file.getPath());
    GvrsReadPerformance reader = new GvrsReadPerformance(file);

    for (int iTest = 0; iTest < 3; iTest++) {
      reader.testRowMajorScan(ps);
      reader.testRowBlockScan(ps);
      reader.testColumnMajorScan(ps);
      reader.testTileBlockScan(ps);
      reader.testTileLoadTime(ps);
    }
  }

  void report(PrintStream ps, String label, double deltaT, double avgValue) {
    ps.format("%-15s %12.6f %12.3f%n", label, deltaT, avgValue);
  }

  void testRowMajorScan(PrintStream ps) throws IOException {
    try (GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsDataType dType = spec.getDataType();
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();
      if (dType == GvrsDataType.INTEGER) {
        long sum = 0;
        for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
          for (int iCol = 0; iCol < nColsInRaster; iCol++) {
            int sample = gvrs.readIntValue(iRow, iCol);
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
            float sample = gvrs.readValue(iRow, iCol);
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
      report(ps, "Row Major", deltaT, avgValue);
    }
  }

  void testColumnMajorScan(PrintStream ps) throws IOException {
    try (GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsDataType dType = spec.getDataType();
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();
      if (dType == GvrsDataType.INTEGER) {
        long sum = 0;
        for (int iCol = 0; iCol < nColsInRaster; iCol++) {
          for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
            int sample = gvrs.readIntValue(iRow, iCol);
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
            float sample = gvrs.readValue(iRow, iCol);
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
      report(ps, "Column Major", deltaT, avgValue);
    }
  }

  void testRowBlockScan(PrintStream ps) throws IOException {
    try (GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      gvrs.setTileCacheSize(GvrsCacheSize.Large);
      GvrsDataType dType = spec.getDataType();
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();
      if (dType == GvrsDataType.INTEGER) {
        long sum = 0;
        for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
          float[] block = gvrs.readBlock(iRow, 0, 1, nColsInRaster);
          for (int iCol = 0; iCol < nColsInRaster; iCol++) {
            float sample = block[iCol];
            if (!Float.isNaN(sample)) {
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
            float sample = gvrs.readValue(iRow, iCol);
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
      report(ps, "Row Block", deltaT, avgValue);
    }
  }

  void testTileBlockScan(PrintStream ps) throws IOException {
    try (GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      gvrs.setTileCacheSize(GvrsCacheSize.Small);
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();

      double sum = 0;
      for (int iRow = 0; iRow < nRowsOfTiles; iRow++) {
        for (int iCol = 0; iCol < nColsOfTiles; iCol++) {
          int row0 = iRow * nRowsInTile;
          int col0 = iCol * nColsInTile;
          float[] block = gvrs.readBlock(row0, col0, nRowsInTile, nColsInTile);
          for (float sample : block) {
            if (!Float.isNaN(sample)) {
              sum += sample;
              nSample++;
            }
          }
        }
      }
      if (nSample > 0) {
        avgValue = sum / (double) nSample;
      }

      long time1 = System.nanoTime();
      double deltaT = (time1 - time0) / 1.0e+9;
      report(ps, "Block Test", deltaT, avgValue);
    }
  }

  void testTileLoadTime(PrintStream ps) throws IOException {
    try (GvrsFile gvrs = new GvrsFile(inputFile, "r")) {
      gvrs.setTileCacheSize(GvrsCacheSize.Small);
      double avgValue = 0;
      long nSample = 0;
      long time0 = System.nanoTime();

      double sum = 0;
      for (int iRow = 0; iRow < nRowsOfTiles; iRow++) {
        for (int iCol = 0; iCol < nColsOfTiles; iCol++) {
          int row0 = iRow * nRowsInTile;
          int col0 = iCol * nColsInTile;
          float sample = gvrs.readValue(row0 + 1, col0 + 1);
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
      report(ps, "Tile Load", deltaT, avgValue);
    }
  }

}
