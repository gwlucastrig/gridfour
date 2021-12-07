/* --------------------------------------------------------------------
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
 * OUT OF OR IN CONNECTI
 * ---------------------------------------------------------------------
 */
 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 11/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.utils;

 
import java.io.File;
import java.util.Arrays;

/**
 * Provides a convenience utility for extracting command-line arguments for
 * running integration tests.
 * <p>
 * The intent of this class is to provide a uniform treatment of command-line
 * arguments. It also provides a way of discovering command-line arguments that
 * are not recognized by the application. This situation sometimes occurs when
 * the operator makes a typographical error in the specification of arguments.
 */
public class TestOptions {

  /**
   * The options that are recognized and processed by the this class. The
   * selection of options may be subject to change as this class evolves during
   * development.
   */
  public final static String[] BUILT_IN_OPTIONS = {
    "-in",
    "-out",
    "-tileSize",
    "-compress",
    "-verify",
    "-big",
    "-zScale",
    "-zOffset",
    "-showProgress",
    "-checksums"
  };

  File inputFile;
  File outputFile;
  int[] tileSize;
  String description;
  boolean compress;
  boolean checksums;
  boolean verify;
  boolean bigAddressSpace;
  boolean isZScaleSet = false;
  double zScale = 1;
  double zOffset = 0;
  boolean showProgress = true;

  /**
   * Indicates whether the specified string matches the pattern of a
   * command-line argument (is introduced by a negative or plus symbol.
   *
   * @param s the test string
   * @return true if the test string is an option
   */
  protected boolean isOption(String s) {
    char c = s.charAt(0);
    return c == '-' || c == '+';
  }

  /**
   * Check supplied options for validity. Input file is checked for existence
   * and readability.
   */
  protected void checkOptions() {
    if (inputFile != null) {
      if (!inputFile.exists()) {
        throw new IllegalArgumentException("Input file does not exist: " + inputFile.getPath());
      }
      if (!inputFile.canRead()) {
        throw new IllegalArgumentException("Unable to access input file: " + inputFile.getPath());
      }
    }

  }

  /**
   * Search the arguments for the specified option followed by a integer value,
   * marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @param defaultValue a default value to be returned if the option is not
   * found, or a null if none is supplied.
   * @return if found, a valid instance of Integer; otherwise, a null.
   */
  public Integer scanIntOption(
    String[] args,
    String option,
    boolean[] matched,
    Integer defaultValue)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          return Integer.parseInt(args[i + 1]);
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException("Illegal integer value for "
            + option + ", " + nex.getMessage(), nex);
        }
      }
    }
    return defaultValue;
  }

  /**
   * Search the arguments for the specified option followed by a long integer
   * value, marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid instance of Long; otherwise, a null.
   */
  public Long scanLongOption(String[] args, String option, boolean[] matched)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          return Long.parseLong(args[i + 1]);
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException(
            "Illegal integer value for " + option + ", " + nex.getMessage(),
            nex);
        }
      }
    }
    return null;
  }

  /**
   * Search the arguments for the specified option followed by a floating-point
   * value, marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid instance of Double; otherwise, a null.
   */
  public Double scanDoubleOption(String[] args, String option, boolean[] matched)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          return Double.parseDouble(args[i + 1]);
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException(
            "Illegal floating-point value for "
            + option + ", " + nex.getMessage(),
            nex);
        }
      }
    }
    return null;
  }

  /**
   * Search the arguments for the specified option followed by a floating-point
   * value, marking the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @param defaultValue a default value to be returned if the option is not
   * found
   * @return if found, a valid instance of Double; otherwise, a null.
   */
  public double scanDoubleOption(String[] args, String option, boolean[] matched, double defaultValue)
    throws IllegalArgumentException {
    Double d = scanDoubleOption(args, option, matched);
    if (d == null) {
      return defaultValue;
    }
    return d;
  }

  /**
   * Scans the argument array to see if it included the specification of a
   * boolean option in the form "-Option" or "-noOption". Note that boolean
   * arguments are single strings and do not take a companion argument (such as
   * "true" or "false").
   *
   * @param args a valid array of command-line arguments
   * @param option the target option
   * @param matched an array paralleling args to indicate whether the args
   * matched a search option.
   * @param defaultValue a default value to be returned if the option is not
   * found, or a null if none is supplied.
   * @return if found, a valid instance of Boolean; otherwise, a null.
   */
  public Boolean scanBooleanOption(
    String[] args,
    String option,
    boolean[] matched,
    Boolean defaultValue) {
    String notOption = "-no" + option.substring(1, option.length());
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (matched != null && matched.length == args.length) {
          matched[i] = true;
        }
        return true;
      } else if (args[i].equalsIgnoreCase(notOption)) {
        if (matched != null && matched.length == args.length) {
          matched[i] = true;
        }
        return false;
      }

    }

    return defaultValue;
  }

  /**
   * Indicates whether the specified option is given in the args array.
   *
   * @param args a valid array of command-line arguments
   * @param option the target option
   * @return true is the option is specified; otherwise, false.
   */
  boolean isOptionSpecified(String[] args, String option) {
    if (option != null) {
      for (String arg : args) {
        if (option.equalsIgnoreCase(arg)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Search the arguments for the specified option followed by a string, marking
   * the matched array if it is found.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid string; otherwise, a null.
   */
  public String scanStringOption(String[] args, String option, boolean[] matched)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          return args[i + 1];
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException(
            "Illegal floating-point value for " + option + ", "
            + nex.getMessage(), nex);
        }
      }
    }
    return null;
  }

  /**
   * Search the arguments for the specified option followed by a pair of
   * integers in the form ###x### which gives the integral width and height for
   * a specification.
   *
   * @param args a valid array of arguments.
   * @param option the target command-line argument (introduced by a dash)
   * @param matched an array parallel to args used to indicate which arguments
   * were identified by the scan operation.
   * @return if found, a valid array containing positive numeric values.
   */
  public int[] scanSizeOption(String[] args, String option, boolean[] matched)
    throws IllegalArgumentException {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(option)) {
        if (i == args.length - 1) {
          throw new IllegalArgumentException("Missing argument for " + option);
        }
        try {
          if (matched != null && matched.length == args.length) {
            matched[i] = true;
            matched[i + 1] = true;
          }
          String s = args[i + 1];
          int index = s.indexOf("x");
          if (index < 0) {
            index = s.indexOf(",");
          }
          if (index <= 1 || index == s.length() - 1) {
            throw new IllegalArgumentException(
              "Invalid entry where size specificaiton expected: " + s);
          }
          int[] result = new int[2];
          result[0] = Integer.parseInt(s.substring(0, index));
          result[1] = Integer.parseInt(s.substring(index + 1, s.length()));
          if (result[0] < 1 || result[1] < 1) {
            throw new IllegalArgumentException(
              "Invalid numeric values for size specification: " + s);
          }
          return result;
        } catch (NumberFormatException nex) {
          throw new IllegalArgumentException(
            "Illegal integer size value for "
            + option
            + ", " + nex.getMessage(), nex);
        }
      }
    }
    return new int[0];
  }

  /**
   * Checks to see that the args[] array is valid.
   *
   * @param args an array of arguments thay must not be null and must not
   * include null references, but zero-length arrays are allowed.
   */
  protected void checkForValidArgsArray(String[] args) {
    if (args == null) {
      throw new IllegalArgumentException("Null argument array not allowed");
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i] == null || args[i].length() == 0) {
        throw new IllegalArgumentException(
          "Null or zero-length argument at index " + i);
      }
    }
  }

  /**
   * Gets the extension from the specified file
   *
   * @param file a valid file reference
   * @return if found, a valid string (period not included); otherwise, a null.
   */
  public String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }

  /**
   * Scan the argument list, extracting standard arguments. A boolean array with
   * a one-to-one correspondence to args[] is returned indicating which
   * arguments were recognized.
   *
   * @param args a valid, potentially zero-length argument list.
   * @return a boolean array indicating which arguments were recognized.
   */
  public boolean[] argumentScan(String[] args) {

    checkForValidArgsArray(args);

    boolean[] matched = new boolean[args.length];
    if (args.length == 0) {
      return matched;
    }

    if (args.length == 1 && !isOption(args[0])) {
      // check for special case where one argument is passed
      // giving an input file name
      String inputFileName = args[0];
      matched[0] = true;
      inputFile = new File(inputFileName);
      checkOptions();
      return matched;
    } else {
      String inputFileName = scanStringOption(args, "-in", matched);
      if (inputFileName != null) {
        inputFile = new File(inputFileName);
      }
    }

    String outputFileName = scanStringOption(args, "-out", matched);
    if (outputFileName != null) {
      outputFile = new File(outputFileName);
    }

    bigAddressSpace
      = scanBooleanOption(args, "-big", matched, bigAddressSpace);
    verify
      = scanBooleanOption(args, "-verify", matched, verify);
    compress
      = scanBooleanOption(args, "-compress", matched, compress);
    checksums
      = scanBooleanOption(args, "-checksums", matched, checksums);

    isZScaleSet = isOptionSpecified(args, "-zScale");
    zScale = scanDoubleOption(args, "-zScale", matched, zScale);
    zOffset = scanDoubleOption(args, "-zOffset", matched, zOffset);

    showProgress = scanBooleanOption(
      args, "-showProgress", matched, showProgress);

    tileSize = scanSizeOption(args, "-tileSize", matched);
    checkOptions();

    return matched;
  }

  /**
   * Gets the input file.
   *
   * @return if specified, a valid input file; otherwise, a null
   */
  public File getInputFile() {
    return inputFile;
  }

  /**
   * Gets the output file.
   *
   * @return if specified, a valid input file; otherwise, a null.
   */
  public File getOutputFile() {
    return outputFile;
  }

  /**
   * Gets the root string for the input file, removing the file-type suffix from
   * the end of the string. This method is intended for use in applications
   * where the output file has the same root name as the input file, but a
   * different file-type suffix is appended.
   *
   * @return if the input file is specified, a valid string; otherwise, a null.
   */
  public String getInputFileRootString() {
    if (inputFile == null) {
      return null;
    }
    String path = inputFile.getPath();
    int index = path.lastIndexOf('.');
    if (index == -1) {
      return path;
    }
    return path.substring(0, index);
  }

  /**
   * Performs a check for any arguments that were not matched.
   *
   * @param args a valid array of arguments
   * @param matched a parallel array to args, used to track which arguments were
   * matched to supported options.
   */
  public void checkForUnrecognizedArgument(String[] args, boolean[] matched) {
    checkForValidArgsArray(args);
    if (matched == null || matched.length < args.length) {
      throw new IllegalArgumentException(
        "Implementation error: matched array must correspond to args array");
    }

    for (int i = 0; i < args.length; i++) {
      if (isOption(args[i]) && !matched[i]) {
        throw new IllegalArgumentException("Unrecognized argument " + args[i]);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (!matched[i]) {
        throw new IllegalArgumentException("Unrecognized argument " + args[i]);
      }
    }
  }

  public void checkForMandatoryOptions(String args[], String[] options) {
    for (String s : options) {
      if ("-in".equalsIgnoreCase(s)) {
        // the -in option has a special syntax where it may
        // be specified as a single-argument on the command line
        if (inputFile != null) {
          continue;
        }
        boolean found = false;
        for (String t : args) {
          if (s.equalsIgnoreCase(t)) {
            found = true;
            break;
          }
        }
        if (!found) {
          throw new IllegalArgumentException("Missing mandatory setting for " + s);
        }
      }
    }
  }

  /**
   * Indicates whether data compression is enabled
   *
   * @return true if data compression is enabled; otherwise, false
   */
  public boolean isCompressionEnabled() {
    return compress;
  }

  
   /**
   * Indicates whether checksums should be computed when writing data
   *
   * @return true if checksum computations are enabled; otherwise, false
   */
  public boolean isChecksumComputationEnabled() {
    return checksums;
  }

  /**
   * Indicates whether data verification should be at the end of the process.
   *
   * @return true if data verification is enabled; otherwise, false
   */
  public boolean isVerificationEnabled() {
    return verify;
  }

  /**
   * Indicates whether the application should print progress messages during
   * lengthy processing operations.
   *
   * @return true if the application should print progress messages; otherwise,
   * false.
   */
  public boolean isShowProgressEnabled() {
    return showProgress;
  }

  /**
   * Gets the tile size for constructing a raster file.
   *
   * @param defaultRowCount default number of rows
   * @param defaultColumnCount default number of columns
   * @return if specified, an array giving the number of rows and columns for a
   * tile; otherwise, a zero-size array.
   */
  public int[] getTileSize(int defaultRowCount, int defaultColumnCount) {
    if (tileSize == null || tileSize.length != 2) {
      int[] result = new int[2];
      result[0] = defaultRowCount;
      result[1] = defaultColumnCount;
      return result;
    }
    return Arrays.copyOf(tileSize, tileSize.length);
  }

  /**
   * Gets the scaling factor for converting real-valued variables
   * to scaled integers.
   *
   * @return a valid floating-point value, by default 1.
   */
  public double getZScale() {
    return zScale;
  }

  /**
   * Gets the offset factor for converting real-valued variables
   * to scaled integers.
   *
   * @return a valid floating-point value, by default 0.
   */
  public double getZOffset() {
    return zOffset;
  }

  /**
   * Indicates whether a z-scale value was specified
   *
   * @return true if a scale value was specified; otherwise, false.
   */
  public boolean isZScaleSpecified() {
    return isZScaleSet;
  }
}
