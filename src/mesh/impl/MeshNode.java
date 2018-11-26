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
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MeshNode {

    private ExecutorService executor;

    private MeshConfiguration config;

    private final Object searchLock;

    private AtomicInteger node_count;

    private Random id_generator;

    private MulticastPacketSocket multicastSocket;

    private RequestServer server;

    private Map<Integer, InetSocketAddress> nodes;

    private HashMap<Integer, MeshConfiguration> configs;

    private List<NodeConnectListener> nodeConnectListeners;

    public MeshNode(HashMap<Integer, MeshConfiguration> configs, InetSocketAddress multicastAddress, SocketAddress serverAddress) throws IOException {
        this(configs, new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 64, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>()), multicastAddress, serverAddress);
    }

    public MeshNode(HashMap<Integer, MeshConfiguration> configs, ExecutorService executor, InetSocketAddress multicastAddress, SocketAddress serverAddress) throws IOException {
        this.executor = executor;
        this.nodes = new ConcurrentHashMap<>();
        this.configs = configs == null ? new HashMap<>() : configs;
        this.multicastSocket = new MulticastPacketSocket(multicastAddress, executor);
        this.server = new RequestServer(this.executor, serverAddress);

        this.searchLock = new Object();

        this.multicastSocket.addHandler(PACKET_TYPE_NETWORK_QUERY, this::onNetQuery);
        this.multicastSocket.addHandler(PACKET_TYPE_NETWORK_INFO, this::onNetInfo);
        this.multicastSocket.addHandler(PACKET_TYPE_NET_JOIN, this::onNetJoin);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_CONFIG, this::onNodeConfig);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_ACTIVE, this::onNodeActive);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_GONE, this::onNodeGone);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_ADVERT, this::onNodeAdvert);

        this.config = new MeshConfiguration(-1, -2, -3, 0);
        this.node_count = new AtomicInteger(0);

        this.nodeConnectListeners = new LinkedList<>();

        this.executor.submit(this::search);
    }

    private void search() {
        // look for an existing mesh
        System.out.println("[MeshNode][search] Looking for existing mesh networks");

        long startTime = System.currentTimeMillis();

        do {
            // send a query for existing networks
            this.sendNetQuery();

            try {
                synchronized (this.searchLock) {
                    this.searchLock.wait(Constants.TIMEOUT_DELAY / 3);
                }

            } catch (InterruptedException e) {
                break;
            }

        } while (System.currentTimeMillis() - startTime < Constants.TIMEOUT_DELAY && this.config.getNetwork_id() < 0);

        System.out.println("[MeshNode][search] Search duration expired. NetworkId=" + this.config.getNetwork_id());

        if (this.config.getNetwork_id() < 0) {
            this.executor.submit(this::createNetwork);
        }
    }

    public void registerRequestHandler(String requestType, RequestHandler handler) {
        this.server.registerHandler(requestType, handler);
    }

    public void registerPacketHandler(String packetType, MulticastPacketSocket.PacketHandler handler) {
        this.multicastSocket.addHandler(packetType, handler);
    }

    public void addNodeConnectListener(NodeConnectListener listener) {
        this.nodeConnectListeners.add(listener);
    }

    public void removeNodeConnectListener(NodeConnectListener listener) {
        this.nodeConnectListeners.remove(listener);
    }

    public void broadcastPacket(DeferredJsonGenerator packet) {
        this.multicastSocket.send(packet);
    }

    public Set<Integer> getAvailableNodes() {
        return nodes.keySet();
    }

    public Socket tryConnect(int nodeId) throws NodeUnavailableException, SocketException, SocketTimeoutException {
        if (this.nodes.containsKey(nodeId)) {
            ClientSocket socket = new ClientSocket(this.nodes.get(nodeId));

            try {
                socket.connect();
                return socket;

            } catch (SocketTimeoutException e) {
                this.nodes.remove(nodeId);
                this.sendNodeGone(nodeId);

                throw e;
            }

        } else if (nodeId == this.getNodeId()) {
            ServerSocket serverSocket = this.server.getServerSocket();
            ClientSocket socket = new ClientSocket(serverSocket.localAddress(), serverSocket.getPort());
            socket.connect();
            return socket;

        } else {
            throw new NodeUnavailableException("Requested node is unavailable");
        }
    }

    public int getNodeId() {
        return this.config.getNodeId();
    }

    private void createNetwork() {
        for (MeshConfiguration config1 : this.configs.values()) {
            // if we have already created a network before:
            if (config1.getNodeId() == 0) {
                this.config = config1;
                break;
            }
        }

        if (this.config.getNetwork_id() < 0){
            this.config = new MeshConfiguration((int) System.nanoTime(), 0, 0, 1);
            System.out.println("[MeshNode][createNetwork] Created new network with NetworkId=" + this.config.getNetwork_id());

        } else {
            System.out.println("[MeshNode][createNetwork] Loading network with NetworkId=" + this.config.getNetwork_id());
        }

        this.configs.put(this.config.getNetwork_id(), this.config);
        this.node_count.set(config.getNodeCount());

        this.id_generator = new Random(this.config.getNetwork_id());
        for (int i = 0; i < this.node_count.get(); i++) this.id_generator.nextInt();
    }

    private void sendNetInfo() {
        System.out.println("[MeshNode][sendNetInfo] Sending NetInfo packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NETWORK_INFO);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NET_ID, this.config.getNetwork_id());
            gen.writeNumberField(MeshConfiguration.PROPERTY_MASTER_ID, this.config.getMasterId());
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_COUNT, this.node_count.get());
            gen.writeEndObject();
        });
    }

    private void sendNetQuery() {
        System.out.println("[MeshNode][sendNetQuery] Sending NetQuery packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NETWORK_QUERY);
            gen.writeEndObject();
        });
    }

    private void sendNetJoin() {
        System.out.println("[MeshNode][sendNetJoin] Sending NetJoin packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NET_JOIN);
            gen.writeEndObject();
        });
    }

    private void sendNodeConfig(InetAddress address) {
        System.out.println("[MeshNode][sendNodeConfig] Sending NodeConfig packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_CONFIG);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.id_generator.nextInt());
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_COUNT, this.node_count.incrementAndGet());
            gen.writeEndObject();
        }, address);
    }

    private void sendNodeActive() {
        System.out.println("[MeshNode][sendNodeActive] Sending NodeActive packet. NetworkId=" + this.config.getNetwork_id() + " node_id=" + this.config.getNodeId());
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ACTIVE);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.config.getNodeId());
            gen.writeEndObject();
        });
    }

    private void sendNodeGone(int node_id) {
        System.out.println("[MeshNode][sendNodeGone] Sending NodeGone packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_GONE);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, node_id);
            gen.writeEndObject();
        });
    }

    private void sendNodeAdvert(InetAddress dest) {
        System.out.println("[MeshNode][sendNodeAdvert] Sending NodeAdvert packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ADVERT);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.config.getNodeId());
            gen.writeNumberField(PROPERTY_PORT_NUMBER, this.server.getServerSocket().getPort());
            gen.writeEndObject();
        }, dest);
    }

    private void onNetInfo(JsonField.ObjectField packet, InetAddress address) {
        System.out.println("[MeshNode][onNetInfo] Received network info");
        if (this.config.getNetwork_id() < 0) {
            // if not part of a network, process received information
            int netId = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NET_ID);
            if (this.configs.containsKey(netId)) {
                // if have configuration for discovered network
                this.config = this.configs.get(netId);
                this.config.setMasterId(Math.min(this.config.getMasterId(), (int) packet.getLongProperty(MeshConfiguration.PROPERTY_MASTER_ID)));
                this.node_count.set(Math.max(config.getNodeCount(), (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT)));
                this.config.setNodeCount(this.node_count.get());
                System.out.println("[MeshNode][onNetInfo] Joining known mesh network");
                this.sendNodeActive();

            } else {
                // new network discovered
                this.config.setNetwork_id(netId);
                this.config.setMasterId((int) packet.getLongProperty(MeshConfiguration.PROPERTY_MASTER_ID));

                System.out.println("[MeshNode][onNetInfo] Joining newly discovered mesh network");

                // send request to join
                this.sendNetJoin();
            }

            synchronized (this.searchLock) {
                this.searchLock.notifyAll();
            }
        }
    }

    private void onNetQuery(JsonField.ObjectField packet, InetAddress address) {
        System.out.println("[MeshNode][onNetQuery] Received NetQuery packet");
        if (this.config.isMaster()) {
            // if master, reply to query
            this.sendNetInfo();
        }
    }

    private void onNetJoin(JsonField.ObjectField packet, InetAddress address) {
        System.out.println("[MeshNode][onNetJoin] Received net join request");
        if (this.config.isMaster()) {
            // if master, reply with node configuration
            this.sendNodeConfig(address);
        }
    }

    private void onNodeConfig(JsonField.ObjectField packet, InetAddress address) {
        System.out.println("[MeshNode][onNetConfig] Received node configuration");
        if (this.config.getNodeId() < 0) {
            // if not part of a network, use configuration
            this.config.setNodeId((int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID));
            this.node_count.set((int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT));
            this.config.setNodeCount(this.node_count.get());
            // add config to known configs so that it gets saved
            this.configs.put(this.config.getNetwork_id(), this.config);

            // announce activity
            this.sendNodeActive();
        }
    }

    private void onNodeActive(JsonField.ObjectField packet, InetAddress address) {
        System.out.println("[MeshNode][onNodeActive] Received NodeActive packet");
        // add advertised node to list of known nodes
        int id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);
        int port = (int) packet.getLongProperty(PROPERTY_PORT_NUMBER);
        boolean alreadyKnew = this.nodes.containsKey(id);
        this.nodes.put(id, new InetSocketAddress(address, port));
        if (this.node_count.get() < id) this.node_count.set(id);
        if (id < this.config.getMasterId()) this.config.setMasterId(id);

        // inform new node of our presence
        this.sendNodeAdvert(address);

        if (!alreadyKnew) this.onNewNode(id);
    }

    private void onNodeGone(JsonField.ObjectField packet, InetAddress address) {
        System.out.println("[MeshNode][onNodeGone] Received NodeGone packet");
        int id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);
        if (id == this.config.getNodeId()){
            // resend activity notification if someone said we didn't respond
            this.multicastSocket.send((gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ACTIVE);
                gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.config.getNodeId());
                gen.writeEndObject();
            });

        } else {
            // remove node from list of known nodes
            this.nodes.remove(id);

            // change master_id if necessary
            if (this.config.getMasterId() == id) {
                int min = this.config.getNodeId();
                for (int nid : this.getAvailableNodes()) min = Math.min(min, nid);
                this.config.setMasterId(min);
            }

            // prepare to be master if necessary
            if (this.config.isMaster()) {
                this.id_generator = new Random(this.config.getNetwork_id());
                for (int i = 0; i < node_count.get(); i++) this.id_generator.nextInt(Integer.MAX_VALUE);
            }
        }
    }

    private void onNodeAdvert(JsonField.ObjectField packet, InetAddress address) {
        System.out.println("[MeshNode][onNodeAdvert] Received NodeAdvert packet");
        int id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);
        int port = (int) packet.getLongProperty(PROPERTY_PORT_NUMBER);

        InetSocketAddress serverAddress = new InetSocketAddress(address, port);
        boolean alreadyKnew = this.nodes.containsKey(id);
        this.nodes.put(id, serverAddress);
        if (id < this.config.getMasterId()) this.config.setMasterId(id);

        if (!alreadyKnew) this.onNewNode(id);
    }

    private void onNewNode(int node_id) {
        for (NodeConnectListener listener : this.nodeConnectListeners) {
            listener.onNodeConnected(node_id);
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
    // sent in response to NODE_ACTIVE to inform new nodes of existing nodes
    public static final String PACKET_TYPE_NODE_ADVERT = "PACKET_NODE_ADVERT";

    // the port number of the node's RequestServer
    public static final String PROPERTY_PORT_NUMBER = "PROP_PORT_NUM";

    @FunctionalInterface
    public interface NodeConnectListener {
        void onNodeConnected(int node_id);
    }
}
