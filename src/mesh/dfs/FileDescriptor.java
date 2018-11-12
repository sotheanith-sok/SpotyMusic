package mesh.dfs;

import com.fasterxml.jackson.core.JsonGenerator;
import net.common.JsonField;
import persistence.IObservable;
import persistence.IObserver;

import java.io.IOException;
import java.util.LinkedList;

public class FileDescriptor implements IObservable {

    private String fileName;

    private long totalSize;

    private int blockCount;

    private int replicas;

    private LinkedList<IObserver> observers;

    public FileDescriptor(String fileName, long totalSize, int blockCount, int replicas) {
        this.fileName = fileName;
        this.totalSize = totalSize;
        this.blockCount = blockCount;
        this.replicas = replicas;

        this.observers = new LinkedList<>();
    }

    public String getFileName() {
        return this.fileName;
    }

    public long getTotalSize() {
        return this.totalSize;
    }

    public int getBlockCount() {
        return this.blockCount;
    }

    public int getReplicas() {
        return this.replicas;
    }

    public static FileDescriptor fromJson(JsonField.ObjectField field) {
        String name = field.getStringProperty(PROPERTY_FILE_NAME);
        long size = field.getLongProperty(PROPERTY_TOTAL_SIZE);
        int blocks = (int) field.getLongProperty(PROPERTY_BLOCK_COUNT);
        int replicas = (int) field.getLongProperty(PROPERTY_REPLICAS);

        return new FileDescriptor(name, size, blocks, replicas);
    }

    public void serialize(JsonGenerator gen) throws IOException {
        this.serialize(gen, false);
    }

    public void serialize(JsonGenerator gen, boolean rawFields) throws IOException {
        if (!rawFields) gen.writeStartObject();
        gen.writeStringField(PROPERTY_FILE_NAME, this.fileName);
        gen.writeNumberField(PROPERTY_TOTAL_SIZE, this.totalSize);
        gen.writeNumberField(PROPERTY_BLOCK_COUNT, this.blockCount);
        gen.writeNumberField(PROPERTY_REPLICAS, this.replicas);
        if (!rawFields) gen.writeEndObject();
    }

    @Override
    public void addObserver(IObserver observer) {
        this.observers.add(observer);
    }

    @Override
    public void removeObserver(IObserver observer) {
        this.observers.remove(observer);
    }

    public static final String PROPERTY_FILE_NAME = "PROP_FILE_NAME";
    public static final String PROPERTY_TOTAL_SIZE = "PROP_TOTAL_SIZE";
    public static final String PROPERTY_BLOCK_COUNT = "PROP_BLOCK_COUNT";
    public static final String PROPERTY_REPLICAS = "PROP_REPLICAS";
}
