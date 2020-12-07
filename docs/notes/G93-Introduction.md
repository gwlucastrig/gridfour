# Introduction
Gridfour's G93File classes help Java applications manage raster (grid) data in situations
where the size of the data exceeds what could reasonably be kept in memory.  To do so,
it provides an API that allows an application to seamlessly swap blocks of the grid
between memory and data files. The G93 library and file format also provide a way of
storing raster data in files that can be shared between applications or used
across multiple application runs. And, in cases where storage space or
transmission bandwidth is limited, G93 provides built-in data compression
that can reduce storage requirements by at least a factor of four.

This article gives an introduction to the Java API for G93 and offers
example code showing how to use it in your own applications.  As usual,
code for the applications described in this article is available from the
[The Gridfour Software Project](https://github.com/gwlucastrig/gridfour).
You may find the code in the Gridfour demo code tree in the class
[PackageData.java](https://github.com/gwlucastrig/gridfour/blob/master/demo/src/main/java/org/gridfour/demo/globalDEM/PackageData.java).

## Sample Data
A wiki article that describes how to use an API to manipulate and store data
would surely benefit from have a good source of data to use as an example.
We are fortunate to have two: ETOPO1 and GEBCO_2019. These products
give world-wide Earth surface elevations and ocean depth (bathymetry) values
in a regular geographic coordinate grid.

In the ETOPO1 product, grid points
are given in a regular spacing of 1 minute of arc (e.g. 60 rows or columns for
each degree of latitude or longitude). Thus ETOPO1 includes
(360x60)x(180x60) data values, or about 233 million samples. Storing that
many numeric values in memory requires a lot of capacity, but is not
out-of-reach for a modern computer. The GEBCO product, however, uses a finer
resolution based on a grid spacing of 15 seconds of arc (e.g. 240 rows or
columns for each degree of latitude or longitude).  That spacing results in
a data collection containing 3.7 billion samples.  And while there are plenty
of computers with sufficient memory to hold that much data, it wouldn't leave
much room for anything else.

## A Tiling Scheme
Fortunately, when dealing with such large data sets, applications seldom
need to access the entire collection all at once. So an obvious solution
to the problem of memory use is to store the data on a file and load
(or store) pieces of it on an as-needed basis. This approach is
used in data formats such as the TIFF image specification which
partition large grids into smaller, regularly sized sub-grids
known as tiles (Adobe, 1992, pg. 68).

The basic idea of tiling a grid can be seen in the image below. In this case,
a grid consisting of six rows and nine columns is divided into six 3-by-3 tiles.

![Grid Tiles](images/General/TilingScheme.png).

In G93, the size of tiles are arbitrary, though all tiles must be of
a uniform size. Applications are free to specify tile sizes according
to their needs. In this case,
a 2-by-9 tile would have worked just fine. In fact, a 4-by-5 tile size would
also work, even though the tiles would not evenly divide
the 6-by-9 master grid. The G93 API handles any extra cells
internally and their management is transparent to the application.

The figure below illustrates how a tiling scheme works.  A collection of surface
elevation and bathymetry data could be divided into regular
tiles ten-degrees across. An application requiring access to
information in Europe would load the relevant tiles without needing
to access information from South America.

![Tiling Scheme](images/PackingData/TileScheme.png)

In the case of the ETOPO1 data set, the PackageData
demonstration application specifies a grid of 90 rows by 120 columns.
In terms of geographic coordinates, the 1-minute resolution
used in ETOPO1 means that these tiles will cover an area with a span of 
1.5 degrees latitude and 2 degrees of longitude.
This size was chosen after some experimentation because it gives a
good data compression results and provides a convenient size
for access.

Tiling is a core feature of the G93 API and deeply involved
in all its operations.  In fact, even a small grid is treated
as being tiled, though it is completely reasonable to specify
a tile size that matches that of the entire grid.

A few other details about G93 tiling are worth noting:
1. Tiles can be written or read in any order.
2. Not all tiles in the specification need to be populated with
   data. The overhead for empty tiles is small.
3. The G93 compression logic treats each tile as a separate
   block of data that is compressed individually, without
   reference to other tiles.
4. A key feature of the G93 file API is that it maintains an
   in-memory cache to store tiles for rapid access.
 

# Creating a G93 File
The G93 library is a tool for both creating raster files and accessing them.
Writing a G93 file is a 3 step process:

1. A specification is created giving the grid and tile size parameters.
   Other metadata may be specified as needed.
2. The grid specification and a file-path specification are used to
   create a new file for writing data. The initial file is treated as
   an empty collection of tiles and will typically be smaller than 1 kilobyte
   in size.
3. Values are added to the file one grid-point at a time. The internal
   bookkeeping and management of tiles is mostly transparent to the
   calling application.
   
The code fragment below is taken from the PackageData example
application (with some simplifications applied for the sake of
this discussion).   PackageData extracts elevation and bathymetry
values from the ETOPO1 and GEBCO products, and stores the information
in a G93 file with optional data compression.  Both ETOPO1 and GEBCO
are distributed in a file format called NetCDF.  A wiki-page describing
how to read data from NetCDF files is provided at this site under the title
[How to Extract Data from a NetCDF File](https://github.com/gwlucastrig/gridfour/wiki/How-to-Extract-Data-from-a-NetCDF-File)

The parameters for the number of rows and columns in the grid are based on the
dimensions of the source data.  The number of rows and columns in the
tile were chosen because they seemed well suited to the needs of an application
that might use the G93 file.    

	NetcdfFile ncfile = NetcdfFile.open(filePathToSourceETOPO1);
	Variable z = ncfile.findVariable("z");  // the NetCDF variable for reading values

	int nRowsInGrid = 10800;  // spans 90 south to 90 north
	int nColsInGrid = 21600;  // spans 180 west to 180 east
	int nRowsInTile = 90;
	int nColsInTile = 120;

	// Create a specification for the overall grid and tiling.
	G93FileSpecification spec
            = new G93FileSpecification(nRowsInGrid, nColsInGrid, nRowsInTile, nColsInTile);
	

	// Create a G93-formatted file for output
	File outputFile = new File("ETOPO1.g93");
	G93File g93File = new G93File(outputFile, spec); 
	
When a G93 file is created, the metadata from the specification object is used to populate
the header file.  Some of this metadata is immutable and must be fully specified before
the output file is created, other elements can be adjusted after the file is opened.
We will look at some of these setting later on.

## Storing Data
As shown in the code block below, storing data in a G93 file is a relatively straightforward process.
In fact, most of complexity in the code example comes from accessing the NetCDF data
rather than writing the G93.  The example reads data from the source file one
row at a time.  This is the most efficient pattern for accessing the ETOPO1 file.
 	
	// initialize access specifications for NetCDF.
	int[] readOrigin = new int[2];
	int[] readShape = new int[2];

	for (int iRow = 0; iRow < nRowsInGrid; iRow++) {
		readOrigin[0] = iRow;
		readOrigin[1] = 0;
		readShape[0] = 1;
		readShape[1] = nColsInGrid;
		// Read one row of data from the NetCDF file.
		// The data will be stored in a NetCDF "Array" object.
		// Then loop on each column, obtain the elevation/bathymetry data
		// and store it in G93.   ETOPO1 stores data as integers.
		// GEBCO_2019 stores it as doubles.
		Array array = z.read(readOrigin, readShape);.
		for (int iCol = 0; iCol < nColsInGrid; iCol++) {
			int sample = array.getInt(iCol);
			g93.storeValue(iRow, iCol, sample);
		}
	}
	g93.flush();
	g93.close();

The snippet above would work just fine, except that it might be a little
slower than we would prefer.  The reason for this is that each row
stored to the data file requires swapping tiles in and out of memory.
Because there are 21600 columns in the master grid and 120 columns in each tile,
there are 21600/120 = 180 tiles in each row of the tiling scheme. So in order
to keep an entire row of the master-grid data in memory, the G93 API
needs to keep 180 tiles in its cache. But, by default, the G93 cache size
is only 16 tiles in size.  And 16 tiles is not wide enough to hold an
entire row of data. Because the storage process scans
one-row-at-a-time, the cache has to drop and load tiles 180 times per
row.  This approach leads to a lot of redundant reading and writing of tiles.
 
Fortunately, the tile cache size can be adjusted by
using the following adjustment before storing the data.

    g93.setTileCacheSize(G93CacheSize.Large);
	
The adjustment is applied after the G93File is opened, but before the
application starts to access data. The "Large" cache size adjustment
tells G93 to adjust the size of the
cache so that it is large enough to store an entire row (or entire column)
of tiles.  Tiles use 4 bytes per each value stored in memory. Because this
example code specifies a tile size of 90-by-120 grid values, an entire row
of 180 tiles would require 180x90x120x4 = 7776000 bytes (about 7.4 megabytes).
This setting is not onerous, and it will dramatically improve the speed
of writing a file. 

How much difference does the larger cache make?  With the default "Medium" 
cache size, the storage process required 237.2 seconds.  With the
"Large" size setting, it required 9.2 seconds.

Here it is worth emphasizing that the need for a larger cache size
is due to the pattern-of-access applied in packaging the data.
Had the packaging process populated a single tile at a time
(rather than spanning an entire row of tiles), the increased tile
cache size would not have been required. This consideration applies
both when writing data and when reading data for a G93 file.

# Other Settings

## Run-time Settings
By run-time settings, we are referring to those parameters that can be set using
access methods for the G93File class after a G93File is opened. These parameters
are not part of the file definition and are not stored between sessions.

We already introduced the tile-cache size and described how it can affect run-time
performance. The other setting of interest is

	g93.setIndexCreationEnabled(boolean enabled);

When writing data to a G93 file, we have the option of telling G93 to write
an index file when the file is closed. The index file is a companion data file
written with the sample root name as the G93 file and the file extension ".g9x".
The index carries information about the location of tiles within the
G93 file.  Without the index, the G93File class has to perform a scan of
the full file at start-up. Since G93 files can be quite large, such
scans can take a few seconds (more on slow file systems).  The availablity of
an index expedites that process considerably. Again, the index file is optional.
If it was accidentally deleted, the G93 API would be able to open
the main file just fine.

## Specification Settings
In addition to the overall grid size and tiling scheme specifications,
there are a number of other settings that can be stored in a G93 file header
when it is created.  These settings are supplied through calls to the
G93FileSpecification class' access methods.

### Coordinate System
The main G93File API allows an application to read and write raster data
values by supplying the row and column indices for the values of interest.
Many real-world applications depend on a horizontal coordinate system
that relate these values not to grid coordinates, but real-valued
position information.

Gridfour allows an application to specify two broad categories of coordinate
systems for file access: _Cartesian Coordinates_ and _Geographic Coordinates_.
 
Although Gridfour is not limited to geographic coordinate systems,
the two example products we used for this wiki-article are geographic in nature.
So we will begin with the specification for a geographic coordinate
system.

As mentioned above, the row and column spacing for ETOPO1 is a uniform 1 minute of
arc (1/60th of a degree). There are a couple of ETOPO1 variations. The
one chosen for this discussion runs from south to north and west to east.
The latitudes start just above the South Pole and run to just below the North.
The longitudes start just to the east of the International Date Line (-179.99166
longitude) and extend to just to its west (+179.99166 longitude). The following code snippet shows an example of how a specification for ETOPO1 geographic coordinates could be constructed:

    G93FileSpecification spec
            = new G93FileSpecification(nRows, nCols, nRowsInTile, nColsInTile);
	double h = 1.0/60.0;  // one minute spacing, 1/60th of a degree
    spec.setGeographicCoordinates(
             -90+h/2,   // south, first row in grid 
            -180+h/2,   // west, first column in grid 
              90-h/2,   // north, last row in grid
             180-h/2);  // east, last column in grid

Note that the geographic coordinates are given in degrees and specified
in the order latitude, longitude.  West longitudes are given as negative
values. East longitudes are given as positive values.

As an example of Cartesian coordinates, consider an example in which the
(x, y) coordinates are normalized between 0 and 1.  In that case, we might specify
coordinates using the following
  
    G93FileSpecification spec
            = new G93FileSpecification(nRows, nCols, nRowsInTile, nColsInTile);
    spec.setCartesianCoordinates(
            0,   // x coordinate of first column in grid 
            0,   // y coordinate of first row in grid
            1,   // x coordinate of last column in grid
            1);  // y coordinate of last row in grid
 
 Note that the order of the Cartesian coordinates follows the standard practice
 of being given as (x, y).  In the example above, we assumed that the coordinates
 were increasing as the row and column index increased.  Consider the case where
 the x coordinate increased which the y coordinate decreased. Such a specification
 would look like:
 
     spec.setCartesianCoordinates(
            0,   // x coordinate of first column in grid 
            1,   // y coordinate of first row in grid
            1,   // x coordinate of last column in grid
            0);  // y coordinate of last row in grid

The G93File class implements methods for mapping Cartesian or Geographic coordinates 
to grid coordinates and vice versus.  These are shown below:

	public double []mapGridToCartesian(double row, double column)   // returns x,y
	public double []mapCartesianToGrid(double   x, double      y)
	
	public double []mapGridToGeographic(double row, double column)  // returns lat, lon
	public double []mapGeographicToGrid(double latitude, longitude) 
	
Note that the mapping functions may return and/or accept fractional values for the row
and column values the process. This feature is intended to support data value interpolation.

## Data definition: dimension and data type.
G93Files can process either scalar values or vector values. For example, surface elevation
is a scalar value. But ocean currents or surface winds have both direction and magnitude
and are usually treated as vectors.  For G93, scalar values are said to be of dimension 1.
A vector such as Earth surface wind data would be of dimension 2.  Higher dimension data is
also supported.

G93Files also allow the specification of integer data or floating point values.

The data type and dimension are specified using one of the following calls:

	spec.setDataModelInt(  int dimension);
	spec.setDataModelFloat(int dimension);
	spec.setDataModelIntegerScaledFloat(int dimension,  double scale,  double offset);

The scale and offset values specified as part of the integer-scaled-float model are used
in cases where floating point values are to be converted to integers for internal storage.
While this approach can result in reduced precision for the input data, it has advantages
when compressing the data. The G93 implementations of integer compression attain
better compression ratios than the implementation for floating-point values.
If data compression is not required, the integer-scaled option has little advantage.
However, it is worth noting that this format is sometimes encountered in raster data
formats used for publicly available data sources and the G93 equivalent may be useful
in handling such source.

Scale and offset are treated as follows:

	intValue = (floatValue-offset) * scale
	floatValue = (intValue/scale) + offset

## Data Compression
Data compression is enabled as part of the file specification:

    spec.setDataCompressionEnabled(compressionEnabled);

Data compression is an interesting topic and will be discussed in more
detail in a future wiki article. For now, we will simply note it's impact
on the storage for ETOPO1 and GEBCO_2019 data.  In uncompressed form,
ETOPO1 data is stored as a 4-byte integer. GEBCO_2019 is stored as
a 4-byte float. 

|  Product   | Size (bits/sample) | Number of Samples  | Time to Process (sec)|
| ---------- | ------------------ | ------------------ | -------------------- |
|  ETOPO1    |      4.460         |    233,280,000     |      68.3            |
|  GEBCO     |      2.909         |  3,732,480,000     |    1215.5            |
|  GEBCO x 2 |      3.59          |  3,732,480,000     |    1320.3            |

The bits per sample value for GEBCO is lower than that for ETOPO1 because
the sample points are closer together (15 seconds of arc versus 1 minute)
and there tends to be less variation in its values from sample to sample.
Unfortunately, in order to store floating point values using data compression,
they need to be converted to integer values as described above. Since the
GEBCO_2019 elevation and depth values are non-integral, truncating the
decimal part of their values loses some information.  For the second
GEBCO value in the table above(GEBCO x 2), a scaling factor of two
was specified for the data model:

	spec.setDataModelFloat(1, 2.0, 0.0);

Again, we emphasize that the value truncation is required only for compressed data. When data
is stored in its non-compressed form, the full precision of the 4-byte floating-point variables
is maintained.
	
## Adding Supplemental Content with Variable-Length Records
In order to maintain a simple design, the G93 API presents a deliberately
minimal feature set. Even so, we recognize that many users have application-specific
requirements for the product. In some cases, users may wish to attach supplemental
data to a G93 file. This requirement can be met through the use of the
Variable-Length Record feature.

Variable-Length Records (VLRs) are blocks of text or binary data that can be
stored as part of a G93 file. The G93 API treats these elements as _opaque_ and
does not perform any operation related to their content. These elements may be
stored or retrieved using identification tags provided by the application.

The ETOPO1 and GEBCO products provide an example of how this feature may be used.
Both data sets are based on geophysical information and, naturally, there are
industry standards for specifying metadata related to their content. 
Because G93 is intended to be a general-purpose utility,
adding direct support for such standards is outside its scope. However, the PackageData
demonstration application implements code for storing relevant metadata in a
G93 file.

The metadata bundled with the demonstration code is based on the _Well-Known Text (WKT)_ standard
which is supported by many Geographic Information Systems. Well-Known Text files provide
information about coordinate systems (map projections), units of measure, and 
specifications for Earth's size and shape ("datums"), and other information
needed to accurately represent the data on a map. While neither ETOPO1 nor GEBCO
supply WKT files in their distributions, both include relevant specifications on
their product web sites. For the demo, we used the information to create a
file called GlobalMSL.prj that contains metadata in WKT format.

Variable-Length Records are stored using a call to the G93File class' storeVariableLengthRecord()
method as shown in the following code snippet:

    byte []b; // an array of bytes containing the WKT data
	
    g93.storeVariableLengthRecord(
            "G93_Projection",
            2111,
            "WKT Projection Metadata",
            b, 0, b.length, true);

The first two arguments in the call provide application-defined identification
keys for the variable-length text.  The last argument indicates that the payload (content)
for the VLR is a text product. An application wishing to fetch the information
from the G93 file would do so by calling

    VariableLengthRecord vlr = g93.getVariableLengthRecord("G93_Projection", 2111);
    String payloadText = vlr.readPayloadText();

The method that fetches the VLR is named "get" because the record identifications
are loaded from a G93 file when it is initially opened. So the method simply gets
the identification from memory. However, the method that fetches the text
is named "read" because a file-read operation is needed to retrieve it.

As mentioned above, the assignment of the identification string and numeric
value (in this case, "G93_Projection" and 2111) are arbitrary and are defined
by the application. The parameters used for this example are based on the LAS file
standard that are used for terrestrial Lidar (see [What is Lidar?](https://oceanservice.noaa.gov/facts/lidar.html)
at the National Oceanic and Atmospheric Administration web site).
The LAS standard defines a data structure called "Variable Length Record"
which was the model for the G93 implementation.  LAS uses the identifications
LASF_Projection, 2111 for Well-Known Text.
 
At present, there is no registry or official standard for specifying
identification strings in G93. The development of such a resource would
depend on much wider adoption of G93 than is currently the case. 

# Conclusion
The information given in this wiki article should be enough to get you
started using G93.  The use of many of the functions described above
is demonstrated in the [PackageData.java](https://github.com/gwlucastrig/gridfour/blob/master/demo/src/main/java/org/gridfour/demo/globalDEM/PackageData.java)
application included in the Gridfour software distribution.

The G93 API is still under development. While it has undergone quite a bit of testing, it has seen
very little actual use. If you encounter issues using G93, please let us know.
Also, if you identify new features or enhancements you would like added to the API,
we welcome your suggestions.


# References
Adobe Systems Inc.,  1992. _TIFF Revision 6.0, Final-June 3, 1992_.  Accessed December 2019
from https://www.itu.int/itudoc/itu-t/com16/tiff-fx/docs/tiff6.pdf

General Bathymetric Chart of the Oceans [GEBCO], 2019. _GEBCO Gridded Bathymetry Data_.
Accessed December 2019 from [https://www.gebco.net/data_and_products/gridded_bathymetry_data/](https://www.gebco.net/data_and_products/gridded_bathymetry_data/)

National Oceanographic and Atmospheric Administration [NOAA], 2019.
_ETOPO1 Global Relief Model_. Accessed December 2019 from [https://www.ngdc.noaa.gov/mgg/global/](https://www.ngdc.noaa.gov/mgg/global/)

Sonalysts, Inc., 2019. _wXstation_. Accessed December 2019 from [http://www.sonalysts.com/products/wxstation/](http://www.sonalysts.com/products/wxstation/)

University Corporation for Atmospheric Research [UCAR], 2019. _NetCDF-Java Library_
Accessed December 2019 from [https://www.unidata.ucar.edu/software/netcdf-java/current/](https://www.unidata.ucar.edu/software/netcdf-java/current/)

 