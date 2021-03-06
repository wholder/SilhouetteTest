import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
 *   Curio Small Cutting Bed is 8.5 x 6 inches, or 4318 x 3048 units (with X axis as tool head)
 *   Curio Large Cutting Bed is 8.5 x 12 inches, or 4318 x 6096 units
 *    based on  508 units/inch, or 20 units/mm
 *    Note: Silhouette Studio does not auto detect which base is inserted
 *
 *  Other commands:
 *    G           - Query current position and selected tool slot, returns String like this:
 *                  "  1000,  1000,    20" where selected tool is last number / 10, or 2 here
 *    O100,100    - Move +100,+100 relative to current location (use G command to verify new position)
 *
 */

public class SilhouetteTest extends JFrame {
  private static DecimalFormat  df = new DecimalFormat("0.##");
  private static List<Cutter>   cutters = new LinkedList<>();
  private JTextArea             text = new JTextArea();
  private JTextField            command;
  private JCheckBox             moveTest, drawTest, penTest, circleTest, showCmds, sendCmd;
  private JComboBox<Cutter>     select;
  private boolean               manCmd, clearCmd;
  private USBIO                 usb;

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
    cutters.add(new Cutter("Cameo 3",  (short) 0x0B4D, (short) 0x112F, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("SD-2",     (short) 0x0B4D, (short) 0x111D, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("SD-1",     (short) 0x0B4D, (short) 0x111C, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("CC300-20", (short) 0x0B4D, (short) 0x111A, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("CC200-20", (short) 0x0B4D, (short) 0x110A, (byte) 0, (byte) 0x01, (byte) 0x82));
    cutters.add(new Cutter("Run Scan"));
  }

  public static void main (String[] args) {
    new SilhouetteTest();
  }

  private void runTests () {
    try {
      Cutter sel = (Cutter) select.getSelectedItem();
      if (sel == null)
        return;
      if (sel.doScan) {
        SilhouetteScan.doScan(text);
        return;
      }
      usb = new USBIO(sel.vend, sel.prod, sel.intFace, sel.outEnd, sel.inEnd);
      // Gobble up any leftover responses from a prior command sequence, if any
      while (usb.receive().length > 0)
        ;
      if (manCmd) {
        String cmd = command.getText();
        sendCmd(cmd);
        getResponse();
        if (clearCmd) {
          command.setText("");
          clearCmd = false;
        }
      } else {
        text.setText("");
        appendLine("Cutter: " + getVersionString());
        sendCmd("FN0");                                 // Set landscape mode
        sendCmd("FC18");                                // Offset for Tool 1 (18 = cutter, 0 = pen)
        sendCmd("FC18");                                // Offset for Tool 2
        // When the Curio is set to what I consider Landscape mode in which the left/right movement of the cutting
        // head is the X axis and the in/out movement of the tray is the Y axis.  However, I have to reverse the
        // order of the X and Y values in the draw and move commands to make the Curio work this way, so I've coded
        // accordingly.  Likewise, the code for getCoords() is likewise reversed and can be called to get the size
        // of the workspace reported as two points for upper left and lower right with x = 0,y = 0 being the position
        // with cutting head to the left and positioned to the rear of the tray.
        Rectangle2D.Double work = getWorkArea();
        appendLine("Workspace: " + df.format(work.x) + ", " + df.format(work.y) + ", " +
                    df.format(work.width) + ", " + df.format(work.height));
        // Call limitWorkArea() to define an aera outside of which the device will not cut
        // For example, limit safe work area by setting 30 unit boundary on all sides, like this
        //work = limitCutArea(work, 30, 30);
        //appendLine("Workspace: " + df.format(work.x) + ", " + df.format(work.y) + ", " +
        //            df.format(work.width) + ", " + df.format(work.height)");
        // Note: subsequent calls to getWorkArea() will then show the newly set limited area, so you'l have to save
        // or hard code the original size of the work area to restore it later, as I've found no command to do this.
        if (moveTest.isSelected()) {
          appendLine("Do Move Test");
          // Move inside the perimeter of the full cutting area (8.5 x 6 inches) inset by 500 units.  Move speed seems to
          // be equal to draw speed set to maximum, which is 10 ("!10")  Also: unlike a draw command, issuing a new move
          // command will interrupt a move in progress, so moveTo() internally calls doWait() to allow each move command
          // to complete before processing another.
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
          appendLine("Do Draw Test");
          doWait();
          for (int pen = 1; pen <= 2; pen++) {
            int inset = 150 * pen;
            appendLine("  Draw with Pen " + pen);
            selectPen(pen);
            moveTo(work.x + inset, work.y + inset);
            // Build one command string to draw a rectangle the size of the cutting area minus 150 or 300 units
            // Unlike move commands, draw commands execute in sequence and the next command does not proceed
            // until the prior comamnd is complete.  This means that only one send command is needed as long as
            // the total length of the set of commands does not exceed the 64 byte limit of the endpoint buffer.
            String draw = "D" +
                formatCoords(work.width - inset, work.y + inset) + "," +
                formatCoords(work.width - inset, work.height - inset) + "," +
                formatCoords(work.x + inset, work.height - inset) + "," +
                formatCoords(work.x + inset, work.y + inset);
            sendCmd(draw);
            doWait();
          }
        }
        // Used to try out experimental command sequences
        if (penTest.isSelected()) {
          // Tests how a moveTo() followed by a drawTo() at the same location results in a pen down/up
          appendLine("Do Pen Up/Down Test");
          for (int pen = 1; pen <= 2; pen++) {
            appendLine("  Use Pen " + pen);
            selectPen(pen);
            for (int ii = 0; ii < 2; ii++) {
              moveTo(1000, 1000);
              // By repeating command at same location we can increase pen down time.  Each draw() call
              // adds about 0.25 seconds to the down time.
              for (int jj = 0; jj < 10; jj++) {
                drawTo(1000, 1000);
              }
            }
          }
        }
        if (circleTest.isSelected()) {
        appendLine("Draw 4 inch diameter circle using Bezier curves");
        setDrawSpeed(10);
          /*
           *  Draw Bezier Circle using data generated by Silhouette Studio.  Note: the first and last lines,
           *  seem to be used to add lead in and lead out segments.  The actual circle is drawn by the 4 inner
           *  BZ (Bezier) commands.  I'm not sure why these extra segments are added, but perhaps it's needed
           *  to get a smooth start with the blade.  Curiously, when the sketch  pen is the selected tool,
           *  Silhouette Studio generates a ong series of draw commmands instead of using a bezier curve.
           *  I have no idea why it does this.
           *    sendCmd("BZ0,508.20,2011.90,508.06,2018.58,508,2025.28,508,2032,0");
           *    sendCmd("BZ1,508,2032,508,2592.62,963.38,3048,1524,3048,0");
           *    sendCmd("BZ1,1524,3048,2084.62,3048,2540,2592.62,2540,2032,0");
           *    sendCmd("BZ1,2540,2032,2540,1471.38,2084.62,1016,1524,1016,0");
           *    sendCmd("BZ1,1524,1016,963.38,1016,508,1471.38,508,2032,0");
           *    sendCmd("BZ1,508,2032,508,2038.72,508.06,2045.42,508.20,2052.10,0");
           *    doWait();
           */
          // This draws the same 4x4 inch circle (minus lead in/out) using the new bezier() function
          Point2D.Double[][] points = {
              {new Point2D.Double(2032.0, 508.0), new Point2D.Double(2592.62, 508.0),
                  new Point2D.Double(3048.0, 963.38), new Point2D.Double(3048.0, 1524.0)},
              {new Point2D.Double(3048.0, 1524.0), new Point2D.Double(3048.0, 2084.62),
                  new Point2D.Double(2592.62, 2540.0), new Point2D.Double(2032.0, 2540.0)},
              {new Point2D.Double(2032.0, 2540.0), new Point2D.Double(1471.38, 2540.0),
                  new Point2D.Double(1016.0, 2084.62), new Point2D.Double(1016.0, 1524.0)},
              {new Point2D.Double(1016.0, 1524.0), new Point2D.Double(1016.0, 963.38),
                  new Point2D.Double(1471.38, 508.0), new Point2D.Double(2032.0, 508.0)}};
          for (int ii = 0; ii < points.length; ii++) {
            bezier(points[ii], (ii != 0));
          }
          doWait();
        }
        appendLine("Return to Home Position");
        moveHome();
        appendLine("Done");
      }
    } catch (Exception ex) {
      appendLine(ex.getMessage());
      ex.printStackTrace();
    } finally {
      if (usb != null) {
        usb.close();
      }
    }
  }

  private SilhouetteTest () {
    super("SilhouetteTest");
    text.setColumns(50);
    text.setRows(30);
    text.setFont(new Font("Monaco", Font.PLAIN, 12));
    text.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JScrollPane scroll = new JScrollPane(text, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    add(scroll, BorderLayout.CENTER);
    JPanel controls = new JPanel(new FlowLayout());
    command = new JTextField();
    CardLayout cardLayout = new CardLayout();
    JPanel cards = new JPanel(cardLayout);
    JPanel options = new JPanel(new GridLayout(1, 5, 2, 2));
    options.add(moveTest = new JCheckBox("Move Test", true));
    options.add(drawTest = new JCheckBox("Draw Test", false));
    options.add(penTest = new JCheckBox("Pen Dwell", false));
    options.add(circleTest = new JCheckBox("Draw Circle", false));
    options.add(showCmds = new JCheckBox("Show I/O", false));
    cards.add(options);
    cards.add(command);
    controls.add(cards);
    controls.add(sendCmd = new JCheckBox("Snd Cmd", false));
    sendCmd.addChangeListener(ev -> {
      if (manCmd = sendCmd.isSelected()) {
        cardLayout.last(cards);
      } else {
        cardLayout.first(cards);
      }
    });
    select = new JComboBox<>(cutters.toArray(new Cutter[0]));
    controls.add(select);
    JButton run = new JButton("RUN");
    run.addActionListener(e -> startTests());
    command.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent ev) {
        if(ev.getKeyCode() == KeyEvent.VK_ENTER) {
          clearCmd = true;
          startTests();
        }
      }
    });
    controls.add(run);
    add(controls, BorderLayout.SOUTH);
    setLocationRelativeTo(null);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    pack();
    setVisible(true);
  }

  private void startTests () {
    Thread worker = new Thread(this::runTests);
    worker.start();
  }

  private void moveHome () {
    sendCmd("H");                                           // Move to Home Position
    doWait();
  }

  private String getVersionString() {
    sendCmd("FG");                                          // Query Version String
    byte[] rsp = usb.receive();
    return (new String(rsp)).substring(0, rsp.length - 1).trim();
  }

  /**
   * Sets the speed where the input value times 10 is centimeters/second
   * @param speed parameter range 1 - 10
   */
  private void setDrawSpeed (int speed) {
    speed = Math.min(Math.max(speed, 1), 10);               // Range is 1-10
    sendCmd("!" + speed);
  }

  /**
   * Set tool pressure (multiplied by 7 to get grams of force, or 7-230 grams)
   * @param pres parameter range 1 - 33
   */
  private void setPressure (int pres) {
    pres = Math.min(Math.max(pres, 1), 33);                 // Range is 1-33
    sendCmd("FX" + pres);
  }

  private void selectPen (int pen) {
    if (pen == 1 || pen == 2) {                             // 1 selects left pen, 2 selects right pen
      sendCmd("J" + pen);
    }
  }

  private void moveTo (Point2D.Double pnt) {
    moveTo(pnt.x, pnt.y);
  }

  /**
   * Move tool head to given position.
   * @param xLoc x position (in units)
   * @param yLoc y position (in units)
   */
  private void moveTo (double xLoc, double yLoc) {
    sendCmd("M" + formatCoords(xLoc, yLoc));
    doWait();
  }

  /**
   * Drop curretly selected tool at current position of tool head and then move to the position set by
   * the Point parameter.
   * @param pnt location to move to
   */
  private void drawTo (Point2D.Double pnt) {
    drawTo(pnt.x, pnt.y);
  }

  /**
   * Drop curretly selected tool at current position of tool head and then move to the position set by
   * the parameters xLoc and yLoc.
   * @param xLoc x position (in units)
   * @param yLoc y position (in units)
   */
  private void drawTo (double xLoc, double yLoc) {
    sendCmd("D" + formatCoords(xLoc, yLoc));
    doWait();
  }

  /**
   * Draw a 4 point Bezier curve to Silhouette device
   *  pnts[0] is starting point
   *  pnts[1] is first control point
   *  pnts[2] is second control points
   *  pnts[3] is ending point for curve (and the starting point for the next segment)
   * @param pnts 4 bezier (points start, cp1, cp3, end)
   * @param cont true if this curve continues from another curve segment or a line segment
   */
  private void bezier (Point2D.Double[] pnts, boolean cont) {
    sendCmd("BZ" + (cont ? "1" : "0") + "," +
    formatCoords(pnts[0]) + "," +
    formatCoords(pnts[1]) + "," +
    formatCoords(pnts[2]) + "," +
    formatCoords(pnts[3]));
  }

  /**
   * Takes 3 point, quadratic curve and returns 4 point cubic (Bezier) curve
   * @param quad 3 point, quadratic curve
   * @return 4 point cubic (Bezier) curve
   */
  private static Point2D.Double[] quadToCubic (Point2D.Double[] quad) {
    return new Point2D.Double[]  {
        new Point2D.Double(quad[0].x, quad[0].y),
        new Point2D.Double(quad[0].x + (2.0 * (quad[1].x - quad[0].x) / 3.0), quad[0].y + (2.0 * (quad[1].y - quad[0].y) / 3.0)),
        new Point2D.Double(quad[2].x + (2.0 * (quad[1].x - quad[2].x) / 3.0), quad[2].y + (2.0 * (quad[1].y - quad[2].y) / 3.0)),
        new Point2D.Double(quad[2].x, quad[2].y)
    };
  }

  /**
   * Convert the Point parameter into a comma separated coordinate pair and return this as a String
   * Note: this code reverses the X and Y axes to make the movement of the tool head the X axis
   * @param pnt coordinate pair.
   * @return String value of comma separated coordinate pair + end
   */
  private String formatCoords (Point2D.Double pnt) {
    return formatCoords(pnt.x, pnt.y);
  }

  /**
   * Convert the xLoc and yLoc parameters into a comma separated coordinate pair and return this as a String
   * Note: this code reverses the X and Y axes to make the movement of the tool head the X axis
   * @param xLoc x position (in units)
   * @param yLoc y position (in units)
   * @return String value of comma separated coordinate pair + end
   */
  private String formatCoords (double xLoc, double yLoc) {
    return df.format(yLoc) + "," + df.format(xLoc);
  }

  private Rectangle2D.Double getWorkArea () {
    sendCmd("[");
    String[] v1 = getResponse().split(",");
    double x = Double.parseDouble(v1[1].trim());
    double y = Double.parseDouble(v1[0].trim());
    sendCmd("U");
    String[] v2 = getResponse().split(",");
    double wid = Double.parseDouble(v2[1].trim());
    double hyt = Double.parseDouble(v2[0].trim());
    // Note: reverse X/Y axes so tool head moves on X axis
    return new Rectangle2D.Double(x, y, wid, hyt);
  }

  private void sendCmd (String cmd) {
    if (showCmds.isSelected() || sendCmd.isSelected()) {
      appendLine("Snd: \"" + cmd + "\"");
    }
    usb.send((cmd + "\u0003").getBytes());
  }

  private String getResponse () {
    byte[] data = usb.receive();
    if (data.length > 0) {
      String rsp = (new String(data)).substring(0, data.length - 1);
      if (showCmds.isSelected() || sendCmd.isSelected()) {
        appendLine("Rec: \"" + rsp + "\"");
      }
      return rsp;
    }
    return "";
  }

  private Rectangle2D.Double limitCutArea (Rectangle2D.Double work, double xInset, double yInset) {
    Rectangle2D.Double rect = new Rectangle2D.Double(xInset, yInset, work.width - xInset * 2, work.height - yInset * 2);
    sendCmd("\\" + formatCoords(rect.x, rect.y));
    sendCmd("Z" + formatCoords(rect.width, rect.height));
    return rect;
  }

  private void appendLine (String line) {
    text.append(line + "\n");
    text.setCaretPosition(text.getDocument().getLength());
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
    while (getStatus() == '1')
      ;
  }
}
