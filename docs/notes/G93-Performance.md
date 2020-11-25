# Introduction
The primary design goal for Gridfour's G93 API was to create an API that would
provide a simple and reliable interface for working with grid-based data sources.
And, in designing the G93 file format, we sought to develop a file-format specification
that would be stable across future versions, portable across different operating environments
and implementation languages, and suitable for long-term archiving of data.

None of that would matter if the G93 software did not provide acceptable performance.

This document describes the results of performance tests for the G93 software that were
aimed at assessing the usefulness of the implementation. The tests involved
performing various patterns of data access on two medium-to-large raster
data products.  The tests were performed on a medium-quality laptop with 16 GB of installed memory
and standard Solid State Drive (SSD).

In evaluating the Java implementation for G93, we were interested in two
kinds of performance: speed of access and data compression ratios. The tests
were performed using two publicly available data sets: ETOPO1 and GEBCO_2019.
These products, which provide world-wide elevation and ocean bottom depth information,
are described elsewhere in this wiki in the article [How to Extract Data from a NetCDF File](https://github.com/gwlucastrig/gridfour/wiki/How-to-Extract-Data-from-a-NetCDF-File).

The following table gives physical details for the test data files. Grid dimensions
are given in number of rows by number of columns.

| Product    |  File Size   | Grid Dimensions  |  # Values     | Bits/Value  |
| ---------- | ------------ | ---------------- | ------------- | ----------- |
| ETOPO1     |    890 MB    |  10800x21600     | > 233 Million |  32.01      |
| GEBCO 2019 |  11180 MB    |  43200x86400     | > 3.7 Billion |  25.18      |

The ETOPO1 data is stored as 4-byte integers giving depth/elevation in meters.
Its values range from -10803 meters depth to 8333 meters height. The GEBCO_2019
data is stored as floating point values in a moderately compact form.
Its values range from -10880.588 meters to 8613.156 meters.


# Performance Tests
The source data products are distributed in the well-known NetCDF data format.
The data in the products is stored in row-major order, so the most efficient
pattern of access for reading them is one-row-at-a-time.

The G93 data is intended to support efficient data access regardless of
what pattern is used to read or write them. Like the source data, the G93 test files
used for this evaluation were written in a row-major order.  So accessing
the G93 data on grid-point-at-a-time in row-major order would traverse the
file in the order that it was laid out.  Not surprisingly,
sequential access of the G93 files tends to be the fastest pattern of
access.

In practice, real-world applications tend to process data from
large grids in blocks of neighboring data points. So they usually focus on a small
subset of the overall collection. The test patterns used for this evaluation
were based on that assumption. Except for the "Tile Load" case, 
they involved 100 percent data retrieval.  The "block" tests
used the G93 block API to read multiple values at once (either an entire
row or an entire tile). Other tests read values one-at-a-time.

The following table lists timing results for various patterns
of access when reading all data in the indicated files. Times are given in seconds.
Results are given for both the standard (non-compressed) variations of the G93
files and the compressed forms.


|  Pattern      |  ETOPO1  |  ETOP01 Comp | GEBCO_2019 | GEBCO Comp |
| ------------- | -------- | ------------ | ---------- | ---------- |
| Row Blocks    |   2.76   |  5.52        |  65.32     |  103.47    |
| Row-Major     |   2.91   |  5.76        |  66.83     |  107.67    |
| Column-Major  |   3.29   |  6.28        |  96.83     |  148.27    |
| Tile Block    |   1.80   |  4.74        |  29.40     |   77.66    |
| Tile Load     |   0.53   |  3.33        |  20.81     |   69.32    |

Again, these access-time values reflect how long it takes to read the entire file.
The values cited are rather abstract and, without more information, it may not be clear
how they can be used to estimate the performance of an actual application. To put the
figures in context, it is useful to remember that the ETOPO1 file is
large (233 million points) and the GEBCO 2019 product is larger (3.7 billion points).
Translating the times for the row-major access pattern to number of samples
retrieved per second leads to the following results (all rates in millions
of samples per second):

|  ETOPO1     |  ETOP01 Comp | GEBCO_2019 | GEBCO Comp |
| ----------- | ------------ | ---------- | ---------- |
|   80.2 M/s  |  40.5 M/s    |  55.9 M/s  |  34.7 M/s  |


## Row Blocks versus Row-Major
The Row Blocks test reads each row of data in the source grid in
a single "read". There are 10800 rows of data in the ETOPO1 product
and 21600 columns. Thus each block-read operation returns 21600 values.
This pattern of access is illustrated by the following code snippet

    for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
      float []block = g93.readBlock(iRow, 0, 1, nColsInRaster);
	}
	
In contrast the Row-Major operation reads the grid points for
each row one value at a time.   Internally, the G93File class
reads the data one tile at a time and stores them in a cache as necessary.
The "read-value" API determines which tile is needed and fetches
it from the cache each time the application requests a value. This pattern 
of access is illustrated by the following code snippet:

    for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
      for (int iCol = 0; iCol < nColumnsInRaster; iCol++) {
         int sample = g93.readIntValue(iRow, iCol);
	  }
	}

The Column-Major pattern is similar, but uses columns as the outer loop.

    for (int iCol = 0; iCol < nColumnsInRaster; iCol++) {
      for (int iRow = 0; iRow < nRowsInRaster; iRow++) {
         int sample = g93.readIntValue(iRow, iCol);
	  }
	}	

As these code snippets show, the Row-Major and Column-Major patterns
perform a separate read operation for each data point. Because each of these
read operations is independent, they involve a overhead
both for the Java method call and for the internal arithmetic to compute
an index into the grid and retrieve the associated tile from G93's
internal tile-cache. This small overhead was multiplied across the
numerous data read operations that were performed by these tests.

The difference in data-fetching time between the Row-Major and Column-Major
access pattern is due to the fact that the tiles stored in the
particular G93 files used for this test were themselves written
to the file in row-major order (rows of tiles). So two tiles that
were adjacent in a single row were also adjacent in their file
locations. However, two tiles that were adjacent in a single
column were not adjacent in the file. This the Column-Major
pattern required more file seek-and-fetch operations than the
Row-Major alternative.

In theory, the block retrievals should be faster
than the single-point read operations because fewer calls result
in less overhead. As the table above shows, the difference in the
operations was detectable, but not especially impressive. This result suggests
that the g93.readBlock() method may present opportunities for improvement.

## The Cost of Reading Data from the File
The Tile Load test retrieves just one point for tile. Thus the run time for
the test is essentially just the time required to load read the individual tiles from
the data file.  The Tile Block test follows the same pattern as Tile Load,
but loads the entire content of the tile using the readBlock() method.
So the difference in time for the tests is just the overhead of indexing
and copying the data from the tile to the result array.

At present, the Tile Block read times are significantly lower than those
of the Row Block test. Again, this result suggests that there may be
opportunities to optimize the access code for the read routines.

## Access Times for G93 versus NetCDF
As mentioned above, the source data used for the performance tests
described in these notes is distributed in a format called NetCDF.
G93 is not intended to compete with NetCDF. The two data formats
are intended for different purposes and to operate in
different environments. But the Java API for NetCDF is a well-written
module and, as such, provides a good standard for speed of access.
Any raster-based file implementation should be able to operate with performance that
is at least as good as NetCDF. 

The table below gives accessing times for reading the _entire_ data set for
ETOPO1 and GEBCO_2019, using the Java API for NetCDF and G93 file formats.
Both files can be accessed in random order, but the most efficient way of retrieving
their content is by following a pattern of access established by their underlying
data definition. The NetCDF distributions of the ETOPO1 and GEBCO products
can be accessed most efficiently in row-major order (one complete row at a time).
The G93 format can be accessed most efficiently one complete tile at a time.
So performance statistics for those two patterns are included in the table
below.  Because the NetCDF version of GEBCO_2019 is stored in a semi-compressed
format, the timing values for the compressed version of G93 are
more relevant than the uncompressed version. Finally, to clarify the
differences in behavior between the two API's, the table repeats the
G93 timing data for row-major access.
 

|  Format     |  ETOPO1  | GEBCO_2019 Compressed | GEBCO_2019 |
| ----------- | -------- | --------------------- | ---------- |
| NetCDF      |   2.1    |     132.2             |  N/A       | 
| G93 (tiles) |   1.8    |      77.7             |  29.40     |
| G93 (rows)  |   2.9    |     107.7             |  66.8      |

The differences in timing reflect the differences in the intention of the
two packages. NetCDF is a widely used standard for distributing data
(it also has support for a number of languages besides Java, including C/C++, C#, and Python).
G93 is intended more for back-end processing for large raster data sets
in cases where the pattern-of-access is arbitrary or unpredictable. When using NetCDF
(at least the Java version), it is often helpful if the pattern of data access
sticks to the underlying organization of the file. For example, when accessing
the NetCDF file in column-major order, the ETOPO1 read operation took
1184.3 seconds to complete while G93 took 3.3 seconds.  Of course, we
need to be a little candid here.  It is possible to devise access patterns that
defeat both file formts. Had we accessed the G93 and NetCDF files in a completely random 
pattern for a very large number of data points, we could have achieved equally
bad performance for both.


## Why G93 Does not use Memory Mapped File Access
The G93 API does not use Java's memory-mapped file access. Instead, it
uses old-school file read-and-write operations. The reason for this
is based on known problems in Java. First, Java does not support
memory-mapped file access for files larger than 2.1 gigabytes,
and raster files of that size or larger are common in scientific
applications. Additionally, there are reports that Java does not
always close memory mapped files and clean up resources when
running under Windows. Thus, for the initial implementation,
we decided to avoid memory mapped files.


# Data Compression
Although there are many good general-purpose data compression utilities
available to the software community, raster-based data files tend to
compress only moderately well. For example, consider the compression results for
ETOPO1 using two well-known compression formats, Zip and 7z.  Since the elevation
values in ETOPO1 range from -10803 to 8333, they can be stored comfortably in
a two-byte short integer. There are just over 233 million sample points in ETOPO1.
Storing them as short integers leads to a storage size of 466,560,000 bytes.
Using Zip and 7z compressors on that data yields the following results.

| Product           |  Size (bytes)  | Relative Size  | Bits/Value |
| ----------------- | -------------- | -------------- | ---------- |
| ETOPO1 (standard) | 466,560,000    |   100.0 %      | 16.00      |
| ETOPO1 Zip        | 309,100,039    |    66.3 %      | 10.60      |
| ETOPO1 7z         | 201,653,141    |    43.2 %      |  6.92      |

It is also worth nothing that general-purpose compression has the disadvantage
that in order to access any of the data in the file, it is necessary to decompress the whole
thing. For many grid-based applications, only a small portion of a raster file is needed
at any particular time.

The G93 API uses standard compression techniques (Huffman coding, Deflate),
but transforms the data so that it is more readily compressed and yields
better compression ratios. Each tile is compressed individually, so
an application that wants only part of the data in the file can
obtain it without decompressing everything else. 

The G93 file format supports 3 different data formats:

    1.  Integer (4-byte integers)
	2.  Float (4-byte, 32 bit IEEE 754 standard floating-point values)
	3.  Integer-Coded Floats (floating-point values scaled and stored as integers)
	
The GEBCO data is expressed in a non-integral form. While the data can be stored
as integer-coded-floats, the integer coding requires some loss of precision. 
So, to store the data in a lossless form, it needs to be represented using
the 4-byte floating-point format.  While the floating-point representation of the
data preserves all the precision in the original, it requires a different approach
to data compression than the integer forms.  Unfortunately, the G93 floating-point
implementation does not achieve quite as favorable compression ratios as the integer-based
compressors. 

The table below shows the relative storage size required for different products
and storage options. The entries marked GEBCO x 1, GEBCO x 2, are scaled integer
representations of the original sample points. There are two reasons that the scaled versions
compress more readily than the floating-point. First, the G93 integer
compressors are more powerful than the floating-point compressor. Second,
the scaling operation truncates some of the fractional part of the original
values and, thus, discards some of the information in the original product.


|  Product        | Size (bits/sample) | Number of Samples  | Time to Compress (sec) |
| --------------- | ------------------ | ------------------ | ---------------------- |
|  ETOPO1         |      4.46          |    233,280,000     |      68.3              |
|  GEBCO x 1      |      2.89          |  3,732,480,000     |    1252.1              |
|  GEBCO x 2      |      3.56          |  3,732,480,000     |    1210.7              |
|  GEBCO x 4      |      4.35          |  3,732,480,000     |    1193.7              |
|  GEBCO (floats) |     15.41          |  3,732,480,000     |     748.2              |


ETOPO1 data is stored in the form of integers with a small amount of overhead
for metadata and file-management elements. As table above shows, G93 data compression reduces
the raw data for ETOPO1 from 16 bits per sample to 4.46 bits per sample.  So the storage
required for the data is reduced to about 27.9 % of the original size. 

When deciding whether to use the native floating-point representation of the GEBCO data
or the more compact integer-scaled float representation,  the main consideration is
how much precision is required for the application that uses it. One can reasonably
ask just how much precision the GEBCO data really requires.  For example, a scaling
factor of 1 essentially rounds the floating-point values to their nearest integers.
In effect, this process reduces the data to depth and elevation given in integer meters.
For ocean data with 1/2 kilometer sample spacing, an accuracy of 1 meter may be more than is
truly required. Even on land, it is unlikely than many of the points in the data set
are accurate to within 1 meter of the actual surface elevation. However, the extra
precision in the floating-point format (or that achieved with a larger scaling factor)
may help reduce data artifacts due to quantization noise. Also, for purposes of this test, we were
interested in how well the algorithm would work, so we applied various
scaling factors as shown in the results above.

# Future Work
One item that was not fully explored in this article is the effect of disk speed in read
operations. The testing was, after all, conducted using a system equiped with
a fast solid-state drive. A future investigation will use an USB external disk drive to
see how much extra access time a slower disk requires.

Some of the test results above suggest that there may still be opportunities
to improve the access speeds, especially for the block-based file access routines.
Future work will involve a careful review of the code used for the readBlock()
method.

# Conclusion
The figures cited in this article provide a rough guide to estimating
the kind of performance an application designer can expect using the
G93 API. It also gives an indication of the resources that would be needed
to process and store data in G93 format.

As with any API, the design of G93 makes assumptions about the way it will
be accessed. Our hope is that those assumptions are broad enough that
G93 will provide acceptable performance for most applications.

