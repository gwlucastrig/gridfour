/*
 * The MIT License
 *
 * Copyright 2022 Gary W. Lucas
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
package org.gridfour.imaging.palette;

import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ColorPaletteTableReaderTest {

  public ColorPaletteTableReaderTest() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  @Test
  public void testInputStream() {
    ColorPaletteTableReader reader = new ColorPaletteTableReader();
    try ( InputStream ins = getClass().getResourceAsStream("OceanBasemap.cpt")) {
      ColorPaletteTable cpt = reader.read(ins);
      double minValue = cpt.getRangeMin();
      double maxValue = cpt.getRangeMax();
      assertEquals(-11000.0, minValue, "Invalid minimum range value");
      assertEquals(  8000.0, maxValue, "Invalid maximum range value");
      int argb = cpt.getArgb(0);
      assertEquals(argb, 0xffc0c0c0, "Bad mapping for zero");
    } catch (IOException ioex) {
      fail("Internal error reading rgb.txt " + ioex.getMessage());
    }
  }
}
