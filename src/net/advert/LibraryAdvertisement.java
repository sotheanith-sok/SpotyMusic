package net.advert;

import java.net.InetAddress;

/**
 * Stores advertisement data for libraries on the local network.
 *
 * @author Nicholas Utz
 */
public class LibraryAdvertisement {

    private String name;
    private InetAddress address;
    private int port;
    private int songCount;

    private long expiresAt;

    /**
     * Creates a new LibraryAdvertisement with the given library details.
     *
     * @param name the name of the advertised library
     * @param address the address from which the library was received
     * @param port the port number advertised for connecting to the library
     * @param songs the number of songs in the library
     * @param expiresAt when the advertisement expires
     */
    protected LibraryAdvertisement(String name, InetAddress address, int port, int songs, long expiresAt) {
        this.name = name;
        this.address = address;
        this.port = port;
        this.songCount = songs;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the name of the advertised library, that is the name of the user signed in on the remote machine.
     *
     * @return name of advertised library.
     */
    public String getLibraryName() {
        return this.name;
    }

    /**
     * Returns the address of the advertised library. It is assumed that the library can be accessed at the same address
     * from which the advertisement was received.
     *
     * @return library address
     */
    public InetAddress getLibraryAddress() {
        return this.address;
    }

    /**
     * Returns the port number to use when connecting to the remote library. This port number is part of the library
     * advertisement.
     *
     * @return library port number
     */
    public int getLibraryPort() {
        return this.port;
    }

    /**
     * Returns the number of songs that the library advertises.
     *
     * @return library song count
     */
    public int getSongCount() {
        return this.songCount;
    }

    /**
     * Returns the time (in millis) at which the latest advertisement expires.
     *
     * @return expiration time
     */
    protected long getExpiresAt() {
        return this.expiresAt;
    }

    /**
     * Sets the time at which this advertisement expires.
     *
     * @param expiresAt time of expiration
     */
    protected synchronized void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
