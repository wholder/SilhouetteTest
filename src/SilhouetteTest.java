import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
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
 *   Curio Large Cutting Bed is 8.5 x 12 inches, or 6096 x 4318 units
 *    based on  508 units/inch, or 20 units/mm
 *    Note: Silhouette Studio does not auto detect which base is inserted
 */

public class SilhouetteTest extends JFrame {
  static final DecimalFormat  df = new DecimalFormat("#0.0#");
  private static List<Cutter> cutters = new LinkedList<>();
  private JTextArea           text = new JTextArea();
  private JCheckBox           moveTest, drawTest, penTest, circleTest, showCmds;
  private JComboBox<Cutter>   select;
  private USBIO               usb;

  static class Cutter {
    String  name;
    short   vend, prod;
    byte    intFace, outEnd, inEnd;
    boolean doScan;

    Cutter (String name) {
      this.name = name;
      doScan = true;
    }

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
    cutters.add(new Cutter("Run Scan"));
  }

  public static void main (String[] args) throws Exception {
    new SilhouetteTest();
  }

  private void runTests () {
    try {
      text.setText("");
      Cutter sel = (Cutter) select.getSelectedItem();
      if (sel.doScan) {
        SilhouetteScan.doScan(text);
        return;
      }
      usb = new USBIO(sel.vend, sel.prod, sel.intFace, sel.outEnd, sel.inEnd);
      if (showCmds.isSelected()) {
        usb.setDebug(text);
      }
      while (usb.receive().length > 0)
        ;
      text.append("Cutter: " + getVersionString() + "\n");
      usb.send("FN0\u0003".getBytes());               // Set landscape mode
      usb.send("FC18\u0003".getBytes());              // Offset for Tool 1 (18 = cutter, 0 = pen)
      usb.send("FC18\u0003".getBytes());              // Offset for Tool 2
      // When the Curio is set to what I consider Landscape mode in which the left/right movement of the cutting
      // head is the X axis and the in/out movement of the tray is the Y axis.  However, I have to reverse the
      // order of the X and Y values in the draw and move commands to make the Curio work this way, so I've coded
      // accordingly.  Likewise, the code for getCoords() is likewise reversed and can be called to get the size
      // of the workspace reported as two points for upper left and lower right with x = 0,y = 0 being the position
      // with cutting head to the left and positioned to the rear of the tray.
      Rectangle2D.Double work = getWorkArea();
      // Limit safe work area by setting 30 unit boundary on all sides
      text.append("Workspace: x1 = " + work.x + ", y1 = " + work.y + ", x2 = " + work.width + ", y2 = " + work.height + "\n");
      work = limitCutArea(work, 30, 30);
      text.append("Workspace: x1 = " + work.x + ", y1 = " + work.y + ", x2 = " + work.width + ", y2 = " + work.height + "\n");
      /*
      Commands I still haven't completely figured out how to use.  Supposedly:
        Track enhancement: rolls material in and out several times to emboss grip rollers (not used with Curio)
      usb.send("FW300\u0003".getBytes());             // Set Media (paper type) (100-138, or 300)
      usb.send("FY0\u0003".getBytes());               // "FY1" for track enhance, else "FN0" for none
      usb.send("SO0\u0003".getBytes());               // Set Origin 0 (Not sure what this does)
      */
      moveHome();
      if (moveTest.isSelected()) {
        text.append("Do Move Test\n");
        // Move around the perimeter of the full cutting area (8.5 x 6 inches) inset by 500 units
        // Note: move speed seems to be equal to draw speed set to 10 ("!10")
        moveTo(work.x + 500, work.y + 500);
        for (int ii = 0; ii < 1; ii++) {
          moveTo(work.width - 500, work.y + 500);
          moveTo(work.width - 500, work.height - 500);
          moveTo(work.x + 500, work.height - 500);
          moveTo(work.x + 500, work.y + 500);
        }
      }
      setDrawSpeed(6);
      if (drawTest.isSelected()) {
        text.append("Do Draw Test\n");
        doWait();
        for (int pen = 1; pen <= 2; pen++) {
          int inset = 150 * pen;
          text.append("  Draw with Pen " + pen + "\n");
          selectPen(pen);
          moveTo(work.x + inset, work.y + inset);
          // Build one command string to draw a rectangle the size of the cutting area minus 150 or 300 units
          String draw = "D" +
          formatCoords(work.width - inset, work.y + inset, ",") +
          formatCoords(work.width - inset, work.height - inset, ",") +
          formatCoords(work.x + inset, work.height - inset, ",") +
          formatCoords(work.x + inset, work.y + inset, "\u0003");
          usb.send(draw.getBytes());
          doWait();
        }
      }
      // Used to try out experimental command seqqueces
      if (penTest.isSelected()) {
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
      }
      if (circleTest.isSelected()) {
        text.append("Draw 4 inch diameter circle\n");
        // Draw Bezier Circle
        usb.send(("BZ0,508.20,2011.90,508.06,2018.58,508,2025.28,508,2032,0" + "\u0003").getBytes());
        usb.send(("BZ1,508,2032,508,2592.62,963.38,3048,1524,3048,0" + "\u0003").getBytes());
        usb.send(("BZ1,1524,3048,2084.62,3048,2540,2592.62,2540,2032,0" + "\u0003").getBytes());
        usb.send(("BZ1,2540,2032,2540,1471.38,2084.62,1016,1524,1016,0" + "\u0003").getBytes());
        usb.send(("BZ1,1524,1016,963.38,1016,508,1471.38,508,2032,0" + "\u0003").getBytes());
        usb.send(("BZ1,508,2032,508,2038.72,508.06,2045.42,508.20,2052.10,0" + "\u0003").getBytes());
        doWait();
      }
      text.append("Return to Home Position\n");
      moveHome();
    } catch (Exception ex) {
      text.append(ex.getMessage() + "\n");
      ex.printStackTrace();
    } finally {
      if (usb != null) {
        usb.close();
      }
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
    JPanel panel = new JPanel(new GridLayout(1, 7, 2, 2));
    panel.add(moveTest = new JCheckBox("Move Test", true));
    panel.add(drawTest = new JCheckBox("Draw Test", false));
    panel.add(penTest = new JCheckBox("Pen Dn/Up", false));
    panel.add(circleTest = new JCheckBox("Draw Circle", false));
    panel.add(showCmds = new JCheckBox("Show I/O", false));
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

  private void moveHome () {
    usb.send("H\u0003".getBytes());                         // Move to Home Position
    doWait();
  }

  private String getVersionString() {
    usb.send("FG\u0003".getBytes());                        // Query Version String
    byte[] rsp = usb.receive();
    return (new String(rsp)).substring(0, rsp.length - 1).trim();
  }

  /**
   * Sets the speed where the input value times 10 is centimeters/second
   * @param speed parameter range 1 - 10
   */
  private void setDrawSpeed (int speed) {
    speed = Math.max(Math.min(speed, 1), 10);               // Range is 1-10
    usb.send(("!" + speed + "\u0003").getBytes());
  }

  /**
   * Set tool pressure (multiplied by 7 to get grams of force, or 7-230 grams)
   * @param pres parameter range 1 - 33
   */
  private void setPressure (int pres) {
    pres = Math.max(Math.min(pres, 1), 33);                 // Range is 1-33
    usb.send(("FX" + pres + "\u0003").getBytes());
  }

  private void selectPen (int pen) {
    if (pen == 1 || pen == 2) {                             // 1 selects left pen, 2 selects right pen
      usb.send(("J" + pen + "\u0003").getBytes());
    }
  }

  private void moveTo (Point2D.Double pnt) {
    moveTo(pnt.x, pnt.y);
  }

  private void moveTo (double xLoc, double yLoc) {
    usb.send(("M" + formatCoords(xLoc, yLoc, "\u0003")).getBytes());
    doWait();
  }

  private void drawTo (Point2D.Double pnt) {
    drawTo(pnt.x, pnt.y);
  }

  private void drawTo (double xLoc, double yLoc) {
    usb.send(("D" + formatCoords(xLoc, yLoc, "\u0003")).getBytes());
    doWait();
  }

  private String formatCoords (Point2D.Double pnt, String end) {
    return formatCoords(pnt.x, pnt.y, end);
  }

  private String formatCoords (double xLoc, double yLoc, String end) {
    return df.format(yLoc) + "," + df.format(xLoc) + end;
  }

  private void drawPlusMark (int xLoc, int yLoc) {
    int size = 20;              // Cross is +/- 1 mm
    moveTo(xLoc - size, yLoc);
    drawTo(xLoc + size, yLoc);
    moveTo(xLoc, yLoc - size);
    drawTo(xLoc, yLoc + size);
  }

  private Point2D.Double getCoord (String cmd) {
    usb.send(cmd.getBytes());
    byte[] rsp = usb.receive();
    String tmp = (new String(rsp)).substring(0, rsp.length - 1);
    String[] vals = tmp.split(",");
    double yy = Double.parseDouble(vals[0].trim());
    double xx = Double.parseDouble(vals[1].trim());
    return new Point2D.Double(xx, yy);
  }

  private Rectangle2D.Double getWorkArea () {
    Point2D.Double ul = getCoord("[\u0003");
    Point2D.Double lr = getCoord("U\u0003");
    return new Rectangle2D.Double(ul.x, ul.y, lr.x, lr.y);
  }

  private Rectangle2D.Double limitCutArea (Rectangle2D.Double work, double xInset, double yInset) {
    Rectangle2D.Double rect = new Rectangle2D.Double(xInset, yInset, work.width - xInset * 2, work.height - yInset * 2);
    usb.send(("/" + rect.y + "," + rect.x + "\u0003").getBytes());
    usb.send(("/" + rect.height + "," + rect.width + "\u0003").getBytes());
    return rect;
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
   * Waits until move or draw command is complete and motion is stopped
   */
  private void doWait () {
    JTextArea save = usb.getDebug();
    usb.setDebug(null);
    while (getStatus() == '1')
      ;
    usb.setDebug(save);
  }
}
