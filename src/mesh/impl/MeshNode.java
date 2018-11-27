package mesh.impl;

import net.Constants;
import net.common.DeferredJsonGenerator;
import net.common.JsonField;
import net.lib.ClientSocket;
import net.lib.ServerSocket;
import net.lib.Socket;
import net.reqres.RequestHandler;
import net.reqres.RequestServer;
import utils.Logger;

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

    private Logger logger;

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

        this.logger = new Logger("MeshNode", Constants.TRACE);
        this.searchLock = new Object();

        this.logger.info(" Server address: " + this.server.getServerSocket().localAddress() + ":" + this.server.getServerSocket().getPort());

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
        this.logger.log("[search] Looking for existing mesh networks");

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

        if (this.config.getNetwork_id() < 0) {
            this.logger.log("[search] No network found, creating new network");
            this.executor.submit(this::createNetwork);
        }
    }

    public void registerRequestHandler(String requestType, RequestHandler handler) {
        this.server.registerHandler(requestType, handler);
        this.logger.finer("[registerRequestHandler] Registered request handler for request type " + requestType);
    }

    public void registerPacketHandler(String packetType, MulticastPacketSocket.PacketHandler handler) {
        this.multicastSocket.addHandler(packetType, handler);
        this.logger.finer("[registerPacketHandler] Registered request handler for request type " + packetType);
    }

    public void addNodeConnectListener(NodeConnectListener listener) {
        this.nodeConnectListeners.add(listener);
        this.logger.finer("[addNodeConnectListener] Added new NodeConnectListener");
    }

    public void removeNodeConnectListener(NodeConnectListener listener) {
        this.nodeConnectListeners.remove(listener);
        this.logger.finer("[removeNodeConnectListener] Removed NodeConnectListener");
    }

    public void broadcastPacket(DeferredJsonGenerator packet) {
        this.multicastSocket.send(packet);
    }

    public Set<Integer> getAvailableNodes() {
        return nodes.keySet();
    }

    public Socket tryConnect(int nodeId) throws NodeUnavailableException, SocketException, SocketTimeoutException {
        this.logger.finer("[tryConnect] Attempting to connect to node " + nodeId);
        if (this.nodes.containsKey(nodeId)) {
            this.logger.debug("[tryConnect] Connecting to remote node...");
            ClientSocket socket = new ClientSocket(this.nodes.get(nodeId));

            try {
                socket.connect();
                this.logger.finest("[tryConnect] Connected to remote node successfully");
                return socket;

            } catch (SocketException e) {
                this.nodes.remove(nodeId);
                this.sendNodeGone(nodeId);
                this.logger.warn("[tryConnect] Connection timed out.");
                throw e;
            }

        } else if (nodeId == this.getNodeId()) {
            this.logger.debug("[tryConnect] Connecting to local server...");
            ServerSocket serverSocket = this.server.getServerSocket();
            ClientSocket socket = new ClientSocket(serverSocket.localAddress(), serverSocket.getPort());
            socket.connect();
            this.logger.finest("[tryConnect] Connected to local server successfully");
            return socket;

        } else {
            this.logger.warn("[tryConnect] The requested node is not active");
            throw new NodeUnavailableException("Requested node is unavailable");
        }
    }

    public int getNodeId() {
        return this.config.getNodeId();
    }

    private void createNetwork() {
        this.logger.info("[createNetwork] Establishing new network");
        for (MeshConfiguration config1 : this.configs.values()) {
            // if we have already created a network before:
            if (config1.getNodeId() == 0) {
                this.config = config1;
                break;
            }
        }

        if (this.config.getNetwork_id() < 0){
            this.config = new MeshConfiguration((int) System.nanoTime(), 0, 0, 1);
            this.logger.log("[createNetwork] Created new network with NetworkId=" + this.config.getNetwork_id());

        } else {
            this.logger.log("[createNetwork] Loading network with NetworkId=" + this.config.getNetwork_id());
        }

        this.configs.put(this.config.getNetwork_id(), this.config);
        this.node_count.set(config.getNodeCount());

        this.nodes.put(this.config.getNodeId(), new InetSocketAddress(this.server.getServerSocket().localAddress(), this.server.getServerSocket().getPort()));

        this.id_generator = new Random(this.config.getNetwork_id());
        for (int i = 0; i < this.node_count.get(); i++) this.id_generator.nextInt();
    }

    private void sendNetInfo() {
        this.logger.fine("[sendNetInfo] Sending NetInfo packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NETWORK_INFO);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NET_ID, this.config.getNetwork_id());
            gen.writeNumberField(MeshConfiguration.PROPERTY_MASTER_ID, this.config.getMasterId());
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_COUNT, this.node_count.get());
            gen.writeEndObject();
        });

        this.logger.debug("[sendNetInfo] NetInfo packet sent");
    }

    private void sendNetQuery() {
        this.logger.fine("[sendNetQuery] Sending NetQuery packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NETWORK_QUERY);
            gen.writeEndObject();
        });

        this.logger.debug("[sendNetQuery] NetQuery packet sent");
    }

    private void sendNetJoin() {
        this.logger.fine("[sendNetJoin] Sending NetJoin packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NET_JOIN);
            gen.writeEndObject();
        });

        this.logger.debug("[sendNetJoin] NetJoin packet sent");
    }

    private void sendNodeConfig(InetAddress address) {
        this.logger.fine("[sendNodeConfig] Sending NodeConfig packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_CONFIG);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.id_generator.nextInt());
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_COUNT, this.node_count.incrementAndGet());
            gen.writeEndObject();
        }, address);

        this.logger.debug("[sendNodeConfig] NodeConfig packet sent");
    }

    private void sendNodeActive() {
        this.logger.fine("[sendNodeActive] Sending NodeActive packet. NetworkId=" + this.config.getNetwork_id() + " node_id=" + this.config.getNodeId());
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ACTIVE);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.config.getNodeId());
            gen.writeNumberField(PROPERTY_PORT_NUMBER, this.server.getServerSocket().getPort());
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_COUNT, this.config.getNodeCount());
            gen.writeEndObject();
        });

        this.logger.debug("[sendNodeActive] NodeActive packet sent");
    }

    private void sendNodeGone(int node_id) {
        this.logger.fine("[sendNodeGone] Sending NodeGone packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_GONE);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, node_id);
            gen.writeEndObject();
        });

        this.logger.debug("[sendNodeGone] NodeGone packet sent");
    }

    private void sendNodeAdvert(InetAddress dest) {
        this.logger.fine("[sendNodeAdvert] Sending NodeAdvert packet to " + dest);
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NODE_ADVERT);
            gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.config.getNodeId());
            gen.writeNumberField(PROPERTY_PORT_NUMBER, this.server.getServerSocket().getPort());
            gen.writeEndObject();
        }, dest);

        this.logger.debug("[sendNodeAdvert] NodeAdvert packet sent");
    }

    private void onNetInfo(JsonField.ObjectField packet, InetAddress address) {
        this.logger.fine("[onNetInfo] Received network info");
        if (this.config.getNetwork_id() < 0) {
            // if not part of a network, process received information
            int netId = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NET_ID);
            if (this.configs.containsKey(netId)) {
                // if have configuration for discovered network
                this.config = this.configs.get(netId);
                this.config.setMasterId(Math.min(this.config.getMasterId(), (int) packet.getLongProperty(MeshConfiguration.PROPERTY_MASTER_ID)));
                this.node_count.set(Math.max(config.getNodeCount(), (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT)));
                this.config.setNodeCount(this.node_count.get());
                this.logger.info("[onNetInfo] Joining known mesh network");
                this.nodes.put(this.config.getNodeId(), new InetSocketAddress(this.server.getServerSocket().localAddress(), this.server.getServerSocket().getPort()));
                this.sendNodeActive();

            } else {
                // new network discovered
                this.config.setNetwork_id(netId);
                this.config.setMasterId((int) packet.getLongProperty(MeshConfiguration.PROPERTY_MASTER_ID));

                this.logger.info("[onNetInfo] Joining newly discovered mesh network");

                // send request to join
                this.sendNetJoin();
            }

            synchronized (this.searchLock) {
                this.searchLock.notifyAll();
            }
        }

        this.logger.debug("[onNetInfo] NetInfo packet handled");
    }

    private void onNetQuery(JsonField.ObjectField packet, InetAddress address) {
        this.logger.fine("[onNetQuery] Received NetQuery packet");
        if (this.config.isMaster()) {
            // if master, reply to query
            this.sendNetInfo();
        }

        this.logger.debug("[onNetQuery] NetQuery packet handled");
    }

    private void onNetJoin(JsonField.ObjectField packet, InetAddress address) {
        this.logger.fine("[onNetJoin] Received net join request");
        if (this.config.isMaster()) {
            // if master, reply with node configuration
            this.sendNodeConfig(address);
        }

        this.logger.debug("[onNetJoin] NetJoin packet handled");
    }

    private void onNodeConfig(JsonField.ObjectField packet, InetAddress address) {
        this.logger.log("[onNodeConfig] Received node configuration");
        if (this.config.getNodeId() < 0) {
            // if not part of a network, use configuration
            this.config.setNodeId((int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID));
            this.node_count.set((int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT));
            this.config.setNodeCount(this.node_count.get());
            // add config to known configs so that it gets saved
            this.configs.put(this.config.getNetwork_id(), this.config);

            this.nodes.put(this.config.getNodeId(), new InetSocketAddress(this.server.getServerSocket().localAddress(), this.server.getServerSocket().getPort()));

            // announce activity
            this.sendNodeActive();
        }

        this.logger.debug("[onNodeConfig] NodeConfig packet handled");
    }

    private void onNodeActive(JsonField.ObjectField packet, InetAddress address) {
        this.logger.fine("[onNodeActive] Received NodeActive packet");
        // add advertised node to list of known nodes

        this.logger.trace("[onNodeActive] Getting sender node id");
        int id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);

        this.logger.trace("[onNodeActive] Getting sender server port");
        int port = (int) packet.getLongProperty(PROPERTY_PORT_NUMBER);

        this.logger.trace("[onNodeActive] Getting sender node count");
        int count = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT);

        this.logger.trace("[onNodeActive] Checking if node is already known");
        boolean alreadyKnew = this.nodes.containsKey(id);

        this.logger.trace("[onNodeActive] Adding node to known nodes");
        this.nodes.put(id, new InetSocketAddress(address, port));

        if (this.node_count.get() < count) {
            this.logger.trace("[onNodeActive] Updating node count");
            this.node_count.set(count);
        }

        if (id < this.config.getMasterId()) {
            this.config.setMasterId(id);
            this.logger.trace("[onNodeActive] Updating master id");

        } else {
            this.logger.trace("[onNodeActive] New node id is not less than current master");
        }

        // inform new node of our presence
        this.logger.trace("[onNodeActive] Sending targeted NodeAdvert");
        this.sendNodeAdvert(address);

        if (!alreadyKnew) {
            this.logger.trace("[onNodeActive] Notifying NodeConnectListeners");
            this.onNewNode(id);
        }
        this.logger.debug("[onNodeActive] NodeActive packet handled");
    }

    private void onNodeGone(JsonField.ObjectField packet, InetAddress address) {
        this.logger.log("[onNodeGone] Received NodeGone packet");
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

        this.logger.debug("[onNodeGone] NodeGone packet handled");
    }

    private void onNodeAdvert(JsonField.ObjectField packet, InetAddress address) {
        this.logger.fine("[onNodeAdvert] Received NodeAdvert packet");
        int id = (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID);
        int port = (int) packet.getLongProperty(PROPERTY_PORT_NUMBER);

        InetSocketAddress serverAddress = new InetSocketAddress(address, port);
        boolean alreadyKnew = this.nodes.containsKey(id);
        this.nodes.put(id, serverAddress);
        if (id < this.config.getMasterId()) this.config.setMasterId(id);

        if (!alreadyKnew) this.onNewNode(id);
        this.logger.debug("[onNodeAdvert] NodeAdvert packet handled");
    }

    private void onNewNode(int node_id) {
        this.logger.finer("[onNewNode] New node connected: " + node_id);
        this.logger.finest("[onNewNode] There are now " + this.nodes.size() + " active nodes");
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
