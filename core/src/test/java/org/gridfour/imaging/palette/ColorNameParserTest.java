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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
 
public class ColorNameParserTest {

  public ColorNameParserTest() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  @Test
  public void testAllNamedColors() {
    ColorNameParser parser = new ColorNameParser();
    List<String>nameList = parser.getNames();
    int index = 0;
    try ( InputStream ins = ColorNameParser.class.getResourceAsStream("rgb.txt"); 
      InputStreamReader inr = new InputStreamReader(ins, "US-ASCII");  
      BufferedReader reader = new BufferedReader(inr);)
    {
      String line;
         while((line = reader.readLine())!=null){
        String rgbString = line.substring(0, 12).trim();
        String name = line.substring(12, line.length()).trim();
        String []a = rgbString.split(" ");
        int []rgb = new int[3];
        int k=0;
        for(int i=0; i<a.length; i++){
          if(a[i]!=null && !a[i].isEmpty()){
             rgb[k++] = Integer.parseInt(a[i].trim());
          }
        }
        Color c = parser.parse(name);
        if(c==null){
          fail("Unrecognized color "+name);
        }
        assertEquals(rgb[0], c.getRed(),   "Mismatch red value for "+name);
        assertEquals(rgb[1], c.getGreen(), "Mismatch green value for "+name);
        assertEquals(rgb[2], c.getBlue(),  "Mismatch blue value for "+name);
        assertEquals(nameList.get(index), name, "Mismatch for color name"+name);
        index++;
      }
    } catch (IOException ioex) {
      fail("Internal error reading rgb.txt "+ioex.getMessage());
    }
  }
}
