/*
 * The MIT License
 *
 * Copyright 2022 gwluc.
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

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Set;
import org.gridfour.io.BufferedRandomAccessFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the ability of the multi-thread read operations to handle
 * problematic data or exceptions.
 */
public class MultiThreadReadTest {

  @TempDir
  File tempDir;

  public MultiThreadReadTest() {
  }

  /**
   * This test verifies that the tile reading assistant is
   * running while reading a compressed file and is terminated
   * when the file is closed.  A short delay is executed after the
   * close operation to give Java time to clean up the thread.
   */
  @Test
  void testHandlingOfFileClose() {
    File testFile = new File(tempDir, "MultiThreadReadTest.gvrs");
    GvrsFileSpecification spec = new GvrsFileSpecification(30, 30);
    spec.setDataCompressionEnabled(true);
    spec.addElementInt("z");
    long length0 = 0;
    try ( GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      GvrsElement z = gvrs.getElement("z");
      writeCompressibleData(z, 0, 0);
      length0 = testFile.length();
      gvrs.flush();
    } catch (IOException ex) {
      fail("IOException in writing " + testFile + " " + ex.getMessage());
    }

    // Step 2 verify that the Reading Assitant thread exists during
    // read operations and then is removed after file is closed.
    try ( GvrsFile gvrs = new GvrsFile(testFile, "r")) {
      gvrs.setMultiThreadingEnabled(true);
      GvrsElement z = gvrs.getElement("z");
      int dontCare = z.readValueInt(15, 15);
      if (!isReadingAssistantRunning()) {
        fail("Reading assistant thread is not running while reading data");
      }
    } catch (IOException ex) {
      // no action required
    }

    shortDelay();
    if (isReadingAssistantRunning()) {
      fail("Reading assistant thread still exists after file closed");
    }

    // Step 3, corrupt the content of the test file and see
    // if the reading assistant is properly closed in the event
    // of an improper termination.
    try ( BufferedRandomAccessFile braf = new BufferedRandomAccessFile(testFile, "rw")) {
      braf.seek(length0 + 16);
      braf.writeByte(-1);
      braf.writeByte(-1);
      braf.writeByte(-1);
      braf.writeByte(-1);
      braf.writeByte(-1);
      braf.flush();
    } catch (IOException ex) {
      fail("IOException in damaging " + testFile + " " + ex.getMessage());
    }

    try ( GvrsFile gvrs = new GvrsFile(testFile, "r")) {
      gvrs.setMultiThreadingEnabled(true);
      GvrsElement z = gvrs.getElement("z");
      int dontCare = z.readValueInt(15, 15);
      // If the read operation did not throw an exception, then
      // the logic above did not succeed in corrupting the file.
      // In such a case, it's this JUnit test that is failing, not GVRS.
      fail("Exception was not thrown when reading corrupt data");
    } catch (IOException ex) {
      // no action required
    }
    shortDelay();
    if (isReadingAssistantRunning()) {
      fail("Reading assistant thread still exists after closed on IOException");
    }

    testFile.delete();
  }

  /**
   * Verifies that the reading assistant will correctly access a tile
   * containing non-compressed data.
   */
  @Test
  void testHandlingOfNonCompressibleData(){
    File testFile = new File(tempDir, "MultiThreadReadTest.gvrs");
    GvrsFileSpecification spec = new GvrsFileSpecification(30, 60, 30, 30);
    spec.setDataCompressionEnabled(true);
    spec.addElementInt("z");
    try ( GvrsFile gvrs = new GvrsFile(testFile, spec)) {
      GvrsElement z = gvrs.getElement("z");
      writeCompressibleData(z, 0, 0);
      writeNonCompressibleData(z, 0, 30);
    }catch(IOException ioex){
      // no action required
    }


    try ( GvrsFile gvrs = new GvrsFile(testFile, "r")) {
      gvrs.setMultiThreadingEnabled(true);
      GvrsElement z = gvrs.getElement("z");
      int dontCare = z.readValueInt(15, 15);
      Random random = new Random(0);
      int expectedValue = random.nextInt();
      int testValue = z.readValueInt(0, 30);
      assertEquals(expectedValue, testValue, "Non compressed tile not read correctly");

    }catch(IOException ioex){
      // no action required
    }
          testFile.delete();
  }

  private void writeCompressibleData(GvrsElement z, int row0, int col0) throws IOException {
     for (int i = 0; i < 30; i++) {
        for (int j = 0; j < 30; j++) {
          z.writeValueInt(row0+i, col0+j, i + j);
        }
      }
  }

    private void writeNonCompressibleData(GvrsElement z, int row0, int col0) throws IOException {
      Random random = new Random(0);
      for (int i = 0; i < 30; i++) {
        for (int j = 0; j < 30; j++) {
          z.writeValueInt(row0+i, col0+j, random.nextInt());
        }
      }
  }


  private boolean isReadingAssistantRunning() {
    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    for (Thread t : threads) {
      String name = t.getName();
      if (name != null && name.startsWith("GVRS Reading Assistant")) {
        return true;
      }
    }
    return false;
  }

  private void shortDelay(){
    try{
      Thread.sleep(200);
    }catch(InterruptedException inex){
      // no action required.
    }
  }
  @BeforeAll
  public static void setUpClass() {
  }

  @BeforeEach
  public void setUp() {
  }

}
