
package org.gridfour.gvrs;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author gwluc
 */
public class GvrsInterpolationTest {

  public GvrsInterpolationTest() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  // The samples here are designed to model and test a coordinate
  // system that wraps entirely around the world. For example,
  // EPSG 4326 which has longitudes starting at -180.
  // The values at the endpoints match the values in the center.
  // Using that fact, we can interpolate across the center longitude
  // of the data set and across the seam, and confirm that the resulting
  // values match.
  static final int[] samples = {
    //  0  1  2  3  4  5  6  7  8  9 10 11
    0, 1, 2, 2, 1, 0, 0, 1, 2, 2, 1, 0
  };

  @Test
  public void seamCrossingTest(){
    double nRows = 6;
    double nCols = 12; // length of sample array
    double s = 360.0 / 12;  // 30 degrees
    double h = s / 2.0;     // 15 degrees
    double crossingLon = -180;
    double lon0 = crossingLon + h;
    double lat0 = -90 + h;
    GvrsFileSpecification spec = new GvrsFileSpecification(6, 12);
    spec.setGeographicModel(lat0, lon0, s, s);
    spec.addElementFloat("z");

    try ( GvrsFile gvrs = new GvrsFile(spec)) {
      GvrsElement zElement = gvrs.getElement("z");
      for (int iRow = 0; iRow < nRows; iRow++) {
        for (int iCol = 0; iCol < nCols; iCol++) {
          zElement.writeValue(iRow, iCol, samples[iCol]);
        }
      }

      GvrsInterpolatorBSpline interpolator
        = new GvrsInterpolatorBSpline(zElement);

      // create a set of reference lookups at the central longitude
      double centerLon = crossingLon+180;
      double[] zRef = new double[21];
      for (int i = -10; i <= 10; i++) {
        double lon = centerLon + i * h / 5.0;
        zRef[i + 10] = interpolator.z(lon, 0);
      }

      // now compare those reference values against values created
      // at the crossing longitude 180 degrees out from center
      for (int iTest = -180; iTest <= 180; iTest += 360) {
        double lonOffset = centerLon + iTest;
        for (int i = -10; i <= 10; i++) {
          double lon = lonOffset + i * h / 5.0;
          double z = interpolator.z(lon, 0);
          assertEquals(z, zRef[i+10], 1.0e-9,
            "Mismatch for test "+i+", longitude "+lon);
        }
      }
    } catch (IOException ex) {
      fail("Test failed due to exception "+ex.getMessage());
    }
  }

}
