
# Introduction
One of the motivations for creating the Gridfour G93 API was to assist investigators
who were experimenting with different techniques for compressing raster data.
To that end, G93 makes it easy to register new data-compression classes with
the existing API so that you can add custom compression functions to G93 without
modifying any of the existing API.

Currently, G93 uses two internal compression modules: one based on Huffman codes and
one based on the well-known Deflate library.  For this article, we will explore adding
a new compression module based on BZip2. For general data compression applications,
BZip2 can produce somewhat better results than Deflate, though it does require more
processing time and memory resources to do so.

One reason that BZip2 is not included in our stock software distribution is that the
Gridfour Project has adopted a _strict_ rule that the Core Gridfour module does not introduce
any secondary dependencies to a Java application. The Core module is pure Java code and
depends only on the standard Java API.  The Core module has no external dependencies. But, because BZip2
is not part of the standard Java API, adding it to the Core would have required allowing
an external dependency. To work around that restriction the G93 file specification allows an application
to register compression classes that belong to libraries external to Core.

## Implementing a BZip2 Compressor
Suppose we were to write a compression class called CodecBZip2 that depended on the
compression utilities in the Apache Compression Library (commons-compress-1.19.jar or more recent
versions).   The class could be written by implementing the Gridfour interface IG93CompressorCodec:

    public class CodecBzip2 implements IG93CompressorCodec {
        public CodecBzip2(){
        }
        // methods from interface as appropriate
    }

We'll discuss more about the implementation below.  For now, let's look at how the compression could
be added to a G93File object.

    G93FileSpecification spec = new G93FileSpecification(nRows, nCols, 90, 120);
    spec.addCompressionCodec("BZip2", CodecBzip2.class);  // BZip2 is an arbitrary name for the compressor
    spec.setDataCompressionEnabled(true);

    File outputFile = new File("Test.g93");
    G93File g93 = new G93File(outputFile, spec);
    g93.setTileCacheSize(G93CacheSize.Large);
    g93.setIndexCreationEnabled(true);

That's it. When the API goes to store the data for the G93 file, it constructs an instance
of the CodecBZip2 class using the default constructor, and then uses that instance to perform data compression.
Naturally, this approach leads to three important requirements for a supplemental compressor:
1. G93 must be able to construct an instance using the default constructor and that instance must be fully
initialized and ready for operations when initialized.
2. The Java Virtual Machine (JVM) must be able to find the class supplied as a supplemental compressor in its classpath.
3. The supplemental compressor must implement the interface IG93CompressorCodec.

## Does BZip2 Make Things Better?
For this article, I implemented the CodecBZip2 class and ran it on the ETOPO1 data set that has been
the subject of other "How-to" articles in this series.  The results are as follows:

|   Version    |   Time to Process   |  Bits/Symbol  |  File Size  |
| ------------ | ------------------- | ------------- | ----------- |
| Standard     |    72 Seconds       |   4.46        |  127.1 MB   |
| BZip2 Added  |   212 Seconds       |   4.20        |  119.7 MB   |
   
As you can see, the saving was modest, though the additional processing for
adding BZip2 to the compression logic made a substantial increase in the application's run time.
While the extra processing time is a disadvantage, it would not necessarily disqualify
BZip2 as an alternate solution to Deflate. In applications that use data compression,
it is common to write data in a compressed form just once, but to read it back multiple times.
Like many data compression techniques, BZip2 requires substantially more processing
to compress data, than to decompress it. So the extra processing for compression would be
a one-time expense for the data producer, but would not affect the data consumers.

Details of how the Gridfour compressors work will be the subject of a future
wiki article. For now, it is sufficient to note that G93 compresses the tiles
in a data file individually. Each time a tile is written to the file,
G93 tries each registered compression method and picks the one that yields
the largest reduction in data storage size.

Each of the existing compression methods uses _Predictive-Transform_ methods
to improve the compressability of the data. There are 3 predictive-transform models
currently supported: Constant, Linear, and Triangle. The statistics below show which 
compression methods (Huffman, Deflate, and BZip2) were used for compression
and which predictor.  In the stock setup, Deflate usually produces better results,
except for about 20 percent of the tiles for which Huffman works better.
It turns out that BZip2 works well for the same kind of data as Deflate (both
look for patterns in the data).  So in most cases where Deflate would have
been used, BZip2 was preferred.  

The following statistics were issued through a call to

	g93.summarize(System.out, true)

which was invoked after the storage of the G93 output file was complete.
The times-used column indicates the number of tiles (and the percent of all tiles)
for which a particular method was selected. 

	Codec G93_Huffman
	   Predictor                Times Used        bits/sym   entropy     avg bits in tree/tile
	   Constant                  393 ( 1.8 %)      1.9         2.3          205.4/20487.5
	   Linear                   1572 ( 7.3 %)      2.9         2.9          744.3/30985.3
	   Triangle                 3956 (18.3 %)      3.7         4.0          773.9/39829.3
	   ConstantWithNulls           0 ( 0.0 %)      0.0         0.0            0.0/0.0
	Codec G93_Deflate
	   Predictor               Times Used         bits/sym   entropy     avg bits per tile
	   Constant                  205 ( 0.9 %)      1.6         2.0         17037.2
	   Linear                    179 ( 0.8 %)      5.3         5.6         56868.0
	   Triangle                 1856 ( 8.6 %)      7.1         6.8         77096.0
	   ConstantWithNulls           0 ( 0.0 %)      0.0         0.0            0.0
	Codec BZip2
	   Predictor               Times Used         bits/sym   entropy     avg bits per tile
	   Constant                 1269 ( 5.9 %)      1.2         1.4         13022.2
	   Linear                    954 ( 4.4 %)      2.2         2.6         24079.9
	   Triangle                11216 (51.9 %)      4.7         5.4         50511.5
	   ConstantWithNulls           0 ( 0.0 %)      0.0         0.0            0.0

## How the BZip2 Compressor was Created
For this article, the structure of the BZip2 compressor was very similar
to the existing Deflate compressor class included in the Gridfour code
distribution.  So implementing CodecBZip2 was just a matter of copying
the existing CodecDeflate compressor and replacing the calls to the
Java Deflate API with calls to the Apache Compress BZip2 API.

However, it is important to note that the fact that we chose to simply refactor
an existing module doesn't mean that all alternate compression techniques
need to do the same thing. One of the ideas of G93 is to provide as much room
for experimentation and variation as possible. So if you do wish to write your own
compressor variation, feel free to do things differently. 

# Conclusion
The experiment with the BZip2 compression only explored a small fraction
of the possible techniques that could have been tried in my efforts to reduce
the size of the output file.  But it does illustrate the potential of custom
compressors to extend the capabilities of the Gridfour Core library.

If you choose to conduct your own investigations, we look forward to hearing
about your results.  Who knows?  Perhaps you may discover techniques that
substantially reduce the storage required for G93-based data products.

 
