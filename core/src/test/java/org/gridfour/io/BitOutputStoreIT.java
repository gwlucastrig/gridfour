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
 *   During development, I ran this test with thousands of random seeds.
 * The large number of tests was necessary to find the "unexpected edge cases"
 * that would have broken this code. However, running so large number of tests
 * was too time-consuming for a reasonable integration test.
 * -----------------------------------------------------------------------
 */
 
package org.gridfour.io;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Performs an integration test of the BitOutputStore and BitInputStore.
 * Data is written to the output and then confirmed that it can be read from
 * the input.
 */
public class BitOutputStoreIT {

  private static final int[] MASK = new int[33];

  static {
    // mask[0] = 00000000
    // masl[1] = 00000001
    // mask[2] = 00000011
    // mask[3] = 00000111
    // etc.   
    int m = 1;
    for (int i = 1; i < MASK.length; i++) {
      MASK[i] = m;
      m = (m << 1) | 1;
    }
  }

  public BitOutputStoreIT() {
  }

  /**
   * Round-trip test of storage and access of a bit sequences.
   */
  @Test
  public void testRoundTrip() {
	int nTest = 5;
	for(int iTest=0; iTest<nTest; iTest++){
       test(iTest, 1000000);
	}
  }

  private void test(int seed, int nSymbolsInText) {
	// The idea of a round-trip test is to store a large number of bit
	// sequences of differing length and see if the extraction produces
	// the same sequence.  For this purpose, we create random bit sequences
	// of random lengths to test the appendBits method and the corresponding
	// getBits.  Java's Random class implements a nextInt method that will
	// produce a pattern of arbitrary bits of length 32.  We use the
	// MASK[n] variables to truncate these to just the length we wish to store.

    Random random = new Random(seed);
	int bitCount = 0;
    int[] nBits = new int[nSymbolsInText];
    int[] values = new int[nSymbolsInText];
    BitOutputStore writer = new BitOutputStore();
    for (int i = 0; i < nSymbolsInText; i++) {
      int n = random.nextInt(32) + 1;  // nextInt(32) gives values 0 to 31
      int v = random.nextInt();
      nBits[i] = n;
      values[i] = v & MASK[n];  // zero-out bits past position n
      writer.appendBits(n, v);  // store only the n low-order bits, higher-order ignored.
      writer.appendBit(i & 1);
      bitCount += (n + 1);
    }

    int nBitsInText = writer.getEncodedTextLength();
    assertEquals(bitCount, nBitsInText, "Encoded bit count mismatch for seed " + seed);

    int nBytesInText = (nBitsInText + 7) / 8;
    byte[] content = writer.getEncodedText();
    assertEquals(nBytesInText, content.length, "Encoding length mismatch for seed " + seed);

    // Now test to see if the reader produces the same bits that
    // we stored in the input sequence
    BitInputStore reader = new BitInputStore(content);
    for (int i = 0; i < nSymbolsInText; i++) {
      int n = nBits[i];
      int v0 = values[i];
      int v1 = reader.getBits(n);

      assertEquals(v0, v1, "Mismatch values for seed " + seed + ", index" + i);

      int a = reader.getBit();
      assertEquals(a, i & 1, "Mismatch single-bit test for seed " + seed + ", index" + i);
    }

  }

}
