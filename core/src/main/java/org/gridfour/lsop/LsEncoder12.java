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
 *  Test on this class show that for digital elevation models and
 * photographic imagery, the Huffman encoder is preferred over the
 * Deflate encoder a majority of the time.  I speculate that the
 * predictor does so well that the residuals are essentially noise.
 * Now, the values of the residuals do not follow any predictable pattern
 * in terms of the sequence in which they are stored, so the advantages
 * that are usually associated with Deflate are unavailable.
 * But if the magnitude of the residuals does have an do have an
 * identifiable frequency distribution (with smaller residuals being more
 * common), the Huffman would be effective. In the absense of patterns,
 * Deflate essentially devolves into a Huffman encoder. But our Huffman
 * is an unusually efficient one in terms of reducing the overhead for the
 * symbol table, so in such cases it is preferred over Deflate.
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
 * Optimal Predictor with 12 coefficients.
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
public class LsEncoder12 implements ICompressionEncoder {

  private final LsOptimalPredictor12 optimalPredictor
    = new LsOptimalPredictor12();

  private boolean deflateEnabled = true;

  /**
   * Enables or disables the use of Deflate in the compression sequence.
   * When Deflate is enabled, this class will attempt to compress data
   * using both both Huffman and Deflate compression. For data with good
   * predictability, the Huffman encoding will often achieve better compression
   * results than the Deflate method. For such data types, disabling
   * Deflate may expedite processing with only a negligible increase
   * in output size.
   *
   * @param enabled true if Deflate is enabled; otherwise, false.
   */
  public void setDeflateEnabled(boolean enabled) {
    this.deflateEnabled = enabled;
  }

  @Override
  public byte[] encode(int codecIndex, int nRows, int nCols, int[] values) {
    LsOptimalPredictorResult result = optimalPredictor.encode(
      nRows, nCols, values);
    if (result == null) {
      return null;
    }

    // construct an array giving the header for the packing.
    // initially, the "generic compression" method is set
    // to 1 (meaning Deflate), but this may be overwritten by zero
    // if testing determines that Huffman would require less storage.
    byte[] header = LsHeader.packHeader(
      codecIndex,
      12,
      result.seed,
      result.coefficients,
      result.nInitializerCodes,
      result.nInteriorCodes,
      LsHeader.COMPRESSION_TYPE_HUFFMAN);

    HuffmanEncoder huffman = new HuffmanEncoder();
    BitOutputStore store = new BitOutputStore();
    huffman.encode(store, result.nInitializerCodes, result.initializerCodes);
    huffman.encode(store, result.nInteriorCodes, result.interiorCodes);
    int huffLength = store.getEncodedTextLengthInBytes();

    byte[] packing = new byte[header.length + huffLength];
    byte[] huff = store.getEncodedText();
    System.arraycopy(header, 0, packing, 0, header.length);
    System.arraycopy(huff, 0, packing, header.length, huff.length);

    if (!deflateEnabled) {
      return packing;
    }

    // Recall that the LSOP encoding requires two separate sequences
    //   1. the initialization sequence for the outside rows and columns
    //      of the grid
    //   2. the predictor sequence for the inside of the grid, the
    //      part which can be populated using the predictor.
    // Experimentation showed that the Deflate output was smaller if
    // the two sequences were compressed separately. I hypothesize that
    // the reason for this is that the staistical properties of the two
    // sequences are different enough that combining them in a single
    // output adds overhead to the Deflate output and reduces compressibility.
    //
    // Even though we will store the encoding for the initialization
    // before the encoding for the interior, we process the interior first.
    // This approach gives us a chance for an early exit if the interior
    // is larger than the Huffman coding.  If it is, there is no point
    // in processing the initialization because Deflate is not going to be used.
    Deflater deflater = new Deflater(6);
    deflater.setInput(result.interiorCodes, 0, result.nInteriorCodes);
    deflater.finish();
    byte[] insidePack = new byte[result.nInteriorCodes + 128];
    int insideN = deflater.deflate(insidePack, 0, insidePack.length, Deflater.FULL_FLUSH);
    if (insideN <= 0 || insideN >= huffLength) {
      // either the deflate failed (insideN<=0) or the Deflate results for
      // the inside region of the grid is larger than Huffman.
      // In either case, we're done.
      return packing;
    }

    deflater = new Deflater(6);
    deflater.setInput(result.initializerCodes, 0, result.nInitializerCodes);
    deflater.finish();
    byte[] initPack = new byte[result.nInitializerCodes + 128];
    int initN = deflater.deflate(initPack, 0, initPack.length, Deflater.FULL_FLUSH);
    if (initN <= 0 || initN + insideN >= huffLength) {
      // either the deflate failed (insideN<=0) or the Deflate results for
      // the overall grid is larger than Huffman.
      return packing;
    }

    packing = new byte[header.length + initN + insideN];
    header[header.length - 1] = LsHeader.COMPRESSION_TYPE_DEFLATE;
    System.arraycopy(header, 0, packing, 0, header.length);
    System.arraycopy(initPack, 0, packing, header.length, initN);
    System.arraycopy(insidePack, 0, packing, header.length + initN, insideN);

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
