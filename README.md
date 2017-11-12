<p align="center"><img src="https://github.com/wholder/SilhouetteTest/blob/master/images/SilhouetteTest%20Screenshot.png"></p>

# SilhouetteTest
**SilhouetteTest** is an experimental program I wrote to learn how to use the [**Usb4Java**](http://usb4java.org) library to connect to and control a Silhouette/Graphtex Curio Paper and Vinyl Cutter.  The code allows you to test other Silhouette cutters, too, but I've only tested the code with the Curio and the USB I/O values used to connect to these other cutters is only based on information I gleaned from other, similar projects on the Internet and is likely to be incorrect, or insufficient.  Please see the notes in the source code for more details.

I created and tested SilhouetteTest using [**IntelliJ Community Edition 2017**](https://www.jetbrains.com/idea/download/#section=mac) on a Mac Pro and did some further testing on Windows 10 (using Parallels) and Linux Mint.  The code should work on the Mac without further configuration, but there are some additional setup and configuration steps needed before it will run on Windows or Linux (see comments in the source code for additional details.)
### Purpose of this Project
I have another project called [**LaserCut**](https://github.com/wholder/LaserCut) that is a CAD-like program for creating and cutting 2D designs on a Laser Cutter, such as the Epilog Zing.  I'm using this test code to experiment with and learn how to control the Silhouette devices and to eventually add support for them into my LaserCut program.  I also thought that other programmers who want to learn how to control Silhouette cutters, or other USB devices might find it useful as a reference.
### Usb4Java
**SilhouetteTest** uses Usb4Java to communicate with the USB-based Silhouette cutters.  For convenience, I have included the Usb4Java libraries in this project, but you should check for newer versions, as I may not update this project once I've accomplished my goal of added code into LaserCut to directly drive the Shilouette cutters.
- [Usb4Java](http://usb4java.org) - Usb4Java Project Page
- [JavaDocs](http://usb4java.org/apidocs/index.html) - Usb4Java JavaDocs
