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
 *   the first 2 rows and then the first two columns are pre-populated using
 * simple differences. After that, the remainder of the grid is populated
 * using the Lewis and Smith Optimal Predictor
 * -----------------------------------------------------------------------
 */
package org.gridfour.g93.lsop.compressor;

import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.gridfour.util.CodecM32;

/**
 * Applies the methods of Lewis and Smith (1994) using optimal predictors
 * for data compression.
 * <p>
 * The method used for this class is based on the PDF document:
 * <cite>Lewis, M. and Smith, D. H. (1994). "Optimal Predictors for the
 * Data Compression of Digital Elevation
 * Models using the Method of Lagrange Multipliers" </cite>
 *
 */
public class LsOptimalPredictor {

  LsOptimalPredictorResult encode(
    int nRows,
    int nColumns,
    int[] values) {

    if (nRows < 4 || nColumns < 4) {
      return null;
    }

    // Initialze storage for up to
    //    1) two full initial rows
    //    2) two columns in each subsequent row
    //    3) With 6 bytes per value
    int n = (nColumns * 2 + (nRows - 2) * 2);
    CodecM32 initializationCodec = new CodecM32(n);
    int seed = values[0];

    // for the initialization, we use the simple differencing predictor
    // to populate the first two rows, and then the first two columns
    // of all subsequent rows.
    long prior = seed;
    for (int i = 1; i < nColumns; i++) {
      long test = values[i];
      long delta = test - prior;
      if (isDeltaOutOfBounds(delta)) {
        return null;
      }
      initializationCodec.encode((int) delta);
      prior = test;
    }

    // the second row
    prior = values[0];
    for (int i = 0; i < nColumns; i++) {
      long test = values[i + nColumns];
      long delta = test - prior;
      if (isDeltaOutOfBounds(delta)) {
        return null;
      }
      initializationCodec.encode((int) delta);
      prior = test;
    }

    // all subsequent rows, nColumns each
    for (int iRow = 2; iRow < nRows; iRow++) {
      int index = iRow * nColumns;
      prior = values[index - nColumns];
      for (int i = 0; i < 2; i++) {
        long test = values[index++];
        long delta = test - prior;
        if (isDeltaOutOfBounds(delta)) {
          return null;
        }
        initializationCodec.encode((int) delta);
        prior = test;
      }
    }

    // now process the samples and compute the 3x3 optimal predictor
    double[] ud = computeCoefficients(nRows, nColumns, values);
    if (ud == null) {
      return null;
    }

    // although the solution is in doubles, we wish to store floats
    // in our output.  in order to ensure correct calculations, we need to
    // work with floats.
    float[] u = new float[9];
    for (int i = 0; i < ud.length; i++) {
      u[i] = (float) ud[i];
    }

    float u0 = u[0];
    float u1 = u[1];
    float u2 = u[2];
    float u3 = u[3];
    float u4 = u[4];
    float u5 = u[5];
    float u6 = u[6];
    float u7 = u[7];

    // Allocate a new mCodec with storage for the interior codes
    // use it to store the delta values.
    n = (nRows - 2) * (nColumns - 2);
    CodecM32 interiorCodec = new CodecM32(n);
    for (int iRow = 2; iRow < nRows; iRow++) {
      for (int iCol = 2; iCol < nColumns; iCol++) {
        int index = iRow * nColumns + iCol;
        float p
          = u0 * values[index - 1]
          + u1 * values[index - nColumns - 1]
          + u2 * values[index - nColumns]
          + u3 * values[index - 2]
          + u4 * values[index - nColumns - 2]
          + u5 * values[index - 2 * nColumns - 2]
          + u6 * values[index - 2 * nColumns - 1]
          + u7 * values[index - 2 * nColumns];
        int delta = values[index] - (int) (p + 0.5f);
        interiorCodec.encode(delta);
      }
    }

    int nInitializationCodes = initializationCodec.getEncodedLength();
    byte[] initializationEncoding = initializationCodec.getEncoding();
    int nInteriorCodes = interiorCodec.getEncodedLength();
    byte[] interiorEncoding = interiorCodec.getEncoding();

    return new LsOptimalPredictorResult(seed, u,
      nInitializationCodes, initializationEncoding,
      nInteriorCodes, interiorEncoding);
  }

  private boolean isDeltaOutOfBounds(long delta) {
    if (delta < Integer.MIN_VALUE) {
      return true;
    } else if (delta > Integer.MAX_VALUE) {
      return true;
    }
    return false;
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
   * @param nRows a value of 2 or greater
   * @param nColumns a value of 2 or greater
   * @param values an array of values given in row-major order.
   * @return if successful, an valid array; otherwise, null.
   */
  public double[] computeCoefficients(int nRows, int nColumns, int[] values) {
    if (nRows < 4 || nColumns < 4) {
      return null;
    }
    // now process the samples and compute the 3x3 optimal predictor
    double[] z = new double[9];
    double[] s = new double[9];
    double c[][] = new double[9][9];
    for (int iRow = 2; iRow < nRows; iRow++) {
      for (int iCol = 2; iCol < nColumns; iCol++) {
        int index = iRow * nColumns + iCol;
        z[0] = values[index];
        z[1] = values[index - 1];                // z[row][col-1]
        z[2] = values[index - nColumns - 1];     // z[row-1][col-1]
        z[3] = values[index - nColumns];         // z[row-1][col]
        z[4] = values[index - 2];                // z[row][col-2]
        z[5] = values[index - nColumns - 2];     // z[row-1][col-2]
        z[6] = values[index - 2 * nColumns - 2]; // z[row-2][col-2]
        z[7] = values[index - 2 * nColumns - 1]; // z[row-2][col-1]
        z[8] = values[index - 2 * nColumns];     // z[row-2][col]
        for (int i = 0; i < 9; i++) {
          s[i] += z[i];
        }
        for (int i = 0; i < 9; i++) {
          for (int j = i; j < 9; j++) {
            c[i][j] += z[i] * z[j];
          }
        }
      }
    }
    // transcribe symmetric part of the summation matrix
    for (int i = 1; i < 9; i++) {
      for (int j = 0; j < i; j++) {
        c[i][j] = c[j][i];
      }
    }

    // populate the design matrix
    double[][] m = new double[9][9];
    for (int i = 1; i < 9; i++) {
      for (int j = 1; j < 9; j++) {
        m[i - 1][j - 1] = c[i][j];
      }
      m[i - 1][8] = s[i];
    }
    for (int j = 1; j < 9; j++) {
      m[8][j - 1] = s[j];
    }

    // m[8][8] = 0
    RealMatrix mX = new BlockRealMatrix(m);
    QRDecomposition cd = new QRDecomposition(mX); // NOPMD
    DecompositionSolver cdSolver = cd.getSolver();

    RealMatrix cInv = null;
    try {
      cInv = cdSolver.getInverse();
    } catch (SingularMatrixException sme) {
      return null;
    }

    double[][] b = new double[9][1];
    for (int i = 1; i < 9; i++) {
      b[i - 1][0] = c[0][i];
    }
    b[8][0] = s[0];
    RealMatrix mB = new BlockRealMatrix(b);
    RealMatrix mU = cInv.multiply(mB);

    return mU.getColumn(0);

  }
}