/* --------------------------------------------------------------------
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
 * 09/2019  G. Lucas     Created  
 *
 * Notes:
 *
 *   
 * -----------------------------------------------------------------------
 */

 

package org.gridfour.demo.globalDEM;

/**
 * Provides palette definitions to be used for the example applications
 * included in this package.
 */
 class ExamplePalettes {
  private ExamplePalettes(){
    // a private constructor to deter application code from constructing
    // instances of this class.
  }
  
  
  //  Colormap used in the ETOPO1 global relief map:
//  http://ngdc.noaa.gov/mgg/global/global.html
// 
//  Above sea level is a modified version of GMT_globe.cpt, 
//  Designed by Lester M. Anderson (CASP, UK) lester.anderson@casp.cam.ac.uk,
//  Modified by Jesse Varner and Elliot Lim (NOAA/NGDC) with a smaller band
//  of white for the highest elevations.
//  The ocean colors are adapted from GMT_haxby.cpt, popularized by Bill Haxby, LDEO
//     Palette is licensed under the Gnu General Public License (Volume 2).
//     Palette copyright (c) 2009 by Lester M. Anderson, Jesse Varner, and
//     Elliot Lim.
//
//  Downloaded from http://soliton.vm.bytemark.co.uk/pub/cpt-city/ngdc/
  
static float paletteETOPO1[][] = {
	{-11000,	10,	0,	121},
	{-10500,	26,	0,	137},
	{-10000,	38,	0,	152},
	{-9500,	27,	3,	166},
	{-9000,	16,	6,	180},
	{-8500,	5,	9,	193},
	{-8000,	0,	14,	203},
	{-7500,	0,	22,	210},
	{-7000,	0,	30,	216},
	{-6500,	0,	39,	223},
	{-6000,	12,	68,	231},
	{-5500,	26,	102,	240},
	{-5000,	19,	117,	244},
	{-4500,	14,	133,	249},
	{-4000,	21,	158,	252},
	{-3500,	30,	178,	255},
	{-3000,	43,	186,	255},
	{-2500,	55,	193,	255},
	{-2000,	65,	200,	255},
	{-1500,	79,	210,	255},
	{-1000,	94,	223,	255},
	{-500,	138,	227,	255},
        {0,     208,    227,    255},
	{0,     51,	102,	0},
	{100,	51,	204,	102},
	{200,	187,	228,	146},
	{500,	255,	220,	185},
	{1000,	243,	202,	137},
	{1500,	230,	184,	88},
	{2000,	217,	166,	39},
	{2500,	168,	154,	31},
	{3000,	164,	144,	25},
	{3500,	162,	134,	19},
	{4000,	159,	123,	13},
	{4500,	156,	113,	7},
	{5000,	153,	102,	0},
	{5500,	162,	89,	89},
	{6000,	178,	118,	118},
	{6500,	183,	147,	147},
	{7000,	194,	176,	176},
	{7500,	204,	204,	204},
	{8000,	229,	229,	229},
	};

  
   // The palette used for bathymetry is based on Esri, Inc.'s 
   // "Ocean Basemap color style" which was obtained from the following address  
   // https://www.esri.com/arcgis-blog/products/mapping/mapping/esri-ocean-basemap-color-style-available-for-download/
   // We are grateful to Esri for generously posting these colors in
   // a non-copyrighted document.
   // Colors are specified for indicated depths (column 0) starting
   // at a depth of 11000 meters.  Colors are given RGB values in 
   // colums 1 through for 3.
  static float paletteGEBCO[][] = {
    {-11000, 56, 91, 140},
    {-9500, 43, 102, 166},
    {-8500, 66, 124, 179},
    {-7000, 82, 143, 204},
    {-6000, 98, 159, 217},
    {-4500, 134, 179, 235},
    {-3000, 149, 188, 230},
    {-1000, 170, 207, 242},
    {-400, 181, 215, 247},
    {-150, 191, 224, 255},
    { -35, 209, 233, 255},
    {-0.001f, 192, 192, 192},
    {8000,    255, 255, 255},
  };



}
