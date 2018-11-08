package mesh.dfs;

import java.io.File;

public class BlockDescriptor {

    private String fileName;

    private int block_number;

    private int replica_number;

    private File file;

    public BlockDescriptor(File f) {
        if (f.isDirectory()) throw new IllegalArgumentException("File block cannot be a directory");
        this.file = f;
        String name = f.getName();
        String[] parts = name.split(".");
        this.fileName = parts[0];
        this.block_number = Integer.parseInt(parts[1]);
        this.replica_number = Integer.parseInt(parts[2]);
    }

    public BlockDescriptor(String blockName) {
        this(new File(DFS.blockDirectory.getPath() + blockName));
    }

    public BlockDescriptor(String fileName, int block_number) {
        this(fileName, block_number, 0);
    }

    public BlockDescriptor(String fileName, int block_number, int replica_number) {
        this.file = new File(DFS.blockDirectory.getPath().concat(fileName + "." + block_number + "." + replica_number));
        this.fileName = fileName;
        this.block_number = block_number;
        this.replica_number = replica_number;
    }

    public File getFile() {
        return this.file;
    }

    public String getFileName() {
        return this.fileName;
    }

    public String getBlockName() {
        return this.file.getName();
    }

    public int getBlockNumber() {
        return this.block_number;
    }

    public void setBlockNumber(int block_number) {
        this.block_number = block_number;
    }

    public int getReplicaNumber() {
        return this.replica_number;
    }

    public void setReplicaNumber(int replica) {
        this.replica_number = replica;
    }

    public int getBlockId() {
        return this.file.getName().hashCode();
    }

    public boolean blockExists() {
        return this.file.exists();
    }

    public long blockSize() {
        return this.file.length();
    }

    public long lastModified() {
        return this.file.lastModified();
    }

    public static final String PROPERTY_BLOCK_SIZE = "PROP_BLOCK_SIZE";
    public static final String PROPERTY_BLOCK_MODIFIED = "PROP_BLOCK_MODIFIED";
}
