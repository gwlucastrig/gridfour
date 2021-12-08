package org.gridfour.gvrs;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Provides the most basic test of writing and then reading a file
 * that contains only a single tile with only one element.
 */
public class MultiIntElementTest {

  private int intFillValue = -9999;
  private int[] intSamples;
  private int[] rndSamples;

  @TempDir
  File tempDir;

  public MultiIntElementTest() {
    intSamples = new int[100];
    rndSamples = new int[100];
    Random random = new Random(0);
    for (int i = 0; i < intSamples.length; i++) {
      intSamples[i] = i - 50;
      rndSamples[i] = random.nextInt();
    }

  }

  @BeforeAll
  public static void setUpClass() {

  }

  @BeforeEach
  public void setUp() {
  }

  @Test
  void threeElementTest() {
    File testFile = new File(tempDir, "MultiIntElementTest.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsElementSpecification eSpec0 = new GvrsElementSpecificationInt("z0");
    GvrsElementSpecification eSpec1 = new GvrsElementSpecificationInt("z1");
    GvrsElementSpecification eSpec2 = new GvrsElementSpecificationInt("z2");

    GvrsFileSpecification spec = new GvrsFileSpecification(10, 10, 10, 10);
    spec.addElementSpecification(eSpec0);
    spec.addElementSpecification(eSpec1);
    spec.addElementSpecification(eSpec2);
    spec.setDataCompressionEnabled(true);

    try (
      GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      GvrsElement e0 = gvrs.getElement(eSpec0.getName());
      GvrsElement e1 = gvrs.getElement(eSpec1.getName());
      GvrsElement e2 = gvrs.getElement(eSpec2.getName());

      for (int iRow = 0; iRow < 10; iRow++) {
        for (int iCol = 0; iCol < 10; iCol++) {
          int index = iRow * 10 + iCol;
          e0.writeValueInt(iRow, iCol, intSamples[index]);
          e1.writeValueInt(iRow, iCol, rndSamples[index]);
          e2.writeValueInt(iRow, iCol, intSamples[index]);
        }
      }
      gvrs.flush();
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }

    try (
      GvrsFile gvrs = new GvrsFile(testFile, "r")) {
      GvrsElement e0 = gvrs.getElement(eSpec0.getName());
      GvrsElement e1 = gvrs.getElement(eSpec1.getName());
      GvrsElement e2 = gvrs.getElement(eSpec2.getName());
      int[] block0 = e0.readBlockInt(0, 0, 10, 10);
      int[] block1 = e1.readBlockInt(0, 0, 10, 10);
      int[] block2 = e2.readBlockInt(0, 0, 10, 10);
 
  
      assertArrayEquals(intSamples, block0, "block0");
      assertArrayEquals(rndSamples, block1, "block1");
      assertArrayEquals(intSamples, block2, "block2");

    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }
    
    if (testFile.exists()) {
      testFile.delete();
    }
  }
}
