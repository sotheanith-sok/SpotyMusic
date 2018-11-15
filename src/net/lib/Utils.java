package net.lib;

import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

public class Utils {
    public static NetworkInterface getLoopbackInterface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback()) return iface;
        }

        return null;
    }

    public static NetworkInterface getNonLoopback() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isLoopback()) return iface;
        }

        return null;
    }

    public static SocketAddress getSocketAddress(int port) throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || iface.isVirtual()) continue;
            SocketAddress address = getSocketAddress(iface, port);
            if (address != null) return address;
        }

        return null;
    }

    public static SocketAddress getSocketAddress(NetworkInterface iface, int port) {
        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();
            if (address instanceof Inet6Address) continue;
            return new InetSocketAddress(address, port);
        }

        return null;
    }

    public static DatagramSocket getSocket() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            DatagramSocket socket = getSocket(iface);
            if (socket != null) return socket;
        }

        return null;
    }

    private static AtomicInteger port = new AtomicInteger(32123);

    public static DatagramSocket getSocket(NetworkInterface iface) throws SocketException {
        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        //if (!addresses.hasMoreElements()) System.err.println("[Utils][getSocket] No addresses on Non-loopback interface?");
        DatagramSocket socket = new DatagramSocket();
        while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();
            System.out.println("[Utils][getSocket] Attempting to bind address " + address + ":" + port.get());
            try {
                socket.bind(new InetSocketAddress(address, port.getAndIncrement()));
                return socket;

            } catch (SocketException e) {
                e.printStackTrace();
                // couldn't bind that address, try the next one
            }
        }

        return null;
    }

    public static DatagramSocket getSocket(InetAddress address) throws SocketException {
        return new DatagramSocket(new InetSocketAddress(address, 0));
    }

}
