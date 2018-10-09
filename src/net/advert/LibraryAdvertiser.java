package net.advert;

import com.fasterxml.jackson.core.*;
import connect.Library;
import javafx.collections.ObservableList;
import persistence.DataManager;
import persistence.User;
import utils.ObservableListImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

/**
 * The LibraryAdvertiser manages sending and receiving library advertisements in the local network.
 *
 * Libraries seen on the network are accessible via an {@link ObservableList} of {@link LibraryAdvertisement} objects.
 *
 * LibraryAdvertiser uses three background threads to handle advertising processes.
 * <ul>
 *     <li>
 *          The AdvertiserThread sends advertisements describing the current user's library at regular intervals,
 *          defined by {@link #ADVERTISEMENT_INTERVAL}.
 *      </li>
 *      <li>
 *          The ListenerThread listens for advertisements from other instances of the application.
 *      </li>
 *      <li>
 *          The CleanupThread removes LibraryAdvertisements from the list of advertisements when an advertisement
 *          becomes older than {@link #ADVERTISEMENT_TTL}.
 *      </li>
 * </ul>
 *
 * @author Nicholas Utz
 */
public class LibraryAdvertiser {

    /** A well-known multicast address for advertising */
    public static InetAddress ADVERT_ADDRESS;
    /** A well-known port number for advertising */
    public static final int ADVERT_PORT= 6500;
    /** Time-to-live value for received advertisements */
    public static final long ADVERTISEMENT_TTL = 2500;
    /** Interval at which advertisements are sent */
    public static final long ADVERTISEMENT_INTERVAL = 2000;
    /** Maximum size of an advertisement packet */
    private static final int MAX_PACKET_SIZE = 2048;

    private boolean run;

    private JsonFactory jsonFactory;

    private MulticastSocket soc;

    private Thread advertiserThread;
    private int libPort;
    private DatagramPacket advertisement;

    private Thread listenerThread;
    private ObservableList<LibraryAdvertisement> libraries;
    private HashMap<InetAddress, LibraryAdvertisement> adverts;

    private Thread cleanupThread;

    /**
     * Creates a new LibraryAdvertiser that advertises the current local library.
     *
     * @param libPort the port number used to connect to the local library
     */
    public LibraryAdvertiser(int libPort) {
        this.libraries = new ObservableListImpl<>();
        this.adverts = new HashMap<>();

        this.advertiserThread = new Thread(this::advert);
        this.listenerThread = new Thread(this::listen);
        this.cleanupThread = new Thread(this::cleanup);

        this.advertiserThread.setName("LibraryAdvertiserThread");
        this.listenerThread.setName("LibraryAdvertisementListenerThread");
        this.cleanupThread.setName("LibraryAdvertisementCleanupThread");
        this.advertiserThread.setDaemon(true);
        this.listenerThread.setDaemon(true);
        this.cleanupThread.setDaemon(true);

        this.libPort = libPort;
    }

    /**
     * Returns an {@link ObservableList} of {@link LibraryAdvertisement}s that represent libraries being advertised
     * on the network.
     *
     * @return list of libraries
     */
    public ObservableList<LibraryAdvertisement> getLibraryList() {
        return this.libraries;
    }

    /**
     * Starts the LibraryAdvertiser and its associated background threads.
     *
     * Initializes advertising multicast socket, creates advertisement packet for local library, and starts background
     * threads.
     */
    public void start() {
        this.run = true;

        try {
            this.soc = new MulticastSocket(ADVERT_PORT);
            this.soc.joinGroup(ADVERT_ADDRESS);

        } catch (IOException e) {
            System.err.println("[LibraryAdvertiser][start] IOException while creating advertisement socket");
            e.printStackTrace();
            this.run = false;
            return;
        }

        this.jsonFactory = new JsonFactory();
        User user = DataManager.getDataManager().getCurrentUser();
       Library localLib = null;
       try {
          localLib = DataManager.getDataManager().getCurrentLibrary().get();

       } catch (InterruptedException e) {
          e.printStackTrace();
       } catch (ExecutionException e) {
          e.printStackTrace();
       }

       // wrap in try/catch, to comply with throws, but don't handle exception because there won't be an exception.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(MAX_PACKET_SIZE);
        try {
            /*
             * create advertisement:
             * {
             *  "name": string,
             *  "port": int,
             *  "songs": int
             * }
             * address is assumed to be the address that the advertisement was sent from
             */
            JsonGenerator gen = this.jsonFactory.createGenerator(buffer, JsonEncoding.UTF8);
            gen.writeStartObject();
            gen.writeStringField("name", user.getUsername());
            gen.writeNumberField("port", this.libPort);
            gen.writeNumberField("songs", localLib.getSongs().size());
            gen.writeEndObject();
            gen.close();

        } catch (IOException e) {}

        // create packet for advertisement
        this.advertisement = new DatagramPacket(new byte[buffer.size()], buffer.size(), ADVERT_ADDRESS, ADVERT_PORT);
        this.advertisement.setData(buffer.toByteArray());

        this.advertiserThread.start();
        this.listenerThread.start();
        this.cleanupThread.start();
    }

    /**
     * Stops the LibraryAdvertiser and its associated background threads.
     */
    public void stop() {
        System.out.println("[LibraryAdvertiser][stop] Stopping LibraryAdvertiser");
        this.run = false;
        this.advertiserThread.interrupt();
        this.listenerThread.interrupt();
        this.cleanupThread.interrupt();
        this.soc.close();
    }

    /**
     * Main function of advertising thread.
     *
     * Sends advertisements for local library at regular intervals.
     */
    private void advert() {
        while(this.run) {
            try {
                this.soc.send(this.advertisement);

            } catch (IOException e) {
                System.err.println("[LibraryAdvertiser][advert] IOException while sending advertisement");
                e.printStackTrace();
            }

            try {
                Thread.sleep(ADVERTISEMENT_INTERVAL);
            } catch (InterruptedException e) {
                if (this.run) {
                    System.out.println("[LibraryAdvertiser][advert] AdvertiserThread interrupted unexpectedly");
                    e.printStackTrace();

                } else {
                    // thread interrupted because advertiser is being stopped
                }
            }
        }
    }

    /**
     * Main function of listener thread.
     *
     * Waits to receive advertisements and adds them to the list of known libraries, or resets their timeout counter.
     */
    private void listen() {
        DatagramPacket rec = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
        while (this.run) {
            try {
                this.soc.receive(rec);

            } catch (SocketTimeoutException e) {
                // if no one else is advertising, then we'll timeout a lot. Don't need to do anything with this exception
                // use continue to prevent trying to parse a packet that wasn't received
                continue;

            } catch (IOException e) {
                System.err.println("[LibraryAdvertiser][listen] IOException while receiving advertisement");
                e.printStackTrace();
                continue; // ?
            }

            // parse advertisement
            try {
                JsonParser parser = this.jsonFactory.createParser(rec.getData(), rec.getOffset(), rec.getLength());

                String name = null;
                int port = -1;
                int songs = -1;

                JsonToken token = parser.currentToken();
                while (token != JsonToken.END_OBJECT) {// if end object, end of packet
                    if (token == JsonToken.FIELD_NAME) {
                        String fieldName = parser.getCurrentName();
                        if (fieldName == "name") name = parser.nextTextValue();
                        else if (fieldName == "port") port = parser.nextIntValue(-1);
                        else if (fieldName == "songs") songs = parser.nextIntValue(-1);
                    }
                    token = parser.nextToken();
                }

                if (name == null | port == -1 | songs == -1) {
                    // not enough information to make advertisement
                    System.err.println("[LibraryAdvertiser][listen] Insufficient information to construct advertisement");
                    continue;
                }

                // create or refresh advertisement
                if (this.adverts.containsKey(rec.getAddress())) {
                    this.adverts.get(rec.getAddress()).setExpiresAt(System.currentTimeMillis() + ADVERTISEMENT_TTL);

                } else {
                    LibraryAdvertisement advert = new LibraryAdvertisement(name, rec.getAddress(), port, songs, System.currentTimeMillis() + ADVERTISEMENT_TTL);
                    this.adverts.put(advert.getLibraryAddress(), advert);

                }

            } catch (IOException e) {
                System.err.println("[LibraryAdvertiser][listen] IOException while parsing advertisement");
                e.printStackTrace();
            }

        }
    }

    /**
     * Main function of cleanup thread.
     *
     * Checks for expired advertisements at regular intervals.
     */
    private void cleanup() {
        while (this.run) {
            try {
                long now = System.currentTimeMillis();
                for (LibraryAdvertisement advert : this.libraries) {
                    if (advert.getExpiresAt() < now) {
                        this.libraries.remove(advert);
                        this.adverts.remove(advert.getLibraryAddress());
                    }
                }

                Thread.sleep(ADVERTISEMENT_TTL);

            } catch (InterruptedException e) {
                if (!this.run) break;
                System.err.println("[LibraryAdvertiser][cleanup] Cleanup thread unexpectedly interrupted");
                e.printStackTrace();
            }
        }
    }

    static {
        try {
            // 124 is completely random, in case you were wondering
            ADVERT_ADDRESS = InetAddress.getByAddress(new byte[]{(byte) 233, (byte) 253, 2, 124});
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
