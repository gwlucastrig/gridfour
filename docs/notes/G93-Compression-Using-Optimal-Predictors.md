
**Under construction -- Corrections, better formatting, and more text coming soon**


# Introduction
The Gridfour software library recently added a supplemental data compression module that
offers an incremental improvement in data compression ratios for integer-based
raster data sets. The implementation, which is designated LSOP, is based on
the method of Optimal Predictors proposed by Smith and Lewis (1994).
LSOP is a lossless data compression technique that offers good compression
ratios and good data-access speeds.

Although the algorithm used by the LSOP compressor was described in 1994, it has been
largely overshadowed by lossy techniques based on wavelets or the discrete
cosine transform. But because it is lossless, Smith and Lewis' Optimal Predictor method
has the advantage of preserving the exact values provided by its input data. This feature
makes the compression technique suitable for archival, reference, and data-distribution
applications. Optimal predictors also have the attractive characteristic of requiring
a light processing load. By implementing the Optimal Predictor algorithm as a G93
data compression format, the Gridfour project hopes to direct some overdue attention
to this useful technique.

The algorithm used by Smith and Lewis was published in _Computers & Geosciences_ (Smith & Lewis, 1994),
but an earlier version of their paper (Lewis & Smith, 1993) may obtained without fee from the  [Cartography and Geographic Information Society website](https://cartogis.org/docs/proceedings/archive/auto-carto-11/pdf/optimal-predictors-for-the-data-compression-of-digital-elevation-models-using-the-method-of-lagrange-multipliers.pdf).  The Lewis & Smith paper provided
the basis for the implementation used for Gridfour.  One of the paper's authors, Derek Smith,
also contributed to an "overview" paper that gives much useful background on the ideas used
for data compression (see Kidner and Smith, 2003).


## Performance
The table below gives data compression values in bits per sample (bps) for 
the ETOPO1 and GEBCO 2020 data sets.  Both data sets give global elevation and
bathymetry data in meters.  Both data sets have similar complexity.
The sample spacing for the GEBCO data set is smaller than that of the ETOPO1 set.
The closer spacing of sample points in the GEBCO data set means that
all of the predictors (both standard and LSOP) tend to be more successful
for GEBCO than that are for ETOPO1.  Thus the data compression rate given in
terms of bits-per-symbol is smaller for GEBCO than for ETOPO1.   
 

| Product    | Grid Spacing  | Values in Grid | Standard  | LSOP Only | Combined  | Improvement    |
| ---------- | ------------- | -------------- | --------- | --------- | --------- | -------------- |
| ETOPO1     |  1 minute     |  233 Million   | 4.40 bps  | 3.67 bps  | 3.66 bps  | 16.8%, 20.6 MB |
| GEBCO 2020 |  15 seconds   |  3.7 Billion   | 3.08 bps  | 2.97 bps  | 2.87 bps  |  6.8%, 71.2 MB |

In order to manage large raster data sets such as ETOPO1 and GEBCO_2020, the Gridfour library's 
G93 file format divides the overall collection into subsets called "tiles".  For the test results
given above, the partitioning scheme was specified to use tiles 120 rows high and 240 columns wide.
Each tile is compressed independently. When a tile is compressed, the Gridfour library uses a _brute force_
method for compressing the data: it reduces the tile using one of the three standard predictors
(Constant, Linear, and Triangle) and the compresses the residual values using either of
the conventional data compression techniques Huffman coding or the Deflate algorithm.  The compressed
sizes are compared and the combination that produces the best results is used to store data to
a file.  For this test, the LSOP predictor was added to the roster, and the same approach was applied.

The LSOP predictor proved to be the most effective predictor in 90.1 % of the tiles for ETOPO1
and 53.7 % of the tiles for GEBCO_2020.  Normally, the Deflate data compression technique is
the preferred method for the standard predictors (with only a small number of tiles using Huffman).
Since the Deflate algorithm is generally more powerful that Huffman, this preference is not surprising.
However, when the Optimal Predictor implementation was added to the to the data compression process,
the Huffman data compression technique was preferred by 5-to-1 margin for both
test sets.   

One interesting feature in the compression statistics is that the LSOP predictors
give a much larger percentage improvement for the lower-resolution ETOPO1 product
than for the higher-resolution GEBCO product. The LSOP predictor is more powerful
than the standard predictors, but in the denser product the standard predictors already
do well enough that the extra predictive power produces only a small improvement.

### Total Reduction for Storage Size
We can consider the degree to which the combination of both the Gridfour standard
and Optimal Predictor compression techniques reduce 
the size of data by comparing the output file size to a realistic estimate of
the input size. Both the ETOPO1 and GEBCO_2020 products give elevations in integer values ranging from
-10952 meters to 8627 meters. These values could comfortably be stored in a standard
two-byte (16 bit) integer format. So if we compute the size for the original data as using two
bytes per sample, we can compare it to the compressed output size as shown below.

| Product    | Grid Dimensions  |  # Values     | Source Size  |  Compressed Size    |
| ---------- | ---------------- | ------------- | ------------ | ------------------- |
| ETOPO1     |  10800x21600     | > 233 Million |    444.9 MB  | 101.63 MB (22.84 %) |
| GEBCO 2020 |  43200x86400     | > 3.7 Billion |   7119.1 MB  |1279.06 MB (17.97 %) |

Once again, we see the influence of data density on the effectiveness of a predictor
based data compression.  When samples are spaced closer together, the residuals from
the predictor tend to be smaller in magnitude and more readily compressed. Thus the
GEBCO_2020 product yields a somewhat better storage reduction (17.97 % of its original size)
than the less dense ETOPO1 product (22.84%). 

# How it works
The concept of using predictive algorithms to improve data compression was introduced
in a previous article in this series (Lucas, 2019).  In practice, raster data often compresses
poorly when processed by convention data compressors such as Huffman coding or
the popular Deflate algorithm used in the zip format. Predictive algorithms implement
an invertible (bijective) transformation that converts the source data from a raster grid
into a form that exhibits a greatly improved degree of redundancy and, thus, is more suitable
for compression by conventional means.  To do so, predictive techniques implement models that
estimate the value at various grid points based on the values of their immediate neighbors.
In practice, the residuals (error terms) for the predictions tend to have a small magnitude
and exhibit a high degree of redundancy from grid cell to grid cell.  Thus the sequence
of residuals tends to compress much more effectively than the data from the source grid.
With appropriate coding, it is possible to reconstruct the original data from the residuals.
So the use of predictors leads to a lossless data compression technique that produces
a substantial reduction in the storage requirements for the data.

Smith and Lewis used the classic method of Lagrange Multipliers to analyze a data set
and identify parameters for a predictor that were optimal in the sense that the mean error
of the residuals tended to zero. This feature led to output data set with statistical properties
that made it well suited to data compression by convention data compression techniques.
To see how Smith and Lewis’ Optimal Predictors work, we will begin with a simpler technique,
the Triangle Predictor, which was introduced by Kidner and Smith in 1993.

The triangle predictor estimates the value for a grid cell using a linear combination
of the values of its three neighboring points as shown below:

![Geometry for triangle predictor](images/OptimalPredictors/SLGrid_3x3.png)

Give a grid with uniform spacing and sample points A, B, and C, the predicted value
for point P is computed as

![Variables for triangle predictor](images/OptimalPredictors/image001.png)

The equation above assigns coefficients of 1 (or -1) to each of the source data values. 
It is natural to wonder if there might be a different set of weighting coefficients that 
describe the data better. For example, consider a region characterized by roughly parallel 
ridges as shown below.  In parts of the region where the ridges run east and west, sample 
points aligned in a horizontal direction might be better predictors than those in the vertical. 
Thus the value A might be a better predictor of P than the values for C and B. In others, 
where the ridges have a diagonal orientation, the value for B might be a better predictor 
than either A or B. Obtaining the best predictions depends on having a way 
to pick an optimal set of parameters.

<figure>
  <img src="images/OptimalPredictors/image002.jpg" alt="Terrain with ridges">
<br>
  <figcaption>Figure 1 - Terrain with ridges rendered using Gridfour DemoCOG application, land-cover from Patterson (2020).</figcaption>
</figure>

## Lagrange Multipliers
The method of Lagrange Multipliers resembles that of the method of Ordinary Least Squares. 
Because the least-squares technique is better known than Lagrange, we will begin with that 
method before moving to Lagrange Multipliers. 

For the three-point case, we wish to find suitable
coefficients  ![Variables for triangle predictor](images/OptimalPredictors/image003.png) for a predictor in the form 

![Estimator for grid cell i,j](images/OptimalPredictors/image004.png)

The error term, or residual, ![Residual](images/OptimalPredictors/image005.png) is given as

![Residual calculation](images/OptimalPredictors/image006.png)

The classic least-squares estimator attempts to find the set of
coefficients  ![Coefficients for predictor](images/OptimalPredictors/image003.png) that would minimize
the sum of the squares of the 

![Sum of the squared errors](images/OptimalPredictors/image008.png)

From calculus, we know that for the choice of coefficients that results in the minimum of 
the error function, the partial derivatives with respect to each of the coefficients will be equal to zero:

![Calculus based minimization](images/OptimalPredictors/image009.png)

Based on these observations, we can combine the partial derivatives to construct a system of linear equations
that contain the desired coefficients as variables. Solving for those variables yields the parameters for an
estimator function that minimizes the sum of the squared errors.

### Adding Constraints for Optimization
The method of Lagrange Multipliers extends the Ordinary Least Squares
technique by permitting the specification of one or more constraints
on the minimization result.  The method makes a tradeoff, permitting the overall sum of the squared errors
to increase slightly but guaranteeing that the constraint condition is met by the solution.
In the case of the Optimal Predictors technique, Smith and Lewis specified that the mean error
for the predictors must equal zero
 
![Mean error](images/OptimalPredictors/image011.png)

This additional constraint is the key to the success of the Optimal Predictors method.
While Ordinary Least Squares guarantees a minimum error, there is no requirement that mean
error it produces is zero. In fact, it often is not. But Smith and Lewis reported that by
adding the constraint, they could improve the compression ratios by about 35 % (Lewis & Smith, pg. 256).
So while we might expect that allowing the overall sum of the squared errors to increase might
degrade the effectiveness of the predictor, it turns out that just the opposite is true.
The mean-error-of-zero constraint improves the overall redundancy in the set of residuals
produced by the predictor.  This increase in redundancy, in turn, leads to better compressibility.

To get a sense of why this improvement occurs, let’s consider an example where we are
storing data using the Huffman coding compression technique. Huffman coding works best
when the frequency distribution for symbols in an encoded data set is strongly non-uniform.
So if a predictor could produce a set of residuals in which the value zero occurs much more
frequently than other values, it will tend to compress well using Huffman. 

Let's assume that the predictor residuals fall into a normal distribution, so that the majority
of the error values lie close to the average error. Suppose that a data set exists in which
Ordinary Least Squares produces an average error of 0.5.  Since the compression operates on
integer values, some of the residuals in the mid range will round up to one and some down to zero. Thus the
number of incidences symbols with a value of zero and symbols with a value of one 
will be about the same.  Neither one nor zero will dominate the frequency distribution
for residuals.  The Huffman coding for such a data set will produce a relatively
small reduction in storage size.

Now, suppose that using Optimal Predictors, we develop a set of predictor
coefficients that produces an average error of zero. In that case, the number
of residuals with a value of zero will be larger than those with a value of one.
So we see that the output from Optimal Predictors is better suited for the
Huffman encoding than the output from Ordinary Least Squares.

### Configuration and Performance of Optimal Predictors
In addition to the three-coefficient Optimal Predictor described above, Smith and Lewis also implemented an
eight-coefficient variation. Later, Kidner & Smith added 12 and 24-coefficient variations. The layout for these
predictors is shown below.
 
![Layout for predictor coefficients](images/OptimalPredictors/lsgroups.png)

Currently, the Gridfour implementation uses the 12 coefficient variation.  

The following table gives some general statistics for the prediction using the 12-coefficient predictor
over the ETOPO1 data set. Data from the polar regions was excluded because it tends to have a higher
predictability than for the rest of the world.  

|                                      |   ETOPO1     |  GEBCO 2020  |  
| ------------------------------------ | ------------ | ------------ |
| Mean error predicted values          |     -1.49E-6 |    8.99E-7   |
| RMSE predicted values                |     14.73    |   10.67      |
| Mean absolute error                  |      4.78    |    1.74      |
| Integer residuals with zero value    |     25.3 %   |   43.4 %     |
| Avg. bits/sample (LSOP only)         |       3.67   |     2.97     |
 

The statistics in the table above confirm that the denser spacing of the GEBCO 2020 data set
leads to an increase in the predictability of its values compared to ETOPO1. The effects
of this improved predictability are reflected in the reduced number of bits needed to encode the data. 

 
### Derivation for Optimal Predictors
The equations and derivation for Optimal Predictors using Lagrange Multipliers is
discussed in detail in [Lewis & Smith](https://cartogis.org/docs/proceedings/archive/auto-carto-11/pdf/optimal-predictors-for-the-data-compression-of-digital-elevation-models-using-the-method-of-lagrange-multipliers.pdf) (1993).


# Implementation Details
 
## Modules and Dependencies in the Java Implementation
The current implementation policy for the Gridfour project is to ensure that the _core_ Gridfour module (GridfourCore.jar)
has no external dependencies beyond the standard Java API. Determining coefficients for a 12-coefficient Optimal Predictor
requires solving a system of 13 linear equations.  The linear algebra and matrix operations required to find
such a solution are not supported by the standard Java API. To perform these operations, the LSOP implementation
depends on the API provided by the Apache Commons Math library.  To prevent the Gridfour core module from depending on
this external library, the Java classes related to Smith and Lewis' Optimal Predictor algorithm are implemented in
a separate module named "lsop" (GridfourLsop.jar).

Fortunately, the logic required to decompress LSOP data does not require solving linear systems and, thus, does
not require external dependencies. Therefore, the _data decompression_ logic for the LSOP algorithm
is implemented in the Gridfour core module. This approach ensures that even if an application does not include
the GridfourLsop.jar file, it will still be able to decode an existing G93-formatted data file that contains
information compressed using the Optimal Predictor module. This approach reflects the Gridfour design
philosophy of open-source and open-data implementations and ensuring that information stored in the G93
format remains accessible to external applications both now and in the future.  

## Computational Considerations

As noted above, the current Gridfour implementation uses the 12 coefficient variation. If you refer to the source code
for the implementation, you will note that the data elements and predictor coefficients are assigned
variables named according to their position relative to the data cell of interest as shown below (for example u1, u2, u3, etc.)
 
![Layout for neighbor values](images/OptimalPredictors/SLGrid_12x12.png)

### Floating-point Operations
The predictor used in the LSOP compressor is based on floating-point arithmetic. Writing the code
for the LSOP predictor, encoder, and decoder, required strict attention to the way computer
systems and the Java programming language perform floating-point operations. 
One of the most important considerations in the design of the G93 file format is that data created on one
system, or in one development environment (Java, C/C++, .NET, etc.), can be accessed software
running on another. In order to support that goal, it essential that the floating-point
arithmetic needed for the Optimal Predictor be reproducible in a consistent manner. 

In order to ensure consistency, LSOP uses 4-byte, single-precision floating point arithmetic for
its predictor. The Java classes related to Optimal Predictors are implemented using Java's **strictfp** keyword.
All floating-point to integer conversions are all performed using Java's StrictMath.round() function.
Similar capabilities are available for most modern development environments and programming languages.
  

### Configuring a File to use Optimal Predictors
During construction of a G93 file, the parameters for a file are specified using the G93FileSpecification class
(see [How to Package Data Using the G93 Library](https://github.com/gwlucastrig/gridfour/wiki/How-to-Package-Data-Using-the-G93-Library). A Java application can specify the use of the Optimal Predictors compressor, using the approach shown in the
following code snippet:

    import org.gridfour.g93.lsop.compressor.LsEncoder12;
    import org.gridfour.g93.lsop.decompressor.LsDecoder12;

    public static void addLsopToSpecification(G93FileSpecification spec) {
        spec.addCompressionCodec("G93_LSOP12", LsEncoder12.class, LsDecoder12.class);
    }

The compression codec must be specified before a file is created. As noted above, the LsEncoder12 class depends on
the Apache Common Math library.  Once a file is created and fully populated, the encoder and the math library
are no longer needed. So an application that access a G93 file on a read-only basis does not need to
include these resources.

## Future Work: Arithmetic Coding
Kidner and Smith (2003) reported using arithmetic coding as their preferred method for compressing
the post-prediction data set (the residuals). Since arithmetic coding is conceptually similar to Huffman
coding, and since Huffman coding was the preferred compression technique for the LSOP predictor,
I investigated the use of arithmetic coding in the Gridfour implementation.  For purposes of this investigation,
the _Reference arithmetic coding_ library (Project Nayuki, 2020) was used to support arithmetic coding operations.  

In testing, arithmetic coding yielded only small improvements in data compression.  For ETOPO1, the
Huffman-variation cited above required and average of 3.6547 bits per symbol.  The arithmetic coding variation
required 3.6531 bits per symbol.  However, the arithmetic coding process is slower than Huffman. Arithmetic coding 
required an average of 234 seconds across multiple trials versus 122 seconds for Huffman.

With regard to these results, it is worth noting that the Gridfour implementation of Huffman coding is
carefully tuned to the requirements of the G93 data compression application. A great deal of attention
has been given both to speed of access and the representation of the Huffman tree when data is stored.
The Nayuki arithmetic coding API is a reference implementation that is intended to demonstrate how arithmetic
coding works and does not focus on the most minimal compression or fastest data access.  In the test implementation,
I paid considerable attention to creating a compact representation of the frequency table for storing the
data, but did otherwise attempt to optimize the code.

In future work, the may be opportunities to obtain even better compression ratios using an arithmetic coding
implementation customized for the G93 file application.

# Referenceces

Kidner, David & Smith, Derek. (2003). Advances in the data compression of digital elevation models. Computers & Geosciences. 29. 985-1002. 

Lewis, M., & Smith, D.H. (1993). Optimal predictors for the data compression of digital 
elevation models using the method of Lagrange multipliers.  In _Auto-Carto XI Proceedings of the
International Symposium on Computer-Assisted Cartography_, Oct 30-Nov 1, 1993. PDF document accessed
Aug, 2020 from https://cartogis.org/docs/proceedings/archive/auto-carto-11/pdf/optimal-predictors-for-the-data-compression-of-digital-elevation-models-using-the-method-of-lagrange-multipliers.pdf

Patterson, Tom. (2020). _100-meter Natural Earth Map Data_. Accessed September 2020 from  [http://shadedrelief.com/NE_100m/](http://shadedrelief.com/NE_100m/)

Project Nayuki. (2020). _Reference arithmetic coding_. Accessed September 2020 from https://www.nayuki.io/page/reference-arithmetic-coding.

Smith, Derek H., & Lewis, Michael. (1994).  Optimal predictors for compression of digital elevation models.
_Computers & Geosciences, Volume 20, Issues 7-8,_ August-October 1994, 1137-1141

 

 