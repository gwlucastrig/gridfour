/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.

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
 * 12/2019  G. Lucas     Introduced to support application-contributed
 *                       data-compression codecs.
 * 07/2020  G. Lucas     Refactored to improve architecture.
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides specification strings related to the use of data encoders
 * and decoders in Gvrs-formatted files.
 */
class CodecSpecification {

    private final String identification;
    private final String encoderClassName;
    private final String decoderClassName;

    private CodecSpecification() {
        identification = null;
        encoderClassName = null;
        decoderClassName = null;
    }

    CodecSpecification(String identification, String encoderClassName, String decoderClassName) {
        this.identification = identification;
        this.encoderClassName = encoderClassName; // encoder;
        this.decoderClassName = decoderClassName;
    }

    /**
     * Gets the identification string for the codec.
     *
     * @return a string of up to 16 ASCII characters, should follow
     * the syntax of a Java identification string.
     */
    String getIdentification() {
        return identification;
    }

    /**
     * Gets the class name for the encoder specification
     *
     * @return a valid string
     */
    String getEncoderClassName() {
        return encoderClassName;
    }

    /**
     * Gets the class name for the decoder specification
     *
     * @return a valid string
     */
    String getDecoderClassName() {
        return decoderClassName;
    }

    CodecHolder getHolder(boolean mandatory) throws IOException {

        Class<?> encoderClass = null;
        try {
            encoderClass = Class.forName(encoderClassName);
            if (!ICompressionEncoder.class.isAssignableFrom(encoderClass)) {
                throw new IOException(
                    "Codec specification " + identification
                    + " encoder does not implement "
                    + ICompressionEncoder.class.getName());
            }
        } catch (ClassNotFoundException ex) {
            // don't care, writing is not required
        }

        Class<?> decoderClass = null;

        try {
            decoderClass = Class.forName(decoderClassName);
            if (!ICompressionDecoder.class.isAssignableFrom(decoderClass)) {
                throw new IOException(
                    "Codec specification " + identification
                    + " decoder does not implement "
                    + ICompressionDecoder.class.getName());
            }
        } catch (ClassNotFoundException ex) {
            if (mandatory) {
                throw new IOException(
                    "Codec specification " + identification
                    + " refers to unavailable class " + identification, ex);
            }
        }

        if (decoderClass == null) {
            return null;
        }
        return new CodecHolder(
            identification, encoderClass, decoderClass);
    }

    static String specificationStringFormat(List<CodecHolder> csList) {
        StringBuilder sb = new StringBuilder();
        for (CodecHolder cs : csList) {
            String codecID = cs.getIdentification();
            Class<?> encoderClass = cs.getEncoder();
            Class<?> decoderClass = cs.getDecoder();
            sb.append(codecID).append(',')
                .append(encoderClass.getCanonicalName()).append(',')
                .append(decoderClass.getCanonicalName()).append('\n');
        }
        return sb.toString();
    }

    static List<CodecSpecification> specificationStringParse(String string)
        throws IOException {
        List<CodecSpecification> csList = new ArrayList<>();
        int iValue = 0;
        StringBuilder sb = new StringBuilder();
        String[] values = new String[3];
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == ',') {
                if (iValue > 2) {
                    throw new IOException(
                        "Too many fields in codec specification");
                }
                values[iValue++] = sb.toString();
                sb.setLength(0);
            } else if (c == '\n') {
                if (iValue == 2 && sb.length() > 0) {
                    values[iValue++] = sb.toString();
                    sb.setLength(0);
                }
                if (iValue != 3) {
                    throw new IOException("Insufficient fields in codec specification");
                }
                CodecSpecification spec
                    = new CodecSpecification(values[0], values[1], values[2]);
                csList.add(spec);
                iValue = 0;
            } else if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }

        return csList;
    }

}
