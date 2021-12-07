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
 * 10/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.gridfour.lsop;

import org.gridfour.gvrs.GvrsFileSpecification;

/**
 * Provides convenience methods for adding the LSOP encoder and decoder
 * to a GVRS File Specification.
 */
public class LsCodecUtility {

    /**
     * The standard ID for the Smith and Lewis optimal-predictor based encoder
     * and
     * decoder pair.
     */
    public static final String LSOP_CODEC_ID = "LSOP12";

    /**
     * A static method to adds the LS encoder and decoder classes to
     * the GVRS File Specification.
     * <p>
     * The exclusive option is intended for development and diagnostic
     * purposes. It adjusts the GvrsFileSpecification so that the default
     * compressor codecs are removed (if any) and the Optimal Predictor
     * codecs are used exclusively.
     *
     * @param spec a valid GVRS file specification
     * @param exclusive a flag indicating whether Optimal Predictors are to be
     * used exclusively for data compression (true) or whether other compressor
     * codec are to be included.
     */
    public static void addLsopToSpecification(GvrsFileSpecification spec, boolean exclusive) {
        if (exclusive) {
            spec.removeAllCompressionCodecs();
        }
        //spec.addCompressionCodec("LSOP08", LsEncoder08.class, LsDecoder08.class);
        spec.addCompressionCodec(LSOP_CODEC_ID, LsEncoder12.class, LsDecoder12.class);
    }

    /**
     * A private constructor to deter applications from constructing instances
     * of this class.
     */
    private LsCodecUtility() {
    }
}
