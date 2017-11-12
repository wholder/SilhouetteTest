import org.usb4java.*;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 *  Implements a bulk tranfer I/O driver that uses usb4java to communicate with a USB Device
 *  such as a Silhouette Curio, Cameo or Portrait using the Usb4Java Library.
 *
 *  See: http://usb4java.org, and http://usb4java.org/apidocs/index.html for more info
 */

class USBIO {
  private static final int  TIMEOUT = 500;
  private DeviceHandle      handle;
  private Context           context = new Context();
  private byte              iFace, outEnd, inEnd;
  private JTextArea         debug;

  USBIO (short vendorId, short productId, byte iFace, byte outEnd, byte inEnd) {
    this.iFace = iFace;
    this.outEnd = outEnd;
    this.inEnd = inEnd;
    int error = LibUsb.init(context);
    if (error != LibUsb.SUCCESS) {
      throw new LibUsbException("Unable to initialize libusb", error);
    }
    DeviceList list = new DeviceList();
    if ((error = LibUsb.getDeviceList(context, list)) < 0) {
      throw new LibUsbException("Unable to get device list", error);
    }
    for (Device device : list) {
      DeviceDescriptor desc = new DeviceDescriptor();
      LibUsb.getDeviceDescriptor(device, desc);
      if (desc.idVendor() == vendorId && desc.idProduct() == productId) {
        handle = new DeviceHandle();
        if ((error = LibUsb.open(device, handle)) >= 0) {
          if ((error = LibUsb.claimInterface(handle, iFace)) == LibUsb.SUCCESS) {
            return;
          } else {
            if (LibUsb.detachKernelDriver(handle, iFace) == LibUsb.SUCCESS) {
              if ((error = LibUsb.claimInterface(handle, iFace)) == LibUsb.SUCCESS) {
                return;
              }
              throw new LibUsbException("Unable to claim interface", error);
            }
          }
        }
      }
    }
    throw new LibUsbException("Unable to open device", error);
  }

  void setDebug (JTextArea debug) {
    this.debug = debug;
  }

  JTextArea getDebug () {
    return debug;
  }

  private void debugPrint (byte[] data) {
    StringBuilder buf = new StringBuilder();
    for (byte cc : data) {
      if (cc >= 0x20) {
        buf.append((char) cc);
      } else {
        buf.append("\\u00");
        buf.append(toHex(cc));
      }
    }
    debug.append(buf.toString() + "\n");
  }

  String toHex (int val) {
    if (val < 0x10) {
      return "0" + Integer.toHexString(val).toUpperCase();
    } else {
      return Integer.toHexString(val).toUpperCase();
    }
  }

  void send (byte[] data) {
    if (debug != null) {
      debugPrint(data);
    }
    ByteBuffer outBuf = BufferUtils.allocateByteBuffer(data.length);
    outBuf.put(data);
    IntBuffer outNum = IntBuffer.allocate(1);
    int error;
    if ((error = LibUsb.bulkTransfer(handle, outEnd, outBuf, outNum, TIMEOUT)) < 0) {
      throw new LibUsbException("Unable to send data", error);
    }
  }

  byte[] receive () {
    return receive(TIMEOUT);
  }

  byte[] receive (int timeout) {
    ByteBuffer inBuf = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
    IntBuffer inNum = IntBuffer.allocate(1);  // Used to get bytes read count
    if (LibUsb.bulkTransfer(handle, inEnd, inBuf, inNum, timeout) >= 0) {
      if (inBuf.hasArray()) {
        return inBuf.array();
      } else {
        int cnt = inNum.get(0);
        byte[] data = new byte[cnt];
        for (int ii = 0; ii < cnt; ii++) {
          data[ii] = inBuf.get();
        }
        inBuf.clear();
        return data;
      }
    }
    return new byte[0];
  }

  void close () {
    try {
      int error = LibUsb.releaseInterface(handle, iFace);
      if (error != LibUsb.SUCCESS) {
        throw new LibUsbException("Unable to release interface", error);
      }
    } finally {
      LibUsb.close(handle);
      LibUsb.exit(context);
    }
  }
}
