/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2026  Gary W. Lucas.

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
 * 03/2026  G. Lucas     Initial implementation
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.compress.canonicalHuffman;

import java.util.Arrays;
import org.gridfour.io.BitInputStore;
import org.gridfour.io.BitOutputStore;

class LengthEncoder {

  /**
   * The maximum standard symbol supported by this encoding.
   */
  static final int MAX_STANDARD_SYMBOL = 15;

  /**
   * Repeat the previous symbol 3 to 7 times using a 2-bit integer
   * repetition specification.
   */
  static final int REPEAT_PREV_2BITS = 16;  // 2 bits, 3 to 7 times
  /**
   * Repeat the value zero 3 to 10 times using a 3-bit integer
   * repetition specification.
   */
  static final int REPEAT_ZERO_3BITS = 17; // 3 bits, 3 to 10
  /**
   * Repeat the value zero 11 to 138 times using a 3-bit integer
   * repetition specification.
   */
  static final int REPEAT_ZERO_7BITS = 18; // 7 bits, 11 to 138

  /**
   * The number of codes used for length-encoding (one greater than the
   * highest numeric value for a symbol).
   */
  static final int SYMBOL_SET_SIZE = 19;

  int nCodedSymbols;
  int nCodes;
  int[] codes;
  int[] runLengths;

  LengthEncoder(int nCodedSymbols, int nCodes, int[] codes, int[] runLengths) {
    this.nCodedSymbols = nCodedSymbols;
    this.nCodes = nCodes;
    this.codes = Arrays.copyOf(codes, nCodes);
    this.runLengths = Arrays.copyOf(runLengths, nCodes);
  }

  static LengthEncoder encodeLengths(int n, int[] codeLen) {
    int[] countCode = new int[n];
    int[] runLength = new int[n];

    int prior = -1;
    int i;
    int nCountCode = 0;
    for (int iCodeLen = 0; iCodeLen < n; iCodeLen++) {
      if (codeLen[iCodeLen] > MAX_STANDARD_SYMBOL) {
        throw new IllegalArgumentException("Invalid code length: " + codeLen[iCodeLen]);
      }
      if (codeLen[iCodeLen] == 0) {
        prior = 0;
        for (i = iCodeLen + 1; i < n; i++) {
          if (codeLen[i] != 0) {
            break;
          }
        }
        int nZero = i - iCodeLen;
        if (nZero == 1) {
          countCode[nCountCode++] = 0;
        } else if (nZero == 2) {
          countCode[nCountCode++] = 0;
          countCode[nCountCode++] = 0;
          iCodeLen++; // skip the repeat
        } else if (nZero <= 10) {
          // in the range 3 to 10
          countCode[nCountCode] = REPEAT_ZERO_3BITS;
          runLength[nCountCode] = nZero - 3;
          nCountCode++;
          iCodeLen = i - 1; // -1 because loop control will add one
        } else {
          if (nZero > 138) {
            nZero = 138; // 138 is the maximum we can store
          }
          countCode[nCountCode] = REPEAT_ZERO_7BITS;
          runLength[nCountCode] = nZero - 11;
          nCountCode++;
          iCodeLen += (nZero - 1); // -1 because loop control will add one
        }
      } else {
        // non-zero
        if (codeLen[iCodeLen] == prior) {
          // The code length matches the prior.  If there are three in a row,
          // we can use the code REPEAT_PREV (2-bits).  But if there's only one or
          // two, we just store a literal.
          for (i = iCodeLen + 1; i < n; i++) {
            if (codeLen[i] != prior) {
              break;
            }
          }
          int nPrior = i - iCodeLen;
          // nPrior will be at least 1.
          switch (nPrior) {
            case 1:
              countCode[nCountCode++] = prior;
              break;
            case 2:
              // not enough to use this repeat code
              countCode[nCountCode++] = prior;
              countCode[nCountCode++] = prior;
              iCodeLen = i - 1; // -1 because loop control will add one
              break;
            default:
              if (nPrior > 6) {
                nPrior = 6;  // 6 is the maximum we can store
              }
              countCode[nCountCode] = REPEAT_PREV_2BITS;
              runLength[nCountCode] = nPrior - 3;
              nCountCode++;
              iCodeLen += (nPrior - 1); // -1 because loop control will add one
              break;
          }
        } else {
          prior = codeLen[iCodeLen];
          countCode[nCountCode++] = prior;
        }
      }
    }

    return new LengthEncoder(n, nCountCode, countCode, runLength);
  }

  static int[] decodeLengths(int nOutput, int nInput, int[] codes, int[] runLengths) {
    int nCodeLen = 0;
    int[] codeLen = new int[nOutput];
    int prior = 0;
    for (int iInput = 0; iInput < nInput; iInput++) {
      if (codes[iInput] <= MAX_STANDARD_SYMBOL) {
        // a simple literal
        prior = codes[iInput];
        codeLen[nCodeLen++] = prior;
      } else if (codes[iInput] == REPEAT_PREV_2BITS) {
        // simple repeat
        int n = runLengths[iInput] + 3;
        for (int i = 0; i < n; i++) {
          codeLen[nCodeLen++] = prior;
        }
      } else if (codes[iInput] == REPEAT_ZERO_3BITS) {
        prior = 0;
        int n = runLengths[iInput] + 3;
        for (int i = 0; i < n; i++) {
          codeLen[nCodeLen++] = 0;
        }
      } else if (codes[iInput] == REPEAT_ZERO_7BITS) {
        int n = runLengths[iInput] + 11;
        for (int i = 0; i < n; i++) {
          codeLen[nCodeLen++] = 0;
        }
      }
    }
    return codeLen;
  }

  static void writeEncodedLengths(BitOutputStore output, int nCodes, int[] codes, int[] runLengths) {
    for (int i = 0; i < nCodes; i++) {
      int index = codes[i];
      // write index as 5 bits
      output.appendBits(5, index);
      // now check for special bit sequences
      switch (index) {
        case LengthEncoder.REPEAT_PREV_2BITS: {
          int n = runLengths[i];
          output.appendBits(2, n);
          break;
        }
        case LengthEncoder.REPEAT_ZERO_3BITS: {
          int n = runLengths[i];
          output.appendBits(3, n);
          break;
        }
        case LengthEncoder.REPEAT_ZERO_7BITS: {
          int n = runLengths[i];
          output.appendBits(7, n);
          break;
        }
        default:
          break;
      }
    }
  }

  static int readEncodedLengths(BitInputStore input, int nSymbols, int[] symbols) {
    int k = 0;
    int prior = 0;
    int n;
    while (k < nSymbols) {
      // read 5 bits
      int index = input.getBits(5);
      if (index <= MAX_STANDARD_SYMBOL) {
        prior = index;
        symbols[k++] = index;
      } else {
        switch (index) {
          case REPEAT_PREV_2BITS:
            n = input.getBits(2) + 3;
            for (int i = 0; i < n; i++) {
              symbols[k++] = prior;
            }
            break;
          case REPEAT_ZERO_3BITS:
            prior = 0;
            n = input.getBits(3) + 3;
            for (int i = 0; i < n; i++) {
              symbols[k++] = 0;
            }
            break;

          case REPEAT_ZERO_7BITS:
            prior = 0;
            n = input.getBits(7) + 11;
            for (int i = 0; i < n; i++) {
              symbols[k++] = 0;
            }
            break;
          default:
            break; // we should never get here
        }
      }
    }
    return k;
  }
}
