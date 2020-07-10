/* --------------------------------------------------------------------
 * The MIT License
 *
 * Copyright (C) 2020  Gary W. Lucas.

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
 * 04/2020  G. Lucas     Created
 *
 * Notes:
 *
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.entropy;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.gridfour.demo.utils.TestOptions;
import org.gridfour.g93.G93CacheSize;
import org.gridfour.g93.G93DataType;
import org.gridfour.g93.G93File;
import org.gridfour.g93.G93FileSpecification;
import org.gridfour.util.KahanSummation;


/**
 * Provides a tool for obtaining an accurate value for the entropy of a G93 file
 * (within the precision of conventional floating point representations)
 * <p>
 * Although this class does provide a main method, it can also be used within an
 * application.
 */
public class EntropyTabulator {

  private static final String TEMP_COUNT_FILE_NAME
      = "TabulateEntropyTemporary.g93";

  private static final String[] USAGE = {
    "TabulateEntropy",
    "Arguments:",
    "   -in   <file>",
    "         specifies the input G93 file for evaluation",
    "   -showProgress, -noShowProgress",
    "         controls whether progress messages are printed during evaluation",
    "",
    "   Note: When running, this routine creates a temporary file",
    "         that is 16 Gigabytes in size.  The file is removed upon",
    "         completion of processing"
  };

  /**
   * The main method for the TabulateEntropy application
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      for (String s : USAGE) {
        System.err.println(s);
      }
      System.exit(0);
    }

    TestOptions options = new TestOptions();
    boolean[] matched = options.argumentScan(args);
    File inputFile = options.getInputFile();
    if (inputFile == null) {
      throw new IllegalArgumentException("No specification for input file");
    }
    if (!inputFile.exists()) {
      throw new IllegalArgumentException("Input file does not exist: "
          + inputFile.getPath());
    }
    if (!inputFile.canRead()) {
      throw new IllegalArgumentException("Unable to access input file: "
          + inputFile.getPath());
    }

    EntropyTabulator tabulator = new EntropyTabulator();
    tabulator.process(System.out, inputFile, options.isShowProgressEnabled());

  }

  /**
   * Standard constructor
   */
  public EntropyTabulator() {

  }

  /**
   * Process the specified G93 file and write a report to the specified print
   * stream.
   * <p>
   * If configured to do so, this method will write progress reports to the
   * specified print stream.
   *
   * @param ps a valid print stream, System&#46;out is a valid candidate
   * @param inputFile a reference to a G93 file
   * @param showProgress indicates if progress reports are to be printed during
   * processing
   * @return on successful completion, a valid floating-point value; otherwise,
   * a Double&#46;NaN.
   */
  public double process(PrintStream ps, File inputFile, boolean showProgress) {
    double entropy = Double.NaN;

    ps.format("%nEntropy tabulation for G93 files%n");
    Locale locale = Locale.getDefault();
    Date date = new Date();
    SimpleDateFormat sdFormat = new SimpleDateFormat("dd MMM yyyy HH:mm z", locale);
    ps.format("Date of Execution: %s%n", sdFormat.format(date));

    String inputPath = inputFile.getPath();
    ps.format("Input file:  %s%n", inputPath);

    File parent = inputFile.getParentFile();
    File countsFile = new File(parent, TEMP_COUNT_FILE_NAME);

    // Define the specs for the entropy stats file
    G93FileSpecification countsSpec
        = new G93FileSpecification(65536, 65536, 256, 256);
    countsSpec.setDataCompressionEnabled(false);
    countsSpec.setDataModelInt(1);

    try (G93File source = new G93File(inputFile, "r");
        G93File counts = new G93File(countsFile, countsSpec);) {
      G93FileSpecification sourceSpec = source.getSpecification();
      int nRowsInSource = sourceSpec.getRowsInGrid();
      int nColsInSource = sourceSpec.getColumnsInGrid();
      int nRowsOfTilesInSource = sourceSpec.getRowsOfTilesInGrid();
      int nColsOfTilesInSource = sourceSpec.getColumnsOfTilesInGrid();
      int nRowsInTile = sourceSpec.getRowsInTile();
      int nColsInTile = sourceSpec.getColumnsInTile();
      G93DataType dataType = sourceSpec.getDataType();
      long nSamples = 0;
      long nSymbols = 0;

      ps.println("Source File " + inputFile.getName());
      ps.format("   Rows:      %8d%n", nRowsInSource);
      ps.format("   Columns:   %8d%n", nColsInSource);
      source.setTileCacheSize(G93CacheSize.Small);
      counts.setTileCacheSize(2000);
      long time0 = System.currentTimeMillis();
      if (showProgress) {
        ps.format("Initializing temporary entropy tabulation file %s%n",
            countsFile.getPath());
      }
      for (int iRow = 0; iRow < 65536; iRow++) {
        for (int iCol = 0; iCol < 65535; iCol++) {
          counts.storeIntValue(iRow, iCol, 0);
        }
      }

      // -----------------------------------------------------------------
      // Package the data
      if (showProgress) {
        ps.format("Initialization done in %d ms%n",
            System.currentTimeMillis() - time0);
        ps.format("Beginning tabulation");
      }
      time0 = System.currentTimeMillis();
      for (int iTileRow = 0; iTileRow < nRowsOfTilesInSource; iTileRow++) {
          if (showProgress && iTileRow > 0) {
          long time1 = System.currentTimeMillis();
          double deltaT = time1 - time0;
          double rate = (iTileRow + 1) / deltaT;  // rows per millis
          int nRemaining = nRowsOfTilesInSource - iTileRow;
          long remainingT = (long) (nRemaining / rate);
          Date d = new Date(time1 + remainingT);
          ps.format("Surveyed %d rows, %4.1f%% of total, est completion at %s%n",
              iTileRow * nRowsInTile,
              100.0 * (double) iTileRow / (nRowsOfTilesInSource - 1.0), d);
          ps.flush();
        }
        int row0 = iTileRow * nRowsInTile;
        int row1 = row0 + nRowsInTile;
        if (row1 > nRowsInSource) {
          row1 = nRowsInSource;
        }
        for (int iTileCol = 0; iTileCol < nColsOfTilesInSource; iTileCol++) {
          int col0 = iTileCol * nColsInTile;
          int col1 = col0 + nColsInTile;
          if (col1 > nColsInSource) {
            col1 = nColsInSource;
          }

          for (int iRow = row0; iRow < row1; iRow++) {
            for (int iCol = col0; iCol < col1; iCol++) {
              int bits;
              if (dataType == G93DataType.Float4) {
                float sample = source.readValue(iRow, iCol);
                bits = Float.floatToRawIntBits(sample);
              } else {
                bits = source.readIntValue(iRow, iCol);
              }
              long longIndex = ((long) bits) & 0x00ffffffffL;
              long longRow = longIndex / 65536L;
              long longCol = longIndex - longRow * 65536L;
              int count = counts.readIntValue((int) longRow, (int) longCol);
              counts.storeIntValue((int) longRow, (int) longCol, count + 1);
              nSamples++;
              if (count == 0) {
                nSymbols++;
              }
            }
          }
        }
      }

      counts.flush();
      source.close();
      long time1 = System.currentTimeMillis();
      double timeToProcess = (time1 - time0) / 1000.0;
      if (showProgress) {
        ps.format("Finished surveying source file in %4.1f seconds%n", timeToProcess);
        ps.format("Performing tabulation of count data%n");
        ps.flush();
      }

      time0 = System.currentTimeMillis();
      double nSamplesDouble = (double) nSamples;
      int maxCount = 0;
      long nUnique = 0;
      long nRepeated = 0;

      KahanSummation ks = new KahanSummation();
      for (int iRow = 0; iRow < 65536; iRow++) {
        if (showProgress && (iRow & 1023) == 0 && iRow > 0) {
          time1 = System.currentTimeMillis();
          double deltaT = time1 - time0;
          double rate = (iRow + 1) / deltaT;  // rows per millis
          int nRemaining = 65536 - iRow;
          long remainingT = (long) (nRemaining / rate);
          Date d = new Date(time1 + remainingT);
          ps.format("Tabulated %d rows, %4.1f%% of total, est completion at %s%n",
              iRow, 100.0 * (double) iRow / 65536.0, d);
          ps.flush();
        }
        for (int iCol = 0; iCol < 65536; iCol++) {
          int count = counts.readIntValue(iRow, iCol);
          if (count > 0) {
            double p = (double) count / nSamplesDouble;
            double s = -p * Math.log(p);
            ks.add(s);
            if (count > maxCount) {
              maxCount = count;
            }
            if (count == 1) {
              nUnique++;
            } else {
              nRepeated++;
            }
          }
        }
        }
        // get the syn if entropy calculations, then convert to log base 2
        entropy = ks.getSum() / Math.log(2.0);

      time1 = System.currentTimeMillis();
      double timeToTabulate = (time1 - time0) / 1000.0;
      ps.format("Finished processing file in %4.1f seconds%n", timeToTabulate);
      ps.format("Samples:          %12d%n", nSamples);
      ps.format("Unique Symbols:   %12d%n", nUnique);
      ps.format("Repeated Symbols: %12d%n", nRepeated);
      ps.format("Total symbols:    %12d%n", nSymbols);
      ps.format("Max count:        %12d%n", maxCount);
      ps.format("Entropy:          %9.5f%n ", entropy);
    } catch (IOException ioex) {
      ps.println("IOException accessing " + inputFile.getPath() + ", " + ioex.getMessage());
      ioex.printStackTrace(ps);
    }

    countsFile.delete();
    return entropy;
  }

}
