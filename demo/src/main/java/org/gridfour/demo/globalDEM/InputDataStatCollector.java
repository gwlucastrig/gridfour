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
 * 12/2019  G. Lucas     Created
 *
 * Notes:
 *
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.globalDEM;

/**
 * Provides methods for collecting general statistics about the input data.
 */
class InputDataStatCollector {

  private final int defMin;
  private final int defMax;
  private final int nCounter;
  private final int[] counter;
  private double min = Double.MAX_VALUE;
  private double max = Double.MIN_VALUE;
  long nSamples;
  long sumSamples;
  int base;
  double scale;

  InputDataStatCollector(int minValue, int maxValue, double scale) {
    defMin = minValue;
    defMax = maxValue;
    nCounter = (int) ((defMax - defMin + 1) * scale);
    counter = new int[nCounter + 1];
    base = (int) (defMin * scale);
    this.scale = (float) scale;
  }

  void addSample(double inputSample) {
    if (Double.isNaN(inputSample)) {
      return;
    }

    if (inputSample < min) {
      min = inputSample;
    }
    if (inputSample > max) {
      max = inputSample;
    }

    int sample = (int) (inputSample * scale + 0.5);
    int index = sample - base;
    if (index < 0 || index > nCounter) {
      return;
    }
    counter[index]++;
    sumSamples += sample;
    nSamples++;

  }

  double getEntropy() {
    if (nSamples == 0) {
      return 0;
    }
    double d = (double) nSamples;
    double sum = 0;
    for (int i = 0; i < counter.length; i++) {
      if (counter[i] > 0) {
        double p = (double) counter[i] / d;
        double s = p * Math.log(p);
        sum += s;
      }
    }
    return -sum / Math.log(2.0);
  }

  double getMean() {
    if (nSamples == 0) {
      return 0;
    }
    return (double) sumSamples / (double) nSamples / scale;
  }

}
