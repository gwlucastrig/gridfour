package org.gridfour.gvrs;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Provides the most basic test of writing and then reading a file
 * that contains only a single tile with only one element.
 */
public class GvrsElementSimpleWriteAndReadTest {

  private final int intFillValue = -9999;
  private final float fltFillValue = -999.9f;
  private final int[] intSamples;
  private final float[] fltSamples;
  private final GvrsElementSpecification[] intSpecs;
  private final GvrsElementSpecification[] fltSpecs;

  @TempDir
  File tempDir;

  public GvrsElementSimpleWriteAndReadTest() {
    intSamples = new int[100];
    fltSamples = new float[100];
    for (int i = 0; i < intSamples.length - 1; i++) {
      intSamples[i] = i - 50;
      fltSamples[i] = intSamples[i] / 10.0f;
    }
    intSamples[intSamples.length - 1] = intFillValue;
    fltSamples[intSamples.length - 1] = fltFillValue;

    intSpecs = new GvrsElementSpecification[2];
    intSpecs[0] = new GvrsElementSpecificationInt("zInt", intFillValue);
    intSpecs[1] = new GvrsElementSpecificationShort("zShort", (short) intFillValue);
    fltSpecs = new GvrsElementSpecification[2];
    fltSpecs[0] = new GvrsElementSpecificationFloat("zFloat", fltFillValue);
    fltSpecs[1] = new GvrsElementSpecificationIntCodedFloat("zICF", fltFillValue, 10f, 0);
  }

  @BeforeAll
  public static void setUpClass() {

  }

  @BeforeEach
  public void setUp() {
  }

  @Test
  void singleTileWrite() {
    // Test writing to and reading from a file that contains only one tile.
    // This is the most basic test.
    for (int iSpec = 0; iSpec < intSpecs.length; iSpec++) {
      File testFile = new File(tempDir, "WriteAndReadTest.gvrs");
      if (testFile.exists()) {
        testFile.delete();
      }

      GvrsFileSpecification spec = new GvrsFileSpecification(10, 10, 10, 10);
      GvrsElementSpecification eSpec = intSpecs[iSpec];
      spec.addElementSpecification(eSpec);
      try (
        GvrsFile gvrs = new GvrsFile(testFile, spec)) {
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            if (iRow == 9 && iCol == 9) {
              break;
            }
            element.writeValueInt(iRow, iCol, intSamples[index]);
          }
        }
      } catch (IOException ex) {
        fail("IOException in processing " + testFile + " " + ex.getMessage());
      }

      try (
        GvrsFile gvrs = new GvrsFile(testFile, "r")) {
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            int iTest = element.readValueInt(iRow, iCol);
            assertEquals(intSamples[index], iTest);
          }
        }
      } catch (IOException ex) {
        fail("IOException in processing " + eSpec.getName() + " " + ex.getMessage());
      }
    }

    for (int iSpec = 0; iSpec < fltSpecs.length; iSpec++) {
      File testFile = new File(tempDir, "singleTileFlt.gvrs");
      if (testFile.exists()) {
        testFile.delete();
      }

      GvrsFileSpecification spec = new GvrsFileSpecification(10, 10, 10, 10);
      GvrsElementSpecification eSpec = fltSpecs[iSpec];
      spec.addElementSpecification(eSpec);
      try (
        GvrsFile gvrs = new GvrsFile(testFile, spec)) {
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            if (iRow == 9 && iCol == 9) {
              break;
            }
            element.writeValue(iRow, iCol, fltSamples[index]);
          }
        }
      } catch (IOException ex) {
        fail("IOException in processing " + testFile + " " + ex.getMessage());
      }

      try (
        GvrsFile gvrs = new GvrsFile(testFile, "r")) {
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            float fltTest = element.readValue(iRow, iCol);
            assertEquals(fltSamples[index], fltTest, eSpec.getName() + ":" + iRow + "," + iCol);
          }
        }
      } catch (IOException ex) {
        fail("IOException in processing " + eSpec.getName() + " " + ex.getMessage());
      }
    }
  }
 
}
