/*
 * The MIT License
 *
 * Copyright 2021 G. W. Lucas.
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

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2021  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

/**
 * Provides support and definitions in support of the
 * GVRS convention for specifying identifiers:
 *  <ol>
    * <li>The first character must be an upper or lowercase ASCII letter.
    * This restriction is applied to support languages such as Python where
    * an initial underscore indicates a special interpretation for
    * an identifier.</li>
    * <li>A combination of ASCII characters including upper and lowercase
    * letters, numeric digits, underscores.</li>
    * </ol>
    * <p>
    * <strong>Note:</strong> GVRS identifiers are intended to be consistent
    * with naming conventions across a broad range of computer languages
    * (including C/C++, Java, and Python).  So the allowable character set
    * for identifiers is limited.
 */
 class GvrsIdentifier {

   /**
    * Verifies that the specified string meets the syntax requirements
    * for a GVRS identifier.
    * <ol>
    * <li>The first character must be an upper or lowercase ASCII letter.
    * This restriction is applied to support languages such as Python where
    * an initial underscore indicates a special interpretation for
    * an identifier.</li>
    * <li>A combination of ASCII characters including upper and lowercase
    * letters, numeric digits, underscores.</li>
    * </ol>
    * <p>
    * <strong>Note:</strong> GVRS identifiers are intended to be consistent
    * with naming conventions across a broad range of computer languages
    * (including C/C++, Java, and Python).  So the allowable character set
    * for identifiers is limited.
    * @param identifier a valid, non-empty string
    * @param maxLength maximum length of identifier.
    */
    public static void checkIdentifier(String identifier , int maxLength) {
    if (identifier == null || identifier.isEmpty()) {
      throw new IllegalArgumentException("Null or blank identifier not supported");
    }
    String s = identifier.trim();
    if(s.length()>maxLength){
      throw new IllegalArgumentException(
        "Length of specified identifier exceeds maximum length "+maxLength+" for \""+s+"\"");
    }

    // For convenience, this code uses the Java Character class and methods for
    // verifying identifiers. However, the defintion for identifiers
    // used here is common to many development environments and not
    // specific to Java.
    //   GVRS identifiers are slightly more restrictive than Java identifiers
    // in that they do not allow $ or _ as the first character.
    char c = s.charAt(0);
    if(!Character.isJavaIdentifierStart(c) || c=='$' || c=='_') {
      throw new IllegalArgumentException(
        "Invalid identifier, first character is not an ASCII letter: "+ s);
    }
    
    for (int i = 1; i < s.length(); i++) {
      c = s.charAt(i);
      if (!Character.isJavaIdentifierPart(c)|| c == '$') {
        throw new IllegalArgumentException(
          "Invalid character in identifier " + s);
      }
    }
  }
 
}
