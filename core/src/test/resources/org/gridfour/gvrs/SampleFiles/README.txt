This directory includes sample files intended to exercise the various
features and data formats supported by GVRS.

For samples 0 through 12, data values are computed based on the grid index,
starting with 0 at grid row=0, column=0, 1 at grid position row=1, column=1, etc.
Data is stored using elements specified as types Short, Integer, Float, 
or Integer-Coded Float.

Some cases features a tile size that does not evenly divide the grid size.
In these cases, the last rows and columns of tiles will contain cells that
are not valid grid cells.  These tile cells are populated with null data values.


Samples 13 and 14 assign model coordinates in the range 0 to 1 to each grid cell.
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


	  
