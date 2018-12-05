package mesh.dfs;

import net.Constants;
import net.common.DeferredStreamJsonGenerator;
import net.reqres.Socketplexer;
import utils.Logger;

import java.io.*;
import java.util.concurrent.*;

public class WriteBlockRequestHandler implements Runnable {

    private Socketplexer socketplexer;

    private BlockDescriptor block;

    private DFS dfs;

    private boolean append;

    private Logger logger;

    public WriteBlockRequestHandler(Socketplexer socketplexer, BlockDescriptor block, DFS dfs) {
        this(socketplexer, block, dfs, false);
    }

    public WriteBlockRequestHandler(Socketplexer socketplexer, BlockDescriptor block, DFS dfs, boolean append) {
        this.socketplexer = socketplexer;
        this.block = block;
        this.dfs = dfs;
        this.append = append;
        this.logger = new Logger("WriteBlockRequestHandler", Constants.DEBUG);
    }

    @Override
    public void run() {
        this.logger.log(" Processing WriteBlockRequest");
        File f = this.block.getFile();

        try {
            this.logger.debug(" Creating file to store block");
            if (!f.exists()) f.createNewFile();
        } catch (IOException e) {
            this.logger.error(" Unable to create file to store block! " + block.getBlockName());
            e.printStackTrace();

            try {
                (new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, this.append ? DFS.RESPONSE_APPEND_BLOCK : DFS.RESPONSE_WRITE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e1) {
                this.logger.warn(" Unable to obtain response header stream");
                this.socketplexer.terminate();
            }

            return;
        }

        OutputStream out = null;
        OutputStream headersOut = null;
        try {
            this.logger.finer(" Opening FileOutputStream");
            out = new BufferedOutputStream(new FileOutputStream(block.getFile(), this.append));
            this.logger.trace(" FileOutputStream opened");

            try {
                this.logger.trace(" Sending response headers");
                (new DeferredStreamJsonGenerator(headersOut = this.socketplexer.openOutputChannel(1), false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, this.append ? DFS.RESPONSE_APPEND_BLOCK : DFS.RESPONSE_WRITE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    gen.writeEndObject();
                })).run();
                this.logger.trace(" Response headers sent");

            } catch (IOException e) {
                this.logger.error(" Unable to obtain response header stream");
                this.socketplexer.terminate();
            }

        } catch (FileNotFoundException e) {
            this.logger.error(" Unable to open file to write block! " + block.getBlockName());
            e.printStackTrace();

            try {
                this.logger.trace(" Sending response headers");
                (new DeferredStreamJsonGenerator(headersOut = this.socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, this.append ? DFS.RESPONSE_APPEND_BLOCK : DFS.RESPONSE_WRITE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                })).run();
                this.logger.trace(" Response headers sent");

            } catch (IOException e1) {
                this.logger.error(" Unable to obtain response header stream");
                this.socketplexer.terminate();
            }

            return;
        }

        InputStream in = null;
        try {
            this.logger.fine(" Getting request body stream");
            Future<InputStream> future = this.socketplexer.waitInputChannel(2);
            in = future.get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS);
            try { headersOut.close(); } catch (IOException e) {
                this.logger.warn(" Unable to close header stream");
            }
            this.logger.finest(" Request body stream opened");

            byte[] trx = new byte[1024 * 8];
            this.logger.trace(" Copying request body to file");
            int read = 0;
            long trxd = 0;
            while ((read = in.read(trx, 0, trx.length)) != -1) {
                out.write(trx, 0, read);
                trxd += read;
            }

            this.logger.log(" Received " + trxd + " bytes");

            in.close();
            out.close();
            this.socketplexer.terminate();

        } catch (IOException e) {
            this.logger.error(" There was a problem while writing received block! " + block.getBlockName());
            e.printStackTrace();

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            this.logger.error(" There was a problem getting the block data");
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

        this.logger.log(" Successfully wrote block " + block.getBlockName());
    }
}
