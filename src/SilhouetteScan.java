import org.usb4java.*;

import javax.swing.*;

class SilhouetteScan {
  static void doScan (JTextArea text) {
    Context context = new Context();
    int result = LibUsb.init(context);
    if (result < 0) {
      throw new LibUsbException("Unable to initialize libusb", result);
    }
    DeviceList list = new DeviceList();
    result = LibUsb.getDeviceList(context, list);
    if (result < 0) {
      throw new LibUsbException("Unable to get device list", result);
    }
    try {
      boolean deviceFound = false;
      for (Device device : list) {
        int address = LibUsb.getDeviceAddress(device);
        int busNumber = LibUsb.getBusNumber(device);
        DeviceDescriptor descriptor = new DeviceDescriptor();
        result = LibUsb.getDeviceDescriptor(device, descriptor);
        byte numConfigs = descriptor.bNumConfigurations();
        if (result < 0) {
          throw new LibUsbException("Unable to read device descriptor", result);
        }
        String usbClass = DescriptorUtils.getUSBClassName(descriptor.bDeviceClass());
        short vendor = descriptor.idVendor();
        if (!"hub".equalsIgnoreCase(usbClass) && vendor == (short) 0x0B4D) {
          deviceFound = true;
          text.append(String.format("Bus: %03d Device %03d: Vendor %04x, Product %04x%n",
              busNumber, address, vendor, descriptor.idProduct()));
          for (byte ii = 0; ii < numConfigs; ii++) {
            ConfigDescriptor cDesc = new ConfigDescriptor();
            if (LibUsb.getConfigDescriptor(device, ii, cDesc) >= 0) {
              Interface[] ifaces = cDesc.iface();
              for (Interface iface : ifaces) {
                InterfaceDescriptor[] iDescs = iface.altsetting();
                for (InterfaceDescriptor iDesc : iDescs) {
                  byte iNum = iDesc.bInterfaceNumber();
                  byte numEndpoints = iDesc.bNumEndpoints();
                  if (numEndpoints > 0) {
                    text.append("  Interface: " + iNum + "\n");
                    EndpointDescriptor[] eDescs = iDesc.endpoint();
                    for (EndpointDescriptor eDesc : eDescs) {
                      byte endAdd = eDesc.bEndpointAddress();
                      byte eAttr = eDesc.bmAttributes();
                      int maxPkt = eDesc.wMaxPacketSize();
                      String[] tTypes = {"CON", "ISO", "BLK", "INT"};
                      String tType = tTypes[eAttr & 0x03];
                      String dir = (endAdd & 0x80) != 0 ? " (IN) " : " (OUT)";
                      text.append("    " + tType + " add: " + String.format("%02x", endAdd) + dir + " pkt: " + maxPkt + "\n");
                    }
                  }
                }
              }
            }
          }
        }
      }
      if (!deviceFound) {
        text.append("No Silhouette devices detected");
      }
    } catch (Exception ex) {
      text.append(ex.getMessage());
    } finally {
      LibUsb.freeDeviceList(list, true);
    }
    LibUsb.exit(context);
  }
}
