package mesh;

import connect.Library;
import mesh.dfs.DFS;
import mesh.impl.MeshConfiguration;
import mesh.impl.MeshNode;
import mesh.library.MeshLibrary;
import net.common.JsonField;
import net.common.JsonStreamParser;
import net.lib.Utils;
import persistence.ObservableMap;
import persistence.ObservableMapSerializer;

import java.io.*;
import java.net.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MeshSystem {

    public static final File root = new File("SpotyMusic/");
    public static final File meshConfigs = new File("SpotyMusic/nets.json");

    private static MeshSystem instance = null;

    private ObservableMap<Integer, MeshConfiguration> configs;

    private MeshNode node;

    private DFS dfs;

    private MeshLibrary library;

    private ScheduledThreadPoolExecutor executor;

    private MeshSystem() {
        this.configs = new ObservableMap<>();
        this.executor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    }

    public static MeshSystem getInstance() {
        if (instance == null) {
            instance = new MeshSystem();
        }

        return instance;
    }

    public void init() {
        if (!root.exists()) root.mkdirs();

        // load mesh configurations
        try {
            if (meshConfigs.exists()) {
                InputStream in = new BufferedInputStream(new FileInputStream(meshConfigs));
                JsonStreamParser parser = new JsonStreamParser(in, true, (field) -> {
                    if (!field.isObject()) return;

                    JsonField.ObjectField configs = (JsonField.ObjectField) field;
                    for (JsonField config : configs.getProperties().values()) {
                        MeshConfiguration inst = MeshConfiguration.fromJson((JsonField.ObjectField) config);
                        this.configs.put(inst.getNetwork_id(), inst);
                        System.out.println("[MeshSystem][init] Loaded mesh configuration for network " + inst.getNetwork_id());
                    }

                });
                parser.run();

            } else {
                meshConfigs.createNewFile();
                System.out.println("[MeshSystem][init] No configurations file detected");
            }

        } catch (FileNotFoundException e) {
            System.err.println("[MeshSystem][init] There was a problem opening the mesh config file");
            e.printStackTrace();
            return;

        } catch (IOException e) {
            System.err.println("[MeshSystem][init] There was a problem parsing the mesh config file");
            e.printStackTrace();
            return;
        }

        new ObservableMapSerializer<>(this.configs, meshConfigs, MeshConfiguration::serialize, this.executor, 1500, TimeUnit.MILLISECONDS);

        InetSocketAddress multicastAddress;
        try {
            multicastAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 233, (byte) 253, 2, 124}), 12321);

        } catch (UnknownHostException e) {
            System.err.println("[MeshSystem][init] Unable to bind multicast socket address");
            e.printStackTrace();
            return;
        }

        SocketAddress serverAddress;
        try {
            serverAddress = Utils.getSocketAddress(12324);

        } catch (SocketException e) {
            System.err.println("[MeshSystem][init] Unable to bind server socket address");
            e.printStackTrace();
            return;
        }

        System.out.println("[MeshSystem][init] Initializing MeshNode...");
        try {
            this.node = new MeshNode(this.configs, this.executor, multicastAddress, serverAddress);
            System.out.println("[MeshSystem][init] MeshNode initialized");

        } catch (IOException e) {
            System.err.println("[MeshSystem][init] IOException while initializing MeshNode");
            e.printStackTrace();
            return;
        }

        System.out.println("[MeshSystem][init] Initializing DFS...");
        try {
            this.dfs = new DFS(this.node, this.executor);
            this.dfs.init();
            System.out.println("[MeshSystem][init] DFS initialized");

        } catch (Exception e) {
            System.err.println("[MeshSystem][init] Exception while initializing DFS");
            e.printStackTrace();
            return;
        }

        System.out.println("[MeshSystem][init] Initializing MeshLibrary...");
        try {
            this.library = new MeshLibrary(this.node, this.dfs, this.executor);
            this.library.init();

        } catch (Exception e) {
            System.err.println("[MeshSystem][init] Exception while initializing MeshLibrary");
            e.printStackTrace();
            return;
        }
    }

    public MeshLibrary getLibrary() {
        return this.library;
    }
}
