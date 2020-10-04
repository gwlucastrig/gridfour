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
 * 06/2020  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.demo.geoTiff;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.apache.commons.imaging.FormatCompliance;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.color.ColorCieLab;
import org.apache.commons.imaging.color.ColorConversions;
import org.apache.commons.imaging.color.ColorXyz;
import org.apache.commons.imaging.common.bytesource.ByteSourceFile;
import org.apache.commons.imaging.formats.tiff.TiffContents;
import org.apache.commons.imaging.formats.tiff.TiffDirectory;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffReader;
import org.apache.commons.imaging.formats.tiff.constants.GeoTiffTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffConstants;

/**
 * Accesses the Natural Earth Land Cover file and obtains interpolated
 * color values based on the CIELAB color space.
 * <p>
 * The CIELAB color space is better suited to interpolating color
 * values than the RGB color space because it more closely matches
 * the sensitivity and color response of the human eye than the RGB
 * system. Because the colors used in the Natural Earth Land Cover product
 * were already chosen to blend well together, the improvement is minor.
 * In general, the CIELAB result tends to have somewhat better contrast
 * and color saturation than the RGB results. The improvement is small,
 * but noticeable.
 */
public class LandCoverTints {

    AlbersEqualAreaConic albers;
    AffineTransform projToRaster;

    int x0;
    int y0;
    int x1;
    int y1;
    int w;
    int h;
    int[] xrgb;
    ColorCieLab[] xlab;

    boolean hasOceanPixels;

    /**
     * Construct a color tint processor initialized to cover the specified
     * area. An internal data set including both RGB and CIELAB instances is
     * created and used for interpolation purposes.
     *
     * @param file The input Natural Earth Land Cover file
     * @param xlon0 Minimum longitude for the area of interest
     * @param xlon1 Maximum longitude for the area of interest
     * @param ylat0 Minimum latitude for the area of interest
     * @param ylat1 Maximum latitude for the area of interest
     * @throws ImageReadException in the event of an unrecognized element in the
     * source image
     * @throws IOException in the event of an unrecoverable I/O error
     */
    LandCoverTints(File file, double xlon0, double xlon1, double ylat0, double ylat1)
        throws ImageReadException, IOException {

        TiffReader tiffReader = new TiffReader(true);
        ByteSourceFile byteSource = new ByteSourceFile(file);
        FormatCompliance formatCompliance = FormatCompliance.getDefault();
        // Read the directories in the TIFF file.  Directories are the
        // main data element of a TIFF file. They usually include an image
        // element, but sometimes just carry metadata. This example
        // reads all the directories in the file.   Typically reading
        // the directories is not a time-consuming operation.
        HashMap<String, Object> params = new HashMap<>();
        TiffContents contents = tiffReader.readFirstDirectory(
            byteSource, params, true, formatCompliance);

        // Read the first Image File Directory (IFD) in the file.  A practical
        // implementation could use any of the directories in the file.
        // This demo uses the first one just for simplicity.
        TiffDirectory directory = contents.directories.get(0);

        AffineTransform[] a = extractTransforms(directory);
        projToRaster = a[1];

        albers = new AlbersEqualAreaConic(23.0, -96, 29.5, 45.5);

        double[] geo = new double[8];
        double[] prj = new double[8];
        double[] xy = new double[8];
        geo[0] = ylat0;
        geo[1] = xlon0;
        geo[2] = ylat0;
        geo[3] = xlon1;
        geo[4] = ylat1;
        geo[5] = xlon1;
        geo[6] = ylat1;
        geo[7] = xlon0;
        albers.forward(geo, 0, prj, 0, 4);
        projToRaster.transform(prj, 0, xy, 0, 4);

        double xmin = Double.POSITIVE_INFINITY;
        double ymin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            xmin = Math.min(xmin, xy[i * 2]);
            xmax = Math.max(xmax, xy[i * 2]);
            ymin = Math.min(ymin, xy[i * 2 + 1]);
            ymax = Math.max(ymax, xy[i * 2 + 1]);
        }

        x0 = (int) Math.floor(xmin) - 1;
        y0 = (int) Math.floor(ymin) - 1;
        x1 = (int) Math.ceil(xmax) + 1;
        y1 = (int) Math.ceil(ymax) + 1;
        w = x1 - x0 + 1;
        h = y1 - y0 + 1;

        params.put(TiffConstants.PARAM_KEY_SUBIMAGE_X, x0);
        params.put(TiffConstants.PARAM_KEY_SUBIMAGE_Y, y0);
        params.put(TiffConstants.PARAM_KEY_SUBIMAGE_WIDTH, w);
        params.put(TiffConstants.PARAM_KEY_SUBIMAGE_HEIGHT, h);
        BufferedImage image = Imaging.getBufferedImage(file, params);
        // ImageIO.write(image, "PNG", new File("conus_tints.png"));
        xrgb = new int[w * h];
        image.getRGB(0, 0, w, h, xrgb, 0, w);
        xlab = new ColorCieLab[xrgb.length];
        for (int i = 0; i < xrgb.length; i++) {
            ColorXyz xyz = ColorConversions.convertRGBtoXYZ(xrgb[i]);
            xlab[i] = ColorConversions.convertXYZtoCIELab(xyz);
            if ((xrgb[i] & 0x00ffffff) == 0x00ffffff) {
                this.hasOceanPixels = true;
            }
        }
    }

    private AffineTransform[] extractTransforms(TiffDirectory directory) throws ImageReadException {
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

    /**
     * Performs the preferred method for getting an RGB value
     * for the specified geographic coordinates from
     * the source image, applying the shading factor (in the range 0 to 1).
     * The latitude and longitude much be within the bounds specified
     * to the constructor.
     * <p>
     * Because of the different pixel resolution of the Natural Earth
     * Land Cover, the act of obtaining a color value for an output pixel
     * requires an interpolation. In general, the CIELAB color space
     * is better suited to interpolating color values than the conventional
     * RGB color space because it more closely matches
     * the sensitivity and color response of the human eye than the RGB
     * system.
     *
     * @param lon the longitude of interest
     * @param lat the latitude of interest
     * @param shading the shading factor in the range 0 to 1
     * @return an interpolated RGB value.
     */
    int getPixelValueUsingCieLab(double lon, double lat, double shading) {
        double[] c = new double[6];
        c[0] = lat;
        c[1] = lon;
        albers.forward(c, 0, c, 2, 1);
        projToRaster.transform(c, 2, c, 4, 1);
        double x = c[4];
        double y = c[5];
        if (x < x0 || y < y0 || x >= x1 || y >= y1) {
            return 0;
        }
        x -= x0;
        y -= y0;
        int ix = (int) x;
        int iy = (int) y;
        int index = iy * w + ix;

        ColorCieLab p00 = xlab[index]; // p(x,y)
        ColorCieLab p01 = xlab[index + w]; // p(x, y+1)
        ColorCieLab p10 = xlab[index + 1]; // p(x+1, y)
        ColorCieLab p11 = xlab[index + w + 1]; // p(x+1, y+1)
        double t = (y - iy);
        double s = (x - ix);


        double L0 = s * (p10.L - p00.L) + p00.L;
        double a0 = s * (p10.a - p00.a) + p00.a;
        double b0 = s * (p10.b - p00.b) + p00.b;

        double L1 = s * (p11.L - p01.L) + p01.L;
        double a1 = s * (p11.a - p01.a) + p01.a;
        double b1 = s * (p11.b - p01.b) + p01.b;

        double L = (t * (L1 - L0) + L0) * shading;
        double a = t * (a1 - a0) + a0;
        double b = t * (b1 - b0) + b0;

        ColorXyz xyz = ColorConversions.convertCIELabtoXYZ(L, a, b);
        return ColorConversions.convertXYZtoRGB(xyz.X, xyz.Y, xyz.Z);

    }

    /**
     * Get an RGB value for the specified geographic coordinates from
     * the source image, applying the shading factor (in the range 0 to 1).
     * The latitude and longitude much be within the bounds specified
     * to the constructor.
     * <p>
     * This method interpolates pixel values using the RGB color space.
     * Although it produces acceptable results, the alternate CIELAB
     * color space tends to produce better results (at a somewhat higher
     * computational cost). This method is provided as a comparison.
     *
     * @param lon the longitude of interest
     * @param lat the latitude of interest
     * @param shading the shading factor in the range 0 to 1
     * @return an interpolated RGB value.
     */
    int getPixelValueUsingRgb(double lon, double lat, double shading) {
        double[] c = new double[6];
        c[0] = lat;
        c[1] = lon;
        albers.forward(c, 0, c, 2, 1);
        projToRaster.transform(c, 2, c, 4, 1);
        double x = c[4];
        double y = c[5];
        if (x < x0 || y < y0 || x >= x1 || y >= y1) {
            return 0;
        }
        x -= x0;
        y -= y0;
        int ix = (int) x;
        int iy = (int) y;
        int index = iy * w + ix;

        int p00 = xrgb[index]; // p(x,y)
        int p01 = xrgb[index + w]; // p(x, y+1)
        int p10 = xrgb[index + 1]; // p(x+1, y)
        int p11 = xrgb[index + w + 1]; // p(x+1, y+1)
        double t = (y - iy);
        double s = (x - ix);
        int r00 = (p00 >> 16) & 0xff;
        int g00 = (p00 >> 8) & 0xff;
        int b00 = p00 & 0xff;

        int r10 = (p10 >> 16) & 0xff;
        int g10 = (p10 >> 8) & 0xff;
        int b10 = p10 & 0xff;

        int r01 = (p01 >> 16) & 0xff;
        int g01 = (p01 >> 8) & 0xff;
        int b01 = p01 & 0xff;

        int r11 = (p11 >> 16) & 0xff;
        int g11 = (p11 >> 8) & 0xff;
        int b11 = p11 & 0xff;

        double mr0 = s * (r10 - r00) + r00;
        double mg0 = s * (g10 - g00) + g00;
        double mb0 = s * (b10 - b00) + b00;

        double mr1 = s * (r11 - r01) + r01;
        double mg1 = s * (g11 - g01) + g01;
        double mb1 = s * (b11 - b01) + b01;

        int r = (int) ((t * (mr1 - mr0) + mr0) * shading + 0.5);
        int g = (int) ((t * (mg1 - mg0) + mg0) * shading + 0.5);
        int b = (int) ((t * (mb1 - mb0) + mb0) * shading + 0.5);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Indicates that the specified raster subset contains ocean pixel values.
     * The Natural Earth Land Cover files do not provide color assignments
     * for ocean pixels. An ocean pixel value will be assigned a value of white.
     *
     * @return true if the specified subset includes ocean pixels;
     * otherwise false.
     */
    boolean hasOceanPixels() {
        return hasOceanPixels;
    }
    void extendNearShoreColors(int nPasses) {
        int[] blank = new int[xrgb.length];
        int[] fixdex = new int[xrgb.length];
        int[] fixrgb = new int[xrgb.length];
        ColorCieLab[] fixlab = new ColorCieLab[xrgb.length];
        int nBlank = 0;
        for (int i = 0; i < xrgb.length; i++) {
            if ((xrgb[i] & 0x00ffffff) == 0x00ffffff) {
                blank[nBlank++] = i;
            }
        }
        for (int iPass = 0; iPass < nPasses && nBlank > 0; iPass++) {
            int iBlank = 0;
            int nFix = 0;
            blankLoop:
            while (iBlank < nBlank) {
                int index = blank[iBlank];
                int row = index / w;
                int col = index - row * w;
                int i0 = row > 0 ? row - 1 : 0;
                int i1 = row < h - 1 ? row + 1 : h;
                int j0 = col > 0 ? col - 1 : 0;
                int j1 = col < w - 1 ? col + 1 : w;
                for (int i = i0; i < i1; i++) {
                    int offset = i0 * w;
                    for (int j = j0; j < j1; j++) {
                        if ((xrgb[offset + j] & 0x00ffffff) != 0x00ffffff) {
                            fixrgb[nFix] = xrgb[offset + j];
                            fixlab[nFix] = xlab[offset + j];
                            fixdex[nFix] = index;
                            blank[iBlank] = blank[nBlank - 1];
                            nBlank--;
                            nFix++;
                            continue blankLoop;
                        }
                    }
                }
                // if we get here, no replacement was found
                // continue on to the next candidate.
                iBlank++;
            }
            if (nFix == 0) {
                return;
            }
            for (int i = 0; i < nFix; i++) {
                xrgb[fixdex[i]] = fixrgb[i];
                xlab[fixdex[i]] = fixlab[i];
            }
        }
    }
}
