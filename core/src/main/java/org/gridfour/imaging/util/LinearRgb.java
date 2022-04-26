/*
 * The MIT License
 *
 * Copyright 2022 G. W. Lucas.
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
 * 04/2022  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.imaging.util;

/**
 * Provides utilities for converting data from standard RGB (sRGB) to
 * linear RGB. The source for the computations used in this class is
 * <a href="https://en.wikipedia.org/wiki/SRGB"> https://en.wikipedia.org/wiki/SRGB</a>
 * <p>
 * <strong>Why this class exists: </strong>  The standard RGB (sRGB) color
 * space is optimized for the visual response of the human eye. As such,
 * it is based on a non-linear response to intensity values. This
 * approach works well for display purposes, but has a disadvantage
 * when used for interpolation, color-blending, or other purposes
 * which would benefit from a linear intensity scale. Therefore, in
 * image processing applications it is common to map the standard
 * RGB (sRGB) to a linear form.
 * <p>
 * The conversion between non-linear and linear intensities depends
 * on a power function and is sometimes computationally expensive. To streamline
 * that process, this class implements a logic to perform the conversion
 * using a lookup table. The sRGB to linear RGB conversion is straightforward
 * because it maps simple byte values (in the range 0 to 255) to
 * floating-point values (in the range 0 to 1.0). The conversion from linear
 * RGB to sRGB is more complicated. While a simple lookup operation is
 * feasible, the operations results in a small error (one level of intensity)
 * in approximately 0.7 percent of all cases. This class trades uses an interpolation
 * technique to reduce the error to less than 0.1 percent. The interpolation
 * is more costly than a simple look up, but requires about half the processing
 * time of the direct power function. The resulting errors tend to be due
 * to round off of values very close to the midpoint between two integral
 * values (for example, a value of 100.499 for the interpolated method versus
 * 100.5001 for the directly computed method).
 * <p>
 * <strong>Using this class: </strong> This class is implemented as
 * a thread-safe singleton.  You may invoke it as shown in the code example
 * below:
 * <pre>
 *  Color orange = Color.orange;
 *   int r = orange.getRed();
 *   int g = orange.getGreen();
 *   int b = orange.getBlue();
 *
 *   LinearRgb linearRgb = LinearRgb.getInstance();  // get single instance
 *   float rLinear = linearRgb.standardToLinear(r);  // value in range 0 to 1
 *   float gLinear = linearRgb.standardToLinear(g);  // value in range 0 to 1
 *   float bLinear = linearRgb.standardToLinear(b);  // value in range 0 to 1
 *
 *   double yLinear = 0.2126*rLinear + 0.7152*gLinear+0.0722*bLinear;
 *   int gray = linearRgb.linearToStandard(yLinear);
 *   Color orange2Grayscale = new Color(gray, gray, gray);
 * </pre>
 * <p>
 * <strong>Performance: </strong> The performance of this class was tested
 * on a mid-level laptop processing 1 million samples. The results collected
 * over several trials are summarized below:
 * <pre>
 *    Method                          Time to Process
 *    Direct computation sRGB to Linear        33.21 milliseconds
 *    Lookup table standardToLinear()           1.01 milliseconds
 *    Direct computation Linear to sSGB        36.86 milliseconds
 *    Interpolated lookup linearToStandard()   12.90 milliseconds
 * </pre>
 * The interpolation-based lookup used for the linearToStandard()
 * class requires more processing time than a simple lookup,
 * but returns a match for the direct computation in more than 99.9 percent
 * of all cases and never experiences an error greater than 1 level.
 *
 */
public class LinearRgb {

  /**
   * The SingletonHolder class ensures lazy class loading. This approach
   * is explained at
   * https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
   * and is recommended for all Java-compliant virtual machines (JVM).
   */
  private static class SingletonHolder {

    private static final LinearRgb singleton = new LinearRgb();
  }


  /**
   * Returns a single, sharable, read-only instance of this class.
   *
   * @return a valid instance of the GammaConverter class.
   */
  public static LinearRgb getInstance() {
    return SingletonHolder.singleton;
  }

  /**
   * The offset for the power functions for converting between systems
   */
   private static final double G_OFFSET = 0.055;
   /**
    * The scaling factor for the power functions for converting between systems
    */
  private static final double G_SCALE = 1 + G_OFFSET;

  /**
   * The simple divide or multiple factor used for conversions in the
   * low end of the intensity range
   */
  private static final double G_FACTOR = 12.92;

  /**
   * The transition point in the sRGB system between dark range of
   * intensities where the conversion is a simple dividing factor to the
   * lighter range where a power function is applied.
   */
  private static final double G_TRANSITION = 0.04045;

  /**
   * The transition point in the linear RGB system between the dark range
   * of intensities where the conversion is a simple multiply factor
   * to the lighter range where a power function is applied.
   */
  private static final double G_INV_TRANSITION = G_TRANSITION / G_FACTOR;

  /**
   * The number of control points used for computing the interpolated
   * values for the linear-to-standard conversion
   */
  private static final int N_BINS = 512;


  private final float[] std2lin;
  private final float[] lin2std;

  private LinearRgb() {
    // a private constructor to deter applications from creating
    // specific instances of this class.

    // We create two arrays.  The std2lin is a simple mapping
    // that converts a pixel component to its equivalent in a linear
    // intensity space. The lin2std is the inverse mapping.  The inverse
    // is a brute force calculation.
    std2lin = new float[256];
    for (int i = 0; i < 256; i++) {
      std2lin[i] = (float) computeLinear(i);
    }

    lin2std = new float[N_BINS + 1];
    for (int i = 0; i <= N_BINS; i++) {
      double linearValue = (double)i/N_BINS;
      lin2std[i] = (float) computeStandard(linearValue);
    }
  }

  /**
   * Compute the linear form of the specified value from the standard
   * sRGB color space. The computation is performed using power function
   * to produce the most accurate mapping.
   * @param standard a R,G, or B value from the standard (non-linear)
   * sRGB color space; value 0 to 255.
   * @return a floating point value in the range 0 to 1.
   */
  public final double computeLinear(int standard) {
    double s = standard / 255.0;
    if (s <= G_TRANSITION) {
      return s / G_FACTOR;
    } else {
      return Math.pow((s + G_OFFSET) / G_SCALE, 2.4);
    }
  }

    /**
   * Compute the standard form of the specified value from the linear
   * color space. The computation is performed using power function
   * to produce the most accurate mapping.
   * @param linear a linear value in the range 0 to 1
   * @return a floating point value in the range 0 to 255.0
   */
  public final double computeStandard(double linear) {
    double a;
    if (linear <= G_INV_TRANSITION) {
      a = G_FACTOR * linear;
    } else {
      a = G_SCALE * Math.pow(linear, 1.0 / 2.4) - G_OFFSET;
    }
    return a * 255;
  }

  /**
   * Converts a value giving an intensity from one of the color components
   * in the sRGB space to the linear equivalent. The input is assumed to
   * be either the r, g, or b component given in a range 0 to 255.
   * Conversion is performed using a simple lookup table. This method
   * yields good performance.
   *
   * @param p a value in the range 0 to 255.
   * @return a value in the range 0 to 1
   */
  public float standardToLinear(int p) {
    return std2lin[p];
  }

  /**
   * Converts an intensity value from the linear color space to its equivalent
   * in the standard RGB (sRGB) space.  Conversion is performed using a
   * linear interpretation and is an match for the rounded-off value
   * from the direct method in better than 99.9 percent of all cases and
   * never experiences an error greater than 1 level.
   * <p>
   * Even good resampling techniques including Lanczos and B-Sline methods can
   * result in values that are out of the range 0 to 1. In such cases, this
   * method truncates the input value so that it is restricted to the
   * valid range.
   * @param linearValue a value in the range 0 to 1; out-of-range
   * values will be truncated.
   * @return an integer value in the range 0 to 255.
   */
  public int linearToStandard(double linearValue) {
    if (linearValue <= G_INV_TRANSITION) {
      if (linearValue <= 0) {
        return 0;
      }
      return (int) (255.0 * G_FACTOR * linearValue + 0.5);
    } else if (linearValue >= 1.0) {
      return 255;
    }
    // The following interpolation is just a variation on
    //    y = ( y0*(x1-x)+y1*(x-x0) ) / (x1-x0)
    // It was simplified to take advantage of the fact that
    // by scaling up the linearValue to x, the resulting
    // x1 = x0+1 and (x1-x0) = 1 will cancel out.
    // we also substited a temporary variable q = x - x0
    // to save us one floating point subtraction.

    double x = linearValue * N_BINS;
    int index = (int) x; // index == x0
    double q = x - index;
    double y0 = lin2std[index];
    double y1 = lin2std[index + 1];
    return (int) (y0 * (1.0 - q) + y1 * q + 0.5);
  }


  /**
   * Provides a test method for evaluating the linear interpolation
   * performed by this class's linearToStandard method.
   * This method provides an average absolute error of 0.00093
   * levels-of-intensity and 0.1448 levels maximum absolute error.
   * Although this method is included mainly for testing purposes,
   * application developers may use this method if it is appropriate
   * to their requirements.
   * @param linearValue a value in the range 0 to 1, with out-of-range
   * values being truncated.
   * @return a floating-point value in the range 0 to 255.
   */
  public double testLinearToStandard(double linearValue) {
    if (linearValue <= G_INV_TRANSITION) {
      if (linearValue <= 0) {
        return 0;
      }
      return 255.0 * G_FACTOR * linearValue;
    } else if (linearValue >= 1.0) {
      return 255;
    }
    // The following interpolation is just a variation on
    //    y = ( y0*(x1-x)+y1*(x-x0) ) / (x1-x0)
    // See the linearToStrandard() method for more explanation

    double x = linearValue * N_BINS;
    int index = (int) x; // index == x0
    double q = x - index;  // Essentially  x - x0
    double y0 = lin2std[index];
    double y1 = lin2std[index + 1];
    return y0 * (1.0 - q) + y1 * q;
  }
}
