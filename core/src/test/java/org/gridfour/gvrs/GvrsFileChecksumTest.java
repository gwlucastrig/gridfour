package org.gridfour.gvrs;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.gridfour.io.BufferedRandomAccessFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Provides the most basic test of writing and then reading a file
 * that contains only a single tile with only one element.
 */
public class GvrsFileChecksumTest {

  private final int[] rndSamples;

  @TempDir
  File tempDir;

  public GvrsFileChecksumTest() {
    rndSamples = new int[100];
    Random random = new Random(0);
    for (int i = 0; i < rndSamples.length; i++) {
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
  void simpleChecksumTest() {
    File testFile = new File(tempDir, "SimpleChecksumTest.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsElementSpecification eSpec0 = new GvrsElementSpecificationInt("z0");

    GvrsFileSpecification spec = new GvrsFileSpecification(10, 20, 10, 10);
    spec.addElementSpecification(eSpec0);
    spec.setChecksumEnabled(true);
 
    long size1 = 0; // size after tike 0 is written
 
    try (
       GvrsFile gvrs = new GvrsFile(testFile, spec)) {
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }
 
    // Write a single tile of data to the file.  Then get the size and
    // write an additional tile.
    try (
       GvrsFile gvrs = new GvrsFile(testFile, "rw")) {
      GvrsElement e0 = gvrs.getElement(eSpec0.getName());
      for (int iRow = 0; iRow < 10; iRow++) {
        for (int iCol = 0; iCol < 10; iCol++) {
          int index = iRow * 10 + iCol;
          e0.writeValueInt(iRow, iCol, rndSamples[index]);
        }
      }
      gvrs.flush();
      size1 = testFile.length();
      for (int iRow = 0; iRow < 10; iRow++) {
        for (int iCol = 0; iCol < 10; iCol++) {
          int index = iRow * 10 + iCol;
          e0.writeValueInt(iRow, iCol + 10, rndSamples[index]);
        }
      }
      gvrs.flush();
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }

    try {
      GvrsFileInspector inspector = new GvrsFileInspector(testFile);
      boolean checksumFailed = inspector.didFileFailInspection();
      assertFalse(checksumFailed, "Bad checksum on good file");
    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }

    // Introduce a one-bit error in tile 1.
    try ( BufferedRandomAccessFile braf = new BufferedRandomAccessFile(testFile, "rw")) {
      braf.seek(size1 + 20);
      byte test = braf.readByte();
      test ^= 0b00010000;
      braf.seek(size1 + 20);
      braf.writeByte(test & 0xff);
    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }

    try {
      GvrsFileInspector inspector = new GvrsFileInspector(testFile);
      boolean checksumFailed = inspector.didFileFailInspection();
      boolean headerFailed = inspector.didFileHeaderFailInspction();
      assertTrue(checksumFailed, "Inspection did not detect bad file");
      assertFalse(headerFailed, "Inspection wrongly identified header failure");
    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }

    // Remove a one-bit error in tile 1.  Set an error in the header
    try ( BufferedRandomAccessFile braf = new BufferedRandomAccessFile(testFile, "rw")) {
      braf.seek(size1 + 20);
      byte test = braf.readByte();
      test ^= 0b00010000;
      braf.seek(size1 + 20);
      braf.writeByte(test & 0xff);
      braf.seek(100);
      test = braf.readByte();
      test ^= 0b00010000;
      braf.seek(100);
      braf.writeByte(test & 0xff);
    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }

    try {
      GvrsFileInspector inspector = new GvrsFileInspector(testFile);
      boolean checksumFailed = inspector.didFileFailInspection();
      boolean headerFailed = inspector.didFileHeaderFailInspction();
      assertTrue(checksumFailed, "Inspection did not detect bad file");
      assertTrue(headerFailed, "Inspection failed to identify header failure");
    } catch (IOException ex) {
      fail("IOException in processing " + ex.getMessage());
    }

    if (testFile.exists()) {
      testFile.delete();
    }
  }
}
