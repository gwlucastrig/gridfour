/*
 * The MIT License
 *
 * Copyright 2022 by Gary W. Lucas
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
 * 08/2016  G. Lucas     Created for the Tinfour project
 * 07/2022  G. Lucas     Adapted for Gridfour
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.lsop;

import java.io.PrintStream;

/**
 * Tabulates mean and variance for "delta" values obtained from
 * tests in which the result is found by comparing an estimated value
 * (such as an interpolation result) with a known or expected value.
 * The tabulator collects sums using both the signed values of the deltas as
 * well as the absolute values of the deltas. The Kahan Summation algorithm
 * is used to provide extended precision when processing a very large
 * number of samples.
 */
class TabulatorDelta {

  double sumD; // sum of abs(delta)
  double sumD2; // sum of delta^2
  double sumSignedD;
  double maxD = Double.NEGATIVE_INFINITY;  // max signed delta
  double minD = Double.POSITIVE_INFINITY;  // nim signed delta

  double cD; // compensator for Kahan Summation, sum delta
  double cD2;// compensator, sum delta squared

  int nD; // number of tabulated deltas
  int nNaN; // number of NaN's

  /**
   * Adds the specified delta value to the running tally of observed value.
   *
   * @param delta a valid floating point value, NaN's are ignored.
   */
  void tabulate(double delta) {
    double d2 = delta * delta;
    double dAbs = Math.abs(delta);
    if (Double.isNaN(delta)) {
      nNaN++;
    } else {
      double y, t;
      nD++;
      sumSignedD += delta;

      // to avoid numeric issues, apply Kahan summation algorithm.
      y = dAbs - cD;
      t = sumD + y;
      cD = (t - sumD) - y;
      sumD = t;

      y = d2 - cD2;
      t = sumD2 + y;
      cD2 = (t - sumD2) - y;
      sumD2 = t;

      if (delta > maxD) {
        maxD = delta;
      }
      if (delta < minD) {
        minD = delta;
      }
    }
  }

  /**
   * Print a summary of the mean, standard deviation, min, max, and
   * sum of signed errors.
   *
   * @param ps the print stream to which output is to be streamed
   * @param label a label for the beginning of the output line
   */
  void summarize(PrintStream ps, String label) {
    double meanE = 0;
    double sigma = 0;
    double rmse = 0;
    if (nD > 1) {
      meanE = sumD / nD;
      // to reduce errors due to loss of precision,
      // rather than using the conventional form for std dev
      // nE*sumE2-sumE*sumE)/((nE*(nE-1))
      // use the form below
      sigma = Math.sqrt((sumD2 - (sumD / nD) * sumD) / (nD - 1));
      rmse = getRMSE();
    }
    ps.format("%-25.25s %13.6f %13.6f %13.6f %10.3f %8.3f %9.3f%n",
      label, rmse, meanE, sigma, minD, maxD, sumSignedD);
  }

  /**
   * Get the mean of the absolute values of the input sample values.
   *
   * @return a valid floating point number, zero if no input has occurred.
   */
  double getMeanAbsValue() {
    if (nD == 0) {
      return 0;
    }
    return sumD / nD;
  }

  /**
   * Get an unbiased estimate of the standard deviation of the population
   * based on the tabulated samples.
   *
   * @return the standard deviation of the absolute values of the inputs,
   * or zero if insufficient data is available.
   */
  double getStdDevAbsValue() {
    if (nD < 2) {
      return 0;
    }

    // to reduce errors due to loss of precision,
    // rather than using the conventional form for std dev
    // nE*sumE2-sumE*sumE)/((nE*(nE-1))
    // use the form below
    return Math.sqrt((sumD2 - (sumD / nD) * sumD) / (nD - 1));
  }

  /**
   * Get the signed minimum value of the input samples
   *
   * @return a valid floating point number
   */
  double getMinValue() {
    return minD;
  }

  /**
   * Get the signed maximum value of the input samples
   *
   * @return a valid floating point number
   */
  double getMaxValue() {
    return maxD;
  }

  /**
   * Gets the sum of the signed sample values as input into this tabulator.
   * In practice, a sum with a large positive or negative value would
   * mean that the process used to estimate values consistently
   * overshot or undershot the actual value.
   *
   * @return the sum of the signed values.
   */
  double getSumSignedValues() {
    return this.sumSignedD;
  }

  /**
   * Get the mean for the true values of the tabulated entries.
   * @return if specified, a valid floating-point value; otherwise, a zero.
   */
  double getMeanSignedValues() {
    if (nD == 0) {
      return 0;
    } else {
      return this.sumSignedD / this.nD;
    }
  }

  /**
   * Gets the number of sample values passed into this instance of the
   * tabulator.
   *
   * @return a positive, potentially zero value.
   */
  int getNumberSamples() {
    return nD;
  }

  /**
   * Get the root mean squared error (RMSE)
   *
   * @return a positive value or zero if insufficient data is available.
   */
  double getRMSE() {
    if (nD < 2) {
      return 0;
    }
    return Math.sqrt(sumD2 / (nD - 1));
  }

}
