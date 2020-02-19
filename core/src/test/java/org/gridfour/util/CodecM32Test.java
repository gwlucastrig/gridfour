/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.

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
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 02/2020  G. Lucas     Created  
 *
 * Notes:
 *   At this time, this class only tests a limited range of integers.
 *   Unfortunately, the complete test takes about 20 minutes, which is
 *   way too long for a unit test (and is also pushing things for
 *   and integration test).   You may experiment by modifying the "bracket"
 *   element included below.
 * -----------------------------------------------------------------------
 */
package org.gridfour.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performs round-trip tests for coding and decoding integers using the
 * M32 codec.
 */
public class CodecM32Test {

  public CodecM32Test() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @AfterAll
  public static void tearDownClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  @AfterEach
  public void tearDown() {
  }

  /**
   * Test of decode method, of class CodecM32.
   */
  @Test
  public void testSingleSize() {
    CodecM32 m32 = new CodecM32(1);

    // first, test the encodings of length 1.
    for (int i = -126; i < 126; i++) {
      testSize(m32, i, 1);
    }
	
	
     testSize(m32, 126, 3);
     testSize(m32, 127, 3);
     testSize(m32, -128, 3);
     testSize(m32, -127, 3);
   
     testSize(m32, 128, 3);
     testSize(m32, -129, 3);
   
     testSize(m32, 32767, 3);
     testSize(m32, 32768, 4);
   
     testSize(m32, -32768, 3);
     testSize(m32, -32769, 4);
   
     testSize(m32, (1 << 23) - 1, 4);
     testSize(m32, (1 << 23), 5);
   
     testSize(m32, -(1 << 23), 4);
	 
	 testSize(m32, Integer.MIN_VALUE, 1);   // special use as a NO-DATA flag.
 
  }
  
  /**
   * Test of decode method, of class CodecM32.
   */
  @Test
  public void testSequence() {
 
    int bracket = 32780;
    CodecM32 m32 = new CodecM32(2*bracket+1);
 
	for(int i=-bracket; i<bracket; i++){
		m32.encode(i);
	}
	m32.rewind();
	for(int i=-bracket; i<bracket; i++){
		int test = m32.decode();
		assertEquals(test, i, "Value mismatch in sequence test "+i+" != "+test);
	}
 
  }

  private void testSize(CodecM32 m32, int input, int size) {
    m32.rewind();
    m32.encode(input);
    int length = m32.getEncodedLength();
    assertEquals(size, length, "Failed to match expected size "
            + input + ", " + size + "( " + length + ")");
    m32.rewind();
    int output = m32.decode();
    assertEquals(input, output,
            "Decoded value mismatch: input " + input + ", output " + output);
  }

}
