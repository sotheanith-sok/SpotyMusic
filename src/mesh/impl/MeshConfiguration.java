package mesh.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import net.common.JsonField;
import persistence.IObservable;
import persistence.IObserver;

import java.io.IOException;
import java.util.LinkedList;

public class MeshConfiguration implements IObservable {
    private int network_id;
    private int network_master;
    private int node_id;
    private int node_count;

    private LinkedList<IObserver> observers;

    public MeshConfiguration(int network_id, int network_master, int node_id, int node_count) {
        this.network_id = network_id;
        this.network_master = network_master;
        this.node_id = node_id;
        this.node_count = node_count;
        this.observers = new LinkedList<>();
    }

    public int getNetwork_id() {
        return this.network_id;
    }

    public void setNetwork_id(int id) {
        this.network_id = id;
        this.onChange();
    }

    public int getMasterId() {
        return this.network_master;
    }

    public void setMasterId(int id) {
        this.network_master = id;
        this.onChange();
    }

    public int getNodeId() {
        return this.node_id;
    }

    public void setNodeId(int id) {
        this.node_id = id;
        this.onChange();
    }

    public int getNodeCount() {
        return this.node_count;
    }

    public void setNodeCount(int count) {
        this.node_count = count;
        this.onChange();
    }

    public boolean isMaster() {
        return this.network_master == this.node_id;
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

    private void onChange() {
        for (IObserver observer : this.observers) {
            observer.onObservableChange();
        }
    }

    @Override
    public void addObserver(IObserver observer) {
        this.observers.add(observer);
    }

    @Override
    public void removeObserver(IObserver observer) {
        this.observers.remove(observer);
    }
}
