/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2020  Gary W. Lucas.

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
 * 07/2020  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.lsComp;

import java.util.zip.Deflater;
import org.gridfour.g93.HuffmanEncoder;
import org.gridfour.g93.IG93Encoder;
import org.gridfour.io.BitOutputStore;

/**
 * Provides methods and data elements used to encode raster data to be
 * compressed
 * using the G93-LS8 format based on the methods of Lewis and Smith's
 * Optimal Predictor.
 * <p>
 * The LS8 decoder and encoder are separated into separate packages and
 * separate modules in order to manage code dependencies. The encoding
 * process requires solving a 9-variable linear system. Doing so requires
 * the use of a 3rd party Java library, so an implementation that uses the
 * LS8 format introduces an additional dependency to the code base.
 * But the decoding process does not use any operations that would require
 * an external dependency. Thus the decoder is specified as part of the
 * Gridfour core module, but the encoder is not.
 */
public class LS8Encoder implements IG93Encoder {

  private final LS8OptimalPredictor optimalPredictor
    = new LS8OptimalPredictor();

  @Override
  public byte[] encode(int codecIndex, int nRows, int nCols, int[] values) {
    LS8OptimalPredictorResult result
      = optimalPredictor.encode(nRows, nCols, values);
    if (result == null) {
      return null;
    }

    // the preface is 49 bytes:
    //    1 byte     codecIndex
    //    4 bytes    seed
    //    9*4 bytes  coefficients
    //    4 bytes    nInitializationCodes
    //    4 bytes    nInteriorCodes
    //    1 byte     method
    byte[] preface = new byte[46];
    preface[0] = (byte) (codecIndex & 0xff);
    int offset = packInteger(preface, 1, result.seed);
    for (int i = 0; i < 8; i++) {
      offset = packFloat(preface, offset, result.coefficients[i]);
    }
    offset = packInteger(preface, offset, result.nInitializerCodes);
    offset = packInteger(preface, offset, result.nInteriorCodes);
    preface[offset] = 1;

    Deflater deflater = new Deflater(6);
    deflater.setInput(result.initializerCodes, 0, result.nInitializerCodes);
    deflater.finish();
    byte[] initPack = new byte[result.nInitializerCodes + 128];
    int initN = deflater.deflate(initPack, 0, initPack.length, Deflater.FULL_FLUSH);
    if (initN <= 0) {
      // deflate failed
      return null;
    }

    deflater = new Deflater(6);
    deflater.setInput(result.interiorCodes, 0, result.nInteriorCodes);
    deflater.finish();
    byte[] insidePack = new byte[result.nInteriorCodes + 128];
    int insideN = deflater.deflate(insidePack, 0, insidePack.length, Deflater.FULL_FLUSH);
    if (insideN <= 0) {
      // deflate failed
      return null;
    }

    byte[] packing = new byte[46 + initN + insideN];

    System.arraycopy(preface, 0, packing, 0, 46);
    System.arraycopy(initPack, 0, packing, 46, initN);
    System.arraycopy(insidePack, 0, packing, 46 + initN, insideN);

    HuffmanEncoder huffman = new HuffmanEncoder();
    BitOutputStore store = new BitOutputStore();
    huffman.encode(store, result.nInitializerCodes, result.initializerCodes);
    huffman.encode(store, result.nInteriorCodes, result.interiorCodes);
    int huffLength = store.getEncodedTextLengthInBytes();
    if (huffLength < initN + insideN) {
      packing = new byte[46 + huffLength];
      byte[] huff = store.getEncodedText();
      preface[offset] = 0;
      System.arraycopy(preface, 0, packing, 0, 46);
      System.arraycopy(huff, 0, packing, 46, huff.length);
    }

    return packing;
  }

  @Override
  public byte[] encodeFloats(int codecIndex, int nRows, int nCols, float[] values) {
    return null;
  }

  @Override
  public boolean implementsFloatingPointEncoding() {
    return false;
  }

  @Override
  public boolean implementsIntegerEncoding() {
    return true;
  }

  private int packInteger(byte[] output, int offset, int iValue) {
    output[offset] = (byte) (iValue & 0xff);
    output[offset + 1] = (byte) ((iValue >> 8) & 0xff);
    output[offset + 2] = (byte) ((iValue >> 16) & 0xff);
    output[offset + 3] = (byte) ((iValue >> 24) & 0xff);
    return offset + 4;
  }

  private int packFloat(byte[] output, int offset, float f) {
    int iValue = Float.floatToRawIntBits(f);
    return packInteger(output, offset, iValue);
  }

}
