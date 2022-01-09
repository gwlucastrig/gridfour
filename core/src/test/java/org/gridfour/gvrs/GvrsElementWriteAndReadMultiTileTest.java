package org.gridfour.gvrs;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Performs write and read tests for a file that contains multiple tiles.
 * Intended to test file growth and tile management.
 */
public class GvrsElementWriteAndReadMultiTileTest {

  private final int intFillValue = -9999;
  private final float fltFillValue = -999.9f;
  private final int[] intSamples;
  private final float[] fltSamples;
  private final GvrsElementSpecification[] intSpecs;
  private final GvrsElementSpecification[] fltSpecs;

  @TempDir
  File tempDir;

  public GvrsElementWriteAndReadMultiTileTest() {
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

  /**
   * Performs a test to verify that GVRS detects non-populated tiles
   * and handles them properly. A non-populated tile should not be
   * written to the file. So we write a file containing two tiles,
   * one populated, the second one is empty. The empty tile should
   * not be written to disk. So when we store a single value in the
   * second tile, the file size should grow by the size of one tile
   * (including 8-byte padding factor, tile header, and checksum).
   */
  @Test
  void emptyNeighborTileWriteRead() {

    for (int iSpec = 0; iSpec < intSpecs.length; iSpec++) {
      File testFile = new File(tempDir, "WriteAndReadMultiTileTest.gvrs");
      if (testFile.exists()) {
        testFile.delete();
      }

      // Define two tiles side-by-side.
      GvrsFileSpecification spec = new GvrsFileSpecification(10, 20, 10, 10);
      GvrsElementSpecification eSpec = intSpecs[iSpec];
      spec.addElementSpecification(eSpec);

      // populate the first tile, but not the second
      try (
         GvrsFile gvrs = new GvrsFile(testFile, spec)) {
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            element.writeValueInt(iRow, iCol, intSamples[index]);
          }
        }
      } catch (IOException ex) {
        fail("IOException in processing " + testFile + " " + ex.getMessage());
      }

      try ( GvrsFile gvrs = new GvrsFile(testFile, "rw")) {
        RecordManager recordMan = gvrs.getRecordManager();
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            int iTest = element.readValueInt(iRow, iCol);
            assertEquals(intSamples[index], iTest);
          }
        }

        // Test a read on the non-populated section of the grid to
        // confirm that the API returns the fill value.
        int iTest = element.readValueInt(0, 10);
        assertEquals(intFillValue, iTest, "Failed reading from non-populated tile");

        // Test to see that a non-populated tile has not previously been
        // written to the disk.
        RecordManagerStats stats0 = recordMan.scanForFileSpaceStats();
      
        element.writeValueInt(0, 10, intFillValue);
        gvrs.flush();
        RecordManagerStats stats1 = recordMan.scanForFileSpaceStats();
        long size0 = stats0.sizeAllocatedSpace;
        long size1 = stats1.sizeAllocatedSpace;
        assertEquals(size0, size1, "Storing a fill value should not change empty tile");

        // Now store a non-fill value to verify that file size increases by the
        // size of one tile.
        element.writeValueInt(0, 10, 1066);
        gvrs.flush();
        stats1 = recordMan.scanForFileSpaceStats();
        size1 = stats1.sizeAllocatedSpace;
        
        // The size of one tile will be:
        //    The record overhead (12 bytes)
        //    4 byes for the tile index
        //    4 bytes for the size of the tile data
        //    the size of the tile data
        int sizeOneTile = RecordManager.RECORD_OVERHEAD_SIZE
          +8+spec.getStandardTileSizeInBytes();
        long targetOutputSize = (size0 + sizeOneTile + 7) & 0xfffffff8L;
        assertEquals(targetOutputSize, size1, "Before and After Size");
      } catch (IOException ex) {
        fail("IOException in processing " + eSpec.getName() + " " + ex.getMessage());
      }
      if (testFile.exists()) {
        testFile.delete();
      }
    }

    for (int iSpec = 0; iSpec < fltSpecs.length; iSpec++) {
      File testFile = new File(tempDir, "singleTileInt.gvrs");
      if (testFile.exists()) {
        testFile.delete();
      }

      // Define two tiles side-by-side.
      GvrsFileSpecification spec = new GvrsFileSpecification(10, 20, 10, 10);
      GvrsElementSpecification eSpec = fltSpecs[iSpec];
      spec.addElementSpecification(eSpec);

      // populate the first tile, but not the second
      try (
         GvrsFile gvrs = new GvrsFile(testFile, spec)) {
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            element.writeValue(iRow, iCol, fltSamples[index]);
          }
        }
      } catch (IOException ex) {
        fail("IOException in processing " + testFile + " " + ex.getMessage());
      }

      try (
         GvrsFile gvrs = new GvrsFile(testFile, "rw")) {
        RecordManager recordMan = gvrs.getRecordManager();
        GvrsElement element = gvrs.getElement(eSpec.getName());
        for (int iRow = 0; iRow < 10; iRow++) {
          for (int iCol = 0; iCol < 10; iCol++) {
            int index = iRow * 10 + iCol;
            float fTest = element.readValue(iRow, iCol);
            assertEquals(fltSamples[index], fTest);
          }
        }

        // Test a read on the non-populated section of the grid to
        // confirm that the API returns the fill value.
        float fTest = element.readValue(0, 10);
        assertEquals(fltFillValue, fTest, "Failed reading from non-populated tile");

        // Test to see that a non-populated tile has not previously been
        // written to the disk.
        RecordManagerStats stats0 = recordMan.scanForFileSpaceStats();
        long size0 = stats0.sizeAllocatedSpace;
        element.writeValue(0, 10, fltFillValue);
        gvrs.flush();
        RecordManagerStats stats1 = recordMan.scanForFileSpaceStats();
        long size1 = stats1.sizeAllocatedSpace;
        assertEquals(size0, size1, "Storing a fill value should not change empty tile for " + element.getName());

        // Now store a non-fill value to verify that file size increases by the
        // size of one tile.
        element.writeValue(0, 10, 1066.0f);
        gvrs.flush();
        stats1 = recordMan.scanForFileSpaceStats();
        size1 = stats1.sizeAllocatedSpace;
        int sizeForTile = RecordManager.RECORD_OVERHEAD_SIZE
          +4
          +spec.getNumberOfElements()*4
          +spec.getStandardTileSizeInBytes();
        long targetOutputSize = (size0 + sizeForTile + 7) & 0xfffffff8L;
        assertEquals(targetOutputSize, size1,
          "Conflicting before and after file size for " + element.getName());
      } catch (IOException ex) {
        fail("IOException in processing " + eSpec.getName() + " " + ex.getMessage());
      }
    }

  }
}
