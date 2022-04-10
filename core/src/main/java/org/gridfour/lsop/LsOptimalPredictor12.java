/*
 * The MIT License
 *
 * Copyright 2020 by Gary W. Lucas
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
 * 07/2020  G. Lucas     Created
 *
 * Notes:
 *  The design of this class needs improvement.  At present, it combines
 * logic specific to the Gridfour encoding with logic that is related to
 * the Optimal Predictor algorithm.  I would like to move the Gridfour
 * stuff (the initializations and M32 encoding) into the LsEncoder12 class
 * and make this class be a clean implementation of Optimal Predictors.
 *
 * One feature of Optimal Predictors is that certain grid cells are
 * "unreachable" to the predictor and must be populated using other means.
 *    1.  The first row and first column are initialized using the
 *        simple Differencing Predictor that is used for other Gridfour encoders.
 *    2.  The second row and second column are initialized using
 *        the simple Triangle Predictor that is used for other Gridfour encoders.
 *    3.  The rows in the interior area of the grid are initialized using
 *        Optimal Predictors, except for the last two columns in each row
 *        which are also unreachable.  These are populated using
 *        the triangle predictor.
 *
 *  This logic produces two separate packings for the compressed output.
 *  The first is for the initialization, which covers the first two rows
 *  of cells as well as the first and last pairs of columns.
 *  The second is for the interior part of the grid, which is created
 *  using Optimal Predictors.  The reason for the separate packing is that
 *  they have significantly different statistical properties (symbol
 *  frequencies) and compress more effectively when separated than when
 *  lumped together.
 *
 *  In future work, it may be advantageous to replace the Triangle Predictor
 *  with Smith and Lewis' 3-point variation of the Optimal Predictor.
 *  This approach should reduce the size of the initializer storage, though
 *  the savings most likely would be modest since the initializer itself
 *  only makes a small contribution to the overall storage size.
 * -----------------------------------------------------------------------
 */
package org.gridfour.lsop;


import org.gridfour.compress.CodecM32;
import org.gridfour.util.jama.LUDecomposition;
import org.gridfour.util.jama.Matrix;

/**
 * Applies the methods of Lewis and Smith (1994) using optimal predictors
 * for data compression.
 * <p>
 * The method used for this class is based on the PDF document:
 * <cite>Lewis, M. and Smith, D. H. (1994). "Optimal Predictors for the
 * Data Compression of Digital Elevation
 * Models using the Method of Lagrange Multipliers" </cite>
 * <p>
 * The floating-point arithmetic operations for the encode method in this class
 * are performed using the Java strictfp specification. This design choice
 * is essential to the correct operation of this module. Because one of the goals
 * for Gridfour is to facilitate portability to other development environments,
 * it is essential that the math operations performed here be reliably
 * reproducible across platforms and programming languages.
 *
 */
public class LsOptimalPredictor12 {

  double errorSum;
  double errorSquaredSum;
  double errorAbsSum;
  long errorCount;
  long deltaZeroCount;

  /**
   * Encode the specified raster using the Smith-Lewis Optimal Predictor
   * 12-coefficient variation
   *
   * @param nRows the number of rows in the raster
   * @param nColumns the number of columns in the raster
   * @param values the values for the raster given as an array of integers
   * in row-major order.
   * @return if successful, a valid results instance; otherwise, a null.
   */
  strictfp public LsOptimalPredictorResult encode(
    int nRows,
    int nColumns,
    int[] values) {

    if (nRows < 6 || nColumns < 6) {
      return null;
    }

    // Initialze storage for
    //    1) two full initial rows
    //    2) first two columns in each subsequent row
    //    3) last two columns in each subsequent row
    //  Constructor CodecM32 allocates 6 bytes per value
    int n = nColumns * 4 + nRows * 2 - 8;
    CodecM32 initializationCodec = new CodecM32(n);

    // The Initialization
    // It is necessary to initialize the first two rows and the
    // first two columns and the last two columns, since these are "unreachable"
    // cells that cannot be populated by the predictor.  We use the
    // differencing predictor for the first row and first column.
    // Then we use the triangle predictor for the second row and second column.
    // Finally, we use the triangle predictor for the last two columns. But
    // when decompressing, the final two columns will not be extracted
    // until after the interior is fully populated.

    // Fist row, start from column 1, do not encode column 0
    int seed = values[0];
    long prior = seed;
    for (int i = 1; i < nColumns; i++) {
      long test = values[i];
      long delta = test - prior;
      initializationCodec.encode((int) delta);
      prior = test;
    }

    // the first column ------------------------------------
    prior = values[0];
    for (int i = 1; i < nRows; i++) {
      long test = values[i * nColumns];
      long delta = test - prior;
      initializationCodec.encode((int) delta);
      prior = test;
    }

    // now use the Triangle Predictor-------------------------
    // the second row
    for (int i = 1; i < nColumns; i++) {
      int index = nColumns + i;
      long a = values[index - 1];
      long b = values[index - nColumns - 1];
      long c = values[index - nColumns];
      long test = values[index];
      long delta = test - ((a + c) - b);
      initializationCodec.encode((int) delta);

    }

    // The second column
    for (int i = 2; i < nRows; i++) {
      int index = i * nColumns + 1;
      long a = values[index - 1];
      long b = values[index - nColumns - 1];
      long c = values[index - nColumns];
      long test = values[index];
      long delta = test - ((a + c) - b);
      initializationCodec.encode((int) delta);
    }

    // The last two columns
      for (int i = 2; i < nRows; i++) {
          int index = i * nColumns + nColumns - 2;
          long a = values[index - 1];
          long b = values[index - nColumns - 1];
          long c = values[index - nColumns];
          long test = values[index];
          long delta = test - ((a + c) - b);
          initializationCodec.encode((int) delta);

          index++;
          a = values[index - 1];
          b = values[index - nColumns - 1];
          c = values[index - nColumns];
          test = values[index];
          delta = test - ((a + c) - b);
          initializationCodec.encode((int) delta);
      }


    // now process the samples and compute the 12-by-12 optimal predictor
    double[] ud = computeCoefficients(nRows, nColumns, values);
    if (ud == null) {
      return null;
    }

    // although the solution is in doubles, we wish to store floats
    // in our output.  in order to ensure correct calculations, we need to work
    // with floats. So we transcribe the coefficients to an array of floats
    float[] u = new float[13];
    for (int i = 0; i < ud.length; i++) {
      u[i] = (float) ud[i];
    }

    // Although the array u[] is indexed from zero, the coefficients
    // for the predictors are numbered starting at one. Here we copy them
    // out so that the code will match up with the indexing used in the
    // original papers.  There may be some small performance gain to be
    // had by not indexing the array u[] multiple times in the loop below,
    // but that is not the main motivation for using the copy variables.
    float u1 = u[0];
    float u2 = u[1];
    float u3 = u[2];
    float u4 = u[3];
    float u5 = u[4];
    float u6 = u[5];
    float u7 = u[6];
    float u8 = u[7];
    float u9 = u[8];
    float u10 = u[9];
    float u11 = u[10];
    float u12 = u[11];
    // u[12] is the coefficient for the lagrange multplier itself,
    // which we do not use in the predictor

    // Allocate a new mCodec with storage for the interior codes
    // use it to store the delta values.
    n = (nRows - 2) * (nColumns - 4);
    CodecM32 interiorCodec = new CodecM32(n);
    for (int iRow = 2; iRow < nRows; iRow++) {
      for (int iCol = 2; iCol < nColumns - 2; iCol++) {
        int index = iRow * nColumns + iCol;
        float p
          = u1 * values[index - 1]
          + u2 * values[index - nColumns - 1]
          + u3 * values[index - nColumns]
          + u4 * values[index - nColumns + 1]
          + u5 * values[index - nColumns + 2]
          + u6 * values[index - 2]
          + u7 * values[index - nColumns - 2]
          + u8 * values[index - 2 * nColumns - 2]
          + u9 * values[index - 2 * nColumns - 1]
          + u10 * values[index - 2 * nColumns]
          + u11 * values[index - 2 * nColumns + 1]
          + u12 * values[index - 2 * nColumns + 2];
        int estimate = StrictMath.round(p);
        int delta = values[index] - estimate;
        interiorCodec.encode(delta);
        double err = values[index] - p;
        errorSum += err;
        errorSquaredSum += err * err;
        errorAbsSum += Math.abs(err);
        errorCount++;
        if (delta == 0) {
          deltaZeroCount++;
        }
      }
    }

    int nInitializationCodes = initializationCodec.getEncodedLength();
    byte[] initializationEncoding = initializationCodec.getEncoding();
    int nInteriorCodes = interiorCodec.getEncodedLength();
    byte[] interiorEncoding = interiorCodec.getEncoding();

    return new LsOptimalPredictorResult(seed, 12, u,
      nInitializationCodes, initializationEncoding,
      nInteriorCodes, interiorEncoding);
  }


  /**
   * Computes the coefficients for an optimal predictor. In the unusual case
   * that no solution is available, or that the input data size is inadequate,
   * a null value will be returned.
   *
   * The layout of the coefficients is as shown below
   * <pre>
   *    row i:      u3   u0   S(i,j)
   *    row i-1:    u4   u1   u2
   *    row i-2:    u5   u6   u7
   * </pre>
   *
   * @param nRows a value of 6 or greater
   * @param nColumns a value of 6 or greater
   * @param values an array of values given in row-major order.
   * @return if successful, an valid array; otherwise, null.
   */
  public double[] computeCoefficients(int nRows, int nColumns, int[] values) {
    if (nRows < 6 || nColumns < 6) {
      return null;
    }
    // now process the samples and compute the 12 coefficient optimal predictor
    double[] z = new double[13];
    double[] s = new double[13];
    double c[][] = new double[13][13];
    for (int iRow = 2; iRow < nRows; iRow++) {
      for (int iCol = 2; iCol < nColumns - 2; iCol++) {
        int index = iRow * nColumns + iCol;
        z[0] = values[index];
        z[1] = values[index - 1];
        z[2] = values[index - nColumns - 1];
        z[3] = values[index - nColumns];
        z[4] = values[index - nColumns + 1];
        z[5] = values[index - nColumns + 2];
        z[6] = values[index - 2];
        z[7] = values[index - nColumns - 2];
        z[8] = values[index - 2 * nColumns - 2];
        z[9] = values[index - 2 * nColumns - 1];
        z[10] = values[index - 2 * nColumns];
        z[11] = values[index - 2 * nColumns + 1];
        z[12] = values[index - 2 * nColumns + 2];
        for (int i = 0; i < 13; i++) {
          s[i] += z[i];
        }
        for (int i = 0; i < 13; i++) {
          for (int j = i; j < 13; j++) {
            c[i][j] += z[i] * z[j];
          }
        }
      }
    }
    // transcribe symmetric part of the summation matrix
    for (int i = 1; i < 13; i++) {
      for (int j = 0; j < i; j++) {
        c[i][j] = c[j][i];
      }
    }

    // populate the design matrix
    double[][] m = new double[13][13];
    for (int i = 1; i < 13; i++) {
      for (int j = 1; j < 13; j++) {
        m[i - 1][j - 1] = c[i][j];
      }
      m[i - 1][12] = s[i];
    }
    for (int j = 1; j < 13; j++) {
      m[12][j - 1] = s[j];
    }

    double[][] b = new double[13][1];
    for (int i = 1; i < 13; i++) {
      b[i - 1][0] = c[0][i];
    }
    b[12][0] = s[0];

    try{
        Matrix mat = new Matrix(m, 13, 13);
        LUDecomposition lud = new LUDecomposition(mat);
        Matrix solution = lud.solve(new Matrix(b, 13, 1));
        double[] result = new double[12];
        for (int i = 0; i < 12; i++) {
            result[i] = solution.get(i, 0);
        }
        return result;
    }catch(RuntimeException rex){
        return null;
    }

  }

  /**
   * Gets the mean error for the predictor. The error is defined as the
   * the value minus the predictor computed using single-precision
   * floating-point
   * values (4-byte floats)).
   *
   * @return a valid floating point value
   */
  public double getMeanError() {
    if (errorCount == 0) {
      return 0;
    }
    return errorSum / errorCount;
  }

  /**
   * Gets the root mean squared error (RMSE) for the predictor.
   *
   * @return a valid floating point value
   */
  public double getRootMeanSquaredError() {
    if (errorCount == 0) {
      return 0;
    }
    return Math.sqrt(errorSquaredSum / errorCount);
  }

  /**
   * Gets the percent of the integer coded residuals that have a
   * zero value.
   *
   * @return a value in the range zero to 100.
   */
  public double getPercentZeroIntegerResiduals() {
    if (errorCount == 0) {
      return 0;
    }
    return 100.0 * (double) deltaZeroCount / (double) errorCount;
  }

  public double getMeanAbsError() {
    if (errorCount == 0) {
      return 0;
    }
    return Math.sqrt(errorAbsSum / errorCount);
  }
}
