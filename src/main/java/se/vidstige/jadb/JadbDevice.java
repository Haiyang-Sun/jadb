package se.vidstige.jadb;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class JadbDevice {

    private final ITransportFactory transportFactory;
    private final String serial;

    public JadbDevice(ITransportFactory tFactory) {
        this(tFactory, null);
    }

    public JadbDevice(ITransportFactory tFactory, String serial) {
        this.serial = serial;
        this.transportFactory = tFactory;
    }

    public String getSerial() {
        return serial;
    }

    public State getState() throws IOException, JadbException {
        try (Transport transport = transportFactory.createTransport()) {
            transport.send(serial == null ? "host:get-state" : "host-serial:" + serial + ":get-state");
            transport.verifyResponse();
            return State.fromString(transport.readString());
        }
    }

    /**
     * Execute a shell command.
     *
     * @param command main command to run. E.g. "ls"
     * @param args    arguments to the command.
     * @return combined stdout/stderr stream.
     * @throws IOException
     * @throws JadbException
     */
    public InputStream executeShell(String command, String... args) throws IOException, JadbException {
        Transport transport = getTransport();
        StringBuilder shellLine = new StringBuilder(command);
        for (String arg : args) {
            shellLine.append(" ");
            shellLine.append(Bash.quote(arg));
        }
        send(transport, "shell:" + shellLine.toString());
        return new AdbFilterInputStream(new BufferedInputStream(transport.getInputStream()));
    }

    public List<RemoteFile> list(String remotePath) throws IOException, JadbException {
        Transport transport = getTransport();
        SyncTransport sync = transport.startSync();
        sync.send("LIST", remotePath);

        List<RemoteFile> result = new ArrayList<RemoteFile>();
        for (RemoteFileRecord dent = sync.readDirectoryEntry(); dent != RemoteFileRecord.DONE; dent = sync.readDirectoryEntry()) {
            result.add(dent);
        }
        transport.close();
        return result;
    }

    public void push(InputStream source, long lastModified, int mode, RemoteFile remote) throws IOException, JadbException {
        Transport transport = getTransport();
        SyncTransport sync = transport.startSync();
        sync.send("SEND", remote.getPath() + "," + Integer.toString(mode));

        sync.sendStream(source);

        sync.sendStatus("DONE", (int) lastModified);
        sync.verifyStatus();
    }

    public void push(File local, RemoteFile remote) throws IOException, JadbException {
        FileInputStream fileStream = new FileInputStream(local);
        push(fileStream, local.lastModified(), getMode(local), remote);
        fileStream.close();
    }

    public void pull(RemoteFile remote, OutputStream destination) throws IOException, JadbException {
        Transport transport = getTransport();
        SyncTransport sync = transport.startSync();
        sync.send("RECV", remote.getPath());
        sync.readChunksTo(destination);
    }

    public void pull(RemoteFile remote, File local) throws IOException, JadbException {
        FileOutputStream fileStream = new FileOutputStream(local);
        pull(remote, fileStream);
        fileStream.flush();
        fileStream.close();
    }

    private void send(Transport transport, String command) throws IOException, JadbException {
        transport.send(command);
        transport.verifyResponse();
    }

    private Transport getTransport() throws IOException, JadbException {
        Transport transport = transportFactory.createTransport();
        transport.send(serial == null ? "host:transport-any" : "host:transport:" + serial);
        transport.verifyResponse();
        return transport;
    }

    private int getMode(File file) {
        //noinspection OctalInteger
        return 0664;
    }

    @Override
    public String toString() {
        return "Android Device with serial " + serial;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JadbDevice)) return false;

        JadbDevice that = (JadbDevice) o;

        return serial != null ? serial.equals(that.serial) : that.serial == null;
    }

    @Override
    public int hashCode() {
        return serial != null ? serial.hashCode() : 0;
    }

    public enum State {
        UNKNOWN,
        OFFLINE,
        DEVICE,
        BOOTLOADER;

        public static State fromString(String state) {
            switch (state) {
                case "device":
                    return State.DEVICE;
                case "offline":
                    return State.OFFLINE;
                case "bootloader":
                    return State.BOOTLOADER;
                default:
                    return State.UNKNOWN;
            }
        }
    }
}
