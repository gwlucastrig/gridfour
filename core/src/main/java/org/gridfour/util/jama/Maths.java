/* ---------------------------------------------------------------------------
 * This package includes the public-domain JAMA matrix library implementation.
 * Only a subset of the JAMA classes is included.
 *
 * This software is a cooperative product of The MathWorks and the
 * National Institute of Standards and Technology (NIST) which has been
 * released to the public domain. Neither The MathWorks nor NIST assumes
 * any responsibility whatsoever for its use by other parties,
 * and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic.
 *
 * The original source code for this package can be found at
 * https://math.nist.gov/javanumerics/jama/
 *
 * --------------------------------------------------------------------------
 */
package org.gridfour.util.jama;



class Maths {

   /** sqrt(a^2 + b^2) without under/overflow. **/

   static double hypot(double a, double b) {
      double r;
      if (Math.abs(a) > Math.abs(b)) {
         r = b/a;
         r = Math.abs(a)*Math.sqrt(1+r*r);
      } else if (b != 0) {
         r = a/b;
         r = Math.abs(b)*Math.sqrt(1+r*r);
      } else {
         r = 0.0;
      }
      return r;
   }
}
