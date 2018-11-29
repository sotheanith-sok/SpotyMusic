package mesh.dfs;

import net.Constants;
import net.common.DeferredStreamJsonGenerator;
import net.reqres.Socketplexer;

import java.io.*;
import java.util.concurrent.*;

public class WriteBlockRequestHandler implements Runnable {

    private Socketplexer socketplexer;

    private BlockDescriptor block;

    private DFS dfs;

    private boolean append;

    public WriteBlockRequestHandler(Socketplexer socketplexer, BlockDescriptor block, DFS dfs) {
        this(socketplexer, block, dfs, false);
    }

    public WriteBlockRequestHandler(Socketplexer socketplexer, BlockDescriptor block, DFS dfs, boolean append) {
        this.socketplexer = socketplexer;
        this.block = block;
        this.dfs = dfs;
        this.append = append;
    }

    @Override
    public void run() {
        File f = this.block.getFile();

        try {
            if (!f.exists()) f.createNewFile();
        } catch (IOException e) {
            System.err.println("[WriteBlockRequestHandler] Unable to create file to store block! " + block.getBlockName());
            e.printStackTrace();

            try {
                (new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, this.append ? DFS.RESPONSE_APPEND_BLOCK : DFS.RESPONSE_WRITE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e1) {
                System.err.println("[WriteBlockRequestHandler] Unable to obtain response header stream");
                this.socketplexer.terminate();
            }

            return;
        }

        OutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(block.getFile(), this.append));

            try {
                (new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, this.append ? DFS.RESPONSE_APPEND_BLOCK : DFS.RESPONSE_WRITE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    gen.writeEndObject();
                })).run();

            } catch (IOException e) {
                System.err.println("[WriteBlockRequestHandler] Unable to obtain response header stream");
                this.socketplexer.terminate();
            }

        } catch (FileNotFoundException e) {
            System.err.println("[WriteBlockRequestHandler] Unable to open file to write block! " + block.getBlockName());
            e.printStackTrace();

            try {
                (new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, this.append ? DFS.RESPONSE_APPEND_BLOCK : DFS.RESPONSE_WRITE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                })).run();

            } catch (IOException e1) {
                System.err.println("[WriteBlockRequestHandler] Unable to obtain response header stream");
                this.socketplexer.terminate();
            }

            return;
        }

        InputStream in = null;
        try {
            Future<InputStream> future = this.socketplexer.waitInputChannel(2);
            in = future.get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS);

            byte[] trx = new byte[1024 * 8];
            int read = 0;
            while ((read = in.read(trx, 0, trx.length)) != -1) {
                out.write(trx, 0, read);
            }

            System.out.println("[WriteBlockRequestHandler] End of request body stream");

            in.close();
            out.close();
            this.socketplexer.terminate();

        } catch (IOException e) {
            System.err.println("[WriteBlockRequestHandler] There was a problem while writing received block! " + block.getBlockName());
            e.printStackTrace();

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            System.err.println("[WriteBlockRequestHandler] There was a problem getting the block data");
            e.printStackTrace();

        } finally {
            try { out.close(); } catch (IOException e) { e.printStackTrace(); }

            if (in != null) {
                try { in.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }

        // add block to list of blocks
        this.block.updateStats();
        this.dfs.blocks.put(this.block.getBlockName(), this.block);

        System.out.println("[WriteBlockRequestHandler] Successfully wrote block " + block.getBlockName());
    }
}
