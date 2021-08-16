/* --------------------------------------------------------------------
 * Copyright (C) 2020  Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 08/2020  G. Lucas     Created
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
