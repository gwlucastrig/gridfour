/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridfour.demo.geoTiff;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import javax.imageio.ImageIO;
import org.apache.commons.imaging.FormatCompliance;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.common.bytesource.ByteSourceFile;
import org.apache.commons.imaging.formats.tiff.TiffContents;
import org.apache.commons.imaging.formats.tiff.TiffDirectory;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImagingParameters;
import org.apache.commons.imaging.formats.tiff.TiffRasterData;
import org.apache.commons.imaging.formats.tiff.TiffReader;
import org.apache.commons.imaging.formats.tiff.constants.GeoTiffTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.gridfour.demo.utils.TestOptions;
import org.gridfour.interpolation.InterpolationResult;
import org.gridfour.interpolation.InterpolationTarget;
import org.gridfour.interpolation.InterpolatorBSpline;
import org.tinfour.gis.shapefile.ShapefileReader;
import org.tinfour.gis.shapefile.ShapefileRecord;
import org.tinfour.gis.shapefile.ShapefileType;

/**
 * A simple example application that reads the content of a TIFF file containing
 * floating-point data and extracts its content. TIFF files are * sometimes used
 * to store non-image information for scientific and geophysical data products,
 * including terrestrial elevation and ocean depth data.
 *
 */
public class DemoCOG {

    /**
     * The mean radius of the Earth based on WGS84.
     */
    private static final double EARTH_MEAN_RADIUS = 6371008.8;

    private static final String[] USAGE = {
        "Usage for ExtractElevationsFromCog",
        "<-in input file>     Mandatory input elevation file ",
        "[-ocean]             Optional boolean indicates that elevations of ",
        "                     zero or less should be assigned ocean color",
        "[-reduction factor]  Optional integer reduction factor for reduced size",
        "                     image (default 12)",
        "[-scaleBar]          Optional boolean controls whether scale-bar is",
        "                     attached to the reduced-size image (default true)",
        "  ",};

    private static void printUsageAndExit() {
        for (String s : USAGE) {
            System.err.println(s);
        }
        System.exit(0);
    }

    /**
     * Reads the content of a TIFF file containing floating-point data and
     * stores
     * the results to a GVRS file.
     *
     * @param args the command line arguments giving the path to an input TIFF
     * file and other options for processing.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsageAndExit();
        }

        DemoCOG extractor = new DemoCOG();

        try {
            extractor.process(args, System.out);
        } catch (IOException | IllegalArgumentException | ImageReadException ex) {
            System.err.println("Error processing " + args[0] + " file " + args[1]);
            System.err.println(ex.getMessage());
        }
    }

    /**
     * Process the specified elevation GeoTIFF/
     *
     * @param args a set of command-line options for processing
     * @param ps a valid instance for printing status and metadata elements.
     * @throws IOException in the event of an unrecoverable I/O error
     * @throws ImageReadException in the event of an internal format error in
     * reading a GeoTIFF image.
     */
    void process(String[] args, PrintStream ps)
      throws IOException, ImageReadException {

        TestOptions options = new TestOptions();
        options.argumentScan(args);

        File target = options.getInputFile();
        File outputFile;

        String name = target.getName();
        int iExt = name.lastIndexOf('.');
        String rootName = name.substring(0, iExt);
        String outputName = rootName + "_FullRez.jpg";

        File parent = target.getParentFile();
        outputFile = new File(parent, outputName);

        ps.format("Input:  %s%n", target.getPath());
        ps.format("Output: %s%n", outputFile.getPath());

        boolean[] matched = new boolean[args.length];
        boolean ocean = options.scanBooleanOption(args, "-ocean", matched, false);
        int reductionFactor = options.scanIntOption(args, "-reduction", matched, 12);
        boolean scaleBar = options.scanBooleanOption(args, "-scaleBar", matched, true);

        int waterARGB = 0xff85b0cd;
        Color waterColor = new Color(waterARGB);

        ByteSourceFile byteSource = new ByteSourceFile(target);

        // Establish a TiffReader. This is just a simple constructor that
        // does not actually access the file.  So the application cannot
        // obtain the byteOrder, or other details, until the contents has
        // been read.  Then read the directories associated with the
        // file by passing in the byte source and options.
        TiffReader tiffReader = new TiffReader(true);

        // Read the directories in the TIFF file.  Directories are the
        // main data element of a TIFF file. They usually include an image
        // element, but sometimes just carry metadata. This example
        // reads all the directories in the file.   Typically reading
        // the directories is not a time-consuming operation.
        TiffContents contents = tiffReader.readDirectories(
          byteSource,
          true, // indicates that application should read image data, if present
          FormatCompliance.getDefault());

        // Read the first Image File Directory (IFD) in the file.  A practical
        // implementation could use any of the directories in the file.
        // This demo uses the first one just for simplicity.
        TiffDirectory directory = contents.directories.get(0);
        // Check that the first directory in the file has raster data.
        // For this demonstration, it is assumed that input data will
        // always be Cloud Optimized GeoTIFF files and will always have
        // raster data.
        if (!directory.hasTiffFloatingPointRasterData()) {
            ps.println(
              "Specified directory does not contain floating-point data");
            System.exit(-1);
        }

        // Extract and print the mandatory GeoKeyDirectoryTag.  If this tag is
        // not present, the image is not a GeoTIFF.  The "true" flag on
        // the access routine indicates that the tag is mandatory and
        // will cause the API to throw a ImageReadException if it is not there.
        ps.println("");
        ps.println("Extracting the GeoKeyDiretoryTag");
        short[] geoKeyDirectory = directory.getFieldValue(
          GeoTiffTagConstants.EXIF_TAG_GEO_KEY_DIRECTORY_TAG, true);

        // all GeoKey elements are unsigned shorts (2 bytes), some of which exceed
        // the value 32767 (the maximum value of a signed short).
        // since Java does not support an unsigned short type, we need to
        // mask in the low-order 2 bytes and obtain a 4-byte integer equivalent.
        int[] geoKey = new int[geoKeyDirectory.length];
        for (int i = 0; i < geoKeyDirectory.length; i++) {
            geoKey[i] = geoKeyDirectory[i] & 0xffff;
        }
        // print the table for reference.
        ps.println("     key     ref     len   value/pos");
        int k = 0;
        int n = geoKeyDirectory.length / 4;
        for (int i = 0; i < n; i++) {
            int ref = geoKey[k + 1];
            String label = "";
            if (ref == GeoTiffTagConstants.EXIF_TAG_GEO_ASCII_PARAMS_TAG.tag) {
                label = "ASCII";
            } else if (ref == GeoTiffTagConstants.EXIF_TAG_GEO_DOUBLE_PARAMS_TAG.tag) {
                label = "Double";
            }
            for (int j = 0; j < 4; j++) {
                ps.format("%8d", geoKey[k++]);
            }
            if (label.isEmpty()) {
                ps.format("%n");
            } else {
                ps.format("   %s%n", label);
            }
        }

        // find the raster geometry type specification
        // which is indicated by a GeoKey of 1025
        // and the GeogAngularUnitsKey, which is 2054
        String rasterInterpretation = "Unspecified";
        String unitOfMeasure = "Unspecified";
        for (int i = 0; i < n; i += 4) {
            if (geoKey[i] == 1025) {
                int rasterInterpretationCode = geoKey[i + 3];
                if (rasterInterpretationCode == 1) {
                    rasterInterpretation = "PixelIsArea";
                } else {
                    rasterInterpretation = "PixelIsPoint";
                }
            } else if (geoKey[i] == 2054) {
                int unitOfMeasureCode = geoKey[i + 3];
                if (unitOfMeasureCode == 9102) {
                    unitOfMeasure = "Angular Degrees";
                } else {
                    unitOfMeasure = "Unsupported code " + unitOfMeasureCode;
                }
            }
        }

        ps.println("Elements from GeoKeyDirectoryTag");
        ps.println("  Raster Interpretation(1025): " + rasterInterpretation);
        ps.println("  Unit of Measure(2054):       " + unitOfMeasure);

        TiffField modelTiepoint = directory.findField(
          GeoTiffTagConstants.EXIF_TAG_MODEL_TIEPOINT_TAG);
        double[] tiePoints = modelTiepoint.getDoubleArrayValue();
        ps.println("");
        ps.println("ModelTiepointTag");
        for (int i = 0; i < tiePoints.length; i++) {
            ps.format("  %2d.  %19.9f%n", i, tiePoints[i]);
        }

        // Note:  The y coordinate of the model pixel scale is backwards.
        //        Cloud Optimized GeoTIFF images are stored
        //        from upper-left corner downward (following the convention
        //        of virtually all graphics standards), so the rows in the
        //        raster run from north to south.  Thus, one might expect that
        //        the delta-Y between rows is a negative number.  But by the
        //        GeoTIFF standard, the verical spacing is given as a postive
        //        number.
        TiffField pixelScale = directory.findField(
          GeoTiffTagConstants.EXIF_TAG_MODEL_PIXEL_SCALE_TAG);
        double[] pixScale = pixelScale.getDoubleArrayValue();
        ps.println("ModelPixelScaleTag");
        for (int i = 0; i < pixScale.length; i++) {
            ps.format("  %2d.  %15.10e%n", i, pixScale[i]);
        }

        TiffField imageWidth = directory.findField(
          TiffTagConstants.TIFF_TAG_IMAGE_WIDTH);
        TiffField imageLength = directory.findField(
          TiffTagConstants.TIFF_TAG_IMAGE_LENGTH);
        int w = imageWidth.getIntValue();
        int h = imageLength.getIntValue();  // by "length", they mean height
        AffineTransform[] a = extractTransforms(directory);
        AffineTransform pix2Geo = a[0];
        AffineTransform geo2Pix = a[1];

        double[] c = new double[12];
        c[0] = 6;  // adjust corner coordinates for 6 row/column overlap
        c[1] = 6;
        c[2] = w - 6;
        c[3] = h - 6;
        pix2Geo.transform(c, 0, c, 4, 2);
        double lat0 = c[5];  // Northwest corner
        double lon0 = c[4];
        double lat1 = c[7];
        double lon1 = c[6];

        ps.println("Geographic bounds (excluding overlap)");
        ps.format("   Northwest:  %12.6f %12.6f%n", lat0, lon0);
        ps.format("   Southeast:  %12.6f %12.6f%n", lat1, lon1);

        // now get the true bounds
        c[0] = 0;  // adjust corner coordinates for 6 row/column overlap
        c[1] = 0;
        c[2] = w;
        c[3] = h;
        pix2Geo.transform(c, 0, c, 4, 2);
        lat0 = c[5];  // Northwest corner
        lon0 = c[4];
        lat1 = c[7];
        lon1 = c[6];
        Rectangle2D geoBounds
          = new Rectangle2D.Double(lon0, lat1, lon1 - lon0, lat0 - lat1);

        // The Natural Earth Land Cover data set is divided into two files
        // one for the west half of the conterminous US and one for the east.
        // They two files overlap slightly and the division is at 96 W.
        double midLon = (lon0 + lon1) / 2.0;
        String lcFileName;
        if (midLon < -96) {
            lcFileName = "W_CONUS_100m_NE_LC.tif";
        } else {
            lcFileName = "E_CONUS_100m_NE_LC.tif";
        }
        LandCoverTints landCoverTints = new LandCoverTints(
          new File(".\\NaturalEarth\\CONUS_100m_NE_LC\\" + lcFileName),
          lon0, lon1, lat0, lat1
        );

        landCoverTints.extendNearShoreColors(100);
        ps.println("Reading data from file");
        long time0 = System.nanoTime();
        TiffImagingParameters  params = new TiffImagingParameters();
        TiffRasterData rasterData = directory.getRasterData(params);
        long time1 = System.nanoTime();
        ps.println("Data read in " + ((time1 - time0) / 1.0e+6) + " ms");

        // Compute the scale in meters using the Earth's mean radius.
        // The scales give the Earth-surface distances between rows and
        // columns of raster cells.  They will be used in the interpolation
        // logic below to get an accurate estimate of the slope (and
        // thus, the surface normal) at each pixel location in the output
        // image.
        //     The xScale is adjusted for the convergence of the meridians
        // at the center latitude of the raster data set.
        double cenLat = (lat1 + lat0) / 2.0;
        double cosCenLat = Math.cos(Math.toRadians(cenLat));
        double yScale
          = pixScale[1] * (Math.PI / 180) * EARTH_MEAN_RADIUS;
        double xScale
          = pixScale[0] * (Math.PI / 180) * EARTH_MEAN_RADIUS * cosCenLat;

        // Compute the adjusted width for the output image
        int adjustedWidth = (int) (w * cosCenLat + 0.5);
        int adjustedHeight = h;

        // compute a transform that will convert geographic coordinates
        // to the pixel coordinates in the adjusted (narrow) image size
        // This transform will be used for rendering the optional shapefiles.
        AffineTransform geoToAdjusted;
        geoToAdjusted = AffineTransform.getScaleInstance(cosCenLat, 1);
        geoToAdjusted.concatenate(geo2Pix);

        // Specify the parameters for the illumination source (the "sun")
        double ambient = 0.2;
        double steepen = 5.0;
        double sunAzimuth = Math.toRadians(145);
        double sunElevation = Math.toRadians(60);

        // create a unit vector pointing at illumination source
        double cosA = Math.cos(sunAzimuth);
        double sinA = Math.sin(sunAzimuth);
        double cosE = Math.cos(sunElevation);
        double sinE = Math.sin(sunElevation);
        double xSun = cosA * cosE;
        double ySun = sinA * cosE;
        double zSun = sinE;

        // GeoTIFF goes from north to south, so I had to negate the
        // usual -result.zy component in order to get the b-spline
        // harmonized with the pixel coordinates.
        InterpolatorBSpline bSpline = new InterpolatorBSpline();
        float[] f = rasterData.getData();
        int[] argb = new int[adjustedHeight * adjustedWidth];
        InterpolationResult result = new InterpolationResult();
        int index = 0;

        // loop on the rows and columns of the adjusted-size image.
        // for each row and column, compute xCol, yCol which are the
        // corresponding coordinates in the original elevation raster.
        // To obtain an elevation (z) value for the adjusted-size image,
        // we need to perform an interpolation into the elevations from the
        // source data.   As a bonus, the interpolation routine also
        // provides derivatives (slopes) in the direction of the x and y
        // axis.
		ps.println("Begin rendering");
		time0 = System.nanoTime();
        for (int iRow = 0; iRow < adjustedHeight; iRow++) {
            double yRow = iRow + 0.5;
            for (int iCol = 0; iCol < adjustedWidth; iCol++) {
                double xCol = iCol / cosCenLat + 0.5;
                bSpline.interpolate(yRow, xCol, h, w, f,
                  yScale, xScale,
                  InterpolationTarget.FirstDerivatives,
                  result);
                // double z = result.z;  not used, included for documentation
                double nx = -result.zx * steepen;
                double ny = result.zy * steepen;
                double s = Math.sqrt(nx * nx + ny * ny + 1);
                nx /= s;
                ny /= s;
                double nz = 1 / s;
                double dot = nx * xSun + ny * ySun + nz * zSun;
                double shade = ambient;
                if (dot > 0) {
                    shade = dot * (1 - ambient) + ambient;
                }

                if (ocean && result.z <= 0) {
                    argb[index] = waterARGB;
                } else {
                    // Perform a lookup into the land-cover tints image.
                    // Convert the source image coordinates to geographic
                    // coordinates and call the associated land-cover tints.
                    c[0] = xCol;
                    c[1] = yRow;
                    pix2Geo.transform(c, 0, c, 2, 1);
                    argb[index]
                      = landCoverTints.getPixelValueUsingCieLab(c[2], c[3], shade);

                    // the following was used for monochrome images
                    //argb[index] = getRgb(shade);
                }

                index++;
            }

        }
		time1 = System.nanoTime();
        ps.println("Shaded relief rendering completed in " + ((time1 - time0) / 1.0e+6) + " ms");

        BufferedImage bImage = new BufferedImage(
          adjustedWidth,
          adjustedHeight,
          BufferedImage.TYPE_INT_RGB);
        bImage.setRGB(0, 0, adjustedWidth, adjustedHeight, argb, 0, adjustedWidth);
        Graphics2D graphics = bImage.createGraphics();
        graphics.setRenderingHint(
          RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);

        BasicStroke thinStroke = new BasicStroke(1.0f);
        BasicStroke thickStroke = new BasicStroke(4.0f);
        BasicStroke veryThickStroke = new BasicStroke(18.0f);

        File shapefile
          = new File(".\\NationalAtlas\\water_bodies\\wtrbdyp010.shp");
        drawShape(ps, shapefile, graphics, waterColor, thinStroke, geoBounds, geoToAdjusted, true);

        shapefile
          = new File(".\\NationalAtlas\\streams\\streaml010.shp");
        drawShape(ps, shapefile, graphics, waterColor, thickStroke, geoBounds, geoToAdjusted, true);

        shapefile = new File(".\\NaturalEarth\\ne_10m_admin_1_states_provinces_lines.shp");
        drawShape(ps, shapefile, graphics, Color.black, veryThickStroke, geoBounds, geoToAdjusted, false);

        // Get the updated argb (pixel) values based on whatever changes
        // may have been introduced by the shapefile rendering.
        bImage.getRGB(0, 0, adjustedWidth, adjustedHeight, argb, 0, adjustedWidth);

        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(Color.darkGray);
        graphics.drawRect(0, 0, adjustedWidth - 2, adjustedHeight - 2);

        ps.println("Writing image to " + outputFile);
        ImageIO.write(bImage, "JPEG", outputFile);

        double mPerPix = reductionFactor * yScale;
        BufferedImage sImage = makeReducedImage(adjustedWidth, adjustedHeight, argb, reductionFactor);

        if (scaleBar) {
            BufferedImage rImage = new BufferedImage(sImage.getWidth(), sImage.getHeight() + 80, BufferedImage.TYPE_INT_RGB);
            graphics = rImage.createGraphics();
            graphics.setRenderingHint(
              RenderingHints.KEY_ANTIALIASING,
              RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.white);
            graphics.fillRect(0, 0, rImage.getWidth() + 1, rImage.getHeight() + 1);
            graphics.drawImage(sImage, 0, 0, null);

            int yOffset = rImage.getHeight() - 55;
            drawScale(graphics, yOffset, rImage.getWidth(), 420, mPerPix);
            labelSource(graphics, yOffset, name);
            graphics.setStroke(new BasicStroke(2.0f));
            graphics.setColor(Color.darkGray);
            graphics.drawRect(0, 0, sImage.getWidth() - 2, sImage.getHeight() - 2);
            graphics.drawRect(0, 0, rImage.getWidth() - 2, rImage.getHeight() - 2);
            sImage = rImage;
        } else {
            graphics = sImage.createGraphics();
            graphics.setRenderingHint(
              RenderingHints.KEY_ANTIALIASING,
              RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(2.0f));
            graphics.setColor(Color.darkGray);
            graphics.drawRect(0, 0, sImage.getWidth() - 2, sImage.getHeight() - 2);
        }

        outputName = rootName + "_Reduced.jpg";
        parent = target.getParentFile();
        outputFile = new File(parent, outputName);
        ps.println("Writing image to " + outputFile);
        ImageIO.write(sImage, "JPEG", outputFile);
    }
    private final static int BAR_X_MARGIN = 20;
    private final static int BAR_HEIGHT = 10;
    private final static int BAR_LAB_SZ = 14;
    private final static Font BAR_FONT = new Font("Arial", Font.BOLD, BAR_LAB_SZ);

    void drawScale(Graphics2D graphics, int y0, int width, int length, double mPerPix) {
        // For this specific product, we know a priori that 10 kilometers or
        // 10 statute miles is a good interval for a scale bar.
        // Compute the length of a reasonable sized scale bar so that
        // we can fit it into the output image.
        double kmPerPix = mPerPix / 1000.0;  // kilometers
        double smPerPix = mPerPix / 1609.34;  // statute miles
        double kmLen = 10 * Math.floor(length * kmPerPix / 10.0) / kmPerPix;
        double smLen = 10 * Math.floor(length * smPerPix / 10.0) / smPerPix;
        double mxLen = Math.max(kmLen, smLen);
        double xOffset = (int) (width - mxLen - BAR_X_MARGIN);

        drawBar(graphics, y0, xOffset, length, kmPerPix, "Kilometers", true);
        drawBar(graphics, y0 + 2 * BAR_HEIGHT, xOffset, length, smPerPix, "Miles", false);

    }

    void labelSource(Graphics2D graphics, int y0, String name) {
        graphics.setColor(Color.black);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setFont(BAR_FONT);
        graphics.drawString(name, BAR_X_MARGIN, y0 - 2);
    }

    void drawBar(Graphics2D graphics, double y0, double x0, int length, double uPerPix, String uomLabel, boolean labelHigh) {
        int nTens = (int) ((length * uPerPix) / 10);
        double[] tics = new double[nTens + 2];
        int k = 0;
        tics[k++] = 0;
        tics[k++] = 5;
        for (int i = 1; i <= nTens; i++) {
            tics[k++] = i * 10;
        }
        String[] labels = new String[tics.length];
        for (int i = 0; i < tics.length; i++) {
            labels[i] = String.format("%d", (int) (tics[i]));
        }

        double w = tics[tics.length - 1] / uPerPix;
        double y = y0; // labelHigh ? y0 - BAR_HEIGHT : y0;
        double x;
        graphics.setColor(Color.black);
        graphics.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < tics.length - 1; i++) {
            if ((i & 1) == 0) {
                graphics.setColor(Color.white);
            } else {
                graphics.setColor(Color.black);
            }
            x = x0 + tics[i] / uPerPix;
            double dx = (tics[i + 1] - tics[i]) / uPerPix;
            Rectangle2D r2d = new Rectangle2D.Double(x, y, dx, BAR_HEIGHT);
            graphics.fill(r2d);
        }
        graphics.setColor(Color.darkGray);
        Rectangle2D r2d = new Rectangle2D.Double(x0, y0, w, BAR_HEIGHT);
        graphics.draw(r2d);
        FontRenderContext frc = new FontRenderContext(null, true, true);

        for (int i = 0; i < tics.length; i++) {
            x = x0 + tics[i] / uPerPix;
            TextLayout labelLayout = new TextLayout(labels[i], BAR_FONT, frc);
            Rectangle2D bounds = labelLayout.getBounds();
            x -= bounds.getCenterX();
            if (labelHigh) {
                y = y0 - 2;
            } else {
                y = y0 + BAR_HEIGHT + 2 + labelLayout.getAscent();
            }
            labelLayout.draw(graphics, (float) x, (float) y);
        }

        TextLayout uomLayout = new TextLayout(uomLabel, BAR_FONT, frc);
        Rectangle2D bounds = uomLayout.getBounds();
        x = x0 - BAR_HEIGHT - bounds.getMaxX();
        y = y0 + BAR_HEIGHT / 2 + bounds.getHeight() / 2;
        uomLayout.draw(graphics, (float) x, (float) y);

    }

    // a simple shading computation for monochrome images
    //int getRgb(float shading) {
    //    int r = (int) (shading * 255 + 0.5f);
    //    int g = (int) (shading * 255 + 0.5f);
    //    int b = (int) (shading * 255 + 0.5f);
    //    return ((((0xff00 | r) << 8) | g) << 8) | b;
    //}
    AffineTransform[] extractTransforms(TiffDirectory directory) throws ImageReadException {
        TiffField modelTiepoint = directory.findField(
          GeoTiffTagConstants.EXIF_TAG_MODEL_TIEPOINT_TAG);
        double[] tiePoints = modelTiepoint.getDoubleArrayValue();

        // Note:  The y coordinate of the model pixel scale is backwards.
        //        Cloud Optimized GeoTIFF images are stored
        //        from upper-left corner downward (following the convention
        //        of virtually all graphics standards), so the rows in the
        //        raster run from north to south.  Thus, one might expect that
        //        the delta-Y between rows is a negative number.  But by the
        //        GeoTIFF standard, the verical spacing is given as a postive
        //        number.
        TiffField pixelScale = directory.findField(
          GeoTiffTagConstants.EXIF_TAG_MODEL_PIXEL_SCALE_TAG);
        double[] pixScale = pixelScale.getDoubleArrayValue();

        double i0 = tiePoints[0]; // raster tie point coordinate (column)
        double j0 = tiePoints[1]; // raster tie point coordinate (row)
        double x0 = tiePoints[3]; // geographic tie point coordinate (lon)
        double y0 = tiePoints[4]; // geographic tie point coordinate (lat)
        double sX = pixScale[0];  // degrees per column
        double sY = pixScale[1];  // degrees per row (negated)

        AffineTransform raster2Geo = new AffineTransform(
          sX, 0, 0, -sY,
          x0 - i0 * sX,
          y0 + j0 * sY);

        AffineTransform geo2Raster = new AffineTransform(
          1 / sX, 0, 0, -1 / sY,
          i0 - x0 / sX,
          j0 + y0 / sY);

        AffineTransform[] a = {raster2Geo, geo2Raster};
        return a;
    }

    void drawShape(PrintStream ps, File shapefile,
      Graphics2D graphics,
      Color color,
      BasicStroke stroke,
      Rectangle2D geoBounds,
      AffineTransform geoToPixel,
      boolean fillEnabled) {

        if (!shapefile.exists()) {
            ps.println("Shapefile not found: " + shapefile.getPath());
            return;
        }

        ps.println("Processing shapefile " + shapefile.getName());
        graphics.setColor(color);
        graphics.setStroke(stroke);
        long time0 = System.currentTimeMillis();
        try (ShapefileReader reader = new ShapefileReader(shapefile)) {
            ShapefileType shapeType = reader.getShapefileType();
            boolean fillFlag = shapeType.isPolygon() && fillEnabled;
            double[] c = new double[4];
            ShapefileRecord record = null;
            while ((record = reader.readNextRecord(record)) != null) {
                Rectangle2D recordBounds = new Rectangle2D.Double(
                  record.x0, record.y0,
                  record.x1 - record.x0, record.y1 - record.y0);
                if (!geoBounds.intersects(recordBounds)) {
                    continue;
                }

                for (int iPart = 0; iPart < record.nParts; iPart++) {
                    Path2D path = new Path2D.Double();
                    int i0 = record.partStart[iPart];
                    int i1 = record.partStart[iPart + 1];
                    boolean moveFlag = true;
                    for (int i = i0; i < i1; i++) {
                        c[0] = record.xyz[i * 3];
                        c[1] = record.xyz[i * 3 + 1];
                        geoToPixel.transform(c, 0, c, 2, 1);
                        if (moveFlag) {
                            path.moveTo(c[2], c[3]);
                            moveFlag = false;
                        } else {
                            path.lineTo(c[2], c[3]);
                        }
                    }
                    graphics.draw(path);
                    if (fillFlag) {
                        graphics.fill(path);
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            ps.println("exception reading shapefile " + ex.getMessage());
        } catch (EOFException eofex) {
            // no action required. the data in the shapefile is fully consumed.
        } catch (IOException ioex) {
            ps.println("IOException reading shapefile " + ioex.getMessage());
        }
        long time1 = System.currentTimeMillis();
        ps.println("Processed " + shapefile.getName() + " in " + (time1 - time0) + " ms");
    }

    /**
     * Makes an image containing reduced size version of the input
     * specification. Source pixels are combined by taking the average
     * red, green, and blue values of a square block of pixels
     * of the specified size factor. If the factor does not evenly divide
     * the source image, the partial rows or columns are ignored.
     *
     * @param iWidth width of the source data
     * @param iHeight height of the source data
     * @param input the source pixels for the image in RGB format.
     * @param factor size of block for reduction
     * @return a valid image.
     */
    BufferedImage makeReducedImage(int iWidth, int iHeight, int[] input, int factor) {

        int nRow = iHeight / factor;
        int nCol = iWidth / factor;
        int nDown = factor * factor;
        int[] argb = new int[nRow * nCol];
        for (int iRow = 0; iRow < nRow; iRow++) {
            int i0 = iRow * factor;
            int i1 = i0 + factor;
            for (int jCol = 0; jCol < nCol; jCol++) {
                int j0 = jCol * factor;
                int j1 = j0 + factor;
                int rSum = 0;
                int gSum = 0;
                int bSum = 0;
                for (int i = i0; i < i1; i++) {
                    for (int j = j0; j < j1; j++) {
                        int rgb = input[i * iWidth + j];
                        rSum += (rgb >> 16) & 0xff;
                        gSum += (rgb >> 8) & 0xff;
                        bSum += rgb & 0xff;
                    }
                }

                rSum = (rSum + nDown / 2) / nDown;
                gSum = (gSum + nDown / 2) / nDown;
                bSum = (bSum + nDown / 2) / nDown;
                argb[iRow * nCol + jCol]
                  = 0xff000000 | (rSum << 16) | (gSum << 8) | bSum;
            }
        }
        BufferedImage sImage
          = new BufferedImage(nCol, nRow, BufferedImage.TYPE_INT_RGB);
        sImage.setRGB(0, 0, nCol, nRow, argb, 0, nCol);
        return sImage;
    }
}
