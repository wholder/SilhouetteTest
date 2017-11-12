import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;

/*
 *   Test Program for Silhouette/Graphtec Curio
 *
 *  Links with useful information on Graphtec/Silhouette devices:
 *    https://ohthehugemanatee.net/2011/07/gpgl-reference-courtesy-of-graphtec/
 *    https://github.com/pmonta/gerber2graphtec/blob/master/file2graphtec
 *    https://github.com/Timmmm/robocut/blob/master/Plotter.cpp
 *    https://github.com/vishnubob/silhouette/blob/master/src/gpgl.py
 *    https://github.com/fablabnbg/inkscape-silhouette/blob/master/Commands.md
 *    https://en.wikipedia.org/wiki/HP-GL
 *    https://github.com/Skrupellos/silhouette/blob/master/decode
 *
 *  To Run on Windows see:
 *    https://github.com/pbatard/libwdi/wiki/Zadig
 *
 *  To Run on Linux: (Works!  Needed to unplug and replug device after steps below)
 *    Create a file named usb.rules in /etc/udev/rules.d/ like this
 *      1. groups (to get list of groups user belongs to, such as "sudo" which is used in step 3)
 *      2. sudo su (then input password)
 *      3. echo SUBSYSTEM=="usb",ATTR{idVendor}=="0b4d",ATTR{idProduct}=="112c",MODE="0660",GROUP="sudo" >> usb.rules
 *      3b. or use text editor to create this text in /etc/udev/rules.d/usb.rules file
 *      4. sudo udevadm control --reload
 *      5. exit (to exit sufdo su)
 *      6. Important: unplug device and then plug it in again
 *    Note: use "lsusb" to list available USB devices on linux
 *
 *   Note: on linux, it also works to run as root:
 *     sudo su (then enter password)
 *     java -jar <path to SilhouetteTest.jar>
 *
 *   Curio Small Cutting Bed is 8.5 x 6 inches, or 4318 x 3048 units
 *     or 508 units/inch, or 20 units/mm (after power up in landscape mode)
 *
 *   Speed: value of 1-10 is multiplied by 10 to get speed in centimeters/second
 *   Pressure/Thickness: value of 1-33 is multiplied by 7 to get grams of force (7-230)
 *   Track enhancement: rolls material in and out several times to emboss grip rollers (not used with Curio)
 */

public class SilhouetteTest extends JFrame {
  private static List<Cutter> cutters = new LinkedList<>();
  private JTextArea           text = new JTextArea();
  private JCheckBox           moveTest, drawTest, miscTest, showCmds;
  private JComboBox<Cutter>   select;
  private USBIO               usb;

  static class Cutter {
    String  name;
    short   vend, prod;
    byte    intFace, outEnd, inEnd;

    Cutter (String name, short vend, short prod, byte intFace, byte outEnd, byte inEnd) {
      this.name = name;
      this.vend = vend;
      this.prod = prod;
      this.intFace = intFace;
      this.outEnd = outEnd;
      this.inEnd = inEnd;
    }

    public String toString () {
      return name;
    }
  }

  static {
    cutters.add(new Cutter("Curio",    (short) 0x0B4D, (short) 0x112C, (byte) 0, (byte) 0x01, (byte) 0x82));
    // Values for the devices below are not verified and are included only as placeholders until they are
    cutters.add(new Cutter("Portrait", (short) 0x0B4D, (short) 0x1123, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("Cameo",    (short) 0x0B4D, (short) 0x1121, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("SD-2",     (short) 0x0B4D, (short) 0x111D, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("SD-1",     (short) 0x0B4D, (short) 0x111C, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("CC300-20", (short) 0x0B4D, (short) 0x111A, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("CC200-20", (short) 0x0B4D, (short) 0x110A, (byte) 0, (byte) 0x01, (byte) 0x82));
  }

  public static void main (String[] args) throws Exception {
    new SilhouetteTest();
  }

  private void runTests () {
    try {
      Cutter sel = (Cutter) select.getSelectedItem();
      usb = new USBIO(sel.vend, sel.prod, sel.intFace, sel.outEnd, sel.inEnd);
      if (showCmds.isSelected()) {
        usb.setDebug(text);
      }
      usb.send("FG\u0003".getBytes());               // Query Version String
      byte[] rsp = usb.receive();
      String vers = (new String(rsp)).substring(0, rsp.length - 1).trim();
      text.append("Cutter: " + vers + "\n");
      // Get dimensions of work space as reported by the device as two points, upper left and lower right.
      // Note: the Curio powers up in Landscape mode, which flips the way I see the X and Y axes. so, I have
      // reversed them in the code so the origin is the home position, left/right is X, and base in/out is Y.
      Point ul = getCoord("[\u0003");
      Point lr = getCoord("U\u0003");
      text.append("Workspace: x1 = " + ul.x + ", y1 = " + ul.y + ", x2 = " + lr.x + ", y2 = " + lr.y + "\n");
      /*
      Commands I still haven't completely figured out how to use
      usb.send("FW300\u0003".getBytes());             // Media (paper type) (100-138, or 300)
      print(usb.receive());
      usb.send("FX15\u0003".getBytes());              // Pressure (1-33)
      print(usb.receive());
      usb.send("FC18\u0003".getBytes());              // Cutter Offset "FC18" for cutting, "FC0" for pen
      print(usb.receive());
      usb.send("FY0\u0003".getBytes());               // "FY1" for track enhance, else "FN0" for none
      print(usb.receive());
      usb.send("FN0\u0003".getBytes());               // "FN1" for Landscape, else "FN0" for Portrait mode
      print(usb.receive());
      usb.send("SO0\u0003".getBytes());               // Set Origin 0 (Not sure what this does)
      print(usb.receive());
      */
      usb.send("H\u0003".getBytes());                 // Move to Home Position
      doWait();
      if (moveTest.isSelected()) {
        text.append("Do Move Test\n");
        // Move around the perimeter of the full cutting area (8.5 x 6 inches) inset by 500 units
        // Note: move speed seems to be equal to draw speed set to 10 ("!10")
        for (int ii = 0; ii < 1; ii++) {
          moveTo(lr.x - 500, ul.y + 500);
          moveTo(lr.x - 500, lr.y - 500);
          moveTo(ul.x + 500, lr.y - 500);
          moveTo(ul.x + 500, ul.y + 500);
        }
      }
      drawSpeed(6);
      if (drawTest.isSelected()) {
        text.append("Do Draw Test\n");
        doWait();
        for (int pen = 1; pen <= 2; pen++) {
          text.append("  Draw with Pen " + pen + "\n");
          selectPen(pen);
          // Build one command string to draw around the perimeter of the cutting area inset by 500 units
          StringBuilder draw = new StringBuilder("D");
          draw.append(getDrawCoords(lr.x - 500, ul.y + 500, ","));
          draw.append(getDrawCoords(lr.x - 500, lr.y - 500, ","));
          draw.append(getDrawCoords(ul.x + 500, lr.y - 500, ","));
          draw.append(getDrawCoords(ul.x + 500, ul.y + 500, "\u0003"));
          usb.send(draw.toString().getBytes());
          doWait();
        }
      }
      // Used to try out experimental command seqqueces
      if (miscTest.isSelected()) {
        if (true) {
          // Tests how a moveTo() followed by a drawTo() at the same location results in a pen down/up
          text.append("Do Pen Up/Down Test\n");
          for (int pen = 1; pen <= 2; pen++) {
            text.append("  Use Pen " + pen + "\n");
            selectPen(pen);
            for (int ii = 0; ii < 2; ii++) {
              moveTo(1000, 1000);
              drawTo(1000, 1000);
            }
          }
        } else {
          // Draws part of a large arc, but not a circle...
          text.append("Do Draw 3 Point Circle\n");
          int xLoc = 2000;
          int yLoc = 2000;
          int radius = 500;
          Point p1 = new Point(xLoc, yLoc - radius);
          Point p2 = new Point(xLoc, yLoc + radius);
          Point p3 = new Point(xLoc + radius, yLoc);

          moveTo(xLoc + radius, yLoc);
          StringBuilder draw = new StringBuilder("WP");
          draw.append(getDrawCoords(p1, ","));
          draw.append(getDrawCoords(p2, ","));
          draw.append(getDrawCoords(p3, "\u0003"));
          usb.send(draw.toString().getBytes());
          doWait();

          //drawCross(p1);
          //drawCross(p2);
          //drawCross(p3);
        }
      }
      text.append("Return to Home Position\n");
      usb.send("H\u0003".getBytes());                 // Move to Home Position
      doWait();
    } catch (Exception ex) {
      text.append(ex.getMessage() + "\n");
      ex.printStackTrace();
    } finally {
      usb.close();
    }
    text.append("Done\n");
  }

  private SilhouetteTest () {
    super("SilhouetteTest");
    text.setColumns(50);
    text.setRows(30);
    text.setFont(new Font("Monaco", Font.PLAIN, 12));
    text.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JScrollPane scroll = new JScrollPane(text, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    add(scroll, BorderLayout.CENTER);
    JPanel panel = new JPanel(new GridLayout(1, 6, 2, 2));
    panel.add(moveTest = new JCheckBox("Move Test", true));
    panel.add(drawTest = new JCheckBox("Draw Test", false));
    panel.add(miscTest = new JCheckBox("Misc Test", false));
    panel.add(showCmds = new JCheckBox("Show Cmds", false));
    select = new JComboBox<>(cutters.toArray(new Cutter[cutters.size()]));
    panel.add(select);
    JButton run = new JButton("RUN");
    run.addActionListener(e -> {
      Thread worker = new Thread(this::runTests);
      worker.start();
    });
    panel.add(run);
    add(panel, BorderLayout.SOUTH);
    setLocationRelativeTo(null);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    pack();
    setVisible(true);
  }

  private void drawSpeed (int speed) {
    speed = Math.max(Math.min(speed, 1), 10);               // Range is 1-10
    usb.send(("!" + speed + "\u0003").getBytes());
  }

  private void selectPen (int pen) {
    if (pen == 1 || pen == 2) {                             // 1 selects left pen, 2 selects right pen
      usb.send(("J" + pen + "\u0003").getBytes());
    }
  }

  private void moveTo (Point pnt) {
    moveTo(pnt.x, pnt.y);
  }

  private void moveTo (int xLoc, int yLoc) {
    usb.send(("M" + yLoc + "," + xLoc +"\u0003").getBytes());
    doWait();
  }

  private void drawTo (Point pnt) {
    drawTo(pnt.x, pnt.y);
  }

  private void drawTo (int xLoc, int yLoc) {
    usb.send(("D" + getDrawCoords(xLoc, yLoc, "\u0003")).getBytes());
    doWait();
  }

  private String getDrawCoords (Point pnt, String end) {
    return getDrawCoords(pnt.x, pnt.y, end);
  }

  private String getDrawCoords (int xLoc, int yLoc, String end) {
    return yLoc + "," + xLoc + end;
  }

  private void drawCross (Point pnt) {
    drawCross(pnt.x, pnt.y);
  }

  private void drawCross (int xLoc, int yLoc) {
    int size = 20;              // Cross is +/- 1 mm
    moveTo(xLoc - size, yLoc);
    drawTo(xLoc + size, yLoc);
    moveTo(xLoc, yLoc - size);
    drawTo(xLoc, yLoc + size);
  }

  private Point getCoord (String cmd) {
    usb.send(cmd.getBytes());
    byte[] rsp = usb.receive();
    String tmp = (new String(rsp)).substring(0, rsp.length - 1);
    String[] vals = tmp.split(",");
    int yy = Integer.parseInt(vals[0].trim());
    int xx = Integer.parseInt(vals[1].trim());
    return new Point(xx, yy);
  }

  /**
   * Used by doWait() to get status of plotter
   * @return '1' if plotter is executing a move or draw command
   */
  private byte getStatus () {
    usb.send(new byte[]{0x1B, 0x05});               // Status Request
    byte[] data;
    do {
      data = usb.receive(100);
    } while (data.length == 0);
    return data[0];
  }

  /**
   * Waits until current movement, or draw command is complete
   */
  private void doWait () {
    JTextArea save = usb.getDebug();
    usb.setDebug(null);
    while (getStatus() == '1')
      ;
    usb.setDebug(save);
  }
}
