package mesh.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import net.common.JsonField;

import java.io.IOException;

public class MeshConfiguration {
    public int network_id;
    public int network_master;
    public int node_id;
    public int node_count;

    public MeshConfiguration(int network_id, int network_master, int node_id, int node_count) {
        this.network_id = network_id;
        this.network_master = network_master;
        this.node_id = node_id;
        this.node_count = node_count;
    }

    public void serialize(JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField(PROPERTY_NET_ID, this.network_id);
        gen.writeNumberField(PROPERTY_MASTER_ID, this.network_master);
        gen.writeNumberField(PROPERTY_NODE_ID, this.node_id);
        gen.writeNumberField(PROPERTY_NODE_COUNT, this.node_count);
        gen.writeEndObject();
    }

    public static MeshConfiguration fromJson(JsonField.ObjectField config) {
        return new MeshConfiguration(
                (int) config.getLongProperty(PROPERTY_NET_ID),
                (int) config.getLongProperty(PROPERTY_MASTER_ID),
                (int) config.getLongProperty(PROPERTY_NODE_ID),
                (int) config.getLongProperty(PROPERTY_NODE_COUNT)
        );
    }

    public static final String PROPERTY_NET_ID = "PROP_NET_ID";
    public static final String PROPERTY_MASTER_ID = "PROP_MASTER_ID";
    public static final String PROPERTY_NODE_ID = "PROP_NODE_ID";
    public static final String PROPERTY_NODE_COUNT = "PROP_NODE_COUNT";
}
