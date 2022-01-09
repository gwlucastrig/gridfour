
// Note:  This test was written for an earlier version of the
//        RecordManager in which the alloc method took the full size of
//        the file-space to be allocated (including the header and checksum).
//        The new version accepts just the size of the content, which is
//        12 bytes less than is actually allocated.  I have adjusted the
//        tests, but they are sort of messy and need to be cleaned up
//        to be a better fit for the new model.

package org.gridfour.gvrs;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the implementation of the record allocation logic
 * in the RecordManager.
 * <p>
 * Note that the following tests allocate records of type Metadata because
 * the record manager pre-poluates the specified record with zeroes
 * to ensure that the file is correctly populated.
 */
public class RecordAllocationTest {

  @TempDir
  File tempDir;

  public RecordAllocationTest() {

  }

  @BeforeAll
  public static void setUpClass() {

  }

  @BeforeEach
  public void setUp() {
  }

  /** 
   * Perform a simple test to ensure that free records are allocated
   * for reuse.
   */
  @Test
  void fileSpaceReuse() {

    File testFile = new File(tempDir, "RecordAllocationTest.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsFileSpecification spec = new GvrsFileSpecification(10, 10, 10, 10);
    GvrsElementSpecification eSpec = new GvrsElementSpecificationInt("z");
    spec.addElementSpecification(eSpec);
    try (
       GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      RecordManager recordMan = gvrs.getRecordManager();
      long rec0 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      recordMan.fileSpaceDealloc(rec0);
      long recx = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      assertEquals(recx, rec0, "Re-allocation of same size failed for record 0");

      rec0 = recx;
      long rec1 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec2 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      recordMan.fileSpaceDealloc(rec1);
      recx = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      assertEquals(recx, rec1, "Re-allocation of same size failed for record 1");
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }
  }

  /**
   * Performs a test to verify that the RecordManager correctly claims
   * some free space at the end of the file.  The free space is smaller
   * than the allocation request, so the RecordManager should use the
   * free space and extend the file size to provide the remaining required
   * storage space.
   */
  @Test
  void fileSpaceLastRecordReuse() {
    File testFile = new File(tempDir, "RecordAllocationTest.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsFileSpecification spec = new GvrsFileSpecification(10, 10);
    GvrsElementSpecification eSpec = new GvrsElementSpecificationInt("z");
    spec.addElementSpecification(eSpec);
    try (
       GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      RecordManager recordMan = gvrs.getRecordManager();
      long rec0 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec1 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      for (int i = 1; i < 10; i++) {
        recordMan.fileSpaceDealloc(rec1);
        long recx = recordMan.fileSpaceAlloc(1024 + i * 128, RecordType.Metadata);
        assertEquals(recx, rec1, "Last record not freed and reused on iteration " + i);
      }

    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }
  }

  /**
   * Performs a test to verify that adjacent free records would be
   * combined into a single free record.
   */
  @Test
  void fileSpaceFreeRecordMerge() {
    File testFile = new File(tempDir, "RecordAllocationTest.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsFileSpecification spec = new GvrsFileSpecification(10, 10);
    GvrsElementSpecification eSpec = new GvrsElementSpecificationInt("z");
    spec.addElementSpecification(eSpec);
    try (
       GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      RecordManager recordMan = gvrs.getRecordManager();
      long rec0 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec1 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec2 = recordMan.fileSpaceAlloc(1500, RecordType.Metadata);
      long rec3 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec4 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      // deallocate the first record, then the second
      recordMan.fileSpaceDealloc(rec1);
      recordMan.fileSpaceDealloc(rec2);
      long recx = recordMan.fileSpaceAlloc(2048, RecordType.Metadata);
      assertEquals(recx, rec1, "Adjacent  free records not merged upward");
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }

    if (testFile.exists()) {
      testFile.delete();
    }

    try (
       GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      RecordManager recordMan = gvrs.getRecordManager();
      long rec0 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec1 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec2 = recordMan.fileSpaceAlloc(1500, RecordType.Metadata);
      long rec3 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      long rec4 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      // delete the second record first
      recordMan.fileSpaceDealloc(rec3);
      recordMan.fileSpaceDealloc(rec2);
      long recx = recordMan.fileSpaceAlloc(2048, RecordType.Metadata);
      assertEquals(recx, rec2, "Adjacent free records not merged downward");
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }
  }
  
  /**
   * Performs a test to see if a free record is correctly split when
   * smaller records are allocated.  In this case, a 3 kilobyte record
   * is allocated and then freed.  Next, a 2 kilobyte record is allocated.
   * The return file position should match that of the original 3K allocation.
   * Finally, a 1 kilobyte record is allocated.  If the 3K free space
   * was split correctly, that 1K should sit at the end of the original
   * 3K section.
   */
  @Test
  void fileSpaceReuseWithSplit() {

    File testFile = new File(tempDir, "RecordAllocationTest.gvrs");
    if (testFile.exists()) {
      testFile.delete();
    }

    GvrsFileSpecification spec = new GvrsFileSpecification(10, 10, 10, 10);
    GvrsElementSpecification eSpec = new GvrsElementSpecificationInt("z");
    spec.addElementSpecification(eSpec);
    try (
       GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      RecordManager recordMan = gvrs.getRecordManager();
      long recx = recordMan.fileSpaceAlloc(100, RecordType.Metadata);
      long rec0 = recordMan.fileSpaceAlloc(3*1024+512, RecordType.Metadata);
      long rec1 = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      recordMan.fileSpaceDealloc(rec0);
      long recA = recordMan.fileSpaceAlloc(2*1024, RecordType.Metadata);
      long recB = recordMan.fileSpaceAlloc(1024, RecordType.Metadata);
      assertEquals(recA, rec0, "Re-allocation with split failed for record 0-A");
      assertEquals(recB, rec0+2*1024+16, 
        "Re-allocation with split failed for record 0-B "+(recB-rec0));
      recordMan.fileSpaceDealloc(recA);
      recordMan.fileSpaceDealloc(recB);
    } catch (IOException ex) {
      fail("IOException in processing " + testFile + " " + ex.getMessage());
    }
  }
  
}
