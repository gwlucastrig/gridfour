
# Introduction
The G93 software library recently added a supplemental data compression that
offers an incremental improvement in data compression ratios for integer-based
raster data sets. The implementation, which is designated LSOP, is based on
the method of optimal predictors proposed by Smith and Lewis (1994).
LSOP is a lossless data compression technique that offers good compression
ratios and good data-access speeds.

Although the algorithm used by the LSOP compressor was described in 1994, it has been
largely overshadowed by lossy techniques based on wavelets or the discrete
cosine transform. By implementing Smith and Lewis's algorithm in a G93
data compression format, the Gridfour project hopes to direct attention
to their interesting technique.

The algorithm used by Smith and Lewis was published in _Computers & Geosciences_
but an alternate version (Lewis & Smith, 1994) may obtained without fee from  [https://cartogis.org/](https://cartogis.org/docs/proceedings/archive/auto-carto-11/pdf/optimal-predictors-for-the-data-compression-of-digital-elevation-models-using-the-method-of-lagrange-multipliers.pdf) 


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
| ETOPO1     |  1 minute     |  233 Million   | 4.04 bps  | 3.78 bps  | 3.77 bps  | 14.4%, 17.6 MB |
| GEBCO 2020 |  15 seconds   |  3.7 Billion   | 3.08 bps  | 3.08 bps  | 2.96 bps  |  3.9%, 54.9 MB |

One interesting feature in the compression statistics is that the LSOP predictors
give a much larger percentage improvement for the lower-resolution ETOPO1 product
than for the higher-resolution GEBCO product. The LSOP predictor is more powerful
than the standard predictors, but in the denser product the standard predictors already
do well enough that the extra predictive power only offers a small improvement.

# Referenceces

Lewis, M., & Smith, D.H. (1994). Optimal predictors for the daa compression of digital 
elevation models using the method of Lagrange Multipliers.  PDF document accessed
Aug, 2020 from https://cartogis.org/docs/proceedings/archive/auto-carto-11/pdf/optimal-predictors-for-the-data-compression-of-digital-elevation-models-using-the-method-of-lagrange-multipliers.pdf

Smith, Derek H., & Lewis, Michael. (1994).  Optimal predictors for compression of digital elevation models.
_Computers & Geosciences, Volume 20, Issues 7-8,_ August-October 1994, 1137-1141

 

 