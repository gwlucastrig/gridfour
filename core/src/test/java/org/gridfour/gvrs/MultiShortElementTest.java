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
public class MultiShortElementTest {

  private short intFillValue = -9999;
  private short[] intSamples;
  private short[] rndSamples;

  @TempDir
  File tempDir;

  public MultiShortElementTest() {
    intSamples = new short[100];
    rndSamples = new short[100];
    Random random = new Random(0);
    for (int i = 0; i < intSamples.length; i++) {
      intSamples[i] = (short)(i - 50);
      rndSamples[i] = (short)(random.nextInt()&0xffff);
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
    File testFile = new File(tempDir, "MultiShortElementTest.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsElementSpec eSpec0 = new GvrsElementSpecShort("z0");
    GvrsElementSpec eSpec1 = new GvrsElementSpecShort("z1");
    GvrsElementSpec eSpec2 = new GvrsElementSpecShort("z2");

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
 
  
      testEquality(intSamples, block0, "block0");
      testEquality(rndSamples, block1, "block1");
      testEquality(intSamples, block2, "block2");

    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }
    
    if (testFile.exists()) {
      testFile.delete();
    }
  }
 

void testEquality(short []samples, int []block, String name){
   for(int i=0; i<samples.length; i++){
     assertEquals(samples[i], (short)block[i], name);
   }
}

}