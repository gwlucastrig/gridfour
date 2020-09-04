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
package org.gridfour.g93.lsop.compressor;

import org.gridfour.g93.G93FileSpecification;
import org.gridfour.g93.lsop.decompressor.LsDecoder08;
import org.gridfour.g93.lsop.decompressor.LsDecoder12;

/**
 * Provides convenience methods for adding the Ls8 encoder and decoder
 * to a G93 File Specification.
 */
public class LsCodecUtility {

  /**
   * The standard ID for the Smith and Lewis optimal-predictor based encoder and
   * decoder pair.
   */
  public static final String LSOP_CODEC_ID = "G93_LSOP12";

  /**
   * A static method to adds the LS encoder and decoder classes to
   * the G93 File Specification.
   *
   * @param spec a valid G93 file specification
   */
  public static void addLsopToSpecification(G93FileSpecification spec) {
    // spec.removeAllCompressionCodecs();
    // spec.addCompressionCodec("G93_LSOP08", LsEncoder08.class, LsDecoder08.class);
    spec.addCompressionCodec(LSOP_CODEC_ID, LsEncoder12.class, LsDecoder12.class);
  }

  /**
   * A private constructor to deter applications from constructing instances
   * of this class.
   */
  private LsCodecUtility() {
  }
}
