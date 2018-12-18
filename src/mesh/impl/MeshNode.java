package mesh.impl;

import net.Constants;
import net.common.DeferredJsonGenerator;
import net.common.DeferredStreamJsonGenerator;
import net.common.JsonField;
import net.lib.ClientSocket;
import net.lib.ServerSocket;
import net.lib.Socket;
import net.reqres.RequestHandler;
import net.reqres.RequestServer;
import net.reqres.Socketplexer;
import utils.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MeshNode {

    private ExecutorService executor;

    private MeshConfiguration config = null;

    private final Object searchLock;

    private AtomicInteger node_count;

    private Logger logger;

    private MulticastPacketSocket multicastSocket;

    private RequestServer server;

    private Map<Integer, InetSocketAddress> nodes;
    private PriorityQueue<Integer> nodeIds;

    private List<NodeConnectListener> nodeConnectListeners;

    private int monitorTarget = 0;
    private Socket monitorConnection = null;

    public MeshNode(MeshConfiguration config, InetSocketAddress multicastAddress, SocketAddress serverAddress) throws IOException {
        this(config, new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 64, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>()), multicastAddress, serverAddress);
    }

    public MeshNode(MeshConfiguration config, ExecutorService executor, InetSocketAddress multicastAddress, SocketAddress serverAddress) throws IOException {
        this.executor = executor;
        this.nodes = new ConcurrentHashMap<>();
        this.nodeIds = new PriorityQueue<>();
        this.multicastSocket = new MulticastPacketSocket(multicastAddress, executor);
        this.server = new RequestServer(this.executor, serverAddress);

        this.logger = new Logger("MeshNode", Constants.DEBUG);
        this.searchLock = new Object();

        this.logger.info(" Server address: " + this.server.getServerSocket().localAddress() + ":" + this.server.getServerSocket().getPort());

        this.multicastSocket.addHandler(PACKET_TYPE_NETWORK_QUERY, this::onNetQuery);
        this.multicastSocket.addHandler(PACKET_TYPE_NETWORK_INFO, this::onNetInfo);
        //this.multicastSocket.addHandler(PACKET_TYPE_NET_JOIN, this::onNetJoin);
        //this.multicastSocket.addHandler(PACKET_TYPE_NODE_CONFIG, this::onNodeConfig);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_ACTIVE, this::onNodeActive);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_GONE, this::onNodeGone);
        this.multicastSocket.addHandler(PACKET_TYPE_NODE_ADVERT, this::onNodeAdvert);

        this.server.registerHandler(REQUEST_MONITOR, this::monitorHandler);

        this.node_count = new AtomicInteger(0);

        this.config = config;

        this.nodeConnectListeners = new LinkedList<>();
    }

    public void connect() {
        this.executor.submit(this::init);
    }

    private void init() {
        Random id_generator = new Random(System.currentTimeMillis());

        if (this.config.getNodeCount() < 0){
            int id = id_generator.nextInt();
            this.config.setMasterId(id);
            this.config.setNodeId(id);
            this.config.setNodeCount(1);
            this.logger.log("[init] Generated new node id: " + this.config.getNodeId());
        }

        this.node_count.set(config.getNodeCount());
        this.nodes.put(this.config.getNodeId(), new InetSocketAddress(this.server.getServerSocket().localAddress(), this.server.getServerSocket().getPort()));
        this.nodeIds.add(this.config.getNodeId());

        // look for an existing mesh
        this.logger.log("[init] Looking for existing mesh networks");

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

        } while (System.currentTimeMillis() - startTime < Constants.TIMEOUT_DELAY && this.config.getNodeCount() < 0);

        this.sendNodeActive();
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

    public PriorityQueue<Integer> getAvailableNodes() {
        return this.nodeIds;
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
                this.nodeIds.remove(nodeId);
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

    public boolean isMaster() {
        return this.config.isMaster();
    }

    public int getNextNode() {
        Iterator<Integer> iter = this.nodeIds.iterator();
        int nextNode = iter.next();
        int me = this.config.getNodeId();
        // nodeIds is sorted, so the first ID that's greater than me
        // is guaranteed to be the next node
        // if I am the greatest, then nextNode will never be
        // overwritten, and the lowest active node ID will be returned
        while (iter.hasNext()) {
            int temp = iter.next();
            if (temp > me) {
                nextNode = temp;
                break;
            }
        }

        return nextNode;
    }

    public int getPreviousNode() {
        Iterator<Integer> iter = this.nodeIds.iterator();
        int prev = iter.next();
        int t = 0;

        int me = this.config.getNodeId();

        while (iter.hasNext()) {
            t = iter.next();
            if (t > prev && t < me) {
                prev = t;
            }
        }

        // after iterating over all nodes, if prev > me, then me is
        // lowest node, so return t which will be the highest node
        if (prev > me) return t;
        // otherwise return the largest id that's less than me
        return prev;
    }

    public int getBestId(int block_id) {
        // find node with best matching id number
        Iterator<Integer> iter = this.nodeIds.iterator();
        int node_id = iter.next();
        while (iter.hasNext()) {
            int next = iter.next();
            if (next > block_id) {
                break;

            } else {
                node_id = next;
            }
        }

        return node_id;
    }

    private void sendNetInfo() {
        this.logger.fine("[sendNetInfo] Sending NetInfo packet");
        this.multicastSocket.send((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, PACKET_TYPE_NETWORK_INFO);
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

    private void sendNodeActive() {
        this.logger.fine("[sendNodeActive] Sending NodeActive packet. node_id=" + this.config.getNodeId());
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

        this.config.setMasterId(Math.min(this.config.getMasterId(), (int) packet.getLongProperty(MeshConfiguration.PROPERTY_MASTER_ID)));
        this.node_count.set(Math.max(config.getNodeCount(), (int) packet.getLongProperty(MeshConfiguration.PROPERTY_NODE_COUNT)));
        this.config.setNodeCount(this.node_count.get());

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
        if (!alreadyKnew) this.nodeIds.add(id);

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
            this.sendNodeActive();

        } else {
            this.nodeDisconnected(id);
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
        if (!alreadyKnew) this.nodeIds.add(id);
        if (id < this.config.getMasterId()) this.config.setMasterId(id);

        if (!alreadyKnew) this.onNewNode(id);
        this.logger.debug("[onNodeAdvert] NodeAdvert packet handled");
    }

    private void onNewNode(int node_id) {
        this.logger.finer("[onNewNode] New node connected: " + node_id);
        this.logger.finest("[onNewNode] There are now " + this.nodes.size() + " active nodes");
        if (node_id < this.config.getMasterId()) this.config.setMasterId(node_id);

        for (NodeConnectListener listener : this.nodeConnectListeners) {
            listener.onNodeConnected(node_id);
        }

        if (this.monitorTarget == 0 || node_id < this.monitorTarget) {
            this.monitor(node_id);
        }
    }

    private void nodeDisconnected(int node_id) {
        // remove node from list of known nodes
        this.nodes.remove(node_id);
        this.nodeIds.remove(node_id);

        // change master_id if necessary
        if (this.config.getMasterId() == node_id) {
            int min = this.config.getNodeId();
            for (int nid : this.getAvailableNodes()) min = Math.min(min, nid);
            this.config.setMasterId(min);
        }
    }

    private void monitor(int target) {
        this.executor.submit(() -> {
            if (this.monitorConnection != null) {
                this.logger.fine("[monitor] Closing monitoring link to " + this.monitorTarget);
                this.monitorConnection.close();
            }
            this.monitorTarget = target;

            try {
                this.logger.finer("[monitor] Establishing monitoring link with node " + target);
                this.monitorConnection = this.tryConnect(target);
                this.logger.trace("[monitor] Monitoring connection established");

            } catch (NodeUnavailableException | SocketException | SocketTimeoutException e) {
                this.logger.warn("[monitor] There was a problem setting up monitoring for node " + target);
                e.printStackTrace();
            }

            Socketplexer socketplexer = new Socketplexer(this.monitorConnection, this.executor);

            this.logger.finer("[monitor] Sending monitor request header");
            try {
                this.logger.debug("[monitor] Getting request header output stream");
                OutputStream headersOut = socketplexer.openOutputChannel(1);
                this.logger.trace("[monitor] Got header output stream");

                this.logger.debug("[monitor] Writing monitor request headers");
                (new DeferredStreamJsonGenerator(headersOut, false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_MONITOR);
                    gen.writeNumberField(MeshConfiguration.PROPERTY_NODE_ID, this.config.getNodeId());
                    gen.writeEndObject();
                })).run();
                this.logger.trace("[monitor] Request headers written");

            } catch (IOException e) {
                e.printStackTrace();
            }

            this.monitorConnection.addTimeoutListener(this::monitorDisconnect);
            this.logger.fine("[monitor] Monitoring connection to " + target);
        });
    }

    private void monitorDisconnect() {
        this.logger.info("[monitorDisconnect] Monitored node " + monitorTarget + " stopped responding");
        this.sendNodeGone(this.monitorTarget);
        this.nodeDisconnected(this.monitorTarget);
        if (this.nodes.size() > 1) {
            this.monitor(this.getNextNode());
        }
    }

    private void monitorHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.logger.log("[monitorHandler] Handling monitor request from node " + request.getLongProperty(MeshConfiguration.PROPERTY_NODE_ID));
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

    public static final String REQUEST_MONITOR = "REQ_MONITOR";

    @FunctionalInterface
    public interface NodeConnectListener {
        void onNodeConnected(int node_id);
    }
}
