/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gridfour.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
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
