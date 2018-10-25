package net.connect;

import java.net.*;
import java.util.Enumeration;

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
            if (iface.isLoopback()) continue;
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                return new InetSocketAddress(addresses.nextElement(), port);
            }
        }

        return null;
    }
}
