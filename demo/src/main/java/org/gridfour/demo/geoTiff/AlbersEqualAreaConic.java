/* --------------------------------------------------------------------
 * Copyright (C) 2020  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 06/2020  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.geoTiff;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.log;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

/**
 * Provides a partial implementation of the Albers Equal Area Conic map projection.
 */
class AlbersEqualAreaConic {

    // parameters for WGS84, should be close enough to NAD83
    private static final double semiMajorAxis = 6378137.0;
    private static final double flattening = 1 / 298.257223563;
    private static final double semiMinorAxis = 6356752.314245179;
    private static final double weightedMeanRadius = 6371008.7714;
    private static final double eccentricity = 8.1819190842622e-2;

    double latitudeOfOrigin;
    double centralMeridian;
    double standardParallel1;
    double standardParallel2;
    private final double e, e2;
    private final double phi0, phi1, phi2;
    private final double rho0;
    private final double q0, q1, q2, m1, m2;
    final double nu, Ca;
    final double eRadius; // equatorial radius

    /**
     * Compute q from equation 3-12.
     *
     * @param sinphi the sin of the latitude
     * @return a valid floating-point value
     */
    private double computeQ(double sinphi) {
        return (1 - e2)
            * (sinphi / (1 - e2 * sinphi * sinphi)
            - (1 / (2 * e)) * log((1 - e * sinphi) / (1 + e * sinphi)));

    }

    /**
     * Compute m from equation 14-15.
     *
     * @param sinphi the sine of phi
     * @param cosphi the cosine of phi
     * @return a valid floating point number
     */
    private double computeM(double sinphi, double cosphi) {
        return cosphi / sqrt(1 - e2 * sinphi * sinphi);
    }

    /**
    Construct an instance of a class to provide the Albers Equal Area Conic
    map projection.
    @param latitudeOfOrigin The latitude at the center of the projection and
    the origin of the projected coordinate system
    @param centralMeridian The longitude at the center of the projection and
    the origin of the projected coordinate system.
    @param standardParallel1 the first parallel of secancy for the projection
    @param standardParallel2 the second parallel of secancy for the projection
     */
    AlbersEqualAreaConic(
        double latitudeOfOrigin,
        double centralMeridian,
        double standardParallel1,
        double standardParallel2) {
        this.latitudeOfOrigin = latitudeOfOrigin;
        this.centralMeridian = centralMeridian;
        if (abs(standardParallel1) <= abs(standardParallel2)) {
            this.standardParallel1 = standardParallel1;
            this.standardParallel2 = standardParallel2;
        } else {
            this.standardParallel2 = standardParallel1;
            this.standardParallel1 = standardParallel2;
        }

        eRadius = semiMajorAxis;
        e = eccentricity;
        e2 = e * e;
        phi0 = Math.toRadians(latitudeOfOrigin);
        phi1 = Math.toRadians(this.standardParallel1);
        phi2 = Math.toRadians(this.standardParallel2);

        double sinPhi0 = sin(phi0);
        double sinPhi1 = sin(phi1);
        double sinPhi2 = sin(phi2);
        double cosPhi1 = cos(phi1);
        double cosPhi2 = cos(phi2);

        q0 = computeQ(sinPhi0);
        q1 = computeQ(sinPhi1);
        q2 = computeQ(sinPhi2);

        m1 = computeM(sinPhi1, cosPhi1);
        m2 = computeM(sinPhi2, cosPhi2);
        nu = (m1 * m1 - m2 * m2) / (q2 - q1);

        Ca = m1 * m1 + nu * q1;
        rho0 = sqrt(Ca - nu * q0) / nu;
    }

    /**
    Map the specified geographic coordinates to the projected
    coordinate plane. Geographic coordinates are given in order as
    latitude, longitude. So srcPts[0] would be latitude and
    srcPts[1] would be longitude.  Projected coordinates are given in
    order x, y.  So dstPts[0] would be x and dstPts[1] would be y.
    @param srcPts an array giving the source latitude and longitude
    coordinates
    @param srcOff the offset into the srcPts array where the data starts.
    @param dstPts an array giving the output (destination) x and y coordinates.
    @param dstOff the offset into the destination array where the data is written.
    @param numPts the number of points to be transformed through the
    forward map projection
    @return true if successful; otherwise, false.
     */
    public boolean forward(
        double[] srcPts,
        int srcOff,
        double[] dstPts,
        int dstOff,
        int numPts) {
        int iSrc = srcOff;
        int iDst = dstOff;
        double lat, lon;
        double x, y;
        double phi, theta, rho, delta, sinPhi, q;

        for (int iPts = 0; iPts < numPts; iPts++) {
            lat = srcPts[iSrc++];
            lon = srcPts[iSrc++];
            if (abs(lat) > 90) {
                if (abs(lat) < 90 + 1.0e+7) {
                    lat = Math.signum(lat) * 90;
                } else {
                    return false;
                }
            }

            phi = Math.toRadians(lat);
            delta = lon - centralMeridian;
            if (delta < -180) {
                delta += 360;
            } else if (delta >= 180) {
                delta -= 360;
            }

            delta = Math.toRadians(delta);
            theta = nu * delta;
            sinPhi = sin(phi);
            q = computeQ(sinPhi);
            rho = sqrt(Ca - nu * q) / nu;
            x = eRadius * rho * sin(theta);
            y = eRadius * (rho0 - rho * cos(theta));
            dstPts[iDst++] = x;
            dstPts[iDst++] = y;
        }

        return true;
    }
}
