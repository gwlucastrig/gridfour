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
 * 12/2019  G. Lucas     Introduced to support application-contributed
 *                       data-compression codecs.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.gridfour.g93;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


  /**
   * Provides a simple container for compression codec specifications.
   */
  public class G93SpecificationForCodec{
    private final String identification;
    private final Class<?> codec;
    private final int index;
    private G93SpecificationForCodec(){
      identification = null;
      codec = null;
      index = 0;
    }


    G93SpecificationForCodec(String identification, Class<?> codec, int index) {
      this.identification = identification;
      this.codec = codec;
      this.index = index;
    }

    /**
     * Gets the identification string for the codec.
     * @return a string of up to 16 ASCII characters, should follow
     * the syntax of a Java identification string.
     */
    public String getIdentification(){
      return identification;
    }

    /**
     * Gets the codec associated with the specified identification string.
     * @return a valid Java class object.
     */
    public Class<?> getCodec() {
      return codec;
    }

    /**
     * Get the numeric index of the compression specification
     * @return a value in the range 0 to 255.
     */
    public int getIndex(){
      return index;
    }


    public static List<G93SpecificationForCodec> parseSpecificationString(String string) throws IOException {
      List<G93SpecificationForCodec> csList = new ArrayList<>();
      String codecID = null;
      int mode = 0;
      StringBuilder sb = new StringBuilder();
      int index = 0;
      for (int i = 0; i < string.length(); i++) {
        char c = string.charAt(i);
        if (Character.isWhitespace(c)) {
          if (mode == 1 && c == '\n') {
            String path = sb.toString();
            try {
              Class<?> codec = Class.forName(path);
              csList.add(new G93SpecificationForCodec(codecID, codec, index++));
            } catch (ClassNotFoundException ex) {
              throw new IOException(
                      "Codec specification " + codecID
                      + " refers to unavailable class " + path, ex);
            }
            mode = 0;
          }
        } else if (c == ',') {
          if (mode == 0) {
            codecID = sb.toString();
            sb.setLength(0);
            mode = 1;
          } else {
            throw new IOException("Comma out of place in codec specification");
          }
        } else {
          sb.append(c);
        }
      }

      return csList;

    }
  }
