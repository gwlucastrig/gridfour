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
 * 03/2020  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.gridfour.compress;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.gridfour.io.BitInputStore;
import org.gridfour.io.BitOutputStore;

/**
 * Provides data compression and decompression for floating-point
 * values. Integer values are not supported.
 */
public class CodecFloat implements ICompressionEncoder, ICompressionDecoder {

  private static class SimpleStats {

    int nSum;
    long sum;

    double eSum;

    void addCount(int counts) {
      sum += counts;
      nSum++;
    }

    void surveyPacking(int n, int offset, byte[]packing){
      if(n==0){
        return;
      }
      int []count = new int[256];
      for(int i=0; i<n; i++){
        count[packing[i+offset]&0xff]++;
      }

      double e = 0;
      for(int i=0; i<256; i++){
        if(count[i]>0){
          double p = (double)count[i]/(double)n;
          e+=p*Math.log(p);
        }
      }

      eSum+= -e/Math.log(2.0);
    }

    double getAvgCount() {
      if (nSum == 0) {
        return 0;
      }
      return sum / (double) nSum;
    }

    void clear() {
      nSum = 0;
      sum = 0;
      eSum = 0;
    }

    double getAvgEntropy(){
      return eSum/nSum;
    }
  }

  int nCellsInTile;
  boolean wasDataEncoded;

  SimpleStats sTotal = new SimpleStats();
  SimpleStats sSignBit = new SimpleStats();
  SimpleStats sExp = new SimpleStats();
  SimpleStats sM1Delta = new SimpleStats();
  SimpleStats sM2Delta = new SimpleStats();
  SimpleStats sM3Delta = new SimpleStats();

  @Override
  public int[] decode(int nRows, int nColumns, byte[] packing) throws IOException {
    throw new IOException(
      "Attempt to decode an integral format not supported by this CODEC");
  }

  @Override
  public byte[] encode(int codecIndex, int nRows, int nCols, int[] values) {
    throw new IllegalArgumentException(
      "Attempt to enccode an integral format not supported by this CODEC");
  }

  @Override
  public void analyze(int nRows, int nColumns, byte[] packing) throws IOException {
    nCellsInTile = nRows * nColumns;
    int nBytesForSignBits = (nCellsInTile + 7) / 8;
    byte[] scratch = new byte[nCellsInTile];

    wasDataEncoded = true;
    int offset = 2;
    int n = unpackInteger(packing, offset);
    sSignBit.addCount(n);
    doInflate(packing, offset + 4, n, scratch, nBytesForSignBits);
    sSignBit.surveyPacking(nBytesForSignBits, 0, scratch);
    offset += (4 + n);

    n = unpackInteger(packing, offset);
    sExp.addCount(n);
    doInflate(packing, offset + 4, n, scratch, nCellsInTile);
    sExp.surveyPacking(nCellsInTile, 0, scratch);
    offset += (4 + n);

    n = unpackInteger(packing, offset);
    sM1Delta.addCount(n);
    doInflate(packing, offset + 4, n, scratch, nCellsInTile);
    sM1Delta.surveyPacking(nCellsInTile, 0, scratch);
    offset += (4 + n);

    n = unpackInteger(packing, offset);
    sM2Delta.addCount(n);
    doInflate(packing, offset + 4, n, scratch, nCellsInTile);
    sM2Delta.surveyPacking(nCellsInTile, 0, scratch);
    offset += (4 + n);

    n = unpackInteger(packing, offset);
    sM3Delta.addCount(n);
    doInflate(packing, offset + 4, n, scratch, nCellsInTile);
    sM3Delta.surveyPacking(nCellsInTile, 0, scratch);
    offset += (4 + n);

    sTotal.addCount(offset);
  }

  @Override
  public void reportAnalysisData(PrintStream ps, int nTilesInRaster) {
    if(nCellsInTile==0){
      // no statistics were gathered, do nothing
      return;
    }
    if (wasDataEncoded) {
      double avgBitsPerSample = sTotal.getAvgCount() * 8.0 / nCellsInTile;
      double avgBytesPerSample = sTotal.getAvgCount() / nCellsInTile;

      ps.println("Gridfour_Float");
      ps.format("   Times Used: %d%n", sSignBit.nSum);
      ps.format("   Average bytes per tile, by element%n");
      ps.format("     Element             Input      Compressed     (Reduction)    Entropy   Bits/Symbol%n");
      // To get an accurate count of the input, we need to make
      // special adjustments.   The sign bits are packed into bytes,
      // so they require 1/8th as many bytes as other values.
      // The high-mantissa includes only 7 bits per cell.
      int kSignBit = (nCellsInTile+7)/8;
      int kM1 = (nCellsInTile*7)/8;
      ps.format("     Sign bits        %8d   %12.2f       (%7.4f%%)     %5.2f    %5.2f%n",
        kSignBit,
        sSignBit.getAvgCount(),
        100*sSignBit.getAvgCount() / (double)kSignBit,
        sSignBit.getAvgEntropy(),
        sSignBit.getAvgCount()/(double)kSignBit);
      ps.format("     Exponent         %8d   %12.2f       (%7.4f%%)     %5.2f    %5.2f%n",
        nCellsInTile,
        sExp.getAvgCount(),
        100*sExp.getAvgCount() / nCellsInTile,
        sExp.getAvgEntropy(),
        8*sExp.getAvgCount()/nCellsInTile);

      // The mantissa 1 only contains 7 bits, not 8.  So we adjust.
      ps.format("     Mantissa-1 delta %8d   %12.2f       (%7.4f%%)     %5.2f    %5.2f%n",
        kM1,
        sM1Delta.getAvgCount(),
        100*sM1Delta.getAvgCount() / (double)kM1,
        sM1Delta.getAvgEntropy(),
        8*sM1Delta.getAvgCount()/nCellsInTile);

      ps.format("     Mantissa-2 delta %8d   %12.2f       (%7.4f%%)     %5.2f    %5.2f%n",
        nCellsInTile,
        sM2Delta.getAvgCount(),
        100*sM2Delta.getAvgCount() / nCellsInTile,
        sM2Delta.getAvgEntropy(),
        8*sM2Delta.getAvgCount()/nCellsInTile);
      ps.format("     Mantissa-3 delta %8d   %12.2f       (%7.4f%%)     %5.2f    %5.2f%n",
        nCellsInTile,
        sM3Delta.getAvgCount(),
        100*sM3Delta.getAvgCount() / nCellsInTile,
        sM3Delta.getAvgEntropy(),
        8*sM3Delta.getAvgCount()/nCellsInTile);
      ps.println();
      ps.format("   Average Bytes/Tile    %12.2f        (%7.4f%%)%n",
        sTotal.getAvgCount(),
        100*sTotal.getAvgCount() / (nCellsInTile * 4));
      ps.format("   Average Bytes/Sample  %12.2f        (%7.4f%%)%n",
        avgBytesPerSample,
        100*avgBytesPerSample / 4);
      ps.format("   Average Bits/Sample   %12.2f%n", avgBitsPerSample);
    } else {
      ps.println("Gridfour_Float (not used)");
    }
  }

  @Override
  public void clearAnalysisData() {
    sSignBit.clear();
    sExp.clear();
    sM1Delta.clear();
    sM2Delta.clear();
    sM3Delta.clear();
    sTotal.clear();

  }

  private int packBytes(byte[] output, int offset, byte[] sequence) {
    int sequenceLength = sequence.length;
    packInteger(output, offset, sequence.length);
    System.arraycopy(sequence, 0, output, offset + 4, sequenceLength);
    return offset + sequenceLength + 4;
  }

  private int packInteger(byte[] output, int offset, int iValue) {
    output[offset] = (byte) (iValue & 0xff);
    output[offset + 1] = (byte) ((iValue >> 8) & 0xff);
    output[offset + 2] = (byte) ((iValue >> 16) & 0xff);
    output[offset + 3] = (byte) ((iValue >> 24) & 0xff);
    return offset + 4;
  }

  private int unpackInteger(byte[] input, int offset) {
    return (input[offset] & 0xff)
      | ((input[offset + 1] & 0xff) << 8)
      | ((input[offset + 2] & 0xff) << 16)
      | ((input[offset + 3] & 0xff) << 24);

  }

  private byte[] doDeflate(byte[] input, SimpleStats stats) {
    // we always reset the deflater after operation to ensure
    // that it does not retain any objects generated during the compression.
    Deflater deflater = new Deflater(9);
    deflater.setInput(input);
    deflater.finish();
    byte[] resultB = new byte[input.length + 128];
    int dB = deflater.deflate(resultB, 0, resultB.length, Deflater.FULL_FLUSH);
    stats.addCount(dB);
    if (dB <= 0) {
      // deflate failed
      throw new RuntimeException("Deflate failed");
    }
    return Arrays.copyOf(resultB, dB);

  }

  int doInflate(byte[] input, int offset, int length, byte[] output, int outputLength) {
    Inflater inflater = new Inflater();
    inflater.setInput(input, offset, length);
    try {
      int test = inflater.inflate(output, 0, outputLength);
      inflater.end();
      if (test < 0) {
        throw new RuntimeException("Inflate failed");
      }
      return test;
    } catch (DataFormatException dfex) {
      throw new RuntimeException("Inflate failed");
    }
  }

  void encodeDeltas(byte[] scratch, int nRows, int nColumns) {
    int prior0 = 0;
    int test;
    int k = 0;
    for (int iRow = 0; iRow < nRows; iRow++) {
      int prior = prior0;
      prior0 = scratch[k];
      for (int iCol = 0; iCol < nColumns; iCol++) {
        test = scratch[k];
        scratch[k++] = (byte) (test - prior);
        prior = test;
      }
    }
  }

  void decodeDeltas(byte[] scratch, int nRows, int nColumns) {
    int prior = 0;
    int k = 0;
    for (int iRow = 0; iRow < nRows; iRow++) {
      for (int iCol = 0; iCol < nColumns; iCol++) {
        prior += scratch[k];
        scratch[k++] = (byte) prior;
      }
      prior = scratch[iRow * nColumns];
    }
  }

  @Override
  public byte[] encodeFloats(int codecIndex, int nRows, int nColumns, float[] values) {
    nCellsInTile = nRows * nColumns;
    wasDataEncoded = true;

    int[] c = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      c[i] = Float.floatToRawIntBits(values[i]);
    }
    BitOutputStore bSign = new BitOutputStore();
    for (int i = 0; i < c.length; i++) {
      int bit = (c[i] >> 31) & 1;
      bSign.appendBit(bit);
    }

    byte[] compSignBit = doDeflate(bSign.getEncodedText(), this.sSignBit);
    byte[] scratch = new byte[c.length];
    for (int i = 0; i < c.length; i++) {
      // get the exponent part of the floating point value
      scratch[i] = (byte) ((c[i] >> 23) & 0xff);
    }
    byte[] compExp = doDeflate(scratch, sExp);

    for (int i = 0; i < c.length; i++) {
      // get the high byte of the mantissa (7 bits)
      scratch[i] = (byte) ((c[i] >> 16) & 0x7f);
    }
    encodeDeltas(scratch, nRows, nColumns);
    byte[] compM1 = doDeflate(scratch, sM1Delta);

    for (int i = 0; i < c.length; i++) {
      // get the middle byte of the mantissa (8 bits)
      scratch[i] = (byte) ((c[i] >> 8) & 0xff);
    }
    encodeDeltas(scratch, nRows, nColumns);
    byte[] compM2 = doDeflate(scratch, sM2Delta);

    for (int i = 0; i < c.length; i++) {
      // get the low byte of the mantissa (8 bits)
      scratch[i] = (byte) (c[i] & 0xff);
    }
    encodeDeltas(scratch, nRows, nColumns);
    byte[] compM3 = doDeflate(scratch, sM3Delta);

    int nPacked = compSignBit.length
      + compExp.length
      + compM1.length
      + compM2.length
      + compM3.length;

    byte[] packing = new byte[nPacked + 2 + 5 * 4];
    sTotal.addCount(packing.length);

    packing[0] = (byte) codecIndex;
    packing[1] = (byte) 0;
    int offset = 2;
    offset = packBytes(packing, offset, compSignBit);
    offset = packBytes(packing, offset, compExp);
    offset = packBytes(packing, offset, compM1);
    offset = packBytes(packing, offset, compM2);
    offset = packBytes(packing, offset, compM3);

    assert offset == packing.length : "Incorrect packing";

    return packing;
  }

  @Override
  public float[] decodeFloats(int nRows, int nColumns, byte[] packing) throws IOException {
    nCellsInTile = nRows * nColumns;
    byte[] scratch = new byte[nCellsInTile];
    int[] rawInt = new int[nCellsInTile];
    float[] f = new float[nCellsInTile];
    int nSignBytes = (nCellsInTile + 7) / 8;

    // int index = packing[0];      // not used at this time
    // int predictor = packing[1];  // not used at this time
    int offset = 2;
    int n = unpackInteger(packing, offset);
    offset += 4;
    doInflate(packing, offset, n, scratch, nSignBytes);
    BitInputStore bins = new BitInputStore(scratch);
    int signBit = 0;
    for (int i = 0; i < nCellsInTile; i++) {
      signBit = bins.getBit();
      rawInt[i] = signBit << 31;
    }
    offset += n;

    n = unpackInteger(packing, offset);
    offset += 4;
    doInflate(packing, offset, n, scratch, nCellsInTile);
    for (int i = 0; i < nCellsInTile; i++) {
      rawInt[i] |= (scratch[i] & 0xff) << 23;
    }
    offset += n;

    n = unpackInteger(packing, offset);
    offset += 4;
    doInflate(packing, offset, n, scratch, nCellsInTile);
    decodeDeltas(scratch, nRows, nColumns);
    for (int i = 0; i < nCellsInTile; i++) {
      rawInt[i] |= (scratch[i] & 0x7f) << 16;
    }
    offset += n;

    n = unpackInteger(packing, offset);
    offset += 4;
    doInflate(packing, offset, n, scratch, nCellsInTile);
    decodeDeltas(scratch, nRows, nColumns);
    for (int i = 0; i < nCellsInTile; i++) {
      rawInt[i] |= (scratch[i] & 0xff) << 8;
    }
    offset += n;

    n = unpackInteger(packing, offset);
    offset += 4;
    doInflate(packing, offset, n, scratch, nCellsInTile);
    decodeDeltas(scratch, nRows, nColumns);
    for (int i = 0; i < nCellsInTile; i++) {
      rawInt[i] |= scratch[i] & 0xff;
    }
    offset += n;

    assert offset == packing.length : "Incorrect packing";

    for (int i = 0; i < nCellsInTile; i++) {
      f[i] = Float.intBitsToFloat(rawInt[i]);
    }

    return f;
  }

  @Override
  public boolean implementsFloatingPointEncoding() {
    return true;
  }

  @Override
  public boolean implementsIntegerEncoding() {
    return false;
  }

}
