<p align="center"><img src="https://github.com/wholder/SilhouetteTest/blob/master/images/SilhouetteTest%20Screenshot.png"></p>

# SilhouetteTest
**SilhouetteTest** is an experimental, Java Language-based program I created to learn how to use the [**Usb4Java**](http://usb4java.org) library to connect to and control a [**Silhouette Curio**™](https://www.silhouetteamerica.com/shop/silhouette-curio-products).  I've included code for other Silhouette cutters, but I've only tested the code with the Curio and the USB I/O values used to connect to these other cutters is only based on information I gleaned from other, similar projects on the Internet and is likely to be incorrect, or insufficient.  Please see the notes in the source code for more details.

I created and tested SilhouetteTest using [**IntelliJ Community Edition 2017**](https://www.jetbrains.com/idea/download/#section=mac) on a Mac Pro and did some further testing on Windows 10 (using Parallels) and Linux Mint.  The code should work on the Mac without further configuration, but there are some additional setup and configuration steps needed before it will run on Windows or Linux (see comments in the source code for additional details.)
### Requirements
SilhouetteTest requires Java 8 JRE or [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html), or later to be installed.
### Purpose of this Project
I have another project called [**LaserCut**](https://github.com/wholder/LaserCut) that is a CAD-like program for creating and cutting 2D designs on a Laser Cutter, such as the Epilog Zing™.  I'm using this test code to experiment with and learn how to control the Silhouette devices and to eventually add support for them into my LaserCut program.  I also thought that other programmers who want to learn how to control Silhouette cutters, or other USB devices might find it useful as a reference.
### Usb4Java
`SilhouetteTest` uses `Usb4Java` to communicate with the USB-based Silhouette cutters.  For convenience, I have included the `Usb4Java` libraries in this project, but you should check for newer versions, as I may not update this project once I've accomplished my goal of updating LaserCut to directly drive a Silhouette cutter.
- [Usb4Java](http://usb4java.org) - Usb4Java Project Page
- [JavaDocs](http://usb4java.org/apidocs/index.html) - Usb4Java JavaDocs
### Information Wanted
If anyone owns a Mac and any of the other Silhouette cutters, such as the Cameo (original, V2 or V3), Portrait, etc., you can help me determine the USB I/O parameters for these other cutters using SilhouetteTest.  There is a [**Runnable JAR file**](https://github.com/wholder/SilhouetteTest/tree/master/out/artifacts/SilhouetteTest_jar) included in the checked in code that you can download.  Just double click the SilhouetteTest.jar file and it should start and display a window like the one shown near the top of this page.  Note: you may have to select the SilhouetteTest.jar file, right click and select "Open" the first time you run the file to satisfy Mac OS' security checks.  Then, select "Run Scan" and click the "RUN" button.  If it can identify a Silhouette-made device (vendor id == `0x0B4D`), it will display text like this:

      Bus: 000 Device 027: Vendor 0x0B4D, Product 0x112C
        Interface: 0
          BLK add: 0x01 (OUT) pkt: 64
          BLK add: 0x82 (IN)  pkt: 64

Please copy all of this text, along with the full Model Name and Version of the Silhouette device tested, and post it in a comment in the Wiki Section listed above.
### USB Communication with the Curio
As shown above, the USB Endpoints for the Curio (and probably the other Silhouette devices) use 64 byte I/O buffers.  This means any code sending commands to the Curio must break up these commands into packets of 64 bytes, or less.  The simplest way to do this (and the method I used for SilhouetteTest)  is to send one command at a time and end each command with a `0x03` byte.  For commands that move the tool head, you can find out when the prior command has completed by sending a the two byte "status" command `0x1B 0x05` and then reading back a two byte response of `0xnn 0x03` where nn will be `0x31` (ASCII `'1'`) when the tool head is in motion and `0x30` (ASCII `'0'`) when the motion has stopped.  Note: in addition to the status command, there are additional commands that will send back other types of information from the Curio.  See the source code for more details. 

Silhouette Studio seems to use a more sophisticated scheme where it stuffs commands into a 64 byte buffer (ending each command with a 0x03 byte) and then sends these out as the 64 byte buffer fills up.  This means that a single command may wind up being split between two different sequential packets.  There is probably a limit of how many bytes can be sent like this before the Silhouette Studio ahs to stop and wit for the commands it has sent to complete, but I have not investigated this in detail.
### Direct Command Mode
If you enable the `"Snd Cmd"` checkbox, a text entry field will appear where you can type commands, such as `"M1000,1000"` (move to position 1000, 1000 where the values are in units of 508 units/inch) and send them directly to the selected Silhouette device by pressing ENTER.  The scrolling text area will print out the command you send along with any response received back, if any.  I added this a way to try and discover new commands through experimentation.  

_Caution: I have observed that some of the commands I tried seemed to put the Curio into a state where some functions, such as tool up and down, stopped working, or began to behave oddly and that cycling the power did not restore their ability to function.  Fortunately, running Silhouette Studio and performing an operation seemed to fix things.  Note: all commands must be terminated by a value of 0x03 but this value is added automatically after you press ENTER._
### Basic Commands
Here is a list of most of the more useful commands needed to control the Silhouette Curio that I've been able to test and verify.  All coordinate values are expressed in "units" where 508 units equals one inch and 20 units equals one millimeter.  On the Curio, the first coordinate value (normally the X axis) in a pair of coordinate values controls in and out movement of the work tray and the second coordinate value (normally the Y axis) moves the tool head left and right.  Increasing values of X (the first value) move the tray back towards the rear of the Curio and increasing values of Y (the 2nd value) move the tool head to the right.  Rotating the Curio by 90 degrees clockwise when looking down from above will show that this scheme implements a quadrant 1 coordinate system with the origin at the lower left.

    H           - Home the tool head and tray (returns to the origin)
    
    M100,200    - Move to position 100,200
    
    D100,300    - Lower selected tool and draw or cut a line from current position to 100,300
    
    G           - Causes the Curio to send back a String like "   100,   300,    20" where the first two values
                  are the current position of the tool head and the 3rd value is *usually* the selected tool * 10.
                  
    Jn          - Select tool n where 1 is the lefthand tool and 2 is the righthand tool
    
    !n          - Set the cut/draw speed to n where n varies from 1-10 and n * 10 is centimeters/second
    
    FXn         - Set the tool pressure when cutting where n varies from 1-33 and n * 7 is grams of force
    
    FN0         - Select Landscape mode (FN1 selects Portrait mode, but this doesn't work on Curio)
    
    FG          - Returns firmware version info, such as "CURIO V1.20    "
    
    FCn         - Sets tool offset where n is 18 for a cutting tool and 0 for sketch pen. Silhouette Studio issues
                  this command twice in a row and I think the first sets the offset for the lefthand tool (J1) and
                  the 2nd sets it for the righthand tool.
                  
    [           - Get lower left corner of current working area (used with 'U' command)
    U           - Get width/height of current working area (3048,4318 for small base and 6096,4318 for large base)
    
    \0,0        - Set lower left corner of current working area to 0,0
    Z3048,4318  - Set width/height of current working area to 3048,4318
                  
    BZ          - draws 4 point Bezier curve (see image below)

<p align="center"><img src="https://github.com/wholder/SilhouetteTest/blob/master/images/Bezier Curve.png"></p>
