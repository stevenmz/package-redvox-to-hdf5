# package-redvox-to-hdf5

Package-redvox-to-hdf5 is a utility to convert [Redvox](https://redvox.io/) a collection of [infrasound](https://en.wikipedia.org/wiki/Infrasound) packets a [HDF5](https://support.hdfgroup.org/HDF5/whatishdf5.html) file.
Converting to one file eases the burden on analysts who are both familiar with the HDF5 format, and who want to work with a small number of files (Redvox-enabled phones can produce 1000's of infrasound recordings a day).

This work was performed under the auspices of the U.S. Department of Energy by Lawrence Livermore National Laboratory under Contract DE-AC52-07NA27344.

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Download a Pre-built Version
GitHub hosts certain releases of this software which would save you the effort of downloading Maven to build the code. You can find all released versions [here](https://github.com/stevenmz/package-redvox-to-hdf5/packages/).

### Prerequisites

To build and run the code, you will need to download and install the following tools:
1. [HDF View](https://www.hdfgroup.org/downloads/hdfview/) : Installing HDF View will provide the HDF5 libraries necessary to run package-redvox-to-hdf5 and it will also provide a viewer for you to see what is contained in the output files.
2. [Java 8](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html): This code is written for Java 8. I have not tried to compile on later versions of Java.
3. [Apache Maven](https://maven.apache.org/): Maven is used to manage the external library dependencies and build this software.


### Building the Code

```
1. Clone this repository.
2. Change directory (cd) to the project's root folder.
3. Execute: mvn install
```

## Deployment

To run this program, you will have to specify the location of the HDF5 libraries on
your computer as part of the java command. The program takes two input parameters, the path to a directory containing Redvox packets in JSON format, and the desired output filename.

### Examples

* On OS X: `java -Xmx2G -Djava.library.path=/Applications/HDFView.app/Contents/MacOS -cp target/PackageRedvoxToHdf5-1.0.1.jar gov.llnl.gmp.minos.uhtolbnlpipeline.PackageRedvoxToHdf5 <input directory> <output filename>`

### Using the output
* [HDF View](https://www.hdfgroup.org/downloads/hdfview/): A program that lets you visually explore the contents of HDF5 files. You can view groups, datasets, attributes, and even view the dataset contents if they fit in memory.
* [h5py](https://www.h5py.org/): A python library for programatically interacting with a dataset.

## Built With

* [Maven](https://maven.apache.org/) - Dependency Management


## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Authors

* **Steven Magana-Zook** - *Initial work* - [Stevenmz](https://github.com/stevenmz)


## License

This project is licensed under the GPL 3.0 License - see the [COPYING](COPYING) file for details

## Acknowledgments

* [Dr. Anthony Christe](https://github.com/anthonyjchriste) for his tireless efforts creating the Redvox infrastructure and software.
* [Dr. Milton Garces](https://www.higp.hawaii.edu/cgi-directory/directory.cgi?func=disp&searchname=MiltonA.Garces) for his leadership in getting the Redvox technology into researcher hands.
* The rest of the [University of Hawaii Infrasound Laboratory (ISLA)](https://www.isla.hawaii.edu/) that made Redvox Infrasound tools accessible to the research community.

## Disclaimer
This document was prepared as an account of work sponsored by an agency of the United States government.
Neither the United States government nor Lawrence Livermore National Security, LLC, nor any of their employees makes any warranty,
expressed or implied, or assumes any legal liability or responsibility for the accuracy, completeness, or usefulness of any information,
apparatus, product, or process disclosed, or represents that its use would not infringe privately owned rights. Reference herein to any
specific commercial product, process, or service by trade name, trademark, manufacturer, or otherwise does not necessarily constitute or
imply its endorsement, recommendation, or favoring by the United States government or Lawrence Livermore National Security, LLC.
The views and opinions of authors expressed herein do not necessarily state or reflect those of the United States government or
Lawrence Livermore National Security, LLC, and shall not be used for advertising or product endorsement purposes.
