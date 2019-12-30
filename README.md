# The Gridfour Software Project
Tools for raster data including scientific and geophysical applications.

## Background
Although there many tools for image processing and gridded data applications for
Geographic Information Systems, the Gridfour Project believes that there is still
a need for general-purpose software utilities for the processing of raster data
products. Potential applications in these areas run the gamut from rendering,
data compression, contouring, surface analysis, and many others.

## Our Inspiration
Recently, there has been a lot of news about the [Seabed 2030 Project](https://seabed2030.gebco.net/) . That ambitious
undertaking aims to map 100 percent of the ocean floor by 2030.  To put that in perspective,
the project organizers estimate that, today, only about 20 percent of the world's oceans are fully
mapped (Seabed 2030, FAQ)[https://seabed2030.gebco.net/faq/#q4].  So there's a lot of work to be done
in the next decade.

Gridfour will probably _not_ be part of that effort. But the existence of projects like Seabed 2030,
and many others, points to a need for software libraries that can assist in the processing of
grid-based (raster) data sets. That need inspired us to create Gridfour.

## An Old Idea Made New
The first module created for the Gridfour Software Project is the G93 grid-based data
compression and file management module.  The algorithms used in G93 have been around 
for a long time. They were originally developed for a project named Gem93 that was
completed in 1993.  Gem93 included data compression inspired by the work of
Kidner and Smith (1991).

Of course, the state of the art has advanced quite a bit since 1993. And although
the Gridfour is based on old ideas, we hope that our G93 library will provide
a convenient tool for investigators developing new techniques for compressing
geophysical and scientific data in raster form.  G93 makes it very easy to
extend the Gridfour codebased and add new data compression capabilities.
Our hope is that by providing this tool, investigators will be able to
focus on their own research and leave the details of file-management to
the G93 tools.

## Things to Come  
The Gridfour Software Project is still in its infancy.  There is a lot
of opportunity for new ideas and new software development. In the future
we hope to include implementations of contouring, statistical analysis,
and physical modeling logic to our collection.

In the meantime, you are welcome to visit our companion Tinfour Software Project at https://github.com/gwlucastrig/Tinfour

Finally, we end with a picture of a global digital elevation module data set named ETOPO1.
ETOP01 was one of the data sets used for the G93 pilot project and a good example of the
potential of systems like it.  The G93 data compression reduces the size of this
data set by a factor of about 4. Future work may bring about more improvements.

![Gridfour rendering of ETOPOO1](doc/images/ETOPO1.jpg "Gridfour rendering of ETOPO1")
