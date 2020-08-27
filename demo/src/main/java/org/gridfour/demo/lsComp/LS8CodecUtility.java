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



package org.gridfour.demo.lsComp;

import org.gridfour.g93.G93FileSpecification;
import org.gridfour.g93.lsComp.LS8Decoder;

/**
 * Provides convenience methods for adding the LS8 encoder and decoder
 * to a G93 File Specification.
 */
public class LS8CodecUtility {

  /**
   * The standard ID for the LS8 encoder and decoder pair.
   */
  public static final String LS8_CODEC_ID = "G93_LS8";

  /**
   * A static method to adds the LS8 encoder and decoder classes to
   * the G93 File Specification.
   *
   * @param spec a valid G93 file specification
   */
  public static void addLS8ToSpecification(G93FileSpecification spec) {
    spec.addCompressionCodec(LS8_CODEC_ID, LS8Encoder.class, LS8Decoder.class);
  }
}
