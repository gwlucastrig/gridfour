/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.
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
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

import org.gridfour.compress.ICompressionDecoder;
import org.gridfour.compress.ICompressionEncoder;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides data elements and methods related to the use of data compressors
 * and decompressors (encoders and decoders) in Gvrs-formatted files.
 */
class CodecHolder {

    private final String identification;
    private final Class<?> encoder;
    private final Class<?> decoder;
    private ICompressionEncoder encoderInstance;
    private ICompressionDecoder decoderInstance;

    /**
     * A private constructor to deter application code from creating
     * instances of this class.
     */
    private CodecHolder() {
        identification = null;
        encoder = null;
        decoder = null;
    }

    /**
     * Constructs an instance of this class.
     *
     * @param identification a valid, non-empty codec-identification string.
     * @param encoder the class name for the encoder (nulls allowed)
     * @param decoder the class name for the decoder
     */
    CodecHolder(String identification, Class<?> encoder, Class<?> decoder) {
        this.identification = identification;
        this.encoder = encoder;
        this.decoder = decoder;
    }

    CodecHolder(CodecHolder c) {
        identification = c.identification;
        encoder = c.encoder;
        decoder = c.decoder;
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
     * Gets the encoder class associated with the specified identification
     * string.
     *
     * @return a valid Java class object.
     */
    Class<?> getEncoder() {
        return encoder;
    }

    /**
     * Gets the encoder class associated with the specified identification
     * string.
     *
     * @return a valid Java class object.
     */
    Class<?> getDecoder() {
        return decoder;
    }

    /**
     * Used during parsing. Appends the specifications obtained
     * from the parse to a list of instances
     *
     * @param csList a valid list
     * @param values an array of dimension 3 giving identification,
     * encoder, and decoder
     * @param requireEncoding indicates whether the encoder must be
     * defined in the classpath
     * @throws IOException in the event of an invalid class specification.
     */
    private static void appendToList(
        List<CodecHolder> csList,
        String[] values,
        boolean requireEncoding) throws IOException {
        if (values[0].isEmpty()) {
            throw new IOException("Missing codec ID value");
        }
        Class<?> encoderClass = null;
        if (requireEncoding) {
            try {
                encoderClass = Class.forName(values[1]);
            } catch (ClassNotFoundException ex) {
                throw new IOException(
                    "Codec specification " + values[0]
                    + " refers to unavailable class " + values[1], ex);
            }
            if (!ICompressionEncoder.class.isAssignableFrom(encoderClass)) {
                throw new IOException(
                    "Codec specification " + values[0]
                    + " encoder does not implement "
                    + ICompressionEncoder.class.getName());
            }
        }
        Class<?> decoderClass = null;

        try {
            decoderClass = Class.forName(values[2]);
        } catch (ClassNotFoundException ex) {
            throw new IOException(
                "Codec specification " + values[0]
                + " refers to unavailable class " + values[2], ex);
        }
        if (!ICompressionDecoder.class.isAssignableFrom(decoderClass)) {
            throw new IOException(
                "Codec specification " + values[0]
                + " decoder does not implement "
                + ICompressionDecoder.class.getName());
        }

        CodecHolder codec = new CodecHolder(
            values[0], encoderClass, decoderClass);

        csList.add(codec);
    }

    private Object getInstance(String codecID, Class<?> c) throws IOException {
        try {
            Constructor<?> constructor = c.getConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException ex) {
            throw new IOException("Missing no-argument constructor for codec "
                + codecID + ", " + c.getName(), ex);
        } catch (SecurityException ex) {
            throw new IOException("Security exception for codec "
                + codecID + ", " + c.getName(), ex);
        } catch (InstantiationException
            | IllegalAccessException
            | IllegalArgumentException
            | InvocationTargetException ex) {
            throw new IOException("Failed to construct codec "
                + codecID + ", " + c.getName(), ex);
        }
    }

    ICompressionEncoder getEncoderInstance() {
        if (encoderInstance == null) {
            if (encoder != null) {
                try {
                    encoderInstance = (ICompressionEncoder) getInstance(identification, encoder);
                } catch (IOException ioex) {
                    // dontCare
                }
            }
        }
        return encoderInstance;
    }

    ICompressionDecoder getDecoderInstance() {
        if (decoderInstance == null) {
            if (decoder != null) {
                try {
                    decoderInstance = (ICompressionDecoder) getInstance(identification, decoder);
                } catch (IOException ioex) {
                    // dontCare
                }
            }
        }
        return decoderInstance;
    }

    /**
     * Formats a specification string based on the content of the
     * application-supplied list of CodecHolder instances
     *
     * @param csList a valid, potentially empty, list
     * @return a valid, potentially empty string.
     */
    static String formatSpecificationString(List<CodecHolder> csList) {
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

    /**
     * Parses a specification string, obtaining a list of CodecHolder instances.
     * During the parse, this method checks the class names obtained from
     * the specification and sees if they are defined in the classpath.
     * The decoder is always mandatory, but the encoder can be optional
     * depending on whether the requireEncoding flag is set to true.
     * <p>
     * The format for a line of specification is three strings in the form:
     * <br>
     * <pre>Codec ID, encoderClassName,  decoderClassName</pre>
     * <p>
     * If the parse is performed on a read-only file, then the encoder
     * specifications are immaterial, so this method will not throw an
     * exception if they are undefined. However, if the file is write
     * enabled, then the classes indicated by the encoding specifications
     * are mandatory.
     * <p>
     * This method throws an IOException in the event of parsing
     * issues. The reason that an IOException is used rather than
     * more specific exceptions is that parses are expected to be performed
     * by a file-opening method.
     *
     * @param string a valid string
     * @param requireEncoding indicates that the encoder class must
     * be included in the classpath.
     * @return a valid, potentially empty list of CodecHolder instances
     * @throws IOException if the string is incorrectly formatted or
     * any of the specified decoders or encoders are undefined.
     */
    static List<CodecHolder> parseSpecificationString(
        String string, boolean requireEncoding)
        throws IOException {
        List<CodecHolder> csList = new ArrayList<>();
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
                    throw new IOException(
                        "Insufficient fields in codec specification");
                }
                appendToList(csList, values, requireEncoding);
                iValue = 0;
            } else if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }

        if (iValue == 2 && sb.length() > 0) {
            // this could happen if the linefeed is missing from the
            // last line.  Although that condition is not expected to
            // happen, it is handled here:
            values[iValue++] = sb.toString();
            appendToList(csList, values, requireEncoding);
        }
        return csList;
    }

    /**
     * Indicates whether the encoder implements support for integer or
     * integer-coded data. If the encoder is null, this method returns
     * a value of false.
     *
     * @return true if integer coding is supported by the encoder;
     * otherwise, false.
     */
    boolean implementsIntegerEncoding() {

        ICompressionEncoder test = getEncoderInstance();
        if (test != null) {
            return test.implementsIntegerEncoding();
        }

        return false;
    }

    /**
     * Indicates whether the encoder implements support for floating-point
     * data. If the encoder is null, this method returns a value of false.
     *
     * @return true if integer coding is supported by the encoder;
     * otherwise, false.
     */
    boolean implementsFloatingPointEncoding() {

        ICompressionEncoder test = getEncoderInstance();
        if (test != null) {
            return test.implementsFloatingPointEncoding();
        }

        return false;
    }

}
