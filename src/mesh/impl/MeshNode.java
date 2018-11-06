package mesh.impl;

import net.Constants;
import net.common.DeferredJsonGenerator;
import net.common.JsonField;
import net.lib.ClientSocket;
import net.lib.ServerSocket;
import net.lib.Socket;
import net.reqres.RequestHandler;
import net.reqres.RequestServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MeshNode {

    private ExecutorService executor;

    private int network_id;
    private int master_id;
    private int node_id;

    private AtomicInteger node_count;

    private Random id_generator;

    private MulticastPacketSocket multicastSocket;

    private RequestServer server;

    private Map<Integer, InetAddress> nodes;

    private HashMap<Integer, MeshConfiguration> configs;

    public MeshNode(HashMap<Integer, MeshConfiguration> configs, InetAddress multicastAddress) throws IOException {
        this(configs, new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 64, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>()), multicastAddress);
    }

    public MeshNode(HashMap<Integer, MeshConfiguration> configs, ExecutorService executor, InetAddress multicastAddress) throws IOException {
        this.executor = executor;
        this.nodes = new ConcurrentHashMap<>();
        this.configs = configs == null ? new HashMap<>() : configs;
        this.multicastSocket = new MulticastPacketSocket(multicastAddress, executor);
        this.server = new RequestServer(this.executor, 12321);

        this.multicastSocket.addHandler(PACKET_TYPE_NETWORK_QUERY, this::onNetQuery);
        this.multicastSocket.addHandler(PACKET_TYPE_NETWORK_INFO, this::onNetInfo);
        this.multicastSocket.addHandler(PACKET_TYPE_NET_JOIN, this::onNetJoin);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_CONFIG, this::onNodeConfig);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_ACTIVE, this::onNodeActive);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_GONE, this::onNodeGone);

        this.network_id = -1;
        this.master_id = -2;
        this.node_id = -3;
        this.node_count = new AtomicInteger(0);

        this.executor.submit(this::search);
    }

    private void search() {
        // look for an existing mesh
        System.out.println("[MeshNode][init] Looking for existing mesh networks");

        // send a query for existing networks
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NETWORK_QUERY);
            gen.writeEndObject();
        });

        try {
            Thread.sleep(Constants.TIMEOUT_DELAY);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (this.network_id < 0) {
            this.executor.submit(this::createNetwork);
        }
    }

    public void registerRequestHandler(String requestType, RequestHandler handler) {
        this.server.registerHandler(requestType, handler);
    }

    public void registerPacketHandler(String packetType, MulticastPacketSocket.PacketHandler handler) {
        this.multicastSocket.addHandler(packetType, handler);
    }

    public void sendMulticastPacket(DeferredJsonGenerator packet) {
        this.multicastSocket.send(packet);
    }

    public Set<Integer> getAvailableNodes() {
        return nodes.keySet();
    }

    public Socket tryConnect(int nodeId) throws NodeUnavailableException, SocketException, SocketTimeoutException {
        if (this.nodes.containsKey(nodeId)) {
            ClientSocket socket = new ClientSocket(this.nodes.get(nodeId), 12321);

            try {
                socket.connect();
                return socket;

            } catch (SocketException e) {
                throw e;

            } catch (SocketTimeoutException e) {
                this.nodes.remove(nodeId);
                this.multicastSocket.send((gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_GONE);
                    gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, nodeId);
                    gen.writeEndObject();
                });

                throw e;
            }

        } else if (nodeId == this.node_id) {
            ServerSocket serverSocket = this.server.getServerSocket();
            ClientSocket socket = new ClientSocket(serverSocket.localAddress(), serverSocket.getPort());
            socket.connect();
            return socket;

        } else {
            throw new NodeUnavailableException("Requested node is unavailable");
        }
    }

    public int getNodeId() {
        return this.node_id;
    }

    private void createNetwork() {
        System.out.println("[MeshNode][createNetwork] Creating mesh network");
        MeshConfiguration config = null;
        for (MeshConfiguration config1 : this.configs.values()) {
            // if we have already created a network before:
            if (config1.node_id == 0) {
                config = config1;
                break;
            }
        }

        if (config == null){
            config = new MeshConfiguration((int) System.nanoTime(), 0, 0, 0);
            this.configs.put(config.network_id, config);
            // TODO: save configs
        }

        this.network_id = config.network_id;
        this.master_id = config.network_master;
        this.node_id = config.node_id;
        this.node_count.set(config.node_count);

        this.id_generator = new Random(this.network_id);
        for (int i = 0; i < this.node_count.get(); i++) this.id_generator.nextInt(Integer.MAX_VALUE);
    }

    private void onNetInfo(JsonField.ObjectField packet, InetAddress address) {
        if (this.network_id < 0) {
            // if not part of a network, process received information
            int netId = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NET_ID);
            if (this.configs.containsKey(netId)) {
                // if have configuration for discovered network
                MeshConfiguration config = this.configs.get(netId);
                this.network_id = config.network_id;
                this.node_id = config.node_id;
                this.master_id = Math.min(this.node_id, (int) packet.getLongProperty(MeshConfiguration.PROPERTY_MASTER_ID));
                this.node_count.set(Math.max(config.node_count, (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT)));
                System.out.println("[MeshNode][onNetInfo] Joining known mesh network");
                this.multicastSocket.send((gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ACTIVE);
                    gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.node_id);
                    gen.writeEndObject();
                });

            } else {
                // new network discovered
                this.network_id = netId;
                this.master_id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_MASTER_ID);

                System.out.println("[MeshNode][onNetInfo] Joining newly discovered mesh network");

                // send request to join
                this.multicastSocket.send((gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NET_JOIN);
                    gen.writeEndObject();
                });
            }
        }
    }

    private void onNetQuery(JsonField.ObjectField packet, InetAddress address) {
        if (this.node_id == this.master_id) {
            // if master, reply to query
            this.multicastSocket.send((gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NETWORK_INFO);
                gen.writeNumberField(MeshConfiguration.PROPERTY_NET_ID, this.network_id);
                gen.writeNumberField(MeshConfiguration.PROPERTY_MASTER_ID, this.master_id);
                gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_COUNT, this.node_count.get());
                gen.writeEndObject();
            });
        }
    }

    private void onNetJoin(JsonField.ObjectField packet, InetAddress address) {
        if (this.node_id == this.master_id) {
            // if master, reply with node configuration
            this.multicastSocket.send((gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_CONFIG);
                gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.id_generator.nextInt(Integer.MAX_VALUE));
                gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_COUNT, this.node_count.incrementAndGet());
                gen.writeEndObject();
            });
        }
    }

    private void onNodeConfig(JsonField.ObjectField packet, InetAddress address) {
        if (this.node_id < 0) {
            // if not part of a network, use configuration
            this.node_id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);
            this.node_count.set((int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT));
            MeshConfiguration config = new MeshConfiguration(this.network_id, this.master_id, this.node_id, this.node_count.get());
            this.configs.put(config.network_id, config);
            // TODO: save configs

            // announce activity
            this.multicastSocket.send((gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ACTIVE);
                gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.node_id);
                gen.writeEndObject();
            });
        }
    }

    private void onNodeActive(JsonField.ObjectField packet, InetAddress address) {
        // add advertised node to list of known nodes
        int id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);
        this.nodes.put(id, address);
        if (this.node_count.get() < id) this.node_count.set(id);
        if (id < this.master_id) this.master_id = id;
    }

    private void onNodeGone(JsonField.ObjectField packet, InetAddress address) {
        int id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);
        if (id == this.node_id){
            // resend activity notification if someone said we didn't respond
            this.multicastSocket.send((gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ACTIVE);
                gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.node_id);
                gen.writeEndObject();
            });

        } else {
            // remove node from list of known nodes
            this.nodes.remove(id);

            // change master_id if necessary
            if (id == this.master_id) {
                int min = this.node_id;
                for (int nid : this.getAvailableNodes()) min = Math.min(min, nid);
            }

            // prepare to be master if necessary
            if (this.master_id == this.node_id) {
                this.id_generator = new Random(this.network_id);
                for (int i = 0; i < node_count.get(); i++) this.id_generator.nextInt(Integer.MAX_VALUE);
            }
        }
    }

    /*
     * Sent by master to respond to net query
     * properties: network_id, master_id, node_count
     */
    public static final String PACKET_TYPE_NETWORK_INFO = "PACKET_NET_CONFIG";
    // sent to query for existing networks
    public static final String PACKET_TYPE_NETWORK_QUERY = "PACKET_NET_QUERY";
    // sent to request node configuration
    public static final String PACKET_TYPE_NET_JOIN = "PACKET_NET_JOIN";
    // sent by master to configure new nodes
    public static final String PACKET_TYPE_NODE_CONFIG = "PACKET_NODE_CONFIG";
    // sent by new nodes to indicate that they are now part of the network
    public static final String PACKET_TYPE_NODE_ACTIVE = "PACKET_NODE_ACTIVE";
    // sent when a node is unable to connect to another node, so that all nodes know that that node is down
    public static final String PACKET_TYPE_NODE_GONE= "PACKET_NODE_GONE";
}
