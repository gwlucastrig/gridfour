/*
 * The MIT License
 *
 * Copyright 2022 Gary W. Lucas
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
package org.gridfour.gvrs;

import org.gridfour.coordinates.GridPoint;
import org.gridfour.coordinates.ModelPoint;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * These tests exercise the coordinate transformation logic for
 * the model (Cartesian) coordinate system to ensure that coordinate mappings
 * are correctly recorded and fully bijective.
 */
public class GvrsFileSpecificationCoordinateTest {

  @TempDir
  File tempDir;

  public GvrsFileSpecificationCoordinateTest() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  @Test
  public void transformWithRotation() {
    GvrsFileSpecification spec = new GvrsFileSpecification(11, 11);
    AffineTransform afTrans = AffineTransform.getTranslateInstance(-5, -5);
    AffineTransform afRotate = AffineTransform.getRotateInstance(Math.PI / 4);
    afRotate.concatenate(afTrans);

    spec.setTransformRasterToModel(afRotate);

    // find the range of the model-coordinate values
    double mx0 = spec.getX0();
    double my0 = spec.getY0();
    double mx1 = spec.getX1();
    double my1 = spec.getY1();

    // Test the four corners.  This will verify that the mapGridToModelPoint
    // method works correctly.
    //
    //     3 ---- 2
    //     |      |
    //     0 ---- 1
    //
    //  Remember that the grid coordinates are given in order row, column
    //  (not x, y).
    ModelPoint mp = spec.mapGridToModelPoint(0, 0);
    assertEquals(0, mp.getX(), 1.0e-9, "corner 0, x coordinate mismatch");
    assertEquals(my0, mp.getY(), 1.0e-9, "corner 0, y coordinate mismatch");
    GridPoint gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(0, gp.getRow(), 1.0e-9, "corner 0, row coordinate mismatch");
    assertEquals(0, gp.getColumn(), 1.0e-9, "corner 0, col coordinate mismatch");

    mp= spec.mapGridToModelPoint(0, 10); // row 0, col 10
    assertEquals(mx1, mp.getX(), 1.0e-9, "corner 1, x coordinate mismatch");
    assertEquals(0, mp.getY(), 1.0e-9, "corner 1, y coordinate mismatch");
    gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(0, gp.getRow(), 1.0e-9, "corner 1, row coordinate mismatch");
    assertEquals(10, gp.getColumn(), 1.0e-9, "corner 1, col coordinate mismatch");

    mp = spec.mapGridToModelPoint(10, 10);
    assertEquals(0, mp.getX(), 1.0e-9, "corner 2, x coordinate mismatch");
    assertEquals(my1, mp.getY(), 1.0e-9, "corner 2, y coordinate mismatch");
    gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(10, gp.getRow(), 1.0e-9, "corner 2, row coordinate mismatch");
    assertEquals(10, gp.getColumn(), 1.0e-9, "corner 2, col coordinate mismatch");

    mp = spec.mapGridToModelPoint(10, 0); // row 10, col 0 
    assertEquals(mx0, mp.getX(), 1.0e-9, "corner 3, x coordinate mismatch");
    assertEquals(0, mp.getY(), 1.0e-9, "corner 3, y coordinate mismatch");
    gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(10, gp.getRow(), 1.0e-9, "corner 3, row coordinate mismatch");
    assertEquals(0, gp.getColumn(), 1.0e-9, "corner 3, col coordinate mismatch");

    // Finally, test the center
    mp = spec.mapGridToModelPoint(5, 5); // row 4.5, col 4.5 
    assertEquals(0, mp.getX(), 1.0e-9, "(0,0) x coordinate mismatch");
    assertEquals(0, mp.getY(), 1.0e-9, "(0,0) y coordinate mismatch");
  }

  @Test
  public void simpleDomain() {
    GvrsFileSpecification spec = new GvrsFileSpecification(11, 11);
    spec.setCartesianCoordinates(-10, -20, 10, 20);

    // find the range of the model-coordinate values
    double mx0 = spec.getX0();
    double my0 = spec.getY0();
    double mx1 = spec.getX1();
    double my1 = spec.getY1();

    // Test the four corners.  This will verify that the mapGridToModelPoint
    // method works correctly.
    //
    //     3 ---- 2
    //     |      |
    //     0 ---- 1
    //
    ModelPoint mp = spec.mapGridToModelPoint(0, 0);
    assertEquals(mx0, mp.getX(), 1.0e-9, "corner 0, x coordinate mismatch");
    assertEquals(my0, mp.getY(), 1.0e-9, "corner 0, y coordinate mismatch");
    GridPoint gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(0, gp.getRow(), 1.0e-9, "corner 0, row coordinate mismatch");
    assertEquals(0, gp.getColumn(), 1.0e-9, "corner 0, col coordinate mismatch");

    mp = spec.mapGridToModelPoint(0, 10); // row 0, col 10
    assertEquals(mx1, mp.getX(), 1.0e-9, "corner 1, x coordinate mismatch");
    assertEquals(my0, mp.getY(), 1.0e-9, "corner 1, y coordinate mismatch");
    gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(0, gp.getRow(), 1.0e-9, "corner 1, row coordinate mismatch");
    assertEquals(10, gp.getColumn(), 1.0e-9, "corner 1, col coordinate mismatch");

    mp = spec.mapGridToModelPoint(10, 10);
    assertEquals(mx1, mp.getX(), 1.0e-9, "corner 2, x coordinate mismatch");
    assertEquals(my1, mp.getY(), 1.0e-9, "corner 2, y coordinate mismatch");
    gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(10, gp.getRow(), 1.0e-9, "corner 2, row coordinate mismatch");
    assertEquals(10, gp.getColumn(), 1.0e-9, "corner 2, col coordinate mismatch");

    mp = spec.mapGridToModelPoint(10, 0); // row 10, col 0 
    assertEquals(mx0, mp.getX(), 1.0e-9, "corner 3, x coordinate mismatch");
    assertEquals(my1, mp.getY(), 1.0e-9, "corner 3, y coordinate mismatch");
    gp = spec.mapModelToGridPoint(mp.getX(), mp.getY());
    assertEquals(10, gp.getRow(), 1.0e-9, "corner 3, row coordinate mismatch");
    assertEquals(0, gp.getColumn(), 1.0e-9, "corner 3, col coordinate mismatch");
  }

  @Test
  void transformReadAndWrite() {
    File testFile = new File(tempDir, "TransformReadAndWrite.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsFileSpecification spec1 = new GvrsFileSpecification(11, 11);
    AffineTransform afTrans = AffineTransform.getTranslateInstance(-3, -3);
    AffineTransform afRotate = AffineTransform.getRotateInstance(Math.PI / 6);
    afRotate.concatenate(afTrans);

    spec1.setTransformRasterToModel(afRotate);

    try ( GvrsFile gvrs = new GvrsFile(testFile, spec1)) {

    } catch (IOException ex) {
      fail(ex.getMessage());
    }

    GvrsFileSpecification spec2 = null;
    try ( GvrsFile gvrs = new GvrsFile(testFile, spec1)) {
      spec2 = gvrs.getSpecification();
    } catch (IOException ex) {
      fail(ex.getMessage());
    }

    assertEquals(spec1.m2r00, spec2.m2r00);
    assertEquals(spec1.m2r01, spec2.m2r01);
    assertEquals(spec1.m2r02, spec2.m2r02);
    assertEquals(spec1.m2r10, spec2.m2r10);
    assertEquals(spec1.m2r11, spec2.m2r11);
    assertEquals(spec1.m2r12, spec2.m2r12);

    assertEquals(spec1.r2m00, spec2.r2m00);
    assertEquals(spec1.r2m01, spec2.r2m01);
    assertEquals(spec1.r2m02, spec2.r2m02);
    assertEquals(spec1.r2m10, spec2.r2m10);
    assertEquals(spec1.r2m11, spec2.r2m11);
    assertEquals(spec1.r2m12, spec2.r2m12);

    AffineTransform mToR1 = spec1.getTransformModelToRaster();
    AffineTransform mToR2 = spec2.getTransformModelToRaster();
    boolean test = mToR1.equals(mToR2);
    assertTrue(test, "Model-to-Raster transforms mismatch");

    AffineTransform rToM1 = spec1.getTransformRasterToModel();
    AffineTransform rToM2 = spec2.getTransformRasterToModel();
    test = rToM1.equals(rToM2);
    assertTrue(test, "Raster-to-Model transforms mismatch");

  }
}
