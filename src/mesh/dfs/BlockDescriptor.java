package mesh.dfs;

import java.io.File;
import java.util.Random;

public class BlockDescriptor {

    private String fileName;

    private int block_number;

    private int replica_number;

    private File file;

    private long fileSize;

    private long lastModified;

    private int blockId;

    public BlockDescriptor(File f) {
        if (f.isDirectory()) throw new IllegalArgumentException("File block cannot be a directory");
        this.file = f;
        String name = f.getName();
        String[] parts = name.split("\\.");
        this.fileName = parts[0];
        this.block_number = Integer.parseInt(parts[1]);
        this.replica_number = Integer.parseInt(parts[2]);

        if (f.exists()) {
            this.fileSize = f.length();
            this.lastModified = f.lastModified();
        }

        this.computeBlockId();
    }

    public BlockDescriptor(String blockName) {
        this(new File(DFS.blockDirectory.getPath() + File.separatorChar + blockName));
    }

    public BlockDescriptor(String fileName, int block_number) {
        this(fileName, block_number, 0);
    }

    public BlockDescriptor(String fileName, int block_number, int replica_number) {
        this.file = new File(DFS.blockDirectory.getPath().concat(File.separatorChar + fileName + "." + block_number + "." + replica_number));
        this.fileName = fileName;
        this.block_number = block_number;
        this.replica_number = replica_number;

        if (this.file.exists()) {
            this.fileSize = this.file.length();
            this.lastModified = this.file.lastModified();
        }

        this.computeBlockId();
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
        this.updateStats();
    }

    public int getReplicaNumber() {
        return this.replica_number;
    }

    public void setReplicaNumber(int replica) {
        this.replica_number = replica;
        this.updateStats();
    }

    private void computeBlockId() {
        String reverse = "";
        for(int i = this.file.getName().length() - 1; i >= 0; i--)
            reverse = reverse + this.file.getName().charAt(i);

        Random rand = new Random(reverse.hashCode());
        for (int i = 0; i < this.getBlockNumber(); i++) rand.nextInt();
        this.blockId = rand.nextInt();
    }

    public int getBlockId() {
        return this.blockId;
    }

    public boolean blockExists() {
        return this.file.exists();
    }

    public long blockSize() {
        return this.fileSize;
    }

    public long lastModified() {
        return this.lastModified;
    }

    protected void updateStats() {
        if (this.file.exists()) {
            this.fileSize = this.file.length();
            this.lastModified = this.file.lastModified();
        }

        this.computeBlockId();
    }

    public static final String PROPERTY_BLOCK_NUMBER = "PROP_BLOCK_NUMBER";
    public static final String PROPERTY_BLOCK_REPLICA = "PROP_BLOCK_REPLICA";
    public static final String PROPERTY_BLOCK_SIZE = "PROP_BLOCK_SIZE";
    public static final String PROPERTY_BLOCK_MODIFIED = "PROP_BLOCK_MODIFIED";
}
