This directory includes sample files intended to exercise the various
features and data formats supported by GVRS.

Samples of data primitives are provided in the file SampleDataPrimitives.dat.
These represent the basic data types used to construct GVRS files.
 
For sample files 0 through 12, data values are computed based on the grid index,
starting with -1 at grid row=0, column=0, 1 at grid position row=1, column=1, etc.
Thus the grid cells include both negative and positive values. The table
below illustrates the values in a 3 row by 5 column grid.

               col 0
    row  0       -1    0    1    2    3
    row  1        4    5    6    7    8
	row  2        9   10   11   12   13

Data is stored using elements specified as types Short, Integer, Float, 
or Integer-Coded Float.
	
Some cases features a tile size that does not evenly divide the grid size.
In these cases, the last rows and columns of tiles will contain cells that
are not valid grid cells.  These tile cells are populated with null data values.


Sample files 13 and 14 assign model coordinates in the range 0 to 1 to each grid cell.
Data is stored in Float or Integer-Coded Float elements.   The value of each cell
is computed from model coordinates using the following formula:
     
	  z = sin(x * PI) * sin(y * PI)
	  

File                            Grid    Tiles   Description
--------------------------     -------  -----   --------------------------------------------------------
Sample00_ShortNoComp.gvrs       10x10    5x5    Short, no nulls, not compressed
Sample01_IntNoComp.gvrs         10x10    5x5    Integer, no nulls, not compressed
Sample02_FltNoComp.gvrs         10x10    5x5    Float, no nulls, not compressed
Sample03_ICFNoComp.gvrs         10x10    5x5    Integer-Coded Float, scale=1.0, no nulls, not compressed

Sample04_ShortComp.gvrs        100x100  50x50   Short, no nulls, compressed
Sample05_IntComp.gvrs          100x100  50x50   Integer, no nulls, compressed
Sample06_FltComp.gvrs          100x100  50x50   Float, no nulls, compressed
Sample07_ICFComp.gvrs          100x100  50x50   Integer-Coded Float, scale=1.0, no nulls, compressed

Sample08_MixedTypes.gvrs        10x10    5x5    Multi-element short and float

Sample09_ShortNoComp.gvrs       10x10    6x6    Short, has nulls, not compressed
Sample10_IntNoComp.gvrs         10x10    6x6    Integer, has nulls, not compressed
Sample11_FltNoComp.gvrs         10x10    6x6    Float, has nulls, not compressed
Sample12_ICFNoComp.gvrs         10x10    6x6    Integer-Coded Float, scale=1.0, has nulls, not compressed

Sample13_ModelCoord.gvrs        11x11   11x11   Float with model coordinates
Sample14_LSOP.gvrs             101x101 101x101  ICF with LSOP compression

SampleDataPrimitives.dat                        Sample data primitives
SampleMetadata.gvrs                             Sample populated with metadata elements


---------------------------------------------------------------------------------
Sample data primitives were written using the following Java code

    try (BufferedRandomAccessFile braf = new BufferedRandomAccessFile(file, "rw")) {
      braf.leWriteShort(0x0000_01ff);
      braf.leWriteShort(0x0000_ff01);

      braf.leWriteInt(0x0102_03ff);
      braf.leWriteInt(0x0203_ff01);
      braf.leWriteInt(0x03ff_0102);
      braf.leWriteInt(0xff01_0203);

      float floatTarget = 1.0f + 1.0f / 256f;
      braf.leWriteFloat(-floatTarget);
      braf.leWriteFloat(floatTarget);

      double doubleTarget = 1.0 + 1.0 / 256.0;
      braf.leWriteDouble(-doubleTarget);
      braf.leWriteDouble(doubleTarget);

      braf.leWriteUTF("Test data for GVRS");
	  
	  braf.leWriteLong(0x01020304_05060708L);
      braf.leWriteLong(0xff010203_04050607L);
    }

--------------------------------------------------------------------------------------
Sample metadata is stored in the form:
     name                   ID             n bytes   content 
   GvrsCompressionCodecs     0  ASCII           33   GvrsHuffman|GvrsDeflate|GvrsFloat
   GvrsJavaCodecs            0  ASCII          240   GvrsHuffman,org.gridfour.compress.CodecHuffman,...
   mShort                    0  Short           20      -1       0       1       2       3
   mUnsShort                 1  Short Unsign    20   65535       0       1       2       3
   mInt                      2  Int             20      -1       0       1       2       3
   mDbl                      3  Double          32      -1.000000  0.000000  0.500000  0.600000
   mFlt                      4  Float            0
