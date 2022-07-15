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
package org.gridfour.lsop;

import java.util.zip.Deflater;
import org.gridfour.compress.HuffmanEncoder;
import org.gridfour.compress.ICompressionEncoder;
import org.gridfour.io.BitOutputStore;

/**
 * Provides methods and data elements used to encode raster data to be
 * compressed using the LSOP format based on the methods of Lewis and Smith's
 * Optimal Predictor with 8 coefficients
 * <p>
 * The LS decoder and encoder are separated into separate packages and
 * separate modules in order to manage code dependencies. The encoding
 * process requires solving a 9-variable linear system. Doing so requires
 * the use of a 3rd party Java library, so an implementation that uses the
 * LS format introduces an additional dependency to the code base.
 * But the decoding process does not use any operations that would require
 * an external dependency. Thus the decoder is specified as part of the
 * Gridfour core module, but the encoder is not.
 */
public class LsEncoder08 implements ICompressionEncoder {

    private final LsOptimalPredictor08 optimalPredictor
        = new LsOptimalPredictor08();

    @Override
    strictfp public byte[] encode(int codecIndex, int nRows, int nCols, int[] values) {
        LsOptimalPredictorResult result
            = optimalPredictor.encode(nRows, nCols, values);
        if (result == null) {
            return null;
        }
        // construct an array giving the header for the packing.
        // initially, the "generic compression" method is set
        // to 1 (meaning Deflate), but this may be overwritten by zero
        // if testing determines that Huffman would require less storage.
        byte[] header = LsHeader.packHeader(
            codecIndex,
            8,
            result.seed,
            result.coefficients,
            result.nInitializerCodes,
            result.nInteriorCodes,
            LsHeader.COMPRESSION_TYPE_DEFLATE,
            false,
            0);

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

        byte[] packing = new byte[header.length + initN + insideN];

        System.arraycopy(header, 0, packing, 0, header.length);
        System.arraycopy(initPack, 0, packing, header.length, initN);
        System.arraycopy(insidePack, 0, packing, header.length + initN, insideN);

        HuffmanEncoder huffman = new HuffmanEncoder();
        BitOutputStore store = new BitOutputStore();
        huffman.encode(store, result.nInitializerCodes, result.initializerCodes);
        huffman.encode(store, result.nInteriorCodes, result.interiorCodes);
        int huffLength = store.getEncodedTextLengthInBytes();
        if (huffLength < initN + insideN) {
            header = LsHeader.packHeader(
              codecIndex,
              8,
              result.seed,
              result.coefficients,
              result.nInitializerCodes,
              result.nInteriorCodes,
              LsHeader.COMPRESSION_TYPE_HUFFMAN,
              false,
              0);
            packing = new byte[header.length + huffLength];
            byte[] huff = store.getEncodedText();
            header[header.length - 1] = 0;
            System.arraycopy(header, 0, packing, 0, header.length);
            System.arraycopy(huff, 0, packing, header.length, huff.length);
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

}
