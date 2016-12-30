package se.vidstige.jadb;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class JadbConnection implements ITransportFactory {

    private final String host;
    private final int port;

    private static final int DEFAULTPORT = 5037;

    public JadbConnection() {
        this("localhost", DEFAULTPORT);
    }

    public JadbConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Transport createTransport() throws IOException {
        return new Transport(new Socket(host, port));
    }

    public String getHostVersion() throws IOException, JadbException {
        try (Transport main = createTransport()) {
            main.send("host:version");
            main.verifyResponse();
            return main.readString();
        }
    }

    public List<JadbDevice> getDevices() throws IOException, JadbException {
        Transport devices = createTransport();
            devices.send("host:devices");
            devices.verifyResponse();
            List<JadbDevice> res = parseDevices(devices.readString());
            devices.close();
            return res;
    }

    public DeviceWatcher createDeviceWatcher(DeviceDetectionListener listener) throws IOException, JadbException {
        Transport transport = createTransport();
        transport.send("host:track-devices");
        transport.verifyResponse();
        return new DeviceWatcher(transport, listener, this);
    }

    public List<JadbDevice> parseDevices(String body) {
        String[] lines = body.split("\n");
        ArrayList<JadbDevice> devices = new ArrayList<JadbDevice>(lines.length);
        for (String line : lines) {
            String[] parts = line.split("\t");
            if (parts.length > 1) {
                devices.add(new JadbDevice(this, parts[0]));
            }
        }
        return devices;
    }

    /**
     * forward socket connections
     * <p>
     * forward specs are one of: <br>
     * tcp:[port] <br>
     * localabstract:[unix domain socket name] <br>
     * localreserved:[unix domain socket name] <br>
     * localfilesystem:[unix domain socket name] <br>
     * dev:[character device name] <br>
     * jdwp:[process pid] (remote only)
     *
     * @param local  address
     * @param remote address
     * @see <a href='https://developer.android.com/studio/command-line/adb.html#forwardports'>developer.android.com</a>
     */
    public void setForwarding(String local, String remote) throws IOException, JadbException {
        try (Transport transport = createTransport()) {
            transport.send("host:forward:" + local + ";" + remote);
            transport.verifyResponse();
        }
    }

    /**
     * Removes all socket forwardings
     */
    public void removeForwardings() throws IOException, JadbException {
        try (Transport transport = createTransport()) {
            transport.send("host:killforward-all");
            transport.verifyResponse();
        }
    }
}
