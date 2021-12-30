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
 * Verifies that a tile containing multiple elements of different types
 * can be written and read successfully.
 */
public class MultiElementTest {

  //private final int intFillValue = -9999;
  private final int[] intSamples;
  private final int[] rndSamples;
  private final float[] fltSamples;
  private final short[] srtSamples;

  @TempDir
  File tempDir;

  public MultiElementTest() {
    intSamples = new int[81];
    rndSamples = new int[81];
    fltSamples = new float[81];
    srtSamples = new short[81];
    Random random = new Random(0);
    for (int i = 0; i < intSamples.length; i++) {
      intSamples[i] = i - 40;
      rndSamples[i] = random.nextInt();
      fltSamples[i] = intSamples[i] / 10.0f;
      srtSamples[i] = (short) intSamples[i];
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
    File testFile = new File(tempDir, "MultiElementTest.gvrs");


    GvrsElementSpecification eSpec0 = new GvrsElementSpecificationInt("zInt");
    GvrsElementSpecification eSpec1 = new GvrsElementSpecificationShort("zShort");
    GvrsElementSpecification eSpec2 = new GvrsElementSpecificationFloat("zFloat");

    for(int iTest=0; iTest<2; iTest++){
          if (testFile.exists()) {
      testFile.delete();
    }
 
    GvrsFileSpecification spec = new GvrsFileSpecification(9, 9);
    spec.addElementSpecification(eSpec0);
    spec.addElementSpecification(eSpec1);
    spec.addElementSpecification(eSpec2);
    String modeStr = "Standard";
    if(iTest==1){
    modeStr = "Compressed";
       spec.setDataCompressionEnabled(true);
    }
    
    try (
      GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      GvrsElement e0 = gvrs.getElement(eSpec0.getName());
      GvrsElement e1 = gvrs.getElement(eSpec1.getName());
      GvrsElement e2 = gvrs.getElement(eSpec2.getName());

      for (int iRow = 0; iRow < 9; iRow++) {
        for (int iCol = 0; iCol < 9; iCol++) {
          int index = iRow * 9 + iCol;
          e0.writeValueInt(iRow, iCol, intSamples[index]);
          e1.writeValueInt(iRow, iCol, srtSamples[index]);
          e2.writeValue(iRow, iCol, fltSamples[index]);
        }
      }
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }

    try (
      GvrsFile gvrs = new GvrsFile(testFile, "r")) {
      GvrsElement e0 = gvrs.getElement(eSpec0.getName());
      GvrsElement e1 = gvrs.getElement(eSpec1.getName());
      GvrsElement e2 = gvrs.getElement(eSpec2.getName());
      int[] block0 = e0.readBlockInt(0, 0, 9, 9);
      int[] block1 = e1.readBlockInt(0, 0, 9, 9);
      float[] block2 = e2.readBlock(0, 0, 9, 9);
      assertArrayEquals(intSamples, block0, "Int elements "+modeStr);
      for (int i = 0; i < srtSamples.length; i++) {
        assertEquals(srtSamples[i], (short) block1[i], "Short elements "+modeStr);
      }
      assertArrayEquals(fltSamples, block2, "Float elements "+modeStr);

    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }
    }
    
    if (testFile.exists()) {
      testFile.delete();
    }
  }
}
