/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gridfour.io;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.io.TempDir;

public class BufferedRandomAccessFileTest {
 
  @TempDir
  Path tempDir;
  
  
  public BufferedRandomAccessFileTest() {
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


  
  @Test
  public void testRoundTrip() throws Exception {
    File tempFolder = tempDir.toFile();
    File tempFile = new File(tempFolder, "Test.data");
	float pi = (float)Math.PI;
	String benediction = "Good night and \u041f\u0440\u043e\u0449\u0430\u0439";
		
    try(BufferedRandomAccessFile braf = 
            new BufferedRandomAccessFile(tempFile, "rw"))
	{
		braf.writeShort(0x0102);
		braf.writeInt(0x03040506);
		braf.writeLong(0x0708090a0b0c0d0eL);
		braf.writeFloat(pi);
		braf.writeDouble(Math.E);
		braf.writeUTF(benediction);
		braf.seek(0);
		assertEquals(braf.readShort(), 0x0102, "Mismatched short"); 
		assertEquals(braf.readInt(), 0x03040506, "Mismatched int");
		assertEquals(braf.readLong(), 0x0708090a0b0c0d0eL, "Mismatched long");
		assertEquals(braf.readFloat(), pi, "Mismatched float");
		assertEquals(braf.readDouble(), Math.E, "Mismatched e");
		assertEquals(braf.readUTF(), benediction, "Mismatched UTF");
	}
    
            
    
  }
 
  
}
